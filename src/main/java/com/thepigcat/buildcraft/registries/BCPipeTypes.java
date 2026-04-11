package com.thepigcat.buildcraft.registries;

import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.api.pipes.PipeTypeHolder;
import com.thepigcat.buildcraft.content.blocks.ClayItemPipeBlock;
import com.thepigcat.buildcraft.content.blocks.DaizuliItemPipeBlock;
import com.thepigcat.buildcraft.content.blocks.GoldItemPipeBlock;
import com.thepigcat.buildcraft.content.blocks.DiamondItemPipeBlock;
import com.thepigcat.buildcraft.content.blocks.EmeraldItemPipeBlock;
import com.thepigcat.buildcraft.content.blocks.ExtractingFluidPipeBlock;
import com.thepigcat.buildcraft.content.blocks.ExtractingItemPipeBlock;
import com.thepigcat.buildcraft.content.blocks.ExtractingKinesisPipeBlock;
import com.thepigcat.buildcraft.content.blocks.FluidPipeBlock;
import com.thepigcat.buildcraft.content.blocks.IronItemPipeBlock;
import com.thepigcat.buildcraft.content.blocks.ItemPipeBlock;
import com.thepigcat.buildcraft.content.blocks.KinesisPipeBlock;
import com.thepigcat.buildcraft.content.blocks.LapisItemPipeBlock;
import com.thepigcat.buildcraft.content.blocks.ObsidianItemPipeBlock;
import com.thepigcat.buildcraft.content.blocks.StripeItemPipeBlock;
import com.thepigcat.buildcraft.content.items.blocks.ItemPipeBlockItem;
import com.thepigcat.buildcraft.util.ModelUtils;
import com.thepigcat.buildcraft.util.PipeRegistrationHelper;

public final class BCPipeTypes {
    public static final PipeRegistrationHelper HELPER = new PipeRegistrationHelper(BuildcraftLegacy.MODID);

    public static final PipeTypeHolder<ItemPipeBlock, ItemPipeBlockItem> DEFAULT = HELPER.registerPipeType("default", ItemPipeBlock::new, ItemPipeBlockItem::new,
            ModelUtils.DEFAULT_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE, ModelUtils.DEFAULT_ITEM_MODEL_FILE,
            "base", "connection");
    public static final PipeTypeHolder<ExtractingItemPipeBlock, ItemPipeBlockItem> EXTRACTING = HELPER.registerPipeType("extracting", ExtractingItemPipeBlock::new, ItemPipeBlockItem::new,
            ModelUtils.EXTRACTING_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE, ModelUtils.DEFAULT_ITEM_MODEL_FILE,
            "base", "connection", "connection_extracting");

    public static final PipeTypeHolder<IronItemPipeBlock, ItemPipeBlockItem> IRON = HELPER.registerPipeType("iron", IronItemPipeBlock::new, ItemPipeBlockItem::new,
            ModelUtils.IRON_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE, ModelUtils.DEFAULT_ITEM_MODEL_FILE,
            "connection", "base_blocked", "connection_blocked");

    public static final PipeTypeHolder<ObsidianItemPipeBlock, ItemPipeBlockItem> OBSIDIAN_SUCTION = HELPER.registerPipeType("obsidian_suction", ObsidianItemPipeBlock::new, ItemPipeBlockItem::new,
            ModelUtils.DEFAULT_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE, ModelUtils.DEFAULT_ITEM_MODEL_FILE,
            "base", "connection");

    public static final PipeTypeHolder<ClayItemPipeBlock, ItemPipeBlockItem> CLAY = HELPER.registerPipeType("clay", ClayItemPipeBlock::new, ItemPipeBlockItem::new,
            ModelUtils.DEFAULT_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE, ModelUtils.DEFAULT_ITEM_MODEL_FILE,
            "base", "connection");

    public static final PipeTypeHolder<EmeraldItemPipeBlock, ItemPipeBlockItem> EMERALD = HELPER.registerPipeType("emerald", EmeraldItemPipeBlock::new, ItemPipeBlockItem::new,
            ModelUtils.EXTRACTING_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE, ModelUtils.DEFAULT_ITEM_MODEL_FILE,
            "base", "connection", "connection_extracting");

    public static final PipeTypeHolder<DiamondItemPipeBlock, ItemPipeBlockItem> DIAMOND = HELPER.registerPipeType("diamond", DiamondItemPipeBlock::new, ItemPipeBlockItem::new,
            ModelUtils.DIAMOND_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE, ModelUtils.DEFAULT_ITEM_MODEL_FILE,
            "base", "connection_down", "connection_up", "connection_north", "connection_south", "connection_west", "connection_east");
    public static final PipeTypeHolder<LapisItemPipeBlock, ItemPipeBlockItem> LAPIS = HELPER.registerPipeType("lapis", LapisItemPipeBlock::new, ItemPipeBlockItem::new,
            ModelUtils.DEFAULT_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE, ModelUtils.DEFAULT_ITEM_MODEL_FILE,
            "base", "connection");
    public static final PipeTypeHolder<DaizuliItemPipeBlock, ItemPipeBlockItem> DAIZULI = HELPER.registerPipeType("daizuli", DaizuliItemPipeBlock::new, ItemPipeBlockItem::new,
            ModelUtils.DEFAULT_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE, ModelUtils.DEFAULT_ITEM_MODEL_FILE,
            "base", "connection");

    public static final PipeTypeHolder<GoldItemPipeBlock, ItemPipeBlockItem> GOLD = HELPER.registerPipeType("gold", GoldItemPipeBlock::new, ItemPipeBlockItem::new,
            ModelUtils.DEFAULT_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE, ModelUtils.DEFAULT_ITEM_MODEL_FILE,
            "base", "connection");

    public static final PipeTypeHolder<StripeItemPipeBlock, ItemPipeBlockItem> STRIPE = HELPER.registerPipeType("stripe", StripeItemPipeBlock::new, ItemPipeBlockItem::new,
            ModelUtils.DEFAULT_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE, ModelUtils.DEFAULT_ITEM_MODEL_FILE,
            "base", "connection");

    // Kinesis (power) pipe types — energy visuals via KinesisPipeBERenderer
    public static final PipeTypeHolder<KinesisPipeBlock, ItemPipeBlockItem> KINESIS_DEFAULT = HELPER.registerPipeType("kinesis_default", KinesisPipeBlock::new, ItemPipeBlockItem::new,
            ModelUtils.DEFAULT_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE, ModelUtils.DEFAULT_ITEM_MODEL_FILE,
            "base", "connection");

    public static final PipeTypeHolder<ExtractingKinesisPipeBlock, ItemPipeBlockItem> KINESIS_EXTRACTING = HELPER.registerPipeType("kinesis_extracting", ExtractingKinesisPipeBlock::new, ItemPipeBlockItem::new,
            ModelUtils.EXTRACTING_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE, ModelUtils.DEFAULT_ITEM_MODEL_FILE,
            "base", "connection", "connection_extracting");

    // Fluid pipe types
    public static final PipeTypeHolder<FluidPipeBlock, ItemPipeBlockItem> FLUID_DEFAULT = HELPER.registerPipeType("fluid_default", FluidPipeBlock::new, ItemPipeBlockItem::new,
            ModelUtils.DEFAULT_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE, ModelUtils.DEFAULT_ITEM_MODEL_FILE,
            "base", "connection");

    public static final PipeTypeHolder<ExtractingFluidPipeBlock, ItemPipeBlockItem> FLUID_EXTRACTING = HELPER.registerPipeType("fluid_extracting", ExtractingFluidPipeBlock::new, ItemPipeBlockItem::new,
            ModelUtils.EXTRACTING_BLOCK_MODEL_DEFINITION, ModelUtils.DEFAULT_BLOCK_MODEL_FILE, ModelUtils.DEFAULT_ITEM_MODEL_FILE,
            "base", "connection", "connection_extracting");

    public static void init() {
    }
}
