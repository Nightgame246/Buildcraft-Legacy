# Fluid Pipe Flow Rendering — Design

**Status:** Approved, ready for implementation plan
**Date:** 2026-04-12
**Scope:** Fluid pipes only (Kinesis in follow-up spec)

## Problem

Our current `FluidPipeBERenderer` zeichnet Fluid als statische Boxen pro Section, deren Größe mit der Fluid-Menge skaliert. Man sieht Füllstand, aber keine **Bewegung**. Im Original BuildCraft 1.12 wirkt Fluid als ob es aktiv durch das Rohr strömt — die Textur scrollt sichtbar in Flow-Richtung.

Ursache: Wir tracken keine Flow-Offsets und verwenden Sprite-UVs statt world-space UVs.

## Ziel

Fluid-Pipes sollen sich optisch wie im Original verhalten:

- Fluid „gleitet" sichtbar durch die Pipe in Flow-Richtung
- Richtung passt zur tatsächlichen Transport-Richtung (extraction ↔ output)
- Bei gestopptem Flow: Textur steht still, Füllung bleibt sichtbar
- Frame-Rate-unabhängig (Interpolation über `partialTick`)

## Ansatz

Approach **C** aus Brainstorming: Offset-Logik + world-space UVs, aber nicht die komplette originale `FluidRenderer`-Library nachbauen.

**Trick**: Quads werden um `offset` verschoben gezeichnet, der BufferBuilder um `-offset` translatiert. In der Welt bewegt sich nichts, aber die UVs werden aus der **originalen** (unverschobenen) Vertex-Position berechnet → Textur scrollt, während Box still steht.

## Scope

### Änderungen

- `FluidPipeBE.java` — Client-seitige Offset-Felder, Offset-Computation in `clientTick()`, neuer Accessor `getOffsetsForRender(float partialTick)`
- `FluidPipeBERenderer.java` — Offsets anwenden, world-space UV-Mapping, Horizontal/Vertikal-Center-Fix

### Nicht angefasst

- Server-Tick-Logik (3-Phasen Movement `moveFromPipe`/`moveFromCenter`/`moveToCenter`) — unverändert
- `SyncFluidPipePayload` — unverändert (Amounts + Directions reichen, Offsets sind rein client-side)
- Registry, Block-Klasse, Item, Recipes — unverändert

### Voraussetzungen

- **Separater Vor-Commit** (nicht Teil dieses Specs): `ExtractingFluidPipeBlock` muss Richtungs-Cycling auf `BCItems.WRENCH` umstellen (aktuell `useWithoutItem` mit leerer Hand). Wird für Test-Szenario 3 benötigt und stellt Konsistenz mit `IronItemPipeBlock` her.

## Datenmodell (Client-Side only)

Neue Felder in `FluidPipeBE`:

```java
private final Vec3[] offsetLast = new Vec3[7];  // [0-5 = Direction.ordinal, 6 = CENTER]
private final Vec3[] offsetThis = new Vec3[7];
```

- `offsetLast` = Offset am Ende des vorherigen Client-Ticks
- `offsetThis` = Offset nach aktuellem Tick
- Renderer interpoliert: `offsetLast * (1 - partialTick) + offsetThis * partialTick`
- Range wird durch Wrapping bei ±0.5 pro Achse in `[-0.5, +0.5]` gehalten → UV-Scroll endlos, kein Drift

### Konstante

```java
private static final double FLOW_MULTIPLIER = 0.016;  // wie Original BC
```

### NBT / Sync

- Keine NBT-Persistenz (Client-State, regeneriert sich)
- Kein Sync-Payload-Update

## Algorithmus: Client-Tick Offset

Ausgeführt pro Client-Tick in `FluidPipeBE.clientTick()`, nach dem Amount-Interpolation-Update:

```
for each section i in 0..6:
    offsetLast[i] = offsetThis[i]

    // Leere Section: sofort auf Zero
    if clientAmountThis[i] == 0 && clientAmountLast[i] == 0:
        offsetThis[i] = Vec3.ZERO
        continue

    if i == CENTER (6):
        // Flow-Richtung: vom vollsten Face zum leersten, signum-quantisiert.
        // `face.normal` = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ())
        Vec3 dir = Vec3.ZERO
        for each face in Direction.values():
            double weight = clientAmountThis[face.ordinal()] - clientAmountThis[CENTER]
            dir += face.normal * weight
        dir = (signum(dir.x), signum(dir.y), signum(dir.z))
        offsetThis[i] = offsetLast[i] + dir.scale(-FLOW_MULTIPLIER)
    else:
        // Face-Section: Offset entlang Face-Axis, Vorzeichen aus clientDirection
        // (clientDirection[i] ist der synchronisierte `ticksInDirection`: negativ=IN, positiv=OUT)
        double sign = Math.signum(clientDirection[i])
        Direction face = Direction.values()[i]
        offsetThis[i] = offsetLast[i] + face.normal * (-FLOW_MULTIPLIER * sign)

    // Wrap: crossing ±0.5 → zurück springen (auch offsetLast!)
    for each axis (x, y, z):
        if offsetThis[axis] > 0.5:
            offsetThis[axis] -= 1
            offsetLast[axis] -= 1
        else if offsetThis[axis] < -0.5:
            offsetThis[axis] += 1
            offsetLast[axis] += 1
```

**Wichtiger Punkt:** `offsetLast` muss beim Wrapping mitgezogen werden — sonst macht die partial-tick Interpolation einen Sprung.

## Renderer

### Offset + UV-Trick

Pro Section:
1. Originale Quad-Positionen berechnen (ohne Offset)
2. Offset abfragen: `Vec3 offset = getOffsetsForRender(partialTick)[sectionIdx]`
3. Quad-Vertices um `offset` verschieben
4. UVs aus **unverschobener** Vertex-Position berechnen:
   ```
   for each face:
       uvAxis1, uvAxis2 = 2D-Achsen senkrecht zur Face-Normal
       u = sprite.u0 + fractional(unverschobenePos.uvAxis1) * (sprite.u1 - sprite.u0)
       v = sprite.v0 + fractional(unverschobenePos.uvAxis2) * (sprite.v1 - sprite.v0)
   ```
5. → Textur kachelt im 1×1-Raster, erscheint weltfest → Box bewegt sich, Textur „fließt" gegen die Box-Bewegung durch

### Center-Rendering: Horizontal / Vertikal / Mixed

Aktuell: immer horizontale Box. Neu (wie Original):

- **Vertikal connected** (UP oder DOWN): Center als **vertikale Säule**
  - X/Z-Radius = `0.24 * sqrt(fill)` (Volumen-korrekte Skalierung)
  - Y = voll (0.26..0.74)
- **Horizontal connected** (N/S/W/E): Center als **horizontale Box**
  - Volle X/Z-Ausdehnung (0.26..0.74)
  - Y-Höhe = `0.48 * fill`, bottom-up
- **Gemischt** (vertikal + horizontal): **beide Teile rendern**
  - Horizontaler Teil zuerst
  - Vertikaler Teil darüber/darunter (je nach gas/liquid und vorhandenen Connections)

### Face-Sections

- **Horizontal (NSWE)**: Höhe = `0.48 * fill`, volle Achsen-Spannweite — unverändert
- **Vertikal (UP/DOWN)**: Radius = `0.24 * sqrt(fill)`, volle Y-Spannweite — unverändert

### `renderBox()` Änderung

Neuer Parameter `Vec3 uvOrigin` (unverschobene Box-Position). Vertex-Positionen um Offset verschoben, UV-Koords aus `uvOrigin`-relativen Koordinaten berechnet.

## Tests

### Ingame-Verifikation

1. **Statischer Flow**: Tank → Wooden Fluid Pipe → Cobblestone → Gold → Tank. Water fließt sichtbar durch.
2. **Stopp-Verhalten**: Ziel-Tank voll → Flow stoppt, Textur steht.
3. **Richtungswechsel**: Wrench auf Extracting-Pipe → andere Seite extracting → Flow dreht sich nach Cooldown um. (Setzt Wrench-Pre-Commit voraus.)
4. **Vertikale Pipes**: Tank oben → Pipe runter → Tank unten. Abwärts-Scroll sichtbar.
5. **T-Junction Horizontal + Vertikal**: Center zeigt beide Teile ohne Lücken.
6. **Frame-Rate**: 30 / 60 / 120 FPS — Flow-Geschwindigkeit stabil.

### Performance-Check

- Spark-Profiler, ~20 aktive Fluid-Pipes
- Erwartung: Client-Tick-Overhead vernachlässigbar (7 Vec3-Ops pro Pipe)

### Keine Unit-Tests

Reines Rendering → visuell verifizieren.

## Risiken / Offene Punkte

- **UV-Precision bei negativen Koordinaten**: `fractional()` muss negativ-sauber sein (`x - floor(x)` statt `x % 1`).
- **TextureAtlasSprite UV-Mapping**: Die NeoForge-API liefert andere UV-Ranges als 1.12 — Sprite-UV-Range muss korrekt gemappt werden.
- **Translucent-Sorting bei Overlap**: Horizontaler + vertikaler Center-Teil können sich überlappen — Reihenfolge und `RenderType.translucent()` sollten korrekt sein.
