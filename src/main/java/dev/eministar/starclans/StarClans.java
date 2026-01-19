package dev.eministar.starclans;

import dev.eministar.starclans.command.CommandRegister;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.database.HikariProvider;
import dev.eministar.starclans.database.SQL;
import dev.eministar.starclans.gui.*;
import dev.eministar.starclans.listener.GlobalChatListener;
import dev.eministar.starclans.listener.ProfilePreloadListener;
import dev.eministar.starclans.placeholder.StarClansExpansion;
import dev.eministar.starclans.service.ClanService;
import dev.eministar.starclans.vault.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class StarClans extends JavaPlugin {

    private ClanRepository repo;
    private ClanService service;

    private ClanMainMenu mainMenu;
    private ClanCreateMenu createMenu;
    private ClanInvitesMenu invitesMenu;
    private ClanMembersMenu membersMenu;
    private ClanMemberManageMenu manageMenu;
    private ClanSettingsMenu settingsMenu;
    private ClanTagStyleMenu tagStyleMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        VaultHook.init(this);

        HikariProvider.init(this);
        if (HikariProvider.isReady()) {
            try {
                SQL.initSchema(HikariProvider.get());
            } catch (Exception e) {
                e.printStackTrace();
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        } else {
            getLogger().warning("Database disabled or not ready. StarClans will run limited.");
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

        getServer().getPluginManager().registerEvents(mainMenu, this);
        getServer().getPluginManager().registerEvents(createMenu, this);
        getServer().getPluginManager().registerEvents(invitesMenu, this);
        getServer().getPluginManager().registerEvents(membersMenu, this);
        getServer().getPluginManager().registerEvents(manageMenu, this);
        getServer().getPluginManager().registerEvents(settingsMenu, this);
        getServer().getPluginManager().registerEvents(tagStyleMenu, this);

        getServer().getPluginManager().registerEvents(new ProfilePreloadListener(service), this);
        getServer().getPluginManager().registerEvents(new GlobalChatListener(this, service, repo), this);

        CommandRegister.register(
                this, service, repo,
                mainMenu, createMenu, invitesMenu, membersMenu, manageMenu,
                tagStyleMenu, settingsMenu
        );

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new StarClansExpansion(this, service, repo).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        } else {
            getLogger().warning("PlaceholderAPI not found. Placeholders disabled.");
        }

        getLogger().info("StarClans enabled.");
    }

    @Override
    public void onDisable() {
        HikariProvider.shutdown();
        getLogger().info("StarClans disabled.");
    }

    public ClanRepository repo() {
        return repo;
    }

    public ClanService service() {
        return service;
    }
}
