package dev.eministar.starclans.model;

public final class ClanProfile {

    public final boolean inClan;
    public final long clanId;
    public final String clanName;
    public final String clanTag;
    public final MemberRole role;
    public final int memberCount;
    public final int inviteCount;

    public ClanProfile(boolean inClan, long clanId, String clanName, String clanTag, MemberRole role, int memberCount, int inviteCount) {
        this.inClan = inClan;
        this.clanId = clanId;
        this.clanName = clanName == null ? "" : clanName;
        this.clanTag = clanTag == null ? "" : clanTag;
        this.role = role == null ? MemberRole.MEMBER : role;
        this.memberCount = memberCount;
        this.inviteCount = inviteCount;
    }

    public static ClanProfile none(int invites) {
        return new ClanProfile(false, -1, "", "", MemberRole.MEMBER, 0, invites);
    }
}
