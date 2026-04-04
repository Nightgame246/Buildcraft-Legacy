package com.thepigcat.buildcraft.content.menus;

import com.thepigcat.buildcraft.content.blockentities.DiamondItemPipeBE;
import com.thepigcat.buildcraft.registries.BCMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public class DiamondPipeMenu extends AbstractContainerMenu {
    public static final int FILTER_ROWS = 6;
    public static final int FILTER_COLS = 9;
    public static final int FILTER_SLOTS = FILTER_ROWS * FILTER_COLS; // 54

    private static final int PLAYER_INV_START = FILTER_SLOTS;
    private static final int PLAYER_INV_END   = FILTER_SLOTS + 27;
    private static final int HOTBAR_END        = FILTER_SLOTS + 36;

    public final DiamondItemPipeBE blockEntity;

    public DiamondPipeMenu(int containerId, Inventory inv, DiamondItemPipeBE blockEntity) {
        super(BCMenuTypes.DIAMOND_PIPE.get(), containerId);
        this.blockEntity = blockEntity;

        // 54 ghost filter slots — 6 directions × 9 slots each
        for (int row = 0; row < FILTER_ROWS; row++) {
            for (int col = 0; col < FILTER_COLS; col++) {
                int slot = row * FILTER_COLS + col;
                addSlot(new SlotItemHandler(blockEntity.getFilterHandler(), slot,
                        8 + col * 18,
                        18 + row * 18));
            }
        }

        // Player main inventory (3 × 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 198));
        }
    }

    public DiamondPipeMenu(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(containerId, inv, (DiamondItemPipeBE) inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    /**
     * Ghost slot behavior: clicking places a copy of the carried item into the filter
     * without consuming it. Right-click or empty-handed click clears the slot.
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < FILTER_SLOTS) {
            Slot slot = this.slots.get(slotId);
            ItemStack carried = this.getCarried();
            if (!carried.isEmpty()) {
                slot.set(carried.copyWithCount(1));
            } else {
                slot.set(ItemStack.EMPTY);
            }
            return; // don't propagate — ghost slots don't consume items
        }
        super.clicked(slotId, button, clickType, player);
    }

    /**
     * Shift-click only moves items between player inventory and hotbar.
     * Filter slots are excluded from all shift-click logic.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < FILTER_SLOTS) {
            return ItemStack.EMPTY;
        }
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < PLAYER_INV_END) {
                if (!this.moveItemStackTo(stack, PLAYER_INV_END, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity.getLevel() != null
                && blockEntity.getLevel().getBlockEntity(blockEntity.getBlockPos()) == blockEntity
                && player.distanceToSqr(
                        blockEntity.getBlockPos().getX() + 0.5,
                        blockEntity.getBlockPos().getY() + 0.5,
                        blockEntity.getBlockPos().getZ() + 0.5) <= 64.0;
    }
}
