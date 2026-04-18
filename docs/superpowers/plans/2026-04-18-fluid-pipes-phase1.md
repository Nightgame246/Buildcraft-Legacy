# Fluid Pipe Materials Phase 1 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Sandstone, Quartz, Void, and Iron fluid pipe materials to achieve BC 1.12 parity.

**Architecture:** Sandstone/Quartz reuse `FluidPipeBE` via `FLUID_DEFAULT` PipeType with new `applyMaterialProperties()` cases. Void and Iron each get their own BlockEntity subclass and PipeType. Three protected hooks in `FluidPipeBE` (`isVoidPipe`, `isOutputAllowed`, `isInputBlocked`) decouple the subclass behavior without exposing private internals.

**Tech Stack:** NeoForge 1.21.1, Java 21, Minecraft 1.21.1 block/BE registration patterns as used throughout this codebase.

---

## File Map

| Action | File |
|--------|------|
| Modify | `src/main/java/com/thepigcat/buildcraft/content/blockentities/FluidPipeBE.java` |
| Create | `src/main/java/com/thepigcat/buildcraft/content/blockentities/VoidFluidPipeBE.java` |
| Create | `src/main/java/com/thepigcat/buildcraft/content/blockentities/IronFluidPipeBE.java` |
| Create | `src/main/java/com/thepigcat/buildcraft/content/blocks/VoidFluidPipeBlock.java` |
| Create | `src/main/java/com/thepigcat/buildcraft/content/blocks/IronFluidPipeBlock.java` |
| Modify | `src/main/java/com/thepigcat/buildcraft/registries/BCPipeTypes.java` |
| Modify | `src/main/java/com/thepigcat/buildcraft/registries/BCBlockEntities.java` |
| Modify | `src/main/java/com/thepigcat/buildcraft/registries/BCPipes.java` |
| Modify | `src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java` |
| Modify | `src/main/java/com/thepigcat/buildcraft/content/blocks/FluidPipeBlock.java` |
| Create | `src/main/resources/assets/buildcraft/textures/block/sandstone_fluid_pipe.png` |
| Create | `src/main/resources/assets/buildcraft/textures/block/quartz_fluid_pipe.png` |
| Create | `src/main/resources/assets/buildcraft/textures/block/void_fluid_pipe.png` |
| Create | `src/main/resources/assets/buildcraft/textures/block/iron_fluid_pipe.png` |
| Create | `src/main/resources/assets/buildcraft/textures/block/iron_fluid_pipe_blocked.png` |

---

### Task 1: FluidPipeBE — Add 3 Protected Hook Methods

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/FluidPipeBE.java`

- [ ] **Step 1: Add the three protected hook methods**

  Add these three methods immediately after `applyMaterialProperties()` (around line 102), before the `// ── Tick ──` comment:

  ```java
  /** Override to return true for void pipes: fluid entering the pipe is destroyed. */
  protected boolean isVoidPipe() { return false; }

  /** Override to restrict which center-to-face directions are allowed. Iron pipe returns dir == lockedDirection. */
  protected boolean isOutputAllowed(Direction dir) { return true; }

  /**
   * Override to block a face section from accepting external fills (IFluidHandler.fill).
   * Iron pipe returns true for the lockedDirection face to prevent back-fill from the output side.
   */
  protected boolean isInputBlocked(int sectionIdx) { return false; }
  ```

- [ ] **Step 2: Guard moveFromPipe() for void pipe**

  `moveFromPipe()` is at line 232. Add a guard at the very start of the method body:

  ```java
  private void moveFromPipe() {
      if (isVoidPipe()) return;   // ← ADD THIS LINE
      for (Direction face : Direction.values()) {
  ```

- [ ] **Step 3: Guard moveFromCenter() for iron pipe output restriction**

  In `moveFromCenter()` (line 255), inside the `for (Direction dir : Direction.values())` loop, add the `isOutputAllowed` check. The modified loop body looks like this:

  ```java
  for (Direction dir : Direction.values()) {
      Section section = sections[dir.ordinal()];
      if (!section.getCurrentDirection().canOutput()) continue;
      if (!isOutputAllowed(dir)) continue;   // ← ADD THIS LINE
      if (section.getMaxFilled() > 0 && capabilityCaches.containsKey(dir)) {
  ```

- [ ] **Step 4: Modify moveToCenter() for void pipe fluid destruction**

  In `moveToCenter()` (line 295), find the innermost `if (actuallyDrained > 0)` block (around line 331). Change it from:

  ```java
  if (actuallyDrained > 0) {
      center.fill(actuallyDrained, true);
      section.ticksInDirection = COOLDOWN_INPUT;
      left -= actuallyDrained;
  }
  ```

  To:

  ```java
  if (actuallyDrained > 0) {
      if (!isVoidPipe()) {
          center.fill(actuallyDrained, true);
      }
      section.ticksInDirection = COOLDOWN_INPUT;
      left -= actuallyDrained;
  }
  ```

- [ ] **Step 5: Block external fill for iron pipe lockedDirection in Section.fill()**

  Inside `Section.fill(@NotNull FluidStack resource, FluidAction action)` (the IFluidHandler implementation, around line 602), add the `isInputBlocked` check after the `directions.contains(face)` guard:

  ```java
  // Check pipe connectivity for face sections
  if (index < 6) {
      Direction face = Direction.values()[index];
      if (!directions.contains(face)) return 0;
  }
  // Block external fill on iron pipe output face
  if (index < 6 && isInputBlocked(index)) return 0;   // ← ADD THIS LINE
  ```

- [ ] **Step 6: Verify the build compiles**

  ```bash
  ./gradlew compileJava
  ```

  Expected: BUILD SUCCESSFUL, 0 errors.

- [ ] **Step 7: Commit**

  ```bash
  git add src/main/java/com/thepigcat/buildcraft/content/blockentities/FluidPipeBE.java
  git commit -m "feat(fluid-pipe): add isVoidPipe/isOutputAllowed/isInputBlocked hooks for subclasses"
  ```

---

### Task 2: VoidFluidPipeBE + VoidFluidPipeBlock

**Files:**
- Create: `src/main/java/com/thepigcat/buildcraft/content/blockentities/VoidFluidPipeBE.java`
- Create: `src/main/java/com/thepigcat/buildcraft/content/blocks/VoidFluidPipeBlock.java`

- [ ] **Step 1: Create VoidFluidPipeBE.java**

  ```java
  package com.thepigcat.buildcraft.content.blockentities;

  import com.thepigcat.buildcraft.registries.BCBlockEntities;
  import net.minecraft.core.BlockPos;
  import net.minecraft.world.level.block.state.BlockState;

  public class VoidFluidPipeBE extends FluidPipeBE {
      public VoidFluidPipeBE(BlockPos pos, BlockState blockState) {
          super(BCBlockEntities.VOID_FLUID_PIPE.get(), pos, blockState);
      }

      @Override
      protected boolean isVoidPipe() {
          return true;
      }
  }
  ```

- [ ] **Step 2: Create VoidFluidPipeBlock.java**

  ```java
  package com.thepigcat.buildcraft.content.blocks;

  import com.mojang.serialization.MapCodec;
  import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
  import com.thepigcat.buildcraft.registries.BCBlockEntities;
  import net.minecraft.world.level.block.BaseEntityBlock;
  import net.minecraft.world.level.block.entity.BlockEntityType;

  public class VoidFluidPipeBlock extends FluidPipeBlock {
      public VoidFluidPipeBlock(Properties properties) {
          super(properties);
      }

      @Override
      protected BlockEntityType<? extends PipeBlockEntity<?>> getBlockEntityType() {
          return BCBlockEntities.VOID_FLUID_PIPE.get();
      }

      @Override
      protected MapCodec<? extends BaseEntityBlock> codec() {
          return simpleCodec(VoidFluidPipeBlock::new);
      }
  }
  ```

- [ ] **Step 3: Compile check**

  ```bash
  ./gradlew compileJava
  ```

  Expected: BUILD SUCCESSFUL (both files refer to `BCBlockEntities.VOID_FLUID_PIPE` which doesn't exist yet — you will see a compile error here. That's expected. Registrations come in Task 4, so come back and re-run this check after Task 4.)

- [ ] **Step 4: Commit (after Task 4 compile succeeds)**

  ```bash
  git add src/main/java/com/thepigcat/buildcraft/content/blockentities/VoidFluidPipeBE.java
  git add src/main/java/com/thepigcat/buildcraft/content/blocks/VoidFluidPipeBlock.java
  git commit -m "feat(fluid-pipe): add VoidFluidPipeBlock and VoidFluidPipeBE"
  ```

---

### Task 3: IronFluidPipeBE + IronFluidPipeBlock

**Files:**
- Create: `src/main/java/com/thepigcat/buildcraft/content/blockentities/IronFluidPipeBE.java`
- Create: `src/main/java/com/thepigcat/buildcraft/content/blocks/IronFluidPipeBlock.java`

- [ ] **Step 1: Create IronFluidPipeBE.java**

  ```java
  package com.thepigcat.buildcraft.content.blockentities;

  import com.thepigcat.buildcraft.api.blocks.PipeBlock;
  import com.thepigcat.buildcraft.registries.BCBlockEntities;
  import net.minecraft.core.BlockPos;
  import net.minecraft.core.Direction;
  import net.minecraft.core.HolderLookup;
  import net.minecraft.nbt.CompoundTag;
  import net.minecraft.world.level.block.state.BlockState;

  import java.util.List;
  import java.util.Set;

  public class IronFluidPipeBE extends FluidPipeBE {
      private Direction lockedDirection;

      public IronFluidPipeBE(BlockPos pos, BlockState blockState) {
          super(BCBlockEntities.IRON_FLUID_PIPE.get(), pos, blockState);
      }

      @Override
      protected boolean isOutputAllowed(Direction dir) {
          return lockedDirection == null || dir == lockedDirection;
      }

      @Override
      protected boolean isInputBlocked(int sectionIdx) {
          return lockedDirection != null && Direction.values()[sectionIdx] == lockedDirection;
      }

      /** Rotates the locked output direction through all currently connected directions. */
      public void rotateLockedDirection() {
          if (directions.isEmpty()) {
              lockedDirection = null;
              updateBlockedDirections();
              return;
          }

          List<Direction> sorted = directions.stream()
                  .sorted(java.util.Comparator.comparingInt(Direction::ordinal))
                  .toList();

          if (lockedDirection == null || !sorted.contains(lockedDirection)) {
              lockedDirection = sorted.getFirst();
          } else {
              int idx = sorted.indexOf(lockedDirection);
              lockedDirection = sorted.get((idx + 1) % sorted.size());
          }

          updateBlockedDirections();
          setChanged();
          if (level != null && !level.isClientSide()) {
              level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
          }
      }

      /** Sets the blockstate CONNECTION properties: BLOCKED for lockedDirection, CONNECTED for others. */
      private void updateBlockedDirections() {
          if (level == null || level.isClientSide()) return;
          BlockState state = getBlockState();
          boolean changed = false;
          for (Direction dir : Direction.values()) {
              PipeBlock.PipeState current = state.getValue(PipeBlock.CONNECTION[dir.get3DDataValue()]);
              if (current != PipeBlock.PipeState.NONE) {
                  PipeBlock.PipeState target = (dir == lockedDirection)
                          ? PipeBlock.PipeState.BLOCKED
                          : PipeBlock.PipeState.CONNECTED;
                  if (current != target) {
                      state = state.setValue(PipeBlock.CONNECTION[dir.get3DDataValue()], target);
                      changed = true;
                  }
              }
          }
          if (changed) {
              level.setBlock(worldPosition, state, 3);
          }
      }

      public Direction getLockedDirection() {
          return lockedDirection;
      }

      @Override
      public void onLoad() {
          super.onLoad();
          // Default to first connected direction if nothing was saved
          if (lockedDirection == null && !directions.isEmpty()) {
              lockedDirection = directions.iterator().next();
          }
          updateBlockedDirections();
      }

      @Override
      public void setDirections(Set<Direction> directions) {
          super.setDirections(directions);
          if (lockedDirection == null && !directions.isEmpty()) {
              lockedDirection = directions.iterator().next();
          }
          updateBlockedDirections();
      }

      @Override
      protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
          super.loadAdditional(tag, registries);
          if (tag.contains("locked_direction")) {
              int idx = tag.getInt("locked_direction");
              this.lockedDirection = idx >= 0 ? Direction.values()[idx] : null;
          } else {
              this.lockedDirection = null;
          }
      }

      @Override
      protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
          super.saveAdditional(tag, registries);
          tag.putInt("locked_direction", lockedDirection != null ? lockedDirection.ordinal() : -1);
      }
  }
  ```

- [ ] **Step 2: Create IronFluidPipeBlock.java**

  ```java
  package com.thepigcat.buildcraft.content.blocks;

  import com.mojang.serialization.MapCodec;
  import com.thepigcat.buildcraft.PipesRegistry;
  import com.thepigcat.buildcraft.api.blockentities.PipeBlockEntity;
  import com.thepigcat.buildcraft.api.blocks.PipeBlock;
  import com.thepigcat.buildcraft.api.pipes.Pipe;
  import com.thepigcat.buildcraft.content.blockentities.IronFluidPipeBE;
  import com.thepigcat.buildcraft.registries.BCBlockEntities;
  import com.thepigcat.buildcraft.registries.BCItems;
  import com.thepigcat.buildcraft.util.BlockUtils;
  import com.thepigcat.buildcraft.util.CapabilityUtils;
  import net.minecraft.core.BlockPos;
  import net.minecraft.core.Direction;
  import net.minecraft.core.registries.BuiltInRegistries;
  import net.minecraft.network.chat.Component;
  import net.minecraft.world.InteractionHand;
  import net.minecraft.world.InteractionResult;
  import net.minecraft.world.ItemInteractionResult;
  import net.minecraft.world.entity.player.Player;
  import net.minecraft.world.item.Item;
  import net.minecraft.world.item.ItemStack;
  import net.minecraft.world.level.Level;
  import net.minecraft.world.level.LevelAccessor;
  import net.minecraft.world.level.block.BaseEntityBlock;
  import net.minecraft.world.level.block.Block;
  import net.minecraft.world.level.block.entity.BlockEntity;
  import net.minecraft.world.level.block.entity.BlockEntityType;
  import net.minecraft.world.level.block.state.BlockState;
  import net.minecraft.world.level.storage.loot.LootParams;
  import net.minecraft.world.phys.BlockHitResult;
  import org.jetbrains.annotations.NotNull;

  import java.util.List;

  public class IronFluidPipeBlock extends FluidPipeBlock {
      public IronFluidPipeBlock(Properties properties) {
          super(properties);
      }

      @Override
      public PipeState getConnectionType(LevelAccessor level, BlockPos pipePos, BlockState pipeState,
                                          Direction connectionDirection, BlockPos connectPos) {
          BlockState otherState = level.getBlockState(connectPos);
          Block otherBlock = otherState.getBlock();
          BlockEntity be = level.getBlockEntity(connectPos);

          boolean isConnected = false;
          if (isFluidPipe(otherBlock)) {
              isConnected = true;
          } else if (otherBlock instanceof PipeBlock) {
              return PipeState.NONE;
          } else if (be != null && CapabilityUtils.fluidHandlerCapability(be, connectionDirection.getOpposite()) != null) {
              isConnected = true;
          }

          if (isConnected) {
              IronFluidPipeBE ironBE = BlockUtils.getBE(IronFluidPipeBE.class, level, pipePos);
              if (ironBE != null && ironBE.getLockedDirection() != null) {
                  return connectionDirection == ironBE.getLockedDirection()
                          ? PipeState.BLOCKED : PipeState.CONNECTED;
              } else {
                  // Before BE data arrives on client, preserve existing BLOCKED state
                  PipeState existing = pipeState.getValue(CONNECTION[connectionDirection.get3DDataValue()]);
                  if (existing == PipeState.BLOCKED) return PipeState.BLOCKED;
              }
              return PipeState.CONNECTED;
          }

          return PipeState.NONE;
      }

      @Override
      protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                  Player player, BlockHitResult hitResult) {
          return InteractionResult.PASS;
      }

      @Override
      protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                 BlockPos pos, Player player, InteractionHand hand,
                                                 BlockHitResult hitResult) {
          if (stack.getItem() == BCItems.WRENCH.get()) {
              if (!level.isClientSide()) {
                  IronFluidPipeBE be = BlockUtils.getBE(IronFluidPipeBE.class, level, pos);
                  if (be != null) {
                      be.rotateLockedDirection();
                      player.displayClientMessage(
                              Component.literal("Iron Fluid Pipe output: " + be.getLockedDirection()), true);
                  }
              }
              return ItemInteractionResult.sidedSuccess(level.isClientSide());
          }
          return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
      }

      @Override
      protected MapCodec<? extends BaseEntityBlock> codec() {
          return simpleCodec(IronFluidPipeBlock::new);
      }

      @Override
      protected BlockEntityType<? extends PipeBlockEntity<?>> getBlockEntityType() {
          return BCBlockEntities.IRON_FLUID_PIPE.get();
      }

      @Override
      protected @NotNull List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
          Pipe pipe = PipesRegistry.PIPES.get(this.builtInRegistryHolder().key().location().getPath());
          Item dropItem = BuiltInRegistries.ITEM.get(pipe.dropItem());
          if (!pipe.customLoottable()) {
              return List.of(dropItem.getDefaultInstance());
          }
          return super.getDrops(state, params);
      }
  }
  ```

- [ ] **Step 3: Commit (after Task 4 compile succeeds)**

  ```bash
  git add src/main/java/com/thepigcat/buildcraft/content/blockentities/IronFluidPipeBE.java
  git add src/main/java/com/thepigcat/buildcraft/content/blocks/IronFluidPipeBlock.java
  git commit -m "feat(fluid-pipe): add IronFluidPipeBlock and IronFluidPipeBE"
  ```

---

### Task 4: Registrations — BCPipeTypes, BCBlockEntities, BCPipes, Caps

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCPipeTypes.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCBlockEntities.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/registries/BCPipes.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java`
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blocks/FluidPipeBlock.java`

- [ ] **Step 1: Add FLUID_VOID and FLUID_IRON to BCPipeTypes**

  Add imports at the top of BCPipeTypes.java:
  ```java
  import com.thepigcat.buildcraft.content.blocks.IronFluidPipeBlock;
  import com.thepigcat.buildcraft.content.blocks.VoidFluidPipeBlock;
  ```

  Append after the existing `FLUID_EXTRACTING` declaration (before `public static void init()`):
  ```java
  public static final PipeTypeHolder<VoidFluidPipeBlock, ItemPipeBlockItem> FLUID_VOID =
      HELPER.registerPipeType("fluid_void", VoidFluidPipeBlock::new, ItemPipeBlockItem::new,
          ModelUtils.DEFAULT_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE,
          ModelUtils.DEFAULT_ITEM_MODEL_FILE, "base", "connection");

  public static final PipeTypeHolder<IronFluidPipeBlock, ItemPipeBlockItem> FLUID_IRON =
      HELPER.registerPipeType("fluid_iron", IronFluidPipeBlock::new, ItemPipeBlockItem::new,
          ModelUtils.IRON_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE,
          ModelUtils.DEFAULT_ITEM_MODEL_FILE, "connection", "base_blocked", "connection_blocked");
  ```

- [ ] **Step 2: Add VOID_FLUID_PIPE and IRON_FLUID_PIPE to BCBlockEntities**

  Add imports in BCBlockEntities.java:
  ```java
  import com.thepigcat.buildcraft.content.blockentities.IronFluidPipeBE;
  import com.thepigcat.buildcraft.content.blockentities.VoidFluidPipeBE;
  import com.thepigcat.buildcraft.content.blocks.IronFluidPipeBlock;
  import com.thepigcat.buildcraft.content.blocks.VoidFluidPipeBlock;
  ```

  First, add a `collectBlocksExact` helper at the bottom of BCBlockEntities (alongside the existing `collectBlocks` methods):
  ```java
  /** Matches only the exact class, not subclasses. Used to prevent FLUID_PIPE from
   *  capturing VoidFluidPipeBlock/IronFluidPipeBlock instances. */
  private static Block[] collectBlocksExact(Class<? extends Block> clazz) {
      return BuiltInRegistries.BLOCK.stream()
              .filter(b -> b.getClass() == clazz)
              .toList().toArray(Block[]::new);
  }
  ```

  Then change the existing `FLUID_PIPE` declaration from `collectBlocks(FluidPipeBlock.class)` to `collectBlocksExact(FluidPipeBlock.class)`:
  ```java
  public static final Supplier<BlockEntityType<FluidPipeBE>> FLUID_PIPE = BLOCK_ENTITIES.register("fluid_pipe",
          () -> BlockEntityType.Builder.of(FluidPipeBE::new, collectBlocksExact(FluidPipeBlock.class)).build(null));
  ```

  Then append after the existing `EXTRACTING_FLUID_PIPE` declaration:
  ```java
  public static final Supplier<BlockEntityType<VoidFluidPipeBE>> VOID_FLUID_PIPE = BLOCK_ENTITIES.register("void_fluid_pipe",
          () -> BlockEntityType.Builder.of(VoidFluidPipeBE::new, collectBlocks(VoidFluidPipeBlock.class)).build(null));

  public static final Supplier<BlockEntityType<IronFluidPipeBE>> IRON_FLUID_PIPE = BLOCK_ENTITIES.register("iron_fluid_pipe",
          () -> BlockEntityType.Builder.of(IronFluidPipeBE::new, collectBlocks(IronFluidPipeBlock.class)).build(null));
  ```

- [ ] **Step 3: Add 4 new pipes to BCPipes**

  Add imports in BCPipes.java if not already present (check: `Tags.Items.OBSIDIANS` is already used for VOID item pipe):
  ```java
  // Tags.Items.OBSIDIANS is already imported via "import net.neoforged.neoforge.common.Tags;"
  ```

  Append after `GOLD_FLUID` (the last fluid pipe entry):
  ```java
  public static final PipeHolder SANDSTONE_FLUID = HELPER.registerPipe("sandstone_fluid", BCPipeTypes.FLUID_DEFAULT,
      "Sandstone Fluid Pipe", 0f, List.of(
          BuildcraftLegacy.rl("block/sandstone_fluid_pipe")
      ), Either.right(ResourceLocation.parse("sandstone")), Ingredient.of(Blocks.SANDSTONE),
      List.of(BlockTags.MINEABLE_WITH_PICKAXE), 34);

  public static final PipeHolder QUARTZ_FLUID = HELPER.registerPipe("quartz_fluid", BCPipeTypes.FLUID_DEFAULT,
      "Quartz Fluid Pipe", 0f, List.of(
          BuildcraftLegacy.rl("block/quartz_fluid_pipe")
      ), Either.right(ResourceLocation.parse("quartz_block")), Ingredient.of(Tags.Items.GEMS_QUARTZ),
      List.of(BlockTags.MINEABLE_WITH_PICKAXE), 35);

  public static final PipeHolder VOID_FLUID = HELPER.registerPipe("void_fluid", BCPipeTypes.FLUID_VOID,
      "Void Fluid Pipe", 0f, List.of(
          BuildcraftLegacy.rl("block/void_fluid_pipe")
      ), Either.right(ResourceLocation.parse("obsidian")), Ingredient.of(Tags.Items.OBSIDIANS),
      List.of(BlockTags.MINEABLE_WITH_PICKAXE), 36);

  public static final PipeHolder IRON_FLUID = HELPER.registerPipe("iron_fluid", BCPipeTypes.FLUID_IRON,
      "Iron Fluid Pipe", 0f, List.of(
          BuildcraftLegacy.rl("block/iron_fluid_pipe"),
          BuildcraftLegacy.rl("block/iron_fluid_pipe_blocked")
      ), Either.right(ResourceLocation.parse("iron_block")), Ingredient.of(Tags.Items.INGOTS_IRON),
      List.of(BlockTags.MINEABLE_WITH_PICKAXE), 37);
  ```

- [ ] **Step 4: Register FluidHandler capabilities in BuildcraftLegacy.attachCaps()**

  In `BuildcraftLegacy.java`, in `attachCaps()`, append after the existing `EXTRACTING_FLUID_PIPE` capability line (around line 126):
  ```java
  event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
          BCBlockEntities.VOID_FLUID_PIPE.get(), FluidPipeBE::getFluidHandler);
  event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,
          BCBlockEntities.IRON_FLUID_PIPE.get(), IronFluidPipeBE::getFluidHandler);
  ```

  You'll need to add the import for `IronFluidPipeBE` at the top of BuildcraftLegacy.java if not already there:
  ```java
  import com.thepigcat.buildcraft.content.blockentities.IronFluidPipeBE;
  ```

- [ ] **Step 5: Update FluidPipeBlock.isFluidPipe() to include VoidFluidPipeBlock**

  In `FluidPipeBlock.java`, `isFluidPipe()` is currently:
  ```java
  static boolean isFluidPipe(Block block) {
      return block instanceof FluidPipeBlock || block instanceof ExtractingFluidPipeBlock;
  }
  ```

  Since `VoidFluidPipeBlock extends FluidPipeBlock` and `IronFluidPipeBlock extends FluidPipeBlock`, `instanceof FluidPipeBlock` already catches them. No change needed — verify and move on.

- [ ] **Step 6: Compile all new code**

  ```bash
  ./gradlew compileJava
  ```

  Expected: BUILD SUCCESSFUL. If there are errors, fix imports first.

- [ ] **Step 7: Commit**

  ```bash
  git add src/main/java/com/thepigcat/buildcraft/registries/BCPipeTypes.java
  git add src/main/java/com/thepigcat/buildcraft/registries/BCBlockEntities.java
  git add src/main/java/com/thepigcat/buildcraft/registries/BCPipes.java
  git add src/main/java/com/thepigcat/buildcraft/BuildcraftLegacy.java
  git commit -m "feat(fluid-pipe): register FLUID_VOID, FLUID_IRON types and 4 new fluid pipe entries"
  ```

  Then commit the Task 2 + 3 files now that they compile:
  ```bash
  git add src/main/java/com/thepigcat/buildcraft/content/blockentities/VoidFluidPipeBE.java
  git add src/main/java/com/thepigcat/buildcraft/content/blocks/VoidFluidPipeBlock.java
  git add src/main/java/com/thepigcat/buildcraft/content/blockentities/IronFluidPipeBE.java
  git add src/main/java/com/thepigcat/buildcraft/content/blocks/IronFluidPipeBlock.java
  git commit -m "feat(fluid-pipe): add VoidFluidPipeBE, VoidFluidPipeBlock, IronFluidPipeBE, IronFluidPipeBlock"
  ```

---

### Task 5: applyMaterialProperties() — Sandstone and Quartz

**Files:**
- Modify: `src/main/java/com/thepigcat/buildcraft/content/blockentities/FluidPipeBE.java`

- [ ] **Step 1: Add sandstone and quartz cases to applyMaterialProperties()**

  Current method (lines 78-102) has this else-if chain:
  ```java
  if (pipeId.contains("gold")) { ... }
  else if (pipeId.contains("stone") && !pipeId.contains("cobblestone") && !pipeId.contains("sandstone")) { ... }
  else if (pipeId.contains("cobblestone")) { ... }
  else { ... }
  ```

  Add two new cases. Insert them BEFORE the final `else` block:
  ```java
  } else if (pipeId.contains("quartz") && pipeId.contains("fluid")) {
      transferPerTick = 40;
      delay = 5;
  } else if (pipeId.contains("sandstone") && pipeId.contains("fluid")) {
      transferPerTick = 10;
      delay = 10;
  } else {
      // Wooden and other defaults
      transferPerTick = 10;
      delay = 10;
  }
  ```

  Note: the `stone` case already excludes `sandstone` via `!pipeId.contains("sandstone")`, so order between the new cases and the stone case doesn't matter. The `quartz` case guards with `&& pipeId.contains("fluid")` to avoid matching item-pipe quartz IDs in future.

- [ ] **Step 2: Compile check**

  ```bash
  ./gradlew compileJava
  ```

  Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

  ```bash
  git add src/main/java/com/thepigcat/buildcraft/content/blockentities/FluidPipeBE.java
  git commit -m "feat(fluid-pipe): add sandstone (10mB/t) and quartz (40mB/t) speed cases"
  ```

---

### Task 6: Placeholder Textures

**Files:**
- Create: 5 PNG files in `src/main/resources/assets/buildcraft/textures/block/`

The game will crash with missing texture errors if PNGs are absent. Create placeholder textures by copying an existing one. Proper textures can replace them later.

- [ ] **Step 1: Copy cobblestone_fluid_pipe.png as placeholder for all 5 new textures**

  ```bash
  TEXDIR=src/main/resources/assets/buildcraft/textures/block
  cp "$TEXDIR/cobblestone_fluid_pipe.png" "$TEXDIR/sandstone_fluid_pipe.png"
  cp "$TEXDIR/cobblestone_fluid_pipe.png" "$TEXDIR/quartz_fluid_pipe.png"
  cp "$TEXDIR/cobblestone_fluid_pipe.png" "$TEXDIR/void_fluid_pipe.png"
  cp "$TEXDIR/cobblestone_fluid_pipe.png" "$TEXDIR/iron_fluid_pipe.png"
  cp "$TEXDIR/cobblestone_fluid_pipe.png" "$TEXDIR/iron_fluid_pipe_blocked.png"
  ```

- [ ] **Step 2: Commit placeholder textures**

  ```bash
  git add src/main/resources/assets/buildcraft/textures/block/sandstone_fluid_pipe.png
  git add src/main/resources/assets/buildcraft/textures/block/quartz_fluid_pipe.png
  git add src/main/resources/assets/buildcraft/textures/block/void_fluid_pipe.png
  git add src/main/resources/assets/buildcraft/textures/block/iron_fluid_pipe.png
  git add src/main/resources/assets/buildcraft/textures/block/iron_fluid_pipe_blocked.png
  git commit -m "feat(fluid-pipe): add placeholder textures for 4 new fluid pipe materials"
  ```

---

### Task 7: Datagen — Blockstates, Models, Lang

**Files:**
- Generated into: `src/generated/resources/`

- [ ] **Step 1: Run datagen**

  ```bash
  ./gradlew runData
  ```

  Expected: BUILD SUCCESSFUL. This generates:
  - Blockstate JSONs: `sandstone_fluid_pipe.json`, `quartz_fluid_pipe.json`, `void_fluid_pipe.json`, `iron_fluid_pipe.json`
  - Model JSONs for each pipe's `_base`, `_connection` (and `_base_blocked`, `_connection_blocked` for iron)
  - Item models for all 4 pipes
  - Lang entries are auto-generated from the pipe name strings set in BCPipes

- [ ] **Step 2: Verify generated files exist**

  ```bash
  ls src/generated/resources/assets/buildcraft/blockstates/ | grep fluid
  ```

  Expected output includes: `cobblestone_fluid_pipe.json`, `gold_fluid_pipe.json`, `iron_fluid_pipe.json`, `quartz_fluid_pipe.json`, `sandstone_fluid_pipe.json`, `stone_fluid_pipe.json`, `void_fluid_pipe.json`, `wooden_fluid_pipe.json`

- [ ] **Step 3: Full build check**

  ```bash
  ./gradlew build
  ```

  Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit generated assets**

  ```bash
  git add src/generated/
  git commit -m "feat(fluid-pipe): datagen blockstates and models for 4 new fluid pipe materials"
  ```

---

### Task 8: Ingame Verification

- [ ] **Step 1: Start the client**

  ```bash
  ./gradlew runClient
  ```

- [ ] **Step 2: Test Sandstone Fluid Pipe**
  - Craft or give: `/give @p buildcraft:sandstone_fluid_pipe`
  - Connect to a fluid source (tank) and a destination tank
  - Verify fluid flows at ~10 mB/tick speed (same as cobblestone)
  - Verify sandstone does NOT connect to machines (only to other fluid pipes)

- [ ] **Step 3: Test Quartz Fluid Pipe**
  - Craft or give: `/give @p buildcraft:quartz_fluid_pipe`
  - Connect in a line; verify fluid flows noticeably faster than stone/cobblestone
  - Rate: ~40 mB/tick (4× cobblestone)

- [ ] **Step 4: Test Void Fluid Pipe**
  - Craft or give: `/give @p buildcraft:void_fluid_pipe`
  - Connect to a fluid source; verify fluid flowing in is destroyed (source drains, no accumulation in void pipe)
  - Verify void pipe does NOT output fluid to adjacent tanks

- [ ] **Step 5: Test Iron Fluid Pipe**
  - Craft or give: `/give @p buildcraft:iron_fluid_pipe`
  - Connect it between a source and two destinations
  - Right-click with Wrench to rotate the locked output direction; verify the HUD message shows the correct direction
  - Verify only the locked direction (shown as BLOCKED texture) receives fluid
  - Verify fluid cannot back-fill into the pipe from the locked direction side

- [ ] **Step 6: Note any issues**

  If any pipe doesn't work as expected, file a bug note and revisit the relevant task. Common failure modes:
  - `currentFluid` stays EMPTY despite fluid flowing in → check `Section.fill()` guard ordering
  - Iron pipe distributes to all directions → `isOutputAllowed()` not being called (check hook integration in `moveFromCenter()`)
  - Void pipe leaks fluid to center → check `isVoidPipe()` guard in `moveToCenter()`
