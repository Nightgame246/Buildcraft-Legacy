package com.thepigcat.buildcraft.client.screens;

import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.content.blockentities.EmeraldItemPipeBE;
import com.thepigcat.buildcraft.content.menus.EmeraldPipeMenu;
import com.thepigcat.buildcraft.networking.ToggleFilterModePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class EmeraldPipeScreen extends AbstractContainerScreen<EmeraldPipeMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(BuildcraftLegacy.MODID, "textures/gui/emerald_pipe.png");

    private Button toggleButton;

    public EmeraldPipeScreen(EmeraldPipeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 161;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // Toggle button: in the gap between filter row and player inventory
        toggleButton = Button.builder(getFilterModeLabel(), btn -> {
            PacketDistributor.sendToServer(new ToggleFilterModePayload(menu.blockEntity.getBlockPos()));
        }).bounds(x + 62, y + 45, 52, 16).build();
        addRenderableWidget(toggleButton);
    }

    private Component getFilterModeLabel() {
        EmeraldItemPipeBE.FilterMode mode = menu.getFilterMode();
        return Component.literal(mode == EmeraldItemPipeBE.FilterMode.WHITELIST ? "Whitelist" : "Blacklist");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Update button text when server syncs the mode
        if (toggleButton != null) {
            toggleButton.setMessage(getFilterModeLabel());
        }
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
