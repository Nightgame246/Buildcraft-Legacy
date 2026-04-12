package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.portingdeadmods.portingdeadlibs.api.blockentities.ContainerBlockEntity;
import com.portingdeadmods.portingdeadlibs.api.blocks.ContainerBlock;
import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.content.blockentities.TankBE;
import com.thepigcat.buildcraft.data.BCDataComponents;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.util.BlockUtils;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TankBlock extends ContainerBlock {
    public static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);
    public static final BooleanProperty TOP_JOINED = BooleanProperty.create("top_joined");
    public static final BooleanProperty BOTTOM_JOINED = BooleanProperty.create("bottom_joined");

    public TankBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(TOP_JOINED, false).setValue(BOTTOM_JOINED, false));
    }

    @Override
    public boolean tickingEnabled() {
        return false;
    }

    @Override
    public BlockEntityType<? extends ContainerBlockEntity> getBlockEntityType() {
        return BCBlockEntities.TANK.get();
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(TankBlock::new);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(TOP_JOINED, BOTTOM_JOINED));
    }

    @Override
    protected @NotNull ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        // BlockItems (including other tanks) should fall through to placement logic
        if (stack.getItem() instanceof BlockItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        TankBE be = BlockUtils.getBE(TankBE.class, level, pos);
        IFluidHandler itemFluidHandler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        IFluidHandler tankFluidHandler = be.getFluidHandler();

        if (itemFluidHandler != null && !(stack.getItem() instanceof BucketItem)) {
            FluidStack fluidInItemTank = itemFluidHandler.getFluidInTank(0);
            IFluidHandler fluidHandler0 = tankFluidHandler;
            IFluidHandler fluidHandler1 = itemFluidHandler;

            if (!fluidInItemTank.isEmpty()) {
                fluidInItemTank.getFluid().getPickupSound().ifPresent(player::playSound);
                fluidHandler0 = itemFluidHandler;
                fluidHandler1 = tankFluidHandler;
            } else {
                SoundEvent sound = tankFluidHandler.getFluidInTank(0).getFluidType().getSound(SoundActions.BUCKET_EMPTY);
                if (sound != null) {
                    player.playSound(sound);
                }
            }

            FluidStack drained = fluidHandler0.drain(fluidHandler0.getFluidInTank(0), IFluidHandler.FluidAction.EXECUTE);
            int filled = fluidHandler1.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            fluidHandler0.fill(drained.copyWithAmount(drained.getAmount() - filled), IFluidHandler.FluidAction.EXECUTE);

            return ItemInteractionResult.SUCCESS;
        } else if (itemFluidHandler != null && stack.getItem() instanceof BucketItem) {
            FluidStack fluidInItemTank = itemFluidHandler.getFluidInTank(0);
            if (fluidInItemTank.isEmpty() && tankFluidHandler.drain(1000, IFluidHandler.FluidAction.SIMULATE).getAmount() == 1000) {
                ItemStack filledBucket = ItemUtils.createFilledResult(stack, player, tankFluidHandler.drain(1000, IFluidHandler.FluidAction.EXECUTE).getFluid().getBucket().getDefaultInstance());
                player.setItemInHand(hand, filledBucket);
                tankFluidHandler.getFluidInTank(0).getFluid().getPickupSound().ifPresent(player::playSound);
                return ItemInteractionResult.SUCCESS;
            } else if (!fluidInItemTank.isEmpty() && tankFluidHandler.fill(fluidInItemTank.copyWithAmount(1000), IFluidHandler.FluidAction.SIMULATE) == 1000) {
                tankFluidHandler.fill(fluidInItemTank.copyWithAmount(1000), IFluidHandler.FluidAction.EXECUTE);
                ItemStack emptyBucket = ItemUtils.createFilledResult(stack, player, BucketItem.getEmptySuccessItem(stack, player));
                player.setItemInHand(hand, emptyBucket);
                SoundEvent sound = tankFluidHandler.getFluidInTank(0).getFluidType().getSound(SoundActions.BUCKET_EMPTY);
                if (sound != null) {
                    player.playSound(sound);
                }
                return ItemInteractionResult.SUCCESS;
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        FluidStack itemTankFluid = context.getItemInHand().getOrDefault(BCDataComponents.TANK_CONTENT, SimpleFluidContent.EMPTY).copy();
        boolean topJoined = level.getBlockEntity(clickedPos.above()) instanceof TankBE tankBE && (tankBE.getFluid().is(itemTankFluid.getFluid()) || tankBE.getFluid().isEmpty() || itemTankFluid.isEmpty());
        boolean bottomJoined = level.getBlockEntity(clickedPos.below()) instanceof TankBE tankBE && (tankBE.getFluid().is(itemTankFluid.getFluid()) || tankBE.getFluid().isEmpty() || itemTankFluid.isEmpty());

        if (topJoined && bottomJoined) {
            TankBE aboveTank = BlockUtils.getBE(TankBE.class, level, clickedPos.above());
            TankBE belowTank = BlockUtils.getBE(TankBE.class, level, clickedPos.below());
            if (!aboveTank.getFluid().is(belowTank.getFluid().getFluid()) || !aboveTank.getFluid().is(itemTankFluid.getFluid())) {
                topJoined = false;
            }

        }

        return state != null ? state.setValue(TOP_JOINED, topJoined).setValue(BOTTOM_JOINED, bottomJoined) : null;
    }

    @Override
    protected @NotNull BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        TankBE tankBE = BlockUtils.getBE(TankBE.class, level, pos);
        FluidStack fluidInTank = tankBE.getFluidHandler().getFluidInTank(0);
        boolean value = neighborState.is(this);
        if (direction == Direction.UP) {
            if (value) {
                FluidStack fluidInTank1 = BlockUtils.getBE(TankBE.class, level, pos.above()).getFluidHandler().getFluidInTank(0);
                value = fluidInTank1.is(fluidInTank.getFluid()) || fluidInTank.isEmpty() || fluidInTank1.isEmpty();
            }
            tankBE.setTopJoined(value);
            return state.setValue(TOP_JOINED, value);
        } else if (direction == Direction.DOWN) {
            if (value) {
                FluidStack fluidInTank1 = BlockUtils.getBE(TankBE.class, level, pos.below()).getFluidHandler().getFluidInTank(0);
                value = fluidInTank1.is(fluidInTank.getFluid()) || fluidInTank.isEmpty() || fluidInTank1.isEmpty();
            }
            tankBE.setBottomJoined(value);
            return state.setValue(BOTTOM_JOINED, value);
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        TankBE tankBE = BlockUtils.getBE(TankBE.class, level, pos);
        if (tankBE == null) return;

        FluidStack baseFluidCopy = tankBE.getFluidTank().getFluid().copy();
        int baseFluidAmount = baseFluidCopy.getAmount();

        boolean topJoined = state.getValue(TOP_JOINED);
        boolean bottomJoined = state.getValue(BOTTOM_JOINED);

        // Find bottomPos without mutating state; reformStack will walk again.
        BlockPos bottomPos = pos;
        while (level.getBlockState(bottomPos).getValue(BOTTOM_JOINED)) {
            bottomPos = bottomPos.below();
        }
        TankBE bottomTankBe = BlockUtils.getBE(TankBE.class, level, bottomPos);
        if (bottomTankBe == null) return;

        // Case A: standalone placement — no merge. reformStack with size=1 preserves
        // the placed tank's fluid via the DynamicFluidTank that already holds it.
        if (!topJoined && !bottomJoined) {
            reformStack(level, pos);
            return;
        }

        // getFluidHandler() delegates to the above-stack's master, giving us
        // the entire above-stack's fluid total — exactly what we need to sum.
        TankBE aboveTank = topJoined ? BlockUtils.getBE(TankBE.class, level, pos.above()) : null;
        int aboveFluidAmount = (aboveTank != null)
                ? aboveTank.getFluidHandler().getFluidInTank(0).getAmount()
                : 0;

        // Pick the fluid identity: prefer existing master's fluid; fall back to
        // the placed tank's fluid; last resort the above tank's fluid.
        FluidStack existing = bottomTankBe.getFluidHandler().getFluidInTank(0);
        FluidStack fluidIdentity = !existing.isEmpty() ? existing : baseFluidCopy;
        if (fluidIdentity.isEmpty() && aboveTank != null && aboveFluidAmount > 0) {
            fluidIdentity = aboveTank.getFluidHandler().getFluidInTank(0);
        }

        int totalAmount;
        if (topJoined && bottomJoined) {
            // Bridging: master's current total + placed + above's total
            totalAmount = existing.getAmount() + baseFluidAmount + aboveFluidAmount;
        } else if (topJoined) {
            // Placed is new bottom (no bottomJoined); above stack stacks on top
            totalAmount = aboveFluidAmount + baseFluidAmount;
        } else {
            // bottomJoined only: placed sits on top of existing stack
            totalAmount = existing.getAmount() + baseFluidAmount;
        }

        if (!fluidIdentity.isEmpty()) {
            bottomTankBe.initialFluid = fluidIdentity.copyWithAmount(totalAmount);
        }

        reformStack(level, pos);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean blockGoingAway = !state.is(newState.getBlock());
        boolean hadTop = state.getValue(TOP_JOINED);
        boolean hadBottom = state.getValue(BOTTOM_JOINED);

        if (blockGoingAway) {
            if (hadTop && hadBottom) {
                splitTank(level, pos);
            } else if (hadTop) {
                moveFluidsAbove(level, pos);
            } else if (hadBottom) {
                removeFluidFromBottomTank(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);

        // After super.onRemove, the block is gone and updateShape has adjusted
        // TOP_JOINED/BOTTOM_JOINED on the neighbors. Reform both resulting
        // stack halves so bottomTankPos pointers and master capacity are
        // consistent.
        if (blockGoingAway) {
            if (hadTop) {
                BlockPos above = pos.above();
                if (level.getBlockEntity(above) instanceof TankBE) {
                    reformStack(level, above);
                }
            }
            if (hadBottom) {
                BlockPos below = pos.below();
                if (level.getBlockEntity(below) instanceof TankBE) {
                    reformStack(level, below);
                }
            }
        }
    }

    private static void removeFluidFromBottomTank(Level level, BlockPos pos) {
        TankBE removedTank = BlockUtils.getBE(TankBE.class, level, pos);
        FluidStack fluidStack = removedTank.getFluidHandler().getFluidInTank(0);
        int tank = removedTank.getBlockPos().getY() - removedTank.getBottomTankPos().getY();
        int prevFluidAmount = tank * BCConfig.tankCapacity;
        int fluidAmount = Math.min(fluidStack.getAmount() - prevFluidAmount, BCConfig.tankCapacity);
        removedTank.getFluidHandler().drain(fluidAmount, IFluidHandler.FluidAction.EXECUTE);
    }

    private static void moveFluidsAbove(Level level, BlockPos pos) {
        TankBE removedTank = BlockUtils.getBE(TankBE.class, level, pos);
        TankBE aboveTank = BlockUtils.getBE(TankBE.class, level, pos.above());
        FluidStack fluidInTank = removedTank.getFluidTank().getFluidInTank(0);
        int amount = Math.max(fluidInTank.getAmount() - BCConfig.tankCapacity, 0);
        aboveTank.initialFluid = fluidInTank.copyWithAmount(amount);
    }

    private static void splitTank(Level level, BlockPos pos) {
        TankBE removedTank = BlockUtils.getBE(TankBE.class, level, pos);
        FluidStack fluidStack = removedTank.getFluidHandler().getFluidInTank(0);
        int tank = removedTank.getBlockPos().getY() - removedTank.getBottomTankPos().getY();

        TankBE topTank = BlockUtils.getBE(TankBE.class, level, pos.above());
        int topFluidAmount = Math.max(fluidStack.getAmount() - ((tank + 1) * BCConfig.tankCapacity), 0);
        topTank.initialFluid = fluidStack.copyWithAmount(topFluidAmount);

        int prevFluidAmount = tank * BCConfig.tankCapacity;
        int fluidAmount = Math.min(fluidStack.getAmount() - prevFluidAmount, BCConfig.tankCapacity);
        BlockPos bottomTankPos = removedTank.getBottomTankPos();

        TankBE bottomTank = BlockUtils.getBE(TankBE.class, level, bottomTankPos);
        bottomTank.initialFluid = removedTank.getFluidHandler().getFluidInTank(0).copyWithAmount(fluidStack.getAmount() - topFluidAmount - fluidAmount);
    }

    @Override
    protected @NotNull VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected @NotNull List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof TankBE be && BCConfig.tankRetainFluids) {
            ItemStack stack = new ItemStack(this);
            FluidStack fluidStack = be.getFluidHandler().getFluidInTank(0);
            int tank = be.getBlockPos().getY() - be.getBottomTankPos().getY();
            int prevFluidAmount = tank * BCConfig.tankCapacity;
            int fluidAmount = Math.min(fluidStack.getAmount() - prevFluidAmount, BCConfig.tankCapacity);
            if (fluidAmount >= 0) {
                stack.set(BCDataComponents.TANK_CONTENT, SimpleFluidContent.copyOf(be.getFluidHandler().getFluidInTank(0).copyWithAmount(fluidAmount)));
            }
            return List.of(stack);
        }
        return super.getDrops(state, params);
    }

    /**
     * Reform a multi-block tank stack starting from {@code anchor}. Walks down to find
     * the bottom (where BOTTOM_JOINED is false), up to find the top (where
     * TOP_JOINED is false), then updates {@code bottomTankPos} on every tank in range,
     * calls {@code initTank(size)} on the bottom master, and forces a client sync.
     *
     * <p>Precondition: {@code anchor} must be the position of an existing TankBE.
     * This is the single source of truth for {@code bottomTankPos} mutations
     * and {@code initTank} calls (onPlace wired; onRemove wiring pending).
     */
    private static void reformStack(LevelAccessor level, BlockPos anchor) {
        if (!(level.getBlockEntity(anchor) instanceof TankBE)) return;

        // 1. Walk down to find the bottom
        BlockPos bottomPos = anchor;
        while (level.getBlockState(bottomPos).getValue(BOTTOM_JOINED)) {
            bottomPos = bottomPos.below();
        }

        // 2. Walk up from bottom to find the top
        BlockPos topPos = bottomPos;
        while (level.getBlockState(topPos).getValue(TOP_JOINED)) {
            topPos = topPos.above();
        }

        int size = topPos.getY() - bottomPos.getY() + 1;

        // 3. Point every tank in range at the new bottom, capture the master
        TankBE bottomBe = null;
        for (int y = bottomPos.getY(); y <= topPos.getY(); y++) {
            BlockPos p = new BlockPos(bottomPos.getX(), y, bottomPos.getZ());
            TankBE be = BlockUtils.getBE(TankBE.class, level, p);
            if (be != null) {
                be.setBottomTankPos(bottomPos);
                if (y == bottomPos.getY()) bottomBe = be;
            }
        }

        // 4. Initialize the master's capacity (applies pending initialFluid)
        if (bottomBe != null) {
            bottomBe.initTank(size);
        }

        // 5. Force explicit client sync on the bottom so non-master BEs see
        //    a consistent master before they query delegated capabilities
        if (level instanceof Level l) {
            BlockState bottomState = l.getBlockState(bottomPos);
            l.sendBlockUpdated(bottomPos, bottomState, bottomState, 3);
        }
    }

}
