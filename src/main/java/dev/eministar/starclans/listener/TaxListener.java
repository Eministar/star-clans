package dev.eministar.starclans.listener;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.model.ClanProfile;
import dev.eministar.starclans.service.ClanService;
import dev.eministar.starclans.vault.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TaxListener implements Listener {

    private final StarClans plugin;
    private final ClanService service;
    private final Map<UUID, Double> lastBalances = new HashMap<>();

    public TaxListener(StarClans plugin, ClanService service) {
        this.plugin = plugin;
        this.service = service;

        new BukkitRunnable() {
            @Override
            public void run() {
                checkBalances();
            }
        }.runTaskTimer(plugin, 100L, 100L); // Every 5 seconds
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (VaultHook.hasEconomy()) {
            lastBalances.put(e.getPlayer().getUniqueId(), Double.valueOf(VaultHook.eco().getBalance(e.getPlayer())));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastBalances.remove(e.getPlayer().getUniqueId());
    }

    private void checkBalances() {
        if (!VaultHook.hasEconomy()) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            double current = VaultHook.eco().getBalance(p);
            Double last = lastBalances.get(uuid);

            if (last != null && current > last.doubleValue()) {
                double earned = current - last.doubleValue();
                applyTax(p, earned);
                // Update last balance to current AFTER tax deduction
                lastBalances.put(uuid, Double.valueOf(VaultHook.eco().getBalance(p)));
            } else {
                lastBalances.put(uuid, Double.valueOf(current));
            }
        }
    }

    private void applyTax(Player p, double earned) {
        ClanProfile profile = service.getCached(p.getUniqueId());
        if (profile == null || !profile.inClan || profile.taxRate <= 0) return;

        double taxAmount = earned * (profile.taxRate / 100.0);
        if (taxAmount < 0.01) return;

        VaultHook.eco().withdrawPlayer(p, taxAmount);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.repo().deposit(profile.clanId, taxAmount);
                service.recordTaxPayment(p.getUniqueId(), p.getName(), profile.clanId, taxAmount, profile.balance + taxAmount);
            } catch (Exception ignored) {
            }
        });

        if (plugin.getConfig().getBoolean("clan.tax.notify", true)) {
            p.sendMessage(plugin.lang().prefixed("messages.tax.deducted",
                    "amount", service.money(taxAmount),
                    "rate", Double.valueOf(profile.taxRate)));
        }
    }
}
