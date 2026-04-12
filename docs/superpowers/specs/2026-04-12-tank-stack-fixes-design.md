# Tank Stack Fixes — Design Spec

**Datum:** 2026-04-12
**Scope:** Multi-Block-Tank Stack-Lifecycle robust machen — Destruction, Bridging, Client-Sync.
**Ziel:** Verhalten originalgetreu zu BuildCraft 1.12.2, mit Fokus auf korrekte Bookkeeping-Pointers.

---

## Problem

Gemeldete Bugs im aktuellen Multi-Block-Tank:

1. **Flicker beim Break** — Tank verschwindet kurz, dann wieder da (Client-Server-Desync).
2. **Drittes Segment füllt nicht** — 3er-Stack akzeptiert kein Fluid mehr nach einer bestimmten Menge; Capacity bleibt falsch.
3. **Destroyed-but-invisible** — Block ist serverseitig weg, clientseitig noch sichtbar (oder umgekehrt).

## Root Cause

Der Tank-Stack nutzt ein Master-Pointer-Modell:
- Der unterste Tank im Stack (Bottom) hält den echten `DynamicFluidTank`.
- Andere Tanks im Stack delegieren via `bottomTankPos`-Feld.
- Capacity wird über `initTank(size)` auf dem Master gesetzt.

**Der Bug:** `initTank(size)` und `bottomTankPos`-Updates passieren **nur** in `TankBlock.onPlace`. Beim Break (`onRemove`) werden nur die `initialFluid`-Werte auf Nachbarn verteilt — die Pointer bleiben auf dem gelöschten Master zeigend, und Capacity wird nie neu berechnet. Bei Bridging (Tank platzieren zwischen zwei isolierten Tanks) funktioniert `onPlace` nur weil es aufwärts walkt, aber die Logik ist an 2 Stellen dupliziert.

Zusätzlich: Kein expliziter Block-Update-Packet nach Stack-Änderungen → Client-Render liest aus veraltetem State.

---

## Lösung: `reformStack` — Single Source of Truth

Alle Stack-Lifecycle-Änderungen laufen über einen einzigen Helper. Dieser ist **die einzige** Stelle, die `bottomTankPos` ändert oder `initTank(size)` aufruft.

### Architektur

**Verantwortlichkeitstrennung:**
- `TankBlock.updateShape` — nur BlockState-Properties (`TOP_JOINED`/`BOTTOM_JOINED`) basierend auf Fluid-Kompatibilität.
- `TankBlock.onPlace` — legt `initialFluid` auf Bottom, ruft `reformStack(level, pos)` auf.
- `TankBlock.onRemove` — verteilt Fluid via existierende Helpers (`splitTank`/`moveFluidsAbove`/`removeFluidFromBottomTank`), ruft `reformStack` für obere und untere Stack-Hälfte auf.
- `reformStack(level, anchor)` — walkt Stack vom Anchor, setzt alle Pointer, ruft `initTank`, triggert Sync.

**Delegations-Modell (unverändert):**
- Bottom-Tank = Master, hält `DynamicFluidTank`.
- Andere Tanks delegieren via `bottomTankPos`.
- `initialFluid` ist Transfer-Mechanismus zwischen Fluid-Redistribution und Stack-Reform.

---

### `reformStack` Algorithmus

**Signatur:** `private static void reformStack(LevelAccessor level, BlockPos anchor)`

**Vorbedingung:** `anchor` muss Position eines existierenden TankBE sein. Aufrufer prüft das.

**Schritte:**

1. **Bottom finden:** Walk von `anchor` nach unten, solange `state.getValue(BOTTOM_JOINED) == true` → `bottomPos`.
2. **Top finden:** Walk von `bottomPos` nach oben, solange `state.getValue(TOP_JOINED) == true` → `topPos`.
3. **Size berechnen:** `size = topPos.getY() - bottomPos.getY() + 1`.
4. **Pointer umbiegen:** Für jede Y-Position `y` in `[bottomPos.getY(), topPos.getY()]`:
   - `TankBE.setBottomTankPos(bottomPos)`
5. **Master initialisieren:** Auf `bottomTankBe`: `initTank(size)` — appliziert pending `initialFluid` falls gesetzt, setzt `DynamicFluidTank` capacity auf `size * BCConfig.tankCapacity`.
6. **Sync:** Für jede BE im Range `setChanged()` aufrufen. Auf Bottom zusätzlich `level.sendBlockUpdated(bottomPos, state, state, 3)` — stellt sicher dass Client den neuen Master-State sieht, bevor Non-Master-BEs ihre Capability abfragen.

---

### Call Sites

**`onPlace` (vereinfacht):**
1. Berechne `initialFluid` für Bottom-Tank wie im bestehenden Code (drei if/else-Fälle für `TOP_JOINED`/`BOTTOM_JOINED`-Kombinationen; Summation des bestehenden Fluids + des neu platzierten + des darüberliegenden).
2. Setze `bottomTankBe.initialFluid`.
3. Ruf `reformStack(level, pos)` auf.
4. Entfernt: manuelle While-Loops, direkte `initTank`-Aufrufe, `setTopJoined`/`setBottomJoined`-Calls.

**`onRemove` (ergänzt):**
1. Fluid-Redistribute-Helpers laufen wie bisher (setzen `initialFluid` auf Nachbarn oben/unten).
2. `super.onRemove` aufrufen → Block ist weg, `updateShape` auf Nachbarn aktualisiert `TOP_JOINED`/`BOTTOM_JOINED`.
3. **Neu:**
   - Wenn `level.getBlockEntity(pos.above()) instanceof TankBE`: `reformStack(level, pos.above())` — reformt oberen Split.
   - Wenn `level.getBlockEntity(pos.below()) instanceof TankBE`: `reformStack(level, pos.below())` — reformt unteren Split (Capacity-Reduktion).

**`updateShape` (unverändert in Funktion, vereinfacht):**
- Liefert weiterhin BlockState mit `TOP_JOINED`/`BOTTOM_JOINED` basierend auf Nachbar-Fluid-Kompatibilität.
- **Entfernt:** `tankBE.setTopJoined`/`setBottomJoined` Calls (diese Instance-Fields können aus BlockState abgeleitet werden, siehe TankBE-Cleanup).

---

### TankBE Cleanup

Die Instance-Felder `topJoined`/`bottomJoined` in TankBE sind redundant — sie sind bereits im BlockState encoded. Entferne:
- `private boolean topJoined;` + `private boolean bottomJoined;`
- `setTopJoined`/`setBottomJoined` Setter
- `isTopJoined`/`isBottomJoined` Getter

Falls eines der Getter extern genutzt wird: aus `getBlockState().getValue(TOP_JOINED/BOTTOM_JOINED)` ableiten.

**`loadData`-Fix:**

Aktuell:
```java
this.bottomTankPos = BlockPos.of(tag.getLong("bottomTankPos"));
```

Wenn Tag nicht gesetzt ist (frisch platzierter Tank ohne Stack), liefert `getLong` `0L` → `BlockPos(0,0,0)` als falscher Pointer. Fix:
```java
if (tag.contains("bottomTankPos")) {
    this.bottomTankPos = BlockPos.of(tag.getLong("bottomTankPos"));
}
```

---

### Client-Sync

**Flicker-Root-Cause:** Nach Break sendet Server BlockBreakPacket, Client entfernt Block. Stack-Reform-Änderungen auf Server ohne expliziten Sync → benachbarte Tank-BEs haben stale State auf Client bis nächstes reguläres Packet.

**Fix:**
1. `TankBE.setBottomTankPos` ruft bereits `this.updateData()` auf (PDL-Mechanismus, schickt BE-Data-Packet). ✅
2. `DynamicFluidTank.onChange` triggert `updateData()` ebenfalls. ✅
3. **Neu:** `reformStack` ruft explizit `level.sendBlockUpdated(bottomPos, state, state, 3)` am Ende auf, damit Master-Update garantiert raus geht.

---

## Testing — Ingame-Szenarien

Kein Unit-Test-Setup; alle Tests ingame. Default-Config: `BCConfig.tankCapacity = 8000 mB`, Bucket = `1000 mB`.

### Test 1: Flicker beim Break
Mittel-Tank aus 3er-Stack mit Fluid brechen. Erwartung: Block geht sauber weg, kein Wiederauftauchen, kein Flackern. Oberer Tank zeigt sofort korrekte Fluid-Füllung proportional zu seinem Anteil.

### Test 2: Capacity im Stack (buckets)
3er-Stack leer platzieren.
- 8× Bucket Wasser → Bottom-Block optisch komplett voll, andere leer.
- 16× Bucket → Bottom + Mittel voll, Top leer.
- 24× Bucket → alle drei voll.
- `getFluidHandler().getTankCapacity(0)` = `24000 mB`.

### Test 3: Destroyed-but-invisible
Nach Test 1 prüfen: kein Geister-BE/Block. `/data get block X Y Z` gibt `null` für die entfernte Position.

### Test 4: Bridge-Case
Zwei einzelne Tanks, gleicher Fluid, je halbvoll, mit 1-Block-Lücke übereinander. Tank in die Lücke platzieren. Erwartung: alle drei mergen, Fluid = Summe beider, Capacity = `24000 mB`, Master = Bottom-Tank.

### Test 5: Split via Break-Middle
5er-Stack mit etwa `20000 mB` Fluid (=2.5 Blöcke). Mittleren (Pos 2 von unten) brechen. Erwartung:
- Obere 2 Tanks: neuer Stack, Master auf Pos 3, Capacity `16000 mB`, Fluid = Menge oberhalb des gebrochenen.
- Untere 2 Tanks: Master auf Pos 0, Capacity `16000 mB`, Fluid = Menge unterhalb des gebrochenen.
- Gebrochener Block dropped als Item mit seiner Fluid-Anteil (via `tankRetainFluids`).

### Test 6: Break-Bottom
Bottom eines 3er-Stacks brechen. Erwartung: Obere 2 werden eigenständiger 2er-Stack, Master auf Pos 1. Alle `getFluidHandler()`-Calls delegieren korrekt.

### Test 7: Break-Top
Top eines 3er-Stacks brechen. Erwartung: Unterer 2er-Stack behält Master, Capacity schrumpft auf `16000 mB`. Fluid > neue Capacity droppt mit gebrochenem Block.

### Test 8: Chunk-Reload
Stack platzieren, füllen, Welt verlassen + wieder rein. Erwartung: alle `bottomTankPos` korrekt geladen (testet `loadData`-Fix), `getFluidHandler()` funktioniert, Rendering zeigt richtige Füllung.

---

## Non-Goals

- **Cascade-Animation beim Bridge** — Im Original BC springt das Fluid beim Merge auf den Bottom. Keine Animation.
- **Pumpen-Integration** — Pumpen sind noch nicht portiert; Tests laufen via Bucket-Interaktion.
- **Horizontaler Tank-Stack** — Nur vertikales Stacking (matched Original BC).
- **Fluid-Mixing zwischen verschiedenen Fluids im selben Stack** — BlockState-Logik verhindert das bereits (siehe `updateShape`).

---

## Definition of Done

- `reformStack` Helper implementiert und einzige Quelle für `bottomTankPos` + `initTank` Updates.
- `onPlace`, `onRemove` nutzen `reformStack`.
- `TankBE.topJoined`/`bottomJoined` Instance-Fields entfernt.
- `loadData` behandelt fehlenden `bottomTankPos`-Tag korrekt.
- Alle 8 Ingame-Tests bestanden.
- `./gradlew compileJava` passes, keine neuen Log-Warnings.
