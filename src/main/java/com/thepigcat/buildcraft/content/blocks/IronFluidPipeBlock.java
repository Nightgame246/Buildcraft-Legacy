package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.PipesRegistry;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.api.pipes.Pipe;
import com.thepigcat.buildcraft.content.blockentities.IronFluidPipeBE;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.registries.BCItems;
import com.thepigcat.buildcraft.util.BlockUtils;
import com.thepigcat.buildcraft.util.CapabilityUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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

public class IronFluidPipeBlock extends FluidPipeBlock {
    public IronFluidPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public PipeState getConnectionType(LevelAccessor level, BlockPos pipePos, BlockState pipeState,
                                        Direction connectionDirection, BlockPos connectPos) {
        BlockState otherState = level.getBlockState(connectPos);
        Block otherBlock = otherState.getBlock();
        BlockEntity be = level.getBlockEntity(connectPos);

        boolean isConnected = false;
        if (isFluidPipe(otherBlock)) {
            isConnected = true;
        } else if (otherBlock instanceof PipeBlock) {
            return PipeState.NONE;
        } else if (be != null && CapabilityUtils.fluidHandlerCapability(be, connectionDirection.getOpposite()) != null) {
            isConnected = true;
        }

        if (isConnected) {
            IronFluidPipeBE ironBE = BlockUtils.getBE(IronFluidPipeBE.class, level, pipePos);
            if (ironBE != null && ironBE.getLockedDirection() != null) {
                return connectionDirection == ironBE.getLockedDirection()
                        ? PipeState.BLOCKED : PipeState.CONNECTED;
            } else {
                // Before BE data arrives on client, preserve existing BLOCKED state
                PipeState existing = pipeState.getValue(CONNECTION[connectionDirection.get3DDataValue()]);
                if (existing == PipeState.BLOCKED) return PipeState.BLOCKED;
            }
            return PipeState.CONNECTED;
        }

        return PipeState.NONE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                               BlockPos pos, Player player, InteractionHand hand,
                                               BlockHitResult hitResult) {
        if (stack.getItem() == BCItems.WRENCH.get()) {
            if (!level.isClientSide()) {
                IronFluidPipeBE be = BlockUtils.getBE(IronFluidPipeBE.class, level, pos);
                if (be != null) {
                    be.rotateLockedDirection();
                    player.displayClientMessage(
                            Component.literal("Iron Fluid Pipe output: " + be.getLockedDirection()), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(IronFluidPipeBlock::new);
    }

    @Override
    protected BlockEntityType<? extends PipeBlockEntity<?>> getBlockEntityType() {
        return BCBlockEntities.IRON_FLUID_PIPE.get();
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
