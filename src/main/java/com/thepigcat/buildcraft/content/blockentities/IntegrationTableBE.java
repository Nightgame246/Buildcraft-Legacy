package com.thepigcat.buildcraft.content.blockentities;

import com.portingdeadmods.portingdeadlibs.api.blockentities.ContainerBlockEntity;
import com.thepigcat.buildcraft.BCConfig;
import com.thepigcat.buildcraft.api.blockentities.ILaserTarget;
import com.thepigcat.buildcraft.api.recipes.IntegrationRecipe;
import com.thepigcat.buildcraft.api.recipes.IntegrationRecipeRegistry;
import com.thepigcat.buildcraft.content.menus.IntegrationTableMenu;
import com.thepigcat.buildcraft.registries.BCBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.item.crafting.Ingredient;
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

public class IntegrationTableBE extends ContainerBlockEntity implements ILaserTarget, MenuProvider {
    public static final int INPUT_SLOTS = 9;   // slot 0 = center, slots 1..8 = ring
    public static final int OUTPUT_SLOTS = 1;
    public static final int CENTER = 0;

    public int power = 0;
    public ItemStack assumedOutput = ItemStack.EMPTY;
    protected boolean recipeDirty = true;
    @Nullable private IntegrationRecipe currentRecipe = null;

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

    // ── ILaserTarget ──
    @Override public int getRequiredLaserPower() {
        return canCraft() ? Math.max(0, BCConfig.integrationTableFeCost - power) : 0;
    }
    @Override public void receiveLaserPower(int fe) { power += fe; setChanged(); }

    // ── Tick ──
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
