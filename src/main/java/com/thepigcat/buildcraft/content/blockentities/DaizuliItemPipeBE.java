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
import java.util.List;
import java.util.Set;

public class DaizuliItemPipeBE extends ItemPipeBE implements ColouredPipe {
    private DyeColor pipeColor = DyeColor.WHITE;
    private Direction targetDirection;

    public DaizuliItemPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.DAIZULI_ITEM_PIPE.get(), pos, blockState);
    }

    /**
     * Original BC routing: PipeBehaviourDaizuli.sideCheck()
     * - If item color matches pipe color -> ONLY route to targetDirection
     * - If item color does NOT match (or has no color) -> EXCLUDE targetDirection, allow all others
     */
    @Override
    protected Direction chooseDirection(Set<Direction> availableDirections) {
        if (availableDirections.isEmpty()) {
            return from;
        }

        ItemStack carried = itemHandler.getStackInSlot(0);
        DyeColor activeColor = getActiveColor();

        if (activeColor != null && targetDirection != null) {
            DyeColor itemColor = ItemUtils.getItemColor(carried);

            if (activeColor.equals(itemColor)) {
                // Color matches -> route to target direction only
                if (availableDirections.contains(targetDirection)) {
                    return targetDirection;
                }
                // Target not available, fall back to from (bounce back)
                return from;
            } else {
                // Color doesn't match (or no color) -> exclude target, route to any other
                List<Direction> candidates = new ArrayList<>(availableDirections);
                candidates.remove(targetDirection);
                if (!candidates.isEmpty()) {
                    return candidates.get(level.random.nextInt(candidates.size()));
                }
                // All directions are the target -> fall through to default
            }
        }

        return super.chooseDirection(availableDirections);
    }

    /**
     * Gate hook: returns the active color for routing decisions.
     * Gates can override this in Phase E to set the color dynamically.
     */
    public DyeColor getActiveColor() {
        return pipeColor;
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

    @Override
    public void onLoad() {
        super.onLoad();
        if (targetDirection == null && !directions.isEmpty()) {
            targetDirection = directions.iterator().next();
        }
    }

    @Override
    public void setDirections(Set<Direction> directions) {
        super.setDirections(directions);
        if (targetDirection == null && !directions.isEmpty()) {
            targetDirection = directions.iterator().next();
        }
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
