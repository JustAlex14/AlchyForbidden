package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.essence.MobStacks;
import com.lealex.alchyx.client.RiftDecal;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * What shows a spawner is knotted (essence/MobStacks): a small violet rift open in the heart of its cage, breathing,
 * and a magic circle on the ground round it, turning slowly, its band of signs the other way.
 */
public final class KnotVisuals {
    private KnotVisuals() {}

    private static final Identifier CIRCLE = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/misc/rite_circle.png");
    private static final Identifier RUNES = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/misc/rite_runes.png");
    private static final ContextKey<List<Shown>> RENDER_KEY =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "knot_visuals"));
    private static final int VIOLET = 0xB98CFF;
    /** The tumbling rings round the cage (just wider than the cage's corners), and the gathering point's circle. */
    private static final float RING_RADIUS = 1.0F, TARGET_RADIUS = 0.8F;
    /** Past this, a knotted spawner is not drawn. */
    private static final double RANGE = 64;

    /** target: where its mobs gather, or null (round the spawner). */
    private record Shown(BlockPos at, BlockPos target, float time) {}

    public static void onExtract(ExtractLevelRenderStateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || MobStacks.clientKnots().isEmpty()) {
            event.getRenderState().setRenderData(RENDER_KEY, null);
            return;
        }
        // wrapped (a whole number of turns at every ring rate: tenths of a degree per tick), so the angles stay precise
        float time = level.getGameTime() % 72000 + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getRenderState().cameraRenderState.pos;
        List<Shown> shown = new ArrayList<>();
        for (BlockEntity spawner : MobStacks.clientKnots()) {
            if (spawner.isRemoved() || spawner.getLevel() != level) continue;
            BlockPos pos = spawner.getBlockPos();
            if (camera != null && pos.distToCenterSqr(camera) > RANGE * RANGE) continue;
            shown.add(new Shown(pos, com.lealex.alchyforbidden.item.RiftKnotItem.target(spawner), time));
        }
        event.getRenderState().setRenderData(RENDER_KEY, shown.isEmpty() ? null : shown);
    }

    public static void onSubmit(SubmitCustomGeometryEvent event) {
        List<Shown> shown = event.getLevelRenderState().getRenderData(RENDER_KEY);
        if (shown == null) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        for (Shown knot : shown) {
            BlockPos at = knot.at();
            float time = knot.time();
            poseStack.pushPose();
            poseStack.translate(at.getX() + 0.5 - camera.x, at.getY() - camera.y, at.getZ() + 0.5 - camera.z);
            // the rift in the heart of the cage
            poseStack.pushPose();
            poseStack.translate(0, 0.5, 0);
            float breath = 1 + 0.08F * Mth.sin(time * 0.12F);
            RiftDecal.submit(poseStack, event.getSubmitNodeCollector(), event.getLevelRenderState().cameraRenderState,
                    0.42F * breath, (int) (at.asLong() & 3), 0.85F + 0.15F * Mth.sin(time * 0.07F), VIOLET);
            poseStack.popPose();
            // two rings tumbling round the cage, each turning whole on all three axes at its own rates, like an
            // armillary gone mad: the violet circle and the white band of signs
            int alpha = Math.round(255 * (0.75F + 0.15F * Mth.sin(time * 0.1F)));
            ring(event, poseStack, CIRCLE, RING_RADIUS, alpha << 24 | VIOLET, time, 4.3F, 3.1F, 2.6F);
            ring(event, poseStack, RUNES, RING_RADIUS * 0.88F, alpha << 24 | 0xFFFFFF, time, -2.9F, 4.7F, -3.6F);
            poseStack.popPose();
            // its gathering point: a small circle of signs on that block, turning the other way
            BlockPos target = knot.target();
            if (target != null) {
                poseStack.pushPose();
                poseStack.translate(target.getX() + 0.5 - camera.x, target.getY() + 1.02 - camera.y, target.getZ() + 0.5 - camera.z);
                poseStack.mulPose(Axis.YP.rotationDegrees(-time * 1.2F));
                event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(CIRCLE), (pose, buffer) ->
                        com.lealex.alchyx.client.FlatDisc.quad(pose, buffer, TARGET_RADIUS, alpha << 24 | VIOLET));
                poseStack.popPose();
            }
        }
    }

    /** One ring round the cage's middle, turned whole on X, Y and Z (degrees per tick each). */
    private static void ring(SubmitCustomGeometryEvent event, PoseStack poseStack, Identifier texture, float radius, int color,
                             float time, float x, float y, float z) {
        poseStack.pushPose();
        poseStack.translate(0, 0.5, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * y));
        poseStack.mulPose(Axis.XP.rotationDegrees(time * x));
        poseStack.mulPose(Axis.ZP.rotationDegrees(time * z));
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(texture), (pose, buffer) ->
                com.lealex.alchyx.client.FlatDisc.quad(pose, buffer, radius, color));
        poseStack.popPose();
    }
}
