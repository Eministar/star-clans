package dev.eministar.starclans.gui;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.model.ClanProfile;
import dev.eministar.starclans.model.MemberRole;
import dev.eministar.starclans.service.ClanService;
import dev.eministar.starclans.utils.StarPrefix;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public final class ClanMembersMenu implements Listener {

    private final StarClans plugin;
    private final ClanService service;
    private final ClanRepository repo;
    private final ClanMainMenu mainMenu;

    private final Map<UUID, Integer> page = new HashMap<>();
    private final Map<UUID, Long> clanView = new HashMap<>();
    private final Map<UUID, List<ClanRepository.MemberRow>> data = new HashMap<>();

    private ClanMemberManageMenu manageMenu;

    public ClanMembersMenu(StarClans plugin, ClanService service, ClanRepository repo, ClanMainMenu mainMenu) {
        this.plugin = plugin;
        this.service = service;
        this.repo = repo;
        this.mainMenu = mainMenu;
    }

    public void bindManageMenu(ClanMemberManageMenu manageMenu) {
        this.manageMenu = manageMenu;
    }

    public void open(Player p) {
        service.loadProfileAsync(p.getUniqueId(), prof -> {
            if (!prof.inClan) {
                p.sendMessage(StarPrefix.PREFIX + "§cDu bist in keinem Clan.");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return;
            }
            clanView.put(p.getUniqueId(), prof.clanId);
            page.putIfAbsent(p.getUniqueId(), 0);
            loadMembersAndOpen(p, prof);
        });
    }

    private void loadMembersAndOpen(Player p, ClanProfile prof) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<ClanRepository.MemberRow> list = repo.listMembers(prof.clanId);
                data.put(p.getUniqueId(), list);
                Bukkit.getScheduler().runTask(plugin, () -> openInventory(p, prof, list));
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(StarPrefix.PREFIX + "§cFehler beim Laden. Console."));
                e.printStackTrace();
            }
        });
    }

    private void openInventory(Player p, ClanProfile prof, List<ClanRepository.MemberRow> list) {
        Inventory inv = Bukkit.createInventory(null, 54, "§b§lClan §8| §fMembers");

        for (int i = 0; i < 54; i++) inv.setItem(i, glass());
        inv.setItem(49, back());
        inv.setItem(45, prev());
        inv.setItem(53, next());

        int pg = page.getOrDefault(p.getUniqueId(), 0);
        int perPage = 28;
        int start = pg * perPage;

        List<Integer> slots = Arrays.asList(
                10,11,12,13,14,15,16,
                19,20,21,22,23,24,25,
                28,29,30,31,32,33,34,
                37,38,39,40,41,42,43
        );

        for (int i = 0; i < slots.size(); i++) {
            int idx = start + i;
            if (idx >= list.size()) break;
            ClanRepository.MemberRow m = list.get(idx);
            inv.setItem(slots.get(i), memberHead(m));
        }

        inv.setItem(4, header(prof, list.size(), pg));

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.4f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView() == null) return;
        if (!"§b§lClan §8| §fMembers".equals(e.getView().getTitle())) return;

        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || it.getType() == Material.AIR) return;

        int slot = e.getRawSlot();
        UUID u = p.getUniqueId();

        if (slot == 49) {
            mainMenu.open(p);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            return;
        }
        if (slot == 45) {
            int pg = page.getOrDefault(u, 0);
            if (pg > 0) page.put(u, pg - 1);
            open(p);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            return;
        }
        if (slot == 53) {
            int pg = page.getOrDefault(u, 0);
            page.put(u, pg + 1);
            open(p);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
            return;
        }

        if (it.getType() == Material.PLAYER_HEAD && manageMenu != null) {
            SkullMeta sm = (SkullMeta) it.getItemMeta();
            if (sm == null) return;
            String dn = sm.getDisplayName();
            if (dn == null || dn.isEmpty()) return;

            List<ClanRepository.MemberRow> list = data.get(u);
            if (list == null) return;

            ClanRepository.MemberRow picked = null;
            for (ClanRepository.MemberRow row : list) {
                if (("§f" + row.name).equals(dn)) {
                    picked = row;
                    break;
                }
            }
            if (picked == null) return;

            manageMenu.open(p, picked.uuid);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
        }
    }

    private ItemStack memberHead(ClanRepository.MemberRow m) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) it.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(m.uuid));
        meta.setDisplayName("§f" + m.name);

        List<String> lore = new ArrayList<>();
        lore.add("§8");
        lore.add("§7Rang: " + roleColor(m.role) + m.role.name());
        boolean online = Bukkit.getPlayer(m.uuid) != null;
        lore.add("§7Status: " + (online ? "§aOnline" : "§7Offline"));
        lore.add("§8");
        lore.add("§bKlick §7für Management");
        meta.setLore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    private String roleColor(MemberRole r) {
        if (r == MemberRole.LEADER) return "§6";
        if (r == MemberRole.OFFICER) return "§b";
        return "§7";
    }

    private ItemStack header(ClanProfile prof, int members, int pg) {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§b§l" + prof.clanName + " §8[§f" + prof.clanTag + "§8]");
        meta.setLore(Arrays.asList(
                "§8",
                "§7Mitglieder: §f" + members,
                "§7Seite: §f" + (pg + 1),
                "§8",
                "§7Tipp: §fLeader/Officer können verwalten"
        ));
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack glass() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§8 ");
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack back() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§cZurück");
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack prev() {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§7Vorherige Seite");
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack next() {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§7Nächste Seite");
        it.setItemMeta(meta);
        return it;
    }
}
