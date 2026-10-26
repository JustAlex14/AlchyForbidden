package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.essence.Essence;
import com.lealex.alchyforbidden.essence.Inscription;
import com.lealex.alchyforbidden.essence.Inscriptions;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.lealex.alchyx.AlchyXClientConfig;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * What the carvings look like on a body, for every player near (the carvings are synced to everyone who sees the
 * player), stronger the more the body carries (Inscriptions.power): Echo Wings spread wings of echo while it flies
 * (folded on its back otherwise), Chained Wings grey wings while it glides, the walkers mark what
 * they stand on, the skins answer a blow, the heart beats, the eyes glint, and the corruption itself winds round a body
 * carrying two carvings or more. All particles, following AlchyX's particle amount.
 */
public final class InscriptionVisuals {
    private InscriptionVisuals() {}

    /** Per player: last hurt time, last on-ground (1/0), last swinging (1/0). */
    private static final Map<Player, int[]> LAST = new WeakHashMap<>();

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused() || minecraft.player == null) return;
        Vec3 eye = minecraft.player.position();
        long time = level.getGameTime();
        RandomSource random = level.getRandom();
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        for (Player player : level.players()) {
            if (player.isSpectator() || player.distanceToSqr(eye) > 48 * 48) continue;
            List<Inscription> carved = Inscriptions.of(player);
            if (carved.isEmpty()) continue;
            boolean self = player == minecraft.player && firstPerson; // (nothing in its own eyes)
            int[] last = LAST.computeIfAbsent(player, p -> new int[] {0, 1, 0});
            boolean hurt = player.hurtTime > last[0];
            boolean onGround = player.onGround();
            boolean jumped = last[1] == 1 && !onGround && player.getDeltaMovement().y > 0.2;
            boolean swung = player.swinging && last[2] == 0;
            last[0] = player.hurtTime;
            last[1] = onGround ? 1 : 0;
            last[2] = player.swinging ? 1 : 0;
            double power = Inscriptions.power(player);
            for (Inscription inscription : carved) {
                show(level, player, inscription, time, random, self, hurt, jumped, swung, power);
            }
            reserveGain(level, player, random);
        }
    }

    private static void show(ClientLevel level, Player player, Inscription inscription, long time, RandomSource random,
                             boolean self, boolean hurt, boolean jumped, boolean swung, double power) {
        double x = player.getX(), y = player.getY(), z = player.getZ();
        int more = (int) Math.round(power * 1.5); // a stronger body shows more
        switch (inscription) {
            case ECHO_WINGS -> { // souls pouring off the spread wings while it flies or glides (the wings: client/WingLayer)
                boolean ownFlight = player.getAbilities().flying && !player.isCreative() && !player.isSpectator(); // not creative flight
                if (ownFlight || player.isFallFlying()) {
                    for (int i = 0; i < 2 + more; i++) {
                        if (!spawn(random)) continue;
                        Vec3 at = wingTip(player, random.nextBoolean() ? 1 : -1, 0.3 + random.nextDouble() * 1.7 * Math.pow(power, 0.3))
                                .add(0, (random.nextDouble() - 0.6) * 1.2, 0);
                        ParticleOptions soul = random.nextInt(3) == 0 ? ParticleTypes.SOUL : random.nextInt(4) == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SCULK_SOUL;
                        level.addParticle(soul, at.x, at.y, at.z, (random.nextDouble() - 0.5) * 0.02, 0.02 + random.nextDouble() * 0.03, (random.nextDouble() - 0.5) * 0.02);
                    }
                } else if (random.nextInt(30) == 0) { // a soul now and then off the folded wings
                    Vec3 at = wingTip(player, random.nextBoolean() ? 1 : -1, 0.4).add(0, -0.3, 0);
                    level.addParticle(ParticleTypes.SCULK_SOUL, at.x, at.y, at.z, 0, 0.02, 0);
                }
            }
            case CHAINED_WINGS -> { // violet sparks off the iron while it glides
                if (player.isFallFlying() && !Inscriptions.has(player, Inscription.ECHO_WINGS)) {
                    for (int i = 0; i < 1 + more / 2; i++) {
                        if (!spawn(random)) continue;
                        Vec3 at = wingTip(player, random.nextBoolean() ? 1 : -1, 0.3 + random.nextDouble() * 1.7).add(0, (random.nextDouble() - 0.6) * 1.0, 0);
                        level.addParticle(random.nextBoolean() ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.SMOKE, at.x, at.y, at.z, 0, 0.01, 0);
                    }
                }
            }
            case SKY_WALKER -> { // a ring of pale light under its feet while the air holds it
                if (!onSolid(level, player) && !player.getAbilities().flying && !player.isFallFlying() && Math.abs(player.getDeltaMovement().y) < 0.01) {
                    int n = 6 + more * 2;
                    double r = 0.5 + 0.15 * power;
                    for (int i = 0; i < n; i++) {
                        double a = time * 0.1 + i * Math.PI * 2 / n;
                        level.addParticle(dust(0xD8F0FF, 0.8), x + Math.cos(a) * r, y + 0.02, z + Math.sin(a) * r, 0, 0, 0);
                    }
                    if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.ENCHANT, x, y + 0.6, z, (random.nextDouble() - 0.5) * 1.5, -0.6, (random.nextDouble() - 0.5) * 1.5);
                }
            }
            case WATER_WALKER, LAVA_WALKER -> { // ripples where it stands on the surface
                FluidState fluid = level.getFluidState(BlockPos.containing(x, y - 0.1, z));
                boolean water = inscription == Inscription.WATER_WALKER;
                if (!fluid.isEmpty() && fluid.is(water ? FluidTags.WATER : FluidTags.LAVA) && spawn(random)) {
                    for (int i = 0; i < more; i++) {
                        double a = random.nextDouble() * Math.PI * 2, r = 0.3 + random.nextDouble() * 0.3;
                        level.addParticle(water ? ParticleTypes.SPLASH : ParticleTypes.SMALL_FLAME, x + Math.cos(a) * r, y + 0.05, z + Math.sin(a) * r, 0, 0.03, 0);
                    }
                    if (!water && random.nextInt(4) == 0) level.addParticle(ParticleTypes.LAVA, x, y + 0.1, z, 0, 0, 0);
                }
            }
            case SWIFT_STRIDE -> {
                if (player.isSprinting() && player.onGround() && spawn(random)) {
                    for (int i = 0; i < more; i++) {
                        level.addParticle(dust(0x7AE8B0, 0.9), x + (random.nextDouble() - 0.5) * 0.5, y + 0.1 + random.nextDouble() * 0.3, z + (random.nextDouble() - 0.5) * 0.5, 0, 0, 0);
                    }
                    if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.CLOUD, x, y + 0.1, z, -player.getDeltaMovement().x * 0.3, 0.02, -player.getDeltaMovement().z * 0.3);
                }
            }
            case HIGH_LEAP -> {
                if (jumped) {
                    int n = 10 + more * 3;
                    for (int i = 0; i < n; i++) {
                        double a = i * Math.PI * 2 / n;
                        level.addParticle(dust(0x9FE6A0, 1.0), x + Math.cos(a) * 0.4, y + 0.05, z + Math.sin(a) * 0.4, Math.cos(a) * 0.12, 0.03, Math.sin(a) * 0.12);
                    }
                    level.addParticle(ParticleTypes.CLOUD, x, y, z, 0, -0.05, 0);
                }
            }
            case SILENT_STEP -> { // sculk prints where it walks
                if (player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() > 0.002 && time % 5 == 0) {
                    level.addParticle(dust(0x0E7A70, 1.1), x, y + 0.02, z, 0, 0, 0);
                    if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.SCULK_CHARGE_POP, x, y + 0.05, z, 0, 0.01, 0);
                }
            }
            case FIRE_SKIN -> {
                if ((player.isOnFire() || player.isInLava()) && spawn(random)) {
                    for (int i = 0; i <= more; i++) {
                        level.addParticle(ParticleTypes.FLAME, x + (random.nextDouble() - 0.5) * 0.7, y + random.nextDouble() * 1.8, z + (random.nextDouble() - 0.5) * 0.7, 0, 0.03, 0);
                    }
                } else if (random.nextInt(Math.max(8, 40 - more * 6)) == 0) { // embers now and then
                    level.addParticle(ParticleTypes.SMALL_FLAME, x + (random.nextDouble() - 0.5) * 0.5, y + 0.4 + random.nextDouble(), z + (random.nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
                }
            }
            case THORN_SKIN -> {
                if (hurt) {
                    for (int i = 0; i < 12 + more * 4; i++) {
                        double a = random.nextDouble() * Math.PI * 2, h = 0.3 + random.nextDouble() * 1.4;
                        level.addParticle(dust(0x5A9A3A, 1.1), x + Math.cos(a) * 0.35, y + h, z + Math.sin(a) * 0.35, Math.cos(a) * 0.25, 0, Math.sin(a) * 0.25);
                    }
                    level.addParticle(ParticleTypes.CRIT, x, y + 1, z, 0, 0.1, 0);
                }
            }
            case STONE_SHELL -> {
                if (hurt) {
                    BlockParticleOption stone = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE.defaultBlockState());
                    for (int i = 0; i < 12 + more * 4; i++) {
                        level.addParticle(stone, x + (random.nextDouble() - 0.5) * 0.8, y + 0.3 + random.nextDouble() * 1.4, z + (random.nextDouble() - 0.5) * 0.8, 0, 0.1, 0);
                    }
                } else if (random.nextInt(60) == 0) { // grit settling off it
                    level.addParticle(dust(0x8C8696, 0.6), x + (random.nextDouble() - 0.5) * 0.6, y + 1.2, z + (random.nextDouble() - 0.5) * 0.6, 0, -0.02, 0);
                }
            }
            case SECOND_LEAP -> { // a faint wind about the legs
                if (!onSolid(level, player) && random.nextInt(4) == 0) {
                    level.addParticle(dust(0xA0F0E0, 0.6), x + (random.nextDouble() - 0.5) * 0.5, y + 0.2, z + (random.nextDouble() - 0.5) * 0.5, 0, -0.03, 0);
                }
            }
            case WALL_CLIMBER -> { // grit falling from the climbing hands and feet
                if (player.horizontalCollision && !onSolid(level, player) && random.nextInt(2) == 0) {
                    level.addParticle(dust(0x6A5A3A, 0.7), x + (random.nextDouble() - 0.5) * 0.6, y + random.nextDouble() * 1.6, z + (random.nextDouble() - 0.5) * 0.6, 0, -0.05, 0);
                }
            }
            case BARK_SKIN -> { // splinters when struck
                if (hurt) {
                    BlockParticleOption bark = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LOG.defaultBlockState());
                    for (int i = 0; i < 10 + more * 3; i++) {
                        level.addParticle(bark, x + (random.nextDouble() - 0.5) * 0.8, y + 0.3 + random.nextDouble() * 1.4, z + (random.nextDouble() - 0.5) * 0.8, 0, 0.1, 0);
                    }
                }
            }
            case DREAD_MANTLE -> { // a dark mantle hanging off the shoulders
                if (random.nextInt(Math.max(3, 12 - more * 2)) == 0) {
                    Vec3 back = Vec3.directionFromRotation(0, player.yBodyRot).scale(-0.3);
                    level.addParticle(ParticleTypes.SMOKE, x + back.x + (random.nextDouble() - 0.5) * 0.5, y + 1.3, z + back.z + (random.nextDouble() - 0.5) * 0.5, 0, -0.02, 0);
                }
            }
            case SOUL_SIPHON, HEAVY_HAND -> { // the hands smoulder
                if (random.nextInt(Math.max(4, 20 - more * 3)) == 0) {
                    level.addParticle(dust(inscription.color(), 0.6), x + (random.nextDouble() - 0.5) * 0.7, y + 0.8, z + (random.nextDouble() - 0.5) * 0.7, 0, 0.01, 0);
                }
            }
            case SECOND_BREATH, IRON_GUT, PALE_MIND, TOUGH_METABOLISM -> { // a slow pulse of its colour now and then
                if ((time + player.getId() * 5L) % 80 == 0) {
                    for (int i = 0; i < 4 + more; i++) {
                        double a = random.nextDouble() * Math.PI * 2;
                        level.addParticle(dust(inscription.color(), 0.7), x + Math.cos(a) * 0.3, y + 1.1, z + Math.sin(a) * 0.3, 0, 0.02, 0);
                    }
                }
            }
            case SHADOW_SKIN -> {
                if (player.isShiftKeyDown() && spawn(random)) {
                    for (int i = 0; i < more; i++) {
                        level.addParticle(random.nextInt(4) == 0 ? ParticleTypes.SQUID_INK : ParticleTypes.LARGE_SMOKE, x + (random.nextDouble() - 0.5) * 0.7,
                                y + random.nextDouble() * 1.6, z + (random.nextDouble() - 0.5) * 0.7, 0, 0.01, 0);
                    }
                }
            }
            case NIGHT_EYES, ECHO_SIGHT, CLEAR_MIND -> { // the eyes glint
                if (!self && random.nextInt(Math.max(4, 24 - more * 4)) == 0) {
                    Vec3 look = Vec3.directionFromRotation(0, player.getYHeadRot());
                    Vec3 side = new Vec3(-look.z, 0, look.x).scale(0.12);
                    Vec3 at = player.getEyePosition().add(look.scale(0.28));
                    DustParticleOptions glint = dust(inscription.color(), 0.8);
                    level.addParticle(glint, at.x + side.x, at.y, at.z + side.z, 0, 0, 0);
                    level.addParticle(glint, at.x - side.x, at.y, at.z - side.z, 0, 0, 0);
                }
                if (inscription == Inscription.CLEAR_MIND && time % 6 == 0) { // a thin halo
                    for (int i = 0; i < 6; i++) {
                        double a = time * 0.05 + i * Math.PI / 3;
                        level.addParticle(dust(0xE6E0FF, 0.5), x + Math.cos(a) * 0.3, y + player.getBbHeight() + 0.25, z + Math.sin(a) * 0.3, 0, 0, 0);
                    }
                }
            }
            case DEEP_HEART -> { // it beats, faster when hurt
                int beat = player.getHealth() < player.getMaxHealth() * 0.5F ? 20 : 40;
                if ((time + player.getId() * 7L) % beat == 0) {
                    int n = 8 + more * 3;
                    for (int i = 0; i < n; i++) {
                        double a = i * Math.PI * 2 / n;
                        level.addParticle(dust(0x7A3ED6, 1.0), x + Math.cos(a) * 0.3, y + 1.15, z + Math.sin(a) * 0.3, Math.cos(a) * 0.08, 0, Math.sin(a) * 0.08);
                    }
                    level.addParticle(dust(0xD0304A, 1.3), x, y + 1.15, z, 0, 0, 0);
                }
            }
            case HOLLOW_LUNGS -> {
                if (player.isUnderWater() && time % 6 == 0) {
                    Vec3 mouth = player.getEyePosition().add(player.getLookAngle().scale(0.3)).add(0, -0.15, 0);
                    level.addParticle(ParticleTypes.BUBBLE, mouth.x, mouth.y, mouth.z, 0, 0.05, 0);
                }
            }
            case ESSENCE_RESERVE -> { // what it holds, circling its chest
                if (time % 4 == 0) {
                    Essence essence = Essence.values()[(int) ((time / 4) % 3)];
                    if (Inscriptions.reserve(player, essence) > 0) {
                        double a = time * 0.2;
                        ParticleOptions mote = switch (essence) {
                            case WRATH -> ModRegistries.WRATH_MOTE.get();
                            case SORROW -> ModRegistries.SORROW_MOTE.get();
                            case CORRUPTION -> ModRegistries.CORRUPTION_MOTE.get();
                        };
                        level.addParticle(mote, x + Math.cos(a) * 0.45, y + 1.1, z + Math.sin(a) * 0.45, 0, 0, 0);
                    }
                }
            }
            case QUICK_HANDS -> {
                if (swung && !self) hand(level, player, ParticleTypes.CRIT, 4 + more * 2, random);
            }
            case WRATHFUL_ARMS -> {
                if (swung && !self && player.getHealth() < player.getMaxHealth() * 0.5F) hand(level, player, ParticleTypes.FLAME, 5 + more * 2, random);
            }
            case RIFT_GRIP -> {
                if (player.swinging && !self && time % 2 == 0) hand(level, player, ParticleTypes.REVERSE_PORTAL, 2 + more, random);
            }
            case LONG_REACH -> { // a violet streak out past the arm's own reach
                if (swung) {
                    Vec3 from = player.getEyePosition(), look = player.getLookAngle();
                    for (double d = 1.5; d <= 3.0 + 2.0 * power; d += 0.3) {
                        Vec3 p = from.add(look.scale(d));
                        level.addParticle(dust(0x9C7AE8, 0.8), p.x, p.y, p.z, 0, 0, 0);
                    }
                }
            }
        }
    }

    /** About where a wing's tip is (side 1 / -1), for a fleck off it. */
    private static Vec3 wingTip(Player player, int side, double reach) {
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        return player.position().add(0, 1.3, 0).subtract(forward.scale(0.4)).add(right.scale(side * reach));
    }

    /** What each player's carved reserve held last tick (to see it fill). */
    private static final java.util.Map<Player, int[]> RESERVE = new java.util.WeakHashMap<>();

    /**
     * Essence coming into a carved reserve (a kill's Wrath, the dark's Sorrow, an infuser bound to it...): a burst of
     * that essence's motes drawn in from round the body to the chest, more for more.
     */
    private static void reserveGain(ClientLevel level, Player player, RandomSource random) {
        if (!Inscriptions.has(player, Inscription.ESSENCE_RESERVE)) {
            RESERVE.remove(player);
            return;
        }
        int[] last = RESERVE.computeIfAbsent(player, p -> {
            int[] now = new int[Essence.values().length];
            for (Essence e : Essence.values()) now[e.ordinal()] = Inscriptions.reserve(p, e);
            return now;
        });
        for (Essence essence : Essence.values()) {
            int now = Inscriptions.reserve(player, essence), gain = now - last[essence.ordinal()];
            last[essence.ordinal()] = now;
            if (gain <= 0) continue;
            ParticleOptions mote = switch (essence) {
                case WRATH -> ModRegistries.WRATH_MOTE.get();
                case SORROW -> ModRegistries.SORROW_MOTE.get();
                case CORRUPTION -> ModRegistries.CORRUPTION_MOTE.get();
            };
            double cx = player.getX(), cy = player.getY() + 1.1, cz = player.getZ();
            int count = AlchyXClientConfig.count(Math.min(16, 3 + gain / 8), random);
            for (int i = 0; i < count; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = 0.9 + random.nextDouble() * 0.5, h = (random.nextDouble() - 0.5) * 1.2;
                double x = cx + Math.cos(a) * r, y = cy + h, z = cz + Math.sin(a) * r;
                level.addParticle(mote, x, y, z, (cx - x) * 0.12, (cy - y) * 0.12, (cz - z) * 0.12);
            }
        }
    }

    private static void hand(ClientLevel level, Player player, ParticleOptions particle, int count, RandomSource random) {
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        double arm = player.getMainArm() == HumanoidArm.RIGHT ? -1 : 1;
        Vec3 at = player.position().add(0, 1.05, 0).add(forward.scale(0.45)).add(right.scale(arm * 0.35));
        for (int i = 0; i < count; i++) {
            level.addParticle(particle, at.x, at.y, at.z, (random.nextDouble() - 0.5) * 0.1, random.nextDouble() * 0.06, (random.nextDouble() - 0.5) * 0.1);
        }
    }

    private static DustParticleOptions dust(int color, double scale) {
        return new DustParticleOptions(color, (float) Math.max(0.2, Math.min(3.5, scale)));
    }

    /** Standing on something solid (the sky walker's ring is for the air only). */
    private static boolean onSolid(ClientLevel level, Player player) {
        return player.onGround() && !level.getBlockState(BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ())).isAir();
    }

    private static boolean spawn(RandomSource random) {
        return AlchyXClientConfig.spawn(random);
    }
}
