# Integration Table — Design

**Datum:** 2026-07-12
**Phase:** E (Silicon), Subphase nach Advanced Crafting Table
**Status:** Design genehmigt, bereit für Implementierungsplan

## Ziel

Portierung der **Integration Table** aus BuildCraft 1.12 (`TileIntegrationTable`) nach NeoForge 1.21.1.
Eine laser-betriebene Silicon-Maschine: ein **Center-Item** + ein **Ring aus 8 Zusatz-Items** ergeben
über ein Rezept ein **Output-Item**. Laser-Energie zahlt fixe FE-Kosten pro Craft.

Der Port ist **originalgetreu** und **infrastrukturell**: BC 1.12 (Neptune) liefert die Maschine
**ohne Standard-Rezepte** — ihr eigentlicher Zweck (Gate-Aufwertung: Gate + Chipset → aufgewertetes Gate,
Roboter-Integration) kommt erst mit der noch nicht portierten Gates-Phase. Wir bauen jetzt die
**vollständige Maschine + Rezept-Registry** und liefern **1–2 Platzhalter-Demo-Rezepte** zum Testen;
echte Rezepte folgen mit den Gates.

## Getroffene Design-Entscheidungen

| Entscheidung | Wahl | Begründung |
|---|---|---|
| Scope | Maschine jetzt, echte Rezepte später (mit Gates) | Originalgetreu (BC liefert Maschine ohne Rezepte); Integration Table ist Voraussetzung für Gates |
| Demo-Rezepte | 1–2 Platzhalter jetzt (throwaway) | Testbarkeit; werden mit der Gates-Phase ersetzt |
| Block-Modell | Originaler 9px-Slab (Nubs + Center-Puck), 5 Original-Texturen, custom Collision | Originalgetreu, hebt sich von den Voll-Block-Tischen ab |
| GUI | Original-`integration_table.png` kopieren, exakte Original-Koordinaten (176×191) | Lektion aus der Advanced Crafting Table: GUI-Koordinaten IMMER an die Textur binden, nie raten |
| Zutaten-Abgleich | Center enthält `center` (≥ centerCount); Ring = exakter Multiset-Match | Wie Original `IntegrationRecipeBasic.matches()` (precise) |
| Kosten | Fix pro Rezept, `BCConfig.integrationTableFeCost` (Default 5000) | Wie Original (fixe `requiredMicroJoules`) |
| Pipe-Cap | Inputs INSERT / Output EXTRACT (kombinierter IO-Handler) | Konsistent mit `AdvancedCraftingTableIOHandler` |
| Client-Sync | `onDataPacket`-Override (power + assumedOutput direkt anwenden) | Lektion aus der Advanced Crafting Table: NeoForge routet Laufzeit-BE-Updates über `onDataPacket`→`loadData`, NICHT `handleUpdateTag` |
| Laser-Antrieb | `implements ILaserTarget` | `LaserBE` targetiert das Interface → automatisch gespeist, keine Registrierung |

## Referenz-Implementierungen

- **Original:** `/run/media/fabi/SSD/codeing/BuildCraft-1.12/common/buildcraft/`
  - `silicon/tile/TileIntegrationTable.java`, `silicon/tile/TileLaserTableBase.java`
  - `lib/recipe/IntegrationRecipeBasic.java`, `lib/recipe/IntegrationRecipeRegistry.java`
  - `silicon/gui/GuiIntegrationTable.java`, `silicon/container/ContainerIntegrationTable.java`
  - `silicon/block/BlockLaserTable.java`
  - Modell: `buildcraft_resources/assets/buildcraftsilicon/models/block/table/integration.json`
  - Texturen: `.../textures/blocks/table/integration/{top,middle,bottom,side,center}.png`, `.../textures/gui/integration_table.png`
- **Vorbilder in diesem Repo** (Basis-Package `com.thepigcat.buildcraft`):
  - Advanced Crafting Table (GUI-Koordinaten-Matching + `onDataPacket`-Sync + IO-Handler): `content/blockentities/AdvancedCraftingTableBE.java`, `.../menus/AdvancedCraftingTableMenu.java`, `.../client/screens/AdvancedCraftingTableScreen.java`, `.../blockentities/AdvancedCraftingTableIOHandler.java`
  - Assembly Table (In-Code-Rezept-Registry): `api/recipes/AssemblyRecipe.java`, `api/recipes/AssemblyRecipeRegistry.java`, `BuildcraftLegacy.registerAssemblyRecipes()`
  - `api/blockentities/ILaserTarget.java`, `content/blockentities/LaserBE.java`
  - Slot-Helfer: `content/menus/SlotDisplay.java` (für die Output-Preview)

## Komponenten

Alle Pfade unter `src/main/java/com/thepigcat/buildcraft/`.

### `api/recipes/IntegrationRecipe.java` + `IntegrationRecipeRegistry.java`
- `record IntegrationRecipe(ResourceLocation id, Ingredient center, int centerCount, List<Ingredient> ring, ItemStack output, int feCost)`.
- `IntegrationRecipeRegistry`: statische `LinkedHashMap<ResourceLocation, IntegrationRecipe>` mit `register`, `get(id)`, `all()` — exakte Form von `AssemblyRecipeRegistry`.
- **Match-Semantik** (`matches(centerStack, ringStacks)`):
  - `center.test(centerStack) && centerStack.getCount() >= centerCount`, **und**
  - die nicht-leeren Ring-Stacks bilden einen **exakten Multiset-Match** zu `ring`: jede `Ingredient` aus `ring` verbraucht genau einen (unbenutzten) nicht-leeren Ring-Slot, und **kein** nicht-leerer Ring-Slot bleibt übrig.

### `content/blockentities/IntegrationTableIOHandler.java`
- Kombinierter Cap-View für Pipes: Slots `[0, inputs)` = Center(1)+Ring(8) → INSERT-only; Slot(s) `[inputs, total)` = Output → EXTRACT-only. Analog `AdvancedCraftingTableIOHandler`.
- (Automatische Ring-Befüllung ist unpräzise, aber INSERT bleibt für einfache Automatisierung erlaubt; Output-EXTRACT ist der Hauptzweck.)

### `content/blockentities/IntegrationTableBE.java`
- `extends ContainerBlockEntity implements ILaserTarget, MenuProvider`.
- Handler: `center` (1, roher `ItemStackHandler`), `ring` (8), `output` (1). Kombiniert via `IntegrationTableIOHandler`.
- Felder: `int power`, `ItemStack assumedOutput` (Preview), gecachtes `currentRecipe`, `recipeDirty`.
- `getRequiredLaserPower()` = `canCraft() ? feCost − power : 0`; `receiveLaserPower(fe)` addiert.
- `recomputeRecipe()` bei `recipeDirty`: Ring/Center lesen → `IntegrationRecipeRegistry`-Scan → `currentRecipe` + `assumedOutput`.
- `canCraft()` = Rezept vorhanden && Output-Slot kann `assumedOutput` aufnehmen && Center/Ring vorhanden (Multiset).
- `serverTick`: recompute wenn dirty; wenn `canCraft() && power >= feCost` → Center um `centerCount` verbrauchen, Ring-Multiset verbrauchen, `assumedOutput` in `output` legen (Überlauf → angrenzend/Welt), `power −= feCost`; Block-Update bei Änderung.
- **Sync:** `getUpdateTag`/`handleUpdateTag` + **`onDataPacket`-Override** → gemeinsamer `applyClientSync(tag)` liest **nur** `power` + `assumedOutput` (nicht via `loadData`; Slots synchronisieren übers Menu).
- **NBT:** `saveData`/`loadData` serialisieren `center`/`ring`/`output` + `power`; on load `recipeDirty=true`.
- `MenuProvider`: `getDisplayName` = `container.buildcraft.integration_table`; `createMenu` → `new IntegrationTableMenu(...)`.

### `content/blocks/IntegrationTableBlock.java`
- `extends BaseEntityBlock`, `simpleCodec`, kein FACING.
- `getShape`/`getCollisionShape` → `Block.box(0, 0, 0, 16, 9, 16)` (9px-Slab); `getRenderShape` = MODEL.
- `useWithoutItem` → `player.openMenu(be, pos)`; `getTicker` → server-only `IntegrationTableBE::serverTick`.

### `content/menus/IntegrationTableMenu.java` (Original-Koordinaten, 176×191)
- `extends PDLAbstractContainerMenu<IntegrationTableBE>`.
- Ring/Center: 3×3-Gitter bei Pixel `(19 + col*25, 24 + row*25)` (Spalten x∈{19,44,69}, Reihen y∈{24,49,74}); die **Mitte** (col=1,row=1) ist der `center`-Slot (Handler-Index 0), die 8 anderen sind `ring` (Indizes 0..7).
- **Output-Preview:** `SlotDisplay` bei `(101, 36)`, gefüttert von `getAssumedOutput()`.
- **Echter Output-Slot:** `SlotItemHandler` auf `output` bei `(138, 49)`.
- `addPlayerInventory(inv, 109)` + Hotbar entsprechend.
- `RegistryFriendlyByteBuf`-Konstruktor (liest BlockPos). Definierte Slot-Index-Ranges für `quickMoveStack`.
- Exponiert `getPower()`, `getFeCost()`, `getAssumedOutput()`.

### `client/screens/IntegrationTableScreen.java`
- `extends PDLAbstractContainerScreen<IntegrationTableMenu>`; Textur `buildcraft:textures/gui/integration_table.png`.
- `imageWidth=176`, `imageHeight=191`, `inventoryLabelY=imageHeight-94`.
- Progress-Bar: dest `(164, 22)` 4×70, Source-UV `(176, 0)`, füllt bottom-up.

## Registrierung & Wiring

- `BCBlocks.INTEGRATION_TABLE` via `registerBlockAndItem` (gleiche Properties wie die Schwestern).
- `BCBlockEntities.INTEGRATION_TABLE`, `BCMenuTypes.INTEGRATION_TABLE` — analog.
- `BuildcraftLegacy.attachCaps()`: `Capabilities.ItemHandler.BLOCK` → `(be,side)->be.getIOHandler()`.
- `BuildcraftLegacy.registerIntegrationRecipes()` (parallel zu `registerAssemblyRecipes()`), im Setup aufgerufen.
- `BuildcraftLegacyClient.registerMenuScreens()`: `event.register(BCMenuTypes.INTEGRATION_TABLE.get(), IntegrationTableScreen::new)`.
- **Kein** eigener Payload nötig.

## Config

`BCConfig.integrationTableFeCost` (`@ConfigValue`, Default **5000**, Kategorie `integration` oder `assembly`).

## Demo-Rezepte (Platzhalter, throwaway)

1–2 klar als Platzhalter markierte Test-Rezepte mit vorhandenen Items, z. B.:
- `red_chipset` (Center, ×1) + Ring aus 4× `redstone` → `iron_chipset` (testet Center-Count + Ring-Multiset).

Diese werden gelöscht/ersetzt, sobald die Gates-Phase echte Integration-Rezepte bringt.

## Datagen

- Custom Blockstate + **originales 9px-Slab-Modell** (`table/integration.json` portiert) mit 5 kopierten Texturen.
- Item-Modell (vom Block abgeleitet oder eigenes).
- Lang: `block.buildcraft.integration_table` = "Integration Table", `container.buildcraft.integration_table` = "Integration Table".
- Loot: `dropSelf`.
- Block-Crafting-Rezept: `OiO / OrO / OgO` — O=`obsidian`, i=`gold_ingot`, r=`red_chipset`, g=`diamond_gear` (alle vorhanden).

## Testing

- **Unit-nah:** `matches()`/Multiset-Logik isoliert — exakter Ring-Match (kein Rest, keine fehlende Anforderung), Center-Count, Output-voll → kein Craft, kein Rezept → assumedOutput leer.
- **In-Game (Pflicht — Lektion aus der Advanced Crafting Table):**
  - GUI-Layout sauber an die Textur ausgerichtet (Ring, Center, Preview, echter Output, Player-Inv, Progress-Bar).
  - Preview aktualisiert **live** bei Center/Ring-Änderung (onDataPacket-Sync).
  - Laser-Antrieb: Craft läuft, Output landet im Output-Slot, Center/Ring werden korrekt verbraucht, Progress-Bar animiert.
  - Pipe: Output extrahierbar; Input-Insert.
  - 9px-Slab: korrekte Collision/Höhe.
  - Persistenz: Center/Ring/Output/Power überstehen Weltneuladen.

## Bewusst ausgeklammert (YAGNI)

- **Echte Gate-/Roboter-Rezepte** — folgen mit der Gates-Phase (Integration Table ist deren Voraussetzung).
- Kein Recipe-Book, keine scaling costs (Original nutzt fixe Kosten).
