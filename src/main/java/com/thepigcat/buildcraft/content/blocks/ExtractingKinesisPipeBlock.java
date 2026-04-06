package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.PipesRegistry;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.api.blocks.ExtractingPipeBlock;
import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.api.pipes.Pipe;
import com.thepigcat.buildcraft.content.blockentities.KinesisPipeBE;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.util.BlockUtils;
import com.thepigcat.buildcraft.util.CapabilityUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Extracting kinesis pipe (wooden kinesis). The extraction side actively
 * pulls energy from engines or other energy sources.
 */
public class ExtractingKinesisPipeBlock extends ExtractingPipeBlock {
    public ExtractingKinesisPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(KinesisPipeBlock.POWER_LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(KinesisPipeBlock.POWER_LEVEL);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return switch (state.getValue(KinesisPipeBlock.POWER_LEVEL)) {
            case 1 -> 2;
            case 2 -> 5;
            case 3 -> 8;
            case 4 -> 12;
            default -> 0;
        };
    }

    @Override
    public PipeState getConnectionType(LevelAccessor level, BlockPos pipePos, BlockState pipeState, Direction connectionDirection, BlockPos connectPos) {
        BlockEntity be = level.getBlockEntity(connectPos);
        BlockState connectState = level.getBlockState(connectPos);

        // Connect to other kinesis pipes (never extract from them)
        if (KinesisPipeBlock.isKinesisPipe(connectState.getBlock()) && !connectState.is(this)) {
            return PipeState.CONNECTED;
        }

        // Never connect to non-kinesis pipes (item pipes, fluid pipes, etc.)
        if (connectState.getBlock() instanceof PipeBlock) {
            return PipeState.NONE;
        }

        // Connect to non-pipe blocks with IEnergyStorage (engines, machines)
        if (be != null && CapabilityUtils.energyStorageCapability(be, connectionDirection.getOpposite()) != null) {
            if (!isExtracting(pipeState)) {
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
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
                if (neighborBE != null && !(neighborBE instanceof KinesisPipeBE)
                        && CapabilityUtils.energyStorageCapability(neighborBE, dir.getOpposite()) != null) {
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
                KinesisPipeBE be = BlockUtils.getBE(KinesisPipeBE.class, level, pos);
                if (be != null) {
                    be.extracting = nextDir;
                    be.setChanged();
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(ExtractingKinesisPipeBlock::new);
    }

    @Override
    protected BlockEntityType<? extends PipeBlockEntity<?>> getBlockEntityType() {
        return BCBlockEntities.KINESIS_PIPE.get();
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
