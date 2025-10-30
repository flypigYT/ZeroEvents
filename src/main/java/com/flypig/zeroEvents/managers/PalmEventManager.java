package com.flypig.zeroEvents.managers;

import com.flypig.zeroEvents.ZeroEvents;
import com.flypig.zeroEvents.enums.EventRarity;
import com.flypig.zeroEvents.enums.EventType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class PalmEventManager {

    private final ZeroEvents plugin;
    private BukkitTask palmEventTimer;
    private final Map<Location, PalmTreeData> activePalmTrees;
    private final Random random;
    private long lastPalmEventTime;

    // UUID для профиля кокоса
    private static final UUID COCONUT_UUID = UUID.fromString("7c3936c2-e071-4b6e-a283-5f9f1e7b2a4b");
    // Текстура кокоса
    private static final String COCONUT_TEXTURE = "http://textures.minecraft.net/texture/69139a5b1f91693835e34903ce34be86c926e9a1dffcb377cc5ed8f33d99821";

    public PalmEventManager(ZeroEvents plugin) {
        this.plugin = plugin;
        this.activePalmTrees = new HashMap<>();
        this.random = new Random();
        this.lastPalmEventTime = System.currentTimeMillis();
    }

    public void startPalmEventTimer() {
        int palmIntervalMinutes = plugin.getConfig().getInt("palm-event.interval", 60);

        if (palmIntervalMinutes > 0) {
            long palmIntervalTicks = palmIntervalMinutes * 60 * 20L;

            palmEventTimer = new BukkitRunnable() {
                @Override
                public void run() {
                    startPalmEvent();
                }
            }.runTaskTimer(plugin, palmIntervalTicks, palmIntervalTicks);

            lastPalmEventTime = System.currentTimeMillis();
        }
    }

    public void stopPalmEventTimer() {
        if (palmEventTimer != null && !palmEventTimer.isCancelled()) {
            palmEventTimer.cancel();
        }
    }

    public void startPalmEvent() {
        List<Location> locations = getPalmEventLocations();
        if (locations.isEmpty()) {
            plugin.getLogger().warning("Не настроены локации для пальм!");
            return;
        }

        EventRarity rarity = getRandomRarity();

        // Спавним пальмы на всех точках
        for (Location location : locations) {
            spawnPalmTree(location, rarity);
        }

        notifyPlayers(rarity);
        lastPalmEventTime = System.currentTimeMillis();
    }

    private void spawnPalmTree(@NotNull Location location, EventRarity rarity) {
        // Создаём 5 кокосов-дисплеев на каждой точке
        List<ItemDisplay> coconuts = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            ItemDisplay coconut = createCoconutDisplay(location.clone().add(0.5, 0, 0.5));
            coconuts.add(coconut);
        }

        int lifetimeSeconds = plugin.getConfig().getInt("palm-event.lifetime-seconds", 300);
        double fallHeight = plugin.getConfig().getDouble("palm-event.fall-height", 60.0);

        PalmTreeData treeData = new PalmTreeData(location, coconuts, rarity, fallHeight);
        activePalmTrees.put(location, treeData);

        // Запускаем механизм падения кокосов
        startCoconutDropScheduler(treeData);

        // Таймер жизни
        new BukkitRunnable() {
            @Override
            public void run() {
                if (activePalmTrees.containsKey(location)) {
                    removePalmTree(location);
                }
            }
        }.runTaskLater(plugin, lifetimeSeconds * 20L);
    }

    private ItemDisplay createCoconutDisplay(Location location) {
        ItemDisplay display = (ItemDisplay) location.getWorld().spawnEntity(
                location, EntityType.ITEM_DISPLAY);

        // Создаём кастомную голову кокоса
        ItemStack coconutHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) coconutHead.getItemMeta();

        if (meta != null) {
            PlayerProfile profile = Bukkit.createPlayerProfile(COCONUT_UUID, "Coconut");
            PlayerTextures textures = profile.getTextures();

            try {
                textures.setSkin(new URL(COCONUT_TEXTURE));
                profile.setTextures(textures);
                meta.setOwnerProfile(profile);
                coconutHead.setItemMeta(meta);
            } catch (MalformedURLException e) {
                plugin.getLogger().warning("Не удалось загрузить текстуру кокоса: " + e.getMessage());
            }
        }

        display.setItemStack(coconutHead);

        // Настраиваем размер (2x2x2)
        Transformation transformation = display.getTransformation();
        transformation.getScale().set(2f, 2f, 2f);
        display.setTransformation(transformation);

        display.setBillboard(Display.Billboard.FIXED);
        display.setViewRange(128f);

        return display;
    }

    private void startCoconutDropScheduler(@NotNull PalmTreeData treeData) {
        new BukkitRunnable() {
            int dropsCompleted = 0;

            @Override
            public void run() {
                if (!activePalmTrees.containsKey(treeData.location)) {
                    this.cancel();
                    return;
                }

                // Первое падение через 10 секунд
                if (dropsCompleted == 0) {
                    dropRandomCoconut(treeData);
                    dropsCompleted++;

                    // Планируем следующие падения с интервалом 5-10 секунд
                    scheduleNextDrop(treeData, this);
                }
            }
        }.runTaskLater(plugin, 200L); // 10 секунд
    }

    private void scheduleNextDrop(PalmTreeData treeData, BukkitRunnable parentTask) {
        int delay = 100 + random.nextInt(100); // 5-10 секунд

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!activePalmTrees.containsKey(treeData.location)) {
                    parentTask.cancel();
                    return;
                }

                dropRandomCoconut(treeData);

                // Планируем следующее падение
                scheduleNextDrop(treeData, parentTask);
            }
        }.runTaskLater(plugin, delay);
    }

    private void dropRandomCoconut(PalmTreeData treeData) {
        // Находим активные дисплеи
        List<ItemDisplay> activeDisplays = new ArrayList<>();
        for (int i = 0; i < treeData.coconutDisplays.size(); i++) {
            if (!treeData.fallenCoconuts[i]) {
                activeDisplays.add(treeData.coconutDisplays.get(i));
            }
        }

        if (activeDisplays.isEmpty()) {
            return; // Все кокосы уже упали
        }

        // Выбираем случайный активный дисплей
        ItemDisplay selectedDisplay = activeDisplays.get(random.nextInt(activeDisplays.size()));
        int index = treeData.coconutDisplays.indexOf(selectedDisplay);

        // Помечаем как упавший
        treeData.fallenCoconuts[index] = true;

        // Запускаем анимацию падения
        animateCoconutFall(selectedDisplay, treeData);
    }

    private void animateCoconutFall(ItemDisplay coconut, PalmTreeData treeData) {
        Location startLoc = coconut.getLocation();
        double targetY = startLoc.getY() - treeData.fallHeight;

        new BukkitRunnable() {
            double currentY = startLoc.getY();
            double velocity = 0;

            @Override
            public void run() {
                if (coconut.isDead() || !activePalmTrees.containsKey(treeData.location)) {
                    this.cancel();
                    return;
                }

                // Физика падения
                velocity += 0.08; // Гравитация
                currentY -= velocity;

                if (currentY <= targetY) {
                    // Приземление
                    coconut.teleport(startLoc.clone().subtract(0, treeData.fallHeight, 0));
                    onCoconutLanded(coconut.getLocation(), treeData);
                    coconut.remove();

                    // Восстанавливаем дисплей через 15 секунд
                    int displayIndex = treeData.coconutDisplays.indexOf(coconut);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (activePalmTrees.containsKey(treeData.location)) {
                                ItemDisplay newCoconut = createCoconutDisplay(treeData.location.clone().add(0.5, 0, 0.5));
                                treeData.coconutDisplays.set(displayIndex, newCoconut);
                                treeData.fallenCoconuts[displayIndex] = false;
                            }
                        }
                    }.runTaskLater(plugin, 300L); // 15 секунд

                    this.cancel();
                    return;
                }

                coconut.teleport(startLoc.clone().subtract(0, startLoc.getY() - currentY, 0));
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void onCoconutLanded(Location location, PalmTreeData treeData) {
        // Звук разрушения яйца черепахи
        location.getWorld().playSound(location, Sound.ENTITY_TURTLE_EGG_BREAK, 1.0f, 1.0f);

        // Партиклы разрушения
        location.getWorld().spawnParticle(Particle.ITEM, location.clone().add(0, 0.5, 0),
                20, 0.3, 0.3, 0.3, 0.1, new ItemStack(Material.BROWN_CONCRETE));

        // Дроп предметов
        int dropAmount = plugin.getConfig().getInt("palm-event.rarities." +
                treeData.rarity.name().toLowerCase() + ".drops.amount", 3);

        for (int i = 0; i < dropAmount; i++) {
            ItemStack item = plugin.getLootManager().getRandomLootItem(EventType.PALM, treeData.rarity);
            if (item != null) {
                location.getWorld().dropItemNaturally(location, item);
            }
        }
    }

    private void removePalmTree(Location location) {
        PalmTreeData treeData = activePalmTrees.remove(location);
        if (treeData == null) return;

        // Удаляем все дисплеи
        for (ItemDisplay coconut : treeData.coconutDisplays) {
            if (coconut != null && !coconut.isDead()) {
                coconut.remove();
            }
        }

        // Уведомление об окончании
        String messageStr = plugin.getConfig().getString("messages.palm-timeout");
        if (messageStr != null) {
            Component message = parseColorCodes(messageStr);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(message);
            }
        }
    }

    public void cleanupActivePalms() {
        for (PalmTreeData treeData : new ArrayList<>(activePalmTrees.values())) {
            for (ItemDisplay coconut : treeData.coconutDisplays) {
                if (coconut != null && !coconut.isDead()) {
                    coconut.remove();
                }
            }
        }
        activePalmTrees.clear();
    }

    private void notifyPlayers(EventRarity rarity) {
        String messageStr = plugin.getConfig().getString("palm-event.rarities." +
                rarity.name().toLowerCase() + ".start-message");

        if (messageStr != null) {
            Component message = parseColorCodes(messageStr);

            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(message);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }
    }

    @NotNull
    private List<Location> getPalmEventLocations() {
        List<Location> locations = new ArrayList<>();
        var locationsSection = plugin.getConfig().getConfigurationSection("palm-event.locations");

        if (locationsSection == null) return locations;

        for (String key : locationsSection.getKeys(false)) {
            String worldName = plugin.getConfig().getString("palm-event.locations." + key + ".world");
            if (worldName == null) continue;

            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            double x = plugin.getConfig().getDouble("palm-event.locations." + key + ".x");
            double y = plugin.getConfig().getDouble("palm-event.locations." + key + ".y");
            double z = plugin.getConfig().getDouble("palm-event.locations." + key + ".z");

            locations.add(new Location(world, x, y, z));
        }

        return locations;
    }

    private EventRarity getRandomRarity() {
        int roll = random.nextInt(100);

        if (roll < 5) {
            return EventRarity.LEGENDARY;
        } else if (roll < 20) {
            return EventRarity.MYTHIC;
        } else if (roll < 50) {
            return EventRarity.RARE;
        } else {
            return EventRarity.DEFAULT;
        }
    }

    public long getTimeUntilNextPalmEvent() {
        int intervalMinutes = plugin.getConfig().getInt("palm-event.interval", 60);
        if (intervalMinutes == 0) return 0;

        long intervalMillis = intervalMinutes * 60 * 1000L;
        long timeSinceLastEvent = System.currentTimeMillis() - lastPalmEventTime;
        long timeUntilNext = intervalMillis - timeSinceLastEvent;
        return Math.max(0, timeUntilNext / 1000);
    }

    @NotNull
    private Component parseColorCodes(@NotNull String message) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
    }

    private static class PalmTreeData {
        final Location location;
        final List<ItemDisplay> coconutDisplays;
        final EventRarity rarity;
        final double fallHeight;
        final boolean[] fallenCoconuts;

        PalmTreeData(Location location, List<ItemDisplay> coconutDisplays, EventRarity rarity, double fallHeight) {
            this.location = location;
            this.coconutDisplays = coconutDisplays;
            this.rarity = rarity;
            this.fallHeight = fallHeight;
            this.fallenCoconuts = new boolean[5];
        }
    }
}