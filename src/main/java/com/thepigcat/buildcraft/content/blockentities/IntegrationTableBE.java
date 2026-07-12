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
