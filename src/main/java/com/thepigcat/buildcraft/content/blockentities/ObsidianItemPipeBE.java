package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.networking.SyncPipeDirectionPayload;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

public class ObsidianItemPipeBE extends ItemPipeBE {
    private int suctionCooldown = 0;

    public ObsidianItemPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.OBSIDIAN_ITEM_PIPE.get(), pos, blockState);
    }

    @Override
    public void tick() {
        if (!level.isClientSide()) {
            if (suctionCooldown > 0) {
                suctionCooldown--;
            } else if (itemHandler.getStackInSlot(0).isEmpty()) {
                // Find open sides (sides that are NOT connected to anything)
                Set<Direction> openSides = new HashSet<>();
                for (Direction dir : Direction.values()) {
                    if (!directions.contains(dir)) {
                        openSides.add(dir);
                    }
                }

                if (!openSides.isEmpty()) {
                    for (Direction dir : openSides) {
                        AABB aabb = new AABB(worldPosition.relative(dir)).inflate(0.5);
                        List<ItemEntity> itemEntities = level.getEntitiesOfClass(ItemEntity.class, aabb);
                        for (ItemEntity itemEntity : itemEntities) {
                            if (itemEntity.isRemoved()) continue;
                            
                            ItemStack stack = itemEntity.getItem();
                            ItemStack remainder = itemHandler.insertItem(0, stack, false);
                            if (remainder.getCount() < stack.getCount()) {
                                if (remainder.isEmpty()) {
                                    itemEntity.discard();
                                } else {
                                    itemEntity.setItem(remainder);
                                }

                                // Set direction for the newly suctioned item
                                if (!directions.isEmpty()) {
                                    this.setFrom(dir);
                                    this.setTo(chooseDirection(directions));
                                    PacketDistributor.sendToAllPlayers(new SyncPipeDirectionPayload(worldPosition, Optional.ofNullable(from), Optional.ofNullable(to)));
                                }
                                suctionCooldown = 10;
                                break;
                            }
                        }
                    }
                }
            }
        }

        super.tick();
    }
}
