package dev.eministar.starclans.gui;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.model.MemberRole;
import dev.eministar.starclans.service.ClanService;
import dev.eministar.starclans.utils.StarPrefix;
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

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanSettingsMenu implements Listener {

    private static final String TITLE = "§b§lClan §8| §fSettings";

    private final StarClans plugin;
    private final ClanService service;
    private final ClanRepository repo;
    private final ClanMainMenu mainMenu;

    private final Set<UUID> motdEdit = ConcurrentHashMap.newKeySet();

    public ClanSettingsMenu(StarClans plugin, ClanService service, ClanRepository repo, ClanMainMenu mainMenu) {
        this.plugin = plugin;
        this.service = service;
        this.repo = repo;
        this.mainMenu = mainMenu;
    }

    public void open(Player p) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long clanId = repo.getClanIdByMember(p.getUniqueId());
                if (clanId <= 0) {
                    Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(StarPrefix.PREFIX + "§cDu bist in keinem Clan."));
                    return;
                }
                MemberRole role = repo.getRole(p.getUniqueId());
                ClanRepository.ClanSettingsRow s = repo.getSettings(clanId);
                Bukkit.getScheduler().runTask(plugin, () -> openInv(p, role, s));
            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> p.sendMessage(StarPrefix.PREFIX + "§cFehler. Console."));
            }
        });
    }

    private void openInv(Player p, MemberRole role, ClanRepository.ClanSettingsRow s) {
        Inventory inv = Bukkit.createInventory(null, 45, TITLE);

        for (int i = 0; i < 45; i++) inv.setItem(i, glass());

        inv.setItem(40, back());

        boolean can = role != MemberRole.MEMBER;

        inv.setItem(13, can ? motd(s.motd) : locked("§cKeine Rechte"));
        inv.setItem(21, can ? toggle("§bOpen Invite", s.openInvite) : locked("§cKeine Rechte"));
        inv.setItem(23, can ? toggle("§cFriendly Fire", s.friendlyFire) : locked("§cKeine Rechte"));

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.4f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!TITLE.equals(e.getView().getTitle())) return;

        e.setCancelled(true);

        if (e.getRawSlot() < 0 || e.getRawSlot() >= 45) return;

        int slot = e.getRawSlot();

        if (slot == 40) {
            mainMenu.open(p);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            return;
        }

        if (slot == 13) {
            motdEdit.add(p.getUniqueId());
            p.closeInventory();
            p.sendMessage(StarPrefix.PREFIX + "§7Schreib neue MOTD in den Chat. §8(§fcancel§8 zum Abbrechen)");
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            return;
        }

        if (slot == 21) {
            service.toggleOpenInvite(p, s -> {
                p.sendMessage(StarPrefix.PREFIX + s);
                open(p);
            });
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.6f);
            return;
        }

        if (slot == 23) {
            service.toggleFriendlyFire(p, s -> {
                p.sendMessage(StarPrefix.PREFIX + s);
                open(p);
            });
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
                p.sendMessage(StarPrefix.PREFIX + "§7Abgebrochen.");
                open(p);
            });
            return;
        }

        service.setMotd(p, msg, s -> {
            p.sendMessage(StarPrefix.PREFIX + s);
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
            meta.setDisplayName("§eMOTD bearbeiten");
            meta.setLore(Arrays.asList(
                    "§8",
                    "§7Aktuell:",
                    "§f" + (motd == null || motd.isEmpty() ? "§8(keine)" : motd),
                    "§8",
                    "§bKlick §7zum Bearbeiten"
            ));
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack toggle(String name, boolean enabled) {
        ItemStack it = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(
                    "§8",
                    "§7Status: " + (enabled ? "§aAN" : "§7AUS"),
                    "§8",
                    "§bKlick §7zum togglen"
            ));
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
            meta.setDisplayName("§cZurück");
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack glass() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§8 ");
            it.setItemMeta(meta);
        }
        return it;
    }
}
