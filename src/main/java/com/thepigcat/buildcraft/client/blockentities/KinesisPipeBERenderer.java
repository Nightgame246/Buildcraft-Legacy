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
 * Renders the inner energy stripe for kinesis (power) pipes.
 * Matches the original BuildCraft 1.12 approach: scrolling UV texture,
 * radius scales with energy, full brightness, direction-aware flow.
 */
public class KinesisPipeBERenderer implements BlockEntityRenderer<KinesisPipeBE> {
    private static final ResourceLocation POWER_FLOW_TEXTURE =
            BuildcraftLegacy.rl("textures/block/power_flow.png");

    // Max beam radius — original BC used 0.248, slightly smaller to stay inside pipe
    private static final float MAX_RADIUS = 0.22f;

    // UV scroll speed per tick
    private static final float SCROLL_SPEED = 0.05f;

    public KinesisPipeBERenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(KinesisPipeBE be, float partialTick, PoseStack ps, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        // Use smoothed display power from the BE (client-side interpolated)
        float power = be.getDisplayPower(partialTick);
        if (power <= 0.001f) return;

        // Continuous radius with sqrt curve (original BC used sqrt for visual emphasis)
        float radius = MAX_RADIUS * (float) Math.sqrt(power);

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(POWER_FLOW_TEXTURE));
        Matrix4f pose = ps.last().pose();
        // Full brightness — energy beams glow like original BC
        int light = LightTexture.FULL_BRIGHT;

        // Animated UV base offset
        float time = 0;
        if (be.getLevel() != null) {
            time = (be.getLevel().getGameTime() + partialTick) * SCROLL_SPEED;
        }

        BlockState state = be.getBlockState();

        // Center cube — static UVs (no scroll)
        renderCenterCube(pose, vc, radius, light, packedOverlay);

        // Connection strips — direction-aware UV scroll
        for (Direction dir : Direction.values()) {
            PipeBlock.PipeState pipeState = state.getValue(PipeBlock.CONNECTION[dir.get3DDataValue()]);
            if (pipeState != PipeBlock.PipeState.NONE) {
                // Scroll direction matches axis: positive dirs flow outward, negative dirs flow inward
                float dirScroll = time * dir.getAxisDirection().getStep();
                float uvOffset = dirScroll % 1.0f;
                renderConnectionStrip(pose, vc, dir, radius, uvOffset, light, packedOverlay);
            }
        }
    }

    private void renderCenterCube(Matrix4f pose, VertexConsumer vc, float r, int light, int overlay) {
        float min = 0.5f - r;
        float max = 0.5f + r;

        // 6 faces of the center cube
        quadY(pose, vc, min, max, min, max, min, 0, 0, 1, 1, light, overlay);  // Down
        quadY(pose, vc, min, max, min, max, max, 0, 0, 1, 1, light, overlay);  // Up
        quadZ(pose, vc, min, max, min, max, min, 0, 0, 1, 1, light, overlay);  // North
        quadZ(pose, vc, min, max, min, max, max, 0, 0, 1, 1, light, overlay);  // South
        quadX(pose, vc, min, max, min, max, min, 0, 0, 1, 1, light, overlay);  // West
        quadX(pose, vc, min, max, min, max, max, 0, 0, 1, 1, light, overlay);  // East
    }

    private void renderConnectionStrip(Matrix4f pose, VertexConsumer vc,
                                        Direction dir, float r, float uvOffset,
                                        int light, int overlay) {
        float cMin = 0.5f - r;
        float cMax = 0.5f + r;

        // UV scrolls along the connection length
        float v0 = uvOffset;
        float v1 = uvOffset + 1.0f;

        switch (dir) {
            case DOWN -> { // Y: 0 -> cMin
                quadZ(pose, vc, cMin, cMax, 0, cMin, cMin, 0, v0, 1, v1, light, overlay);
                quadZ(pose, vc, cMin, cMax, 0, cMin, cMax, 0, v0, 1, v1, light, overlay);
                quadX(pose, vc, 0, cMin, cMin, cMax, cMin, 0, v0, 1, v1, light, overlay);
                quadX(pose, vc, 0, cMin, cMin, cMax, cMax, 0, v0, 1, v1, light, overlay);
            }
            case UP -> { // Y: cMax -> 1
                quadZ(pose, vc, cMin, cMax, cMax, 1, cMin, 0, v0, 1, v1, light, overlay);
                quadZ(pose, vc, cMin, cMax, cMax, 1, cMax, 0, v0, 1, v1, light, overlay);
                quadX(pose, vc, cMax, 1, cMin, cMax, cMin, 0, v0, 1, v1, light, overlay);
                quadX(pose, vc, cMax, 1, cMin, cMax, cMax, 0, v0, 1, v1, light, overlay);
            }
            case NORTH -> { // Z: 0 -> cMin
                quadY(pose, vc, cMin, cMax, 0, cMin, cMin, 0, v0, 1, v1, light, overlay);
                quadY(pose, vc, cMin, cMax, 0, cMin, cMax, 0, v0, 1, v1, light, overlay);
                quadX(pose, vc, cMin, cMax, 0, cMin, cMin, 0, v0, 1, v1, light, overlay);
                quadX(pose, vc, cMin, cMax, 0, cMin, cMax, 0, v0, 1, v1, light, overlay);
            }
            case SOUTH -> { // Z: cMax -> 1
                quadY(pose, vc, cMin, cMax, cMax, 1, cMin, 0, v0, 1, v1, light, overlay);
                quadY(pose, vc, cMin, cMax, cMax, 1, cMax, 0, v0, 1, v1, light, overlay);
                quadX(pose, vc, cMin, cMax, cMax, 1, cMin, 0, v0, 1, v1, light, overlay);
                quadX(pose, vc, cMin, cMax, cMax, 1, cMax, 0, v0, 1, v1, light, overlay);
            }
            case WEST -> { // X: 0 -> cMin
                quadY(pose, vc, 0, cMin, cMin, cMax, cMin, 0, v0, 1, v1, light, overlay);
                quadY(pose, vc, 0, cMin, cMin, cMax, cMax, 0, v0, 1, v1, light, overlay);
                quadZ(pose, vc, 0, cMin, cMin, cMax, cMin, 0, v0, 1, v1, light, overlay);
                quadZ(pose, vc, 0, cMin, cMin, cMax, cMax, 0, v0, 1, v1, light, overlay);
            }
            case EAST -> { // X: cMax -> 1
                quadY(pose, vc, cMax, 1, cMin, cMax, cMin, 0, v0, 1, v1, light, overlay);
                quadY(pose, vc, cMax, 1, cMin, cMax, cMax, 0, v0, 1, v1, light, overlay);
                quadZ(pose, vc, cMax, 1, cMin, cMax, cMin, 0, v0, 1, v1, light, overlay);
                quadZ(pose, vc, cMax, 1, cMin, cMax, cMax, 0, v0, 1, v1, light, overlay);
            }
        }
    }

    // Quad on X-plane (fixed X, varies Y and Z)
    private static void quadX(Matrix4f pose, VertexConsumer vc,
                               float y0, float y1, float z0, float z1, float x,
                               float u0, float v0, float u1, float v1,
                               int light, int overlay) {
        float nx = x > 0.5f ? 1 : -1;
        vc.addVertex(pose, x, y0, z0).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(nx, 0, 0);
        vc.addVertex(pose, x, y0, z1).setColor(255, 255, 255, 255).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(nx, 0, 0);
        vc.addVertex(pose, x, y1, z1).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(nx, 0, 0);
        vc.addVertex(pose, x, y1, z0).setColor(255, 255, 255, 255).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(nx, 0, 0);
    }

    // Quad on Y-plane (fixed Y, varies X and Z)
    private static void quadY(Matrix4f pose, VertexConsumer vc,
                               float x0, float x1, float z0, float z1, float y,
                               float u0, float v0, float u1, float v1,
                               int light, int overlay) {
        float ny = y > 0.5f ? 1 : -1;
        vc.addVertex(pose, x0, y, z0).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(0, ny, 0);
        vc.addVertex(pose, x0, y, z1).setColor(255, 255, 255, 255).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(0, ny, 0);
        vc.addVertex(pose, x1, y, z1).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(0, ny, 0);
        vc.addVertex(pose, x1, y, z0).setColor(255, 255, 255, 255).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(0, ny, 0);
    }

    // Quad on Z-plane (fixed Z, varies X and Y)
    private static void quadZ(Matrix4f pose, VertexConsumer vc,
                               float x0, float x1, float y0, float y1, float z,
                               float u0, float v0, float u1, float v1,
                               int light, int overlay) {
        float nz = z > 0.5f ? 1 : -1;
        vc.addVertex(pose, x0, y0, z).setColor(255, 255, 255, 255).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(0, 0, nz);
        vc.addVertex(pose, x1, y0, z).setColor(255, 255, 255, 255).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(0, 0, nz);
        vc.addVertex(pose, x1, y1, z).setColor(255, 255, 255, 255).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(0, 0, nz);
        vc.addVertex(pose, x0, y1, z).setColor(255, 255, 255, 255).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(0, 0, nz);
    }
}
