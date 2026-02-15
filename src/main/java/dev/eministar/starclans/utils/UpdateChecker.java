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
    private static final String VERSION_URL = "https://hub.star-dev.xyz/starclans/version.txt";
    private static final String PLUGIN_URL = "https://hub.star-dev.xyz/starclans";
    private static volatile String latestVersion;
    private static volatile String currentVersion;
    private static volatile boolean updateAvailable;
    private static volatile boolean listenerRegistered;
    private static volatile Lang lang;

    private UpdateChecker() {
    }

    public static void check(JavaPlugin plugin) {
        if (plugin instanceof dev.eministar.starclans.StarClans sc) {
            lang = sc.lang();
        }
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
        String header = lang != null ? lang.get("messages.update.header") : ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━" + ChatColor.YELLOW
                + ChatColor.BOLD + " StarClans " + ChatColor.GOLD + "Update"
                + ChatColor.DARK_GRAY + " ━━━━━━━━━━━━━━";
        player.sendMessage(header);
        player.sendMessage(lang != null ? lang.get("messages.update.current", "version", current)
                : ChatColor.GRAY + "Deine Version: " + ChatColor.RED + current);
        player.sendMessage(lang != null ? lang.get("messages.update.latest", "version", latest)
                : ChatColor.GRAY + "Neue Version: " + ChatColor.GREEN + latest);
        player.sendMessage(lang != null ? lang.get("messages.update.action")
                : ChatColor.GRAY + "Lade jetzt herunter oder kopiere die Version.");

        TextComponent download = new TextComponent(lang != null ? lang.get("messages.update.download") : "⤓ Download");
        download.setColor(ChatColor.AQUA);
        download.setBold(true);
        download.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, PLUGIN_URL));
        download.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(lang != null ? lang.get("messages.update.download_hover") : "Öffnet die Download-Seite").color(ChatColor.GRAY).create()));

        TextComponent spacer = new TextComponent("  ");

        TextComponent copy = new TextComponent(lang != null ? lang.get("messages.update.copy") : "⎘ Version kopieren");
        copy.setColor(ChatColor.GOLD);
        copy.setBold(true);
        copy.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, latest));
        copy.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(lang != null ? lang.get("messages.update.copy_hover") : "Kopiert die neue Version").color(ChatColor.GRAY).create()));

        player.spigot().sendMessage(download, spacer, copy);
        player.sendMessage(lang != null ? lang.get("messages.update.footer") : ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
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
