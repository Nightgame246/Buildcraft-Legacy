package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.PipesRegistry;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.api.pipes.Pipe;
import com.thepigcat.buildcraft.content.blockentities.IronItemPipeBE;
import com.thepigcat.buildcraft.content.blockentities.ItemPipeBE;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.registries.BCItems;
import com.thepigcat.buildcraft.util.BlockUtils;
import com.thepigcat.buildcraft.util.CapabilityUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class IronItemPipeBlock extends PipeBlock {
    public IronItemPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public PipeState getConnectionType(LevelAccessor level, BlockPos pipePos, BlockState pipeState, Direction connectionDirection, BlockPos connectPos) {
        BlockState otherState = level.getBlockState(connectPos);
        Block otherBlock = otherState.getBlock();
        BlockEntity be = level.getBlockEntity(connectPos);

        String currentPipeId = BuiltInRegistries.BLOCK.getKey(this).getPath();
        boolean isConnected = false;

        if (otherBlock instanceof PipeBlock) {
            String otherPipeId = BuiltInRegistries.BLOCK.getKey(otherBlock).getPath();

            // Classic Buildcraft: Stone, Cobblestone and Quartz don't connect to each other
            List<String> separatePipes = List.of("stone_pipe", "cobblestone_pipe", "quartz_pipe");
            if (separatePipes.contains(currentPipeId) && separatePipes.contains(otherPipeId)) {
                if (!currentPipeId.equals(otherPipeId)) {
                    return PipeState.NONE;
                }
            }
            isConnected = true;
        } else if (currentPipeId.equals("sandstone_pipe")) {
            return PipeState.NONE;
        } else if (be != null && CapabilityUtils.itemHandlerCapability(be, connectionDirection.getOpposite()) != null) {
            isConnected = true;
        }

        if (isConnected) {
            // Iron Pipe logic: check if this direction is blocked
            IronItemPipeBE ironBE = BlockUtils.getBE(IronItemPipeBE.class, level, pipePos);
            if (ironBE != null && ironBE.getLockedDirection() != null) {
                if (connectionDirection == ironBE.getLockedDirection()) {
                    return PipeState.BLOCKED;
                }
            } else {
                // BE not available or lockedDirection not synced yet (e.g. client-side before
                // BE data packet arrives). Preserve any existing BLOCKED state from the blockstate
                // to prevent updateShape cascades from overwriting server-sent BLOCKED → CONNECTED.
                PipeState existing = pipeState.getValue(CONNECTION[connectionDirection.get3DDataValue()]);
                if (existing == PipeState.BLOCKED) {
                    return PipeState.BLOCKED;
                }
            }
            return PipeState.CONNECTED;
        }

        return PipeState.NONE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() == BCItems.WRENCH.get()) {
            if (!level.isClientSide()) {
                IronItemPipeBE be = BlockUtils.getBE(IronItemPipeBE.class, level, pos);
                if (be != null) {
                    be.rotateLockedDirection();
                    player.displayClientMessage(Component.literal("Iron Pipe output: " + be.getLockedDirection()), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            ItemPipeBE pipeBE = BlockUtils.getBE(ItemPipeBE.class, level, pos);
            if (pipeBE != null) {
                Containers.dropContents(level, pos, NonNullList.of(ItemStack.EMPTY, pipeBE.getItemHandler().getStackInSlot(0)));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(IronItemPipeBlock::new);
    }

    @Override
    protected BlockEntityType<? extends PipeBlockEntity<?>> getBlockEntityType() {
        return BCBlockEntities.IRON_ITEM_PIPE.get();
    }

    @Override
    protected @NotNull List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        Pipe pipe = PipesRegistry.PIPES.get(this.builtInRegistryHolder().key().location().getPath());
        Item dropItem = BuiltInRegistries.ITEM.get(pipe.dropItem());
        if (!pipe.customLoottable()) {
            return List.of(dropItem.getDefaultInstance());
        }
        return super.getDrops(state, params);
    }
}
