# Fluid Pipes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the BuildCraft 1.12 section-based fluid pipe system to NeoForge 1.21.1 with 4 materials (Wooden, Cobblestone, Stone, Gold) and TESR fluid rendering.

**Architecture:** Each fluid pipe has 7 internal sections (6 faces + center). Fluid physically moves through sections: face-in -> center -> face-out -> next pipe. A TESR renders the actual fluid texture as 3D quads inside the pipe, with fill level proportional to amount. The system reuses the existing `PipeBlockEntity<CAP>` / `PipeBlock` / `PipeType` / `Pipe` infrastructure.

**Tech Stack:** NeoForge 1.21.1, Java 21, `IFluidHandler` capability, `BlockEntityRenderer`, `CustomPacketPayload` networking, data-driven pipe registration via `PipeRegistrationHelper`.

**Spec:** `docs/superpowers/specs/2026-04-07-fluid-pipes-design.md`

---

### File Map

| Action | File | Responsibility |
|--------|------|----------------|
| Create | `content/blockentities/FluidPipeBE.java` | Core fluid transport logic with 7 sections |
| Create | `content/blockentities/ExtractingFluidPipeBE.java` | Wooden fluid pipe: energy-based extraction |
| Create | `content/blocks/FluidPipeBlock.java` | Block with fluid capability connections |
| Create | `content/blocks/ExtractingFluidPipeBlock.java` | Extracting variant block |
| Create | `networking/SyncFluidPipePayload.java` | Client sync for fluid amounts/directions |
| Create | `client/blockentities/FluidPipeBERenderer.java` | TESR rendering fluid inside pipes |
| Modify | `registries/BCPipeTypes.java` | Add FLUID_DEFAULT + FLUID_EXTRACTING |
| Modify | `registries/BCPipes.java` | Add 4 fluid pipe material variants |
| Modify | `registries/BCBlockEntities.java` | Add FLUID_PIPE block entity type |
| Modify | `BuildcraftLegacy.java` | Register capability + payload |
| Modify | `BuildcraftLegacyClient.java` | Register renderer |
| Modify | `util/CapabilityUtils.java` | Add fluidHandlerCapability(be, direction) |
| Create | 5 textures in `textures/block/` | Pipe textures (PNGs) |

All paths relative to `src/main/java/com/thepigcat/buildcraft/`.

---

### Task 1: FluidPipeBlock + ExtractingFluidPipeBlock

**Files:**
- Create: `src/main/java/com/thepigcat/buildcraft/content/blocks/FluidPipeBlock.java`
- Create: `src/main/java/com/thepigcat/buildcraft/content/blocks/ExtractingFluidPipeBlock.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/util/CapabilityUtils.java`

- [ ] **Step 1: Add directional fluidHandlerCapability to CapabilityUtils**

In `src/main/java/com/thepigcat/buildcraft/util/CapabilityUtils.java`, add after the existing `fluidHandlerCapability(BlockEntity)` method:

```java
public static @Nullable IFluidHandler fluidHandlerCapability(BlockEntity blockEntity, Direction direction) {
    return blockEntityCapability(Capabilities.FluidHandler.BLOCK, blockEntity, direction);
}
```

- [ ] **Step 2: Create FluidPipeBlock**

Create `src/main/java/com/thepigcat/buildcraft/content/blocks/FluidPipeBlock.java`:

```java
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
        return block instanceof FluidPipeBlock;
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
```

- [ ] **Step 3: Create ExtractingFluidPipeBlock**

Create `src/main/java/com/thepigcat/buildcraft/content/blocks/ExtractingFluidPipeBlock.java`:

```java
package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.PipesRegistry;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.api.blocks.ExtractingPipeBlock;
import com.thepigcat.buildcraft.api.pipes.Pipe;
import com.thepigcat.buildcraft.content.blockentities.FluidPipeBE;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.util.CapabilityUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
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
import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.util.BlockUtils;
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
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hitResult) {
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
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(ExtractingFluidPipeBlock::new);
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
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blocks/FluidPipeBlock.java \
        src/main/java/com/thepigcat/buildcraft/content/blocks/ExtractingFluidPipeBlock.java \
        src/main/java/com/thepigcat/buildcraft/util/CapabilityUtils.java
git commit -m "feat: add FluidPipeBlock and ExtractingFluidPipeBlock"
```

---

### Task 2: SyncFluidPipePayload

**Files:**
- Create: `src/main/java/com/thepigcat/buildcraft/networking/SyncFluidPipePayload.java`

- [ ] **Step 1: Create the payload record**

Create `src/main/java/com/thepigcat/buildcraft/networking/SyncFluidPipePayload.java`:

```java
package com.thepigcat.buildcraft.networking;

import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.content.blockentities.FluidPipeBE;
import com.thepigcat.buildcraft.util.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public record SyncFluidPipePayload(
        BlockPos pos,
        Optional<FluidStack> fluid,
        short[] amounts,
        byte[] directions
) implements CustomPacketPayload {

    public static final Type<SyncFluidPipePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BuildcraftLegacy.MODID, "sync_fluid_pipe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncFluidPipePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public @NotNull SyncFluidPipePayload decode(RegistryFriendlyByteBuf buf) {
                    BlockPos pos = buf.readBlockPos();
                    boolean hasFluid = buf.readBoolean();
                    Optional<FluidStack> fluid = hasFluid
                            ? Optional.of(FluidStack.OPTIONAL_STREAM_CODEC.decode(buf))
                            : Optional.empty();
                    short[] amounts = new short[7];
                    byte[] dirs = new byte[7];
                    for (int i = 0; i < 7; i++) {
                        amounts[i] = buf.readShort();
                        dirs[i] = buf.readByte();
                    }
                    return new SyncFluidPipePayload(pos, fluid, amounts, dirs);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, SyncFluidPipePayload payload) {
                    buf.writeBlockPos(payload.pos());
                    buf.writeBoolean(payload.fluid().isPresent());
                    payload.fluid().ifPresent(f -> FluidStack.OPTIONAL_STREAM_CODEC.encode(buf, f));
                    for (int i = 0; i < 7; i++) {
                        buf.writeShort(payload.amounts()[i]);
                        buf.writeByte(payload.directions()[i]);
                    }
                }
            };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncFluidPipePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            FluidPipeBE be = BlockUtils.getBE(FluidPipeBE.class, context.player().level(), payload.pos());
            if (be != null) {
                be.handleFluidSync(payload);
            }
        });
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/networking/SyncFluidPipePayload.java
git commit -m "feat: add SyncFluidPipePayload for fluid pipe client sync"
```

---

### Task 3: FluidPipeBE — Core Logic

**Files:**
- Create: `src/main/java/com/thepigcat/buildcraft/content/blockentities/FluidPipeBE.java`

- [ ] **Step 1: Create FluidPipeBE with Section inner class and tick logic**

Create `src/main/java/com/thepigcat/buildcraft/content/blockentities/FluidPipeBE.java`:

```java
package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.content.blocks.FluidPipeBlock;
import com.thepigcat.buildcraft.networking.SyncFluidPipePayload;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FluidPipeBE extends PipeBlockEntity<IFluidHandler> {

    private static final int DIRECTION_COOLDOWN = 60;
    private static final int COOLDOWN_INPUT = -DIRECTION_COOLDOWN;
    private static final int COOLDOWN_OUTPUT = DIRECTION_COOLDOWN;

    // Section indices: 0-5 = Direction.ordinal(), 6 = CENTER
    private static final int CENTER = 6;

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

    public FluidPipeBE(BlockPos pos, BlockState blockState) {
        this(BCBlockEntities.FLUID_PIPE.get(), pos, blockState);
    }

    protected FluidPipeBE(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        for (int i = 0; i < 7; i++) {
            sections[i] = new Section(i);
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
            if (section.getCurrentDirection() == Dir.OUT) {
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
            if (section.getCurrentDirection() != Dir.OUT) continue;

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
            if (section.getCurrentDirection() != Dir.OUT) continue;
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
            if (section.getCurrentDirection() == Dir.IN || section.getCurrentDirection() == Dir.NONE) {
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

    // ── Section Inner Class ──────────────────────────────────────────────

    enum Dir {
        IN, NONE, OUT;

        public static Dir get(int ticksInDirection) {
            if (ticksInDirection == 0) return NONE;
            return ticksInDirection < 0 ? IN : OUT;
        }
    }

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
            if (dir == Dir.OUT) return 0;
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
                    currentFluid = resource.copyWithAmount(0);
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
```

- [ ] **Step 2: Compile check**

Run: `./gradlew compileJava 2>&1 | tail -30`
Expected: Compilation errors for missing BCBlockEntities.FLUID_PIPE — that's OK, we register it in Task 5.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/FluidPipeBE.java
git commit -m "feat: add FluidPipeBE with section-based fluid transport"
```

---

### Task 4: ExtractingFluidPipeBE

**Files:**
- Create: `src/main/java/com/thepigcat/buildcraft/content/blockentities/ExtractingFluidPipeBE.java`

- [ ] **Step 1: Create ExtractingFluidPipeBE**

Create `src/main/java/com/thepigcat/buildcraft/content/blockentities/ExtractingFluidPipeBE.java`:

```java
package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class ExtractingFluidPipeBE extends FluidPipeBE {
    protected final EnergyStorage energyStorage;

    public ExtractingFluidPipeBE(BlockPos pos, BlockState blockState) {
        this(BCBlockEntities.FLUID_PIPE.get(), pos, blockState);
    }

    protected ExtractingFluidPipeBE(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        this.energyStorage = new EnergyStorage(BCConfig.extractionPipeEnergyCapacity);
    }

    @Override
    public void tick() {
        if (level != null && !level.isClientSide()) {
            extractFluid();
        }
        super.tick();
    }

    private void extractFluid() {
        if (extracting == null) return;
        if (energyStorage.getEnergyStored() < BCConfig.extractionEnergyCost) return;

        BlockCapabilityCache<IFluidHandler, Direction> cache = capabilityCaches.get(extracting);
        if (cache == null) return;
        IFluidHandler source = cache.getCapability();
        if (source == null) return;

        // Try to drain from the source
        FluidStack simulated = source.drain(transferPerTick, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.isEmpty()) return;

        // Insert into our extracting-side section
        IFluidHandler mySection = getFluidHandler(extracting);
        int accepted = mySection.fill(simulated, IFluidHandler.FluidAction.EXECUTE);
        if (accepted > 0) {
            source.drain(simulated.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
            energyStorage.extractEnergy(BCConfig.extractionEnergyCost, false);
            setChanged();
        }
    }

    public IEnergyStorage getEnergyStorage(Direction direction) {
        return energyStorage;
    }

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
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/ExtractingFluidPipeBE.java
git commit -m "feat: add ExtractingFluidPipeBE for wooden fluid pipe extraction"
```

---

### Task 5: Registration (PipeTypes, Pipes, BlockEntities, Capabilities, Payload)

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCPipeTypes.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCPipes.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCBlockEntities.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java`

- [ ] **Step 1: Add FLUID_DEFAULT and FLUID_EXTRACTING to BCPipeTypes**

In `BCPipeTypes.java`, add imports for `FluidPipeBlock` and `ExtractingFluidPipeBlock`, then add before `init()`:

```java
// Fluid pipe types
public static final PipeTypeHolder<FluidPipeBlock, ItemPipeBlockItem> FLUID_DEFAULT = HELPER.registerPipeType("fluid_default", FluidPipeBlock::new, ItemPipeBlockItem::new,
        ModelUtils.DEFAULT_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE, ModelUtils.DEFAULT_ITEM_MODEL_FILE,
        "base", "connection");

public static final PipeTypeHolder<ExtractingFluidPipeBlock, ItemPipeBlockItem> FLUID_EXTRACTING = HELPER.registerPipeType("fluid_extracting", ExtractingFluidPipeBlock::new, ItemPipeBlockItem::new,
        ModelUtils.EXTRACTING_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE, ModelUtils.DEFAULT_ITEM_MODEL_FILE,
        "base", "connection", "connection_extracting");
```

- [ ] **Step 2: Add fluid pipe variants to BCPipes**

In `BCPipes.java`, add after the kinesis pipe entries:

```java
// ── Fluid pipes ─────────────────────────────────────────────────────
public static final PipeHolder WOODEN_FLUID = HELPER.registerPipe("wooden_fluid", BCPipeTypes.FLUID_EXTRACTING, "Wooden Fluid Pipe", 0f, List.of(
        BuildcraftLegacy.rl("block/wooden_fluid_pipe"),
        BuildcraftLegacy.rl("block/wooden_fluid_pipe_extracting")
), Either.right(ResourceLocation.parse("oak_planks")), Ingredient.of(ItemTags.PLANKS), List.of(BlockTags.MINEABLE_WITH_AXE), 30);

public static final PipeHolder COBBLESTONE_FLUID = HELPER.registerPipe("cobblestone_fluid", BCPipeTypes.FLUID_DEFAULT, "Cobblestone Fluid Pipe", 0f, List.of(
        BuildcraftLegacy.rl("block/cobblestone_fluid_pipe")
), Either.right(ResourceLocation.parse("cobblestone")), Ingredient.of(Blocks.COBBLESTONE), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 31);

public static final PipeHolder STONE_FLUID = HELPER.registerPipe("stone_fluid", BCPipeTypes.FLUID_DEFAULT, "Stone Fluid Pipe", 0f, List.of(
        BuildcraftLegacy.rl("block/stone_fluid_pipe")
), Either.right(ResourceLocation.parse("stone")), Ingredient.of(Blocks.STONE), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 32);

public static final PipeHolder GOLD_FLUID = HELPER.registerPipe("gold_fluid", BCPipeTypes.FLUID_DEFAULT, "Gold Fluid Pipe", 0f, List.of(
        BuildcraftLegacy.rl("block/gold_fluid_pipe")
), Either.right(ResourceLocation.parse("gold_block")), Ingredient.of(Tags.Items.INGOTS_GOLD), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 33);
```

- [ ] **Step 3: Add FLUID_PIPE to BCBlockEntities**

In `BCBlockEntities.java`, add:

```java
public static final Supplier<BlockEntityType<FluidPipeBE>> FLUID_PIPE = BLOCK_ENTITIES.register("fluid_pipe",
        () -> BlockEntityType.Builder.of(FluidPipeBE::new, collectBlocks(FluidPipeBlock.class, ExtractingFluidPipeBlock.class)).build(null));
```

Add imports for `FluidPipeBE`, `FluidPipeBlock`, `ExtractingFluidPipeBlock`.

- [ ] **Step 4: Register capability and payload in BuildcraftLegacy**

In the `attachCaps` method, add in the `// FLUID` section:

```java
event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, BCBlockEntities.FLUID_PIPE.get(), FluidPipeBE::getFluidHandler);
```

For energy on extracting fluid pipes, add in the `// ENERGY` section:

```java
event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BCBlockEntities.FLUID_PIPE.get(), (be, dir) -> {
    if (be instanceof ExtractingFluidPipeBE ext) return ext.getEnergyStorage(dir);
    return null;
});
```

In the `registerPayloads` method, add:

```java
registrar.playToClient(SyncFluidPipePayload.TYPE, SyncFluidPipePayload.STREAM_CODEC, SyncFluidPipePayload::handle);
```

Add imports for `FluidPipeBE`, `ExtractingFluidPipeBE`, `SyncFluidPipePayload`.

- [ ] **Step 5: Compile check**

Run: `./gradlew compileJava 2>&1 | tail -30`
Expected: Should compile. If texture-related warnings appear, that's expected (textures come in Task 7).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/registries/BCPipeTypes.java \
        src/main/java/com/thepigcat/buildcraft/registries/BCPipes.java \
        src/main/java/com/thepigcat/buildcraft/registries/BCBlockEntities.java \
        src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java
git commit -m "feat: register fluid pipe types, variants, BE, capability, and payload"
```

---

### Task 6: FluidPipeBERenderer

**Files:**
- Create: `src/main/java/com/thepigcat/buildcraft/client/blockentities/FluidPipeBERenderer.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/BuildcraftLegacyClient.java`

- [ ] **Step 1: Create FluidPipeBERenderer**

Create `src/main/java/com/thepigcat/buildcraft/client/blockentities/FluidPipeBERenderer.java`:

```java
package com.thepigcat.buildcraft.client.blockentities;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.content.blockentities.FluidPipeBE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;

/**
 * Renders fluid inside fluid pipes as 3D quads per section.
 * Uses the fluid's own texture and tint color (water, lava, oil, etc.).
 */
public class FluidPipeBERenderer implements BlockEntityRenderer<FluidPipeBE> {

    public FluidPipeBERenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FluidPipeBE be, float partialTick, PoseStack ps,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        FluidStack fluid = be.getFluidForRender();
        if (fluid.isEmpty()) return;

        int capacity = be.getCapacity();
        if (capacity <= 0) return;

        IClientFluidTypeExtensions fluidExt = IClientFluidTypeExtensions.of(fluid.getFluid());
        ResourceLocation stillTex = fluidExt.getStillTexture(fluid);
        if (stillTex == null) return;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(stillTex);

        int tintColor = fluidExt.getTintColor(fluid);
        float r = ((tintColor >> 16) & 0xFF) / 255f;
        float g = ((tintColor >> 8) & 0xFF) / 255f;
        float b = (tintColor & 0xFF) / 255f;
        float a = ((tintColor >> 24) & 0xFF) / 255f;
        if (a == 0f) a = 1f;

        VertexConsumer vc = buffers.getBuffer(RenderType.translucent());
        Matrix4f pose = ps.last().pose();

        BlockState state = be.getBlockState();

        // Render center section (index 6)
        double centerAmount = be.getAmountForRender(6, partialTick);
        if (centerAmount > 0) {
            float fill = (float) Math.min(1.0, centerAmount / capacity);
            float halfSize = 0.24f * fill;
            float min = 0.5f - halfSize;
            float max = 0.5f + halfSize;
            // Render fill as height for center
            float height = 0.26f + (0.74f - 0.26f) * fill;
            renderBox(pose, vc, sprite, 0.26f, 0.74f, 0.26f, height, 0.26f, 0.74f,
                    r, g, b, a, packedLight, packedOverlay);
        }

        // Render face sections (index 0-5)
        for (Direction dir : Direction.values()) {
            PipeBlock.PipeState pipeState = state.getValue(PipeBlock.CONNECTION[dir.get3DDataValue()]);
            if (pipeState == PipeBlock.PipeState.NONE) continue;

            double amount = be.getAmountForRender(dir.ordinal(), partialTick);
            if (amount <= 0) continue;

            float fill = (float) Math.min(1.0, amount / capacity);
            renderConnectionFluid(pose, vc, sprite, dir, fill, r, g, b, a, packedLight, packedOverlay);
        }
    }

    private void renderConnectionFluid(Matrix4f pose, VertexConsumer vc, TextureAtlasSprite sprite,
                                        Direction dir, float fill,
                                        float r, float g, float b, float a,
                                        int light, int overlay) {
        // For horizontal pipes: fluid height is proportional to fill
        // For vertical pipes: fluid radius scales with sqrt(fill) like original BC
        float pipeInner = 0.24f;

        switch (dir.getAxis()) {
            case X -> {
                float halfH = pipeInner * fill;
                float yMin = 0.5f - halfH;
                float yMax = 0.5f + halfH;
                float zMin = 0.5f - pipeInner;
                float zMax = 0.5f + pipeInner;
                if (dir == Direction.WEST) {
                    renderBox(pose, vc, sprite, 0f, 0.26f, yMin, yMax, zMin, zMax, r, g, b, a, light, overlay);
                } else {
                    renderBox(pose, vc, sprite, 0.74f, 1f, yMin, yMax, zMin, zMax, r, g, b, a, light, overlay);
                }
            }
            case Z -> {
                float halfH = pipeInner * fill;
                float yMin = 0.5f - halfH;
                float yMax = 0.5f + halfH;
                float xMin = 0.5f - pipeInner;
                float xMax = 0.5f + pipeInner;
                if (dir == Direction.NORTH) {
                    renderBox(pose, vc, sprite, xMin, xMax, yMin, yMax, 0f, 0.26f, r, g, b, a, light, overlay);
                } else {
                    renderBox(pose, vc, sprite, xMin, xMax, yMin, yMax, 0.74f, 1f, r, g, b, a, light, overlay);
                }
            }
            case Y -> {
                float radius = pipeInner * (float) Math.sqrt(fill);
                float xMin = 0.5f - radius;
                float xMax = 0.5f + radius;
                float zMin = 0.5f - radius;
                float zMax = 0.5f + radius;
                if (dir == Direction.DOWN) {
                    renderBox(pose, vc, sprite, xMin, xMax, 0f, 0.26f, zMin, zMax, r, g, b, a, light, overlay);
                } else {
                    renderBox(pose, vc, sprite, xMin, xMax, 0.74f, 1f, zMin, zMax, r, g, b, a, light, overlay);
                }
            }
        }
    }

    private void renderBox(Matrix4f pose, VertexConsumer vc, TextureAtlasSprite sprite,
                           float x0, float x1, float y0, float y1, float z0, float z1,
                           float r, float g, float b, float a,
                           int light, int overlay) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        // Down face (y = y0)
        vc.addVertex(pose, x0, y0, z0).setColor(r, g, b, a).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(0, -1, 0);
        vc.addVertex(pose, x1, y0, z0).setColor(r, g, b, a).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(0, -1, 0);
        vc.addVertex(pose, x1, y0, z1).setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(0, -1, 0);
        vc.addVertex(pose, x0, y0, z1).setColor(r, g, b, a).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(0, -1, 0);

        // Up face (y = y1)
        vc.addVertex(pose, x0, y1, z1).setColor(r, g, b, a).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(0, 1, 0);
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(0, 1, 0);
        vc.addVertex(pose, x1, y1, z0).setColor(r, g, b, a).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(0, 1, 0);
        vc.addVertex(pose, x0, y1, z0).setColor(r, g, b, a).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(0, 1, 0);

        // North face (z = z0)
        vc.addVertex(pose, x0, y0, z0).setColor(r, g, b, a).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(0, 0, -1);
        vc.addVertex(pose, x0, y1, z0).setColor(r, g, b, a).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(0, 0, -1);
        vc.addVertex(pose, x1, y1, z0).setColor(r, g, b, a).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(0, 0, -1);
        vc.addVertex(pose, x1, y0, z0).setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(0, 0, -1);

        // South face (z = z1)
        vc.addVertex(pose, x1, y0, z1).setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        vc.addVertex(pose, x0, y1, z1).setColor(r, g, b, a).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);
        vc.addVertex(pose, x0, y0, z1).setColor(r, g, b, a).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(0, 0, 1);

        // West face (x = x0)
        vc.addVertex(pose, x0, y0, z1).setColor(r, g, b, a).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(-1, 0, 0);
        vc.addVertex(pose, x0, y1, z1).setColor(r, g, b, a).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(-1, 0, 0);
        vc.addVertex(pose, x0, y1, z0).setColor(r, g, b, a).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(-1, 0, 0);
        vc.addVertex(pose, x0, y0, z0).setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(-1, 0, 0);

        // East face (x = x1)
        vc.addVertex(pose, x1, y0, z0).setColor(r, g, b, a).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(1, 0, 0);
        vc.addVertex(pose, x1, y1, z0).setColor(r, g, b, a).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(1, 0, 0);
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(1, 0, 0);
        vc.addVertex(pose, x1, y0, z1).setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(1, 0, 0);
    }
}
```

- [ ] **Step 2: Register renderer in BuildcraftLegacyClient**

In `BuildcraftLegacyClient.java`, in the `registerRenderers` method, add:

```java
event.registerBlockEntityRenderer(BCBlockEntities.FLUID_PIPE.get(), FluidPipeBERenderer::new);
```

Add import for `FluidPipeBERenderer`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/client/blockentities/FluidPipeBERenderer.java \
        src/main/java/com/thepigcat/buildcraft/BuildcraftLegacyClient.java
git commit -m "feat: add FluidPipeBERenderer for in-pipe fluid visualization"
```

---

### Task 7: Textures

**Files:**
- Create: `src/main/resources/assets/buildcraft/textures/block/wooden_fluid_pipe.png`
- Create: `src/main/resources/assets/buildcraft/textures/block/wooden_fluid_pipe_extracting.png`
- Create: `src/main/resources/assets/buildcraft/textures/block/cobblestone_fluid_pipe.png`
- Create: `src/main/resources/assets/buildcraft/textures/block/stone_fluid_pipe.png`
- Create: `src/main/resources/assets/buildcraft/textures/block/gold_fluid_pipe.png`

- [ ] **Step 1: Create placeholder textures**

Create 16x16 placeholder PNGs for all 5 textures. These should be simple colored squares that make pipes distinguishable:
- Wooden fluid: tan/brown with blue tint (to distinguish from item pipe)
- Wooden fluid extracting: same with darker extraction stripe
- Cobblestone fluid: gray with blue tint
- Stone fluid: light gray with blue tint
- Gold fluid: yellow/gold with blue tint

Use the existing pipe textures as base and add a blue-ish tint or border to indicate "fluid" variant. Copy the closest existing texture and modify in an image editor, or create simple programmatic PNGs.

Alternatively, copy existing textures as placeholders:

```bash
cd src/main/resources/assets/buildcraft/textures/block/
cp wooden_pipe.png wooden_fluid_pipe.png
cp wooden_pipe_extracting.png wooden_fluid_pipe_extracting.png
cp cobblestone_pipe.png cobblestone_fluid_pipe.png
cp stone_pipe.png stone_fluid_pipe.png
cp gold_pipe.png gold_fluid_pipe.png
```

These serve as temporary placeholders until proper fluid-pipe-specific textures are created.

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/assets/buildcraft/textures/block/wooden_fluid_pipe.png \
        src/main/resources/assets/buildcraft/textures/block/wooden_fluid_pipe_extracting.png \
        src/main/resources/assets/buildcraft/textures/block/cobblestone_fluid_pipe.png \
        src/main/resources/assets/buildcraft/textures/block/stone_fluid_pipe.png \
        src/main/resources/assets/buildcraft/textures/block/gold_fluid_pipe.png
git commit -m "assets: add placeholder textures for fluid pipes"
```

---

### Task 8: Datagen + Build Verification

**Files:**
- Various datagen files (auto-generated by `runData`)

- [ ] **Step 1: Run datagen**

```bash
./gradlew runData 2>&1 | tail -30
```

This generates blockstates, block models, item models, loot tables, recipes, tags, and lang entries for the 4 new fluid pipes.

- [ ] **Step 2: Check generated files exist**

```bash
ls src/main/resources/assets/buildcraft/blockstates/*fluid* 2>/dev/null
ls src/main/resources/assets/buildcraft/models/block/*fluid* 2>/dev/null
ls src/main/resources/assets/buildcraft/models/item/*fluid* 2>/dev/null
```

- [ ] **Step 3: Full build**

```bash
./gradlew build 2>&1 | tail -30
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit generated files**

```bash
git add src/main/resources/
git commit -m "datagen: generate blockstates, models, recipes for fluid pipes"
```

---

### Task 9: Integration Test (runClient)

- [ ] **Step 1: Launch client**

```bash
./gradlew runClient
```

- [ ] **Step 2: In-game testing checklist**

1. Place a tank filled with water
2. Place a wooden fluid pipe next to the tank (should show extracting connection)
3. Extend with cobblestone fluid pipes
4. Place an empty tank at the end
5. Verify: fluid flows from source tank -> wooden pipe -> cobblestone pipes -> destination tank
6. Verify: fluid renders inside the pipes (blue water texture visible)
7. Verify: wooden pipe requires energy (connect a redstone engine)
8. Try stone and gold pipes — verify higher transfer rates
9. Break a pipe mid-chain — verify no crash, fluid in broken pipe is lost
10. Verify: fluid pipes do NOT connect to item pipes or kinesis pipes
11. Verify: stone and cobblestone fluid pipes do NOT connect to each other

- [ ] **Step 3: Fix any issues found during testing**

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "fix: address issues found during fluid pipe integration testing"
```
