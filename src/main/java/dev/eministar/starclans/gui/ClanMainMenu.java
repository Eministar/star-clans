package dev.eministar.starclans.gui;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.model.ClanProfile;
import dev.eministar.starclans.service.ClanService;
import dev.eministar.starclans.vault.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;


public final class ClanMainMenu implements Listener {

    private final StarClans plugin;
    private final ClanService service;

    private final NamespacedKey actionKey;
    private String title;

    public ClanMainMenu(StarClans plugin, ClanService service) {
        this.plugin = plugin;
        this.service = service;
        this.actionKey = new NamespacedKey(plugin, "sc_action");
        this.title = plugin.lang().get("gui.main.title");
    }

    public void open(Player player) {
        service.loadProfileAsync(player.getUniqueId(), p -> openWithProfile(player, p));
    }

    private void openWithProfile(Player player, ClanProfile profile) {
        this.title = plugin.lang().get("gui.main.title");
        Inventory inv = Bukkit.createInventory(player, 54, title);

        ItemStack border = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack fill = pane(Material.GRAY_STAINED_GLASS_PANE);

        for (int i = 0; i < 54; i++) inv.setItem(i, fill);

        int[] frame = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};
        for (int slot : frame) inv.setItem(slot, border);

        if (!profile.inClan) {
            double cost = plugin.getConfig().getDouble("clan.creation.cost", 0.0);
            boolean ecoOk = VaultHook.hasEconomy();

            String costLine;
            if (cost <= 0.0) {
                costLine = plugin.lang().get("gui.main.cost.free");
            } else if (ecoOk) {
                costLine = plugin.lang().get("gui.main.cost.amount",
                        "cost", ClanService.moneyStatic(cost, plugin.lang().get("messages.money_suffix")));
            } else {
                costLine = plugin.lang().get("gui.main.cost.vault_missing");
            }

            inv.setItem(22, button(Material.EMERALD, plugin.lang().get("gui.main.create.name"),
                    plugin.lang().getList("gui.main.create.lore", "cost_line", costLine),
                    "CREATE", true));

            String invLine = profile.inviteCount <= 0
                    ? plugin.lang().get("gui.main.invites.none")
                    : plugin.lang().get("gui.main.invites.some", "count", Integer.valueOf(profile.inviteCount));
            inv.setItem(24, button(Material.PAPER, plugin.lang().get("gui.main.invites.name"),
                    plugin.lang().getList("gui.main.invites.lore", "invites_line", invLine),
                    "INVITES", profile.inviteCount > 0));

            inv.setItem(10, button(Material.GOLD_BLOCK, plugin.lang().get("gui.main.leaderboard.name"),
                    plugin.lang().getList("gui.main.leaderboard.lore"),
                    "LEADERBOARD", true));

            inv.setItem(49, button(Material.BARRIER, plugin.lang().get("gui.main.close.name"),
                    plugin.lang().getList("gui.main.close.lore"), "CLOSE", false));

        } else {
            inv.setItem(22, button(Material.NETHER_STAR, plugin.lang().get("gui.main.my_clan.name"),
                    plugin.lang().getList("gui.main.my_clan.lore",
                            "clan_name", profile.clanName,
                            "clan_tag", profile.clanTag,
                            "role", plugin.lang().role(profile.role),
                            "members", Integer.valueOf(profile.memberCount)),
                    "MANAGE", true));

            inv.setItem(20, button(Material.PLAYER_HEAD, plugin.lang().get("gui.main.members.name"),
                    plugin.lang().getList("gui.main.members.lore"),
                    "MEMBERS", false));

            String invLine = profile.inviteCount <= 0
                    ? plugin.lang().get("gui.main.invites.none")
                    : plugin.lang().get("gui.main.invites.some", "count", Integer.valueOf(profile.inviteCount));
            inv.setItem(24, button(Material.PAPER, plugin.lang().get("gui.main.invites.name"),
                    plugin.lang().getList("gui.main.invites.lore_in_clan", "invites_line", invLine),
                    "INVITES", profile.inviteCount > 0));

            if (service.isBankEnabled()) {
                inv.setItem(21, button(Material.CHEST, plugin.lang().get("gui.main.bank.name"),
                        plugin.lang().getList("gui.main.bank.lore", "balance", service.money(profile.balance)),
                        "BANK", false));
            } else {
                inv.setItem(21, button(Material.GRAY_DYE, plugin.lang().get("gui.main.bank.disabled_name"),
                        plugin.lang().getList("gui.main.bank.disabled_lore"),
                        "NONE", false));
            }

            inv.setItem(23, button(Material.WHITE_BED, plugin.lang().get("gui.main.home.name"),
                    plugin.lang().getList("gui.main.home.lore", "status",
                            (profile.homeWorld == null || profile.homeWorld.isEmpty())
                                    ? plugin.lang().get("gui.main.home.not_set")
                                    : plugin.lang().get("gui.main.home.set")),
                    "HOME", false));

            if (service.isProfileEnabled()) {
                inv.setItem(32, button(Material.BOOK, plugin.lang().get("gui.main.profile.name"),
                        plugin.lang().getList("gui.main.profile.lore"),
                        "PROFILE", true));
            }

            inv.setItem(10, button(Material.GOLD_BLOCK, plugin.lang().get("gui.main.leaderboard.name"),
                    plugin.lang().getList("gui.main.leaderboard.lore"),
                    "LEADERBOARD", true));

            boolean chatOn = service.isClanChat(player.getUniqueId());
            inv.setItem(31, button(Material.OAK_SIGN,
                    plugin.lang().get(chatOn ? "gui.main.chat_toggle.on" : "gui.main.chat_toggle.off"),
                    plugin.lang().getList("gui.main.chat_toggle.lore"),
                    "CHAT_TOGGLE", chatOn));

            inv.setItem(49, button(Material.BARRIER, plugin.lang().get("gui.main.close.name"),
                    plugin.lang().getList("gui.main.close.lore"), "CLOSE", false));
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.25f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (!e.getView().getTitle().equals(title)) return;

        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        ItemMeta meta = it.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.35f);

        if (action.equals("CLOSE")) {
            p.closeInventory();
            return;
        }

        if (action.equals("CREATE")) {
            p.closeInventory();
            p.performCommand("clan create");
            return;
        }

        if (action.equals("INVITES")) {
            p.closeInventory();
            p.performCommand("clan invites");
            return;
        }

        if (action.equals("MEMBERS")) {
            p.closeInventory();
            p.performCommand("clan members");
            return;
        }

        if (action.equals("BANK")) {
            p.closeInventory();
            p.performCommand("clan bank");
            return;
        }

        if (action.equals("HOME")) {
            p.closeInventory();
            p.performCommand("clan home");
            return;
        }

        if (action.equals("LEADERBOARD")) {
            p.closeInventory();
            p.performCommand("clan leaderboard");
            return;
        }

        if (action.equals("PROFILE")) {
            p.closeInventory();
            p.performCommand("clan profile");
            return;
        }

        if (action.equals("MANAGE")) {
            p.closeInventory();
            p.performCommand("clan manage");
            return;
        }

        if (action.equals("CHAT_TOGGLE")) {
            p.closeInventory();
            p.performCommand("clan chat");
        }
    }

    private ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§r");
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack button(Material m, String name, java.util.List<String> lore, String action, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        if (glow) meta.addEnchant(Enchantment.DENSITY, 1, true);
        it.setItemMeta(meta);
        return it;
    }
}
