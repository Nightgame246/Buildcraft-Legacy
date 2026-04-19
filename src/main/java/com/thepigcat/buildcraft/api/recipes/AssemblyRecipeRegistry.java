package com.thepigcat.buildcraft.api.recipes;

import net.minecraft.resources.ResourceLocation;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AssemblyRecipeRegistry {
    private static final Map<ResourceLocation, AssemblyRecipe> REGISTRY = new LinkedHashMap<>();

    public static void register(AssemblyRecipe recipe) {
        REGISTRY.put(recipe.id(), recipe);
    }

    public static AssemblyRecipe get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static Collection<AssemblyRecipe> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    private AssemblyRecipeRegistry() {}
}
