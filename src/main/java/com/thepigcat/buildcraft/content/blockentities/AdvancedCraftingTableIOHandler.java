package com.thepigcat.buildcraft.content.blockentities;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

/**
 * Capability view exposed to pipes/hoppers. Insertion goes only into the materials
 * handler; extraction comes only from the results handler. Mirrors original BC 1.12
 * EnumAccess.INSERT (materials) / EnumAccess.EXTRACT (results) on all faces.
 * Slots [0, materials.size) map to materials; [materials.size, total) map to results.
 */
public class AdvancedCraftingTableIOHandler implements IItemHandler {
    private final IItemHandlerModifiable materials;
    private final IItemHandlerModifiable results;

    public AdvancedCraftingTableIOHandler(IItemHandlerModifiable materials, IItemHandlerModifiable results) {
        this.materials = materials;
        this.results = results;
    }

    @Override public int getSlots() { return materials.getSlots() + results.getSlots(); }

    @Override public @NotNull ItemStack getStackInSlot(int slot) {
        return slot < materials.getSlots()
                ? materials.getStackInSlot(slot)
                : results.getStackInSlot(slot - materials.getSlots());
    }

    @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (slot >= materials.getSlots()) return stack; // results reject insertion
        return materials.insertItem(slot, stack, simulate);
    }

    @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < materials.getSlots()) return ItemStack.EMPTY; // materials reject pipe extraction
        return results.extractItem(slot - materials.getSlots(), amount, simulate);
    }

    @Override public int getSlotLimit(int slot) {
        return slot < materials.getSlots()
                ? materials.getSlotLimit(slot)
                : results.getSlotLimit(slot - materials.getSlots());
    }

    @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return slot < materials.getSlots() && materials.isItemValid(slot, stack);
    }
}
