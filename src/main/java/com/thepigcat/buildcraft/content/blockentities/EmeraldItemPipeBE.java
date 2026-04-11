package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.BCConfig;
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
    public enum FilterMode {
        WHITELIST, BLACKLIST;

        public FilterMode toggle() {
            return this == WHITELIST ? BLACKLIST : WHITELIST;
        }
    }

    private final ItemStackHandler filterHandler = new ItemStackHandler(9) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    private FilterMode filterMode = FilterMode.WHITELIST;

    public EmeraldItemPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.EMERALD_ITEM_PIPE.get(), pos, blockState);
    }

    @Override
    protected void extractItems() {
        if (!itemHandler.getStackInSlot(0).isEmpty()) {
            return;
        }

        if (energyStorage.getEnergyStored() < BCConfig.extractionEnergyCost) {
            return;
        }

        BlockCapabilityCache<IItemHandler, Direction> cache = capabilityCaches.get(this.extracting);
        if (cache != null) {
            IItemHandler extractingHandler = cache.getCapability();

            if (extractingHandler != null) {
                ItemStack extractedStack = ItemStack.EMPTY;
                int extractedSlot = 0;

                for (int i = 0; i < extractingHandler.getSlots(); i++) {
                    ItemStack simulated = extractingHandler.extractItem(i, 64, true);
                    if (!simulated.isEmpty() && matchesFilter(simulated)) {
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

                    energyStorage.extractEnergy(BCConfig.extractionEnergyCost, false);

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
        boolean matchesAny = false;

        for (int i = 0; i < filterHandler.getSlots(); i++) {
            ItemStack filterStack = filterHandler.getStackInSlot(i);
            if (!filterStack.isEmpty()) {
                anyNonEmpty = true;
                if (ItemStack.isSameItem(stack, filterStack)) {
                    matchesAny = true;
                    break;
                }
            }
        }

        if (!anyNonEmpty) {
            // Empty filter: whitelist = everything passes, blacklist = nothing passes
            return getFilterMode() == FilterMode.WHITELIST;
        }

        return switch (getFilterMode()) {
            case WHITELIST -> matchesAny;
            case BLACKLIST -> !matchesAny;
        };
    }

    /**
     * Gate hook: returns the active filter mode.
     * Gates can override this in Phase E to set the mode dynamically.
     */
    public FilterMode getFilterMode() {
        return filterMode;
    }

    public void setFilterMode(FilterMode mode) {
        this.filterMode = mode;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void toggleFilterMode() {
        setFilterMode(filterMode.toggle());
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
        if (tag.contains("filter_mode")) {
            this.filterMode = FilterMode.values()[tag.getInt("filter_mode")];
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("filter", this.filterHandler.serializeNBT(registries));
        tag.putInt("filter_mode", this.filterMode.ordinal());
    }
}
