package com.thepigcat.buildcraft.content.menus;

import com.portingdeadmods.portingdeadlibs.api.gui.menus.PDLAbstractContainerMenu;
import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.content.blockentities.IntegrationTableBE;
import com.thepigcat.buildcraft.registries.BCMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class IntegrationTableMenu extends PDLAbstractContainerMenu<IntegrationTableBE> {
    public static final int INPUT_START = 0;   // inputs (center+ring) menu indices 0..8
    public static final int INPUT_END = 9;
    public static final int PREVIEW = 9;        // output preview (display only)
    public static final int OUTPUT = 10;        // real output slot
    public static final int PLAYER_START = 11;  // player inv + hotbar

    public IntegrationTableMenu(int id, @NotNull Inventory inv, @NotNull IntegrationTableBE be) {
        super(BCMenuTypes.INTEGRATION_TABLE.get(), id, inv, be);

        // 3x3 grid at (19,24) with 25px spacing. Middle cell = center (inputs[0]);
        // the other 8 cells = ring (inputs[1..8]) in reading order.
        int ringHandlerIdx = 1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int handlerIdx = (row == 1 && col == 1) ? IntegrationTableBE.CENTER : ringHandlerIdx++;
                addSlot(new SlotItemHandler(be.getInputs(), handlerIdx, 19 + col * 25, 24 + row * 25));
            }
        }

        // Output preview (display only, index 9) at (101, 36)
        addSlot(new SlotDisplay(be::getAssumedOutput, 101, 36));

        // Real output slot (index 10) at (138, 49)
        addSlot(new SlotItemHandler(be.getOutput(), 0, 138, 49));

        addPlayerInventory(inv, 109);
        addPlayerHotbar(inv, 167);
    }

    public IntegrationTableMenu(int id, @NotNull Inventory inv, @NotNull RegistryFriendlyByteBuf buf) {
        this(id, inv, (IntegrationTableBE) inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index >= PLAYER_START) {
                // player inventory → inputs
                if (!moveItemStackTo(stack, INPUT_START, INPUT_END, false)) return ItemStack.EMPTY;
            } else if (index == OUTPUT || (index >= INPUT_START && index < INPUT_END)) {
                // inputs or real output → player inventory
                if (!moveItemStackTo(stack, PLAYER_START, this.slots.size(), true)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY; // preview: no shift-move
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        }
        return result;
    }

    @Override protected int getMergeableSlotCount() { return INPUT_END; }

    public int getPower() { return blockEntity.power; }
    public int getFeCost() { return BCConfig.integrationTableFeCost; }
    public ItemStack getAssumedOutput() { return blockEntity.getAssumedOutput(); }
}
