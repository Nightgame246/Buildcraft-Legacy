package com.thepigcat.buildcraft.registries;

import com.mojang.datafixers.util.Either;
import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.api.pipes.PipeHolder;
import com.thepigcat.buildcraft.util.PipeRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;

import java.util.List;

public final class BCPipes {
    public static final PipeRegistrationHelper HELPER = new PipeRegistrationHelper(BuildcraftLegacy.MODID);

    public static final PipeHolder WOODEN = HELPER.registerPipe("wooden", BCPipeTypes.EXTRACTING, "Wooden Pipe", 0.25f, List.of(
            BuildcraftLegacy.rl("block/wooden_pipe"),
            BuildcraftLegacy.rl("block/wooden_pipe_extracting")
    ), Either.right(ResourceLocation.parse("oak_planks")), Ingredient.of(ItemTags.PLANKS), List.of(BlockTags.MINEABLE_WITH_AXE), 0);
    public static final PipeHolder COBBLESTONE = HELPER.registerPipe("cobblestone", BCPipeTypes.DEFAULT, "Cobblestone Pipe", 0.25f, List.of(
            BuildcraftLegacy.rl("block/cobblestone_pipe")
    ), Either.right(ResourceLocation.parse("cobblestone")), Ingredient.of(Blocks.COBBLESTONE), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 1);
    public static final PipeHolder STONE = HELPER.registerPipe("stone", BCPipeTypes.DEFAULT, "Stone Pipe", 0.25f, List.of(
            BuildcraftLegacy.rl("block/stone_pipe")
    ), Either.right(ResourceLocation.parse("stone")), Ingredient.of(Blocks.STONE), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 2);
    public static final PipeHolder SANDSTONE = HELPER.registerPipe("sandstone", BCPipeTypes.DEFAULT, "Sandstone Pipe", 0.25f, List.of(
            BuildcraftLegacy.rl("block/sandstone_pipe")
    ), Either.right(ResourceLocation.parse("sandstone")), Ingredient.of(Blocks.SANDSTONE), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 3);
    public static final PipeHolder GOLD = HELPER.registerPipe("gold", BCPipeTypes.GOLD, "Gold Pipe", 0.5f, List.of(
            BuildcraftLegacy.rl("block/gold_pipe")
    ), Either.right(ResourceLocation.parse("gold_block")), Ingredient.of(Tags.Items.INGOTS_GOLD), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 4);
    public static final PipeHolder QUARTZ = HELPER.registerPipe("quartz", BCPipeTypes.DEFAULT, "Quartz Pipe", 0.4f, List.of(
            BuildcraftLegacy.rl("block/quartz_pipe")
    ), Either.right(ResourceLocation.parse("quartz_block")), Ingredient.of(Tags.Items.GEMS_QUARTZ), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 5);
    public static final PipeHolder DIAMOND = HELPER.registerPipe("diamond", BCPipeTypes.DIAMOND, "Diamond Pipe", 0.5f, List.of(
            BuildcraftLegacy.rl("block/diamond_pipe"),       // 0 – base
            BuildcraftLegacy.rl("block/diamond_pipe_down"),  // 1 – DOWN  (black)
            BuildcraftLegacy.rl("block/diamond_pipe_up"),    // 2 – UP    (white)
            BuildcraftLegacy.rl("block/diamond_pipe_north"), // 3 – NORTH (red)
            BuildcraftLegacy.rl("block/diamond_pipe_south"), // 4 – SOUTH (blue)
            BuildcraftLegacy.rl("block/diamond_pipe_west"),  // 5 – WEST  (green)
            BuildcraftLegacy.rl("block/diamond_pipe_east")   // 6 – EAST  (yellow)
    ), Either.right(ResourceLocation.parse("diamond_block")), Ingredient.of(Tags.Items.GEMS_DIAMOND), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 6);
    public static final PipeHolder IRON = HELPER.registerPipe("iron", BCPipeTypes.IRON, "Iron Pipe", 0.25f, List.of(
            BuildcraftLegacy.rl("block/iron_pipe"),
            BuildcraftLegacy.rl("block/iron_pipe_blocked")
    ), Either.right(ResourceLocation.parse("iron_block")), Ingredient.of(Tags.Items.INGOTS_IRON), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 7);

    public static final PipeHolder OBSIDIAN = HELPER.registerPipe("obsidian", BCPipeTypes.OBSIDIAN_SUCTION, "Obsidian Pipe", 0.25f, List.of(
            BuildcraftLegacy.rl("block/obsidian_pipe")
    ), Either.right(ResourceLocation.parse("obsidian")), Ingredient.of(Tags.Items.OBSIDIANS), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 8);

    public static final PipeHolder CLAY = HELPER.registerPipe("clay", BCPipeTypes.CLAY, "Clay Pipe", 0.25f, List.of(
            BuildcraftLegacy.rl("block/clay_pipe")
    ), Either.right(ResourceLocation.parse("clay")), Ingredient.of(Items.CLAY_BALL), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 9);

    public static final PipeHolder VOID = HELPER.registerPipe("void", BCPipeTypes.DEFAULT, "Void Pipe", 0.25f, List.of(
            BuildcraftLegacy.rl("block/void_pipe")
    ), Either.right(ResourceLocation.parse("obsidian")), Ingredient.of(Tags.Items.OBSIDIANS), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 10);

    public static final PipeHolder EMERALD = HELPER.registerPipe("emerald", BCPipeTypes.EMERALD, "Emerald Pipe", 0.25f, List.of(
            BuildcraftLegacy.rl("block/emerald_pipe"),
            BuildcraftLegacy.rl("block/emerald_pipe_extracting")
    ), Either.right(ResourceLocation.parse("emerald_block")), Ingredient.of(Tags.Items.GEMS_EMERALD), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 11);

    public static final PipeHolder LAPIS = HELPER.registerPipe("lapis", BCPipeTypes.LAPIS, "Lapis Pipe", 0.25f, List.of(
            BuildcraftLegacy.rl("block/lapis_pipe")
    ), Either.right(ResourceLocation.parse("lapis_block")), Ingredient.of(Blocks.LAPIS_BLOCK), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 12);

    public static final PipeHolder DAIZULI = HELPER.registerPipe("daizuli", BCPipeTypes.DAIZULI, "Daizuli Pipe", 0.25f, List.of(
            BuildcraftLegacy.rl("block/daizuli_pipe")
    ), Either.right(ResourceLocation.parse("lapis_block")), Ingredient.of(Blocks.LAPIS_BLOCK), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 13);

    public static final PipeHolder STRIPE = HELPER.registerPipe("stripe", BCPipeTypes.STRIPE, "Stripe Pipe", 0.25f, List.of(
            BuildcraftLegacy.rl("block/stripe_pipe")
    ), Either.right(ResourceLocation.parse("gold_block")), Ingredient.of(Tags.Items.INGOTS_GOLD), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 14);

    // ── Kinesis (power) pipes ────────────────────────────────────────────
    public static final PipeHolder WOODEN_KINESIS = HELPER.registerPipe("wooden_kinesis", BCPipeTypes.KINESIS_EXTRACTING, "Wooden Kinesis Pipe", 0f, List.of(
            BuildcraftLegacy.rl("block/wooden_kinesis_pipe"),
            BuildcraftLegacy.rl("block/wooden_kinesis_pipe_extracting")
    ), Either.right(ResourceLocation.parse("oak_planks")), Ingredient.of(ItemTags.PLANKS), List.of(BlockTags.MINEABLE_WITH_AXE), 20);

    public static final PipeHolder COBBLESTONE_KINESIS = HELPER.registerPipe("cobblestone_kinesis", BCPipeTypes.KINESIS_DEFAULT, "Cobblestone Kinesis Pipe", 0f, List.of(
            BuildcraftLegacy.rl("block/cobblestone_kinesis_pipe")
    ), Either.right(ResourceLocation.parse("cobblestone")), Ingredient.of(Blocks.COBBLESTONE), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 21);

    public static final PipeHolder STONE_KINESIS = HELPER.registerPipe("stone_kinesis", BCPipeTypes.KINESIS_DEFAULT, "Stone Kinesis Pipe", 0f, List.of(
            BuildcraftLegacy.rl("block/stone_kinesis_pipe")
    ), Either.right(ResourceLocation.parse("stone")), Ingredient.of(Blocks.STONE), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 22);

    public static final PipeHolder GOLD_KINESIS = HELPER.registerPipe("gold_kinesis", BCPipeTypes.KINESIS_DEFAULT, "Gold Kinesis Pipe", 0f, List.of(
            BuildcraftLegacy.rl("block/gold_kinesis_pipe")
    ), Either.right(ResourceLocation.parse("gold_block")), Ingredient.of(Tags.Items.INGOTS_GOLD), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 23);

    public static final PipeHolder QUARTZ_KINESIS = HELPER.registerPipe("quartz_kinesis", BCPipeTypes.KINESIS_DEFAULT, "Quartz Kinesis Pipe", 0f, List.of(
            BuildcraftLegacy.rl("block/quartz_kinesis_pipe")
    ), Either.right(ResourceLocation.parse("quartz_block")), Ingredient.of(Tags.Items.GEMS_QUARTZ), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 24);

    public static final PipeHolder DIAMOND_KINESIS = HELPER.registerPipe("diamond_kinesis", BCPipeTypes.KINESIS_DEFAULT, "Diamond Kinesis Pipe", 0f, List.of(
            BuildcraftLegacy.rl("block/diamond_kinesis_pipe")
    ), Either.right(ResourceLocation.parse("diamond_block")), Ingredient.of(Tags.Items.GEMS_DIAMOND), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 25);

    // ── Fluid pipes ─────────────────────────────────────────────────────
    public static final PipeHolder WOODEN_FLUID = HELPER.registerPipe("wooden_fluid", BCPipeTypes.FLUID_EXTRACTING, "Wooden Fluid Pipe", 0f, List.of(
            BuildcraftLegacy.rl("block/wooden_fluid_pipe"),
            BuildcraftLegacy.rl("block/wooden_fluid_pipe_extracting")
    ), Either.right(ResourceLocation.parse("oak_planks")), Ingredient.of(ItemTags.PLANKS), List.of(BlockTags.MINEABLE_WITH_AXE), 30);

    public static final PipeHolder COBBLESTONE_FLUID = HELPER.registerPipe("cobblestone_fluid", BCPipeTypes.FLUID_DEFAULT, "Cobblestone Fluid Pipe", 0f, List.of(
            BuildcraftLegacy.rl("block/cobblestone_fluid_pipe")
    ), Either.right(ResourceLocation.parse("cobblestone")), Ingredient.of(Blocks.COBBLESTONE), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 31);

    public static final PipeHolder STONE_FLUID = HELPER.registerPipe("stone_fluid", BCPipeTypes.FLUID_DEFAULT, "Stone Fluid Pipe", 0f, List.of(
            BuildcraftLegacy.rl("block/stone_fluid_pipe")
    ), Either.right(ResourceLocation.parse("stone")), Ingredient.of(Blocks.STONE), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 32);

    public static final PipeHolder GOLD_FLUID = HELPER.registerPipe("gold_fluid", BCPipeTypes.FLUID_DEFAULT, "Gold Fluid Pipe", 0f, List.of(
            BuildcraftLegacy.rl("block/gold_fluid_pipe")
    ), Either.right(ResourceLocation.parse("gold_block")), Ingredient.of(Tags.Items.INGOTS_GOLD), List.of(BlockTags.MINEABLE_WITH_PICKAXE), 33);

    public static final PipeHolder SANDSTONE_FLUID = HELPER.registerPipe("sandstone_fluid", BCPipeTypes.FLUID_DEFAULT,
        "Sandstone Fluid Pipe", 0f, List.of(
            BuildcraftLegacy.rl("block/sandstone_fluid_pipe")
        ), Either.right(ResourceLocation.parse("sandstone")), Ingredient.of(Blocks.SANDSTONE),
        List.of(BlockTags.MINEABLE_WITH_PICKAXE), 34);

    public static final PipeHolder QUARTZ_FLUID = HELPER.registerPipe("quartz_fluid", BCPipeTypes.FLUID_DEFAULT,
        "Quartz Fluid Pipe", 0f, List.of(
            BuildcraftLegacy.rl("block/quartz_fluid_pipe")
        ), Either.right(ResourceLocation.parse("quartz_block")), Ingredient.of(Tags.Items.GEMS_QUARTZ),
        List.of(BlockTags.MINEABLE_WITH_PICKAXE), 35);

    public static final PipeHolder VOID_FLUID = HELPER.registerPipe("void_fluid", BCPipeTypes.FLUID_VOID,
        "Void Fluid Pipe", 0f, List.of(
            BuildcraftLegacy.rl("block/void_fluid_pipe")
        ), Either.right(ResourceLocation.parse("obsidian")), Ingredient.of(Tags.Items.OBSIDIANS),
        List.of(BlockTags.MINEABLE_WITH_PICKAXE), 36);

    public static final PipeHolder IRON_FLUID = HELPER.registerPipe("iron_fluid", BCPipeTypes.FLUID_IRON,
        "Iron Fluid Pipe", 0f, List.of(
            BuildcraftLegacy.rl("block/iron_fluid_pipe"),
            BuildcraftLegacy.rl("block/iron_fluid_pipe_blocked")
        ), Either.right(ResourceLocation.parse("iron_block")), Ingredient.of(Tags.Items.INGOTS_IRON),
        List.of(BlockTags.MINEABLE_WITH_PICKAXE), 37);

    public static final PipeHolder CLAY_FLUID = HELPER.registerPipe("clay_fluid", BCPipeTypes.FLUID_CLAY,
        "Clay Fluid Pipe", 0f, List.of(
            BuildcraftLegacy.rl("block/clay_fluid_pipe")
        ), Either.right(ResourceLocation.parse("clay")), Ingredient.of(Items.CLAY_BALL),
        List.of(BlockTags.MINEABLE_WITH_PICKAXE), 38);

    public static final PipeHolder DIAMOND_FLUID = HELPER.registerPipe("diamond_fluid", BCPipeTypes.FLUID_DIAMOND,
        "Diamond Fluid Pipe", 0f, List.of(
            BuildcraftLegacy.rl("block/diamond_fluid_pipe"),        // 0 – base
            BuildcraftLegacy.rl("block/diamond_fluid_pipe_down"),   // 1 – DOWN
            BuildcraftLegacy.rl("block/diamond_fluid_pipe_up"),     // 2 – UP
            BuildcraftLegacy.rl("block/diamond_fluid_pipe_north"),  // 3 – NORTH
            BuildcraftLegacy.rl("block/diamond_fluid_pipe_south"),  // 4 – SOUTH
            BuildcraftLegacy.rl("block/diamond_fluid_pipe_west"),   // 5 – WEST
            BuildcraftLegacy.rl("block/diamond_fluid_pipe_east")    // 6 – EAST
        ), Either.right(ResourceLocation.parse("diamond_block")), Ingredient.of(Tags.Items.GEMS_DIAMOND),
        List.of(BlockTags.MINEABLE_WITH_PICKAXE), 39);
}
