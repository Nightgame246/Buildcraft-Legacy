package com.thepigcat.buildcraft.content.menus;

import com.portingdeadmods.portingdeadlibs.api.gui.menus.PDLAbstractContainerMenu;
import com.thepigcat.buildcraft.api.recipes.AssemblyRecipe;
import com.thepigcat.buildcraft.api.recipes.AssemblyRecipeRegistry;
import com.thepigcat.buildcraft.content.blockentities.AssemblyTableBE;
import com.thepigcat.buildcraft.content.enums.EnumAssemblyRecipeState;
import com.thepigcat.buildcraft.registries.BCMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AssemblyTableMenu extends PDLAbstractContainerMenu<AssemblyTableBE> {
    public AssemblyTableMenu(int id, @NotNull Inventory inv, @NotNull AssemblyTableBE be) {
        super(BCMenuTypes.ASSEMBLY_TABLE.get(), id, inv, be);
        // Input slots (0-11): 3 cols x 4 rows at x=8, y=36
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new SlotItemHandler(be.getItemHandler(), row * 3 + col, 8 + col * 18, 36 + row * 18));
            }
        }
        // Display slots (12-23): recipe outputs at x=116, y=36
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 3; col++) {
                final int idx = row * 3 + col;
                addSlot(new SlotDisplay(() -> getDisplayStack(idx), 116 + col * 18, 36 + row * 18));
            }
        }
        addPlayerInventory(inv, 123);
        addPlayerHotbar(inv, 181);
    }

    public AssemblyTableMenu(int id, @NotNull Inventory inv, @NotNull RegistryFriendlyByteBuf buf) {
        this(id, inv, (AssemblyTableBE) inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    private ItemStack getDisplayStack(int idx) {
        List<ResourceLocation> keys = new ArrayList<>(blockEntity.recipeStates.keySet());
        if (idx >= keys.size()) return ItemStack.EMPTY;
        AssemblyRecipe recipe = AssemblyRecipeRegistry.get(keys.get(idx));
        return recipe != null ? recipe.output().copy() : ItemStack.EMPTY;
    }

    public Map<ResourceLocation, EnumAssemblyRecipeState> getRecipeStates() {
        return blockEntity.recipeStates;
    }

    public long getPower() { return blockEntity.power; }

    public long getTarget() {
        var active = blockEntity.getActiveRecipe();
        return active != null ? active.feCost() : 0;
    }

    @Override
    protected int getMergeableSlotCount() { return 12; }
}
