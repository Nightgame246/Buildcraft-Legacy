package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.content.blockentities.DaizuliItemPipeBE;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.registries.BCItems;
import com.thepigcat.buildcraft.util.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class DaizuliItemPipeBlock extends ItemPipeBlock {

    public DaizuliItemPipeBlock(Properties properties) {
        super(properties);
    }

    /**
     * Wrench interaction:
     * - Click on the CURRENT target face → cycle pipe color
     * - Click on any OTHER face → set that face as new target
     * - Sneak + wrench → cycle pipe color (regardless of face)
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() == BCItems.WRENCH.get()) {
            if (!level.isClientSide()) {
                DaizuliItemPipeBE be = BlockUtils.getBE(DaizuliItemPipeBE.class, level, pos);
                if (be != null) {
                    Direction clickedSide = hitResult.getDirection();

                    if (player.isShiftKeyDown() || clickedSide == be.getTargetDirection()) {
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

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(DaizuliItemPipeBlock::new);
    }

    @Override
    protected BlockEntityType<? extends PipeBlockEntity<?>> getBlockEntityType() {
        return BCBlockEntities.DAIZULI_ITEM_PIPE.get();
    }
}
