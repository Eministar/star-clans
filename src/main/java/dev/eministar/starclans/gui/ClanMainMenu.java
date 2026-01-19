package dev.eministar.starclans.gui;

import dev.eministar.starclans.utils.StarPrefix;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MainMenu {

    public static void open(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, StarPrefix.PREFIX + "Clans Menü");

        inv.setItem(11, createItem(Material.BOOK, "§aClan Info"));
        inv.setItem(13, createItem(Material.ENDER_PEARL, "§bClan teleportieren"));
        inv.setItem(15, createItem(Material.ANVIL, "§eClan erstellen"));

        p.openInventory(inv);
    }

    private static ItemStack createItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
