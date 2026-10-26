package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.network.AltarRiftPayload;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.lealex.alchyx.block.entity.ChamberShellBlockEntity;
import com.lealex.alchyx.client.RiftDecal;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * The great rift over a growing multiblock (client). It tears open above the core while it gathers itself, goes up
 * with a rising core, and closes once the tier stands; the parts flying out of it and into it are AlchyX's
 * (client/growth/GrowthParts, style alchyforbidden:rift). Also the essence streams and the warden taken by a rift.
 */
public final class AltarRifts {
    private AltarRifts() {}

    private static final int VIOLET = 0xB98CFF;
    private static final int OPEN_TICKS = 40, CLOSE_TICKS = 20, FLY_TICKS = 20, SINK_TICKS = 18; // (a rise lasts AltarRiftPayload.riseTicks)
    private static final ContextKey<Frame> RENDER_KEY =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "altar_rifts"));

    private static final class Rift {
        final BlockPos at;
        final float size;
        int age = 0;
        int closing = -1; // ticks since it began to close, -1 while open
        // moving with the core under it: from where it was to `moveTo` (blocks from `at`) over moveTicks from moveStart
        double fromY = 0, moveTo = 0;
        int moveStart = 0, moveTicks = 0;

        Rift(BlockPos at, float size) {
            this.at = at;
            this.size = size;
        }

        /** How far above where it opened it is now (it slides up with a rising core). */
        double lift(float partial) {
            if (moveTicks <= 0) return moveTo;
            float t = Mth.clamp((age + partial - moveStart) / moveTicks, 0, 1);
            return fromY + (moveTo - fromY) * ease(t);
        }

        Vec3 middle(float partial) {
            return Vec3.atCenterOf(at).add(0, lift(partial), 0);
        }

        float open(float partial) {
            float opened = ease((age + partial) / OPEN_TICKS);
            if (closing < 0) return opened;
            return opened * (1 - ease((closing + partial) / CLOSE_TICKS));
        }
    }

    private record ShownRift(BlockPos at, Vec3 middle, float size, float open, float time) {}

    private record Frame(List<ShownRift> rifts) {}

    private static final Map<BlockPos, Rift> RIFTS = new HashMap<>();

    /** Sculk Corruption drawn into an altar: a stream of sculk from where the mob died, arcing into the eye. */
    private static final class Stream {
        final Vec3 from, to;
        final int essence; // 1 Wrath, 2 Sorrow, else Sculk Corruption
        int age = 0;

        Stream(Vec3 from, Vec3 to, int essence) {
            this.from = from;
            this.to = to;
            this.essence = essence;
        }

        net.minecraft.core.particles.ParticleOptions trail() {
            return trailOf(essence);
        }

        net.minecraft.core.particles.ParticleOptions puff() {
            return switch (essence) {
                case 1 -> net.minecraft.core.particles.ParticleTypes.FLAME;
                case 2 -> net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME;
                default -> net.minecraft.core.particles.ParticleTypes.SCULK_SOUL;
            };
        }

        Vec3 at(float t) {
            Vec3 control = from.add(to).scale(0.5).add(0, 1.5 + from.distanceTo(to) * 0.15, 0);
            double u = 1 - t;
            return from.scale(u * u).add(control.scale(2 * u * t)).add(to.scale(t * t));
        }
    }

    /** The particle an essence's stream is made of (1 Wrath, 2 Sorrow, else Sculk Corruption). */
    private static net.minecraft.core.particles.ParticleOptions glowOf(int essence) {
        return switch (essence) {
            case 1 -> net.minecraft.core.particles.ParticleTypes.SMALL_FLAME;
            case 2 -> net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME;
            default -> net.minecraft.core.particles.ParticleTypes.SCULK_SOUL;
        };
    }

    private static net.minecraft.core.particles.ParticleOptions trailOf(int essence) {
        return switch (essence) {
            case 1 -> ModRegistries.WRATH_MOTE.get();
            case 2 -> ModRegistries.SORROW_MOTE.get();
            default -> ModRegistries.CORRUPTION_MOTE.get();
        };
    }

    private static final int STREAM_TICKS = 26;
    private static final List<Stream> STREAMS = new ArrayList<>();

    public static void receive(AltarRiftPayload payload) {
        switch (payload.kind()) {
            case AltarRiftPayload.OPEN -> RIFTS.put(payload.rift(), new Rift(payload.rift(), payload.size()));
            case AltarRiftPayload.MOVE -> {
                Rift rift = RIFTS.get(payload.rift());
                if (rift != null) {
                    rift.fromY = rift.lift(0);
                    rift.moveTo = payload.pos().getY() - payload.rift().getY();
                    rift.moveStart = rift.age;
                    rift.moveTicks = Math.max(1, payload.size());
                }
            }
            case AltarRiftPayload.ABSORB -> STREAMS.add(new Stream(Vec3.atCenterOf(payload.rift()), Vec3.atCenterOf(payload.pos()).add(0, 1.0, 0), payload.size()));
            // essence sent along a link: AlchyX's guided stream, in the essence's particle, the path glimpsed in rift motes
            case AltarRiftPayload.GUIDE -> {
                Vec3 from = Vec3.atCenterOf(payload.rift()), to = Vec3.atCenterOf(payload.pos());
                com.lealex.alchyx.client.GuidedStreams.add(from, to, trailOf(payload.size()), ModRegistries.RIFT_MOTE.get(),
                        net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, AltarRiftPayload.GUIDE_TICKS);
                // a brighter companion on the same line (soul fire, flame, sculk soul), so a stream reads from afar and in daylight
                com.lealex.alchyx.client.GuidedStreams.add(from, to, glowOf(payload.size()), null, null, AltarRiftPayload.GUIDE_TICKS);
            }
            case AltarRiftPayload.TAKEN -> {
                RIFTS.put(payload.rift(), new Rift(payload.rift(), 3));
                WardenTaken.start(payload.pos(), payload.rift(), payload.size());
            }
            case AltarRiftPayload.CLOSE -> {
                Rift rift = RIFTS.get(payload.rift());
                if (rift != null && rift.closing < 0) rift.closing = 0;
            }
            default -> {}
        }
    }

    /** Closes the rift at that place (if open). */
    public static void closeRift(BlockPos at) {
        Rift rift = RIFTS.get(at);
        if (rift != null && rift.closing < 0) rift.closing = 0;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            RIFTS.clear();
            STREAMS.clear();
            return;
        }
        if (minecraft.isPaused()) return;
        RandomSource random = level.getRandom();
        for (Iterator<Stream> it = STREAMS.iterator(); it.hasNext(); ) {
            Stream stream = it.next();
            if (stream.age == 0) { // the charge leaves the body
                for (int i = 0; i < 6; i++) {
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.SCULK_CHARGE_POP, stream.from.x + (random.nextDouble() - 0.5),
                            stream.from.y - 0.3, stream.from.z + (random.nextDouble() - 0.5), 0, 0.02, 0);
                }
            }
            stream.age++;
            float t = Mth.clamp(stream.age / (float) STREAM_TICKS, 0, 1);
            int head = com.lealex.alchyx.AlchyXClientConfig.count(3, random);
            for (int i = 0; i < head; i++) { // the head of the stream, and a tail a little behind it
                Vec3 p = stream.at(Math.max(0, t - i * 0.04F));
                level.addParticle(stream.trail(), p.x + (random.nextDouble() - 0.5) * 0.15, p.y,
                        p.z + (random.nextDouble() - 0.5) * 0.15, 0, 0, 0);
            }
            if (stream.age % 3 == 0) {
                Vec3 p = stream.at(t);
                level.addParticle(stream.puff(), p.x, p.y, p.z, 0, 0.01, 0);
            }
            if (stream.age >= STREAM_TICKS) { // swallowed by the altar
                for (int i = 0; i < 8; i++) {
                    level.addParticle(stream.trail(), stream.to.x, stream.to.y, stream.to.z,
                            (random.nextDouble() - 0.5) * 0.15, (random.nextDouble() - 0.5) * 0.15, (random.nextDouble() - 0.5) * 0.15);
                }
                level.playLocalSound(stream.to.x, stream.to.y, stream.to.z, net.minecraft.sounds.SoundEvents.SCULK_CATALYST_BLOOM,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 0.7F, false);
                it.remove();
            }
        }
        for (Iterator<Rift> it = RIFTS.values().iterator(); it.hasNext(); ) {
            Rift rift = it.next();
            rift.age++;
            if (rift.closing >= 0 && ++rift.closing >= CLOSE_TICKS) {
                it.remove();
                continue;
            }
            // a few motes drift out of the rift and fall slowly toward the altar
            if (rift.open(0) > 0.5F && random.nextInt(3) == 0) {
                Vec3 c = rift.middle(0);
                double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * rift.size * 0.3;
                level.addParticle(ModRegistries.RIFT_MOTE.get(), c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r,
                        Math.cos(a) * 0.02, -0.03, Math.sin(a) * 0.02);
            }
        }
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static void onExtract(ExtractLevelRenderStateEvent event) {
        if (RIFTS.isEmpty()) {
            event.getRenderState().setRenderData(RENDER_KEY, null);
            return;
        }
        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<ShownRift> rifts = new ArrayList<>();
        for (Rift rift : RIFTS.values()) rifts.add(new ShownRift(rift.at, rift.middle(partial), rift.size, rift.open(partial), rift.age + partial));
        event.getRenderState().setRenderData(RENDER_KEY, new Frame(rifts));
    }

    public static void onSubmit(SubmitCustomGeometryEvent event) {
        Frame frame = event.getLevelRenderState().getRenderData(RENDER_KEY);
        Minecraft minecraft = Minecraft.getInstance();
        if (frame == null || minecraft.level == null) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        for (ShownRift rift : frame.rifts()) {
            if (rift.open() <= 0) continue;
            poseStack.pushPose();
            poseStack.translate(rift.middle().x - camera.x, rift.middle().y - camera.y, rift.middle().z - camera.z);
            float breath = 1 + 0.04F * Mth.sin(rift.time() * 0.15F); // it heaves a little, never still
            RiftDecal.submit(poseStack, collector, event.getLevelRenderState().cameraRenderState, rift.size() * breath,
                    (int) (rift.at().asLong() & 3), rift.open(), VIOLET);
            poseStack.popPose();
        }
    }
}
