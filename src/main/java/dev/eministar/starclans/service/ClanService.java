package dev.eministar.starclans.service;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.model.ClanProfile;
import dev.eministar.starclans.model.MemberRole;
import dev.eministar.starclans.utils.StarPrefix;
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

        if (n.length() < minN || n.length() > maxN) { doneMsg.accept("§cName muss " + minN + "-" + maxN + " Zeichen sein."); return; }
        if (t.length() < minT || t.length() > maxT) { doneMsg.accept("§cTag muss " + minT + "-" + maxT + " Zeichen sein."); return; }
        if (!allowed.matcher(n).matches() || !allowed.matcher(t).matches()) { doneMsg.accept("§cNur A-Z, 0-9 und _ erlaubt."); return; }

        double cost = plugin.getConfig().getDouble("clan.creation.cost", 0.0);
        if (cost > 0.0 && !VaultHook.hasEconomy()) { doneMsg.accept("§cVault/Economy fehlt, aber Kosten > 0."); return; }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long currentClan = repo.getClanIdByMember(player.getUniqueId());
                if (currentClan > 0) { syncMsg(doneMsg, "§cDu bist schon in einem Clan."); return; }

                if (repo.nameExists(n)) { syncMsg(doneMsg, "§cClan-Name ist vergeben."); return; }
                if (repo.tagExists(t)) { syncMsg(doneMsg, "§cClan-Tag ist vergeben."); return; }

                if (cost > 0.0) {
                    boolean ok = withdrawOnMain(player, cost).get(3, TimeUnit.SECONDS);
                    if (!ok) { syncMsg(doneMsg, "§cZu wenig Geld. Kosten: §6" + money(cost)); return; }
                }

                long clanId = repo.createClan(n, t, player.getUniqueId(), player.getName());

                if (!style.isEmpty()) {
                    repo.setTagStyle(clanId, style);
                }

                invalidate(player.getUniqueId());

                sync(() -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.2f);
                    doneMsg.accept("§aClan erstellt! §f" + n + " §8[§b" + t + "§8]");
                });
            } catch (Exception e) {
                syncMsg(doneMsg, "§cFehler beim Erstellen. Console.");
                e.printStackTrace();
            }
        });
    }

    public void invite(Player inviter, Player target, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(inviter.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, "§cDu bist in keinem Clan.");
                    return;
                }

                MemberRole r = repo.getRole(inviter.getUniqueId());

                if (repo.getClanIdByMember(target.getUniqueId()) > 0) {
                    syncMsg(msg, "§cDer Spieler ist schon in einem Clan.");
                    return;
                }

                int minutes = plugin.getConfig().getInt("clan.invite.expireMinutes", 60);
                boolean requiresApproval = r == MemberRole.MEMBER;
                long inviteId = repo.createInvite(clanId, target.getUniqueId(), inviter.getUniqueId(), minutes, requiresApproval);

                sync(() -> {
                    inviter.playSound(inviter.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
                    msg.accept("§aInvite gesendet an §f" + target.getName() + "§a.");
                    if (requiresApproval) {
                        inviter.sendMessage(StarPrefix.PREFIX + "§7Bei Annahme ist eine Freigabe von Officer/Leader noetig.");
                    }
                    target.sendMessage(StarPrefix.PREFIX + "§7Du wurdest in einen Clan eingeladen.");
                    if (requiresApproval) {
                        target.sendMessage(StarPrefix.PREFIX + "§7Wenn du annimmst, muss ein Officer/Leader bestaetigen.");
                    }
                    if (inviteId > 0) {
                        TextComponent accept = new TextComponent("§a[Annehmen]");
                        accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan accept " + inviteId));
                        TextComponent deny = new TextComponent(" §c[Ablehnen]");
                        deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan deny " + inviteId));
                        target.spigot().sendMessage(new ComponentBuilder(StarPrefix.PREFIX + "§7Antwort: ").append(accept).append(deny).create());
                    } else {
                        target.sendMessage(StarPrefix.PREFIX + "§7Öffne §f/clan invites §7oder nutze §a/clan accept <id>§7.");
                    }
                    target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);
                });
            } catch (Exception e) {
                syncMsg(msg, "§cFehler beim Inviten. Console.");
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
                        syncMsg(msg, "§cDu bist schon in einem Clan.");
                        return;
                    }

                    if (inv.requiresApproval) {
                        repo.setInvitePendingApproval(inviteId, true);
                        sync(() -> {
                            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.9f, 1.25f);
                            msg.accept("§7Anfrage gespeichert. §fOfficer/Leader §7muss genehmigen.");
                        });

                        notifyInviteApproval(inv);
                        return;
                    }

                    repo.joinClan(inv.clanId, player.getUniqueId(), player.getName());
                    repo.deleteInvite(inviteId);
                    invalidate(player.getUniqueId());

                    sync(() -> {
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.25f);
                        msg.accept("§aDu bist dem Clan §f" + inv.clanName + " §8[§b" + inv.clanTag + "§8] §abeigetreten.");
                    });
                    notifyClan(inv.clanId, "§a" + player.getName() + " §7ist dem Clan beigetreten.", Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
                    return;
                }

                long clanId = repo.getClanIdByMember(player.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, "§cInvite nicht gefunden oder abgelaufen.");
                    return;
                }

                MemberRole role = repo.getRole(player.getUniqueId());
                if (role == MemberRole.MEMBER) {
                    syncMsg(msg, "§cKeine Rechte.");
                    return;
                }

                ClanRepository.InviteRow pending = repo.getInviteForApproval(inviteId, clanId);
                if (pending == null) {
                    syncMsg(msg, "§cInvite nicht gefunden oder abgelaufen.");
                    return;
                }

                if (repo.getClanIdByMember(pending.targetUuid) > 0) {
                    repo.deleteInvite(inviteId);
                    syncMsg(msg, "§cDer Spieler ist bereits in einem Clan.");
                    return;
                }

                String targetName = Bukkit.getOfflinePlayer(pending.targetUuid).getName();
                repo.joinClan(pending.clanId, pending.targetUuid, targetName == null ? "Unknown" : targetName);
                repo.deleteInvite(inviteId);
                invalidate(pending.targetUuid);

                sync(() -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.25f);
                    msg.accept("§aAnfrage angenommen.");
                    Player t = Bukkit.getPlayer(pending.targetUuid);
                    if (t != null) {
                        t.sendMessage(StarPrefix.PREFIX + "§aDeine Clan-Anfrage wurde genehmigt.");
                        t.playSound(t.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.2f);
                    }
                });
                notifyClan(pending.clanId, "§a" + (targetName == null ? "Neues Mitglied" : targetName) + " §7ist dem Clan beigetreten.", Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
            } catch (Exception e) {
                syncMsg(msg, "§cFehler beim Annehmen. Console.");
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
                    syncMsg(msg, "§7Invite abgelehnt.");
                    return;
                }

                long clanId = repo.getClanIdByMember(player.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, "§cInvite nicht gefunden oder abgelaufen.");
                    return;
                }

                MemberRole role = repo.getRole(player.getUniqueId());
                if (role == MemberRole.MEMBER) {
                    syncMsg(msg, "§cKeine Rechte.");
                    return;
                }

                ClanRepository.InviteRow pending = repo.getInviteForApproval(inviteId, clanId);
                if (pending == null) {
                    syncMsg(msg, "§cInvite nicht gefunden oder abgelaufen.");
                    return;
                }

                repo.deleteInvite(inviteId);
                sync(() -> {
                    msg.accept("§7Anfrage abgelehnt.");
                    Player t = Bukkit.getPlayer(pending.targetUuid);
                    if (t != null) {
                        t.sendMessage(StarPrefix.PREFIX + "§cDeine Clan-Anfrage wurde abgelehnt.");
                    }
                });
            } catch (Exception e) {
                syncMsg(msg, "§cFehler beim Ablehnen. Console.");
                e.printStackTrace();
            }
        });
    }

    public void leave(Player player, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(player.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, "§cDu bist in keinem Clan.");
                    return;
                }

                MemberRole role = repo.getRole(player.getUniqueId());
                if (role == MemberRole.LEADER) {
                    syncMsg(msg, "§cDu bist Leader. Disband/Transfer kommt (oder manuell).");
                    return;
                }

                repo.removeMember(player.getUniqueId());
                invalidate(player.getUniqueId());

                sync(() -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1.2f);
                    msg.accept("§aDu hast den Clan verlassen.");
                });
                notifyClan(clanId, "§7" + player.getName() + " §chat den Clan verlassen.", Sound.ENTITY_VILLAGER_NO);
            } catch (Exception e) {
                syncMsg(msg, "§cFehler beim Verlassen. Console.");
                e.printStackTrace();
            }
        });
    }

    public void disband(Player player, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(player.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, "§cDu bist in keinem Clan.");
                    return;
                }

                MemberRole role = repo.getRole(player.getUniqueId());
                if (role != MemberRole.LEADER) {
                    syncMsg(msg, "§cNur der Leader kann disbanden.");
                    return;
                }

                java.util.List<ClanRepository.MemberRow> members = repo.listMembers(clanId);
                repo.disband(clanId);
                clearCache();

                sync(() -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.6f, 1.0f);
                    msg.accept("§cClan wurde aufgelöst.");
                });
                notifyMembers(members, "§cClan wurde aufgeloest.", Sound.ENTITY_WITHER_DEATH);
            } catch (Exception e) {
                syncMsg(msg, "§cFehler beim Disband. Console.");
                e.printStackTrace();
            }
        });
    }

    public void setMotd(Player actor, String motd, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(actor.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, "§cDu bist in keinem Clan.");
                    return;
                }

                MemberRole r = repo.getRole(actor.getUniqueId());
                if (r == MemberRole.MEMBER) {
                    syncMsg(msg, "§cKeine Rechte.");
                    return;
                }

                repo.setMotd(clanId, motd);
                invalidate(actor.getUniqueId());

                sync(() -> {
                    actor.playSound(actor.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
                    msg.accept("§aMOTD gespeichert.");
                });
                notifyClan(clanId, "§7MOTD wurde von §f" + actor.getName() + " §7geaendert.", Sound.UI_BUTTON_CLICK);
            } catch (Exception e) {
                syncMsg(msg, "§cFehler beim Speichern. Console.");
                e.printStackTrace();
            }
        });
    }

    public void toggleOpenInvite(Player actor, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(actor.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, "§cDu bist in keinem Clan.");
                    return;
                }

                MemberRole r = repo.getRole(actor.getUniqueId());
                if (r == MemberRole.MEMBER) {
                    syncMsg(msg, "§cKeine Rechte.");
                    return;
                }

                boolean now = repo.toggleOpenInvite(clanId);
                invalidate(actor.getUniqueId());

                sync(() -> {
                    actor.playSound(actor.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
                    msg.accept("§7Open Invite: " + (now ? "§aAN" : "§cAUS"));
                });
                notifyClan(clanId, "§7Open Invite wurde von §f" + actor.getName() + " §7auf " + (now ? "§aAN" : "§cAUS") + "§7 gestellt.", Sound.UI_BUTTON_CLICK);
            } catch (Exception e) {
                syncMsg(msg, "§cFehler beim Toggle. Console.");
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
                String prefix = "§8[§b" + nt[1] + "§8] §b";

                for (ClanRepository.MemberRow m : repo.listMembers(clanId)) {
                    Player online = Bukkit.getPlayer(m.uuid);
                    if (online == null) continue;
                    online.sendMessage(prefix + sender.getName() + " §8» §f" + message);
                    online.playSound(online.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35f, 1.8f);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return true;
    }

    public String money(double v) {
        return String.format("%,.0f", v).replace(',', '.') + "§e$";
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
                    syncMsg(msg, "§cDu bist in keinem Clan.");
                    return;
                }

                if (repo.getClanIdByMember(target) != clanId) {
                    syncMsg(msg, "§cDer Spieler ist nicht in deinem Clan.");
                    return;
                }

                MemberRole actorRole = repo.getRole(actor.getUniqueId());
                if (actorRole != MemberRole.LEADER) {
                    syncMsg(msg, "§cNur der Leader kann promoten.");
                    return;
                }

                MemberRole targetRole = repo.getRole(target);
                if (targetRole != MemberRole.MEMBER) {
                    syncMsg(msg, "§cDer Spieler ist nicht Member.");
                    return;
                }

                repo.setRole(target, MemberRole.OFFICER);
                invalidate(actor.getUniqueId());
                invalidate(target);

                sync(() -> {
                    actor.playSound(actor.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
                    msg.accept("§aSpieler wurde zu §bOFFICER §apromoted.");
                    Player t = Bukkit.getPlayer(target);
                    if (t != null) {
                        t.sendMessage(StarPrefix.PREFIX + "§aDu wurdest zu §bOFFICER §abefördert.");
                        t.playSound(t.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.3f);
                    }
                });
                String name = Bukkit.getOfflinePlayer(target).getName();
                notifyClan(clanId, "§b" + (name == null ? "Member" : name) + " §7wurde von §f" + actor.getName() + " §7zu §bOFFICER §7befoerdert.", Sound.UI_BUTTON_CLICK);
            } catch (Exception e) {
                syncMsg(msg, "§cFehler beim Promoten. Console.");
                e.printStackTrace();
            }
        });
    }

    public void demote(Player actor, UUID target, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(actor.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, "§cDu bist in keinem Clan.");
                    return;
                }

                if (repo.getClanIdByMember(target) != clanId) {
                    syncMsg(msg, "§cDer Spieler ist nicht in deinem Clan.");
                    return;
                }

                MemberRole actorRole = repo.getRole(actor.getUniqueId());
                if (actorRole != MemberRole.LEADER) {
                    syncMsg(msg, "§cNur der Leader kann demoten.");
                    return;
                }

                MemberRole targetRole = repo.getRole(target);
                if (targetRole != MemberRole.OFFICER) {
                    syncMsg(msg, "§cDer Spieler ist kein Officer.");
                    return;
                }

                repo.setRole(target, MemberRole.MEMBER);
                invalidate(actor.getUniqueId());
                invalidate(target);

                sync(() -> {
                    actor.playSound(actor.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
                    msg.accept("§aSpieler wurde zu §7MEMBER §agedemoted.");
                    Player t = Bukkit.getPlayer(target);
                    if (t != null) {
                        t.sendMessage(StarPrefix.PREFIX + "§cDu wurdest zu §7MEMBER §czurückgestuft.");
                        t.playSound(t.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.1f);
                    }
                });
                String name = Bukkit.getOfflinePlayer(target).getName();
                notifyClan(clanId, "§7" + (name == null ? "Member" : name) + " §7wurde von §f" + actor.getName() + " §7zu §7MEMBER §7gedemoted.", Sound.UI_BUTTON_CLICK);
            } catch (Exception e) {
                syncMsg(msg, "§cFehler beim Demoten. Console.");
                e.printStackTrace();
            }
        });
    }

    public void kick(Player actor, UUID target, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (actor.getUniqueId().equals(target)) {
                    syncMsg(msg, "§cDu kannst dich nicht selbst kicken.");
                    return;
                }

                long clanId = repo.getClanIdByMember(actor.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, "§cDu bist in keinem Clan.");
                    return;
                }

                if (repo.getClanIdByMember(target) != clanId) {
                    syncMsg(msg, "§cDer Spieler ist nicht in deinem Clan.");
                    return;
                }

                MemberRole actorRole = repo.getRole(actor.getUniqueId());
                MemberRole targetRole = repo.getRole(target);

                if (targetRole == MemberRole.LEADER) {
                    syncMsg(msg, "§cDu kannst den Leader nicht kicken.");
                    return;
                }

                if (actorRole == MemberRole.MEMBER) {
                    syncMsg(msg, "§cKeine Rechte zum Kicken.");
                    return;
                }

                if (actorRole == MemberRole.OFFICER && targetRole != MemberRole.MEMBER) {
                    syncMsg(msg, "§cDu kannst nur Member kicken.");
                    return;
                }

                repo.removeMember(target);
                invalidate(actor.getUniqueId());
                invalidate(target);

                sync(() -> {
                    actor.playSound(actor.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1.3f);
                    msg.accept("§aSpieler wurde gekickt.");
                    Player t = Bukkit.getPlayer(target);
                    if (t != null) {
                        t.sendMessage(StarPrefix.PREFIX + "§cDu wurdest aus dem Clan gekickt.");
                        t.playSound(t.getLocation(), Sound.ENTITY_WITHER_HURT, 0.7f, 1.1f);
                    }
                });
                String name = Bukkit.getOfflinePlayer(target).getName();
                notifyClan(clanId, "§c" + (name == null ? "Member" : name) + " §7wurde von §f" + actor.getName() + " §7gekickt.", Sound.ENTITY_VILLAGER_NO);
            } catch (Exception e) {
                syncMsg(msg, "§cFehler beim Kicken. Console.");
                e.printStackTrace();
            }
        });
    }

    public void setTagStyle(Player actor, String style, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(actor.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, "§cDu bist in keinem Clan.");
                    return;
                }

                MemberRole r = repo.getRole(actor.getUniqueId());
                if (r == MemberRole.MEMBER) {
                    syncMsg(msg, "§cKeine Rechte.");
                    return;
                }

                String clean = style == null ? "" : style.trim();
                repo.setTagStyle(clanId, clean);
                clearCache();

                sync(() -> msg.accept("§aTag-Style gespeichert."));
                notifyClan(clanId, "§7Clan-Tag Style wurde von §f" + actor.getName() + " §7geaendert.", Sound.UI_BUTTON_CLICK);
            } catch (Exception e) {
                syncMsg(msg, "§cFehler. Console.");
                e.printStackTrace();
            }
        });
    }

    public void setChatSuffix(Player actor, String suffix, Consumer<String> msg) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(actor.getUniqueId());
                if (clanId <= 0) {
                    syncMsg(msg, "§cDu bist in keinem Clan.");
                    return;
                }

                MemberRole r = repo.getRole(actor.getUniqueId());
                if (r == MemberRole.MEMBER) {
                    syncMsg(msg, "§cKeine Rechte.");
                    return;
                }

                String tmp = suffix == null ? "" : suffix.trim();
                if (tmp.length() > 24) tmp = tmp.substring(0, 24);

                final String clean = tmp;
                repo.setChatSuffix(clanId, clean);
                clearCache();

                final String out = clean.isEmpty() ? "§7Suffix entfernt." : "§aSuffix gespeichert.";
                sync(() -> msg.accept(out));

            } catch (Exception e) {
                syncMsg(msg, "§cFehler. Console.");
                e.printStackTrace();
            }
        });
    }


    public static String moneyStatic(double v) {
        return String.format("%,.0f", v).replace(',', '.') + "§e$";
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
                        t.sendMessage(StarPrefix.PREFIX + "§7Neue Clan-Anfrage: §f" + (targetName == null ? "Spieler" : targetName));
                        if (inviterName != null) {
                            t.sendMessage(StarPrefix.PREFIX + "§7Eingeladen von: §f" + inviterName);
                        }
                        t.sendMessage(StarPrefix.PREFIX + "§7Nutze §f/clan invites §7zum Bearbeiten.");
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
                t.sendMessage(StarPrefix.PREFIX + message);
                if (sound != null) {
                    t.playSound(t.getLocation(), sound, 0.6f, 1.2f);
                }
            }
        });
    }
}
