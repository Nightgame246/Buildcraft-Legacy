package com.thepigcat.buildcraft.content.blockentities;

import com.portingdeadmods.portingdeadlibs.api.blockentities.ContainerBlockEntity;
import com.portingdeadmods.portingdeadlibs.utils.capabilities.HandlerUtils;
import com.thepigcat.buildcraft.api.blockentities.ILaserTarget;
import com.thepigcat.buildcraft.content.menus.AssemblyTableMenu;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AssemblyTableBE extends ContainerBlockEntity implements ILaserTarget, MenuProvider {
    public long power = 0;

    public AssemblyTableBE(BlockPos pos, BlockState state) {
        super(BCBlockEntities.ASSEMBLY_TABLE.get(), pos, state);
        addItemHandler(HandlerUtils::newItemStackHandler, builder -> builder.slots(12));
    }

    @Override public int getRequiredLaserPower() { return 0; }
    @Override public void receiveLaserPower(int fe) { power += fe; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AssemblyTableBE be) {
        // implemented in Task 5
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("container.buildcraft.assembly_table");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player player) {
        return new AssemblyTableMenu(id, inv, this);
    }
}
