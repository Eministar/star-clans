package dev.eministar.starclans.discord;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.service.ClanService;
import dev.eministar.starclans.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DiscordWebhookClient {

    private static final String ROOT = "discord.webhook";
    private static final List<String> FALLBACK_EVENT_KEYS = List.of(
            "test", "create", "disband", "join", "leave", "kick", "transfer", "promote", "demote",
            "bank_deposit", "bank_withdraw", "tax_collected", "tax_changed", "home_set",
            "daily_leaderboard_digest", "leaderboard_top_changed"
    );

    private final StarClans plugin;
    private final HttpClient http;
    private final AtomicBoolean topChangeRunning = new AtomicBoolean(false);

    private volatile BukkitTask digestTask;
    private volatile String lastDigestStamp = "";
    private volatile TopSnapshot lastTopSnapshot;
    private volatile long lastTopCheckAtMillis;

    public DiscordWebhookClient(StarClans plugin) {
        this.plugin = plugin;
        this.http = HttpClient.newBuilder().build();
    }

    public void reload() {
        cancelDigestTask();
        lastDigestStamp = "";
        primeTopSnapshotAsync();

        if (plugin.getConfig().getBoolean(ROOT + ".digest.enabled", false)) {
            digestTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::tickDigest, 20L * 30L, 20L * 30L);
        }
    }

    public void shutdown() {
        cancelDigestTask();
    }

    public List<String> configuredEventKeys() {
        Set<String> keys = new LinkedHashSet<>(FALLBACK_EVENT_KEYS);
        var section = plugin.getConfig().getConfigurationSection(ROOT + ".events");
        if (section != null) {
            keys.addAll(section.getKeys(false));
        }
        return new ArrayList<>(keys);
    }

    public boolean sendEvent(String eventKey, Map<String, ?> values) {
        if (!plugin.getConfig().getBoolean(ROOT + ".enabled", false)) {
            return false;
        }
        if (!plugin.getConfig().isConfigurationSection(ROOT + ".events." + eventKey)) {
            return false;
        }

        Map<String, String> normalized = normalizeValues(values);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> sendResolvedEvent(eventKey, normalized));
        return true;
    }

    public boolean sendTestEvent(String eventKey) {
        String key = eventKey == null || eventKey.isBlank() ? "test" : eventKey.trim().toLowerCase(Locale.ROOT);
        if ("daily_leaderboard_digest".equals(key)) {
            return triggerDailyDigest(true);
        }
        return sendEvent(key, sampleValues());
    }

    public boolean triggerDailyDigest(boolean manual) {
        if (!plugin.getConfig().getBoolean(ROOT + ".enabled", false)) {
            return false;
        }
        if (!plugin.getConfig().isConfigurationSection(ROOT + ".events.daily_leaderboard_digest")) {
            return false;
        }
        if (!manual && !plugin.getConfig().getBoolean(ROOT + ".digest.enabled", false)) {
            return false;
        }
        if (plugin.repo() == null) {
            return false;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> sendDailyDigestNow(manual));
        return true;
    }

    public void handlePotentialTopChangeAsync() {
        if (!plugin.getConfig().getBoolean(ROOT + ".enabled", false)) return;
        if (!plugin.getConfig().getBoolean(ROOT + ".digest.announceTop1Changes", true)) return;
        if (!plugin.getConfig().isConfigurationSection(ROOT + ".events.leaderboard_top_changed")) return;
        if (plugin.repo() == null) return;

        long cooldownMs = Math.max(0L, plugin.getConfig().getLong(ROOT + ".digest.topChangeCooldownSeconds", 15L)) * 1000L;
        long now = System.currentTimeMillis();
        if (cooldownMs > 0L && now - lastTopCheckAtMillis < cooldownMs) {
            return;
        }
        lastTopCheckAtMillis = now;

        if (!topChangeRunning.compareAndSet(false, true)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                checkTopChangeNow();
            } finally {
                topChangeRunning.set(false);
            }
        });
    }

    private void sendResolvedEvent(String eventKey, Map<String, String> values) {
        FileConfiguration cfg = plugin.getConfig();
        Defaults defaults = readDefaults(cfg);
        EventConfig event = readEvent(cfg, eventKey);
        if (event == null || !event.enabled()) return;

        if (event.minAmount() > 0.0 && parseDouble(values.get("amount_raw")) < event.minAmount()) {
            return;
        }

        ChannelConfig channel = readChannel(cfg, defaults, event.channel());
        if (channel == null || !channel.enabled() || channel.url().isBlank()) return;

        Map<String, String> vars = new LinkedHashMap<>(values);
        vars.putIfAbsent("server", serverName());
        vars.put("event_key", eventKey);
        vars.put("channel", channel.key());

        String mentionRoleId = firstNonBlank(event.mentionRoleId(), channel.mentionRoleId(), defaults.mentionRoleId());
        String mention = mentionRoleId.isBlank() ? "" : "<@&" + mentionRoleId + ">";
        vars.put("mention", mention);

        Map<String, Object> payload = new LinkedHashMap<>();
        String content = truncate(resolveContent(event, mention, vars), 2000);
        if (!content.isBlank()) payload.put("content", content);
        if (!channel.username().isBlank()) payload.put("username", truncate(channel.username(), 80));
        if (!channel.avatarUrl().isBlank()) payload.put("avatar_url", truncate(channel.avatarUrl(), 2048));
        payload.put("allowed_mentions", buildAllowedMentions(defaults));
        payload.put("embeds", List.of(buildEmbed(event, defaults, vars)));

        postJson(channel.url(), defaults.timeoutMs(), defaults.retryAttempts(), defaults.retryDelayMs(), eventKey, payload);
    }

    private Map<String, Object> buildEmbed(EventConfig event, Defaults defaults, Map<String, String> vars) {
        Map<String, Object> embed = new LinkedHashMap<>();

        String title = truncate(apply(event.title(), vars), 256);
        String description = truncate(apply(event.description(), vars), 4096);
        if (!title.isBlank()) embed.put("title", title);
        if (!description.isBlank()) embed.put("description", description);
        embed.put("color", Integer.valueOf(event.color()));
        if (event.includeTimestamp()) embed.put("timestamp", Instant.now().toString());

        String authorName = truncate(apply(event.authorName(), vars), 256);
        String authorIconUrl = truncate(apply(event.authorIconUrl(), vars), 2048);
        if (!authorName.isBlank() || !authorIconUrl.isBlank()) {
            Map<String, Object> author = new LinkedHashMap<>();
            if (!authorName.isBlank()) author.put("name", authorName);
            if (!authorIconUrl.isBlank()) author.put("icon_url", authorIconUrl);
            embed.put("author", author);
        }

        String thumbnailUrl = truncate(apply(event.thumbnailUrl(), vars), 2048);
        if (!thumbnailUrl.isBlank()) {
            Map<String, Object> thumbnail = new LinkedHashMap<>();
            thumbnail.put("url", thumbnailUrl);
            embed.put("thumbnail", thumbnail);
        }

        String imageUrl = truncate(apply(event.imageUrl(), vars), 2048);
        if (!imageUrl.isBlank()) {
            Map<String, Object> image = new LinkedHashMap<>();
            image.put("url", imageUrl);
            embed.put("image", image);
        }

        String footerText = truncate(apply(firstNonBlank(event.footerText(), defaults.footerText()), vars), 2048);
        String footerIconUrl = truncate(apply(firstNonBlank(event.footerIconUrl(), defaults.footerIconUrl()), vars), 2048);
        if (!footerText.isBlank() || !footerIconUrl.isBlank()) {
            Map<String, Object> footer = new LinkedHashMap<>();
            if (!footerText.isBlank()) footer.put("text", footerText);
            if (!footerIconUrl.isBlank()) footer.put("icon_url", footerIconUrl);
            embed.put("footer", footer);
        }

        List<Map<String, Object>> fields = new ArrayList<>();
        for (FieldTemplate field : event.fields()) {
            String name = truncate(apply(field.name(), vars), 256);
            String value = truncate(apply(field.value(), vars), 1024);
            if (name.isBlank() || value.isBlank()) continue;
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", name);
            out.put("value", value);
            out.put("inline", Boolean.valueOf(field.inline()));
            fields.add(out);
        }
        if (!fields.isEmpty()) embed.put("fields", fields);

        return embed;
    }

    private void postJson(String url, int timeoutMs, int attempts, long retryDelayMs, String eventKey, Map<String, Object> payload) {
        String body = toJson(payload);
        int maxAttempts = Math.max(1, attempts);
        long delay = Math.max(250L, retryDelayMs);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMillis(timeoutMs))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int code = res.statusCode();
                if (code >= 200 && code < 300) return;
                if ((code == 429 || code >= 500) && attempt < maxAttempts) {
                    Thread.sleep(delay * attempt);
                    continue;
                }

                LoggerUtil.warn("Discord Webhook failed with status " + code + " for event '" + eventKey + "'.");
                return;
            } catch (Exception e) {
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(delay * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    continue;
                }
                LoggerUtil.warn("Discord Webhook error for event '" + eventKey + "': " + e.getMessage());
                return;
            }
        }
    }

    private void tickDigest() {
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.getBoolean(ROOT + ".enabled", false)) return;
        if (!cfg.getBoolean(ROOT + ".digest.enabled", false)) return;

        ZoneId zone = parseZone(cfg.getString(ROOT + ".digest.timezone", "UTC"));
        ZonedDateTime now = ZonedDateTime.now(zone);
        int hour = clamp(cfg.getInt(ROOT + ".digest.hour", 19), 0, 23);
        int minute = clamp(cfg.getInt(ROOT + ".digest.minute", 0), 0, 59);
        if (now.getHour() != hour || now.getMinute() != minute) return;

        String stamp = now.toLocalDate() + "|" + hour + "|" + minute + "|" + digestSort(cfg);
        if (stamp.equals(lastDigestStamp)) return;

        lastDigestStamp = stamp;
        sendDailyDigestNow(false);
    }

    private void sendDailyDigestNow(boolean manual) {
        ClanRepository repo = plugin.repo();
        ClanService service = plugin.service();
        if (repo == null || service == null) return;

        try {
            FileConfiguration cfg = plugin.getConfig();
            boolean byBalance = "balance".equals(digestSort(cfg));
            int top = Math.max(1, cfg.getInt(ROOT + ".digest.top", 5));
            List<ClanRepository.ClanLeaderboardRow> rows = byBalance ? repo.getTopClansByBalance(top) : repo.getTopClansByMembers(top);

            Map<String, String> vars = buildDigestValues(rows, byBalance, manual, service);
            sendResolvedEvent("daily_leaderboard_digest", vars);

            if (!rows.isEmpty()) {
                lastTopSnapshot = toSnapshot(rows.get(0));
            }
        } catch (Exception e) {
            LoggerUtil.warn("Daily leaderboard digest could not be generated: " + e.getMessage());
        }
    }

    private void checkTopChangeNow() {
        ClanRepository repo = plugin.repo();
        ClanService service = plugin.service();
        if (repo == null || service == null) return;

        try {
            boolean byBalance = "balance".equals(digestSort(plugin.getConfig()));
            List<ClanRepository.ClanLeaderboardRow> rows = byBalance ? repo.getTopClansByBalance(1) : repo.getTopClansByMembers(1);
            if (rows.isEmpty()) {
                lastTopSnapshot = null;
                return;
            }

            TopSnapshot current = toSnapshot(rows.get(0));
            TopSnapshot previous = lastTopSnapshot;
            lastTopSnapshot = current;

            if (previous == null || previous.clanId() == current.clanId()) return;

            Map<String, String> vars = new LinkedHashMap<>();
            vars.put("sort_key", byBalance ? "balance" : "members");
            vars.put("sort_label", byBalance ? "Balance" : "Members");
            vars.put("old_top_name", previous.name());
            vars.put("old_top_tag", previous.tag());
            vars.put("old_top_balance", service.money(previous.balance()));
            vars.put("old_top_balance_raw", rawNumber(previous.balance()));
            vars.put("old_top_members", String.valueOf(previous.members()));
            vars.put("new_top_name", current.name());
            vars.put("new_top_tag", current.tag());
            vars.put("new_top_balance", service.money(current.balance()));
            vars.put("new_top_balance_raw", rawNumber(current.balance()));
            vars.put("new_top_members", String.valueOf(current.members()));
            vars.put("clan_name", current.name());
            vars.put("clan_tag", current.tag());
            vars.put("clan_balance", service.money(current.balance()));
            vars.put("clan_balance_raw", rawNumber(current.balance()));
            vars.put("member_count", String.valueOf(current.members()));
            sendResolvedEvent("leaderboard_top_changed", vars);
        } catch (Exception e) {
            LoggerUtil.warn("Leaderboard top-change check failed: " + e.getMessage());
        }
    }

    private void primeTopSnapshotAsync() {
        if (plugin.repo() == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                boolean byBalance = "balance".equals(digestSort(plugin.getConfig()));
                List<ClanRepository.ClanLeaderboardRow> rows = byBalance ? plugin.repo().getTopClansByBalance(1) : plugin.repo().getTopClansByMembers(1);
                lastTopSnapshot = rows.isEmpty() ? null : toSnapshot(rows.get(0));
            } catch (Exception ignored) {
                lastTopSnapshot = null;
            }
        });
    }

    private Defaults readDefaults(FileConfiguration cfg) {
        String base = ROOT + ".defaults";
        boolean roles = cfg.getBoolean(base + ".allowedMentions.roles", true);
        boolean users = cfg.getBoolean(base + ".allowedMentions.users", false);
        boolean everyone = cfg.getBoolean(base + ".allowedMentions.everyone", false);
        return new Defaults(
                cfg.getString(base + ".username", cfg.getString(ROOT + ".username", "StarClans")),
                cfg.getString(base + ".avatarUrl", cfg.getString(ROOT + ".avatarUrl", "")),
                cfg.getString(base + ".footerText", "StarClans - {server}"),
                cfg.getString(base + ".footerIconUrl", ""),
                cfg.getString(base + ".mentionRoleId", cfg.getString(ROOT + ".mentionRoleId", "")),
                roles,
                users,
                everyone,
                Math.max(1000, cfg.getInt(base + ".timeoutMs", cfg.getInt(ROOT + ".timeoutMs", 5000))),
                Math.max(1, cfg.getInt(base + ".retry.attempts", 3)),
                Math.max(250L, cfg.getLong(base + ".retry.delayMs", 1500L))
        );
    }

    private ChannelConfig readChannel(FileConfiguration cfg, Defaults defaults, String key) {
        String base = ROOT + ".channels." + key;
        String defaultBase = ROOT + ".channels.default";
        return new ChannelConfig(
                key,
                cfg.getBoolean(base + ".enabled", true),
                firstNonBlank(cfg.getString(base + ".url", ""), cfg.getString(defaultBase + ".url", ""), cfg.getString(ROOT + ".url", "")),
                firstNonBlank(cfg.getString(base + ".username", ""), cfg.getString(defaultBase + ".username", ""), defaults.username()),
                firstNonBlank(cfg.getString(base + ".avatarUrl", ""), cfg.getString(defaultBase + ".avatarUrl", ""), defaults.avatarUrl()),
                firstNonBlank(cfg.getString(base + ".mentionRoleId", ""), cfg.getString(defaultBase + ".mentionRoleId", ""), defaults.mentionRoleId())
        );
    }

    private EventConfig readEvent(FileConfiguration cfg, String eventKey) {
        String base = ROOT + ".events." + eventKey;
        if (!cfg.isConfigurationSection(base)) return null;

        List<FieldTemplate> fields = new ArrayList<>();
        for (Map<?, ?> raw : cfg.getMapList(base + ".fields")) {
            Object name = raw.get("name");
            Object value = raw.get("value");
            if (name == null || value == null) continue;
            Object inlineValue = raw.containsKey("inline") ? raw.get("inline") : Boolean.FALSE;
            boolean inline = Boolean.parseBoolean(String.valueOf(inlineValue));
            fields.add(new FieldTemplate(String.valueOf(name), String.valueOf(value), inline));
        }

        return new EventConfig(
                cfg.getBoolean(base + ".enabled", true),
                cfg.getString(base + ".channel", "default"),
                cfg.getString(base + ".title", ""),
                cfg.getString(base + ".description", ""),
                cfg.getString(base + ".content", ""),
                parseColor(cfg.get(base + ".color"), 0x38BDF8),
                cfg.getString(base + ".mentionRoleId", ""),
                cfg.getString(base + ".author.name", ""),
                cfg.getString(base + ".author.iconUrl", ""),
                cfg.getString(base + ".thumbnailUrl", ""),
                cfg.getString(base + ".imageUrl", ""),
                cfg.getString(base + ".footer.text", ""),
                cfg.getString(base + ".footer.iconUrl", ""),
                cfg.getBoolean(base + ".includeTimestamp", true),
                Math.max(0.0, cfg.getDouble(base + ".minAmount", 0.0)),
                fields
        );
    }

    private Map<String, Object> buildAllowedMentions(Defaults defaults) {
        List<String> parse = new ArrayList<>();
        if (defaults.allowRoleMentions()) parse.add("roles");
        if (defaults.allowUserMentions()) parse.add("users");
        if (defaults.allowEveryone()) parse.add("everyone");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("parse", parse);
        return out;
    }

    private Map<String, String> buildDigestValues(List<ClanRepository.ClanLeaderboardRow> rows, boolean byBalance, boolean manual, ClanService service) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("manual", String.valueOf(manual));
        vars.put("sort_key", byBalance ? "balance" : "members");
        vars.put("sort_label", byBalance ? "Balance" : "Members");
        vars.put("top_count", String.valueOf(rows.size()));

        List<String> lines = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ClanRepository.ClanLeaderboardRow row = rows.get(i);
            String metric = byBalance ? service.money(row.balance) : String.valueOf(row.memberCount);
            lines.add("**#" + (i + 1) + "** " + row.name + " [`" + row.tag + "`] - " + metric);
            vars.put("top_" + (i + 1) + "_name", row.name);
            vars.put("top_" + (i + 1) + "_tag", row.tag);
            vars.put("top_" + (i + 1) + "_metric", metric);
            vars.put("top_" + (i + 1) + "_balance", service.money(row.balance));
            vars.put("top_" + (i + 1) + "_balance_raw", rawNumber(row.balance));
            vars.put("top_" + (i + 1) + "_members", String.valueOf(row.memberCount));
        }

        if (rows.isEmpty()) {
            vars.put("leaderboard_lines", "_No clans available yet._");
            vars.put("top_clan_name", "");
            vars.put("top_clan_tag", "");
            vars.put("top_clan_balance", "0");
            vars.put("top_clan_balance_raw", "0");
            vars.put("top_clan_members", "0");
        } else {
            ClanRepository.ClanLeaderboardRow top = rows.get(0);
            vars.put("leaderboard_lines", String.join("\n", lines));
            vars.put("top_clan_name", top.name);
            vars.put("top_clan_tag", top.tag);
            vars.put("top_clan_balance", service.money(top.balance));
            vars.put("top_clan_balance_raw", rawNumber(top.balance));
            vars.put("top_clan_members", String.valueOf(top.memberCount));
        }

        return vars;
    }

    private Map<String, String> sampleValues() {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("server", serverName());
        vars.put("actor", "Eministar");
        vars.put("target", "FrostWing");
        vars.put("clan_name", "StarKeep");
        vars.put("clan_tag", "STAR");
        vars.put("member_count", "12");
        vars.put("amount", "25.000$");
        vars.put("amount_raw", "25000");
        vars.put("balance_before", "100.000$");
        vars.put("balance_before_raw", "100000");
        vars.put("balance_after", "125.000$");
        vars.put("balance_after_raw", "125000");
        vars.put("clan_balance", "125.000$");
        vars.put("clan_balance_raw", "125000");
        vars.put("old_rate", "5%");
        vars.put("old_rate_raw", "5");
        vars.put("new_rate", "7.5%");
        vars.put("new_rate_raw", "7.5");
        vars.put("world", "world");
        vars.put("position", "x=128, y=72, z=-44");
        vars.put("leaderboard_lines", "**#1** StarKeep [`STAR`] - 125.000$\n**#2** NovaGuard [`NOVA`] - 96.000$");
        vars.put("sort_label", "Balance");
        vars.put("sort_key", "balance");
        vars.put("top_count", "5");
        vars.put("top_clan_name", "StarKeep");
        vars.put("top_clan_tag", "STAR");
        vars.put("top_clan_balance", "125.000$");
        vars.put("top_clan_balance_raw", "125000");
        vars.put("top_clan_members", "12");
        vars.put("old_top_name", "NovaGuard");
        vars.put("old_top_tag", "NOVA");
        vars.put("old_top_balance", "96.000$");
        vars.put("old_top_balance_raw", "96000");
        vars.put("old_top_members", "10");
        vars.put("new_top_name", "StarKeep");
        vars.put("new_top_tag", "STAR");
        vars.put("new_top_balance", "125.000$");
        vars.put("new_top_balance_raw", "125000");
        vars.put("new_top_members", "12");
        return vars;
    }

    private String resolveContent(EventConfig event, String mention, Map<String, String> vars) {
        String content = truncate(apply(event.content(), vars), 2000);
        if (content.isBlank()) {
            return mention;
        }
        return content;
    }

    private Map<String, String> normalizeValues(Map<String, ?> values) {
        Map<String, String> out = new LinkedHashMap<>();
        if (values == null) return out;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) continue;
            out.put(entry.getKey(), sanitize(entry.getValue()));
        }
        return out;
    }

    private String sanitize(Object value) {
        if (value == null) return "";
        String raw = String.valueOf(value);
        String translated = ChatColor.translateAlternateColorCodes('&', raw);
        String stripped = ChatColor.stripColor(translated);
        return stripped == null ? translated : stripped;
    }

    private String apply(String template, Map<String, String> vars) {
        String out = template == null ? "" : template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            out = out.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return out;
    }

    private String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + esc(s) + "\"";
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        if (value instanceof Iterable<?> iterable) {
            StringBuilder out = new StringBuilder("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) out.append(',');
                out.append(toJson(item));
                first = false;
            }
            out.append(']');
            return out.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) continue;
                if (!first) out.append(',');
                out.append(toJson(key)).append(':').append(toJson(entry.getValue()));
                first = false;
            }
            out.append('}');
            return out.toString();
        }
        return "\"" + esc(String.valueOf(value)) + "\"";
    }

    private String esc(String s) {
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

    private String serverName() {
        String configured = plugin.getConfig().getString(ROOT + ".defaults.serverName", "");
        return configured == null || configured.isBlank() ? Bukkit.getServer().getName() : configured;
    }

    private String digestSort(FileConfiguration cfg) {
        String raw = cfg.getString(ROOT + ".digest.sortBy", "balance");
        return "members".equalsIgnoreCase(raw) ? "members" : "balance";
    }

    private ZoneId parseZone(String raw) {
        try {
            return ZoneId.of(raw == null || raw.isBlank() ? "UTC" : raw.trim());
        } catch (Exception ignored) {
            return ZoneId.of("UTC");
        }
    }

    private int parseColor(Object raw, int fallback) {
        if (raw == null) return fallback;
        String text = String.valueOf(raw).trim();
        if (text.startsWith("#")) text = text.substring(1);
        if (text.startsWith("0x") || text.startsWith("0X")) text = text.substring(2);
        try {
            return Integer.parseInt(text, 16);
        } catch (Exception ignored) {
            try {
                return Integer.parseInt(text);
            } catch (Exception ignoredAgain) {
                return fallback;
            }
        }
    }

    private double parseDouble(String raw) {
        if (raw == null || raw.isBlank()) return 0.0;
        try {
            return Double.parseDouble(raw.replace(',', '.').replaceAll("[^0-9.\\-]", ""));
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    private String rawNumber(double value) {
        return String.format(Locale.US, "%.2f", Double.valueOf(value));
    }

    private TopSnapshot toSnapshot(ClanRepository.ClanLeaderboardRow row) {
        return new TopSnapshot(row.clanId, row.name, row.tag, row.balance, row.memberCount);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value == null ? "" : value;
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void cancelDigestTask() {
        BukkitTask task = digestTask;
        digestTask = null;
        if (task != null) task.cancel();
    }

    private record Defaults(
            String username,
            String avatarUrl,
            String footerText,
            String footerIconUrl,
            String mentionRoleId,
            boolean allowRoleMentions,
            boolean allowUserMentions,
            boolean allowEveryone,
            int timeoutMs,
            int retryAttempts,
            long retryDelayMs
    ) {
    }

    private record ChannelConfig(String key, boolean enabled, String url, String username, String avatarUrl, String mentionRoleId) {
    }

    private record FieldTemplate(String name, String value, boolean inline) {
    }

    private record EventConfig(
            boolean enabled,
            String channel,
            String title,
            String description,
            String content,
            int color,
            String mentionRoleId,
            String authorName,
            String authorIconUrl,
            String thumbnailUrl,
            String imageUrl,
            String footerText,
            String footerIconUrl,
            boolean includeTimestamp,
            double minAmount,
            List<FieldTemplate> fields
    ) {
    }

    private record TopSnapshot(long clanId, String name, String tag, double balance, int members) {
    }
}
