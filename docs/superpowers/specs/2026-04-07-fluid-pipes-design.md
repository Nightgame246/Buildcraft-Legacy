# Fluid Pipes Design Spec

**Date:** 2026-04-07
**Status:** Completed (2026-04-07)
**Scope:** Port of BuildCraft 1.12 fluid pipe system to NeoForge 1.21.1

## Overview

Section-based fluid transport pipes, faithful to the original BuildCraft 1.12 `PipeFlowFluids` system. Each pipe has 7 internal sections (6 faces + center) that fluid physically moves through. TESR rendering shows the fluid inside pipes.

### Materials (Phase 1)

| Material    | transferPerTick | delay | capacity | Besonderheit             |
|-------------|-----------------|-------|----------|--------------------------|
| Wooden      | 10 mB/t         | 10    | 100 mB   | Extracting (energy-based) |
| Cobblestone | 10 mB/t         | 10    | 100 mB   | Basis                    |
| Stone       | 20 mB/t         | 10    | 200 mB   | Doppelte Rate            |
| Gold        | 80 mB/t         | 2     | 800 mB   | Schnellste Rate + Delay  |

`capacity = transferPerTick * 10` (matches original BC formula)

## Architecture

### Class Hierarchy

```
PipeBlockEntity<IFluidHandler>
  └── FluidPipeBE
        └── ExtractingFluidPipeBE

PipeBlock
  └── FluidPipeBlock
        └── ExtractingFluidPipeBlock
```

### FluidPipeBE — Core Fluid Transport

**Location:** `content/blockentities/FluidPipeBE.java`

**Section Model (from original PipeFlowFluids):**
- 7 `Section` instances: one per `Direction` (6) + one CENTER
- Each section stores:
  - `amount` (int) — current fluid in mB
  - `incoming[]` (int array, length = delay) — delayed fluid arrivals per tick slot
  - `incomingTotalCache` (int) — sum of incoming array for fast access
  - `ticksInDirection` (int) — cooldown determining flow direction (negative = IN, positive = OUT, 0 = NONE)
  - `currentTime` (int) — rotating index into incoming array

**Single Fluid Constraint:** One `currentFluid` (FluidStack) per pipe. If pipe is empty, any fluid can enter. Once set, only matching fluid is accepted. Resets to null when all sections reach 0.

**Tick Logic (server-side, 3 phases per tick):**

1. **`moveFromPipe()`** — For each face section with OUT direction:
   - Drain up to `transferPerTick` from the section
   - Push into neighbor's IFluidHandler via capability cache
   - Set cooldown to COOLDOWN_OUTPUT on success

2. **`moveFromCenter()`** — Center section distributes to output-facing sides:
   - Collect all sides with OUT direction and available space
   - Split available fluid evenly (randomized order to prevent bias)
   - Fill side sections, drain center
   - Set cooldowns

3. **`moveToCenter()`** — Input-facing side sections feed center:
   - Collect all sides with IN direction and drainable fluid
   - Split proportionally based on center space and flow rate
   - Drain sides, fill center
   - Set cooldowns

**Direction Cooldowns:** `DIRECTION_COOLDOWN = 60` ticks. When fluid enters a section from outside, that section gets `ticksInDirection = -60` (INPUT). When fluid leaves to outside, `+60` (OUTPUT). Cooldown ticks toward 0 each tick. This prevents fluid oscillation.

**Material Properties:** Applied in `onLoad()` via `applyMaterialProperties()`, reading the block registry name (same pattern as KinesisPipeBE):
- `transferPerTick` — max mB moved per tick per section
- `delay` — length of the `incoming[]` array (transfer delay in ticks)

**Capability Exposure:** Each face section implements `IFluidHandler` behavior. When a neighbor calls `fill()` on this pipe's face, the corresponding section receives the fluid (subject to direction cooldown and capacity checks).

**NBT Serialization:**
- `currentFluid` — FluidStack compound tag
- Per section (7x): `amount`, `ticksInDirection`, `incoming[]` array

**Client Sync:**
- Custom payload `SyncFluidPipePayload` sent when amounts or directions change
- Contains: FluidStack type + 7x (amount as short, direction as 2-bit enum)
- Rate-limited to avoid network spam (every ~4 ticks, matching original `BCCoreConfig.networkUpdateRate`)

**Client-Side Interpolation:**
- Each section stores `clientAmountThis`, `clientAmountLast`, `target`
- `tickClient()` interpolates amounts smoothly toward server target
- `offsetThis`/`offsetLast` for flow animation offset (UV scrolling direction)

### ExtractingFluidPipeBE — Wooden Fluid Pipe

**Location:** `content/blockentities/ExtractingFluidPipeBE.java`

- Extends `FluidPipeBE`
- Has additional `EnergyStorage` for energy-based extraction (consistent with ExtractItemPipeBE)
- On tick: if `extracting != null`, pulls fluid from adjacent IFluidHandler using energy
- **Proportionale Extraktion (wie Original BC):** `mB = FE × BCConfig.fluidExtractionRate` (default 5 mB/FE)
- Engine-Stärke bestimmt Extraktionsrate (Stirling: 100 mB/t, Combustion: 200 mB/t)
- Kosten: `ceil(extractedMb / fluidExtractionRate)` FE pro Tick
- Nutzt `fillSectionForExtraction()` — bypassed per-tick Transport-Limits

### FluidPipeBlock

**Location:** `content/blocks/FluidPipeBlock.java`

- Extends `PipeBlock`
- `getConnectionType()` returns CONNECTED for:
  - Other `FluidPipeBlock` instances (with material compatibility — same rules as item pipes)
  - Blocks exposing `Capabilities.FluidHandler.BLOCK`
- Never connects to ItemPipeBlock or KinesisPipeBlock
- Creates `FluidPipeBE` as block entity

### ExtractingFluidPipeBlock

**Location:** `content/blocks/ExtractingFluidPipeBlock.java`

- Extends `ExtractingPipeBlock` (nicht FluidPipeBlock — eigene Vererbungskette)
- Sets `extracting` direction on the BE (face touching a fluid source)
- Rechtsklick cycling der Extraktionsrichtung
- Eigener BlockEntityType: `BCBlockEntities.EXTRACTING_FLUID_PIPE`

### FluidPipeBERenderer

**Location:** `client/blockentities/FluidPipeBERenderer.java`

- `BlockEntityRenderer<FluidPipeBE>`
- Renders fluid texture as 3D quads per section using `PoseStack` + `MultiBufferSource`
- Gets fluid sprite via `IClientFluidTypeExtensions.of(fluidType)` -> `getStillTexture()`
- Gets tint color via `getFluidTintColor()`
- Uses `RenderType.translucent()` for proper fluid blending

**Per-section rendering:**
- **Horizontal connections:** Fluid quader with height proportional to `amount/capacity`
- **Vertical connections:** Fluid quader with radius scaled by `sqrt(amount/capacity)` (matches original BC visual curve)
- **Center section:** Full cube scaled by fill level, with axis preference (horizontal if any horizontal connection has fluid, else vertical column)

**Interpolation:** Uses `getAmountsForRender(partialTicks)` with `clientAmountLast`/`clientAmountThis` lerp for smooth visual transitions.

## Registration

### BCPipeTypes additions

```java
FLUID_DEFAULT = registerPipeType("fluid_default", FluidPipeBlock::new, ...)
FLUID_EXTRACTING = registerPipeType("fluid_extracting", ExtractingFluidPipeBlock::new, ...)
```

### BCPipes additions

```java
WOODEN_FLUID = registerPipe("wooden_fluid", FLUID_EXTRACTING, "Wooden Fluid Pipe", 0f, ...)
COBBLESTONE_FLUID = registerPipe("cobblestone_fluid", FLUID_DEFAULT, "Cobblestone Fluid Pipe", 0f, ...)
STONE_FLUID = registerPipe("stone_fluid", FLUID_DEFAULT, "Stone Fluid Pipe", 0f, ...)
GOLD_FLUID = registerPipe("gold_fluid", FLUID_DEFAULT, "Gold Fluid Pipe", 0f, ...)
```

Note: `transferSpeed` in Pipe record is 0f for fluid pipes — actual transfer rates are determined by `applyMaterialProperties()` in the BE, not the Pipe record (same pattern as Kinesis pipes).

### BCBlockEntities additions

```java
FLUID_PIPE = register("fluid_pipe",
    () -> BlockEntityType.Builder.of(FluidPipeBE::new,
        collectBlocks(FluidPipeBlock.class)).build(null));
EXTRACTING_FLUID_PIPE = register("extracting_fluid_pipe",
    () -> BlockEntityType.Builder.of(ExtractingFluidPipeBE::new,
        collectBlocks(ExtractingFluidPipeBlock.class)).build(null));
```

**Wichtig:** Getrennte BE-Typen! Sonst wird `ExtractingFluidPipeBE` nie instanziiert.

### Capability Registration (BuildcraftLegacy.attachCaps)

```java
event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
    BCBlockEntities.FLUID_PIPE.get(), FluidPipeBE::getFluidHandler);
event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
    BCBlockEntities.EXTRACTING_FLUID_PIPE.get(), FluidPipeBE::getFluidHandler);
event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
    BCBlockEntities.EXTRACTING_FLUID_PIPE.get(), ExtractingFluidPipeBE::getEnergyStorage);
```

### Networking

`SyncFluidPipePayload` registered in `BuildcraftLegacy.registerPayloads()`:
- Fields: `BlockPos pos`, `Optional<FluidStack> fluid`, `int[] amounts` (7), `int[] directions` (7)
- Sent server -> all clients when fluid state changes

### Renderer Registration

In client event handler:
```java
event.registerBlockEntityRenderer(BCBlockEntities.FLUID_PIPE.get(), FluidPipeBERenderer::new);
```

## Textures

5 PNG files needed:
- `wooden_fluid_pipe.png` — base texture
- `wooden_fluid_pipe_extracting.png` — extracting overlay
- `cobblestone_fluid_pipe.png` — base texture
- `stone_fluid_pipe.png` — base texture
- `gold_fluid_pipe.png` — base texture

Location: `src/main/resources/assets/buildcraft/textures/block/`

## Datagen

- Blockstates: multipart definitions (base + connections per direction)
- Block models: base + connection models (reuse DEFAULT/EXTRACTING model templates)
- Item models: standard pipe item model
- Loot tables: drop self
- Recipes: Pipe Sealant + Material (standard pipe crafting pattern)
- Tags: mineable/pickaxe (or axe for wooden)
- Lang entries: en_us + de_de

## Config

New BCConfig fields:
- `fluidExtractionRate` (default: 5) — mB extrahiert pro FE (proportional wie Original BC)
- Reuse existing `extractionPipeEnergyCapacity` für Energiepuffer

## Bekannte Fixes während Implementierung

1. **`copyWithAmount(0)` Bug:** `currentFluid` als Typ-Marker muss amount > 0 haben, sonst gibt `isEmpty()` true zurück → tick() kehrt sofort zurück, keine Bewegung/Sync/Rendering
2. **Getrennte BE-Typen:** `FLUID_PIPE` und `EXTRACTING_FLUID_PIPE` brauchen separate `BlockEntityType`s, sonst wird `ExtractingFluidPipeBE` nie instanziiert
3. **ItemPipeBlock Cross-Connection:** `isItemPipe()` muss FluidPipeBlock und KinesisPipeBlock explizit ausschließen
4. **Original-Texturen:** Aus BuildCraft 1.12 übernommen statt Platzhalter

## Future Extensions (not in this scope)

- Sandstone Fluid Pipe (no machine connection)
- Iron Fluid Pipe (directional output)
- Quartz Fluid Pipe (high throughput)
- Diamond Fluid Pipe (highest throughput)
- Void Fluid Pipe (destroys fluid)
- Clay Fluid Pipe (nearest-first routing)
