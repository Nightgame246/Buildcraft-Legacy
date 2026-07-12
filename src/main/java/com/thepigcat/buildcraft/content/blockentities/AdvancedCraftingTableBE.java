package com.thepigcat.buildcraft.content.blockentities;

import com.portingdeadmods.portingdeadlibs.api.blockentities.ContainerBlockEntity;
import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.api.blockentities.ILaserTarget;
import com.thepigcat.buildcraft.content.menus.AdvancedCraftingTableMenu;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AdvancedCraftingTableBE extends ContainerBlockEntity implements ILaserTarget, MenuProvider {
    public static final int BLUEPRINT_SLOTS = 9;
    public static final int MATERIAL_SLOTS = 15;
    public static final int RESULT_SLOTS = 9;

    public int power = 0;
    public ItemStack assumedResult = ItemStack.EMPTY;
    protected boolean blueprintDirty = true;
    @Nullable private RecipeHolder<CraftingRecipe> currentRecipe = null;

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

    // ── ILaserTarget ───────────────────────────────────────────────────────────
    @Override public int getRequiredLaserPower() {
        return canCraft() ? Math.max(0, BCConfig.advancedCraftingTableFeCost - power) : 0;
    }
    @Override public void receiveLaserPower(int fe) { power += fe; setChanged(); }

    // ── Tick ─────────────────────────────────────────────────────────────────
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
        applyClientSync(tag, registries);
    }

    // NeoForge's default onDataPacket routes runtime block-entity updates through
    // loadWithComponents()/loadData(), NOT handleUpdateTag(). We override it to apply only
    // the client-visible fields (power + assumedResult) directly, so the preview and progress
    // bar update live. We intentionally do NOT call super/loadData here: blueprint/materials/
    // results are synced through the container menu slots, and loadData would clobber them
    // from an update tag that doesn't carry them.
    @Override public void onDataPacket(net.minecraft.network.Connection net,
                                       ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) applyClientSync(tag, registries);
    }

    private void applyClientSync(CompoundTag tag, HolderLookup.Provider registries) {
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
