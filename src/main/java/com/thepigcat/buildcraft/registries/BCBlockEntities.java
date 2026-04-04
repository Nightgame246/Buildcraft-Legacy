package com.thepigcat.buildcraft.registries;

import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.content.blockentities.*;
import com.thepigcat.buildcraft.content.blocks.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class BCBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, BuildcraftLegacy.MODID);

    public static final Supplier<BlockEntityType<ItemPipeBE>> ITEM_PIPE = BLOCK_ENTITIES.register("item_pipe",
            () -> BlockEntityType.Builder.of(ItemPipeBE::new, collectBlocks(ItemPipeBlock.class)).build(null));

    public static final Supplier<BlockEntityType<ExtractItemPipeBE>> EXTRACTING_ITEM_PIPE = BLOCK_ENTITIES.register("extracting_item_pipe",
            () -> BlockEntityType.Builder.of(ExtractItemPipeBE::new, collectBlocks(ExtractingItemPipeBlock.class)).build(null));

    public static final Supplier<BlockEntityType<IronItemPipeBE>> IRON_ITEM_PIPE = BLOCK_ENTITIES.register("iron_item_pipe",
            () -> BlockEntityType.Builder.of(IronItemPipeBE::new, collectBlocks(IronItemPipeBlock.class)).build(null));

    public static final Supplier<BlockEntityType<ObsidianItemPipeBE>> OBSIDIAN_ITEM_PIPE = BLOCK_ENTITIES.register("obsidian_item_pipe",
            () -> BlockEntityType.Builder.of(ObsidianItemPipeBE::new, collectBlocks(ObsidianItemPipeBlock.class)).build(null));

    public static final Supplier<BlockEntityType<ClayItemPipeBE>> CLAY_ITEM_PIPE = BLOCK_ENTITIES.register("clay_item_pipe",
            () -> BlockEntityType.Builder.of(ClayItemPipeBE::new, collectBlocks(ClayItemPipeBlock.class)).build(null));

    public static final Supplier<BlockEntityType<EmeraldItemPipeBE>> EMERALD_ITEM_PIPE = BLOCK_ENTITIES.register("emerald_item_pipe",
            () -> BlockEntityType.Builder.of(EmeraldItemPipeBE::new, collectBlocks(EmeraldItemPipeBlock.class)).build(null));

    public static final Supplier<BlockEntityType<DiamondItemPipeBE>> DIAMOND_ITEM_PIPE = BLOCK_ENTITIES.register("diamond_item_pipe",
            () -> BlockEntityType.Builder.of(DiamondItemPipeBE::new, collectBlocks(DiamondItemPipeBlock.class)).build(null));
    public static final Supplier<BlockEntityType<LapisItemPipeBE>> LAPIS_ITEM_PIPE = BLOCK_ENTITIES.register("lapis_item_pipe",
            () -> BlockEntityType.Builder.of(LapisItemPipeBE::new, collectBlocks(LapisItemPipeBlock.class)).build(null));
    public static final Supplier<BlockEntityType<DaizuliItemPipeBE>> DAIZULI_ITEM_PIPE = BLOCK_ENTITIES.register("daizuli_item_pipe",
            () -> BlockEntityType.Builder.of(DaizuliItemPipeBE::new, collectBlocks(DaizuliItemPipeBlock.class)).build(null));

    public static final Supplier<BlockEntityType<StripeItemPipeBE>> STRIPE_ITEM_PIPE = BLOCK_ENTITIES.register("stripe_item_pipe",
            () -> BlockEntityType.Builder.of(StripeItemPipeBE::new, collectBlocks(StripeItemPipeBlock.class)).build(null));

    public static final Supplier<BlockEntityType<TankBE>> TANK = BLOCK_ENTITIES.register("tank",
            () -> BlockEntityType.Builder.of(TankBE::new, BCBlocks.TANK.get()).build(null));
    public static final Supplier<BlockEntityType<CrateBE>> CRATE = BLOCK_ENTITIES.register("crate",
            () -> BlockEntityType.Builder.of(CrateBE::new, BCBlocks.CRATE.get()).build(null));

    public static final Supplier<BlockEntityType<RedstoneEngineBE>> REDSTONE_ENGINE = BLOCK_ENTITIES.register("redstone_engine",
            () -> BlockEntityType.Builder.of(RedstoneEngineBE::new, BCBlocks.REDSTONE_ENGINE.get()).build(null));
    public static final Supplier<BlockEntityType<StirlingEngineBE>> STIRLING_ENGINE = BLOCK_ENTITIES.register("stirling_engine",
            () -> BlockEntityType.Builder.of(StirlingEngineBE::new, BCBlocks.STIRLING_ENGINE.get()).build(null));
    public static final Supplier<BlockEntityType<CombustionEngineBE>> COMBUSTION_ENGINE = BLOCK_ENTITIES.register("combustion_engine",
            () -> BlockEntityType.Builder.of(CombustionEngineBE::new, BCBlocks.COMBUSTION_ENGINE.get()).build(null));

    public static final Supplier<BlockEntityType<QuarryBE>> QUARRY = BLOCK_ENTITIES.register("quarry",
            () -> BlockEntityType.Builder.of(QuarryBE::new, BCBlocks.QUARRY.get()).build(null));

    private static Block[] collectBlocks(Class<? extends Block> clazz) {
        return BuiltInRegistries.BLOCK.stream().filter(clazz::isInstance).toList().toArray(Block[]::new);
    }
}
