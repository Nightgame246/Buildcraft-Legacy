package com.thepigcat.buildcraft.client.blockentities;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.api.blocks.PipeBlock;
import com.thepigcat.buildcraft.content.blockentities.KinesisPipeBE;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Renders the inner energy beam for kinesis pipes.
 * Linear radius (0.248 * power), heat-map color (green → yellow → red),
 * scrolling UV texture, full brightness, flow-direction-aware UV scroll.
 */
public class KinesisPipeBERenderer implements BlockEntityRenderer<KinesisPipeBE> {
    // Grayscale version of power_flow so vertex colors tint cleanly
    private static final ResourceLocation POWER_FLOW_TEXTURE =
            BuildcraftLegacy.rl("textures/block/power_flow_heat.png");

    private static final float MAX_RADIUS = 0.248f;
    private static final float SCROLL_SPEED = 0.05f;

    public KinesisPipeBERenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(KinesisPipeBE be, float partialTick, PoseStack ps, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        float centerPower = be.getSectionPower(6, partialTick);
        if (centerPower <= 0.001f) return;

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(POWER_FLOW_TEXTURE));
        Matrix4f pose = ps.last().pose();
        int light = LightTexture.FULL_BRIGHT;

        float time = 0;
        if (be.getLevel() != null) {
            time = (be.getLevel().getGameTime() + partialTick) * SCROLL_SPEED;
        }

        BlockState state = be.getBlockState();

        // Center cube — linear radius, heat-map tint
        float centerRadius = MAX_RADIUS * centerPower;
        renderCenterCube(pose, vc, centerRadius, centerPower, light, packedOverlay);

        // Connection arms
        for (Direction dir : Direction.values()) {
            PipeBlock.PipeState pipeState = state.getValue(PipeBlock.CONNECTION[dir.get3DDataValue()]);
            if (pipeState == PipeBlock.PipeState.NONE) continue;

            int d = dir.get3DDataValue();
            float armPower = be.getSectionPower(d, partialTick);
            if (armPower <= 0.001f) continue;

            float armRadius = MAX_RADIUS * armPower;
            float flowSign = be.getSectionFlowsOut(d) ? -1f : 1f;
            float raw = (time * flowSign) % 1.0f;
            float uvOffset = raw < 0 ? raw + 1.0f : raw;
            renderConnectionStrip(pose, vc, dir, armRadius, armPower, uvOffset, light, packedOverlay);
        }
    }

    // Red component: 0 at power=0, 255 at power>=0.5
    private static int heatR(float p) {
        return p >= 0.5f ? 255 : (int) (255 * p * 2f);
    }

    // Green component: 255 at power<=0.5, 0 at power=1
    private static int heatG(float p) {
        return p <= 0.5f ? 255 : (int) (255 * (1f - (p - 0.5f) * 2f));
    }

    private void renderCenterCube(Matrix4f pose, VertexConsumer vc, float r, float power, int light, int overlay) {
        float min = 0.5f - r;
        float max = 0.5f + r;
        int cr = heatR(power), cg = heatG(power);

        quadY(pose, vc, min, max, min, max, min, 0, 0, 1, 1, cr, cg, light, overlay);
        quadY(pose, vc, min, max, min, max, max, 0, 0, 1, 1, cr, cg, light, overlay);
        quadZ(pose, vc, min, max, min, max, min, 0, 0, 1, 1, cr, cg, light, overlay);
        quadZ(pose, vc, min, max, min, max, max, 0, 0, 1, 1, cr, cg, light, overlay);
        quadX(pose, vc, min, max, min, max, min, 0, 0, 1, 1, cr, cg, light, overlay);
        quadX(pose, vc, min, max, min, max, max, 0, 0, 1, 1, cr, cg, light, overlay);
    }

    private void renderConnectionStrip(Matrix4f pose, VertexConsumer vc,
                                        Direction dir, float r, float power, float uvOffset,
                                        int light, int overlay) {
        float cMin = 0.5f - r;
        float cMax = 0.5f + r;
        float v0 = uvOffset;
        float v1 = uvOffset + 1.0f;
        int cr = heatR(power), cg = heatG(power);

        switch (dir) {
            case DOWN -> {
                quadZ(pose, vc, cMin, cMax, 0, cMin, cMin, 0, v0, 1, v1, cr, cg, light, overlay);
                quadZ(pose, vc, cMin, cMax, 0, cMin, cMax, 0, v0, 1, v1, cr, cg, light, overlay);
                quadX(pose, vc, 0, cMin, cMin, cMax, cMin, 0, v0, 1, v1, cr, cg, light, overlay);
                quadX(pose, vc, 0, cMin, cMin, cMax, cMax, 0, v0, 1, v1, cr, cg, light, overlay);
            }
            case UP -> {
                quadZ(pose, vc, cMin, cMax, cMax, 1, cMin, 0, v0, 1, v1, cr, cg, light, overlay);
                quadZ(pose, vc, cMin, cMax, cMax, 1, cMax, 0, v0, 1, v1, cr, cg, light, overlay);
                quadX(pose, vc, cMax, 1, cMin, cMax, cMin, 0, v0, 1, v1, cr, cg, light, overlay);
                quadX(pose, vc, cMax, 1, cMin, cMax, cMax, 0, v0, 1, v1, cr, cg, light, overlay);
            }
            case NORTH -> {
                quadY(pose, vc, cMin, cMax, 0, cMin, cMin, 0, v0, 1, v1, cr, cg, light, overlay);
                quadY(pose, vc, cMin, cMax, 0, cMin, cMax, 0, v0, 1, v1, cr, cg, light, overlay);
                quadX(pose, vc, cMin, cMax, 0, cMin, cMin, 0, v0, 1, v1, cr, cg, light, overlay);
                quadX(pose, vc, cMin, cMax, 0, cMin, cMax, 0, v0, 1, v1, cr, cg, light, overlay);
            }
            case SOUTH -> {
                quadY(pose, vc, cMin, cMax, cMax, 1, cMin, 0, v0, 1, v1, cr, cg, light, overlay);
                quadY(pose, vc, cMin, cMax, cMax, 1, cMax, 0, v0, 1, v1, cr, cg, light, overlay);
                quadX(pose, vc, cMin, cMax, cMax, 1, cMin, 0, v0, 1, v1, cr, cg, light, overlay);
                quadX(pose, vc, cMin, cMax, cMax, 1, cMax, 0, v0, 1, v1, cr, cg, light, overlay);
            }
            case WEST -> {
                quadY(pose, vc, 0, cMin, cMin, cMax, cMin, 0, v0, 1, v1, cr, cg, light, overlay);
                quadY(pose, vc, 0, cMin, cMin, cMax, cMax, 0, v0, 1, v1, cr, cg, light, overlay);
                quadZ(pose, vc, 0, cMin, cMin, cMax, cMin, 0, v0, 1, v1, cr, cg, light, overlay);
                quadZ(pose, vc, 0, cMin, cMin, cMax, cMax, 0, v0, 1, v1, cr, cg, light, overlay);
            }
            case EAST -> {
                quadY(pose, vc, cMax, 1, cMin, cMax, cMin, 0, v0, 1, v1, cr, cg, light, overlay);
                quadY(pose, vc, cMax, 1, cMin, cMax, cMax, 0, v0, 1, v1, cr, cg, light, overlay);
                quadZ(pose, vc, cMax, 1, cMin, cMax, cMin, 0, v0, 1, v1, cr, cg, light, overlay);
                quadZ(pose, vc, cMax, 1, cMin, cMax, cMax, 0, v0, 1, v1, cr, cg, light, overlay);
            }
        }
    }

    private static void quadX(Matrix4f pose, VertexConsumer vc,
                               float y0, float y1, float z0, float z1, float x,
                               float u0, float v0, float u1, float v1,
                               int cr, int cg, int light, int overlay) {
        float nx = x > 0.5f ? 1 : -1;
        vc.addVertex(pose, x, y0, z0).setColor(cr, cg, 0, 255).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(nx, 0, 0);
        vc.addVertex(pose, x, y0, z1).setColor(cr, cg, 0, 255).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(nx, 0, 0);
        vc.addVertex(pose, x, y1, z1).setColor(cr, cg, 0, 255).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(nx, 0, 0);
        vc.addVertex(pose, x, y1, z0).setColor(cr, cg, 0, 255).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(nx, 0, 0);
    }

    private static void quadY(Matrix4f pose, VertexConsumer vc,
                               float x0, float x1, float z0, float z1, float y,
                               float u0, float v0, float u1, float v1,
                               int cr, int cg, int light, int overlay) {
        float ny = y > 0.5f ? 1 : -1;
        vc.addVertex(pose, x0, y, z0).setColor(cr, cg, 0, 255).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(0, ny, 0);
        vc.addVertex(pose, x0, y, z1).setColor(cr, cg, 0, 255).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(0, ny, 0);
        vc.addVertex(pose, x1, y, z1).setColor(cr, cg, 0, 255).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(0, ny, 0);
        vc.addVertex(pose, x1, y, z0).setColor(cr, cg, 0, 255).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(0, ny, 0);
    }

    private static void quadZ(Matrix4f pose, VertexConsumer vc,
                               float x0, float x1, float y0, float y1, float z,
                               float u0, float v0, float u1, float v1,
                               int cr, int cg, int light, int overlay) {
        float nz = z > 0.5f ? 1 : -1;
        vc.addVertex(pose, x0, y0, z).setColor(cr, cg, 0, 255).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(0, 0, nz);
        vc.addVertex(pose, x1, y0, z).setColor(cr, cg, 0, 255).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(0, 0, nz);
        vc.addVertex(pose, x1, y1, z).setColor(cr, cg, 0, 255).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(0, 0, nz);
        vc.addVertex(pose, x0, y1, z).setColor(cr, cg, 0, 255).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(0, 0, nz);
    }
}
