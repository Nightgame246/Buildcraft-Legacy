package com.thepigcat.buildcraft.content.blockentities;

import com.portingdeadmods.portingdeadlibs.api.blockentities.ContainerBlockEntity;
import com.portingdeadmods.portingdeadlibs.utils.capabilities.HandlerUtils;
import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.content.blocks.LaserBlock;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LaserBE extends ContainerBlockEntity {
    @Nullable public BlockPos targetPos = null;
    private final List<BlockPos> candidates = new ArrayList<>();
    private int scanCooldown = 0;

    public LaserBE(BlockPos pos, BlockState state) {
        super(BCBlockEntities.LASER.get(), pos, state);
        addEnergyStorage(HandlerUtils::newEnergystorage, builder -> builder
                .capacity(BCConfig.laserBatteryCapacity)
                .maxTransfer(BCConfig.laserMaxReceive));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LaserBE be) {
        if (--be.scanCooldown <= 0) {
            be.scanCooldown = 10;
            be.scanForTargets(state);
        }

        // Re-select if current target no longer needs power
        if (be.targetPos != null) {
            if (!(level.getBlockEntity(be.targetPos) instanceof AssemblyTableBE tbl) || tbl.getRequiredLaserPower() <= 0) {
                be.targetPos = null;
            }
        }

        BlockPos prevTarget = be.targetPos;  // capture BEFORE pickTarget

        if (be.targetPos == null) {
            be.pickTarget();
        }

        if (be.targetPos != null && level.getBlockEntity(be.targetPos) instanceof AssemblyTableBE target) {
            IEnergyStorage battery = be.getEnergyStorage();
            if (battery != null) {
                int toSend = Math.min(BCConfig.laserMaxOutput, battery.getEnergyStored());
                toSend = Math.min(toSend, target.getRequiredLaserPower());
                if (toSend > 0) {
                    battery.extractEnergy(toSend, false);
                    target.receiveLaserPower(toSend);
                }
            }
        }

        // Sync targetPos to clients when it changes
        if (!java.util.Objects.equals(prevTarget, be.targetPos)) {
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private void scanForTargets(BlockState state) {
        candidates.clear();
        if (level == null) return;
        Direction facing = state.getValue(LaserBlock.FACING);
        BlockPos origin = getBlockPos();
        int range = 8;

        for (int dist = 1; dist <= range; dist++) {
            int spread = Math.min(dist / 2, 1);
            for (int a = -spread; a <= spread; a++) {
                for (int b = -spread; b <= spread; b++) {
                    BlockPos check = switch (facing) {
                        case NORTH -> origin.offset(a, b, -dist);
                        case SOUTH -> origin.offset(a, b, dist);
                        case EAST  -> origin.offset(dist, b, a);
                        case WEST  -> origin.offset(-dist, b, a);
                        case UP    -> origin.offset(a, dist, b);
                        case DOWN  -> origin.offset(a, -dist, b);
                    };
                    if (level.getBlockEntity(check) instanceof AssemblyTableBE) {
                        candidates.add(check);
                    }
                }
            }
        }
    }

    private void pickTarget() {
        if (level == null || candidates.isEmpty()) return;
        for (BlockPos candidate : candidates) {
            if (level.getBlockEntity(candidate) instanceof AssemblyTableBE tbl && tbl.getRequiredLaserPower() > 0) {
                targetPos = candidate;
                return;
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("has_target", targetPos != null);
        if (targetPos != null) {
            tag.putInt("tx", targetPos.getX());
            tag.putInt("ty", targetPos.getY());
            tag.putInt("tz", targetPos.getZ());
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        if (tag.getBoolean("has_target")) {
            targetPos = new BlockPos(tag.getInt("tx"), tag.getInt("ty"), tag.getInt("tz"));
        } else {
            targetPos = null;
        }
    }
}
