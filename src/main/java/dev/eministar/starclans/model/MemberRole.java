package dev.eministar.starclans.model;

public enum MemberRole {
    LEADER,
    OFFICER,
    MEMBER;

    public boolean isAtLeast(MemberRole other) {
        return this.ordinal() <= other.ordinal();
    }
}
