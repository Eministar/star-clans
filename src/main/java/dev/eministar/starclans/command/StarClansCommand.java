package dev.eministar.starclans.command;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.HikariProvider;
import dev.eministar.starclans.database.SQL;
import dev.eministar.starclans.service.ClanService;
import dev.eministar.starclans.utils.LoggerUtil;
import dev.eministar.starclans.vault.VaultHook;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class StarClansCommand implements CommandExecutor {

    private final StarClans plugin;
    private final ClanService service;

    public StarClansCommand(StarClans plugin, ClanService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
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
                    sender.sendMessage(plugin.lang().error("messages.db_schema_failed"));
                    LoggerUtil.error("Fehler beim Initialisieren des Datenbankschemas (Reload)!", e);
                    return true;
                }
            }

            service.clearCache();
            if (plugin.discord() != null) {
                plugin.discord().reload();
            }
            sender.sendMessage(plugin.lang().success("messages.reload_done"));
            return true;
        }

        if (args[0].equalsIgnoreCase("webhook")) {
            if (!sender.hasPermission("starclans.admin.webhook")) {
                sender.sendMessage(plugin.lang().prefixed("messages.no_permission"));
                return true;
            }
            handleWebhookCommand(sender, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("version")) {
            sender.sendMessage(plugin.lang().prefix() + "§7Version: §b" + plugin.getDescription().getVersion());
            sender.sendMessage(plugin.lang().prefix() + "§7Author: §fEministar");
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void handleWebhookCommand(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("help")) {
            sendWebhookHelp(sender);
            return;
        }

        if (args[1].equalsIgnoreCase("test")) {
            String event = args.length >= 3 ? args[2].toLowerCase() : "test";
            boolean ok = plugin.discord() != null && plugin.discord().sendTestEvent(event);
            sender.sendMessage(plugin.lang().prefixed(ok ? "messages.webhook.test_sent" : "messages.webhook.test_failed", "event", event));
            return;
        }

        if (args[1].equalsIgnoreCase("digest")) {
            boolean ok = plugin.discord() != null && plugin.discord().triggerDailyDigest(true);
            sender.sendMessage(plugin.lang().prefixed(ok ? "messages.webhook.digest_sent" : "messages.webhook.digest_failed"));
            return;
        }

        sendWebhookHelp(sender);
    }

    private void sendHelp(CommandSender sender) {
        List<String> help = plugin.lang().getList("messages.starclans.help.lines");
        if (help.isEmpty()) {
            // Fallback if not in lang.yml yet
            sender.sendMessage("§8§m----------------------------------------");
            sender.sendMessage("§f/starclans reload §7- Lädt das Plugin neu");
            sender.sendMessage("§f/starclans version §7- Zeigt die Version an");
            sender.sendMessage("§f/starclans webhook test §7- Sendet einen Test-Webhook");
            sender.sendMessage("§8§m----------------------------------------");
            return;
        }

        sender.sendMessage(plugin.lang().get("messages.starclans.help.title"));
        for (String line : help) {
            sender.sendMessage(line);
        }
    }

    private void sendWebhookHelp(CommandSender sender) {
        List<String> help = plugin.lang().getList("messages.webhook.help.lines");
        if (help.isEmpty()) {
            sender.sendMessage(plugin.lang().prefix() + "§7/starclans webhook test [event]");
            sender.sendMessage(plugin.lang().prefix() + "§7/starclans webhook digest");
            return;
        }

        sender.sendMessage(plugin.lang().get("messages.webhook.help.title"));
        for (String line : help) {
            sender.sendMessage(line);
        }
    }
}
