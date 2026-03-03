package dev.eministar.starclans.command;

import org.bukkit.command.*;

import java.util.ArrayList;
import java.util.List;

public final class StarClansTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            String input = args[0].toLowerCase();

            if ("reload".startsWith(input)) suggestions.add("reload");
            if ("version".startsWith(input)) suggestions.add("version");
            if ("help".startsWith(input)) suggestions.add("help");

            return suggestions;
        }
        return List.of();
    }
}
