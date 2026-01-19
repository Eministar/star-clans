package dev.eministar.starclans.placeholder;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.model.ClanProfile;
import dev.eministar.starclans.service.ClanService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public final class StarClansExpansion extends PlaceholderExpansion {

    private final StarClans plugin;
    private final ClanService service;
    private final ClanRepository repo;

    public StarClansExpansion(StarClans plugin, ClanService service, ClanRepository repo) {
        this.plugin = plugin;
        this.service = service;
        this.repo = repo;
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

        String key = params.toLowerCase();

        return switch (key) {
            case "name" -> p != null && p.inClan ? p.clanName : clanNameFallback(player.getUniqueId());
            case "name_formatted" -> {
                if (p != null && p.inClan) {
                    yield "§b" + p.clanName + " §8(" + "§f" + p.memberCount + "§8)";
                }
                long clanId = clanIdFallback(player.getUniqueId());
                if (clanId <= 0) yield "(-)";
                String name = clanNameFallback(player.getUniqueId());
                if (name.isEmpty()) yield "(-)";
                int members = memberCountFallback(clanId);
                yield "§b" + name + " §8(" + "§f" + members + "§8)";
            }
            case "tag" -> p != null && p.inClan ? p.clanTag : clanTagFallback(player.getUniqueId());
            case "role" -> p != null && p.inClan ? prettyRole(p.role.name()) : "";
            case "members" -> p != null && p.inClan ? String.valueOf(p.memberCount) : "0";
            case "invites" -> p != null ? String.valueOf(p.inviteCount) : "0";
            case "in_clan" -> p != null ? (p.inClan ? "true" : "false") : (clanIdFallback(player.getUniqueId()) > 0 ? "true" : "false");
            case "suffix" -> {
                if (p != null && p.inClan) {
                    yield clanSuffix(p.clanId, p.clanTag);
                }
                long clanId = clanIdFallback(player.getUniqueId());
                if (clanId <= 0) yield "";
                String tag = clanTagFallback(player.getUniqueId());
                if (tag.isEmpty()) yield "";
                yield clanSuffix(clanId, tag);
            }
            case "formatted_suffix" -> {
                String suffix;
                if (p != null && p.inClan) {
                    suffix = clanSuffix(p.clanId, p.clanTag);
                } else {
                    long clanId = clanIdFallback(player.getUniqueId());
                    if (clanId <= 0) yield "";
                    String tag = clanTagFallback(player.getUniqueId());
                    if (tag.isEmpty()) yield "";
                    suffix = clanSuffix(clanId, tag);
                }
                yield suffix.isEmpty() ? "" : " " + suffix;
            }
            default -> "";
        };
    }

    private String clanSuffix(long clanId, String tag) {
        try {
            ClanRepository.ClanCosmeticsRow cos = repo.getCosmetics(clanId);
            String style = cos.tagStyle == null || cos.tagStyle.isEmpty() ? "§b" : cos.tagStyle;
            String t = tag == null ? "" : tag;
            if (t.isEmpty()) return "";
            return "§8[§r" + style + t + "§8]§r";
        } catch (Exception e) {
            return "";
        }
    }

    private long clanIdFallback(java.util.UUID uuid) {
        try {
            return repo.getClanIdByMember(uuid);
        } catch (Exception e) {
            return -1;
        }
    }

    private String clanNameFallback(java.util.UUID uuid) {
        try {
            long clanId = repo.getClanIdByMember(uuid);
            if (clanId <= 0) return "";
            return repo.getClanNameTag(clanId)[0];
        } catch (Exception e) {
            return "";
        }
    }

    private String clanTagFallback(java.util.UUID uuid) {
        try {
            long clanId = repo.getClanIdByMember(uuid);
            if (clanId <= 0) return "";
            return repo.getClanNameTag(clanId)[1];
        } catch (Exception e) {
            return "";
        }
    }

    private int memberCountFallback(long clanId) {
        try {
            return repo.countMembers(clanId);
        } catch (Exception e) {
            return 0;
        }
    }

    private String prettyRole(String role) {
        return switch (role) {
            case "LEADER" -> "Leader";
            case "OFFICER" -> "Officer";
            default -> "Member";
        };
    }
}
