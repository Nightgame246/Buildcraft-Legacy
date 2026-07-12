package com.thepigcat.buildcraft.api.recipes;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IntegrationRecipeRegistry {
    private static final Map<ResourceLocation, IntegrationRecipe> REGISTRY = new LinkedHashMap<>();

    public static void register(IntegrationRecipe recipe) {
        REGISTRY.put(recipe.id(), recipe);
    }

    public static IntegrationRecipe get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static Collection<IntegrationRecipe> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    private IntegrationRecipeRegistry() {}
}
