package dev.eministar.starclans.database;

import dev.eministar.starclans.model.MemberRole;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

public final class ClanRepository {

    public static final class InviteRow {
        public final long id;
        public final long clanId;
        public final String clanName;
        public final String clanTag;

        public InviteRow(long id, long clanId, String clanName, String clanTag) {
            this.id = id;
            this.clanId = clanId;
            this.clanName = clanName;
            this.clanTag = clanTag;
        }
    }

    public static final class MemberRow {
        public final UUID uuid;
        public final String name;
        public final MemberRole role;

        public MemberRow(UUID uuid, String name, MemberRole role) {
            this.uuid = uuid;
            this.name = name;
            this.role = role;
        }
    }

    public static final class ClanSettingsRow {
        public final boolean openInvite;
        public final boolean friendlyFire;
        public final String motd;

        public ClanSettingsRow(boolean openInvite, boolean friendlyFire, String motd) {
            this.openInvite = openInvite;
            this.friendlyFire = friendlyFire;
            this.motd = motd == null ? "" : motd;
        }
    }

    public static final class ClanCosmeticsRow {
        public final String tagStyle;
        public final String chatSuffix;

        public ClanCosmeticsRow(String tagStyle, String chatSuffix) {
            this.tagStyle = tagStyle == null ? "" : tagStyle;
            this.chatSuffix = chatSuffix == null ? "" : chatSuffix;
        }
    }

    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9_]+$");

    private final DataSource ds;

    private volatile boolean resolved;
    private final Object lock = new Object();

    private String tClans = "clans";
    private String tMembers = "clan_members";
    private String tInvites = "clan_invites";
    private String tSettings = "clan_settings";

    private String cClanIdClans;
    private String cClanName;
    private String cClanTag;
    private String cClanCreatedBy;


    private String cMembersClanId;
    private String cMemberUuid;
    private String cMemberName;
    private String cMemberRole;

    private String cInviteId;
    private String cInviteClanId;
    private String cInviteTargetUuid;
    private String cInviteInviterUuid;
    private String cInviteExpiresAt;
    private String cInviteCreatedAt;

    private String cSettingsClanId;
    private String cSettingsOpenInvite;
    private String cSettingsFriendlyFire;
    private String cSettingsMotd;

    private String tCosmetics = "clan_cosmetics";
    private String cCosClanId;
    private String cCosTagStyle;
    private String cCosChatSuffix;


    public ClanRepository(DataSource ds) {
        if (ds == null) throw new IllegalStateException("DataSource is null");
        this.ds = ds;
    }

    private Connection c() throws Exception {
        return ds.getConnection();
    }

    private void ensureResolved() throws Exception {
        if (resolved) return;
        synchronized (lock) {
            if (resolved) return;

            Set<String> clansCols = columns(tClans);
            Set<String> membersCols = columns(tMembers);
            Set<String> invitesCols = columns(tInvites);
            Set<String> settingsCols = columns(tSettings);

            cClanIdClans = pick(clansCols, "id", "clan_id", "clanId");
            cClanName = pick(clansCols, "name", "clan_name", "clanName");
            cClanTag = pick(clansCols, "tag", "clan_tag", "clanTag");
            cClanCreatedBy = pick(clansCols, "created_by", "createdBy", "creator_uuid", "created_by_uuid", "owner_uuid", "owner");


            cMembersClanId = pick(membersCols, "clan_id", "clanId", "id_clan");
            cMemberUuid = pick(membersCols, "member_uuid", "uuid", "player_uuid", "user_uuid", "member", "player");
            cMemberName = pick(membersCols, "member_name", "name", "username", "player_name");
            cMemberRole = pick(membersCols, "role", "member_role", "rank");

            cInviteId = pick(invitesCols, "id", "invite_id", "inviteId");
            cInviteClanId = pick(invitesCols, "clan_id", "clanId");
            cInviteTargetUuid = pick(invitesCols, "target_uuid", "uuid", "member_uuid", "player_uuid", "user_uuid", "target");
            cInviteInviterUuid = pick(invitesCols, "inviter_uuid", "sender_uuid", "from_uuid", "inviter", "sender");
            cInviteExpiresAt = pick(invitesCols, "expires_at", "expire_at", "expires", "expire");
            cInviteCreatedAt = pick(invitesCols, "created_at", "created", "time", "createdAt");

            cSettingsClanId = pick(settingsCols, "clan_id", "clanId");
            cSettingsOpenInvite = pick(settingsCols, "open_invite", "open_invites", "openinvites", "open");
            cSettingsFriendlyFire = pick(settingsCols, "friendly_fire", "friendlyfire", "pvp", "ff");
            cSettingsMotd = pick(settingsCols, "motd", "clan_motd", "message", "msg");

            Set<String> cosCols = columns(tCosmetics);

            cCosClanId = pick(cosCols, "clan_id", "clanId");
            cCosTagStyle = pick(cosCols, "tag_style", "tagStyle");
            cCosChatSuffix = pick(cosCols, "chat_suffix", "chatSuffix");

            require(tCosmetics, cCosClanId, "clan_id");
            require(tCosmetics, cCosTagStyle, "tag_style");
            require(tCosmetics, cCosChatSuffix, "chat_suffix");

            require(tClans, cClanIdClans, "id/clan_id");
            require(tClans, cClanName, "name");
            require(tClans, cClanTag, "tag");

            require(tMembers, cMembersClanId, "clan_id");
            require(tMembers, cMemberUuid, "member_uuid/uuid");
            require(tMembers, cMemberName, "member_name/name");
            require(tMembers, cMemberRole, "role");

            require(tInvites, cInviteId, "id");
            require(tInvites, cInviteClanId, "clan_id");
            require(tInvites, cInviteTargetUuid, "target_uuid/uuid");
            require(tInvites, cInviteInviterUuid, "inviter_uuid/sender_uuid");
            require(tInvites, cInviteExpiresAt, "expires_at");

            require(tSettings, cSettingsClanId, "clan_id");
            if (cSettingsMotd == null) cSettingsMotd = "motd";

            resolved = true;
        }
    }

    private void require(String table, String col, String expected) {
        if (col == null) throw new IllegalStateException("Schema mismatch in " + table + " (missing " + expected + ")");
    }

    private Set<String> columns(String table) throws Exception {
        Set<String> out = new HashSet<>();
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?"
             )) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1).toLowerCase(Locale.ROOT));
            }
        }
        if (out.isEmpty()) throw new IllegalStateException("Table missing: " + table);
        return out;
    }

    private String pick(Set<String> cols, String... candidates) {
        for (String c : candidates) {
            if (c == null) continue;
            if (cols.contains(c.toLowerCase(Locale.ROOT))) return c;
        }
        return null;
    }

    private String q(String name) {
        if (name == null || !SAFE.matcher(name).matches()) throw new IllegalStateException("Unsafe identifier: " + name);
        return "`" + name + "`";
    }

    private boolean readBool(ResultSet rs, String col) throws Exception {
        if (col == null) return false;
        try {
            return rs.getBoolean(col);
        } catch (Exception ignored) {
            try {
                return rs.getInt(col) == 1;
            } catch (Exception ignored2) {
                return false;
            }
        }
    }

    private String readString(ResultSet rs, String col) throws Exception {
        if (col == null) return "";
        try {
            String s = rs.getString(col);
            return s == null ? "" : s;
        } catch (Exception ignored) {
            return "";
        }
    }

    public long getClanIdByMember(UUID member) throws Exception {
        ensureResolved();
        String sql = "SELECT " + q(cMembersClanId) + " FROM " + q(tMembers) + " WHERE " + q(cMemberUuid) + "=? LIMIT 1";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, member.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    public String[] getClanNameTag(long clanId) throws Exception {
        ensureResolved();
        String sql = "SELECT " + q(cClanName) + "," + q(cClanTag) + " FROM " + q(tClans) + " WHERE " + q(cClanIdClans) + "=? LIMIT 1";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new String[]{"", ""};
                return new String[]{rs.getString(1), rs.getString(2)};
            }
        }
    }

    public MemberRole getRole(UUID member) throws Exception {
        ensureResolved();
        String sql = "SELECT " + q(cMemberRole) + " FROM " + q(tMembers) + " WHERE " + q(cMemberUuid) + "=? LIMIT 1";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, member.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return MemberRole.MEMBER;
                String r = rs.getString(1);
                try {
                    return MemberRole.valueOf(r);
                } catch (Exception ignored) {
                    return MemberRole.MEMBER;
                }
            }
        }
    }

    public int countMembers(long clanId) throws Exception {
        ensureResolved();
        String sql = "SELECT COUNT(*) FROM " + q(tMembers) + " WHERE " + q(cMembersClanId) + "=?";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public boolean nameExists(String name) throws Exception {
        ensureResolved();
        String sql = "SELECT 1 FROM " + q(tClans) + " WHERE " + q(cClanName) + "=? LIMIT 1";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean tagExists(String tag) throws Exception {
        ensureResolved();
        String sql = "SELECT 1 FROM " + q(tClans) + " WHERE " + q(cClanTag) + "=? LIMIT 1";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, tag);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public long createClan(String name, String tag, UUID creator, String creatorName) throws Exception {
        ensureResolved();
        try (Connection con = c()) {
            con.setAutoCommit(false);
            try {
                long clanId;
                boolean hasCreatedBy = cClanCreatedBy != null;

                String sqlClan = hasCreatedBy
                        ? "INSERT INTO " + q(tClans) + " (" + q(cClanName) + "," + q(cClanTag) + "," + q(cClanCreatedBy) + ") VALUES (?,?,?)"
                        : "INSERT INTO " + q(tClans) + " (" + q(cClanName) + "," + q(cClanTag) + ") VALUES (?,?)";

                try (PreparedStatement ps = con.prepareStatement(sqlClan, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name);
                    ps.setString(2, tag);
                    if (hasCreatedBy) ps.setString(3, creator.toString());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) clanId = rs.getLong(1);
                        else throw new IllegalStateException("No generated clan id");
                    }
                }

                String sqlMember = "INSERT INTO " + q(tMembers) + " (" + q(cMembersClanId) + "," + q(cMemberUuid) + "," + q(cMemberName) + "," + q(cMemberRole) + ") VALUES (?,?,?,?)";
                try (PreparedStatement ps = con.prepareStatement(sqlMember)) {
                    ps.setLong(1, clanId);
                    ps.setString(2, creator.toString());
                    ps.setString(3, creatorName == null ? "Unknown" : creatorName);
                    ps.setString(4, MemberRole.LEADER.name());
                    ps.executeUpdate();
                }

                String sqlSettings = "INSERT IGNORE INTO " + q(tSettings) + " (" + q(cSettingsClanId) + ") VALUES (?)";
                try (PreparedStatement ps = con.prepareStatement(sqlSettings)) {
                    ps.setLong(1, clanId);
                    ps.executeUpdate();
                }

                String sqlCos = "INSERT IGNORE INTO " + q(tCosmetics) + " (" + q(cCosClanId) + ") VALUES (?)";
                try (PreparedStatement ps = con.prepareStatement(sqlCos)) {
                    ps.setLong(1, clanId);
                    ps.executeUpdate();
                }

                con.commit();
                return clanId;
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public void joinClan(long clanId, UUID member, String name) throws Exception {
        ensureResolved();
        String sql = "INSERT INTO " + q(tMembers) + " (" + q(cMembersClanId) + "," + q(cMemberUuid) + "," + q(cMemberName) + "," + q(cMemberRole) + ") VALUES (?,?,?,?)";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, clanId);
            ps.setString(2, member.toString());
            ps.setString(3, name == null ? "Unknown" : name);
            ps.setString(4, MemberRole.MEMBER.name());
            ps.executeUpdate();
        }
    }

    public void removeMember(UUID member) throws Exception {
        ensureResolved();
        String sql = "DELETE FROM " + q(tMembers) + " WHERE " + q(cMemberUuid) + "=?";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, member.toString());
            ps.executeUpdate();
        }
    }

    public ClanCosmeticsRow getCosmetics(long clanId) throws Exception {
        ensureResolved();
        String sql = "SELECT " + q(cCosTagStyle) + "," + q(cCosChatSuffix) + " FROM " + q(tCosmetics) + " WHERE " + q(cCosClanId) + "=? LIMIT 1";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new ClanCosmeticsRow("", "");
                return new ClanCosmeticsRow(rs.getString(1), rs.getString(2));
            }
        }
    }

    public void ensureCosmeticsRow(long clanId) throws Exception {
        ensureResolved();
        String sql = "INSERT IGNORE INTO " + q(tCosmetics) + " (" + q(cCosClanId) + ") VALUES (?)";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, clanId);
            ps.executeUpdate();
        }
    }

    public void setTagStyle(long clanId, String style) throws Exception {
        ensureResolved();
        ensureCosmeticsRow(clanId);
        String sql = "UPDATE " + q(tCosmetics) + " SET " + q(cCosTagStyle) + "=? WHERE " + q(cCosClanId) + "=?";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, style == null ? "" : style);
            ps.setLong(2, clanId);
            ps.executeUpdate();
        }
    }

    public void setChatSuffix(long clanId, String suffix) throws Exception {
        ensureResolved();
        ensureCosmeticsRow(clanId);
        String sql = "UPDATE " + q(tCosmetics) + " SET " + q(cCosChatSuffix) + "=? WHERE " + q(cCosClanId) + "=?";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, suffix == null ? "" : suffix);
            ps.setLong(2, clanId);
            ps.executeUpdate();
        }
    }


    public void disband(long clanId) throws Exception {
        ensureResolved();
        try (Connection con = c()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + q(tInvites) + " WHERE " + q(cInviteClanId) + "=?")) {
                    ps.setLong(1, clanId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + q(tMembers) + " WHERE " + q(cMembersClanId) + "=?")) {
                    ps.setLong(1, clanId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + q(tSettings) + " WHERE " + q(cSettingsClanId) + "=?")) {
                    ps.setLong(1, clanId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + q(tClans) + " WHERE " + q(cClanIdClans) + "=?")) {
                    ps.setLong(1, clanId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM " + q(tCosmetics) + " WHERE " + q(cCosClanId) + "=?")) {
                    ps.setLong(1, clanId);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public void cleanupExpiredInvites() throws Exception {
        ensureResolved();
        String sql = "DELETE FROM " + q(tInvites) + " WHERE " + q(cInviteExpiresAt) + " < NOW()";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    public int countInvites(UUID target) throws Exception {
        ensureResolved();
        cleanupExpiredInvites();
        String sql = "SELECT COUNT(*) FROM " + q(tInvites) + " WHERE " + q(cInviteTargetUuid) + "=? AND " + q(cInviteExpiresAt) + " >= NOW()";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void createInvite(long clanId, UUID target, UUID inviter, int minutes) throws Exception {
        ensureResolved();
        cleanupExpiredInvites();
        int m = Math.max(1, minutes);
        String sql = "INSERT INTO " + q(tInvites) + " (" + q(cInviteClanId) + "," + q(cInviteTargetUuid) + "," + q(cInviteInviterUuid) + "," + q(cInviteExpiresAt) + ") VALUES (?,?,?,DATE_ADD(NOW(), INTERVAL ? MINUTE))";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, clanId);
            ps.setString(2, target.toString());
            ps.setString(3, inviter.toString());
            ps.setInt(4, m);
            ps.executeUpdate();
        }
    }

    public InviteRow getInviteById(long inviteId, UUID target) throws Exception {
        ensureResolved();
        cleanupExpiredInvites();
        String sql = "SELECT i." + q(cInviteId) + ", i." + q(cInviteClanId) + ", c." + q(cClanName) + ", c." + q(cClanTag) +
                " FROM " + q(tInvites) + " i JOIN " + q(tClans) + " c ON c." + q(cClanIdClans) + " = i." + q(cInviteClanId) +
                " WHERE i." + q(cInviteId) + "=? AND i." + q(cInviteTargetUuid) + "=? AND i." + q(cInviteExpiresAt) + " >= NOW() LIMIT 1";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, inviteId);
            ps.setString(2, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new InviteRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4));
            }
        }
    }

    public List<InviteRow> getInvites(UUID target) throws Exception {
        ensureResolved();
        cleanupExpiredInvites();

        String order = cInviteCreatedAt != null ? " ORDER BY i." + q(cInviteCreatedAt) + " DESC" : " ORDER BY i." + q(cInviteId) + " DESC";

        String sql = "SELECT i." + q(cInviteId) + ", i." + q(cInviteClanId) + ", c." + q(cClanName) + ", c." + q(cClanTag) +
                " FROM " + q(tInvites) + " i JOIN " + q(tClans) + " c ON c." + q(cClanIdClans) + " = i." + q(cInviteClanId) +
                " WHERE i." + q(cInviteTargetUuid) + "=? AND i." + q(cInviteExpiresAt) + " >= NOW()" + order;

        List<InviteRow> out = new ArrayList<>();
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new InviteRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4)));
            }
        }
        return out;
    }

    public void deleteInvite(long inviteId) throws Exception {
        ensureResolved();
        String sql = "DELETE FROM " + q(tInvites) + " WHERE " + q(cInviteId) + "=?";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, inviteId);
            ps.executeUpdate();
        }
    }

    public List<MemberRow> listMembers(long clanId) throws Exception {
        ensureResolved();
        String sql = "SELECT " + q(cMemberUuid) + "," + q(cMemberName) + "," + q(cMemberRole) +
                " FROM " + q(tMembers) + " WHERE " + q(cMembersClanId) + "=? ORDER BY " + q(cMemberRole) + " ASC, " + q(cMemberName) + " ASC";
        List<MemberRow> out = new ArrayList<>();
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID u = UUID.fromString(rs.getString(1));
                    String n = rs.getString(2);
                    MemberRole r;
                    try {
                        r = MemberRole.valueOf(rs.getString(3));
                    } catch (Exception ignored) {
                        r = MemberRole.MEMBER;
                    }
                    out.add(new MemberRow(u, n, r));
                }
            }
        }
        return out;
    }

    public void setRole(UUID member, MemberRole role) throws Exception {
        ensureResolved();
        String sql = "UPDATE " + q(tMembers) + " SET " + q(cMemberRole) + "=? WHERE " + q(cMemberUuid) + "=?";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, role.name());
            ps.setString(2, member.toString());
            ps.executeUpdate();
        }
    }

    public void updateMemberName(UUID member, String name) throws Exception {
        ensureResolved();
        String sql = "UPDATE " + q(tMembers) + " SET " + q(cMemberName) + "=? WHERE " + q(cMemberUuid) + "=?";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name == null ? "Unknown" : name);
            ps.setString(2, member.toString());
            ps.executeUpdate();
        }
    }

    public ClanSettingsRow getSettings(long clanId) throws Exception {
        ensureResolved();
        String sql = "SELECT * FROM " + q(tSettings) + " WHERE " + q(cSettingsClanId) + "=? LIMIT 1";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new ClanSettingsRow(false, false, "");
                boolean open = readBool(rs, cSettingsOpenInvite);
                boolean ff = readBool(rs, cSettingsFriendlyFire);
                String motd = readString(rs, cSettingsMotd);
                return new ClanSettingsRow(open, ff, motd);
            }
        }
    }

    public boolean toggleOpenInvite(long clanId) throws Exception {
        ensureResolved();
        if (cSettingsOpenInvite == null) return false;
        ClanSettingsRow s = getSettings(clanId);
        boolean next = !s.openInvite;

        String sql = "UPDATE " + q(tSettings) + " SET " + q(cSettingsOpenInvite) + "=? WHERE " + q(cSettingsClanId) + "=?";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBoolean(1, next);
            ps.setLong(2, clanId);
            ps.executeUpdate();
        }
        return next;
    }

    public boolean toggleFriendlyFire(long clanId) throws Exception {
        ensureResolved();
        if (cSettingsFriendlyFire == null) return false;
        ClanSettingsRow s = getSettings(clanId);
        boolean next = !s.friendlyFire;

        String sql = "UPDATE " + q(tSettings) + " SET " + q(cSettingsFriendlyFire) + "=? WHERE " + q(cSettingsClanId) + "=?";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBoolean(1, next);
            ps.setLong(2, clanId);
            ps.executeUpdate();
        }
        return next;
    }

    public void setMotd(long clanId, String motd) throws Exception {
        ensureResolved();
        if (cSettingsMotd == null) return;

        String sql = "UPDATE " + q(tSettings) + " SET " + q(cSettingsMotd) + "=? WHERE " + q(cSettingsClanId) + "=?";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, motd == null ? "" : motd);
            ps.setLong(2, clanId);
            ps.executeUpdate();
        }
    }

    public List<MemberRow> getMembers(long clanId) throws Exception {
        return listMembers(clanId);
    }
}
