package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.network.AttackFxPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
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
 * The attacks of the rift's creatures drawn as shapes (network/AttackFxPayload): bolts as small 3D models by style
 * (a soul orb, a wax drop, a bone spike, a blade of light, an obsidian boulder, a feather, a crystal shard) with a glow
 * round them; beams as textured rays, crossed, scrolling, sweeping; rings as glowing bands going out along the floor;
 * zones as patches of wax, fire or shadow; columns of light (a faint warning, then the strike); spikes of obsidian or
 * bone rising along a shockwave line; novas as a blast going out; a burning edge closing in. Everything is emissive.
 */
public final class AttackFx {
    private AttackFx() {}

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/fx/" + name + ".png");
    }

    private static final Identifier BOLT = tex("bolt"), GLOW = tex("glow"), BEAM = tex("beam"), SONIC = tex("sonic"), RING = tex("ring"),
            WAX = tex("puddle"), FIRE = tex("fire"), SHADOW = tex("shadow"), COLUMN = tex("column"), SPIKE = tex("spike");
    private static final ContextKey<Frame> RENDER_KEY = new ContextKey<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "attack_fx"));

    private static final class Fx {
        final AttackFxPayload p;
        int age;
        Vec3 pos, prevPos, vel;

        Fx(AttackFxPayload p) {
            this.p = p;
            this.pos = this.prevPos = new Vec3(p.x(), p.y(), p.z());
            this.vel = new Vec3(p.dx(), p.dy(), p.dz());
        }
    }

    private static final List<Fx> LIVE = new ArrayList<>();

    private record Frame(List<Fx> fx, float partial) {}

    public static void receive(AttackFxPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != seen) { // arrived in a new world before the tick saw it
            LIVE.clear();
            seen = level;
        }
        if (payload.kind() == AttackFxPayload.BOLT_END) {
            LIVE.removeIf(f -> f.p.kind() == AttackFxPayload.BOLT && f.p.id() == payload.id());
            if (payload.a() > 0 && LIVE.size() < 600) LIVE.add(new Fx(payload)); // it struck: a flash where it landed
            return;
        }
        if (LIVE.size() < 600) LIVE.add(new Fx(payload));
    }

    /** The world the live shapes belong to: a new one (another dimension, the void trial and back) starts empty. */
    private static @org.jspecify.annotations.Nullable ClientLevel seen;

    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != seen) {
            LIVE.clear();
            seen = level;
        }
        if (level == null) return;
        for (Iterator<Fx> it = LIVE.iterator(); it.hasNext(); ) {
            Fx f = it.next();
            f.age++;
            if (f.p.kind() == AttackFxPayload.BOLT) {
                f.prevPos = f.pos;
                if (f.p.entity() >= 0 && level.getEntity(f.p.entity()) instanceof Entity prey && prey.isAlive()) {
                    f.vel = com.lealex.alchyforbidden.familiar.RiftBeast.homeIn(f.vel, f.pos, prey.position().add(0, prey.getBbHeight() * 0.55, 0), f.age);
                }
                if (f.p.a() > 0 && f.age > 18 && level.getEntity(f.p.owner()) instanceof Entity owner) {
                    Vec3 home = owner.position().add(0, owner.getBbHeight() * 0.5, 0).subtract(f.pos);
                    if (home.lengthSqr() < 1.5) {
                        it.remove();
                        continue;
                    }
                    f.vel = home.normalize().scale(f.vel.length());
                }
                f.pos = f.pos.add(f.vel);
            } else if ((f.p.kind() == AttackFxPayload.ZONE || f.p.kind() == AttackFxPayload.COLUMN && f.age < f.p.delay())
                    && f.p.entity() >= 0 && level.getEntity(f.p.entity()) instanceof Entity prey) {
                f.prevPos = f.pos; // a shadow or a warning creeping after its prey
                f.pos = f.pos.lerp(new Vec3(prey.getX(), prey.getY(), prey.getZ()), f.p.b() > 0 ? f.p.b() : 0.1);
            } else {
                f.prevPos = f.pos;
            }
            if (f.p.kind() == AttackFxPayload.EDGE && !(level.getEntity(f.p.owner()) instanceof Entity owner && owner.isAlive())) {
                it.remove(); // the burning floor goes with the one that lit it
                continue;
            }
            if (f.age > f.p.life() + 2) it.remove();
        }
    }

    public static void onExtract(ExtractLevelRenderStateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || LIVE.isEmpty()) {
            event.getRenderState().setRenderData(RENDER_KEY, null);
            return;
        }
        event.getRenderState().setRenderData(RENDER_KEY, new Frame(new ArrayList<>(LIVE), minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false)));
    }

    public static void onSubmit(SubmitCustomGeometryEvent event) {
        Frame frame = event.getLevelRenderState().getRenderData(RENDER_KEY);
        if (frame == null) return;
        Vec3 cam = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        float pt = frame.partial();
        for (Fx f : frame.fx()) {
            float t = f.age + pt;
            switch (f.p.kind()) {
                case AttackFxPayload.BOLT -> bolt(event, poseStack, cam, f, pt, t);
                case AttackFxPayload.BOLT_END -> flash(event, poseStack, cam, f, t);
                case AttackFxPayload.BEAM -> beam(event, poseStack, cam, f, t);
                case AttackFxPayload.RING -> ring(event, poseStack, cam, f, t);
                case AttackFxPayload.ZONE -> zone(event, poseStack, cam, f, t);
                case AttackFxPayload.COLUMN -> column(event, poseStack, cam, f, t);
                case AttackFxPayload.LINE -> line(event, poseStack, cam, f, t);
                case AttackFxPayload.NOVA -> nova(event, poseStack, cam, f, t);
                case AttackFxPayload.EDGE -> edge(event, poseStack, cam, f, t);
                default -> {}
            }
        }
    }

    // ---- drawing helpers (world coordinates relative to the camera) ----

    private static RenderType type(Identifier texture) {
        return RenderTypes.entityTranslucentEmissive(texture);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, Vec3 p, float u, float v, int color) {
        buffer.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightCoordsUtil.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }

    /** A quad a, b, c, d (drawn once: the entity translucent types do not cull, so it shows from both sides). */
    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float u0, float v0, float u1, float v1, int color) {
        vertex(buffer, pose, a, u0, v0, color);
        vertex(buffer, pose, b, u0, v1, color);
        vertex(buffer, pose, c, u1, v1, color);
        vertex(buffer, pose, d, u1, v0, color);
    }

    private static int argb(int rgb, float alpha) {
        return Mth.clamp((int) (alpha * 255), 0, 255) << 24 | (rgb & 0xFFFFFF);
    }

    /** A box along a direction (f forward, r right, u up), centred on c, half sizes l, w, h. */
    private static void box(VertexConsumer buffer, PoseStack.Pose pose, Vec3 c, Vec3 f, Vec3 r, Vec3 u, double l, double w, double h, int color) {
        Vec3[] k = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            k[i] = c.add(f.scale((i & 1) == 0 ? -l : l)).add(r.scale((i & 2) == 0 ? -w : w)).add(u.scale((i & 4) == 0 ? -h : h));
        }
        int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};
        for (int[] q : faces) quad(buffer, pose, k[q[0]], k[q[1]], k[q[2]], k[q[3]], 0, 0, 1, 1, color);
    }

    /** A camera-facing square of a texture. */
    private static void billboard(VertexConsumer buffer, PoseStack.Pose pose, Vec3 c, Vec3 toCam, double size, float spin, int color) {
        Vec3 n = toCam.normalize();
        Vec3 r = n.cross(new Vec3(0, 1, 0));
        if (r.lengthSqr() < 1e-4) r = new Vec3(1, 0, 0);
        r = r.normalize();
        Vec3 u = r.cross(n).normalize();
        double cs = Math.cos(spin) * size, sn = Math.sin(spin) * size;
        Vec3 a = r.scale(cs).add(u.scale(sn)), b = r.scale(-sn).add(u.scale(cs));
        quad(buffer, pose, c.subtract(a).subtract(b), c.subtract(a).add(b), c.add(a).add(b), c.add(a).subtract(b), 0, 0, 1, 1, color);
    }

    private static Vec3[] basis(Vec3 forward) {
        Vec3 f = forward.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : forward.normalize();
        Vec3 r = f.cross(new Vec3(0, 1, 0));
        if (r.lengthSqr() < 1e-4) r = f.cross(new Vec3(1, 0, 0));
        r = r.normalize();
        return new Vec3[] {f, r, r.cross(f).normalize()};
    }

    // ---- the shapes ----

    private static void bolt(SubmitCustomGeometryEvent event, PoseStack poseStack, Vec3 cam, Fx f, float pt, float t) {
        Vec3 at = f.prevPos.lerp(f.pos, pt).subtract(cam);
        Vec3[] b = basis(f.vel);
        double s = f.p.size();
        int style = f.p.style(), color = argb(f.p.color(), 1F);
        float spin = t * 0.4F;
        Vec3 r = b[1].scale(Math.cos(spin)).add(b[2].scale(Math.sin(spin))), u = b[2].scale(Math.cos(spin)).subtract(b[1].scale(Math.sin(spin)));
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, type(style == AttackFxPayload.BONE || style == AttackFxPayload.BOULDER ? SPIKE : BOLT), (pose, buffer) -> {
            switch (style) {
                case AttackFxPayload.WAX -> box(buffer, pose, at, b[0], r, u, 0.16 * s, 0.11 * s, 0.11 * s, color);
                case AttackFxPayload.BONE -> {
                    box(buffer, pose, at, b[0], r, u, 0.42 * s, 0.07 * s, 0.07 * s, argb(0xE8E0D0, 1));
                    box(buffer, pose, at.add(b[0].scale(-0.38 * s)), b[0], r, u, 0.08 * s, 0.12 * s, 0.12 * s, argb(0xE8E0D0, 1));
                }
                case AttackFxPayload.BLADE -> box(buffer, pose, at, b[0], r, u, 0.38 * s, 0.22 * s, 0.025 * s, color);
                case AttackFxPayload.BOULDER -> box(buffer, pose, at, b[0], r, u, 0.32 * s, 0.32 * s, 0.32 * s, argb(0x3A2A5A, 1));
                case AttackFxPayload.FEATHER -> {
                    box(buffer, pose, at, b[0], r, u, 0.3 * s, 0.1 * s, 0.015 * s, color);
                    box(buffer, pose, at, b[0], r, u, 0.34 * s, 0.015 * s, 0.03 * s, argb(0xFFFFFF, 1));
                }
                case AttackFxPayload.SHARD -> {
                    box(buffer, pose, at, b[0], r, u, 0.3 * s, 0.09 * s, 0.09 * s, color);
                    box(buffer, pose, at, b[0], u, r, 0.18 * s, 0.13 * s, 0.05 * s, color);
                }
                default -> box(buffer, pose, at, b[0], r, u, 0.14 * s, 0.14 * s, 0.14 * s, color); // a soul orb
            }
        });
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, type(GLOW), (pose, buffer) -> {
            Vec3 toCam = at.scale(-1);
            billboard(buffer, pose, at, toCam, (style == AttackFxPayload.ORB ? 0.55 : 0.4) * s, t * 0.1F, argb(f.p.color(), 0.85F));
            for (int k = 1; k <= 3; k++) { // its trail
                Vec3 back = at.subtract(f.vel.normalize().scale(k * 0.3 * s));
                billboard(buffer, pose, back, back.scale(-1), (0.4 - k * 0.08) * s, t * 0.1F + k, argb(f.p.color(), 0.5F - k * 0.12F));
            }
        });
    }

    private static void flash(SubmitCustomGeometryEvent event, PoseStack poseStack, Vec3 cam, Fx f, float t) {
        float s = Mth.clamp(t / f.p.life(), 0, 1);
        Vec3 at = new Vec3(f.p.x(), f.p.y(), f.p.z()).subtract(cam);
        double size = f.p.size() * (0.4 + 1.1 * s);
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, type(GLOW), (pose, buffer) -> {
            billboard(buffer, pose, at, at.scale(-1), size, s * 2, argb(f.p.color(), 1 - s));
            billboard(buffer, pose, at, at.scale(-1), size * 0.5, -s * 3, argb(0xFFFFFF, (1 - s) * 0.8F));
        });
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, type(BOLT), (pose, buffer) -> { // shards thrown off it
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3 + f.p.id(), up = ((k * 7 + f.p.id()) % 5 - 2) * 0.25;
                Vec3 dir = new Vec3(Math.cos(a), up, Math.sin(a)).normalize();
                Vec3[] b = basis(dir);
                double r = 0.08 * f.p.size() * (1 - s);
                box(buffer, pose, at.add(dir.scale(s * 0.9 * f.p.size())), b[0], b[1], b[2], r * 1.6, r, r, argb(f.p.color(), 1 - s));
            }
        });
    }

    private static void beam(SubmitCustomGeometryEvent event, PoseStack poseStack, Vec3 cam, Fx f, float t) {
        float life = f.p.life();
        float fade = Mth.clamp(Math.min(t / 3F, (life - t) / 4F), 0, 1);
        if (fade <= 0) return;
        Vec3 dir0 = new Vec3(f.p.dx(), f.p.dy(), f.p.dz());
        int mode = f.p.style() >> 4;
        double turn = 0, tt = Mth.clamp(t / life, 0, 1);
        if (mode == 1) turn = Math.sin(tt * Math.PI) * f.p.a() - f.p.b();
        else if (mode == 2) turn = tt * f.p.a();
        Vec3 dir = new Vec3(dir0.x * Math.cos(turn) - dir0.z * Math.sin(turn), dir0.y, dir0.x * Math.sin(turn) + dir0.z * Math.cos(turn)).normalize();
        Vec3 from = new Vec3(f.p.x(), f.p.y(), f.p.z()).subtract(cam), to = from.add(dir.scale(f.p.extra()));
        Vec3[] b = basis(dir);
        double w = f.p.size() * (0.85 + 0.15 * Math.sin(t * 0.8));
        boolean sonic = (f.p.style() & 15) == AttackFxPayload.SONIC;
        float scroll = -t * 0.15F, len = f.p.extra() / (float) Math.max(0.5, f.p.size() * 2);
        int color = argb(f.p.color(), 0.85F * fade), core = argb(0xFFFFFF, 0.7F * fade);
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, type(sonic ? SONIC : BEAM), (pose, buffer) -> {
            for (int k = 0; k < 2; k++) { // two crossed ribbons
                Vec3 side = (k == 0 ? b[1] : b[2]).scale(w);
                quad(buffer, pose, from.subtract(side), to.subtract(side), to.add(side), from.add(side), 0, scroll, 1, scroll + len, color);
                Vec3 thin = side.scale(0.35);
                quad(buffer, pose, from.subtract(thin), to.subtract(thin), to.add(thin), from.add(thin), 0, scroll * 2, 1, scroll * 2 + len, core);
            }
        });
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, type(GLOW), (pose, buffer) ->
                billboard(buffer, pose, from, from.scale(-1), w * 2.5, t * 0.2F, argb(f.p.color(), 0.8F * fade)));
    }

    /** A flat ring band at height y, radius r, thickness th. */
    private static void band(VertexConsumer buffer, PoseStack.Pose pose, Vec3 c, double r, double th, float uOff, int color) {
        int n = Math.max(24, (int) (r * 8));
        double inner = Math.max(0, r - th / 2), outer = r + th / 2;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            Vec3 i0 = c.add(Math.cos(a0) * inner, 0, Math.sin(a0) * inner), i1 = c.add(Math.cos(a1) * inner, 0, Math.sin(a1) * inner);
            Vec3 o0 = c.add(Math.cos(a0) * outer, 0, Math.sin(a0) * outer), o1 = c.add(Math.cos(a1) * outer, 0, Math.sin(a1) * outer);
            float u0 = uOff + i * 4F / n, u1 = uOff + (i + 1) * 4F / n;
            quad(buffer, pose, i0, o0, o1, i1, u0, 0, u1, 1, color);
        }
    }

    private static void ring(SubmitCustomGeometryEvent event, PoseStack poseStack, Vec3 cam, Fx f, float t) {
        double r = 0.5 + f.p.size() * t;
        if (r > f.p.extra() + 0.5) return;
        float fade = (float) Mth.clamp(1 - (r - f.p.extra() * 0.8) / (f.p.extra() * 0.2 + 0.01), 0, 1);
        Vec3 c = new Vec3(f.p.x(), f.p.y() + 0.06, f.p.z()).subtract(cam);
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, type(RING), (pose, buffer) -> {
            band(buffer, pose, c, r, 0.9, t * 0.05F, argb(f.p.color(), 0.95F * fade));
            band(buffer, pose, c.add(0, 0.25, 0), r, 0.4, -t * 0.05F, argb(0xFFFFFF, 0.5F * fade)); // a crest over it
        });
    }

    private static float frameTime(float t) {
        return t - (float) Math.floor(t);
    }

    private static void zone(SubmitCustomGeometryEvent event, PoseStack poseStack, Vec3 cam, Fx f, float t) {
        float life = f.p.life();
        float fade = Mth.clamp(Math.min(t / 6F, (life - t) / 10F), 0, 1);
        if (fade <= 0) return;
        Identifier texture = switch (f.p.style()) {
            case AttackFxPayload.Z_FIRE -> FIRE;
            case AttackFxPayload.Z_SHADOW -> SHADOW;
            default -> WAX;
        };
        Vec3 c = new Vec3(Mth.lerp(frameTime(t), f.prevPos.x, f.pos.x), f.p.y() + 0.04 + Math.floorMod(f.p.id(), 5) * 0.003, Mth.lerp(frameTime(t), f.prevPos.z, f.pos.z)).subtract(cam);
        float r = f.p.size() * (f.p.style() == AttackFxPayload.Z_SHADOW ? Mth.clamp(t / 20F, 0.3F, 1) : 1);
        float spin = t * 0.01F + f.p.id();
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, type(texture), (pose, buffer) -> {
            Vec3 a = new Vec3(Math.cos(spin) * r, 0, Math.sin(spin) * r), b = new Vec3(-Math.sin(spin) * r, 0, Math.cos(spin) * r);
            quad(buffer, pose, c.subtract(a).subtract(b), c.subtract(a).add(b), c.add(a).add(b), c.add(a).subtract(b), 0, 0, 1, 1, argb(f.p.color(), fade));
        });
    }

    /** A cylinder from c up h, radius r, sides n, its texture scrolling up. */
    private static void cylinder(VertexConsumer buffer, PoseStack.Pose pose, Vec3 c, double r, double h, float scroll, int color) {
        int n = 12;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            Vec3 p0 = c.add(Math.cos(a0) * r, 0, Math.sin(a0) * r), p1 = c.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
            quad(buffer, pose, p0.add(0, h, 0), p0, p1, p1.add(0, h, 0), i / (float) n, scroll, (i + 1) / (float) n, scroll + (float) h / 4F, color);
        }
    }

    private static void column(SubmitCustomGeometryEvent event, PoseStack poseStack, Vec3 cam, Fx f, float t) {
        Vec3 c = f.prevPos.lerp(f.pos, frameTime(t)).subtract(cam);
        float delay = f.p.delay(), life = f.p.life();
        double r = f.p.size(), h = f.p.extra();
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, type(COLUMN), (pose, buffer) -> {
            if (t < delay) { // the warning: a faint column, a ring tightening on the floor
                float p = t / delay;
                cylinder(buffer, pose, c, r * 0.3, h, -t * 0.05F, argb(f.p.color(), 0.25F + 0.2F * (float) Math.sin(t * 0.8)));
                band(buffer, pose, c.add(0, 0.05, 0), r * (1.6 - 0.6 * p), 0.25, 0, argb(f.p.color(), 0.6F + 0.4F * p));
            } else { // the strike
                float s = (t - delay) / Math.max(1, life - delay);
                float alpha = Mth.clamp(1 - s, 0, 1);
                cylinder(buffer, pose, c, r * (1 + s * 0.5), h, -t * 0.2F, argb(f.p.color(), alpha));
                cylinder(buffer, pose, c, r * 0.4, h, -t * 0.4F, argb(0xFFFFFF, alpha));
            }
        });
    }

    private static void line(SubmitCustomGeometryEvent event, PoseStack poseStack, Vec3 cam, Fx f, float t) {
        Vec3 from = new Vec3(f.p.x(), f.p.y(), f.p.z()).subtract(cam), dir = new Vec3(f.p.dx(), 0, f.p.dz()).normalize();
        int color = f.p.style() == AttackFxPayload.L_BONE ? argb(0xE8E0D0, 1) : argb(0x4A3470, 1);
        double reach = Math.min(f.p.extra(), f.p.size() * t);
        Vec3[] b = basis(new Vec3(0, 1, 0));
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, type(SPIKE), (pose, buffer) -> {
            for (int d = 1; d <= reach; d++) {
                float born = d / f.p.size(), age = t - born;
                float rise = Mth.clamp(age / 3F, 0, 1) * Mth.clamp((f.p.life() - t) / 8F, 0, 1);
                if (rise <= 0) continue;
                double hgt = (0.9 + ((d * 37) % 5) * 0.12) * rise;
                Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(((d * 13) % 3 - 1) * 0.25);
                Vec3 base = from.add(dir.scale(d)).add(side);
                box(buffer, pose, base.add(0, hgt * 0.5, 0), new Vec3(0, 1, 0), new Vec3(dir.x, 0, dir.z), new Vec3(-dir.z, 0, dir.x), hgt * 0.5, 0.22, 0.22, color);
                box(buffer, pose, base.add(0, hgt + 0.2 * rise, 0), new Vec3(0, 1, 0), new Vec3(dir.x, 0, dir.z), new Vec3(-dir.z, 0, dir.x), 0.2 * rise, 0.1, 0.1, color);
            }
        });
    }

    private static void nova(SubmitCustomGeometryEvent event, PoseStack poseStack, Vec3 cam, Fx f, float t) {
        float life = f.p.life();
        float s = Mth.clamp(t / life, 0, 1), alpha = 1 - s;
        Vec3 c = new Vec3(f.p.x(), f.p.y(), f.p.z()).subtract(cam);
        double r = 0.5 + f.p.size() * (1 - (1 - s) * (1 - s));
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, type(RING), (pose, buffer) -> {
            band(buffer, pose, c.add(0, 0.08, 0), r, 1.6, t * 0.03F, argb(f.p.color(), alpha));
            band(buffer, pose, c.add(0, 0.8, 0), r * 0.92, 1.0, -t * 0.03F, argb(f.p.color(), alpha * 0.7F));
            band(buffer, pose, c.add(0, 1.6, 0), r * 0.8, 0.6, t * 0.03F, argb(0xFFFFFF, alpha * 0.5F));
        });
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, type(COLUMN), (pose, buffer) ->
                cylinder(buffer, pose, c, r, 2.2 * (1 - s) + 0.3, -t * 0.1F, argb(f.p.color(), alpha * 0.6F)));
    }

    private static void edge(SubmitCustomGeometryEvent event, PoseStack poseStack, Vec3 cam, Fx f, float t) {
        double outer = f.p.extra(), inner = Math.max(f.p.a(), outer - f.p.size() * t);
        if (inner >= outer - 0.1) return;
        Vec3 c = new Vec3(f.p.x(), f.p.y() + 0.05, f.p.z()).subtract(cam);
        float fade = Mth.clamp((f.p.life() - t) / 20F, 0, 1);
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, type(FIRE), (pose, buffer) -> {
            for (double r = inner; r < outer; r += 1.0) band(buffer, pose, c, r + 0.5, 1.05, (float) r * 0.3F + t * 0.02F, argb(f.p.color(), 0.85F * fade));
        });
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, type(COLUMN), (pose, buffer) ->
                cylinder(buffer, pose, c, inner, 1.2 + 0.2 * Math.sin(t * 0.3), -t * 0.15F, argb(f.p.color(), 0.7F * fade))); // the wall of flame at its edge
    }
}
