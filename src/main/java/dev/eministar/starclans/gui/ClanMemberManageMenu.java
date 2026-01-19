package dev.eministar.starclans.gui;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public final class ClanMemberManageMenu implements Listener {

    private final StarClans plugin;
    private final ClanService service;
    private final ClanRepository repo;
    private final ClanMembersMenu membersMenu;

    private final Map<UUID, UUID> target = new HashMap<>();
    private final Map<UUID, MemberRole> viewerRole = new HashMap<>();
    private final Map<UUID, MemberRole> targetRole = new HashMap<>();

    public ClanMemberManageMenu(StarClans plugin, ClanService service, ClanRepository repo, ClanMembersMenu membersMenu) {
        this.plugin = plugin;
        this.service = service;
        this.repo = repo;
        this.membersMenu = membersMenu;
    }

    public void open(Player viewer, UUID targetUuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(viewer.getUniqueId());
                if (clanId <= 0) {
                    Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage(StarPrefix.PREFIX + "§cDu bist in keinem Clan."));
                    return;
                }

                long tClan = repo.getClanIdByMember(targetUuid);
                if (tClan != clanId) {
                    Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage(StarPrefix.PREFIX + "§cNicht in deinem Clan."));
                    return;
                }

                MemberRole vRole = repo.getRole(viewer.getUniqueId());
                MemberRole tRole = repo.getRole(targetUuid);

                if (vRole == MemberRole.MEMBER) {
                    Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage(StarPrefix.PREFIX + "§cKeine Rechte."));
                    return;
                }

                if (vRole == MemberRole.OFFICER && tRole != MemberRole.MEMBER) {
                    Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage(StarPrefix.PREFIX + "§cDu kannst nur Member verwalten."));
                    return;
                }

                if (vRole == MemberRole.LEADER && tRole == MemberRole.LEADER) {
                    Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage(StarPrefix.PREFIX + "§cDu kannst den Leader nicht verwalten."));
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> openInv(viewer, targetUuid, vRole, tRole));
            } catch (Exception e) {
                Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage(StarPrefix.PREFIX + "§cFehler. Console."));
                e.printStackTrace();
            }
        });
    }

    private void openInv(Player viewer, UUID t, MemberRole vRole, MemberRole tRole) {
        target.put(viewer.getUniqueId(), t);
        viewerRole.put(viewer.getUniqueId(), vRole);
        targetRole.put(viewer.getUniqueId(), tRole);

        Inventory inv = Bukkit.createInventory(null, 27, "§b§lMember §8| §fManage");

        for (int i = 0; i < 27; i++) inv.setItem(i, glass());
        inv.setItem(13, head(t, tRole));
        inv.setItem(18, back());

        inv.setItem(11, promote());
        inv.setItem(15, demote());
        inv.setItem(22, kick());

        boolean canKick = vRole != MemberRole.MEMBER;
        boolean canPromote = vRole == MemberRole.LEADER;

        if (!canPromote) {
            inv.setItem(11, locked("§cKeine Rechte"));
            inv.setItem(15, locked("§cKeine Rechte"));
        }

        if (!canKick) {
            inv.setItem(22, locked("§cKeine Rechte"));
        }

        viewer.openInventory(inv);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.4f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView() == null) return;
        if (!"§b§lMember §8| §fManage".equals(e.getView().getTitle())) return;

        e.setCancelled(true);

        UUID u = p.getUniqueId();
        UUID t = target.get(u);
        if (t == null) return;
        MemberRole vRole = viewerRole.get(u);
        MemberRole tRole = targetRole.get(u);
        if (vRole == null || tRole == null) return;

        int slot = e.getRawSlot();
        if (slot == 18) {
            membersMenu.open(p);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            return;
        }

        if (slot == 11) {
            if (vRole != MemberRole.LEADER) {
                p.sendMessage(StarPrefix.PREFIX + "§cKeine Rechte.");
                return;
            }
            service.promote(p, t, s -> {
                p.sendMessage(StarPrefix.PREFIX + s);
                open(p, t);
            });
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            return;
        }

        if (slot == 15) {
            if (vRole != MemberRole.LEADER) {
                p.sendMessage(StarPrefix.PREFIX + "§cKeine Rechte.");
                return;
            }
            service.demote(p, t, s -> {
                p.sendMessage(StarPrefix.PREFIX + s);
                open(p, t);
            });
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            return;
        }

        if (slot == 22) {
            if (vRole == MemberRole.MEMBER) {
                p.sendMessage(StarPrefix.PREFIX + "§cKeine Rechte.");
                return;
            }
            if (vRole == MemberRole.OFFICER && tRole != MemberRole.MEMBER) {
                p.sendMessage(StarPrefix.PREFIX + "§cDu kannst nur Member kicken.");
                return;
            }
            service.kick(p, t, s -> {
                p.sendMessage(StarPrefix.PREFIX + s);
                membersMenu.open(p);
            });
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f);
        }
    }

    private ItemStack head(UUID uuid, MemberRole role) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) it.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        meta.setDisplayName("§f" + Bukkit.getOfflinePlayer(uuid).getName());
        meta.setLore(Arrays.asList(
                "§8",
                "§7Rang: " + roleColor(role) + role.name(),
                "§8"
        ));
        it.setItemMeta(meta);
        return it;
    }

    private String roleColor(MemberRole r) {
        if (r == MemberRole.LEADER) return "§6";
        if (r == MemberRole.OFFICER) return "§b";
        return "§7";
    }

    private ItemStack promote() {
        ItemStack it = new ItemStack(Material.EMERALD);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§aPromote §8→ §bOFFICER");
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack demote() {
        ItemStack it = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§eDemote §8→ §7MEMBER");
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack kick() {
        ItemStack it = new ItemStack(Material.REDSTONE);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§cKick");
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack locked(String name) {
        ItemStack it = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
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

    private ItemStack glass() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§8 ");
        it.setItemMeta(meta);
        return it;
    }
}
