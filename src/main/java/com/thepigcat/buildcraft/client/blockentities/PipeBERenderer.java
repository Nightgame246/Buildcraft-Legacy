package com.thepigcat.buildcraft.client.blockentities;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thepigcat.buildcraft.BuildcraftLegacy;
import com.thepigcat.buildcraft.content.blockentities.ColouredPipe;
import com.thepigcat.buildcraft.content.blockentities.ItemPipeBE;
import com.thepigcat.buildcraft.content.blockentities.LapisItemPipeBE;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class PipeBERenderer implements BlockEntityRenderer<ItemPipeBE> {
    public PipeBERenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ItemPipeBE pipeBlockEntity, float partialTicks, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int i1) {
        ItemStack stack = pipeBlockEntity.getItemHandler().getStackInSlot(0);

        Direction from = pipeBlockEntity.getFrom();
        Direction to = pipeBlockEntity.getTo();

        renderPipeColourOverlay(pipeBlockEntity, poseStack, multiBufferSource, i, i1);

        float v = Mth.lerp(partialTicks, pipeBlockEntity.lastMovement, pipeBlockEntity.movement);
        float scalar = 1.5f - v;

        poseStack.pushPose();
        {
            if (from != null && to != null) {
                Vec3i normal = (scalar > 1 ? from.getOpposite() : to).getNormal();
                double x = 0.5 + normal.getX();
                double y = 0.5 + normal.getY();
                double z = 0.5 + normal.getZ();
                double x1 = x - normal.getX() * scalar;
                double y1 = y - normal.getY() * scalar;
                double z1 = z - normal.getZ() * scalar;
                poseStack.translate(x1, y1, z1);
            } else {
                poseStack.translate(0.5, 0.5, 0.5);
            }
            poseStack.scale(0.5f, 0.5f, 0.5f);
            if (stack.getItem() instanceof BlockItem) {
                poseStack.scale(0.5f, 0.5f, 0.5f);
            }
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, i, i1, poseStack, multiBufferSource, pipeBlockEntity.getLevel(), 1);
        }
        poseStack.popPose();
    }

    private static void renderPipeColourOverlay(ItemPipeBE pipeBlockEntity, PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight, int packedOverlay) {
        if (!(pipeBlockEntity instanceof ColouredPipe colouredPipe)) {
            return;
        }

        String folder = pipeBlockEntity instanceof LapisItemPipeBE ? "lapis" : "daizuli";
        ResourceLocation texture = BuildcraftLegacy.rl("textures/block/coloured_pipes/" + folder + "_item_" + colouredPipe.getPipeColor().getName() + ".png");
        VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.entityCutout(texture));

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        float size = 0.18f; // Small box in the center
        renderBox(poseStack, vertexConsumer, size, packedLight, packedOverlay);
        poseStack.popPose();
    }

    private static void renderBox(PoseStack poseStack, VertexConsumer consumer, float size, int light, int overlay) {
        Matrix4f matrix4f = poseStack.last().pose();
        Matrix3f matrix3f = poseStack.last().normal();

        // Down
        renderFace(matrix4f, matrix3f, consumer, -size, -size, size, size, -size, -size, 0, 1, 0, 1, light, overlay, 0, -1, 0);
        // Up
        renderFace(matrix4f, matrix3f, consumer, -size, size, -size, size, size, size, 0, 1, 0, 1, light, overlay, 0, 1, 0);
        // North
        renderFace(matrix4f, matrix3f, consumer, -size, size, -size, size, -size, -size, 0, 1, 0, 1, light, overlay, 0, 0, -1);
        // South
        renderFace(matrix4f, matrix3f, consumer, size, size, size, -size, -size, size, 0, 1, 0, 1, light, overlay, 0, 0, 1);
        // West
        renderFace(matrix4f, matrix3f, consumer, -size, size, size, -size, -size, -size, 0, 1, 0, 1, light, overlay, -1, 0, 0);
        // East
        renderFace(matrix4f, matrix3f, consumer, size, size, -size, size, -size, size, 0, 1, 0, 1, light, overlay, 1, 0, 0);
    }

    private static void renderFace(Matrix4f pPose, Matrix3f pNormal, VertexConsumer pConsumer, float pX1, float pY1, float pZ1, float pX2, float pY2, float pZ2, float pU1, float pU2, float pV1, float pV2, int pLight, int pOverlay, float pNx, float pNy, float pNz) {
        pConsumer.addVertex(pPose, pX1, pY1, pZ1).setColor(255, 255, 255, 255).setUv(pU1, pV1).setOverlay(pOverlay).setLight(pLight).setNormal(pNx, pNy, pNz);
        pConsumer.addVertex(pPose, pX2, pY1, pZ1).setColor(255, 255, 255, 255).setUv(pU2, pV1).setOverlay(pOverlay).setLight(pLight).setNormal(pNx, pNy, pNz);
        pConsumer.addVertex(pPose, pX2, pY2, pZ2).setColor(255, 255, 255, 255).setUv(pU2, pV2).setOverlay(pOverlay).setLight(pLight).setNormal(pNx, pNy, pNz);
        pConsumer.addVertex(pPose, pX1, pY2, pZ2).setColor(255, 255, 255, 255).setUv(pU1, pV2).setOverlay(pOverlay).setLight(pLight).setNormal(pNx, pNy, pNz);
    }
}
