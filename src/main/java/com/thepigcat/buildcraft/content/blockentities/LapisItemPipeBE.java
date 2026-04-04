package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.util.ItemUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class LapisItemPipeBE extends ItemPipeBE implements ColouredPipe {
    private DyeColor pipeColor = DyeColor.WHITE;

    public LapisItemPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.LAPIS_ITEM_PIPE.get(), pos, blockState);
    }

    @Override
    public void tick() {
        super.tick();

        if (level != null && !level.isClientSide()) {
            ItemStack carried = this.itemHandler.getStackInSlot(0);
            if (!carried.isEmpty() && this.lastMovement < 0.5f && this.movement >= 0.5f) {
                ItemStack coloured = carried.copy();
                ItemUtils.setItemColor(coloured, pipeColor);
                this.itemHandler.setStackInSlot(0, coloured);
            }
        }
    }

    public void cyclePipeColor() {
        setPipeColor(DyeColor.byId((pipeColor.getId() + 1) % DyeColor.values().length));
    }

    public void setPipeColor(DyeColor pipeColor) {
        this.pipeColor = pipeColor;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public DyeColor getPipeColor() {
        return pipeColor;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.pipeColor = DyeColor.byId(tag.getInt("pipe_color"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("pipe_color", this.pipeColor.getId());
    }
}
