# Assembly Table + Laser + Chipsets Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the BC 1.12 Assembly Table, Laser, and Redstone Chipsets to NeoForge 1.21.1 as Phase E-1.

**Architecture:** Java-based `AssemblyRecipeRegistry`, Laser pushes FE to `AssemblyTableBE` via `ILaserTarget` interface, GUI shows recipe states synced via `getUpdateTag`/`handleUpdateTag`, recipe toggle via `SetRecipeStatePayload` (C→S).

**Tech Stack:** NeoForge 1.21.1, PDL `ContainerBlockEntity`/`PDLAbstractContainerMenu`/`PDLAbstractContainerScreen`, JOML for TESR math, NeoForge `DeferredRegister`

---

## Context for implementers

- Root package: `com.thepigcat.buildcraft`
- Source root: `src/main/java/com/thepigcat/buildcraft/`
- Resources root: `src/main/resources/assets/buildcraft/`
- Existing registries to modify: `BCItems`, `BCBlocks`, `BCBlockEntities`, `BCMenuTypes`, `BCConfig`
- Main mod class (for capabilities + payloads): `BuildcraftLegacy.java`
- Datagen entry: `DataGatherer.java`
- All blocks use `DeferredBlock` / `DeferredItem` via `BCBlocks.registerBlockAndItem()`
- All ContainerBlockEntity subclasses use `saveData`/`loadData` (NOT `saveAdditional`/`loadAdditional`)
- Pattern for client sync: override `getUpdatePacket()` → `ClientboundBlockEntityDataPacket.create(this)`, override `getUpdateTag()` + `handleUpdateTag()`
- Build/verify: `bash /run/media/fabi/SSD/codeing/Buildcraft-Legacy/gradlew compileJava 2>&1 | tail -5`

---

## Task 1: Foundation types

**Files:**
- Create: `src/main/java/com/thepigcat/buildcraft/content/enums/EnumAssemblyRecipeState.java`
- Create: `src/main/java/com/thepigcat/buildcraft/api/blockentities/ILaserTarget.java`
- Create: `src/main/java/com/thepigcat/buildcraft/api/recipes/AssemblyRecipe.java`
- Create: `src/main/java/com/thepigcat/buildcraft/api/recipes/AssemblyRecipeRegistry.java`

- [ ] **Step 1: Create EnumAssemblyRecipeState**

```java
// src/main/java/com/thepigcat/buildcraft/content/enums/EnumAssemblyRecipeState.java
package com.thepigcat.buildcraft.content.enums;

public enum EnumAssemblyRecipeState {
    POSSIBLE,            // items in inv match, not saved by player
    SAVED,               // player saved it, but items missing
    SAVED_ENOUGH,        // saved + items present, waiting for active slot
    SAVED_ENOUGH_ACTIVE  // currently receiving power / crafting
}
```

- [ ] **Step 2: Create ILaserTarget**

```java
// src/main/java/com/thepigcat/buildcraft/api/blockentities/ILaserTarget.java
package com.thepigcat.buildcraft.api.blockentities;

public interface ILaserTarget {
    /** FE still required to finish the active recipe. 0 if no active recipe. */
    int getRequiredLaserPower();
    /** Called by LaserBE each tick with the FE it is delivering. */
    void receiveLaserPower(int fe);
}
```

- [ ] **Step 3: Create AssemblyRecipe**

```java
// src/main/java/com/thepigcat/buildcraft/api/recipes/AssemblyRecipe.java
package com.thepigcat.buildcraft.api.recipes;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.Set;

public record AssemblyRecipe(ResourceLocation id, Set<Ingredient> inputs, ItemStack output, int feCost) {}
```

- [ ] **Step 4: Create AssemblyRecipeRegistry**

```java
// src/main/java/com/thepigcat/buildcraft/api/recipes/AssemblyRecipeRegistry.java
package com.thepigcat.buildcraft.api.recipes;

import net.minecraft.resources.ResourceLocation;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AssemblyRecipeRegistry {
    private static final Map<ResourceLocation, AssemblyRecipe> REGISTRY = new LinkedHashMap<>();

    public static void register(AssemblyRecipe recipe) {
        REGISTRY.put(recipe.id(), recipe);
    }

    public static AssemblyRecipe get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static Collection<AssemblyRecipe> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    private AssemblyRecipeRegistry() {}
}
```

- [ ] **Step 5: Compile**

```bash
bash /run/media/fabi/SSD/codeing/Buildcraft-Legacy/gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/enums/EnumAssemblyRecipeState.java \
        src/main/java/com/thepigcat/buildcraft/api/blockentities/ILaserTarget.java \
        src/main/java/com/thepigcat/buildcraft/api/recipes/AssemblyRecipe.java \
        src/main/java/com/thepigcat/buildcraft/api/recipes/AssemblyRecipeRegistry.java
git commit -m "feat(silicon): add AssemblyRecipe registry, ILaserTarget, EnumAssemblyRecipeState"
```

---

## Task 2: BCConfig entries + Chipset items

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/BCConfig.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCItems.java`

- [ ] **Step 1: Add config values to BCConfig**

Add after the existing `@ConfigValue` fields (before the closing brace):

```java
// Assembly Table / Laser
@ConfigValue(name = "Laser Battery Capacity", comment = "FE capacity of the Laser block", category = "capacity.energy")
public static int laserBatteryCapacity = 4000;
@ConfigValue(name = "Laser Max Receive", comment = "Max FE/t the Laser accepts from kinesis pipes", category = "capacity.energy")
public static int laserMaxReceive = 200;
@ConfigValue(name = "Laser Max Output", comment = "Max FE/t the Laser pushes to Assembly Table per tick", category = "production.energy")
public static int laserMaxOutput = 40;

// Chipset FE costs
@ConfigValue(name = "Red Chipset FE Cost", comment = "FE required to craft a Red (Redstone) Chipset", category = "assembly")
public static int redChipsetFeCost = 10_000;
@ConfigValue(name = "Iron Chipset FE Cost", comment = "FE required to craft an Iron Chipset", category = "assembly")
public static int ironChipsetFeCost = 20_000;
@ConfigValue(name = "Gold Chipset FE Cost", comment = "FE required to craft a Gold Chipset", category = "assembly")
public static int goldChipsetFeCost = 40_000;
@ConfigValue(name = "Quartz Chipset FE Cost", comment = "FE required to craft a Quartz Chipset", category = "assembly")
public static int quartzChipsetFeCost = 60_000;
@ConfigValue(name = "Diamond Chipset FE Cost", comment = "FE required to craft a Diamond Chipset", category = "assembly")
public static int diamondChipsetFeCost = 80_000;
```

- [ ] **Step 2: Add chipset items to BCItems**

Add after the `DIAMOND_GEAR` line:

```java
// Chipsets
public static final DeferredItem<Item> RED_CHIPSET     = registerItem("red_chipset",     Item::new);
public static final DeferredItem<Item> IRON_CHIPSET    = registerItem("iron_chipset",    Item::new);
public static final DeferredItem<Item> GOLD_CHIPSET    = registerItem("gold_chipset",    Item::new);
public static final DeferredItem<Item> QUARTZ_CHIPSET  = registerItem("quartz_chipset",  Item::new);
public static final DeferredItem<Item> DIAMOND_CHIPSET = registerItem("diamond_chipset", Item::new);
```

- [ ] **Step 3: Add placeholder chipset textures**

Run this Python script to generate 16×16 placeholder PNGs for each chipset:

```python
# run once: python3 /tmp/gen_chipsets.py
from PIL import Image
import os

out_dir = "/run/media/fabi/SSD/codeing/Buildcraft-Legacy/src/main/resources/assets/buildcraft/textures/item"
os.makedirs(out_dir, exist_ok=True)

chipsets = {
    "red_chipset":     (180, 30, 30),
    "iron_chipset":    (160, 160, 160),
    "gold_chipset":    (220, 180, 30),
    "quartz_chipset":  (200, 200, 220),
    "diamond_chipset": (30, 200, 200),
}

for name, color in chipsets.items():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(4, 12):
        for y in range(4, 12):
            img.putpixel((x, y), color + (255,))
    img.save(os.path.join(out_dir, f"{name}.png"))
    print(f"Created {name}.png")
```

```bash
python3 /tmp/gen_chipsets.py
```

- [ ] **Step 4: Compile**

```bash
bash /run/media/fabi/SSD/codeing/Buildcraft-Legacy/gradlew compileJava 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/BCConfig.java \
        src/main/java/com/thepigcat/buildcraft/registries/BCItems.java \
        src/main/resources/assets/buildcraft/textures/item/red_chipset.png \
        src/main/resources/assets/buildcraft/textures/item/iron_chipset.png \
        src/main/resources/assets/buildcraft/textures/item/gold_chipset.png \
        src/main/resources/assets/buildcraft/textures/item/quartz_chipset.png \
        src/main/resources/assets/buildcraft/textures/item/diamond_chipset.png
git commit -m "feat(silicon): add chipset items, BCConfig laser/assembly entries"
```

---

## Task 3: LaserBlock + AssemblyTableBlock + registrations

**Files:**
- Create: `src/main/java/com/thepigcat/buildcraft/content/blocks/LaserBlock.java`
- Create: `src/main/java/com/thepigcat/buildcraft/content/blocks/AssemblyTableBlock.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCBlocks.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCBlockEntities.java`

- [ ] **Step 1: Create LaserBlock**

```java
// src/main/java/com/thepigcat/buildcraft/content/blocks/LaserBlock.java
package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.content.blockentities.LaserBE;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

public class LaserBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public LaserBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getNearestLookingDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return BCBlockEntities.LASER.get().create(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, BCBlockEntities.LASER.get(), LaserBE::serverTick);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(LaserBlock::new);
    }
}
```

- [ ] **Step 2: Create AssemblyTableBlock**

```java
// src/main/java/com/thepigcat/buildcraft/content/blocks/AssemblyTableBlock.java
package com.thepigcat.buildcraft.content.blocks;

import com.mojang.serialization.MapCodec;
import com.thepigcat.buildcraft.content.blockentities.AssemblyTableBE;
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

public class AssemblyTableBlock extends BaseEntityBlock {
    public AssemblyTableBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return BCBlockEntities.ASSEMBLY_TABLE.get().create(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AssemblyTableBE be) {
            player.openMenu(be);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, BCBlockEntities.ASSEMBLY_TABLE.get(), AssemblyTableBE::serverTick);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(AssemblyTableBlock::new);
    }
}
```

- [ ] **Step 3: Register blocks in BCBlocks**

Add after the `QUARRY` line:

```java
// Silicon
public static final DeferredBlock<LaserBlock> LASER = registerBlockAndItem("laser", LaserBlock::new,
        BlockBehaviour.Properties.of().strength(2.0f).sound(SoundType.METAL).mapColor(MapColor.METAL).requiresCorrectToolForDrops());
public static final DeferredBlock<AssemblyTableBlock> ASSEMBLY_TABLE = registerBlockAndItem("assembly_table", AssemblyTableBlock::new,
        BlockBehaviour.Properties.of().strength(3.0f).sound(SoundType.METAL).mapColor(MapColor.METAL).requiresCorrectToolForDrops());
```

Also add the imports at the top of `BCBlocks.java`:
```java
import com.thepigcat.buildcraft.content.blocks.LaserBlock;
import com.thepigcat.buildcraft.content.blocks.AssemblyTableBlock;
```

- [ ] **Step 4: Register block entities in BCBlockEntities**

Add after the `EXTRACTING_FLUID_PIPE` entry (before the `collectBlocks` helpers):

```java
public static final Supplier<BlockEntityType<LaserBE>> LASER = BLOCK_ENTITIES.register("laser",
        () -> BlockEntityType.Builder.of(LaserBE::new, BCBlocks.LASER.get()).build(null));
public static final Supplier<BlockEntityType<AssemblyTableBE>> ASSEMBLY_TABLE = BLOCK_ENTITIES.register("assembly_table",
        () -> BlockEntityType.Builder.of(AssemblyTableBE::new, BCBlocks.ASSEMBLY_TABLE.get()).build(null));
```

Add imports:
```java
import com.thepigcat.buildcraft.content.blockentities.LaserBE;
import com.thepigcat.buildcraft.content.blockentities.AssemblyTableBE;
```

- [ ] **Step 5: Create skeleton LaserBE** (just enough to compile with the static `serverTick` reference)

```java
// src/main/java/com/thepigcat/buildcraft/content/blockentities/LaserBE.java
package com.thepigcat.buildcraft.content.blockentities;

import com.portingdeadmods.portingdeadlibs.api.blockentities.ContainerBlockEntity;
import com.portingdeadmods.portingdeadlibs.utils.capabilities.HandlerUtils;
import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class LaserBE extends ContainerBlockEntity {
    public LaserBE(BlockPos pos, BlockState state) {
        super(BCBlockEntities.LASER.get(), pos, state);
        addEnergyStorage(HandlerUtils::newEnergystorage, builder -> builder
                .capacity(BCConfig.laserBatteryCapacity)
                .maxTransfer(BCConfig.laserMaxReceive));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LaserBE be) {
        // implemented in Task 4
    }
}
```

- [ ] **Step 6: Create skeleton AssemblyTableBE**

```java
// src/main/java/com/thepigcat/buildcraft/content/blockentities/AssemblyTableBE.java
package com.thepigcat.buildcraft.content.blockentities;

import com.portingdeadmods.portingdeadlibs.api.blockentities.ContainerBlockEntity;
import com.portingdeadmods.portingdeadlibs.utils.capabilities.HandlerUtils;
import com.thepigcat.buildcraft.api.blockentities.ILaserTarget;
import com.thepigcat.buildcraft.content.menus.AssemblyTableMenu;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AssemblyTableBE extends ContainerBlockEntity implements ILaserTarget, MenuProvider {
    public long power = 0;

    public AssemblyTableBE(BlockPos pos, BlockState state) {
        super(BCBlockEntities.ASSEMBLY_TABLE.get(), pos, state);
        addItemHandler(HandlerUtils::newItemStackHandler, builder -> builder.slots(12));
    }

    @Override public int getRequiredLaserPower() { return 0; } // implemented Task 5
    @Override public void receiveLaserPower(int fe) { power += fe; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AssemblyTableBE be) {
        // implemented in Task 5
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("container.buildcraft.assembly_table");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player player) {
        return new AssemblyTableMenu(id, inv, this);
    }
}
```

**Note:** `AssemblyTableMenu` does not exist yet — create a minimal skeleton next.

- [ ] **Step 7: Create skeleton AssemblyTableMenu** (enough to compile)

```java
// src/main/java/com/thepigcat/buildcraft/content/menus/AssemblyTableMenu.java
package com.thepigcat.buildcraft.content.menus;

import com.portingdeadmods.portingdeadlibs.api.gui.menus.PDLAbstractContainerMenu;
import com.thepigcat.buildcraft.content.blockentities.AssemblyTableBE;
import com.thepigcat.buildcraft.registries.BCMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class AssemblyTableMenu extends PDLAbstractContainerMenu<AssemblyTableBE> {
    public AssemblyTableMenu(int id, @NotNull Inventory inv, @NotNull AssemblyTableBE be) {
        super(BCMenuTypes.ASSEMBLY_TABLE.get(), id, inv, be);
        // 12 slots: 3 columns × 4 rows starting at (8, 18)
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new SlotItemHandler(be.getItemHandler(), row * 3 + col, 8 + col * 18, 18 + row * 18));
            }
        }
        addPlayerHotbar(inv);
        addPlayerInventory(inv);
    }

    public AssemblyTableMenu(int id, @NotNull Inventory inv, @NotNull RegistryFriendlyByteBuf buf) {
        this(id, inv, (AssemblyTableBE) inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    @Override
    protected int getMergeableSlotCount() {
        return 12;
    }
}
```

- [ ] **Step 8: Register AssemblyTable menu type in BCMenuTypes**

Add after the `DIAMOND_FLUID_PIPE` line:

```java
public static final Supplier<MenuType<AssemblyTableMenu>> ASSEMBLY_TABLE =
        registerMenuType("assembly_table", AssemblyTableMenu::new);
```

Add import:
```java
import com.thepigcat.buildcraft.content.menus.AssemblyTableMenu;
```

- [ ] **Step 9: Compile**

```bash
bash /run/media/fabi/SSD/codeing/Buildcraft-Legacy/gradlew compileJava 2>&1 | tail -5
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blocks/LaserBlock.java \
        src/main/java/com/thepigcat/buildcraft/content/blocks/AssemblyTableBlock.java \
        src/main/java/com/thepigcat/buildcraft/content/blockentities/LaserBE.java \
        src/main/java/com/thepigcat/buildcraft/content/blockentities/AssemblyTableBE.java \
        src/main/java/com/thepigcat/buildcraft/content/menus/AssemblyTableMenu.java \
        src/main/java/com/thepigcat/buildcraft/registries/BCBlocks.java \
        src/main/java/com/thepigcat/buildcraft/registries/BCBlockEntities.java \
        src/main/java/com/thepigcat/buildcraft/registries/BCMenuTypes.java
git commit -m "feat(silicon): add LaserBlock, AssemblyTableBlock, skeleton BEs + menu registration"
```

---

## Task 4: LaserBE — server logic (battery, scan, push)

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/LaserBE.java`

- [ ] **Step 1: Implement full LaserBE**

Replace the file content with:

```java
package com.thepigcat.buildcraft.content.blockentities;

import com.portingdeadmods.portingdeadlibs.api.blockentities.ContainerBlockEntity;
import com.portingdeadmods.portingdeadlibs.utils.capabilities.HandlerUtils;
import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.api.blockentities.ILaserTarget;
import com.thepigcat.buildcraft.content.blocks.LaserBlock;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LaserBE extends ContainerBlockEntity {
    @Nullable public BlockPos targetPos = null;
    private final List<BlockPos> candidates = new ArrayList<>();
    private int scanCooldown = 0;

    public LaserBE(BlockPos pos, BlockState state) {
        super(BCBlockEntities.LASER.get(), pos, state);
        addEnergyStorage(HandlerUtils::newEnergystorage, builder -> builder
                .capacity(BCConfig.laserBatteryCapacity)
                .maxTransfer(BCConfig.laserMaxReceive));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LaserBE be) {
        if (--be.scanCooldown <= 0) {
            be.scanCooldown = 10;
            be.scanForTargets(state);
        }

        // Re-select if current target no longer needs power
        if (be.targetPos != null) {
            if (!(level.getBlockEntity(be.targetPos) instanceof AssemblyTableBE tbl) || tbl.getRequiredLaserPower() <= 0) {
                be.targetPos = null;
            }
        }

        if (be.targetPos == null) {
            be.pickTarget();
        }

        if (be.targetPos != null && level.getBlockEntity(be.targetPos) instanceof AssemblyTableBE target) {
            IEnergyStorage battery = be.getEnergyStorage();
            if (battery != null) {
                int toSend = Math.min(BCConfig.laserMaxOutput, battery.getEnergyStored());
                toSend = Math.min(toSend, target.getRequiredLaserPower());
                if (toSend > 0) {
                    battery.extractEnergy(toSend, false);
                    target.receiveLaserPower(toSend);
                }
            }
        }

        // Sync targetPos to clients when it changes
        BlockPos prevTarget = be.targetPos;
        if (prevTarget != be.targetPos) {
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private void scanForTargets(BlockState state) {
        candidates.clear();
        if (level == null) return;
        Direction facing = state.getValue(LaserBlock.FACING);
        BlockPos origin = getBlockPos();
        int range = 8;

        // Scan a 3×3×range volume in the facing direction
        for (int dist = 1; dist <= range; dist++) {
            int spread = Math.min(dist / 2, 1);
            for (int a = -spread; a <= spread; a++) {
                for (int b = -spread; b <= spread; b++) {
                    BlockPos check = switch (facing) {
                        case NORTH -> origin.offset(a, b, -dist);
                        case SOUTH -> origin.offset(a, b, dist);
                        case EAST  -> origin.offset(dist, b, a);
                        case WEST  -> origin.offset(-dist, b, a);
                        case UP    -> origin.offset(a, dist, b);
                        case DOWN  -> origin.offset(a, -dist, b);
                    };
                    if (level.getBlockEntity(check) instanceof AssemblyTableBE) {
                        candidates.add(check);
                    }
                }
            }
        }
    }

    private void pickTarget() {
        if (level == null || candidates.isEmpty()) return;
        for (BlockPos pos : candidates) {
            if (level.getBlockEntity(pos) instanceof AssemblyTableBE tbl && tbl.getRequiredLaserPower() > 0) {
                targetPos = pos;
                return;
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("has_target", targetPos != null);
        if (targetPos != null) {
            tag.putInt("tx", targetPos.getX());
            tag.putInt("ty", targetPos.getY());
            tag.putInt("tz", targetPos.getZ());
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        if (tag.getBoolean("has_target")) {
            targetPos = new BlockPos(tag.getInt("tx"), tag.getInt("ty"), tag.getInt("tz"));
        } else {
            targetPos = null;
        }
    }
}
```

- [ ] **Step 2: Compile**

```bash
bash /run/media/fabi/SSD/codeing/Buildcraft-Legacy/gradlew compileJava 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/LaserBE.java
git commit -m "feat(silicon): implement LaserBE — cone scan, FE push, target sync"
```

---

## Task 5: AssemblyTableBE — recipe state machine + craft logic

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/AssemblyTableBE.java`

- [ ] **Step 1: Implement full AssemblyTableBE**

Replace the file content with:

```java
package com.thepigcat.buildcraft.content.blockentities;

import com.portingdeadmods.portingdeadlibs.api.blockentities.ContainerBlockEntity;
import com.portingdeadmods.portingdeadlibs.utils.capabilities.HandlerUtils;
import com.thepigcat.buildcraft.api.blockentities.ILaserTarget;
import com.thepigcat.buildcraft.api.recipes.AssemblyRecipe;
import com.thepigcat.buildcraft.api.recipes.AssemblyRecipeRegistry;
import com.thepigcat.buildcraft.content.enums.EnumAssemblyRecipeState;
import com.thepigcat.buildcraft.content.menus.AssemblyTableMenu;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class AssemblyTableBE extends ContainerBlockEntity implements ILaserTarget, MenuProvider {
    public long power = 0;
    // Only populated on server; synced to client via getUpdateTag/handleUpdateTag
    public final Map<ResourceLocation, EnumAssemblyRecipeState> recipeStates = new LinkedHashMap<>();

    public AssemblyTableBE(BlockPos pos, BlockState state) {
        super(BCBlockEntities.ASSEMBLY_TABLE.get(), pos, state);
        addItemHandler(HandlerUtils::newItemStackHandler, builder -> builder.slots(12));
    }

    // ── ILaserTarget ─────��──────────────────────────────────────────────────

    @Override
    public int getRequiredLaserPower() {
        AssemblyRecipe active = getActiveRecipe();
        if (active == null) return 0;
        return (int) Math.max(0, active.feCost() - power);
    }

    @Override
    public void receiveLaserPower(int fe) {
        power += fe;
        setChanged();
    }

    // ── Tick ───────────────────────���────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, AssemblyTableBE be) {
        if (level.isClientSide) return;
        boolean changed = be.updateRecipes();
        AssemblyRecipe active = be.getActiveRecipe();
        if (active != null && be.power >= active.feCost()) {
            be.power -= active.feCost();
            be.consumeInputs(active);
            ejectOutput(level, pos, active.output().copy());
            // Advance to next SAVED_ENOUGH recipe (round-robin)
            be.recipeStates.put(active.id(), EnumAssemblyRecipeState.SAVED_ENOUGH);
            be.activateNextRecipe();
            changed = true;
        }
        if (changed) {
            level.sendBlockUpdated(pos, state, state, 3);
            be.setChanged();
        }
    }

    // ── Recipe state machine ────────────────────────��────────────────────────

    /** Returns true if recipeStates changed. */
    private boolean updateRecipes() {
        IItemHandler inv = getItemHandler();
        if (inv == null) return false;
        boolean changed = false;

        // Add new POSSIBLE entries for recipes whose ingredients are satisfied
        for (AssemblyRecipe recipe : AssemblyRecipeRegistry.all()) {
            if (!recipeStates.containsKey(recipe.id())) {
                if (hasIngredients(inv, recipe)) {
                    recipeStates.put(recipe.id(), EnumAssemblyRecipeState.POSSIBLE);
                    changed = true;
                }
            }
        }

        // Update existing entries
        for (var it = recipeStates.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            AssemblyRecipe recipe = AssemblyRecipeRegistry.get(entry.getKey());
            if (recipe == null) { it.remove(); changed = true; continue; }

            boolean enough = hasIngredients(inv, recipe);
            EnumAssemblyRecipeState state = entry.getValue();
            EnumAssemblyRecipeState newState = switch (state) {
                case POSSIBLE -> enough ? state : null; // remove if no longer possible
                case SAVED -> enough ? EnumAssemblyRecipeState.SAVED_ENOUGH : state;
                case SAVED_ENOUGH -> enough ? state : EnumAssemblyRecipeState.SAVED;
                case SAVED_ENOUGH_ACTIVE -> enough ? state : EnumAssemblyRecipeState.SAVED;
            };

            if (newState == null) { it.remove(); changed = true; }
            else if (newState != state) { entry.setValue(newState); changed = true; }
        }

        // Ensure exactly one SAVED_ENOUGH_ACTIVE exists if any SAVED_ENOUGH available
        if (getActiveRecipe() == null) {
            changed |= activateNextRecipe();
        }
        return changed;
    }

    private boolean activateNextRecipe() {
        for (var entry : recipeStates.entrySet()) {
            if (entry.getValue() == EnumAssemblyRecipeState.SAVED_ENOUGH) {
                entry.setValue(EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE);
                return true;
            }
        }
        return false;
    }

    @Nullable
    public AssemblyRecipe getActiveRecipe() {
        for (var entry : recipeStates.entrySet()) {
            if (entry.getValue() == EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE) {
                return AssemblyRecipeRegistry.get(entry.getKey());
            }
        }
        return null;
    }

    /** Toggle a recipe between POSSIBLE↔SAVED (or SAVED_ENOUGH↔SAVED_ENOUGH_ACTIVE). */
    public void toggleSaved(ResourceLocation recipeId) {
        EnumAssemblyRecipeState state = recipeStates.get(recipeId);
        if (state == null) return;
        EnumAssemblyRecipeState next = switch (state) {
            case POSSIBLE -> EnumAssemblyRecipeState.SAVED;
            case SAVED -> EnumAssemblyRecipeState.POSSIBLE;
            case SAVED_ENOUGH -> EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE;
            case SAVED_ENOUGH_ACTIVE -> EnumAssemblyRecipeState.SAVED_ENOUGH;
        };
        recipeStates.put(recipeId, next);
        setChanged();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean hasIngredients(IItemHandler inv, AssemblyRecipe recipe) {
        for (Ingredient ingredient : recipe.inputs()) {
            boolean found = false;
            for (int i = 0; i < inv.getSlots(); i++) {
                if (ingredient.test(inv.getStackInSlot(i))) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    private void consumeInputs(AssemblyRecipe recipe) {
        IItemHandler inv = getItemHandler();
        if (inv == null) return;
        for (Ingredient ingredient : recipe.inputs()) {
            for (int i = 0; i < inv.getSlots(); i++) {
                if (ingredient.test(inv.getStackInSlot(i))) {
                    inv.extractItem(i, 1, false);
                    break;
                }
            }
        }
    }

    private static void ejectOutput(Level level, BlockPos pos, ItemStack output) {
        for (Direction dir : Direction.values()) {
            var cap = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos.relative(dir), dir.getOpposite());
            if (cap != null) {
                ItemStack remainder = ItemHandlerHelper.insertItem(cap, output, false);
                if (remainder.isEmpty()) return;
                output = remainder;
            }
        }
        // Drop if no adjacent inventory accepted it
        net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(
                level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, output);
        level.addFreshEntity(entity);
    }

    // ── Sync ────────────────────────��────────────────────────────���───────────

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putLong("power", power);
        ListTag list = new ListTag();
        recipeStates.forEach((id, state) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.toString());
            entry.putInt("state", state.ordinal());
            list.add(entry);
        });
        tag.put("recipe_states", list);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        power = tag.getLong("power");
        recipeStates.clear();
        ListTag list = tag.getList("recipe_states", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation id = ResourceLocation.parse(entry.getString("id"));
            EnumAssemblyRecipeState state = EnumAssemblyRecipeState.values()[entry.getInt("state")];
            recipeStates.put(id, state);
        }
    }

    // ── NBT ──────────────────────────────────────────────────��───────────────

    @Override
    protected void saveData(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveData(tag, provider);
        tag.putLong("power", power);
        ListTag list = new ListTag();
        recipeStates.forEach((id, state) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.toString());
            entry.putInt("state", state.ordinal());
            list.add(entry);
        });
        tag.put("recipe_states", list);
    }

    @Override
    protected void loadData(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadData(tag, provider);
        power = tag.getLong("power");
        recipeStates.clear();
        ListTag list = tag.getList("recipe_states", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation id = ResourceLocation.parse(entry.getString("id"));
            EnumAssemblyRecipeState state = EnumAssemblyRecipeState.values()[entry.getInt("state")];
            recipeStates.put(id, state);
        }
    }

    // ── MenuProvider ──────────────────────��──────────────────────────────────

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("container.buildcraft.assembly_table");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player player) {
        return new AssemblyTableMenu(id, inv, this);
    }
}
```

- [ ] **Step 2: Compile**

```bash
bash /run/media/fabi/SSD/codeing/Buildcraft-Legacy/gradlew compileJava 2>&1 | tail -5
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/blockentities/AssemblyTableBE.java
git commit -m "feat(silicon): implement AssemblyTableBE — recipe state machine, craft logic, sync"
```

---

## Task 6: Networking — SetRecipeStatePayload

**Files:**
- Create: `src/main/java/com/thepigcat/buildcraft/networking/SetRecipeStatePayload.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java`

- [ ] **Step 1: Create SetRecipeStatePayload**

```java
// src/main/java/com/thepigcat/buildcraft/networking/SetRecipeStatePayload.java
package com.thepigcat.buildcraft.networking;

import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.content.blockentities.AssemblyTableBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetRecipeStatePayload(BlockPos pos, ResourceLocation recipeId) implements CustomPacketPayload {
    public static final Type<SetRecipeStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BuildcraftLegacy.MODID, "set_recipe_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetRecipeStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> { buf.writeBlockPos(p.pos()); buf.writeResourceLocation(p.recipeId()); },
                    buf -> new SetRecipeStatePayload(buf.readBlockPos(), buf.readResourceLocation())
            );

    public static void handle(SetRecipeStatePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            if (player.level().getBlockEntity(payload.pos()) instanceof AssemblyTableBE be) {
                if (player.distanceToSqr(payload.pos().getCenter()) <= 64.0) {
                    be.toggleSaved(payload.recipeId());
                    player.level().sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
```

- [ ] **Step 2: Register payload in BuildcraftLegacy.registerPayloads()**

Add to the `registerPayloads` method body:

```java
registrar.playToServer(SetRecipeStatePayload.TYPE, SetRecipeStatePayload.STREAM_CODEC, SetRecipeStatePayload::handle);
```

Also add the import at the top of BuildcraftLegacy.java:
```java
import com.thepigcat.buildcraft.networking.SetRecipeStatePayload;
```

- [ ] **Step 3: Register Laser FE capability in BuildcraftLegacy.attachCaps()**

In the `attachCaps` method, add:

```java
event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, BCBlockEntities.LASER.get(),
        (be, side) -> be.getEnergyStorage());
```

Also add imports if not present:
```java
import com.thepigcat.buildcraft.content.blockentities.LaserBE;
import com.thepigcat.buildcraft.registries.BCBlockEntities; // already present
```

- [ ] **Step 4: Register chipset assembly recipes in BuildcraftLegacy**

Add a new `registerAssemblyRecipes()` method and call it from the mod constructor:

In the constructor, add after `modEventBus.addListener(this::registerPayloads)`:
```java
registerAssemblyRecipes();
```

Add the method (before the closing brace of the class):

```java
private static void registerAssemblyRecipes() {
    AssemblyRecipeRegistry.register(new AssemblyRecipe(
            BuildcraftLegacy.rl("red_chipset"),
            Set.of(Ingredient.of(Items.REDSTONE)),
            new ItemStack(BCItems.RED_CHIPSET.get()),
            BCConfig.redChipsetFeCost));
    AssemblyRecipeRegistry.register(new AssemblyRecipe(
            BuildcraftLegacy.rl("iron_chipset"),
            Set.of(Ingredient.of(Items.REDSTONE), Ingredient.of(Tags.Items.INGOTS_IRON)),
            new ItemStack(BCItems.IRON_CHIPSET.get()),
            BCConfig.ironChipsetFeCost));
    AssemblyRecipeRegistry.register(new AssemblyRecipe(
            BuildcraftLegacy.rl("gold_chipset"),
            Set.of(Ingredient.of(Items.REDSTONE), Ingredient.of(Tags.Items.INGOTS_GOLD)),
            new ItemStack(BCItems.GOLD_CHIPSET.get()),
            BCConfig.goldChipsetFeCost));
    AssemblyRecipeRegistry.register(new AssemblyRecipe(
            BuildcraftLegacy.rl("quartz_chipset"),
            Set.of(Ingredient.of(Items.REDSTONE), Ingredient.of(Tags.Items.GEMS_QUARTZ)),
            new ItemStack(BCItems.QUARTZ_CHIPSET.get()),
            BCConfig.quartzChipsetFeCost));
    AssemblyRecipeRegistry.register(new AssemblyRecipe(
            BuildcraftLegacy.rl("diamond_chipset"),
            Set.of(Ingredient.of(Items.REDSTONE), Ingredient.of(Tags.Items.GEMS_DIAMOND)),
            new ItemStack(BCItems.DIAMOND_CHIPSET.get()),
            BCConfig.diamondChipsetFeCost));
}
```

Add imports to `BuildcraftLegacy.java`:
```java
import com.thepigcat.buildcraft.api.recipes.AssemblyRecipe;
import com.thepigcat.buildcraft.api.recipes.AssemblyRecipeRegistry;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.Tags;
import java.util.Set;
```

- [ ] **Step 5: Compile**

```bash
bash /run/media/fabi/SSD/codeing/Buildcraft-Legacy/gradlew compileJava 2>&1 | tail -5
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/networking/SetRecipeStatePayload.java \
        src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java
git commit -m "feat(silicon): add SetRecipeStatePayload, FE capability for Laser, chipset assembly recipes"
```

---

## Task 7: AssemblyTableMenu + AssemblyTableScreen

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/menus/AssemblyTableMenu.java`
- Create: `src/main/java/com/thepigcat/buildcraft/client/screens/AssemblyTableScreen.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java` (register screen)

- [ ] **Step 1: Finalize AssemblyTableMenu**

Replace the file content with:

```java
package com.thepigcat.buildcraft.content.menus;

import com.portingdeadmods.portingdeadlibs.api.gui.menus.PDLAbstractContainerMenu;
import com.thepigcat.buildcraft.content.blockentities.AssemblyTableBE;
import com.thepigcat.buildcraft.content.enums.EnumAssemblyRecipeState;
import com.thepigcat.buildcraft.registries.BCMenuTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class AssemblyTableMenu extends PDLAbstractContainerMenu<AssemblyTableBE> {
    public AssemblyTableMenu(int id, @NotNull Inventory inv, @NotNull AssemblyTableBE be) {
        super(BCMenuTypes.ASSEMBLY_TABLE.get(), id, inv, be);
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new SlotItemHandler(be.getItemHandler(), row * 3 + col, 8 + col * 18, 18 + row * 18));
            }
        }
        addPlayerHotbar(inv);
        addPlayerInventory(inv);
    }

    public AssemblyTableMenu(int id, @NotNull Inventory inv, @NotNull RegistryFriendlyByteBuf buf) {
        this(id, inv, (AssemblyTableBE) inv.player.level().getBlockEntity(buf.readBlockPos()));
    }

    /** Returns a snapshot of the BE's recipe states for rendering (client-side). */
    public Map<ResourceLocation, EnumAssemblyRecipeState> getRecipeStates() {
        return blockEntity.recipeStates;
    }

    public long getPower() { return blockEntity.power; }

    public long getTarget() {
        var active = blockEntity.getActiveRecipe();
        return active != null ? active.feCost() : 0;
    }

    @Override
    protected int getMergeableSlotCount() { return 12; }
}
```

- [ ] **Step 2: Create placeholder GUI texture**

```python
# run once: python3 /tmp/gen_assembly_gui.py
from PIL import Image, ImageDraw

img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
draw = ImageDraw.Draw(img)
# Background panel 176x200
draw.rectangle([0, 0, 175, 199], fill=(198, 198, 198, 255))
# Input slots (3x4 grid at 8,18, each 18x18)
for row in range(4):
    for col in range(3):
        x, y = 8 + col * 18, 18 + row * 18
        draw.rectangle([x, y, x+16, y+16], fill=(139, 139, 139, 255), outline=(55, 55, 55, 255))
# Recipe panel at 112,18, 56x136
draw.rectangle([112, 18, 167, 153], fill=(170, 170, 170, 255), outline=(55, 55, 55, 255))
# Power bar at 8,160, 160x8
draw.rectangle([8, 160, 167, 167], fill=(60, 60, 60, 255))
# Player inventory 8,175
for row in range(3):
    for col in range(9):
        x, y = 8 + col * 18, 175 + row * 18
        draw.rectangle([x, y, x+16, y+16], fill=(139, 139, 139, 255), outline=(55, 55, 55, 255))
# Hotbar 8,233
for col in range(9):
    x, y = 8 + col * 18, 233
    draw.rectangle([x, y, x+16, y+16], fill=(139, 139, 139, 255), outline=(55, 55, 55, 255))

img.save("/run/media/fabi/SSD/codeing/Buildcraft-Legacy/src/main/resources/assets/buildcraft/textures/gui/assembly_table.png")
print("Created assembly_table.png")
```

```bash
python3 /tmp/gen_assembly_gui.py
```

- [ ] **Step 3: Create AssemblyTableScreen**

```java
// src/main/java/com/thepigcat/buildcraft/client/screens/AssemblyTableScreen.java
package com.thepigcat.buildcraft.client.screens;

import com.portingdeadmods.portingdeadlibs.api.client.screens.PDLAbstractContainerScreen;
import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.api.recipes.AssemblyRecipe;
import com.thepigcat.buildcraft.api.recipes.AssemblyRecipeRegistry;
import com.thepigcat.buildcraft.content.enums.EnumAssemblyRecipeState;
import com.thepigcat.buildcraft.content.menus.AssemblyTableMenu;
import com.thepigcat.buildcraft.networking.SetRecipeStatePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AssemblyTableScreen extends PDLAbstractContainerScreen<AssemblyTableMenu> {
    private static final int RECIPE_PANEL_X = 112;
    private static final int RECIPE_PANEL_Y = 18;
    private static final int RECIPE_PANEL_W = 56;
    private static final int RECIPE_PANEL_ENTRY_H = 20;
    private int recipeScrollOffset = 0;
    private static final int VISIBLE_RECIPES = 6;

    public AssemblyTableScreen(AssemblyTableMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 240;
    }

    @Override
    public @NotNull ResourceLocation getBackgroundTexture() {
        return ResourceLocation.fromNamespaceAndPath(BuildcraftLegacy.MODID, "textures/gui/assembly_table.png");
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        super.renderBg(g, partialTick, mx, my);
        renderRecipePanel(g, mx, my);
        renderPowerBar(g);
    }

    private void renderRecipePanel(GuiGraphics g, int mx, int my) {
        Map<ResourceLocation, EnumAssemblyRecipeState> states = menu.getRecipeStates();
        List<Map.Entry<ResourceLocation, EnumAssemblyRecipeState>> visible = new ArrayList<>();
        int skip = 0;
        for (var entry : states.entrySet()) {
            if (skip++ < recipeScrollOffset) continue;
            if (visible.size() >= VISIBLE_RECIPES) break;
            visible.add(entry);
        }

        int px = leftPos + RECIPE_PANEL_X;
        int py = topPos + RECIPE_PANEL_Y;
        for (int i = 0; i < visible.size(); i++) {
            var entry = visible.get(i);
            AssemblyRecipe recipe = AssemblyRecipeRegistry.get(entry.getKey());
            if (recipe == null) continue;

            int ey = py + i * RECIPE_PANEL_ENTRY_H;
            int color = switch (entry.getValue()) {
                case POSSIBLE -> 0xFF555555;
                case SAVED -> 0xFF886600;
                case SAVED_ENOUGH -> 0xFF226622;
                case SAVED_ENOUGH_ACTIVE -> 0xFF44AA44;
            };
            g.fill(px, ey, px + RECIPE_PANEL_W, ey + RECIPE_PANEL_ENTRY_H - 1, color);
            // Render output item icon at left of entry
            g.renderItem(recipe.output(), px + 2, ey + 2);
            // Highlight if mouse over
            if (mx >= px && mx < px + RECIPE_PANEL_W && my >= ey && my < ey + RECIPE_PANEL_ENTRY_H) {
                g.fill(px, ey, px + RECIPE_PANEL_W, ey + RECIPE_PANEL_ENTRY_H - 1, 0x44FFFFFF);
            }
        }
    }

    private void renderPowerBar(GuiGraphics g) {
        long power = menu.getPower();
        long target = menu.getTarget();
        if (target <= 0) return;
        float frac = Math.min(1f, (float) power / target);
        int barX = leftPos + 8;
        int barY = topPos + 160;
        int barW = (int) (160 * frac);
        g.fill(barX, barY, barX + barW, barY + 8, 0xFF44AA44);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int px = leftPos + RECIPE_PANEL_X;
        int py = topPos + RECIPE_PANEL_Y;
        if (mx >= px && mx < px + RECIPE_PANEL_W) {
            Map<ResourceLocation, EnumAssemblyRecipeState> states = menu.getRecipeStates();
            List<ResourceLocation> ids = new ArrayList<>(states.keySet());
            int relY = (int) my - py;
            int idx = recipeScrollOffset + relY / RECIPE_PANEL_ENTRY_H;
            if (idx >= 0 && idx < ids.size()) {
                PacketDistributor.sendToServer(new SetRecipeStatePayload(menu.blockEntity.getBlockPos(), ids.get(idx)));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int px = leftPos + RECIPE_PANEL_X;
        int py = topPos + RECIPE_PANEL_Y;
        if (mx >= px && mx < px + RECIPE_PANEL_W && my >= py && my < py + VISIBLE_RECIPES * RECIPE_PANEL_ENTRY_H) {
            recipeScrollOffset = Math.max(0, recipeScrollOffset - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }
}
```

- [ ] **Step 4: Register screen in BuildcraftLegacy client setup**

In `BuildcraftLegacy.java`, find or add a `@EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)` class and add:

```java
@SubscribeEvent
public static void registerScreens(RegisterMenuScreensEvent event) {
    event.register(BCMenuTypes.ASSEMBLY_TABLE.get(), AssemblyTableScreen::new);
}
```

Add imports:
```java
import com.thepigcat.buildcraft.client.screens.AssemblyTableScreen;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
```

Look at how existing screens are registered (search for `RegisterMenuScreensEvent` in the project) and follow the same pattern:

```bash
grep -rn "RegisterMenuScreensEvent" /run/media/fabi/SSD/codeing/Buildcraft-Legacy/src/main/java/ | head -5
```

- [ ] **Step 5: Compile**

```bash
bash /run/media/fabi/SSD/codeing/Buildcraft-Legacy/gradlew compileJava 2>&1 | tail -5
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/content/menus/AssemblyTableMenu.java \
        src/main/java/com/thepigcat/buildcraft/client/screens/AssemblyTableScreen.java \
        src/main/resources/assets/buildcraft/textures/gui/assembly_table.png \
        src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java
git commit -m "feat(silicon): add AssemblyTableScreen with recipe panel, power bar"
```

---

## Task 8: LaserBERenderer (TESR)

**Files:**
- Create: `src/main/java/com/thepigcat/buildcraft/client/blockentities/LaserBERenderer.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java` (register renderer)

- [ ] **Step 1: Create LaserBERenderer**

```java
// src/main/java/com/thepigcat/buildcraft/client/blockentities/LaserBERenderer.java
package com.thepigcat.buildcraft.client.blockentities;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thepigcat.buildcraft.content.blockentities.LaserBE;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class LaserBERenderer implements BlockEntityRenderer<LaserBE> {
    private static final float BEAM_R = 0.03f;

    public LaserBERenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(LaserBE be, float partialTick, PoseStack ps, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (be.targetPos == null || be.getLevel() == null) return;

        BlockPos origin = be.getBlockPos();
        float dx = be.targetPos.getX() - origin.getX();
        float dy = be.targetPos.getY() - origin.getY();
        float dz = be.targetPos.getZ() - origin.getZ();

        float time = (be.getLevel().getGameTime() + partialTick) * 0.08f;
        float pulse = (float)(0.5 + 0.5 * Math.sin(time));
        int g = (int)(100 + 155 * pulse);
        int r = (int)(30 * pulse);

        // Direction vector (normalized)
        Vector3f dir = new Vector3f(dx, dy, dz).normalize();
        // Perpendicular vectors for beam cross-section
        Vector3f up = Math.abs(dir.y) < 0.9f
                ? new Vector3f(0, 1, 0)
                : new Vector3f(1, 0, 0);
        Vector3f right = new Vector3f(dir).cross(up).normalize().mul(BEAM_R);
        Vector3f upPerp = new Vector3f(dir).cross(right).normalize().mul(BEAM_R);

        float fx = 0.5f, fy = 0.5f, fz = 0.5f;
        float tx = dx + 0.5f, ty = dy + 0.5f, tz = dz + 0.5f;

        VertexConsumer vc = buffers.getBuffer(RenderType.translucent());
        Matrix4f pose = ps.last().pose();

        // Draw 2 crossing quads (+ cross-section)
        drawBeamQuad(pose, vc, fx, fy, fz, tx, ty, tz, right.x, right.y, right.z, r, g, 200);
        drawBeamQuad(pose, vc, fx, fy, fz, tx, ty, tz, upPerp.x, upPerp.y, upPerp.z, r, g, 200);
    }

    private static void drawBeamQuad(Matrix4f pose, VertexConsumer vc,
                                     float fx, float fy, float fz,
                                     float tx, float ty, float tz,
                                     float ox, float oy, float oz,
                                     int r, int g, int a) {
        vc.addVertex(pose, fx + ox, fy + oy, fz + oz).setColor(r, g, 0, a).setUv(0, 0).setLight(0xF000F0);
        vc.addVertex(pose, fx - ox, fy - oy, fz - oz).setColor(r, g, 0, a).setUv(1, 0).setLight(0xF000F0);
        vc.addVertex(pose, tx - ox, ty - oy, tz - oz).setColor(r, g, 0, a).setUv(1, 1).setLight(0xF000F0);
        vc.addVertex(pose, tx + ox, ty + oy, tz + oz).setColor(r, g, 0, a).setUv(0, 1).setLight(0xF000F0);
    }

    @Override
    public boolean shouldRenderOffScreen(LaserBE be) {
        return true; // beam can extend far beyond the block's AABB
    }
}
```

- [ ] **Step 2: Register the renderer**

Find where other TESR renderers are registered (search for `BlockEntityRenderers.register` or `RegisterBlockEntityRenderersEvent`):

```bash
grep -rn "BlockEntityRenderers\|KinesisPipeBERenderer" /run/media/fabi/SSD/codeing/Buildcraft-Legacy/src/main/java/ | head -5
```

Follow the same pattern to add:
```java
event.register(BCBlockEntities.LASER.get(), LaserBERenderer::new);
```

Add import:
```java
import com.thepigcat.buildcraft.client.blockentities.LaserBERenderer;
```

- [ ] **Step 3: Compile**

```bash
bash /run/media/fabi/SSD/codeing/Buildcraft-Legacy/gradlew compileJava 2>&1 | tail -5
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/client/blockentities/LaserBERenderer.java \
        src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java
git commit -m "feat(silicon): add LaserBERenderer — pulsing cross-beam TESR"
```

---

## Task 9: Datagen — blockstates, models, lang, loot, recipes

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/datagen/assets/BCBlockStateProvider.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/datagen/assets/BCItemModelProvider.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/datagen/assets/BCEnUSLangProvider.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/datagen/data/BCBlockLootTableProvider.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/datagen/data/BCRecipeProvider.java`
- Create: Block texture PNGs for Laser and Assembly Table

- [ ] **Step 1: Create placeholder block textures**

```python
# run: python3 /tmp/gen_block_textures.py
from PIL import Image, ImageDraw
import os

base = "/run/media/fabi/SSD/codeing/Buildcraft-Legacy/src/main/resources/assets/buildcraft/textures/block"

def save(name, color, border=None):
    img = Image.new("RGBA", (16, 16), color)
    if border:
        d = ImageDraw.Draw(img)
        d.rectangle([0,0,15,15], outline=border)
    img.save(os.path.join(base, f"{name}.png"))

# Laser
save("laser_top",  (80, 80, 80, 255), (40, 40, 40, 255))
save("laser_side", (80, 80, 80, 255), (40, 40, 40, 255))
save("laser_front",(30, 200, 200, 255), (20, 150, 150, 255))

# Assembly Table
save("assembly_table_top",  (80, 70, 60, 255), (50, 40, 30, 255))
save("assembly_table_side", (100, 90, 80, 255), (50, 40, 30, 255))

print("Done")
```

```bash
python3 /tmp/gen_block_textures.py
```

- [ ] **Step 2: Add blockstates and models in BCBlockStateProvider**

In `registerStatesAndModels()`, add before the pipe loop:

```java
laserBlock(BCBlocks.LASER.get());
simpleBlock(BCBlocks.ASSEMBLY_TABLE.get());
```

Add the `laserBlock` helper method:

```java
private void laserBlock(Block block) {
    ResourceLocation blockLoc = key(block);
    String base = "block/" + blockLoc.getPath();
    BlockModelBuilder model = models().withExistingParent(name(block), mcLoc("block/orientable_with_bottom"))
            .texture("top",    modLoc(base + "_top"))
            .texture("bottom", modLoc(base + "_top"))
            .texture("front",  modLoc(base + "_front"))
            .texture("side",   modLoc(base + "_side"));
    facingBlock(block, model);
}
```

For `simpleBlock(BCBlocks.ASSEMBLY_TABLE.get())`, the datagen framework's built-in `simpleBlock()` auto-generates a cube_all model. Override with a custom cube_column-like model instead:

Replace `simpleBlock(BCBlocks.ASSEMBLY_TABLE.get())` with:

```java
BlockModelBuilder tableModel = models().withExistingParent(name(BCBlocks.ASSEMBLY_TABLE.get()), mcLoc("block/cube_bottom_top"))
        .texture("top",  modLoc("block/assembly_table_top"))
        .texture("bottom", modLoc("block/assembly_table_top"))
        .texture("side", modLoc("block/assembly_table_side"));
simpleBlock(BCBlocks.ASSEMBLY_TABLE.get(), tableModel);
```

- [ ] **Step 3: Add item models in BCItemModelProvider**

In `registerModels()`, add:

```java
// Chipsets
basicItem(BCItems.RED_CHIPSET.get());
basicItem(BCItems.IRON_CHIPSET.get());
basicItem(BCItems.GOLD_CHIPSET.get());
basicItem(BCItems.QUARTZ_CHIPSET.get());
basicItem(BCItems.DIAMOND_CHIPSET.get());
```

Add `LASER` and `ASSEMBLY_TABLE` to `DEFAULT_MODEL_BLACKLIST` so the auto-block-item handler skips them (they have custom models already generated by blockstate provider):

```java
private static final Set<Block> DEFAULT_MODEL_BLACKLIST = Set.of(
        BCBlocks.TANK.get(),
        BCBlocks.CRATE.get(),
        BCBlocks.REDSTONE_ENGINE.get(),
        BCBlocks.STIRLING_ENGINE.get(),
        BCBlocks.COMBUSTION_ENGINE.get(),
        BCBlocks.LASER.get(),
        BCBlocks.ASSEMBLY_TABLE.get()
);
```

- [ ] **Step 4: Add lang entries in BCEnUSLangProvider**

Find the `addTranslations()` (or equivalent) method and add:

```java
add(BCBlocks.LASER.get(), "Laser");
add(BCBlocks.ASSEMBLY_TABLE.get(), "Assembly Table");
add(BCItems.RED_CHIPSET.get(), "Redstone Chipset");
add(BCItems.IRON_CHIPSET.get(), "Iron Chipset");
add(BCItems.GOLD_CHIPSET.get(), "Gold Chipset");
add(BCItems.QUARTZ_CHIPSET.get(), "Quartz Chipset");
add(BCItems.DIAMOND_CHIPSET.get(), "Diamond Chipset");
add("container.buildcraft.assembly_table", "Assembly Table");
```

- [ ] **Step 5: Add loot tables in BCBlockLootTableProvider**

In `generate()` (or `getBlockTables()`), add:

```java
dropSelf(BCBlocks.LASER.get());
dropSelf(BCBlocks.ASSEMBLY_TABLE.get());
```

- [ ] **Step 6: Add crafting recipes in BCRecipeProvider**

In `buildRecipes()`, add:

```java
// Laser — Iron Ingots + Redstone + Gold
ShapedRecipeBuilder.shaped(RecipeCategory.MISC, BCBlocks.LASER)
        .pattern("IRI")
        .pattern("IGI")
        .pattern("IRI")
        .define('I', Tags.Items.INGOTS_IRON)
        .define('R', Items.REDSTONE)
        .define('G', Tags.Items.INGOTS_GOLD)
        .unlockedBy("has_redstone", has(Items.REDSTONE))
        .save(recipeOutput);

// Assembly Table — Iron + Redstone + Pistons
ShapedRecipeBuilder.shaped(RecipeCategory.MISC, BCBlocks.ASSEMBLY_TABLE)
        .pattern("IPI")
        .pattern("RLR")
        .pattern("IPI")
        .define('I', Tags.Items.INGOTS_IRON)
        .define('P', Blocks.PISTON)
        .define('R', Items.REDSTONE)
        .define('L', Items.LAPIS_LAZULI)
        .unlockedBy("has_iron", has(Tags.Items.INGOTS_IRON))
        .save(recipeOutput);
```

Add import if missing: `import net.minecraft.world.level.block.Blocks;`

- [ ] **Step 7: Run datagen**

```bash
bash /run/media/fabi/SSD/codeing/Buildcraft-Legacy/gradlew runData 2>&1 | tail -10
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Compile**

```bash
bash /run/media/fabi/SSD/codeing/Buildcraft-Legacy/gradlew compileJava 2>&1 | tail -5
```

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/thepigcat/buildcraft/datagen/ \
        src/main/resources/assets/buildcraft/textures/block/laser_top.png \
        src/main/resources/assets/buildcraft/textures/block/laser_side.png \
        src/main/resources/assets/buildcraft/textures/block/laser_front.png \
        src/main/resources/assets/buildcraft/textures/block/assembly_table_top.png \
        src/main/resources/assets/buildcraft/textures/block/assembly_table_side.png \
        src/main/resources/
git commit -m "feat(silicon): datagen — blockstates, models, lang, loot, crafting recipes"
```

---

## Self-review checklist (run before handing off)

1. Every class in the File Map from the spec exists and compiles.
2. `AssemblyRecipeRegistry.all()` returns the 5 chipset recipes after `registerAssemblyRecipes()` runs.
3. `LaserBE` has `getUpdatePacket()` returning `ClientboundBlockEntityDataPacket.create(this)`.
4. `AssemblyTableBE` has `getUpdatePacket()`, `getUpdateTag()`, `handleUpdateTag()`, `saveData()`, `loadData()`.
5. `SetRecipeStatePayload` is registered `playToServer` in `registerPayloads()`.
6. `LaserBE` FE capability is registered in `attachCaps()`.
7. Screen `AssemblyTableScreen` is registered in the mod client setup.
8. `LaserBERenderer` is registered in the TESR registration event.
9. Datagen ran without errors (`runData` green).
10. Final `compileJava` is green.
