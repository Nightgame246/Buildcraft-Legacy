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
    private final float[] currentSectionPower = new float[7];
    private final boolean[] lastSentFlowsOut = new boolean[6];
    // Client-side targets populated by handleUpdateTag
    private final float[] targetSectionPower = new float[7];
    private final boolean[] targetFlowsOut = new boolean[6];
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

    // Forces a full sync on the first server tick after load/placement
    private boolean needsInitialSync = true;

    // Client-side display smoothing — per section (0–5: arms by Direction ordinal, 6: center)
    private final float[] displaySectionPower = new float[7];
    private final float[] lastDisplaySectionPower = new float[7];
    private final boolean[] displayFlowsOut = new boolean[6];

    /** Smoothed power (0–1) for section s, interpolated with partialTick. Section 6 = center. */
    public float getSectionPower(int section, float partialTick) {
        return lastDisplaySectionPower[section]
               + (displaySectionPower[section] - lastDisplaySectionPower[section]) * partialTick;
    }

    /** True if energy flows outward on arm d (UV scrolls away from center). */
    public boolean getSectionFlowsOut(int dir) {
        return displayFlowsOut[dir];
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        if (level == null) return;

        if (level.isClientSide()) {
            clientTick();
            return;
        }

        if (needsInitialSync) {
            needsInitialSync = false;
            java.util.Arrays.fill(lastSentSectionPower, -1f); // guarantee diff > threshold on first check
        }
        distributeEnergy();
        syncSectionPowerIfNeeded();
        java.util.Arrays.fill(incomingThisTick, 0); // reset AFTER sync so incoming is visible this tick
    }

    private void clientTick() {
        // Seed center from POWER_LEVEL blockstate until the first UpdateTag arrives
        if (targetSectionPower[6] == 0f) {
            BlockState state = getBlockState();
            if (state.hasProperty(KinesisPipeBlock.POWER_LEVEL)) {
                targetSectionPower[6] = state.getValue(KinesisPipeBlock.POWER_LEVEL) / 4.0f;
            }
        }
        for (int s = 0; s < 7; s++) {
            lastDisplaySectionPower[s] = displaySectionPower[s];
            displaySectionPower[s] += (targetSectionPower[s] - displaySectionPower[s]) * 0.15f;
            if (displaySectionPower[s] < 0.005f && targetSectionPower[s] == 0f) {
                displaySectionPower[s] = 0f;
            }
        }
        System.arraycopy(targetFlowsOut, 0, displayFlowsOut, 0, 6);
    }

    // POWER_LEVEL blockstate updates removed — they forced chunk re-renders every tick
    // causing visual flicker. The TESR (getUpdateTag/handleUpdateTag) provides all
    // client-side power data via sendBlockUpdated which only sends a data packet.

    /**
     * Push stored energy to connected neighbours (other kinesis pipes or consumers).
     * Energy is split evenly. Loss is applied per transfer.
     */
    private void distributeEnergy() {
        java.util.Arrays.fill(outgoingThisTick, 0);
        // incomingThisTick is reset in tick() AFTER syncSectionPowerIfNeeded so the
        // current-tick incoming flow is visible to the sync check.
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

    private void syncSectionPowerIfNeeded() {
        java.util.Arrays.fill(currentSectionPower, 0);
        for (int d = 0; d < 6; d++) {
            currentSectionPower[d] = maxTransfer > 0
                ? Math.min(1f, (float) Math.max(incomingThisTick[d], outgoingThisTick[d]) / maxTransfer)
                : 0f;
        }
        int maxArmFlow = 0;
        for (int d = 0; d < 6; d++) {
            maxArmFlow = Math.max(maxArmFlow, Math.max(incomingThisTick[d], outgoingThisTick[d]));
        }
        if (maxArmFlow > 0 && maxTransfer > 0) {
            currentSectionPower[6] = Math.min(1f, (float) maxArmFlow / maxTransfer);
        } else {
            int capacity = energyStorage.getMaxEnergyStored();
            currentSectionPower[6] = capacity > 0 ? Math.min(1f, (float) energyStorage.getEnergyStored() / capacity) : 0f;
        }

        boolean changed = false;
        for (int s = 0; s < 7; s++) {
            if (Math.abs(currentSectionPower[s] - lastSentSectionPower[s]) > 0.01f) { changed = true; break; }
        }
        if (!changed) {
            boolean wasActive = false, nowActive = false;
            for (int s = 0; s < 7; s++) {
                if (lastSentSectionPower[s] > 0.005f) wasActive = true;
                if (currentSectionPower[s] > 0.005f) nowActive = true;
            }
            if (wasActive && !nowActive) changed = true;
        }
        if (!changed) {
            // Also sync if flow direction flipped (magnitude may be constant)
            for (int d = 0; d < 6; d++) {
                if ((outgoingThisTick[d] > 0) != lastSentFlowsOut[d]) { changed = true; break; }
            }
        }
        if (changed) {
            System.arraycopy(currentSectionPower, 0, lastSentSectionPower, 0, 7);
            for (int d = 0; d < 6; d++) {
                lastSentFlowsOut[d] = outgoingThisTick[d] > 0;
            }
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        net.minecraft.nbt.ListTag powerList = new net.minecraft.nbt.ListTag();
        for (float v : lastSentSectionPower) {
            powerList.add(net.minecraft.nbt.FloatTag.valueOf(v));
        }
        tag.put("section_power", powerList);
        byte flowsOutBits = 0;
        for (int d = 0; d < 6; d++) {
            if (lastSentFlowsOut[d]) flowsOutBits |= (byte) (1 << d);
        }
        tag.putByte("flows_out", flowsOutBits);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        if (tag.contains("section_power")) {
            net.minecraft.nbt.ListTag list = tag.getList("section_power", net.minecraft.nbt.Tag.TAG_FLOAT);
            for (int s = 0; s < Math.min(7, list.size()); s++) {
                targetSectionPower[s] = list.getFloat(s);
            }
        }
        if (tag.contains("flows_out")) {
            byte bits = tag.getByte("flows_out");
            for (int d = 0; d < 6; d++) {
                targetFlowsOut[d] = (bits & (1 << d)) != 0;
            }
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
