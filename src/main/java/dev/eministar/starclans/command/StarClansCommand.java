package dev.eministar.starclans.command;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.HikariProvider;
import dev.eministar.starclans.database.SQL;
import dev.eministar.starclans.service.ClanService;
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
            sender.sendMessage(plugin.lang().prefixed("messages.use.starclans_reload"));
            return true;
        }

        if (!args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(plugin.lang().prefixed("messages.use.starclans_reload"));
            return true;
        }

        if (!sender.hasPermission("starclans.admin.reload")) {
            sender.sendMessage(plugin.lang().prefixed("messages.no_permission"));
            return true;
        }

        plugin.reloadConfig();
        plugin.lang().reload();
        VaultHook.init(plugin);

        HikariProvider.shutdown();
        HikariProvider.init(plugin);

        if (HikariProvider.isReady()) {
            try {
                SQL.initSchema(HikariProvider.get());
            } catch (Exception e) {
                sender.sendMessage(plugin.lang().prefixed("messages.db_schema_failed"));
                e.printStackTrace();
                return true;
            }
        }

        service.clearCache();
        sender.sendMessage(plugin.lang().prefixed("messages.reload_done"));
        return true;
    }
}
