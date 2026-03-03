package dev.eministar.starclans.database;

import dev.eministar.starclans.model.ClanProfile;
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
        public final UUID targetUuid;
        public final UUID inviterUuid;
        public final boolean requiresApproval;
        public final boolean pendingApproval;
        public final long expiresAtMillis;

        public InviteRow(long id, long clanId, String clanName, String clanTag,
                         UUID targetUuid, UUID inviterUuid,
                         boolean requiresApproval, boolean pendingApproval,
                         long expiresAtMillis) {
            this.id = id;
            this.clanId = clanId;
            this.clanName = clanName;
            this.clanTag = clanTag;
            this.targetUuid = targetUuid;
            this.inviterUuid = inviterUuid;
            this.requiresApproval = requiresApproval;
            this.pendingApproval = pendingApproval;
            this.expiresAtMillis = expiresAtMillis;
        }
    }

    public static final class ClanLookupRow {
        public final long clanId;
        public final String clanName;
        public final String clanTag;

        public ClanLookupRow(long clanId, String clanName, String clanTag) {
            this.clanId = clanId;
            this.clanName = clanName == null ? "" : clanName;
            this.clanTag = clanTag == null ? "" : clanTag;
        }
    }

    public static final class ClanLeaderboardRow {
        public final long clanId;
        public final String name;
        public final String tag;
        public final double balance;
        public final int memberCount;

        public ClanLeaderboardRow(long clanId, String name, String tag, double balance, int memberCount) {
            this.clanId = clanId;
            this.name = name == null ? "" : name;
            this.tag = tag == null ? "" : tag;
            this.balance = balance;
            this.memberCount = memberCount;
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
        public final String motd;
        public final double taxRate;

        public ClanSettingsRow(boolean openInvite, String motd, double taxRate) {
            this.openInvite = openInvite;
            this.motd = motd == null ? "" : motd;
            this.taxRate = taxRate;
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
    private String cClanBalance;
    private String cClanHomeWorld;
    private String cClanHomeX;
    private String cClanHomeY;
    private String cClanHomeZ;
    private String cClanHomeYaw;
    private String cClanHomePitch;


    private String cMembersClanId;
    private String cMemberUuid;
    private String cMemberName;
    private String cMemberRole;

    private String cInviteId;
    private String cInviteClanId;
    private String cInviteTargetUuid;
    private String cInviteInviterUuid;
    private String cInviteRequiresApproval;
    private String cInvitePendingApproval;
    private String cInviteExpiresAt;
    private String cInviteCreatedAt;

    private String cSettingsClanId;
    private String cSettingsOpenInvite;
    private String cSettingsMotd;
    private String cSettingsTaxRate;

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
            cClanBalance = pick(clansCols, "balance", "clan_balance", "money", "funds");
            cClanHomeWorld = pick(clansCols, "home_world", "homeWorld", "world");
            cClanHomeX = pick(clansCols, "home_x", "homeX", "x");
            cClanHomeY = pick(clansCols, "home_y", "homeY", "y");
            cClanHomeZ = pick(clansCols, "home_z", "homeZ", "z");
            cClanHomeYaw = pick(clansCols, "home_yaw", "homeYaw", "yaw");
            cClanHomePitch = pick(clansCols, "home_pitch", "homePitch", "pitch");


            cMembersClanId = pick(membersCols, "clan_id", "clanId", "id_clan");
            cMemberUuid = pick(membersCols, "member_uuid", "uuid", "player_uuid", "user_uuid", "member", "player");
            cMemberName = pick(membersCols, "member_name", "name", "username", "player_name");
            cMemberRole = pick(membersCols, "role", "member_role", "rank");

            cInviteId = pick(invitesCols, "id", "invite_id", "inviteId");
            cInviteClanId = pick(invitesCols, "clan_id", "clanId");
            cInviteTargetUuid = pick(invitesCols, "target_uuid", "uuid", "member_uuid", "player_uuid", "user_uuid", "target");
            cInviteInviterUuid = pick(invitesCols, "inviter_uuid", "sender_uuid", "from_uuid", "inviter", "sender");
            cInviteRequiresApproval = pick(invitesCols, "requires_approval", "requiresApproval", "needs_approval", "needsApproval");
            cInvitePendingApproval = pick(invitesCols, "pending_approval", "pendingApproval", "approval_pending", "approvalPending");
            cInviteExpiresAt = pick(invitesCols, "expires_at", "expire_at", "expires", "expire");
            cInviteCreatedAt = pick(invitesCols, "created_at", "created", "time", "createdAt");

            cSettingsClanId = pick(settingsCols, "clan_id", "clanId");
            cSettingsOpenInvite = pick(settingsCols, "open_invite", "open_invites", "openinvites", "open");
            cSettingsMotd = pick(settingsCols, "motd", "clan_motd", "message", "msg");
            cSettingsTaxRate = pick(settingsCols, "tax_rate", "taxRate", "tax");

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
            require(tClans, cClanBalance, "balance");

            require(tMembers, cMembersClanId, "clan_id");
            require(tMembers, cMemberUuid, "member_uuid/uuid");
            require(tMembers, cMemberName, "member_name/name");
            require(tMembers, cMemberRole, "role");

            require(tInvites, cInviteId, "id");
            require(tInvites, cInviteClanId, "clan_id");
            require(tInvites, cInviteTargetUuid, "target_uuid/uuid");
            require(tInvites, cInviteInviterUuid, "inviter_uuid/sender_uuid");
            require(tInvites, cInviteRequiresApproval, "requires_approval");
            require(tInvites, cInvitePendingApproval, "pending_approval");
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
        if (name == null || !SAFE.matcher(name).matches())
            throw new IllegalStateException("Unsafe identifier: " + name);
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

    private long toMillis(Timestamp ts) {
        return ts == null ? 0L : ts.getTime();
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

    public ClanLookupRow findClanByNameOrTag(String input) throws Exception {
        ensureResolved();
        String raw = input == null ? "" : input.trim();
        if (raw.isEmpty()) return null;

        String sql = "SELECT " + q(cClanIdClans) + "," + q(cClanName) + "," + q(cClanTag) +
                " FROM " + q(tClans) +
                " WHERE LOWER(" + q(cClanName) + ")=LOWER(?) OR UPPER(" + q(cClanTag) + ")=UPPER(?) LIMIT 1";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, raw);
            ps.setString(2, raw);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new ClanLookupRow(rs.getLong(1), rs.getString(2), rs.getString(3));
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

    public long createInvite(long clanId, UUID target, UUID inviter, int minutes) throws Exception {
        return createInvite(clanId, target, inviter, minutes, false);
    }

    public long createInvite(long clanId, UUID target, UUID inviter, int minutes, boolean requiresApproval) throws Exception {
        ensureResolved();
        cleanupExpiredInvites();
        int m = Math.max(1, minutes);
        String sql = "INSERT INTO " + q(tInvites) + " (" + q(cInviteClanId) + "," + q(cInviteTargetUuid) + "," + q(cInviteInviterUuid) + "," + q(cInviteRequiresApproval) + "," + q(cInvitePendingApproval) + "," + q(cInviteExpiresAt) + ") VALUES (?,?,?,?,?,DATE_ADD(NOW(), INTERVAL ? MINUTE))";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, clanId);
            ps.setString(2, target.toString());
            ps.setString(3, inviter.toString());
            ps.setBoolean(4, requiresApproval);
            ps.setBoolean(5, false);
            ps.setInt(6, m);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        return -1;
    }

    public long createJoinRequest(long clanId, UUID target, int minutes) throws Exception {
        ensureResolved();
        cleanupExpiredInvites();
        int m = Math.max(1, minutes);
        String sql = "INSERT INTO " + q(tInvites) + " (" + q(cInviteClanId) + "," + q(cInviteTargetUuid) + "," + q(cInviteInviterUuid) + "," + q(cInviteRequiresApproval) + "," + q(cInvitePendingApproval) + "," + q(cInviteExpiresAt) + ") VALUES (?,?,?,?,?,DATE_ADD(NOW(), INTERVAL ? MINUTE))";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, clanId);
            ps.setString(2, target.toString());
            ps.setString(3, target.toString());
            ps.setBoolean(4, true);
            ps.setBoolean(5, true);
            ps.setInt(6, m);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        return -1;
    }

    public String getMemberName(UUID member) throws Exception {
        ensureResolved();
        String sql = "SELECT " + q(cMemberName) + " FROM " + q(tMembers) + " WHERE " + q(cMemberUuid) + "=? LIMIT 1";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, member.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return "Unknown";
                String n = rs.getString(1);
                return n == null || n.isEmpty() ? "Unknown" : n;
            }
        }
    }

    public boolean hasActiveInviteForTargetClan(UUID target, long clanId) throws Exception {
        ensureResolved();
        cleanupExpiredInvites();
        String sql = "SELECT 1 FROM " + q(tInvites) +
                " WHERE " + q(cInviteTargetUuid) + "=? AND " + q(cInviteClanId) + "=? AND " + q(cInviteExpiresAt) + " >= NOW() LIMIT 1";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, target.toString());
            ps.setLong(2, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public InviteRow getInviteById(long inviteId, UUID target) throws Exception {
        return getInviteForTarget(inviteId, target);
    }

    public InviteRow getInviteForTarget(long inviteId, UUID target) throws Exception {
        ensureResolved();
        cleanupExpiredInvites();
        String sql = "SELECT i." + q(cInviteId) + ", i." + q(cInviteClanId) + ", c." + q(cClanName) + ", c." + q(cClanTag) +
                ", i." + q(cInviteTargetUuid) + ", i." + q(cInviteInviterUuid) +
                ", i." + q(cInviteRequiresApproval) + ", i." + q(cInvitePendingApproval) + ", i." + q(cInviteExpiresAt) +
                " FROM " + q(tInvites) + " i JOIN " + q(tClans) + " c ON c." + q(cClanIdClans) + " = i." + q(cInviteClanId) +
                " WHERE i." + q(cInviteId) + "=? AND i." + q(cInviteTargetUuid) + "=? AND i." + q(cInvitePendingApproval) + "=0 AND i." + q(cInviteExpiresAt) + " >= NOW() LIMIT 1";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, inviteId);
            ps.setString(2, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new InviteRow(
                        rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        UUID.fromString(rs.getString(5)), UUID.fromString(rs.getString(6)),
                        readBool(rs, cInviteRequiresApproval), readBool(rs, cInvitePendingApproval),
                        toMillis(rs.getTimestamp(9))
                );
            }
        }
    }

    public InviteRow getInviteForApproval(long inviteId, long clanId) throws Exception {
        ensureResolved();
        cleanupExpiredInvites();
        String sql = "SELECT i." + q(cInviteId) + ", i." + q(cInviteClanId) + ", c." + q(cClanName) + ", c." + q(cClanTag) +
                ", i." + q(cInviteTargetUuid) + ", i." + q(cInviteInviterUuid) +
                ", i." + q(cInviteRequiresApproval) + ", i." + q(cInvitePendingApproval) + ", i." + q(cInviteExpiresAt) +
                " FROM " + q(tInvites) + " i JOIN " + q(tClans) + " c ON c." + q(cClanIdClans) + " = i." + q(cInviteClanId) +
                " WHERE i." + q(cInviteId) + "=? AND i." + q(cInviteClanId) + "=? AND i." + q(cInvitePendingApproval) + "=1 AND i." + q(cInviteExpiresAt) + " >= NOW() LIMIT 1";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, inviteId);
            ps.setLong(2, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new InviteRow(
                        rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        UUID.fromString(rs.getString(5)), UUID.fromString(rs.getString(6)),
                        readBool(rs, cInviteRequiresApproval), readBool(rs, cInvitePendingApproval),
                        toMillis(rs.getTimestamp(9))
                );
            }
        }
    }

    public List<InviteRow> getInvites(UUID target) throws Exception {
        ensureResolved();
        cleanupExpiredInvites();

        String order = cInviteCreatedAt != null ? " ORDER BY i." + q(cInviteCreatedAt) + " DESC" : " ORDER BY i." + q(cInviteId) + " DESC";

        String sql = "SELECT i." + q(cInviteId) + ", i." + q(cInviteClanId) + ", c." + q(cClanName) + ", c." + q(cClanTag) +
                ", i." + q(cInviteTargetUuid) + ", i." + q(cInviteInviterUuid) +
                ", i." + q(cInviteRequiresApproval) + ", i." + q(cInvitePendingApproval) + ", i." + q(cInviteExpiresAt) +
                " FROM " + q(tInvites) + " i JOIN " + q(tClans) + " c ON c." + q(cClanIdClans) + " = i." + q(cInviteClanId) +
                " WHERE i." + q(cInviteTargetUuid) + "=? AND i." + q(cInvitePendingApproval) + "=0 AND i." + q(cInviteExpiresAt) + " >= NOW()" + order;

        List<InviteRow> out = new ArrayList<>();
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new InviteRow(
                            rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                            UUID.fromString(rs.getString(5)), UUID.fromString(rs.getString(6)),
                            readBool(rs, cInviteRequiresApproval), readBool(rs, cInvitePendingApproval),
                            toMillis(rs.getTimestamp(9))
                    ));
                }
            }
        }
        return out;
    }

    public List<InviteRow> getPendingApprovals(long clanId) throws Exception {
        ensureResolved();
        cleanupExpiredInvites();

        String order = cInviteCreatedAt != null ? " ORDER BY i." + q(cInviteCreatedAt) + " DESC" : " ORDER BY i." + q(cInviteId) + " DESC";

        String sql = "SELECT i." + q(cInviteId) + ", i." + q(cInviteClanId) + ", c." + q(cClanName) + ", c." + q(cClanTag) +
                ", i." + q(cInviteTargetUuid) + ", i." + q(cInviteInviterUuid) +
                ", i." + q(cInviteRequiresApproval) + ", i." + q(cInvitePendingApproval) + ", i." + q(cInviteExpiresAt) +
                " FROM " + q(tInvites) + " i JOIN " + q(tClans) + " c ON c." + q(cClanIdClans) + " = i." + q(cInviteClanId) +
                " WHERE i." + q(cInviteClanId) + "=? AND i." + q(cInvitePendingApproval) + "=1 AND i." + q(cInviteExpiresAt) + " >= NOW()" + order;

        List<InviteRow> out = new ArrayList<>();
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new InviteRow(
                            rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                            UUID.fromString(rs.getString(5)), UUID.fromString(rs.getString(6)),
                            readBool(rs, cInviteRequiresApproval), readBool(rs, cInvitePendingApproval),
                            toMillis(rs.getTimestamp(9))
                    ));
                }
            }
        }
        return out;
    }

    public void setInvitePendingApproval(long inviteId, boolean pending) throws Exception {
        ensureResolved();
        String sql = "UPDATE " + q(tInvites) + " SET " + q(cInvitePendingApproval) + "=? WHERE " + q(cInviteId) + "=?";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBoolean(1, pending);
            ps.setLong(2, inviteId);
            ps.executeUpdate();
        }
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

    public void transferLeadership(UUID currentLeader, UUID nextLeader) throws Exception {
        ensureResolved();
        try (Connection con = c()) {
            con.setAutoCommit(false);
            try {
                String sql = "UPDATE " + q(tMembers) + " SET " + q(cMemberRole) + "=? WHERE " + q(cMemberUuid) + "=?";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, MemberRole.LEADER.name());
                    ps.setString(2, nextLeader.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, MemberRole.OFFICER.name());
                    ps.setString(2, currentLeader.toString());
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
                if (!rs.next()) return new ClanSettingsRow(false, "", 0.0);
                boolean open = readBool(rs, cSettingsOpenInvite);
                String motd = readString(rs, cSettingsMotd);
                double tax = rs.getDouble(cSettingsTaxRate);
                return new ClanSettingsRow(open, motd, tax);
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

    public void deposit(long clanId, double amount) throws Exception {
        ensureResolved();
        String sql = "UPDATE " + q(tClans) + " SET " + q(cClanBalance) + "=" + q(cClanBalance) + "+? WHERE " + q(cClanIdClans) + "=?";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setLong(2, clanId);
            ps.executeUpdate();
        }
    }

    public void withdraw(long clanId, double amount) throws Exception {
        ensureResolved();
        String sql = "UPDATE " + q(tClans) + " SET " + q(cClanBalance) + "=" + q(cClanBalance) + "-? WHERE " + q(cClanIdClans) + "=?";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setLong(2, clanId);
            ps.executeUpdate();
        }
    }

    public void setHome(long clanId, String world, double x, double y, double z, float yaw, float pitch) throws Exception {
        ensureResolved();
        String sql = "UPDATE " + q(tClans) + " SET " + q(cClanHomeWorld) + "=?, " + q(cClanHomeX) + "=?, " + q(cClanHomeY) + "=?, " + q(cClanHomeZ) + "=?, " + q(cClanHomeYaw) + "=?, " + q(cClanHomePitch) + "=? WHERE " + q(cClanIdClans) + "=?";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setDouble(2, x);
            ps.setDouble(3, y);
            ps.setDouble(4, z);
            ps.setFloat(5, yaw);
            ps.setFloat(6, pitch);
            ps.setLong(7, clanId);
            ps.executeUpdate();
        }
    }

    public void setTaxRate(long clanId, double rate) throws Exception {
        ensureResolved();
        String sql = "UPDATE " + q(tSettings) + " SET " + q(cSettingsTaxRate) + "=? WHERE " + q(cSettingsClanId) + "=?";
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, rate);
            ps.setLong(2, clanId);
            ps.executeUpdate();
        }
    }

    public List<ClanLeaderboardRow> getTopClansByBalance(int limit) throws Exception {
        ensureResolved();
        String sql = "SELECT c." + q(cClanIdClans) + ", c." + q(cClanName) + ", c." + q(cClanTag) + ", c." + q(cClanBalance) + ", (SELECT COUNT(*) FROM " + q(tMembers) + " m WHERE m." + q(cMembersClanId) + " = c." + q(cClanIdClans) + ") as m_count " +
                " FROM " + q(tClans) + " c ORDER BY c." + q(cClanBalance) + " DESC LIMIT ?";
        List<ClanLeaderboardRow> out = new ArrayList<>();
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ClanLeaderboardRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getInt(5)));
                }
            }
        }
        return out;
    }

    public List<ClanLeaderboardRow> getTopClansByMembers(int limit) throws Exception {
        ensureResolved();
        String sql = "SELECT c." + q(cClanIdClans) + ", c." + q(cClanName) + ", c." + q(cClanTag) + ", c." + q(cClanBalance) + ", (SELECT COUNT(*) FROM " + q(tMembers) + " m WHERE m." + q(cMembersClanId) + " = c." + q(cClanIdClans) + ") as m_count " +
                " FROM " + q(tClans) + " c ORDER BY m_count DESC LIMIT ?";
        List<ClanLeaderboardRow> out = new ArrayList<>();
        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new ClanLeaderboardRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getDouble(4), rs.getInt(5)));
                }
            }
        }
        return out;
    }

    public ClanProfile getFullProfile(UUID uuid) throws Exception {
        ensureResolved();
        long clanId = getClanIdByMember(uuid);
        int invites = countInvites(uuid);
        if (clanId <= 0) return ClanProfile.none(invites);

        String sql = "SELECT c." + q(cClanName) + ", c." + q(cClanTag) + ", c." + q(cClanBalance) + ", " +
                "c." + q(cClanHomeWorld) + ", c." + q(cClanHomeX) + ", c." + q(cClanHomeY) + ", c." + q(cClanHomeZ) + ", " +
                "c." + q(cClanHomeYaw) + ", c." + q(cClanHomePitch) + ", " +
                "s." + q(cSettingsTaxRate) + ", " +
                "(SELECT " + q(cMemberRole) + " FROM " + q(tMembers) + " WHERE " + q(cMemberUuid) + "=?) as role, " +
                "(SELECT COUNT(*) FROM " + q(tMembers) + " WHERE " + q(cMembersClanId) + "=?) as m_count " +
                " FROM " + q(tClans) + " c " +
                " LEFT JOIN " + q(tSettings) + " s ON s." + q(cSettingsClanId) + "=c." + q(cClanIdClans) +
                " WHERE c." + q(cClanIdClans) + "=? LIMIT 1";

        try (Connection con = c();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, clanId);
            ps.setLong(3, clanId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString(1);
                    String tag = rs.getString(2);
                    double bal = rs.getDouble(3);
                    String world = rs.getString(4);
                    double x = rs.getDouble(5);
                    double y = rs.getDouble(6);
                    double z = rs.getDouble(7);
                    float yaw = rs.getFloat(8);
                    float pitch = rs.getFloat(9);
                    double tax = rs.getDouble(10);
                    MemberRole role = MemberRole.MEMBER;
                    String roleStr = rs.getString(11);
                    if (roleStr != null) {
                        try {
                            role = MemberRole.valueOf(roleStr);
                        } catch (Exception ignored) {
                        }
                    }
                    int members = rs.getInt(12);

                    return new ClanProfile(true, clanId, name, tag, role, members, invites, bal, world, x, y, z, yaw, pitch, tax);
                }
            }
        }
        return ClanProfile.none(invites);
    }
}
