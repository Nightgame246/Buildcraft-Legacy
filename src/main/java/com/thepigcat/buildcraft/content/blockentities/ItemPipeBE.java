package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.PipesRegistry;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.api.pipes.Pipe;
import com.thepigcat.buildcraft.networking.SyncPipeDirectionPayload;
import com.thepigcat.buildcraft.networking.SyncPipeMovementPayload;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.util.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ItemPipeBE extends PipeBlockEntity<IItemHandler> {
    protected final ItemStackHandler itemHandler;

    protected Direction from;
    protected Direction to;
    private Direction prevFrom;
    public float movement;
    public float lastMovement;
    protected float itemSpeed = 0.01f;

    public ItemPipeBE(BlockPos pos, BlockState blockState) {
        this(BCBlockEntities.ITEM_PIPE.get(), pos, blockState);
    }

    public ItemPipeBE(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
        this.itemHandler = new ItemStackHandler(1) {
            @Override
            public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                if (BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).getPath().equals("void_pipe")) {
                    return ItemStack.EMPTY; // Void pipe consumes everything
                }
                return super.insertItem(slot, stack, simulate);
            }

            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (!level.isClientSide()) {
                    level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                }
            }
        };
    }

    @Override
    protected BlockCapability<IItemHandler, Direction> getCapType() {
        return Capabilities.ItemHandler.BLOCK;
    }

    public void tick() {
        // handle item transmission
        if (!level.isClientSide() && this.movement >= 1f) {
            if (to != null) {
                IItemHandler insertingHandler = capabilityCaches.get(to).getCapability();
                if (insertingHandler != null) {
                    ItemStack pipeContent = insertingHandler.getStackInSlot(0);

                    if (!(level.getBlockEntity(worldPosition.relative(to)) instanceof ItemPipeBE)) {
                        pipeContent = ItemStack.EMPTY;
                    }

                    if (pipeContent.isEmpty()) {
                        ItemStack remainder = insertItems(insertingHandler);
                        BuildcraftLegacy.LOGGER.debug("remainder: {}", remainder);
                        this.itemHandler.insertItem(0, remainder, false);

                        ItemPipeBE blockEntity = BlockUtils.getBE(ItemPipeBE.class, level, worldPosition.relative(this.to));

                        if (blockEntity != null) {
                            blockEntity.itemSpeed = this.itemSpeed; // Pass speed to next pipe
                            moveItemForward(blockEntity);
                        }

                        if (!remainder.isEmpty()) {
                            moveItemBackward();
                        }
                    } else {
                        moveItemBackward();
                    }
                }

            }

        }

        if (!this.itemHandler.getStackInSlot(0).isEmpty()) {
                this.lastMovement = this.movement;
                
                String pipeId = BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).getPath();
                
                // Momentum & Friction Logic (values matched to original BuildCraft 1.12.2)
                if (pipeId.equals("gold_pipe")) {
                    // Active acceleration
                    itemSpeed = Math.min(itemSpeed + 0.01f, 0.25f);
                } else if (pipeId.equals("stone_pipe")) {
                    // Low friction: preserves speed well
                    itemSpeed = Math.max(itemSpeed - 0.008f, 0.01f);
                } else if (pipeId.equals("cobblestone_pipe")) {
                    // High friction: slows items quickly
                    itemSpeed = Math.max(itemSpeed - 0.02f, 0.01f);
                } else if (pipeId.equals("sandstone_pipe")) {
                    // Low friction like stone (sandstone = no-inventory stone variant)
                    itemSpeed = Math.max(itemSpeed - 0.008f, 0.01f);
                } else {
                    // Standard friction for other pipes (Quartz, Iron, etc.)
                    itemSpeed = Math.max(itemSpeed - 0.002f, 0.01f);
                }
                
                this.movement += itemSpeed;

                if (!level.isClientSide()) {
                    BlockCapabilityCache<IItemHandler, Direction> fromCache = this.capabilityCaches.get(from);
                    BlockCapabilityCache<IItemHandler, Direction> toCache = this.capabilityCaches.get(to);
                    if (toCache == null || toCache.getCapability() == null) {
                        if (fromCache == null || fromCache.getCapability() == null) {
                            this.lastMovement = 0;
                            this.movement = 0;
                            this.setTo(null);
                            this.setFrom(null);

                            PacketDistributor.sendToAllPlayers(new SyncPipeDirectionPayload(worldPosition, Optional.empty(), Optional.empty()));
                            PacketDistributor.sendToAllPlayers(new SyncPipeMovementPayload(worldPosition, this.movement, this.lastMovement));
                        } else {
                            moveItemBackward(1 - this.lastMovement, 1 - this.movement);
                        }
                    }
                }

        } else {
            lastMovement = 0;
            movement = 0;
        }
    }

    public void setFrom(Direction from) {
        this.prevFrom = this.from;
        this.from = from;
    }

    public void setTo(Direction to) {
        this.to = to;
    }

    public Direction getFrom() {
        return from;
    }

    public Direction getTo() {
        return to;
    }

    protected Direction chooseDirection(Set<Direction> availableDirections) {
        if (availableDirections.isEmpty()) return from;
        int dirIndex = level.random.nextInt(0, availableDirections.size());
        return availableDirections.stream().toList().get(dirIndex);
    }

    private void moveItemForward(ItemPipeBE blockEntity) {
        Set<Direction> directions = new HashSet<>(blockEntity.directions);
        directions.remove(to.getOpposite());

        blockEntity.setFrom(to.getOpposite());

        blockEntity.setTo(blockEntity.chooseDirection(directions));

        blockEntity.lastMovement = Math.abs(1 - this.lastMovement);
        blockEntity.movement = Math.abs(1 - this.movement);

        PacketDistributor.sendToAllPlayers(new SyncPipeMovementPayload(blockEntity.getBlockPos(), blockEntity.movement, blockEntity.lastMovement));
        PacketDistributor.sendToAllPlayers(new SyncPipeDirectionPayload(blockEntity.getBlockPos(), Optional.ofNullable(blockEntity.from), Optional.ofNullable(blockEntity.to)));
    }

    private void moveItemBackward() {
        moveItemBackward(0, 0);
    }

    private void moveItemBackward(float lastMovement, float movement) {
        Direction to = this.to;
        this.setTo(from);
        this.setFrom(to);
        this.lastMovement = lastMovement;
        this.movement = movement;

        PacketDistributor.sendToAllPlayers(new SyncPipeMovementPayload(worldPosition, this.movement, this.lastMovement));

        PacketDistributor.sendToAllPlayers(new SyncPipeDirectionPayload(worldPosition, Optional.ofNullable(from), Optional.ofNullable(this.to)));
    }

    /**
     * @return remainder
     */
    private ItemStack insertItems(IItemHandler insertingHandler) {
        // Get stack in pipe (only simulated)
        ItemStack toInsert = itemHandler.extractItem(0, this.itemHandler.getSlotLimit(0), false);

        return ItemHandlerHelper.insertItem(insertingHandler, toInsert, false);
    }

    public IItemHandler getItemHandler(Direction direction) {
        return itemHandler;
    }

    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        this.itemHandler.deserializeNBT(registries, tag.getCompound("item_handler"));
        this.itemSpeed = tag.getFloat("item_speed");
        if (this.itemSpeed <= 0) this.itemSpeed = 0.01f;

        int toIndex = tag.getInt("to");
        if (toIndex != -1) {
            this.setTo(Direction.values()[toIndex]);
        }
        int fromIndex = tag.getInt("from");
        if (fromIndex != -1) {
            this.setFrom(Direction.values()[fromIndex]);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        tag.put("item_handler", this.itemHandler.serializeNBT(registries));
        tag.putFloat("item_speed", this.itemSpeed);

        tag.putInt("to", to != null ? to.ordinal() : -1);
        tag.putInt("from", from != null ? from.ordinal() : -1);
    }
}
