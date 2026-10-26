package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.block.entity.SoulCircleBlockEntity;
import com.lealex.alchyx.client.FlatDisc;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * The Binding Circle's imbuing (and unbinding) ritual seen in the world: the ring rises out of the circle and hangs
 * turning faster and faster over a disc of runes in its spell's colour (turning backward for an unbinding) that opens
 * on the circle's floor, then flies up and fades (the streams and the flash are the block entity's particles).
 */
public final class ImbueRituals {
    private ImbueRituals() {}

    private static final ContextKey<List<Shown>> RENDER_KEY =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "imbue_rituals"));
    private static final Identifier CIRCLE = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/misc/rite_circle.png");
    private static final Identifier RUNES = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/misc/rite_runes.png");

    private record Shown(Vec3 floor, Vec3 ring, ItemStackRenderState item, float scale, float turn, float discTurn, float alpha, int color) {}

    private static final class Made {
        long at;
        ItemStackRenderState state;
    }

    private static final Map<SoulCircleBlockEntity, Made> MADE = new WeakHashMap<>();

    public static void onExtract(ExtractLevelRenderStateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            event.getRenderState().setRenderData(RENDER_KEY, null);
            return;
        }
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<Shown> shown = new ArrayList<>();
        for (SoulCircleBlockEntity circle : SoulCircleBlockEntity.loaded(level)) {
            if (circle.isRemoved() || circle.ritualRing().isEmpty()) continue;
            float t = level.getGameTime() - circle.ritualAt() + partial;
            if (t < 0 || t >= SoulCircleBlockEntity.RITUAL_TICKS) continue;
            Made made = MADE.computeIfAbsent(circle, key -> new Made());
            if (made.state == null || made.at != circle.ritualAt()) {
                made.at = circle.ritualAt();
                made.state = new ItemStackRenderState();
                minecraft.getItemModelResolver().updateForTopItem(made.state, circle.ritualRing(), ItemDisplayContext.GROUND, level, null, 0);
            }
            float spin = t < 48 ? t * t * 0.35F : 48 * 48 * 0.35F + (t - 48) * 40F; // faster and faster
            float open = Mth.clamp(Math.min(t / 12F, (SoulCircleBlockEntity.RITUAL_TICKS - t) / 12F), 0, 1);
            float sign = circle.ritualTearing() ? -1 : 1;
            var pos = circle.getBlockPos();
            shown.add(new Shown(new Vec3(pos.getX() + 0.5, pos.getY() + 1.03, pos.getZ() + 0.5), circle.ritualRingPos(t), made.state,
                    circle.ritualRingScale(t), spin, sign * t * 4F, open, circle.ritualColor()));
        }
        event.getRenderState().setRenderData(RENDER_KEY, shown.isEmpty() ? null : shown);
    }

    public static void onSubmit(SubmitCustomGeometryEvent event) {
        List<Shown> shown = event.getLevelRenderState().getRenderData(RENDER_KEY);
        if (shown == null) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        var collector = event.getSubmitNodeCollector();
        for (Shown ritual : shown) {
            int a = (int) (ritual.alpha() * 210);
            // the disc on the circle's floor, and a smaller one turning against it under the ring
            poseStack.pushPose();
            poseStack.translate(ritual.floor().x - camera.x, ritual.floor().y - camera.y, ritual.floor().z - camera.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(ritual.discTurn()));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(CIRCLE),
                    (pose, buffer) -> FlatDisc.quad(pose, buffer, 2.4F, a << 24 | ritual.color()));
            poseStack.popPose();
            poseStack.pushPose();
            poseStack.translate(ritual.ring().x - camera.x, ritual.ring().y - 0.45 - camera.y, ritual.ring().z - camera.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-ritual.discTurn() * 1.8F));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(RUNES),
                    (pose, buffer) -> FlatDisc.quad(pose, buffer, 0.9F * ritual.scale(), a << 24 | 0xFFFFFF));
            poseStack.popPose();
            // the ring itself, turning, lit
            if (ritual.scale() > 0.01F) {
                poseStack.pushPose();
                poseStack.translate(ritual.ring().x - camera.x, ritual.ring().y - camera.y, ritual.ring().z - camera.z);
                poseStack.mulPose(Axis.YP.rotationDegrees(ritual.turn()));
                float s = 1.3F * ritual.scale();
                poseStack.scale(s, s, s);
                ritual.item().submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
            }
        }
    }
}
