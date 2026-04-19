package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

public class DiamondFluidPipeBE extends FluidPipeBE {

    private final ItemStackHandler filterHandler = new ItemStackHandler(54) {
        @Override
        public int getSlotLimit(int slot) { return 1; }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    public DiamondFluidPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.DIAMOND_FLUID_PIPE.get(), pos, blockState);
    }

    @Override
    protected void applyMaterialProperties() {
        setMaterialProperties(80, 10);
    }

    @Override
    protected List<Direction> selectOutputDirections(List<Direction> candidates) {
        FluidStack current = getCurrentFluid();
        if (current.isEmpty()) return candidates;

        List<Direction> priority = new ArrayList<>();
        List<Direction> fallback = new ArrayList<>();

        for (Direction dir : candidates) {
            int base = dir.get3DDataValue() * 9;
            boolean hasFilter = false;
            boolean hasMatch = false;

            for (int i = 0; i < 9; i++) {
                ItemStack filter = filterHandler.getStackInSlot(base + i);
                if (filter.isEmpty()) continue;
                IFluidHandlerItem handler = filter.getCapability(Capabilities.FluidHandler.ITEM);
                if (handler == null || handler.getTanks() == 0) continue;
                FluidStack filterFluid = handler.getFluidInTank(0);
                if (filterFluid.isEmpty()) continue;
                hasFilter = true;
                if (FluidStack.isSameFluid(filterFluid, current)) {
                    hasMatch = true;
                    break;
                }
            }

            if (hasMatch) priority.add(dir);
            else if (!hasFilter) fallback.add(dir);
            // else: filtered but no match — excluded
        }

        if (!priority.isEmpty()) return priority;
        if (!fallback.isEmpty()) return fallback;
        return candidates; // deadlock guard
    }

    public ItemStackHandler getFilterHandler() {
        return filterHandler;
    }

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

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("filters")) {
            filterHandler.deserializeNBT(registries, tag.getCompound("filters"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("filters", filterHandler.serializeNBT(registries));
    }
}
