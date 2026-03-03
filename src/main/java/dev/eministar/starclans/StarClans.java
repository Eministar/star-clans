package dev.eministar.starclans;

import dev.eministar.starclans.command.CommandRegister;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.database.HikariProvider;
import dev.eministar.starclans.database.SQL;
import dev.eministar.starclans.discord.DiscordWebhookClient;
import dev.eministar.starclans.gui.*;
import dev.eministar.starclans.listener.GlobalChatListener;
import dev.eministar.starclans.listener.ProfilePreloadListener;
import dev.eministar.starclans.listener.TaxListener;
import dev.eministar.starclans.placeholder.StarClansExpansion;
import dev.eministar.starclans.service.ClanService;
import dev.eministar.starclans.utils.Banner;
import dev.eministar.starclans.utils.Lang;
import dev.eministar.starclans.utils.LoggerUtil;
import dev.eministar.starclans.utils.UpdateChecker;
import dev.eministar.starclans.utils.Version;
import dev.eministar.starclans.vault.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class StarClans extends JavaPlugin {

    private ClanRepository repo;
    private ClanService service;
    private Lang lang;
    private DiscordWebhookClient discord;

    private ClanMainMenu mainMenu;
    private ClanCreateMenu createMenu;
    private ClanInvitesMenu invitesMenu;
    private ClanMembersMenu membersMenu;
    private ClanMemberManageMenu manageMenu;
    private ClanSettingsMenu settingsMenu;
    private ClanTagStyleMenu tagStyleMenu;
    private ClanBankMenu bankMenu;
    private ClanLeaderboardMenu leaderboardMenu;

    @Override
    public void onEnable() {
        LoggerUtil.init(this);
        saveDefaultConfig();
        this.lang = new Lang(this);
        this.discord = new DiscordWebhookClient(this);

        Version.init(this);
        UpdateChecker.check(this);

        VaultHook.init(this);

        HikariProvider.init(this);
        if (HikariProvider.isReady()) {
            try {
                SQL.initSchema(HikariProvider.get());
            } catch (Exception e) {
                LoggerUtil.error("Die Datenbank konnte nicht initialisiert werden!", e);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        } else {
            LoggerUtil.warn("Datenbank ist deaktiviert oder nicht bereit. StarClans läuft im eingeschränkten Modus.");
        }

        this.repo = new ClanRepository(HikariProvider.get());
        this.service = new ClanService(this, repo);

        this.mainMenu = new ClanMainMenu(this, service);
        this.tagStyleMenu = new ClanTagStyleMenu(this, service, repo);
        this.createMenu = new ClanCreateMenu(this, service, tagStyleMenu);
        this.invitesMenu = new ClanInvitesMenu(this, repo);

        this.membersMenu = new ClanMembersMenu(this, service, repo, mainMenu);
        this.manageMenu = new ClanMemberManageMenu(this, service, repo, membersMenu);
        this.membersMenu.bindManageMenu(manageMenu);

        this.settingsMenu = new ClanSettingsMenu(this, service, repo, mainMenu);
        this.bankMenu = new ClanBankMenu(this, service);
        this.leaderboardMenu = new ClanLeaderboardMenu(this, service);

        getServer().getPluginManager().registerEvents(mainMenu, this);
        getServer().getPluginManager().registerEvents(createMenu, this);
        getServer().getPluginManager().registerEvents(invitesMenu, this);
        getServer().getPluginManager().registerEvents(membersMenu, this);
        getServer().getPluginManager().registerEvents(manageMenu, this);
        getServer().getPluginManager().registerEvents(settingsMenu, this);
        getServer().getPluginManager().registerEvents(tagStyleMenu, this);
        getServer().getPluginManager().registerEvents(bankMenu, this);
        getServer().getPluginManager().registerEvents(leaderboardMenu, this);

        getServer().getPluginManager().registerEvents(new ProfilePreloadListener(this, service, repo), this);
        getServer().getPluginManager().registerEvents(new GlobalChatListener(this, service, repo), this);
        getServer().getPluginManager().registerEvents(new TaxListener(this, service), this);

        CommandRegister.register(
                this, service, repo,
                mainMenu, createMenu, invitesMenu, membersMenu, manageMenu,
                tagStyleMenu, settingsMenu, bankMenu, leaderboardMenu
        );

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new StarClansExpansion(this, service, repo).register();
            LoggerUtil.success("PlaceholderAPI Erweiterung wurde erfolgreich registriert.");
        } else {
            LoggerUtil.warn("PlaceholderAPI wurde nicht gefunden. Platzhalter sind deaktiviert.");
        }

        Banner.print(this);
        LoggerUtil.success("StarClans wurde erfolgreich aktiviert.");
    }

    @Override
    public void onDisable() {
        HikariProvider.shutdown();
        LoggerUtil.info("StarClans wurde deaktiviert.");
    }

    public ClanRepository repo() {
        return repo;
    }

    public ClanService service() {
        return service;
    }

    public Lang lang() {
        return lang;
    }

    public DiscordWebhookClient discord() {
        return discord;
    }
}
