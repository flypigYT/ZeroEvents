package com.flypig.zeroEvents.listeners;

import com.flypig.zeroEvents.ZeroEvents;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

public class ShulkerListener implements Listener {

    private final ZeroEvents plugin;

    public ShulkerListener(ZeroEvents plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onShulkerBreak(@NotNull BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();

        if (!isShulkerBox(type)) {
            return;
        }

        Location location = block.getLocation();
        if (!plugin.getEventManager().isActiveShulker(location)) {
            return;
        }

        // БЛОКИРУЕМ ЛОМАНИЕ - игрок не должен получить шалкер как предмет
        event.setCancelled(true);
        event.setDropItems(false);

        // Наносим урон шалкеру
        plugin.getEventManager().handleShulkerDamage(location);
    }

    @EventHandler
    public void onShulkerInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Material type = block.getType();

        // Проверка на шалкер
        if (isShulkerBox(type)) {
            Location location = block.getLocation();
            if (plugin.getEventManager().isActiveShulker(location)) {
                // Блокируем открытие шалкера
                event.setCancelled(true);
            }
            return;
        }

        // Проверка на вазу
        if (type == Material.DECORATED_POT) {
            Location location = block.getLocation();
            if (plugin.getEventManager().isActiveVase(location)) {
                // Блокируем взаимодействие с вазой (нельзя положить предметы)
                event.setCancelled(true);
            }
        }
    }

    private boolean isShulkerBox(Material material) {
        return switch (material) {
            case SHULKER_BOX, WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX, MAGENTA_SHULKER_BOX,
                 LIGHT_BLUE_SHULKER_BOX, YELLOW_SHULKER_BOX, LIME_SHULKER_BOX, PINK_SHULKER_BOX,
                 GRAY_SHULKER_BOX, LIGHT_GRAY_SHULKER_BOX, CYAN_SHULKER_BOX, PURPLE_SHULKER_BOX,
                 BLUE_SHULKER_BOX, BROWN_SHULKER_BOX, GREEN_SHULKER_BOX, RED_SHULKER_BOX,
                 BLACK_SHULKER_BOX -> true;
            default -> false;
        };
    }
}