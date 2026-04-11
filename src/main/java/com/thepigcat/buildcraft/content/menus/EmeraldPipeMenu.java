package com.thepigcat.buildcraft.content.menus;

import com.thepigcat.buildcraft.content.blockentities.EmeraldItemPipeBE;
import com.thepigcat.buildcraft.registries.BCMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public class EmeraldPipeMenu extends AbstractContainerMenu {
    public static final int FILTER_SLOTS = 9;

    private static final int PLAYER_INV_START = FILTER_SLOTS;
    private static final int PLAYER_INV_END   = FILTER_SLOTS + 27;
    private static final int HOTBAR_END        = FILTER_SLOTS + 36;

    public final EmeraldItemPipeBE blockEntity;
    private final ContainerData data;

    public EmeraldPipeMenu(int containerId, Inventory inv, EmeraldItemPipeBE blockEntity) {
        super(BCMenuTypes.EMERALD_PIPE.get(), containerId);
        this.blockEntity = blockEntity;

        // Sync filterMode to client via ContainerData
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return blockEntity.getFilterMode().ordinal();
            }

            @Override
            public void set(int index, int value) {
                blockEntity.setFilterMode(EmeraldItemPipeBE.FilterMode.values()[value]);
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
        addDataSlots(data);

        // 9 ghost filter slots in a 3x3 grid
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slot = row * 3 + col;
                addSlot(new SlotItemHandler(blockEntity.getFilterHandler(), slot,
                        62 + col * 18,
                        21 + row * 18));
            }
        }

        // Player main inventory (3 x 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
    }

    public EmeraldPipeMenu(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(containerId, inv, (EmeraldItemPipeBE) inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    public EmeraldItemPipeBE.FilterMode getFilterMode() {
        return EmeraldItemPipeBE.FilterMode.values()[data.get(0)];
    }

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
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

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
