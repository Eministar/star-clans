package dev.eministar.starclans.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ClanTabCompleter implements TabCompleter {

    private static final List<String> ROOT = Arrays.asList(
            "create","invites","members","manage","settings","tagstyler","tagstyle","styler",
            "invite","accept","deny","chat","leave","disband","kick","promote","demote"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return filter(ROOT, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("invite") || sub.equals("kick") || sub.equals("promote") || sub.equals("demote") || sub.equals("members")) {
                List<String> names = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
                return filter(names, args[1]);
            }
        }

        return List.of();
    }

    private List<String> filter(List<String> base, String token) {
        String t = token.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : base) {
            if (s.toLowerCase().startsWith(t)) out.add(s);
        }
        return out;
    }
}
