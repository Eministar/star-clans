package dev.eministar.starclans.gui;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.model.MemberRole;
import dev.eministar.starclans.service.ClanService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanSettingsMenu implements Listener {

    private final StarClans plugin;
    private final ClanService service;
    private final ClanRepository repo;
    private final ClanMainMenu mainMenu;

    private final Set<UUID> motdEdit = ConcurrentHashMap.newKeySet();
    private String title;

    public ClanSettingsMenu(StarClans plugin, ClanService service, ClanRepository repo, ClanMainMenu mainMenu) {
        this.plugin = plugin;
        this.service = service;
        this.repo = repo;
        this.mainMenu = mainMenu;
        this.title = plugin.lang().get("gui.settings.title");
    }

    public void open(Player p) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(p.getUniqueId());
                if (clanId <= 0) {
                    Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(plugin.lang().prefixed("messages.not_in_clan")));
                    return;
                }
                MemberRole role = repo.getRole(p.getUniqueId());
                if (role != MemberRole.LEADER) {
                    Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(plugin.lang().prefixed("messages.only_leader_manage")));
                    return;
                }
                ClanRepository.ClanSettingsRow s = repo.getSettings(clanId);
                Bukkit.getScheduler().runTask(plugin, () -> openInv(p, role, s));
            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(plugin.lang().prefixed("messages.error_console")));
            }
        });
    }

    private void openInv(Player p, MemberRole role, ClanRepository.ClanSettingsRow s) {
        this.title = plugin.lang().get("gui.settings.title");
        Inventory inv = Bukkit.createInventory(null, 45, title);

        for (int i = 0; i < 45; i++) inv.setItem(i, glass());

        inv.setItem(40, back());

        boolean can = role != MemberRole.MEMBER;

        inv.setItem(11, tagStyler());
        inv.setItem(13, can ? motd(s.motd) : locked(plugin.lang().get("gui.settings.locked")));
        inv.setItem(15, disband());

        inv.setItem(21, can ? toggle(plugin.lang().get("gui.settings.open_invite.name"), s.openInvite) : locked(plugin.lang().get("gui.settings.locked")));
        inv.setItem(23, members());
        inv.setItem(31, invites());

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.4f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!title.equals(e.getView().getTitle())) return;

        e.setCancelled(true);

        if (e.getRawSlot() < 0 || e.getRawSlot() >= 45) return;

        int slot = e.getRawSlot();

        if (slot == 40) {
            mainMenu.open(p);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            return;
        }

        if (slot == 11) {
            p.closeInventory();
            p.performCommand("clan tagstyler");
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            return;
        }

        if (slot == 13) {
            motdEdit.add(p.getUniqueId());
            p.closeInventory();
            p.sendMessage(plugin.lang().prefixed("messages.motd.prompt"));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            return;
        }

        if (slot == 15) {
            p.closeInventory();
            p.performCommand("clan disband");
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.7f, 1.0f);
            return;
        }

        if (slot == 21) {
            service.toggleOpenInvite(p, s -> {
                p.sendMessage(plugin.lang().prefixedRaw(s));
                open(p);
            });
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            return;
        }

        if (slot == 23) {
            p.closeInventory();
            p.performCommand("clan members");
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            return;
        }

        if (slot == 31) {
            p.closeInventory();
            p.performCommand("clan invites");
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID u = p.getUniqueId();

        if (!motdEdit.contains(u)) return;

        e.setCancelled(true);
        motdEdit.remove(u);

        String msg = e.getMessage();
        if (msg.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.sendMessage(plugin.lang().prefixed("messages.motd.cancelled"));
                open(p);
            });
            return;
        }

        service.setMotd(p, msg, s -> {
            p.sendMessage(plugin.lang().prefixedRaw(s));
            open(p);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        motdEdit.remove(e.getPlayer().getUniqueId());
    }

    private ItemStack motd(String motd) {
        ItemStack it = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.settings.motd.name"));
            meta.setLore(plugin.lang().getList("gui.settings.motd.lore",
                    "motd", motd == null || motd.isEmpty() ? plugin.lang().get("gui.settings.motd.none") : motd));
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack toggle(String name, boolean enabled) {
        ItemStack it = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(plugin.lang().getList("gui.settings.toggle.lore",
                    "status", enabled ? plugin.lang().get("gui.settings.toggle.on") : plugin.lang().get("gui.settings.toggle.off")));
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack locked(String name) {
        ItemStack it = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack back() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.settings.back"));
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack tagStyler() {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.settings.tag_styler.name"));
            meta.setLore(plugin.lang().getList("gui.settings.tag_styler.lore"));
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack members() {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.settings.members.name"));
            meta.setLore(plugin.lang().getList("gui.settings.members.lore"));
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack invites() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.settings.invites.name"));
            meta.setLore(plugin.lang().getList("gui.settings.invites.lore"));
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack disband() {
        ItemStack it = new ItemStack(Material.TNT);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.settings.disband.name"));
            meta.setLore(plugin.lang().getList("gui.settings.disband.lore"));
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack glass() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.settings.glass"));
            it.setItemMeta(meta);
        }
        return it;
    }
}
