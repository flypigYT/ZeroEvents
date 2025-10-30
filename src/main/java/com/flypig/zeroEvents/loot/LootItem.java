package com.flypig.zeroEvents.loot;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class LootItem {
    private final int slot;
    private int chance;
    private final ItemStack item;

    public LootItem(int slot, int chance, @NotNull ItemStack item) {
        this.slot = slot;
        this.chance = chance;
        this.item = item.clone();
    }

    public int getSlot() {
        return slot;
    }

    public int getChance() {
        return chance;
    }

    @NotNull
    public ItemStack getItem() {
        return item.clone();
    }

    public void setChance(int chance) {
        this.chance = chance;
    }
}