package com.thepigcat.buildcraft.networking;

import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.content.blockentities.FluidPipeBE;
import com.thepigcat.buildcraft.util.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public record SyncFluidPipePayload(
        BlockPos pos,
        Optional<FluidStack> fluid,
        short[] amounts,
        byte[] directions
) implements CustomPacketPayload {

    public static final Type<SyncFluidPipePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BuildcraftLegacy.MODID, "sync_fluid_pipe"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncFluidPipePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public @NotNull SyncFluidPipePayload decode(RegistryFriendlyByteBuf buf) {
                    BlockPos pos = buf.readBlockPos();
                    boolean hasFluid = buf.readBoolean();
                    Optional<FluidStack> fluid = hasFluid
                            ? Optional.of(FluidStack.OPTIONAL_STREAM_CODEC.decode(buf))
                            : Optional.empty();
                    short[] amounts = new short[7];
                    byte[] dirs = new byte[7];
                    for (int i = 0; i < 7; i++) {
                        amounts[i] = buf.readShort();
                        dirs[i] = buf.readByte();
                    }
                    return new SyncFluidPipePayload(pos, fluid, amounts, dirs);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, SyncFluidPipePayload payload) {
                    buf.writeBlockPos(payload.pos());
                    buf.writeBoolean(payload.fluid().isPresent());
                    payload.fluid().ifPresent(f -> FluidStack.OPTIONAL_STREAM_CODEC.encode(buf, f));
                    for (int i = 0; i < 7; i++) {
                        buf.writeShort(payload.amounts()[i]);
                        buf.writeByte(payload.directions()[i]);
                    }
                }
            };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncFluidPipePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            FluidPipeBE be = BlockUtils.getBE(FluidPipeBE.class, context.player().level(), payload.pos());
            if (be != null) {
                be.handleFluidSync(payload);
            }
        });
    }
}
