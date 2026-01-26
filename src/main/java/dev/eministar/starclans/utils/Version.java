package dev.eministar.starclans.utils;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.util.Properties;

public final class Version {
    private static String version = "unknown";
    private static final String POM_PROPERTIES_PATH =
            "/META-INF/maven/dev.eministar/StarClans/pom.properties";

    private Version() {}

    public static void init(JavaPlugin plugin) {
        String pomVersion = loadPomVersion();
        if (pomVersion != null && !pomVersion.isBlank()) {
            version = pomVersion.trim();
            return;
        }

        if (plugin != null && plugin.getDescription() != null) {
            version = plugin.getDescription().getVersion();
        }
    }

    public static String get() {
        return version;
    }

    private static String loadPomVersion() {
        try (InputStream stream = Version.class.getResourceAsStream(POM_PROPERTIES_PATH)) {
            if (stream == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(stream);
            return properties.getProperty("version");
        } catch (Exception ignored) {
            return null;
        }
    }
}
