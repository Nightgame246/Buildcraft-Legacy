package com.thepigcat.buildcraft.api.recipes;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/**
 * An Integration Table recipe: a center item (matched by {@code center}, consumed {@code centerCount}
 * at a time) plus a ring described as an exact multiset of {@code ring} ingredients (each ingredient
 * consumes exactly one non-empty ring slot; no ring slot may be left over), producing {@code output}
 * for a fixed {@code feCost}.
 */
public record IntegrationRecipe(ResourceLocation id, Ingredient center, int centerCount,
                                List<Ingredient> ring, ItemStack output, int feCost) {}
