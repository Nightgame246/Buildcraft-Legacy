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
