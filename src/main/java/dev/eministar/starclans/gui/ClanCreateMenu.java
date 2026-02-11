package dev.eministar.starclans.gui;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.service.ClanService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanCreateMenu implements Listener {

    private final StarClans plugin;
    private final ClanService service;
    private final ClanTagStyleMenu tagStyleMenu;

    private final NamespacedKey actionKey;
    private String title;

    private final Map<UUID, ClanService.CreateState> state = new ConcurrentHashMap<>();
    private final Map<UUID, String> awaiting = new ConcurrentHashMap<>();

    public ClanCreateMenu(StarClans plugin, ClanService service, ClanTagStyleMenu tagStyleMenu) {
        this.plugin = plugin;
        this.service = service;
        this.tagStyleMenu = tagStyleMenu;
        this.actionKey = new NamespacedKey(plugin, "sc_action");
        this.title = plugin.lang().get("gui.create.title");
    }

    public void open(Player p) {
        service.loadProfileAsync(p.getUniqueId(), prof -> {
            if (prof != null && prof.inClan) {
                p.sendMessage(plugin.lang().prefixed("messages.already_in_clan"));
                return;
            }
            state.put(p.getUniqueId(), new ClanService.CreateState());
            openView(p);
        });
    }

    private void openView(Player p) {
        this.title = plugin.lang().get("gui.create.title");
        ClanService.CreateState s = state.get(p.getUniqueId());
        if (s == null) {
            s = new ClanService.CreateState();
            state.put(p.getUniqueId(), s);
        }

        Inventory inv = Bukkit.createInventory(p, 27, title);

        for (int i = 0; i < 27; i++) inv.setItem(i, pane(Material.GRAY_STAINED_GLASS_PANE));
        for (int i = 0; i <= 8; i++) inv.setItem(i, pane(Material.BLACK_STAINED_GLASS_PANE));
        inv.setItem(9, pane(Material.BLACK_STAINED_GLASS_PANE));
        inv.setItem(17, pane(Material.BLACK_STAINED_GLASS_PANE));
        for (int i = 18; i <= 26; i++) inv.setItem(i, pane(Material.BLACK_STAINED_GLASS_PANE));

        inv.setItem(11, button(Material.NAME_TAG, plugin.lang().get("gui.create.set_name.name"),
                plugin.lang().getList("gui.create.set_name.lore",
                        "current", s.name.isEmpty() ? plugin.lang().get("gui.create.current.none") : s.name),
                "SET_NAME", true));

        inv.setItem(13, button(Material.PAPER, plugin.lang().get("gui.create.set_tag.name"),
                plugin.lang().getList("gui.create.set_tag.lore",
                        "current", s.tag.isEmpty() ? plugin.lang().get("gui.create.current.none") : s.tag),
                "SET_TAG", true));

        String styled = (s.tagStyle.isEmpty() ? "§b" : s.tagStyle) + (s.tag.isEmpty() ? plugin.lang().get("gui.create.tag_preview_fallback") : s.tag) + "§r";
        inv.setItem(14, button(Material.NETHER_STAR, plugin.lang().get("gui.create.tag_style.name"),
                plugin.lang().getList("gui.create.tag_style.lore", "styled", styled),
                "TAG_STYLE", !s.tagStyle.isEmpty()));

        boolean ready = !s.name.isEmpty() && !s.tag.isEmpty();
        inv.setItem(15, button(Material.EMERALD_BLOCK,
                plugin.lang().get(ready ? "gui.create.confirm.ready_name" : "gui.create.confirm.name"),
                plugin.lang().getList("gui.create.confirm.lore",
                        "status", plugin.lang().get(ready ? "gui.create.confirm.ready_status" : "gui.create.confirm.not_ready_status")),
                "CONFIRM", ready));

        inv.setItem(22, button(Material.BARRIER, plugin.lang().get("gui.create.cancel.name"),
                plugin.lang().getList("gui.create.cancel.lore"), "CANCEL", false));

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.25f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (!e.getView().getTitle().equals(title)) return;

        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;

        String action = it.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        if (action.equals("CANCEL")) {
            awaiting.remove(p.getUniqueId());
            state.remove(p.getUniqueId());
            p.closeInventory();
            p.performCommand("clan");
            return;
        }

        if (action.equals("SET_NAME")) {
            awaiting.put(p.getUniqueId(), "NAME");
            p.closeInventory();
            p.sendMessage(plugin.lang().prefixed("messages.create.prompt_name"));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
            return;
        }

        if (action.equals("SET_TAG")) {
            awaiting.put(p.getUniqueId(), "TAG");
            p.closeInventory();
            p.sendMessage(plugin.lang().prefixed("messages.create.prompt_tag"));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
            return;
        }

        if (action.equals("TAG_STYLE")) {
            ClanService.CreateState s = state.get(p.getUniqueId());
            if (s == null) {
                s = new ClanService.CreateState();
                state.put(p.getUniqueId(), s);
            }
            p.closeInventory();
            ClanService.CreateState finalState = s;
            tagStyleMenu.openCreate(p, s.tag, s.tagStyle, () -> openView(p), style -> finalState.tagStyle = style);
            return;
        }

        if (action.equals("CONFIRM")) {
            ClanService.CreateState s = state.get(p.getUniqueId());
            if (s == null || s.name.isEmpty() || s.tag.isEmpty()) {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                return;
            }
            p.closeInventory();
            service.tryCreateClan(p, s.name, s.tag, s.tagStyle, msg -> p.sendMessage(plugin.lang().prefixedRaw(msg)));
            state.remove(p.getUniqueId());
            awaiting.remove(p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        String mode = awaiting.get(p.getUniqueId());
        if (mode == null) return;

        e.setCancelled(true);

        String msg = e.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, new Runnable() {
            @Override
            public void run() {
                ClanService.CreateState s = state.get(p.getUniqueId());
                if (s == null) {
                    state.put(p.getUniqueId(), new ClanService.CreateState());
                    s = state.get(p.getUniqueId());
                }

                if (mode.equals("NAME")) {
                    s.name = msg;
                    awaiting.remove(p.getUniqueId());
                    p.sendMessage(plugin.lang().prefixed("messages.create.name_set", "name", msg));
                    openView(p);
                    return;
                }

                if (mode.equals("TAG")) {
                    s.tag = msg.toUpperCase();
                    awaiting.remove(p.getUniqueId());
                    p.sendMessage(plugin.lang().prefixed("messages.create.tag_set", "tag", s.tag));
                    openView(p);
                }
            }
        });
    }

    private ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName("§r");
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack button(Material m, String name, java.util.List<String> lore, String action, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        if (glow) meta.addEnchant(Enchantment.DENSITY, 1, true);
        it.setItemMeta(meta);
        return it;
    }
}
