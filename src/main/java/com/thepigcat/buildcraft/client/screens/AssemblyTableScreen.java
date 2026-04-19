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
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
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
        this.imageHeight = 250;
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
            g.renderItem(recipe.output(), px + 2, ey + 2);
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
