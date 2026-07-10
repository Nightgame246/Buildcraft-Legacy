package com.thepigcat.buildcraft.content.menus;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/** Ghost slot for the blueprint grid: shows an item template but never holds or moves a real stack. */
public class PhantomSlot extends SlotItemHandler {
    public PhantomSlot(IItemHandler handler, int index, int x, int y) {
        super(handler, index, x, y);
    }

    @Override public boolean mayPlace(@NotNull ItemStack stack) { return false; }

    @Override public boolean mayPickup(@NotNull Player player) { return false; }
}
