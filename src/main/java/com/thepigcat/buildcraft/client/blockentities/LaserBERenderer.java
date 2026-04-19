package com.thepigcat.buildcraft.client.blockentities;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thepigcat.buildcraft.content.blockentities.LaserBE;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class LaserBERenderer implements BlockEntityRenderer<LaserBE> {
    private static final float BEAM_R = 0.03f;

    public LaserBERenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(LaserBE be, float partialTick, PoseStack ps, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (be.targetPos == null || be.getLevel() == null) return;

        BlockPos origin = be.getBlockPos();
        float dx = be.targetPos.getX() - origin.getX();
        float dy = be.targetPos.getY() - origin.getY();
        float dz = be.targetPos.getZ() - origin.getZ();

        float time = (be.getLevel().getGameTime() + partialTick) * 0.08f;
        float pulse = (float)(0.5 + 0.5 * Math.sin(time));
        int g = (int)(100 + 155 * pulse);
        int r = (int)(30 * pulse);

        Vector3f dir = new Vector3f(dx, dy, dz).normalize();
        Vector3f up = Math.abs(dir.y) < 0.9f
                ? new Vector3f(0, 1, 0)
                : new Vector3f(1, 0, 0);
        Vector3f right = new Vector3f(dir).cross(up).normalize().mul(BEAM_R);
        Vector3f upPerp = new Vector3f(dir).cross(right).normalize().mul(BEAM_R);

        float fx = 0.5f, fy = 0.5f, fz = 0.5f;
        float tx = dx + 0.5f, ty = dy + 0.5f, tz = dz + 0.5f;

        VertexConsumer vc = buffers.getBuffer(RenderType.translucent());
        Matrix4f pose = ps.last().pose();

        drawBeamQuad(pose, vc, fx, fy, fz, tx, ty, tz, right.x, right.y, right.z, r, g, 200);
        drawBeamQuad(pose, vc, fx, fy, fz, tx, ty, tz, upPerp.x, upPerp.y, upPerp.z, r, g, 200);
    }

    private static void drawBeamQuad(Matrix4f pose, VertexConsumer vc,
                                     float fx, float fy, float fz,
                                     float tx, float ty, float tz,
                                     float ox, float oy, float oz,
                                     int r, int g, int a) {
        vc.addVertex(pose, fx + ox, fy + oy, fz + oz).setColor(r, g, 0, a).setUv(0, 0).setLight(0xF000F0);
        vc.addVertex(pose, fx - ox, fy - oy, fz - oz).setColor(r, g, 0, a).setUv(1, 0).setLight(0xF000F0);
        vc.addVertex(pose, tx - ox, ty - oy, tz - oz).setColor(r, g, 0, a).setUv(1, 1).setLight(0xF000F0);
        vc.addVertex(pose, tx + ox, ty + oy, tz + oz).setColor(r, g, 0, a).setUv(0, 1).setLight(0xF000F0);
    }

    @Override
    public boolean shouldRenderOffScreen(LaserBE be) {
        return true;
    }
}
