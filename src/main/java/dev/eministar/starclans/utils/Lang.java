package dev.eministar.starclans.utils;

import dev.eministar.starclans.model.MemberRole;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Lang {

    private final JavaPlugin plugin;
    private File file;
    private FileConfiguration cfg;
    private long lastModified;

    public Lang(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String fileName = plugin.getConfig().getString("lang.file", "lang.yml");
        if (fileName == null || fileName.isBlank()) {
            fileName = "lang.yml";
        }
        file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        cfg = YamlConfiguration.loadConfiguration(file);

        FileConfiguration defaults = loadDefaults(fileName);
        if (defaults != null) {
            cfg.setDefaults(defaults);
            cfg.options().copyDefaults(true);
            save();
        }

        lastModified = file.exists() ? file.lastModified() : 0L;
    }

    public String prefix() {
        ensureLatest();
        return get("prefix");
    }

    public String get(String path, Object... pairs) {
        ensureLatest();
        String raw = cfg.getString(path, "");
        return color(apply(raw, pairs));
    }

    public List<String> getList(String path, Object... pairs) {
        ensureLatest();
        List<String> list = cfg.getStringList(path);
        if (list == null || list.isEmpty()) {
            String single = cfg.getString(path);
            if (single == null) {
                return List.of();
            }
            list = new ArrayList<>();
            list.add(single);
        }
        List<String> out = new ArrayList<>(list.size());
        for (String s : list) {
            out.add(color(apply(s, pairs)));
        }
        return out;
    }

    public String prefixed(String path, Object... pairs) {
        ensureLatest();
        String msg = get(path, pairs);
        String p = prefix();
        return p + msg;
    }

    public String prefixedRaw(String message) {
        ensureLatest();
        return prefix() + color(message);
    }

    public String role(MemberRole role) {
        ensureLatest();
        String key = role == null ? "member" : role.name().toLowerCase(Locale.ROOT);
        return get("roles." + key);
    }

    public String roleColor(MemberRole role) {
        ensureLatest();
        String key = role == null ? "member" : role.name().toLowerCase(Locale.ROOT);
        return get("role_colors." + key);
    }

    private String apply(String s, Object... pairs) {
        if (s == null) {
            return "";
        }
        if (pairs == null || pairs.length == 0) {
            return s;
        }
        String out = s;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String key = String.valueOf(pairs[i]);
            String val = String.valueOf(pairs[i + 1]);
            out = out.replace("{" + key + "}", val);
        }
        return out;
    }

    private String color(String s) {
        if (s == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private void ensureLatest() {
        if (file == null || !file.exists()) {
            return;
        }
        long current = file.lastModified();
        if (current != 0L && current != lastModified) {
            reload();
        }
    }

    private void save() {
        try {
            cfg.save(file);
        } catch (Exception ignored) {
        }
    }

    private FileConfiguration loadDefaults(String fileName) {
        try {
            var in = plugin.getResource(fileName);
            if (in == null) {
                return null;
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        } catch (Exception ignored) {
            return null;
        }
    }
}
