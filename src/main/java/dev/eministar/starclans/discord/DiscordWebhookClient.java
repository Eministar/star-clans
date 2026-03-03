package dev.eministar.starclans.discord;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

public final class DiscordWebhookClient {

    private final StarClans plugin;
    private final HttpClient http;

    public DiscordWebhookClient(StarClans plugin) {
        this.plugin = plugin;
        this.http = HttpClient.newBuilder().build();
    }

    public void sendEvent(String eventKey, String title, String description, int color) {
        FileConfiguration cfg = plugin.getConfig();

        if (!cfg.getBoolean("discord.webhook.enabled", false)) return;
        if (!cfg.getBoolean("discord.webhook.events." + eventKey, false)) return;

        String url = cfg.getString("discord.webhook.url", "");
        if (url == null || url.isBlank()) return;

        String username = cfg.getString("discord.webhook.username", "StarClans");
        String avatarUrl = cfg.getString("discord.webhook.avatarUrl", "");
        String mentionRoleId = cfg.getString("discord.webhook.mentionRoleId", "");
        int timeoutMs = Math.max(1000, cfg.getInt("discord.webhook.timeoutMs", 5000));

        String content = mentionRoleId == null || mentionRoleId.isBlank() ? "" : "<@&" + mentionRoleId + ">";
        String payload = buildPayload(content, username, avatarUrl, title, description, color);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMillis(timeoutMs))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int code = res.statusCode();
                if (code < 200 || code >= 300) {
                    LoggerUtil.warn("Discord Webhook fehlgeschlagen (Status " + code + ") für Event '" + eventKey + "'.");
                }
            } catch (Exception e) {
                LoggerUtil.warn("Discord Webhook Fehler für Event '" + eventKey + "': " + e.getMessage());
            }
        });
    }

    private String buildPayload(String content, String username, String avatarUrl, String title, String description, int color) {
        return "{"
                + "\"content\":\"" + esc(content) + "\","
                + "\"username\":\"" + esc(username) + "\","
                + "\"avatar_url\":\"" + esc(avatarUrl) + "\","
                + "\"embeds\":[{"
                + "\"title\":\"" + esc(title) + "\","
                + "\"description\":\"" + esc(description) + "\","
                + "\"color\":" + color + ","
                + "\"timestamp\":\"" + Instant.now().toString() + "\""
                + "}]"
                + "}";
    }

    private String esc(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') out.append("\\\\");
            else if (c == '"') out.append("\\\"");
            else if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else if (c == '\t') out.append("\\t");
            else out.append(c);
        }
        return out.toString();
    }
}
