package com.thepigcat.buildcraft.client.screens;

import com.portingdeadmods.portingdeadlibs.api.client.screens.PDLAbstractContainerScreen;
import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.content.enums.EnumAssemblyRecipeState;
import com.thepigcat.buildcraft.content.menus.AssemblyTableMenu;
import com.thepigcat.buildcraft.networking.SetRecipeStatePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AssemblyTableScreen extends PDLAbstractContainerScreen<AssemblyTableMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(BuildcraftLegacy.MODID, "textures/gui/assembly_table.png");

    // State icon UV offsets in the texture (at x=176)
    private static final int UV_SAVED           = 0;
    private static final int UV_SAVED_ENOUGH    = 16;
    private static final int UV_ACTIVE          = 32;
    // Progress bar UV (x=176, y=48), 4px wide x 70px tall, fills from bottom
    private static final int PROGRESS_X = 86;
    private static final int PROGRESS_Y = 36;
    private static final int PROGRESS_H = 70;

    public AssemblyTableScreen(AssemblyTableMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 207;
        this.inventoryLabelY = this.imageHeight - 94; // = 113, just above player inv at y=123
    }

    @Override
    public @NotNull ResourceLocation getBackgroundTexture() {
        return TEXTURE;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        super.renderBg(g, partialTick, mx, my);
        renderProgressBar(g);
        renderStateIcons(g);
    }

    private void renderProgressBar(GuiGraphics g) {
        long power = menu.getPower();
        long target = menu.getTarget();
        if (target <= 0) return;
        float frac = Math.min(1f, (float) power / target);
        int fillH = (int) (PROGRESS_H * frac);
        if (fillH <= 0) return;
        int skipH = PROGRESS_H - fillH;
        g.blit(TEXTURE, leftPos + PROGRESS_X, topPos + PROGRESS_Y + skipH, 176, 48 + skipH, 4, fillH);
    }

    private void renderStateIcons(GuiGraphics g) {
        Map<ResourceLocation, EnumAssemblyRecipeState> states = menu.getRecipeStates();
        List<Map.Entry<ResourceLocation, EnumAssemblyRecipeState>> entries = new ArrayList<>(states.entrySet());
        for (int i = 0; i < Math.min(12, entries.size()); i++) {
            int iconV = switch (entries.get(i).getValue()) {
                case SAVED -> UV_SAVED;
                case SAVED_ENOUGH -> UV_SAVED_ENOUGH;
                case SAVED_ENOUGH_ACTIVE -> UV_ACTIVE;
                default -> -1; // POSSIBLE: no icon
            };
            if (iconV < 0) continue;
            int col = i % 3;
            int row = i / 3;
            g.blit(TEXTURE, leftPos + 116 + col * 18, topPos + 36 + row * 18, 176, iconV, 16, 16);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            List<ResourceLocation> ids = new ArrayList<>(menu.getRecipeStates().keySet());
            for (int i = 0; i < Math.min(12, ids.size()); i++) {
                int col = i % 3;
                int row = i / 3;
                int slotX = leftPos + 116 + col * 18;
                int slotY = topPos + 36 + row * 18;
                if (mx >= slotX && mx < slotX + 16 && my >= slotY && my < slotY + 16) {
                    PacketDistributor.sendToServer(
                            new SetRecipeStatePayload(menu.blockEntity.getBlockPos(), ids.get(i)));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
