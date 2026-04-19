# Fluid Pipes Phase 2: Clay + Diamond — Design Spec

**Date:** 2026-04-19  
**Status:** Implemented (2026-04-19)

## Overview

Port the two remaining special-behavior fluid pipes from BC 1.12.2 to NeoForge 1.21.1:

- **Clay Fluid Pipe** — Prefers to route fluid into adjacent non-pipe blocks (machines, tanks) over other pipes.
- **Diamond Fluid Pipe** — Filter GUI: each of 6 output directions can have up to 9 fluid-container filters. Matching fluid → priority; mismatched filters → excluded; no filter → fallback.

Both pipes follow the same structural pattern as the existing Iron/Void/Quartz fluid pipe variants.

---

## Transfer Rates (from BC 1.12 BCTransportConfig, baseFlowRate=10)

| Pipe     | transferPerTick | delay |
|----------|-----------------|-------|
| Clay     | 40 mB/t         | 10    |
| Diamond  | 80 mB/t         | 10    |

---

## Clay Fluid Pipe

### Block: `ClayFluidPipeBlock extends FluidPipeBlock`

Standard fluid pipe block, no GUI. No override of `getConnectionType` needed (same connectivity rules as base `FluidPipeBlock`).

### BlockEntity: `ClayFluidPipeBE extends FluidPipeBE`

Overrides `applyMaterialProperties()` to set `transferPerTick = 40, delay = 10`.

Routing hook: override `selectOutputDirections(List<Direction> candidates)` in `FluidPipeBE`. Clay checks each candidate — if the neighbor block at that direction is **not** a `PipeBlock`, it's a "machine neighbor". Return only machine neighbors if any exist; otherwise return the full candidate list (so fluid doesn't deadlock in an all-pipe network).

### Hook to add in `FluidPipeBE.moveFromCenter()`

```java
// After building outputDirs, before Collections.shuffle:
outputDirs = selectOutputDirections(outputDirs);
```

```java
// Base implementation (no-op):
protected List<Direction> selectOutputDirections(List<Direction> candidates) {
    return candidates;
}
```

### Registry entries

- Block: `BCBlocks.CLAY_FLUID_PIPE`
- BlockEntity: `BCBlockEntities.CLAY_FLUID_PIPE` (uses `collectBlocksExact` pattern)
- Block: `ClayFluidPipeBlock` (extends `FluidPipeBlock`)

---

## Diamond Fluid Pipe

### Block: `DiamondFluidPipeBlock extends FluidPipeBlock`

Right-click opens the filter GUI (same pattern as `DiamondItemPipeBlock`). On block removal: drops the carried item in the pipe (already handled by BE loot).

### BlockEntity: `DiamondFluidPipeBE extends FluidPipeBE`

**Material properties:** `transferPerTick = 80, delay = 10`.

**Filter storage:** `ItemStackHandler filterHandler` with 54 ghost slots (6 directions × 9 slots per direction). Slot layout: `base = dir.get3DDataValue() * 9`. No stack limit per slot (ghost slots: size 1 copies).

**Fluid extraction from filter slots:** `FluidUtil.getFluidContained(ItemStack filterItem)` — works for buckets, fluid bottles, etc.

**Routing override:** `selectOutputDirections(List<Direction> candidates)` applies BC 1.12 `PipeBehaviourDiamondFluid.sideCheck` logic:

```
For each candidate direction:
  - Collect all non-empty filter slots for that direction.
  - If no filters → FALLBACK group.
  - If filters exist AND at least one fluid matches currentFluid → PRIORITY group.
  - If filters exist AND none match → EXCLUDED (dropped from candidates).

Return: PRIORITY group if non-empty, else FALLBACK group, else all candidates (deadlock guard).
```

Fluid matching: `FluidStack.isSameFluid(filterFluid, currentFluid)` (type only, ignores amount/components — consistent with item pipe `isSameItem` behavior).

**NBT:** Save/load `filterHandler` as `"filters"` compound tag (same as `DiamondItemPipeBE`).

**Sync:** Filter slots are synced via `getUpdateTag`/`handleUpdateTag` (same pattern as `DiamondItemPipeBE`).

### Menu: `DiamondFluidPipeMenu extends AbstractContainerMenu`

Identical layout to `DiamondPipeMenu` but typed to `DiamondFluidPipeBE`:

- 54 ghost filter slots (6 rows × 9 cols), same pixel positions
- Player inventory (3×9) at y=140
- Hotbar at y=198
- Ghost slot click behavior: place copy of carried (count 1); empty-handed → clear slot
- `quickMoveStack`: filter slots excluded, player inv ↔ hotbar only

### Screen: `DiamondFluidPipeScreen extends AbstractContainerScreen<DiamondFluidPipeMenu>`

Reuses the same GUI texture as `DiamondPipeScreen` (`textures/gui/diamond_pipe_gui.png`). Same dimensions (176×222). Title label at y=6; player inventory label at `imageHeight - 94`.

### Menu registration

Add `BCMenuTypes.DIAMOND_FLUID_PIPE` in `BCMenuTypes`.  
Register screen: `event.register(BCMenuTypes.DIAMOND_FLUID_PIPE.get(), DiamondFluidPipeScreen::new)`.

---

## Textures

Copy from BC 1.12 `buildcraft_resources/assets/buildcrafttransport/textures/pipes/`:

- `clay_fluid.png` → `assets/buildcraft/textures/block/clay_fluid_pipe.png`
- `diamond_fluid.png` → `assets/buildcraft/textures/block/diamond_fluid_pipe.png`

No additional `_blocked` or `_extracting` variants needed for these pipes.

---

## Datagen

### Blockstates + Models (`BCBlockStateProvider`)

Both pipes: standard fluid pipe pattern (same as cobblestone, stone, etc.) — one `FluidPipeBlockStateProvider` call per pipe.

### Item Models (`BCItemModelProvider`)

Same `pipeItem()` call pattern.

### Lang (`BCEnUSLangProvider`)

- `"block.buildcraft.clay_fluid_pipe"` → `"Clay Fluid Pipe"`
- `"block.buildcraft.diamond_fluid_pipe"` → `"Diamond Fluid Pipe"`

### Recipes (`BCRecipeProvider`)

- `clay_fluid_pipe`: `fluidPipeRecipe(out, "clay")` — requires `clay_pipe` + slimeball → 1 (follows existing `fluidPipeRecipe` helper)
- `diamond_fluid_pipe`: `fluidPipeRecipe(out, "diamond")` — requires `diamond_fluid_pipe` + slimeball → 1

Note: this requires `clay_pipe` and `diamond_pipe` items to exist already (they do — BCPipes defines them).

### Loot Tables (`BCBlockLootTableProvider`)

Standard self-drop for both pipes.

---

## Files to Create

| File | Purpose |
|------|---------|
| `content/blocks/ClayFluidPipeBlock.java` | Block class |
| `content/blockentities/ClayFluidPipeBE.java` | BlockEntity |
| `content/blocks/DiamondFluidPipeBlock.java` | Block class with GUI open |
| `content/blockentities/DiamondFluidPipeBE.java` | BlockEntity with filter |
| `content/menus/DiamondFluidPipeMenu.java` | Container menu |
| `client/screens/DiamondFluidPipeScreen.java` | GUI screen |

## Files to Modify

| File | Change |
|------|--------|
| `content/blockentities/FluidPipeBE.java` | Add `selectOutputDirections` hook + call in `moveFromCenter` |
| `registries/BCBlocks.java` | Register `CLAY_FLUID_PIPE`, `DIAMOND_FLUID_PIPE` blocks |
| `registries/BCBlockEntities.java` | Register `CLAY_FLUID_PIPE`, `DIAMOND_FLUID_PIPE` BEs |
| `registries/BCMenuTypes.java` | Register `DIAMOND_FLUID_PIPE` menu |
| `BuildcraftLegacy.java` | Register caps + screen |
| `datagen/data/BCRecipeProvider.java` | Add `clay`/`diamond` fluid pipe recipes |
| `datagen/data/BCBlockLootTableProvider.java` | Add loot entries |
| `datagen/assets/BCBlockStateProvider.java` | Add blockstate/model entries |
| `datagen/assets/BCItemModelProvider.java` | Add item model entries |
| `datagen/assets/BCEnUSLangProvider.java` | Add lang entries |
| Texture assets | Copy 2 PNGs from BC 1.12 |
