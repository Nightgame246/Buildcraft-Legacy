package com.thepigcat.buildcraft.data;

import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.data.components.BigStackContainerContents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class BCDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, BuildcraftLegacy.MODID);

    public static final Supplier<DataComponentType<BigStackContainerContents>> CRATE_CONTENT = DATA_COMPONENTS.register("crate_content",
            () -> DataComponentType.<BigStackContainerContents>builder().persistent(BigStackContainerContents.CODEC).networkSynchronized(BigStackContainerContents.STREAM_CODEC).build());
    public static final Supplier<DataComponentType<SimpleFluidContent>> TANK_CONTENT = DATA_COMPONENTS.register("tank_content",
            () -> DataComponentType.<SimpleFluidContent>builder().persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC).build());
    public static final Supplier<DataComponentType<DyeColor>> ITEM_COLOUR = DATA_COMPONENTS.register("item_colour",
            () -> DataComponentType.<DyeColor>builder().persistent(DyeColor.CODEC).networkSynchronized(ByteBufCodecs.idMapper(DyeColor::byId, DyeColor::getId)).build());
}
