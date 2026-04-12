package com.thepigcat.buildcraft.client.blockentities;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.content.blockentities.FluidPipeBE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;

/**
 * Renders fluid inside fluid pipes as 3D quads per section.
 * Uses the fluid's own texture and tint color (water, lava, oil, etc.).
 */
public class FluidPipeBERenderer implements BlockEntityRenderer<FluidPipeBE> {

    public FluidPipeBERenderer(BlockEntityRendererProvider.Context context) {
    }

    /**
     * Compute UV coordinates for a vertex at local position `(lx, ly, lz)`
     * using the 2 axes tangent to the given face normal. Offset shifts the
     * sampled position so the texture scrolls while geometry stays still.
     */
    private static void writeVertex(VertexConsumer vc, Matrix4f pose,
                                     float lx, float ly, float lz,
                                     Vec3 offset, TextureAtlasSprite sprite,
                                     Direction.Axis normalAxis,
                                     float r, float g, float b, float a,
                                     int light, int overlay,
                                     float nx, float ny, float nz) {
        double ax, ay;
        // Pick the two tangent axes (u-axis, v-axis) based on face normal
        switch (normalAxis) {
            case Y -> { ax = lx + offset.x; ay = lz + offset.z; }
            case X -> { ax = lz + offset.z; ay = ly + offset.y; }
            case Z -> { ax = lx + offset.x; ay = ly + offset.y; }
            default -> throw new IllegalStateException("Unexpected axis: " + normalAxis);
        }
        // frac handles negatives correctly: x - floor(x)
        double fu = ax - Math.floor(ax);
        double fv = ay - Math.floor(ay);
        float u = sprite.getU0() + (float) fu * (sprite.getU1() - sprite.getU0());
        float v = sprite.getV0() + (float) fv * (sprite.getV1() - sprite.getV0());
        vc.addVertex(pose, lx, ly, lz)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(nx, ny, nz);
    }

    @Override
    public void render(FluidPipeBE be, float partialTick, PoseStack ps,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        FluidStack fluid = be.getFluidForRender();
        if (fluid.isEmpty()) return;

        int capacity = be.getCapacity();
        if (capacity <= 0) return;

        IClientFluidTypeExtensions fluidExt = IClientFluidTypeExtensions.of(fluid.getFluid());
        ResourceLocation stillTex = fluidExt.getStillTexture(fluid);
        if (stillTex == null) return;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(stillTex);

        int tintColor = fluidExt.getTintColor(fluid);
        float r = ((tintColor >> 16) & 0xFF) / 255f;
        float g = ((tintColor >> 8) & 0xFF) / 255f;
        float b = (tintColor & 0xFF) / 255f;
        float a = ((tintColor >> 24) & 0xFF) / 255f;
        if (a == 0f) a = 1f;

        VertexConsumer vc = buffers.getBuffer(RenderType.translucent());
        Matrix4f pose = ps.last().pose();

        BlockState state = be.getBlockState();

        // Render center section (index 6)
        double centerAmount = be.getAmountForRender(6, partialTick);
        if (centerAmount > 0) {
            float fill = (float) Math.min(1.0, centerAmount / capacity);
            float halfSize = 0.24f * fill;
            float min = 0.5f - halfSize;
            float max = 0.5f + halfSize;
            // Render fill as height for center
            float height = 0.26f + (0.74f - 0.26f) * fill;
            Vec3 centerOffset = be.getOffsetForRender(6, partialTick);
            renderBox(pose, vc, sprite, 0.26f, 0.74f, 0.26f, height, 0.26f, 0.74f,
                    centerOffset, r, g, b, a, packedLight, packedOverlay);
        }

        // Render face sections (index 0-5)
        for (Direction dir : Direction.values()) {
            PipeBlock.PipeState pipeState = state.getValue(PipeBlock.CONNECTION[dir.get3DDataValue()]);
            if (pipeState == PipeBlock.PipeState.NONE) continue;

            double amount = be.getAmountForRender(dir.ordinal(), partialTick);
            if (amount <= 0) continue;

            float fill = (float) Math.min(1.0, amount / capacity);
            Vec3 faceOffset = be.getOffsetForRender(dir.ordinal(), partialTick);
            renderConnectionFluid(pose, vc, sprite, dir, fill, faceOffset, r, g, b, a, packedLight, packedOverlay);
        }
    }

    private void renderConnectionFluid(Matrix4f pose, VertexConsumer vc, TextureAtlasSprite sprite,
                                        Direction dir, float fill, Vec3 offset,
                                        float r, float g, float b, float a,
                                        int light, int overlay) {
        float pipeInner = 0.24f;

        switch (dir.getAxis()) {
            case X -> {
                float halfH = pipeInner * fill;
                float yMin = 0.5f - halfH;
                float yMax = 0.5f + halfH;
                float zMin = 0.5f - pipeInner;
                float zMax = 0.5f + pipeInner;
                if (dir == Direction.WEST) {
                    renderBox(pose, vc, sprite, 0f, 0.26f, yMin, yMax, zMin, zMax, offset, r, g, b, a, light, overlay);
                } else {
                    renderBox(pose, vc, sprite, 0.74f, 1f, yMin, yMax, zMin, zMax, offset, r, g, b, a, light, overlay);
                }
            }
            case Z -> {
                float halfH = pipeInner * fill;
                float yMin = 0.5f - halfH;
                float yMax = 0.5f + halfH;
                float xMin = 0.5f - pipeInner;
                float xMax = 0.5f + pipeInner;
                if (dir == Direction.NORTH) {
                    renderBox(pose, vc, sprite, xMin, xMax, yMin, yMax, 0f, 0.26f, offset, r, g, b, a, light, overlay);
                } else {
                    renderBox(pose, vc, sprite, xMin, xMax, yMin, yMax, 0.74f, 1f, offset, r, g, b, a, light, overlay);
                }
            }
            case Y -> {
                float radius = pipeInner * (float) Math.sqrt(fill);
                float xMin = 0.5f - radius;
                float xMax = 0.5f + radius;
                float zMin = 0.5f - radius;
                float zMax = 0.5f + radius;
                if (dir == Direction.DOWN) {
                    renderBox(pose, vc, sprite, xMin, xMax, 0f, 0.26f, zMin, zMax, offset, r, g, b, a, light, overlay);
                } else {
                    renderBox(pose, vc, sprite, xMin, xMax, 0.74f, 1f, zMin, zMax, offset, r, g, b, a, light, overlay);
                }
            }
        }
    }

    private void renderBox(Matrix4f pose, VertexConsumer vc, TextureAtlasSprite sprite,
                           float x0, float x1, float y0, float y1, float z0, float z1,
                           Vec3 offset,
                           float r, float g, float b, float a,
                           int light, int overlay) {
        // Down face (y = y0), normal 0,-1,0
        writeVertex(vc, pose, x0, y0, z0, offset, sprite, Direction.Axis.Y, r, g, b, a, light, overlay, 0, -1, 0);
        writeVertex(vc, pose, x1, y0, z0, offset, sprite, Direction.Axis.Y, r, g, b, a, light, overlay, 0, -1, 0);
        writeVertex(vc, pose, x1, y0, z1, offset, sprite, Direction.Axis.Y, r, g, b, a, light, overlay, 0, -1, 0);
        writeVertex(vc, pose, x0, y0, z1, offset, sprite, Direction.Axis.Y, r, g, b, a, light, overlay, 0, -1, 0);

        // Up face (y = y1), normal 0,+1,0
        writeVertex(vc, pose, x0, y1, z1, offset, sprite, Direction.Axis.Y, r, g, b, a, light, overlay, 0, 1, 0);
        writeVertex(vc, pose, x1, y1, z1, offset, sprite, Direction.Axis.Y, r, g, b, a, light, overlay, 0, 1, 0);
        writeVertex(vc, pose, x1, y1, z0, offset, sprite, Direction.Axis.Y, r, g, b, a, light, overlay, 0, 1, 0);
        writeVertex(vc, pose, x0, y1, z0, offset, sprite, Direction.Axis.Y, r, g, b, a, light, overlay, 0, 1, 0);

        // North face (z = z0), normal 0,0,-1
        writeVertex(vc, pose, x0, y0, z0, offset, sprite, Direction.Axis.Z, r, g, b, a, light, overlay, 0, 0, -1);
        writeVertex(vc, pose, x0, y1, z0, offset, sprite, Direction.Axis.Z, r, g, b, a, light, overlay, 0, 0, -1);
        writeVertex(vc, pose, x1, y1, z0, offset, sprite, Direction.Axis.Z, r, g, b, a, light, overlay, 0, 0, -1);
        writeVertex(vc, pose, x1, y0, z0, offset, sprite, Direction.Axis.Z, r, g, b, a, light, overlay, 0, 0, -1);

        // South face (z = z1), normal 0,0,+1
        writeVertex(vc, pose, x1, y0, z1, offset, sprite, Direction.Axis.Z, r, g, b, a, light, overlay, 0, 0, 1);
        writeVertex(vc, pose, x1, y1, z1, offset, sprite, Direction.Axis.Z, r, g, b, a, light, overlay, 0, 0, 1);
        writeVertex(vc, pose, x0, y1, z1, offset, sprite, Direction.Axis.Z, r, g, b, a, light, overlay, 0, 0, 1);
        writeVertex(vc, pose, x0, y0, z1, offset, sprite, Direction.Axis.Z, r, g, b, a, light, overlay, 0, 0, 1);

        // West face (x = x0), normal -1,0,0
        writeVertex(vc, pose, x0, y0, z1, offset, sprite, Direction.Axis.X, r, g, b, a, light, overlay, -1, 0, 0);
        writeVertex(vc, pose, x0, y1, z1, offset, sprite, Direction.Axis.X, r, g, b, a, light, overlay, -1, 0, 0);
        writeVertex(vc, pose, x0, y1, z0, offset, sprite, Direction.Axis.X, r, g, b, a, light, overlay, -1, 0, 0);
        writeVertex(vc, pose, x0, y0, z0, offset, sprite, Direction.Axis.X, r, g, b, a, light, overlay, -1, 0, 0);

        // East face (x = x1), normal +1,0,0
        writeVertex(vc, pose, x1, y0, z0, offset, sprite, Direction.Axis.X, r, g, b, a, light, overlay, 1, 0, 0);
        writeVertex(vc, pose, x1, y1, z0, offset, sprite, Direction.Axis.X, r, g, b, a, light, overlay, 1, 0, 0);
        writeVertex(vc, pose, x1, y1, z1, offset, sprite, Direction.Axis.X, r, g, b, a, light, overlay, 1, 0, 0);
        writeVertex(vc, pose, x1, y0, z1, offset, sprite, Direction.Axis.X, r, g, b, a, light, overlay, 1, 0, 0);
    }
}
