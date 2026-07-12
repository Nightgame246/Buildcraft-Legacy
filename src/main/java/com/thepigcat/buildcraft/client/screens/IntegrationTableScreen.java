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
