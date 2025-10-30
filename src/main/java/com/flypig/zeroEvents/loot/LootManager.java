package com.flypig.zeroEvents.loot;

import com.flypig.zeroEvents.ZeroEvents;
import com.flypig.zeroEvents.enums.EventRarity;
import com.flypig.zeroEvents.enums.EventType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LootManager {

    private final ZeroEvents plugin;
    private final File lootFile;
    private FileConfiguration lootConfig;
    private final Map<String, Map<Integer, LootItem>> lootItems;
    private final Random random;

    public LootManager(ZeroEvents plugin) {
        this.plugin = plugin;
        this.lootFile = new File(plugin.getDataFolder(), "loot.yml");
        this.lootItems = new HashMap<>();
        this.random = new Random();
        loadLoot();
    }

    public void loadLoot() {
        if (!lootFile.exists()) {
            plugin.saveResource("loot.yml", false);
        }

        lootConfig = YamlConfiguration.loadConfiguration(lootFile);
        lootItems.clear();

        // Загружаем для каждого типа и редкости
        for (EventType type : EventType.values()) {
            for (EventRarity rarity : EventRarity.values()) {
                String key = getKey(type, rarity);
                loadLootForKey(key);
            }
        }
    }

    private void loadLootForKey(String key) {
        Map<Integer, LootItem> items = new HashMap<>();
        ConfigurationSection itemsSection = lootConfig.getConfigurationSection(key);

        if (itemsSection != null) {
            for (String slotKey : itemsSection.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(slotKey);
                    ItemStack item = itemsSection.getItemStack(slotKey + ".item");
                    int chance = itemsSection.getInt(slotKey + ".chance", 100);

                    if (item != null) {
                        items.put(slot, new LootItem(slot, chance, item));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        lootItems.put(key, items);
    }

    public void saveLoot() {
        // Очищаем весь конфиг
        for (String key : new HashSet<>(lootConfig.getKeys(false))) {
            lootConfig.set(key, null);
        }

        // Сохраняем все типы и редкости
        for (Map.Entry<String, Map<Integer, LootItem>> entry : lootItems.entrySet()) {
            String baseKey = entry.getKey();
            for (LootItem lootItem : entry.getValue().values()) {
                String path = baseKey + "." + lootItem.getSlot();
                lootConfig.set(path + ".item", lootItem.getItem());
                lootConfig.set(path + ".chance", lootItem.getChance());
            }
        }

        try {
            lootConfig.save(lootFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Не удалось сохранить loot.yml: " + e.getMessage());
        }
    }

    public void setLootItem(EventType type, EventRarity rarity, int slot, @NotNull ItemStack item, int chance) {
        String key = getKey(type, rarity);
        lootItems.computeIfAbsent(key, k -> new HashMap<>())
                .put(slot, new LootItem(slot, chance, item));
        saveLoot();
    }

    public void removeLootItem(EventType type, EventRarity rarity, int slot) {
        String key = getKey(type, rarity);
        Map<Integer, LootItem> items = lootItems.get(key);
        if (items != null) {
            items.remove(slot);
            saveLoot();
        }
    }

    @NotNull
    public Map<Integer, LootItem> getAllLootItems(EventType type, EventRarity rarity) {
        String key = getKey(type, rarity);
        return new HashMap<>(lootItems.getOrDefault(key, new HashMap<>()));
    }

    @Nullable
    public ItemStack getRandomLootItem(EventType type, EventRarity rarity) {
        String key = getKey(type, rarity);
        Map<Integer, LootItem> items = lootItems.get(key);

        if (items == null || items.isEmpty()) {
            return null;
        }

        List<LootItem> availableItems = new ArrayList<>(items.values());
        int totalWeight = availableItems.stream()
                .mapToInt(LootItem::getChance)
                .sum();

        if (totalWeight <= 0) {
            return null;
        }

        int randomWeight = random.nextInt(totalWeight);
        int currentWeight = 0;

        for (LootItem lootItem : availableItems) {
            currentWeight += lootItem.getChance();
            if (currentWeight > randomWeight) {
                return lootItem.getItem();
            }
        }

        return null;
    }

    public void updateChance(EventType type, EventRarity rarity, int slot, int newChance) {
        String key = getKey(type, rarity);
        Map<Integer, LootItem> items = lootItems.get(key);

        if (items != null) {
            LootItem item = items.get(slot);
            if (item != null) {
                item.setChance(newChance);
                saveLoot();
            }
        }
    }

    private String getKey(EventType type, EventRarity rarity) {
        return type.name().toLowerCase() + "." + rarity.name().toLowerCase();
    }
}