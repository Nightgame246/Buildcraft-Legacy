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
    public static final PipeHolder GOLD = HELPER.registerPipe("gold", BCPipeTypes.DEFAULT, "Gold Pipe", 0.5f, List.of(
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
}
