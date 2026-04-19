# Kinesis Pipe Rendering — Per-Section Power & Flow Direction Design Spec

**Date:** 2026-04-19  
**Status:** Approved

## Overview

Upgrade `KinesisPipeBERenderer` to match the original BC 1.12 kinesis rendering:

- **Per-section power levels** — each of the 7 sections (6 arms + center) has an independent power level that controls the beam radius.
- **Correct flow direction** — UV scroll on each arm shows whether energy is flowing in or out, instead of being hardcoded to the axis orientation.

Both features require server-side tracking of incoming and outgoing energy per direction, synced to the client via `getUpdateTag`/`handleUpdateTag`.

---

## Server-Side Tracking (KinesisPipeBE)

### Direction Wrappers

6 `IEnergyStorage` wrappers are pre-created in the constructor (one per direction). Each wrapper:
- Delegates all methods to the base `energyStorage`
- On `receiveEnergy(amount, simulate=false)` with `accepted > 0`: writes to `incomingThisTick[dir.get3DDataValue()] += accepted`
- Returns `canExtract() = false` (pipes never expose extraction externally)

These wrappers replace the return value of `getEnergyStorage(Direction direction)` — instead of returning the raw storage, return `directionWrappers[dir.get3DDataValue()]`.

### Per-Tick Arrays

```java
private final int[] incomingThisTick = new int[6]; // reset at start of each server tick
private final int[] outgoingThisTick = new int[6]; // populated in distributeEnergy()
```

`incomingThisTick` is reset at the start of each server tick (before `distributeEnergy`).  
`outgoingThisTick` is reset before `distributeEnergy`, then populated: for each direction where `receiver.receiveEnergy(toDeliver, false)` returns `accepted > 0`, add `accepted` to `outgoingThisTick[dir.get3DDataValue()]`.

### sectionPower Computation

After distribution, compute `float[] serverSectionPower` (not stored as field — computed inline for sync):

```
for d in 0..5:
    serverSectionPower[d] = clamp(max(incomingThisTick[d], outgoingThisTick[d]) / maxTransfer, 0, 1)
serverSectionPower[6] = clamp(energyStored / capacity, 0, 1)
```

`sectionFlowsOut[d]` (boolean): `outgoingThisTick[d] > 0`

### Sync Trigger

After computing `serverSectionPower`: if any value differs from the last-sent value by more than `0.05f`, or if the pipe transitioned from active to fully inactive (all zero), call:
```java
level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
```

Store `float[] lastSentSectionPower = new float[7]` to track what was last sent (updated on each sync trigger).

---

## Client Sync

### getUpdateTag / handleUpdateTag

`getUpdateTag()` includes (in addition to existing data):
- `"section_power"` — `ListTag` of 7 `FloatTag` values (indices 0–6)
- `"flows_out"` — `ByteTag` bitfield: bit `d` = `sectionFlowsOut[d]` (bit 0 = DOWN, …, bit 5 = EAST)

`handleUpdateTag()` reads both and writes to:
- `float[] targetSectionPower[7]`
- `boolean[] targetFlowsOut[6]`

### Existing POWER_LEVEL Blockstate

Kept as-is. The `POWER_LEVEL` blockstate continues to be set by `updatePowerLevel()` and is used by the existing `clientTick()` smoothing for the center section as a fallback. The new `targetSectionPower[6]` (from UpdateTag) takes priority in the renderer when available (non-zero).

---

## Client-Side Smoothing (KinesisPipeBE)

Replace `float displayPower` / `float lastDisplayPower` with:

```java
private final float[] displaySectionPower = new float[7];
private final float[] lastDisplaySectionPower = new float[7];
private final boolean[] displayFlowsOut = new boolean[6];
private final float[] targetSectionPower = new float[7];
private final boolean[] targetFlowsOut = new boolean[6];
```

`clientTick()` per section:
```
lastDisplaySectionPower[s] = displaySectionPower[s]
displaySectionPower[s] += (targetSectionPower[s] - displaySectionPower[s]) * 0.15f
if displaySectionPower[s] < 0.005f && targetSectionPower[s] == 0:
    displaySectionPower[s] = 0
displayFlowsOut[d] = targetFlowsOut[d]  // direct, no smoothing
```

Add method:
```java
public float getSectionPower(int section, float partialTick) {
    return lastDisplaySectionPower[section] + (displaySectionPower[section] - lastDisplaySectionPower[section]) * partialTick;
}
public boolean getSectionFlowsOut(int dir) { return displayFlowsOut[dir]; }
```

Remove `getDisplayPower(float partialTick)` — replaced by `getSectionPower(6, partialTick)` for the center.

---

## Renderer (KinesisPipeBERenderer)

### Changes

- Replace `float power = be.getDisplayPower(partialTick)` with per-section reads.
- `renderCenterCube`: uses `be.getSectionPower(6, partialTick)` for radius.
- `renderConnectionStrip`: uses `be.getSectionPower(dir.get3DDataValue(), partialTick)` for radius.
- UV scroll direction: replace `dir.getAxisDirection().getStep()` with:
  ```java
  float flowSign = be.getSectionFlowsOut(dir.get3DDataValue()) ? -1f : 1f;
  float dirScroll = time * flowSign;
  ```
  (outgoing = scroll away from center = negative; incoming = scroll toward center = positive)
- Guard: if `getSectionPower(s, partialTick) <= 0.001f`, skip that section (no beam).

### Signature change in renderConnectionStrip

```java
private void renderConnectionStrip(Matrix4f pose, VertexConsumer vc,
                                    Direction dir, float r, float uvOffset,
                                    int light, int overlay)
```
Signature unchanged — `r` is now computed per-section before the call.

---

## Files to Modify

| File | Change |
|------|--------|
| `content/blockentities/KinesisPipeBE.java` | Direction wrappers, per-tick arrays, sectionPower computation, sync trigger, client-side smoothing arrays, `getSectionPower`/`getSectionFlowsOut`, `getUpdateTag`/`handleUpdateTag` |
| `client/blockentities/KinesisPipeBERenderer.java` | Per-section radius, corrected UV scroll direction |

No new files. No registry changes. No datagen.

---

## Transfer Rate Reference (from existing KinesisPipeBE.applyMaterialProperties)

| Material | maxTransfer | lossRate |
|----------|-------------|----------|
| Wooden   | 80          | 1%       |
| Cobblestone | 80       | 10%      |
| Stone    | 120         | 2%       |
| Sandstone | 120        | 2%       |
| Quartz   | 200         | 1%       |
| Diamond  | 320         | 0%       |
| Gold     | 512         | 0%       |

`capacity = BCConfig.kinesisPipeEnergyCapacity` (used for center power normalization).
