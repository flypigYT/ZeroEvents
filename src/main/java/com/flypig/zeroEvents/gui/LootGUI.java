package com.flypig.zeroEvents.gui;

import com.flypig.zeroEvents.ZeroEvents;
import com.flypig.zeroEvents.enums.EventRarity;
import com.flypig.zeroEvents.enums.EventType;
import com.flypig.zeroEvents.loot.LootItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class LootGUI implements Listener {

    private final ZeroEvents plugin;
    private final Map<UUID, LootMenuData> activeMenus;

    public LootGUI(ZeroEvents plugin) {
        this.plugin = plugin;
        this.activeMenus = new HashMap<>();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openLootMenu(Player player, EventType type, EventRarity rarity) {
        Inventory inv = Bukkit.createInventory(null, 54,
                Component.text("Настройка лута: " + type.name() + " - " + rarity.name())
                        .color(NamedTextColor.GOLD));

        // Загружаем текущий лут
        Map<Integer, LootItem> lootItems = plugin.getLootManager().getAllLootItems(type, rarity);

        for (Map.Entry<Integer, LootItem> entry : lootItems.entrySet()) {
            int slot = entry.getKey();
            LootItem lootItem = entry.getValue();

            if (slot >= 0 && slot < 45) { // Первые 45 слотов для лута
                ItemStack display = lootItem.getItem().clone();
                ItemMeta meta = display.getItemMeta();

                if (meta != null) {
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.text("Шанс: " + lootItem.getChance() + "%")
                            .color(NamedTextColor.YELLOW));
                    lore.add(Component.empty());
                    lore.add(Component.text("ЛКМ - Удалить").color(NamedTextColor.RED));
                    lore.add(Component.text("ПКМ - Изменить шанс").color(NamedTextColor.GREEN));
                    meta.lore(lore);
                    display.setItemMeta(meta);
                }

                inv.setItem(slot, display);
            }
        }

        // Кнопки управления
        ItemStack addButton = createButton(Material.LIME_DYE, "Добавить предмет",
                "Положите предмет в любой", "свободный слот (первые 45)");
        ItemStack closeButton = createButton(Material.BARRIER, "Закрыть", "");

        inv.setItem(53, closeButton);
        inv.setItem(45, addButton);

        player.openInventory(inv);

        // Сохраняем контекст меню
        activeMenus.put(player.getUniqueId(), new LootMenuData(type, rarity));
    }

    @EventHandler
    public void onInventoryClick(@NotNull InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        UUID playerId = player.getUniqueId();
        if (!activeMenus.containsKey(playerId)) return;

        LootMenuData menuData = activeMenus.get(playerId);
        Inventory inv = e.getInventory();

        // Проверяем, что это наше GUI
        String title = "Настройка лута: " + menuData.type.name() + " - " + menuData.rarity.name();
        if (!e.getView().title().equals(Component.text(title).color(NamedTextColor.GOLD))) {
            return;
        }

        int slot = e.getRawSlot();

        // Клик вне инвентаря GUI
        if (slot < 0 || slot >= 54) {
            return;
        }

        // Кнопка закрытия
        if (slot == 53) {
            e.setCancelled(true);
            player.closeInventory();
            return;
        }

        // Служебные слоты (нижний ряд кроме 53)
        if (slot >= 45) {
            e.setCancelled(true);
            return;
        }

        // Рабочие слоты (0-44)
        ItemStack clickedItem = e.getCurrentItem();
        ItemStack cursorItem = e.getCursor();

        // ПКМ - изменение шанса
        if (e.getClick() == ClickType.RIGHT && clickedItem != null && clickedItem.getType() != Material.AIR) {
            e.setCancelled(true);
            openChanceMenu(player, menuData, slot);
            return;
        }

        // ЛКМ - удаление
        if (e.getClick() == ClickType.LEFT && clickedItem != null && clickedItem.getType() != Material.AIR) {
            e.setCancelled(true);
            plugin.getLootManager().removeLootItem(menuData.type, menuData.rarity, slot);
            inv.setItem(slot, null);
            player.sendMessage(Component.text("Предмет удален!").color(NamedTextColor.GREEN));
            return;
        }

        // Добавление нового предмета
        if (cursorItem.getType() != Material.AIR && (clickedItem == null || clickedItem.getType() == Material.AIR)) {
            e.setCancelled(true);

            ItemStack newItem = cursorItem.clone();
            plugin.getLootManager().setLootItem(menuData.type, menuData.rarity, slot, newItem, 100);

            // Обновляем отображение
            ItemStack display = newItem.clone();
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Шанс: 100%").color(NamedTextColor.YELLOW));
                lore.add(Component.empty());
                lore.add(Component.text("ЛКМ - Удалить").color(NamedTextColor.RED));
                lore.add(Component.text("ПКМ - Изменить шанс").color(NamedTextColor.GREEN));
                meta.lore(lore);
                display.setItemMeta(meta);
            }

            inv.setItem(slot, display);
            player.setItemOnCursor(null);
            player.sendMessage(Component.text("Предмет добавлен с шансом 100%!").color(NamedTextColor.GREEN));
        }
    }

    private void openChanceMenu(Player player, LootMenuData menuData, int itemSlot) {
        Inventory chanceInv = Bukkit.createInventory(null, 27,
                Component.text("Выберите шанс").color(NamedTextColor.GOLD));

        int[] chances = {5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};

        for (int i = 0; i < chances.length; i++) {
            int chance = chances[i];
            ItemStack button = createButton(Material.PAPER,
                    chance + "%",
                    "Установить шанс " + chance + "%");
            chanceInv.setItem(i + 8, button);
        }

        ItemStack backButton = createButton(Material.ARROW, "Назад", "");
        chanceInv.setItem(26, backButton);

        player.openInventory(chanceInv);

        // Обновляем контекст
        menuData.chanceMenuSlot = itemSlot;
    }

    @EventHandler
    public void onChanceMenuClick(@NotNull InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        UUID playerId = player.getUniqueId();
        if (!activeMenus.containsKey(playerId)) return;

        LootMenuData menuData = activeMenus.get(playerId);

        if (!e.getView().title().equals(Component.text("Выберите шанс").color(NamedTextColor.GOLD))) {
            return;
        }

        e.setCancelled(true);

        int slot = e.getRawSlot();

        // Кнопка "Назад"
        if (slot == 26) {
            openLootMenu(player, menuData.type, menuData.rarity);
            return;
        }

        // Кнопки с процентами
        if (slot >= 8 && slot <= 18) {
            ItemStack clicked = e.getCurrentItem();
            if (clicked != null && clicked.getType() == Material.PAPER) {
                ItemMeta meta = clicked.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    String displayName = ((net.kyori.adventure.text.TextComponent) Objects.requireNonNull(meta.displayName())).content();
                    try {
                        int chance = Integer.parseInt(displayName.replace("%", ""));

                        // Обновляем шанс
                        plugin.getLootManager().updateChance(menuData.type, menuData.rarity,
                                menuData.chanceMenuSlot, chance);

                        player.sendMessage(Component.text("Шанс изменен на " + chance + "%!")
                                .color(NamedTextColor.GREEN));

                        // Возвращаемся в главное меню
                        openLootMenu(player, menuData.type, menuData.rarity);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(@NotNull InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player player) {
            UUID playerId = player.getUniqueId();
            LootMenuData menuData = activeMenus.get(playerId);

            if (menuData != null) {
                // Проверяем, что закрывается наше меню
                String expectedTitle = "Настройка лута: " + menuData.type.name() + " - " + menuData.rarity.name();
                String chanceTitle = "Выберите шанс";

                Component viewTitle = e.getView().title();
                if (viewTitle.equals(Component.text(expectedTitle).color(NamedTextColor.GOLD)) ||
                        viewTitle.equals(Component.text(chanceTitle).color(NamedTextColor.GOLD))) {

                    // НЕ удаляем контекст сразу - даём возможность переоткрыть
                    // Удаляем только через 2 секунды если не переоткрыли
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!player.getOpenInventory().title().equals(
                                Component.text(expectedTitle).color(NamedTextColor.GOLD)) &&
                                !player.getOpenInventory().title().equals(
                                        Component.text(chanceTitle).color(NamedTextColor.GOLD))) {
                            activeMenus.remove(playerId);
                        }
                    }, 40L);
                }
            }
        }
    }

    private ItemStack createButton(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(name)
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));

            if (lore.length > 0) {
                List<Component> loreList = new ArrayList<>();
                for (String line : lore) {
                    if (!line.isEmpty()) {
                        loreList.add(Component.text(line)
                                .color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false));
                    }
                }
                meta.lore(loreList);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private static class LootMenuData {
        final EventType type;
        final EventRarity rarity;
        int chanceMenuSlot = -1;

        LootMenuData(EventType type, EventRarity rarity) {
            this.type = type;
            this.rarity = rarity;
        }
    }
}