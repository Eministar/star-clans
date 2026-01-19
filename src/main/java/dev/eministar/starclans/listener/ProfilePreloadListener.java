package dev.eministar.starclans.listener;

import dev.eministar.starclans.service.ClanService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class ProfilePreloadListener implements Listener {

    private final ClanService service;

    public ProfilePreloadListener(ClanService service) {
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        service.loadProfileAsync(e.getPlayer().getUniqueId(), p -> {});
    }
}
