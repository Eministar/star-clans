package dev.eministar.starclans.database;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SQL {

    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9_]+$");
    private static final String CHARSET = "utf8mb4";
    private static final String COLLATE = "utf8mb4_unicode_ci";

    public static void initSchema(DataSource ds) throws Exception {
        if (ds == null) throw new IllegalStateException("DataSource is null");

        try (Connection con = ds.getConnection()) {
            try (Statement st = con.createStatement()) {
                st.execute("""
                            CREATE TABLE IF NOT EXISTS clans (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                name VARCHAR(16) NOT NULL,
                                tag VARCHAR(5) NOT NULL,
                                balance DOUBLE NOT NULL DEFAULT 0.0,
                                home_world VARCHAR(64) DEFAULT NULL,
                                home_x DOUBLE DEFAULT 0,
                                home_y DOUBLE DEFAULT 0,
                                home_z DOUBLE DEFAULT 0,
                                home_yaw FLOAT DEFAULT 0,
                                home_pitch FLOAT DEFAULT 0,
                                created_by CHAR(36) NOT NULL,
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                UNIQUE KEY uk_clans_name (name),
                                UNIQUE KEY uk_clans_tag (tag)
                            ) ENGINE=InnoDB DEFAULT CHARSET=%s COLLATE=%s
                        """.formatted(CHARSET, COLLATE));

                st.execute("""
                            CREATE TABLE IF NOT EXISTS clan_members (
                                clan_id BIGINT NOT NULL,
                                member_uuid CHAR(36) NOT NULL,
                                member_name VARCHAR(16) NOT NULL,
                                role VARCHAR(16) NOT NULL DEFAULT 'MEMBER',
                                joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                PRIMARY KEY (member_uuid),
                                KEY idx_members_clan (clan_id)
                            ) ENGINE=InnoDB DEFAULT CHARSET=%s COLLATE=%s
                        """.formatted(CHARSET, COLLATE));

                st.execute("""
                            CREATE TABLE IF NOT EXISTS clan_invites (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                clan_id BIGINT NOT NULL,
                                target_uuid CHAR(36) NOT NULL,
                                inviter_uuid CHAR(36) NOT NULL,
                                requires_approval TINYINT(1) NOT NULL DEFAULT 0,
                                pending_approval TINYINT(1) NOT NULL DEFAULT 0,
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                expires_at TIMESTAMP NOT NULL,
                                KEY idx_inv_target (target_uuid),
                                KEY idx_inv_clan (clan_id)
                            ) ENGINE=InnoDB DEFAULT CHARSET=%s COLLATE=%s
                        """.formatted(CHARSET, COLLATE));

                st.execute("""
                            CREATE TABLE IF NOT EXISTS clan_settings (
                                clan_id BIGINT NOT NULL PRIMARY KEY,
                                open_invites TINYINT(1) NOT NULL DEFAULT 1,
                                motd VARCHAR(64) NOT NULL DEFAULT '',
                                tax_rate DOUBLE NOT NULL DEFAULT 0.0
                            ) ENGINE=InnoDB DEFAULT CHARSET=%s COLLATE=%s
                        """.formatted(CHARSET, COLLATE));

                st.execute("""
                            CREATE TABLE IF NOT EXISTS clan_cosmetics (
                                clan_id BIGINT NOT NULL PRIMARY KEY,
                                tag_style VARCHAR(64) NOT NULL DEFAULT '',
                                chat_suffix VARCHAR(64) NOT NULL DEFAULT ''
                            ) ENGINE=InnoDB DEFAULT CHARSET=%s COLLATE=%s
                        """.formatted(CHARSET, COLLATE));

                st.execute("""
                            CREATE TABLE IF NOT EXISTS clan_bank_history (
                                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                clan_id BIGINT NOT NULL,
                                actor_uuid CHAR(36) DEFAULT NULL,
                                actor_name VARCHAR(16) NOT NULL DEFAULT '',
                                type VARCHAR(24) NOT NULL,
                                amount DOUBLE NOT NULL DEFAULT 0.0,
                                balance_after DOUBLE NOT NULL DEFAULT 0.0,
                                note VARCHAR(128) NOT NULL DEFAULT '',
                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                KEY idx_bank_history_clan (clan_id),
                                KEY idx_bank_history_created (created_at)
                            ) ENGINE=InnoDB DEFAULT CHARSET=%s COLLATE=%s
                        """.formatted(CHARSET, COLLATE));
            }
        }

        migrate(ds);
        ensureUpToDate(ds);

        requireColumn(ds, "clans", "id");
        requireColumn(ds, "clans", "name");
        requireColumn(ds, "clans", "tag");
        requireColumn(ds, "clans", "balance");
        requireColumn(ds, "clans", "home_world");
        requireColumn(ds, "clans", "created_by");

        requireColumn(ds, "clan_members", "member_uuid");
        requireColumn(ds, "clan_members", "member_name");
        requireColumn(ds, "clan_members", "clan_id");

        requireColumn(ds, "clan_invites", "id");
        requireColumn(ds, "clan_invites", "target_uuid");
        requireColumn(ds, "clan_invites", "clan_id");
        requireColumn(ds, "clan_invites", "expires_at");

        requireColumn(ds, "clan_settings", "clan_id");
        requireColumn(ds, "clan_settings", "open_invites");
        requireColumn(ds, "clan_settings", "motd");
        requireColumn(ds, "clan_settings", "tax_rate");

        requireColumn(ds, "clan_cosmetics", "clan_id");
        requireColumn(ds, "clan_cosmetics", "tag_style");
        requireColumn(ds, "clan_cosmetics", "chat_suffix");

        requireColumn(ds, "clan_bank_history", "id");
        requireColumn(ds, "clan_bank_history", "clan_id");
        requireColumn(ds, "clan_bank_history", "type");
        requireColumn(ds, "clan_bank_history", "amount");
        requireColumn(ds, "clan_bank_history", "balance_after");
        requireColumn(ds, "clan_bank_history", "created_at");
    }

    private static void ensureUpToDate(DataSource ds) throws Exception {
        try (Connection con = ds.getConnection()) {
            ensureColumn(con, "clans", "created_at", "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
            ensureColumn(con, "clans", "balance", "DOUBLE NOT NULL DEFAULT 0.0");
            ensureColumn(con, "clans", "home_world", "VARCHAR(64) DEFAULT NULL");
            ensureColumn(con, "clans", "home_x", "DOUBLE DEFAULT 0");
            ensureColumn(con, "clans", "home_y", "DOUBLE DEFAULT 0");
            ensureColumn(con, "clans", "home_z", "DOUBLE DEFAULT 0");
            ensureColumn(con, "clans", "home_yaw", "FLOAT DEFAULT 0");
            ensureColumn(con, "clans", "home_pitch", "FLOAT DEFAULT 0");
            ensureUniqueIndex(con, "clans", "uk_clans_name", "name");
            ensureUniqueIndex(con, "clans", "uk_clans_tag", "tag");

            ensureColumn(con, "clan_members", "role", "VARCHAR(16) NOT NULL DEFAULT 'MEMBER'");
            ensureColumn(con, "clan_members", "joined_at", "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
            ensureIndex(con, "clan_members", "idx_members_clan", "clan_id");

            ensureColumn(con, "clan_invites", "created_at", "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
            ensureColumn(con, "clan_invites", "requires_approval", "TINYINT(1) NOT NULL DEFAULT 0");
            ensureColumn(con, "clan_invites", "pending_approval", "TINYINT(1) NOT NULL DEFAULT 0");
            ensureIndex(con, "clan_invites", "idx_inv_target", "target_uuid");
            ensureIndex(con, "clan_invites", "idx_inv_clan", "clan_id");

            ensureColumn(con, "clan_settings", "open_invites", "TINYINT(1) NOT NULL DEFAULT 1");
            ensureColumn(con, "clan_settings", "motd", "VARCHAR(64) NOT NULL DEFAULT ''");
            ensureColumn(con, "clan_settings", "tax_rate", "DOUBLE NOT NULL DEFAULT 0.0");

            ensureColumn(con, "clan_cosmetics", "tag_style", "VARCHAR(64) NOT NULL DEFAULT ''");
            ensureColumn(con, "clan_cosmetics", "chat_suffix", "VARCHAR(64) NOT NULL DEFAULT ''");

            ensureColumn(con, "clan_bank_history", "actor_uuid", "CHAR(36) DEFAULT NULL");
            ensureColumn(con, "clan_bank_history", "actor_name", "VARCHAR(16) NOT NULL DEFAULT ''");
            ensureColumn(con, "clan_bank_history", "type", "VARCHAR(24) NOT NULL");
            ensureColumn(con, "clan_bank_history", "amount", "DOUBLE NOT NULL DEFAULT 0.0");
            ensureColumn(con, "clan_bank_history", "balance_after", "DOUBLE NOT NULL DEFAULT 0.0");
            ensureColumn(con, "clan_bank_history", "note", "VARCHAR(128) NOT NULL DEFAULT ''");
            ensureColumn(con, "clan_bank_history", "created_at", "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
            ensureIndex(con, "clan_bank_history", "idx_bank_history_clan", "clan_id");
            ensureIndex(con, "clan_bank_history", "idx_bank_history_created", "created_at");
        }
    }

    private static void migrate(DataSource ds) throws Exception {
        try (Connection con = ds.getConnection()) {
            renameColumn(con, "clans", "uuid", "created_by", "CHAR(36) NOT NULL");
            renameColumn(con, "clans", "owner_uuid", "created_by", "CHAR(36) NOT NULL");
            ensureColumn(con, "clans", "created_by", "CHAR(36) NOT NULL");

            renameColumn(con, "clan_members", "uuid", "member_uuid", "CHAR(36) NOT NULL");
            renameColumn(con, "clan_members", "player_uuid", "member_uuid", "CHAR(36) NOT NULL");
            renameColumn(con, "clan_members", "user_uuid", "member_uuid", "CHAR(36) NOT NULL");
            ensureColumn(con, "clan_members", "member_uuid", "CHAR(36) NOT NULL");

            renameColumn(con, "clan_members", "username", "member_name", "VARCHAR(16) NOT NULL");
            renameColumn(con, "clan_members", "name", "member_name", "VARCHAR(16) NOT NULL");
            ensureColumn(con, "clan_members", "member_name", "VARCHAR(16) NOT NULL");

            renameColumn(con, "clan_invites", "uuid", "target_uuid", "CHAR(36) NOT NULL");
            renameColumn(con, "clan_invites", "member_uuid", "target_uuid", "CHAR(36) NOT NULL");
            ensureColumn(con, "clan_invites", "target_uuid", "CHAR(36) NOT NULL");

            renameColumn(con, "clan_invites", "sender_uuid", "inviter_uuid", "CHAR(36) NOT NULL");
            ensureColumn(con, "clan_invites", "inviter_uuid", "CHAR(36) NOT NULL");
        }
    }

    private static void ensureColumn(Connection con, String table, String col, String def) throws Exception {
        if (hasColumn(con, table, col)) return;
        try (Statement st = con.createStatement()) {
            st.execute("ALTER TABLE " + q(table) + " ADD COLUMN " + q(col) + " " + def);
        }
    }

    private static void renameColumn(Connection con, String table, String oldCol, String newCol, String def) throws Exception {
        if (!hasColumn(con, table, oldCol)) return;
        if (hasColumn(con, table, newCol)) return;
        try (Statement st = con.createStatement()) {
            st.execute("ALTER TABLE " + q(table) + " CHANGE " + q(oldCol) + " " + q(newCol) + " " + def);
        }
    }

    private static void ensureIndex(Connection con, String table, String indexName, String column) throws Exception {
        if (hasIndex(con, table, indexName)) return;
        try (Statement st = con.createStatement()) {
            st.execute("CREATE INDEX " + q(indexName) + " ON " + q(table) + " (" + q(column) + ")");
        }
    }

    private static void ensureUniqueIndex(Connection con, String table, String indexName, String column) throws Exception {
        if (hasIndex(con, table, indexName)) return;
        try (Statement st = con.createStatement()) {
            st.execute("CREATE UNIQUE INDEX " + q(indexName) + " ON " + q(table) + " (" + q(column) + ")");
        }
    }

    private static boolean hasIndex(Connection con, String table, String indexName) throws Exception {
        try (PreparedStatement ps = con.prepareStatement("""
                    SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                """)) {
            ps.setString(1, table);
            ps.setString(2, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static boolean hasColumn(Connection con, String table, String col) throws Exception {
        try (PreparedStatement ps = con.prepareStatement("""
                    SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """)) {
            ps.setString(1, table);
            ps.setString(2, col);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static void requireColumn(DataSource ds, String table, String col) throws Exception {
        try (Connection con = ds.getConnection()) {
            if (!hasColumn(con, table, col)) {
                throw new IllegalStateException("Schema mismatch: missing column " + table + "." + col);
            }
        }
    }

    private static String q(String id) {
        if (id == null) throw new IllegalStateException("Identifier null");
        String x = id.toLowerCase(Locale.ROOT);
        if (!SAFE.matcher(x).matches()) throw new IllegalStateException("Unsafe identifier: " + id);
        return "`" + id + "`";
    }
}
