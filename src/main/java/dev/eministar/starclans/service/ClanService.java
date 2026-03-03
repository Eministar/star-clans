package dev.eministar.starclans.service;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.model.ClanProfile;
import dev.eministar.starclans.model.MemberRole;
import dev.eministar.starclans.utils.LoggerUtil;
import dev.eministar.starclans.vault.VaultHook;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
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

                ClanRepository.ClanLookupRow clan = repo.findClanByNameOrTag(clanInput);
                if (clan == null) {
                    syncMsg(msg, plugin.lang().get("messages.join.clan_not_found"));
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
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.join.fail"));
                LoggerUtil.error("Fehler bei der Beitrittsanfrage von " + player.getName(), e);
            }
        });
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
                    sendWebhook("join",
                            "Clan Join",
                            player.getName() + " joined " + inv.clanName + " [" + inv.clanTag + "].",
                            0x2ECC71);
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
                sendWebhook("join",
                        "Clan Join",
                        joinedName + " joined " + pending.clanName + " [" + pending.clanTag + "] after approval.",
                        0x2ECC71);
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
                sendWebhook("leave",
                        "Clan Leave",
                        player.getName() + " left clan " + clanId + ".",
                        0x95A5A6);
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
                repo.disband(clanId);
                clearCache();

                sync(() -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.6f, 1.0f);
                    msg.accept(plugin.lang().get("messages.disband.success"));
                });
                notifyMembers(members, plugin.lang().get("messages.broadcasts.disband"), Sound.ENTITY_WITHER_DEATH);
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

                sendWebhook("transfer",
                        "Leader Transfer",
                        actor.getName() + " transferred leadership to " + finalTargetName +
                                " in clan " + clanId + ".",
                        0xF1C40F);
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
                sendWebhook("kick",
                        "Clan Kick",
                        actor.getName() + " kicked " + kickedName + " from clan " + clanId + ".",
                        0xE74C3C);
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

                String tmp = suffix == null ? "" : suffix.trim();
                if (tmp.length() > 24) tmp = tmp.substring(0, 24);

                final String clean = tmp;
                repo.setChatSuffix(clanId, clean);
                clearCache();

                final String out = clean.isEmpty()
                        ? plugin.lang().get("messages.chat_suffix.removed")
                        : plugin.lang().get("messages.chat_suffix.saved");
                sync(() -> msg.accept(out));

            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.chat_suffix.fail"));
                LoggerUtil.error("Fehler beim Setzen des Chat-Suffix durch " + actor.getName(), e);
            }
        });
    }

    public void deposit(Player player, double amount, Consumer<String> msg) {
        if (amount <= 0) {
            msg.accept(plugin.lang().get("messages.bank.invalid_amount"));
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
                invalidate(player.getUniqueId());

                sync(() -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                    msg.accept(plugin.lang().get("messages.bank.deposit_success", "amount", money(amount)));
                });

            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.bank.fail"));
                LoggerUtil.error("Fehler beim Einzahlen von " + amount + " durch " + player.getName(), e);
            }
        });
    }

    public void withdraw(Player player, double amount, Consumer<String> msg) {
        if (amount <= 0) {
            msg.accept(plugin.lang().get("messages.bank.invalid_amount"));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClanProfile p = repo.getFullProfile(player.getUniqueId());
                if (!p.inClan) {
                    syncMsg(msg, plugin.lang().get("messages.not_in_clan"));
                    return;
                }

                if (!p.role.isAtLeast(MemberRole.OFFICER)) {
                    syncMsg(msg, plugin.lang().get("messages.no_rights"));
                    return;
                }

                if (p.balance < amount) {
                    syncMsg(msg, plugin.lang().get("messages.bank.clan_not_enough_money", "amount", money(amount)));
                    return;
                }

                repo.withdraw(p.clanId, amount);
                invalidate(player.getUniqueId());

                // Give money to player
                VaultHook.eco().depositPlayer(player, amount);

                sync(() -> {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, 0.5f, 1.2f);
                    msg.accept(plugin.lang().get("messages.bank.withdraw_success", "amount", money(amount)));
                });

            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.bank.fail"));
                LoggerUtil.error("Fehler beim Abheben von " + amount + " durch " + player.getName(), e);
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

    private void sendWebhook(String eventKey, String title, String description, int color) {
        if (plugin.discord() == null) return;
        plugin.discord().sendEvent(eventKey, title, description, color);
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
