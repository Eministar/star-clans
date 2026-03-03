package dev.eministar.starclans.gui;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.model.ClanProfile;
import dev.eministar.starclans.model.MemberRole;
import dev.eministar.starclans.service.ClanService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class ClanBankMenu implements Listener {

    private final StarClans plugin;
    private final ClanService service;
    private final NamespacedKey actionKey;
    private final String title;

    public ClanBankMenu(StarClans plugin, ClanService service) {
        this.plugin = plugin;
        this.service = service;
        this.actionKey = new NamespacedKey(plugin, "sc_bank_action");
        this.title = plugin.lang().get("gui.bank.title");
    }

    public void open(Player player) {
        service.loadProfileAsync(player.getUniqueId(), p -> {
            if (!p.inClan) {
                player.sendMessage(plugin.lang().prefixed("messages.not_in_clan"));
                return;
            }
            openWithProfile(player, p);
        });
    }

    private void openWithProfile(Player player, ClanProfile p) {
        Inventory inv = Bukkit.createInventory(player, 27, title);

        ItemStack border = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26}) {
            inv.setItem(i, border);
        }

        inv.setItem(11, button(Material.GOLD_INGOT, plugin.lang().get("gui.bank.deposit.name"),
                plugin.lang().getList("gui.bank.deposit.lore"), "DEPOSIT", true));

        inv.setItem(13, button(Material.CHEST, plugin.lang().get("gui.bank.info.name"),
                plugin.lang().getList("gui.bank.info.lore", "balance", service.money(p.balance)),
                "INFO", false));

        if (p.role.isAtLeast(MemberRole.OFFICER)) {
            inv.setItem(15, button(Material.IRON_INGOT, plugin.lang().get("gui.bank.withdraw.name"),
                    plugin.lang().getList("gui.bank.withdraw.lore"), "WITHDRAW", true));
        }

        inv.setItem(22, button(Material.ARROW, plugin.lang().get("gui.generic.back.name"),
                plugin.lang().getList("gui.generic.back.lore"), "BACK", false));

        player.openInventory(inv);
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

        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);

        if (action.equals("BACK")) {
            p.closeInventory();
            p.performCommand("clan");
            return;
        }

        if (action.equals("DEPOSIT")) {
            p.closeInventory();
            p.sendMessage(plugin.lang().prefixed("messages.bank.deposit_usage"));
            return;
        }

        if (action.equals("WITHDRAW")) {
            p.closeInventory();
            p.sendMessage(plugin.lang().prefixed("messages.bank.withdraw_usage"));
            return;
        }
    }

    private ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§r");
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack button(Material m, String name, List<String> lore, String action, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
            if (glow) meta.addEnchant(Enchantment.DENSITY, 1, true);
            it.setItemMeta(meta);
        }
        return it;
    }
}
