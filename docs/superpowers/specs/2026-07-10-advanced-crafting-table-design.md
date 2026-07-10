# Advanced Crafting Table — Design

**Datum:** 2026-07-10
**Phase:** E (Silicon), Subphase 1 nach Assembly Table
**Status:** Design genehmigt, bereit für Implementierungsplan

## Ziel

Portierung der **Advanced Crafting Table** aus BuildCraft 1.12 (`TileAdvancedCraftingTable`)
nach NeoForge 1.21.1. Eine laser-betriebene Auto-Crafting-Maschine: ein Phantom-Blueprint-Grid
definiert ein Vanilla-Crafting-Rezept, echte Zutaten liegen in einem separaten Materials-Puffer,
und solange Laser-Energie fließt, craftet die Maschine wiederholt in einen Results-Puffer.

Der Port ist **originalgetreu**: exakter Stack-Abgleich, drei getrennte Inventare,
fixe Kosten pro Craft, Ansteuerung über `ILaserTarget`.

## Getroffene Design-Entscheidungen

| Entscheidung | Wahl | Begründung |
|---|---|---|
| Rezept-Eingabe | Manuelles Phantom-Grid (kein Vanilla-Recipe-Book) | Kern-Mechanismus originalgetreu, aber ohne die fragile 1.21.1-Recipe-Book-API. Recipe-Book bleibt optionale Folge-Phase. |
| Zutaten-Abgleich | Exakte Stacks | Wie Original (der Ingredient-Zweig war dort auskommentiert). Keine Tag-Äquivalente. |
| Inventar-Struktur | Drei getrennte ItemStackHandler | Saubere Cap-Sidedness: Materials=INSERT, Results=EXTRACT, Blueprint=keine Cap. |
| Kosten pro Craft | Fixer Config-Wert, Default 5000 FE | Wie Original (fixe 500 MJ). Bei Laser-Output 40 FE/t ≈ 6 s/Craft. |
| Laser-Ansteuerung | `LaserBE` auf `instanceof ILaserTarget` generalisieren | Wie Original (targetiert das Interface). Kein Risiko für Assembly Table. |
| Result-Preview | Reiner Anzeige-Slot, gefüttert vom synchronisierten `assumedResult` | Wie Original (`SlotDisplay` / `resultClient`). |
| Block-Facing | Kein FACING | Konsistent mit `AssemblyTableBlock` (Schwestermaschine). |

## Referenz-Implementierungen

- **Vorbild in diesem Repo:** Assembly Table
  - `content/blocks/AssemblyTableBlock.java`
  - `content/blockentities/AssemblyTableBE.java` (`ContainerBlockEntity`, `ILaserTarget`, `MenuProvider`)
  - `content/menus/AssemblyTableMenu.java` (`PDLAbstractContainerMenu`)
  - `client/screens/AssemblyTableScreen.java` (`PDLAbstractContainerScreen`)
  - `content/blockentities/LaserBE.java` / `content/blocks/LaserBlock.java`
  - `api/blockentities/ILaserTarget.java` (`int getRequiredLaserPower()`, `void receiveLaserPower(int fe)`)
- **Original:** `/run/media/fabi/SSD/codeing/BuildCraft-1.12/common/buildcraft/`
  - `silicon/tile/TileAdvancedCraftingTable.java`
  - `lib/tile/craft/WorkbenchCrafting.java` + `lib/tile/craft/IAutoCraft.java`
  - `silicon/container/ContainerAdvancedCraftingTable.java`
  - `silicon/gui/GuiAdvancedCraftingTable.java`

## Komponenten

Alle Pfade unter `src/main/java/com/thepigcat/buildcraft/`.

### `content/blocks/AdvancedCraftingTableBlock.java`
- `extends BaseEntityBlock`, `simpleCodec`.
- Kein Blockstate-Property (kein FACING).
- `newBlockEntity` → `BCBlockEntities.ADVANCED_CRAFTING_TABLE.get().create(...)`.
- `useWithoutItem` → `player.openMenu(be, pos)` (BE ist `MenuProvider`).
- `getTicker` → server-only via `createTickerHelper(..., AdvancedCraftingTableBE::serverTick)`.

### `content/blockentities/AdvancedCraftingTableBE.java`
- `extends ContainerBlockEntity implements ILaserTarget, MenuProvider`.
- Drei Handler:
  - `blueprint` — 9 Slots, Phantom (Geist-Items, nie verbraucht, keine Cap).
  - `materials` — 15 Slots, echte Zutaten, Cap INSERT (alle Seiten).
  - `results` — 9 Slots, Output, Cap EXTRACT (alle Seiten).
- Felder: `public int power`, `ItemStack assumedResult`, gecachtes `currentRecipe` (`RecipeHolder<CraftingRecipe>` bzw. resolved `CraftingRecipe`), Dirty-Flag.
- `ILaserTarget`:
  - `getRequiredLaserPower()` = `canCraft() ? feCost - power : 0`.
  - `receiveLaserPower(fe)` = `power += fe`.
- Rezept-Neuberechnung bei Änderung an `blueprint` **oder** `materials`:
  - `CraftingInput` aus den 9 Geist-Items bauen.
  - `level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)` → `currentRecipe`.
  - `assumedResult = currentRecipe.value().assemble(input, level.registryAccess())`.
- `canCraft()`:
  - Rezept vorhanden **&&**
  - `results` kann `assumedResult` voll aufnehmen **&&**
  - `materials` enthält für jeden belegten Blueprint-Slot den **exakten Stack** (Item + benötigte Anzahl summiert über gleiche Blueprint-Items).
- `serverTick`:
  - Rezept ggf. neu berechnen (wenn dirty).
  - Wenn `canCraft() && power >= feCost`:
    - je ein Item pro belegtem Blueprint-Slot aus `materials` entnehmen,
    - `assumedResult` in `results` einfügen (Überlauf → angrenzende `Capabilities.ItemHandler.BLOCK` / sonst `ItemEntity`),
    - Remainder-Items (Container-Items wie leere Eimer) zurück in `materials` (Überlauf → angrenzend/Welt),
    - `power -= feCost`.
  - Bei Zustandsänderung Block-Update senden.
- Sync: `getUpdatePacket`/`getUpdateTag`/`handleUpdateTag` schreiben `power` + `assumedResult`.
- NBT: `saveData`/`loadData` (PDL-Hooks) schreiben die drei Handler + `power`.
  Blueprint-Geist-Items müssen persistiert werden.
- `MenuProvider`: `getDisplayName` = `container.buildcraft.advanced_crafting_table`;
  `createMenu` → `new AdvancedCraftingTableMenu(id, inv, this)`.

### `content/menus/AdvancedCraftingTableMenu.java`
- `extends PDLAbstractContainerMenu<AdvancedCraftingTableBE>`.
- Slots (Layout am Original orientiert, Pixel im Screen final abzustimmen):
  - Blueprint: 3×3 **Phantom-Slots** (Ghost). Klick-Logik im `clicked()`-Override:
    - Klick mit Item in der Hand → Kopie (Anzahl 1) ins Grid, Hand behält Item.
    - Klick auf belegten Slot → leeren.
    - Kein echtes Item-Movement, kein Verbrauch.
  - Materials: 5×3 `SlotItemHandler`.
  - Results: 3×3 `SlotItemHandler` (nur Extraktion durch Spieler; Insert durch Craft-Logik).
  - Result-Preview: 1 Display-Slot (nur Anzeige, `assumedResult`).
  - Player-Inventar + Hotbar.
- Zweiter Konstruktor mit `RegistryFriendlyByteBuf` (liest BlockPos) als `IContainerFactory`.
- `getMergeableSlotCount()` so, dass Shift-Klick in den Materials-Puffer wandert.
- Exponiert `getPower()`, `getAssumedResult()`, `getFeCost()` für den Screen.

### `client/screens/AdvancedCraftingTableScreen.java`
- `extends PDLAbstractContainerScreen<AdvancedCraftingTableMenu>`.
- Textur `textures/gui/advanced_crafting_table.png` (aus Original übernehmen/anpassen).
- Vertikaler Progress-Bar `power/feCost`.
- Result-Preview über dem Display-Slot rendern.

## Nötiger Refactor: `LaserBE`

`LaserBE` ist an drei Stellen (`scanForTargets`, `pickTarget`, `serverTick`) hart auf
`AssemblyTableBE` verdrahtet. Diese Checks werden auf **`instanceof ILaserTarget`** verallgemeinert.
Danach speist ein Laser automatisch beide Tischtypen. Die Assembly Table implementiert `ILaserTarget`
bereits — kein Verhaltensänderung für sie.

## Registrierung & Wiring

- `registries/BCBlocks.java`: `ADVANCED_CRAFTING_TABLE` via `registerBlockAndItem`.
- `registries/BCBlockEntities.java`: BE-Type via `BlockEntityType.Builder.of(...)`.
- `registries/BCMenuTypes.java`: `ADVANCED_CRAFTING_TABLE = registerMenuType(...)` (buf-Konstruktor).
- `BuildcraftLegacy.attachCaps()`:
  - `Capabilities.ItemHandler.BLOCK` für die BE — Materials INSERT / Results EXTRACT über PDL-Sided-Access.
- `BuildcraftLegacyClient.registerMenuScreens()`:
  - `event.register(BCMenuTypes.ADVANCED_CRAFTING_TABLE.get(), AdvancedCraftingTableScreen::new)`.
- **Kein** eigener Payload nötig (Phantom-Klicks laufen über `Menu.clicked()`).

## Config

`BCConfig`: neues `@ConfigValue`-Feld `advancedCraftingTableFeCost` (Default **5000**),
Kategorie `assembly` (oder neue Kategorie `advanced_crafting`).

## Datagen

- Blockstate + Block-Model + Item-Model (Texturen aus Original BC 1.12 übernehmen).
- Lang: Block-Name + `container.buildcraft.advanced_crafting_table`.
- Loot-Table (drops self).
- Crafting-Rezept für den Block selbst.

## Testing

- **Unit:** `canCraft()`-Logik isoliert prüfen:
  - exakter Stack-Match inkl. korrekter Anzahl (mehrere gleiche Blueprint-Items summieren),
  - Results-Puffer voll → kein Craft,
  - kein/ungültiges Rezept → `assumedResult` leer, `getRequiredLaserPower()` = 0.
- **In-Game (`runClient`):**
  - Rezept ins Blueprint-Grid legen, Materials füllen, Laser anschließen.
  - Craft läuft, Output landet im Results-Puffer, Power-Bar bewegt sich.
  - Pipe INSERT in Materials / EXTRACT aus Results an allen Seiten.
  - Container-Item-Rückgabe (leerer Eimer) landet in Materials.
  - Persistenz: Blueprint-Geist-Items überstehen Weltneuladen.

## Offene Punkte / bewusst ausgeklammert (YAGNI)

- **Vanilla-Recipe-Book-Integration** — spätere optionale Phase.
- **Ingredient-/Tag-basierter Abgleich** — bewusst nicht (Original-Verhalten gewählt).
- Exakte GUI-Pixel-Koordinaten werden beim Screen-Bau final abgestimmt.
