package dev.eministar.starclans.utils;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class UpdateChecker {
    private static final String VERSION_URL = "https://plugins.star-dev.xyz/starclans/version.txt";
    private static final String PLUGIN_URL = "https://plugins.star-dev.xyz/starclans";
    private static volatile String latestVersion;
    private static volatile String currentVersion;
    private static volatile boolean updateAvailable;
    private static volatile boolean listenerRegistered;

    private UpdateChecker() {}

    public static void check(JavaPlugin plugin) {
        registerJoinListener(plugin);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String latest = fetchLatestVersion();
            if (latest == null || latest.isBlank()) {
                return;
            }

            String current = Version.get();
            if (latest.equalsIgnoreCase(current)) {
                latestVersion = latest;
                currentVersion = current;
                updateAvailable = false;
                return;
            }

            latestVersion = latest;
            currentVersion = current;
            updateAvailable = true;

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
            HttpURLConnection connection = openConnectionFollowRedirects(VERSION_URL);
            if (connection == null) {
                return null;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                return line == null ? null : line.trim();
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void sendUpdateMessage(Player player, String current, String latest) {
        player.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━" + ChatColor.YELLOW
                + ChatColor.BOLD + " StarClans " + ChatColor.GOLD + "Update"
                + ChatColor.DARK_GRAY + " ━━━━━━━━━━━━━━");
        player.sendMessage(ChatColor.GRAY + "Deine Version: " + ChatColor.RED + current);
        player.sendMessage(ChatColor.GRAY + "Neue Version: " + ChatColor.GREEN + latest);
        player.sendMessage(ChatColor.GRAY + "Lade jetzt herunter oder kopiere die Version.");

        TextComponent download = new TextComponent("⤓ Download");
        download.setColor(ChatColor.AQUA);
        download.setBold(true);
        download.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, PLUGIN_URL));
        download.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Öffnet die Download-Seite").color(ChatColor.GRAY).create()));

        TextComponent spacer = new TextComponent("  ");

        TextComponent copy = new TextComponent("⎘ Version kopieren");
        copy.setColor(ChatColor.GOLD);
        copy.setBold(true);
        copy.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, latest));
        copy.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Kopiert die neue Version").color(ChatColor.GRAY).create()));

        player.spigot().sendMessage(download, spacer, copy);
        player.sendMessage(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private static HttpURLConnection openConnectionFollowRedirects(String url) {
        try {
            String current = url;
            for (int i = 0; i < 3; i++) {
                HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
                connection.setRequestMethod("GET");
                connection.setUseCaches(false);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(false);
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) {
                        return null;
                    }
                    current = location;
                    continue;
                }
                return connection;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static void registerJoinListener(JavaPlugin plugin) {
        if (listenerRegistered) {
            return;
        }
        listenerRegistered = true;
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                if (!updateAvailable || latestVersion == null || currentVersion == null) {
                    return;
                }
                Player player = event.getPlayer();
                if (!player.isOp()) {
                    return;
                }
                sendUpdateMessage(player, currentVersion, latestVersion);
            }
        }, plugin);
    }
}
