package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.content.blockentities.DiamondFluidPipeBE;
import com.thepigcat.buildcraft.content.menus.DiamondFluidPipeMenu;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.registries.BCMenuTypes;
import com.thepigcat.buildcraft.util.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class DiamondFluidPipeBlock extends FluidPipeBlock {
    public DiamondFluidPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            DiamondFluidPipeBE be = BlockUtils.getBE(DiamondFluidPipeBE.class, level, pos);
            if (be != null) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new DiamondFluidPipeMenu(id, inv, be),
                        Component.translatable("menu.buildcraft.diamond_fluid_pipe")
                ), buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(DiamondFluidPipeBlock::new);
    }

    @Override
    protected BlockEntityType<? extends PipeBlockEntity<?>> getBlockEntityType() {
        return BCBlockEntities.DIAMOND_FLUID_PIPE.get();
    }
}
