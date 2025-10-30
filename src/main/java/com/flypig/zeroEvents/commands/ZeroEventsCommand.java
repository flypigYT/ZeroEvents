package com.flypig.zeroEvents.commands;

import com.flypig.zeroEvents.ZeroEvents;
import com.flypig.zeroEvents.enums.EventRarity;
import com.flypig.zeroEvents.enums.EventType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ZeroEventsCommand implements CommandExecutor, TabCompleter {

    private final ZeroEvents plugin;

    public ZeroEventsCommand(ZeroEvents plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String @NotNull [] args) {
        if (!sender.hasPermission("zeroevents.admin")) {
            sender.sendMessage(Component.text("У вас нет прав для использования этой команды!")
                    .color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadPluginConfig();
                sender.sendMessage(parseColorCodes(
                        plugin.getConfig().getString("messages.reload", "&aКонфигурация перезагружена!")
                ));
                break;

            case "start":
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Использование: /ze start <vase|shulker|palm> [default|rare|mythic|legendary]")
                            .color(NamedTextColor.RED));
                    return true;
                }

                EventType type = EventType.fromString(args[1]);
                EventRarity rarity = args.length >= 3 ? EventRarity.fromString(args[2]) : EventRarity.DEFAULT;

                if (type == EventType.VASE) {
                    plugin.getEventManager().startVaseEvent(rarity);
                } else if (type == EventType.SHULKER) {
                    plugin.getEventManager().startShulkerEvent(rarity);
                } else if (type == EventType.PALM) {
                    plugin.getPalmEventManager().startPalmEvent();
                }

                sender.sendMessage(parseColorCodes(
                        plugin.getConfig().getString("messages.event-started-manually",
                                "&aИвент " + type.name().toLowerCase() + " (" + rarity.name().toLowerCase() + ") запущен!")
                ));
                break;

            case "info":
                long nextVaseTime = plugin.getEventManager().getTimeUntilNextVaseEvent();
                long nextShulkerTime = plugin.getEventManager().getTimeUntilNextShulkerEvent();
                long nextPalmTime = plugin.getPalmEventManager().getTimeUntilNextPalmEvent();

                sender.sendMessage(Component.text("=== Информация об ивентах ===")
                        .color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
                sender.sendMessage(Component.text("Следующая ваза через: " + (nextVaseTime / 60) + " минут")
                        .color(NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("Следующие шалкеры через: " + (nextShulkerTime / 60) + " минут")
                        .color(NamedTextColor.YELLOW));
                sender.sendMessage(Component.text("Следующая пальма через: " + (nextPalmTime / 60) + " минут")
                        .color(NamedTextColor.YELLOW));
                break;

            case "loot":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Эта команда доступна только игрокам!")
                            .color(NamedTextColor.RED));
                    return true;
                }

                if (args.length < 3) {
                    sender.sendMessage(Component.text("Использование: /ze loot <vase|shulker|palm> <default|rare|mythic|legendary>")
                            .color(NamedTextColor.RED));
                    return true;
                }

                EventType lootType = EventType.fromString(args[1]);
                EventRarity lootRarity = EventRarity.fromString(args[2]);

                plugin.getLootGUI().openLootMenu(player, lootType, lootRarity);
                break;

            case "clean":
                plugin.getEventManager().cleanupActiveEvents();
                plugin.getPalmEventManager().cleanupActivePalms();
                sender.sendMessage(Component.text("Все активные ивенты очищены!")
                        .color(NamedTextColor.GREEN));
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String @NotNull [] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("reload", "start", "info", "loot", "clean"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("loot"))) {
            completions.addAll(Arrays.asList("vase", "shulker", "palm"));
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("loot"))) {
            completions.addAll(Arrays.asList("default", "rare", "mythic", "legendary"));
        }

        return completions;
    }

    private void sendHelp(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text("=== ZeroEvents Команды ===")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("/ze reload")
                .color(NamedTextColor.YELLOW)
                .append(Component.text(" - Перезагрузить конфигурацию").color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/ze start <vase|shulker|palm> [rarity]")
                .color(NamedTextColor.YELLOW)
                .append(Component.text(" - Запустить ивент").color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/ze info")
                .color(NamedTextColor.YELLOW)
                .append(Component.text(" - Информация о следующих ивентах").color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/ze loot <vase|shulker|palm> <rarity>")
                .color(NamedTextColor.YELLOW)
                .append(Component.text(" - Настроить лут").color(NamedTextColor.WHITE)));
        sender.sendMessage(Component.text("/ze clean")
                .color(NamedTextColor.YELLOW)
                .append(Component.text(" - Удалить все активные ивенты").color(NamedTextColor.WHITE)));
    }

    private @NotNull Component parseColorCodes(@NotNull String message) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
    }
}