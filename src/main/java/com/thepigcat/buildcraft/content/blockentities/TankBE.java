package com.thepigcat.buildcraft.content.blockentities;

import com.google.common.collect.ImmutableMap;
import com.portingdeadmods.portingdeadlibs.api.blockentities.ContainerBlockEntity;
import com.portingdeadmods.portingdeadlibs.api.capabilities.DynamicFluidTank;
import com.portingdeadmods.portingdeadlibs.api.utils.IOAction;
import com.portingdeadmods.portingdeadlibs.utils.capabilities.HandlerUtils;
import com.portingdeadmods.portingdeadlibs.utils.capabilities.SidedCapUtils;
import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.data.BCDataComponents;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.util.BlockUtils;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class TankBE extends ContainerBlockEntity {
    private BlockPos bottomTankPos;
    public FluidStack initialFluid;

    public TankBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.TANK.get(), pos, blockState);
        addFluidHandler(HandlerUtils::newDynamicFluidTank, builder -> builder
                .onChange($ -> this.updateData())
                .slotLimit($ -> BCConfig.tankCapacity));
    }

    public FluidStack getFluid() {
        return getFluidHandler().getFluidInTank(0);
    }

    public DynamicFluidTank getFluidTank() {
        return ((DynamicFluidTank) super.getFluidHandler());
    }

    @Override
    public IFluidHandler getFluidHandler() {
        if (this.bottomTankPos != null) {
            if (this.bottomTankPos.equals(worldPosition)) {
                return (this.getFluidTank());
            } else {
                TankBE tankBE = BlockUtils.getBE(TankBE.class, level, this.bottomTankPos);
                if (tankBE != null) {
                    return (tankBE.getFluidHandler());
                } else {
                    return this.getFluidTank();
                }
            }
        } else {
            return this.getFluidTank();
        }
    }

    public void setBottomTankPos(BlockPos bottomTankPos) {
        this.bottomTankPos = bottomTankPos;
        this.updateData();
    }

    public void initTank(int tanks) {
        this.getFluidTank().setCapacity(tanks * BCConfig.tankCapacity);
        if (this.initialFluid != null) {
            this.getFluidTank().setFluid(this.initialFluid);
            this.initialFluid = null;
        }
        this.updateData();
    }

    public BlockPos getBottomTankPos() {
        return bottomTankPos;
    }

    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadData(tag, provider);
        if (tag.contains("bottomTankPos")) {
            this.bottomTankPos = BlockPos.of(tag.getLong("bottomTankPos"));
        }
    }

    @Override
    protected void saveData(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveData(tag, provider);
        if (this.bottomTankPos != null) {
            tag.putLong("bottomTankPos", this.bottomTankPos.asLong());
        }
    }

    /**
     * PDL's {@code ContainerBlock.onRemove} unconditionally calls
     * {@code dropItems(getItemHandler())}; TankBE registers no item handler so
     * the argument is null and PDL then NPEs on {@code handler.getSlots()}.
     * The exception propagates out of {@code LevelChunk.setBlockState},
     * {@code markAndNotifyBlock} never runs, and the client's predictive break
     * gets reverted — hence the "block comes back" flicker on tank break.
     */
    @Override
    public void dropItems(IItemHandler handler) {
        if (handler != null) super.dropItems(handler);
    }

    @Override
    public void saveToItem(ItemStack stack, HolderLookup.Provider registries) {
        super.saveToItem(stack, registries);

        stack.set(BCDataComponents.TANK_CONTENT.get(), SimpleFluidContent.copyOf(this.getFluidTank().getFluid()));
    }

}
