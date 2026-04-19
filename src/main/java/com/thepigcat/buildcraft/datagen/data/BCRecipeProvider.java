package com.thepigcat.buildcraft.datagen.data;

import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.registries.BCBlocks;
import com.thepigcat.buildcraft.registries.BCItems;
import com.thepigcat.buildcraft.tags.BCTags;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class BCRecipeProvider extends net.minecraft.data.recipes.RecipeProvider {
    public BCRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput recipeOutput) {
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, BCItems.WRENCH)
                .pattern("I I")
                .pattern(" G ")
                .pattern(" I ")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('G', BCTags.Items.STONE_GEAR)
                .unlockedBy("has_stone_gear", has(BCTags.Items.STONE_GEAR))
                .save(recipeOutput);

        gearRecipe(recipeOutput, ItemTags.PLANKS, null, BCItems.WOODEN_GEAR);
        gearRecipe(recipeOutput, Tags.Items.COBBLESTONES, BCTags.Items.WOODEN_GEAR, BCItems.STONE_GEAR);
        gearRecipe(recipeOutput, Tags.Items.INGOTS_IRON, BCTags.Items.STONE_GEAR, BCItems.IRON_GEAR);
        gearRecipe(recipeOutput, Tags.Items.INGOTS_GOLD, BCTags.Items.IRON_GEAR, BCItems.GOLD_GEAR);
        gearRecipe(recipeOutput, Tags.Items.GEMS_DIAMOND, BCTags.Items.GOLD_GEAR, BCItems.DIAMOND_GEAR);

        // ── Item Pipe Recipes (M G M → 8 pipes) ───────────────────────────
        pipeRecipe(recipeOutput, "wooden",      ItemTags.PLANKS);
        pipeRecipe(recipeOutput, "cobblestone", Tags.Items.COBBLESTONES);
        pipeRecipe(recipeOutput, "stone",       Blocks.STONE);
        pipeRecipe(recipeOutput, "sandstone",   Blocks.SANDSTONE);
        pipeRecipe(recipeOutput, "quartz",      Blocks.QUARTZ_BLOCK);
        pipeRecipe(recipeOutput, "iron",        Tags.Items.INGOTS_IRON);
        pipeRecipe(recipeOutput, "gold",        Tags.Items.INGOTS_GOLD);
        pipeRecipe(recipeOutput, "diamond",     Tags.Items.GEMS_DIAMOND);
        pipeRecipe(recipeOutput, "obsidian",    Tags.Items.OBSIDIANS);
        pipeRecipe(recipeOutput, "clay",        Blocks.CLAY);
        pipeRecipe(recipeOutput, "lapis",       Tags.Items.STORAGE_BLOCKS_LAPIS);
        pipeRecipe(recipeOutput, "stripe",      BCTags.Items.GOLD_GEAR);
        pipeRecipe(recipeOutput, "emerald",     Tags.Items.GEMS_EMERALD);

        // Void pipe: black dye + glass + redstone dust
        Item voidPipe = BuiltInRegistries.ITEM.get(BuildcraftLegacy.rl("void_pipe"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, voidPipe, 8)
                .pattern("LGR")
                .define('L', Items.BLACK_DYE)
                .define('G', Tags.Items.GLASS_BLOCKS)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_glass", has(Tags.Items.GLASS_BLOCKS))
                .save(recipeOutput);

        // Daizuli pipe: lapis block + glass + diamond
        Item daizuliPipe = BuiltInRegistries.ITEM.get(BuildcraftLegacy.rl("daizuli_pipe"));
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, daizuliPipe, 8)
                .pattern("LGR")
                .define('L', Tags.Items.STORAGE_BLOCKS_LAPIS)
                .define('G', Tags.Items.GLASS_BLOCKS)
                .define('R', Tags.Items.GEMS_DIAMOND)
                .unlockedBy("has_glass", has(Tags.Items.GLASS_BLOCKS))
                .save(recipeOutput);

        // ── Kinesis Pipe Recipes (item pipe + redstone → 1 kinesis pipe) ──
        kinesisPipeRecipe(recipeOutput, "wooden");
        kinesisPipeRecipe(recipeOutput, "cobblestone");
        kinesisPipeRecipe(recipeOutput, "stone");
        kinesisPipeRecipe(recipeOutput, "quartz");
        kinesisPipeRecipe(recipeOutput, "gold");
        kinesisPipeRecipe(recipeOutput, "diamond");

        // ── Fluid Pipe Recipes (item pipe + slimeball → 1 fluid pipe) ─────
        fluidPipeRecipe(recipeOutput, "wooden");
        fluidPipeRecipe(recipeOutput, "cobblestone");
        fluidPipeRecipe(recipeOutput, "stone");
        fluidPipeRecipe(recipeOutput, "quartz");
        fluidPipeRecipe(recipeOutput, "iron");
        fluidPipeRecipe(recipeOutput, "gold");
        fluidPipeRecipe(recipeOutput, "sandstone");
        fluidPipeRecipe(recipeOutput, "void");
        fluidPipeRecipe(recipeOutput, "clay");
        fluidPipeRecipe(recipeOutput, "diamond");

        engineRecipe(recipeOutput, ItemTags.PLANKS, BCTags.Items.WOODEN_GEAR, BCBlocks.REDSTONE_ENGINE);
        engineRecipe(recipeOutput, Tags.Items.COBBLESTONES, BCTags.Items.STONE_GEAR, BCBlocks.STIRLING_ENGINE);
        engineRecipe(recipeOutput, Tags.Items.INGOTS_IRON, BCTags.Items.IRON_GEAR, BCBlocks.COMBUSTION_ENGINE);

        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, BCBlocks.CRATE)
                .pattern("LSL")
                .pattern("L L")
                .pattern("LSL")
                .define('L', ItemTags.LOGS)
                .define('S', ItemTags.WOODEN_SLABS)
                .unlockedBy("has_log", has(ItemTags.LOGS))
                .save(recipeOutput);

        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, BCBlocks.TANK)
                .pattern("GGG")
                .pattern("G G")
                .pattern("GGG")
                .define('G', Tags.Items.GLASS_BLOCKS)
                .unlockedBy("has_glass", has(Tags.Items.GLASS_BLOCKS))
                .save(recipeOutput);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, BCBlocks.TANK)
                .requires(BCBlocks.TANK)
                .unlockedBy("has_tank", has(BCBlocks.TANK))
                .save(recipeOutput, BuildcraftLegacy.rl("tank_reset"));

        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, BCBlocks.QUARRY)
                .pattern("DGD")
                .pattern("GEG")
                .pattern("DGD")
                .define('D', BCTags.Items.DIAMOND_GEAR)
                .define('G', BCTags.Items.GOLD_GEAR)
                .define('E', BCBlocks.COMBUSTION_ENGINE)
                .unlockedBy("has_diamond_gear", has(BCTags.Items.DIAMOND_GEAR))
                .save(recipeOutput);

        // ── Silicon ────────────────────────────────────────────────────────
        // Laser
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, BCBlocks.LASER)
                .pattern("IRI")
                .pattern("IGI")
                .pattern("IRI")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('R', Items.REDSTONE)
                .define('G', Tags.Items.INGOTS_GOLD)
                .unlockedBy("has_redstone", has(Items.REDSTONE))
                .save(recipeOutput);

        // Assembly Table
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, BCBlocks.ASSEMBLY_TABLE)
                .pattern("IPI")
                .pattern("RLR")
                .pattern("IPI")
                .define('I', Tags.Items.INGOTS_IRON)
                .define('P', Blocks.PISTON)
                .define('R', Items.REDSTONE)
                .define('L', Items.LAPIS_LAZULI)
                .unlockedBy("has_iron", has(Tags.Items.INGOTS_IRON))
                .save(recipeOutput);
    }

    public static Criterion<InventoryChangeTrigger.TriggerInstance> has(TagKey<Item> tag) {
        return RecipeProvider.has(tag);
    }

    private void pipeRecipe(RecipeOutput out, String id, TagKey<Item> material) {
        Item result = BuiltInRegistries.ITEM.get(BuildcraftLegacy.rl(id + "_pipe"));
        if (result == Items.AIR) return;
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result, 8)
                .pattern("MGM")
                .define('M', material)
                .define('G', Tags.Items.GLASS_BLOCKS)
                .unlockedBy("has_material", has(material))
                .save(out);
    }

    private void pipeRecipe(RecipeOutput out, String id, ItemLike material) {
        Item result = BuiltInRegistries.ITEM.get(BuildcraftLegacy.rl(id + "_pipe"));
        if (result == Items.AIR) return;
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result, 8)
                .pattern("MGM")
                .define('M', material)
                .define('G', Tags.Items.GLASS_BLOCKS)
                .unlockedBy("has_material", has(material))
                .save(out);
    }

    private void kinesisPipeRecipe(RecipeOutput out, String material) {
        Item from = BuiltInRegistries.ITEM.get(BuildcraftLegacy.rl(material + "_pipe"));
        Item to   = BuiltInRegistries.ITEM.get(BuildcraftLegacy.rl(material + "_kinesis_pipe"));
        if (from == Items.AIR || to == Items.AIR) return;
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, to)
                .requires(from)
                .requires(Items.REDSTONE)
                .unlockedBy("has_pipe", has(from))
                .save(out);
    }

    private void fluidPipeRecipe(RecipeOutput out, String material) {
        Item from = BuiltInRegistries.ITEM.get(BuildcraftLegacy.rl(material + "_pipe"));
        Item to   = BuiltInRegistries.ITEM.get(BuildcraftLegacy.rl(material + "_fluid_pipe"));
        if (from == Items.AIR || to == Items.AIR) return;
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, to)
                .requires(from)
                .requires(Items.SLIME_BALL)
                .unlockedBy("has_pipe", has(from))
                .save(out);
    }

    private void engineRecipe(RecipeOutput recipeOutput, TagKey<Item> material, TagKey<Item> gear, ItemLike result) {
        String path = material.location().getPath();
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                .pattern("MMM")
                .pattern(" L ")
                .pattern("GPG")
                .define('M', material)
                .define('L', Tags.Items.GLASS_BLOCKS)
                .define('G', gear)
                .define('P', Items.PISTON)
                .unlockedBy("has_"+path, has(material))
                .save(recipeOutput);
    }

    private void gearRecipe(RecipeOutput recipeOutput, TagKey<Item> material, @Nullable TagKey<Item> previous, ItemLike result) {
        String path = material.location().getPath();
        if (previous != null) {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                    .pattern(" M ")
                    .pattern("MPM")
                    .pattern(" M ")
                    .define('M', material)
                    .define('P', previous)
                    .unlockedBy("has_" + path, has(material))
                    .save(recipeOutput);
        } else {
            ShapedRecipeBuilder.shaped(RecipeCategory.MISC, result)
                    .pattern(" M ")
                    .pattern("M M")
                    .pattern(" M ")
                    .define('M', material)
                    .unlockedBy("has_" + path, has(material))
                    .save(recipeOutput);
        }
    }
}
