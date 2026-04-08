# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Context

This is a continuation of **Buildcraft-Legacy** (https://modrinth.com/mod/legacy-buildcraft), a community port of the original BuildCraft mod (Forge 1.12.2) to **NeoForge 1.21.1**. We are continuing and extending the work of the original Buildcraft-Legacy author. The project is maintained by two developers for private server use.

Original BuildCraft source (1.12.2) for reference: https://github.com/BuildCraft/BuildCraft

- **Mod ID**: `buildcraft`
- **Root package**: `com.thepigcat.buildcraft`
- **Key dependency**: [PortingDeadLibs (PDL)](https://github.com/PortingDeadMods/PortingDeadLibs) — provides `ContainerBlockEntity`, `DynamicFluidTank`, `PDLFluid`/fluid helpers, and `@ConfigValue` config annotations.

## Build Commands

```bash
# Build the mod jar
./gradlew build

# Run Minecraft client with mod loaded (for testing)
./gradlew runClient

# Run dedicated server with mod loaded
./gradlew runServer

# Regenerate data (recipes, loot tables, tags, lang files)
./gradlew runData

# Run tests
./gradlew test

# Clean build artifacts
./gradlew clean
```

The built jar ends up in `build/libs/`.

## NeoForge 1.21.1 Key Patterns

**Registries** — always use `DeferredRegister`, never `GameRegistry`:
```java
public static final DeferredRegister<Block> BLOCKS =
    DeferredRegister.create(BuiltInRegistries.BLOCK, MODID);
```

**Capabilities** — NeoForge 1.21.1 registers capabilities via `RegisterCapabilitiesEvent` (NOT the old `AttachCapabilitiesEvent`):
```java
event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BCBlockEntities.CRATE.get(), CrateBE::getItemHandler);
```
All capability registrations live in `BuildcraftLegacy.attachCaps()`.

**BlockCapabilityCache** — used for efficient repeated capability lookups (e.g., pipe-to-pipe, engine output). Created in `onLoad()`, stored as fields, queried each tick via `.getCapability()`.

**Networking** — payloads implement `CustomPacketPayload`, registered in `BuildcraftLegacy.registerPayloads()`. Send with `PacketDistributor.sendToAllPlayers(new MyPayload(...))`.

**Rendering** — `BlockEntityRenderer<T>` with `PoseStack` + `MultiBufferSource`. No direct OpenGL.

**Events** — `@EventBusSubscriber(modid = MODID)` defaults to `Bus.GAME`. Use `Bus.MOD` for mod lifecycle events.

**Config** — use PDL's `@ConfigValue` annotation on static fields in `BCConfig`. No raw `ModConfigSpec` needed.

**ResourceLocation** — always use `ResourceLocation.fromNamespaceAndPath("mod", "path")`, never the old two-arg constructor. `BuildcraftLegacy.rl(path)` is a shorthand for the `buildcraft` namespace.

## Architecture Overview

### Package Structure

- `api/` — abstract base classes: `PipeBlockEntity<CAP>`, `EngineBlockEntity`, `EngineBlock`, `PipeBlock`, pipe data model (`Pipe` record, `PipeType`, `PipeHolder`)
- `content/` — concrete block/blockentity/menu/item implementations
- `registries/` — all `DeferredRegister` declarations (`BCBlocks`, `BCItems`, `BCBlockEntities`, `BCFluids`, `BCPipeTypes`, `BCPipes`, etc.)
- `client/` — renderers, screens, item renderers, models
- `networking/` — payload types for client/server sync
- `datagen/` — data generators for recipes, loot tables, lang, blockstates, item models
- `util/` — helpers (`BlockUtils`, `CapabilityUtils`, `ModelUtils`, `PipeRegistrationHelper`)
- `events/` — game-bus event handlers (`CommonEvents`)
- `mixins/` — Mixin patches

### ContainerBlockEntity (PDL)

The base class for all machine block entities (engines, tank, crate). Provides a builder-style API in the constructor:
```java
addEnergyStorage(HandlerUtils::newEnergystorage, builder -> builder.capacity(...).maxTransfer(...));
addFluidHandler(HandlerUtils::newDynamicFluidTank, builder -> builder.slotLimit($ -> BCConfig.tankCapacity));
```
Sided capability access is handled automatically by PDL via `getItemHandlerOnSide()`, `getFluidHandlerOnSide()`, `getEnergyStorageOnSide()`. Use `loadData`/`saveData` (not `loadAdditional`/`saveAdditional`) for NBT in ContainerBlockEntity subclasses.

### Pipes — Data-Driven System

Pipes have two layers:

1. **`PipeType`** (`BCPipeTypes`) — defines the Block class, BlockItem class, and model generation strategy. Currently `DEFAULT` (plain transport, `ItemPipeBlock`) and `EXTRACTING` (pulls from adjacent inventories, `ExtractingItemPipeBlock`).

2. **`Pipe` record** (`BCPipes`) — a specific pipe variant: transfer speed, textures, material properties, recipe ingredient, etc. Encoded/decoded via Codec to JSON.

At startup, `PipesRegistry` writes default pipe JSONs to `config/buildcraft/pipes/` (if not present), then reads them back. This means pipes are **user-configurable** without code changes. Pipe blocks and items are registered dynamically during `RegisterEvent`, not via `DeferredRegister`.

To add a new pipe: add a `PipeHolder` entry in `BCPipes`, add a texture, and run `runData` for blockstates/models/recipes.

### Pipe Block Entity (`PipeBlockEntity<CAP>`)

Abstract base for all pipe BEs. On `onLoad()` (server-side only), creates `BlockCapabilityCache` entries for all 6 directions. Stores connected `directions` (a `Set<Direction>`) updated by `PipeBlock.setPipeProperties()` when neighbors change.

`ItemPipeBE` extends this: holds a single-slot `ItemStackHandler`, tracks `from`/`to` directions, animates `movement` (0→1 float). When movement hits 1, tries to insert into the next pipe or adjacent inventory. Syncs direction + movement to clients via payloads.

### Engines

Three engines, all extending `EngineBlockEntity` → `ContainerBlockEntity`:
- `RedstoneEngineBE` — low power, no fuel
- `StirlingEngineBE` — burns solid fuel (has inventory)
- `CombustionEngineBE` — burns fluid fuel (has fluid tank + inventory)

`EngineBlockEntity` exports energy each tick via a `BlockCapabilityCache` pointing in the engine's `FACING` direction. Piston animation is driven by `movement`/`lastMovement` floats, synced via the standard block update packet.

### Tank

`TankBE` supports vertical multi-block stacking. The bottom tank in a column is the "master" — it holds the combined `DynamicFluidTank` with capacity scaled by `tanks * BCConfig.tankCapacity`. All tanks in the column delegate `getFluidHandler()` to the bottom tank via `bottomTankPos`. Fluid is preserved in the item stack via `BCDataComponents.TANK_CONTENT`.

### Kinesis Pipes (Energy Transport)

`KinesisPipeBE` extends `PipeBlockEntity<IEnergyStorage>`. Passiv wie Original BC — Engines pushen Energie rein, die Pipe zieht nicht aktiv. Verteilt Energie gleichmäßig an verbundene Nachbarn mit materialabhängigem Verlust (Gold=0%, Cobblestone=10%). 6 Materialien: Wooden, Cobblestone, Stone, Gold, Quartz, Diamond. TESR-Rendering mit scrollendem `power_flow.png` Sprite.

### Fluid Pipes (Fluid Transport)

`FluidPipeBE` extends `PipeBlockEntity<IFluidHandler>`. Section-basiertes System (7 Sections: 6 faces + CENTER) wie Original BC `PipeFlowFluids`. 3-Phasen Tick: moveFromPipe → moveFromCenter → moveToCenter. Direction Cooldowns verhindern Oszillation. 4 Materialien: Wooden (extracting), Cobblestone, Stone, Gold.

`ExtractingFluidPipeBE` nutzt proportionale Extraktion: `mB = FE × fluidExtractionRate` (default 5). Separate `BlockEntityType` (EXTRACTING_FLUID_PIPE) notwendig.

`currentFluid` ist ein Typ-Marker mit `amount=1` (nicht 0, da `isEmpty()` sonst true zurückgibt und tick() blockiert).

### Currently Implemented

- Item pipes (wooden, cobblestone, stone, sandstone, quartz, gold, iron, obsidian, clay, emerald, diamond, lapis, daizuli, stripe, void)
- Kinesis pipes (wooden, cobblestone, stone, gold, quartz, diamond)
- Fluid pipes (wooden, cobblestone, stone, gold)
- Engines (redstone, stirling, combustion)
- Fluid tank (stackable, configurable capacity)
- Crate (large item storage, configurable capacity, face-based interaction)
- Oil fluid
- Quarry

### Not Yet Ported

Builder, Filler, Gates/Triggers/Actions, Facades — these exist in the original 1.12 source and are candidates for future porting.

## Migration Notes (1.12 → 1.21.1)

When porting remaining unported code:

1. `TileEntity` → `BlockEntity`, `update()` → `tick()` via `BlockEntityTicker`
2. `IFluidHandler` usages → NeoForge 1.21.1 fluid capability API; register via `RegisterCapabilitiesEvent`
3. `ItemStack.getItem() == Items.X` → `is(Items.X)` or tags
4. `World` → `Level`, `IBlockState` → `BlockState`, `EnumFacing` → `Direction`
5. `NBTTagCompound` → `CompoundTag`, `NBTTagList` → `ListTag`
6. Machine block entities: extend `ContainerBlockEntity` (PDL) instead of plain `BlockEntity`
7. Config fields: use `@ConfigValue` in `BCConfig` instead of `ModConfigSpec`

## AI Workflow

Du (Claude Code) bist der **Hauptentwickler** in diesem Projekt. Du machst Architektur, Logik, Bug-Fixing, Planung und Code-Reviews.

Weitere Tools (optional, vom Entwickler nach Bedarf eingesetzt):
- **OpenCode** + **Ollama** (Qwen2.5-Coder 14B, lokal) — Fleißarbeit, Boilerplate, Routine-Portierungen
- **Gemini CLI** — 1M-Token-Kontext für große Datei-Analysen, nur Flash (Free Tier)

Der Entwickler (Fabi) koordiniert direkt — kein AI-Orchestrator.

### Superpowers Skills

Nutze die Superpowers-Skills (brainstorming, writing-plans, systematic-debugging, subagent-driven-development, etc.) proaktiv wenn sie zur Aufgabe passen:
- **Neues Feature planen** → brainstorming → writing-plans
- **Bug fixen** → systematic-debugging
- **Größere Implementierung** → subagent-driven-development
- **Code Review** → requesting-code-review
