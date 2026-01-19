package dev.eministar.starclans.gui;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanTagStylerMenu implements Listener {

    public enum Mode { CREATE, CLAN }

    public static final class Context {
        public final Mode mode;
        public final UUID player;
        public final long clanId;
        public final String tag;
        public final Runnable back;

        public String style = "";

        public Context(Mode mode, UUID player, long clanId, String tag, Runnable back) {
            this.mode = mode;
            this.player = player;
            this.clanId = clanId;
            this.tag = tag;
            this.back = back;
        }
    }

    private final StarClans plugin;
    private final ClanService service;
    private final ClanRepository repo;

    private final NamespacedKey key;
    private final String title = "§d§lTag Styler";

    private final Map<UUID, Context> ctx = new ConcurrentHashMap<>();
    private final Set<UUID> awaitingCustom = ConcurrentHashMap.newKeySet();

    public ClanTagStylerMenu(StarClans plugin, ClanService service, ClanRepository repo) {
        this.plugin = plugin;
        this.service = service;
        this.repo = repo;
        this.key = new NamespacedKey(plugin, "sc_tagstyler_action");
    }

    public void openForCreate(Player p, String tag, String currentStyle, Runnable backToCreate) {
        Context c = new Context(Mode.CREATE, p.getUniqueId(), -1, tag, backToCreate);
        c.style = currentStyle == null ? "" : currentStyle;
        ctx.put(p.getUniqueId(), c);
        openInv(p, c);
    }

    public void openForClan(Player p, Runnable backToSettings) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(p.getUniqueId());
                if (clanId <= 0) {
                    Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(StarPrefix.PREFIX + "§cDu bist in keinem Clan."));
                    return;
                }

                MemberRole role = repo.getRole(p.getUniqueId());
                if (role == MemberRole.MEMBER) {
                    Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(StarPrefix.PREFIX + "§cKeine Rechte dafür."));
                    return;
                }

                String[] nt = repo.getClanNameTag(clanId);
                ClanRepository.CosmeticsRow cos = repo.getCosmetics(clanId);

                Context c = new Context(Mode.CLAN, p.getUniqueId(), clanId, nt[1], backToSettings);
                c.style = cos.tagStyle == null ? "" : cos.tagStyle;
                ctx.put(p.getUniqueId(), c);

                Bukkit.getScheduler().runTask(plugin, () -> openInv(p, c));
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(StarPrefix.PREFIX + "§cFehler. Console."));
                e.printStackTrace();
            }
        });
    }

    private void openInv(Player p, Context c) {
        Inventory inv = Bukkit.createInventory(p, 27, title);

        for (int i = 0; i < 27; i++) inv.setItem(i, pane(Material.GRAY_STAINED_GLASS_PANE));
        for (int i = 0; i <= 8; i++) inv.setItem(i, pane(Material.BLACK_STAINED_GLASS_PANE));
        inv.setItem(9, pane(Material.BLACK_STAINED_GLASS_PANE));
        inv.setItem(17, pane(Material.BLACK_STAINED_GLASS_PANE));
        for (int i = 18; i <= 26; i++) inv.setItem(i, pane(Material.BLACK_STAINED_GLASS_PANE));

        inv.setItem(13, preview(c.tag, c.style));

        inv.setItem(10, preset("§bAqua", "§b", Material.LIGHT_BLUE_DYE));
        inv.setItem(11, preset("§dPink", "§d", Material.MAGENTA_DYE));
        inv.setItem(12, preset("§6Gold", "§6", Material.ORANGE_DYE));
        inv.setItem(14, preset("§5Purple", "§5", Material.PURPLE_DYE));
        inv.setItem(15, action(Material.PAPER, "§fCustom Farbe", List.of("§7Schreibe in den Chat:", "§f#ff2b2b §7oder §f&b", "§7oder §freset"), "CUSTOM", true));
        inv.setItem(16, action(Material.BARRIER, "§cReset", List.of("§7Entfernt den Style"), "RESET", false));

        inv.setItem(22, action(Material.EMERALD_BLOCK, "§a§lSpeichern", List.of("§7Übernimmt den Style"), "SAVE", true));
        inv.setItem(26, action(Material.ARROW, "§cZurück", List.of("§7Zurück"), "BACK", false));

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.25f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!e.getView().getTitle().equals(title)) return;

        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;

        String a = it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (a == null) return;

        Context c = ctx.get(p.getUniqueId());
        if (c == null) return;

        if (a.startsWith("PRESET:")) {
            c.style = a.substring("PRESET:".length());
            openInv(p, c);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            return;
        }

        if (a.equals("CUSTOM")) {
            awaitingCustom.add(p.getUniqueId());
            p.closeInventory();
            p.sendMessage(StarPrefix.PREFIX + "§7Gib eine Farbe ein: §f#ff2b2b§7 / §f&b§7 / §freset");
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            return;
        }

        if (a.equals("RESET")) {
            c.style = "";
            openInv(p, c);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            return;
        }

        if (a.equals("BACK")) {
            p.closeInventory();
            ctx.remove(p.getUniqueId());
            awaitingCustom.remove(p.getUniqueId());
            if (c.back != null) c.back.run();
            return;
        }

        if (a.equals("SAVE")) {
            if (c.mode == Mode.CREATE) {
                p.closeInventory();
                ctx.remove(p.getUniqueId());
                awaitingCustom.remove(p.getUniqueId());
                p.sendMessage(StarPrefix.PREFIX + "§aTag-Style gespeichert.");
                if (c.back != null) c.back.run();
                return;
            }

            p.closeInventory();
            ctx.remove(p.getUniqueId());
            awaitingCustom.remove(p.getUniqueId());

            service.setTagStyle(p, c.style, s -> {
                p.sendMessage(StarPrefix.PREFIX + s);
                if (c.back != null) c.back.run();
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();

        if (!awaitingCustom.contains(u)) return;

        e.setCancelled(true);
        awaitingCustom.remove(u);

        Context c = ctx.get(u);
        if (c == null) return;

        String msg = e.getMessage().trim();

        String parsed = parseStyle(msg);
        if (parsed == null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.sendMessage(StarPrefix.PREFIX + "§cUngültig. Nutze §f#RRGGBB§c oder §f&b§c oder §freset");
                openInv(p, c);
            });
            return;
        }

        c.style = parsed;
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.sendMessage(StarPrefix.PREFIX + "§aStyle gesetzt.");
            openInv(p, c);
        });
    }

    public String getCreateStyle(UUID player) {
        Context c = ctx.get(player);
        if (c == null) return "";
        if (c.mode != Mode.CREATE) return "";
        return c.style == null ? "" : c.style;
    }

    private ItemStack preview(String tag, String style) {
        ItemStack it = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§fPreview");
        meta.setLore(List.of(
                "§8",
                "§7So sieht es aus:",
                "§8[§r" + (style == null ? "" : style) + tag + "§r§8]",
                "§8",
                "§7Wähle einen Style."
        ));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack preset(String name, String style, Material dye) {
        return action(dye, name, List.of("§7Setzt " + name, "§8[§r" + style + "TAG§r§8]"), "PRESET:" + style, true);
    }

    private ItemStack action(Material m, String name, List<String> lore, String action, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, action);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (glow) meta.addEnchant(Enchantment.DURABILITY, 1, true);
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

    private String parseStyle(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.equalsIgnoreCase("reset")) return "";

        if (s.startsWith("§") || s.startsWith("&")) {
            if (s.length() < 2) return null;
            return "§" + s.charAt(1);
        }

        if (s.length() == 1) {
            return "§" + s.charAt(0);
        }

        if (s.startsWith("#")) s = s.substring(1);
        if (s.matches("^[0-9a-fA-F]{6}$")) {
            return hexToLegacy(s);
        }

        return null;
    }

    private String hexToLegacy(String hex) {
        String h = hex.toUpperCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder("§x");
        for (char c : h.toCharArray()) sb.append('§').append(c);
        return sb.toString();
    }
}
