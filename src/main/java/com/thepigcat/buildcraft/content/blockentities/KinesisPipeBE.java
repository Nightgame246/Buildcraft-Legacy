package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.content.blocks.KinesisPipeBlock;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * Kinesis (power) Pipe BlockEntity.
 * Receives FE from engines or adjacent kinesis pipes and distributes it to
 * connected kinesis pipes or energy consumers.
 *
 * Material properties (max transfer rate, energy loss) are determined by
 * the block's registry name, matching the original BuildCraft behaviour:
 *   - Wooden:      entry-point pipe, actively extracts from engines
 *   - Gold:        highest throughput, zero loss
 *   - Diamond:     high throughput, zero loss
 *   - Quartz:      good throughput, minimal loss
 *   - Stone:       medium throughput, low loss
 *   - Cobblestone: lowest throughput, highest loss
 *   - Sandstone:   same as stone, but won't connect to machines (block-level)
 */
public class KinesisPipeBE extends PipeBlockEntity<IEnergyStorage> {
    protected final EnergyStorage energyStorage;

    // Material-dependent, set in applyMaterialProperties()
    private int maxTransfer = 80;
    private float lossRate = 0.05f;

    // Server-side per-tick energy flow tracking
    private final int[] incomingThisTick = new int[6];
    private final int[] outgoingThisTick = new int[6];
    private final float[] lastSentSectionPower = new float[7];
    private final IEnergyStorage[] directionWrappers = new IEnergyStorage[6];

    public KinesisPipeBE(BlockPos pos, BlockState blockState) {
        this(BCBlockEntities.KINESIS_PIPE.get(), pos, blockState);
    }

    protected KinesisPipeBE(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.energyStorage = new EnergyStorage(
                BCConfig.kinesisPipeEnergyCapacity,
                BCConfig.kinesisPipeEnergyCapacity, // maxReceive — accept quickly
                BCConfig.kinesisPipeEnergyCapacity  // maxExtract — internal use
        );
        for (int d = 0; d < 6; d++) {
            final int idx = d;
            directionWrappers[d] = new IEnergyStorage() {
                @Override public int receiveEnergy(int maxReceive, boolean simulate) {
                    int accepted = energyStorage.receiveEnergy(maxReceive, simulate);
                    if (!simulate && accepted > 0) incomingThisTick[idx] += accepted;
                    return accepted;
                }
                @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
                @Override public int getEnergyStored() { return energyStorage.getEnergyStored(); }
                @Override public int getMaxEnergyStored() { return energyStorage.getMaxEnergyStored(); }
                @Override public boolean canExtract() { return false; }
                @Override public boolean canReceive() { return energyStorage.canReceive(); }
            };
        }
    }

    @Override
    protected BlockCapability<IEnergyStorage, Direction> getCapType() {
        return Capabilities.EnergyStorage.BLOCK;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        applyMaterialProperties();
    }

    private void applyMaterialProperties() {
        String pipeId = BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).getPath();

        if (pipeId.contains("gold")) {
            // Gold kinesis: highest throughput, zero loss
            maxTransfer = 512;
            lossRate = 0f;
        } else if (pipeId.contains("diamond")) {
            // Diamond kinesis: high throughput, zero loss
            maxTransfer = 320;
            lossRate = 0f;
        } else if (pipeId.contains("quartz")) {
            // Quartz kinesis: good throughput, minimal loss
            maxTransfer = 200;
            lossRate = 0.01f;
        } else if (pipeId.contains("cobblestone") || pipeId.contains("cobble")) {
            // Cobblestone kinesis: low throughput, highest loss
            maxTransfer = 80;
            lossRate = 0.10f;
        } else if (pipeId.contains("sandstone")) {
            // Sandstone kinesis: same as stone (no machine connection at block level)
            maxTransfer = 120;
            lossRate = 0.02f;
        } else if (pipeId.contains("stone")) {
            // Stone kinesis: medium throughput, moderate loss
            maxTransfer = 120;
            lossRate = 0.02f;
        } else {
            // Default / Wooden kinesis: entry-point, low throughput, minimal loss
            maxTransfer = 80;
            lossRate = 0.01f;
        }
    }

    private int lastPowerLevel = -1;

    // Client-side display smoothing for the renderer
    private float displayPower = 0f;
    private float lastDisplayPower = 0f;

    /**
     * Smoothed power value (0–1) for the renderer, interpolated with partialTick.
     */
    public float getDisplayPower(float partialTick) {
        return lastDisplayPower + (displayPower - lastDisplayPower) * partialTick;
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        if (level == null) return;

        if (level.isClientSide()) {
            clientTick();
            return;
        }

        distributeEnergy();
        updatePowerLevel();
    }

    /**
     * Client-side: smoothly interpolate displayPower toward the blockstate POWER_LEVEL.
     * Uses sqrt curve like original BC for visual emphasis at low energy.
     */
    private void clientTick() {
        lastDisplayPower = displayPower;
        float target = 0f;
        BlockState state = getBlockState();
        if (state.hasProperty(KinesisPipeBlock.POWER_LEVEL)) {
            target = state.getValue(KinesisPipeBlock.POWER_LEVEL) / 4.0f;
        }
        // Smooth approach — settles in ~7 ticks
        displayPower += (target - displayPower) * 0.15f;
        // Snap to zero when negligible
        if (displayPower < 0.005f && target == 0f) {
            displayPower = 0f;
        }
    }

    private void updatePowerLevel() {
        int max = energyStorage.getMaxEnergyStored();
        int stored = energyStorage.getEnergyStored();
        int newLevel;
        if (max <= 0 || stored <= 0) {
            newLevel = 0;
        } else {
            float ratio = (float) stored / max;
            if (ratio > 0.75f) newLevel = 4;
            else if (ratio > 0.50f) newLevel = 3;
            else if (ratio > 0.25f) newLevel = 2;
            else newLevel = 1;
        }

        if (newLevel != lastPowerLevel) {
            lastPowerLevel = newLevel;
            BlockState state = getBlockState();
            if (state.hasProperty(KinesisPipeBlock.POWER_LEVEL)
                    && state.getValue(KinesisPipeBlock.POWER_LEVEL) != newLevel) {
                // Flag 2 = send to client, no neighbor block update (avoids cascade)
                level.setBlock(worldPosition, state.setValue(KinesisPipeBlock.POWER_LEVEL, newLevel), 2);
            }
        }
    }

    /**
     * Push stored energy to connected neighbours (other kinesis pipes or consumers).
     * Energy is split evenly. Loss is applied per transfer.
     */
    private void distributeEnergy() {
        java.util.Arrays.fill(outgoingThisTick, 0);
        java.util.Arrays.fill(incomingThisTick, 0);
        if (energyStorage.getEnergyStored() <= 0) return;

        // Collect receivers, skip the extracting side to avoid push/pull loops
        List<Direction> receiverDirs = new ArrayList<>();
        List<IEnergyStorage> receivers = new ArrayList<>();
        for (Direction dir : directions) {
            if (dir == extracting) continue;
            BlockCapabilityCache<IEnergyStorage, Direction> cache = capabilityCaches.get(dir);
            if (cache == null) continue;
            IEnergyStorage neighbor = cache.getCapability();
            if (neighbor != null && neighbor.canReceive()) {
                receiverDirs.add(dir);
                receivers.add(neighbor);
            }
        }
        if (receivers.isEmpty()) return;

        int budget = Math.min(energyStorage.getEnergyStored(), maxTransfer);
        int perReceiver = budget / receivers.size();
        if (perReceiver <= 0) return;

        int totalConsumed = 0;
        for (int i = 0; i < receivers.size(); i++) {
            IEnergyStorage receiver = receivers.get(i);
            Direction dir = receiverDirs.get(i);
            int offer = Math.min(perReceiver, energyStorage.getEnergyStored() - totalConsumed);
            if (offer <= 0) break;

            // Apply loss: floor the delivered amount so loss is always deducted
            int loss = (int) (offer * lossRate);
            int toDeliver = offer - loss;
            if (toDeliver <= 0) {
                totalConsumed += offer; // All energy lost in transit
                continue;
            }

            int accepted = receiver.receiveEnergy(toDeliver, false);
            if (accepted > 0) {
                outgoingThisTick[dir.get3DDataValue()] += accepted;
                // We consume what was delivered + proportional loss
                int proportionalLoss = (toDeliver > 0) ? (int) Math.ceil((double) loss * accepted / toDeliver) : 0;
                totalConsumed += accepted + proportionalLoss;
            }
        }

        if (totalConsumed > 0) {
            energyStorage.extractEnergy(totalConsumed, false);
            setChanged();
        }
    }

    // ── Capability getter ────────────────────────────────────────────────

    public IEnergyStorage getEnergyStorage(Direction direction) {
        return direction != null ? directionWrappers[direction.get3DDataValue()] : energyStorage;
    }

    // ── NBT ──────────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("energy")) {
            energyStorage.deserializeNBT(registries, tag.get("energy"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("energy", energyStorage.serializeNBT(registries));
    }
}
