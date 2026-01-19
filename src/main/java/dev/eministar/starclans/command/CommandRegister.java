package dev.eministar.starclans.command;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.gui.*;
import dev.eministar.starclans.service.ClanService;
import org.bukkit.command.PluginCommand;

public final class CommandRegister {

    public static void register(StarClans plugin,
                                ClanService service,
                                ClanRepository repo,
                                ClanMainMenu mainMenu,
                                ClanCreateMenu createMenu,
                                ClanInvitesMenu invitesMenu,
                                ClanMembersMenu membersMenu,
                                ClanMemberManageMenu manageMenu,
                                ClanTagStyleMenu tagStyleMenu,
                                ClanSettingsMenu settingsMenu) {

        PluginCommand clan = plugin.getCommand("clan");
        if (clan == null) {
            throw new IllegalStateException("Command 'clan' not found in plugin.yml");
        }

        clan.setExecutor(new ClanCommand(service, repo, mainMenu, createMenu, invitesMenu, membersMenu, manageMenu, tagStyleMenu, settingsMenu));
        clan.setTabCompleter(new ClanTabCompleter());
    }

    private CommandRegister() {}
}
