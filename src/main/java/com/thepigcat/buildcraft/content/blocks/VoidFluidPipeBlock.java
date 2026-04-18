package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class VoidFluidPipeBlock extends FluidPipeBlock {
    public VoidFluidPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected BlockEntityType<? extends PipeBlockEntity<?>> getBlockEntityType() {
        return BCBlockEntities.VOID_FLUID_PIPE.get();
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(VoidFluidPipeBlock::new);
    }
}
