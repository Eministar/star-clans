package dev.eministar.starclans.gui;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.model.ClanProfile;
import dev.eministar.starclans.model.MemberRole;
import dev.eministar.starclans.service.ClanService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ClanTagStyleMenu implements Listener {

    private static final class CreateCtx {
        final String tag;
        String style;
        final Runnable back;
        final Consumer<String> saveStyle;

        CreateCtx(String tag, String style, Runnable back, Consumer<String> saveStyle) {
            this.tag = tag == null ? "" : tag;
            this.style = style == null ? "" : style;
            this.back = back;
            this.saveStyle = saveStyle;
        }
    }

    private static final class EditCtx {
        String style;

        EditCtx(String style) {
            this.style = style == null ? "" : style;
        }
    }

    private final StarClans plugin;
    private final ClanService service;
    private final ClanRepository repo;

    private final NamespacedKey key;
    private String title;

    private final Map<UUID, CreateCtx> creating = new ConcurrentHashMap<>();
    private final Map<UUID, EditCtx> editing = new ConcurrentHashMap<>();
    private final Set<UUID> refreshing = ConcurrentHashMap.newKeySet();

    private final List<ColorPick> colors;

    private final int[] colorSlots = {10,11,12,13,14,15,16,19,20,21,23,25,28,29,30,31};

    public ClanTagStyleMenu(StarClans plugin, ClanService service, ClanRepository repo) {
        this.plugin = plugin;
        this.service = service;
        this.repo = repo;
        this.key = new NamespacedKey(plugin, "sc_tagstyler");
        this.title = plugin.lang().get("gui.tag_style.title");
        this.colors = List.of(
                new ColorPick("§0", Material.BLACK_DYE, "black"),
                new ColorPick("§8", Material.GRAY_DYE, "gray"),
                new ColorPick("§7", Material.LIGHT_GRAY_DYE, "light_gray"),
                new ColorPick("§f", Material.WHITE_DYE, "white"),
                new ColorPick("§c", Material.RED_DYE, "red"),
                new ColorPick("§6", Material.ORANGE_DYE, "orange"),
                new ColorPick("§e", Material.YELLOW_DYE, "yellow"),
                new ColorPick("§a", Material.LIME_DYE, "lime"),
                new ColorPick("§2", Material.GREEN_DYE, "green"),
                new ColorPick("§b", Material.LIGHT_BLUE_DYE, "light_blue"),
                new ColorPick("§3", Material.CYAN_DYE, "cyan"),
                new ColorPick("§9", Material.BLUE_DYE, "blue"),
                new ColorPick("§d", Material.MAGENTA_DYE, "magenta"),
                new ColorPick("§5", Material.PURPLE_DYE, "purple"),
                new ColorPick("§4", Material.BROWN_DYE, "brown"),
                new ColorPick("§1", Material.LAPIS_LAZULI, "dark_blue")
        );
    }

    public void open(Player p) {
        CreateCtx ctx = creating.get(p.getUniqueId());
        if (ctx != null) {
            openInvCreate(p, ctx);
            return;
        }

        ClanProfile prof = service.getCached(p.getUniqueId());
        if (prof == null) {
            service.loadProfileAsync(p.getUniqueId(), x -> Bukkit.getScheduler().runTask(plugin, () -> open(p)));
            return;
        }
        if (!prof.inClan) {
            p.sendMessage(plugin.lang().prefixed("messages.not_in_clan"));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MemberRole r = repo.getRole(p.getUniqueId());
                if (r == MemberRole.MEMBER) {
                    sync(() -> p.sendMessage(plugin.lang().prefixed("messages.no_rights")));
                    return;
                }
                ClanRepository.ClanCosmeticsRow cos = repo.getCosmetics(prof.clanId);
                sync(() -> {
                    EditCtx edit = new EditCtx(cos.tagStyle);
                    editing.put(p.getUniqueId(), edit);
                    openInvClan(p, prof, edit);
                });
            } catch (Exception e) {
                sync(() -> p.sendMessage(plugin.lang().prefixed("messages.error_console")));
                e.printStackTrace();
            }
        });
    }

    public void openCreate(Player p, String tag, String currentStyle, Runnable backToCreateMenu, Consumer<String> saveStyleToState) {
        CreateCtx ctx = new CreateCtx(tag, currentStyle, backToCreateMenu, saveStyleToState);
        creating.put(p.getUniqueId(), ctx);
        openInvCreate(p, ctx);
    }

    private void openInvClan(Player p, ClanProfile prof, EditCtx edit) {
        this.title = plugin.lang().get("gui.tag_style.title");
        Inventory inv = Bukkit.createInventory(p, 45, title);

        for (int i = 0; i < 45; i++) inv.setItem(i, pane(Material.GRAY_STAINED_GLASS_PANE));
        for (int i = 0; i < 9; i++) inv.setItem(i, pane(Material.BLACK_STAINED_GLASS_PANE));
        for (int i = 36; i < 45; i++) inv.setItem(i, pane(Material.BLACK_STAINED_GLASS_PANE));

        inv.setItem(4, previewClan(prof, edit));

        boolean bold = edit.style.contains("§l");
        String currentColor = extractColor(edit.style);

        for (int i = 0; i < Math.min(colors.size(), colorSlots.length); i++) {
            ColorPick c = colors.get(i);
            boolean selected = c.code.equals(currentColor);
            inv.setItem(colorSlots[i], colorButton(c, selected));
        }

        inv.setItem(33, button(Material.ANVIL,
                plugin.lang().get(bold ? "gui.tag_style.bold.on" : "gui.tag_style.bold.off"),
                plugin.lang().getList("gui.tag_style.bold.lore"),
                "TOGGLE_BOLD", bold));

        inv.setItem(34, button(Material.BUCKET, plugin.lang().get("gui.tag_style.reset.name"),
                plugin.lang().getList("gui.tag_style.reset.lore"),
                "RESET", false));

        inv.setItem(38, button(Material.EMERALD, plugin.lang().get("gui.tag_style.save.name"),
                plugin.lang().getList("gui.tag_style.save.lore"),
                "SAVE", true));

        inv.setItem(40, button(Material.BARRIER, plugin.lang().get("gui.tag_style.back.name"),
                plugin.lang().getList("gui.tag_style.back.lore"),
                "BACK", false));

        markRefreshing(p);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.25f);
    }

    private void openInvCreate(Player p, CreateCtx ctx) {
        this.title = plugin.lang().get("gui.tag_style.title");
        Inventory inv = Bukkit.createInventory(p, 45, title);

        for (int i = 0; i < 45; i++) inv.setItem(i, pane(Material.GRAY_STAINED_GLASS_PANE));
        for (int i = 0; i < 9; i++) inv.setItem(i, pane(Material.BLACK_STAINED_GLASS_PANE));
        for (int i = 36; i < 45; i++) inv.setItem(i, pane(Material.BLACK_STAINED_GLASS_PANE));

        inv.setItem(4, previewCreate(ctx.tag, ctx.style));

        boolean bold = ctx.style.contains("§l");
        String currentColor = extractColor(ctx.style);

        for (int i = 0; i < Math.min(colors.size(), colorSlots.length); i++) {
            ColorPick c = colors.get(i);
            boolean selected = c.code.equals(currentColor);
            inv.setItem(colorSlots[i], colorButton(c, selected));
        }

        inv.setItem(33, button(Material.ANVIL,
                plugin.lang().get(bold ? "gui.tag_style.bold.on" : "gui.tag_style.bold.off"),
                plugin.lang().getList("gui.tag_style.bold.lore"),
                "CREATE_TOGGLE_BOLD", bold));

        inv.setItem(34, button(Material.BUCKET, plugin.lang().get("gui.tag_style.reset.name"),
                plugin.lang().getList("gui.tag_style.reset.lore"),
                "CREATE_RESET", false));

        inv.setItem(40, button(Material.BARRIER, plugin.lang().get("gui.tag_style.create_back.name"),
                plugin.lang().getList("gui.tag_style.create_back.lore"),
                "CREATE_BACK", false));

        markRefreshing(p);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.25f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!title.equals(e.getView().getTitle())) return;

        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;

        ItemMeta meta = it.getItemMeta();
        String action = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (action == null) return;

        CreateCtx ctx = creating.get(p.getUniqueId());
        if (ctx != null) {
            if (action.equals("CREATE_BACK")) {
                creating.remove(p.getUniqueId());
                p.closeInventory();
                String saved = ctx.style == null ? "" : ctx.style;
                sync(() -> {
                    ctx.saveStyle.accept(saved);
                    ctx.back.run();
                });
                return;
            }

            if (action.equals("CREATE_RESET")) {
                ctx.style = "";
                openInvCreate(p, ctx);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.35f);
                return;
            }

            if (action.equals("CREATE_TOGGLE_BOLD")) {
                String color = extractColor(ctx.style);
                boolean bold = ctx.style.contains("§l");
                String next = color + (!bold ? "§l" : "");
                if (color.isEmpty()) next = !bold ? "§f§l" : "§f";
                ctx.style = next;
                openInvCreate(p, ctx);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.35f);
                return;
            }

            if (action.startsWith("COLOR:")) {
                String code = action.substring("COLOR:".length());
                boolean bold = ctx.style.contains("§l");
                ctx.style = code + (bold ? "§l" : "");
                openInvCreate(p, ctx);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.35f);
                return;
            }

            return;
        }

        EditCtx edit = editing.get(p.getUniqueId());
        if (edit == null) {
            edit = new EditCtx("");
            editing.put(p.getUniqueId(), edit);
        }

        if (action.equals("BACK")) {
            editing.remove(p.getUniqueId());
            p.closeInventory();
            p.performCommand("clan");
            return;
        }

        if (action.equals("TOGGLE_BOLD")) {
            String color = extractColor(edit.style);
            boolean bold = edit.style.contains("§l");

            String style = color + (!bold ? "§l" : "");
            if (color.isEmpty()) style = !bold ? "§f§l" : "§f";
            edit.style = style;

            ClanProfile prof = service.getCached(p.getUniqueId());
            if (prof == null) {
                open(p);
                return;
            }
            openInvClan(p, prof, edit);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.35f);
            return;
        }

        if (action.equals("RESET")) {
            edit.style = "";
            ClanProfile prof = service.getCached(p.getUniqueId());
            if (prof == null) {
                open(p);
                return;
            }
            openInvClan(p, prof, edit);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.35f);
            return;
        }

        if (action.startsWith("COLOR:")) {
            String code = action.substring("COLOR:".length());
            boolean bold = edit.style.contains("§l");
            edit.style = code + (bold ? "§l" : "");
            ClanProfile prof = service.getCached(p.getUniqueId());
            if (prof == null) {
                open(p);
                return;
            }
            openInvClan(p, prof, edit);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.35f);
            return;
        }

        if (action.equals("SAVE")) {
            service.setTagStyle(p, edit.style, s -> {
                p.sendMessage(plugin.lang().prefixedRaw(s));
                editing.remove(p.getUniqueId());
                open(p);
            });
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            return;
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!title.equals(e.getView().getTitle())) return;

        if (refreshing.remove(p.getUniqueId())) {
            return;
        }

        CreateCtx ctx = creating.remove(p.getUniqueId());
        if (ctx != null) {
            String saved = ctx.style == null ? "" : ctx.style;
            Bukkit.getScheduler().runTask(plugin, () -> {
                ctx.saveStyle.accept(saved);
                ctx.back.run();
            });
        }
        editing.remove(p.getUniqueId());
    }

    private ItemStack previewClan(ClanProfile prof, EditCtx edit) {
        String styled = (edit.style.isEmpty() ? "§b" : edit.style) + prof.clanTag + "§r";
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(plugin.lang().get("gui.tag_style.preview.name"));
        meta.setLore(plugin.lang().getList("gui.tag_style.preview.lore_clan",
                "styled", styled));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addEnchant(Enchantment.DENSITY, 1, true);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack previewCreate(String tag, String style) {
        String styled = (style == null || style.isEmpty() ? "§b" : style) + tag + "§r";
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(plugin.lang().get("gui.tag_style.preview.name"));
        meta.setLore(plugin.lang().getList("gui.tag_style.preview.lore_create",
                "styled", styled));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addEnchant(Enchantment.DENSITY, 1, true);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack colorButton(ColorPick c, boolean selected) {
        ItemStack it = new ItemStack(c.mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(plugin.lang().get("gui.tag_style.colors." + c.nameKey));
        meta.setLore(List.of(plugin.lang().get(selected ? "gui.tag_style.color.selected" : "gui.tag_style.color.select")));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "COLOR:" + c.code);
        if (selected) {
            meta.addEnchant(Enchantment.DENSITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(plugin.lang().get("gui.tag_style.glass"));
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack button(Material m, String name, List<String> lore, String action, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, action);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (glow) meta.addEnchant(Enchantment.DENSITY, 1, true);
        it.setItemMeta(meta);
        return it;
    }

    private String extractColor(String style) {
        if (style == null) return "";
        for (int i = 0; i < style.length() - 1; i++) {
            char a = style.charAt(i);
            char b = style.charAt(i + 1);
            if (a == '§' && "0123456789abcdef".indexOf(Character.toLowerCase(b)) >= 0) {
                return "§" + b;
            }
        }
        return "";
    }

    private void sync(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private void markRefreshing(Player p) {
        java.util.UUID u = p.getUniqueId();
        refreshing.add(u);
        Bukkit.getScheduler().runTask(plugin, () -> refreshing.remove(u));
    }

    private static final class ColorPick {
        final String code;
        final Material mat;
        final String nameKey;

        ColorPick(String code, Material mat, String nameKey) {
            this.code = code;
            this.mat = mat;
            this.nameKey = nameKey;
        }
    }
}
