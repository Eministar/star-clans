package dev.eministar.starclans.command;

import org.bukkit.command.*;

import java.util.List;

public final class StarClansTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            if ("reload".startsWith(args[0].toLowerCase())) return List.of("reload");
        }
        return List.of();
    }
}
