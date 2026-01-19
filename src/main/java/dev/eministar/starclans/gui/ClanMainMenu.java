package dev.eministar.starclans.gui;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.model.ClanProfile;
import dev.eministar.starclans.service.ClanService;
import dev.eministar.starclans.utils.StarPrefix;
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

import java.util.Arrays;

public final class ClanMainMenu implements Listener {

    private final StarClans plugin;
    private final ClanService service;

    private final NamespacedKey actionKey;
    private final String title = "§b§lStarClans §8✦ §fClan Menü";

    public ClanMainMenu(StarClans plugin, ClanService service) {
        this.plugin = plugin;
        this.service = service;
        this.actionKey = new NamespacedKey(plugin, "sc_action");
    }

    public void open(Player player) {
        service.loadProfileAsync(player.getUniqueId(), p -> openWithProfile(player, p));
    }

    private void openWithProfile(Player player, ClanProfile profile) {
        Inventory inv = Bukkit.createInventory(player, 54, title);

        ItemStack border = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack fill = pane(Material.GRAY_STAINED_GLASS_PANE);

        for (int i = 0; i < 54; i++) inv.setItem(i, fill);

        int[] frame = {0,1,2,3,4,5,6,7,8, 9,17, 18,26, 27,35, 36,44, 45,46,47,48,49,50,51,52,53};
        for (int slot : frame) inv.setItem(slot, border);

        if (!profile.inClan) {
            double cost = plugin.getConfig().getDouble("clan.creation.cost", 0.0);
            boolean ecoOk = VaultHook.hasEconomy();

            String costLine;
            if (cost <= 0.0) costLine = "§8• §fKosten: §aGratis";
            else if (ecoOk) costLine = "§8• §fKosten: §6" + ClanService.moneyStatic(cost);
            else costLine = "§8• §fKosten: §cVault/Eco fehlt!";

            inv.setItem(20, button(Material.EMERALD, "§a§lClan erstellen",
                    Arrays.asList("§7Erstelle deinen eigenen Clan", "", costLine, "", "§7Klick zum Starten"),
                    "CREATE", true));

            String invLine = profile.inviteCount <= 0 ? "§8• §fEinladungen: §7Keine" : "§8• §fEinladungen: §a" + profile.inviteCount;
            inv.setItem(24, button(Material.PAPER, "§e§lEinladungen",
                    Arrays.asList("§7Sieh deine offenen Invites", "", invLine, "", "§7Klick zum Öffnen"),
                    "INVITES", profile.inviteCount > 0));

            inv.setItem(49, button(Material.BARRIER, "§c§lSchließen", Arrays.asList("§7Menü schließen"), "CLOSE", false));

        } else {
            inv.setItem(20, button(Material.NETHER_STAR, "§b§lMein Clan",
                    Arrays.asList("§7Name: §f" + profile.clanName, "§7Tag: §b" + profile.clanTag, "§7Rolle: §f" + profile.role.name(),
                            "§7Mitglieder: §f" + profile.memberCount, "", "§7Klick für Clan-Manage"),
                    "MANAGE", true));

            inv.setItem(22, button(Material.PLAYER_HEAD, "§d§lMitglieder",
                    Arrays.asList("§7Mitglieder anzeigen", "§7und Rollen später verwalten", "", "§7Klick zum Öffnen"),
                    "MEMBERS", false));

            String invLine = profile.inviteCount <= 0 ? "§8• §fEinladungen: §7Keine" : "§8• §fEinladungen: §a" + profile.inviteCount;
            inv.setItem(24, button(Material.PAPER, "§e§lEinladungen",
                    Arrays.asList("§7Offene Invites", "", invLine, "", "§7Klick zum Öffnen"),
                    "INVITES", profile.inviteCount > 0));

            boolean chatOn = service.isClanChat(player.getUniqueId());
            inv.setItem(40, button(Material.OAK_SIGN, chatOn ? "§a§lClan Chat: AN" : "§c§lClan Chat: AUS",
                    Arrays.asList("§7Toggle Clan-Chat Modus", "", "§7Klick zum Umschalten"),
                    "CHAT_TOGGLE", chatOn));

            inv.setItem(49, button(Material.BARRIER, "§c§lSchließen", Arrays.asList("§7Menü schließen"), "CLOSE", false));
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
