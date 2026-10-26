package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.block.entity.AltarBlockEntity;
import com.lealex.alchyforbidden.essence.Essence;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * The circle a rite draws round the altar: on its floor (the first layer, under the platform once it has risen), a
 * glowing ring of the mage's signs with a seven pointed star, a band of signs turning the other way over it, in the
 * colours of what the rite spends (violet when it spends nothing). It fades in as the rite begins, turns faster and
 * burns brighter as it nears its end, flashes as the result is made and fades. Signs rise off its rim meanwhile.
 * Sized to the floor of the altar's tier.
 */
public final class RiteCircles {
    private RiteCircles() {}

    private static final Identifier CIRCLE = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/misc/rite_circle.png");
    private static final Identifier RUNES = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/misc/rite_runes.png");
    private static final ContextKey<List<Shown>> RENDER_KEY =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "rite_circles"));
    private static final int FADE_IN = 20, FADE_OUT = 24;
    /** Its radius over the floor of each tier (3 x 3, 7 x 7, 11 x 11). */
    private static final float[] RADIUS = {2.6F, 3.8F, 5.6F};

    /** Per altar on this client: when it was last seen at a rite, how far along, in what colour. */
    private static final class Seen {
        long lastRiting = Long.MIN_VALUE / 2, began;
        float progress;
        int color = 0xB98CFF;
        boolean riting;
    }

    private static final Map<AltarBlockEntity, Seen> SEEN = new WeakHashMap<>();

    private record Shown(Vec3 middle, float radius, float alpha, float turn, float flash, int color) {}

    /** The rite's colour: its essences' colours weighted by what it spends of each. */
    private static int colorOf(int[] cost) {
        int total = cost[0] + cost[1] + cost[2];
        if (total <= 0) return 0xB98CFF;
        float r = 0, g = 0, b = 0;
        for (Essence essence : Essence.values()) {
            float w = cost[essence.ordinal()] / (float) total;
            int c = essence.color();
            r += (c >> 16 & 255) * w;
            g += (c >> 8 & 255) * w;
            b += (c & 255) * w;
        }
        // brightened: the circle glows
        float max = Math.max(r, Math.max(g, b));
        float k = max > 0 ? Math.min(2.2F, 230F / max) : 1;
        return Math.min(255, Math.round(r * k)) << 16 | Math.min(255, Math.round(g * k)) << 8 | Math.min(255, Math.round(b * k));
    }

    /** Where the floor's top is under an altar, and its middle. */
    private static Vec3 floorMiddle(AltarBlockEntity altar) {
        return new Vec3(altar.getBlockPos().getX() + 0.5, altar.getBlockPos().getY() - altar.standing() + 1 + 0.03, altar.getBlockPos().getZ() + 0.5);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused()) return;
        long now = level.getGameTime();
        RandomSource random = level.getRandom();
        for (AltarBlockEntity altar : AltarBlockEntity.loaded(level)) {
            Seen seen = SEEN.computeIfAbsent(altar, key -> new Seen());
            boolean riting = altar.isRiting();
            if (riting) {
                if (!seen.riting) seen.began = now;
                seen.lastRiting = now;
                seen.progress = altar.riteProgress();
                seen.color = colorOf(altar.riteCost());
                // signs rising off the rim, more of them as it nears its end
                float radius = RADIUS[Mth.clamp(altar.standing(), 1, 3) - 1];
                Vec3 middle = floorMiddle(altar);
                int count = com.lealex.alchyx.AlchyXClientConfig.count(1 + (int) (seen.progress * 3), random);
                for (int i = 0; i < count; i++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    double x = middle.x + Math.cos(a) * radius, z = middle.z + Math.sin(a) * radius;
                    level.addParticle(random.nextInt(3) == 0 ? ModRegistries.RIFT_MOTE.get() : ParticleTypes.ENCHANT,
                            x, middle.y + 0.1, z, 0, 0.05 + random.nextDouble() * 0.05, 0);
                }
            }
            seen.riting = riting;
        }
    }

    public static void onExtract(ExtractLevelRenderStateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || SEEN.isEmpty()) {
            event.getRenderState().setRenderData(RENDER_KEY, null);
            return;
        }
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float now = level.getGameTime() + partial;
        List<Shown> shown = new ArrayList<>();
        for (Map.Entry<AltarBlockEntity, Seen> entry : SEEN.entrySet()) {
            AltarBlockEntity altar = entry.getKey();
            Seen seen = entry.getValue();
            if (altar.isRemoved() || altar.getLevel() != level) continue;
            float since = now - seen.lastRiting; // after the rite: the flash and the fade
            if (since > FADE_OUT) continue;
            float in = Mth.clamp((now - seen.began) / FADE_IN, 0, 1);
            float alpha = seen.riting ? in * (0.55F + 0.35F * seen.progress + 0.1F * Mth.sin(now * 0.25F))
                    : Math.max(0, 1 - since / FADE_OUT);
            float flash = seen.riting ? 0 : Math.max(0, 1 - since / 8F);
            // it turns faster as the rite goes on (the turn worked out from the progress, so it never jumps)
            float turn = (now - seen.began) * (0.6F + 1.4F * seen.progress);
            shown.add(new Shown(floorMiddle(altar), RADIUS[Mth.clamp(altar.standing(), 1, 3) - 1], Mth.clamp(alpha, 0, 1), turn, flash, seen.color));
        }
        event.getRenderState().setRenderData(RENDER_KEY, shown.isEmpty() ? null : shown);
    }

    public static void onSubmit(SubmitCustomGeometryEvent event) {
        List<Shown> shown = event.getLevelRenderState().getRenderData(RENDER_KEY);
        if (shown == null) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        for (Shown circle : shown) {
            int a = Math.round(255 * circle.alpha());
            int glow = Math.round(255 * Math.min(1, circle.alpha() * 0.35F + circle.flash()));
            poseStack.pushPose();
            poseStack.translate(circle.middle().x - camera.x, circle.middle().y - camera.y, circle.middle().z - camera.z);
            // the circle, turning slowly
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(circle.turn()));
            float r = circle.radius();
            event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(CIRCLE), (pose, buffer) -> {
                com.lealex.alchyx.client.FlatDisc.quad(pose, buffer, r, a << 24 | circle.color());
                com.lealex.alchyx.client.FlatDisc.quad(pose, buffer, r * (1.03F + 0.05F * circle.flash()), glow << 24 | circle.color()); // its halo
            });
            poseStack.popPose();
            // the band of signs, the other way, a little above
            poseStack.pushPose();
            poseStack.translate(0, 0.01, 0);
            poseStack.mulPose(Axis.YP.rotationDegrees(-circle.turn() * 1.7F));
            event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(RUNES), (pose, buffer) ->
                    com.lealex.alchyx.client.FlatDisc.quad(pose, buffer, r, a << 24 | 0xFFFFFF));
            poseStack.popPose();
            poseStack.popPose();
        }
    }
}
