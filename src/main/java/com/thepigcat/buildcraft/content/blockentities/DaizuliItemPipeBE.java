package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.util.ItemUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Set;

public class DaizuliItemPipeBE extends ItemPipeBE implements ColouredPipe {
    private DyeColor pipeColor = DyeColor.WHITE;
    private Direction targetDirection;

    public DaizuliItemPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.DAIZULI_ITEM_PIPE.get(), pos, blockState);
    }

    @Override
    protected Direction chooseDirection(Set<Direction> availableDirections) {
        if (availableDirections.isEmpty()) {
            return from;
        }

        ItemStack carried = itemHandler.getStackInSlot(0);
        if (targetDirection != null && availableDirections.contains(targetDirection) && ItemUtils.hasItemColor(carried, pipeColor)) {
            return targetDirection;
        }

        ArrayList<Direction> candidates = new ArrayList<>(availableDirections);
        candidates.remove(targetDirection);
        if (candidates.isEmpty()) {
            return super.chooseDirection(availableDirections);
        }
        return candidates.get(level.random.nextInt(candidates.size()));
    }

    public void cyclePipeColor() {
        setPipeColor(DyeColor.byId((pipeColor.getId() + 1) % DyeColor.values().length));
    }

    public void setPipeColor(DyeColor pipeColor) {
        this.pipeColor = pipeColor;
        notifyConfigChanged();
    }

    public Direction getTargetDirection() {
        return targetDirection;
    }

    public void setTargetDirection(Direction targetDirection) {
        this.targetDirection = targetDirection;
        notifyConfigChanged();
    }

    @Override
    public DyeColor getPipeColor() {
        return pipeColor;
    }

    private void notifyConfigChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.pipeColor = DyeColor.byId(tag.getInt("pipe_color"));
        this.targetDirection = tag.contains("target_direction") ? Direction.values()[tag.getInt("target_direction")] : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("pipe_color", this.pipeColor.getId());
        if (this.targetDirection != null) {
            tag.putInt("target_direction", this.targetDirection.ordinal());
        }
    }
}
