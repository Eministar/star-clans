package dev.eministar.starclans.service;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.model.ClanProfile;
import dev.eministar.starclans.model.MemberRole;
import dev.eministar.starclans.vault.VaultHook;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

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

    private final Pattern allowed = Pattern.compile("^[A-Za-z0-9_]+$");

    public ClanService(StarClans plugin, ClanRepository repo) {
        this.plugin = plugin;
        this.repo = repo;
    }

    public void clearCache() {
        cache.clear();
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
        clanChat.put(uuid, next);
        return next;
    }

    public void loadProfileAsync(UUID uuid, Consumer<ClanProfile> cb) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int invites = repo.countInvites(uuid);
                long clanId = repo.getClanIdByMember(uuid);

                if (clanId <= 0) {
                    ClanProfile p = ClanProfile.none(invites);
                    cache.put(uuid, p);
                    syncProfile(cb, p);
                    return;
                }

                String[] nt = repo.getClanNameTag(clanId);
                MemberRole role = repo.getRole(uuid);
                int members = repo.countMembers(clanId);

                ClanProfile p = new ClanProfile(true, clanId, nt[0], nt[1], role, members, invites);
                cache.put(uuid, p);
                syncProfile(cb, p);
            } catch (Exception e) {
                syncProfile(cb, ClanProfile.none(0));
                e.printStackTrace();
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
            doneMsg.accept(plugin.lang().get("messages.create.name_len", "min", minN, "max", maxN));
            return;
        }
        if (t.length() < minT || t.length() > maxT) {
            doneMsg.accept(plugin.lang().get("messages.create.tag_len", "min", minT, "max", maxT));
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
                e.printStackTrace();
            }
        });
    }

    public void invite(Player inviter, Player target, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
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

                int minutes = plugin.getConfig().getInt("clan.invite.expireMinutes", 60);
                boolean requiresApproval = r == MemberRole.MEMBER;
                long inviteId = repo.createInvite(clanId, target.getUniqueId(), inviter.getUniqueId(), minutes, requiresApproval);

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
                e.printStackTrace();
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
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.invite.accept_fail"));
                e.printStackTrace();
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
                e.printStackTrace();
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
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.leave.fail"));
                e.printStackTrace();
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
                e.printStackTrace();
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
                e.printStackTrace();
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
                e.printStackTrace();
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
                e.printStackTrace();
            }
        });

        return true;
    }

    public String money(double v) {
        return String.format("%,.0f", v).replace(',', '.') + plugin.lang().get("messages.money_suffix");
    }

    private CompletableFuture<Boolean> withdrawOnMain(Player player, double cost) {
        CompletableFuture<Boolean> f = new CompletableFuture<>();
        sync(() -> {
            try {
                if (!VaultHook.hasEconomy()) {
                    f.complete(false);
                    return;
                }
                double bal = VaultHook.eco().getBalance(player);
                if (bal < cost) {
                    f.complete(false);
                    return;
                }
                boolean ok = VaultHook.eco().withdrawPlayer(player, cost).transactionSuccess();
                f.complete(ok);
            } catch (Exception e) {
                f.complete(false);
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
                e.printStackTrace();
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
                e.printStackTrace();
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
            } catch (Exception e) {
                syncMsg(msg, plugin.lang().get("messages.kick.fail"));
                e.printStackTrace();
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
                if (r == MemberRole.MEMBER) {
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
                e.printStackTrace();
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
                e.printStackTrace();
            }
        });
    }


    public static String moneyStatic(double v, String suffix) {
        return String.format("%,.0f", v).replace(',', '.') + suffix;
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
                        if (inviterName != null) {
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
