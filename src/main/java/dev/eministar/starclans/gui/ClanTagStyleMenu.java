package dev.eministar.starclans.gui;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.model.ClanProfile;
import dev.eministar.starclans.model.MemberRole;
import dev.eministar.starclans.service.ClanService;
import dev.eministar.starclans.utils.StarPrefix;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
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

    private final StarClans plugin;
    private final ClanService service;
    private final ClanRepository repo;

    private final NamespacedKey key;
    private final String title = "§d§lClan §8| §fTag Styler";

    private final Set<UUID> awaitingSuffix = ConcurrentHashMap.newKeySet();
    private final Map<UUID, CreateCtx> creating = new ConcurrentHashMap<>();

    private final List<ColorPick> colors = List.of(
            new ColorPick("§0", Material.BLACK_DYE, "§0Schwarz"),
            new ColorPick("§8", Material.GRAY_DYE, "§8Grau"),
            new ColorPick("§7", Material.LIGHT_GRAY_DYE, "§7Hellgrau"),
            new ColorPick("§f", Material.WHITE_DYE, "§fWeiß"),
            new ColorPick("§c", Material.RED_DYE, "§cRot"),
            new ColorPick("§6", Material.ORANGE_DYE, "§6Orange"),
            new ColorPick("§e", Material.YELLOW_DYE, "§eGelb"),
            new ColorPick("§a", Material.LIME_DYE, "§aHellgrün"),
            new ColorPick("§2", Material.GREEN_DYE, "§2Grün"),
            new ColorPick("§b", Material.LIGHT_BLUE_DYE, "§bHellblau"),
            new ColorPick("§3", Material.CYAN_DYE, "§3Cyan"),
            new ColorPick("§9", Material.BLUE_DYE, "§9Blau"),
            new ColorPick("§d", Material.MAGENTA_DYE, "§dMagenta"),
            new ColorPick("§5", Material.PURPLE_DYE, "§5Lila"),
            new ColorPick("§4", Material.BROWN_DYE, "§4Braun"),
            new ColorPick("§1", Material.LAPIS_LAZULI, "§1Dunkelblau")
    );

    private final int[] colorSlots = {10,11,12,13,14,15,16,19,20,21,23,25,28,29,30,31};

    public ClanTagStyleMenu(StarClans plugin, ClanService service, ClanRepository repo) {
        this.plugin = plugin;
        this.service = service;
        this.repo = repo;
        this.key = new NamespacedKey(plugin, "sc_tagstyler");
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
            p.sendMessage(StarPrefix.PREFIX + "§cDu bist in keinem Clan.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MemberRole r = repo.getRole(p.getUniqueId());
                if (r == MemberRole.MEMBER) {
                    sync(() -> p.sendMessage(StarPrefix.PREFIX + "§cKeine Rechte."));
                    return;
                }
                ClanRepository.ClanCosmeticsRow cos = repo.getCosmetics(prof.clanId);
                sync(() -> openInvClan(p, prof, cos));
            } catch (Exception e) {
                sync(() -> p.sendMessage(StarPrefix.PREFIX + "§cFehler. Console."));
                e.printStackTrace();
            }
        });
    }

    public void openCreate(Player p, String tag, String currentStyle, Runnable backToCreateMenu, Consumer<String> saveStyleToState) {
        CreateCtx ctx = new CreateCtx(tag, currentStyle, backToCreateMenu, saveStyleToState);
        creating.put(p.getUniqueId(), ctx);
        openInvCreate(p, ctx);
    }

    private void openInvClan(Player p, ClanProfile prof, ClanRepository.ClanCosmeticsRow cos) {
        Inventory inv = Bukkit.createInventory(p, 45, title);

        for (int i = 0; i < 45; i++) inv.setItem(i, pane(Material.GRAY_STAINED_GLASS_PANE));
        for (int i = 0; i < 9; i++) inv.setItem(i, pane(Material.BLACK_STAINED_GLASS_PANE));
        for (int i = 36; i < 45; i++) inv.setItem(i, pane(Material.BLACK_STAINED_GLASS_PANE));

        inv.setItem(4, previewClan(prof, cos));

        boolean bold = cos.tagStyle.contains("§l");
        String currentColor = extractColor(cos.tagStyle);

        for (int i = 0; i < Math.min(colors.size(), colorSlots.length); i++) {
            ColorPick c = colors.get(i);
            boolean selected = c.code.equals(currentColor);
            inv.setItem(colorSlots[i], colorButton(c, selected));
        }

        inv.setItem(22, button(Material.FEATHER, "§bSuffix bearbeiten",
                List.of("§7Aktuell: §f" + (cos.chatSuffix.isEmpty() ? "§7-" : cos.chatSuffix), "", "§bKlick §7und schreib es in den Chat", "§8(cancel zum Abbrechen)"),
                "EDIT_SUFFIX", true));

        inv.setItem(24, button(Material.SPYGLASS, "§7Suffix löschen",
                List.of("§7Entfernt den Clan-Suffix"),
                "CLEAR_SUFFIX", false));

        inv.setItem(33, button(Material.ANVIL, bold ? "§a§lFett: AN" : "§7Fett: AUS",
                List.of("§7Togglest §lBold §7für den Tag"),
                "TOGGLE_BOLD", bold));

        inv.setItem(34, button(Material.BUCKET, "§7Reset",
                List.of("§7Setzt Style auf Standard"),
                "RESET", false));

        inv.setItem(40, button(Material.BARRIER, "§cZurück",
                List.of("§7Zurück ins Clan-Menü"),
                "BACK", false));

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.25f);
    }

    private void openInvCreate(Player p, CreateCtx ctx) {
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

        inv.setItem(33, button(Material.ANVIL, bold ? "§a§lFett: AN" : "§7Fett: AUS",
                List.of("§7Togglest §lBold §7für den Tag"),
                "CREATE_TOGGLE_BOLD", bold));

        inv.setItem(34, button(Material.BUCKET, "§7Reset",
                List.of("§7Setzt Style auf Standard"),
                "CREATE_RESET", false));

        inv.setItem(40, button(Material.BARRIER, "§cZurück",
                List.of("§7Zurück zum Erstellen"),
                "CREATE_BACK", false));

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

        if (action.equals("BACK")) {
            p.closeInventory();
            p.performCommand("clan");
            return;
        }

        if (action.equals("EDIT_SUFFIX")) {
            awaitingSuffix.add(p.getUniqueId());
            p.closeInventory();
            p.sendMessage(StarPrefix.PREFIX + "§7Schreib den neuen §bClan-Suffix §7in den Chat. §8(cancel zum Abbrechen)");
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            return;
        }

        if (action.equals("CLEAR_SUFFIX")) {
            service.setChatSuffix(p, "", s -> p.sendMessage(StarPrefix.PREFIX + s));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(p), 2L);
            return;
        }

        if (action.equals("TOGGLE_BOLD")) {
            applyBoldToggleClan(p);
            return;
        }

        if (action.equals("RESET")) {
            service.setTagStyle(p, "", s -> p.sendMessage(StarPrefix.PREFIX + s));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> open(p), 2L);
            return;
        }

        if (action.startsWith("COLOR:")) {
            String code = action.substring("COLOR:".length());
            applyColorClan(p, code);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        if (!awaitingSuffix.contains(p.getUniqueId())) return;

        e.setCancelled(true);
        awaitingSuffix.remove(p.getUniqueId());

        String msg = e.getMessage() == null ? "" : e.getMessage().trim();
        if (msg.equalsIgnoreCase("cancel")) {
            sync(() -> {
                p.sendMessage(StarPrefix.PREFIX + "§7Abgebrochen.");
                open(p);
            });
            return;
        }

        service.setChatSuffix(p, msg, s -> sync(() -> {
            p.sendMessage(StarPrefix.PREFIX + s);
            open(p);
        }));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!title.equals(e.getView().getTitle())) return;

        CreateCtx ctx = creating.remove(p.getUniqueId());
        if (ctx != null) {
            String saved = ctx.style == null ? "" : ctx.style;
            Bukkit.getScheduler().runTask(plugin, () -> {
                ctx.saveStyle.accept(saved);
                ctx.back.run();
            });
        }
    }

    private void applyColorClan(Player p, String code) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClanProfile prof = service.getCached(p.getUniqueId());
                if (prof == null || !prof.inClan) return;

                ClanRepository.ClanCosmeticsRow cos = repo.getCosmetics(prof.clanId);
                boolean bold = cos.tagStyle.contains("§l");
                String style = code + (bold ? "§l" : "");

                service.setTagStyle(p, style, s -> p.sendMessage(StarPrefix.PREFIX + s));

                sync(() -> {
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.7f);
                    open(p);
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private void applyBoldToggleClan(Player p) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClanProfile prof = service.getCached(p.getUniqueId());
                if (prof == null || !prof.inClan) return;

                ClanRepository.ClanCosmeticsRow cos = repo.getCosmetics(prof.clanId);
                String color = extractColor(cos.tagStyle);
                boolean bold = cos.tagStyle.contains("§l");

                String style = color + (!bold ? "§l" : "");
                if (color.isEmpty()) style = !bold ? "§f§l" : "§f";

                service.setTagStyle(p, style, s -> p.sendMessage(StarPrefix.PREFIX + s));

                sync(() -> {
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.7f);
                    open(p);
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private ItemStack previewClan(ClanProfile prof, ClanRepository.ClanCosmeticsRow cos) {
        String styled = (cos.tagStyle.isEmpty() ? "§b" : cos.tagStyle) + prof.clanTag + "§r";
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§d§lPreview");
        meta.setLore(List.of(
                "§8",
                "§7Tag: §8[§r" + styled + "§8]",
                "§7Suffix: §f" + (cos.chatSuffix.isEmpty() ? "§7-" : cos.chatSuffix),
                "§8"
        ));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addEnchant(Enchantment.DENSITY, 1, true);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack previewCreate(String tag, String style) {
        String styled = (style == null || style.isEmpty() ? "§b" : style) + tag + "§r";
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§d§lPreview");
        meta.setLore(List.of(
                "§8",
                "§7Tag: §8[§r" + styled + "§8]",
                "§8",
                "§7Wird beim Erstellen gespeichert"
        ));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.addEnchant(Enchantment.DENSITY, 1, true);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack colorButton(ColorPick c, boolean selected) {
        ItemStack it = new ItemStack(c.mat);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(c.name);
        meta.setLore(List.of(selected ? "§aAusgewählt" : "§7Klick zum Auswählen"));
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
        meta.setDisplayName("§r");
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

    private static final class ColorPick {
        final String code;
        final Material mat;
        final String name;

        ColorPick(String code, Material mat, String name) {
            this.code = code;
            this.mat = mat;
            this.name = name;
        }
    }
}
