package dev.eministar.starclans.license;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.service.ClanService;
import dev.eministar.starclans.utils.StarPrefix;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClanChatListener implements Listener {

    private final StarClans plugin;
    private final ClanService service;
    private final ClanRepository repo;

    public ClanChatListener(StarClans plugin, ClanService service, ClanRepository repo) {
        this.plugin = plugin;
        this.service = service;
        this.repo = repo;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player sender = e.getPlayer();
        UUID u = sender.getUniqueId();

        if (!service.isClanChat(u)) return;

        e.setCancelled(true);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(u);
                if (clanId <= 0) {
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(StarPrefix.PREFIX + "§cDu bist in keinem Clan."));
                    return;
                }

                List<ClanRepository.MemberRow> members = repo.getMembers(clanId);
                List<UUID> targets = new ArrayList<>();
                for (ClanRepository.MemberRow m : members) targets.add(m.uuid);

                String msg = StarPrefix.PREFIX + "§bClan §8» §f" + sender.getName() + "§7: §f" + e.getMessage();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (UUID t : targets) {
                        Player p = Bukkit.getPlayer(t);
                        if (p != null) p.sendMessage(msg);
                    }
                });

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }
}
