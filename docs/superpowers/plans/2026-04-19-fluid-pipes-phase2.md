# Fluid Pipes Phase 2 (Clay + Diamond) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add Clay Fluid Pipe (40 mB/t, prefers non-pipe neighbors) and Diamond Fluid Pipe (80 mB/t, 6-direction fluid filter GUI) following the exact BC 1.12 behavior.

**Architecture:** Both pipes follow the existing special-pipe pattern (IronFluidPipeBlock / VoidFluidPipeBlock): register a new `PipeType` in `BCPipeTypes`, a `Pipe` entry in `BCPipes`, a separate `BlockEntityType` in `BCBlockEntities`, and capability registration in `BuildcraftLegacy`. Routing behavior is injected via a new `selectOutputDirections` hook added to `FluidPipeBE`.

**Tech Stack:** NeoForge 1.21.1, Java 21, existing `FluidPipeBE` section system, NeoForge `Capabilities.FluidHandler.ITEM` for Diamond filter resolution.

---

## File Map

**Create:**
- `content/blocks/ClayFluidPipeBlock.java`
- `content/blockentities/ClayFluidPipeBE.java`
- `content/blocks/DiamondFluidPipeBlock.java`
- `content/blockentities/DiamondFluidPipeBE.java`
- `content/menus/DiamondFluidPipeMenu.java`
- `client/screens/DiamondFluidPipeScreen.java`
- `assets/buildcraft/textures/block/clay_fluid_pipe.png`
- `assets/buildcraft/textures/block/diamond_fluid_pipe.png`

**Modify:**
- `content/blockentities/FluidPipeBE.java` — protected hook + helpers
- `registries/BCPipeTypes.java` — FLUID_CLAY, FLUID_DIAMOND
- `registries/BCPipes.java` — CLAY_FLUID, DIAMOND_FLUID
- `registries/BCBlockEntities.java` — CLAY_FLUID_PIPE, DIAMOND_FLUID_PIPE
- `registries/BCMenuTypes.java` — DIAMOND_FLUID_PIPE
- `BuildcraftLegacy.java` — capability registration
- `BuildcraftLegacyClient.java` — screen registration
- `datagen/data/BCRecipeProvider.java` — 2 recipes
- `datagen/assets/BCEnUSLangProvider.java` — menu title key

---

## Task 1: Copy Original BC Textures

**Files:**
- Create: `src/main/resources/assets/buildcraft/textures/block/clay_fluid_pipe.png`
- Create: `src/main/resources/assets/buildcraft/textures/block/diamond_fluid_pipe.png`

- [x] **Step 1: Copy textures from original BC 1.12**

```bash
cp "/run/media/fabi/SSD/codeing/BuildCraft-1.12/buildcraft_resources/assets/buildcrafttransport/textures/pipes/clay_fluid.png" \
   "src/main/resources/assets/buildcraft/textures/block/clay_fluid_pipe.png"

cp "/run/media/fabi/SSD/codeing/BuildCraft-1.12/buildcraft_resources/assets/buildcrafttransport/textures/pipes/diamond_fluid.png" \
   "src/main/resources/assets/buildcraft/textures/block/diamond_fluid_pipe.png"
```

- [x] **Step 2: Verify files exist**

```bash
ls -la src/main/resources/assets/buildcraft/textures/block/clay_fluid_pipe.png \
       src/main/resources/assets/buildcraft/textures/block/diamond_fluid_pipe.png
```

Expected: both files present, non-zero size.

- [x] **Step 3: Commit**

```bash
git add src/main/resources/assets/buildcraft/textures/block/clay_fluid_pipe.png \
        src/main/resources/assets/buildcraft/textures/block/diamond_fluid_pipe.png
git commit -m "feat(fluid-pipes-phase2): add clay and diamond fluid pipe textures from BC 1.12"
```

---

## Task 2: Add Hooks to FluidPipeBE

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/FluidPipeBE.java`

The goal is to:
1. Make `applyMaterialProperties()` overridable (`private` → `protected`).
2. Add `setMaterialProperties(int, int)` so subclasses can set properties without accessing private `sections`.
3. Add `getCurrentFluid()` getter for use in `DiamondFluidPipeBE`.
4. Add `selectOutputDirections(List<Direction>)` hook and call it in `moveFromCenter()`.

- [x] **Step 1: Change `applyMaterialProperties` from `private` to `protected`**

In `FluidPipeBE.java`, change line:
```java
private void applyMaterialProperties() {
```
to:
```java
protected void applyMaterialProperties() {
```

- [x] **Step 2: Add `setMaterialProperties` helper after `applyMaterialProperties()`**

After the closing brace of `applyMaterialProperties()`, add:
```java
protected void setMaterialProperties(int transferPerTick, int delay) {
    this.transferPerTick = transferPerTick;
    this.delay = delay;
    this.capacity = Math.max(1000, transferPerTick * 10);
    for (Section s : sections) {
        s.resizeIncoming(delay);
    }
}
```

- [x] **Step 3: Add `getCurrentFluid()` getter**

After `setMaterialProperties`, add:
```java
protected FluidStack getCurrentFluid() { return currentFluid; }
```

- [x] **Step 4: Add `selectOutputDirections` hook**

After `getCurrentFluid()`, add:
```java
protected List<Direction> selectOutputDirections(List<Direction> candidates) {
    return candidates;
}
```

- [x] **Step 5: Call the hook in `moveFromCenter()`**

In `moveFromCenter()`, find the block that ends the candidate collection:
```java
        if (outputDirs.isEmpty()) return;
        Collections.shuffle(outputDirs);
```

Replace with:
```java
        if (outputDirs.isEmpty()) return;
        outputDirs = selectOutputDirections(outputDirs);
        if (outputDirs.isEmpty()) return;
        Collections.shuffle(outputDirs);
```

- [x] **Step 6: Add missing import for List if needed**

Check that `java.util.List` is already imported (it is — `List.of(0,1,2,3,4,5)` is used in `moveToCenter`). No change needed.

- [x] **Step 7: Compile check**

```bash
./gradlew compileJava 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 8: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/FluidPipeBE.java
git commit -m "feat(fluid-pipe): add selectOutputDirections hook + setMaterialProperties helper"
```

---

## Task 3: Clay Fluid Pipe — Block + BlockEntity

**Files:**
- Create: `src/main/java/com/thepigcat/buildcraft/content/blocks/ClayFluidPipeBlock.java`
- Create: `src/main/java/com/thepigcat/buildcraft/content/blockentities/ClayFluidPipeBE.java`

- [x] **Step 1: Create `ClayFluidPipeBlock.java`**

```java
package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ClayFluidPipeBlock extends FluidPipeBlock {
    public ClayFluidPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(ClayFluidPipeBlock::new);
    }

    @Override
    protected BlockEntityType<? extends PipeBlockEntity<?>> getBlockEntityType() {
        return BCBlockEntities.CLAY_FLUID_PIPE.get();
    }
}
```

- [x] **Step 2: Create `ClayFluidPipeBE.java`**

```java
package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class ClayFluidPipeBE extends FluidPipeBE {
    public ClayFluidPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.CLAY_FLUID_PIPE.get(), pos, blockState);
    }

    @Override
    protected void applyMaterialProperties() {
        setMaterialProperties(40, 10);
    }

    @Override
    protected List<Direction> selectOutputDirections(List<Direction> candidates) {
        List<Direction> nonPipe = candidates.stream()
                .filter(dir -> !(level.getBlockState(worldPosition.relative(dir)).getBlock() instanceof PipeBlock))
                .toList();
        return nonPipe.isEmpty() ? candidates : nonPipe;
    }
}
```

- [x] **Step 3: Compile check**

```bash
./gradlew compileJava 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 4: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blocks/ClayFluidPipeBlock.java \
        src/main/java/com/thepigcat/buildcraft/content/blockentities/ClayFluidPipeBE.java
git commit -m "feat(fluid-pipes-phase2): add ClayFluidPipeBlock and ClayFluidPipeBE"
```

---

## Task 4: Diamond Fluid Pipe — BlockEntity

**Files:**
- Create: `src/main/java/com/thepigcat/buildcraft/content/blockentities/DiamondFluidPipeBE.java`

- [x] **Step 1: Create `DiamondFluidPipeBE.java`**

```java
package com.thepigcat.buildcraft.content.blockentities;

import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

public class DiamondFluidPipeBE extends FluidPipeBE {

    private final ItemStackHandler filterHandler = new ItemStackHandler(54) {
        @Override
        public int getSlotLimit(int slot) { return 1; }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    public DiamondFluidPipeBE(BlockPos pos, BlockState blockState) {
        super(BCBlockEntities.DIAMOND_FLUID_PIPE.get(), pos, blockState);
    }

    @Override
    protected void applyMaterialProperties() {
        setMaterialProperties(80, 10);
    }

    @Override
    protected List<Direction> selectOutputDirections(List<Direction> candidates) {
        FluidStack current = getCurrentFluid();
        if (current.isEmpty()) return candidates;

        List<Direction> priority = new ArrayList<>();
        List<Direction> fallback = new ArrayList<>();

        for (Direction dir : candidates) {
            int base = dir.get3DDataValue() * 9;
            boolean hasFilter = false;
            boolean hasMatch = false;

            for (int i = 0; i < 9; i++) {
                ItemStack filter = filterHandler.getStackInSlot(base + i);
                if (filter.isEmpty()) continue;
                IFluidHandlerItem handler = filter.getCapability(Capabilities.FluidHandler.ITEM);
                if (handler == null || handler.getTanks() == 0) continue;
                FluidStack filterFluid = handler.getFluidInTank(0);
                if (filterFluid.isEmpty()) continue;
                hasFilter = true;
                if (FluidStack.isSameFluid(filterFluid, current)) {
                    hasMatch = true;
                    break;
                }
            }

            if (hasMatch) priority.add(dir);
            else if (!hasFilter) fallback.add(dir);
            // else: filtered but no match — excluded
        }

        if (!priority.isEmpty()) return priority;
        if (!fallback.isEmpty()) return fallback;
        return candidates; // deadlock guard: all filtered but nothing matched
    }

    public ItemStackHandler getFilterHandler() {
        return filterHandler;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("filters")) {
            filterHandler.deserializeNBT(registries, tag.getCompound("filters"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("filters", filterHandler.serializeNBT(registries));
    }
}
```

- [x] **Step 2: Compile check**

```bash
./gradlew compileJava 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 3: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/DiamondFluidPipeBE.java
git commit -m "feat(fluid-pipes-phase2): add DiamondFluidPipeBE with 6-direction fluid filter routing"
```

---

## Task 5: Diamond Fluid Pipe — Block, Menu, Screen

**Files:**
- Create: `src/main/java/com/thepigcat/buildcraft/content/blocks/DiamondFluidPipeBlock.java`
- Create: `src/main/java/com/thepigcat/buildcraft/content/menus/DiamondFluidPipeMenu.java`
- Create: `src/main/java/com/thepigcat/buildcraft/client/screens/DiamondFluidPipeScreen.java`

- [x] **Step 1: Create `DiamondFluidPipeBlock.java`**

```java
package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
import com.thepigcat.buildcraft.content.blockentities.DiamondFluidPipeBE;
import com.thepigcat.buildcraft.content.menus.DiamondFluidPipeMenu;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import com.thepigcat.buildcraft.registries.BCMenuTypes;
import com.thepigcat.buildcraft.util.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class DiamondFluidPipeBlock extends FluidPipeBlock {
    public DiamondFluidPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            DiamondFluidPipeBE be = BlockUtils.getBE(DiamondFluidPipeBE.class, level, pos);
            if (be != null) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new DiamondFluidPipeMenu(id, inv, be),
                        Component.translatable("menu.buildcraft.diamond_fluid_pipe")
                ), buf -> buf.writeBlockPos(pos));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(DiamondFluidPipeBlock::new);
    }

    @Override
    protected BlockEntityType<? extends PipeBlockEntity<?>> getBlockEntityType() {
        return BCBlockEntities.DIAMOND_FLUID_PIPE.get();
    }
}
```

- [x] **Step 2: Create `DiamondFluidPipeMenu.java`**

```java
package com.thepigcat.buildcraft.content.menus;

import com.thepigcat.buildcraft.content.blockentities.DiamondFluidPipeBE;
import com.thepigcat.buildcraft.registries.BCMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public class DiamondFluidPipeMenu extends AbstractContainerMenu {
    public static final int FILTER_ROWS = 6;
    public static final int FILTER_COLS = 9;
    public static final int FILTER_SLOTS = FILTER_ROWS * FILTER_COLS; // 54

    private static final int PLAYER_INV_START = FILTER_SLOTS;
    private static final int PLAYER_INV_END   = FILTER_SLOTS + 27;
    private static final int HOTBAR_END        = FILTER_SLOTS + 36;

    public final DiamondFluidPipeBE blockEntity;

    public DiamondFluidPipeMenu(int containerId, Inventory inv, DiamondFluidPipeBE blockEntity) {
        super(BCMenuTypes.DIAMOND_FLUID_PIPE.get(), containerId);
        this.blockEntity = blockEntity;

        for (int row = 0; row < FILTER_ROWS; row++) {
            for (int col = 0; col < FILTER_COLS; col++) {
                int slot = row * FILTER_COLS + col;
                addSlot(new SlotItemHandler(blockEntity.getFilterHandler(), slot,
                        8 + col * 18, 18 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, 198));
        }
    }

    public DiamondFluidPipeMenu(int containerId, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(containerId, inv, (DiamondFluidPipeBE) inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < FILTER_SLOTS) {
            Slot slot = this.slots.get(slotId);
            ItemStack carried = this.getCarried();
            slot.set(carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < FILTER_SLOTS) return ItemStack.EMPTY;
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < PLAYER_INV_END) {
                if (!this.moveItemStackTo(stack, PLAYER_INV_END, HOTBAR_END, false))
                    return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(stack, PLAYER_INV_START, PLAYER_INV_END, false))
                    return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
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

- [x] **Step 3: Create `DiamondFluidPipeScreen.java`**

```java
package com.thepigcat.buildcraft.client.screens;

import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.content.menus.DiamondFluidPipeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class DiamondFluidPipeScreen extends AbstractContainerScreen<DiamondFluidPipeMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BuildcraftLegacy.MODID, "textures/gui/diamond_pipe_gui.png");

    public DiamondFluidPipeScreen(DiamondFluidPipeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.inventoryLabelY = this.imageHeight - 94;
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

- [x] **Step 4: Compile check**

```bash
./gradlew compileJava 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blocks/DiamondFluidPipeBlock.java \
        src/main/java/com/thepigcat/buildcraft/content/menus/DiamondFluidPipeMenu.java \
        src/main/java/com/thepigcat/buildcraft/client/screens/DiamondFluidPipeScreen.java
git commit -m "feat(fluid-pipes-phase2): add DiamondFluidPipeBlock, Menu and Screen"
```

---

## Task 6: Registry — PipeTypes, Pipes, BlockEntities, MenuTypes

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCPipeTypes.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCPipes.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCBlockEntities.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCMenuTypes.java`

- [x] **Step 1: Add pipe types to `BCPipeTypes.java`**

Add after the `FLUID_IRON` declaration (before `public static void init()`):

```java
public static final PipeTypeHolder<ClayFluidPipeBlock, ItemPipeBlockItem> FLUID_CLAY =
    HELPER.registerPipeType("fluid_clay", ClayFluidPipeBlock::new, ItemPipeBlockItem::new,
        ModelUtils.DEFAULT_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE,
        ModelUtils.DEFAULT_ITEM_MODEL_FILE, "base", "connection");

public static final PipeTypeHolder<DiamondFluidPipeBlock, ItemPipeBlockItem> FLUID_DIAMOND =
    HELPER.registerPipeType("fluid_diamond", DiamondFluidPipeBlock::new, ItemPipeBlockItem::new,
        ModelUtils.DEFAULT_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE,
        ModelUtils.DEFAULT_ITEM_MODEL_FILE, "base", "connection");
```

Also add the two imports at the top of the file:
```java
import com.thepigcat.buildcraft.content.blocks.ClayFluidPipeBlock;
import com.thepigcat.buildcraft.content.blocks.DiamondFluidPipeBlock;
```

- [x] **Step 2: Add pipe entries to `BCPipes.java`**

Add after `IRON_FLUID` (before the closing `}`):

```java
public static final PipeHolder CLAY_FLUID = HELPER.registerPipe("clay_fluid", BCPipeTypes.FLUID_CLAY,
    "Clay Fluid Pipe", 0f, List.of(
        BuildcraftLegacy.rl("block/clay_fluid_pipe")
    ), Either.right(ResourceLocation.parse("clay")), Ingredient.of(Items.CLAY_BALL),
    List.of(BlockTags.MINEABLE_WITH_PICKAXE), 38);

public static final PipeHolder DIAMOND_FLUID = HELPER.registerPipe("diamond_fluid", BCPipeTypes.FLUID_DIAMOND,
    "Diamond Fluid Pipe", 0f, List.of(
        BuildcraftLegacy.rl("block/diamond_fluid_pipe")
    ), Either.right(ResourceLocation.parse("diamond_block")), Ingredient.of(Tags.Items.GEMS_DIAMOND),
    List.of(BlockTags.MINEABLE_WITH_PICKAXE), 39);
```

Check existing imports at the top of `BCPipes.java` — `Items`, `Tags.Items.GEMS_DIAMOND`, `Either`, `ResourceLocation`, `Ingredient`, `BlockTags`, `List` should already be present. If `Items` is not imported, add:
```java
import net.minecraft.world.item.Items;
```

- [x] **Step 3: Add BlockEntity types to `BCBlockEntities.java`**

Add after `IRON_FLUID_PIPE` registration:

```java
public static final Supplier<BlockEntityType<ClayFluidPipeBE>> CLAY_FLUID_PIPE = BLOCK_ENTITIES.register("clay_fluid_pipe",
        () -> BlockEntityType.Builder.of(ClayFluidPipeBE::new, collectBlocks(ClayFluidPipeBlock.class)).build(null));

public static final Supplier<BlockEntityType<DiamondFluidPipeBE>> DIAMOND_FLUID_PIPE = BLOCK_ENTITIES.register("diamond_fluid_pipe",
        () -> BlockEntityType.Builder.of(DiamondFluidPipeBE::new, collectBlocks(DiamondFluidPipeBlock.class)).build(null));
```

Add missing imports:
```java
import com.thepigcat.buildcraft.content.blockentities.ClayFluidPipeBE;
import com.thepigcat.buildcraft.content.blockentities.DiamondFluidPipeBE;
import com.thepigcat.buildcraft.content.blocks.ClayFluidPipeBlock;
import com.thepigcat.buildcraft.content.blocks.DiamondFluidPipeBlock;
```

- [x] **Step 4: Add menu type to `BCMenuTypes.java`**

Add after `EMERALD_PIPE`:
```java
public static final Supplier<MenuType<DiamondFluidPipeMenu>> DIAMOND_FLUID_PIPE =
        registerMenuType("diamond_fluid_pipe", DiamondFluidPipeMenu::new);
```

Add import:
```java
import com.thepigcat.buildcraft.content.menus.DiamondFluidPipeMenu;
```

- [x] **Step 5: Compile check**

```bash
./gradlew compileJava 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 6: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/registries/BCPipeTypes.java \
        src/main/java/com/thepigcat/buildcraft/registries/BCPipes.java \
        src/main/java/com/thepigcat/buildcraft/registries/BCBlockEntities.java \
        src/main/java/com/thepigcat/buildcraft/registries/BCMenuTypes.java
git commit -m "feat(fluid-pipes-phase2): register clay/diamond fluid pipe types, BEs and menu"
```

---

## Task 7: Capability + Screen Registration

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/BuildcraftLegacyClient.java`

- [x] **Step 1: Register fluid capabilities in `BuildcraftLegacy.java`**

In `attachCaps()`, after the `IRON_FLUID_PIPE` registration:
```java
event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
        BCBlockEntities.CLAY_FLUID_PIPE.get(), FluidPipeBE::getFluidHandler);
event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
        BCBlockEntities.DIAMOND_FLUID_PIPE.get(), FluidPipeBE::getFluidHandler);
```

No new imports needed (all types already imported).

- [x] **Step 2: Register screen in `BuildcraftLegacyClient.java`**

In `registerMenuScreens()`, after the `DIAMOND_PIPE` registration:
```java
event.register(BCMenuTypes.DIAMOND_FLUID_PIPE.get(), DiamondFluidPipeScreen::new);
```

Add imports at top:
```java
import com.thepigcat.buildcraft.client.screens.DiamondFluidPipeScreen;
import com.thepigcat.buildcraft.registries.BCMenuTypes;
```

(`BCMenuTypes` may already be imported — check first.)

- [x] **Step 3: Compile check**

```bash
./gradlew compileJava 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 4: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java \
        src/main/java/com/thepigcat/buildcraft/BuildcraftLegacyClient.java
git commit -m "feat(fluid-pipes-phase2): register caps and screen for clay/diamond fluid pipes"
```

---

## Task 8: Datagen — Recipes + Lang

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/datagen/data/BCRecipeProvider.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/datagen/assets/BCEnUSLangProvider.java`

- [x] **Step 1: Add fluid pipe recipes in `BCRecipeProvider.java`**

In `generate()`, find the existing fluid pipe recipe block:
```java
        fluidPipeRecipe(recipeOutput, "sandstone");
        fluidPipeRecipe(recipeOutput, "void");
```

Add after `void`:
```java
        fluidPipeRecipe(recipeOutput, "clay");
        fluidPipeRecipe(recipeOutput, "diamond");
```

The `fluidPipeRecipe(out, "clay")` call looks up `clay_pipe` item + slimeball → `clay_fluid_pipe`. The `fluidPipeRecipe(out, "diamond")` looks up `diamond_pipe` + slimeball → `diamond_fluid_pipe`. Both pipe items already exist in the dynamic pipe registry.

- [x] **Step 2: Add menu title lang key in `BCEnUSLangProvider.java`**

Find the line:
```java
        add("menu.buildcraft.diamond_pipe", "Diamond Pipe");
```

Add below it:
```java
        add("menu.buildcraft.diamond_fluid_pipe", "Diamond Fluid Pipe");
```

- [x] **Step 3: Run datagen**

```bash
./gradlew runData 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`, new recipe files generated in `src/generated/`.

- [x] **Step 4: Verify generated files**

```bash
find src/generated -name "*clay_fluid*" -o -name "*diamond_fluid*" 2>/dev/null | sort
```

Expected: at least `clay_fluid_pipe.json` and `diamond_fluid_pipe.json` recipe files.

- [x] **Step 5: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/datagen/data/BCRecipeProvider.java \
        src/main/java/com/thepigcat/buildcraft/datagen/assets/BCEnUSLangProvider.java \
        src/generated/
git commit -m "feat(fluid-pipes-phase2): add recipes and lang for clay/diamond fluid pipes"
```

---

## Task 9: Integration Test — runClient

- [x] **Step 1: Build and start client**

```bash
./gradlew runClient
```

- [x] **Step 2: In-game test — Clay Fluid Pipe**

1. Craft a clay fluid pipe (`clay_pipe` + slimeball in crafting table → 1 clay fluid pipe).
2. Place a fluid tank with water, connect clay fluid pipe, connect a second pipe network leading to two targets: one machine (tank), one plain pipe segment.
3. Observe: fluid routes preferentially into the machine/tank rather than continuing into the pipe network.
4. In a pipe-only network (no machine neighbors), confirm fluid still flows normally (no deadlock).

- [x] **Step 3: In-game test — Diamond Fluid Pipe**

1. Craft a diamond fluid pipe (`diamond_pipe` + slimeball → 1 diamond fluid pipe).
2. Place it, right-click → filter GUI opens with 6×9 ghost slots + player inventory.
3. Place a water bucket in the EAST slots (row 5, slots 45–53). Place an oil bucket in WEST slots (row 4, slots 36–44).
4. Connect a water source on one side, two output tanks (east = water tank, west = oil tank). Observe:
   - Water routes to the east tank (filter match → priority).
   - If no filter matches (e.g. lava), water goes to fallback (unfiltered) directions only.
   - If all directions are filtered but none match, fluid still moves (deadlock guard).
5. Confirm ghost slots: placing a bucket in filter doesn't consume the bucket from inventory.

- [x] **Step 4: Check item names in-game**

Both pipes should show correct display names in inventory: "Clay Fluid Pipe", "Diamond Fluid Pipe".

---

## Self-Review Checklist

- [x] Spec coverage: Clay routing ✓, Diamond filter ✓, transfer rates ✓, textures ✓, recipes ✓, GUI ✓, caps ✓
- [x] No TBDs or incomplete steps
- [x] Type consistency: `ClayFluidPipeBE` uses `BCBlockEntities.CLAY_FLUID_PIPE`, `DiamondFluidPipeBE` uses `BCBlockEntities.DIAMOND_FLUID_PIPE`, `DiamondFluidPipeMenu` uses `BCMenuTypes.DIAMOND_FLUID_PIPE` — all consistent
- [x] `FluidStack.isSameFluid` is correct NeoForge 1.21.1 API (not `isFluidEqual` from 1.12)
- [x] `Capabilities.FluidHandler.ITEM` is correct NeoForge 1.21.1 cap lookup
- [x] `selectOutputDirections` called before `Collections.shuffle` — correct order
- [x] `getCurrentFluid()` getter added to `FluidPipeBE` before `DiamondFluidPipeBE` uses it (Task 2 before Task 4)
- [x] `BCBlockEntities.FLUID_PIPE` uses `collectBlocksExact` — won't accidentally capture Clay/Diamond subclasses ✓
