package dev.eministar.starclans.command;

import dev.eministar.starclans.StarClans;
import org.bukkit.command.*;

import java.util.ArrayList;
import java.util.List;

public final class StarClansTabCompleter implements TabCompleter {

    private final StarClans plugin;

    public StarClansTabCompleter(StarClans plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            String input = args[0].toLowerCase();

            if ("reload".startsWith(input)) suggestions.add("reload");
            if ("version".startsWith(input)) suggestions.add("version");
            if ("help".startsWith(input)) suggestions.add("help");
            if ("webhook".startsWith(input)) suggestions.add("webhook");

            return suggestions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("webhook")) {
            List<String> suggestions = new ArrayList<>();
            String input = args[1].toLowerCase();
            if ("test".startsWith(input)) suggestions.add("test");
            if ("digest".startsWith(input)) suggestions.add("digest");
            if ("help".startsWith(input)) suggestions.add("help");
            return suggestions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("webhook") && args[1].equalsIgnoreCase("test")) {
            List<String> suggestions = new ArrayList<>();
            String input = args[2].toLowerCase();
            for (String eventKey : plugin.discord() == null ? List.<String>of() : plugin.discord().configuredEventKeys()) {
                if (eventKey.toLowerCase().startsWith(input)) {
                    suggestions.add(eventKey);
                }
            }
            return suggestions;
        }

        return List.of();
    }
}
