# Tank Stack Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Fix Multi-Block-Tank Stack-Lifecycle Bugs (Destruction, Bridging, Client-Sync) durch zentralen `reformStack` Helper.

**Architecture:** `reformStack(LevelAccessor, BlockPos)` wird die einzige Stelle, die `bottomTankPos` setzt und `initTank(size)` aufruft. Von `onPlace` und `onRemove` aufgerufen. Löst: obsolete Pointer nach Break, fehlende Capacity-Rekalkulation bei Split/Bridge, Client-Flicker.

**Tech Stack:** NeoForge 1.21.1, PortingDeadLibs (`ContainerBlockEntity`, `DynamicFluidTank`). Keine Unit-Tests — Test-Strategie ist Compile-Verify + Ingame-Manual-Test.

**Testing-Strategie:** Das Projekt hat kein Unit-Test-Setup für Gameplay-Code. Jede Task-Implementierung wird per `./gradlew compileJava` verifiziert. Die finale Validierung erfolgt in Task 5 via die 8 Ingame-Szenarien aus der Spec (`docs/superpowers/specs/2026-04-12-tank-stack-fixes-design.md`).

**Spec-Referenz:** Details zu Root-Cause, Architektur, Non-Goals und Test-Szenarien stehen in der Design-Spec. Dieser Plan implementiert sie.

---

## File Structure

Alle Änderungen in zwei Dateien:

- `src/main/java/com/thepigcat/buildcraft/content/blocks/TankBlock.java` — Logik der `reformStack`-Funktion, Migration von `onPlace`/`onRemove`
- `src/main/java/com/thepigcat/buildcraft/content/blockentities/TankBE.java` — `loadData` Null-Safe machen, redundante Instance-Fields entfernen

Beide Dateien existieren. Keine neuen Files.

---

## Task 1: `reformStack` Helper + `loadData` Null-Safety

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blocks/TankBlock.java` (neue private Methode)
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/TankBE.java:100-103` (`loadData`)

Ziel: Isoliert den Helper hinzufügen, noch NICHT von `onPlace`/`onRemove` aufrufen. Existing-Code bleibt intakt für dieses Commit. Danach `loadData` gegen fehlendes `bottomTankPos`-Tag absichern.

- [x] **Step 1: `reformStack` Methode in `TankBlock.java` einfügen**

Füge am Ende der Klasse (vor dem schließenden `}`) folgende Methode ein:

```java
    /**
     * Reform a multi-block tank stack starting from `anchor`. Walks down to find
     * the bottom (where BOTTOM_JOINED is false), up to find the top (where
     * TOP_JOINED is false), then updates `bottomTankPos` on every tank in range,
     * calls `initTank(size)` on the bottom master, and forces a client sync.
     *
     * Precondition: `anchor` must be the position of an existing TankBE.
     * This is the ONLY place that mutates `bottomTankPos` or calls `initTank`.
     */
    private static void reformStack(LevelAccessor level, BlockPos anchor) {
        if (!(level.getBlockEntity(anchor) instanceof TankBE)) return;

        // 1. Walk down to find the bottom
        BlockPos bottomPos = anchor;
        while (level.getBlockState(bottomPos).getValue(BOTTOM_JOINED)) {
            bottomPos = bottomPos.below();
        }

        // 2. Walk up from bottom to find the top
        BlockPos topPos = bottomPos;
        while (level.getBlockState(topPos).getValue(TOP_JOINED)) {
            topPos = topPos.above();
        }

        int size = topPos.getY() - bottomPos.getY() + 1;

        // 3. Point every tank in range at the new bottom
        for (int y = bottomPos.getY(); y <= topPos.getY(); y++) {
            BlockPos p = new BlockPos(bottomPos.getX(), y, bottomPos.getZ());
            TankBE be = BlockUtils.getBE(TankBE.class, level, p);
            if (be != null) {
                be.setBottomTankPos(bottomPos);
            }
        }

        // 4. Initialize the master's capacity (applies pending initialFluid)
        TankBE bottomBe = BlockUtils.getBE(TankBE.class, level, bottomPos);
        if (bottomBe != null) {
            bottomBe.initTank(size);
        }

        // 5. Force explicit client sync on the bottom so non-master BEs see
        //    a consistent master before they query delegated capabilities
        if (level instanceof Level l) {
            BlockState bottomState = l.getBlockState(bottomPos);
            l.sendBlockUpdated(bottomPos, bottomState, bottomState, 3);
        }
    }
```

- [x] **Step 2: Compile verifizieren**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL, keine Warnings für `TankBlock.java`.

- [x] **Step 3: `TankBE.loadData` Null-Safe machen**

In `src/main/java/com/thepigcat/buildcraft/content/blockentities/TankBE.java:100-103` ersetze:

```java
    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadData(tag, provider);
        this.bottomTankPos = BlockPos.of(tag.getLong("bottomTankPos"));
    }
```

durch:

```java
    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadData(tag, provider);
        if (tag.contains("bottomTankPos")) {
            this.bottomTankPos = BlockPos.of(tag.getLong("bottomTankPos"));
        }
    }
```

Warum: Ohne den `contains`-Check liefert `getLong` bei fehlendem Tag `0L` → falscher `BlockPos(0,0,0)`-Pointer bei frisch geladenen Tanks.

- [x] **Step 4: Compile verifizieren**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blocks/TankBlock.java src/main/java/com/thepigcat/buildcraft/content/blockentities/TankBE.java
git commit -m "feat(tank): add reformStack helper + loadData null-safety

Introduces reformStack(LevelAccessor, BlockPos) as the single source
of truth for stack bookkeeping. Not yet wired into onPlace/onRemove.
Also fixes loadData to not misinterpret missing bottomTankPos as (0,0,0).

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 2: Migrate `onPlace` to `reformStack`

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blocks/TankBlock.java:184-245` (`onPlace`)

Ziel: Duplicate Stack-Walk-Logik in `onPlace` ersetzen durch ein Call auf `reformStack`. Die `initialFluid`-Berechnung bleibt — nur die Pointer/initTank-Abwicklung übernimmt `reformStack`.

- [x] **Step 1: `onPlace` neu schreiben**

Ersetze die komplette aktuelle `onPlace`-Methode in `TankBlock.java:184-245` durch:

```java
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        TankBE tankBE = BlockUtils.getBE(TankBE.class, level, pos);
        if (tankBE == null) return;

        FluidStack baseFluidCopy = tankBE.getFluidTank().getFluid().copy();
        int baseFluidAmount = baseFluidCopy.getAmount();

        boolean topJoined = state.getValue(TOP_JOINED);
        boolean bottomJoined = state.getValue(BOTTOM_JOINED);

        // Determine where initialFluid must be set (always on the new stack's bottom).
        // Walk down once to find bottomPos without mutating anything.
        BlockPos bottomPos = pos;
        while (level.getBlockState(bottomPos).getValue(BOTTOM_JOINED)) {
            bottomPos = bottomPos.below();
        }
        TankBE bottomTankBe = BlockUtils.getBE(TankBE.class, level, bottomPos);
        if (bottomTankBe == null) return;

        // Case A: standalone placement — no merge, just size=1 with this tank's fluid
        if (!topJoined && !bottomJoined) {
            // bottomTankBe == tankBE, initialFluid already matches current tank content.
            // No merge math needed — reformStack will call initTank(1) which preserves fluid.
            reformStack(level, pos);
            return;
        }

        // Case B: topJoined — sum in fluid from the tank(s) above the placed block
        int aboveFluidAmount = 0;
        if (topJoined) {
            TankBE aboveTank = BlockUtils.getBE(TankBE.class, level, pos.above());
            if (aboveTank != null) {
                aboveFluidAmount = aboveTank.getFluidHandler().getFluidInTank(0).getAmount();
            }
        }

        // Pick the fluid identity: prefer existing bottom-master's fluid; fall back to
        // the placed tank's fluid if master is empty.
        FluidStack existing = bottomTankBe.getFluidHandler().getFluidInTank(0);
        FluidStack fluidIdentity = existing.isEmpty() ? baseFluidCopy : existing;
        if (fluidIdentity.isEmpty() && aboveFluidAmount > 0) {
            // Master empty, placed empty, but stuff above has fluid — use the above tank's identity
            FluidStack aboveStack = BlockUtils.getBE(TankBE.class, level, pos.above()).getFluidHandler().getFluidInTank(0);
            fluidIdentity = aboveStack;
        }

        int totalAmount;
        if (topJoined && bottomJoined) {
            // Bridging: master amount already includes its stack; placed tank adds its own; above tank adds what it held before it got re-pointed
            totalAmount = existing.getAmount() + baseFluidAmount + aboveFluidAmount;
        } else if (topJoined) {
            // Placed on top of empty ground, above exists: above + placed
            totalAmount = aboveFluidAmount + baseFluidAmount;
        } else {
            // bottomJoined only: placed sits on existing stack's top — master + placed
            totalAmount = existing.getAmount() + baseFluidAmount;
        }

        if (!fluidIdentity.isEmpty()) {
            bottomTankBe.initialFluid = fluidIdentity.copyWithAmount(totalAmount);
        }

        reformStack(level, pos);
    }
```

- [x] **Step 2: Compile verifizieren**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. Keine Referenzen mehr auf `setTopJoined`/`setBottomJoined` aus `onPlace` (diese werden in Task 4 aus TankBE entfernt, sind aber aktuell noch vorhanden und wurden nur nicht mehr aufgerufen).

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blocks/TankBlock.java
git commit -m "refactor(tank): onPlace now uses reformStack

Removes duplicate stack-walk logic from onPlace. Fluid merge math
stays (sum existing + placed + above), but bottomTankPos updates and
initTank calls now go through reformStack.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 3: Extend `onRemove` to call `reformStack`

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blocks/TankBlock.java:247-259` (`onRemove`)

Ziel: Nach Fluid-Redistribute ruft `onRemove` `reformStack` für die verbleibenden Stack-Hälften auf. Das fixt die Destruction-Bugs (Flicker, falsche Capacity, Geister-Pointer).

- [x] **Step 1: `onRemove` erweitern**

Ersetze die aktuelle `onRemove`-Methode in `TankBlock.java:247-259` durch:

```java
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean blockGoingAway = !state.is(newState.getBlock());
        boolean hadTop = state.getValue(TOP_JOINED);
        boolean hadBottom = state.getValue(BOTTOM_JOINED);

        if (blockGoingAway) {
            if (hadTop && hadBottom) {
                splitTank(level, pos);
            } else if (hadTop) {
                moveFluidsAbove(level, pos);
            } else if (hadBottom) {
                removeFluidFromBottomTank(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);

        // After super.onRemove, the block is gone and updateShape has adjusted
        // TOP_JOINED/BOTTOM_JOINED on the neighbors. Reform both resulting
        // stack halves so bottomTankPos pointers and master capacity are
        // consistent.
        if (blockGoingAway) {
            if (hadTop) {
                BlockPos above = pos.above();
                if (level.getBlockEntity(above) instanceof TankBE) {
                    reformStack(level, above);
                }
            }
            if (hadBottom) {
                BlockPos below = pos.below();
                if (level.getBlockEntity(below) instanceof TankBE) {
                    reformStack(level, below);
                }
            }
        }
    }
```

Warum die Reihenfolge `super.onRemove` VOR `reformStack`: `updateShape` läuft erst nachdem der Block entfernt wurde und aktualisiert die `TOP_JOINED`/`BOTTOM_JOINED`-Properties der Nachbarn. `reformStack` verlässt sich auf diese Properties beim Walk.

- [x] **Step 2: Compile verifizieren**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blocks/TankBlock.java
git commit -m "fix(tank): onRemove reforms surviving stack halves

After a tank is broken, the existing helpers (splitTank/moveFluidsAbove/
removeFluidFromBottomTank) redistribute fluid as initialFluid on neighbors.
Now onRemove also calls reformStack on the above/below neighbor so the
surviving stacks get correct bottomTankPos pointers, recomputed capacity,
and an explicit client sync — fixes break-flicker and capacity-stays-stale
bugs.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 4: Remove Redundant `topJoined`/`bottomJoined` Fields in TankBE

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/TankBE.java:44-45,120-134`

Ziel: Die Instance-Fields `topJoined`/`bottomJoined` sind redundant — dieselbe Info steckt in der BlockState-Property. Entfernen, damit es keine zwei Sources-of-Truth gibt.

- [x] **Step 1: Prüfen ob die Felder noch von außen genutzt werden**

Run: `grep -rn "isTopJoined\|isBottomJoined\|setTopJoined\|setBottomJoined" src/main/java`
Expected: Nur Treffer innerhalb `TankBE.java` und `TankBlock.java`. Wenn Treffer in anderen Dateien: Treffer auf `getBlockState().getValue(TankBlock.TOP_JOINED/BOTTOM_JOINED)` umstellen (dieser Plan ergänzen).

- [x] **Step 2: Felder und Zugriff aus `TankBE.java` entfernen**

In `src/main/java/com/thepigcat/buildcraft/content/blockentities/TankBE.java`:

Entferne die Felder (Zeile 44-45):
```java
    private boolean topJoined;
    private boolean bottomJoined;
```

Entferne die Setter/Getter (Zeile 120-134):
```java
    public void setTopJoined(boolean topJoined) {
        this.topJoined = topJoined;
    }

    public void setBottomJoined(boolean bottomJoined) {
        this.bottomJoined = bottomJoined;
    }

    public boolean isTopJoined() {
        return topJoined;
    }

    public boolean isBottomJoined() {
        return bottomJoined;
    }
```

- [x] **Step 3: `TankBlock.updateShape` von `setTopJoined`/`setBottomJoined` säubern**

In `src/main/java/com/thepigcat/buildcraft/content/blocks/TankBlock.java:161-181` ersetze die `updateShape`-Methode durch:

```java
    @Override
    protected @NotNull BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        TankBE tankBE = BlockUtils.getBE(TankBE.class, level, pos);
        if (tankBE == null) return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
        FluidStack fluidInTank = tankBE.getFluidHandler().getFluidInTank(0);
        boolean value = neighborState.is(this);
        if (direction == Direction.UP) {
            if (value) {
                TankBE neighbor = BlockUtils.getBE(TankBE.class, level, pos.above());
                if (neighbor != null) {
                    FluidStack fluidInTank1 = neighbor.getFluidHandler().getFluidInTank(0);
                    value = fluidInTank1.is(fluidInTank.getFluid()) || fluidInTank.isEmpty() || fluidInTank1.isEmpty();
                }
            }
            return state.setValue(TOP_JOINED, value);
        } else if (direction == Direction.DOWN) {
            if (value) {
                TankBE neighbor = BlockUtils.getBE(TankBE.class, level, pos.below());
                if (neighbor != null) {
                    FluidStack fluidInTank1 = neighbor.getFluidHandler().getFluidInTank(0);
                    value = fluidInTank1.is(fluidInTank.getFluid()) || fluidInTank.isEmpty() || fluidInTank1.isEmpty();
                }
            }
            return state.setValue(BOTTOM_JOINED, value);
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }
```

Änderungen: keine `setTopJoined`/`setBottomJoined`-Calls mehr, Null-Checks für BlockEntity-Lookups hinzugefügt.

- [x] **Step 4: Compile verifizieren**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. Wenn Fehler zu unresolved `setTopJoined`/`setBottomJoined`/`isTopJoined`/`isBottomJoined` auftreten, diese Call-Sites mit `getBlockState().getValue(TankBlock.TOP_JOINED)` bzw. `...BOTTOM_JOINED` ersetzen.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/TankBE.java src/main/java/com/thepigcat/buildcraft/content/blocks/TankBlock.java
git commit -m "refactor(tank): drop redundant topJoined/bottomJoined fields

These instance fields duplicated info already in the BlockState
properties. updateShape no longer writes to the BE at all —
join state lives solely in the block state.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Task 5: Ingame Verification (8 Scenarios)

**Files:** keine Code-Änderungen — reines Testing. Bugs als Follow-up-Tasks fixen.

Alle Tests mit Default-Config: `BCConfig.tankCapacity = 8000 mB`, Bucket = `1000 mB`. Creative-Modus, Wasser-Bucket.

- [x] **Step 1: Client starten**

Run: `sh ./gradlew runClient` (Hintergrund, ~1-3 Min bis Hauptmenü).

- [x] **Step 2: Test 1 — Flicker beim Break**

Aufbau: 3er-Tank-Stack, von oben mit einigen Buckets halb füllen.
Aktion: Mittleren Tank mit Linksklick brechen.
Erwartung: Der Block verschwindet einmalig und bleibt weg (kein Wiederauftauchen/Flicker). Der obere Tank zeigt sofort seinen proportionalen Fluid-Rest korrekt. Der untere Tank zeigt seinen Rest korrekt.

- [x] **Step 3: Test 2 — Capacity im Stack (Buckets)**

Aufbau: leerer 3er-Tank-Stack.
Aktion: Mit gefülltem Wasserbucket in den Bottom-Tank rechtsklicken, 8× wiederholen.
Erwartung: Nach 8 Buckets ist der Bottom-Block optisch komplett voll, Mittel und Top leer. Nach weiteren 8 Buckets (insgesamt 16) ist auch der Mittel-Block voll. Nach weiteren 8 (insgesamt 24) ist auch der Top voll. Debug-Check optional: `/data get block X Y Z` auf den Bottom-Tank → `DynamicFluidTank`-Capacity sollte `24000` anzeigen (kann versteckt sein in PDL-Tags).

- [x] **Step 4: Test 3 — Destroyed-but-invisible**

Nach Test 1: Position des entfernten Mittel-Tanks prüfen.
Run: `/data get block X Y Z` (mit den Koordinaten des gebrochenen Blocks).
Expected: Antwort "No element of type BlockEntity..." oder Block ist Luft — KEIN TankBE-Tag mehr zurück.

- [x] **Step 5: Test 4 — Bridge-Case**

Aufbau: 2 einzelne Tanks, je halb voll mit Wasser, mit 1 Block Luft-Lücke übereinander.
Aktion: Tank (leer) in die Lücke platzieren.
Erwartung: Alle drei Tanks mergen zu einem Stack. Fluid optisch = Summe beider ursprünglicher Mengen. Wenn beide halb voll (je 4000 mB) → 8000 mB total → Bottom-Block komplett voll.

- [x] **Step 6: Test 5 — Split via Break-Middle**

Aufbau: 5er-Tank-Stack komplett leer, dann 20× Wasser-Bucket auf Bottom = 20000 mB = 2.5 Blöcke voll (Bottom und Mittel1 voll, Mittel2 halb, Top1/Top2 leer).
Aktion: Mittel2 (Pos 2 von unten) brechen.
Erwartung:
- Obere 2 Tanks (Pos 3 + 4) werden 2er-Stack, Master = Pos 3, Capacity `16000 mB`.
- Untere 2 Tanks (Pos 0 + 1) bleiben 2er-Stack, Master = Pos 0, Capacity `16000 mB`, Fluid `16000 mB` (voll).
- Der gebrochene Block droppt als Item mit seinem Fluid-Anteil (im Default `BCConfig.tankRetainFluids = true`; Tank-Item zeigt Fluid-Content im Tooltip).

- [x] **Step 7: Test 6 — Break-Bottom**

Aufbau: 3er-Tank-Stack, halb voll (12000 mB).
Aktion: Bottom-Tank brechen.
Erwartung: Obere 2 Tanks werden eigenständiger 2er-Stack. Master jetzt Pos 1 (alter Mittel-Tank, jetzt neuer Bottom). Der gebrochene (alte Master) droppt mit seinem Fluid-Anteil (`8000 mB`). Verbleibender Stack hält `4000 mB`, Bottom-Block halb voll optisch.

- [x] **Step 8: Test 7 — Break-Top**

Aufbau: 3er-Tank-Stack voll (24000 mB).
Aktion: Top-Tank brechen.
Erwartung: Unterer 2er-Stack behält Master (Bottom-Position). Capacity schrumpft auf `16000 mB`. Überschüssiges Fluid `8000 mB` droppt mit dem gebrochenen Block. Verbleibender Stack optisch komplett voll.

- [x] **Step 9: Test 8 — Chunk-Reload**

Aufbau: 3er-Stack, mit 12000 mB gefüllt.
Aktion: Welt verlassen, wieder reinjoinen.
Erwartung: Alle drei Tanks zeigen beim Wiedereintritt korrekten Füllstand (Bottom voll, Mittel halb, Top leer). Rechtsklick mit leerem Bucket auf Bottom → 1 Bucket zieht Wasser raus, Füllstand reduziert sich korrekt (kein Null-Pointer / IndexOutOfBounds in Logs).

- [x] **Step 10: Report**

Wenn alle 8 Szenarien bestanden:

```bash
git commit --allow-empty -m "test: verify tank stack fixes ingame

All 8 scenarios from plan passed: flicker-free break, correct
stack capacity (buckets), no ghost blocks, bridging merges fluids,
split/break-bottom/break-top redistribute correctly, chunk-reload
preserves stack state.

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

Bei Bugs: als Follow-up-Tasks öffnen, nicht in diesen Tasks flicken. Mögliche Stellen:

- Flicker bleibt → `reformStack` Step 5 (`sendBlockUpdated`) nicht ausgelöst, prüfe ob `level instanceof Level l` zutrifft (Server-Side sollte immer Level sein).
- Drittes Segment füllt nicht → `initTank(size)` mit falscher `size`, Walk-Range in `reformStack` Step 3 prüfen.
- Geister-Block → `super.onRemove` Aufruf-Reihenfolge prüfen in Task 3.
- Bridge merged nicht Fluid → `initialFluid` in Task 2 `onPlace` falsch gesetzt, Summation der drei Case-Äste prüfen.
- Break-Middle: Capacity der oberen Hälfte falsch → `reformStack` auf `pos.above()` prüft zuerst ob BE existiert; falls ja `walkUp` ok.
- Chunk-Reload: Pointer zeigt auf (0,0,0) → Task 1 `loadData`-Fix nicht aktiv, `tag.contains`-Check fehlt.

---

## Definition of Done

- Task 1–4 committed, `./gradlew compileJava` passes ohne neue Warnings.
- Task 5: Alle 8 Ingame-Tests bestanden, kein Flicker, Capacities korrekt, Bridge funktioniert, Chunk-Reload stabil.
- Keine Regression bei bestehenden Fluid-Pipe-Interaktionen mit Tanks.
