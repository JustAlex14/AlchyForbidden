package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * A warden killed beside an Echo Resonator is taken by the rift (AltarRiftPayload.TAKEN): a rift opens over its body
 * (client/AltarRifts), and as the game's own corpse fades the dead warden rises slowly up into it, lying limp, turning,
 * the rift's corruption creeping over it, shrinking into the rift at the end, which then closes. It comes back out of
 * a rift onto the resonator's table once the last tier stands (client/DeadWardenFigure).
 */
public final class WardenTaken {
    private WardenTaken() {}

    /** The game's corpse lies there this long before it vanishes; then ours rises. */
    private static final int WAIT = 20, RISE = 100;
    private static final ContextKey<List<Shown>> RENDER_KEY =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "warden_taken"));

    private static final class Taken {
        final BlockPos pos, rift;
        final float yaw;
        int age = 0;

        Taken(BlockPos pos, BlockPos rift, float yaw) {
            this.pos = pos;
            this.rift = rift;
            this.yaw = yaw;
        }

        /** How far up it has come (0 on the ground, 1 at the rift). */
        float up(float age) {
            return ease((age - WAIT) / RISE);
        }

        Vec3 at(float age) {
            Vec3 from = Vec3.atBottomCenterOf(pos), to = Vec3.atCenterOf(rift).add(0, -0.4, 0);
            return from.lerp(to, up(age));
        }
    }

    private record Shown(Vec3 at, float yaw, float scale, float corruption, float time, BlockPos pos) {}

    private static final List<Taken> TAKEN = new ArrayList<>();

    public static void start(BlockPos pos, BlockPos rift, float yaw) {
        TAKEN.add(new Taken(pos, rift, yaw));
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            TAKEN.clear();
            return;
        }
        if (minecraft.isPaused()) return;
        RandomSource random = level.getRandom();
        for (Iterator<Taken> it = TAKEN.iterator(); it.hasNext(); ) {
            Taken taken = it.next();
            taken.age++;
            if (taken.age == WAIT) { // the rift takes hold of it: souls torn off the body as it lifts
                Vec3 at = taken.at(taken.age);
                for (int i = 0; i < 20; i++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    level.addParticle(i % 2 == 0 ? ParticleTypes.SCULK_SOUL : ParticleTypes.SCULK_CHARGE_POP,
                            at.x + Math.cos(a) * 0.8, at.y + 0.2, at.z + Math.sin(a) * 0.8, 0, 0.05 + random.nextDouble() * 0.05, 0);
                }
            }
            if (taken.age > WAIT && taken.age % 2 == 0) { // a column of motes drawn up from the ground into the rift
                Vec3 from = Vec3.atBottomCenterOf(taken.pos);
                double a = random.nextDouble() * Math.PI * 2;
                level.addParticle(ModRegistries.RIFT_MOTE.get(), from.x + Math.cos(a) * 0.7, from.y + 0.1, from.z + Math.sin(a) * 0.7,
                        0, 0.12, 0);
            }
            if (taken.age == WAIT + RISE - 5) { // swallowed: a puff of the rift
                Vec3 at = Vec3.atCenterOf(taken.rift);
                for (int i = 0; i < 16; i++) {
                    double a = random.nextDouble() * Math.PI * 2;
                    level.addParticle(ModRegistries.RIFT_MOTE.get(), at.x, at.y, at.z, Math.cos(a) * 0.15, (random.nextDouble() - 0.5) * 0.1, Math.sin(a) * 0.15);
                }
            }
            if (taken.age > WAIT && random.nextInt(3) == 0) { // its soul smoking off it, motes of the rift on it
                Vec3 at = taken.at(taken.age).add((random.nextDouble() - 0.5) * 1.2, 0.5, (random.nextDouble() - 0.5) * 1.2);
                level.addParticle(random.nextBoolean() ? ParticleTypes.SCULK_SOUL : ModRegistries.RIFT_MOTE.get(), at.x, at.y, at.z, 0, 0.03, 0);
            }
            if (taken.age >= WAIT + RISE) {
                AltarRifts.closeRift(taken.rift);
                it.remove();
            }
        }
    }

    public static void onExtract(ExtractLevelRenderStateEvent event) {
        if (TAKEN.isEmpty()) {
            event.getRenderState().setRenderData(RENDER_KEY, null);
            return;
        }
        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<Shown> shown = new ArrayList<>();
        for (Taken taken : TAKEN) {
            float age = taken.age + partial;
            if (age < WAIT) continue; // the game's own corpse is still there
            float u = (age - WAIT) / RISE;
            float scale = 1 - 0.85F * ease((u - 0.75F) / 0.25F); // drawn into the rift at the end
            shown.add(new Shown(taken.at(age), taken.yaw + u * 120F, scale, Mth.clamp(u * 1.3F, 0, 1), age, taken.pos));
        }
        event.getRenderState().setRenderData(RENDER_KEY, shown);
    }

    public static void onSubmit(SubmitCustomGeometryEvent event) {
        List<Shown> shown = event.getLevelRenderState().getRenderData(RENDER_KEY);
        Minecraft minecraft = Minecraft.getInstance();
        if (shown == null || minecraft.level == null) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        for (Shown body : shown) {
            poseStack.pushPose();
            poseStack.translate(body.at().x - camera.x, body.at().y - camera.y, body.at().z - camera.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-body.yaw()));
            poseStack.scale(body.scale(), body.scale(), body.scale());
            DeadWardenFigure.submitBody(poseStack, event.getSubmitNodeCollector(), body.time(),
                    LevelRenderer.getLightCoords(minecraft.level, body.pos().above()), body.corruption());
            poseStack.popPose();
        }
    }
}
