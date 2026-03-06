package dev.eministar.starclans.utils;

import org.bukkit.plugin.java.JavaPlugin;

public final class Version {
    private static final String VERSION = "2.0.0";

    private Version() {
    }

    public static void init(JavaPlugin ignored) {
        // no-op: version is a fixed constant
    }

    public static String get() {
        return VERSION;
    }
}
