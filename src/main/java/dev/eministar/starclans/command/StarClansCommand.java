package dev.eministar.starclans.command;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.HikariProvider;
import dev.eministar.starclans.database.SQL;
import dev.eministar.starclans.service.ClanService;
import dev.eministar.starclans.utils.StarPrefix;
import dev.eministar.starclans.vault.VaultHook;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class StarClansCommand implements CommandExecutor {

    private final StarClans plugin;
    private final ClanService service;

    public StarClansCommand(StarClans plugin, ClanService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(StarPrefix.PREFIX + "§7Nutze: §f/starclans reload");
            return true;
        }

        if (!args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(StarPrefix.PREFIX + "§7Nutze: §f/starclans reload");
            return true;
        }

        if (!sender.hasPermission("starclans.admin.reload")) {
            sender.sendMessage(StarPrefix.PREFIX + "§cKeine Rechte.");
            return true;
        }

        plugin.reloadConfig();
        VaultHook.init(plugin);

        HikariProvider.shutdown();
        HikariProvider.init(plugin);

        if (HikariProvider.isReady()) {
            try {
                SQL.initSchema(HikariProvider.get());
            } catch (Exception e) {
                sender.sendMessage(StarPrefix.PREFIX + "§cDB Schema init failed. Console.");
                e.printStackTrace();
                return true;
            }
        }

        service.clearCache();
        sender.sendMessage(StarPrefix.PREFIX + "§aReload done. §7Config/Vault/DB refreshed.");
        return true;
    }
}
