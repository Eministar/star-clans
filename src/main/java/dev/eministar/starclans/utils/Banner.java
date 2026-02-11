package dev.eministar.starclans.utils;

import org.bukkit.plugin.java.JavaPlugin;

public final class Banner {

    private Banner() {}

    // ANSI Colors
    private static final String RESET = "\u001B[0m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String BLUE = "\u001B[34m";
    private static final String MAGENTA = "\u001B[95m";
    private static final String GRAY = "\u001B[90m";
    private static final String BOLD = "\u001B[1m";

    public static void print(JavaPlugin plugin) {
        String version = Version.get();

        String[] lines = new String[] {
                GRAY + "┌─────────────────────────────────────────────┐" + RESET,
                GRAY + "│" + RESET + MAGENTA + "          ✦✦✦   " + PURPLE + BOLD + "STAR" + RESET + CYAN + BOLD + "CLANS" + RESET + MAGENTA + "   ✦✦✦          " + RESET + GRAY + "│" + RESET,
                GRAY + "│" + RESET + PURPLE + "        ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★        " + RESET + GRAY + "│" + RESET,
                GRAY + "│" + RESET + PURPLE + "      ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★      " + RESET + GRAY + "│" + RESET,
                GRAY + "│" + RESET + PURPLE + "    ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★    " + RESET + GRAY + "│" + RESET,
                GRAY + "│" + RESET + PURPLE + "      ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★      " + RESET + GRAY + "│" + RESET,
                GRAY + "│" + RESET + PURPLE + "        ★ ★ ★ ★ ★ ★ ★ ★ ★ ★ ★        " + RESET + GRAY + "│" + RESET,
                GRAY + "│" + RESET + BLUE + "         Multiplayer Clan System         " + RESET + GRAY + "│" + RESET,
                GRAY + "├─────────────────────────────────────────────┤" + RESET,
                GRAY + "│" + RESET + CYAN + BOLD + "Version" + RESET + GRAY + ": " + RESET + PURPLE + BOLD + "v" + version + RESET + GRAY + "   " + RESET + CYAN + BOLD + "Author" + RESET + GRAY + ": " + RESET + PURPLE + "Eministar" + RESET + GRAY + "│" + RESET,
                GRAY + "└─────────────────────────────────────────────┘" + RESET
        };

        for (String line : lines) {
            plugin.getLogger().info(line);
        }
    }
}
