package com.thepigcat.buildcraft.content.menus;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class SlotDisplay extends Slot {
    private static final SimpleContainer DUMMY = new SimpleContainer(1);
    private final Supplier<ItemStack> displaySupplier;

    public SlotDisplay(Supplier<ItemStack> displaySupplier, int x, int y) {
        super(DUMMY, 0, x, y);
        this.displaySupplier = displaySupplier;
    }

    @Override
    public @NotNull ItemStack getItem() {
        return displaySupplier.get();
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public void set(@NotNull ItemStack stack) {}
}
