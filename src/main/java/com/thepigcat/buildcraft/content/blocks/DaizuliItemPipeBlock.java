package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.content.blockentities.DaizuliItemPipeBE;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.registries.BCItems;
import com.thepigcat.buildcraft.util.BlockUtils;
import com.thepigcat.buildcraft.util.CapabilityUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class DaizuliItemPipeBlock extends ItemPipeBlock {
    private static final double CENTER_THRESHOLD = 0.1875D;

    public DaizuliItemPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public PipeState getConnectionType(LevelAccessor level, BlockPos pipePos, BlockState pipeState, Direction connectionDirection, BlockPos connectPos) {
        // First check if we should connect at all (using parent logic)
        PipeState baseState = super.getConnectionType(level, pipePos, pipeState, connectionDirection, connectPos);
        if (baseState == PipeState.NONE) {
            return PipeState.NONE;
        }

        // Daizuli: target direction shows as BLOCKED (like Iron pipe)
        DaizuliItemPipeBE be = BlockUtils.getBE(DaizuliItemPipeBE.class, level, pipePos);
        if (be != null && be.getTargetDirection() != null) {
            if (connectionDirection == be.getTargetDirection()) {
                return PipeState.BLOCKED;
            }
        } else {
            // Preserve existing BLOCKED state on client before BE data arrives
            PipeState existing = pipeState.getValue(CONNECTION[connectionDirection.get3DDataValue()]);
            if (existing == PipeState.BLOCKED) {
                return PipeState.BLOCKED;
            }
        }
        return PipeState.CONNECTED;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() == BCItems.WRENCH.get()) {
            if (!level.isClientSide()) {
                DaizuliItemPipeBE be = BlockUtils.getBE(DaizuliItemPipeBE.class, level, pos);
                if (be != null) {
                    Direction clickedSide = hitResult.getDirection();
                    boolean centerHit = isCenterFaceHit(hitResult, pos, clickedSide);

                    if (centerHit || clickedSide == be.getTargetDirection()) {
                        be.cyclePipeColor();
                        player.displayClientMessage(Component.literal("Daizuli Pipe color: " + be.getPipeColor().getName()), true);
                    } else {
                        be.setTargetDirection(clickedSide);
                        player.displayClientMessage(Component.literal("Daizuli Pipe target: " + clickedSide.getSerializedName()), true);
                    }
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    private static boolean isCenterFaceHit(BlockHitResult hitResult, BlockPos pos, Direction face) {
        Vec3 local = hitResult.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        return switch (face) {
            case DOWN, UP -> Math.abs(local.x - 0.5D) <= CENTER_THRESHOLD && Math.abs(local.z - 0.5D) <= CENTER_THRESHOLD;
            case NORTH, SOUTH -> Math.abs(local.x - 0.5D) <= CENTER_THRESHOLD && Math.abs(local.y - 0.5D) <= CENTER_THRESHOLD;
            case WEST, EAST -> Math.abs(local.y - 0.5D) <= CENTER_THRESHOLD && Math.abs(local.z - 0.5D) <= CENTER_THRESHOLD;
        };
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(DaizuliItemPipeBlock::new);
    }

    @Override
    protected BlockEntityType<? extends PipeBlockEntity<?>> getBlockEntityType() {
        return BCBlockEntities.DAIZULI_ITEM_PIPE.get();
    }
}
