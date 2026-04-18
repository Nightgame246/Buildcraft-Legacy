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

public class IronItemPipeBE extends ItemPipeBE {
    private Direction lockedDirection;

    public IronItemPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.IRON_ITEM_PIPE.get(), pos, blockState);
    }

    @Override
    protected Direction chooseDirection(Set<Direction> availableDirections) {
        if (lockedDirection != null && availableDirections.contains(lockedDirection)) {
            return lockedDirection;
        }
        return super.chooseDirection(availableDirections);
    }

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
            int currentIndex = sorted.indexOf(lockedDirection);
            lockedDirection = sorted.get((currentIndex + 1) % sorted.size());
        }

        updateBlockedDirections();
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void updateBlockedDirections() {
        this.blocked.clear();
        if (lockedDirection != null) {
            for (Direction dir : directions) {
                if (dir != lockedDirection) {
                    this.blocked.add(dir);
                }
            }
        }
        
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            boolean changed = false;
            for (Direction dir : Direction.values()) {
                PipeBlock.PipeState currentPipeState = state.getValue(PipeBlock.CONNECTION[dir.get3DDataValue()]);
                if (currentPipeState != PipeBlock.PipeState.NONE) {
                    PipeBlock.PipeState targetState = (dir == lockedDirection) ? PipeBlock.PipeState.CONNECTED : PipeBlock.PipeState.BLOCKED;
                    if (currentPipeState != targetState) {
                        state = state.setValue(PipeBlock.CONNECTION[dir.get3DDataValue()], targetState);
                        changed = true;
                    }
                }
            }
            if (changed) {
                level.setBlock(worldPosition, state, 3);
            }
        }
    }

    public Direction getLockedDirection() {
        return lockedDirection;
    }

    @Override
    public void onLoad() {
        super.onLoad(); // populates directions via PipeBlock.setPipeProperties
        // Default to the first connected direction if no lockedDirection was saved
        if (lockedDirection == null && !directions.isEmpty()) {
            lockedDirection = directions.iterator().next();
        }
        updateBlockedDirections();
    }

    @Override
    public void setDirections(Set<Direction> directions) {
        super.setDirections(directions);
        // Auto-assign lockedDirection on first connection (e.g. during placement)
        if (lockedDirection == null && !directions.isEmpty()) {
            lockedDirection = directions.iterator().next();
        }
        updateBlockedDirections();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // tag.getInt returns 0 (not -1) when the key is absent, so check explicitly.
        if (tag.contains("locked_direction")) {
            int lockedIndex = tag.getInt("locked_direction");
            this.lockedDirection = lockedIndex >= 0 ? Direction.values()[lockedIndex] : null;
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
