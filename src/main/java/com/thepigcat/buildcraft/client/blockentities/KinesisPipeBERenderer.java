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
 *
 * Each arm extends from the block face to the block center (0.5), so adjacent
 * pipe blocks connect seamlessly with no z-fighting. No separate center cube.
 *
 * Radius scales linearly with a visible floor so the beam is always perceptible
 * once energy is present. Vertex color gives a heat-map: green → yellow → red.
 * UV scrolls along the arm to simulate energy flowing through the pipe.
 */
public class KinesisPipeBERenderer implements BlockEntityRenderer<KinesisPipeBE> {

    // 32x64 grayscale stripe texture — two identical tiles stacked so we can
    // scroll UV within [0, 0.5] without ever exceeding [0, 1].
    private static final ResourceLocation POWER_FLOW_TEXTURE =
            BuildcraftLegacy.rl("textures/block/power_flow_heat.png");

    // UV: each arm maps one tile (0→0.5 of the double-height texture).
    private static final float UV_TILE = 0.5f;

    private static final float MIN_RADIUS = 0.04f;
    private static final float MAX_RADIUS = 0.22f;
    private static final float SCROLL_SPEED = 0.04f;

    public KinesisPipeBERenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(KinesisPipeBE be, float partialTick, PoseStack ps,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        BlockState state = be.getBlockState();

        // Don't render if pipe has no energy at all
        float centerPower = be.getSectionPower(6, partialTick);
        if (centerPower <= 0.001f) return;

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(POWER_FLOW_TEXTURE));
        Matrix4f pose = ps.last().pose();
        int light = LightTexture.FULL_BRIGHT;

        float time = (be.getLevel() != null)
                ? (be.getLevel().getGameTime() + partialTick) * SCROLL_SPEED
                : 0f;

        for (Direction dir : Direction.values()) {
            PipeBlock.PipeState pipeState = state.getValue(PipeBlock.CONNECTION[dir.get3DDataValue()]);
            if (pipeState == PipeBlock.PipeState.NONE) continue;

            int d = dir.get3DDataValue();
            float armPower = be.getSectionPower(d, partialTick);
            if (armPower <= 0.001f) continue;

            float r = MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * armPower;
            int cr = heatR(armPower), cg = heatG(armPower);

            // Scroll toward or away from center based on flow direction
            float flowSign = be.getSectionFlowsOut(d) ? -1f : 1f;
            float raw = (time * flowSign) % UV_TILE;
            float uvo = raw < 0 ? raw + UV_TILE : raw; // uvOffset always in [0, UV_TILE)

            renderArm(pose, vc, dir, r, uvo, cr, cg, light, packedOverlay);
        }
    }

    /**
     * Renders the 4 side quads of a beam arm. The arm runs from the block face
     * (0 or 1 in the direction axis) to the block center (0.5). No end-caps —
     * the tube is open at both ends; ends are never visible from outside.
     */
    private static void renderArm(Matrix4f pose, VertexConsumer vc, Direction dir,
                                   float r, float uvo, int cr, int cg,
                                   int light, int overlay) {
        float lo = 0.5f - r; // cross-section lower bound (X or Z depending on arm)
        float hi = 0.5f + r; // cross-section upper bound
        float v0 = uvo;
        float v1 = uvo + UV_TILE;

        switch (dir) {
            case DOWN -> { // arm: y 0 → 0.5, cross: x and z in [lo, hi]
                // Z-faces (fixed z, varies x and y)
                quadZ(pose, vc, lo, hi, 0f, 0.5f, lo, 0, v0, 1, v1, cr, cg, light, overlay);
                quadZ(pose, vc, lo, hi, 0f, 0.5f, hi, 0, v0, 1, v1, cr, cg, light, overlay);
                // X-faces (fixed x, varies y and z)
                quadX(pose, vc, 0f, 0.5f, lo, hi, lo, 0, v0, 1, v1, cr, cg, light, overlay);
                quadX(pose, vc, 0f, 0.5f, lo, hi, hi, 0, v0, 1, v1, cr, cg, light, overlay);
            }
            case UP -> { // arm: y 0.5 → 1
                quadZ(pose, vc, lo, hi, 0.5f, 1f, lo, 0, v0, 1, v1, cr, cg, light, overlay);
                quadZ(pose, vc, lo, hi, 0.5f, 1f, hi, 0, v0, 1, v1, cr, cg, light, overlay);
                quadX(pose, vc, 0.5f, 1f, lo, hi, lo, 0, v0, 1, v1, cr, cg, light, overlay);
                quadX(pose, vc, 0.5f, 1f, lo, hi, hi, 0, v0, 1, v1, cr, cg, light, overlay);
            }
            case NORTH -> { // arm: z 0 → 0.5, cross: x and y in [lo, hi]
                // Y-faces (v scrolls along z)
                quadY(pose, vc, lo, hi, 0f, 0.5f, lo, 0, v0, 1, v1, cr, cg, light, overlay);
                quadY(pose, vc, lo, hi, 0f, 0.5f, hi, 0, v0, 1, v1, cr, cg, light, overlay);
                // X-faces: u maps to z (length), v maps to y (cross) → swap
                quadX(pose, vc, lo, hi, 0f, 0.5f, lo, v0, 0, v1, 1, cr, cg, light, overlay);
                quadX(pose, vc, lo, hi, 0f, 0.5f, hi, v0, 0, v1, 1, cr, cg, light, overlay);
            }
            case SOUTH -> { // arm: z 0.5 → 1
                quadY(pose, vc, lo, hi, 0.5f, 1f, lo, 0, v0, 1, v1, cr, cg, light, overlay);
                quadY(pose, vc, lo, hi, 0.5f, 1f, hi, 0, v0, 1, v1, cr, cg, light, overlay);
                quadX(pose, vc, lo, hi, 0.5f, 1f, lo, v0, 0, v1, 1, cr, cg, light, overlay);
                quadX(pose, vc, lo, hi, 0.5f, 1f, hi, v0, 0, v1, 1, cr, cg, light, overlay);
            }
            case WEST -> { // arm: x 0 → 0.5, cross: y and z in [lo, hi]
                // Y-faces: u maps to x (length), v maps to z (cross) → swap
                quadY(pose, vc, 0f, 0.5f, lo, hi, lo, v0, 0, v1, 1, cr, cg, light, overlay);
                quadY(pose, vc, 0f, 0.5f, lo, hi, hi, v0, 0, v1, 1, cr, cg, light, overlay);
                // Z-faces: u maps to x (length), v maps to y (cross) → swap
                quadZ(pose, vc, 0f, 0.5f, lo, hi, lo, v0, 0, v1, 1, cr, cg, light, overlay);
                quadZ(pose, vc, 0f, 0.5f, lo, hi, hi, v0, 0, v1, 1, cr, cg, light, overlay);
            }
            case EAST -> { // arm: x 0.5 → 1
                quadY(pose, vc, 0.5f, 1f, lo, hi, lo, v0, 0, v1, 1, cr, cg, light, overlay);
                quadY(pose, vc, 0.5f, 1f, lo, hi, hi, v0, 0, v1, 1, cr, cg, light, overlay);
                quadZ(pose, vc, 0.5f, 1f, lo, hi, lo, v0, 0, v1, 1, cr, cg, light, overlay);
                quadZ(pose, vc, 0.5f, 1f, lo, hi, hi, v0, 0, v1, 1, cr, cg, light, overlay);
            }
        }
    }

    // Heat-map: green (0) → yellow (0.5) → red (1)
    private static int heatR(float p) {
        return p >= 0.5f ? 255 : (int) (255 * p * 2f);
    }

    private static int heatG(float p) {
        return p <= 0.5f ? 255 : (int) (255 * (1f - (p - 0.5f) * 2f));
    }

    // ---- Quad helpers -------------------------------------------------------

    // Fixed X plane: varies Y (y0→y1) and Z (z0→z1)
    // Vertex order: u varies with z, v varies with y
    private static void quadX(Matrix4f m, VertexConsumer vc,
                               float y0, float y1, float z0, float z1, float x,
                               float u0, float v0, float u1, float v1,
                               int cr, int cg, int light, int overlay) {
        float nx = x > 0.5f ? 1 : -1;
        vc.addVertex(m, x, y0, z0).setColor(cr, cg, 0, 255).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(nx, 0, 0);
        vc.addVertex(m, x, y0, z1).setColor(cr, cg, 0, 255).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(nx, 0, 0);
        vc.addVertex(m, x, y1, z1).setColor(cr, cg, 0, 255).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(nx, 0, 0);
        vc.addVertex(m, x, y1, z0).setColor(cr, cg, 0, 255).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(nx, 0, 0);
    }

    // Fixed Y plane: varies X (x0→x1) and Z (z0→z1)
    // Vertex order: u varies with x, v varies with z
    private static void quadY(Matrix4f m, VertexConsumer vc,
                               float x0, float x1, float z0, float z1, float y,
                               float u0, float v0, float u1, float v1,
                               int cr, int cg, int light, int overlay) {
        float ny = y > 0.5f ? 1 : -1;
        vc.addVertex(m, x0, y, z0).setColor(cr, cg, 0, 255).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(0, ny, 0);
        vc.addVertex(m, x0, y, z1).setColor(cr, cg, 0, 255).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(0, ny, 0);
        vc.addVertex(m, x1, y, z1).setColor(cr, cg, 0, 255).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(0, ny, 0);
        vc.addVertex(m, x1, y, z0).setColor(cr, cg, 0, 255).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(0, ny, 0);
    }

    // Fixed Z plane: varies X (x0→x1) and Y (y0→y1)
    // Vertex order: u varies with x, v varies with y
    private static void quadZ(Matrix4f m, VertexConsumer vc,
                               float x0, float x1, float y0, float y1, float z,
                               float u0, float v0, float u1, float v1,
                               int cr, int cg, int light, int overlay) {
        float nz = z > 0.5f ? 1 : -1;
        vc.addVertex(m, x0, y0, z).setColor(cr, cg, 0, 255).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(0, 0, nz);
        vc.addVertex(m, x1, y0, z).setColor(cr, cg, 0, 255).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(0, 0, nz);
        vc.addVertex(m, x1, y1, z).setColor(cr, cg, 0, 255).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(0, 0, nz);
        vc.addVertex(m, x0, y1, z).setColor(cr, cg, 0, 255).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(0, 0, nz);
    }
}
