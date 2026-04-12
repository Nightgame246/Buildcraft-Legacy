package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.PipesRegistry;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.api.blocks.ExtractingPipeBlock;
import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.api.pipes.Pipe;
import com.thepigcat.buildcraft.content.blockentities.FluidPipeBE;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.util.BlockUtils;
import com.thepigcat.buildcraft.util.CapabilityUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import com.thepigcat.buildcraft.registries.BCItems;
import net.minecraft.world.InteractionHand;
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

public class ExtractingFluidPipeBlock extends ExtractingPipeBlock {
    public ExtractingFluidPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public PipeState getConnectionType(LevelAccessor level, BlockPos pipePos, BlockState pipeState,
                                        Direction connectionDirection, BlockPos connectPos) {
        BlockEntity be = level.getBlockEntity(connectPos);
        BlockState connectState = level.getBlockState(connectPos);
        if (be != null && !connectState.is(this)
                && CapabilityUtils.fluidHandlerCapability(be, connectionDirection.getOpposite()) != null) {
            if (!isExtracting(pipeState) && !(connectState.getBlock() instanceof PipeBlock)) {
                return PipeState.EXTRACTING;
            } else {
                return PipeState.CONNECTED;
            }
        }
        return PipeState.NONE;
    }

    private static boolean isExtracting(BlockState state) {
        for (Direction direction : Direction.values()) {
            if (state.getValue(CONNECTION[direction.get3DDataValue()]) == PipeState.EXTRACTING) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() != BCItems.WRENCH.get()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            Direction currentDir = null;
            for (Direction dir : Direction.values()) {
                if (state.getValue(CONNECTION[dir.get3DDataValue()]) == PipeState.EXTRACTING) {
                    currentDir = dir;
                    break;
                }
            }

            Direction nextDir = null;
            Direction[] dirs = Direction.values();
            int start = currentDir == null ? 0 : currentDir.ordinal() + 1;

            for (int i = 0; i < 6; i++) {
                Direction dir = dirs[(start + i) % 6];
                BlockPos neighborPos = pos.relative(dir);
                BlockEntity neighborBE = level.getBlockEntity(neighborPos);
                if (neighborBE != null && !(neighborBE instanceof FluidPipeBE)
                        && CapabilityUtils.fluidHandlerCapability(neighborBE, dir.getOpposite()) != null) {
                    nextDir = dir;
                    break;
                }
            }

            if (nextDir != null) {
                BlockState newState = state;
                for (Direction dir : Direction.values()) {
                    PipeState currentType = state.getValue(CONNECTION[dir.get3DDataValue()]);
                    if (currentType != PipeState.NONE) {
                        newState = newState.setValue(CONNECTION[dir.get3DDataValue()],
                                dir == nextDir ? PipeState.EXTRACTING : PipeState.CONNECTED);
                    }
                }
                level.setBlock(pos, newState, 3);
                FluidPipeBE fluidBE = BlockUtils.getBE(FluidPipeBE.class, level, pos);
                if (fluidBE != null) {
                    fluidBE.extracting = nextDir;
                    fluidBE.setChanged();
                }
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(ExtractingFluidPipeBlock::new);
    }

    @Override
    protected BlockEntityType<? extends PipeBlockEntity<?>> getBlockEntityType() {
        return BCBlockEntities.EXTRACTING_FLUID_PIPE.get();
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
