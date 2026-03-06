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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanLeaderboardMenu implements Listener {

    private final StarClans plugin;
    private final ClanService service;
    private final ClanPublicProfileMenu profileMenu;
    private final NamespacedKey actionKey;
    private final NamespacedKey clanIdKey;
    private final Map<UUID, Boolean> sortMode = new ConcurrentHashMap<>();
    private String title;

    public ClanLeaderboardMenu(StarClans plugin, ClanService service, ClanPublicProfileMenu profileMenu) {
        this.plugin = plugin;
        this.service = service;
        this.profileMenu = profileMenu;
        this.actionKey = new NamespacedKey(plugin, "sc_lb_action");
        this.clanIdKey = new NamespacedKey(plugin, "sc_lb_clan_id");
        this.title = plugin.lang().get("gui.leaderboard.title");
    }

    public void open(Player player) {
        open(player, true);
    }

    public void open(Player player, boolean byBalance) {
        sortMode.put(player.getUniqueId(), Boolean.valueOf(byBalance));
        service.getTopClans(byBalance, top -> openWithData(player, top, byBalance));
    }

    private void openWithData(Player player, List<ClanRepository.ClanLeaderboardRow> top, boolean byBalance) {
        this.title = plugin.lang().get("gui.leaderboard.title");
        Inventory inv = Bukkit.createInventory(player, 54, title);

        ItemStack border = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53}) {
            inv.setItem(i, border);
        }

        inv.setItem(4, button(byBalance ? Material.GOLD_BLOCK : Material.PLAYER_HEAD,
                plugin.lang().get(byBalance ? "gui.leaderboard.switch.balance" : "gui.leaderboard.switch.members"),
                plugin.lang().getList("gui.leaderboard.switch.lore"), "SWITCH", -1L));

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        for (int i = 0; i < top.size() && i < slots.length; i++) {
            ClanRepository.ClanLeaderboardRow row = top.get(i);
            inv.setItem(slots[i], clanButton(i + 1, row));
        }

        inv.setItem(49, button(Material.ARROW, plugin.lang().get("gui.generic.back.name"),
                plugin.lang().getList("gui.generic.back.lore"), "BACK", -1L));

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!title.equals(e.getView().getTitle())) return;

        e.setCancelled(true);
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        String action = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);

        if (action.equals("BACK")) {
            player.closeInventory();
            player.performCommand("clan");
            return;
        }

        if (action.equals("SWITCH")) {
            boolean next = !sortMode.getOrDefault(player.getUniqueId(), Boolean.TRUE).booleanValue();
            open(player, next);
            return;
        }

        if (action.equals("PROFILE")) {
            if (!service.isLeaderboardProfileOpenEnabled()) return;
            Long clanId = item.getItemMeta().getPersistentDataContainer().get(clanIdKey, PersistentDataType.LONG);
            if (clanId == null || clanId.longValue() <= 0) return;
            boolean byBalance = sortMode.getOrDefault(player.getUniqueId(), Boolean.TRUE).booleanValue();
            profileMenu.openFromLeaderboard(player, clanId.longValue(), byBalance);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        sortMode.remove(e.getPlayer().getUniqueId());
    }

    private ItemStack clanButton(int rank, ClanRepository.ClanLeaderboardRow row) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.leaderboard.entry.name", "rank", Integer.valueOf(rank), "clan_name", row.name));
            List<String> lore = new ArrayList<>();
            for (String line : plugin.lang().getList("gui.leaderboard.entry.lore")) {
                lore.add(line.replace("{tag}", row.tag)
                        .replace("{balance}", service.money(row.balance))
                        .replace("{members}", String.valueOf(row.memberCount)));
            }
            if (service.isLeaderboardProfileOpenEnabled()) {
                lore.addAll(plugin.lang().getList("gui.leaderboard.entry.profile_lore"));
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING,
                    service.isLeaderboardProfileOpenEnabled() ? "PROFILE" : "NONE");
            meta.getPersistentDataContainer().set(clanIdKey, PersistentDataType.LONG, Long.valueOf(row.clanId));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§r");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack button(Material material, String name, List<String> lore, String action, long clanId) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
            if (clanId > 0) {
                meta.getPersistentDataContainer().set(clanIdKey, PersistentDataType.LONG, Long.valueOf(clanId));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
