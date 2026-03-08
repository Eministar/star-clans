package dev.eministar.starclans.utils;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Banner {

    private Banner() {
    }

    private static final int INNER_WIDTH = 50;

    // ANSI colors (improved palette)
    private static final String RESET = "\u001B[0m";
    private static final String PURPLE = "\u001B[35m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String MAGENTA = "\u001B[95m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GRAY = "\u001B[90m";
    private static final String BOLD = "\u001B[1m";

    public static void print(JavaPlugin plugin) {
        String version = Version.get();
        String versionLabel = "Version: " + version;
        String authorLabel = "By Eministar";
        String meta = padRight(trimToWidth(versionLabel, 24), 24) + padLeft(trimToWidth(authorLabel, 24), 24);

        String[] lines = new String[]{
                "",
                borderTop(),
                framed("  S T A R C L A N S  ", MAGENTA + BOLD),
                framed("The Ultimate Clan Experience", CYAN),
                borderMid(),
                framed("★ ★ ★ ★ ★", PURPLE),
                framed(meta, YELLOW),
                borderBottom(),
                ""
        };

        for (String line : lines) {
            Bukkit.getConsoleSender().sendMessage(line);
        }
    }

    private static String borderTop() {
        return GRAY + "╔" + "═".repeat(INNER_WIDTH) + "╗" + RESET;
    }

    private static String borderMid() {
        return GRAY + "╠" + "═".repeat(INNER_WIDTH) + "╣" + RESET;
    }

    private static String borderBottom() {
        return GRAY + "╚" + "═".repeat(INNER_WIDTH) + "╝" + RESET;
    }

    private static String framed(String text, String color) {
        return GRAY + "║" + RESET + " " + color + center(text, INNER_WIDTH - 2) + RESET + " " + GRAY + "║" + RESET;
    }

    private static String center(String text, int width) {
        String value = trimToWidth(text, width);
        int missing = width - value.length();
        int leftPad = missing / 2;
        int rightPad = missing - leftPad;
        return " ".repeat(leftPad) + value + " ".repeat(rightPad);
    }

    private static String padRight(String text, int width) {
        if (text.length() >= width) return text;
        return text + " ".repeat(width - text.length());
    }

    private static String padLeft(String text, int width) {
        if (text.length() >= width) return text;
        return " ".repeat(width - text.length()) + text;
    }

    private static String trimToWidth(String text, int width) {
        if (text.length() <= width) {
            return text;
        }
        return text.substring(0, width);
    }
}
