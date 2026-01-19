package dev.eministar.starclans.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;

public final class HikariProvider {

    private static HikariDataSource dataSource;

    public static void init(JavaPlugin plugin) {
        FileConfiguration c = plugin.getConfig();

        if (!c.getBoolean("database.enabled", true)) {
            dataSource = null;
            return;
        }

        String host = c.getString("database.host", "127.0.0.1");
        int port = c.getInt("database.port", 3306);
        String db = c.getString("database.name", "starclans");
        String user = c.getString("database.username", "root");
        String pass = c.getString("database.password", "");

        int maxPool = c.getInt("database.pool.maxPoolSize", 10);
        int minIdle = c.getInt("database.pool.minIdle", 2);
        long connTimeout = c.getLong("database.pool.connectionTimeoutMs", 10000);
        long idleTimeout = c.getLong("database.pool.idleTimeoutMs", 600000);
        long maxLifetime = c.getLong("database.pool.maxLifetimeMs", 1800000);

        String jdbcUrl =
                "jdbc:mariadb://" + host + ":" + port + "/" + db +
                        "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC";

        HikariConfig cfg = new HikariConfig();
        cfg.setDriverClassName("org.mariadb.jdbc.Driver");
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(user);
        cfg.setPassword(pass);

        cfg.setMaximumPoolSize(maxPool);
        cfg.setMinimumIdle(minIdle);
        cfg.setConnectionTimeout(connTimeout);
        cfg.setIdleTimeout(idleTimeout);
        cfg.setMaxLifetime(maxLifetime);

        cfg.setPoolName("StarClans-Pool");

        dataSource = new HikariDataSource(cfg);
    }

    public static DataSource get() {
        return dataSource;
    }

    public static boolean isReady() {
        return dataSource != null;
    }

    public static void shutdown() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }
}
