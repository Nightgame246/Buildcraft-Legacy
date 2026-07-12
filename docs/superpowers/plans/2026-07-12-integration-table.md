# Integration Table Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port BuildCraft 1.12's Integration Table to NeoForge 1.21.1 — a laser-powered silicon machine that combines a center item + an 8-slot ring of items into an output via an in-code recipe registry, with placeholder demo recipes (real gate recipes follow the Gates phase).

**Architecture:** A `ContainerBlockEntity` (PDL) implementing `ILaserTarget` (so the existing `LaserBE` powers it automatically). Two raw `ItemStackHandler`s: `inputs` (9 slots — slot 0 = center, slots 1–8 = ring) and `output` (1 slot), combined into a sided `IntegrationTableIOHandler` (insert→inputs, extract→output). Each tick it resolves an `IntegrationRecipe` (center Ingredient + exact ring multiset → output, fixed FE cost); when it holds enough FE it consumes the inputs and deposits the output. A 9px-tall slab block with a custom model. Client sync of `power` + `assumedOutput` uses an `onDataPacket` override (NeoForge routes runtime BE updates through `loadData`, not `handleUpdateTag`).

**Tech Stack:** Java 21, NeoForge 1.21.1, PortingDeadLibs 1.1.7 (`ContainerBlockEntity`, `PDLAbstractContainerMenu`, `PDLAbstractContainerScreen`), NeoForge capabilities & item handlers.

> **STATUS (2026-07-12): ✅ IMPLEMENTED & REVIEWED — in-game verification pending.**
> All 4 tasks executed subagent-driven, each compiled (`compileJava` / `runData` BUILD SUCCESSFUL), reviewed clean, committed to `main`. Final whole-branch review (opus): **READY FOR IN-GAME TEST / MERGE**, no blocking defects. Both prior lessons applied from the start (original GUI coords 176×191, `onDataPacket` runtime sync) — so no runtime GUI/sync bugs are expected this time. Commits: `c08bc76` (scaffold) · `64b0a8a` (screen) · `47a26d9` (crafting+demo recipe) · `3bb7884` (datagen+slab model+textures).
> **Deferred to the Gates phase (non-blocking, recorded):** (1) ring `matches()` is greedy, not a true bipartite match → false-negatives possible with overlapping/tag-based ring ingredients (safe now: inert for the demo recipe, never dupe/loss; `matches()` and `craft()` share the same greedy assignment so no under-consumption). When real Gate recipes with overlapping ingredients land, upgrade BOTH `matches()` and `craft()` to a shared backtracking assignment. (2) `IntegrationRecipe.feCost()` is unused (BE reads the global config) — wire per-recipe cost when Gate recipes need it.
> The unchecked steps below are the interactive `runClient` in-game checks — deferred to the developer.

## Global Constraints

- Root package: `com.thepigcat.buildcraft`. Mod id: `buildcraft`.
- `ResourceLocation`: use `BuildcraftLegacy.rl(path)` / `ResourceLocation.fromNamespaceAndPath(...)`; never the two-arg constructor.
- Machine block entities extend PDL `ContainerBlockEntity`; NBT via `saveData`/`loadData` (NOT `saveAdditional`/`loadAdditional`).
- Capabilities registered in `BuildcraftLegacy.attachCaps()` via `RegisterCapabilitiesEvent`.
- Menus extend `PDLAbstractContainerMenu`; screens extend `PDLAbstractContainerScreen`.
- **Runtime client sync MUST use an `onDataPacket` override** applying only client-visible fields (mirror `AdvancedCraftingTableBE`). NeoForge's default `onDataPacket` calls `loadWithComponents()/loadData()`, not `handleUpdateTag()`.
- **GUI coordinates MUST match the copied `integration_table.png` texture** (original BC 176×191 layout). Do not invent coordinates.
- No test source set exists. Per-task verification is `./gradlew compileJava` (+ `runData` for datagen) plus an in-game `runClient` check by the developer (a subagent cannot drive the GUI).
- Faithful to BC 1.12: exact ring multiset match, fixed FE cost per recipe, 9px slab.
- The laser already targets the `ILaserTarget` interface — no laser changes needed.

---

### Task 1: Scaffold — config, recipe registry, IO handler, block entity, menu, block, registrations

One atomic unit: MC registration mutually references block ↔ block entity ↔ menu ↔ registries, so they land together to compile. Crafting/match logic is stubbed here and implemented in Task 3.

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/BCConfig.java`
- Create: `src/main/java/com/thepigcat/buildcraft/api/recipes/IntegrationRecipe.java`
- Create: `src/main/java/com/thepigcat/buildcraft/api/recipes/IntegrationRecipeRegistry.java`
- Create: `src/main/java/com/thepigcat/buildcraft/content/blockentities/IntegrationTableIOHandler.java`
- Create: `src/main/java/com/thepigcat/buildcraft/content/blockentities/IntegrationTableBE.java`
- Create: `src/main/java/com/thepigcat/buildcraft/content/menus/IntegrationTableMenu.java`
- Create: `src/main/java/com/thepigcat/buildcraft/content/blocks/IntegrationTableBlock.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCBlocks.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCBlockEntities.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCMenuTypes.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java` (attachCaps + empty registerIntegrationRecipes)

**Interfaces:**
- Produces:
  - `IntegrationRecipe` record `(ResourceLocation id, Ingredient center, int centerCount, java.util.List<Ingredient> ring, ItemStack output, int feCost)`.
  - `IntegrationRecipeRegistry`: static `register(IntegrationRecipe)`, `get(ResourceLocation)`, `all()`.
  - `IntegrationTableBE`: `int power`, `ItemStack assumedOutput`; getters `getInputs()`/`getOutput()` (`ItemStackHandler`), `getIOHandler()` (`IItemHandler`), `getAssumedOutput()`, `getPower()`; `getRequiredLaserPower()`/`receiveLaserPower(int)`; static `serverTick(...)`. Constants `INPUT_SLOTS=9` (slot 0 = center, 1..8 = ring), `OUTPUT_SLOTS=1`.
  - `IntegrationTableMenu`: constructors `(int, Inventory, IntegrationTableBE)` and `(int, Inventory, RegistryFriendlyByteBuf)`; `getPower()`, `getFeCost()`, `getAssumedOutput()`.
  - `BCBlocks.INTEGRATION_TABLE`, `BCBlockEntities.INTEGRATION_TABLE`, `BCMenuTypes.INTEGRATION_TABLE`, `BCConfig.integrationTableFeCost`.

- [x] **Step 1: Add the config field**

In `BCConfig.java`, after the `advancedCraftingTableFeCost` field, add:

```java

    @ConfigValue(name = "Integration Table FE Cost", comment = "FE required per craft in the Integration Table", category = "integration")
    public static int integrationTableFeCost = 5000;
```

- [x] **Step 2: Create the recipe record**

Create `api/recipes/IntegrationRecipe.java`:

```java
package com.thepigcat.buildcraft.api.recipes;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * An Integration Table recipe: a center item (matched by {@code center}, consumed {@code centerCount}
 * at a time) plus a ring described as an exact multiset of {@code ring} ingredients (each ingredient
 * consumes exactly one non-empty ring slot; no ring slot may be left over), producing {@code output}
 * for a fixed {@code feCost}.
 */
public record IntegrationRecipe(ResourceLocation id, Ingredient center, int centerCount,
                                List<Ingredient> ring, ItemStack output, int feCost) {}
```

- [x] **Step 3: Create the recipe registry**

Create `api/recipes/IntegrationRecipeRegistry.java` (mirrors `AssemblyRecipeRegistry`):

```java
package com.thepigcat.buildcraft.api.recipes;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IntegrationRecipeRegistry {
    private static final Map<ResourceLocation, IntegrationRecipe> REGISTRY = new LinkedHashMap<>();

    public static void register(IntegrationRecipe recipe) {
        REGISTRY.put(recipe.id(), recipe);
    }

    public static IntegrationRecipe get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static Collection<IntegrationRecipe> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    private IntegrationRecipeRegistry() {}
}
```

- [x] **Step 4: Create the IO capability wrapper**

Create `content/blockentities/IntegrationTableIOHandler.java` (2-handler wrapper, identical shape to `AdvancedCraftingTableIOHandler`: insert→inputs, extract→output):

```java
package com.thepigcat.buildcraft.content.blockentities;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;

/**
 * Capability view exposed to pipes/hoppers. Insertion goes only into the inputs handler
 * (center + ring); extraction comes only from the output handler.
 * Slots [0, inputs.size) map to inputs; [inputs.size, total) map to output.
 */
public class IntegrationTableIOHandler implements IItemHandler {
    private final IItemHandlerModifiable inputs;
    private final IItemHandlerModifiable output;

    public IntegrationTableIOHandler(IItemHandlerModifiable inputs, IItemHandlerModifiable output) {
        this.inputs = inputs;
        this.output = output;
    }

    @Override public int getSlots() { return inputs.getSlots() + output.getSlots(); }

    @Override public @NotNull ItemStack getStackInSlot(int slot) {
        return slot < inputs.getSlots()
                ? inputs.getStackInSlot(slot)
                : output.getStackInSlot(slot - inputs.getSlots());
    }

    @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (slot >= inputs.getSlots()) return stack; // output rejects insertion
        return inputs.insertItem(slot, stack, simulate);
    }

    @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < inputs.getSlots()) return ItemStack.EMPTY; // inputs reject pipe extraction
        return output.extractItem(slot - inputs.getSlots(), amount, simulate);
    }

    @Override public int getSlotLimit(int slot) {
        return slot < inputs.getSlots()
                ? inputs.getSlotLimit(slot)
                : output.getSlotLimit(slot - inputs.getSlots());
    }

    @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return slot < inputs.getSlots() && inputs.isItemValid(slot, stack);
    }
}
```

- [x] **Step 5: Create the block entity (inventories/sync/NBT only — crafting stubbed)**

Create `content/blockentities/IntegrationTableBE.java`:

```java
package com.thepigcat.buildcraft.content.blockentities;

import com.portingdeadmods.portingdeadlibs.api.blockentities.ContainerBlockEntity;
import com.thepigcat.buildcraft.api.blockentities.ILaserTarget;
import com.thepigcat.buildcraft.content.menus.IntegrationTableMenu;
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

public class IntegrationTableBE extends ContainerBlockEntity implements ILaserTarget, MenuProvider {
    public static final int INPUT_SLOTS = 9;   // slot 0 = center, slots 1..8 = ring
    public static final int OUTPUT_SLOTS = 1;
    public static final int CENTER = 0;

    public int power = 0;
    public ItemStack assumedOutput = ItemStack.EMPTY;
    protected boolean recipeDirty = true;

    private final ItemStackHandler inputs = new ItemStackHandler(INPUT_SLOTS) {
        @Override protected void onContentsChanged(int slot) {
            recipeDirty = true;
            IntegrationTableBE.this.setChanged();
        }
    };
    private final ItemStackHandler output = new ItemStackHandler(OUTPUT_SLOTS) {
        @Override protected void onContentsChanged(int slot) { IntegrationTableBE.this.setChanged(); }
    };
    private final IntegrationTableIOHandler ioHandler = new IntegrationTableIOHandler(inputs, output);

    public IntegrationTableBE(BlockPos pos, BlockState state) {
        super(BCBlockEntities.INTEGRATION_TABLE.get(), pos, state);
    }

    public ItemStackHandler getInputs() { return inputs; }
    public ItemStackHandler getOutput() { return output; }
    public IItemHandler getIOHandler() { return ioHandler; }
    public ItemStack getAssumedOutput() { return assumedOutput; }
    public int getPower() { return power; }

    // ── ILaserTarget (real impl in crafting task) ──
    @Override public int getRequiredLaserPower() { return 0; }
    @Override public void receiveLaserPower(int fe) { power += fe; setChanged(); }

    // ── Tick (crafting added in a later task) ──
    public static void serverTick(Level level, BlockPos pos, BlockState state, IntegrationTableBE be) {
        // no-op until crafting task
    }

    // ── Sync ──
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putInt("power", power);
        tag.put("assumed", assumedOutput.saveOptional(registries));
        return tag;
    }

    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        applyClientSync(tag, registries);
    }

    // NeoForge's default onDataPacket routes runtime BE updates through loadWithComponents()/
    // loadData(), NOT handleUpdateTag(). Override it to apply only client-visible fields
    // (power + assumedOutput) directly; do NOT call super/loadData (inputs/output sync via menu slots).
    @Override public void onDataPacket(net.minecraft.network.Connection net,
                                       ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) applyClientSync(tag, registries);
    }

    private void applyClientSync(CompoundTag tag, HolderLookup.Provider registries) {
        power = tag.getInt("power");
        assumedOutput = ItemStack.parseOptional(registries, tag.getCompound("assumed"));
    }

    // ── NBT ──
    @Override protected void saveData(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveData(tag, provider);
        tag.put("inputs", inputs.serializeNBT(provider));
        tag.put("output", output.serializeNBT(provider));
        tag.putInt("power", power);
    }

    @Override protected void loadData(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadData(tag, provider);
        inputs.deserializeNBT(provider, tag.getCompound("inputs"));
        output.deserializeNBT(provider, tag.getCompound("output"));
        power = tag.getInt("power");
        recipeDirty = true;
    }

    // ── MenuProvider ──
    @Override public @NotNull Component getDisplayName() {
        return Component.translatable("container.buildcraft.integration_table");
    }

    @Override public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player player) {
        return new IntegrationTableMenu(id, inv, this);
    }
}
```

- [x] **Step 6: Create the menu**

Create `content/menus/IntegrationTableMenu.java`. Coordinates match the original BC `integration_table.png` (176×191). Slot add-order → menu indices: inputs grid 0–8 (grid reading order, middle cell = center handler idx 0, others = ring handler idx 1..8), preview 9, real output 10, player inv/hotbar 11+.

```java
package com.thepigcat.buildcraft.content.menus;

import com.portingdeadmods.portingdeadlibs.api.gui.menus.PDLAbstractContainerMenu;
import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.content.blockentities.IntegrationTableBE;
import com.thepigcat.buildcraft.registries.BCMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class IntegrationTableMenu extends PDLAbstractContainerMenu<IntegrationTableBE> {
    public static final int INPUT_START = 0;   // inputs (center+ring) menu indices 0..8
    public static final int INPUT_END = 9;
    public static final int PREVIEW = 9;        // output preview (display only)
    public static final int OUTPUT = 10;        // real output slot
    public static final int PLAYER_START = 11;  // player inv + hotbar

    public IntegrationTableMenu(int id, @NotNull Inventory inv, @NotNull IntegrationTableBE be) {
        super(BCMenuTypes.INTEGRATION_TABLE.get(), id, inv, be);

        // 3x3 grid at (19,24) with 25px spacing. Middle cell = center (inputs[0]);
        // the other 8 cells = ring (inputs[1..8]) in reading order.
        int ringHandlerIdx = 1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int handlerIdx = (row == 1 && col == 1) ? IntegrationTableBE.CENTER : ringHandlerIdx++;
                addSlot(new SlotItemHandler(be.getInputs(), handlerIdx, 19 + col * 25, 24 + row * 25));
            }
        }

        // Output preview (display only, index 9) at (101, 36)
        addSlot(new SlotDisplay(be::getAssumedOutput, 101, 36));

        // Real output slot (index 10) at (138, 49)
        addSlot(new SlotItemHandler(be.getOutput(), 0, 138, 49));

        addPlayerInventory(inv, 109);
        addPlayerHotbar(inv, 167);
    }

    public IntegrationTableMenu(int id, @NotNull Inventory inv, @NotNull RegistryFriendlyByteBuf buf) {
        this(id, inv, (IntegrationTableBE) inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index >= PLAYER_START) {
                // player inventory → inputs
                if (!moveItemStackTo(stack, INPUT_START, INPUT_END, false)) return ItemStack.EMPTY;
            } else if (index == OUTPUT || (index >= INPUT_START && index < INPUT_END)) {
                // inputs or real output → player inventory
                if (!moveItemStackTo(stack, PLAYER_START, this.slots.size(), true)) return ItemStack.EMPTY;
            } else {
                return ItemStack.EMPTY; // preview: no shift-move
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        }
        return result;
    }

    @Override protected int getMergeableSlotCount() { return INPUT_END; }

    public int getPower() { return blockEntity.power; }
    public int getFeCost() { return BCConfig.integrationTableFeCost; }
    public ItemStack getAssumedOutput() { return blockEntity.getAssumedOutput(); }
}
```

Note: `addPlayerHotbar(inv, 167)` = 109 (inv top) + 58, matching the vanilla 3-row + gap offset used by the sibling menus.

- [x] **Step 7: Create the block (9px slab)**

Create `content/blocks/IntegrationTableBlock.java`:

```java
package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.content.blockentities.IntegrationTableBE;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class IntegrationTableBlock extends BaseEntityBlock {
    public static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 9, 16); // 9px-tall slab

    public IntegrationTableBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return BCBlockEntities.INTEGRATION_TABLE.get().create(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof IntegrationTableBE be) {
            player.openMenu(be, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, BCBlockEntities.INTEGRATION_TABLE.get(), IntegrationTableBE::serverTick);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(IntegrationTableBlock::new);
    }
}
```

- [x] **Step 8: Register the block**

In `BCBlocks.java`, after the `ADVANCED_CRAFTING_TABLE` entry, add (`.noOcclusion()` because it is not a full cube):

```java
    public static final DeferredBlock<IntegrationTableBlock> INTEGRATION_TABLE = registerBlockAndItem("integration_table", IntegrationTableBlock::new,
            BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).mapColor(MapColor.METAL).requiresCorrectToolForDrops().noOcclusion());
```

Add import: `import com.thepigcat.buildcraft.content.blocks.IntegrationTableBlock;`.

- [x] **Step 9: Register the block entity type**

In `BCBlockEntities.java`, after the `ADVANCED_CRAFTING_TABLE` entry, add:

```java
    public static final Supplier<BlockEntityType<IntegrationTableBE>> INTEGRATION_TABLE = BLOCK_ENTITIES.register("integration_table",
            () -> BlockEntityType.Builder.of(IntegrationTableBE::new, BCBlocks.INTEGRATION_TABLE.get()).build(null));
```

Add import: `import com.thepigcat.buildcraft.content.blockentities.IntegrationTableBE;`.

- [x] **Step 10: Register the menu type**

In `BCMenuTypes.java`, after the `ADVANCED_CRAFTING_TABLE` entry, add:

```java
    public static final Supplier<MenuType<IntegrationTableMenu>> INTEGRATION_TABLE =
            registerMenuType("integration_table", IntegrationTableMenu::new);
```

Add import: `import com.thepigcat.buildcraft.content.menus.IntegrationTableMenu;`.

- [x] **Step 11: Attach the capability + add empty recipe registration hook**

In `BuildcraftLegacy.java` `attachCaps(...)`, after the ADVANCED_CRAFTING_TABLE item-handler registration, add:

```java
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BCBlockEntities.INTEGRATION_TABLE.get(),
                (be, side) -> be.getIOHandler());
```

Then add a new method next to `registerAssemblyRecipes()` (recipes are filled in Task 3):

```java
    private static void registerIntegrationRecipes() {
        // Placeholder recipes added in a later task.
    }
```

And call it in `onCommonSetup`, right after `registerAssemblyRecipes();`:

```java
        registerIntegrationRecipes();
```

- [x] **Step 12: Compile**

Run: `bash gradlew compileJava 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 13: In-game smoke test (no GUI yet — no screen)**

Run: `bash gradlew runClient`
1. `/give @s buildcraft:integration_table`, place it — it renders as a **9px slab** (short block), and you can stand/walk over it correctly (collision is 9px tall). It may be untextured (missing-texture) until the datagen task; that is fine.
2. Feed items via a hopper into the block; break it and confirm items were stored (inputs handler works via the cap).
3. Do NOT right-click yet (no screen → client crash; screen is Task 2).

Expected: slab renders at correct height, hopper insertion works, no server errors.

- [x] **Step 14: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/BCConfig.java \
        src/main/java/com/thepigcat/buildcraft/api/recipes/IntegrationRecipe.java \
        src/main/java/com/thepigcat/buildcraft/api/recipes/IntegrationRecipeRegistry.java \
        src/main/java/com/thepigcat/buildcraft/content/blockentities/IntegrationTableIOHandler.java \
        src/main/java/com/thepigcat/buildcraft/content/blockentities/IntegrationTableBE.java \
        src/main/java/com/thepigcat/buildcraft/content/menus/IntegrationTableMenu.java \
        src/main/java/com/thepigcat/buildcraft/content/blocks/IntegrationTableBlock.java \
        src/main/java/com/thepigcat/buildcraft/registries/BCBlocks.java \
        src/main/java/com/thepigcat/buildcraft/registries/BCBlockEntities.java \
        src/main/java/com/thepigcat/buildcraft/registries/BCMenuTypes.java \
        src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java
git commit -m "feat(integration-table): scaffold block, BE, menu, recipe registry, IO cap (no crafting yet)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Screen + client registration

**Files:**
- Create: `src/main/java/com/thepigcat/buildcraft/client/screens/IntegrationTableScreen.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/BuildcraftLegacyClient.java`

**Interfaces:**
- Consumes: `IntegrationTableMenu.getPower()`, `getFeCost()`; `BCMenuTypes.INTEGRATION_TABLE`.

- [x] **Step 1: Create the screen**

Create `client/screens/IntegrationTableScreen.java` (matches the original BC layout: `imageHeight=191`, progress bar dest `(164,22)`, source strip at `(176,0)`):

```java
package com.thepigcat.buildcraft.client.screens;

import com.portingdeadmods.portingdeadlibs.api.client.screens.PDLAbstractContainerScreen;
import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.content.menus.IntegrationTableMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

public class IntegrationTableScreen extends PDLAbstractContainerScreen<IntegrationTableMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BuildcraftLegacy.MODID, "textures/gui/integration_table.png");

    // Progress bar: source strip at texture (176, 0), 4px x 70px, drawn at (164, 22), fills bottom-up.
    private static final int PROGRESS_X = 164;
    private static final int PROGRESS_Y = 22;
    private static final int PROGRESS_W = 4;
    private static final int PROGRESS_H = 70;

    public IntegrationTableScreen(IntegrationTableMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 191; // matches the original integration_table.png layout
        this.inventoryLabelY = this.imageHeight - 94; // = 97, just above player inv at y=109
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

In `BuildcraftLegacyClient.java` `registerMenuScreens(...)`, after the `ADVANCED_CRAFTING_TABLE` registration, add:

```java
        event.register(BCMenuTypes.INTEGRATION_TABLE.get(), IntegrationTableScreen::new);
```

Add import: `import com.thepigcat.buildcraft.client.screens.IntegrationTableScreen;`.

- [x] **Step 3: Compile**

Run: `bash gradlew compileJava 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: In-game test (GUI opens; texture arrives in the datagen task)**

Run: `bash gradlew runClient`
1. Place the block, right-click → GUI opens without crashing (background may be missing-texture until Task 4).
2. Place items into the ring/center cells and the layout is usable (slots don't overlap the player inventory).
3. Shift-click from player inventory → items land in the input (center+ring) slots; shift-click a ring/center/output item → returns to player inventory.

Expected: GUI opens, layout usable, shift-click routing correct.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/client/screens/IntegrationTableScreen.java \
        src/main/java/com/thepigcat/buildcraft/BuildcraftLegacyClient.java
git commit -m "feat(integration-table): GUI screen + client registration

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Recipe matching + craft logic + demo recipes

Implements ring multiset matching, recipe resolution, the craft, real `ILaserTarget` power, and registers placeholder demo recipes. Edits `IntegrationTableBE.java` and fills `registerIntegrationRecipes()` in `BuildcraftLegacy.java`.

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/IntegrationTableBE.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java` (fill `registerIntegrationRecipes`)

**Interfaces:**
- Consumes: `BCConfig.integrationTableFeCost`, `IntegrationRecipe`, `IntegrationRecipeRegistry`.
- Produces: real `getRequiredLaserPower()`, populated `assumedOutput`, functioning `serverTick`.

- [x] **Step 1: Add imports to the BE**

At the top of `IntegrationTableBE.java`, add:

```java
import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.api.recipes.IntegrationRecipe;
import com.thepigcat.buildcraft.api.recipes.IntegrationRecipeRegistry;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
```

- [x] **Step 2: Add the cached recipe field**

Below `protected boolean recipeDirty = true;` add:

```java
    @Nullable private IntegrationRecipe currentRecipe = null;
```

- [x] **Step 3: Replace the ILaserTarget stub**

Replace:

```java
    @Override public int getRequiredLaserPower() { return 0; }
```

with:

```java
    @Override public int getRequiredLaserPower() {
        return canCraft() ? Math.max(0, BCConfig.integrationTableFeCost - power) : 0;
    }
```

- [x] **Step 4: Replace the serverTick stub and add the crafting helpers**

Replace the no-op `serverTick` with:

```java
    public static void serverTick(Level level, BlockPos pos, BlockState state, IntegrationTableBE be) {
        if (level.isClientSide) return;
        boolean changed = false;

        if (be.recipeDirty) {
            be.recomputeRecipe();
            changed = true;
        }

        int cost = BCConfig.integrationTableFeCost;
        if (be.canCraft() && be.power >= cost) {
            be.power -= cost;
            be.craft();
            changed = true;
        }

        if (changed) {
            level.sendBlockUpdated(pos, state, state, 3);
            be.setChanged();
        }
    }

    private void recomputeRecipe() {
        recipeDirty = false;
        currentRecipe = null;
        assumedOutput = ItemStack.EMPTY;
        ItemStack center = inputs.getStackInSlot(CENTER);
        List<ItemStack> ring = ringStacks();
        for (IntegrationRecipe recipe : IntegrationRecipeRegistry.all()) {
            if (matches(recipe, center, ring)) {
                currentRecipe = recipe;
                assumedOutput = recipe.output().copy();
                return;
            }
        }
    }

    /** The 8 ring stacks (input slots 1..8). */
    private List<ItemStack> ringStacks() {
        List<ItemStack> ring = new ArrayList<>(INPUT_SLOTS - 1);
        for (int s = 1; s < INPUT_SLOTS; s++) ring.add(inputs.getStackInSlot(s));
        return ring;
    }

    /**
     * Center must satisfy the center ingredient with >= centerCount, and the non-empty ring
     * stacks must be an EXACT multiset match to recipe.ring(): every ring ingredient consumes
     * exactly one distinct non-empty ring slot, and no non-empty ring slot is left unmatched.
     */
    private static boolean matches(IntegrationRecipe recipe, ItemStack center, List<ItemStack> ring) {
        if (!recipe.center().test(center) || center.getCount() < recipe.centerCount()) return false;
        List<ItemStack> nonEmpty = new ArrayList<>();
        for (ItemStack s : ring) if (!s.isEmpty()) nonEmpty.add(s);
        if (nonEmpty.size() != recipe.ring().size()) return false; // exact multiset size
        boolean[] used = new boolean[nonEmpty.size()];
        for (Ingredient req : recipe.ring()) {
            boolean found = false;
            for (int i = 0; i < nonEmpty.size(); i++) {
                if (!used[i] && req.test(nonEmpty.get(i))) { used[i] = true; found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    private boolean canCraft() {
        if (currentRecipe == null || assumedOutput.isEmpty()) return false;
        // still matches current inventory, and output slot can accept the result
        if (!matches(currentRecipe, inputs.getStackInSlot(CENTER), ringStacks())) return false;
        return output.insertItem(0, assumedOutput.copy(), true).isEmpty();
    }

    private void craft() {
        IntegrationRecipe recipe = currentRecipe;
        if (recipe == null) return;
        // Consume center
        inputs.extractItem(CENTER, recipe.centerCount(), false);
        // Consume one item per ring ingredient from a distinct matching ring slot (1..8)
        boolean[] consumed = new boolean[INPUT_SLOTS]; // index by input slot
        for (Ingredient req : recipe.ring()) {
            for (int s = 1; s < INPUT_SLOTS; s++) {
                if (!consumed[s] && !inputs.getStackInSlot(s).isEmpty() && req.test(inputs.getStackInSlot(s))) {
                    inputs.extractItem(s, 1, false);
                    consumed[s] = true;
                    break;
                }
            }
        }
        insertOrEject(recipe.output().copy());
    }

    /** Insert into the output handler; overflow goes to an adjacent inventory, else drops in-world. */
    private void insertOrEject(ItemStack stack) {
        ItemStack remainder = output.insertItem(0, stack, false);
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

- [x] **Step 5: Register the demo recipe**

In `BuildcraftLegacy.java`, replace the placeholder body of `registerIntegrationRecipes()` with a demo recipe (center: 1 red_chipset; ring: exactly 4 redstone in 4 ring slots → iron_chipset):

```java
    private static void registerIntegrationRecipes() {
        // DEMO / placeholder recipe — replace with real gate recipes when the Gates phase lands.
        IntegrationRecipeRegistry.register(new IntegrationRecipe(
                BuildcraftLegacy.rl("demo_chipset_upgrade"),
                Ingredient.of(BCItems.RED_CHIPSET.get()), 1,
                java.util.List.of(
                        Ingredient.of(Items.REDSTONE), Ingredient.of(Items.REDSTONE),
                        Ingredient.of(Items.REDSTONE), Ingredient.of(Items.REDSTONE)),
                new ItemStack(BCItems.IRON_CHIPSET.get()),
                BCConfig.integrationTableFeCost));
    }
```

Ensure these imports exist in `BuildcraftLegacy.java` (some already do for the assembly recipes): `com.thepigcat.buildcraft.api.recipes.IntegrationRecipe`, `com.thepigcat.buildcraft.api.recipes.IntegrationRecipeRegistry`, `net.minecraft.world.item.crafting.Ingredient`, `net.minecraft.world.item.Items`, `net.minecraft.world.item.ItemStack`, `com.thepigcat.buildcraft.registries.BCItems`.

- [x] **Step 6: Compile**

Run: `bash gradlew compileJava 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: In-game verification (end-to-end — the laser already works)**

Run: `bash gradlew runClient`
1. Place the Integration Table. Put a **red chipset** in the center cell and **4 redstone** in 4 separate ring cells → the **output preview** shows an iron chipset, and it updates live if you change the inputs (e.g. remove one redstone → preview clears).
2. Place a Laser aimed at the table (≤8 blocks) and power it → the table crafts: an iron chipset appears in the real output slot, the center chipset + 4 redstone are consumed, the progress bar advances.
3. Fill the output slot and confirm crafting halts (no FE consumed while it can't accept output).
4. Attach an item pipe extracting from the output (pulls the iron chipset) and one inserting (lands in inputs, never output).
5. Persistence: set inputs, partially charge, save & reload the world — inputs/output/power survive.

Expected: preview live-updates, craft works end-to-end, pipe sidedness correct, state persists.

- [x] **Step 8: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/IntegrationTableBE.java \
        src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java
git commit -m "feat(integration-table): ring multiset matching, craft logic, demo recipe

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: Datagen + custom slab model + textures

**Files:**
- Create (static resource): `src/main/resources/assets/buildcraft/models/block/integration_table.json`
- Create (textures): `src/main/resources/assets/buildcraft/textures/block/integration_table_{top,middle,bottom,side,center}.png` and `.../textures/gui/integration_table.png`
- Modify: `src/main/java/com/thepigcat/buildcraft/datagen/assets/BCBlockStateProvider.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/datagen/assets/BCEnUSLangProvider.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/datagen/data/BCBlockLootTableProvider.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/datagen/data/BCRecipeProvider.java`

- [x] **Step 1: Copy textures from the original BC 1.12 assets**

```bash
BC=/run/media/fabi/SSD/codeing/BuildCraft-1.12/buildcraft_resources/assets/buildcraftsilicon/textures
DST=/run/media/fabi/SSD/codeing/Buildcraft-Legacy/src/main/resources/assets/buildcraft/textures
cp "$BC"/blocks/table/integration/top.png    "$DST"/block/integration_table_top.png
cp "$BC"/blocks/table/integration/middle.png "$DST"/block/integration_table_middle.png
cp "$BC"/blocks/table/integration/bottom.png "$DST"/block/integration_table_bottom.png
cp "$BC"/blocks/table/integration/side.png   "$DST"/block/integration_table_side.png
cp "$BC"/blocks/table/integration/center.png "$DST"/block/integration_table_center.png
cp "$BC"/gui/integration_table.png           "$DST"/gui/integration_table.png
```

Verify all six files exist afterward with `ls -la "$DST"/block/integration_table_*.png "$DST"/gui/integration_table.png`. (If a source path differs, locate it with `find /run/media/fabi/SSD/codeing/BuildCraft-1.12 -path '*table/integration*' -name '*.png'`.)

- [x] **Step 2: Hand-author the block model (the 12-element 9px slab, textures repointed to buildcraft)**

Create `src/main/resources/assets/buildcraft/models/block/integration_table.json`:

```json
{
    "parent": "block/block",
    "textures": {
        "particle": "buildcraft:block/integration_table_top",
        "top": "buildcraft:block/integration_table_top",
        "middle": "buildcraft:block/integration_table_middle",
        "bottom": "buildcraft:block/integration_table_bottom",
        "side": "buildcraft:block/integration_table_side",
        "center": "buildcraft:block/integration_table_center"
    },
    "elements": [
        {"from": [0, 0, 0], "to": [16, 1, 16], "faces": {
            "down": {"texture": "#bottom"}, "up": {"texture": "#middle"},
            "north": {"texture": "#side"}, "south": {"texture": "#side"}, "west": {"texture": "#side"}, "east": {"texture": "#side"}}},
        {"from": [1, 1, 1], "to": [5, 3, 5], "faces": {
            "down": {"texture": "#bottom"}, "up": {"texture": "#bottom"},
            "north": {"texture": "#side"}, "south": {"texture": "#side"}, "west": {"texture": "#side"}, "east": {"texture": "#side"}}},
        {"from": [11, 1, 1], "to": [15, 3, 5], "faces": {
            "down": {"texture": "#bottom"}, "up": {"texture": "#bottom"},
            "north": {"texture": "#side"}, "south": {"texture": "#side"}, "west": {"texture": "#side"}, "east": {"texture": "#side"}}},
        {"from": [1, 1, 11], "to": [5, 3, 15], "faces": {
            "down": {"texture": "#bottom"}, "up": {"texture": "#bottom"},
            "north": {"texture": "#side"}, "south": {"texture": "#side"}, "west": {"texture": "#side"}, "east": {"texture": "#side"}}},
        {"from": [11, 1, 11], "to": [15, 3, 15], "faces": {
            "down": {"texture": "#bottom"}, "up": {"texture": "#bottom"},
            "north": {"texture": "#side"}, "south": {"texture": "#side"}, "west": {"texture": "#side"}, "east": {"texture": "#side"}}},
        {"from": [0, 3, 0], "to": [16, 9, 5], "faces": {
            "down": {"texture": "#middle"}, "up": {"texture": "#top"},
            "north": {"texture": "#side"}, "south": {"texture": "#side"}, "west": {"texture": "#side"}, "east": {"texture": "#side"}}},
        {"from": [0, 3, 0], "to": [5, 9, 16], "faces": {
            "down": {"texture": "#middle"}, "up": {"texture": "#top"},
            "north": {"texture": "#side"}, "south": {"texture": "#side"}, "west": {"texture": "#side"}, "east": {"texture": "#side"}}},
        {"from": [0, 3, 11], "to": [16, 9, 16], "faces": {
            "down": {"texture": "#middle"}, "up": {"texture": "#top"},
            "north": {"texture": "#side"}, "south": {"texture": "#side"}, "west": {"texture": "#side"}, "east": {"texture": "#side"}}},
        {"from": [11, 3, 0], "to": [16, 9, 16], "faces": {
            "down": {"texture": "#middle"}, "up": {"texture": "#top"},
            "north": {"texture": "#side"}, "south": {"texture": "#side"}, "west": {"texture": "#side"}, "east": {"texture": "#side"}}},
        {"from": [5, 3, 5], "to": [11, 7, 11], "faces": {
            "down": {"texture": "#middle"}, "up": {"texture": "#top"},
            "north": {"texture": "#side"}, "south": {"texture": "#side"}, "west": {"texture": "#side"}, "east": {"texture": "#side"}}},
        {"from": [5, 7, 5], "to": [11, 8, 11], "faces": {
            "down": {"texture": "#center"}, "up": {"texture": "#center"},
            "north": {"texture": "#center"}, "south": {"texture": "#center"}, "west": {"texture": "#center"}, "east": {"texture": "#center"}}}
    ]
}
```

- [x] **Step 3: Blockstate datagen pointing at the hand-authored model**

In `BCBlockStateProvider.java`, add a call in the silicon-machines section (after `advancedCraftingTableBlock(...)`):

```java
        integrationTableBlock(BCBlocks.INTEGRATION_TABLE.get());
```

Add the helper method next to `advancedCraftingTableBlock` (it references the hand-authored model; the pipe loop below already ignores non-pipe blocks, so this block is not double-generated):

```java
    private void integrationTableBlock(Block block) {
        simpleBlock(block, models().getExistingFile(modLoc("block/integration_table")));
    }
```

(The block item model is auto-generated by `BCItemModelProvider.blockItems()` → `parentItemBlock`, whose parent is `block/integration_table`; no item-model change is needed.)

- [x] **Step 4: Lang entries**

In `BCEnUSLangProvider.java`, after the advanced crafting table entries, add:

```java
        addBlock(BCBlocks.INTEGRATION_TABLE, "Integration Table");
```

and:

```java
        add("container.buildcraft.integration_table", "Integration Table");
```

- [x] **Step 5: Loot table**

In `BCBlockLootTableProvider.java`, after the advanced crafting table line, add:

```java
        dropSelf(BCBlocks.INTEGRATION_TABLE.get());
```

- [x] **Step 6: Crafting recipe for the block (original OiO/OrO/OgO)**

In `BCRecipeProvider.java`, after the advanced crafting table recipe, add:

```java
        // Integration Table (original BC 1.12 recipe)
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, BCBlocks.INTEGRATION_TABLE)
                .pattern("OiO")
                .pattern("OrO")
                .pattern("OgO")
                .define('O', Blocks.OBSIDIAN)
                .define('i', Tags.Items.INGOTS_GOLD)
                .define('r', BCItems.RED_CHIPSET.get())
                .define('g', BCItems.DIAMOND_GEAR.get())
                .unlockedBy("has_red_chipset", has(BCItems.RED_CHIPSET.get()))
                .save(recipeOutput);
```

Ensure `BCItems`, `Blocks`, and `Tags` are imported in `BCRecipeProvider.java` (Blocks/Tags already used by sibling recipes; add `import com.thepigcat.buildcraft.registries.BCItems;` if absent).

- [x] **Step 7: Run datagen + compile**

Run: `bash gradlew runData 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL`; generated blockstate `integration_table.json` (referencing `buildcraft:block/integration_table`), item model, `lang/en_us.json` entries, loot table, and recipe json appear under `src/generated/resources/`.
Then: `bash gradlew compileJava 2>&1 | tail -5` → `BUILD SUCCESSFUL`.

- [ ] **Step 8: In-game polish test**

Run: `bash gradlew runClient`
1. The block renders as the textured 9px slab (nubs + center puck) in world and inventory; tooltip reads "Integration Table".
2. The GUI shows the real background texture; the ring/center/preview/output slots sit correctly on it; the progress bar renders on the right edge and animates while charging.
3. Breaking the block drops itself; the crafting recipe (`OiO/OrO/OgO`) works in a vanilla grid.

Expected: textures, name, GUI background, slab model, drop, and recipe all correct.

- [x] **Step 9: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/datagen/ \
        src/main/resources/assets/buildcraft/ \
        src/generated/resources/
git commit -m "feat(integration-table): datagen (blockstate, lang, loot, recipe) + slab model + textures

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:**
- Center + 8-ring + output, single `inputs`(9, slot 0 = center) + `output`(1) → Task 1 BE. ✓
- Exact ring multiset match → `matches()` in Task 3. ✓
- Fixed FE cost, config default 5000 → `BCConfig.integrationTableFeCost` (Task 1) consumed in Task 3. ✓
- ILaserTarget-driven (laser already targets the interface) → Task 1 (stub) + Task 3 (real). ✓
- Inputs INSERT / output EXTRACT cap → `IntegrationTableIOHandler` + attachCaps (Task 1). ✓
- Output preview `SlotDisplay` fed by `assumedOutput`, live via `onDataPacket` → Task 1 (menu+sync), populated Task 3. ✓
- Original GUI coords (176×191, ring 25px @ 19/24, preview 101/36, output 138/49, player 109, bar 164/22) → Task 1 menu + Task 2 screen. ✓
- 9px slab with custom VoxelShape + `.noOcclusion()` → Task 1 block. ✓
- Original 12-element model + 5 textures + GUI texture → Task 4. ✓
- In-code recipe registry (mirror AssemblyRecipe) + placeholder demo recipe → Tasks 1 & 3. ✓
- Persistence of inputs/output/power → saveData/loadData (Task 1), tested Task 3 Step 7. ✓
- Datagen (blockstate/model/item/lang/loot/recipe) → Task 4. ✓

**Placeholder scan:** No TBD/TODO. The demo recipe is deliberately labelled placeholder. Task 1's `registerIntegrationRecipes()` is intentionally empty until Task 3 fills it.

**Type consistency:** `getInputs()`/`getOutput()` return `ItemStackHandler`; `getIOHandler()` returns `IItemHandler`; `assumedOutput`/`getAssumedOutput()` `ItemStack`; `power`/`getPower()` `int`; `integrationTableFeCost` `int`. Menu index constants (INPUT 0–8, PREVIEW 9, OUTPUT 10, PLAYER 11+) match the slot add-order. `IntegrationRecipe` accessors (`center()`, `centerCount()`, `ring()`, `output()`, `feCost()`) are used consistently in `matches()`/`craft()`/`recomputeRecipe()`. `serverTick` signature matches `createTickerHelper`. `CENTER=0` constant used in BE and menu.
