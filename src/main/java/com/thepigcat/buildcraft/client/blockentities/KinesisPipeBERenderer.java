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
 * Center cube + 4-sided arm per connected direction.
 * Center cube faces toward connected arms are skipped to avoid z-fighting.
 * Radius: linear with visible floor (MIN_RADIUS) so beam is always perceptible.
 * Color: heat-map (green → yellow → red) based on power level.
 * UV: double-height stripe texture, scroll within [0, 0.5] — never exceeds [0,1].
 */
public class KinesisPipeBERenderer implements BlockEntityRenderer<KinesisPipeBE> {

    // 32×64 grayscale stripe texture (sine-wave pattern, period 8px).
    // Double height so uvOffset [0,0.5] + tile 0.5 stays within [0,1].
    private static final ResourceLocation POWER_FLOW_TEXTURE =
            BuildcraftLegacy.rl("textures/block/power_flow_heat.png");

    private static final float UV_TILE   = 0.5f;  // one tile = half the texture height
    private static final float MIN_RADIUS = 0.04f;
    private static final float MAX_RADIUS = 0.22f;
    private static final float SCROLL_SPEED = 0.04f;

    public KinesisPipeBERenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(KinesisPipeBE be, float partialTick, PoseStack ps,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        float centerPower = be.getSectionPower(6, partialTick);
        if (centerPower <= 0.001f) return;

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(POWER_FLOW_TEXTURE));
        Matrix4f pose = ps.last().pose();
        int light = LightTexture.FULL_BRIGHT;

        float time = (be.getLevel() != null)
                ? (be.getLevel().getGameTime() + partialTick) * SCROLL_SPEED : 0f;

        BlockState state = be.getBlockState();

        // Center cube — skip faces that touch a connected arm (no z-fighting)
        float cr = MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * centerPower;
        renderCenterCube(pose, vc, cr, centerPower, state, light, packedOverlay);

        // Arms
        for (Direction dir : Direction.values()) {
            if (state.getValue(PipeBlock.CONNECTION[dir.get3DDataValue()]) == PipeBlock.PipeState.NONE) continue;

            int d = dir.get3DDataValue();
            float armPower = be.getSectionPower(d, partialTick);
            // Fallback to center power while per-arm data hasn't arrived from server yet
            if (armPower <= 0.001f) armPower = centerPower;

            float ar = MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * armPower;
            float flowSign = be.getSectionFlowsOut(d) ? -1f : 1f;
            float raw = (time * flowSign) % UV_TILE;
            float uvo = raw < 0 ? raw + UV_TILE : raw;
            renderArm(pose, vc, dir, ar, armPower, uvo, light, packedOverlay);
        }
    }

    // ---- Center cube -------------------------------------------------------

    private static void renderCenterCube(Matrix4f pose, VertexConsumer vc,
                                          float r, float power, BlockState state,
                                          int light, int overlay) {
        float min = 0.5f - r, max = 0.5f + r;
        int cr = heatR(power), cg = heatG(power);

        if (!hasPipe(state, Direction.DOWN))  quadY(pose, vc, min, max, min, max, min, 0,0,1,1, cr,cg,light,overlay);
        if (!hasPipe(state, Direction.UP))    quadY(pose, vc, min, max, min, max, max, 0,0,1,1, cr,cg,light,overlay);
        if (!hasPipe(state, Direction.NORTH)) quadZ(pose, vc, min, max, min, max, min, 0,0,1,1, cr,cg,light,overlay);
        if (!hasPipe(state, Direction.SOUTH)) quadZ(pose, vc, min, max, min, max, max, 0,0,1,1, cr,cg,light,overlay);
        if (!hasPipe(state, Direction.WEST))  quadX(pose, vc, min, max, min, max, min, 0,0,1,1, cr,cg,light,overlay);
        if (!hasPipe(state, Direction.EAST))  quadX(pose, vc, min, max, min, max, max, 0,0,1,1, cr,cg,light,overlay);
    }

    private static boolean hasPipe(BlockState state, Direction dir) {
        return state.getValue(PipeBlock.CONNECTION[dir.get3DDataValue()]) != PipeBlock.PipeState.NONE;
    }

    // ---- Arms --------------------------------------------------------------

    private static void renderArm(Matrix4f pose, VertexConsumer vc,
                                   Direction dir, float r, float power, float uvo,
                                   int light, int overlay) {
        float lo = 0.5f - r, hi = 0.5f + r;
        float v0 = uvo, v1 = uvo + UV_TILE;
        int cr = heatR(power), cg = heatG(power);

        switch (dir) {
            case DOWN ->  { // y: 0 → lo
                quadZ(pose, vc, lo, hi, 0,  lo, lo, 0,v0,1,v1, cr,cg,light,overlay);
                quadZ(pose, vc, lo, hi, 0,  lo, hi, 0,v0,1,v1, cr,cg,light,overlay);
                quadX(pose, vc, 0,  lo, lo, hi, lo, 0,v0,1,v1, cr,cg,light,overlay);
                quadX(pose, vc, 0,  lo, lo, hi, hi, 0,v0,1,v1, cr,cg,light,overlay);
            }
            case UP ->    { // y: hi → 1
                quadZ(pose, vc, lo, hi, hi, 1,  lo, 0,v0,1,v1, cr,cg,light,overlay);
                quadZ(pose, vc, lo, hi, hi, 1,  hi, 0,v0,1,v1, cr,cg,light,overlay);
                quadX(pose, vc, hi, 1,  lo, hi, lo, 0,v0,1,v1, cr,cg,light,overlay);
                quadX(pose, vc, hi, 1,  lo, hi, hi, 0,v0,1,v1, cr,cg,light,overlay);
            }
            case NORTH -> { // z: 0 → lo
                quadY(pose, vc, lo, hi, 0,  lo, lo, 0,v0,1,v1, cr,cg,light,overlay);
                quadY(pose, vc, lo, hi, 0,  lo, hi, 0,v0,1,v1, cr,cg,light,overlay);
                quadX(pose, vc, lo, hi, 0,  lo, lo, 0,v0,1,v1, cr,cg,light,overlay);
                quadX(pose, vc, lo, hi, 0,  lo, hi, 0,v0,1,v1, cr,cg,light,overlay);
            }
            case SOUTH -> { // z: hi → 1
                quadY(pose, vc, lo, hi, hi, 1,  lo, 0,v0,1,v1, cr,cg,light,overlay);
                quadY(pose, vc, lo, hi, hi, 1,  hi, 0,v0,1,v1, cr,cg,light,overlay);
                quadX(pose, vc, lo, hi, hi, 1,  lo, 0,v0,1,v1, cr,cg,light,overlay);
                quadX(pose, vc, lo, hi, hi, 1,  hi, 0,v0,1,v1, cr,cg,light,overlay);
            }
            case WEST ->  { // x: 0 → lo
                quadY(pose, vc, 0,  lo, lo, hi, lo, 0,v0,1,v1, cr,cg,light,overlay);
                quadY(pose, vc, 0,  lo, lo, hi, hi, 0,v0,1,v1, cr,cg,light,overlay);
                quadZ(pose, vc, 0,  lo, lo, hi, lo, 0,v0,1,v1, cr,cg,light,overlay);
                quadZ(pose, vc, 0,  lo, lo, hi, hi, 0,v0,1,v1, cr,cg,light,overlay);
            }
            case EAST ->  { // x: hi → 1
                quadY(pose, vc, hi, 1,  lo, hi, lo, 0,v0,1,v1, cr,cg,light,overlay);
                quadY(pose, vc, hi, 1,  lo, hi, hi, 0,v0,1,v1, cr,cg,light,overlay);
                quadZ(pose, vc, hi, 1,  lo, hi, lo, 0,v0,1,v1, cr,cg,light,overlay);
                quadZ(pose, vc, hi, 1,  lo, hi, hi, 0,v0,1,v1, cr,cg,light,overlay);
            }
        }
    }

    // ---- Color helpers -----------------------------------------------------

    private static int heatR(float p) {
        return p >= 0.5f ? 255 : (int)(255 * p * 2f);
    }

    private static int heatG(float p) {
        return p <= 0.5f ? 255 : (int)(255 * (1f - (p - 0.5f) * 2f));
    }

    // ---- Quad primitives ---------------------------------------------------

    // Fixed X: varies Y (y0→y1) and Z (z0→z1) — u~z, v~y
    private static void quadX(Matrix4f m, VertexConsumer vc,
                               float y0, float y1, float z0, float z1, float x,
                               float u0, float v0, float u1, float v1,
                               int cr, int cg, int light, int overlay) {
        float nx = x > 0.5f ? 1 : -1;
        vc.addVertex(m,x,y0,z0).setColor(cr,cg,0,255).setUv(u0,v0).setOverlay(overlay).setLight(light).setNormal(nx,0,0);
        vc.addVertex(m,x,y0,z1).setColor(cr,cg,0,255).setUv(u1,v0).setOverlay(overlay).setLight(light).setNormal(nx,0,0);
        vc.addVertex(m,x,y1,z1).setColor(cr,cg,0,255).setUv(u1,v1).setOverlay(overlay).setLight(light).setNormal(nx,0,0);
        vc.addVertex(m,x,y1,z0).setColor(cr,cg,0,255).setUv(u0,v1).setOverlay(overlay).setLight(light).setNormal(nx,0,0);
    }

    // Fixed Y: varies X (x0→x1) and Z (z0→z1) — u~x, v~z
    private static void quadY(Matrix4f m, VertexConsumer vc,
                               float x0, float x1, float z0, float z1, float y,
                               float u0, float v0, float u1, float v1,
                               int cr, int cg, int light, int overlay) {
        float ny = y > 0.5f ? 1 : -1;
        vc.addVertex(m,x0,y,z0).setColor(cr,cg,0,255).setUv(u0,v0).setOverlay(overlay).setLight(light).setNormal(0,ny,0);
        vc.addVertex(m,x0,y,z1).setColor(cr,cg,0,255).setUv(u0,v1).setOverlay(overlay).setLight(light).setNormal(0,ny,0);
        vc.addVertex(m,x1,y,z1).setColor(cr,cg,0,255).setUv(u1,v1).setOverlay(overlay).setLight(light).setNormal(0,ny,0);
        vc.addVertex(m,x1,y,z0).setColor(cr,cg,0,255).setUv(u1,v0).setOverlay(overlay).setLight(light).setNormal(0,ny,0);
    }

    // Fixed Z: varies X (x0→x1) and Y (y0→y1) — u~x, v~y
    private static void quadZ(Matrix4f m, VertexConsumer vc,
                               float x0, float x1, float y0, float y1, float z,
                               float u0, float v0, float u1, float v1,
                               int cr, int cg, int light, int overlay) {
        float nz = z > 0.5f ? 1 : -1;
        vc.addVertex(m,x0,y0,z).setColor(cr,cg,0,255).setUv(u0,v0).setOverlay(overlay).setLight(light).setNormal(0,0,nz);
        vc.addVertex(m,x1,y0,z).setColor(cr,cg,0,255).setUv(u1,v0).setOverlay(overlay).setLight(light).setNormal(0,0,nz);
        vc.addVertex(m,x1,y1,z).setColor(cr,cg,0,255).setUv(u1,v1).setOverlay(overlay).setLight(light).setNormal(0,0,nz);
        vc.addVertex(m,x0,y1,z).setColor(cr,cg,0,255).setUv(u0,v1).setOverlay(overlay).setLight(light).setNormal(0,0,nz);
    }
}
