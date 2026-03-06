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
    private final ClanBankMenu bankMenu;
    private final ClanLeaderboardMenu leaderboardMenu;
    private final ClanPublicProfileMenu profileMenu;

    public ClanCommand(StarClans plugin,
                       ClanService service,
                       ClanRepository repo,
                       ClanMainMenu mainMenu,
                       ClanCreateMenu createMenu,
                       ClanInvitesMenu invitesMenu,
                       ClanMembersMenu membersMenu,
                       ClanMemberManageMenu manageMenu,
                       ClanTagStyleMenu tagStyleMenu,
                       ClanSettingsMenu settingsMenu,
                       ClanBankMenu bankMenu,
                       ClanLeaderboardMenu leaderboardMenu,
                       ClanPublicProfileMenu profileMenu) {
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
        this.bankMenu = bankMenu;
        this.leaderboardMenu = leaderboardMenu;
        this.profileMenu = profileMenu;
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
            case "bank" -> bankMenu.open(p);
            case "leaderboard", "top" -> leaderboardMenu.open(p);
            case "profile" -> {
                if (!service.isProfileEnabled()) {
                    p.sendMessage(plugin.lang().prefixed("messages.profile.disabled"));
                    return true;
                }

                if (args.length < 2) {
                    service.loadProfileAsync(p.getUniqueId(), profile -> {
                        if (profile == null || !profile.inClan) {
                            p.sendMessage(plugin.lang().prefixed("messages.profile.usage"));
                            return;
                        }
                        profileMenu.openFromCommand(p, profile.clanId, true);
                    });
                    return true;
                }

                String input = args[1];
                Player targetPlayer = Bukkit.getPlayerExact(input);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        Long clanId = null;
                        if (targetPlayer != null) {
                            long targetClanId = repo.getClanIdByMember(targetPlayer.getUniqueId());
                            clanId = targetClanId > 0 ? Long.valueOf(targetClanId) : null;
                        }

                        if (clanId == null) {
                            ClanRepository.ClanLookupRow clan = repo.findClanByNameOrTag(input);
                            if (clan != null) {
                                clanId = Long.valueOf(clan.clanId);
                            }
                        }

                        Long finalClanId = clanId;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (finalClanId == null || finalClanId.longValue() <= 0) {
                                p.sendMessage(plugin.lang().prefixed("messages.profile.not_found"));
                                return;
                            }
                            profileMenu.openFromCommand(p, finalClanId.longValue(), true);
                        });
                    } catch (Exception ex) {
                        Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(plugin.lang().error("messages.load_failed")));
                    }
                });
            }
            case "home" -> service.teleportHome(p, s -> p.sendMessage(plugin.lang().prefixedRaw(s)));
            case "sethome" -> service.setHome(p, s -> p.sendMessage(plugin.lang().prefixedRaw(s)));

            case "deposit" -> {
                if (args.length < 2) {
                    p.sendMessage(plugin.lang().prefixed("messages.bank.deposit_usage"));
                    return true;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    service.depositAll(p, s -> p.sendMessage(plugin.lang().prefixedRaw(s)));
                    return true;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[1].replace(',', '.'));
                } catch (Exception ex) {
                    p.sendMessage(plugin.lang().prefixed("messages.bank.invalid_amount"));
                    return true;
                }
                service.deposit(p, amount, s -> p.sendMessage(plugin.lang().prefixedRaw(s)));
            }

            case "withdraw" -> {
                if (args.length < 2) {
                    p.sendMessage(plugin.lang().prefixed("messages.bank.withdraw_usage"));
                    return true;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    service.withdrawAll(p, s -> p.sendMessage(plugin.lang().prefixedRaw(s)));
                    return true;
                }
                double amount;
                try {
                    amount = Double.parseDouble(args[1].replace(',', '.'));
                } catch (Exception ex) {
                    p.sendMessage(plugin.lang().prefixed("messages.bank.invalid_amount"));
                    return true;
                }
                service.withdraw(p, amount, s -> p.sendMessage(plugin.lang().prefixedRaw(s)));
            }

            case "tax" -> {
                if (args.length < 2) {
                    p.sendMessage(plugin.lang().prefixed("messages.tax.usage"));
                    return true;
                }
                double rate;
                try {
                    rate = Double.parseDouble(args[1]);
                } catch (Exception ex) {
                    p.sendMessage(plugin.lang().prefixed("messages.invalid_number"));
                    return true;
                }
                service.setTaxRate(p, rate, s -> p.sendMessage(plugin.lang().prefixedRaw(s)));
            }

            case "chat" -> {
                boolean on = service.toggleClanChat(p.getUniqueId());
                p.sendMessage(plugin.lang().prefixed(on ? "messages.clan_chat_on" : "messages.clan_chat_off"));
            }

            case "chatsuffix", "suffix" -> {
                if (!service.isChatSuffixEnabled()) {
                    p.sendMessage(plugin.lang().prefixed("messages.chat_suffix.disabled"));
                    return true;
                }
                if (args.length < 2) {
                    p.sendMessage(plugin.lang().prefixed("messages.chat_suffix.usage"));
                    return true;
                }

                String value = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
                if (value.equalsIgnoreCase("clear") || value.equalsIgnoreCase("remove") || value.equalsIgnoreCase("off") || value.equalsIgnoreCase("none")) {
                    value = "";
                }
                String finalValue = value;
                service.setChatSuffix(p, finalValue, s -> p.sendMessage(plugin.lang().prefixedRaw(s)));
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

            case "join" -> {
                if (args.length < 2) {
                    p.sendMessage(plugin.lang().prefixed("messages.use.clan_join"));
                    return true;
                }
                service.requestJoin(p, args[1], s -> p.sendMessage(plugin.lang().prefixedRaw(s)));
            }

            case "accept" -> {
                if (args.length < 2) {
                    p.sendMessage(plugin.lang().prefixed("messages.use.clan_accept"));
                    return true;
                }
                long id;
                try {
                    id = Long.parseLong(args[1]);
                } catch (Exception ex) {
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
                try {
                    id = Long.parseLong(args[1]);
                } catch (Exception ex) {
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

            case "transfer" -> {
                if (args.length < 2) {
                    p.sendMessage(plugin.lang().prefixed("messages.use.clan_transfer"));
                    return true;
                }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) {
                    p.sendMessage(plugin.lang().prefixed("messages.not_online"));
                    return true;
                }
                service.transferLeader(p, t.getUniqueId(), s -> p.sendMessage(plugin.lang().prefixedRaw(s)));
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
