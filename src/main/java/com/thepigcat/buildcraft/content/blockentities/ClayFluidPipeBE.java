package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class ClayFluidPipeBE extends FluidPipeBE {
    public ClayFluidPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.CLAY_FLUID_PIPE.get(), pos, blockState);
    }

    @Override
    protected void applyMaterialProperties() {
        setMaterialProperties(40, 10);
    }

    @Override
    protected List<Direction> selectOutputDirections(List<Direction> candidates) {
        List<Direction> nonPipe = candidates.stream()
                .filter(dir -> !(level.getBlockState(worldPosition.relative(dir)).getBlock() instanceof PipeBlock))
                .collect(java.util.stream.Collectors.toList());
        return nonPipe.isEmpty() ? candidates : nonPipe;
    }
}
