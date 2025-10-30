package com.flypig.zeroEvents.managers;

import com.flypig.zeroEvents.ZeroEvents;
import com.flypig.zeroEvents.enums.EventRarity;
import com.flypig.zeroEvents.enums.EventType;
import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class EventManager {

    private final ZeroEvents plugin;
    private BukkitTask vaseEventTimer;
    private BukkitTask shulkerEventTimer;
    private final Map<Location, VaseData> activeVases;
    private final Map<Location, ShulkerData> activeShulkers;
    private long lastVaseEventTime;
    private long lastShulkerEventTime;
    private final Random random;

    public EventManager(ZeroEvents plugin) {
        this.plugin = plugin;
        this.activeVases = new HashMap<>();
        this.activeShulkers = new HashMap<>();
        this.lastVaseEventTime = System.currentTimeMillis();
        this.lastShulkerEventTime = System.currentTimeMillis();
        this.random = new Random();
    }

    public void startEventTimer() {
        int vaseIntervalMinutes = plugin.getConfig().getInt("vase-event.interval", 60);

        // Проверка на interval = 0 (отключено)
        if (vaseIntervalMinutes > 0) {
            long vaseIntervalTicks = vaseIntervalMinutes * 60 * 20L;

            vaseEventTimer = new BukkitRunnable() {
                @Override
                public void run() {
                    startVaseEvent(getRandomRarity());
                }
            }.runTaskTimer(plugin, vaseIntervalTicks, vaseIntervalTicks);

            lastVaseEventTime = System.currentTimeMillis();
        }

        int shulkerIntervalMinutes = plugin.getConfig().getInt("shulker-event.interval", 60);

        // Проверка на interval = 0 (отключено)
        if (shulkerIntervalMinutes > 0) {
            long shulkerIntervalTicks = shulkerIntervalMinutes * 60 * 20L;

            shulkerEventTimer = new BukkitRunnable() {
                @Override
                public void run() {
                    startShulkerEvent(getRandomRarity());
                }
            }.runTaskTimer(plugin, shulkerIntervalTicks, shulkerIntervalTicks);

            lastShulkerEventTime = System.currentTimeMillis();
        }
    }

    // Новая система редкости с шансами
    private EventRarity getRandomRarity() {
        int roll = random.nextInt(100);

        if (roll < 5) { // 5%
            return EventRarity.LEGENDARY;
        } else if (roll < 20) { // 15%
            return EventRarity.MYTHIC;
        } else if (roll < 50) { // 30%
            return EventRarity.RARE;
        } else { // 50%
            return EventRarity.DEFAULT;
        }
    }

    public void stopEventTimer() {
        if (vaseEventTimer != null && !vaseEventTimer.isCancelled()) {
            vaseEventTimer.cancel();
        }
        if (shulkerEventTimer != null && !shulkerEventTimer.isCancelled()) {
            shulkerEventTimer.cancel();
        }
    }

    public void reloadEventTimer() {
        stopEventTimer();
        startEventTimer();
    }

    public void startVaseEvent(EventRarity rarity) {
        Location eventLocation = getVaseEventLocation();
        if (eventLocation == null) {
            plugin.getLogger().warning("Не удалось получить локацию для вазы!");
            return;
        }

        spawnDecoratedPot(eventLocation, rarity);
        notifyPlayers(eventLocation, EventType.VASE, rarity);

        lastVaseEventTime = System.currentTimeMillis();
    }

    public void startShulkerEvent(EventRarity rarity) {
        List<Location> locations = getShulkerEventLocations();
        if (locations.isEmpty()) {
            plugin.getLogger().warning("Не настроены локации для шалкеров!");
            return;
        }

        for (Location location : locations) {
            spawnShulker(location, rarity);
        }

        lastShulkerEventTime = System.currentTimeMillis();
    }

    @Nullable
    private Location getVaseEventLocation() {
        String worldName = plugin.getConfig().getString("vase-event.location.world");
        if (worldName == null) return null;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = plugin.getConfig().getDouble("vase-event.location.x");
        double y = plugin.getConfig().getDouble("vase-event.location.y");
        double z = plugin.getConfig().getDouble("vase-event.location.z");

        return new Location(world, x, y, z);
    }

    @NotNull
    private List<Location> getShulkerEventLocations() {
        List<Location> locations = new ArrayList<>();
        var locationsSection = plugin.getConfig().getConfigurationSection("shulker-event.locations");

        if (locationsSection == null) return locations;

        for (String key : locationsSection.getKeys(false)) {
            String worldName = plugin.getConfig().getString("shulker-event.locations." + key + ".world");
            if (worldName == null) continue;

            World world = Bukkit.getWorld(worldName);
            if (world == null) continue;

            double x = plugin.getConfig().getDouble("shulker-event.locations." + key + ".x");
            double y = plugin.getConfig().getDouble("shulker-event.locations." + key + ".y");
            double z = plugin.getConfig().getDouble("shulker-event.locations." + key + ".z");

            locations.add(new Location(world, x, y, z));
        }

        return locations;
    }

    private void spawnDecoratedPot(@NotNull Location location, EventRarity rarity) {
        Block block = location.getBlock();
        block.setType(Material.DECORATED_POT);

        Particle particle = getParticleForRarity(rarity);
        Sound sound = getSoundForRarity(rarity);

        location.getWorld().spawnParticle(particle,
                location.clone().add(0.5, 0.5, 0.5), 50, 0.3, 0.3, 0.3, 0.1);
        location.getWorld().playSound(location, sound, 1.0f, 1.0f);

        Hologram hologram = createFancyHologram(
                location.clone().add(0.5, 1.5, 0.5),
                EventType.VASE,
                rarity,
                0,
                0
        );

        int dropAmount = plugin.getConfig().getInt("vase-event.rarities." +
                rarity.name().toLowerCase() + ".drops.amount", 5);
        int dropInterval = plugin.getConfig().getInt("vase-event.drops.interval-ticks", 40);
        int lifetimeSeconds = plugin.getConfig().getInt("vase-event.rarities." +
                rarity.name().toLowerCase() + ".lifetime-seconds", 300);

        VaseData vaseData = new VaseData(location.clone(), hologram, rarity, dropAmount, dropInterval);
        activeVases.put(location.clone(), vaseData);

        startItemDropScheduler(vaseData);
        startLifetimeTimer(location.clone(), lifetimeSeconds, EventType.VASE);
    }

    private void spawnShulker(@NotNull Location location, EventRarity rarity) {
        Block block = location.getBlock();
        Material shulkerMaterial = getShulkerBoxForRarity(rarity);
        block.setType(shulkerMaterial);

        int maxHits = plugin.getConfig().getInt("shulker-event.rarities." +
                rarity.name().toLowerCase() + ".hits", 50);
        int lifetimeSeconds = plugin.getConfig().getInt("shulker-event.rarities." +
                rarity.name().toLowerCase() + ".lifetime-seconds", 300);

        Hologram hologram = createFancyHologram(
                location.clone().add(0.5, 1.5, 0.5),
                EventType.SHULKER,
                rarity,
                maxHits,
                maxHits
        );

        ShulkerData data = new ShulkerData(location.clone(), hologram, maxHits, maxHits, rarity);
        activeShulkers.put(location.clone(), data);

        Particle particle = getParticleForRarity(rarity);
        Sound sound = getSoundForRarity(rarity);
        location.getWorld().spawnParticle(particle, location.clone().add(0.5, 0.5, 0.5), 30, 0.3, 0.3, 0.3, 0.1);
        location.getWorld().playSound(location, sound, 1.0f, 1.0f);

        notifyPlayers(location, EventType.SHULKER, rarity);
        startLifetimeTimer(location.clone(), lifetimeSeconds, EventType.SHULKER);
    }

    private void startLifetimeTimer(Location location, int lifetimeSeconds, EventType type) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (type == EventType.VASE) {
                    if (activeVases.containsKey(location)) {
                        removeVase(location, true);
                    }
                } else {
                    if (activeShulkers.containsKey(location)) {
                        removeShulker(location, true);
                    }
                }
            }
        }.runTaskLater(plugin, lifetimeSeconds * 20L);
    }

    private Hologram createFancyHologram(Location location, EventType type, EventRarity rarity,
                                         int currentHits, int maxHits) {
        String configPath = type == EventType.VASE ? "vase-event" : "shulker-event";
        List<String> hologramLines = plugin.getConfig().getStringList(
                configPath + ".rarities." + rarity.name().toLowerCase() + ".hologram");

        if (hologramLines.isEmpty()) {
            hologramLines = Collections.singletonList("&6Ивент");
        }

        List<String> processedLines = hologramLines.stream()
                .map(line -> line.replace("%hits%", String.valueOf(currentHits))
                        .replace("%max%", String.valueOf(maxHits)))
                .map(this::translateColorCodes)
                .collect(Collectors.toList());

        String hologramName = type.name().toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);

        TextHologramData data = new TextHologramData(hologramName, location);
        data.setText(processedLines);
        data.setPersistent(false);

        Hologram hologram = FancyHologramsPlugin.get().getHologramManager().create(data);
        FancyHologramsPlugin.get().getHologramManager().addHologram(hologram);

        String cmd = "hologram edit " + hologramName + " background transparent";
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

        return hologram;
    }

    private void notifyPlayers(@NotNull Location location, EventType type, EventRarity rarity) {
        String configPath = type == EventType.VASE ? "vase-event" : "shulker-event";
        String messageStr = plugin.getConfig().getString(configPath + ".rarities." +
                rarity.name().toLowerCase() + ".start-message");

        if (messageStr != null) {
            messageStr = messageStr.replace("%x%", String.valueOf(location.getBlockX()))
                    .replace("%y%", String.valueOf(location.getBlockY()))
                    .replace("%z%", String.valueOf(location.getBlockZ()));
            Component message = parseColorCodes(messageStr);

            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(message);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        }
    }

    private void startItemDropScheduler(@NotNull VaseData vaseData) {
        final int[] dropsLeft = {vaseData.dropAmount};

        vaseData.dropTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (dropsLeft[0] <= 0 || !activeVases.containsKey(vaseData.location)) {
                    this.cancel();
                    if (activeVases.containsKey(vaseData.location)) {
                        removeVase(vaseData.location, false);
                    }
                    return;
                }

                dropSingleItem(vaseData.location, vaseData.rarity);
                dropsLeft[0]--;
            }
        }.runTaskTimer(plugin, 20L, vaseData.dropInterval);
    }

    private void dropSingleItem(@NotNull Location location, EventRarity rarity) {
        Location dropLocation = location.clone().add(0.5, 1.0, 0.5);

        playNewDropEffects(dropLocation, rarity);

        ItemStack item = plugin.getLootManager().getRandomLootItem(EventType.VASE, rarity);
        if (item == null) {
            plugin.getLogger().warning("Нет предметов в луте для дропа!");
            return;
        }

        double minVelocity = plugin.getConfig().getDouble("vase-event.drops.velocity.min", 0.3);
        double maxVelocity = plugin.getConfig().getDouble("vase-event.drops.velocity.max", 0.8);

        double angle = random.nextDouble() * 2 * Math.PI;
        double velocityMagnitude = minVelocity + (maxVelocity - minVelocity) * random.nextDouble();

        Vector velocity = new Vector(
                Math.cos(angle) * velocityMagnitude,
                0.3 + random.nextDouble() * 0.4,
                Math.sin(angle) * velocityMagnitude
        );

        Item droppedItem = dropLocation.getWorld().dropItem(dropLocation, item);
        droppedItem.setVelocity(velocity);
        droppedItem.setPickupDelay(20);

        dropLocation.getWorld().spawnParticle(Particle.ITEM,
                dropLocation, 10, 0.2, 0.2, 0.2, 0.05, item);

        // УБРАНО СООБЩЕНИЕ items-dropped
    }

    private void playNewDropEffects(@NotNull Location location, EventRarity rarity) {
        Particle particle = getParticleForRarity(rarity);
        location.getWorld().spawnParticle(particle, location, 50, 0.1, 0.1, 0.1, 0.3);
        location.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, location, 15, 0.2, 0.2, 0.2, 0.1);

        location.getWorld().playSound(location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.5f, 1.5f);

        new BukkitRunnable() {
            @Override
            public void run() {
                location.getWorld().playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            }
        }.runTaskLater(plugin, 5L);
    }

    private void removeVase(@NotNull Location location, boolean timeout) {
        VaseData vaseData = activeVases.remove(location);
        if (vaseData == null) return;

        Block block = location.getBlock();
        if (block.getType() == Material.DECORATED_POT) {
            block.setType(Material.AIR);

            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (ticks >= 10) {
                        this.cancel();
                        return;
                    }

                    Location particleLoc = location.clone().add(0.5, 0.5 + (ticks * 0.08), 0.5);
                    location.getWorld().spawnParticle(Particle.WHITE_SMOKE,
                            particleLoc, 3, 0.15, 0.08, 0.15, 0.01);

                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);

            location.getWorld().playSound(location, Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.2f);
        }

        if (vaseData.dropTask != null && !vaseData.dropTask.isCancelled()) {
            vaseData.dropTask.cancel();
        }

        if (vaseData.hologram != null) {
            FancyHologramsPlugin.get().getHologramManager().removeHologram(vaseData.hologram);
        }

        String messageKey = timeout ? "messages.vase-timeout" : "messages.event-end";
        String messageStr = plugin.getConfig().getString(messageKey);
        if (messageStr != null) {
            Component message = parseColorCodes(messageStr);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(message);
            }
        }
    }

    public void handleShulkerDamage(Location location) {
        ShulkerData data = activeShulkers.get(location);
        if (data == null) return;

        data.currentHits--;

        // МОМЕНТАЛЬНОЕ обновление голограммы
        updateShulkerHologram(data);

        if (data.currentHits <= 0) {
            destroyShulker(location);
        }
    }

    // Новый метод для моментального обновления голограммы
    private void updateShulkerHologram(ShulkerData data) {
        List<String> hologramLines = plugin.getConfig().getStringList(
                "shulker-event.rarities." + data.rarity.name().toLowerCase() + ".hologram");

        if (!hologramLines.isEmpty()) {
            List<String> processedLines = hologramLines.stream()
                    .map(line -> line.replace("%hits%", String.valueOf(data.currentHits))
                            .replace("%max%", String.valueOf(data.maxHits)))
                    .map(this::translateColorCodes)
                    .collect(Collectors.toList());

            var holoData = data.hologram.getData();
            if (holoData instanceof TextHologramData textData) {
                textData.setText(processedLines);
                // Синхронное обновление без задержки
                Bukkit.getScheduler().runTask(plugin, () -> {
                    data.hologram.forceUpdate();
                    data.hologram.refreshForViewersInWorld();
                });
            }
        }
    }

    private void destroyShulker(Location location) {
        ShulkerData data = activeShulkers.remove(location);
        if (data == null) return;

        int dropAmount = plugin.getConfig().getInt("shulker-event.rarities." +
                data.rarity.name().toLowerCase() + ".drops.amount", 5);

        for (int i = 0; i < dropAmount; i++) {
            ItemStack item = plugin.getLootManager().getRandomLootItem(EventType.SHULKER, data.rarity);
            if (item != null) {
                location.getWorld().dropItemNaturally(location.clone().add(0.5, 0.5, 0.5), item);
            }
        }

        Particle particle = getParticleForRarity(data.rarity);
        location.getWorld().spawnParticle(particle, location.clone().add(0.5, 0.5, 0.5), 100, 0.5, 0.5, 0.5, 0.2);
        location.getWorld().playSound(location, Sound.ENTITY_SHULKER_DEATH, 1.0f, 1.0f);

        location.getBlock().setType(Material.AIR);

        if (data.hologram != null) {
            FancyHologramsPlugin.get().getHologramManager().removeHologram(data.hologram);
        }

        String messageStr = plugin.getConfig().getString("messages.shulker-destroyed");
        if (messageStr != null) {
            Component message = parseColorCodes(messageStr);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(message);
            }
        }
    }

    private void removeShulker(Location location, boolean timeout) {
        ShulkerData data = activeShulkers.remove(location);
        if (data == null) return;

        location.getBlock().setType(Material.AIR);

        if (data.hologram != null) {
            FancyHologramsPlugin.get().getHologramManager().removeHologram(data.hologram);
        }

        if (timeout) {
            String messageStr = plugin.getConfig().getString("messages.shulker-timeout");
            if (messageStr != null) {
                Component message = parseColorCodes(messageStr);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(message);
                }
            }
        }
    }

    public void cleanupActiveEvents() {
        for (VaseData vaseData : new ArrayList<>(activeVases.values())) {
            Location location = vaseData.location;
            Block block = location.getBlock();
            if (block.getType() == Material.DECORATED_POT) {
                block.setType(Material.AIR);
            }

            if (vaseData.dropTask != null && !vaseData.dropTask.isCancelled()) {
                vaseData.dropTask.cancel();
            }

            if (vaseData.hologram != null) {
                FancyHologramsPlugin.get().getHologramManager().removeHologram(vaseData.hologram);
            }
        }
        activeVases.clear();

        for (ShulkerData data : new ArrayList<>(activeShulkers.values())) {
            data.location.getBlock().setType(Material.AIR);
            if (data.hologram != null) {
                FancyHologramsPlugin.get().getHologramManager().removeHologram(data.hologram);
            }
        }
        activeShulkers.clear();
    }

    public boolean isActiveShulker(Location location) {
        return activeShulkers.containsKey(location);
    }

    public boolean isActiveVase(Location location) {
        return activeVases.containsKey(location);
    }

    public long getTimeUntilNextVaseEvent() {
        int intervalMinutes = plugin.getConfig().getInt("vase-event.interval", 60);
        if (intervalMinutes == 0) return 0;

        long intervalMillis = intervalMinutes * 60 * 1000L;
        long timeSinceLastEvent = System.currentTimeMillis() - lastVaseEventTime;
        long timeUntilNext = intervalMillis - timeSinceLastEvent;
        return Math.max(0, timeUntilNext / 1000);
    }

    public long getTimeUntilNextShulkerEvent() {
        int intervalMinutes = plugin.getConfig().getInt("shulker-event.interval", 60);
        if (intervalMinutes == 0) return 0;

        long intervalMillis = intervalMinutes * 60 * 1000L;
        long timeSinceLastEvent = System.currentTimeMillis() - lastShulkerEventTime;
        long timeUntilNext = intervalMillis - timeSinceLastEvent;
        return Math.max(0, timeUntilNext / 1000);
    }

    private Particle getParticleForRarity(EventRarity rarity) {
        return switch (rarity) {
            case RARE -> Particle.SOUL_FIRE_FLAME;
            case MYTHIC -> Particle.FLAME;
            case LEGENDARY -> Particle.END_ROD;
            default -> Particle.ENCHANT;
        };
    }

    private Sound getSoundForRarity(EventRarity rarity) {
        return switch (rarity) {
            case RARE -> Sound.BLOCK_AMETHYST_BLOCK_CHIME;
            case MYTHIC -> Sound.BLOCK_BEACON_ACTIVATE;
            case LEGENDARY -> Sound.UI_TOAST_CHALLENGE_COMPLETE;
            default -> Sound.BLOCK_ENCHANTMENT_TABLE_USE;
        };
    }

    private Material getShulkerBoxForRarity(EventRarity rarity) {
        return switch (rarity) {
            case RARE -> Material.BLUE_SHULKER_BOX;
            case MYTHIC -> Material.RED_SHULKER_BOX;
            case LEGENDARY -> Material.YELLOW_SHULKER_BOX;
            default -> Material.SHULKER_BOX;
        };
    }

    @NotNull
    private Component parseColorCodes(@NotNull String message) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
    }

    @NotNull
    private String translateColorCodes(@NotNull String message) {
        return LegacyComponentSerializer.legacyAmpersand().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(message)
        );
    }

    private static class VaseData {
        Location location;
        Hologram hologram;
        EventRarity rarity;
        int dropAmount;
        int dropInterval;
        BukkitTask dropTask;

        VaseData(Location location, Hologram hologram, EventRarity rarity, int dropAmount, int dropInterval) {
            this.location = location;
            this.hologram = hologram;
            this.rarity = rarity;
            this.dropAmount = dropAmount;
            this.dropInterval = dropInterval;
        }
    }

    private static class ShulkerData {
        Location location;
        Hologram hologram;
        int currentHits;
        int maxHits;
        EventRarity rarity;

        ShulkerData(Location location, Hologram hologram, int currentHits, int maxHits, EventRarity rarity) {
            this.location = location;
            this.hologram = hologram;
            this.currentHits = currentHits;
            this.maxHits = maxHits;
            this.rarity = rarity;
        }
    }
}