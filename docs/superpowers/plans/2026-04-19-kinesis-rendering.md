# Kinesis Pipe Per-Section Power & Flow Direction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade KinesisPipeBE and KinesisPipeBERenderer so each of the 7 sections (6 arms + center) has an independent beam radius driven by actual energy flow, and UV scroll direction matches real flow direction (toward center = incoming, away = outgoing).

**Architecture:** Server-side: 6 pre-created `IEnergyStorage` wrappers intercept `receiveEnergy` to track incoming-per-direction; `distributeEnergy()` tracks outgoing-per-direction. After each tick, `syncSectionPowerIfNeeded()` computes `float[7]` section power + `boolean[6]` flow direction, then sends a block update if values changed. Client receives via `handleUpdateTag`, stores in target arrays, smooths each section in `clientTick()`. Renderer reads per-section power for radius and flow direction for UV scroll sign.

**Tech Stack:** NeoForge 1.21.1, Java 21, `KinesisPipeBE`, `KinesisPipeBERenderer`, NeoForge `IEnergyStorage`, MC NBT (`ListTag`, `FloatTag`).

---

## File Map

**Modify only:**
- `src/main/java/com/thepigcat/buildcraft/content/blockentities/KinesisPipeBE.java` — Tasks 1, 2, 3
- `src/main/java/com/thepigcat/buildcraft/client/blockentities/KinesisPipeBERenderer.java` — Task 4

No new files. No registry changes. No datagen.

---

## Task 1: Direction Wrappers + Per-Tick Tracking Arrays

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/KinesisPipeBE.java`

The goal: 6 `IEnergyStorage` wrappers (one per Direction ordinal) intercept `receiveEnergy` to populate `incomingThisTick[6]`. `distributeEnergy()` populates `outgoingThisTick[6]` and now also tracks receiver Directions.

- [ ] **Step 1: Add server-side tracking fields**

In `KinesisPipeBE.java`, after the line `private float lossRate = 0.05f;`, add:

```java
// Server-side per-tick energy flow tracking
private final int[] incomingThisTick = new int[6];
private final int[] outgoingThisTick = new int[6];
private final float[] lastSentSectionPower = new float[7];
private final IEnergyStorage[] directionWrappers = new IEnergyStorage[6];
```

- [ ] **Step 2: Initialize direction wrappers in constructor**

In the `protected KinesisPipeBE(BlockEntityType<?> type, BlockPos pos, BlockState blockState)` constructor, after the closing `);` of the `new EnergyStorage(...)` block, add:

```java
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
```

- [ ] **Step 3: Return wrapper from getEnergyStorage**

Find:
```java
public IEnergyStorage getEnergyStorage(Direction direction) {
    return energyStorage;
}
```

Replace with:
```java
public IEnergyStorage getEnergyStorage(Direction direction) {
    return direction != null ? directionWrappers[direction.get3DDataValue()] : energyStorage;
}
```

- [ ] **Step 4: Rewrite distributeEnergy to track outgoing per direction**

Find the entire `private void distributeEnergy()` method (lines 180–225) and replace it with:

```java
private void distributeEnergy() {
    java.util.Arrays.fill(outgoingThisTick, 0);
    if (energyStorage.getEnergyStored() <= 0) return;

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

        int loss = (int) (offer * lossRate);
        int toDeliver = offer - loss;
        if (toDeliver <= 0) {
            totalConsumed += offer;
            continue;
        }

        int accepted = receiver.receiveEnergy(toDeliver, false);
        if (accepted > 0) {
            outgoingThisTick[dir.get3DDataValue()] += accepted;
            int proportionalLoss = (toDeliver > 0) ? (int) Math.ceil((double) loss * accepted / toDeliver) : 0;
            totalConsumed += accepted + proportionalLoss;
        }
    }

    if (totalConsumed > 0) {
        energyStorage.extractEnergy(totalConsumed, false);
        setChanged();
    }
}
```

- [ ] **Step 5: Compile check**

```bash
./gradlew compileJava 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/KinesisPipeBE.java
git commit -m "feat(kinesis-rendering): add direction wrappers + per-tick incoming/outgoing tracking"
```

---

## Task 2: sectionPower Computation + Client Sync

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/KinesisPipeBE.java`

Add `syncSectionPowerIfNeeded()` which computes normalized per-section power, detects changes, and sends a block update that includes section data in `getUpdateTag()`. Also add `handleUpdateTag()` to receive the data client-side.

- [ ] **Step 1: Add client-side target arrays**

After the `lastSentSectionPower` field (end of the tracking fields block added in Task 1), add:

```java
// Client-side targets populated by handleUpdateTag
private final float[] targetSectionPower = new float[7];
private final boolean[] targetFlowsOut = new boolean[6];
```

- [ ] **Step 2: Add syncSectionPowerIfNeeded method**

Add this method after `distributeEnergy()`:

```java
private void syncSectionPowerIfNeeded() {
    float[] current = new float[7];
    for (int d = 0; d < 6; d++) {
        current[d] = maxTransfer > 0
            ? Math.min(1f, (float) Math.max(incomingThisTick[d], outgoingThisTick[d]) / maxTransfer)
            : 0f;
    }
    // Center: max arm activity, fallback to stored/capacity
    int maxArmFlow = 0;
    for (int d = 0; d < 6; d++) {
        maxArmFlow = Math.max(maxArmFlow, Math.max(incomingThisTick[d], outgoingThisTick[d]));
    }
    if (maxArmFlow > 0 && maxTransfer > 0) {
        current[6] = Math.min(1f, (float) maxArmFlow / maxTransfer);
    } else {
        int capacity = energyStorage.getMaxEnergyStored();
        current[6] = capacity > 0 ? Math.min(1f, (float) energyStorage.getEnergyStored() / capacity) : 0f;
    }

    boolean changed = false;
    for (int s = 0; s < 7; s++) {
        if (Math.abs(current[s] - lastSentSectionPower[s]) > 0.05f) { changed = true; break; }
    }
    if (!changed) {
        boolean wasActive = false, nowActive = false;
        for (int s = 0; s < 7; s++) {
            if (lastSentSectionPower[s] > 0.005f) wasActive = true;
            if (current[s] > 0.005f) nowActive = true;
        }
        if (wasActive && !nowActive) changed = true;
    }
    if (changed) {
        System.arraycopy(current, 0, lastSentSectionPower, 0, 7);
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // Reset incoming for next tick (outgoing is reset at start of distributeEnergy)
    java.util.Arrays.fill(incomingThisTick, 0);
}
```

- [ ] **Step 3: Call syncSectionPowerIfNeeded from tick()**

In the server-side branch of `tick()`, change:
```java
        distributeEnergy();
        updatePowerLevel();
```
to:
```java
        distributeEnergy();
        syncSectionPowerIfNeeded();
        updatePowerLevel();
```

- [ ] **Step 4: Override getUpdateTag**

Add after `syncSectionPowerIfNeeded()`:

```java
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
        if (outgoingThisTick[d] > 0) flowsOutBits |= (byte) (1 << d);
    }
    tag.putByte("flows_out", flowsOutBits);
    return tag;
}
```

- [ ] **Step 5: Override handleUpdateTag**

Add after `getUpdateTag`:

```java
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
```

- [ ] **Step 6: Compile check**

```bash
./gradlew compileJava 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/KinesisPipeBE.java
git commit -m "feat(kinesis-rendering): compute sectionPower + sync via getUpdateTag/handleUpdateTag"
```

---

## Task 3: Client-Side Per-Section Smoothing

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/KinesisPipeBE.java`

Replace single `displayPower`/`lastDisplayPower` with `float[7]` arrays. Add `getSectionPower(int, float)` and `getSectionFlowsOut(int)` for the renderer. Keep a temporary `getDisplayPower` stub so the renderer still compiles before Task 4.

- [ ] **Step 1: Replace display float fields**

Find and remove:
```java
    // Client-side display smoothing for the renderer
    private float displayPower = 0f;
    private float lastDisplayPower = 0f;
```

Replace with:
```java
    // Client-side display smoothing — per section (0–5: arms by Direction ordinal, 6: center)
    private final float[] displaySectionPower = new float[7];
    private final float[] lastDisplaySectionPower = new float[7];
    private final boolean[] displayFlowsOut = new boolean[6];
```

- [ ] **Step 2: Replace getDisplayPower with new getters (keep stub)**

Find and remove:
```java
    /**
     * Smoothed power value (0–1) for the renderer, interpolated with partialTick.
     */
    public float getDisplayPower(float partialTick) {
        return lastDisplayPower + (displayPower - lastDisplayPower) * partialTick;
    }
```

Replace with:
```java
    /** Smoothed power (0–1) for section s, interpolated with partialTick. Section 6 = center. */
    public float getSectionPower(int section, float partialTick) {
        return lastDisplaySectionPower[section]
               + (displaySectionPower[section] - lastDisplaySectionPower[section]) * partialTick;
    }

    /** True if energy flows outward on arm d (UV scrolls away from center). */
    public boolean getSectionFlowsOut(int dir) {
        return displayFlowsOut[dir];
    }

    /** @deprecated — remove after Task 4 updates the renderer */
    @Deprecated
    public float getDisplayPower(float partialTick) {
        return getSectionPower(6, partialTick);
    }
```

- [ ] **Step 3: Rewrite clientTick() for per-section smoothing**

Find and replace the existing `clientTick()` method:

```java
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
```

With:
```java
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
```

- [ ] **Step 4: Compile check**

```bash
./gradlew compileJava 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` (stub keeps renderer compiling)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/KinesisPipeBE.java
git commit -m "feat(kinesis-rendering): replace single displayPower with per-section smoothing arrays"
```

---

## Task 4: Renderer — Per-Section Radius + Correct UV Scroll Direction

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/client/blockentities/KinesisPipeBERenderer.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/KinesisPipeBE.java` (remove stub)

- [ ] **Step 1: Remove deprecated stub from KinesisPipeBE**

Find and remove:
```java
    /** @deprecated — remove after Task 4 updates the renderer */
    @Deprecated
    public float getDisplayPower(float partialTick) {
        return getSectionPower(6, partialTick);
    }
```

- [ ] **Step 2: Replace render() in KinesisPipeBERenderer**

Find the entire `render()` method:
```java
    @Override
    public void render(KinesisPipeBE be, float partialTick, PoseStack ps, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        // Use smoothed display power from the BE (client-side interpolated)
        float power = be.getDisplayPower(partialTick);
        if (power <= 0.001f) return;

        // Continuous radius with sqrt curve (original BC used sqrt for visual emphasis)
        float radius = MAX_RADIUS * (float) Math.sqrt(power);

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(POWER_FLOW_TEXTURE));
        Matrix4f pose = ps.last().pose();
        // Full brightness — energy beams glow like original BC
        int light = LightTexture.FULL_BRIGHT;

        // Animated UV base offset
        float time = 0;
        if (be.getLevel() != null) {
            time = (be.getLevel().getGameTime() + partialTick) * SCROLL_SPEED;
        }

        BlockState state = be.getBlockState();

        // Center cube — static UVs (no scroll)
        renderCenterCube(pose, vc, radius, light, packedOverlay);

        // Connection strips — direction-aware UV scroll
        for (Direction dir : Direction.values()) {
            PipeBlock.PipeState pipeState = state.getValue(PipeBlock.CONNECTION[dir.get3DDataValue()]);
            if (pipeState != PipeBlock.PipeState.NONE) {
                // Scroll direction matches axis: positive dirs flow outward, negative dirs flow inward
                float dirScroll = time * dir.getAxisDirection().getStep();
                float uvOffset = dirScroll % 1.0f;
                renderConnectionStrip(pose, vc, dir, radius, uvOffset, light, packedOverlay);
            }
        }
    }
```

Replace with:
```java
    @Override
    public void render(KinesisPipeBE be, float partialTick, PoseStack ps, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        float centerPower = be.getSectionPower(6, partialTick);
        if (centerPower <= 0.001f) return;

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(POWER_FLOW_TEXTURE));
        Matrix4f pose = ps.last().pose();
        int light = LightTexture.FULL_BRIGHT;

        float time = 0;
        if (be.getLevel() != null) {
            time = (be.getLevel().getGameTime() + partialTick) * SCROLL_SPEED;
        }

        BlockState state = be.getBlockState();

        // Center cube — radius driven by center section power
        float centerRadius = MAX_RADIUS * (float) Math.sqrt(centerPower);
        renderCenterCube(pose, vc, centerRadius, light, packedOverlay);

        // Connection strips — per-section radius, flow-direction-aware UV scroll
        for (Direction dir : Direction.values()) {
            PipeBlock.PipeState pipeState = state.getValue(PipeBlock.CONNECTION[dir.get3DDataValue()]);
            if (pipeState == PipeBlock.PipeState.NONE) continue;

            int d = dir.get3DDataValue();
            float armPower = be.getSectionPower(d, partialTick);
            if (armPower <= 0.001f) continue;

            float armRadius = MAX_RADIUS * (float) Math.sqrt(armPower);
            // outgoing: scroll away from center; incoming: scroll toward center
            float flowSign = be.getSectionFlowsOut(d) ? -1f : 1f;
            float uvOffset = (time * flowSign) % 1.0f;
            renderConnectionStrip(pose, vc, dir, armRadius, uvOffset, light, packedOverlay);
        }
    }
```

- [ ] **Step 3: Compile check**

```bash
./gradlew compileJava 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Build and start client**

```bash
./gradlew runClient
```

- [ ] **Step 5: In-game visual test**

Setup: place a Redstone Engine facing into a Wooden Kinesis Pipe, then a chain of 2–3 more kinesis pipes into a machine that accepts FE (e.g. a powered rail or another engine set as consumer). Activate the engine with a redstone signal.

Verify:
1. **Arm thickness varies**: the arm connected to the engine and the arm leading to the next pipe show a thicker beam than arms that carry no energy.
2. **Flow direction**: on the Wooden Kinesis Pipe the arm touching the engine scrolls *toward the center*; all output arms scroll *away from the center*.
3. **Center brightness**: the center cube is as thick as the most active arm.
4. **Fade-out**: when the engine stops, all sections fade smoothly to invisible over ~7 ticks.
5. **Idle pipe** (no energy): nothing rendered — `centerPower <= 0.001` guard triggers.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/KinesisPipeBE.java \
        src/main/java/com/thepigcat/buildcraft/client/blockentities/KinesisPipeBERenderer.java
git commit -m "feat(kinesis-rendering): per-section radius + flow-direction UV scroll in renderer"
```

---

## Self-Review Checklist

- [x] **Spec coverage**
  - Direction wrappers → Task 1 Steps 1–3 ✓
  - `incomingThisTick[6]` populated via wrappers → Task 1 Step 2 ✓
  - `outgoingThisTick[6]` populated + reset in `distributeEnergy` → Task 1 Step 4 ✓
  - `sectionPower` computation (arms: max(in,out)/maxTransfer; center: maxArmFlow fallback stored/capacity) → Task 2 Step 2 ✓
  - Sync trigger on >0.05 change or active→inactive → Task 2 Step 2 ✓
  - `getUpdateTag` encodes `section_power` (ListTag float[7]) + `flows_out` (byte bitfield) → Task 2 Step 4 ✓
  - `handleUpdateTag` populates `targetSectionPower[7]` + `targetFlowsOut[6]` → Task 2 Step 5 ✓
  - `displaySectionPower[7]`/`lastDisplaySectionPower[7]`/`displayFlowsOut[6]` → Task 3 Step 1 ✓
  - `getSectionPower(int, float)` + `getSectionFlowsOut(int)` → Task 3 Step 2 ✓
  - `clientTick()` smooths per section, blockstate fallback for center → Task 3 Step 3 ✓
  - Renderer per-section radius → Task 4 Step 2 ✓
  - Renderer UV scroll direction from `getSectionFlowsOut` → Task 4 Step 2 ✓
- [x] **No placeholders** — all code is complete
- [x] **Type consistency** — `targetSectionPower`/`targetFlowsOut` defined in Task 2, used in Task 3; `getSectionPower`/`getSectionFlowsOut` defined in Task 3, used in Task 4; `outgoingThisTick` defined in Task 1, used in Tasks 2 + 4
- [x] **Deprecated stub** in Task 3 prevents compile error until Task 4 removes it
- [x] **`incomingThisTick` reset** at end of `syncSectionPowerIfNeeded()` — after `sendBlockUpdated` has read the values
- [x] **`outgoingThisTick` reset** at start of `distributeEnergy()` — before populating for this tick
