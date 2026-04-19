package com.thepigcat.buildcraft.networking;

import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.content.blockentities.AssemblyTableBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetRecipeStatePayload(BlockPos pos, ResourceLocation recipeId) implements CustomPacketPayload {
    public static final Type<SetRecipeStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BuildcraftLegacy.MODID, "set_recipe_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetRecipeStatePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeBlockPos(p.pos());
                        buf.writeResourceLocation(p.recipeId());
                    },
                    buf -> new SetRecipeStatePayload(buf.readBlockPos(), buf.readResourceLocation())
            );

    public static void handle(SetRecipeStatePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            if (player.level().getBlockEntity(payload.pos()) instanceof AssemblyTableBE be) {
                if (player.distanceToSqr(payload.pos().getCenter()) <= 64.0) {
                    be.toggleSaved(payload.recipeId());
                    player.level().sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
