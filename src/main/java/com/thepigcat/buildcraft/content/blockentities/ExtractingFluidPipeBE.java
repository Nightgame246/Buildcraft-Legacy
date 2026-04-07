package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class ExtractingFluidPipeBE extends FluidPipeBE {
    protected final EnergyStorage energyStorage;

    public ExtractingFluidPipeBE(BlockPos pos, BlockState blockState) {
        this(BCBlockEntities.FLUID_PIPE.get(), pos, blockState);
    }

    protected ExtractingFluidPipeBE(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.energyStorage = new EnergyStorage(BCConfig.extractionPipeEnergyCapacity);
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide()) {
            extractFluid();
        }
        super.tick();
    }

    private void extractFluid() {
        if (extracting == null) return;
        if (energyStorage.getEnergyStored() < BCConfig.extractionEnergyCost) return;

        BlockCapabilityCache<IFluidHandler, Direction> cache = capabilityCaches.get(extracting);
        if (cache == null) return;
        IFluidHandler source = cache.getCapability();
        if (source == null) return;

        // Try to drain from the source
        FluidStack simulated = source.drain(transferPerTick, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.isEmpty()) return;

        // Insert into our extracting-side section
        IFluidHandler mySection = getFluidHandler(extracting);
        int accepted = mySection.fill(simulated, IFluidHandler.FluidAction.EXECUTE);
        if (accepted > 0) {
            source.drain(simulated.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
            energyStorage.extractEnergy(BCConfig.extractionEnergyCost, false);
            setChanged();
        }
    }

    public IEnergyStorage getEnergyStorage(Direction direction) {
        return energyStorage;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("energy")) {
            energyStorage.deserializeNBT(registries, tag.get("energy"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("energy", energyStorage.serializeNBT(registries));
    }
}
