package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Set;

public class IronFluidPipeBE extends FluidPipeBE {
    private Direction lockedDirection;

    public IronFluidPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.IRON_FLUID_PIPE.get(), pos, blockState);
    }

    @Override
    protected boolean isOutputAllowed(Direction dir) {
        return lockedDirection == null || dir == lockedDirection;
    }

    @Override
    protected boolean isInputBlocked(int sectionIdx) {
        // lockedDirection is the *output* face; block external fill on it
        // to prevent back-pressure from downstream re-entering the pipe.
        return lockedDirection != null && Direction.values()[sectionIdx] == lockedDirection;
    }

    /** Rotates the locked output direction through all currently connected directions. */
    public void rotateLockedDirection() {
        if (directions.isEmpty()) {
            lockedDirection = null;
            updateBlockedDirections();
            return;
        }

        List<Direction> sorted = directions.stream()
                .sorted(java.util.Comparator.comparingInt(Direction::ordinal))
                .toList();

        if (lockedDirection == null || !sorted.contains(lockedDirection)) {
            lockedDirection = sorted.getFirst();
        } else {
            int idx = sorted.indexOf(lockedDirection);
            lockedDirection = sorted.get((idx + 1) % sorted.size());
        }

        updateBlockedDirections();
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Sets the blockstate CONNECTION properties: CONNECTED for lockedDirection (output), BLOCKED for others. */
    private void updateBlockedDirections() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        boolean changed = false;
        for (Direction dir : Direction.values()) {
            PipeBlock.PipeState current = state.getValue(PipeBlock.CONNECTION[dir.get3DDataValue()]);
            if (current != PipeBlock.PipeState.NONE) {
                PipeBlock.PipeState target = (dir == lockedDirection)
                        ? PipeBlock.PipeState.CONNECTED
                        : PipeBlock.PipeState.BLOCKED;
                if (current != target) {
                    state = state.setValue(PipeBlock.CONNECTION[dir.get3DDataValue()], target);
                    changed = true;
                }
            }
        }
        if (changed) {
            level.setBlock(worldPosition, state, 3);
        }
    }

    public Direction getLockedDirection() {
        return lockedDirection;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Default to first connected direction if nothing was saved
        if (lockedDirection == null && !directions.isEmpty()) {
            lockedDirection = directions.iterator().next();
        }
        updateBlockedDirections();
    }

    @Override
    public void setDirections(Set<Direction> directions) {
        super.setDirections(directions);
        if (lockedDirection == null && !directions.isEmpty()) {
            lockedDirection = directions.iterator().next();
        }
        updateBlockedDirections();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("locked_direction")) {
            int idx = tag.getInt("locked_direction");
            this.lockedDirection = idx >= 0 ? Direction.values()[idx] : null;
        } else {
            this.lockedDirection = null;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("locked_direction", lockedDirection != null ? lockedDirection.ordinal() : -1);
    }
}
