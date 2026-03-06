package dev.eministar.starclans.gui;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.model.MemberRole;
import dev.eministar.starclans.service.ClanService;
import dev.eministar.starclans.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
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
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanPublicProfileMenu implements Listener {

    private enum BackTarget {
        MAIN,
        LEADERBOARD_BALANCE,
        LEADERBOARD_MEMBERS,
        CLOSE
    }

    private static final class ViewState {
        long clanId;
        int page;
        BackTarget backTarget;

        ViewState(long clanId, int page, BackTarget backTarget) {
            this.clanId = clanId;
            this.page = page;
            this.backTarget = backTarget;
        }
    }

    private final StarClans plugin;
    private final ClanService service;
    private final ClanRepository repo;
    private final ClanMainMenu mainMenu;

    private final NamespacedKey actionKey;
    private final NamespacedKey clanIdKey;

    private final Map<UUID, ViewState> state = new ConcurrentHashMap<>();
    private final int[] memberSlots = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private ClanLeaderboardMenu leaderboardMenu;
    private String title;

    public ClanPublicProfileMenu(StarClans plugin, ClanService service, ClanRepository repo, ClanMainMenu mainMenu) {
        this.plugin = plugin;
        this.service = service;
        this.repo = repo;
        this.mainMenu = mainMenu;
        this.actionKey = new NamespacedKey(plugin, "sc_profile_action");
        this.clanIdKey = new NamespacedKey(plugin, "sc_profile_clan_id");
        this.title = plugin.lang().get("gui.profile.title");
    }

    public void bindLeaderboardMenu(ClanLeaderboardMenu leaderboardMenu) {
        this.leaderboardMenu = leaderboardMenu;
    }

    public void openFromMain(Player player, long clanId) {
        open(player, clanId, BackTarget.MAIN, true);
    }

    public void openFromCommand(Player player, long clanId, boolean hasBackToMain) {
        open(player, clanId, hasBackToMain ? BackTarget.MAIN : BackTarget.CLOSE, true);
    }

    public void openFromLeaderboard(Player player, long clanId, boolean byBalance) {
        open(player, clanId, byBalance ? BackTarget.LEADERBOARD_BALANCE : BackTarget.LEADERBOARD_MEMBERS, true);
    }

    private void open(Player player, long clanId, BackTarget backTarget, boolean resetPage) {
        if (!service.isProfileEnabled()) {
            player.sendMessage(plugin.lang().prefixed("messages.profile.disabled"));
            return;
        }

        ViewState current = state.get(player.getUniqueId());
        int page = resetPage || current == null || current.clanId != clanId ? 0 : current.page;
        state.put(player.getUniqueId(), new ViewState(clanId, page, backTarget));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClanRepository.ClanPublicProfileRow profile = repo.getClanPublicProfile(clanId);
                if (profile == null) {
                    sync(() -> player.sendMessage(plugin.lang().prefixed("messages.profile.not_found")));
                    return;
                }

                List<ClanRepository.MemberRow> members = repo.listMembers(clanId);
                long viewerClanId = repo.getClanIdByMember(player.getUniqueId());

                sync(() -> openInventory(player, profile, members, viewerClanId));
            } catch (Exception e) {
                LoggerUtil.error("Fehler beim Laden des öffentlichen Clan-Profils für " + player.getName(), e);
                sync(() -> player.sendMessage(plugin.lang().error("messages.load_failed")));
            }
        });
    }

    private void openInventory(Player player, ClanRepository.ClanPublicProfileRow profile,
                               List<ClanRepository.MemberRow> members, long viewerClanId) {
        this.title = plugin.lang().get("gui.profile.title");
        Inventory inv = Bukkit.createInventory(player, 54, title);

        for (int i = 0; i < 54; i++) inv.setItem(i, pane(Material.GRAY_STAINED_GLASS_PANE));
        for (int slot : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53}) {
            inv.setItem(slot, pane(Material.BLACK_STAINED_GLASS_PANE));
        }

        inv.setItem(4, header(profile));
        inv.setItem(10, stats(profile));
        inv.setItem(12, recruiting(profile, viewerClanId));
        inv.setItem(14, cosmetics(profile));
        inv.setItem(16, officers(profile, members));

        if (plugin.getConfig().getBoolean("clan.profile.showMotd", true)) {
            inv.setItem(20, motd(profile));
        }
        if (plugin.getConfig().getBoolean("clan.profile.showBalance", true)) {
            inv.setItem(22, balance(profile));
        }
        if (plugin.getConfig().getBoolean("clan.profile.showTax", true)) {
            inv.setItem(24, tax(profile));
        }

        ViewState current = state.get(player.getUniqueId());
        int page = current == null ? 0 : Math.max(0, current.page);
        int pageSize = Math.max(1, Math.min(memberSlots.length, plugin.getConfig().getInt("clan.profile.memberPreview.pageSize", memberSlots.length)));
        int start = page * pageSize;
        for (int i = 0; i < pageSize && i < memberSlots.length; i++) {
            int index = start + i;
            if (index >= members.size()) break;
            inv.setItem(memberSlots[i], memberHead(members.get(index)));
        }

        inv.setItem(45, button(Material.ARROW, plugin.lang().get("gui.profile.prev.name"),
                plugin.lang().getList("gui.profile.prev.lore"), "PREV", profile.clanId));
        inv.setItem(49, button(Material.BARRIER, plugin.lang().get("gui.profile.back.name"),
                plugin.lang().getList("gui.profile.back.lore"), "BACK", profile.clanId));
        inv.setItem(53, button(Material.ARROW, plugin.lang().get("gui.profile.next.name"),
                plugin.lang().getList("gui.profile.next.lore"), "NEXT", profile.clanId));

        int maxPages = Math.max(1, (int) Math.ceil((double) members.size() / pageSize));
        inv.setItem(47, pageInfo(page + 1, maxPages));

        if (viewerClanId == profile.clanId) {
            inv.setItem(31, button(Material.NETHER_STAR, plugin.lang().get("gui.profile.manage.name"),
                    plugin.lang().getList("gui.profile.manage.lore"), "MANAGE", profile.clanId));
        } else if (service.isRecruitingEnabled()) {
            inv.setItem(31, applyButton(profile, viewerClanId));
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.25f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!title.equals(e.getView().getTitle())) return;

        e.setCancelled(true);

        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        Long clanId = meta.getPersistentDataContainer().get(clanIdKey, PersistentDataType.LONG);
        if (clanId == null) clanId = Long.valueOf(-1L);

        UUID uuid = player.getUniqueId();
        ViewState current = state.get(uuid);

        switch (action) {
            case "BACK" -> handleBack(player, current);
            case "PREV" -> {
                if (current == null) return;
                current.page = Math.max(0, current.page - 1);
                open(player, current.clanId, current.backTarget, false);
            }
            case "NEXT" -> {
                if (current == null) return;
                current.page = current.page + 1;
                open(player, current.clanId, current.backTarget, false);
            }
            case "APPLY" -> {
                player.closeInventory();
                service.requestJoin(player, clanId.longValue(), msg -> player.sendMessage(plugin.lang().prefixedRaw(msg)));
            }
            case "MANAGE" -> {
                player.closeInventory();
                player.performCommand("clan manage");
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        state.remove(e.getPlayer().getUniqueId());
    }

    private void handleBack(Player player, ViewState current) {
        if (current == null) {
            player.closeInventory();
            return;
        }

        switch (current.backTarget) {
            case MAIN -> mainMenu.open(player);
            case LEADERBOARD_BALANCE -> {
                if (leaderboardMenu != null) {
                    leaderboardMenu.open(player, true);
                } else {
                    player.performCommand("clan leaderboard");
                }
            }
            case LEADERBOARD_MEMBERS -> {
                if (leaderboardMenu != null) {
                    leaderboardMenu.open(player, false);
                } else {
                    player.performCommand("clan leaderboard");
                }
            }
            case CLOSE -> player.closeInventory();
        }
    }

    private ItemStack header(ClanRepository.ClanPublicProfileRow profile) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.profile.header.name",
                    "clan", profile.name,
                    "tag", profile.tag));

            List<String> lore = new ArrayList<>(plugin.lang().getList("gui.profile.header.lore",
                    "leader", profile.leaderName.isBlank() ? plugin.lang().get("messages.generic_unknown") : profile.leaderName,
                    "members", Integer.valueOf(profile.memberCount)));

            if (plugin.getConfig().getBoolean("clan.profile.showCreationDate", true)) {
                lore.add(plugin.lang().get("gui.profile.header.created",
                        "date", formatDate(profile.createdAtMillis)));
            }

            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack stats(ClanRepository.ClanPublicProfileRow profile) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.profile.stats.name"));
            meta.setLore(plugin.lang().getList("gui.profile.stats.lore",
                    "leader", profile.leaderName.isBlank() ? plugin.lang().get("messages.generic_unknown") : profile.leaderName,
                    "members", Integer.valueOf(profile.memberCount)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack recruiting(ClanRepository.ClanPublicProfileRow profile, long viewerClanId) {
        boolean open = profile.openInvite;
        Material material = open ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String status = open
                    ? plugin.lang().get("gui.profile.recruiting.open")
                    : plugin.lang().get("gui.profile.recruiting.closed");
            meta.setDisplayName(plugin.lang().get("gui.profile.recruiting.name"));
            meta.setLore(plugin.lang().getList("gui.profile.recruiting.lore",
                    "status", status,
                    "state", viewerClanId == profile.clanId
                            ? plugin.lang().get("gui.profile.recruiting.same_clan")
                            : plugin.lang().get("gui.profile.recruiting.default_state")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack cosmetics(ClanRepository.ClanPublicProfileRow profile) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.profile.cosmetics.name"));
            String styledTag = service.formatClanTag(profile.tagStyle, profile.tag);
            String chatSuffix = service.formatChatSuffix(profile.chatSuffix);
            meta.setLore(plugin.lang().getList("gui.profile.cosmetics.lore",
                    "tag_preview", styledTag.isEmpty() ? plugin.lang().get("gui.profile.cosmetics.none") : styledTag,
                    "chat_suffix", chatSuffix.isEmpty() ? plugin.lang().get("gui.profile.cosmetics.none") : chatSuffix));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack officers(ClanRepository.ClanPublicProfileRow profile, List<ClanRepository.MemberRow> members) {
        ItemStack item = new ItemStack(Material.BELL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.profile.officers.name"));
            List<String> online = new ArrayList<>();
            int limit = Math.max(1, plugin.getConfig().getInt("clan.profile.officerPreviewLimit", 5));
            for (ClanRepository.MemberRow member : members) {
                if (member.role == MemberRole.MEMBER) continue;
                if (Bukkit.getPlayer(member.uuid) == null) continue;
                online.add(member.name);
                if (online.size() >= limit) break;
            }
            meta.setLore(plugin.lang().getList("gui.profile.officers.lore",
                    "count", Integer.valueOf(online.size()),
                    "officers", online.isEmpty() ? plugin.lang().get("gui.profile.officers.none") : String.join(", ", online),
                    "leader", profile.leaderName.isBlank() ? plugin.lang().get("messages.generic_unknown") : profile.leaderName));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack motd(ClanRepository.ClanPublicProfileRow profile) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.profile.motd.name"));
            String motd = profile.motd == null || profile.motd.isBlank()
                    ? plugin.lang().get("gui.profile.motd.none")
                    : profile.motd;
            meta.setLore(plugin.lang().getList("gui.profile.motd.lore", "motd", motd));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack balance(ClanRepository.ClanPublicProfileRow profile) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.profile.balance.name"));
            meta.setLore(plugin.lang().getList("gui.profile.balance.lore", "balance", service.money(profile.balance)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack tax(ClanRepository.ClanPublicProfileRow profile) {
        ItemStack item = new ItemStack(Material.HONEY_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.profile.tax.name"));
            meta.setLore(plugin.lang().getList("gui.profile.tax.lore", "rate", Double.valueOf(profile.taxRate)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack pageInfo(int page, int maxPages) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.profile.page.name"));
            meta.setLore(plugin.lang().getList("gui.profile.page.lore",
                    "page", Integer.valueOf(page),
                    "max", Integer.valueOf(maxPages)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack applyButton(ClanRepository.ClanPublicProfileRow profile, long viewerClanId) {
        boolean available = profile.openInvite && viewerClanId <= 0;
        Material material = available ? Material.EMERALD : Material.GRAY_DYE;
        String action = available ? "APPLY" : "NONE";
        String status;
        if (viewerClanId > 0) {
            status = plugin.lang().get("messages.already_in_clan");
        } else {
            status = profile.openInvite
                    ? plugin.lang().get("gui.profile.apply.open")
                    : plugin.lang().get("gui.profile.apply.closed");
        }
        return button(material,
                plugin.lang().get("gui.profile.apply.name"),
                plugin.lang().getList("gui.profile.apply.lore", "status", status),
                action,
                profile.clanId);
    }

    private ItemStack memberHead(ClanRepository.MemberRow member) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(member.uuid);
            meta.setOwningPlayer(offline);
            meta.setDisplayName(plugin.lang().get("gui.profile.member.name", "name", member.name));
            meta.setLore(plugin.lang().getList("gui.profile.member.lore",
                    "role_color", plugin.lang().roleColor(member.role),
                    "role", plugin.lang().role(member.role),
                    "status", Bukkit.getPlayer(member.uuid) != null
                            ? plugin.lang().get("gui.profile.member.online")
                            : plugin.lang().get("gui.profile.member.offline")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.profile.glass"));
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

    private String formatDate(long millis) {
        String pattern = plugin.getConfig().getString("clan.profile.dateFormat", "dd.MM.yyyy HH:mm");
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(pattern, Locale.GERMANY);
        } catch (Exception ignored) {
            formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY);
        }
        return formatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private void sync(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }
}
