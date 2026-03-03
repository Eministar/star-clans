package dev.eministar.starclans.listener;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.model.MemberRole;
import dev.eministar.starclans.service.ClanService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ProfilePreloadListener implements Listener {

    private final StarClans plugin;
    private final ClanService service;
    private final ClanRepository repo;

    public ProfilePreloadListener(StarClans plugin, ClanService service, ClanRepository repo) {
        this.plugin = plugin;
        this.service = service;
        this.repo = repo;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        UUID uuid = player.getUniqueId();

        service.loadProfileAsync(uuid, profile -> {
            if (profile == null || !profile.inClan) return;

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    ClanRepository.ClanSettingsRow settings = repo.getSettings(profile.clanId);
                    List<ClanRepository.MemberRow> members = repo.listMembers(profile.clanId);

                    List<String> officers = new ArrayList<>();
                    for (ClanRepository.MemberRow row : members) {
                        if (row.role == MemberRole.MEMBER) continue;
                        Player online = Bukkit.getPlayer(row.uuid);
                        if (online == null) continue;
                        officers.add(online.getName());
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;

                        if (!settings.motd.isBlank()) {
                            player.sendMessage(plugin.lang().prefixed("messages.motd.join_line", "motd", settings.motd));
                        }
                        if (!officers.isEmpty()) {
                            player.sendMessage(plugin.lang().prefixed("messages.motd.officers_online",
                                    "officers", String.join(", ", officers)));
                        }
                    });
                } catch (Exception ignored) {
                }
            });
        });
    }
}
