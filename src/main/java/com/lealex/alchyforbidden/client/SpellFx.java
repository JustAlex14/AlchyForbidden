package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.network.SpellFxPayload;
import com.lealex.alchyx.client.FlatDisc;
import com.lealex.alchyx.client.RiftDecal;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * The spells' pictures (network/SpellFxPayload). CHAINS: three rifts tear open round the place the spell was cast at,
 * a chain of the rift shoots out of each to the creature, holds it, and is drawn back in as the hold ends. HOLE: the
 * black hole's rift, wide, with a disc of runes turning round it and a second one against it. RIFT: a small rift
 * opening and closing (the mouth a bolt comes out of, a gate's two ends).
 */
public final class SpellFx {
    private SpellFx() {}

    private static final ContextKey<List<Drawn>> RENDER_KEY =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "spell_fx"));
    private static final Identifier CHAIN = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/entity/rift_chain.png");
    private static final Identifier CIRCLE = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/misc/rite_circle.png");
    private static final Identifier RUNES = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/misc/rite_runes.png");
    private static final int VIOLET = 0x9C7AE8, HOLE_TINT = 0xB070FF;
    private static final int CHAIN_OUT = 6, CHAIN_BACK = 8;

    private static final class Fx {
        final SpellFxPayload payload;
        int age;

        Fx(SpellFxPayload payload) {
            this.payload = payload;
        }
    }

    /** What one frame draws: a rift, a chain from a rift to a point, a turning disc. */
    private sealed interface Drawn permits Rift, Chain, Disc, Shadow {}

    /** A shadow of the caster: its own render state (posed here), where it stands, camera-free. */
    private record Shadow(net.minecraft.client.renderer.entity.state.EntityRenderState state, Vec3 at) implements Drawn {}

    private static final int SHADOW_TINT = 0xC0180A28;

    private record Rift(Vec3 at, float size, float open, int variant) implements Drawn {}

    private record Chain(Vec3 from, Vec3 to) implements Drawn {}

    private record Disc(Vec3 at, float radius, float turn, float tilt, int argb, boolean runes) implements Drawn {}

    private static final List<Fx> ACTIVE = new ArrayList<>();

    public static void receive(SpellFxPayload payload) {
        if (ACTIVE.size() < 64) ACTIVE.add(new Fx(payload));
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().level == null) {
            ACTIVE.clear();
            return;
        }
        for (Iterator<Fx> it = ACTIVE.iterator(); it.hasNext(); ) {
            Fx fx = it.next();
            if (++fx.age > fx.payload.ticks()) {
                it.remove();
                continue;
            }
            if (fx.payload.kind() == SpellFxPayload.SHADOW) { // ink and smoke trailing the shadow
                Vec3 at = shadowAt(Minecraft.getInstance().level, fx, 0F);
                if (at != null) {
                    Minecraft.getInstance().level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, at.x, at.y + 1, at.z, 0, 0.02, 0);
                    if (fx.age % 2 == 0) Minecraft.getInstance().level.addParticle(net.minecraft.core.particles.ParticleTypes.SQUID_INK, at.x, at.y + 0.6, at.z, 0, 0, 0);
                }
            }
        }
    }

    /** 0 to 1 over `in` ticks, 1, then back to 0 over the last `out` ticks. */
    private static float envelope(float age, int ticks, int in, int out) {
        return Mth.clamp(Math.min(age / Math.max(1, in), (ticks - age) / Math.max(1, out)), 0, 1);
    }

    /** The three places round `at` the chains come out of (from the entity id, so they stay put). */
    private static Vec3[] anchors(Vec3 at, int seed) {
        Vec3[] out = new Vec3[3];
        double turn = (seed * 0.618034 % 1.0) * Math.PI * 2;
        for (int k = 0; k < 3; k++) {
            double a = turn + k * Math.PI * 2 / 3;
            double r = 2.2 + 0.3 * k, up = k == 2 ? -0.3 : 2.0 + 0.5 * k; // two above, one low out of the ground
            out[k] = at.add(Math.cos(a) * r, up, Math.sin(a) * r);
        }
        return out;
    }

    public static void onExtract(ExtractLevelRenderStateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || ACTIVE.isEmpty()) {
            event.getRenderState().setRenderData(RENDER_KEY, null);
            return;
        }
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float time = (level.getGameTime() % 72000L) + partial;
        List<Drawn> drawn = new ArrayList<>();
        for (Fx fx : ACTIVE) {
            SpellFxPayload p = fx.payload;
            float age = fx.age + partial;
            Vec3 at = new Vec3(p.x(), p.y(), p.z());
            switch (p.kind()) {
                case SpellFxPayload.CHAINS -> {
                    Entity held = p.entity() >= 0 ? level.getEntity(p.entity()) : null;
                    Vec3 target = held != null ? held.getPosition(partial).add(0, held.getBbHeight() * 0.55, 0) : at.add(0, 0.9, 0);
                    float reach = envelope(age, p.ticks(), CHAIN_OUT, CHAIN_BACK);
                    float open = envelope(age, p.ticks(), 4, 4);
                    Vec3[] anchors = anchors(at, p.entity());
                    for (int k = 0; k < anchors.length; k++) {
                        drawn.add(new Rift(anchors[k], 0.9F, open, k + p.entity()));
                        if (reach > 0) drawn.add(new Chain(anchors[k], anchors[k].lerp(target, reach)));
                    }
                }
                case SpellFxPayload.HOLE -> {
                    float open = envelope(age, p.ticks(), 10, 12);
                    float size = p.size() * (0.4F + 0.6F * open) * (1 + 0.06F * Mth.sin(time * 0.3F));
                    drawn.add(new Disc(at, size * 1.5F, time * 6F, 18F, (int) (open * 200) << 24 | HOLE_TINT, false));
                    drawn.add(new Disc(at, size * 1.15F, -time * 9F, -12F, (int) (open * 170) << 24 | 0xFFFFFF, true));
                    drawn.add(new Rift(at, size, open, 2));
                }
                case SpellFxPayload.RIFT -> drawn.add(new Rift(at, p.size(), envelope(age, p.ticks(), 4, 6), (int) (p.x() * 7 + p.z() * 3)));
                case SpellFxPayload.SHADOW -> {
                    Shadow shadow = shadow(level, fx, partial);
                    if (shadow != null) drawn.add(shadow);
                }
                default -> {}
            }
        }
        event.getRenderState().setRenderData(RENDER_KEY, drawn.isEmpty() ? null : drawn);
    }

    public static void onSubmit(SubmitCustomGeometryEvent event) {
        List<Drawn> drawn = event.getLevelRenderState().getRenderData(RENDER_KEY);
        if (drawn == null) return;
        var cameraState = event.getLevelRenderState().cameraRenderState;
        Vec3 camera = cameraState.pos;
        PoseStack poseStack = event.getPoseStack();
        var collector = event.getSubmitNodeCollector();
        List<Chain> chains = new ArrayList<>();
        for (Drawn d : drawn) {
            switch (d) {
                case Rift rift -> {
                    poseStack.pushPose();
                    poseStack.translate(rift.at().x - camera.x, rift.at().y - camera.y, rift.at().z - camera.z);
                    RiftDecal.submit(poseStack, collector, cameraState, rift.size(), rift.variant(), rift.open(), VIOLET);
                    poseStack.popPose();
                }
                case Disc disc -> {
                    poseStack.pushPose();
                    poseStack.translate(disc.at().x - camera.x, disc.at().y - camera.y, disc.at().z - camera.z);
                    poseStack.mulPose(Axis.XP.rotationDegrees(disc.tilt()));
                    poseStack.mulPose(Axis.YP.rotationDegrees(disc.turn()));
                    collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(disc.runes() ? RUNES : CIRCLE),
                            (pose, buffer) -> FlatDisc.quad(pose, buffer, disc.radius(), disc.argb()));
                    poseStack.popPose();
                }
                case Chain chain -> chains.add(chain);
                case Shadow shadow -> {
                    try { // the caster's own renderer, through a ghost's eyes: dark, see-through, full bright
                        Minecraft.getInstance().getEntityRenderDispatcher().submit(shadow.state(), cameraState, shadow.at().x - camera.x,
                                shadow.at().y - camera.y, shadow.at().z - camera.z, poseStack,
                                new com.lealex.alchyx.client.GhostCollector(collector, SHADOW_TINT, 0.0F));
                    } catch (RuntimeException ignored) {
                        // a renderer that cannot be drawn this way: no shadow, nothing else lost
                    }
                }
            }
        }
        if (chains.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(CHAIN), (pose, buffer) -> {
            for (Chain chain : chains) chain(pose, buffer, chain.from(), chain.to());
        });
        poseStack.popPose();
    }

    /** A chain from a to b: two crossed strips of links, one texture length (two links) every 0.6 blocks. */
    private static void chain(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b) {
        Vec3 along = b.subtract(a);
        double length = along.length();
        if (length < 0.05) return;
        Vec3 dir = along.scale(1 / length);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 other = dir.cross(side).normalize();
        double half = 0.14, step = 0.6;
        for (Vec3 w : new Vec3[]{side, other}) {
            Vec3 n = w == side ? other : side;
            Vec3 off = w.scale(half);
            for (double d = 0; d < length; d += step) {
                double e = Math.min(length, d + step);
                float v1 = (float) ((e - d) / step);
                Vec3 p0 = a.add(dir.scale(d)), p1 = a.add(dir.scale(e));
                for (int wind = 0; wind < 2; wind++) {
                    float s = wind == 0 ? 1 : -1;
                    Vec3[] q = wind == 0
                            ? new Vec3[]{p0.subtract(off), p0.add(off), p1.add(off), p1.subtract(off)}
                            : new Vec3[]{p1.subtract(off), p1.add(off), p0.add(off), p0.subtract(off)};
                    float[][] uv = wind == 0
                            ? new float[][]{{0, 0}, {1, 0}, {1, v1}, {0, v1}}
                            : new float[][]{{0, v1}, {1, v1}, {1, 0}, {0, 0}};
                    for (int k = 0; k < 4; k++) {
                        buffer.addVertex(pose, (float) q[k].x, (float) q[k].y, (float) q[k].z).setColor(0xFFFFFFFF).setUv(uv[k][0], uv[k][1])
                                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightCoordsUtil.FULL_BRIGHT)
                                .setNormal(pose, (float) n.x * s, (float) n.y * s, (float) n.z * s);
                    }
                }
            }
        }
    }

    // ---- Shadow Strike ----

    /** Where a shadow stands t ticks into its life: from beside the caster's start to just before its prey, then there. */
    private static Vec3 shadowAt(ClientLevel level, Fx fx, float partial) {
        SpellFxPayload p = fx.payload;
        float t = fx.age + partial - p.size();
        if (t < 0) return null;
        Entity target = level.getEntity(p.entity());
        Vec3 start = new Vec3(p.x(), p.y(), p.z());
        double a = p.size() * 1.3; // each shadow tears out of a different side
        start = start.add(Math.cos(a) * 0.9, 0, Math.sin(a) * 0.9);
        Vec3 end = target != null ? target.getPosition(partial) : start;
        Vec3 dir = end.subtract(start);
        if (dir.lengthSqr() > 1.0) end = end.subtract(dir.normalize().scale(0.9));
        float dash = Mth.clamp(t / com.lealex.alchyforbidden.essence.Spells.SHADOW_DASH, 0F, 1F);
        float e = 1F - (1F - dash) * (1F - dash) * (1F - dash); // fast out, slowing as it arrives
        return start.lerp(end, e);
    }

    private static Shadow shadow(ClientLevel level, Fx fx, float partial) {
        SpellFxPayload p = fx.payload;
        Entity caster = level.getEntity(p.other());
        Vec3 at = shadowAt(level, fx, partial);
        if (caster == null || at == null) return null;
        float t = fx.age + partial - p.size();
        int life = com.lealex.alchyforbidden.essence.Spells.SHADOW_TICKS;
        Entity target = level.getEntity(p.entity());
        var state = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(caster, partial);
        state.shadowPieces.clear();
        state.shadowRadius = 0;
        state.nameTag = null;
        if (state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living) {
            float yaw = living.bodyRot;
            if (target != null) {
                Vec3 to = target.getPosition(partial).subtract(at);
                yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90F;
            }
            living.bodyRot = yaw;
            living.yRot = 0F;
            living.xRot = 10F;
            boolean dashing = t < com.lealex.alchyforbidden.essence.Spells.SHADOW_DASH;
            living.walkAnimationSpeed = dashing ? 1.5F : 0F;
            living.walkAnimationPos = t * 1.6F;
            float grow = Mth.clamp(t / 4F, 0F, 1F), fade = Mth.clamp((life - t) / 6F, 0F, 1F);
            living.scale *= 0.4F + 0.6F * grow * fade;
            if (living instanceof net.minecraft.client.renderer.entity.state.ArmedEntityRenderState armed && !dashing) {
                armed.attackTime = ((t - com.lealex.alchyforbidden.essence.Spells.SHADOW_DASH) / 6F) % 1F; // slash, slash
            }
        }
        return new Shadow(state, at);
    }
}
