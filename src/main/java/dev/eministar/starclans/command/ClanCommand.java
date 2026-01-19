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
            case "members" -> {
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
            case "manage" -> service.loadProfileAsync(p.getUniqueId(), prof -> {
                if (prof == null || !prof.inClan) {
                    p.sendMessage(StarPrefix.PREFIX + "§cDu bist in keinem Clan.");
                    return;
                }
                if (prof.role != dev.eministar.starclans.model.MemberRole.LEADER) {
                    p.sendMessage(StarPrefix.PREFIX + "§cNur der Leader kann Clan-Manage öffnen.");
                    return;
                }
                settingsMenu.open(p);
            });
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

            default -> sendHelp(p);
        }

        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage(StarPrefix.PREFIX + "§e§lClan Hilfe");
        p.sendMessage("§8• §f/clan §7- Hauptmenü öffnen");
        p.sendMessage("§8• §f/clan create §7- Clan erstellen");
        p.sendMessage("§8• §f/clan invites §7- Einladungen/Anfragen ansehen");
        p.sendMessage("§8• §f/clan invite <Spieler> §7- Spieler einladen");
        p.sendMessage("§8• §f/clan accept <id> §7- Einladung/Anfrage annehmen");
        p.sendMessage("§8• §f/clan deny <id> §7- Einladung/Anfrage ablehnen");
        p.sendMessage("§8• §f/clan members §7- Mitgliederliste oeffnen");
        p.sendMessage("§8• §f/clan members <Spieler> §7- Member verwalten");
        p.sendMessage("§8• §f/clan manage §7- Clan-Manage GUI (Leader)");
        p.sendMessage("§8• §f/clan settings §7- Einstellungen oeffnen");
        p.sendMessage("§8• §f/clan tagstyler §7- Tag/Suffix stylen");
        p.sendMessage("§8• §f/clan chat §7- Clan-Chat togglen");
        p.sendMessage("§8• §f/clan kick <Spieler> §7- Member kicken");
        p.sendMessage("§8• §f/clan promote <Spieler> §7- Member befoerdern");
        p.sendMessage("§8• §f/clan demote <Spieler> §7- Member demoten");
        p.sendMessage("§8• §f/clan leave §7- Clan verlassen");
        p.sendMessage("§8• §f/clan disband §7- Clan loeschen (Leader)");
    }
}
