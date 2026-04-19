package com.thepigcat.buildcraft.content.menus;

import com.portingdeadmods.portingdeadlibs.api.gui.menus.PDLAbstractContainerMenu;
import com.thepigcat.buildcraft.content.blockentities.AssemblyTableBE;
import com.thepigcat.buildcraft.content.enums.EnumAssemblyRecipeState;
import com.thepigcat.buildcraft.registries.BCMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class AssemblyTableMenu extends PDLAbstractContainerMenu<AssemblyTableBE> {
    public AssemblyTableMenu(int id, @NotNull Inventory inv, @NotNull AssemblyTableBE be) {
        super(BCMenuTypes.ASSEMBLY_TABLE.get(), id, inv, be);
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new SlotItemHandler(be.getItemHandler(), row * 3 + col, 8 + col * 18, 18 + row * 18));
            }
        }
        addPlayerInventory(inv, 175);
        addPlayerHotbar(inv, 233);
    }

    public AssemblyTableMenu(int id, @NotNull Inventory inv, @NotNull RegistryFriendlyByteBuf buf) {
        this(id, inv, (AssemblyTableBE) inv.player.level().getBlockEntity(buf.readBlockPos()));
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
