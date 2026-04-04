package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

public class ClayItemPipeBE extends ItemPipeBE {
    public ClayItemPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.CLAY_ITEM_PIPE.get(), pos, blockState);
    }

    @Override
    protected Direction chooseDirection(Set<Direction> availableDirections) {
        for (Direction dir : availableDirections) {
            if (!(level.getBlockState(worldPosition.relative(dir)).getBlock() instanceof PipeBlock)) {
                return dir;
            }
        }
        return super.chooseDirection(availableDirections);
    }
}
