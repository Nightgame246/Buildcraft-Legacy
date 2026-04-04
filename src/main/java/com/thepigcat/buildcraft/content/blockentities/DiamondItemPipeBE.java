package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DiamondItemPipeBE extends ItemPipeBE {
    private final ItemStackHandler filterHandler = new ItemStackHandler(54) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        // Organize slots as 6 groups of 9 (one for each direction)
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (!level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    public DiamondItemPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.DIAMOND_ITEM_PIPE.get(), pos, blockState);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("filters")) {
            this.filterHandler.deserializeNBT(registries, tag.getCompound("filters"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("filters", this.filterHandler.serializeNBT(registries));
    }

    public ItemStackHandler getFilterHandler() {
        return filterHandler;
    }

    /**
     * Diamond pipe routing logic (ported from PipeBehaviourDiamondItem 1.12.2):
     *
     *  - A direction that has ≥1 filter AND the carried item matches at least one → PRIORITY target.
     *  - A direction that has filters but NONE match                               → EXCLUDED.
     *  - A direction that has NO filters at all                                    → FALLBACK.
     *
     * Pick order: priority matches > unfiltered fallbacks > all available (deadlock guard).
     *
     * Matching uses {@link ItemStack#isSameItem} (item type only, ignores count & components),
     * which is the correct 1.21 equivalent of the 1.12 item-ID comparison.
     */
    @Override
    protected Direction chooseDirection(Set<Direction> availableDirections) {
        if (availableDirections.isEmpty()) return from;

        ItemStack carried = itemHandler.getStackInSlot(0);
        if (carried.isEmpty()) return super.chooseDirection(availableDirections);

        List<Direction> matched    = new ArrayList<>(); // has filter + item matches
        List<Direction> unfiltered = new ArrayList<>(); // has no filters at all

        for (Direction dir : availableDirections) {
            // Slot group: DOWN=0..8, UP=9..17, NORTH=18..26, SOUTH=27..35, WEST=36..44, EAST=45..53
            int base = dir.get3DDataValue() * 9;
            boolean hasFilter = false;
            boolean hasMatch  = false;

            for (int i = 0; i < 9; i++) {
                ItemStack filter = filterHandler.getStackInSlot(base + i);
                if (!filter.isEmpty()) {
                    hasFilter = true;
                    if (ItemStack.isSameItem(carried, filter)) {
                        hasMatch = true;
                        break;
                    }
                }
            }

            if (hasMatch) {
                matched.add(dir);
            } else if (!hasFilter) {
                unfiltered.add(dir);
            }
            // else: filtered direction with no match → excluded
        }

        List<Direction> candidates = !matched.isEmpty() ? matched : unfiltered;
        if (candidates.isEmpty()) {
            // All connected sides have filters but nothing matched —
            // fall back to random among all available to avoid deadlocking items.
            return super.chooseDirection(availableDirections);
        }
        return candidates.get(level.random.nextInt(candidates.size()));
    }

    // For client synchronization - override if needed
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }
}