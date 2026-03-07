package com.thepigcat.buildcraft.content.blockentities;

import com.portingdeadmods.portingdeadlibs.api.blockentities.ContainerBlockEntity;
import com.portingdeadmods.portingdeadlibs.utils.capabilities.HandlerUtils;
import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class QuarryBE extends ContainerBlockEntity {
    private int currentX;
    private int currentZ;
    private int currentY;
    private boolean isDone;
    private boolean started;

    private final Map<Direction, BlockCapabilityCache<IItemHandler, Direction>> itemHandlerCaches = new EnumMap<>(Direction.class);

    public QuarryBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.QUARRY.get(), pos, blockState);
        addEnergyStorage(HandlerUtils::newEnergystorage, builder -> builder
                .capacity(BCConfig.quarryEnergyCapacity)
                .maxTransfer(BCConfig.quarryEnergyPerBlock * 10));
        addItemHandler(HandlerUtils::newItemStackHandler, builder -> builder.slots(27));
    }

    public static void tick(Level level, BlockPos pos, BlockState state, QuarryBE be) {
        if (level.isClientSide()) return;
        be.pushItems();
        be.mineNext();
    }

    private void pushItems() {
        IItemHandler buffer = getItemHandler();
        if (buffer == null) return;
        for (Direction dir : Direction.values()) {
            BlockCapabilityCache<IItemHandler, Direction> cache = itemHandlerCaches.get(dir);
            if (cache == null) continue;
            IItemHandler adjacent = cache.getCapability();
            if (adjacent == null) continue;
            for (int slot = 0; slot < buffer.getSlots(); slot++) {
                ItemStack stack = buffer.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                ItemStack remainder = ItemHandlerHelper.insertItem(adjacent, stack.copy(), false);
                buffer.extractItem(slot, stack.getCount() - remainder.getCount(), false);
            }
        }
    }

    private void mineNext() {
        if (isDone) return;
        if (!started) {
            currentY = worldPosition.getY() - 1;
            started = true;
            setChanged();
        }

        BlockPos targetPos = new BlockPos(
                worldPosition.getX() - 4 + currentX,
                currentY,
                worldPosition.getZ() - 4 + currentZ
        );

        BlockState blockState = level.getBlockState(targetPos);

        // Skip air, bedrock, and fluids
        if (blockState.isAir() || blockState.is(Blocks.BEDROCK) || !blockState.getFluidState().isEmpty()) {
            advance();
            return;
        }

        // Check energy
        if (getEnergyStorage().extractEnergy(BCConfig.quarryEnergyPerBlock, true) < BCConfig.quarryEnergyPerBlock) {
            return;
        }

        // Collect drops
        List<ItemStack> drops = Block.getDrops(blockState, (ServerLevel) level, targetPos, level.getBlockEntity(targetPos));

        // Try to insert drops into buffer
        IItemHandler buffer = getItemHandler();
        List<ItemStack> remainder = new ArrayList<>();
        for (ItemStack drop : drops) {
            ItemStack remaining = ItemHandlerHelper.insertItemStacked(buffer, drop.copy(), false);
            if (!remaining.isEmpty()) {
                remainder.add(remaining);
            }
        }

        // Remove the block
        level.removeBlock(targetPos, false);

        // Drop any items that didn't fit in the buffer
        for (ItemStack stack : remainder) {
            Block.popResource(level, worldPosition, stack);
        }

        // Consume energy
        getEnergyStorage().extractEnergy(BCConfig.quarryEnergyPerBlock, false);
        setChanged();

        advance();
    }

    private void advance() {
        currentX++;
        if (currentX > 8) {
            currentX = 0;
            currentZ++;
            if (currentZ > 8) {
                currentZ = 0;
                currentY--;
                if (currentY < 1) {
                    isDone = true;
                }
            }
        }
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            for (Direction dir : Direction.values()) {
                itemHandlerCaches.put(dir, BlockCapabilityCache.create(
                        Capabilities.ItemHandler.BLOCK, serverLevel,
                        worldPosition.relative(dir), dir.getOpposite()
                ));
            }
        }
    }

    public IItemHandler getItemHandler(Direction side) {
        return getItemHandler();
    }

    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadData(tag, provider);
        this.currentX = tag.getInt("currentX");
        this.currentZ = tag.getInt("currentZ");
        this.currentY = tag.getInt("currentY");
        this.isDone = tag.getBoolean("isDone");
        this.started = tag.getBoolean("started");
    }

    @Override
    protected void saveData(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveData(tag, provider);
        tag.putInt("currentX", currentX);
        tag.putInt("currentZ", currentZ);
        tag.putInt("currentY", currentY);
        tag.putBoolean("isDone", isDone);
        tag.putBoolean("started", started);
    }
}
