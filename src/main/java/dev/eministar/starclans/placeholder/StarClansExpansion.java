package dev.eministar.starclans.placeholder;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.model.ClanProfile;
import dev.eministar.starclans.service.ClanService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public final class StarClansExpansion extends PlaceholderExpansion {

    private final StarClans plugin;
    private final ClanService service;

    public StarClansExpansion(StarClans plugin, ClanService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return "starclans";
    }

    @Override
    public String getAuthor() {
        return "Emin";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) return "";

        ClanProfile p = service.getCached(player.getUniqueId());
        if (p == null) return "";

        String key = params.toLowerCase();

        return switch (key) {
            case "name" -> p.inClan ? p.clanName : "";
            case "tag" -> p.inClan ? p.clanTag : "";
            case "role" -> p.inClan ? prettyRole(p.role.name()) : "";
            case "members" -> p.inClan ? String.valueOf(p.memberCount) : "0";
            case "invites" -> String.valueOf(p.inviteCount);
            case "in_clan" -> p.inClan ? "true" : "false";
            default -> "";
        };
    }

    private String prettyRole(String role) {
        return switch (role) {
            case "LEADER" -> "Leader";
            case "OFFICER" -> "Officer";
            default -> "Member";
        };
    }
}
