package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.networking.SyncPipeDirectionPayload;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ExtractItemPipeBE extends ItemPipeBE {
    protected final EnergyStorage energyStorage;

    public ExtractItemPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.EXTRACTING_ITEM_PIPE.get(), pos, blockState);
        this.energyStorage = new EnergyStorage(BCConfig.extractionPipeEnergyCapacity);
    }

    protected ExtractItemPipeBE(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.energyStorage = new EnergyStorage(BCConfig.extractionPipeEnergyCapacity);
    }

    @Override
    public void tick() {
        if (!level.isClientSide()) {
            extractItems();
        }
        super.tick();
    }

    protected void extractItems() {
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
                    ItemStack stack = extractingHandler.extractItem(i, 64, false);
                    if (!stack.isEmpty()) {
                        extractedStack = stack;
                        extractedSlot = i;
                        break;
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
