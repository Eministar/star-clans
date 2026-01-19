package dev.eministar.starclans.vault;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class VaultHook {

    private static Economy economy;

    private VaultHook() {}

    public static void init(JavaPlugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            economy = null;
            return;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            economy = null;
            return;
        }
        economy = rsp.getProvider();
    }

    public static boolean hasEconomy() {
        return economy != null;
    }

    public static Economy eco() {
        return economy;
    }
}
