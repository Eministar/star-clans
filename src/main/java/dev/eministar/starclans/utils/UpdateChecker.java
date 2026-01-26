package dev.eministar.starclans.utils;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class UpdateChecker {
    private static final String VERSION_URL = "https://plugins.star-dev.xyz/starclans/version.txt";
    private static final String PLUGIN_URL = "https://plugins.star-dev.xyz/starclans";

    private UpdateChecker() {}

    public static void check(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String latest = fetchLatestVersion();
            if (latest == null || latest.isBlank()) {
                return;
            }

            String current = Version.get();
            if (latest.equalsIgnoreCase(current)) {
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.isOp()) {
                        continue;
                    }
                    sendUpdateMessage(player, current, latest);
                }
            });
        });
    }

    private static String fetchLatestVersion() {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(VERSION_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.readLine();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void sendUpdateMessage(Player player, String current, String latest) {
        player.sendMessage(ChatColor.DARK_GRAY + "------------------------------------------------");
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "StarClans Update");
        player.sendMessage(ChatColor.GRAY + "Aktuell: " + ChatColor.RED + current
                + ChatColor.GRAY + "  |  Neu: " + ChatColor.GREEN + latest);

        TextComponent download = new TextComponent("[Download]");
        download.setColor(ChatColor.AQUA);
        download.setBold(true);
        download.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, PLUGIN_URL));
        download.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Öffnet die Download-Seite").color(ChatColor.GRAY).create()));

        TextComponent spacer = new TextComponent("  ");

        TextComponent copy = new TextComponent("[Copy Version]");
        copy.setColor(ChatColor.YELLOW);
        copy.setBold(true);
        copy.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, latest));
        copy.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Kopiert die neue Version").color(ChatColor.GRAY).create()));

        player.spigot().sendMessage(download, spacer, copy);
        player.sendMessage(ChatColor.DARK_GRAY + "------------------------------------------------");
    }
}
