# Assembly Table + Laser + Chipsets — Design Spec (Phase E-1)

**Goal:** Port the BC 1.12 Assembly Table, Laser, and Redstone Chipsets to NeoForge 1.21.1 as the first sub-phase of Phase E (Silicon).

**Architecture:** Java-based recipe registry (no JSON data packs), laser pushes FE directly to Assembly Tables via `ILaserTarget` interface, Assembly Table tracks recipe states in a server-side map synced to the client GUI.

**Tech Stack:** NeoForge 1.21.1, PDL `ContainerBlockEntity`, `IEnergyStorage` capability, `AbstractContainerMenu` / `Screen`

---

## 1. Items — Chipsets

Five separate item registrations in `BCItems`:

| Registry name       | In-game name         |
|---------------------|----------------------|
| `red_chipset`       | Redstone Chipset     |
| `iron_chipset`      | Iron Chipset         |
| `gold_chipset`      | Gold Chipset         |
| `quartz_chipset`    | Quartz Chipset       |
| `diamond_chipset`   | Diamond Chipset      |

Plain `Item` subclass (`ChipsetItem`), no special behaviour. Textures ported from original BC assets.

---

## 2. Recipe System

**`AssemblyRecipe` record:**
```java
record AssemblyRecipe(ResourceLocation id, Set<Ingredient> inputs, ItemStack output, int feCost)
```

**`AssemblyRecipeRegistry`** — static `Map<ResourceLocation, AssemblyRecipe>`. Populated once during mod init via `BCRecipes.registerAssemblyRecipes()`.

**Chipset recipes:**

| ID                | Inputs                    | FE Cost |
|-------------------|---------------------------|---------|
| `red_chipset`     | Redstone dust             | 10 000  |
| `iron_chipset`    | Redstone + Iron Ingot     | 20 000  |
| `gold_chipset`    | Redstone + Gold Ingot     | 40 000  |
| `quartz_chipset`  | Redstone + Nether Quartz  | 60 000  |
| `diamond_chipset` | Redstone + Diamond        | 80 000  |

All values configurable via `BCConfig` (`@ConfigValue`).

---

## 3. Laser

### LaserBlock
- Extends `Block`, implements `EntityBlock`
- BlockState property: `FACING` (all 6 `Direction` values), default `NORTH`
- Placed via right-click face; `FACING` set to the face the player clicked

### LaserBE (`ContainerBlockEntity`)
- Internal `EnergyStorage`: capacity 4 000 FE, max receive 200 FE/tick (accepts from Kinesis Pipes)
- `BlockPos targetPos` — current target Assembly Table (nullable)
- `List<BlockPos> candidatePositions` — tables in range

**Tick logic (server):**
1. Every 10 ticks (or when `worldUpdated` flag set): scan blocks in `FACING` direction cone, range 8 blocks — collect positions where BE `instanceof AssemblyTableBE` → `candidatePositions`
2. If current target no longer needs power → pick a new random target from `candidatePositions` that has `getRequiredLaserPower() > 0`
3. Extract up to `min(40, battery.stored)` FE from own battery → call `target.receiveLaserPower(fe)`
4. If `targetPos` changed → call `level.sendBlockUpdated(...)` to sync client

**Sync:** `getUpdateTag` encodes `targetPos` (nullable), `handleUpdateTag` decodes on client.

### ILaserTarget (interface)
```java
public interface ILaserTarget {
    int getRequiredLaserPower();   // FE still needed to complete active recipe
    void receiveLaserPower(int fe); // called by laser each tick
}
```
Implemented by `AssemblyTableBE`.

### LaserBERenderer (TESR)
- Registered for `LaserBE`
- If `targetPos == null` → skip
- Draws a line quad (4 vertices forming a thin box) from laser block center to target block center
- Animated: `width = 0.04 + 0.08 * |sin(gameTime * 0.1)|`
- Color: green (`0x00FF00`) → yellow (`0xFFFF00`) based on `sin(gameTime * 0.07)`
- `RenderType.entityCutoutNoCull` with a simple stripe texture (reuse `power_flow_heat.png` or plain white)
- Full bright (`LightTexture.FULL_BRIGHT`)

---

## 4. Assembly Table

### AssemblyTableBlock
- Extends `Block`, implements `EntityBlock`
- No directional property

### EnumAssemblyRecipeState
```java
enum EnumAssemblyRecipeState {
    POSSIBLE,           // items present, not saved by player
    SAVED,              // saved but insufficient items
    SAVED_ENOUGH,       // saved + enough items, waiting for active slot
    SAVED_ENOUGH_ACTIVE // currently receiving power / crafting
}
```

### AssemblyTableBE (`ContainerBlockEntity`, implements `ILaserTarget`)
- 12-slot `ItemStackHandler` inventory (`inv`), accessible from all sides
- `long power` — accumulated FE from lasers
- `SortedMap<AssemblyRecipe, EnumAssemblyRecipeState> recipeStates`

**`updateRecipes()` (called each tick, server):**
1. For every recipe in `AssemblyRecipeRegistry`: check if current inventory can satisfy inputs → if yes and not yet tracked → add as `POSSIBLE`
2. For tracked recipes: re-check ingredient availability → update state between `SAVED`/`SAVED_ENOUGH`/`SAVED_ENOUGH_ACTIVE`
3. Remove `POSSIBLE` entries that no longer have sufficient items
4. Ensure exactly one recipe is `SAVED_ENOUGH_ACTIVE` (the first `SAVED_ENOUGH` if none active)

**`getTarget()` → `int`:** FE cost of the `SAVED_ENOUGH_ACTIVE` recipe, or 0 if none.

**`receiveLaserPower(int fe)`:** `power += fe`

**`getRequiredLaserPower()`:** `max(0, getTarget() - power)`

**Craft logic (server tick, after `updateRecipes`):**
```
if (power >= getTarget() && getTarget() > 0):
    consume inputs from inv
    eject output to adjacent inventory (or drop if none)
    power -= getTarget()
    advance to next SAVED_ENOUGH recipe
    setChanged()
```

**NBT:** saves `power` + `recipeStates` (recipe id + state ordinal per entry).

### AssemblyTableMenu (`AbstractContainerMenu`)
- 12 `Slot` entries for `inv` (offset 0–11)
- Player inventory slots (36 slots)
- `ContainerData` syncs: `power` (int, scaled /100), `target` (int, scaled /100)
- Server→Client recipe state sync: custom payload `AssemblyRecipeStatePayload` sends full `recipeStates` map on open + on change
- Client→Server: `SetRecipeStatePayload(recipeId, newState)` — player toggling SAVED

### AssemblyTableScreen (`AbstractContainerScreen`)
Layout (176×196 px GUI):
- **Left panel (0–107 px wide):** 3×4 item grid (slots 0–11), each slot 18×18 px, top-left at (8, 18)
- **Right panel (108–168 px):** scrollable recipe list; each entry shows output item icon + name; background tint by state (gray=POSSIBLE, yellow=SAVED, green=SAVED_ENOUGH/ACTIVE)
- **Bottom:** horizontal FE bar (8–168 px wide, at y=170), fills left→right, shows `power/target` as tooltip
- Click on recipe entry → send `SetRecipeStatePayload` toggling SAVED
- Active recipe entry has bright green border

---

## 5. Registration & Datagen

**New registrations:**
- `BCItems`: `RED_CHIPSET`, `IRON_CHIPSET`, `GOLD_CHIPSET`, `QUARTZ_CHIPSET`, `DIAMOND_CHIPSET` (plain `Item`), `LASER` BlockItem, `ASSEMBLY_TABLE` BlockItem
- `BCBlocks`: `LASER`, `ASSEMBLY_TABLE`
- `BCBlockEntities`: `LASER`, `ASSEMBLY_TABLE`
- `BCMenuTypes`: `ASSEMBLY_TABLE`
- `BCNetworking`: `AssemblyRecipeStatePayload`, `SetRecipeStatePayload`

**Capabilities (in `BuildcraftLegacy.attachCaps()`):**
- `LaserBE` → `Capabilities.EnergyStorage.BLOCK` (accepts FE from kinesis pipes)

**Datagen:**
- Blockstates/models for Laser (6 rotations) and Assembly Table (static)
- Item models for all 5 chipsets
- Lang entries (en_us)
- Loot tables (drop self)
- Recipes: standard crafting recipes for Laser and Assembly Table blocks (shapeless/shaped)

---

## 6. File Map

| File | Purpose |
|------|---------|
| `content/items/ChipsetItem.java` | Base chipset item class (trivial `Item` subclass, only needed for creative tab grouping) |
| `api/recipes/AssemblyRecipe.java` | Recipe record |
| `api/recipes/AssemblyRecipeRegistry.java` | Static registry |
| `api/blockentities/ILaserTarget.java` | Interface for laser targets |
| `content/blocks/LaserBlock.java` | Laser block (FACING property) |
| `content/blockentities/LaserBE.java` | Laser tile entity |
| `content/blocks/AssemblyTableBlock.java` | Assembly Table block |
| `content/blockentities/AssemblyTableBE.java` | Assembly Table tile entity |
| `content/menus/AssemblyTableMenu.java` | Container menu |
| `client/screens/AssemblyTableScreen.java` | GUI screen |
| `client/blockentities/LaserBERenderer.java` | TESR laser beam |
| `networking/AssemblyRecipeStatePayload.java` | Server→Client recipe sync |
| `networking/SetRecipeStatePayload.java` | Client→Server recipe toggle |
| `content/enums/EnumAssemblyRecipeState.java` | Recipe state enum |
| `registries/BCItems.java` | +5 chipsets, laser item, table item |
| `registries/BCBlocks.java` | +LASER, ASSEMBLY_TABLE |
| `registries/BCBlockEntities.java` | +LASER, ASSEMBLY_TABLE |
| `registries/BCMenuTypes.java` | +ASSEMBLY_TABLE |
| `datagen/...` | Blockstates, models, lang, loot, recipes |
