# Fluid Pipe Flow Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fluid-Pipes zeigen sichtbaren Flow wie im Original BuildCraft 1.12 — Textur scrollt durch die Pipe in Transport-Richtung, ohne dass sich die Pipe-Geometrie in der Welt bewegt.

**Architecture:** Client-seitige Offset-Vektoren pro Section (kein Netzwerk-Traffic). UVs werden aus `frac(localPos + offset) * spriteRange + spriteMin` berechnet → Box bleibt weltfest, Textur scrollt. Center-Rendering unterscheidet Horizontal / Vertikal / Mixed entsprechend der vorhandenen Pipe-Connections.

**Tech Stack:** NeoForge 1.21.1, `BlockEntityRenderer<T>`, `PoseStack` + `MultiBufferSource`, `TextureAtlasSprite`, `Vec3`, existing `ClientboundBlockEntityDataPacket` sync pattern.

**Spec:** `docs/superpowers/specs/2026-04-12-fluid-pipe-flow-rendering-design.md`

---

## File Structure

**Modified (2 files in scope):**
- `src/main/java/com/thepigcat/buildcraft/content/blockentities/FluidPipeBE.java` — offset state, clientTick offset computation, accessor
- `src/main/java/com/thepigcat/buildcraft/client/blockentities/FluidPipeBERenderer.java` — world-space UVs, horizontal/vertical center, offset wiring

**Prerequisite (1 file, separate concern):**
- `src/main/java/com/thepigcat/buildcraft/content/blocks/ExtractingFluidPipeBlock.java` — switch direction cycling from empty-hand to wrench, needed for Test 3

---

## Task 1: Prerequisite — Wooden Fluid Pipe Wrench Support

Vor-Commit: Extracting-Direction-Cycling läuft aktuell über `useWithoutItem` (leere Hand). Soll auf Wrench umgestellt werden, damit Test 3 (Richtungswechsel) funktioniert und das Verhalten mit `IronItemPipeBlock` konsistent ist.

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blocks/ExtractingFluidPipeBlock.java`

- [ ] **Step 1: Add wrench check using `useItemOn`, remove `useWithoutItem`**

Ersetze die existierende `useWithoutItem(...)` Methode durch eine `useItemOn(...)` Override, die nur bei Wrench reagiert. Die Cycling-Logik bleibt identisch (Kopie der Schleife von Zeile 66-108, nur der Wrapper wechselt).

Zu importieren (wenn noch nicht vorhanden):
```java
import com.thepigcat.buildcraft.registries.BCItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
```

Neue Methode (ersetzt `useWithoutItem` an Zeile 63-109):
```java
@Override
protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
    if (stack.getItem() != BCItems.WRENCH.get()) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    if (!level.isClientSide()) {
        Direction currentDir = null;
        for (Direction dir : Direction.values()) {
            if (state.getValue(CONNECTION[dir.get3DDataValue()]) == PipeState.EXTRACTING) {
                currentDir = dir;
                break;
            }
        }

        Direction nextDir = null;
        Direction[] dirs = Direction.values();
        int start = currentDir == null ? 0 : currentDir.ordinal() + 1;

        for (int i = 0; i < 6; i++) {
            Direction dir = dirs[(start + i) % 6];
            BlockPos neighborPos = pos.relative(dir);
            BlockEntity neighborBE = level.getBlockEntity(neighborPos);
            if (neighborBE != null && !(neighborBE instanceof FluidPipeBE)
                    && CapabilityUtils.fluidHandlerCapability(neighborBE, dir.getOpposite()) != null) {
                nextDir = dir;
                break;
            }
        }

        if (nextDir != null) {
            BlockState newState = state;
            for (Direction dir : Direction.values()) {
                PipeState currentType = state.getValue(CONNECTION[dir.get3DDataValue()]);
                if (currentType != PipeState.NONE) {
                    newState = newState.setValue(CONNECTION[dir.get3DDataValue()],
                            dir == nextDir ? PipeState.EXTRACTING : PipeState.CONNECTED);
                }
            }
            level.setBlock(pos, newState, 3);
            FluidPipeBE fluidBE = BlockUtils.getBE(FluidPipeBE.class, level, pos);
            if (fluidBE != null) {
                fluidBE.extracting = nextDir;
                fluidBE.setChanged();
            }
            return ItemInteractionResult.SUCCESS;
        }
    }
    return ItemInteractionResult.sidedSuccess(level.isClientSide());
}
```

- [ ] **Step 2: Compile check**

Run: `sh ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blocks/ExtractingFluidPipeBlock.java
git commit -m "fix: extracting fluid pipe direction cycling uses wrench

Matches iron item pipe behavior and original BC 1.12. Empty-hand right-click
no longer cycles suction direction."
```

---

## Task 2: FluidPipeBE — Add FLOW_MULTIPLIER constant & offset state

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/FluidPipeBE.java`

- [ ] **Step 1: Add import for `Vec3`**

Füge zum Import-Block hinzu (falls noch nicht vorhanden):
```java
import net.minecraft.world.phys.Vec3;
```

- [ ] **Step 2: Add constant and fields**

Füge direkt unter dem existierenden `private static final int CENTER = 6;` (Zeile 30) ein:
```java
private static final double FLOW_MULTIPLIER = 0.016;
```

Füge im Block der Client-Interpolations-Felder (nach Zeile 48 `private FluidStack clientFluid = ...`) hinzu:
```java
private final Vec3[] offsetLast = new Vec3[7];
private final Vec3[] offsetThis = new Vec3[7];
```

- [ ] **Step 3: Initialize offset arrays in constructor**

Erweitere den existierenden `for`-Loop im Constructor (Zeile 56-58):
```java
for (int i = 0; i < 7; i++) {
    sections[i] = new Section(i);
    offsetLast[i] = Vec3.ZERO;
    offsetThis[i] = Vec3.ZERO;
}
```

- [ ] **Step 4: Add `getOffsetsForRender()` accessor**

Füge direkt nach der existierenden `getAmountForRender(...)` Methode (nach Zeile 366) ein:
```java
public Vec3 getOffsetForRender(int sectionIndex, float partialTick) {
    Vec3 last = offsetLast[sectionIndex];
    Vec3 now = offsetThis[sectionIndex];
    return last.scale(1f - partialTick).add(now.scale(partialTick));
}
```

- [ ] **Step 5: Compile check**

Run: `sh ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/FluidPipeBE.java
git commit -m "feat(fluid-pipe): add offset state fields and accessor for flow rendering"
```

---

## Task 3: FluidPipeBE — Compute offsets in clientTick

Offset-Algorithmus pro Client-Tick nach dem Amount-Update.

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/FluidPipeBE.java` (existing `clientTick()` method, ~Zeile 147-160)

- [ ] **Step 1: Replace `clientTick()` body**

Ersetze die existierende `clientTick()` Methode vollständig mit:
```java
private void clientTick() {
    for (int i = 0; i < 7; i++) {
        clientAmountLast[i] = clientAmountThis[i];
        if (clientTarget[i] != clientAmountThis[i]) {
            int delta = clientTarget[i] - clientAmountThis[i];
            int step = Math.max(1, Math.abs(delta) / 4);
            if (delta > 0) {
                clientAmountThis[i] += step;
            } else {
                clientAmountThis[i] -= step;
            }
        }
    }

    // Offset advancement (post amount update)
    for (int i = 0; i < 7; i++) {
        offsetLast[i] = offsetThis[i];

        // Leere Section: Offset sofort zurücksetzen
        if (clientAmountThis[i] == 0 && clientAmountLast[i] == 0) {
            offsetThis[i] = Vec3.ZERO;
            continue;
        }

        double nx, ny, nz;
        if (i == CENTER) {
            // Flow-Richtung: vom vollsten Face zum leersten
            double dx = 0, dy = 0, dz = 0;
            for (Direction face : Direction.values()) {
                double weight = clientAmountThis[face.ordinal()] - clientAmountThis[CENTER];
                dx += face.getStepX() * weight;
                dy += face.getStepY() * weight;
                dz += face.getStepZ() * weight;
            }
            double sx = Math.signum(dx);
            double sy = Math.signum(dy);
            double sz = Math.signum(dz);
            nx = offsetLast[i].x + sx * -FLOW_MULTIPLIER;
            ny = offsetLast[i].y + sy * -FLOW_MULTIPLIER;
            nz = offsetLast[i].z + sz * -FLOW_MULTIPLIER;
        } else {
            // Face-Section: Offset entlang Face-Achse
            Direction face = Direction.values()[i];
            double sign = Math.signum(clientDirection[i]);
            double delta = -FLOW_MULTIPLIER * sign;
            nx = offsetLast[i].x + face.getStepX() * delta;
            ny = offsetLast[i].y + face.getStepY() * delta;
            nz = offsetLast[i].z + face.getStepZ() * delta;
        }

        // Wrap an ±0.5 (offsetLast muss mit!)
        double lx = offsetLast[i].x;
        double ly = offsetLast[i].y;
        double lz = offsetLast[i].z;
        if (nx > 0.5) { nx -= 1; lx -= 1; }
        else if (nx < -0.5) { nx += 1; lx += 1; }
        if (ny > 0.5) { ny -= 1; ly -= 1; }
        else if (ny < -0.5) { ny += 1; ly += 1; }
        if (nz > 0.5) { nz -= 1; lz -= 1; }
        else if (nz < -0.5) { nz += 1; lz += 1; }

        offsetThis[i] = new Vec3(nx, ny, nz);
        offsetLast[i] = new Vec3(lx, ly, lz);
    }
}
```

- [ ] **Step 2: Compile check**

Run: `sh ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/FluidPipeBE.java
git commit -m "feat(fluid-pipe): compute per-section flow offsets in clientTick

Offsets derived from amount deltas (center) and direction-cooldown sign
(faces), scaled by FLOW_MULTIPLIER. Wraps at ±0.5 so UV scroll loops
infinitely without geometry drift."
```

---

## Task 4: Renderer — World-space UV helper

Neue Helper-Methode die UV-Koords aus einer BE-lokalen Position + Offset berechnet. Pro Face 2 Achsen tangential zur Normale → UVs aus jenen 2 Komponenten.

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/client/blockentities/FluidPipeBERenderer.java`

- [ ] **Step 1: Add import for `Vec3`**

Falls noch nicht vorhanden:
```java
import net.minecraft.world.phys.Vec3;
```

- [ ] **Step 2: Add helper method**

Füge als private static Methode (oben in der Klasse, vor `render()`) ein:
```java
/**
 * Compute UV coordinates for a vertex at local position `(lx, ly, lz)`
 * using the 2 axes tangent to the given face normal. Offset shifts the
 * sampled position so the texture scrolls while geometry stays still.
 */
private static void writeVertex(VertexConsumer vc, Matrix4f pose,
                                 float lx, float ly, float lz,
                                 Vec3 offset, TextureAtlasSprite sprite,
                                 Direction.Axis normalAxis,
                                 float r, float g, float b, float a,
                                 int light, int overlay,
                                 float nx, float ny, float nz) {
    double ax, ay;
    // Pick the two tangent axes (u-axis, v-axis) based on face normal
    switch (normalAxis) {
        case Y -> { ax = lx + offset.x; ay = lz + offset.z; }
        case X -> { ax = lz + offset.z; ay = ly + offset.y; }
        case Z -> { ax = lx + offset.x; ay = ly + offset.y; }
        default -> throw new IllegalStateException();
    }
    // frac handles negatives correctly: x - floor(x)
    double fu = ax - Math.floor(ax);
    double fv = ay - Math.floor(ay);
    float u = sprite.getU0() + (float) fu * (sprite.getU1() - sprite.getU0());
    float v = sprite.getV0() + (float) fv * (sprite.getV1() - sprite.getV0());
    vc.addVertex(pose, lx, ly, lz)
            .setColor(r, g, b, a)
            .setUv(u, v)
            .setOverlay(overlay)
            .setLight(light)
            .setNormal(nx, ny, nz);
}
```

- [ ] **Step 3: Compile check**

Run: `sh ./gradlew compileJava`
Expected: BUILD SUCCESSFUL (helper is used in next task).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/client/blockentities/FluidPipeBERenderer.java
git commit -m "refactor(fluid-pipe-render): add world-space UV vertex helper"
```

---

## Task 5: Renderer — Rewrite `renderBox` using UV helper

`renderBox` wird umgebaut: nimmt zusätzlich einen `Vec3 offset` und nutzt den neuen `writeVertex` Helper.

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/client/blockentities/FluidPipeBERenderer.java` (existing `renderBox`, ~Zeile 133-177)

- [ ] **Step 1: Replace `renderBox` method**

Ersetze die komplette bestehende `renderBox(...)` Methode mit:
```java
private void renderBox(Matrix4f pose, VertexConsumer vc, TextureAtlasSprite sprite,
                       float x0, float x1, float y0, float y1, float z0, float z1,
                       Vec3 offset,
                       float r, float g, float b, float a,
                       int light, int overlay) {
    // Down face (y = y0), normal 0,-1,0
    writeVertex(vc, pose, x0, y0, z0, offset, sprite, Direction.Axis.Y, r, g, b, a, light, overlay, 0, -1, 0);
    writeVertex(vc, pose, x1, y0, z0, offset, sprite, Direction.Axis.Y, r, g, b, a, light, overlay, 0, -1, 0);
    writeVertex(vc, pose, x1, y0, z1, offset, sprite, Direction.Axis.Y, r, g, b, a, light, overlay, 0, -1, 0);
    writeVertex(vc, pose, x0, y0, z1, offset, sprite, Direction.Axis.Y, r, g, b, a, light, overlay, 0, -1, 0);

    // Up face (y = y1), normal 0,+1,0
    writeVertex(vc, pose, x0, y1, z1, offset, sprite, Direction.Axis.Y, r, g, b, a, light, overlay, 0, 1, 0);
    writeVertex(vc, pose, x1, y1, z1, offset, sprite, Direction.Axis.Y, r, g, b, a, light, overlay, 0, 1, 0);
    writeVertex(vc, pose, x1, y1, z0, offset, sprite, Direction.Axis.Y, r, g, b, a, light, overlay, 0, 1, 0);
    writeVertex(vc, pose, x0, y1, z0, offset, sprite, Direction.Axis.Y, r, g, b, a, light, overlay, 0, 1, 0);

    // North face (z = z0), normal 0,0,-1
    writeVertex(vc, pose, x0, y0, z0, offset, sprite, Direction.Axis.Z, r, g, b, a, light, overlay, 0, 0, -1);
    writeVertex(vc, pose, x0, y1, z0, offset, sprite, Direction.Axis.Z, r, g, b, a, light, overlay, 0, 0, -1);
    writeVertex(vc, pose, x1, y1, z0, offset, sprite, Direction.Axis.Z, r, g, b, a, light, overlay, 0, 0, -1);
    writeVertex(vc, pose, x1, y0, z0, offset, sprite, Direction.Axis.Z, r, g, b, a, light, overlay, 0, 0, -1);

    // South face (z = z1), normal 0,0,+1
    writeVertex(vc, pose, x1, y0, z1, offset, sprite, Direction.Axis.Z, r, g, b, a, light, overlay, 0, 0, 1);
    writeVertex(vc, pose, x1, y1, z1, offset, sprite, Direction.Axis.Z, r, g, b, a, light, overlay, 0, 0, 1);
    writeVertex(vc, pose, x0, y1, z1, offset, sprite, Direction.Axis.Z, r, g, b, a, light, overlay, 0, 0, 1);
    writeVertex(vc, pose, x0, y0, z1, offset, sprite, Direction.Axis.Z, r, g, b, a, light, overlay, 0, 0, 1);

    // West face (x = x0), normal -1,0,0
    writeVertex(vc, pose, x0, y0, z1, offset, sprite, Direction.Axis.X, r, g, b, a, light, overlay, -1, 0, 0);
    writeVertex(vc, pose, x0, y1, z1, offset, sprite, Direction.Axis.X, r, g, b, a, light, overlay, -1, 0, 0);
    writeVertex(vc, pose, x0, y1, z0, offset, sprite, Direction.Axis.X, r, g, b, a, light, overlay, -1, 0, 0);
    writeVertex(vc, pose, x0, y0, z0, offset, sprite, Direction.Axis.X, r, g, b, a, light, overlay, -1, 0, 0);

    // East face (x = x1), normal +1,0,0
    writeVertex(vc, pose, x1, y0, z0, offset, sprite, Direction.Axis.X, r, g, b, a, light, overlay, 1, 0, 0);
    writeVertex(vc, pose, x1, y1, z0, offset, sprite, Direction.Axis.X, r, g, b, a, light, overlay, 1, 0, 0);
    writeVertex(vc, pose, x1, y1, z1, offset, sprite, Direction.Axis.X, r, g, b, a, light, overlay, 1, 0, 0);
    writeVertex(vc, pose, x1, y0, z1, offset, sprite, Direction.Axis.X, r, g, b, a, light, overlay, 1, 0, 0);
}
```

- [ ] **Step 2: Compile check**

Run: `sh ./gradlew compileJava`
Expected: BUILD FAILS — callers still pass old signature without `offset`. Das ist erwartet, wir fixen im nächsten Step.

- [ ] **Step 3: Update caller in `render()` — center box**

Finde im `render()` die Zeile (ca. Zeile 68):
```java
renderBox(pose, vc, sprite, 0.26f, 0.74f, 0.26f, height, 0.26f, 0.74f,
        r, g, b, a, packedLight, packedOverlay);
```
Ersetze durch:
```java
Vec3 centerOffset = be.getOffsetForRender(6, partialTick);
renderBox(pose, vc, sprite, 0.26f, 0.74f, 0.26f, height, 0.26f, 0.74f,
        centerOffset, r, g, b, a, packedLight, packedOverlay);
```

- [ ] **Step 4: Update caller in `renderConnectionFluid()` — face boxes**

Die `renderConnectionFluid` Methode ruft `renderBox` 6× auf (einmal pro Achsen-Fall). Jeder Call braucht den Section-Offset zusätzlich. Ersetze die komplette `renderConnectionFluid` Methode mit:

```java
private void renderConnectionFluid(Matrix4f pose, VertexConsumer vc, TextureAtlasSprite sprite,
                                    Direction dir, float fill, Vec3 offset,
                                    float r, float g, float b, float a,
                                    int light, int overlay) {
    float pipeInner = 0.24f;

    switch (dir.getAxis()) {
        case X -> {
            float halfH = pipeInner * fill;
            float yMin = 0.5f - halfH;
            float yMax = 0.5f + halfH;
            float zMin = 0.5f - pipeInner;
            float zMax = 0.5f + pipeInner;
            if (dir == Direction.WEST) {
                renderBox(pose, vc, sprite, 0f, 0.26f, yMin, yMax, zMin, zMax, offset, r, g, b, a, light, overlay);
            } else {
                renderBox(pose, vc, sprite, 0.74f, 1f, yMin, yMax, zMin, zMax, offset, r, g, b, a, light, overlay);
            }
        }
        case Z -> {
            float halfH = pipeInner * fill;
            float yMin = 0.5f - halfH;
            float yMax = 0.5f + halfH;
            float xMin = 0.5f - pipeInner;
            float xMax = 0.5f + pipeInner;
            if (dir == Direction.NORTH) {
                renderBox(pose, vc, sprite, xMin, xMax, yMin, yMax, 0f, 0.26f, offset, r, g, b, a, light, overlay);
            } else {
                renderBox(pose, vc, sprite, xMin, xMax, yMin, yMax, 0.74f, 1f, offset, r, g, b, a, light, overlay);
            }
        }
        case Y -> {
            float radius = pipeInner * (float) Math.sqrt(fill);
            float xMin = 0.5f - radius;
            float xMax = 0.5f + radius;
            float zMin = 0.5f - radius;
            float zMax = 0.5f + radius;
            if (dir == Direction.DOWN) {
                renderBox(pose, vc, sprite, xMin, xMax, 0f, 0.26f, zMin, zMax, offset, r, g, b, a, light, overlay);
            } else {
                renderBox(pose, vc, sprite, xMin, xMax, 0.74f, 1f, zMin, zMax, offset, r, g, b, a, light, overlay);
            }
        }
    }
}
```

- [ ] **Step 5: Update call-site von `renderConnectionFluid`**

Finde in `render()` die Face-Section-Schleife (ca. Zeile 73-82) und ersetze:
```java
for (Direction dir : Direction.values()) {
    PipeBlock.PipeState pipeState = state.getValue(PipeBlock.CONNECTION[dir.get3DDataValue()]);
    if (pipeState == PipeBlock.PipeState.NONE) continue;

    double amount = be.getAmountForRender(dir.ordinal(), partialTick);
    if (amount <= 0) continue;

    float fill = (float) Math.min(1.0, amount / capacity);
    renderConnectionFluid(pose, vc, sprite, dir, fill, r, g, b, a, packedLight, packedOverlay);
}
```
Mit:
```java
for (Direction dir : Direction.values()) {
    PipeBlock.PipeState pipeState = state.getValue(PipeBlock.CONNECTION[dir.get3DDataValue()]);
    if (pipeState == PipeBlock.PipeState.NONE) continue;

    double amount = be.getAmountForRender(dir.ordinal(), partialTick);
    if (amount <= 0) continue;

    float fill = (float) Math.min(1.0, amount / capacity);
    Vec3 faceOffset = be.getOffsetForRender(dir.ordinal(), partialTick);
    renderConnectionFluid(pose, vc, sprite, dir, fill, faceOffset, r, g, b, a, packedLight, packedOverlay);
}
```

- [ ] **Step 6: Compile check**

Run: `sh ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/client/blockentities/FluidPipeBERenderer.java
git commit -m "feat(fluid-pipe-render): apply per-section flow offsets via world-space UVs

Texture now scrolls through pipes in flow direction. Geometry stays world-stable
via frac(localPos + offset) UV sampling — box positions unchanged."
```

---

## Task 6: Renderer — Horizontal / Vertical / Mixed Center

Aktuell rendert das Center IMMER als horizontale Box (Höhe skaliert mit fill). Für vertikale Pipes muss es eine `sqrt(fill)`-Radius Säule sein. Bei T-Junctions: beides rendern.

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/client/blockentities/FluidPipeBERenderer.java` (center block in `render()`, ~Zeile 60-70)

- [ ] **Step 1: Replace center rendering block**

Finde in `render()` den Center-Rendering-Block:
```java
// Render center section (index 6)
double centerAmount = be.getAmountForRender(6, partialTick);
if (centerAmount > 0) {
    float fill = (float) Math.min(1.0, centerAmount / capacity);
    float halfSize = 0.24f * fill;
    float min = 0.5f - halfSize;
    float max = 0.5f + halfSize;
    // Render fill as height for center
    float height = 0.26f + (0.74f - 0.26f) * fill;
    renderBox(pose, vc, sprite, 0.26f, 0.74f, 0.26f, height, 0.26f, 0.74f,
            r, g, b, a, packedLight, packedOverlay);
}
```

Ersetze durch:
```java
// Render center section (index 6) — horizontal box, vertical column, or both
double centerAmount = be.getAmountForRender(6, partialTick);
if (centerAmount > 0) {
    float fill = (float) Math.min(1.0, centerAmount / (double) capacity);
    Vec3 centerOffset = be.getOffsetForRender(6, partialTick);

    boolean horizontal = false;
    for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
        if (state.getValue(PipeBlock.CONNECTION[d.get3DDataValue()]) != PipeBlock.PipeState.NONE) {
            horizontal = true;
            break;
        }
    }
    boolean vertical = state.getValue(PipeBlock.CONNECTION[Direction.UP.get3DDataValue()]) != PipeBlock.PipeState.NONE
            || state.getValue(PipeBlock.CONNECTION[Direction.DOWN.get3DDataValue()]) != PipeBlock.PipeState.NONE;

    // Fallback: no connections at all — fall through to a horizontal box for visual continuity
    if (!horizontal && !vertical) horizontal = true;

    float horizTop = 0.26f;  // where the horizontal fill ends (for mixed case)
    if (horizontal) {
        // Horizontal: volle X/Z Spannweite, Höhe skaliert mit fill
        float height = 0.26f + (0.74f - 0.26f) * fill;
        horizTop = height;
        renderBox(pose, vc, sprite, 0.26f, 0.74f, 0.26f, height, 0.26f, 0.74f,
                centerOffset, r, g, b, a, packedLight, packedOverlay);
    }

    if (vertical && horizTop < 0.74f) {
        // Vertikal: Radius X/Z = 0.24 * sqrt(fill), Y von horizTop bis 0.74
        float radius = 0.24f * (float) Math.sqrt(fill);
        float minXZ = 0.5f - radius;
        float maxXZ = 0.5f + radius;
        float yMin = horizontal ? horizTop : 0.26f;
        float yMax = 0.74f;
        renderBox(pose, vc, sprite, minXZ, maxXZ, yMin, yMax, minXZ, maxXZ,
                centerOffset, r, g, b, a, packedLight, packedOverlay);
    }
}
```

- [ ] **Step 2: Remove the redundant old `centerOffset` lookup**

In Task 5 Step 3 haben wir oben bereits `Vec3 centerOffset = be.getOffsetForRender(6, partialTick);` eingefügt. In Step 1 dieser Task wird die Variable innerhalb des neuen Blocks neu deklariert — dadurch gibt's einen Konflikt. Entferne die ältere Zeile (direkt über dem neuen Block), sodass nur noch die Version innerhalb des `if (centerAmount > 0)` Blocks existiert.

- [ ] **Step 3: Compile check**

Run: `sh ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/client/blockentities/FluidPipeBERenderer.java
git commit -m "feat(fluid-pipe-render): horizontal/vertical/mixed center rendering

Vertical pipes render center as sqrt(fill)-radius column instead of
horizontal box. T-junctions render both pieces seamlessly. Matches
original BC 1.12 behavior."
```

---

## Task 7: Ingame Verification

Visuelle Tests gegen Spec Section „Tests".

**Files:** keine Code-Änderungen — reines Testing. Bugs ggf. in neuen Follow-up-Tasks fixen.

- [ ] **Step 1: Start client**

Run: `sh ./gradlew runClient` (im Hintergrund, Client-Start ~1-3 Min).

- [ ] **Step 2: Test Scenario 1 — statischer Flow**

Baue: Water-Tank → Wooden Fluid Pipe (wrench auf Tank-Seite für Extraction) → 3× Cobblestone Fluid Pipe → Gold Fluid Pipe → Leer-Tank.
Erwartung: Sichtbarer Wasser-Scroll durch alle Pipes vom Quell-Tank Richtung Ziel-Tank.

- [ ] **Step 3: Test Scenario 2 — stopp-Verhalten**

Fülle den Ziel-Tank komplett → beobachte. Erwartung: Flow stoppt innerhalb weniger Sekunden, Textur steht still, Fluid-Füllung bleibt sichtbar.

- [ ] **Step 4: Test Scenario 3 — Richtungswechsel**

Leere Ziel-Tank. Wrench auf Wooden Pipe → Extraction-Seite ändern. Erwartung: Nach Direction-Cooldown (~3 Sek) kehrt Flow-Richtung optisch um.

- [ ] **Step 5: Test Scenario 4 — vertikale Pipes**

Tank oben → 3× Fluid Pipe vertikal → Tank unten. Erwartung: Fluid scrollt sichtbar nach unten in der Säule.

- [ ] **Step 6: Test Scenario 5 — T-Junction horizontal + vertikal**

Zentrale Fluid Pipe mit Connections zu NORTH, SOUTH und DOWN. Fluid läuft durch. Erwartung: Center zeigt horizontale Box + vertikale Säule ohne sichtbare Lücke oder Z-Fighting.

- [ ] **Step 7: Test Scenario 6 — Frame-Rate**

`/tick rate 20` (default) → beobachte Flow-Speed. Dann VSync aus/an → Flow-Speed sollte visuell identisch bleiben.

- [ ] **Step 8: Performance-Sanity**

F3-Overlay: TPS stabil bei 20, FPS nicht eingebrochen bei ~10 aktiven Fluid-Pipes im Blick.

- [ ] **Step 9: Report**

Wenn alle Szenarien OK: commit-Nachricht "test: verify fluid pipe flow rendering ingame" (leerer Commit akzeptabel mit `--allow-empty`) oder einfach direkt das Feature als abgeschlossen melden.

Wenn Bugs auftreten:
- Textur wackelt statt zu scrollen → UV-Mapping-Fehler in Task 4, Face-Axen-Mapping checken
- Textur scrollt falsche Richtung → `sign` Vorzeichen in Task 3 drehen
- Center zeigt Lücken bei T-Junction → `horizTop` Berechnung in Task 6 prüfen
- Flow läuft auch bei leerer Section → leere-Section Reset in Task 3 prüfen
- Drift (Textur springt) → Wrapping inkonsistent für `offsetLast` in Task 3 prüfen

---

## Definition of Done

- Task 1–6 committed, `./gradlew compileJava` passes
- Alle 6 Ingame-Szenarien bestanden
- Keine neuen Log-Warnings/Errors im Client
- TPS/FPS stabil bei Fluid-Pipe-Networks
