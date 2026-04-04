package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.content.blockentities.DiamondItemPipeBE;
import com.thepigcat.buildcraft.content.menus.DiamondPipeMenu;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.util.BlockUtils;
import com.thepigcat.buildcraft.util.CapabilityUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
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
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DiamondItemPipeBlock extends PipeBlock {
    public DiamondItemPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public PipeState getConnectionType(LevelAccessor level, BlockPos pipePos, BlockState pipeState, Direction connectionDirection, BlockPos connectPos) {
        BlockState otherState = level.getBlockState(connectPos);
        Block otherBlock = otherState.getBlock();
        BlockEntity be = level.getBlockEntity(connectPos);

        String currentPipeId = BuiltInRegistries.BLOCK.getKey(this).getPath();

        if (otherBlock instanceof PipeBlock) {
            String otherPipeId = BuiltInRegistries.BLOCK.getKey(otherBlock).getPath();

            // Classic Buildcraft: Stone, Cobblestone and Quartz don't connect to each other
            List<String> separatePipes = List.of("stone_pipe", "cobblestone_pipe", "quartz_pipe");
            if (separatePipes.contains(currentPipeId) && separatePipes.contains(otherPipeId)) {
                if (!currentPipeId.equals(otherPipeId)) {
                    return PipeState.NONE;
                }
            }

            return PipeState.CONNECTED;
        }

        // Sandstone doesn't connect to machines/inventories
        if (currentPipeId.equals("sandstone_pipe")) {
            return PipeState.NONE;
        }

        if (be != null && CapabilityUtils.itemHandlerCapability(be, connectionDirection.getOpposite()) != null) {
            return PipeState.CONNECTED;
        }
        return PipeState.NONE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            DiamondItemPipeBE be = BlockUtils.getBE(DiamondItemPipeBE.class, level, pos);
            if (be != null) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new DiamondPipeMenu(id, inv, be),
                        Component.translatable("menu.buildcraft.diamond_pipe")
                ), buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            DiamondItemPipeBE pipeBE = BlockUtils.getBE(DiamondItemPipeBE.class, level, pos);
            Containers.dropContents(level, pos, NonNullList.of(ItemStack.EMPTY, pipeBE.getItemHandler().getStackInSlot(0)));
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(DiamondItemPipeBlock::new);
    }

    @Override
    protected BlockEntityType<? extends PipeBlockEntity<?>> getBlockEntityType() {
        return BCBlockEntities.DIAMOND_ITEM_PIPE.get();
    }
}