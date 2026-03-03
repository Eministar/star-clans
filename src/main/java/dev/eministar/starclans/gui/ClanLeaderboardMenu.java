package dev.eministar.starclans.gui;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.service.ClanService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class ClanLeaderboardMenu implements Listener {

    private final StarClans plugin;
    private final ClanService service;
    private final NamespacedKey actionKey;
    private final String title;

    private boolean byBalance = true;

    public ClanLeaderboardMenu(StarClans plugin, ClanService service) {
        this.plugin = plugin;
        this.service = service;
        this.actionKey = new NamespacedKey(plugin, "sc_lb_action");
        this.title = plugin.lang().get("gui.leaderboard.title");
    }

    public void open(Player player) {
        open(player, true);
    }

    public void open(Player player, boolean balance) {
        this.byBalance = balance;
        service.getTopClans(byBalance, top -> openWithData(player, top));
    }

    private void openWithData(Player player, List<ClanRepository.ClanLeaderboardRow> top) {
        Inventory inv = Bukkit.createInventory(player, 54, title);

        ItemStack border = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53}) {
            inv.setItem(i, border);
        }

        inv.setItem(4, button(byBalance ? Material.GOLD_BLOCK : Material.PLAYER_HEAD,
                plugin.lang().get(byBalance ? "gui.leaderboard.switch.balance" : "gui.leaderboard.switch.members"),
                plugin.lang().getList("gui.leaderboard.switch.lore"), "SWITCH", true));

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < top.size() && i < slots.length; i++) {
            ClanRepository.ClanLeaderboardRow row = top.get(i);
            inv.setItem(slots[i], clanButton(i + 1, row));
        }

        inv.setItem(49, button(Material.ARROW, plugin.lang().get("gui.generic.back.name"),
                plugin.lang().getList("gui.generic.back.lore"), "BACK", false));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (!e.getView().getTitle().equals(title)) return;

        e.setCancelled(true);
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;

        String action = it.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);

        if (action.equals("BACK")) {
            p.closeInventory();
            p.performCommand("clan");
            return;
        }

        if (action.equals("SWITCH")) {
            open(p, !byBalance);
            return;
        }
    }

    private ItemStack clanButton(int rank, ClanRepository.ClanLeaderboardRow row) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.leaderboard.entry.name", "rank", Integer.valueOf(rank), "clan_name", row.name));
            List<String> lore = new ArrayList<>();
            for (String line : plugin.lang().getList("gui.leaderboard.entry.lore")) {
                lore.add(line.replace("{tag}", row.tag)
                        .replace("{balance}", service.money(row.balance))
                        .replace("{members}", String.valueOf(row.memberCount)));
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§r");
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack button(Material m, String name, List<String> lore, String action, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
            it.setItemMeta(meta);
        }
        return it;
    }
}
