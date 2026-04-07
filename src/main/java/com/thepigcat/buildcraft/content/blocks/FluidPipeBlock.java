package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.PipesRegistry;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.api.pipes.Pipe;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.util.CapabilityUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FluidPipeBlock extends PipeBlock {
    public FluidPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public PipeState getConnectionType(LevelAccessor level, BlockPos pipePos, BlockState pipeState,
                                        Direction connectionDirection, BlockPos connectPos) {
        BlockState otherState = level.getBlockState(connectPos);
        Block otherBlock = otherState.getBlock();
        BlockEntity be = level.getBlockEntity(connectPos);

        String currentPipeId = BuiltInRegistries.BLOCK.getKey(this).getPath();

        // Connect to other fluid pipes
        if (isFluidPipe(otherBlock)) {
            String otherPipeId = BuiltInRegistries.BLOCK.getKey(otherBlock).getPath();

            // Stone, Cobblestone, Quartz don't interconnect (same rules as item pipes)
            List<String> separatePipes = List.of(
                    "stone_fluid_pipe", "cobblestone_fluid_pipe", "quartz_fluid_pipe"
            );
            if (separatePipes.contains(currentPipeId) && separatePipes.contains(otherPipeId)) {
                if (!currentPipeId.equals(otherPipeId)) {
                    return PipeState.NONE;
                }
            }

            return PipeState.CONNECTED;
        }

        // Never connect to non-fluid pipes (item pipes, kinesis pipes)
        if (otherBlock instanceof PipeBlock) {
            return PipeState.NONE;
        }

        // Sandstone fluid pipes don't connect to machines
        if (currentPipeId.contains("sandstone")) {
            return PipeState.NONE;
        }

        // Connect to blocks exposing IFluidHandler (tanks, engines, etc.)
        if (be != null && CapabilityUtils.fluidHandlerCapability(be, connectionDirection.getOpposite()) != null) {
            return PipeState.CONNECTED;
        }

        return PipeState.NONE;
    }

    static boolean isFluidPipe(Block block) {
        return block instanceof FluidPipeBlock || block instanceof ExtractingFluidPipeBlock;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(FluidPipeBlock::new);
    }

    @Override
    protected BlockEntityType<? extends PipeBlockEntity<?>> getBlockEntityType() {
        return BCBlockEntities.FLUID_PIPE.get();
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
