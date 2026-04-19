package com.thepigcat.buildcraft.api.recipes;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.Set;

public record AssemblyRecipe(ResourceLocation id, Set<Ingredient> inputs, ItemStack output, int feCost) {}
