package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.networking.SyncFluidPipePayload;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class FluidPipeBE extends PipeBlockEntity<IFluidHandler> {

    private static final int DIRECTION_COOLDOWN = 60;
    private static final int COOLDOWN_INPUT = -DIRECTION_COOLDOWN;
    private static final int COOLDOWN_OUTPUT = DIRECTION_COOLDOWN;

    // Section indices: 0-5 = Direction.ordinal(), 6 = CENTER
    private static final int CENTER = 6;
    private static final double FLOW_MULTIPLIER = 0.016;

    protected int transferPerTick = 10;
    protected int delay = 10;
    public int capacity = 100;

    private FluidStack currentFluid = FluidStack.EMPTY;
    private final Section[] sections = new Section[7];

    // Sync tracking
    private int syncCooldown = 0;
    private boolean needsSync = false;

    // Client-side interpolation
    private int[] clientAmountThis = new int[7];
    private int[] clientAmountLast = new int[7];
    private int[] clientTarget = new int[7];
    private int[] clientDirection = new int[7];
    private FluidStack clientFluid = FluidStack.EMPTY;
    private final Vec3[] offsetLast = new Vec3[7];
    private final Vec3[] offsetThis = new Vec3[7];

    public FluidPipeBE(BlockPos pos, BlockState blockState) {
        this(BCBlockEntities.FLUID_PIPE.get(), pos, blockState);
    }

    protected FluidPipeBE(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        for (int i = 0; i < 7; i++) {
            sections[i] = new Section(i);
            offsetLast[i] = Vec3.ZERO;
            offsetThis[i] = Vec3.ZERO;
        }
    }

    @Override
    protected BlockCapability<IFluidHandler, Direction> getCapType() {
        return Capabilities.FluidHandler.BLOCK;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        applyMaterialProperties();
    }

    private void applyMaterialProperties() {
        String pipeId = BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()).getPath();

        if (pipeId.contains("gold")) {
            transferPerTick = 80;
            delay = 2;
        } else if (pipeId.contains("stone") && !pipeId.contains("cobblestone") && !pipeId.contains("sandstone")) {
            transferPerTick = 20;
            delay = 10;
        } else if (pipeId.contains("cobblestone")) {
            transferPerTick = 10;
            delay = 10;
        } else {
            // Wooden and other defaults
            transferPerTick = 10;
            delay = 10;
        }

        capacity = Math.max(1000, transferPerTick * 10);

        // Resize incoming arrays for all sections
        for (Section section : sections) {
            section.resizeIncoming(delay);
        }
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        if (level == null) return;

        if (level.isClientSide()) {
            clientTick();
            return;
        }

        if (currentFluid.isEmpty()) return;

        int totalFluid = 0;
        boolean canOutput = false;

        for (int i = 0; i < 7; i++) {
            Section section = sections[i];
            section.currentTime = (section.currentTime + 1) % delay;
            section.advanceForMovement();
            totalFluid += section.amount;
            if (section.getCurrentDirection().canOutput()) {
                canOutput = true;
            }
        }

        if (totalFluid == 0) {
            currentFluid = FluidStack.EMPTY;
        } else {
            if (canOutput) {
                moveFromPipe();
            }
            moveFromCenter();
            moveToCenter();
        }

        // Tick direction cooldowns
        for (Section section : sections) {
            if (section.ticksInDirection > 0) {
                section.ticksInDirection--;
            } else if (section.ticksInDirection < 0) {
                section.ticksInDirection++;
            }
        }

        // Sync to clients
        checkAndSync();
    }

    private void clientTick() {
        for (int i = 0; i < 7; i++) {
            clientAmountLast[i] = clientAmountThis[i];
            if (clientTarget[i] != clientAmountThis[i]) {
                int delta = clientTarget[i] - clientAmountThis[i];
                int step = Math.max(1, Math.abs(delta) / 4);
                if (delta > 0) {
                    clientAmountThis[i] += step;
                } else {
                    clientAmountThis[i] -= step;
                }
            }
        }
    }

    // ── Fluid Movement (3 phases, like original BC) ──────────────────────

    private void moveFromPipe() {
        for (Direction face : Direction.values()) {
            Section section = sections[face.ordinal()];
            if (!section.getCurrentDirection().canOutput()) continue;

            int maxDrain = section.drainInternal(transferPerTick, false);
            if (maxDrain <= 0) continue;

            BlockCapabilityCache<IFluidHandler, Direction> cache = capabilityCaches.get(face);
            if (cache == null) continue;
            IFluidHandler handler = cache.getCapability();
            if (handler == null) continue;

            FluidStack toPush = currentFluid.copyWithAmount(maxDrain);
            int filled = handler.fill(toPush, IFluidHandler.FluidAction.EXECUTE);
            if (filled > 0) {
                section.drainInternal(filled, true);
                section.ticksInDirection = COOLDOWN_OUTPUT;
                setChanged();
            }
        }
    }

    private void moveFromCenter() {
        Section center = sections[CENTER];
        int totalAvailable = center.getMaxDrained();
        if (totalAvailable < 1) return;

        List<Direction> outputDirs = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            Section section = sections[dir.ordinal()];
            if (!section.getCurrentDirection().canOutput()) continue;
            if (section.getMaxFilled() > 0 && capabilityCaches.containsKey(dir)) {
                BlockCapabilityCache<IFluidHandler, Direction> cache = capabilityCaches.get(dir);
                if (cache != null && cache.getCapability() != null) {
                    outputDirs.add(dir);
                }
            }
        }

        if (outputDirs.isEmpty()) return;
        Collections.shuffle(outputDirs);

        float min = Math.min((float) transferPerTick * outputDirs.size(), totalAvailable)
                / transferPerTick / outputDirs.size();

        for (Direction dir : outputDirs) {
            Section section = sections[dir.ordinal()];
            int available = section.fill(transferPerTick, false);
            int amountToPush = (int) (available * min);
            if (amountToPush < 1) amountToPush = 1;

            amountToPush = center.drainInternal(amountToPush, false);
            if (amountToPush > 0) {
                int filled = section.fill(amountToPush, true);
                if (filled > 0) {
                    center.drainInternal(filled, true);
                    section.ticksInDirection = COOLDOWN_OUTPUT;
                }
            }
        }
    }

    private void moveToCenter() {
        Section center = sections[CENTER];
        int spaceAvailable = capacity - center.amount;
        if (spaceAvailable <= 0 || center.getMaxFilled() <= 0) return;

        List<Integer> faceIndices = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));
        Collections.shuffle(faceIndices);

        int transferInCount = 0;
        int[] inputPerTick = new int[6];
        for (int idx : faceIndices) {
            Section section = sections[idx];
            inputPerTick[idx] = 0;
            if (section.getCurrentDirection().canInput()) {
                inputPerTick[idx] = section.drainInternal(transferPerTick, false);
                if (inputPerTick[idx] > 0) {
                    transferInCount++;
                }
            }
        }

        if (transferInCount == 0) return;

        int left = Math.min(transferPerTick, spaceAvailable);
        float min = Math.min((float) transferPerTick * transferInCount, spaceAvailable)
                / transferPerTick / transferInCount;

        for (int idx : faceIndices) {
            Section section = sections[idx];
            if (inputPerTick[idx] > 0) {
                int amountToDrain = (int) (inputPerTick[idx] * min);
                if (amountToDrain < 1) amountToDrain = 1;
                if (amountToDrain > left) amountToDrain = left;

                int amountToPush = section.drainInternal(amountToDrain, false);
                if (amountToPush > 0) {
                    int actuallyDrained = section.drainInternal(amountToPush, true);
                    if (actuallyDrained > 0) {
                        center.fill(actuallyDrained, true);
                        section.ticksInDirection = COOLDOWN_INPUT;
                        left -= actuallyDrained;
                    }
                }
            }
        }
    }

    // ── Capability ───────────────────────────────────────────────────────

    public IFluidHandler getFluidHandler(Direction direction) {
        if (direction == null) return sections[CENTER];
        return sections[direction.ordinal()];
    }

    /**
     * Fill a section directly for extraction purposes.
     * Unlike normal fill, this is only bounded by section capacity, not by per-tick transport limits.
     */
    protected int fillSectionForExtraction(Direction dir, FluidStack fluid, int maxAmount) {
        if (fluid.isEmpty()) return 0;
        Section section = sections[dir.ordinal()];

        if (currentFluid.isEmpty()) {
            currentFluid = fluid.copyWithAmount(1);
            needsSync = true;
            for (Section s : sections) {
                s.resizeIncoming(delay);
            }
        } else if (!FluidStack.isSameFluidSameComponents(currentFluid, fluid)) {
            return 0;
        }

        int available = capacity - section.amount;
        int amountToFill = Math.min(available, maxAmount);
        if (amountToFill <= 0) return 0;

        section.incoming[section.currentTime] += amountToFill;
        section.incomingTotalCache += amountToFill;
        section.amount += amountToFill;
        section.ticksInDirection = COOLDOWN_INPUT;
        setChanged();

        return amountToFill;
    }

    // ── Client Sync ──────────────────────────────────────────────────────

    private void checkAndSync() {
        if (syncCooldown > 0) {
            syncCooldown--;
            return;
        }

        boolean changed = false;
        for (int i = 0; i < 7; i++) {
            if (sections[i].amount != sections[i].lastSentAmount) {
                changed = true;
                break;
            }
        }

        if (changed || needsSync) {
            sendFluidSync();
            syncCooldown = 4;
            needsSync = false;
        }
    }

    private void sendFluidSync() {
        short[] amounts = new short[7];
        byte[] dirs = new byte[7];
        for (int i = 0; i < 7; i++) {
            amounts[i] = (short) sections[i].amount;
            dirs[i] = (byte) sections[i].ticksInDirection;
            sections[i].lastSentAmount = sections[i].amount;
        }
        Optional<FluidStack> fluid = currentFluid.isEmpty()
                ? Optional.empty()
                : Optional.of(currentFluid.copy());
        PacketDistributor.sendToAllPlayers(new SyncFluidPipePayload(worldPosition, fluid, amounts, dirs));
    }

    public void handleFluidSync(SyncFluidPipePayload payload) {
        clientFluid = payload.fluid().orElse(FluidStack.EMPTY);
        for (int i = 0; i < 7; i++) {
            clientTarget[i] = payload.amounts()[i];
            clientDirection[i] = payload.directions()[i];
        }
    }

    // ── Render Accessors ─────────────────────────────────────────────────

    public FluidStack getFluidForRender() {
        return clientFluid;
    }

    public double getAmountForRender(int sectionIndex, float partialTick) {
        return clientAmountLast[sectionIndex]
                + (clientAmountThis[sectionIndex] - clientAmountLast[sectionIndex]) * partialTick;
    }

    public Vec3 getOffsetForRender(int sectionIndex, float partialTick) {
        Vec3 last = offsetLast[sectionIndex];
        Vec3 now = offsetThis[sectionIndex];
        return last.scale(1f - partialTick).add(now.scale(partialTick));
    }

    public int getDirectionForRender(int sectionIndex) {
        return clientDirection[sectionIndex];
    }

    public int getCapacity() {
        return capacity;
    }

    // ── NBT ──────────────────────────────────────────────────────────────

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("fluid")) {
            currentFluid = FluidStack.parseOptional(registries, tag.getCompound("fluid"));
        }
        for (int i = 0; i < 7; i++) {
            if (tag.contains("section" + i)) {
                sections[i].readFromNbt(tag.getCompound("section" + i));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!currentFluid.isEmpty()) {
            tag.put("fluid", currentFluid.save(registries));
        }
        for (int i = 0; i < 7; i++) {
            CompoundTag sectionTag = new CompoundTag();
            sections[i].writeToNbt(sectionTag);
            tag.put("section" + i, sectionTag);
        }
    }

    // ── Direction Enum ──────────────────────────────────────────────────

    enum Dir {
        IN, NONE, OUT;

        public static Dir get(int ticksInDirection) {
            if (ticksInDirection == 0) return NONE;
            return ticksInDirection < 0 ? IN : OUT;
        }

        /** Returns true if this direction allows fluid to flow inward (IN or NONE). */
        public boolean canInput() {
            return this != OUT;
        }

        /** Returns true if this direction allows fluid to flow outward (OUT or NONE). */
        public boolean canOutput() {
            return this != IN;
        }
    }

    // ── Section Inner Class ──────────────────────────────────────────────

    class Section implements IFluidHandler {
        final int index;
        int amount = 0;
        int lastSentAmount = 0;
        int currentTime = 0;
        int[] incoming;
        int incomingTotalCache = 0;
        int ticksInDirection = 0;

        Section(int index) {
            this.index = index;
            this.incoming = new int[delay];
        }

        void resizeIncoming(int newDelay) {
            this.incoming = new int[newDelay];
            this.incomingTotalCache = 0;
            this.currentTime = 0;
        }

        Dir getCurrentDirection() {
            return Dir.get(ticksInDirection);
        }

        int getMaxFilled() {
            int availableTotal = capacity - amount;
            int availableThisTick = transferPerTick - incoming[currentTime];
            return Math.min(availableTotal, availableThisTick);
        }

        int getMaxDrained() {
            return Math.min(amount - incomingTotalCache, transferPerTick);
        }

        int fill(int maxFill, boolean doFill) {
            int amountToFill = Math.min(getMaxFilled(), maxFill);
            if (amountToFill <= 0) return 0;
            if (doFill) {
                incoming[currentTime] += amountToFill;
                incomingTotalCache += amountToFill;
                amount += amountToFill;
            }
            return amountToFill;
        }

        int drainInternal(int maxDrain, boolean doDrain) {
            maxDrain = Math.min(maxDrain, getMaxDrained());
            if (maxDrain <= 0) return 0;
            if (doDrain) {
                amount -= maxDrain;
            }
            return maxDrain;
        }

        void advanceForMovement() {
            incomingTotalCache -= incoming[currentTime];
            incoming[currentTime] = 0;
        }

        void writeToNbt(CompoundTag nbt) {
            nbt.putShort("amount", (short) amount);
            nbt.putShort("ticksInDir", (short) ticksInDirection);
            for (int i = 0; i < incoming.length; i++) {
                nbt.putShort("in" + i, (short) incoming[i]);
            }
        }

        void readFromNbt(CompoundTag nbt) {
            amount = nbt.getShort("amount");
            ticksInDirection = nbt.getShort("ticksInDir");
            incomingTotalCache = 0;
            for (int i = 0; i < incoming.length; i++) {
                incoming[i] = nbt.getShort("in" + i);
                incomingTotalCache += incoming[i];
            }
        }

        // ── IFluidHandler implementation ─────────────────────────────────

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return amount > 0 && !currentFluid.isEmpty()
                    ? currentFluid.copyWithAmount(amount)
                    : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return capacity;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return currentFluid.isEmpty() || FluidStack.isSameFluidSameComponents(currentFluid, stack);
        }

        @Override
        public int fill(@NotNull FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) return 0;
            // Only accept if direction allows input
            Dir dir = getCurrentDirection();
            if (!dir.canInput()) return 0;
            // Check pipe connectivity for face sections
            if (index < 6) {
                Direction face = Direction.values()[index];
                if (!directions.contains(face)) return 0;
            }

            if (!currentFluid.isEmpty() && !FluidStack.isSameFluidSameComponents(currentFluid, resource)) {
                return 0;
            }

            if (action.execute()) {
                if (currentFluid.isEmpty()) {
                    currentFluid = resource.copyWithAmount(1);
                    needsSync = true;
                    // Resize incoming arrays for new fluid
                    for (Section s : sections) {
                        s.resizeIncoming(delay);
                    }
                }
            }

            int filled = fill(resource.getAmount(), action.execute());
            if (filled > 0 && action.execute()) {
                ticksInDirection = COOLDOWN_INPUT;
                setChanged();
            }
            return filled;
        }

        @Override
        public @NotNull FluidStack drain(@NotNull FluidStack resource, FluidAction action) {
            // Pipes don't allow external draining
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            // Pipes don't allow external draining
            return FluidStack.EMPTY;
        }
    }
}
