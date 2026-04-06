package com.thepigcat.buildcraft.content.blockentities;

import com.mojang.authlib.GameProfile;
import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.networking.SyncPipeDirectionPayload;
import com.thepigcat.buildcraft.networking.SyncPipeMovementPayload;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.network.PacketDistributor;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

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
        boolean headingToOpenEnd = to != null && !directions.contains(to)
                && !itemHandler.getStackInSlot(0).isEmpty();

        if (headingToOpenEnd) {
            // Item is heading to the open end — handle movement ourselves.
            // We MUST NOT call super.tick() here because the parent's bounce-back logic
            // would see "to has no IItemHandler capability" and call moveItemBackward(),
            // preventing the item from ever reaching movement >= 1f.
            if (!level.isClientSide() && this.movement >= 1f) {
                BlockPos targetPos = worldPosition.relative(to);
                ItemStack stack = itemHandler.getStackInSlot(0);

                boolean success = tryWorldInteraction(stack, to, targetPos);
                if (success && stack.isEmpty()) {
                    itemHandler.setStackInSlot(0, ItemStack.EMPTY);
                }
                if (!success && from == null) {
                    // No return path — drop as last resort
                    dropItem(stack, to, targetPos);
                    itemHandler.setStackInSlot(0, ItemStack.EMPTY);
                }
                // Whether success or fail: afterInteraction() reverses remaining items
                // back through the pipe, or resets if slot is empty.
                afterInteraction();
                return;
            }

            // Movement still building — apply same friction logic as ItemPipeBE
            // so client and server calculate identical movement values.
            this.lastMovement = this.movement;

            String pipeId = BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).getPath();
            if (pipeId.equals("gold_pipe")) {
                itemSpeed = Math.min(itemSpeed + 0.01f, 0.25f);
            } else if (pipeId.equals("stone_pipe")) {
                itemSpeed = Math.max(itemSpeed - 0.008f, 0.01f);
            } else if (pipeId.equals("cobblestone_pipe")) {
                itemSpeed = Math.max(itemSpeed - 0.02f, 0.01f);
            } else if (pipeId.equals("sandstone_pipe")) {
                itemSpeed = Math.max(itemSpeed - 0.008f, 0.01f);
            } else {
                itemSpeed = Math.max(itemSpeed - 0.002f, 0.01f);
            }

            this.movement += itemSpeed;
            return;
        }

        // Normal pipe routing (connected to another pipe) — delegate to parent
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
        orientFakePlayer(fakePlayer, outputDir);

        // Give the fake player a copy of one item
        ItemStack singleItem = stack.copyWithCount(1);
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, singleItem);

        // Hit the face of the target block that faces the pipe.
        // Offset 0.4 instead of 0.5 so the hit lands slightly inside the target block,
        // avoiding placement collisions with the pipe's own block position.
        Direction hitFace = outputDir.getOpposite();
        Vec3 hitLocation = Vec3.atCenterOf(targetPos).add(
                hitFace.getStepX() * 0.4,
                hitFace.getStepY() * 0.4,
                hitFace.getStepZ() * 0.4
        );
        BlockHitResult hitResult = new BlockHitResult(
                hitLocation,
                hitFace,
                targetPos,
                false
        );

        // 1. Try placing the item as a block (covers BlockItems like stone, redstone, etc.)
        UseOnContext useOnContext = new UseOnContext(fakePlayer, InteractionHand.MAIN_HAND, hitResult);
        InteractionResult placeResult = singleItem.useOn(useOnContext);
        BuildcraftLegacy.LOGGER.debug("Stripe pipe useOn result: {} for item {} at {} (hitFace={}, hitLocation={})",
                placeResult, singleItem, targetPos, hitFace, hitLocation);
        if (placeResult.consumesAction()) {
            stack.shrink(1);
            playInteractionFeedback(serverLevel, targetPos);
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
                playInteractionFeedback(serverLevel, targetPos);
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
            playInteractionFeedback(serverLevel, targetPos);
            BuildcraftLegacy.LOGGER.debug("Stripe pipe used item at {}", targetPos);
            return true;
        }

        return false;
    }

    private void playInteractionFeedback(ServerLevel serverLevel, BlockPos targetPos) {
        serverLevel.playSound(null, targetPos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 1.8f);
        serverLevel.sendParticles(ParticleTypes.SMOKE,
                targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5,
                3, 0.1, 0.1, 0.1, 0.01);
    }

    /**
     * After a world interaction: if items remain, bounce them back through the pipe
     * (matching original BC 1.12 sendItem behavior). If empty, full reset.
     */
    private void afterInteraction() {
        if (!itemHandler.getStackInSlot(0).isEmpty() && from != null) {
            // Items remaining — reverse direction, send back through the pipe system
            Direction oldTo = this.to;
            this.setTo(from);
            this.setFrom(oldTo);
            this.lastMovement = 0;
            this.movement = 0;
            // Sync direction FIRST so client knows the new flight direction,
            // then sync movement to reset animation.
            PacketDistributor.sendToAllPlayers(new SyncPipeDirectionPayload(worldPosition, Optional.ofNullable(from), Optional.ofNullable(this.to)));
            PacketDistributor.sendToAllPlayers(new SyncPipeMovementPayload(worldPosition, this.movement, this.lastMovement, this.itemSpeed));
        } else {
            // Slot empty — full reset
            this.lastMovement = 0;
            this.movement = 0;
            this.setFrom(null);
            this.setTo(null);
            PacketDistributor.sendToAllPlayers(new SyncPipeDirectionPayload(worldPosition, Optional.empty(), Optional.empty()));
            PacketDistributor.sendToAllPlayers(new SyncPipeMovementPayload(worldPosition, this.movement, this.lastMovement, this.itemSpeed));
        }
    }

    private static void orientFakePlayer(FakePlayer player, Direction dir) {
        switch (dir) {
            case SOUTH -> { player.setYRot(0f); player.setXRot(0f); }
            case WEST  -> { player.setYRot(90f); player.setXRot(0f); }
            case NORTH -> { player.setYRot(180f); player.setXRot(0f); }
            case EAST  -> { player.setYRot(-90f); player.setXRot(0f); }
            case UP    -> { player.setYRot(0f); player.setXRot(-90f); }
            case DOWN  -> { player.setYRot(0f); player.setXRot(90f); }
        }
    }
}
