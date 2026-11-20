package com.duomodded.tethered.client;

import com.duomodded.tethered.network.TetherStatePayload;
import com.duomodded.tethered.tether.RopeTier;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Draws the rope between you and your partner, sagging when slack, colored by tier. */
public final class RopeRenderer {
    private static final int SEGMENTS = 24;
    private static final double HALF_WIDTH = 0.035;

    public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        ClientLevel level = mc.level;
        TetherStatePayload state = ClientTetherState.get();
        if (me == null || level == null || !state.linked() || !state.rope() || !state.sameLevel()) return;
        Player partner = level.getPlayerByUUID(state.partner());
        if (partner == null) return;

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();

        Vec3 a = me.getPosition(partialTick).add(0, me.getBbHeight() * 0.5, 0);
        if (mc.options.getCameraType().isFirstPerson()) a = a.add(0, -0.3, 0);
        Vec3 b = partner.getPosition(partialTick).add(0, partner.getBbHeight() * 0.5, 0);

        double dist = a.distanceTo(b);
        if (state.slack() && dist > state.maxLength()) return;
        double slack = Math.max(0, state.currentLength() - dist);
        double sag = Math.min(3.0, slack * 0.45);

        int color = RopeTier.byLevel(state.tier()).color;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float bl = (color & 0xFF) / 255f;

        Vec3 dir = b.subtract(a);
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(HALF_WIDTH, 0, 0) : side.normalize().scale(HALF_WIDTH);
        Vec3 up = new Vec3(0, HALF_WIDTH, 0);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.leash());

        strip(consumer, matrix, level, a, b, cam, sag, side, r, g, bl, true);
        strip(consumer, matrix, level, a, b, cam, sag, up, r, g, bl, false);

        buffers.endBatch(RenderType.leash());
        poseStack.popPose();
    }

    private static void strip(VertexConsumer consumer, Matrix4f matrix, ClientLevel level, Vec3 a, Vec3 b, Vec3 cam,
                              double sag, Vec3 offset, float r, float g, float bl, boolean forward) {
        for (int k = 0; k <= SEGMENTS; k++) {
            int i = forward ? k : SEGMENTS - k;
            float t = i / (float) SEGMENTS;
            double x = Mth.lerp(t, a.x, b.x);
            double y = Mth.lerp(t, a.y, b.y) - sag * 4 * t * (1 - t);
            double z = Mth.lerp(t, a.z, b.z);
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z));
            float shade = (i % 2 == 0) ? 1.0f : 0.72f;

            float rx = (float) (x - cam.x);
            float ry = (float) (y - cam.y);
            float rz = (float) (z - cam.z);
            consumer.addVertex(matrix, rx + (float) offset.x, ry + (float) offset.y, rz + (float) offset.z)
                    .setColor(r * shade, g * shade, bl * shade, 1f).setLight(light);
            consumer.addVertex(matrix, rx - (float) offset.x, ry - (float) offset.y, rz - (float) offset.z)
                    .setColor(r * shade, g * shade, bl * shade, 1f).setLight(light);
        }
    }

    private RopeRenderer() {}
}
