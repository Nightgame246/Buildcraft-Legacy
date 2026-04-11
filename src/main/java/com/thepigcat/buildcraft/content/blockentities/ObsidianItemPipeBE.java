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

public class ObsidianItemPipeBE extends ItemPipeBE {
    private static final int SUCTION_COOLDOWN_TICKS = 10;
    private int suctionCooldown = 0;

    public ObsidianItemPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.OBSIDIAN_ITEM_PIPE.get(), pos, blockState);
    }

    /**
     * Returns the single open face of this pipe, or null if there are 0 or 2+ open faces.
     * Original BC: Obsidian pipe only sucks items when exactly one face is open.
     */
    public Direction getOpenFace() {
        Direction openFace = null;
        for (Direction dir : Direction.values()) {
            if (!directions.contains(dir)) {
                if (openFace != null) {
                    return null; // 2+ open faces -> no suction
                }
                openFace = dir;
            }
        }
        return openFace; // null if all 6 sides connected (0 open faces)
    }

    @Override
    public void tick() {
        if (!level.isClientSide()) {
            if (suctionCooldown > 0) {
                suctionCooldown--;
            } else if (itemHandler.getStackInSlot(0).isEmpty()) {
                Direction openFace = getOpenFace();
                if (openFace != null) {
                    AABB aabb = new AABB(worldPosition.relative(openFace)).inflate(0.5);
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

                            // Route item from open face into the pipe network
                            if (!directions.isEmpty()) {
                                this.setFrom(openFace);
                                this.setTo(chooseDirection(directions));
                                PacketDistributor.sendToAllPlayers(new SyncPipeDirectionPayload(worldPosition, Optional.ofNullable(from), Optional.ofNullable(to)));
                            }
                            suctionCooldown = SUCTION_COOLDOWN_TICKS;
                            break;
                        }
                    }
                }
            }
        }

        super.tick();
    }
}
