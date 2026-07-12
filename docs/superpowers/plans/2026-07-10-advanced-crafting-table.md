# Advanced Crafting Table Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port BuildCraft 1.12's Advanced Crafting Table to NeoForge 1.21.1 — a laser-powered auto-crafting machine driven by a manual phantom blueprint grid, matching ingredients by exact stack.

**Architecture:** A `ContainerBlockEntity` (PDL) holding three raw `ItemStackHandler`s (9-slot phantom blueprint, 15-slot materials, 9-slot results). It implements `ILaserTarget`; a `LaserBE` pushes FE into it. Each tick it resolves the vanilla crafting recipe defined by the ghost grid, and when it holds enough FE it pulls exact stacks from materials, crafts, and deposits into results. Pipes see a sided capability view (insert→materials, extract→results). The existing `LaserBE` is generalized from `AssemblyTableBE` to the `ILaserTarget` interface so it powers both tables.

**Tech Stack:** Java 21, NeoForge 1.21.1, PortingDeadLibs 1.1.7 (`ContainerBlockEntity`, `PDLAbstractContainerMenu`, `PDLAbstractContainerScreen`), NeoForge capabilities & item handlers.

> **STATUS (2026-07-12): ✅ COMPLETE & VERIFIED IN-GAME.**
> All 5 tasks executed subagent-driven, reviewed clean, committed to `main`. Final whole-branch review (opus): READY TO MERGE.
> Implementation commits: `ddb27d7` (scaffold) · `606c604` (screen) · `d334347` (progress-bar layout) · `1e5b456` (crafting logic) · `281e955` (laser→ILaserTarget) · `c40a50d` (datagen+textures).
> **In-game testing found two runtime-only bugs the compile/review gates could not catch — both fixed & user-verified:**
> - `dd88503` — GUI layout: slot coords/imageHeight were mirrored from the Assembly Table instead of matching the copied 176×241 texture → slots overlapped the player inventory. Re-aligned to the original BC layout.
> - `99607e4` — Runtime sync: NeoForge routes runtime BE updates through `onDataPacket`→`loadData` (not `handleUpdateTag`), and `loadData` never read `assumedResult` → the recipe preview & progress bar froze after the first sync. Fixed by overriding `onDataPacket` to apply `power`+`assumedResult` directly.
>
> **Lesson:** subagent reviews verified compile + logic but a headless agent cannot drive the GUI — client/server sync and GUI-layout defects surface only in `runClient`. In-game verification by the developer is essential for any block with a GUI or client sync.

## Global Constraints

- Root package: `com.thepigcat.buildcraft`. Mod id: `buildcraft`.
- `ResourceLocation`: use `ResourceLocation.fromNamespaceAndPath(...)` or `BuildcraftLegacy.rl(path)`; never the two-arg constructor.
- Machine block entities extend PDL `ContainerBlockEntity`; NBT via `saveData`/`loadData` (NOT `saveAdditional`/`loadAdditional`).
- Capabilities registered in `BuildcraftLegacy.attachCaps()` via `RegisterCapabilitiesEvent`.
- Menus extend `PDLAbstractContainerMenu`; screens extend `PDLAbstractContainerScreen`.
- The repo has **no test source set** (`src/test` does not exist) and no GameTest fixtures. Per-task verification is therefore `./gradlew compileJava` plus a concrete in-game check via `./gradlew runClient`. This is a deliberate, repo-consistent deviation from unit-test-first TDD — the whole existing codebase is verified this way.
- Faithful to BC 1.12: exact-stack matching, three separate inventories, fixed FE cost per craft.

---

### Task 1: Scaffold — config, IO capability wrapper, block entity, menu, block, registrations

This is one atomic unit: MC registration mutually references block ↔ block entity ↔ menu ↔ registries, so they must land together to compile. Crafting logic is deliberately stubbed here and implemented in Task 3.

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/BCConfig.java` (add cost field)
- Create: `src/main/java/com/thepigcat/buildcraft/content/blockentities/AdvancedCraftingTableIOHandler.java`
- Create: `src/main/java/com/thepigcat/buildcraft/content/blockentities/AdvancedCraftingTableBE.java`
- Create: `src/main/java/com/thepigcat/buildcraft/content/menus/PhantomSlot.java`
- Create: `src/main/java/com/thepigcat/buildcraft/content/menus/AdvancedCraftingTableMenu.java`
- Create: `src/main/java/com/thepigcat/buildcraft/content/blocks/AdvancedCraftingTableBlock.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCBlocks.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCBlockEntities.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCMenuTypes.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java` (attachCaps)

**Interfaces:**
- Produces (used by later tasks):
  - `AdvancedCraftingTableBE`: `int power`, `ItemStack assumedResult`, `boolean blueprintDirty`; getters `getBlueprint()`, `getMaterials()`, `getResults()` (all `ItemStackHandler`), `getIOHandler()` (`IItemHandler`), `getAssumedResult()` (`ItemStack`), `getPower()` (`int`); `getRequiredLaserPower()`/`receiveLaserPower(int)` (`ILaserTarget`); static `serverTick(Level, BlockPos, BlockState, AdvancedCraftingTableBE)`.
  - `AdvancedCraftingTableMenu`: constructors `(int, Inventory, AdvancedCraftingTableBE)` and `(int, Inventory, RegistryFriendlyByteBuf)`; `getPower()`, `getFeCost()`, `getAssumedResult()`.
  - `BCBlocks.ADVANCED_CRAFTING_TABLE`, `BCBlockEntities.ADVANCED_CRAFTING_TABLE`, `BCMenuTypes.ADVANCED_CRAFTING_TABLE`.
  - `BCConfig.advancedCraftingTableFeCost` (`int`).

- [x] **Step 1: Add the config field**

In `BCConfig.java`, after the chipset cost block (after line 75, before the closing brace), add:

```java

    @ConfigValue(name = "Advanced Crafting Table FE Cost", comment = "FE required per craft in the Advanced Crafting Table", category = "advanced_crafting")
    public static int advancedCraftingTableFeCost = 5000;
```

- [x] **Step 2: Create the sided IO capability wrapper**

Create `AdvancedCraftingTableIOHandler.java`:

```java
package com.thepigcat.buildcraft.content.blockentities;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

/**
 * Capability view exposed to pipes/hoppers. Insertion goes only into the materials
 * handler; extraction comes only from the results handler. Mirrors original BC 1.12
 * EnumAccess.INSERT (materials) / EnumAccess.EXTRACT (results) on all faces.
 * Slots [0, materials.size) map to materials; [materials.size, total) map to results.
 */
public class AdvancedCraftingTableIOHandler implements IItemHandler {
    private final IItemHandlerModifiable materials;
    private final IItemHandlerModifiable results;

    public AdvancedCraftingTableIOHandler(IItemHandlerModifiable materials, IItemHandlerModifiable results) {
        this.materials = materials;
        this.results = results;
    }

    @Override public int getSlots() { return materials.getSlots() + results.getSlots(); }

    @Override public @NotNull ItemStack getStackInSlot(int slot) {
        return slot < materials.getSlots()
                ? materials.getStackInSlot(slot)
                : results.getStackInSlot(slot - materials.getSlots());
    }

    @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (slot >= materials.getSlots()) return stack; // results reject insertion
        return materials.insertItem(slot, stack, simulate);
    }

    @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < materials.getSlots()) return ItemStack.EMPTY; // materials reject pipe extraction
        return results.extractItem(slot - materials.getSlots(), amount, simulate);
    }

    @Override public int getSlotLimit(int slot) {
        return slot < materials.getSlots()
                ? materials.getSlotLimit(slot)
                : results.getSlotLimit(slot - materials.getSlots());
    }

    @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return slot < materials.getSlots() && materials.isItemValid(slot, stack);
    }
}
```

- [x] **Step 3: Create the block entity (no crafting logic yet)**

Create `AdvancedCraftingTableBE.java`:

```java
package com.thepigcat.buildcraft.content.blockentities;

import com.portingdeadmods.portingdeadlibs.api.blockentities.ContainerBlockEntity;
import com.thepigcat.buildcraft.api.blockentities.ILaserTarget;
import com.thepigcat.buildcraft.content.menus.AdvancedCraftingTableMenu;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AdvancedCraftingTableBE extends ContainerBlockEntity implements ILaserTarget, MenuProvider {
    public static final int BLUEPRINT_SLOTS = 9;
    public static final int MATERIAL_SLOTS = 15;
    public static final int RESULT_SLOTS = 9;

    public int power = 0;
    public ItemStack assumedResult = ItemStack.EMPTY;
    protected boolean blueprintDirty = true;

    private final ItemStackHandler blueprint = new ItemStackHandler(BLUEPRINT_SLOTS) {
        @Override protected void onContentsChanged(int slot) {
            blueprintDirty = true;
            AdvancedCraftingTableBE.this.setChanged();
        }
    };
    private final ItemStackHandler materials = new ItemStackHandler(MATERIAL_SLOTS) {
        @Override protected void onContentsChanged(int slot) { AdvancedCraftingTableBE.this.setChanged(); }
    };
    private final ItemStackHandler results = new ItemStackHandler(RESULT_SLOTS) {
        @Override protected void onContentsChanged(int slot) { AdvancedCraftingTableBE.this.setChanged(); }
    };
    private final AdvancedCraftingTableIOHandler ioHandler = new AdvancedCraftingTableIOHandler(materials, results);

    public AdvancedCraftingTableBE(BlockPos pos, BlockState state) {
        super(BCBlockEntities.ADVANCED_CRAFTING_TABLE.get(), pos, state);
    }

    public ItemStackHandler getBlueprint() { return blueprint; }
    public ItemStackHandler getMaterials() { return materials; }
    public ItemStackHandler getResults() { return results; }
    public IItemHandler getIOHandler() { return ioHandler; }
    public ItemStack getAssumedResult() { return assumedResult; }
    public int getPower() { return power; }

    // ── ILaserTarget (real impl added in crafting task) ──────────────────────
    @Override public int getRequiredLaserPower() { return 0; }
    @Override public void receiveLaserPower(int fe) { power += fe; setChanged(); }

    // ── Tick (crafting added in a later task) ────────────────────────────────
    public static void serverTick(Level level, BlockPos pos, BlockState state, AdvancedCraftingTableBE be) {
        // no-op until crafting task
    }

    // ── Sync ─────────────────────────────────────────────────────────────────
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("power", power);
        tag.put("assumed", assumedResult.saveOptional(registries));
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        power = tag.getInt("power");
        assumedResult = ItemStack.parseOptional(registries, tag.getCompound("assumed"));
    }

    // ── NBT ──────────────────────────────────────────────────────────────────
    @Override protected void saveData(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveData(tag, provider);
        tag.put("blueprint", blueprint.serializeNBT(provider));
        tag.put("materials", materials.serializeNBT(provider));
        tag.put("results", results.serializeNBT(provider));
        tag.putInt("power", power);
    }

    @Override protected void loadData(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadData(tag, provider);
        blueprint.deserializeNBT(provider, tag.getCompound("blueprint"));
        materials.deserializeNBT(provider, tag.getCompound("materials"));
        results.deserializeNBT(provider, tag.getCompound("results"));
        power = tag.getInt("power");
        blueprintDirty = true;
    }

    // ── MenuProvider ──────────────────────────────────────────────────────────
    @Override public @NotNull Component getDisplayName() {
        return Component.translatable("container.buildcraft.advanced_crafting_table");
    }

    @Override public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player player) {
        return new AdvancedCraftingTableMenu(id, inv, this);
    }
}
```

- [x] **Step 4: Create the phantom slot**

Create `PhantomSlot.java`:

```java
package com.thepigcat.buildcraft.content.menus;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/** Ghost slot for the blueprint grid: shows an item template but never holds or moves a real stack. */
public class PhantomSlot extends SlotItemHandler {
    public PhantomSlot(IItemHandler handler, int index, int x, int y) {
        super(handler, index, x, y);
    }

    @Override public boolean mayPlace(@NotNull ItemStack stack) { return false; }

    @Override public boolean mayPickup(@NotNull Player player) { return false; }
}
```

- [x] **Step 5: Create the menu**

Create `AdvancedCraftingTableMenu.java`. Slot add-order defines menu indices: materials 0–14, results 15–23, blueprint 24–32, preview 33, player inv/hotbar 34–69.

```java
package com.thepigcat.buildcraft.content.menus;

import com.portingdeadmods.portingdeadlibs.api.gui.menus.PDLAbstractContainerMenu;
import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.content.blockentities.AdvancedCraftingTableBE;
import com.thepigcat.buildcraft.registries.BCMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class AdvancedCraftingTableMenu extends PDLAbstractContainerMenu<AdvancedCraftingTableBE> {
    public static final int MAT_START = 0;      // materials 0..14
    public static final int MAT_END = 15;
    public static final int RES_START = 15;     // results 15..23
    public static final int RES_END = 24;
    public static final int BP_START = 24;      // blueprint 24..32
    public static final int BP_END = 33;
    public static final int PREVIEW = 33;       // result preview
    public static final int PLAYER_START = 34;  // player inv + hotbar 34..69

    public AdvancedCraftingTableMenu(int id, @NotNull Inventory inv, @NotNull AdvancedCraftingTableBE be) {
        super(BCMenuTypes.ADVANCED_CRAFTING_TABLE.get(), id, inv, be);

        // Materials 5x3 (menu indices 0..14)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 5; col++)
                addSlot(new SlotItemHandler(be.getMaterials(), row * 5 + col, 8 + col * 18, 84 + row * 18));

        // Results 3x3 (menu indices 15..23)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++)
                addSlot(new SlotItemHandler(be.getResults(), row * 3 + col, 116 + col * 18, 84 + row * 18));

        // Blueprint phantom 3x3 (menu indices 24..32)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 3; col++)
                addSlot(new PhantomSlot(be.getBlueprint(), row * 3 + col, 44 + col * 18, 18 + row * 18));

        // Result preview (menu index 33)
        addSlot(new SlotDisplay(be::getAssumedResult, 134, 36));

        addPlayerInventory(inv, 123);
        addPlayerHotbar(inv, 181);
    }

    public AdvancedCraftingTableMenu(int id, @NotNull Inventory inv, @NotNull RegistryFriendlyByteBuf buf) {
        this(id, inv, (AdvancedCraftingTableBE) inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    @Override
    public void clicked(int slotId, int button, @NotNull ClickType clickType, @NotNull Player player) {
        if (slotId >= BP_START && slotId < BP_END) {
            Slot slot = this.slots.get(slotId);
            ItemStack carried = getCarried();
            if (clickType == ClickType.PICKUP || clickType == ClickType.PICKUP_ALL || clickType == ClickType.QUICK_MOVE) {
                slot.set(carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return; // phantom slots never move real items and never consume the carried stack
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index >= PLAYER_START) {
                // player inventory → materials
                if (!moveItemStackTo(stack, MAT_START, MAT_END, false)) return ItemStack.EMPTY;
            } else if (index >= MAT_START && index < RES_END) {
                // materials or results → player inventory
                if (!moveItemStackTo(stack, PLAYER_START, this.slots.size(), true)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY; // blueprint / preview: no shift-move
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        }
        return result;
    }

    @Override protected int getMergeableSlotCount() { return MAT_END; }

    public int getPower() { return blockEntity.power; }
    public int getFeCost() { return BCConfig.advancedCraftingTableFeCost; }
    public ItemStack getAssumedResult() { return blockEntity.getAssumedResult(); }
}
```

- [x] **Step 6: Create the block**

Create `AdvancedCraftingTableBlock.java` (mirrors `AssemblyTableBlock`; no FACING):

```java
package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.content.blockentities.AdvancedCraftingTableBE;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class AdvancedCraftingTableBlock extends BaseEntityBlock {
    public AdvancedCraftingTableBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return BCBlockEntities.ADVANCED_CRAFTING_TABLE.get().create(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AdvancedCraftingTableBE be) {
            player.openMenu(be, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, BCBlockEntities.ADVANCED_CRAFTING_TABLE.get(), AdvancedCraftingTableBE::serverTick);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(AdvancedCraftingTableBlock::new);
    }
}
```

- [x] **Step 7: Register the block**

In `BCBlocks.java`, after the `ASSEMBLY_TABLE` entry (line 58), add:

```java
    public static final DeferredBlock<AdvancedCraftingTableBlock> ADVANCED_CRAFTING_TABLE = registerBlockAndItem("advanced_crafting_table", AdvancedCraftingTableBlock::new,
            BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).mapColor(MapColor.METAL).requiresCorrectToolForDrops());
```

Add the import at the top: `import com.thepigcat.buildcraft.content.blocks.AdvancedCraftingTableBlock;` (place beside the other block imports).

- [x] **Step 8: Register the block entity type**

In `BCBlockEntities.java`, after the `ASSEMBLY_TABLE` entry (line 85), add:

```java
    public static final Supplier<BlockEntityType<AdvancedCraftingTableBE>> ADVANCED_CRAFTING_TABLE = BLOCK_ENTITIES.register("advanced_crafting_table",
            () -> BlockEntityType.Builder.of(AdvancedCraftingTableBE::new, BCBlocks.ADVANCED_CRAFTING_TABLE.get()).build(null));
```

Add the import: `import com.thepigcat.buildcraft.content.blockentities.AdvancedCraftingTableBE;`.

- [x] **Step 9: Register the menu type**

In `BCMenuTypes.java`, after the `ASSEMBLY_TABLE` entry (line 34), add:

```java
    public static final Supplier<MenuType<AdvancedCraftingTableMenu>> ADVANCED_CRAFTING_TABLE =
            registerMenuType("advanced_crafting_table", AdvancedCraftingTableMenu::new);
```

Add the import: `import com.thepigcat.buildcraft.content.menus.AdvancedCraftingTableMenu;`.

- [x] **Step 10: Attach the item-handler capability**

In `BuildcraftLegacy.java` `attachCaps(...)`, after the QUARRY item-handler registration (line 190), add:

```java
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BCBlockEntities.ADVANCED_CRAFTING_TABLE.get(),
                (be, side) -> be.getIOHandler());
```

(`AdvancedCraftingTableBE` is reachable via `BCBlockEntities`; no extra import needed since the lambda uses the getter.)

- [x] **Step 11: Compile**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`. If `ContainerBlockEntity` complains about a missing registered handler at class-init, it will surface here — none is expected because we never call `getItemHandler()`.

- [x] **Step 12: In-game smoke test (no GUI yet — no screen registered)**

Run: `./gradlew runClient`
In a creative world:
1. `/give @s buildcraft:advanced_crafting_table`, place it.
2. Place a hopper feeding into the table; drop items (e.g. iron ingots) into the hopper. Confirm they enter the table's materials (break the table — items drop, proving they were stored).
3. Do NOT right-click the table yet (would crash: no screen until Task 2).

Expected: block places, hopper insertion into materials works, no server errors in the log.

- [x] **Step 13: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/BCConfig.java \
        src/main/java/com/thepigcat/buildcraft/content/blockentities/AdvancedCraftingTableIOHandler.java \
        src/main/java/com/thepigcat/buildcraft/content/blockentities/AdvancedCraftingTableBE.java \
        src/main/java/com/thepigcat/buildcraft/content/menus/PhantomSlot.java \
        src/main/java/com/thepigcat/buildcraft/content/menus/AdvancedCraftingTableMenu.java \
        src/main/java/com/thepigcat/buildcraft/content/blocks/AdvancedCraftingTableBlock.java \
        src/main/java/com/thepigcat/buildcraft/registries/BCBlocks.java \
        src/main/java/com/thepigcat/buildcraft/registries/BCBlockEntities.java \
        src/main/java/com/thepigcat/buildcraft/registries/BCMenuTypes.java \
        src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java
git commit -m "feat(advanced-crafting-table): scaffold block, BE, menu, IO cap (no crafting yet)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Screen + client registration

**Files:**
- Create: `src/main/java/com/thepigcat/buildcraft/client/screens/AdvancedCraftingTableScreen.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/BuildcraftLegacyClient.java` (registerMenuScreens)

**Interfaces:**
- Consumes: `AdvancedCraftingTableMenu.getPower()`, `getFeCost()`, `getAssumedResult()`; `BCMenuTypes.ADVANCED_CRAFTING_TABLE`.

- [x] **Step 1: Create the screen**

Create `AdvancedCraftingTableScreen.java`. The texture is added in the datagen task; the screen references it now. It draws a vertical FE progress bar and the result-preview stack.

```java
package com.thepigcat.buildcraft.client.screens;

import com.portingdeadmods.portingdeadlibs.api.client.screens.PDLAbstractContainerScreen;
import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.content.menus.AdvancedCraftingTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class AdvancedCraftingTableScreen extends PDLAbstractContainerScreen<AdvancedCraftingTableMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BuildcraftLegacy.MODID, "textures/gui/advanced_crafting_table.png");

    // Progress bar: source strip at (176, 0), 4px wide x 70px tall, drawn at (86, 36), fills bottom-up.
    private static final int PROGRESS_X = 86;
    private static final int PROGRESS_Y = 36;
    private static final int PROGRESS_W = 4;
    private static final int PROGRESS_H = 70;

    public AdvancedCraftingTableScreen(AdvancedCraftingTableMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 207;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public @NotNull ResourceLocation getBackgroundTexture() {
        return TEXTURE;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        super.renderBg(g, partialTick, mx, my);
        int power = menu.getPower();
        int cost = menu.getFeCost();
        if (cost <= 0) return;
        float frac = Math.min(1f, (float) power / cost);
        int fillH = (int) (PROGRESS_H * frac);
        if (fillH <= 0) return;
        int skipH = PROGRESS_H - fillH;
        g.blit(TEXTURE, leftPos + PROGRESS_X, topPos + PROGRESS_Y + skipH, 176, skipH, PROGRESS_W, fillH);
    }
}
```

- [x] **Step 2: Register the screen**

In `BuildcraftLegacyClient.java` `registerMenuScreens(...)`, after the `ASSEMBLY_TABLE` line (132), add:

```java
        event.register(BCMenuTypes.ADVANCED_CRAFTING_TABLE.get(), AdvancedCraftingTableScreen::new);
```

Add the import: `import com.thepigcat.buildcraft.client.screens.AdvancedCraftingTableScreen;`.

- [x] **Step 3: Compile**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 4: In-game test (GUI opens; texture is still missing until datagen task)**

Run: `./gradlew runClient`
1. Place the table, right-click it. GUI opens without crashing (background may be the missing-texture checkerboard — that is fine; datagen adds the texture in Task 5).
2. Pick up an item, click a blueprint (top) slot → a single ghost copy appears and your carried stack is unchanged. Click the ghost slot again with an empty hand → it clears.
3. Put items in materials/results slots; shift-click from player inventory lands in materials; shift-click a materials/results item returns it to the player inventory. Blueprint slots never receive shift-clicked items.

Expected: GUI opens, phantom placement/clear works, shift-click routing correct.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/client/screens/AdvancedCraftingTableScreen.java \
        src/main/java/com/thepigcat/buildcraft/BuildcraftLegacyClient.java
git commit -m "feat(advanced-crafting-table): GUI screen + client registration

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Crafting logic

Implements recipe resolution, exact-stack matching, the craft, and the real `ILaserTarget` power requirement. Edits only `AdvancedCraftingTableBE.java`.

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/AdvancedCraftingTableBE.java`

**Interfaces:**
- Consumes: `BCConfig.advancedCraftingTableFeCost`.
- Produces: real `getRequiredLaserPower()` (returns `feCost - power` when a craft is ready, else 0), populated `assumedResult`, functioning `serverTick`.

- [x] **Step 1: Add imports**

At the top of `AdvancedCraftingTableBE.java`, add:

```java
import com.thepigcat.buildcraft.BCConfig;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
```

- [x] **Step 2: Add the cached recipe field**

Below `protected boolean blueprintDirty = true;` add:

```java
    @Nullable private RecipeHolder<CraftingRecipe> currentRecipe = null;
```

- [x] **Step 3: Replace the ILaserTarget stub**

Replace:

```java
    @Override public int getRequiredLaserPower() { return 0; }
```

with:

```java
    @Override public int getRequiredLaserPower() {
        return canCraft() ? Math.max(0, BCConfig.advancedCraftingTableFeCost - power) : 0;
    }
```

- [x] **Step 4: Replace the serverTick stub and add crafting helpers**

Replace the no-op `serverTick` with the following block (serverTick + all helpers):

```java
    public static void serverTick(Level level, BlockPos pos, BlockState state, AdvancedCraftingTableBE be) {
        if (level.isClientSide) return;
        boolean changed = false;

        if (be.blueprintDirty) {
            be.recomputeRecipe(level);
            changed = true;
        }

        int cost = BCConfig.advancedCraftingTableFeCost;
        if (be.canCraft() && be.power >= cost) {
            be.power -= cost;
            be.craft(level);
            changed = true;
        }

        if (changed) {
            level.sendBlockUpdated(pos, state, state, 3);
            be.setChanged();
        }
    }

    private void recomputeRecipe(Level level) {
        blueprintDirty = false;
        CraftingInput input = blueprintInput();
        if (input.isEmpty()) {
            currentRecipe = null;
            assumedResult = ItemStack.EMPTY;
            return;
        }
        Optional<RecipeHolder<CraftingRecipe>> match =
                level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (match.isPresent()) {
            currentRecipe = match.get();
            assumedResult = currentRecipe.value().assemble(input, level.registryAccess());
        } else {
            currentRecipe = null;
            assumedResult = ItemStack.EMPTY;
        }
    }

    /** 3x3 CraftingInput built from the ghost blueprint items (positions preserved). */
    private CraftingInput blueprintInput() {
        List<ItemStack> items = new ArrayList<>(BLUEPRINT_SLOTS);
        for (int i = 0; i < BLUEPRINT_SLOTS; i++) items.add(blueprint.getStackInSlot(i));
        return CraftingInput.of(3, 3, items);
    }

    private boolean canCraft() {
        if (currentRecipe == null || assumedResult.isEmpty()) return false;
        return hasMaterials() && resultsCanAccept(assumedResult);
    }

    /** True if materials contain the exact stack for every non-empty ghost slot (counts included). */
    private boolean hasMaterials() {
        int[] avail = new int[MATERIAL_SLOTS];
        for (int s = 0; s < MATERIAL_SLOTS; s++) avail[s] = materials.getStackInSlot(s).getCount();
        for (int i = 0; i < BLUEPRINT_SLOTS; i++) {
            ItemStack ghost = blueprint.getStackInSlot(i);
            if (ghost.isEmpty()) continue;
            boolean satisfied = false;
            for (int s = 0; s < MATERIAL_SLOTS; s++) {
                if (avail[s] > 0 && ItemStack.isSameItemSameComponents(materials.getStackInSlot(s), ghost)) {
                    avail[s]--;
                    satisfied = true;
                    break;
                }
            }
            if (!satisfied) return false;
        }
        return true;
    }

    private boolean resultsCanAccept(ItemStack out) {
        ItemStack remainder = out.copy();
        for (int s = 0; s < RESULT_SLOTS && !remainder.isEmpty(); s++) {
            remainder = results.insertItem(s, remainder, true);
        }
        return remainder.isEmpty();
    }

    private void craft(Level level) {
        // Pull one exact material per ghost slot into a positioned 3x3 list.
        List<ItemStack> pulled = new ArrayList<>(BLUEPRINT_SLOTS);
        for (int i = 0; i < BLUEPRINT_SLOTS; i++) {
            ItemStack ghost = blueprint.getStackInSlot(i);
            pulled.add(ghost.isEmpty() ? ItemStack.EMPTY : extractExact(ghost));
        }
        CraftingInput input = CraftingInput.of(3, 3, pulled);

        if (currentRecipe == null || !currentRecipe.value().matches(input, level)) {
            // Safety: recipe no longer matches — return everything we pulled.
            for (ItemStack st : pulled) if (!st.isEmpty()) insertOrEject(materials, st);
            return;
        }

        ItemStack output = currentRecipe.value().assemble(input, level.registryAccess());
        insertOrEject(results, output);

        NonNullList<ItemStack> remaining = currentRecipe.value().getRemainingItems(input);
        for (ItemStack r : remaining) if (!r.isEmpty()) insertOrEject(materials, r);
    }

    /** Extract exactly one item matching the ghost stack from materials. */
    private ItemStack extractExact(ItemStack ghost) {
        for (int s = 0; s < MATERIAL_SLOTS; s++) {
            if (ItemStack.isSameItemSameComponents(materials.getStackInSlot(s), ghost)) {
                return materials.extractItem(s, 1, false);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Insert into the given handler; overflow goes to an adjacent inventory, else drops in-world. */
    private void insertOrEject(ItemStackHandler handler, ItemStack stack) {
        ItemStack remainder = ItemHandlerHelper.insertItem(handler, stack, false);
        if (remainder.isEmpty() || level == null) return;
        for (Direction dir : Direction.values()) {
            var cap = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.relative(dir), dir.getOpposite());
            if (cap != null) {
                remainder = ItemHandlerHelper.insertItem(cap, remainder, false);
                if (remainder.isEmpty()) return;
            }
        }
        ItemEntity entity = new ItemEntity(level,
                worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, remainder);
        level.addFreshEntity(entity);
    }
```

Note: `worldPosition` is `BlockEntity`'s protected field for this BE's position.

- [x] **Step 5: Compile**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 6: Logic review (runtime observability comes in Task 4)**

Re-read `serverTick`/`canCraft`/`craft`. Confirm: `assumedResult` populates once a valid pattern is in the blueprint (verifiable in-game now — the preview slot fills), but the actual craft cannot fire until a laser supplies FE (Task 4), since nothing else raises `power`.

Run: `./gradlew runClient` — place a valid recipe pattern (e.g. 1 stick ghost in the crafting layout for a recipe you know) into the blueprint; confirm the **result preview slot** now shows the expected output. (No craft yet — power stays 0.)

- [x] **Step 7: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/AdvancedCraftingTableBE.java
git commit -m "feat(advanced-crafting-table): crafting logic — recipe resolve, exact-stack match, craft

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Generalize the Laser to power any ILaserTarget

Currently `LaserBE` hardcodes `AssemblyTableBE` in three places. Generalize to `ILaserTarget` (matching original BC) so a laser powers the Advanced Crafting Table too.

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/LaserBE.java`

**Interfaces:**
- Consumes: `ILaserTarget` (from `com.thepigcat.buildcraft.api.blockentities`).

- [x] **Step 1: Add the import**

At the top of `LaserBE.java`, add: `import com.thepigcat.buildcraft.api.blockentities.ILaserTarget;`

- [x] **Step 2: Generalize the stale-target check in serverTick**

Replace:

```java
            if (!(level.getBlockEntity(be.targetPos) instanceof AssemblyTableBE tbl) || tbl.getRequiredLaserPower() <= 0) {
```

with:

```java
            if (!(level.getBlockEntity(be.targetPos) instanceof ILaserTarget tbl) || tbl.getRequiredLaserPower() <= 0) {
```

- [x] **Step 3: Generalize the power-push in serverTick**

Replace:

```java
        if (be.targetPos != null && level.getBlockEntity(be.targetPos) instanceof AssemblyTableBE target) {
```

with:

```java
        if (be.targetPos != null && level.getBlockEntity(be.targetPos) instanceof ILaserTarget target) {
```

- [x] **Step 4: Generalize scanForTargets**

Replace:

```java
                    if (level.getBlockEntity(check) instanceof AssemblyTableBE) {
```

with:

```java
                    if (level.getBlockEntity(check) instanceof ILaserTarget) {
```

- [x] **Step 5: Generalize pickTarget**

Replace:

```java
            if (level.getBlockEntity(candidate) instanceof AssemblyTableBE tbl && tbl.getRequiredLaserPower() > 0) {
```

with:

```java
            if (level.getBlockEntity(candidate) instanceof ILaserTarget tbl && tbl.getRequiredLaserPower() > 0) {
```

- [x] **Step 6: Compile**

Run: `./gradlew compileJava 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 7: End-to-end in-game test**

Run: `./gradlew runClient`
1. Place the Advanced Crafting Table. Put a known recipe pattern in the blueprint (e.g. the 2×2 planks→crafting-table shape, or 1 log→4 planks).
2. Fill materials with the real ingredients (enough for several crafts).
3. Place a Laser pointing at the table (within 8 blocks) and power it (kinesis pipe from an engine, or a redstone engine chain).
4. Observe: the progress bar animates; results buffer fills with the crafted output; materials deplete; container-remainder items (e.g. empty buckets for a cake-like recipe) return to materials.
5. Attach an item pipe to extract from results and another to insert into materials — confirm INSERT hits materials, EXTRACT pulls from results.
6. Regression: place a Laser + Assembly Table and confirm the Assembly Table still charges and crafts chipsets.
7. Persistence: set a blueprint, save & reload the world — the ghost pattern and buffered items survive.

Expected: full craft loop works; Assembly Table unaffected; state persists.

- [x] **Step 8: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/LaserBE.java
git commit -m "refactor(laser): target ILaserTarget interface so it powers both silicon tables

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: Datagen — blockstate/model, lang, loot, recipe, textures

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/datagen/assets/BCBlockStateProvider.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/datagen/assets/BCEnUSLangProvider.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/datagen/data/BCBlockLootTableProvider.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/datagen/data/BCRecipeProvider.java`
- Create (textures): `src/main/resources/assets/buildcraft/textures/block/advanced_crafting_table_top.png`, `.../advanced_crafting_table_side.png`, and GUI `src/main/resources/assets/buildcraft/textures/gui/advanced_crafting_table.png`

- [x] **Step 1: Copy textures from the original BC 1.12 assets**

Locate the original textures and copy them (mirrors earlier texture-copy commits):

```bash
BC=/run/media/fabi/SSD/codeing/BuildCraft-1.12
find "$BC" -path "*buildcraftsilicon*" \( -iname "*advanced*" -o -path "*table*" \) -name "*.png"
```

From the results, copy the advanced crafting table block textures (top + side) and the GUI texture into the mod, e.g.:

```bash
DST=/run/media/fabi/SSD/codeing/Buildcraft-Legacy/src/main/resources/assets/buildcraft/textures
cp "$BC"/buildcraft_resources/assets/buildcraftsilicon/textures/blocks/table/advanced/top.png  "$DST"/block/advanced_crafting_table_top.png
cp "$BC"/buildcraft_resources/assets/buildcraftsilicon/textures/blocks/table/advanced/side.png "$DST"/block/advanced_crafting_table_side.png
cp "$BC"/buildcraft_resources/assets/buildcraftsilicon/textures/gui/advanced_crafting_table.png "$DST"/gui/advanced_crafting_table.png
```

If the exact paths differ, use the `find` output to locate `top.png`/`side.png` under the advanced-table folder and the `advanced_crafting_table.png` (or `autocrafting`/`workbench`) GUI file. If no GUI texture exists in the source, copy `assembly_table.png` as a placeholder base and note it for later art.

- [x] **Step 2: Blockstate + block model**

In `BCBlockStateProvider.java`, add a call inside the silicon-machines section (after line 41 `assemblyTableBlock(...)`):

```java
        advancedCraftingTableBlock(BCBlocks.ADVANCED_CRAFTING_TABLE.get());
```

Then add the helper method next to `assemblyTableBlock` (after line 366):

```java
    private void advancedCraftingTableBlock(Block block) {
        BlockModelBuilder tableModel = models().withExistingParent(name(block), mcLoc("block/cube_bottom_top"))
                .texture("top",    modLoc("block/advanced_crafting_table_top"))
                .texture("bottom", modLoc("block/advanced_crafting_table_top"))
                .texture("side",   modLoc("block/advanced_crafting_table_side"));
        simpleBlock(block, tableModel);
    }
```

- [x] **Step 3: Lang entries**

In `BCEnUSLangProvider.java`, after line 61 (`addBlock(BCBlocks.ASSEMBLY_TABLE, "Assembly Table");`) add:

```java
        addBlock(BCBlocks.ADVANCED_CRAFTING_TABLE, "Advanced Crafting Table");
```

After line 67 (`add("container.buildcraft.assembly_table", "Assembly Table");`) add:

```java
        add("container.buildcraft.advanced_crafting_table", "Advanced Crafting Table");
```

- [x] **Step 4: Loot table**

In `BCBlockLootTableProvider.java`, after line 37 (`dropSelf(BCBlocks.ASSEMBLY_TABLE.get());`) add:

```java
        dropSelf(BCBlocks.ADVANCED_CRAFTING_TABLE.get());
```

- [x] **Step 5: Crafting recipe for the block**

In `BCRecipeProvider.java`, after the Assembly Table recipe (after line 159), add (original BC recipe: crafting table centre, gold gear-ish; here a faithful iron+crafting-table+redstone shape):

```java
        // Advanced Crafting Table
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, BCBlocks.ADVANCED_CRAFTING_TABLE)
                .pattern("IRI")
                .pattern("RCR")
                .pattern("IRI")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('R', Items.REDSTONE)
                .define('C', Blocks.CRAFTING_TABLE)
                .unlockedBy("has_crafting_table", has(Blocks.CRAFTING_TABLE))
                .save(recipeOutput);
```

- [x] **Step 6: Run datagen**

Run: `./gradlew runData 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`; generated files appear under `src/generated/resources/` (blockstate, block model, item model, `lang/en_us.json` entries, loot table, recipe json).

- [x] **Step 7: Compile + in-game polish test**

Run: `./gradlew runClient`
1. The block shows its textures in world and inventory; hovering the item shows "Advanced Crafting Table".
2. The GUI shows the real background texture and the progress bar renders against it.
3. Breaking the block drops itself.
4. The crafting recipe works in a vanilla crafting grid.

Expected: textures, name, GUI background, drop, and recipe all correct.

- [x] **Step 8: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/datagen/ \
        src/main/resources/assets/buildcraft/textures/ \
        src/generated/resources/
git commit -m "feat(advanced-crafting-table): datagen (blockstate, model, lang, loot, recipe) + textures

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- Manual phantom grid → `PhantomSlot` + `Menu.clicked` (Task 1). ✓
- Exact-stack matching → `hasMaterials`/`extractExact` via `isSameItemSameComponents` (Task 3). ✓
- Three separate handlers (blueprint/materials/results) → Task 1 BE. ✓
- Materials INSERT / Results EXTRACT cap → `AdvancedCraftingTableIOHandler` + attachCaps (Task 1). ✓
- Fixed FE cost, config default 5000 → `BCConfig.advancedCraftingTableFeCost` (Task 1) consumed in Task 3. ✓
- ILaserTarget-driven + Laser generalization → Task 3 (impl) + Task 4 (laser). ✓
- Result preview display slot → `SlotDisplay` fed by `assumedResult` (Task 1 menu, populated Task 3). ✓
- Sync power + assumedResult → `getUpdateTag`/`handleUpdateTag` (Task 1). ✓
- No custom payload (phantom via `clicked`) → Task 1 menu. ✓
- Persistence of blueprint ghost items → `saveData`/`loadData` (Task 1), tested Task 4 step 7. ✓
- Datagen (blockstate/model/lang/loot/recipe) + textures → Task 5. ✓
- No FACING (consistent with Assembly Table) → Task 1 block. ✓

**Placeholder scan:** No TBD/TODO in code steps; the only deferred item is optional GUI art if the source lacks a texture (Task 5 Step 1), with an explicit fallback. ✓

**Type consistency:** `getBlueprint/getMaterials/getResults` return `ItemStackHandler` (used by menu slots and crafting helpers); `getIOHandler` returns `IItemHandler` (used by attachCaps); `assumedResult`/`getAssumedResult` `ItemStack` (menu preview + sync); `power`/`getPower` `int`; `advancedCraftingTableFeCost` `int`. Menu index constants (MAT/RES/BP/PREVIEW/PLAYER) are internally consistent with the slot add-order. `serverTick` signature matches `createTickerHelper` usage in the block. ✓
