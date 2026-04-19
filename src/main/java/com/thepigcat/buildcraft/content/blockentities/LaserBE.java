package com.thepigcat.buildcraft.content.blockentities;

import com.portingdeadmods.portingdeadlibs.api.blockentities.ContainerBlockEntity;
import com.portingdeadmods.portingdeadlibs.utils.capabilities.HandlerUtils;
import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class LaserBE extends ContainerBlockEntity {
    public LaserBE(BlockPos pos, BlockState state) {
        super(BCBlockEntities.LASER.get(), pos, state);
        addEnergyStorage(HandlerUtils::newEnergystorage, builder -> builder
                .capacity(BCConfig.laserBatteryCapacity)
                .maxTransfer(BCConfig.laserMaxReceive));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LaserBE be) {
        // implemented in Task 4
    }
}
