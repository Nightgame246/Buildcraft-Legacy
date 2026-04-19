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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public class AssemblyTableBE extends ContainerBlockEntity implements ILaserTarget, MenuProvider {
    public long power = 0;
    public final Map<ResourceLocation, EnumAssemblyRecipeState> recipeStates = new LinkedHashMap<>();

    public AssemblyTableBE(BlockPos pos, BlockState state) {
        super(BCBlockEntities.ASSEMBLY_TABLE.get(), pos, state);
        addItemHandler(HandlerUtils::newItemStackHandler, builder -> builder.slots(12));
    }

    // ── ILaserTarget ─────────────────────────────────────────────────────────

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

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, AssemblyTableBE be) {
        if (level.isClientSide) return;
        boolean changed = be.updateRecipes();
        AssemblyRecipe active = be.getActiveRecipe();
        if (active != null && be.power >= active.feCost()) {
            be.power -= active.feCost();
            be.consumeInputs(active);
            ejectOutput(level, pos, active.output().copy());
            be.recipeStates.put(active.id(), EnumAssemblyRecipeState.SAVED_ENOUGH);
            be.activateNextRecipe();
            changed = true;
        }
        if (changed) {
            level.sendBlockUpdated(pos, state, state, 3);
            be.setChanged();
        }
    }

    // ── Recipe state machine ──────────────────────────────────────────────────

    private boolean updateRecipes() {
        IItemHandler inv = getItemHandler();
        if (inv == null) return false;
        boolean changed = false;

        for (AssemblyRecipe recipe : AssemblyRecipeRegistry.all()) {
            if (!recipeStates.containsKey(recipe.id())) {
                if (hasIngredients(inv, recipe)) {
                    recipeStates.put(recipe.id(), EnumAssemblyRecipeState.POSSIBLE);
                    changed = true;
                }
            }
        }

        for (var it = recipeStates.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            AssemblyRecipe recipe = AssemblyRecipeRegistry.get(entry.getKey());
            if (recipe == null) { it.remove(); changed = true; continue; }

            boolean enough = hasIngredients(inv, recipe);
            EnumAssemblyRecipeState cur = entry.getValue();
            EnumAssemblyRecipeState next = switch (cur) {
                case POSSIBLE -> enough ? cur : null;
                case SAVED -> enough ? EnumAssemblyRecipeState.SAVED_ENOUGH : cur;
                case SAVED_ENOUGH -> enough ? cur : EnumAssemblyRecipeState.SAVED;
                case SAVED_ENOUGH_ACTIVE -> enough ? cur : EnumAssemblyRecipeState.SAVED;
            };

            if (next == null) { it.remove(); changed = true; }
            else if (next != cur) { entry.setValue(next); changed = true; }
        }

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

    public void toggleSaved(ResourceLocation recipeId) {
        EnumAssemblyRecipeState state = recipeStates.get(recipeId);
        if (state == null) return;
        EnumAssemblyRecipeState next = switch (state) {
            case POSSIBLE -> EnumAssemblyRecipeState.SAVED;
            case SAVED -> EnumAssemblyRecipeState.POSSIBLE;
            case SAVED_ENOUGH -> EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE;
            case SAVED_ENOUGH_ACTIVE -> EnumAssemblyRecipeState.SAVED_ENOUGH;
        };
        // Ensure only one SAVED_ENOUGH_ACTIVE at a time
        if (next == EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE) {
            recipeStates.replaceAll((id, s) ->
                    s == EnumAssemblyRecipeState.SAVED_ENOUGH_ACTIVE && !id.equals(recipeId)
                            ? EnumAssemblyRecipeState.SAVED_ENOUGH : s);
        }
        recipeStates.put(recipeId, next);
        setChanged();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
            var cap = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.relative(dir), dir.getOpposite());
            if (cap != null) {
                ItemStack remainder = ItemHandlerHelper.insertItem(cap, output, false);
                if (remainder.isEmpty()) return;
                output = remainder;
            }
        }
        net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(
                level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, output);
        level.addFreshEntity(entity);
    }

    // ── Sync ─────────────────────────────────────────────────────────────────

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

    // ── NBT ──────────────────────────────────────────────────────────────────

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

    // ── MenuProvider ──────────────────────────────────────────────────────────

    @Override
    public @NotNull Component getDisplayName() {
        return Component.translatable("container.buildcraft.assembly_table");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player player) {
        return new AssemblyTableMenu(id, inv, this);
    }
}
