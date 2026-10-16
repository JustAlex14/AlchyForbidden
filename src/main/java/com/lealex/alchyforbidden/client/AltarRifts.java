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
 * The great rift over a growing altar (client). It tears open above the altar while the altar gathers itself, and
 * every part the altar grows flies out of it on an arc down to its place, small at first, turning, growing to its
 * size and coming to rest square; there the hidden shell is shown. A part the next tier clears flies up into it.
 * Once the tier stands the rift closes. Flying parts are drawn full bright (they come out of the rift's light).
 */
public final class AltarRifts {
    private AltarRifts() {}

    private static final int VIOLET = 0xB98CFF;
    private static final int OPEN_TICKS = 40, CLOSE_TICKS = 20, FLY_TICKS = 20, SINK_TICKS = 18, RISE_TICKS = 60;
    private static final ContextKey<Frame> RENDER_KEY =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "altar_rifts"));

    private static final class Rift {
        final BlockPos at;
        final float size;
        int age = 0;
        int closing = -1; // ticks since it began to close, -1 while open

        Rift(BlockPos at, float size) {
            this.at = at;
            this.size = size;
        }

        float open(float partial) {
            float opened = ease((age + partial) / OPEN_TICKS);
            if (closing < 0) return opened;
            return opened * (1 - ease((closing + partial) / CLOSE_TICKS));
        }
    }

    private static final class Part {
        final BlockPos rift, pos;
        final BlockState look;
        final boolean sinking, rising;
        int age = 0;

        Part(BlockPos rift, BlockPos pos, BlockState look, boolean sinking, boolean rising) {
            this.rift = rift;
            this.pos = pos;
            this.look = look;
            this.sinking = sinking;
            this.rising = rising;
        }

        int length() {
            return rising ? RISE_TICKS : sinking ? SINK_TICKS : FLY_TICKS;
        }
    }

    private record ShownRift(BlockPos at, float size, float open, float time) {}

    private record ShownPart(BlockPos rift, BlockPos pos, BlockState look, boolean sinking, boolean rising, float age) {}

    private record Frame(List<ShownRift> rifts, List<ShownPart> parts) {}

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
            return switch (essence) {
                case 1 -> new net.minecraft.core.particles.DustParticleOptions(0xD0304A, 1.1F);
                case 2 -> net.minecraft.core.particles.ParticleTypes.SOUL;
                default -> ModRegistries.SCULK_FLECK.get();
            };
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

    private static final int STREAM_TICKS = 26;
    private static final List<Stream> STREAMS = new ArrayList<>();
    private static final List<Part> PARTS = new ArrayList<>();

    public static void receive(AltarRiftPayload payload) {
        switch (payload.kind()) {
            case AltarRiftPayload.OPEN -> RIFTS.put(payload.rift(), new Rift(payload.rift(), payload.size()));
            case AltarRiftPayload.ABSORB -> STREAMS.add(new Stream(Vec3.atCenterOf(payload.rift()), Vec3.atCenterOf(payload.pos()).add(0, 1.0, 0), payload.size()));
            case AltarRiftPayload.TAKEN -> {
                RIFTS.put(payload.rift(), new Rift(payload.rift(), 3));
                WardenTaken.start(payload.pos(), payload.rift(), payload.size());
            }
            case AltarRiftPayload.RISE -> PARTS.add(new Part(payload.pos(), payload.pos(), payload.look(), false, true));
            case AltarRiftPayload.CLOSE -> {
                Rift rift = RIFTS.get(payload.rift());
                if (rift != null && rift.closing < 0) rift.closing = 0;
            }
            default -> {
                // a part (if the opening was missed, joining mid-growth: the rift opens now)
                RIFTS.computeIfAbsent(payload.rift(), at -> new Rift(at, Math.max(3, payload.size())));
                PARTS.add(new Part(payload.rift(), payload.pos(), payload.look(), payload.kind() == AltarRiftPayload.SINK, false));
            }
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
            PARTS.clear();
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
            for (int i = 0; i < 3; i++) { // the head of the stream, and a tail a little behind it
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
                Vec3 c = Vec3.atCenterOf(rift.at);
                double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * rift.size * 0.3;
                level.addParticle(ModRegistries.RIFT_MOTE.get(), c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r,
                        Math.cos(a) * 0.02, -0.03, Math.sin(a) * 0.02);
            }
        }
        for (Iterator<Part> it = PARTS.iterator(); it.hasNext(); ) {
            Part part = it.next();
            part.age++;
            if (part.rising) { // grinding up out of the stone: dust and grit round its foot
                if (part.age % 4 == 0 && part.age < RISE_TICKS) {
                    BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, part.look);
                    double side = random.nextDouble() - 0.5;
                    boolean alongX = random.nextBoolean();
                    double px = part.pos.getX() + 0.5 + (alongX ? side : (random.nextBoolean() ? 0.52 : -0.52));
                    double pz = part.pos.getZ() + 0.5 + (alongX ? (random.nextBoolean() ? 0.52 : -0.52) : side);
                    level.addParticle(dust, px, part.pos.getY() + rise(part, part.age) - 0.95, pz, 0, 0.05, 0);
                }
            } else if (part.age % 3 == 0) { // a thin trail of the rift behind it
                Vec3 at = flight(part, part.age);
                level.addParticle(ModRegistries.RIFT_MOTE.get(), at.x, at.y, at.z, 0, 0, 0);
            }
            if (part.age >= part.length() && (!part.rising || arrived(level, part) || part.age > RISE_TICKS + 200)) {
                if (!part.sinking) land(level, part);
                it.remove();
            }
        }
    }

    /** The part comes to rest: its shell is shown, sculk flecks puff off it. */
    private static void land(ClientLevel level, Part part) {
        if (level.getBlockEntity(part.pos) instanceof ChamberShellBlockEntity shell && shell.isClientHidden()) shell.setClientHidden(false);
        RandomSource random = level.getRandom();
        Vec3 c = Vec3.atCenterOf(part.pos);
        for (int i = 0; i < 6; i++) {
            level.addParticle(ModRegistries.SCULK_FLECK.get(), c.x + (random.nextDouble() - 0.5) * 0.8, c.y + 0.4,
                    c.z + (random.nextDouble() - 0.5) * 0.8, 0, 0.04, 0);
        }
    }

    /**
     * A risen part stays drawn at its place until the real block is there to take over (a shell, or the very block:
     * the altar's core and the pedestal it leaves only arrive when the server moves the core).
     */
    private static boolean arrived(ClientLevel level, Part part) {
        BlockState there = level.getBlockState(part.pos);
        return there.equals(part.look) || com.lealex.alchyx.multiblock.ShellParts.isShell(there);
    }

    /** How far a rising part has come up (0 at the start, one block below its place; 1 there). */
    private static float rise(Part part, float age) {
        return ease(age / RISE_TICKS);
    }

    /** Where a part is along its way (the middle of the block): an arc between the rift and its place. */
    private static Vec3 flight(Part part, float age) {
        return flight(part.rift, part.pos, part.sinking, age / part.length());
    }

    private static Vec3 flight(BlockPos rift, BlockPos pos, boolean sinking, float t) {
        t = Mth.clamp(t, 0, 1);
        float k = sinking ? ease(t) : 1 - (1 - t) * (1 - t); // out of the rift fast, slowing as it lands
        if (sinking) k = 1 - k;
        Vec3 from = Vec3.atCenterOf(rift), to = Vec3.atCenterOf(pos);
        Vec3 control = from.add(to).scale(0.5).add(0, 2.0, 0); // a little arc over the straight line
        double u = 1 - k;
        return from.scale(u * u).add(control.scale(2 * u * k)).add(to.scale(k * k));
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static void onExtract(ExtractLevelRenderStateEvent event) {
        if (RIFTS.isEmpty() && PARTS.isEmpty()) {
            event.getRenderState().setRenderData(RENDER_KEY, null);
            return;
        }
        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<ShownRift> rifts = new ArrayList<>();
        for (Rift rift : RIFTS.values()) rifts.add(new ShownRift(rift.at, rift.size, rift.open(partial), rift.age + partial));
        List<ShownPart> parts = new ArrayList<>();
        for (Part part : PARTS) parts.add(new ShownPart(part.rift, part.pos, part.look, part.sinking, part.rising, part.age + partial));
        event.getRenderState().setRenderData(RENDER_KEY, new Frame(rifts, parts));
    }

    /** A moving block lit full bright, whatever the light where it ends up. */
    private static final class Lit extends MovingBlockRenderState {
        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return 15;
        }
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
            poseStack.translate(rift.at().getX() + 0.5 - camera.x, rift.at().getY() + 0.5 - camera.y, rift.at().getZ() + 0.5 - camera.z);
            float breath = 1 + 0.04F * Mth.sin(rift.time() * 0.15F); // it heaves a little, never still
            RiftDecal.submit(poseStack, collector, event.getLevelRenderState().cameraRenderState, rift.size() * breath,
                    (int) (rift.at().asLong() & 3), rift.open(), VIOLET);
            poseStack.popPose();
        }
        for (ShownPart part : frame.parts()) {
            if (part.rising()) { // slid straight up out of the block below, lit as the place it rises into
                float up = ease(Math.min(1, part.age() / RISE_TICKS));
                poseStack.pushPose();
                poseStack.translate(part.pos().getX() - camera.x, part.pos().getY() - 1 + up - camera.y, part.pos().getZ() - camera.z);
                MovingBlockRenderState state = new MovingBlockRenderState();
                state.randomSeedPos = part.pos();
                state.blockPos = part.pos();
                state.blockState = part.look();
                state.biome = minecraft.level.getBiome(part.pos());
                state.cardinalLighting = minecraft.level.cardinalLighting();
                state.lightEngine = minecraft.level.getLightEngine();
                collector.submitMovingBlock(poseStack, state);
                poseStack.popPose();
                continue;
            }
            float t = Mth.clamp(part.age() / (part.sinking() ? SINK_TICKS : FLY_TICKS), 0, 1);
            Vec3 at = flight(part.rift(), part.pos(), part.sinking(), t);
            float grown = part.sinking() ? 1 - ease(t) : ease(t);
            float scale = 0.3F + 0.7F * grown;
            float turn = (1 - grown) * 270F; // turning on the way, square when it lands
            poseStack.pushPose();
            poseStack.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(turn));
            poseStack.scale(scale, scale, scale);
            poseStack.translate(-0.5, -0.5, -0.5);
            MovingBlockRenderState state = new Lit();
            state.randomSeedPos = part.pos();
            state.blockPos = part.pos();
            state.blockState = part.look();
            state.biome = minecraft.level.getBiome(part.pos());
            state.cardinalLighting = minecraft.level.cardinalLighting();
            collector.submitMovingBlock(poseStack, state);
            poseStack.popPose();
        }
    }
}
