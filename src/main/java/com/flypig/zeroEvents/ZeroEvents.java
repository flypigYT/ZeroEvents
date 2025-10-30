package com.flypig.zeroEvents;

import com.flypig.zeroEvents.commands.ZeroEventsCommand;
import com.flypig.zeroEvents.gui.LootGUI;
import com.flypig.zeroEvents.listeners.ShulkerListener;
import com.flypig.zeroEvents.loot.LootManager;
import com.flypig.zeroEvents.managers.EventManager;
import com.flypig.zeroEvents.managers.PalmEventManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class ZeroEvents extends JavaPlugin {

    private EventManager eventManager;
    private PalmEventManager palmEventManager;
    private LootManager lootManager;
    private LootGUI lootGUI;

    @Override
    public void onEnable() {
        // Создание папки плагина
        if (!getDataFolder().exists()) {
            if (!getDataFolder().mkdirs()) {
                getLogger().severe("Не удалось создать папку плагина!");
            }
        }

        // Сохранение дефолтного конфига
        saveDefaultConfig();

        // Создание loot.yml если не существует
        File lootFile = new File(getDataFolder(), "loot.yml");
        if (!lootFile.exists()) {
            saveResource("loot.yml", false);
        }

        // Инициализация менеджеров
        lootManager = new LootManager(this);
        eventManager = new EventManager(this);
        palmEventManager = new PalmEventManager(this);
        lootGUI = new LootGUI(this);

        new ShulkerListener(this);

        // Регистрация команд
        PluginCommand command = getCommand("zeroevents");
        if (command != null) {
            ZeroEventsCommand commandExecutor = new ZeroEventsCommand(this);
            command.setExecutor(commandExecutor);
            command.setTabCompleter(commandExecutor);
        }

        // Запуск таймеров ивентов
        eventManager.startEventTimer();
        palmEventManager.startPalmEventTimer();

        getLogger().info("ZeroEvents успешно загружен!");
    }

    @Override
    public void onDisable() {
        // Остановка таймеров и очистка активных ивентов
        if (eventManager != null) {
            eventManager.stopEventTimer();
            eventManager.cleanupActiveEvents();
        }

        if (palmEventManager != null) {
            palmEventManager.stopPalmEventTimer();
            palmEventManager.cleanupActivePalms();
        }

        getLogger().info("ZeroEvents выключен!");
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public PalmEventManager getPalmEventManager() {
        return palmEventManager;
    }

    public LootManager getLootManager() {
        return lootManager;
    }

    public LootGUI getLootGUI() {
        return lootGUI;
    }

    public void reloadPluginConfig() {
        reloadConfig();
        lootManager.loadLoot();
        eventManager.reloadEventTimer();
        palmEventManager.stopPalmEventTimer();
        palmEventManager.startPalmEventTimer();
    }
}