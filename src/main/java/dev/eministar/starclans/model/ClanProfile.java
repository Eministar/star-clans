package dev.eministar.starclans.model;

public final class ClanProfile {

    public final boolean inClan;
    public final long clanId;
    public final String clanName;
    public final String clanTag;
    public final MemberRole role;
    public final int memberCount;
    public final int inviteCount;
    public final double balance;
    public final String homeWorld;
    public final double homeX, homeY, homeZ;
    public final float homeYaw, homePitch;
    public final double taxRate;

    public ClanProfile(boolean inClan, long clanId, String clanName, String clanTag, MemberRole role, int memberCount, int inviteCount, double balance, String homeWorld, double homeX, double homeY, double homeZ, float homeYaw, float homePitch, double taxRate) {
        this.inClan = inClan;
        this.clanId = clanId;
        this.clanName = clanName == null ? "" : clanName;
        this.clanTag = clanTag == null ? "" : clanTag;
        this.role = role == null ? MemberRole.MEMBER : role;
        this.memberCount = memberCount;
        this.inviteCount = inviteCount;
        this.balance = balance;
        this.homeWorld = homeWorld;
        this.homeX = homeX;
        this.homeY = homeY;
        this.homeZ = homeZ;
        this.homeYaw = homeYaw;
        this.homePitch = homePitch;
        this.taxRate = taxRate;
    }

    public static ClanProfile none(int invites) {
        return new ClanProfile(false, -1, "", "", MemberRole.MEMBER, 0, invites, 0, null, 0, 0, 0, 0, 0, 0);
    }
}
