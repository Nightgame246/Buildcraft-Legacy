package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import com.thepigcat.buildcraft.networking.SyncPipeDirectionPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EmeraldItemPipeBE extends ExtractItemPipeBE {
    private final ItemStackHandler filterHandler = new ItemStackHandler(9) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    public EmeraldItemPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.EMERALD_ITEM_PIPE.get(), pos, blockState);
    }

    @Override
    protected void extractItems() {
        if (!itemHandler.getStackInSlot(0).isEmpty()) {
            return;
        }

        BlockCapabilityCache<IItemHandler, Direction> cache = capabilityCaches.get(this.extracting);
        if (cache != null) {
            IItemHandler extractingHandler = cache.getCapability();

            if (extractingHandler != null) {
                ItemStack extractedStack = ItemStack.EMPTY;
                int extractedSlot = 0;

                for (int i = 0; i < extractingHandler.getSlots(); i++) {
                    // Simulate extraction first to preview the stack
                    ItemStack simulated = extractingHandler.extractItem(i, 64, true);
                    if (!simulated.isEmpty() && matchesFilter(simulated)) {
                        // Filter passes, actually extract
                        ItemStack stack = extractingHandler.extractItem(i, 64, false);
                        if (!stack.isEmpty()) {
                            extractedStack = stack;
                            extractedSlot = i;
                            break;
                        }
                    }
                }

                if (!extractedStack.isEmpty()) {
                    ItemStack insertRemainder = itemHandler.insertItem(0, extractedStack, false);
                    extractingHandler.insertItem(extractedSlot, insertRemainder, false);

                    this.setFrom(this.extracting);

                    List<Direction> directions = new ArrayList<>(this.directions);
                    directions.remove(this.extracting);

                    if (!directions.isEmpty()) {
                        this.setTo(directions.getFirst());
                    }

                    PacketDistributor.sendToAllPlayers(new SyncPipeDirectionPayload(worldPosition, Optional.ofNullable(from), Optional.ofNullable(to)));
                }
            }
        }
    }

    public boolean matchesFilter(ItemStack stack) {
        boolean anyNonEmpty = false;
        for (int i = 0; i < filterHandler.getSlots(); i++) {
            ItemStack filterStack = filterHandler.getStackInSlot(i);
            if (!filterStack.isEmpty()) {
                anyNonEmpty = true;
                if (ItemStack.isSameItem(stack, filterStack)) {
                    return true;
                }
            }
        }
        // If all filter slots are empty, any item passes
        return !anyNonEmpty;
    }

    public ItemStackHandler getFilterHandler() {
        return filterHandler;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("filter")) {
            this.filterHandler.deserializeNBT(registries, tag.getCompound("filter"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("filter", this.filterHandler.serializeNBT(registries));
    }
}
