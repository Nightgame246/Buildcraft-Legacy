# Phase B — Item Pipes Speziallogik: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Phase B abschliessen — Gold, Obsidian, Daizuli, Emerald Pipes auf originalgetreue Kernmechanik bringen; Diamond Pipe als TODO dokumentieren.

**Architecture:** Jede Spezial-Pipe bekommt eine eigene BE-Klasse (kein Hardcoding in Base). Block-Klassen werden nur erstellt wenn noetig (eigene Connection-Logik oder Wrench-Interaktion). Gate-Hooks werden als erweiterbare Methoden vorbereitet.

**Tech Stack:** NeoForge 1.21.1, Java 21, PDL (PortingDeadLibs)

**Testing:** Kein Unit-Test-Framework vorhanden. Testen via `./gradlew build` (Kompilierung) und manuell ingame via `./gradlew runClient`.

---

## File Structure

### New Files
- `src/main/java/com/thepigcat/buildcraft/content/blockentities/GoldItemPipeBE.java` — Gold Pipe BE with speed boost logic
- `src/main/java/com/thepigcat/buildcraft/content/blocks/GoldItemPipeBlock.java` — Gold Pipe Block returning correct BE type

### Modified Files
- `src/main/java/com/thepigcat/buildcraft/content/blockentities/ItemPipeBE.java` — Remove hardcoded gold speed logic
- `src/main/java/com/thepigcat/buildcraft/registries/BCBlockEntities.java` — Add GOLD_ITEM_PIPE BlockEntityType
- `src/main/java/com/thepigcat/buildcraft/registries/BCPipeTypes.java` — Add GOLD PipeType
- `src/main/java/com/thepigcat/buildcraft/registries/BCPipes.java` — Update GOLD to use new PipeType
- `src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java` — Register Gold capability
- `src/main/java/com/thepigcat/buildcraft/content/blockentities/ObsidianItemPipeBE.java` — Single-face suction enforcement
- `src/main/java/com/thepigcat/buildcraft/content/blockentities/DaizuliItemPipeBE.java` — Fix color routing logic, add Gate hook
- `src/main/java/com/thepigcat/buildcraft/content/blocks/DaizuliItemPipeBlock.java` — Add blocked state visualization
- `src/main/java/com/thepigcat/buildcraft/content/blockentities/EmeraldItemPipeBE.java` — Add FilterMode enum + blacklist logic
- `src/main/java/com/thepigcat/buildcraft/content/menus/EmeraldPipeMenu.java` — Add filterMode data slot
- `src/main/java/com/thepigcat/buildcraft/client/screens/EmeraldPipeScreen.java` — Add toggle button
- `src/main/java/com/thepigcat/buildcraft/networking/ToggleFilterModePayload.java` — Client->Server payload for filter toggle
- `TODO_NEXT_SESSION.md` — Update Phase B status

---

## Task 1: Gold Pipe — Eigene BE-Klasse

**Files:**
- Create: `src/main/java/com/thepigcat/buildcraft/content/blockentities/GoldItemPipeBE.java`
- Create: `src/main/java/com/thepigcat/buildcraft/content/blocks/GoldItemPipeBlock.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/ItemPipeBE.java:112-127`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCBlockEntities.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCPipeTypes.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCPipes.java:34`
- Modify: `src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java:108`

- [ ] **Step 1: Create GoldItemPipeBE**

Create `src/main/java/com/thepigcat/buildcraft/content/blockentities/GoldItemPipeBE.java`:

```java
package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class GoldItemPipeBE extends ItemPipeBE {
    private static final float SPEED_TARGET = 0.25f;
    private static final float SPEED_DELTA = 0.07f;

    public GoldItemPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.GOLD_ITEM_PIPE.get(), pos, blockState);
    }

    @Override
    public void tick() {
        if (!this.itemHandler.getStackInSlot(0).isEmpty()) {
            // Gold pipe actively accelerates items (original BC PipeBehaviourGold values)
            if (itemSpeed < SPEED_TARGET) {
                itemSpeed = Math.min(itemSpeed + SPEED_DELTA, SPEED_TARGET);
            }
        }
        super.tick();
    }
}
```

- [ ] **Step 2: Create GoldItemPipeBlock**

Create `src/main/java/com/thepigcat/buildcraft/content/blocks/GoldItemPipeBlock.java`:

```java
package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class GoldItemPipeBlock extends ItemPipeBlock {
    public GoldItemPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(GoldItemPipeBlock::new);
    }

    @Override
    protected BlockEntityType<? extends PipeBlockEntity<?>> getBlockEntityType() {
        return BCBlockEntities.GOLD_ITEM_PIPE.get();
    }
}
```

- [ ] **Step 3: Register BlockEntityType in BCBlockEntities**

In `BCBlockEntities.java`, add after the `DAIZULI_ITEM_PIPE` line (line 39):

```java
public static final Supplier<BlockEntityType<GoldItemPipeBE>> GOLD_ITEM_PIPE = BLOCK_ENTITIES.register("gold_item_pipe",
        () -> BlockEntityType.Builder.of(GoldItemPipeBE::new, collectBlocks(GoldItemPipeBlock.class)).build(null));
```

Add import: `import com.thepigcat.buildcraft.content.blocks.GoldItemPipeBlock;`

- [ ] **Step 4: Register PipeType in BCPipeTypes**

In `BCPipeTypes.java`, add after the DAIZULI line (line 57):

```java
public static final PipeTypeHolder<GoldItemPipeBlock, ItemPipeBlockItem> GOLD = HELPER.registerPipeType("gold", GoldItemPipeBlock::new, ItemPipeBlockItem::new,
        ModelUtils.DEFAULT_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE, ModelUtils.DEFAULT_ITEM_MODEL_FILE,
        "base", "connection");
```

Add import: `import com.thepigcat.buildcraft.content.blocks.GoldItemPipeBlock;`

- [ ] **Step 5: Update BCPipes.GOLD to use new PipeType**

In `BCPipes.java`, change line 34 from `BCPipeTypes.DEFAULT` to `BCPipeTypes.GOLD`:

```java
public static final PipeHolder GOLD = HELPER.registerPipe("gold", BCPipeTypes.GOLD, "Gold Pipe", 0.5f, List.of(
```

- [ ] **Step 6: Register capability in BuildcraftLegacy**

In `BuildcraftLegacy.java`, add after line 116 (DAIZULI capability):

```java
event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BCBlockEntities.GOLD_ITEM_PIPE.get(), ItemPipeBE::getItemHandler);
```

- [ ] **Step 7: Remove hardcoded gold logic from ItemPipeBE**

In `ItemPipeBE.java`, replace lines 112-114:

```java
if (pipeId.equals("gold_pipe")) {
    // Active acceleration
    itemSpeed = Math.min(itemSpeed + 0.01f, 0.25f);
} else if (pipeId.equals("stone_pipe")) {
```

With:

```java
if (pipeId.equals("stone_pipe")) {
```

Note: The `pipeId` variable and other pipe-specific friction logic stays (stone, cobblestone, sandstone). Only the gold block is removed since `GoldItemPipeBE.tick()` handles it before calling `super.tick()`.

- [ ] **Step 8: Build and verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/GoldItemPipeBE.java \
       src/main/java/com/thepigcat/buildcraft/content/blocks/GoldItemPipeBlock.java \
       src/main/java/com/thepigcat/buildcraft/content/blockentities/ItemPipeBE.java \
       src/main/java/com/thepigcat/buildcraft/registries/BCBlockEntities.java \
       src/main/java/com/thepigcat/buildcraft/registries/BCPipeTypes.java \
       src/main/java/com/thepigcat/buildcraft/registries/BCPipes.java \
       src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java
git commit -m "refactor: extract Gold Pipe into own BE class, remove hardcoded speed logic from ItemPipeBE"
```

---

## Task 2: Obsidian Pipe — Single-Connection-Enforcement

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/ObsidianItemPipeBE.java`

Note: Obsidian-to-Obsidian blocking already works in `ObsidianItemPipeBlock.getConnectionType()`.

- [ ] **Step 1: Add getOpenFace() and enforce single-face suction**

Replace the entire `ObsidianItemPipeBE.java` with:

```java
package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.networking.SyncPipeDirectionPayload;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

public class ObsidianItemPipeBE extends ItemPipeBE {
    private static final int SUCTION_COOLDOWN_TICKS = 10;
    private int suctionCooldown = 0;

    public ObsidianItemPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.OBSIDIAN_ITEM_PIPE.get(), pos, blockState);
    }

    /**
     * Returns the single open face of this pipe, or null if there are 0 or 2+ open faces.
     * Original BC: Obsidian pipe only sucks items when exactly one face is open.
     */
    public Direction getOpenFace() {
        Direction openFace = null;
        for (Direction dir : Direction.values()) {
            if (!directions.contains(dir)) {
                if (openFace != null) {
                    return null; // 2+ open faces -> no suction
                }
                openFace = dir;
            }
        }
        return openFace; // null if all 6 sides connected (0 open faces)
    }

    @Override
    public void tick() {
        if (!level.isClientSide()) {
            if (suctionCooldown > 0) {
                suctionCooldown--;
            } else if (itemHandler.getStackInSlot(0).isEmpty()) {
                Direction openFace = getOpenFace();
                if (openFace != null) {
                    AABB aabb = new AABB(worldPosition.relative(openFace)).inflate(0.5);
                    List<ItemEntity> itemEntities = level.getEntitiesOfClass(ItemEntity.class, aabb);
                    for (ItemEntity itemEntity : itemEntities) {
                        if (itemEntity.isRemoved()) continue;

                        ItemStack stack = itemEntity.getItem();
                        ItemStack remainder = itemHandler.insertItem(0, stack, false);
                        if (remainder.getCount() < stack.getCount()) {
                            if (remainder.isEmpty()) {
                                itemEntity.discard();
                            } else {
                                itemEntity.setItem(remainder);
                            }

                            // Route item from open face into the pipe network
                            if (!directions.isEmpty()) {
                                this.setFrom(openFace);
                                this.setTo(chooseDirection(directions));
                                PacketDistributor.sendToAllPlayers(new SyncPipeDirectionPayload(worldPosition, Optional.ofNullable(from), Optional.ofNullable(to)));
                            }
                            suctionCooldown = SUCTION_COOLDOWN_TICKS;
                            break;
                        }
                    }
                }
            }
        }

        super.tick();
    }
}
```

Key changes from current code:
- `getOpenFace()` returns exactly ONE open face, or null (enforces single-opening)
- Suction only happens on the single open face (no more looping over all open sides)
- Constant `SUCTION_COOLDOWN_TICKS = 10` instead of magic number
- `chooseDirection(directions)` passes ALL connected directions (not filtered), since from = openFace and openFace is NOT in directions

- [ ] **Step 2: Build and verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/ObsidianItemPipeBE.java
git commit -m "fix: enforce single-face suction for Obsidian Pipe (original BC behavior)"
```

---

## Task 3: Daizuli Pipe — Directional + Color Routing Fix

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/DaizuliItemPipeBE.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blocks/DaizuliItemPipeBlock.java`

The block already has wrench interaction (center = color cycle, face = set target). We need to:
1. Fix the routing logic to match original BC
2. Add blocked/connected visual states for target direction
3. Add Gate hook via `getActiveColor()`

- [ ] **Step 1: Fix DaizuliItemPipeBE routing logic**

Replace `DaizuliItemPipeBE.java` with:

```java
package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.util.ItemUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DaizuliItemPipeBE extends ItemPipeBE implements ColouredPipe {
    private DyeColor pipeColor = DyeColor.WHITE;
    private Direction targetDirection;

    public DaizuliItemPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.DAIZULI_ITEM_PIPE.get(), pos, blockState);
    }

    /**
     * Original BC routing: PipeBehaviourDaizuli.sideCheck()
     * - If item color matches pipe color -> ONLY route to targetDirection
     * - If item color does NOT match (or has no color) -> EXCLUDE targetDirection, allow all others
     */
    @Override
    protected Direction chooseDirection(Set<Direction> availableDirections) {
        if (availableDirections.isEmpty()) {
            return from;
        }

        ItemStack carried = itemHandler.getStackInSlot(0);
        DyeColor activeColor = getActiveColor();

        if (activeColor != null && targetDirection != null) {
            DyeColor itemColor = ItemUtils.getItemColor(carried);

            if (activeColor.equals(itemColor)) {
                // Color matches -> route to target direction only
                if (availableDirections.contains(targetDirection)) {
                    return targetDirection;
                }
                // Target not available, fall back to from (bounce back)
                return from;
            } else {
                // Color doesn't match (or no color) -> exclude target, route to any other
                List<Direction> candidates = new ArrayList<>(availableDirections);
                candidates.remove(targetDirection);
                if (!candidates.isEmpty()) {
                    return candidates.get(level.random.nextInt(candidates.size()));
                }
                // All directions are the target -> fall through to default
            }
        }

        return super.chooseDirection(availableDirections);
    }

    /**
     * Gate hook: returns the active color for routing decisions.
     * Gates can override this in Phase E to set the color dynamically.
     */
    public DyeColor getActiveColor() {
        return pipeColor;
    }

    public void cyclePipeColor() {
        setPipeColor(DyeColor.byId((pipeColor.getId() + 1) % DyeColor.values().length));
    }

    public void setPipeColor(DyeColor pipeColor) {
        this.pipeColor = pipeColor;
        notifyConfigChanged();
    }

    public Direction getTargetDirection() {
        return targetDirection;
    }

    public void setTargetDirection(Direction targetDirection) {
        this.targetDirection = targetDirection;
        updateBlockedDirections();
        notifyConfigChanged();
    }

    @Override
    public DyeColor getPipeColor() {
        return pipeColor;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (targetDirection == null && !directions.isEmpty()) {
            targetDirection = directions.iterator().next();
        }
        updateBlockedDirections();
    }

    @Override
    public void setDirections(Set<Direction> directions) {
        super.setDirections(directions);
        if (targetDirection == null && !directions.isEmpty()) {
            targetDirection = directions.iterator().next();
        }
        updateBlockedDirections();
    }

    private void updateBlockedDirections() {
        this.blocked.clear();
        if (targetDirection != null) {
            for (Direction dir : directions) {
                if (dir != targetDirection) {
                    this.blocked.add(dir);
                }
            }
        }

        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            boolean changed = false;
            for (Direction dir : Direction.values()) {
                PipeBlock.PipeState currentPipeState = state.getValue(PipeBlock.CONNECTION[dir.get3DDataValue()]);
                if (currentPipeState != PipeBlock.PipeState.NONE) {
                    PipeBlock.PipeState target = (dir == targetDirection) ? PipeBlock.PipeState.BLOCKED : PipeBlock.PipeState.CONNECTED;
                    if (currentPipeState != target) {
                        state = state.setValue(PipeBlock.CONNECTION[dir.get3DDataValue()], target);
                        changed = true;
                    }
                }
            }
            if (changed) {
                level.setBlock(worldPosition, state, 3);
            }
        }
    }

    private void notifyConfigChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.pipeColor = DyeColor.byId(tag.getInt("pipe_color"));
        this.targetDirection = tag.contains("target_direction") ? Direction.values()[tag.getInt("target_direction")] : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("pipe_color", this.pipeColor.getId());
        if (this.targetDirection != null) {
            tag.putInt("target_direction", this.targetDirection.ordinal());
        }
    }
}
```

- [ ] **Step 2: Update DaizuliItemPipeBlock for BLOCKED state support**

Replace `DaizuliItemPipeBlock.java` with:

```java
package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.content.blockentities.DaizuliItemPipeBE;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.registries.BCItems;
import com.thepigcat.buildcraft.util.BlockUtils;
import com.thepigcat.buildcraft.util.CapabilityUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class DaizuliItemPipeBlock extends ItemPipeBlock {
    private static final double CENTER_THRESHOLD = 0.1875D;

    public DaizuliItemPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public PipeState getConnectionType(LevelAccessor level, BlockPos pipePos, BlockState pipeState, Direction connectionDirection, BlockPos connectPos) {
        // First check if we should connect at all (using parent logic)
        PipeState baseState = super.getConnectionType(level, pipePos, pipeState, connectionDirection, connectPos);
        if (baseState == PipeState.NONE) {
            return PipeState.NONE;
        }

        // Daizuli: target direction shows as BLOCKED (like Iron pipe)
        DaizuliItemPipeBE be = BlockUtils.getBE(DaizuliItemPipeBE.class, level, pipePos);
        if (be != null && be.getTargetDirection() != null) {
            if (connectionDirection == be.getTargetDirection()) {
                return PipeState.BLOCKED;
            }
        } else {
            // Preserve existing BLOCKED state on client before BE data arrives
            PipeState existing = pipeState.getValue(CONNECTION[connectionDirection.get3DDataValue()]);
            if (existing == PipeState.BLOCKED) {
                return PipeState.BLOCKED;
            }
        }
        return PipeState.CONNECTED;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() == BCItems.WRENCH.get()) {
            if (!level.isClientSide()) {
                DaizuliItemPipeBE be = BlockUtils.getBE(DaizuliItemPipeBE.class, level, pos);
                if (be != null) {
                    Direction clickedSide = hitResult.getDirection();
                    boolean centerHit = isCenterFaceHit(hitResult, pos, clickedSide);

                    if (centerHit || clickedSide == be.getTargetDirection()) {
                        be.cyclePipeColor();
                        player.displayClientMessage(Component.literal("Daizuli Pipe color: " + be.getPipeColor().getName()), true);
                    } else {
                        be.setTargetDirection(clickedSide);
                        player.displayClientMessage(Component.literal("Daizuli Pipe target: " + clickedSide.getSerializedName()), true);
                    }
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    private static boolean isCenterFaceHit(BlockHitResult hitResult, BlockPos pos, Direction face) {
        Vec3 local = hitResult.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        return switch (face) {
            case DOWN, UP -> Math.abs(local.x - 0.5D) <= CENTER_THRESHOLD && Math.abs(local.z - 0.5D) <= CENTER_THRESHOLD;
            case NORTH, SOUTH -> Math.abs(local.x - 0.5D) <= CENTER_THRESHOLD && Math.abs(local.y - 0.5D) <= CENTER_THRESHOLD;
            case WEST, EAST -> Math.abs(local.y - 0.5D) <= CENTER_THRESHOLD && Math.abs(local.z - 0.5D) <= CENTER_THRESHOLD;
        };
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(DaizuliItemPipeBlock::new);
    }

    @Override
    protected BlockEntityType<? extends PipeBlockEntity<?>> getBlockEntityType() {
        return BCBlockEntities.DAIZULI_ITEM_PIPE.get();
    }
}
```

Note: The BLOCKED state uses the same model/texture as connected for now. The visual difference (if IRON_BLOCK_MODEL_DEFINITION is later applied) would show a distinct blocked appearance. For MVP the functional routing is the priority; visual polish with a dedicated blocked texture can be added later.

- [ ] **Step 3: Build and verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/DaizuliItemPipeBE.java \
       src/main/java/com/thepigcat/buildcraft/content/blocks/DaizuliItemPipeBlock.java
git commit -m "fix: Daizuli Pipe directional color routing matches original BC behavior"
```

---

## Task 4: Emerald Pipe — Blacklist-Toggle

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/EmeraldItemPipeBE.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/content/menus/EmeraldPipeMenu.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/client/screens/EmeraldPipeScreen.java`
- Create: `src/main/java/com/thepigcat/buildcraft/networking/ToggleFilterModePayload.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java` (register payload)

- [ ] **Step 1: Add FilterMode enum and blacklist logic to EmeraldItemPipeBE**

Replace `EmeraldItemPipeBE.java` with:

```java
package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import com.thepigcat.buildcraft.networking.SyncPipeDirectionPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EmeraldItemPipeBE extends ExtractItemPipeBE {
    public enum FilterMode {
        WHITELIST, BLACKLIST;

        public FilterMode toggle() {
            return this == WHITELIST ? BLACKLIST : WHITELIST;
        }
    }

    private final ItemStackHandler filterHandler = new ItemStackHandler(9) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    private FilterMode filterMode = FilterMode.WHITELIST;

    public EmeraldItemPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.EMERALD_ITEM_PIPE.get(), pos, blockState);
    }

    @Override
    protected void extractItems() {
        if (!itemHandler.getStackInSlot(0).isEmpty()) {
            return;
        }

        if (energyStorage.getEnergyStored() < BCConfig.extractionEnergyCost) {
            return;
        }

        BlockCapabilityCache<IItemHandler, Direction> cache = capabilityCaches.get(this.extracting);
        if (cache != null) {
            IItemHandler extractingHandler = cache.getCapability();

            if (extractingHandler != null) {
                ItemStack extractedStack = ItemStack.EMPTY;
                int extractedSlot = 0;

                for (int i = 0; i < extractingHandler.getSlots(); i++) {
                    ItemStack simulated = extractingHandler.extractItem(i, 64, true);
                    if (!simulated.isEmpty() && matchesFilter(simulated)) {
                        ItemStack stack = extractingHandler.extractItem(i, 64, false);
                        if (!stack.isEmpty()) {
                            extractedStack = stack;
                            extractedSlot = i;
                            break;
                        }
                    }
                }

                if (!extractedStack.isEmpty()) {
                    ItemStack insertRemainder = itemHandler.insertItem(0, extractedStack, false);
                    extractingHandler.insertItem(extractedSlot, insertRemainder, false);

                    energyStorage.extractEnergy(BCConfig.extractionEnergyCost, false);

                    this.setFrom(this.extracting);

                    List<Direction> directions = new ArrayList<>(this.directions);
                    directions.remove(this.extracting);

                    if (!directions.isEmpty()) {
                        this.setTo(directions.getFirst());
                    }

                    PacketDistributor.sendToAllPlayers(new SyncPipeDirectionPayload(worldPosition, Optional.ofNullable(from), Optional.ofNullable(to)));
                }
            }
        }
    }

    public boolean matchesFilter(ItemStack stack) {
        boolean anyNonEmpty = false;
        boolean matchesAny = false;

        for (int i = 0; i < filterHandler.getSlots(); i++) {
            ItemStack filterStack = filterHandler.getStackInSlot(i);
            if (!filterStack.isEmpty()) {
                anyNonEmpty = true;
                if (ItemStack.isSameItem(stack, filterStack)) {
                    matchesAny = true;
                    break;
                }
            }
        }

        if (!anyNonEmpty) {
            // Empty filter: whitelist = everything passes, blacklist = nothing passes
            return getFilterMode() == FilterMode.WHITELIST;
        }

        return switch (getFilterMode()) {
            case WHITELIST -> matchesAny;
            case BLACKLIST -> !matchesAny;
        };
    }

    /**
     * Gate hook: returns the active filter mode.
     * Gates can override this in Phase E to set the mode dynamically.
     */
    public FilterMode getFilterMode() {
        return filterMode;
    }

    public void setFilterMode(FilterMode mode) {
        this.filterMode = mode;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void toggleFilterMode() {
        setFilterMode(filterMode.toggle());
    }

    public ItemStackHandler getFilterHandler() {
        return filterHandler;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("filter")) {
            this.filterHandler.deserializeNBT(registries, tag.getCompound("filter"));
        }
        if (tag.contains("filter_mode")) {
            this.filterMode = FilterMode.values()[tag.getInt("filter_mode")];
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("filter", this.filterHandler.serializeNBT(registries));
        tag.putInt("filter_mode", this.filterMode.ordinal());
    }
}
```

- [ ] **Step 2: Create ToggleFilterModePayload**

Create `src/main/java/com/thepigcat/buildcraft/networking/ToggleFilterModePayload.java`:

```java
package com.thepigcat.buildcraft.networking;

import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.content.blockentities.EmeraldItemPipeBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ToggleFilterModePayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ToggleFilterModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BuildcraftLegacy.MODID, "toggle_filter_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleFilterModePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBlockPos(payload.pos),
                    buf -> new ToggleFilterModePayload(buf.readBlockPos())
            );

    public static void handle(ToggleFilterModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player.level().getBlockEntity(payload.pos) instanceof EmeraldItemPipeBE be) {
                // Security check: player must be close enough
                if (player.distanceToSqr(payload.pos.getX() + 0.5, payload.pos.getY() + 0.5, payload.pos.getZ() + 0.5) <= 64.0) {
                    be.toggleFilterMode();
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

- [ ] **Step 3: Register payload in BuildcraftLegacy**

In `BuildcraftLegacy.java`, inside `registerPayloads()` method, add after the existing registrations (after line 149):

```java
registrar.playToServer(ToggleFilterModePayload.TYPE, ToggleFilterModePayload.STREAM_CODEC, ToggleFilterModePayload::handle);
```

Add import: `import com.thepigcat.buildcraft.networking.ToggleFilterModePayload;`

- [ ] **Step 4: Update EmeraldPipeMenu with filterMode data slot**

Replace `EmeraldPipeMenu.java` with:

```java
package com.thepigcat.buildcraft.content.menus;

import com.thepigcat.buildcraft.content.blockentities.EmeraldItemPipeBE;
import com.thepigcat.buildcraft.registries.BCMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public class EmeraldPipeMenu extends AbstractContainerMenu {
    public static final int FILTER_SLOTS = 9;

    private static final int PLAYER_INV_START = FILTER_SLOTS;
    private static final int PLAYER_INV_END   = FILTER_SLOTS + 27;
    private static final int HOTBAR_END        = FILTER_SLOTS + 36;

    public final EmeraldItemPipeBE blockEntity;
    private final ContainerData data;

    public EmeraldPipeMenu(int containerId, Inventory inv, EmeraldItemPipeBE blockEntity) {
        super(BCMenuTypes.EMERALD_PIPE.get(), containerId);
        this.blockEntity = blockEntity;

        // Sync filterMode to client via ContainerData
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                return blockEntity.getFilterMode().ordinal();
            }

            @Override
            public void set(int index, int value) {
                blockEntity.setFilterMode(EmeraldItemPipeBE.FilterMode.values()[value]);
            }

            @Override
            public int getCount() {
                return 1;
            }
        };
        addDataSlots(data);

        // 9 ghost filter slots in a 3x3 grid
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slot = row * 3 + col;
                addSlot(new SlotItemHandler(blockEntity.getFilterHandler(), slot,
                        62 + col * 18,
                        21 + row * 18));
            }
        }

        // Player main inventory (3 x 9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Player hotbar
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
    }

    public EmeraldPipeMenu(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(containerId, inv, (EmeraldItemPipeBE) inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    public EmeraldItemPipeBE.FilterMode getFilterMode() {
        return EmeraldItemPipeBE.FilterMode.values()[data.get(0)];
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < FILTER_SLOTS) {
            Slot slot = this.slots.get(slotId);
            ItemStack carried = this.getCarried();
            if (!carried.isEmpty()) {
                slot.set(carried.copyWithCount(1));
            } else {
                slot.set(ItemStack.EMPTY);
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < FILTER_SLOTS) {
            return ItemStack.EMPTY;
        }
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < PLAYER_INV_END) {
                if (!this.moveItemStackTo(stack, PLAYER_INV_END, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity.getLevel() != null
                && blockEntity.getLevel().getBlockEntity(blockEntity.getBlockPos()) == blockEntity
                && player.distanceToSqr(
                        blockEntity.getBlockPos().getX() + 0.5,
                        blockEntity.getBlockPos().getY() + 0.5,
                        blockEntity.getBlockPos().getZ() + 0.5) <= 64.0;
    }
}
```

- [ ] **Step 5: Update EmeraldPipeScreen with toggle button**

Replace `EmeraldPipeScreen.java` with:

```java
package com.thepigcat.buildcraft.client.screens;

import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.content.blockentities.EmeraldItemPipeBE;
import com.thepigcat.buildcraft.content.menus.EmeraldPipeMenu;
import com.thepigcat.buildcraft.networking.ToggleFilterModePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class EmeraldPipeScreen extends AbstractContainerScreen<EmeraldPipeMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BuildcraftLegacy.MODID, "textures/gui/emerald_pipe.png");

    private Button toggleButton;

    public EmeraldPipeScreen(EmeraldPipeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 143;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // Toggle button: top-right area of the filter section
        toggleButton = Button.builder(getFilterModeLabel(), btn -> {
            PacketDistributor.sendToServer(new ToggleFilterModePayload(menu.blockEntity.getBlockPos()));
            btn.setMessage(getFilterModeLabel());
        }).bounds(x + 126, y + 21, 42, 14).build();
        addRenderableWidget(toggleButton);
    }

    private Component getFilterModeLabel() {
        EmeraldItemPipeBE.FilterMode mode = menu.getFilterMode();
        return Component.literal(mode == EmeraldItemPipeBE.FilterMode.WHITELIST ? "Whitelist" : "Blacklist");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Update button text when server syncs the mode
        if (toggleButton != null) {
            toggleButton.setMessage(getFilterModeLabel());
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        g.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0x404040, false);
        g.drawString(font, playerInventoryTitle, 8, imageHeight - 94, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
```

- [ ] **Step 6: Build and verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/EmeraldItemPipeBE.java \
       src/main/java/com/thepigcat/buildcraft/content/menus/EmeraldPipeMenu.java \
       src/main/java/com/thepigcat/buildcraft/client/screens/EmeraldPipeScreen.java \
       src/main/java/com/thepigcat/buildcraft/networking/ToggleFilterModePayload.java \
       src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java
git commit -m "feat: add Blacklist/Whitelist toggle to Emerald Pipe filter GUI"
```

---

## Task 5: Diamond Pipe + Phase B Dokumentation

**Files:**
- Modify: `TODO_NEXT_SESSION.md`

- [ ] **Step 1: Update TODO_NEXT_SESSION.md**

Mark completed Phase B tasks as done, add Diamond Split as future TODO:

In `TODO_NEXT_SESSION.md`, move the following from open to "Erledigt (Archiv)":

```
- [x] Diamond Pipe — Registrierung korrekt (nicht EXTRACTING), Sortier-GUI funktional
- [x] Iron Pipe — Wrench-Rotation + Output-Blocking
- [x] Gold Pipe — Eigene BE-Klasse mit originalgetreuer Beschleunigung (SPEED_TARGET=0.25, SPEED_DELTA=0.07)
- [x] Lapis Pipe — Farb-System (16 Farben, Rechtsklick-Cycling)
- [x] Daizuli Pipe — Directional Color Routing mit Wrench-on-Face + Gate-Hook
- [x] Emerald Pipe — Whitelist/Blacklist Toggle-GUI
- [x] Obsidian Pipe — Single-Face Suction Enforcement
```

Add new future TODO under a new section:

```
## Future — Pipe Erweiterungen
- [ ] Diamond Pipe: Round-Robin / proportionaler Split (braucht Pipe-Base Multi-Output-Support)
- [ ] Obsidian Pipe: Energy-Gating (Engine-Power bestimmt Suction-Range, analog Original MJ-System)
- [ ] Daizuli Pipe: Dedizierte Blocked-Textur fuer Target-Direction
- [ ] Gate-Integration fuer Lapis/Daizuli (getActiveColor()), Emerald (getFilterMode())
```

- [ ] **Step 2: Commit**

```bash
git add TODO_NEXT_SESSION.md
git commit -m "docs: close Phase B, document future pipe extension TODOs"
```

---

## Verification Checklist

After all tasks are complete:

- [ ] `./gradlew build` passes
- [ ] Ingame: Gold Pipe accelerates items visually faster
- [ ] Ingame: Obsidian Pipe only sucks when exactly 5 sides connected (1 open face)
- [ ] Ingame: Obsidian Pipe does NOT suck when 2+ faces are open
- [ ] Ingame: Daizuli Pipe routes matching-color items to target, non-matching to other directions
- [ ] Ingame: Daizuli Pipe wrench-on-face sets target, center/target-face click cycles color
- [ ] Ingame: Emerald Pipe GUI shows Whitelist/Blacklist button
- [ ] Ingame: Emerald Pipe Blacklist mode extracts everything EXCEPT filtered items
- [ ] Existing pipes (stone, cobblestone, iron, etc.) still function correctly
