package com.thepigcat.buildcraft.content.menus;

import com.portingdeadmods.portingdeadlibs.api.gui.menus.PDLAbstractContainerMenu;
import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.content.blockentities.AdvancedCraftingTableBE;
import com.thepigcat.buildcraft.registries.BCMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class AdvancedCraftingTableMenu extends PDLAbstractContainerMenu<AdvancedCraftingTableBE> {
    public static final int MAT_START = 0;      // materials 0..14
    public static final int MAT_END = 15;
    public static final int RES_START = 15;     // results 15..23
    public static final int RES_END = 24;
    public static final int BP_START = 24;      // blueprint 24..32
    public static final int BP_END = 33;
    public static final int PREVIEW = 33;       // result preview
    public static final int PLAYER_START = 34;  // player inv + hotbar 34..69

    public AdvancedCraftingTableMenu(int id, @NotNull Inventory inv, @NotNull AdvancedCraftingTableBE be) {
        super(BCMenuTypes.ADVANCED_CRAFTING_TABLE.get(), id, inv, be);

        // Coordinates match the original BC 1.12 advanced_crafting_table.png (176x241 layout).
        // Materials 5x3 (menu indices 0..14) — original origin (15, 85)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 5; col++)
                addSlot(new SlotItemHandler(be.getMaterials(), row * 5 + col, 15 + col * 18, 85 + row * 18));

        // Results 3x3 (menu indices 15..23) — original origin (109, 85)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++)
                addSlot(new SlotItemHandler(be.getResults(), row * 3 + col, 109 + col * 18, 85 + row * 18));

        // Blueprint phantom 3x3 (menu indices 24..32) — original origin (33, 16)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++)
                addSlot(new PhantomSlot(be.getBlueprint(), row * 3 + col, 33 + col * 18, 16 + row * 18));

        // Result preview (menu index 33) — original (127, 33)
        addSlot(new SlotDisplay(be::getAssumedResult, 127, 33));

        addPlayerInventory(inv, 153);
        addPlayerHotbar(inv, 211);
    }

    public AdvancedCraftingTableMenu(int id, @NotNull Inventory inv, @NotNull RegistryFriendlyByteBuf buf) {
        this(id, inv, (AdvancedCraftingTableBE) inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    @Override
    public void clicked(int slotId, int button, @NotNull ClickType clickType, @NotNull Player player) {
        if (slotId >= BP_START && slotId < BP_END) {
            Slot slot = this.slots.get(slotId);
            ItemStack carried = getCarried();
            if (clickType == ClickType.PICKUP || clickType == ClickType.PICKUP_ALL || clickType == ClickType.QUICK_MOVE) {
                slot.set(carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return; // phantom slots never move real items and never consume the carried stack
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index >= PLAYER_START) {
                // player inventory → materials
                if (!moveItemStackTo(stack, MAT_START, MAT_END, false)) return ItemStack.EMPTY;
            } else if (index >= MAT_START && index < RES_END) {
                // materials or results → player inventory
                if (!moveItemStackTo(stack, PLAYER_START, this.slots.size(), true)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY; // blueprint / preview: no shift-move
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        }
        return result;
    }

    @Override protected int getMergeableSlotCount() { return MAT_END; }

    public int getPower() { return blockEntity.power; }
    public int getFeCost() { return BCConfig.advancedCraftingTableFeCost; }
    public ItemStack getAssumedResult() { return blockEntity.getAssumedResult(); }
}
