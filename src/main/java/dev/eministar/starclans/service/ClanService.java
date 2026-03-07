package dev.eministar.starclans.service;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.model.BankTransactionType;
import dev.eministar.starclans.model.ClanProfile;
import dev.eministar.starclans.model.MemberRole;
import dev.eministar.starclans.utils.LoggerUtil;
import dev.eministar.starclans.vault.VaultHook;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class ClanService {

    public static final class CreateState {
        public String name = "";
        public String tag = "";
        public String tagStyle = "";
    }

    private final StarClans plugin;
    private final ClanRepository repo;

    private final Map<UUID, ClanProfile> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> clanChat = new ConcurrentHashMap<>();
    private final Map<UUID, Long> inviteCooldownUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> joinCooldownUntil = new ConcurrentHashMap<>();

    private final Pattern allowed = Pattern.compile("^[A-Za-z0-9_]+$");

    public ClanService(StarClans plugin, ClanRepository repo) {
        this.plugin = plugin;
        this.repo = repo;
    }

    public boolean isBankEnabled() {
        return plugin.getConfig().getBoolean("clan.bank.enabled", true);
    }

    public boolean isBankHistoryEnabled() {
        return isBankEnabled() && plugin.getConfig().getBoolean("clan.bank.history.enabled", true);
    }

    public boolean isProfileEnabled() {
        return plugin.getConfig().getBoolean("clan.profile.enabled", true);
    }

    public boolean isRecruitingEnabled() {
        return isProfileEnabled() && plugin.getConfig().getBoolean("clan.profile.recruiting.enabled", true);
    }

    public boolean isLeaderboardProfileOpenEnabled() {
        return isProfileEnabled() && plugin.getConfig().getBoolean("clan.profile.openFromLeaderboard", true);
    }

    public boolean isChatSuffixEnabled() {
        return plugin.getConfig().getBoolean("clan.chat.suffix.enabled", true);
    }

    public boolean isChatSuffixVisibleInGlobalChat() {
        return isChatSuffixEnabled() && plugin.getConfig().getBoolean("clan.chat.suffix.showInGlobalChat", true);
    }

    public boolean isChatSuffixVisibleInClanChat() {
        return isChatSuffixEnabled() && plugin.getConfig().getBoolean("clan.chat.suffix.showInClanChat", true);
    }

    public int bankHistoryPageSize() {
        return Math.max(1, Math.min(14, plugin.getConfig().getInt("clan.bank.history.pageSize", 14)));
    }

    public int bankHistoryRetention() {
        return Math.max(0, plugin.getConfig().getInt("clan.bank.history.maxEntriesPerClan", 200));
    }

    public int chatSuffixMaxLength() {
        return Math.max(1, plugin.getConfig().getInt("clan.chat.suffix.maxVisibleLength", 24));
    }

    public List<Double> bankQuickAmounts(boolean withdraw) {
        String path = withdraw ? "clan.bank.quickAmounts.withdraw" : "clan.bank.quickAmounts.deposit";
        List<Double> values = new ArrayList<>();
        for (double value : plugin.getConfig().getDoubleList(path)) {
            if (value > 0) {
                values.add(Double.valueOf(value));
            }
        }
        return values;
    }

    public boolean allowBankCustomInput() {
        return plugin.getConfig().getBoolean("clan.bank.quickAmounts.allowCustomInput", true);
    }

    public boolean allowDepositAllButton() {
        return plugin.getConfig().getBoolean("clan.bank.quickAmounts.allowDepositAll", true);
    }

    public boolean allowWithdrawAllButton() {
        return plugin.getConfig().getBoolean("clan.bank.quickAmounts.allowWithdrawAll", true);
    }

    public MemberRole configuredRole(String path, MemberRole fallback) {
        String raw = plugin.getConfig().getString(path, fallback.name());
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return MemberRole.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public String formatClanTag(String tagStyle, String tag) {
        String style = tagStyle == null || tagStyle.isEmpty() ? "§b" : tagStyle;
        String safeTag = tag == null ? "" : tag;
        if (safeTag.isEmpty()) {
            return "";
        }
        return plugin.lang().get("messages.chat.tag_suffix", "style", style, "tag", safeTag);
    }

    public String formatChatSuffix(String chatSuffix) {
        if (!isChatSuffixEnabled()) return "";
        String safe = chatSuffix == null ? "" : chatSuffix.trim();
        if (safe.isEmpty()) return "";
        return plugin.lang().get("messages.chat.chat_suffix_format", "suffix", safe);
    }

    public String sanitizeChatSuffixInput(String input) {
        String raw = input == null ? "" : input.trim();
        if (raw.isEmpty()) {
            return "";
        }

        boolean allowColors = plugin.getConfig().getBoolean("clan.chat.suffix.allowColorCodes", true);
        boolean allowHex = plugin.getConfig().getBoolean("clan.chat.suffix.allowHexColors", true);

        String normalized;
        if (!allowColors) {
            normalized = ChatColor.stripColor(plugin.lang().colorize(raw));
        } else {
            String withoutHex = allowHex ? raw : raw.replaceAll("(?i)&\\#[0-9A-F]{6}", "");
            normalized = plugin.lang().colorize(withoutHex);
        }

        String visible = ChatColor.stripColor(normalized);
        if (visible == null) {
            visible = normalized;
        }

        if (visible.length() > chatSuffixMaxLength()) {
            return null;
        }

        return normalized.trim();
    }

    public void clearCache() {
        cache.clear();
        inviteCooldownUntil.clear();
        joinCooldownUntil.clear();
    }

    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }

    public ClanProfile getCached(UUID uuid) {
        return cache.get(uuid);
    }

    public boolean isClanChat(UUID uuid) {
        Boolean v = clanChat.get(uuid);
        return v != null && v;
    }

    public boolean toggleClanChat(UUID uuid) {
        boolean next = !isClanChat(uuid);
        clanChat.put(uuid, Boolean.valueOf(next));
        return next;
    }

    public void loadProfileAsync(UUID uuid, Consumer<ClanProfile> cb) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClanProfile p = repo.getFullProfile(uuid);
                cache.put(uuid, p);
                syncProfile(cb, p);
            } catch (Exception e) {
                syncProfile(cb, ClanProfile.none(0));
                LoggerUtil.error("Fehler beim Laden des Spielerprofils!", e);
            }
        });
    }

    public void tryCreateClan(Player player, String name, String tag, Consumer<String> doneMsg) {
        tryCreateClan(player, name, tag, "", doneMsg);
    }

    public void tryCreateClan(Player player, String name, String tag, String tagStyle, Consumer<String> doneMsg) {
        String n = name == null ? "" : name.trim();
        String t = tag == null ? "" : tag.trim().toUpperCase(Locale.ROOT);
        String style = tagStyle == null ? "" : tagStyle.trim();

        int minN = plugin.getConfig().getInt("clan.creation.minNameLen", 3);
        int maxN = plugin.getConfig().getInt("clan.creation.maxNameLen", 16);
        int minT = plugin.getConfig().getInt("clan.creation.minTagLen", 2);
        int maxT = plugin.getConfig().getInt("clan.creation.maxTagLen", 5);

        if (n.length() < minN || n.length() > maxN) {
            doneMsg.accept(plugin.lang().get("messages.create.name_len", "min", Integer.valueOf(minN), "max", Integer.valueOf(maxN)));
            return;
        }
        if (t.length() < minT || t.length() > maxT) {
            doneMsg.accept(plugin.lang().get("messages.create.tag_len", "min", Integer.valueOf(minT), "max", Integer.valueOf(maxT)));
            return;
        }
        if (!allowed.matcher(n).matches() || !allowed.matcher(t).matches()) {
            doneMsg.accept(plugin.lang().get("messages.create.invalid_chars"));
            return;
        }

        double cost = plugin.getConfig().getDouble("clan.creation.cost", 0.0);
        if (cost > 0.0 && !VaultHook.hasEconomy()) {
            doneMsg.accept(plugin.lang().get("messages.create.vault_missing"));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long currentClan = repo.getClanIdByMember(player.getUniqueId());
                if (currentClan > 0) {
                    syncMsg(doneMsg, plugin.lang().get("messages.create.already_in_clan"));
                    return;
                }

                if (repo.nameExists(n)) {
                    syncMsg(doneMsg, plugin.lang().get("messages.create.name_taken"));
                    return;
                }
                if (repo.tagExists(t)) {
                    syncMsg(doneMsg, plugin.lang().get("messages.create.tag_taken"));
                    return;
                }

                if (cost > 0.0) {
                    boolean ok = withdrawOnMain(player, cost).get(3, TimeUnit.SECONDS);
                    if (!ok) {
                        syncMsg(doneMsg, plugin.lang().get("messages.create.not_enough_money", "cost", money(cost)));
                        return;
                    }
                }

                long clanId = repo.createClan(n, t, player.getUniqueId(), player.getName());

                if (!style.isEmpty()) {
                    repo.setTagStyle(clanId, style);
                }

                invalidate(player.getUniqueId());

                sync(() -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.2f);
                    doneMsg.accept(plugin.lang().get("messages.create.success", "name", n, "tag", t));
                });

                LinkedHashMap<String, String> webhook = webhookClanData(clanId);
                webhook.put("actor", player.getName());
                sendWebhook("create", webhook);
                maybeCheckLeaderboardTopChange();
            } catch (Exception e) {
                syncMsg(doneMsg, plugin.lang().get("messages.create.fail"));
                LoggerUtil.error("Fehler bei der Clan-Erstellung für " + player.getName(), e);
            }
        });
    }

    public void invite(Player inviter, Player target, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long inviteCooldown = Math.max(0, plugin.getConfig().getLong("clan.invite.cooldownSeconds", 20));
                long wait = cooldownLeft(inviteCooldownUntil, inviter.getUniqueId());
                if (wait > 0) {
                    syncMsg(msg, plugin.lang().get("messages.invite.cooldown", "seconds", Long.valueOf(wait)));
                    return;
                }

                long clanId = repo.getClanIdByMember(inviter.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_clan"));
                    return;
                }

                MemberRole r = repo.getRole(inviter.getUniqueId());

                if (repo.getClanIdByMember(target.getUniqueId()) > 0) {
                    syncMsg(msg, plugin.lang().get("messages.already_in_clan_other"));
                    return;
                }

                if (repo.hasActiveInviteForTargetClan(target.getUniqueId(), clanId)) {
                    syncMsg(msg, plugin.lang().get("messages.invite.already_pending", "player", target.getName()));
                    return;
                }

                int minutes = plugin.getConfig().getInt("clan.invite.expireMinutes", 60);
                boolean requiresApproval = r == MemberRole.MEMBER;
                long inviteId = repo.createInvite(clanId, target.getUniqueId(), inviter.getUniqueId(), minutes, requiresApproval);
                if (inviteId > 0 && inviteCooldown > 0) {
                    setCooldown(inviteCooldownUntil, inviter.getUniqueId(), inviteCooldown);
                }

                sync(() -> {
                    inviter.playSound(inviter.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
                    msg.accept(plugin.lang().get("messages.invite.sent", "player", target.getName()));
                    if (requiresApproval) {
                        inviter.sendMessage(plugin.lang().prefixed("messages.invite.approval_inviter"));
                    }
                    target.sendMessage(plugin.lang().prefixed("messages.invite.target_invited"));
                    if (requiresApproval) {
                        target.sendMessage(plugin.lang().prefixed("messages.invite.approval_target"));
                    }
                    if (inviteId > 0) {
                        TextComponent accept = new TextComponent(plugin.lang().get("messages.invite.accept_button"));
                        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan accept " + inviteId));
                        TextComponent deny = new TextComponent(plugin.lang().get("messages.invite.deny_button"));
                        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan deny " + inviteId));
                        target.spigot().sendMessage(new ComponentBuilder(plugin.lang().prefixed("messages.invite.response_prefix")).append(accept).append(deny).create());
                    } else {
                        target.sendMessage(plugin.lang().prefixed("messages.invite.open_invites_hint"));
                    }
                    target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);
                });
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.invite.fail"));
                LoggerUtil.error("Fehler beim Senden einer Einladung von " + inviter.getName(), e);
            }
        });
    }

    public void requestJoin(Player player, String clanInput, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClanRepository.ClanLookupRow clan = repo.findClanByNameOrTag(clanInput);
                if (clan == null) {
                    syncMsg(msg, plugin.lang().get("messages.join.clan_not_found"));
                    return;
                }
                requestJoinResolved(player, clan, msg);
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.join.fail"));
                LoggerUtil.error("Fehler bei der Beitrittsanfrage von " + player.getName(), e);
            }
        });
    }

    public void requestJoin(Player player, long clanId, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClanRepository.ClanLookupRow clan = repo.getClanLookup(clanId);
                if (clan == null) {
                    syncMsg(msg, plugin.lang().get("messages.join.clan_not_found"));
                    return;
                }
                requestJoinResolved(player, clan, msg);
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.join.fail"));
                LoggerUtil.error("Fehler bei der Beitrittsanfrage von " + player.getName(), e);
            }
        });
    }

    private void requestJoinResolved(Player player, ClanRepository.ClanLookupRow clan, Consumer<String> msg) throws Exception {
        if (repo.getClanIdByMember(player.getUniqueId()) > 0) {
            syncMsg(msg, plugin.lang().get("messages.already_in_clan"));
            return;
        }

        long requestCooldown = Math.max(0, plugin.getConfig().getLong("clan.joinRequest.cooldownSeconds", 20));
        long wait = cooldownLeft(joinCooldownUntil, player.getUniqueId());
        if (wait > 0) {
            syncMsg(msg, plugin.lang().get("messages.join.cooldown", "seconds", Long.valueOf(wait)));
            return;
        }

        ClanRepository.ClanSettingsRow settings = repo.getSettings(clan.clanId);
        if (!settings.openInvite) {
            syncMsg(msg, plugin.lang().get("messages.join.open_invite_off"));
            return;
        }

        if (repo.hasActiveInviteForTargetClan(player.getUniqueId(), clan.clanId)) {
            syncMsg(msg, plugin.lang().get("messages.join.already_pending"));
            return;
        }

        int minutes = plugin.getConfig().getInt("clan.invite.expireMinutes", 60);
        long requestId = repo.createJoinRequest(clan.clanId, player.getUniqueId(), minutes);
        if (requestId <= 0) {
            syncMsg(msg, plugin.lang().get("messages.join.fail"));
            return;
        }

        if (requestCooldown > 0) {
            setCooldown(joinCooldownUntil, player.getUniqueId(), requestCooldown);
        }

        ClanRepository.InviteRow inv = repo.getInviteForApproval(requestId, clan.clanId);

        sync(() -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.3f);
            msg.accept(plugin.lang().get("messages.join.request_sent",
                    "clan", clan.clanName,
                    "tag", clan.clanTag));
        });

        if (inv != null) {
            notifyInviteApproval(inv);
        }
    }

    public void acceptInvite(Player player, long inviteId, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClanRepository.InviteRow inv = repo.getInviteForTarget(inviteId, player.getUniqueId());
                if (inv != null) {
                    if (repo.getClanIdByMember(player.getUniqueId()) > 0) {
                        syncMsg(msg, plugin.lang().get("messages.already_in_clan"));
                        return;
                    }

                    if (inv.requiresApproval) {
                        repo.setInvitePendingApproval(inviteId, true);
                        sync(() -> {
                            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.25f);
                            msg.accept(plugin.lang().get("messages.invite.request_saved"));
                        });

                        notifyInviteApproval(inv);
                        return;
                    }

                    repo.joinClan(inv.clanId, player.getUniqueId(), player.getName());
                    repo.deleteInvite(inviteId);
                    invalidate(player.getUniqueId());

                    sync(() -> {
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.25f);
                        msg.accept(plugin.lang().get("messages.invite.joined",
                                "clan", inv.clanName,
                                "tag", inv.clanTag));
                    });
                    notifyClan(inv.clanId, plugin.lang().get("messages.broadcasts.join", "player", player.getName()), Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
                    LinkedHashMap<String, String> webhook = webhookClanData(inv.clanId);
                    webhook.put("actor", player.getName());
                    sendWebhook("join", webhook);
                    maybeCheckLeaderboardTopChange();
                    return;
                }

                long clanId = repo.getClanIdByMember(player.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, plugin.lang().get("messages.invite.not_found"));
                    return;
                }

                MemberRole role = repo.getRole(player.getUniqueId());
                if (role == MemberRole.MEMBER) {
                    syncMsg(msg, plugin.lang().get("messages.no_rights"));
                    return;
                }

                ClanRepository.InviteRow pending = repo.getInviteForApproval(inviteId, clanId);
                if (pending == null) {
                    syncMsg(msg, plugin.lang().get("messages.invite.not_found"));
                    return;
                }

                if (repo.getClanIdByMember(pending.targetUuid) > 0) {
                    repo.deleteInvite(inviteId);
                    syncMsg(msg, plugin.lang().get("messages.already_in_clan_other"));
                    return;
                }

                String targetName = Bukkit.getOfflinePlayer(pending.targetUuid).getName();
                repo.joinClan(pending.clanId, pending.targetUuid,
                        targetName == null ? plugin.lang().get("messages.generic_unknown") : targetName);
                repo.deleteInvite(inviteId);
                invalidate(pending.targetUuid);

                sync(() -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.25f);
                    msg.accept(plugin.lang().get("messages.invite.approval_accepted"));
                    Player t = Bukkit.getPlayer(pending.targetUuid);
                    if (t != null) {
                        t.sendMessage(plugin.lang().prefixed("messages.invite.target_approved"));
                        t.playSound(t.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.2f);
                    }
                });
                notifyClan(pending.clanId,
                        plugin.lang().get("messages.broadcasts.join", "player", targetName == null ? plugin.lang().get("messages.generic_new_member") : targetName),
                        Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
                String joinedName = targetName == null ? plugin.lang().get("messages.generic_new_member") : targetName;
                LinkedHashMap<String, String> webhook = webhookClanData(pending.clanId);
                webhook.put("actor", joinedName);
                sendWebhook("join", webhook);
                maybeCheckLeaderboardTopChange();
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.invite.accept_fail"));
                LoggerUtil.error("Fehler beim Annehmen einer Einladung durch " + player.getName(), e);
            }
        });
    }

    public void denyInvite(Player player, long inviteId, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClanRepository.InviteRow inv = repo.getInviteForTarget(inviteId, player.getUniqueId());
                if (inv != null) {
                    repo.deleteInvite(inviteId);
                    invalidate(player.getUniqueId());
                    syncMsg(msg, plugin.lang().get("messages.invite.denied"));
                    return;
                }

                long clanId = repo.getClanIdByMember(player.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, plugin.lang().get("messages.invite.not_found"));
                    return;
                }

                MemberRole role = repo.getRole(player.getUniqueId());
                if (role == MemberRole.MEMBER) {
                    syncMsg(msg, plugin.lang().get("messages.no_rights"));
                    return;
                }

                ClanRepository.InviteRow pending = repo.getInviteForApproval(inviteId, clanId);
                if (pending == null) {
                    syncMsg(msg, plugin.lang().get("messages.invite.not_found"));
                    return;
                }

                repo.deleteInvite(inviteId);
                sync(() -> {
                    msg.accept(plugin.lang().get("messages.invite.denied"));
                    Player t = Bukkit.getPlayer(pending.targetUuid);
                    if (t != null) {
                        t.sendMessage(plugin.lang().prefixed("messages.invite.target_denied"));
                    }
                });
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.invite.deny_fail"));
                LoggerUtil.error("Fehler beim Ablehnen einer Einladung durch " + player.getName(), e);
            }
        });
    }

    public void leave(Player player, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(player.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_clan"));
                    return;
                }

                MemberRole role = repo.getRole(player.getUniqueId());
                if (role == MemberRole.LEADER) {
                    syncMsg(msg, plugin.lang().get("messages.leave.leader_cannot"));
                    return;
                }

                repo.removeMember(player.getUniqueId());
                invalidate(player.getUniqueId());

                sync(() -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1.2f);
                    msg.accept(plugin.lang().get("messages.leave.success"));
                });
                notifyClan(clanId, plugin.lang().get("messages.broadcasts.leave", "player", player.getName()), Sound.ENTITY_VILLAGER_NO);
                LinkedHashMap<String, String> webhook = webhookClanData(clanId);
                webhook.put("actor", player.getName());
                sendWebhook("leave", webhook);
                maybeCheckLeaderboardTopChange();
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.leave.fail"));
                LoggerUtil.error("Fehler beim Verlassen des Clans durch " + player.getName(), e);
            }
        });
    }

    public void disband(Player player, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(player.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_clan"));
                    return;
                }

                MemberRole role = repo.getRole(player.getUniqueId());
                if (role != MemberRole.LEADER) {
                    syncMsg(msg, plugin.lang().get("messages.disband.only_leader"));
                    return;
                }

                java.util.List<ClanRepository.MemberRow> members = repo.listMembers(clanId);
                LinkedHashMap<String, String> webhook = webhookClanData(clanId);
                webhook.put("actor", player.getName());
                repo.disband(clanId);
                clearCache();

                sync(() -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.6f, 1.0f);
                    msg.accept(plugin.lang().get("messages.disband.success"));
                });
                notifyMembers(members, plugin.lang().get("messages.broadcasts.disband"), Sound.ENTITY_WITHER_DEATH);
                sendWebhook("disband", webhook);
                maybeCheckLeaderboardTopChange();
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.disband.fail"));
                LoggerUtil.error("Fehler beim Auflösen des Clans durch " + player.getName(), e);
            }
        });
    }

    public void setMotd(Player actor, String motd, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(actor.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_clan"));
                    return;
                }

                MemberRole r = repo.getRole(actor.getUniqueId());
                if (r == MemberRole.MEMBER) {
                    syncMsg(msg, plugin.lang().get("messages.no_rights"));
                    return;
                }

                repo.setMotd(clanId, motd);
                invalidate(actor.getUniqueId());

                sync(() -> {
                    actor.playSound(actor.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
                    msg.accept(plugin.lang().get("messages.motd.saved"));
                });
                notifyClan(clanId, plugin.lang().get("messages.motd.changed_broadcast", "player", actor.getName()), Sound.UI_BUTTON_CLICK);
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.motd.fail"));
                LoggerUtil.error("Fehler beim Setzen der MOTD durch " + actor.getName(), e);
            }
        });
    }

    public void toggleOpenInvite(Player actor, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(actor.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_clan"));
                    return;
                }

                MemberRole r = repo.getRole(actor.getUniqueId());
                if (r == MemberRole.MEMBER) {
                    syncMsg(msg, plugin.lang().get("messages.no_rights"));
                    return;
                }

                boolean now = repo.toggleOpenInvite(clanId);
                invalidate(actor.getUniqueId());

                sync(() -> {
                    actor.playSound(actor.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
                    msg.accept(plugin.lang().get("messages.open_invite.status",
                            "status", now ? plugin.lang().get("messages.open_invite.on") : plugin.lang().get("messages.open_invite.off")));
                });
                notifyClan(clanId, plugin.lang().get("messages.open_invite.changed_broadcast",
                                "player", actor.getName(),
                                "status", now ? plugin.lang().get("messages.open_invite.on") : plugin.lang().get("messages.open_invite.off")),
                        Sound.UI_BUTTON_CLICK);
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.open_invite.fail"));
                LoggerUtil.error("Fehler beim Toggeln von Open-Invite durch " + actor.getName(), e);
            }
        });
    }

    public boolean handleClanChat(Player sender, String message) {
        if (!isClanChat(sender.getUniqueId())) return false;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(sender.getUniqueId());
                if (clanId <= 0) return;

                String[] nt = repo.getClanNameTag(clanId);
                String prefix = plugin.lang().get("messages.clan_chat.tag_prefix", "tag", nt[1]);

                for (ClanRepository.MemberRow m : repo.listMembers(clanId)) {
                    Player online = Bukkit.getPlayer(m.uuid);
                    if (online == null) continue;
                    online.sendMessage(plugin.lang().get("messages.clan_chat.format",
                            "prefix", prefix,
                            "player", sender.getName(),
                            "message", message));
                    online.playSound(online.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35f, 1.8f);
                }
            } catch (Exception e) {
                LoggerUtil.error("Fehler beim Verarbeiten des Clan-Chats!", e);
            }
        });

        return true;
    }

    public String money(double v) {
        return String.format(Locale.GERMANY, "%,.0f", Double.valueOf(v)).replace(',', '.') + plugin.lang().get("messages.money_suffix");
    }

    private CompletableFuture<Boolean> withdrawOnMain(Player player, double cost) {
        CompletableFuture<Boolean> f = new CompletableFuture<>();
        sync(() -> {
            try {
                if (!VaultHook.hasEconomy()) {
                    f.complete(Boolean.FALSE);
                    return;
                }
                double bal = VaultHook.eco().getBalance(player);
                if (bal < cost) {
                    f.complete(Boolean.FALSE);
                    return;
                }
                boolean ok = VaultHook.eco().withdrawPlayer(player, cost).transactionSuccess();
                f.complete(Boolean.valueOf(ok));
            } catch (Exception e) {
                f.complete(Boolean.FALSE);
            }
        });
        return f;
    }

    public void promote(Player actor, UUID target, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(actor.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_clan"));
                    return;
                }

                if (repo.getClanIdByMember(target) != clanId) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_your_clan"));
                    return;
                }

                MemberRole actorRole = repo.getRole(actor.getUniqueId());
                if (actorRole != MemberRole.LEADER) {
                    syncMsg(msg, plugin.lang().get("messages.promote.only_leader"));
                    return;
                }

                MemberRole targetRole = repo.getRole(target);
                if (targetRole != MemberRole.MEMBER) {
                    syncMsg(msg, plugin.lang().get("messages.promote.only_member"));
                    return;
                }

                repo.setRole(target, MemberRole.OFFICER);
                invalidate(actor.getUniqueId());
                invalidate(target);

                sync(() -> {
                    actor.playSound(actor.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
                    msg.accept(plugin.lang().get("messages.promote.success_actor"));
                    Player t = Bukkit.getPlayer(target);
                    if (t != null) {
                        t.sendMessage(plugin.lang().prefixed("messages.promote.success_target"));
                        t.playSound(t.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
                    }
                });
                String name = Bukkit.getOfflinePlayer(target).getName();
                notifyClan(clanId, plugin.lang().get("messages.promote.broadcast",
                                "player", name == null ? plugin.lang().get("messages.generic_member") : name,
                                "actor", actor.getName()),
                        Sound.UI_BUTTON_CLICK);
                LinkedHashMap<String, String> webhook = webhookClanData(clanId);
                webhook.put("actor", actor.getName());
                webhook.put("target", name == null ? plugin.lang().get("messages.generic_member") : name);
                sendWebhook("promote", webhook);
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.promote.fail"));
                LoggerUtil.error("Fehler beim Befördern durch " + actor.getName(), e);
            }
        });
    }

    public void demote(Player actor, UUID target, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(actor.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_clan"));
                    return;
                }

                if (repo.getClanIdByMember(target) != clanId) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_your_clan"));
                    return;
                }

                MemberRole actorRole = repo.getRole(actor.getUniqueId());
                if (actorRole != MemberRole.LEADER) {
                    syncMsg(msg, plugin.lang().get("messages.demote.only_leader"));
                    return;
                }

                MemberRole targetRole = repo.getRole(target);
                if (targetRole != MemberRole.OFFICER) {
                    syncMsg(msg, plugin.lang().get("messages.demote.only_officer"));
                    return;
                }

                repo.setRole(target, MemberRole.MEMBER);
                invalidate(actor.getUniqueId());
                invalidate(target);

                sync(() -> {
                    actor.playSound(actor.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
                    msg.accept(plugin.lang().get("messages.demote.success_actor"));
                    Player t = Bukkit.getPlayer(target);
                    if (t != null) {
                        t.sendMessage(plugin.lang().prefixed("messages.demote.success_target"));
                        t.playSound(t.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.1f);
                    }
                });
                String name = Bukkit.getOfflinePlayer(target).getName();
                notifyClan(clanId, plugin.lang().get("messages.demote.broadcast",
                                "player", name == null ? plugin.lang().get("messages.generic_member") : name,
                                "actor", actor.getName()),
                        Sound.UI_BUTTON_CLICK);
                LinkedHashMap<String, String> webhook = webhookClanData(clanId);
                webhook.put("actor", actor.getName());
                webhook.put("target", name == null ? plugin.lang().get("messages.generic_member") : name);
                sendWebhook("demote", webhook);
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.demote.fail"));
                LoggerUtil.error("Fehler beim Degradieren durch " + actor.getName(), e);
            }
        });
    }

    public void transferLeader(Player actor, UUID target, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (actor.getUniqueId().equals(target)) {
                    syncMsg(msg, plugin.lang().get("messages.transfer.cannot_self"));
                    return;
                }

                long clanId = repo.getClanIdByMember(actor.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_clan"));
                    return;
                }

                if (repo.getClanIdByMember(target) != clanId) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_your_clan"));
                    return;
                }

                MemberRole actorRole = repo.getRole(actor.getUniqueId());
                if (actorRole != MemberRole.LEADER) {
                    syncMsg(msg, plugin.lang().get("messages.transfer.only_leader"));
                    return;
                }

                repo.transferLeadership(actor.getUniqueId(), target);
                invalidate(actor.getUniqueId());
                invalidate(target);

                String targetName = Bukkit.getOfflinePlayer(target).getName();
                if (targetName == null || targetName.isBlank()) {
                    targetName = plugin.lang().get("messages.generic_member");
                }
                String finalTargetName = targetName;

                sync(() -> {
                    actor.playSound(actor.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.3f);
                    msg.accept(plugin.lang().get("messages.transfer.success_actor", "player", finalTargetName));
                    Player t = Bukkit.getPlayer(target);
                    if (t != null) {
                        t.sendMessage(plugin.lang().prefixed("messages.transfer.success_target"));
                        t.playSound(t.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.2f);
                    }
                });

                notifyClan(clanId, plugin.lang().get("messages.transfer.broadcast",
                        "player", finalTargetName,
                        "actor", actor.getName()), Sound.UI_BUTTON_CLICK);
                LinkedHashMap<String, String> webhook = webhookClanData(clanId);
                webhook.put("actor", actor.getName());
                webhook.put("target", finalTargetName);
                sendWebhook("transfer", webhook);
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.transfer.fail"));
                LoggerUtil.error("Fehler beim Übertragen der Clan-Leitung durch " + actor.getName(), e);
            }
        });
    }

    public void kick(Player actor, UUID target, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (actor.getUniqueId().equals(target)) {
                    syncMsg(msg, plugin.lang().get("messages.kick.cannot_self"));
                    return;
                }

                long clanId = repo.getClanIdByMember(actor.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_clan"));
                    return;
                }

                if (repo.getClanIdByMember(target) != clanId) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_your_clan"));
                    return;
                }

                MemberRole actorRole = repo.getRole(actor.getUniqueId());
                MemberRole targetRole = repo.getRole(target);

                if (targetRole == MemberRole.LEADER) {
                    syncMsg(msg, plugin.lang().get("messages.kick.cannot_leader"));
                    return;
                }

                if (actorRole == MemberRole.MEMBER) {
                    syncMsg(msg, plugin.lang().get("messages.kick.no_rights"));
                    return;
                }

                if (actorRole == MemberRole.OFFICER && targetRole != MemberRole.MEMBER) {
                    syncMsg(msg, plugin.lang().get("messages.kick.only_member"));
                    return;
                }

                repo.removeMember(target);
                invalidate(actor.getUniqueId());
                invalidate(target);

                sync(() -> {
                    actor.playSound(actor.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1.3f);
                    msg.accept(plugin.lang().get("messages.kick.success_actor"));
                    Player t = Bukkit.getPlayer(target);
                    if (t != null) {
                        t.sendMessage(plugin.lang().prefixed("messages.kick.success_target"));
                        t.playSound(t.getLocation(), Sound.ENTITY_WITHER_HURT, 0.7f, 1.1f);
                    }
                });
                String name = Bukkit.getOfflinePlayer(target).getName();
                notifyClan(clanId, plugin.lang().get("messages.kick.broadcast",
                                "player", name == null ? plugin.lang().get("messages.generic_member") : name,
                                "actor", actor.getName()),
                        Sound.ENTITY_VILLAGER_NO);
                String kickedName = name == null ? plugin.lang().get("messages.generic_member") : name;
                LinkedHashMap<String, String> webhook = webhookClanData(clanId);
                webhook.put("actor", actor.getName());
                webhook.put("target", kickedName);
                sendWebhook("kick", webhook);
                maybeCheckLeaderboardTopChange();
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.kick.fail"));
                LoggerUtil.error("Fehler beim Kicken durch " + actor.getName(), e);
            }
        });
    }

    public void setTagStyle(Player actor, String style, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(actor.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_clan"));
                    return;
                }

                MemberRole r = repo.getRole(actor.getUniqueId());
                String minRoleStr = plugin.getConfig().getString("clan.tag.minRoleChange", "OFFICER");
                MemberRole minRole = MemberRole.valueOf(minRoleStr.toUpperCase());

                if (!r.isAtLeast(minRole)) {
                    syncMsg(msg, plugin.lang().get("messages.no_rights"));
                    return;
                }

                String clean = style == null ? "" : style.trim();
                repo.setTagStyle(clanId, clean);
                clearCache();

                sync(() -> msg.accept(plugin.lang().get("messages.tag_style.saved")));
                notifyClan(clanId, plugin.lang().get("messages.tag_style.changed_broadcast", "player", actor.getName()), Sound.UI_BUTTON_CLICK);
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.tag_style.fail"));
                LoggerUtil.error("Fehler beim Setzen des Tag-Styles durch " + actor.getName(), e);
            }
        });
    }

    public void setChatSuffix(Player actor, String suffix, Consumer<String> msg) {
        if (!isChatSuffixEnabled()) {
            msg.accept(plugin.lang().get("messages.chat_suffix.disabled"));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(actor.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_clan"));
                    return;
                }

                MemberRole r = repo.getRole(actor.getUniqueId());
                MemberRole minRole = configuredRole("clan.chat.suffix.minRoleChange", MemberRole.OFFICER);
                if (!r.isAtLeast(minRole)) {
                    syncMsg(msg, plugin.lang().get("messages.no_rights"));
                    return;
                }

                final String clean = sanitizeChatSuffixInput(suffix);
                if (clean == null) {
                    syncMsg(msg, plugin.lang().get("messages.chat_suffix.too_long", "max", Integer.valueOf(chatSuffixMaxLength())));
                    return;
                }
                repo.setChatSuffix(clanId, clean);
                clearCache();

                final String out = clean.isEmpty()
                        ? plugin.lang().get("messages.chat_suffix.removed")
                        : plugin.lang().get("messages.chat_suffix.saved");
                sync(() -> {
                    actor.playSound(actor.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
                    msg.accept(out);
                });
                notifyClan(clanId, plugin.lang().get("messages.chat_suffix.changed_broadcast", "player", actor.getName()), Sound.UI_BUTTON_CLICK);

            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.chat_suffix.fail"));
                LoggerUtil.error("Fehler beim Setzen des Chat-Suffix durch " + actor.getName(), e);
            }
        });
    }

    public void depositAll(Player player, Consumer<String> msg) {
        if (!allowDepositAllButton()) {
            msg.accept(plugin.lang().get("messages.bank.all_disabled"));
            return;
        }
        sync(() -> {
            if (!VaultHook.hasEconomy()) {
                msg.accept(plugin.lang().get("messages.bank.vault_missing"));
                return;
            }
            double amount = VaultHook.eco().getBalance(player);
            if (amount <= 0) {
                msg.accept(plugin.lang().get("messages.bank.nothing_to_deposit"));
                return;
            }
            deposit(player, amount, msg);
        });
    }

    public void withdrawAll(Player player, Consumer<String> msg) {
        if (!allowWithdrawAllButton()) {
            msg.accept(plugin.lang().get("messages.bank.all_disabled"));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClanProfile profile = repo.getFullProfile(player.getUniqueId());
                if (!profile.inClan) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_clan"));
                    return;
                }
                if (profile.balance <= 0) {
                    syncMsg(msg, plugin.lang().get("messages.bank.nothing_to_withdraw"));
                    return;
                }
                withdraw(player, profile.balance, msg);
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.bank.fail"));
                LoggerUtil.error("Fehler beim Abheben des gesamten Clan-Guthabens durch " + player.getName(), e);
            }
        });
    }

    public void deposit(Player player, double amount, Consumer<String> msg) {
        if (!isBankEnabled()) {
            msg.accept(plugin.lang().get("messages.bank.disabled"));
            return;
        }
        if (amount <= 0) {
            msg.accept(plugin.lang().get("messages.bank.invalid_amount"));
            return;
        }
        if (!VaultHook.hasEconomy()) {
            msg.accept(plugin.lang().get("messages.bank.vault_missing"));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClanProfile p = repo.getFullProfile(player.getUniqueId());
                if (!p.inClan) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_clan"));
                    return;
                }

                boolean ok = withdrawOnMain(player, amount).get(3, TimeUnit.SECONDS);
                if (!ok) {
                    syncMsg(msg, plugin.lang().get("messages.bank.not_enough_money", "amount", money(amount)));
                    return;
                }

                repo.deposit(p.clanId, amount);
                recordBankTransaction(p.clanId, player.getUniqueId(), player.getName(), BankTransactionType.DEPOSIT, amount, p.balance + amount, "");
                invalidate(player.getUniqueId());

                sync(() -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                    msg.accept(plugin.lang().get("messages.bank.deposit_success", "amount", money(amount)));
                });
                LinkedHashMap<String, String> webhook = webhookClanData(p.clanId);
                webhook.put("actor", player.getName());
                putAmountVars(webhook, "amount", amount);
                putAmountVars(webhook, "balance_before", p.balance);
                putAmountVars(webhook, "balance_after", p.balance + amount);
                sendWebhook("bank_deposit", webhook);
                maybeCheckLeaderboardTopChange();

            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.bank.fail"));
                LoggerUtil.error("Fehler beim Einzahlen von " + amount + " durch " + player.getName(), e);
            }
        });
    }

    public void withdraw(Player player, double amount, Consumer<String> msg) {
        if (!isBankEnabled()) {
            msg.accept(plugin.lang().get("messages.bank.disabled"));
            return;
        }
        if (amount <= 0) {
            msg.accept(plugin.lang().get("messages.bank.invalid_amount"));
            return;
        }
        if (!VaultHook.hasEconomy()) {
            msg.accept(plugin.lang().get("messages.bank.vault_missing"));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClanProfile p = repo.getFullProfile(player.getUniqueId());
                if (!p.inClan) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_clan"));
                    return;
                }

                MemberRole minRole = configuredRole("clan.bank.withdraw.minRole", MemberRole.OFFICER);
                if (!p.role.isAtLeast(minRole)) {
                    syncMsg(msg, plugin.lang().get("messages.no_rights"));
                    return;
                }

                if (p.balance < amount) {
                    syncMsg(msg, plugin.lang().get("messages.bank.clan_not_enough_money", "balance", money(p.balance)));
                    return;
                }

                repo.withdraw(p.clanId, amount);
                recordBankTransaction(p.clanId, player.getUniqueId(), player.getName(), BankTransactionType.WITHDRAW, amount, p.balance - amount, "");
                invalidate(player.getUniqueId());

                // Give money to player
                VaultHook.eco().depositPlayer(player, amount);

                sync(() -> {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, 0.5f, 1.2f);
                    msg.accept(plugin.lang().get("messages.bank.withdraw_success", "amount", money(amount)));
                });
                LinkedHashMap<String, String> webhook = webhookClanData(p.clanId);
                webhook.put("actor", player.getName());
                putAmountVars(webhook, "amount", amount);
                putAmountVars(webhook, "balance_before", p.balance);
                putAmountVars(webhook, "balance_after", p.balance - amount);
                sendWebhook("bank_withdraw", webhook);
                maybeCheckLeaderboardTopChange();

            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.bank.fail"));
                LoggerUtil.error("Fehler beim Abheben von " + amount + " durch " + player.getName(), e);
            }
        });
    }

    public void recordTaxPayment(UUID playerUuid, String playerName, long clanId, double amount, double balanceAfter) {
        if (!isBankHistoryEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                recordBankTransaction(clanId, playerUuid, playerName, BankTransactionType.TAX, amount, balanceAfter, "");
                LinkedHashMap<String, String> webhook = webhookClanData(clanId);
                webhook.put("actor", playerName == null ? plugin.lang().get("messages.generic_player") : playerName);
                putAmountVars(webhook, "amount", amount);
                putAmountVars(webhook, "balance_before", Math.max(0.0, balanceAfter - amount));
                putAmountVars(webhook, "balance_after", balanceAfter);
                putRateVars(webhook, "new_rate", repo.getSettings(clanId).taxRate);
                sendWebhook("tax_collected", webhook);
                maybeCheckLeaderboardTopChange();
            } catch (Exception e) {
                LoggerUtil.error("Fehler beim Speichern der Clan-Bank-Historie für Tax-Event.", e);
            }
        });
    }

    public void setHome(Player player, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClanProfile p = repo.getFullProfile(player.getUniqueId());
                if (!p.inClan) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_clan"));
                    return;
                }

                String minRoleStr = plugin.getConfig().getString("clan.home.minRoleSet", "OFFICER");
                MemberRole minRole = MemberRole.valueOf(minRoleStr.toUpperCase());

                if (!p.role.isAtLeast(minRole)) {
                    syncMsg(msg, plugin.lang().get("messages.no_rights"));
                    return;
                }

                double cost = plugin.getConfig().getDouble("clan.home.setCost", 0.0);
                if (cost > 0.0) {
                    boolean ok = withdrawOnMain(player, cost).get(3, TimeUnit.SECONDS);
                    if (!ok) {
                        syncMsg(msg, plugin.lang().get("messages.home.set_not_enough_money", "cost", money(cost)));
                        return;
                    }
                }

                Location loc = player.getLocation();
                repo.setHome(p.clanId, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
                invalidate(player.getUniqueId());

                sync(() -> {
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.5f);
                    msg.accept(plugin.lang().get("messages.home.set_success"));
                });
                LinkedHashMap<String, String> webhook = webhookClanData(p.clanId);
                webhook.put("actor", player.getName());
                webhook.put("world", loc.getWorld() == null ? "" : loc.getWorld().getName());
                webhook.put("position", "x=" + (int) Math.floor(loc.getX()) + ", y=" + (int) Math.floor(loc.getY()) + ", z=" + (int) Math.floor(loc.getZ()));
                sendWebhook("home_set", webhook);

            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.home.set_fail"));
                LoggerUtil.error("Fehler beim Setzen des Clan-Homes durch " + player.getName(), e);
            }
        });
    }

    public void teleportHome(Player player, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClanProfile p = repo.getFullProfile(player.getUniqueId());
                if (!p.inClan) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_clan"));
                    return;
                }

                String minRoleStr = plugin.getConfig().getString("clan.home.minRoleTeleport", "MEMBER");
                MemberRole minRole = MemberRole.valueOf(minRoleStr.toUpperCase());

                if (!p.role.isAtLeast(minRole)) {
                    syncMsg(msg, plugin.lang().get("messages.no_rights"));
                    return;
                }

                if (p.homeWorld == null || p.homeWorld.isEmpty()) {
                    syncMsg(msg, plugin.lang().get("messages.home.not_set"));
                    return;
                }

                double cost = plugin.getConfig().getDouble("clan.home.teleportCost", 0.0);
                if (cost > 0.0) {
                    boolean ok = withdrawOnMain(player, cost).get(3, TimeUnit.SECONDS);
                    if (!ok) {
                        syncMsg(msg, plugin.lang().get("messages.home.tp_not_enough_money", "cost", money(cost)));
                        return;
                    }
                }

                sync(() -> {
                    World world = Bukkit.getWorld(p.homeWorld);
                    if (world == null) {
                        msg.accept(plugin.lang().get("messages.home.world_not_found"));
                        return;
                    }
                    Location target = new Location(world, p.homeX, p.homeY, p.homeZ, p.homeYaw, p.homePitch);
                    player.teleport(target);
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.0f);
                    msg.accept(plugin.lang().get("messages.home.tp_success"));
                });

            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.home.tp_fail"));
                LoggerUtil.error("Fehler beim Teleportieren zum Clan-Home für " + player.getName(), e);
            }
        });
    }

    public void setTaxRate(Player player, double rate, Consumer<String> msg) {
        if (rate < 0 || rate > 50) { // Max 50% tax for safety
            msg.accept(plugin.lang().get("messages.tax.invalid_rate"));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClanProfile p = repo.getFullProfile(player.getUniqueId());
                if (!p.inClan) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_clan"));
                    return;
                }

                if (!p.role.isAtLeast(MemberRole.LEADER)) {
                    syncMsg(msg, plugin.lang().get("messages.no_rights"));
                    return;
                }

                repo.setTaxRate(p.clanId, rate);
                invalidate(player.getUniqueId());

                sync(() -> {
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
                    msg.accept(plugin.lang().get("messages.tax.set_success", "rate", Double.valueOf(rate)));
                });
                LinkedHashMap<String, String> webhook = webhookClanData(p.clanId);
                webhook.put("actor", player.getName());
                putRateVars(webhook, "old_rate", p.taxRate);
                putRateVars(webhook, "new_rate", rate);
                sendWebhook("tax_changed", webhook);

            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.tax.fail"));
                LoggerUtil.error("Fehler beim Setzen des Steuersatzes durch " + player.getName(), e);
            }
        });
    }

    public void getTopClans(boolean byBalance, Consumer<List<ClanRepository.ClanLeaderboardRow>> cb) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int limit = plugin.getConfig().getInt("leaderboard.limit", 10);
                List<ClanRepository.ClanLeaderboardRow> top = byBalance
                        ? repo.getTopClansByBalance(limit)
                        : repo.getTopClansByMembers(limit);
                sync(() -> cb.accept(top));
            } catch (Exception e) {
                sync(() -> cb.accept(new ArrayList<>()));
                LoggerUtil.error("Fehler beim Laden der Bestenliste!", e);
            }
        });
    }


    public static String moneyStatic(double v, String suffix) {
        return String.format(Locale.GERMANY, "%,.0f", Double.valueOf(v)).replace(',', '.') + suffix;
    }

    private long cooldownLeft(Map<UUID, Long> cooldowns, UUID uuid) {
        Long until = cooldowns.get(uuid);
        if (until == null) return 0L;
        long left = (until - System.currentTimeMillis() + 999L) / 1000L;
        if (left <= 0) {
            cooldowns.remove(uuid);
            return 0L;
        }
        return left;
    }

    private void setCooldown(Map<UUID, Long> cooldowns, UUID uuid, long seconds) {
        cooldowns.put(uuid, Long.valueOf(System.currentTimeMillis() + (seconds * 1000L)));
    }

    private void sendWebhook(String eventKey, Map<String, String> values) {
        if (plugin.discord() == null) return;
        plugin.discord().sendEvent(eventKey, values);
    }

    private LinkedHashMap<String, String> webhookClanData(long clanId) throws Exception {
        LinkedHashMap<String, String> vars = new LinkedHashMap<>();
        vars.put("clan_id", String.valueOf(clanId));

        ClanRepository.ClanPublicProfileRow row = repo.getClanPublicProfile(clanId);
        if (row != null) {
            vars.put("clan_name", row.name);
            vars.put("clan_tag", row.tag);
            vars.put("member_count", String.valueOf(row.memberCount));
            vars.put("clan_balance", money(row.balance));
            vars.put("clan_balance_raw", rawNumber(row.balance));
            vars.put("tax_rate", formatRate(row.taxRate));
            vars.put("tax_rate_raw", rawNumber(row.taxRate));
            vars.put("leader_name", row.leaderName == null ? "" : row.leaderName);
            return vars;
        }

        ClanRepository.ClanLookupRow lookup = repo.getClanLookup(clanId);
        vars.put("clan_name", lookup == null ? "" : lookup.clanName);
        vars.put("clan_tag", lookup == null ? "" : lookup.clanTag);
        vars.put("member_count", String.valueOf(repo.countMembers(clanId)));
        double balance = repo.getClanBalance(clanId);
        vars.put("clan_balance", money(balance));
        vars.put("clan_balance_raw", rawNumber(balance));
        vars.put("tax_rate", formatRate(0.0));
        vars.put("tax_rate_raw", rawNumber(0.0));
        vars.put("leader_name", "");
        return vars;
    }

    private void putAmountVars(Map<String, String> vars, String key, double amount) {
        vars.put(key, money(amount));
        vars.put(key + "_raw", rawNumber(amount));
    }

    private void putRateVars(Map<String, String> vars, String key, double rate) {
        vars.put(key, formatRate(rate));
        vars.put(key + "_raw", rawNumber(rate));
    }

    private String rawNumber(double value) {
        return String.format(Locale.US, "%.2f", Double.valueOf(value));
    }

    private String formatRate(double value) {
        String out = rawNumber(value);
        while (out.endsWith("0")) {
            out = out.substring(0, out.length() - 1);
        }
        if (out.endsWith(".")) {
            out = out.substring(0, out.length() - 1);
        }
        return out + "%";
    }

    private void maybeCheckLeaderboardTopChange() {
        if (plugin.discord() == null) return;
        plugin.discord().handlePotentialTopChangeAsync();
    }

    private void recordBankTransaction(long clanId, UUID actorUuid, String actorName, BankTransactionType type,
                                       double amount, double balanceAfter, String note) throws Exception {
        if (!isBankHistoryEnabled()) return;
        repo.addBankTransaction(clanId, actorUuid, actorName, type, amount, balanceAfter, note);
        int keep = bankHistoryRetention();
        if (keep > 0) {
            repo.trimBankTransactions(clanId, keep);
        }
    }

    private void sync(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    private void syncMsg(Consumer<String> cb, String s) {
        sync(() -> cb.accept(s));
    }

    private void syncProfile(Consumer<ClanProfile> cb, ClanProfile p) {
        sync(() -> cb.accept(p));
    }

    private void notifyInviteApproval(ClanRepository.InviteRow inv) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                for (ClanRepository.MemberRow m : repo.listMembers(inv.clanId)) {
                    if (m.role == MemberRole.MEMBER) continue;
                    Player t = Bukkit.getPlayer(m.uuid);
                    if (t == null) continue;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String targetName = Bukkit.getOfflinePlayer(inv.targetUuid).getName();
                        String inviterName = Bukkit.getOfflinePlayer(inv.inviterUuid).getName();
                        t.sendMessage(plugin.lang().prefixed("messages.invite.request_new",
                                "player", targetName == null ? plugin.lang().get("messages.generic_player") : targetName));
                        if (inviterName != null && !inv.inviterUuid.equals(inv.targetUuid)) {
                            t.sendMessage(plugin.lang().prefixed("messages.invite.request_from", "player", inviterName));
                        }
                        t.sendMessage(plugin.lang().prefixed("messages.invite.request_action"));
                        t.playSound(t.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }

    private void notifyClan(long clanId, String message, Sound sound) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<ClanRepository.MemberRow> members = repo.listMembers(clanId);
                notifyMembers(members, message, sound);
            } catch (Exception ignored) {
            }
        });
    }

    private void notifyMembers(List<ClanRepository.MemberRow> members, String message, Sound sound) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (ClanRepository.MemberRow m : members) {
                Player t = Bukkit.getPlayer(m.uuid);
                if (t == null) continue;
                t.sendMessage(plugin.lang().prefix() + message);
                if (sound != null) {
                    t.playSound(t.getLocation(), sound, 0.6f, 1.2f);
                }
            }
        });
    }
}
