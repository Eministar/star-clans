package dev.eministar.starclans.listener;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.model.ClanProfile;
import dev.eministar.starclans.model.MemberRole;
import dev.eministar.starclans.service.ClanService;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class GlobalChatListener implements Listener {

    private final StarClans plugin;
    private final ClanService service;
    private final ClanRepository repo;

    public GlobalChatListener(StarClans plugin, ClanService service, ClanRepository repo) {
        this.plugin = plugin;
        this.service = service;
        this.repo = repo;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        if (e.isCancelled()) return;
        Player p = e.getPlayer();
        ClanProfile prof = service.getCached(p.getUniqueId());

        if (prof != null && prof.inClan && service.isClanChat(p.getUniqueId())) {
            e.setCancelled(true);
            String msg = e.getMessage();

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    ClanRepository.ClanCosmeticsRow cos = repo.getCosmetics(prof.clanId);
                    String tagStyle = cos.tagStyle.isEmpty() ? "§b" : cos.tagStyle;
                    String suffix = plugin.lang().get("messages.chat.tag_suffix",
                            "style", tagStyle,
                            "tag", prof.clanTag);
                    MemberRole role = prof.role == null ? MemberRole.MEMBER : prof.role;
                    String hover = plugin.lang().get("messages.chat.hover",
                            "role_color", roleColor(role),
                            "role", plugin.lang().role(role));

                    TextComponent prefix = new TextComponent(plugin.lang().get("messages.chat.clan_prefix"));
                    TextComponent name = new TextComponent(p.getName());
                    name.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(hover).create()));
                    name.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/clan members " + p.getName()));
                    TextComponent suffixComp = new TextComponent(suffix);
                    TextComponent rest = new TextComponent(plugin.lang().get("messages.chat.clan_message_tail", "message", msg));
                    BaseComponent[] out = new BaseComponent[]{prefix, name, suffixComp, rest};

                    java.util.List<ClanRepository.MemberRow> members = repo.listMembers(prof.clanId);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (ClanRepository.MemberRow m : members) {
                            Player t = Bukkit.getPlayer(m.uuid);
                            if (t != null) {
                                t.spigot().sendMessage(out);
                                t.playSound(t.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.5f);
                            }
                        }
                    });
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            return;
        }

        if (prof == null || !prof.inClan) {
            e.setFormat(plugin.lang().get("messages.chat.global_no_clan_format"));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClanRepository.ClanCosmeticsRow cos = repo.getCosmetics(prof.clanId);

                String tagStyle = cos.tagStyle.isEmpty() ? "§b" : cos.tagStyle;
                String suffix = plugin.lang().get("messages.chat.tag_suffix",
                        "style", tagStyle,
                        "tag", prof.clanTag);

                String format = plugin.lang().get("messages.chat.global_with_clan_format",
                        "suffix", suffix);
                Bukkit.getScheduler().runTask(plugin, () -> e.setFormat(format));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private String roleColor(MemberRole r) {
        return plugin.lang().roleColor(r);
    }
}
