# Phase B — Item Pipes Speziallogik: Design Spec

**Datum:** 2026-04-12
**Ziel:** Phase B abschließen — alle Item Pipes auf funktionale Kernmechanik bringen (originalgetreu, ohne Gate-Dependency)

## Scope

5 Pipes, davon 4 Code-Änderungen + 1 Dokumentation:

| Pipe | Aktion | Aufwand |
|------|--------|---------|
| Gold | Refactor: eigene BE-Klasse, Hardcoded-Logik aus Base entfernen | Klein |
| Obsidian | Fix: Single-Connection-Enforcement, Single-Face Suction | Klein |
| Daizuli | Fix: Directional-System + Color-Routing originalgetreu | Mittel |
| Emerald | Feature: Blacklist-Toggle in bestehende GUI | Mittel |
| Diamond | Nur TODO-Dokumentation (Split/Round-Robin für Zukunft) | Minimal |

### Ausgeschlossen (bereits fertig)
- **Iron Pipe** — Wrench-Rotation + Output-Blocking vollständig implementiert
- **Lapis Pipe** — Farb-System funktional, Gate-Integration ist Phase E

## Design-Prinzipien

- **Originalgetreu** — BC 1.12.2 als Referenz, keine Addon-Features
- **Gate-Ready** — erweiterbare Methoden statt direkter Feld-Zugriffe (z.B. `getActiveColor()`, `getFilterMode()`), damit Gates in Phase E andocken können
- **Keine Breaking Changes** an PipeBlock/PipeBlockEntity/ItemPipeBE Base-Klassen (außer Gold-Hardcoding entfernen)
- **Energy-Hooks für später** — Obsidian Pipe bekommt vorerst kein Energy-Requirement, aber die Architektur soll Energy-Gating später ermöglichen

## 1. Gold Pipe — Refactor

### Problem
Speed-Boost ist hardcoded in `ItemPipeBE.tick()` mit String-Check `"gold_pipe"`. Nicht wartbar, verstößt gegen Pipe-Architektur.

### Lösung
- **Neue Klasse:** `GoldItemPipeBE extends ItemPipeBE`
- **Override `tick()`:** Wendet Original-BC-Werte an:
  - `SPEED_DELTA = 0.07f` (Beschleunigung pro Tick)
  - `SPEED_TARGET = 0.25f` (Max-Speed)
- **Hardcoded Gold-Check aus `ItemPipeBE.tick()` entfernen**
- **Neuer `BlockEntityType`** in `BCBlockEntities` für Gold Pipe
- **Neuer `PipeType` `GOLD`** in `BCPipeTypes` (damit richtige BE-Klasse instanziiert wird)
- **Kein neuer Block nötig** — `ItemPipeBlock` reicht

### Original BC Referenz
`PipeBehaviourGold.java`: `event.modifyTo(0.25, 0.07)` in `@PipeEventHandler modifySpeed()`

## 2. Obsidian Pipe — Single-Connection-Enforcement

### Problem
Aktuell erlaubt beliebig viele Connections und saugt auf allen offenen Seiten. Original: genau eine offene Seite.

### Lösung
- **`ObsidianItemPipeBlock.canConnectTo()` Override:** Obsidian-zu-Obsidian blockieren
- **`getOpenFace()` in `ObsidianItemPipeBE`:**
  - Gibt genau eine offene Seite zurück
  - Gibt `null` zurück wenn 0 oder 2+ Seiten offen → keine Suction
- **Suction nur auf der einen offenen Seite** (AABB-Berechnung anpassen)
- **Cooldown bleibt 10 Ticks**
- **Kein Energy-Requirement vorerst** — passives Pickup, Energy-Gating als TODO für später

### Original BC Referenz
`PipeBehaviourObsidian.java`: `getOpenFace()` gibt ONE open face zurück, `canConnect()` returns false für andere Obsidian Pipes.

## 3. Daizuli Pipe — Directional + Color Routing

### Problem
Directional-System inkonsistent, Wrench-Interaktion im Block fehlt, Routing-Logik weicht vom Original ab.

### Lösung
- **Directional-System wie Iron Pipe:**
  - Wrench-on-Face setzt `targetDirection`
  - Visuelle Markierung: Output-Seite als CONNECTED, andere als BLOCKED (analog Iron)
- **`DaizuliItemPipeBlock`:** Wrench-Interaktion einbauen — Klick auf Face setzt Output
- **Color-Cycling:** Rechtsklick ohne Wrench cycled Pipe-Farbe
- **Routing-Logik (originalgetreu):**
  - Item-Farbe == Pipe-Farbe → NUR zur `targetDirection` routen
  - Item-Farbe != Pipe-Farbe (oder keine Farbe) → `targetDirection` AUSSCHLIESSEN, alle anderen erlaubt
- **Gate-Hook:** `getActiveColor()` Methode statt direktem `pipeColor`-Zugriff

### Original BC Referenz
`PipeBehaviourDaizuli.java`: Extends `PipeBehaviourDirectional`, `sideCheck()` prüft Color-Match → Output oder Nicht-Output.

## 4. Emerald Pipe — Blacklist-Toggle

### Problem
Nur Whitelist-Filter, kein Umschalten auf Blacklist möglich.

### Lösung
- **Neues Feld:** `FilterMode filterMode` Enum (`WHITELIST`, `BLACKLIST`) in `EmeraldItemPipeBE`
- **`extractItems()` anpassen:**
  - Whitelist (wie jetzt): nur passende Items extrahieren
  - Blacklist: alle Items AUSSER passende extrahieren
  - Leerer Filter: Whitelist = alles durchlassen, Blacklist = nichts durchlassen
- **GUI-Erweiterung:** Toggle-Button in `EmeraldPipeScreen`
  - Kleiner Button der zwischen W/B umschaltet
  - Visuelles Feedback (Icon oder farbiger Text)
- **`EmeraldPipeMenu`:** Data-Slot für `filterMode` Client-Sync
- **NBT:** `filterMode` in Save/Load
- **Gate-Hook:** `getFilterMode()` Methode

### Original BC Referenz
Emerald im Original = `PipeBehaviourEmzuli` (Gate-Presets). Unsere vereinfachte Version (Whitelist/Blacklist) ist bewusst anders, da Gates noch nicht existieren. Gate-Preset-System wird in Phase E nachgerüstet.

## 5. Diamond Pipe — Dokumentation

### Aktueller Stand
Funktioniert korrekt als passiver Sortier-Knoten mit 54-Slot Filter-GUI. Registrierung ist korrekt (nicht EXTRACTING).

### TODO für Zukunft
- **Round-Robin:** Items abwechselnd an mehrere passende Output-Seiten verteilen
- **Proportionaler Split:** Stack auf mehrere Seiten aufteilen (braucht Pipe-Base-Änderungen)
- Beides erst wenn Pipe-Base Multi-Output unterstützt

## Implementierungsreihenfolge

1. Gold (Quick Refactor, räumt Base auf)
2. Obsidian (klar abgegrenzt, keine Dependencies)
3. Daizuli (mehr Arbeit, baut auf Iron-Pattern auf)
4. Emerald (GUI-Erweiterung, unabhängig)
5. Diamond (nur Doku-Update)

## Future Hooks (Phase E: Gates)

| Pipe | Gate-Hook | Spätere Funktion |
|------|-----------|-----------------|
| Daizuli | `getActiveColor()` | Gate setzt Farbe dynamisch |
| Emerald | `getFilterMode()` | Gate schaltet W/B um |
| Lapis | `getActiveColor()` | Gate setzt Farbe dynamisch |
| Obsidian | Energy-Requirement | Engine-Power bestimmt Suction-Range |

## Nicht im Scope

- Pipe-Base-Änderungen für Multi-Output/Split
- Energy-System für Obsidian
- Gate/Trigger/Action Integration
- Wiki-Dokumentation (separates Projekt nach Phase B)
