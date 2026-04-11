package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class GoldItemPipeBE extends ItemPipeBE {
    private static final float SPEED_TARGET = 0.25f;
    private static final float SPEED_DELTA = 0.01f;

    public GoldItemPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.GOLD_ITEM_PIPE.get(), pos, blockState);
    }

    @Override
    public void tick() {
        if (!this.itemHandler.getStackInSlot(0).isEmpty()) {
            if (itemSpeed < SPEED_TARGET) {
                itemSpeed = Math.min(itemSpeed + SPEED_DELTA, SPEED_TARGET);
            }
        }
        super.tick();
    }
}
