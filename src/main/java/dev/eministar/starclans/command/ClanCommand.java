package dev.eministar.starclans.command;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.gui.*;
import dev.eministar.starclans.service.ClanService;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class ClanCommand implements CommandExecutor {

    private final StarClans plugin;
    private final ClanService service;
    private final ClanRepository repo;
    private final ClanMainMenu mainMenu;
    private final ClanCreateMenu createMenu;
    private final ClanInvitesMenu invitesMenu;
    private final ClanMembersMenu membersMenu;
    private final ClanMemberManageMenu manageMenu;
    private final ClanSettingsMenu settingsMenu;
    private final ClanTagStyleMenu tagStyleMenu;

    public ClanCommand(StarClans plugin,
                       ClanService service,
                       ClanRepository repo,
                       ClanMainMenu mainMenu,
                       ClanCreateMenu createMenu,
                       ClanInvitesMenu invitesMenu,
                       ClanMembersMenu membersMenu,
                       ClanMemberManageMenu manageMenu,
                       ClanTagStyleMenu tagStyleMenu,
                       ClanSettingsMenu settingsMenu) {
        this.plugin = plugin;
        this.service = service;
        this.repo = repo;
        this.mainMenu = mainMenu;
        this.createMenu = createMenu;
        this.invitesMenu = invitesMenu;
        this.membersMenu = membersMenu;
        this.manageMenu = manageMenu;
        this.settingsMenu = settingsMenu;
        this.tagStyleMenu = tagStyleMenu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(plugin.lang().prefixed("messages.player_only"));
            return true;
        }

        if (args.length == 0) {
            mainMenu.open(p);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create" -> createMenu.open(p);
            case "invites" -> invitesMenu.open(p);
            case "members" -> {
                if (args.length < 2) {
                    membersMenu.open(p);
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    p.sendMessage(plugin.lang().prefixed("messages.not_online"));
                    return true;
                }
                manageMenu.open(p, t.getUniqueId());
            }
            case "manage" -> service.loadProfileAsync(p.getUniqueId(), prof -> {
                if (prof == null || !prof.inClan) {
                    p.sendMessage(plugin.lang().prefixed("messages.not_in_clan"));
                    return;
                }
                if (prof.role != dev.eministar.starclans.model.MemberRole.LEADER) {
                    p.sendMessage(plugin.lang().prefixed("messages.only_leader_manage"));
                    return;
                }
                settingsMenu.open(p);
            });
            case "settings" -> settingsMenu.open(p);
            case "tagstyler", "tagstyle", "styler" -> tagStyleMenu.open(p);

            case "chat" -> {
                boolean on = service.toggleClanChat(p.getUniqueId());
                p.sendMessage(plugin.lang().prefixed(on ? "messages.clan_chat_on" : "messages.clan_chat_off"));
            }

            case "invite" -> {
                if (args.length < 2) {
                    p.sendMessage(plugin.lang().prefixed("messages.use.clan_invite"));
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    p.sendMessage(plugin.lang().prefixed("messages.not_online"));
                    return true;
                }
                service.invite(p, t, s -> p.sendMessage(plugin.lang().prefixedRaw(s)));
            }

            case "accept" -> {
                if (args.length < 2) {
                    p.sendMessage(plugin.lang().prefixed("messages.use.clan_accept"));
                    return true;
                }
                long id;
                try { id = Long.parseLong(args[1]); } catch (Exception ex) {
                    p.sendMessage(plugin.lang().prefixed("messages.invalid_id"));
                    return true;
                }
                service.acceptInvite(p, id, s -> p.sendMessage(plugin.lang().prefixedRaw(s)));
            }

            case "deny" -> {
                if (args.length < 2) {
                    p.sendMessage(plugin.lang().prefixed("messages.use.clan_deny"));
                    return true;
                }
                long id;
                try { id = Long.parseLong(args[1]); } catch (Exception ex) {
                    p.sendMessage(plugin.lang().prefixed("messages.invalid_id"));
                    return true;
                }
                service.denyInvite(p, id, s -> p.sendMessage(plugin.lang().prefixedRaw(s)));
            }

            case "leave" -> service.leave(p, s -> p.sendMessage(plugin.lang().prefixedRaw(s)));
            case "disband" -> service.disband(p, s -> p.sendMessage(plugin.lang().prefixedRaw(s)));

            case "kick" -> {
                if (args.length < 2) {
                    p.sendMessage(plugin.lang().prefixed("messages.use.clan_kick"));
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    p.sendMessage(plugin.lang().prefixed("messages.not_online"));
                    return true;
                }
                service.kick(p, t.getUniqueId(), s -> p.sendMessage(plugin.lang().prefixedRaw(s)));
            }

            case "promote" -> {
                if (args.length < 2) {
                    p.sendMessage(plugin.lang().prefixed("messages.use.clan_promote"));
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    p.sendMessage(plugin.lang().prefixed("messages.not_online"));
                    return true;
                }
                service.promote(p, t.getUniqueId(), s -> p.sendMessage(plugin.lang().prefixedRaw(s)));
            }

            case "demote" -> {
                if (args.length < 2) {
                    p.sendMessage(plugin.lang().prefixed("messages.use.clan_demote"));
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    p.sendMessage(plugin.lang().prefixed("messages.not_online"));
                    return true;
                }
                service.demote(p, t.getUniqueId(), s -> p.sendMessage(plugin.lang().prefixedRaw(s)));
            }

            default -> sendHelp(p);
        }

        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage(plugin.lang().prefixed("messages.help.title"));
        for (String line : plugin.lang().getList("messages.help.lines")) {
            p.sendMessage(line);
        }
    }
}
