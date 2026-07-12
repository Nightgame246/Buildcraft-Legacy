package com.thepigcat.buildcraft.content.blockentities;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

/**
 * Capability view exposed to pipes/hoppers. Insertion goes only into the inputs handler
 * (center + ring); extraction comes only from the output handler.
 * Slots [0, inputs.size) map to inputs; [inputs.size, total) map to output.
 */
public class IntegrationTableIOHandler implements IItemHandler {
    private final IItemHandlerModifiable inputs;
    private final IItemHandlerModifiable output;

    public IntegrationTableIOHandler(IItemHandlerModifiable inputs, IItemHandlerModifiable output) {
        this.inputs = inputs;
        this.output = output;
    }

    @Override public int getSlots() { return inputs.getSlots() + output.getSlots(); }

    @Override public @NotNull ItemStack getStackInSlot(int slot) {
        return slot < inputs.getSlots()
                ? inputs.getStackInSlot(slot)
                : output.getStackInSlot(slot - inputs.getSlots());
    }

    @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (slot >= inputs.getSlots()) return stack; // output rejects insertion
        return inputs.insertItem(slot, stack, simulate);
    }

    @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < inputs.getSlots()) return ItemStack.EMPTY; // inputs reject pipe extraction
        return output.extractItem(slot - inputs.getSlots(), amount, simulate);
    }

    @Override public int getSlotLimit(int slot) {
        return slot < inputs.getSlots()
                ? inputs.getSlotLimit(slot)
                : output.getSlotLimit(slot - inputs.getSlots());
    }

    @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return slot < inputs.getSlots() && inputs.isItemValid(slot, stack);
    }
}
