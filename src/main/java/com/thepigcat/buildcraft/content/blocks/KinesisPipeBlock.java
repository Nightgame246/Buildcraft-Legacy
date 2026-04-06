package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.PipesRegistry;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.api.pipes.Pipe;
import com.thepigcat.buildcraft.content.blockentities.KinesisPipeBE;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.util.CapabilityUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Block for kinesis (power) pipes. Connects to other kinesis pipes
 * and to blocks that expose an IEnergyStorage capability.
 * Does NOT connect to item pipes.
 */
public class KinesisPipeBlock extends PipeBlock {
    public static final IntegerProperty POWER_LEVEL = IntegerProperty.create("power_level", 0, 4);

    public KinesisPipeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWER_LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWER_LEVEL);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return switch (state.getValue(POWER_LEVEL)) {
            case 1 -> 2;
            case 2 -> 5;
            case 3 -> 8;
            case 4 -> 12;
            default -> 0;
        };
    }

    @Override
    public PipeState getConnectionType(LevelAccessor level, BlockPos pipePos, BlockState pipeState, Direction connectionDirection, BlockPos connectPos) {
        BlockState otherState = level.getBlockState(connectPos);
        Block otherBlock = otherState.getBlock();
        BlockEntity be = level.getBlockEntity(connectPos);

        String currentPipeId = BuiltInRegistries.BLOCK.getKey(this).getPath();

        // Connect to other kinesis pipes (both default and extracting variants)
        if (isKinesisPipe(otherBlock)) {
            String otherPipeId = BuiltInRegistries.BLOCK.getKey(otherBlock).getPath();

            // Same exclusion rules as item pipes: stone, cobble, quartz don't interconnect
            List<String> separatePipes = List.of(
                    "stone_kinesis_pipe", "cobblestone_kinesis_pipe", "quartz_kinesis_pipe"
            );
            if (separatePipes.contains(currentPipeId) && separatePipes.contains(otherPipeId)) {
                if (!currentPipeId.equals(otherPipeId)) {
                    return PipeState.NONE;
                }
            }

            return PipeState.CONNECTED;
        }

        // Never connect to non-kinesis pipes (item pipes, fluid pipes, etc.)
        if (otherBlock instanceof PipeBlock) {
            return PipeState.NONE;
        }

        // Sandstone kinesis doesn't connect to machines
        if (currentPipeId.contains("sandstone")) {
            return PipeState.NONE;
        }

        // Connect to non-pipe blocks that expose IEnergyStorage (engines, machines)
        if (be != null && CapabilityUtils.energyStorageCapability(be, connectionDirection.getOpposite()) != null) {
            return PipeState.CONNECTED;
        }

        return PipeState.NONE;
    }

    /**
     * Check if a block is any kind of kinesis pipe (default or extracting).
     */
    static boolean isKinesisPipe(Block block) {
        return block instanceof KinesisPipeBlock || block instanceof ExtractingKinesisPipeBlock;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(KinesisPipeBlock::new);
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
