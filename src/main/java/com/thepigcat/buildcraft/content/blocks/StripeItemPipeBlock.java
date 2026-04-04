package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stripe Pipe: connects only to other pipes, never to inventories.
 * Its open end interacts with the world (placing blocks, using items).
 */
public class StripeItemPipeBlock extends ItemPipeBlock {
    public StripeItemPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public PipeState getConnectionType(LevelAccessor level, BlockPos pipePos, BlockState pipeState, Direction connectionDirection, BlockPos connectPos) {
        BlockState otherState = level.getBlockState(connectPos);
        Block otherBlock = otherState.getBlock();

        // Only connect to other pipes, never to inventories
        if (otherBlock instanceof PipeBlock) {
            return PipeState.CONNECTED;
        }

        return PipeState.NONE;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(StripeItemPipeBlock::new);
    }

    @Override
    protected BlockEntityType<? extends PipeBlockEntity<?>> getBlockEntityType() {
        return BCBlockEntities.STRIPE_ITEM_PIPE.get();
    }
}
