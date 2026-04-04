package com.thepigcat.buildcraft.content.blockentities;

import com.mojang.authlib.GameProfile;
import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.networking.SyncPipeDirectionPayload;
import com.thepigcat.buildcraft.networking.SyncPipeMovementPayload;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Stripe Pipe BlockEntity: when an item reaches the open end (non-pipe side),
 * it tries to place the item as a block or use it on entities/blocks via a FakePlayer.
 */
public class StripeItemPipeBE extends ItemPipeBE {
    private static final GameProfile STRIPE_PIPE_PROFILE =
            new GameProfile(UUID.nameUUIDFromBytes("buildcraft:stripe_pipe".getBytes()), "[Buildcraft Stripe Pipe]");

    public StripeItemPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.STRIPE_ITEM_PIPE.get(), pos, blockState);
    }

    @Override
    protected Direction chooseDirection(Set<Direction> availableDirections) {
        // If there are pipe connections available (normal routing), use default logic
        if (!availableDirections.isEmpty()) {
            return super.chooseDirection(availableDirections);
        }

        // No pipe connection on the other side — find the output direction (open end)
        // Prefer the opposite of from (item continues straight ahead)
        if (from != null) {
            Direction opposite = from.getOpposite();
            if (!directions.contains(opposite)) {
                return opposite;
            }
        }

        // Fall back to any non-connected direction
        for (Direction dir : Direction.values()) {
            if (!directions.contains(dir) && dir != from) {
                return dir;
            }
        }

        return from; // last resort: bounce back
    }

    @Override
    public void tick() {
        // Intercept before super.tick() — handle world interaction at the open end
        if (!level.isClientSide() && this.movement >= 1f && to != null) {
            BlockPos targetPos = worldPosition.relative(to);
            boolean isOpenEnd = !directions.contains(to);

            if (isOpenEnd) {
                ItemStack stack = itemHandler.getStackInSlot(0);
                if (!stack.isEmpty()) {
                    boolean success = tryWorldInteraction(stack, to, targetPos);
                    if (success) {
                        // If the stack was fully consumed, clear the slot
                        if (stack.isEmpty()) {
                            itemHandler.setStackInSlot(0, ItemStack.EMPTY);
                        }
                        resetMovement();
                        return;
                    }
                    // Interaction failed — drop the item into the world instead of bouncing back
                    dropItem(stack, to, targetPos);
                    itemHandler.setStackInSlot(0, ItemStack.EMPTY);
                    resetMovement();
                    return;
                }
            }
        }

        super.tick();
    }

    private void dropItem(ItemStack stack, Direction outputDir, BlockPos targetPos) {
        if (!level.isClientSide()) {
            net.minecraft.world.level.block.Block.popResource(level, targetPos, stack.copy());
            BuildcraftLegacy.LOGGER.debug("Stripe pipe dropped item at {} because interaction failed", targetPos);
        }
    }

    private boolean tryWorldInteraction(ItemStack stack, Direction outputDir, BlockPos targetPos) {
        if (!(level instanceof ServerLevel serverLevel)) return false;

        FakePlayer fakePlayer = FakePlayerFactory.get(serverLevel, STRIPE_PIPE_PROFILE);
        fakePlayer.setPos(Vec3.atCenterOf(worldPosition));

        // Give the fake player a copy of one item
        ItemStack singleItem = stack.copyWithCount(1);
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, singleItem);

        BlockHitResult hitResult = new BlockHitResult(
                Vec3.atCenterOf(targetPos),
                outputDir.getOpposite(),
                targetPos,
                false
        );

        // 1. Try placing the item as a block (covers BlockItems like stone, redstone, etc.)
        UseOnContext useOnContext = new UseOnContext(fakePlayer, InteractionHand.MAIN_HAND, hitResult);
        InteractionResult placeResult = singleItem.useOn(useOnContext);
        if (placeResult.consumesAction()) {
            stack.shrink(1);
            BuildcraftLegacy.LOGGER.debug("Stripe pipe placed block at {}", targetPos);
            return true;
        }

        // 2. Try entity interaction (shears on sheep, lead on fence, etc.)
        AABB targetAABB = new AABB(targetPos);
        List<Entity> entities = level.getEntities((Entity) null, targetAABB, e -> e.isAlive());
        for (Entity entity : entities) {
            // Reset hand item (may have been modified by useOn)
            fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, singleItem.copyWithCount(1));
            InteractionResult entityResult = fakePlayer.interactOn(entity, InteractionHand.MAIN_HAND);
            if (entityResult.consumesAction()) {
                ItemStack handResult = fakePlayer.getItemInHand(InteractionHand.MAIN_HAND);
                if (handResult.isEmpty() || handResult.getCount() < 1) {
                    stack.shrink(1);
                }
                BuildcraftLegacy.LOGGER.debug("Stripe pipe interacted with entity {} at {}", entity, targetPos);
                return true;
            }
        }

        // 3. Try using the item in the air (e.g., splash potions, ender pearls)
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, singleItem.copyWithCount(1));
        InteractionResult useResult = singleItem.use(serverLevel, fakePlayer, InteractionHand.MAIN_HAND).getResult();
        if (useResult.consumesAction()) {
            ItemStack handResult = fakePlayer.getItemInHand(InteractionHand.MAIN_HAND);
            if (handResult.isEmpty() || handResult.getCount() < 1) {
                stack.shrink(1);
            }
            BuildcraftLegacy.LOGGER.debug("Stripe pipe used item at {}", targetPos);
            return true;
        }

        return false;
    }

    private void resetMovement() {
        this.lastMovement = 0;
        this.movement = 0;
        this.setFrom(null);
        this.setTo(null);
        PacketDistributor.sendToAllPlayers(new SyncPipeDirectionPayload(worldPosition, Optional.empty(), Optional.empty()));
        PacketDistributor.sendToAllPlayers(new SyncPipeMovementPayload(worldPosition, this.movement, this.lastMovement));
    }

    private void bounceBack() {
        Direction oldTo = this.to;
        this.setTo(from);
        this.setFrom(oldTo);
        this.lastMovement = 0;
        this.movement = 0;
        PacketDistributor.sendToAllPlayers(new SyncPipeMovementPayload(worldPosition, this.movement, this.lastMovement));
        PacketDistributor.sendToAllPlayers(new SyncPipeDirectionPayload(worldPosition, Optional.ofNullable(from), Optional.ofNullable(this.to)));
    }
}
