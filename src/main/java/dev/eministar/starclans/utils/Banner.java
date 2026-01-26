package dev.eministar.starclans.utils;

import org.bukkit.plugin.java.JavaPlugin;

public final class Banner {

    private Banner() {}

    // ANSI Colors
    private static final String RESET = "\u001B[0m";
    private static final String PURPLE = "\u001B[35m";
    private static final String CYAN = "\u001B[36m";
    private static final String GRAY = "\u001B[90m";
    private static final String BOLD = "\u001B[1m";

    public static void print(JavaPlugin plugin) {
        String version = Version.get();

        String[] lines = new String[] {
                GRAY + "────────────────────────────────────────" + RESET,
                PURPLE + "               ★★★★★               " + RESET,
                PURPLE + "           ★★★★★★★★★★★           " + RESET,
                PURPLE + "       ★★★★★★★★★★★★★★★★★       " + RESET,
                PURPLE + "   ★★★★★★★★★★★★★★★★★★★★★★★   " + RESET,
                PURPLE + "       ★★★★★★★★★★★★★★★★★       " + RESET,
                PURPLE + "           ★★★★★★★★★★★           " + RESET,
                PURPLE + "               ★★★★★               " + RESET,
                GRAY + "────────────────────────────────────────" + RESET,
                CYAN + BOLD + "Star" + RESET + GRAY + " - " + RESET + PURPLE + BOLD + "Clans " + RESET + GRAY + "v" + version + RESET,
                GRAY + "by Eministar" + RESET,
                GRAY + "────────────────────────────────────────" + RESET
        };

        for (String line : lines) {
            plugin.getLogger().info(line);
        }
    }
}
