# Fluid Pipe Materials — Phase 1 Design

**Date:** 2026-04-18  
**Scope:** Sandstone, Quartz, Void, Iron fluid pipes  
**Phase 2 (separate):** Clay, Diamond fluid pipes

---

## Overview

Add 4 missing fluid pipe materials to reach parity with original BC 1.12.  
Pattern: mirrors the item pipe architecture exactly (each special pipe = own subclass + BCPipeType + BlockEntityType).

---

## Architecture

### Änderungsmatrix

| | Sandstone | Quartz | Void | Iron |
|---|---|---|---|---|
| `BCPipes` Eintrag | ✓ | ✓ | ✓ | ✓ |
| `BCPipeTypes` | FLUID_DEFAULT | FLUID_DEFAULT | FLUID_VOID (neu) | FLUID_IRON (neu) |
| `BCBlockEntities` | — | — | VOID_FLUID_PIPE (neu) | IRON_FLUID_PIPE (neu) |
| Neues BE | — | — | `VoidFluidPipeBE` | `IronFluidPipeBE` |
| `applyMaterialProperties()` | ✓ Speed | ✓ Speed | — | — |
| Neue Block-Klasse | — | — | — | `IronFluidPipeBlock` |
| Texturen | 1 | 1 | 1 | 2 (clear/filled) |
| Rezept | ✓ | ✓ | ✓ | ✓ |

---

## Per-Pipe Verhalten

### Sandstone Fluid Pipe
- **PipeType:** `FLUID_DEFAULT` (kein neues BE)
- **Speed:** 10 mB/tick, delay 10 (identisch Cobblestone)
- **Einzige Änderung:** `applyMaterialProperties()` in `FluidPipeBE` erkennt `sandstone` explizit damit es nicht in den `wooden`-Fallback fällt
- **Textur:** `sandstone_fluid_pipe.png` (Original BC: sandstone-farbiger Pipe-Kern)
- **Rezept:** Sandstone + Glass Panes (wie andere Pipes)

### Quartz Fluid Pipe
- **PipeType:** `FLUID_DEFAULT` (kein neues BE)
- **Speed:** 40 mB/tick, delay 5 (zwischen Stone 20 und Gold 80)
- **Kapazität:** `Math.max(1000, 40 * 10)` = 1000 mB (durch `Math.max` Floor)
- **`applyMaterialProperties()`:** `pipeId.contains("quartz") && pipeId.contains("fluid")` → `transferPerTick = 40, delay = 5`
- **Textur:** `quartz_fluid_pipe.png`
- **Rezept:** Nether Quartz + Glass Panes

### Void Fluid Pipe
- **PipeType:** `FLUID_VOID` (neu, `FluidPipeBlock::new`)
- **BE:** `VoidFluidPipeBE extends FluidPipeBE`
- **Verhalten:** Passiv — vernichtet Fluid das in die Pipe fließt
  - Überschreibt `moveToCenter()`: alle Face-Sections werden gedrained ohne ins Center zu füllen → Fluid vernichtet
  - Überschreibt `moveFromPipe()`: kein Output nach außen (do nothing) — Void gibt kein Fluid aus
  - Center bleibt immer leer → `moveFromCenter()` ist naturally a no-op, kein Override nötig
- **Wichtig:** Void Pipe akzeptiert Fluid-Connections (Pipes können sich verbinden), zieht aber nichts aktiv an
- **Textur:** `void_fluid_pipe.png` (Original BC: dunkle Textur)
- **Rezept:** Obsidian + Glass Panes

### Iron Fluid Pipe
- **PipeType:** `FLUID_IRON` (neu, `IronFluidPipeBlock::new`)
- **BE:** `IronFluidPipeBE extends FluidPipeBE`
- **Block:** `IronFluidPipeBlock extends FluidPipeBlock` — benötigt eigene Block-Klasse für Wrench-Interaktion (`useWithoutItem` / `useItemOn`)
- **Verhalten:** Einweg-Output — nur eine Direction kann Fluid ausgeben
  - `lockedDirection`: die aktive Output-Direction (NBT-gespeichert)
  - Überschreibt `moveFromCenter()`: pusht Fluid nur in `lockedDirection`-Section
  - Fluid kann nicht von `lockedDirection` ins Pipe eintreten (überschreibt Section-Fill-Check oder `moveToCenter()` filtert `lockedDirection`)
  - Wrench-Rotation: rotiert `lockedDirection` durch alle verbundenen Directions (wie `IronItemPipeBE.rotateLockedDirection()`)
- **Blockstate:** BLOCKED-State für alle Directions außer `lockedDirection` (identisch Iron Item Pipe)
- **Texturen:** 
  - `iron_fluid_pipe.png` — Output-Face (clear/offen)
  - `iron_fluid_pipe_blocked.png` — alle anderen Faces (gefüllt/geschlossen)
- **Rezept:** Iron Ingots + Glass Panes

---

## Registrierungen

### BCPipeTypes (Ergänzungen)
```java
public static final PipeTypeHolder<FluidPipeBlock, ItemPipeBlockItem> FLUID_VOID =
    HELPER.registerPipeType("fluid_void", FluidPipeBlock::new, ItemPipeBlockItem::new,
        ModelUtils.DEFAULT_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE,
        ModelUtils.DEFAULT_ITEM_MODEL_FILE, "base", "connection");

public static final PipeTypeHolder<IronFluidPipeBlock, ItemPipeBlockItem> FLUID_IRON =
    HELPER.registerPipeType("fluid_iron", IronFluidPipeBlock::new, ItemPipeBlockItem::new,
        ModelUtils.IRON_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE,
        ModelUtils.DEFAULT_ITEM_MODEL_FILE, "base", "connection", "connection_blocked");
```

### BCBlockEntities (Ergänzungen)
```java
public static final Supplier<BlockEntityType<VoidFluidPipeBE>> VOID_FLUID_PIPE = ...;
public static final Supplier<BlockEntityType<IronFluidPipeBE>> IRON_FLUID_PIPE = ...;
```

### BCPipes (Ergänzungen, nach GOLD_FLUID)
```java
public static final PipeHolder SANDSTONE_FLUID = HELPER.registerPipe(
    "sandstone_fluid", BCPipeTypes.FLUID_DEFAULT, ..., Ingredient.of(Blocks.SANDSTONE), sort: 34);
public static final PipeHolder QUARTZ_FLUID = HELPER.registerPipe(
    "quartz_fluid", BCPipeTypes.FLUID_DEFAULT, ..., Ingredient.of(Tags.Items.GEMS_QUARTZ), sort: 35);
public static final PipeHolder VOID_FLUID = HELPER.registerPipe(
    "void_fluid", BCPipeTypes.FLUID_VOID, ..., Ingredient.of(Tags.Items.OBSIDIANS), sort: 36);
public static final PipeHolder IRON_FLUID = HELPER.registerPipe(
    "iron_fluid", BCPipeTypes.FLUID_IRON, ..., Ingredient.of(Tags.Items.INGOTS_IRON), sort: 37);
```

### Capability-Registrierung (BuildcraftLegacy.attachCaps)
```java
event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
    BCBlockEntities.VOID_FLUID_PIPE.get(), FluidPipeBE::getFluidHandler);
event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
    BCBlockEntities.IRON_FLUID_PIPE.get(), IronFluidPipeBE::getFluidHandler);
```

---

## FluidPipeBE.applyMaterialProperties() — Neue Cases

```java
} else if (pipeId.contains("quartz") && pipeId.contains("fluid")) {
    transferPerTick = 40;
    delay = 5;
} else if (pipeId.contains("sandstone")) {
    transferPerTick = 10;
    delay = 10;
}
```

`quartz_fluid_pipe` enthält kein "stone", daher greift der bestehende `stone`-Check nicht. Quartz-Case kann an beliebiger Position stehen, aber vor dem `else`-Fallback. Sandstone-Case muss NACH dem `stone`-Check stehen (der bereits `!sandstone` ausschließt) — oder an beliebiger Position da "sandstone" bereits durch den `stone`-Check ausgeschlossen wird.

---

## Datagen

- **Blockstates/Models:** `runData` generiert automatisch über `PipeRegistrationHelper`
- **Rezepte:** Shaped Crafting, je nach Material (Standard Pipe-Crafting-Muster des Projekts)
- **Lang:** `en_us.json` Einträge für alle 4 Pipes
- **Loot Tables:** Standard Drop (Item = Block)

---

## Neue Dateien (Übersicht)

```
content/blocks/IronFluidPipeBlock.java
content/blockentities/VoidFluidPipeBE.java
content/blockentities/IronFluidPipeBE.java
assets/buildcraft/textures/block/sandstone_fluid_pipe.png
assets/buildcraft/textures/block/quartz_fluid_pipe.png
assets/buildcraft/textures/block/void_fluid_pipe.png
assets/buildcraft/textures/block/iron_fluid_pipe.png
assets/buildcraft/textures/block/iron_fluid_pipe_blocked.png
```

---

## Out of Scope (Phase 2)

- Clay Fluid Pipe (bevorzugt Inventare)
- Diamond Fluid Pipe (Filter-GUI per Face)
