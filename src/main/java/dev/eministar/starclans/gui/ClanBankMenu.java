package dev.eministar.starclans.gui;

import dev.eministar.starclans.StarClans;
import dev.eministar.starclans.database.ClanRepository;
import dev.eministar.starclans.model.BankTransactionType;
import dev.eministar.starclans.model.ClanProfile;
import dev.eministar.starclans.model.MemberRole;
import dev.eministar.starclans.service.ClanService;
import dev.eministar.starclans.utils.LoggerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClanBankMenu implements Listener {

    private enum InputMode {
        DEPOSIT,
        WITHDRAW
    }

    private final StarClans plugin;
    private final ClanService service;
    private final ClanRepository repo;
    private final NamespacedKey actionKey;
    private final NamespacedKey amountKey;
    private final Map<UUID, InputMode> awaitingInput = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> historyPage = new ConcurrentHashMap<>();
    private String title;

    private final int[] depositSlots = {10, 11, 12, 13, 14};
    private final int[] withdrawSlots = {19, 20, 21, 22, 23};
    private final int[] historySlots = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    public ClanBankMenu(StarClans plugin, ClanService service, ClanRepository repo) {
        this.plugin = plugin;
        this.service = service;
        this.repo = repo;
        this.actionKey = new NamespacedKey(plugin, "sc_bank_action");
        this.amountKey = new NamespacedKey(plugin, "sc_bank_amount");
        this.title = plugin.lang().get("gui.bank.title");
    }

    public void open(Player player) {
        if (!service.isBankEnabled()) {
            player.sendMessage(plugin.lang().prefixed("messages.bank.disabled"));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                ClanProfile profile = repo.getFullProfile(player.getUniqueId());
                if (!profile.inClan) {
                    sync(() -> player.sendMessage(plugin.lang().prefixed("messages.not_in_clan")));
                    return;
                }

                int pageSize = service.bankHistoryPageSize();
                int currentPage = Math.max(0, historyPage.getOrDefault(player.getUniqueId(), Integer.valueOf(0)).intValue());
                int total = service.isBankHistoryEnabled() ? repo.countBankTransactions(profile.clanId) : 0;
                int maxPage = pageSize <= 0 ? 0 : Math.max(0, (int) Math.ceil((double) total / pageSize) - 1);
                if (currentPage > maxPage) {
                    currentPage = maxPage;
                    historyPage.put(player.getUniqueId(), Integer.valueOf(currentPage));
                }

                List<ClanRepository.BankTransactionRow> history = service.isBankHistoryEnabled()
                        ? repo.getBankTransactions(profile.clanId, pageSize, currentPage * pageSize)
                        : List.of();

                int finalCurrentPage = currentPage;
                sync(() -> openWithData(player, profile, history, total, finalCurrentPage, maxPage));
            } catch (Exception e) {
                LoggerUtil.error("Fehler beim Laden des Clan-Tresors für " + player.getName(), e);
                sync(() -> player.sendMessage(plugin.lang().error("messages.load_failed")));
            }
        });
    }

    private void openWithData(Player player, ClanProfile profile, List<ClanRepository.BankTransactionRow> history,
                              int totalEntries, int currentPage, int maxPage) {
        this.title = plugin.lang().get("gui.bank.title");
        Inventory inv = Bukkit.createInventory(player, 54, title);

        for (int i = 0; i < 54; i++) inv.setItem(i, pane(Material.GRAY_STAINED_GLASS_PANE));
        for (int slot : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53}) {
            inv.setItem(slot, pane(Material.BLACK_STAINED_GLASS_PANE));
        }

        inv.setItem(4, info(profile, totalEntries, currentPage + 1, maxPage + 1));

        fillQuickAmounts(inv, depositSlots, service.bankQuickAmounts(false), "DEPOSIT_QUICK", Material.LIME_DYE, false);
        fillQuickAmounts(inv, withdrawSlots, service.bankQuickAmounts(true), "WITHDRAW_QUICK", Material.RED_DYE, !profile.role.isAtLeast(service.configuredRole("clan.bank.withdraw.minRole", MemberRole.OFFICER)));

        inv.setItem(15, actionButton(service.allowBankCustomInput() ? Material.NAME_TAG : Material.GRAY_DYE,
                plugin.lang().get(service.allowBankCustomInput() ? "gui.bank.deposit_custom.name" : "gui.bank.locked.name"),
                plugin.lang().getList(service.allowBankCustomInput() ? "gui.bank.deposit_custom.lore" : "gui.bank.locked.lore"),
                service.allowBankCustomInput() ? "DEPOSIT_CUSTOM" : "NONE"));
        inv.setItem(16, actionButton(service.allowDepositAllButton() ? Material.EMERALD : Material.GRAY_DYE,
                plugin.lang().get(service.allowDepositAllButton() ? "gui.bank.deposit_all.name" : "gui.bank.locked.name"),
                plugin.lang().getList(service.allowDepositAllButton() ? "gui.bank.deposit_all.lore" : "gui.bank.locked.lore"),
                service.allowDepositAllButton() ? "DEPOSIT_ALL" : "NONE"));

        boolean withdrawLocked = !profile.role.isAtLeast(service.configuredRole("clan.bank.withdraw.minRole", MemberRole.OFFICER));
        boolean customInputEnabled = service.allowBankCustomInput();
        inv.setItem(24, actionButton(withdrawLocked || !customInputEnabled ? Material.GRAY_DYE : Material.NAME_TAG,
                plugin.lang().get(withdrawLocked || !customInputEnabled ? "gui.bank.locked.name" : "gui.bank.withdraw_custom.name"),
                plugin.lang().getList(withdrawLocked || !customInputEnabled ? "gui.bank.locked.lore" : "gui.bank.withdraw_custom.lore"),
                withdrawLocked || !customInputEnabled ? "NONE" : "WITHDRAW_CUSTOM"));
        boolean withdrawAllEnabled = service.allowWithdrawAllButton();
        inv.setItem(25, actionButton(withdrawLocked || !withdrawAllEnabled ? Material.GRAY_DYE : Material.REDSTONE,
                plugin.lang().get(withdrawLocked || !withdrawAllEnabled ? "gui.bank.locked.name" : "gui.bank.withdraw_all.name"),
                plugin.lang().getList(withdrawLocked || !withdrawAllEnabled ? "gui.bank.locked.lore" : "gui.bank.withdraw_all.lore"),
                withdrawLocked || !withdrawAllEnabled ? "NONE" : "WITHDRAW_ALL"));

        if (history.isEmpty()) {
            inv.setItem(31, emptyHistory());
        } else {
            for (int i = 0; i < history.size() && i < historySlots.length; i++) {
                inv.setItem(historySlots[i], historyEntry(history.get(i)));
            }
        }

        inv.setItem(45, actionButton(Material.BARRIER, plugin.lang().get("gui.generic.back.name"),
                plugin.lang().getList("gui.generic.back.lore"), "BACK"));
        inv.setItem(47, actionButton(Material.ARROW, plugin.lang().get("gui.bank.prev.name"),
                plugin.lang().getList("gui.bank.prev.lore"), currentPage > 0 ? "HISTORY_PREV" : "NONE"));
        inv.setItem(49, pageInfo(currentPage + 1, Math.max(1, maxPage + 1)));
        inv.setItem(51, actionButton(Material.ARROW, plugin.lang().get("gui.bank.next.name"),
                plugin.lang().getList("gui.bank.next.lore"), currentPage < maxPage ? "HISTORY_NEXT" : "NONE"));
        inv.setItem(53, actionButton(Material.CLOCK, plugin.lang().get("gui.bank.refresh.name"),
                plugin.lang().getList("gui.bank.refresh.lore"), "REFRESH"));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.25f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!title.equals(e.getView().getTitle())) return;

        e.setCancelled(true);
        ItemStack item = e.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null || action.equals("NONE")) return;

        Double amount = meta.getPersistentDataContainer().get(amountKey, PersistentDataType.DOUBLE);

        switch (action) {
            case "BACK" -> {
                player.closeInventory();
                player.performCommand("clan");
            }
            case "REFRESH" -> open(player);
            case "HISTORY_PREV" -> {
                historyPage.put(player.getUniqueId(), Integer.valueOf(Math.max(0, historyPage.getOrDefault(player.getUniqueId(), Integer.valueOf(0)).intValue() - 1)));
                open(player);
            }
            case "HISTORY_NEXT" -> {
                historyPage.put(player.getUniqueId(), Integer.valueOf(historyPage.getOrDefault(player.getUniqueId(), Integer.valueOf(0)).intValue() + 1));
                open(player);
            }
            case "DEPOSIT_CUSTOM" -> beginInput(player, InputMode.DEPOSIT);
            case "WITHDRAW_CUSTOM" -> beginInput(player, InputMode.WITHDRAW);
            case "DEPOSIT_ALL" -> {
                player.closeInventory();
                service.depositAll(player, msg -> player.sendMessage(plugin.lang().prefixedRaw(msg)));
            }
            case "WITHDRAW_ALL" -> {
                player.closeInventory();
                service.withdrawAll(player, msg -> player.sendMessage(plugin.lang().prefixedRaw(msg)));
            }
            case "DEPOSIT_QUICK" -> {
                if (amount == null) return;
                player.closeInventory();
                service.deposit(player, amount.doubleValue(), msg -> player.sendMessage(plugin.lang().prefixedRaw(msg)));
            }
            case "WITHDRAW_QUICK" -> {
                if (amount == null) return;
                player.closeInventory();
                service.withdraw(player, amount.doubleValue(), msg -> player.sendMessage(plugin.lang().prefixedRaw(msg)));
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player player = e.getPlayer();
        InputMode mode = awaitingInput.get(player.getUniqueId());
        if (mode == null) return;

        e.setCancelled(true);
        String message = e.getMessage().trim();
        awaitingInput.remove(player.getUniqueId());

        if (message.equalsIgnoreCase("cancel")) {
            sync(() -> {
                player.sendMessage(plugin.lang().prefixed("messages.bank.input_cancelled"));
                open(player);
            });
            return;
        }

        if (message.equalsIgnoreCase("all")) {
            sync(() -> {
                if (mode == InputMode.DEPOSIT) {
                    service.depositAll(player, msg -> player.sendMessage(plugin.lang().prefixedRaw(msg)));
                } else {
                    service.withdrawAll(player, msg -> player.sendMessage(plugin.lang().prefixedRaw(msg)));
                }
            });
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(message.replace(',', '.'));
        } catch (Exception ex) {
            sync(() -> {
                player.sendMessage(plugin.lang().prefixed("messages.bank.invalid_amount"));
                open(player);
            });
            return;
        }

        sync(() -> {
            if (mode == InputMode.DEPOSIT) {
                service.deposit(player, amount, msg -> player.sendMessage(plugin.lang().prefixedRaw(msg)));
            } else {
                service.withdraw(player, amount, msg -> player.sendMessage(plugin.lang().prefixedRaw(msg)));
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        awaitingInput.remove(e.getPlayer().getUniqueId());
        historyPage.remove(e.getPlayer().getUniqueId());
    }

    private void beginInput(Player player, InputMode mode) {
        if (!service.allowBankCustomInput()) {
            player.sendMessage(plugin.lang().prefixed("messages.bank.custom_disabled"));
            return;
        }
        awaitingInput.put(player.getUniqueId(), mode);
        player.closeInventory();
        player.sendMessage(plugin.lang().prefixed(mode == InputMode.DEPOSIT
                ? "messages.bank.deposit_prompt"
                : "messages.bank.withdraw_prompt"));
    }

    private void fillQuickAmounts(Inventory inv, int[] slots, List<Double> amounts, String action,
                                  Material material, boolean locked) {
        for (int i = 0; i < slots.length; i++) {
            if (locked) {
                inv.setItem(slots[i], actionButton(Material.GRAY_DYE, plugin.lang().get("gui.bank.locked.name"),
                        plugin.lang().getList("gui.bank.locked.lore"), "NONE"));
                continue;
            }

            if (i >= amounts.size()) {
                inv.setItem(slots[i], pane(Material.GRAY_STAINED_GLASS_PANE));
                continue;
            }

            double amount = amounts.get(i).doubleValue();
            inv.setItem(slots[i], amountButton(material,
                    plugin.lang().get("gui.bank.quick_amount.name", "amount", service.money(amount)),
                    plugin.lang().getList("gui.bank.quick_amount.lore", "amount", service.money(amount)),
                    action,
                    amount));
        }
    }

    private ItemStack info(ClanProfile profile, int totalEntries, int page, int maxPages) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.bank.info.name"));
            meta.setLore(plugin.lang().getList("gui.bank.info.lore",
                    "balance", service.money(profile.balance),
                    "entries", Integer.valueOf(totalEntries),
                    "page", Integer.valueOf(page),
                    "max", Integer.valueOf(maxPages)));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack pageInfo(int page, int maxPages) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.bank.page.name"));
            meta.setLore(plugin.lang().getList("gui.bank.page.lore",
                    "page", Integer.valueOf(page),
                    "max", Integer.valueOf(maxPages)));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack emptyHistory() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.bank.history_empty.name"));
            meta.setLore(plugin.lang().getList("gui.bank.history_empty.lore"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack historyEntry(ClanRepository.BankTransactionRow row) {
        Material material = switch (row.type) {
            case DEPOSIT -> Material.LIME_DYE;
            case WITHDRAW -> Material.RED_DYE;
            case TAX -> Material.GOLD_NUGGET;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.lang().get("gui.bank.history_entry.name",
                    "type", historyType(row.type),
                    "amount", service.money(row.amount)));
            meta.setLore(plugin.lang().getList("gui.bank.history_entry.lore",
                    "actor", row.actorName.isBlank() ? plugin.lang().get("messages.generic_unknown") : row.actorName,
                    "balance", service.money(row.balanceAfter),
                    "date", formatHistoryDate(row.createdAtMillis),
                    "note", row.note.isBlank() ? plugin.lang().get("gui.bank.history_entry.no_note") : row.note));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String historyType(BankTransactionType type) {
        return plugin.lang().get("gui.bank.history_types." + type.name().toLowerCase(Locale.ROOT));
    }

    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§r");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack actionButton(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack amountButton(Material material, String name, List<String> lore, String action, double amount) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
            meta.getPersistentDataContainer().set(amountKey, PersistentDataType.DOUBLE, Double.valueOf(amount));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatHistoryDate(long millis) {
        String pattern = plugin.getConfig().getString("clan.bank.history.timeFormat", "dd.MM.yyyy HH:mm");
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(pattern, Locale.GERMANY);
        } catch (Exception ignored) {
            formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMANY);
        }
        return formatter.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private void sync(Runnable runnable) {
        Bukkit.getScheduler().runTask(plugin, runnable);
    }
}
