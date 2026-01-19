package dev.eministar.starclans.command;

import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.gui.*;
import dev.eministar.starclans.service.ClanService;
import dev.eministar.starclans.utils.StarPrefix;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class ClanCommand implements CommandExecutor {

    private final ClanService service;
    private final ClanRepository repo;
    private final ClanMainMenu mainMenu;
    private final ClanCreateMenu createMenu;
    private final ClanInvitesMenu invitesMenu;
    private final ClanMembersMenu membersMenu;
    private final ClanMemberManageMenu manageMenu;
    private final ClanSettingsMenu settingsMenu;
    private final ClanTagStyleMenu tagStyleMenu;

    public ClanCommand(ClanService service,
                       ClanRepository repo,
                       ClanMainMenu mainMenu,
                       ClanCreateMenu createMenu,
                       ClanInvitesMenu invitesMenu,
                       ClanMembersMenu membersMenu,
                       ClanMemberManageMenu manageMenu,
                       ClanTagStyleMenu tagStyleMenu,
                       ClanSettingsMenu settingsMenu) {
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
            sender.sendMessage("[StarClans] Player only.");
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
            case "members" -> membersMenu.open(p);
            case "manage" -> {
                if (args.length < 2) {
                    membersMenu.open(p);
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    p.sendMessage(StarPrefix.PREFIX + "§cSpieler nicht online.");
                    return true;
                }
                manageMenu.open(p, t.getUniqueId());
            }
            case "settings" -> settingsMenu.open(p);
            case "tagstyler", "tagstyle", "styler" -> tagStyleMenu.open(p);

            case "chat" -> {
                boolean on = service.toggleClanChat(p.getUniqueId());
                p.sendMessage(StarPrefix.PREFIX + (on ? "§aClan-Chat aktiviert." : "§7Clan-Chat deaktiviert."));
            }

            case "invite" -> {
                if (args.length < 2) {
                    p.sendMessage(StarPrefix.PREFIX + "§7Nutze: §f/clan invite <Spieler>");
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    p.sendMessage(StarPrefix.PREFIX + "§cSpieler nicht online.");
                    return true;
                }
                service.invite(p, t, s -> p.sendMessage(StarPrefix.PREFIX + s));
            }

            case "accept" -> {
                if (args.length < 2) {
                    p.sendMessage(StarPrefix.PREFIX + "§7Nutze: §f/clan accept <id>");
                    return true;
                }
                long id;
                try { id = Long.parseLong(args[1]); } catch (Exception ex) {
                    p.sendMessage(StarPrefix.PREFIX + "§cUngültige ID.");
                    return true;
                }
                service.acceptInvite(p, id, s -> p.sendMessage(StarPrefix.PREFIX + s));
            }

            case "deny" -> {
                if (args.length < 2) {
                    p.sendMessage(StarPrefix.PREFIX + "§7Nutze: §f/clan deny <id>");
                    return true;
                }
                long id;
                try { id = Long.parseLong(args[1]); } catch (Exception ex) {
                    p.sendMessage(StarPrefix.PREFIX + "§cUngültige ID.");
                    return true;
                }
                service.denyInvite(p, id, s -> p.sendMessage(StarPrefix.PREFIX + s));
            }

            case "leave" -> service.leave(p, s -> p.sendMessage(StarPrefix.PREFIX + s));
            case "disband" -> service.disband(p, s -> p.sendMessage(StarPrefix.PREFIX + s));

            case "kick" -> {
                if (args.length < 2) {
                    p.sendMessage(StarPrefix.PREFIX + "§7Nutze: §f/clan kick <Spieler>");
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    p.sendMessage(StarPrefix.PREFIX + "§cSpieler nicht online.");
                    return true;
                }
                service.kick(p, t.getUniqueId(), s -> p.sendMessage(StarPrefix.PREFIX + s));
            }

            case "promote" -> {
                if (args.length < 2) {
                    p.sendMessage(StarPrefix.PREFIX + "§7Nutze: §f/clan promote <Spieler>");
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    p.sendMessage(StarPrefix.PREFIX + "§cSpieler nicht online.");
                    return true;
                }
                service.promote(p, t.getUniqueId(), s -> p.sendMessage(StarPrefix.PREFIX + s));
            }

            case "demote" -> {
                if (args.length < 2) {
                    p.sendMessage(StarPrefix.PREFIX + "§7Nutze: §f/clan demote <Spieler>");
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    p.sendMessage(StarPrefix.PREFIX + "§cSpieler nicht online.");
                    return true;
                }
                service.demote(p, t.getUniqueId(), s -> p.sendMessage(StarPrefix.PREFIX + s));
            }

            default -> p.sendMessage(StarPrefix.PREFIX + "§7Subcommands: §fcreate, invites, members, manage, settings, tagstyler, invite, accept, deny, chat, leave, disband, kick, promote, demote");
        }

        return true;
    }
}
