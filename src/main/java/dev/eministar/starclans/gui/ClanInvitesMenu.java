package dev.eministar.starclans.gui;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.model.MemberRole;
import dev.eministar.starclans.utils.LoggerUtil;
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

import java.util.List;
import java.util.UUID;

public final class ClanInvitesMenu implements Listener {

    private static final class InviteDisplay {
        final long id;
        final String clanName;
        final String clanTag;
        final UUID targetUuid;
        final String inviterName;
        final MemberRole inviterRole;
        final boolean approvalView;
        final boolean selfRequest;
        final String expiresIn;

        InviteDisplay(long id, String clanName, String clanTag, UUID targetUuid,
                      String inviterName, MemberRole inviterRole, boolean approvalView, boolean selfRequest,
                      String expiresIn) {
            this.id = id;
            this.clanName = clanName;
            this.clanTag = clanTag;
            this.targetUuid = targetUuid;
            this.inviterName = inviterName;
            this.inviterRole = inviterRole == null ? MemberRole.MEMBER : inviterRole;
            this.approvalView = approvalView;
            this.selfRequest = selfRequest;
            this.expiresIn = expiresIn == null ? "-" : expiresIn;
        }
    }

    private final StarClans plugin;
    private final ClanRepository repo;

    private final NamespacedKey actionKey;
    private final NamespacedKey inviteKey;

    private String title;

    public ClanInvitesMenu(StarClans plugin, ClanRepository repo) {
        this.plugin = plugin;
        this.repo = repo;
        this.actionKey = new NamespacedKey(plugin, "sc_action");
        this.inviteKey = new NamespacedKey(plugin, "sc_invite");
        this.title = plugin.lang().get("gui.invites.title");
    }

    public void open(Player p) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                try {
                    long clanId = repo.getClanIdByMember(p.getUniqueId());
                    MemberRole role = clanId > 0 ? repo.getRole(p.getUniqueId()) : MemberRole.MEMBER;
                    boolean approvals = clanId > 0 && role != MemberRole.MEMBER;
                    List<ClanRepository.InviteRow> invites = approvals
                            ? repo.getPendingApprovals(clanId)
                            : repo.getInvites(p.getUniqueId());

                    List<InviteDisplay> display = new java.util.ArrayList<>();
                    for (ClanRepository.InviteRow row : invites) {
                        boolean selfRequest = row.targetUuid != null && row.targetUuid.equals(row.inviterUuid);
                        String inviterName;
                        MemberRole inviterRole;

                        inviterName = repo.getMemberName(row.inviterUuid);
                        inviterRole = selfRequest ? MemberRole.MEMBER : repo.getRole(row.inviterUuid);

                        String expiresIn = formatRemaining(row.expiresAtMillis - System.currentTimeMillis());
                        display.add(new InviteDisplay(
                                row.id, row.clanName, row.clanTag, row.targetUuid,
                                inviterName, inviterRole, approvals, selfRequest, expiresIn
                        ));
                    }
                    Bukkit.getScheduler().runTask(plugin, new Runnable() {
                        @Override
                        public void run() {
                            openInv(p, display);
                        }
                    });
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        p.sendMessage(plugin.lang().error("messages.load_failed_short"));
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    });
                    LoggerUtil.error("Fehler beim Laden der Einladungen für " + p.getName(), e);
                }
            }
        });
    }

    private void openInv(Player p, List<InviteDisplay> invites) {
        this.title = plugin.lang().get("gui.invites.title");
        Inventory inv = Bukkit.createInventory(p, 54, title);

        for (int i = 0; i < 54; i++) inv.setItem(i, pane(Material.GRAY_STAINED_GLASS_PANE));
        for (int i = 0; i <= 8; i++) inv.setItem(i, pane(Material.BLACK_STAINED_GLASS_PANE));
        inv.setItem(9, pane(Material.BLACK_STAINED_GLASS_PANE));
        inv.setItem(17, pane(Material.BLACK_STAINED_GLASS_PANE));
        inv.setItem(18, pane(Material.BLACK_STAINED_GLASS_PANE));
        inv.setItem(26, pane(Material.BLACK_STAINED_GLASS_PANE));
        inv.setItem(27, pane(Material.BLACK_STAINED_GLASS_PANE));
        inv.setItem(35, pane(Material.BLACK_STAINED_GLASS_PANE));
        for (int i = 45; i <= 53; i++) inv.setItem(i, pane(Material.BLACK_STAINED_GLASS_PANE));

        inv.setItem(49, button(Material.BARRIER, plugin.lang().get("gui.invites.back.name"),
                plugin.lang().getList("gui.invites.back.lore"), "BACK", false, -1));

        int slot = 10;
        for (InviteDisplay row : invites) {
            if (slot >= 44) break;
            if (slot == 17 || slot == 26 || slot == 35) slot++;

            String targetName = row.targetUuid == null ? plugin.lang().get("messages.generic_unknown") : Bukkit.getOfflinePlayer(row.targetUuid).getName();
            if (targetName == null) targetName = plugin.lang().get("messages.generic_unknown");

            ItemStack it;
            String inviterName;
            if (row.selfRequest) {
                inviterName = targetName;
            } else {
                inviterName = row.inviterName == null ? plugin.lang().get("messages.generic_unknown") : row.inviterName;
            }
            if (row.approvalView) {
                it = button(Material.PAPER,
                        plugin.lang().get("gui.invites.request.name", "player", targetName),
                        plugin.lang().getList("gui.invites.request.lore",
                                "inviter", inviterName,
                                "role_color", roleColor(row.inviterRole),
                                "role", plugin.lang().role(row.inviterRole),
                                "expires", row.expiresIn),
                        "INVITE", true, row.id);
            } else {
                it = button(Material.PAPER,
                        plugin.lang().get("gui.invites.invite.name", "clan", row.clanName, "tag", row.clanTag),
                        plugin.lang().getList("gui.invites.invite.lore",
                                "inviter", inviterName,
                                "role_color", roleColor(row.inviterRole),
                                "role", plugin.lang().role(row.inviterRole),
                                "expires", row.expiresIn),
                        "INVITE", true, row.id);
            }

            inv.setItem(slot, it);
            slot++;
        }

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.25f);
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

        if (action.equals("BACK")) {
            p.closeInventory();
            p.performCommand("clan");
            return;
        }

        if (action.equals("INVITE")) {
            Long inviteId = meta.getPersistentDataContainer().get(inviteKey, PersistentDataType.LONG);
            if (inviteId == null) return;

            p.closeInventory();

            if (e.isShiftClick()) {
                p.performCommand("clan deny " + inviteId);
            } else {
                p.performCommand("clan accept " + inviteId);
            }
        }
    }

    private ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§r");
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack button(Material m, String name, java.util.List<String> lore, String action, boolean glow, long inviteId) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        if (inviteId > 0) meta.getPersistentDataContainer().set(inviteKey, PersistentDataType.LONG, inviteId);
        if (glow) meta.addEnchant(Enchantment.DENSITY, 1, true);
        it.setItemMeta(meta);
        return it;
    }

    private String roleColor(MemberRole r) {
        return plugin.lang().roleColor(r);
    }

    private String formatRemaining(long millis) {
        long s = Math.max(0L, millis / 1000L);
        long min = s / 60L;
        long sec = s % 60L;
        if (min > 0) return min + "m " + sec + "s";
        return sec + "s";
    }
}
