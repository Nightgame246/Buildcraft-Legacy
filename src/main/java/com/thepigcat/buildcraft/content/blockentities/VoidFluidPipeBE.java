package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class VoidFluidPipeBE extends FluidPipeBE {
    public VoidFluidPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.VOID_FLUID_PIPE.get(), pos, blockState);
    }

    @Override
    protected boolean isVoidPipe() {
        return true;
    }
}
