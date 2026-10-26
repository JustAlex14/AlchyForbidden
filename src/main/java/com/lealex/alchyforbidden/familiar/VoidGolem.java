package com.lealex.alchyforbidden.familiar;

import com.lealex.alchyforbidden.network.AttackFxPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Void Golem (ring IV, a great one of the void, the biggest): a giant built of obsidian and crying obsidian round a
 * rift for a heart. Phase I: a slam that sends shockwave lines across the floor, floor slabs thrown that stay standing
 * as cover, a guard that blocks every blow from the front. Phase II, the shell cracked and faster: rubble orbits it and
 * only critical blows break the stones, its core burns a beam, it pulls everything in then blasts it away, and it mends
 * when left alone for five seconds. Phase III (Void-Rifted only): the shell bursts, the core floats free as a small black
 * hole pulling everything in, the shell's pieces orbit it and crash down. As a familiar it rests as its floating core;
 * in a fight it is a tank: it draws monsters to itself, takes the blows, throws slabs.
 * In its arena it never lets up: it leaps onto a prey that keeps away and rips boulders out of the floor to throw at
 * one far off, and plays a special every few seconds in every phase. Its great efforts leave it open: fists stuck in
 * the floor after a leap, staggered when its guard is broken from behind, its core venting after the beam (x1.5).
 */
public class VoidGolem extends RiftBeast {
    private static final int CORE = 0x8A4AE8, CRY = 0x6A3AA8, SHADOW = 0x100818;
    /** Its openings (visual bits 3-4 for the model): fists stuck in the floor, staggered, the core venting. */
    private static final int STUCK = 1, STAGGER = 2, VENT = 3;
    // the leap: crouched until LEAP_UP, in the air until LEAP_LAND
    private static final int LEAP_UP = 14, LEAP_LAND = 30, BOULDER_THROW = 14;
    private static final int[] STOMPS = {16, 36, 56};
    private static final int COLLAPSE_PULL = 34, COLLAPSE_BLAST = 58, DASH_GO = 16, DASH_END = 30;
    private static final double COLLAPSE_RADIUS = 7.5;
    private static final net.minecraft.resources.Identifier CRACKED_SPEED = net.minecraft.resources.Identifier.fromNamespaceAndPath("alchyforbidden", "void_golem_cracked");
    private static final net.minecraft.resources.Identifier UNBOUND_SPEED = net.minecraft.resources.Identifier.fromNamespaceAndPath("alchyforbidden", "void_core_speed");
    private static final net.minecraft.resources.Identifier UNBOUND_ARMOR = net.minecraft.resources.Identifier.fromNamespaceAndPath("alchyforbidden", "void_core_armor");
    private int shieldStones;
    private long shieldUntil, guardUntil, lastHurt, nextCrash;
    private float guardYaw;
    private Vec3 beamDir = Vec3.ZERO, slabAt = Vec3.ZERO;
    private final java.util.List<Vec3> lines = new java.util.ArrayList<>();
    private final java.util.Set<java.util.UUID> slamHit = new java.util.HashSet<>(); // each line throws a foe once
    private Vec3 slamFrom = Vec3.ZERO;
    // ---- the arena fight ----
    private long punishUntil, leapReady, nextCollapse;
    private int punishKind, backHits, steered = -1;
    private @org.jspecify.annotations.Nullable String lastSpecial;
    private Vec3 leapFrom = Vec3.ZERO, leapTo = Vec3.ZERO, crackHead = Vec3.ZERO, crackDir = Vec3.ZERO, dashTo = Vec3.ZERO, dashDir = Vec3.ZERO, holdAt = Vec3.ZERO;
    private boolean collapseCalm;
    /** The fissure's marks: each glows, then erupts at its tick. */
    private record Mark(Vec3 at, Vec3 dir, int tick) {}
    private final java.util.List<Mark> marks = new java.util.ArrayList<>();
    private final java.util.Set<java.util.UUID> specialHit = new java.util.HashSet<>(); // a special strikes each foe once

    /** An ability drives its body this tick: straight toward a point, never faster than maxStep a tick (the hold waits). */
    private void steer(Vec3 to, double maxStep) {
        Vec3 d = to.subtract(position());
        double l = d.length();
        setDeltaMovement(l > maxStep ? d.scale(maxStep / l) : d);
        getNavigation().stop();
        steered = tickCount;
    }

    /** Turns it toward a point (body, head and all), a little each tick. */
    private void face(Vec3 at) {
        float yaw = (float) (Math.atan2(at.z - getZ(), at.x - getX()) * (180 / Math.PI)) - 90F;
        yaw = net.minecraft.util.Mth.approachDegrees(getYRot(), yaw, 14F);
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    private Vec3 arenaCenter() {
        return arena() == null ? position() : Vec3.atBottomCenterOf(arena().above());
    }

    /** A point held within the arena's wall (never set back inside by the arena, which looks like a jump). */
    private Vec3 keepInside(Vec3 p) {
        if (arena() == null) return p;
        Vec3 c = arenaCenter(), off = p.subtract(c).multiply(1, 0, 1);
        double lim = com.lealex.alchyforbidden.Config.voidArenaRadius() - 3.5;
        return off.length() <= lim ? p : c.add(off.normalize().scale(lim)).add(0, p.y - c.y, 0);
    }

    /** The top of the floor under a point (searched a few blocks down and up). */
    private double floorAt(Vec3 p) {
        BlockPos.MutableBlockPos at = BlockPos.containing(p.x, p.y + 1, p.z).mutable();
        for (int i = 0; i < 10; i++) {
            if (!level().getBlockState(at).getCollisionShape(level(), at).isEmpty()) return at.getY() + 1;
            at.move(0, -1, 0);
        }
        return p.y;
    }

    private Vec3 onFloor(Vec3 p) {
        return new Vec3(p.x, floorAt(p), p.z);
    }

    /** An opening: held still, taking half again as much, its prey told to strike. */
    public boolean punished() {
        return arena() != null && !familiar() && level().getGameTime() < punishUntil;
    }

    private void punish(ServerLevel level, int kind, int ticks, String message) {
        punishKind = kind;
        punishUntil = level.getGameTime() + ticks;
        getNavigation().stop();
        if (getTarget() instanceof ServerPlayer sp) sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden." + message), true);
    }

    /** Hit from behind three times through its guard: the guard breaks and it staggers. */
    private void breakGuard(ServerLevel level) {
        guardUntil = 0;
        backHits = 0;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CRYING_OBSIDIAN.defaultBlockState()), getX(), getY() + 2.5, getZ(), 40, 0.8, 0.8, 0.8, 0.2);
        playSound(SoundEvents.ANVIL_DESTROY, 1.6F, 0.6F);
        playSound(SoundEvents.IRON_GOLEM_DAMAGE, 1.6F, 0.4F);
        punish(level, STAGGER, 40, "golem_staggered");
    }

    /** Sends one foe away from a point, up and out. */
    private void fling(LivingEntity near, Vec3 from, double out, double up) {
        Vec3 dir = near.position().subtract(from).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1e-4 ? Vec3.ZERO : dir.normalize();
        near.setDeltaMovement(near.getDeltaMovement().add(dir.x * out, up, dir.z * out));
        near.hurtMarked = true;
    }

    /** Foes within a flat radius of a point (and a little above and below it). */
    private java.util.List<LivingEntity> foesNear(ServerLevel level, Vec3 at, double radius, double height) {
        java.util.List<LivingEntity> found = new java.util.ArrayList<>();
        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius, height, radius), this::foe)) {
            double dx = near.getX() - at.x, dz = near.getZ() - at.z;
            if (dx * dx + dz * dz <= radius * radius) found.add(near);
        }
        return found;
    }

    public VoidGolem(EntityType<? extends VoidGolem> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.ATTACK_DAMAGE, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28) // heavy, but never slow enough to kite
                .add(Attributes.FLYING_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.ARMOR, 20.0)
                .add(Attributes.ARMOR_TOUGHNESS, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    public float dormantScale() {
        return 0.16F; // resting, the whole of it drawn small
    }

    // ---- Its entrance: the floor cracks and it rises out of it, dark; its core lights; it roars ----

    @Override
    protected Vec3 introPath(Vec3 home, float p) {
        float up = ease(p / 0.6F), shake = up < 1 ? 0.06F : 0; // from 3.4 down: its eyes (3.6 up) stay over the floor for the camera
        return home.add(Math.sin(p * 90) * shake, -3.4 * (1 - up), Math.cos(p * 77) * shake);
    }

    @Override
    protected void introEffects(ServerLevel level, int t, Vec3 home) {
        net.minecraft.world.level.block.state.BlockState floor = level.getBlockState(net.minecraft.core.BlockPos.containing(home).below());
        if (floor.isAir()) floor = net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState();
        if (t == 0) {
            for (int i = 0; i < 6; i++) { // the floor splitting round where it will rise
                double a = i * Math.PI / 3;
                fxLine(home, new Vec3(Math.cos(a), 0, Math.sin(a)), 6F, 0.5F, 70, com.lealex.alchyforbidden.network.AttackFxPayload.L_OBSIDIAN);
            }
            level.playSound(null, net.minecraft.core.BlockPos.containing(home), net.minecraft.sounds.SoundEvents.WARDEN_EMERGE, net.minecraft.sounds.SoundSource.HOSTILE, 1.6F, 0.4F);
        }
        if (t < 62) {
            for (int i = 0; i < 6; i++) {
                double a = level.getRandom().nextDouble() * Math.PI * 2, r = 1.2 + level.getRandom().nextDouble() * 1.2;
                level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, floor), home.x + Math.cos(a) * r, home.y + 0.2, home.z + Math.sin(a) * r, 1, 0, 0.1, 0, 0.15);
            }
            if (t % 4 == 0) level.playSound(null, net.minecraft.core.BlockPos.containing(home), net.minecraft.sounds.SoundEvents.STONE_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 0.4F);
        }
        if (t == 66) { // its core lights
            fxNova(position().add(0, 2.5, 0), 3F, 12, CORE);
            level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.5F);
        }
        if (t == 78) { // and it roars
            fxNova(home, 8F, 18, CORE);
            level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.RAVAGER_ROAR, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.4F);
            level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.5F);
        }
    }

    private static float ease(float x) {
        x = Math.max(0F, Math.min(1F, x));
        return x * x * (3 - 2 * x);
    }

    @Override
    public RiftCreature creature() {
        return RiftCreature.VOID_GOLEM;
    }

    @Override
    public int swingLength() {
        return 18;
    }

    private boolean unbound() {
        return phase() >= 3 && !familiar();
    }

    @Override
    protected boolean flies() {
        return unbound();
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected boolean moveAllowed(RiftCreature.Move move) {
        if (familiar()) return true;
        if (punished()) return false; // open to blows: no moves
        return switch (move.id()) {
            case "slam", "slab_throw", "stone_guard" -> phase() == 1;
            case "void_pull" -> phase() >= 2;
            default -> true;
        };
    }

    /** While a special plays or it is open to blows it stands its ground: no walking after its prey, no punches. */
    @Override
    protected void addAttackGoals() {
        goalSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.Goal() {
            {
                setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
            }

            @Override
            public boolean canUse() {
                return !level().isClientSide() && (specialShown() >= 0 || punished());
            }

            @Override
            public boolean requiresUpdateEveryTick() {
                return true;
            }

            @Override
            public void tick() {
                getNavigation().stop();
            }
        });
    }

    @Override
    protected int boltStyle(int color, int fire, boolean boomerang) {
        return AttackFxPayload.BOULDER;
    }

    @Override
    protected float boltSize() {
        return 1.7F;
    }

    @Override
    protected java.util.List<String> specialIds() {
        // the order is what the model is told: keep it
        return java.util.List.of("core_beam", "rubble_shield", "leap", "boulder", "fissure", "earthquake", "rock_rain", "dash", "collapse");
    }

    public boolean shielded() {
        return shieldStones > 0 && level().getGameTime() < shieldUntil;
    }

    public boolean guarding() {
        return level().getGameTime() < guardUntil;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && knows(15) && target instanceof LivingEntity living) { // Crushing Weight
            living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2), this);
            living.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 1), this);
        }
        if (hit) {
            playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.5F, 0.4F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()), target.getX(), target.getY() + 1, target.getZ(), 16, 0.4, 0.4, 0.4, 0.1);
        }
        return hit;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        boolean behind = false;
        if (guarding() && attacker != null) { // the guard: nothing from the front gets through
            Vec3 to = attacker.position().subtract(position()).multiply(1, 0, 1).normalize();
            double facing = to.dot(Vec3.directionFromRotation(0, guardYaw)); // the way its guard faces, not where it last walked
            if (facing > 0.3) {
                level.sendParticles(ParticleTypes.CRIT, getX() + to.x * 1.5, getY() + 2, getZ() + to.z * 1.5, 12, 0.3, 0.5, 0.3, 0.2);
                playSound(SoundEvents.SHIELD_BLOCK.value(), 1.5F, 0.5F);
                return false;
            }
            behind = facing < -0.3;
        }
        if (punished()) amount *= 1.5F; // an opening: fists stuck, staggered, venting
        if (knows(5) && (phase() == 1 || familiar())) amount *= familiar() ? 0.8F : 0.67F; // Obsidian Shell
        if (unbound()) amount *= 1.5F;                                                        // the bare core
        if (shielded()) { // the rubble: only a critical blow knocks a stone out
            boolean crit = source.getDirectEntity() instanceof Player player && player.fallDistance > 0 && !player.onGround() && attacker == player;
            if (crit) {
                shieldStones--;
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CRYING_OBSIDIAN.defaultBlockState()), getX(), getY() + 2, getZ(), 30, 1.2, 1, 1.2, 0.2);
                playSound(SoundEvents.STONE_BREAK, 1.5F, 0.6F);
                if (shieldStones <= 0) playSound(SoundEvents.GLASS_BREAK, 1.5F, 0.5F);
            }
            amount *= 0.25F;
        }
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt) {
            lastHurt = level.getGameTime();
            // three blows from behind through its guard break it (arena only: a familiar's guard just holds)
            if (behind && guarding() && arena() != null && !familiar()) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()), getX(), getY() + 2, getZ(), 12, 0.5, 0.6, 0.5, 0.15);
                playSound(SoundEvents.STONE_BREAK, 1.4F, 0.5F);
                if (++backHits >= 3) breakGuard(level);
            }
        }
        return hurt;
    }

    @Override
    protected void onPhase(ServerLevel level, int phase) {
        if (phase == 2) { // the shell cracks: faster
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()), getX(), getY() + 2, getZ(), 120, 1.2, 1.5, 1.2, 0.3);
            level.playSound(null, blockPosition(), SoundEvents.ANVIL_DESTROY, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.4F);
            modify(Attributes.MOVEMENT_SPEED, CRACKED_SPEED, 0.15);
            guardUntil = 0;
            punishUntil = 0;
        } else if (phase == 3) { // the shell bursts: its pieces fly, the core floats free
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI * 2 / 16;
                fireBolt(position().add(0, 2, 0), new Vec3(Math.cos(a), 0.15, Math.sin(a)), hitFor(1.0), CRY, 1, 0.8);
            }
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 2, getZ(), 1, 0, 0, 0, 0);
            level.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.4F);
            setNoGravity(true);
            this.moveControl = new net.minecraft.world.entity.ai.control.FlyingMoveControl(this, 20, true);
            this.navigation = new net.minecraft.world.entity.ai.navigation.FlyingPathNavigation(this, level);
            modify(Attributes.MOVEMENT_SPEED, UNBOUND_SPEED, 0.6);
            modify(Attributes.ARMOR, UNBOUND_ARMOR, -0.75);
            shieldStones = 0;
            punishUntil = 0;
            nextCollapse = level.getGameTime() + PHASE_TICKS + 200; // its first collapse some ten seconds in
        }
    }

    private void modify(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, net.minecraft.resources.Identifier id, double value) {
        var instance = getAttribute(attribute);
        if (instance != null && !instance.hasModifier(id)) instance.addTransientModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    @Override
    public void travel(Vec3 input) {
        if (unbound()) travelFlying(input, getSpeed());
        else super.travel(input);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel server && !dormant()) {
            if (hasEffect(MobEffects.SLOWNESS)) removeEffect(MobEffects.SLOWNESS); // nothing slows it
            // a familiar mends from level 20; in its fight only once the shell has cracked (phase II), not behind the shell
            boolean mends = (familiar() ? knows(20) : arena() != null && phase() == 2) && !unbound();
            if (mends && server.getGameTime() - lastHurt > 100 && tickCount % 20 == 0 && getHealth() < getMaxHealth()) { // Void Heart
                heal(4.0F);
                server.sendParticles(new DustParticleOptions(CORE, 1.4F), getX(), getY() + getBbHeight() * 0.6, getZ(), 6, 0.3, 0.3, 0.3, 0);
            }
            if (knows(10) && familiar() && tickCount % 60 == 0) taunt(server);
            if (unbound()) blackHole(server);
            if ((specialShown() >= 0 || punished()) && steered != tickCount) { // held where it stands unless an ability drives it
                getNavigation().stop();
                setDeltaMovement(0, flies() ? 0 : getDeltaMovement().y, 0);
            }
            if (punished()) opening(server);
            // bits 0-2: the shield stones circling it; bits 3-4: its opening (stuck, staggered, venting)
            int shown = (shielded() ? shieldStones : 0) | (punished() ? punishKind << 3 : 0);
            if (visual() != shown) setVisual(shown);
        }
        if (level().isClientSide() && !dormant()) {
            if (random.nextInt(3) == 0) level().addParticle(new DustParticleOptions(CORE, 1.2F), getX(), getY() + getBbHeight() * (unbound() ? 0.4 : 0.6), getZ(), 0, 0.03, 0);
            if (phase() >= 2 && random.nextInt(2) == 0) level().addParticle(ParticleTypes.REVERSE_PORTAL, getRandomX(0.8), getRandomY(), getRandomZ(0.8), 0, 0.02, 0);
        }
    }

    /** Taunt (10): monsters near its owner turn on it instead. */
    private void taunt(ServerLevel level) {
        Player owner = ownerPlayer();
        if (owner == null) return;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(12), m -> m instanceof Enemy && m.getTarget() == owner)) {
            mob.setTarget(this);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 1, 0, 0, 0, 0);
        }
    }

    /** Phase III: the free core pulls everything in, tears what comes close, and its shell's pieces crash down. */
    private void blackHole(ServerLevel level) {
        Vec3 core = position().add(0, getBbHeight() * 0.4, 0);
        if (collapseCalm) return; // holding its breath before the collapse: nothing pulls, nothing crashes (run!)
        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(14), this::foe)) {
            Vec3 in = core.subtract(near.position()).normalize().scale(0.06);
            near.setDeltaMovement(near.getDeltaMovement().add(in.x, 0, in.z));
            near.hurtMarked = true;
            if (tickCount % 20 == 0 && near.distanceToSqr(core) < 12) {
                near.invulnerableTime = 0;
                near.hurtServer(level, damageSources().indirectMagic(this, this), hitFor(0.6));
            }
        }
        LivingEntity target = getTarget();
        if (target != null && level.getGameTime() >= nextCrash) { // a piece of the shell crashes on its prey
            nextCrash = level.getGameTime() + 50;
            Vec3 over = target.position().add(random.nextDouble() * 2 - 1, 8, random.nextDouble() * 2 - 1);
            fxZone(new Vec3(over.x, target.getY(), over.z), 1.3F, 12, 0x100818, AttackFxPayload.Z_SHADOW); // its shadow first: step out of it
            fireBolt(over, new Vec3(0, -1, 0), hitFor(1.2), CRY, 0, 0.9);
        }
        if (tickCount % 5 == 0) {
            for (int i = 0; i < 8; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = 6 + random.nextDouble() * 4;
                level.sendParticles(ParticleTypes.PORTAL, core.x + Math.cos(a) * r, core.y, core.z + Math.sin(a) * r, 0, -Math.cos(a), 0, -Math.sin(a), 1.2);
            }
        }
    }

    // ---- The moves (as a familiar, and in its fight by phase) ----

    @Override
    protected void performMove(RiftCreature.Move move, int tick, LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) return;
        switch (move.id()) {
            case "slam" -> { // both fists down: shockwave lines run across the floor
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick == 0) playSound(SoundEvents.RAVAGER_ROAR, 1.5F, 0.4F);
                if (tick == move.strike()) {
                    lines.clear();
                    slamHit.clear();
                    slamFrom = position();
                    double base = Math.atan2(target.getZ() - getZ(), target.getX() - getX());
                    for (int i = -1; i <= 1; i++) lines.add(new Vec3(Math.cos(base + i * 0.5), 0, Math.sin(base + i * 0.5)));
                    lines.add(new Vec3(-Math.cos(base), 0, -Math.sin(base)));
                    for (Vec3 dir : lines) fxLine(slamFrom, dir, move.length() - move.strike(), 1F, move.length() - move.strike() + 20, AttackFxPayload.L_OBSIDIAN);
                    fxNova(position(), 4F, 12, CORE);
                    server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.6F, 0.4F);
                }
                if (tick > move.strike()) {
                    double d = (tick - move.strike()) * 1.0;
                    for (Vec3 dir : lines) {
                        Vec3 p = slamFrom.add(dir.scale(d));
                        for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(p, p).inflate(1.2, 1.5, 1.2), this::foe)) {
                            if (!slamHit.add(near.getUUID())) continue; // once per slam (a burning foe was skipped before)
                            near.invulnerableTime = 0;
                            near.hurtServer(server, damageSources().mobAttack(this), hitFor(1.4));
                            onHitAffixes(server, near);
                            near.setDeltaMovement(near.getDeltaMovement().add(0, 0.8, 0));
                            near.hurtMarked = true;
                        }
                    }
                }
            }
            case "slab_throw" -> { // a slab torn from the floor thrown at its prey; in its arena it lands standing, cover for a while
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick < move.strike() && tick % 2 == 0) {
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()), getX(), getY() + 0.2, getZ(), 6, 0.8, 0.1, 0.8, 0.1);
                }
                if (tick == move.strike()) {
                    slabAt = target.position();
                    Vec3 from = position().add(0, getBbHeight() * 0.85, 0), aim = slabAt.add(0, 0.5, 0).subtract(from);
                    fireBolt(from, aim, hitFor(1.6), CRY, 1, Math.max(1.0, aim.length() / 10)); // there by the time the slab stands
                    playSound(SoundEvents.SNOW_GOLEM_SHOOT, 1.5F, 0.3F);
                }
                if (tick == move.strike() + 10) {
                    // it stands a little before where its prey was (never round anyone), on the floor
                    Vec3 back = position().subtract(slabAt).multiply(1, 0, 1).normalize();
                    Vec3 wall = slabAt.add(back.scale(1.5)), across = new Vec3(-back.z, 0, back.x);
                    BlockPos foot = BlockPos.containing(wall);
                    for (int down = 0; down < 3 && server.getBlockState(foot.below()).isAir(); down++) foot = foot.below();
                    for (int w = -1; w <= 1; w++) {
                        for (int h = 0; h < 2; h++) {
                            BlockPos at = BlockPos.containing(wall.add(across.scale(w))).atY(foot.getY() + h);
                            if (!server.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at)).isEmpty()) continue;
                            tempBlock(server, at, Blocks.OBSIDIAN.defaultBlockState(), 300);
                        }
                    }
                    for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(slabAt, slabAt).inflate(2), this::foe)) {
                        near.invulnerableTime = 0;
                        near.hurtServer(server, damageSources().mobAttack(this), hitFor(0.8));
                    }
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()), slabAt.x, slabAt.y + 0.5, slabAt.z, 30, 1, 0.5, 1, 0.2);
                    playSound(SoundEvents.ANVIL_LAND, 1.2F, 0.5F);
                }
            }
            case "stone_guard" -> { // arms crossed before it: nothing from the front gets through
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick == move.strike()) {
                    guardUntil = server.getGameTime() + move.length() - move.strike();
                    guardYaw = yBodyRot;
                    backHits = 0;
                    playSound(SoundEvents.SHIELD_BLOCK.value(), 1.5F, 0.4F);
                    if (arena() != null && !familiar() && target instanceof ServerPlayer sp) {
                        sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden.golem_guard"), true);
                    }
                }
                // behind its guard it turns only slowly: there is time to get round it (broken from behind, it staggers instead)
                if (tick >= move.strike() && guarding()) {
                    float want = (float) (Math.atan2(target.getZ() - getZ(), target.getX() - getX()) * (180 / Math.PI)) - 90F;
                    guardYaw = net.minecraft.util.Mth.approachDegrees(guardYaw, want, 2.5F);
                    setYRot(guardYaw);
                    setYBodyRot(guardYaw);
                    setYHeadRot(guardYaw);
                    Vec3 ahead = Vec3.directionFromRotation(0, guardYaw);
                    getLookControl().setLookAt(getX() + ahead.x * 8, getEyeY(), getZ() + ahead.z * 8); // over the look at its prey
                }
            }
            case "void_pull" -> { // everything pulled in, then blasted away
                setDeltaMovement(0, flies() ? 0 : getDeltaMovement().y, 0);
                if (tick == 0) {
                    com.lealex.alchyforbidden.network.SpellFxPayload.send(server, com.lealex.alchyforbidden.network.SpellFxPayload.HOLE, position().add(0, 2, 0), -1, move.length(), 2.0F);
                    playSound(SoundEvents.WARDEN_SONIC_CHARGE, 1.5F, 0.4F);
                }
                if (tick < move.strike()) {
                    for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(14), this::foe)) {
                        Vec3 in = position().subtract(near.position()).normalize().scale(0.13);
                        near.setDeltaMovement(near.getDeltaMovement().add(in.x, 0.02, in.z));
                        near.hurtMarked = true;
                    }
                }
                if (tick == move.strike()) {
                    for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6), this::foe)) {
                        near.invulnerableTime = 0;
                        near.hurtServer(server, damageSources().indirectMagic(this, this), hitFor(2.0));
                        onHitAffixes(server, near);
                        Vec3 out = near.position().subtract(position()).multiply(1, 0, 1).normalize();
                        near.setDeltaMovement(out.x * 1.6, 0.7, out.z * 1.6);
                        near.hurtMarked = true;
                    }
                    fxNova(position(), 6.5F, 16, CORE);
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.6F, 0.5F);
                }
            }
            default -> {}
        }
    }

    // ---- The fight's specials: one every few seconds in every phase ----

    @Override
    protected String nextSpecial(int phase, LivingEntity target) {
        if (punished()) return null; // still open to blows: it waits
        long now = level().getGameTime();
        double d = distanceTo(target);
        String next;
        if (phase >= 3) { // the free core: it rushes its prey, rains its shell, and now and then collapses
            if (now >= nextCollapse) next = "collapse";
            else if (d > 5 && !"dash".equals(lastSpecial)) next = "dash";
            else next = random.nextInt(3) == 0 ? "dash" : "rock_rain";
        } else if (d >= 7 && d <= 16 && now >= leapReady) {
            next = "leap"; // a prey keeping away: it leaps onto it
        } else if (d > 9) {
            next = "boulder"; // far and the leap not ready: boulders
        } else {
            java.util.List<String> pool = new java.util.ArrayList<>(phase == 1
                    ? java.util.List.of("fissure", "earthquake")
                    : java.util.List.of("core_beam", "rock_rain", "fissure"));
            if (phase == 2 && !shielded()) pool.add("rubble_shield");
            if (pool.size() > 1) pool.remove(lastSpecial);
            next = pool.get(random.nextInt(pool.size()));
        }
        lastSpecial = next;
        return next;
    }

    /** Closer than the default: a boulder every three seconds or so, and the openings counted in. */
    @Override
    protected int specialGap() {
        if (lastSpecial == null) return 40 + random.nextInt(40);
        return switch (lastSpecial) {
            case "boulder" -> 30 + random.nextInt(12);
            case "leap" -> 30 + 25 + random.nextInt(30);      // after its fists come out of the floor
            case "core_beam" -> 40 + 25 + random.nextInt(30); // after it has vented
            case "collapse" -> 50 + random.nextInt(30);
            default -> 40 + random.nextInt(40);
        };
    }

    @Override
    protected int specialLength(String id) {
        return switch (id) {
            case "core_beam" -> 54;
            case "leap" -> LEAP_LAND + 4;
            case "boulder" -> 24;
            case "fissure" -> 76;
            case "earthquake" -> STOMPS[2] + 12;
            case "rock_rain" -> 60;
            case "dash" -> 40;
            case "collapse" -> COLLAPSE_BLAST + 14;
            default -> 20;
        };
    }

    @Override
    protected void onSpecialEnd(ServerLevel level, String id, LivingEntity target) {
        collapseCalm = false;
        if (id.equals("leap")) punish(level, STUCK, 30, "golem_stuck");
        else if (id.equals("core_beam")) {
            punish(level, VENT, 40, "golem_vent");
            playSound(SoundEvents.LAVA_EXTINGUISH, 1.6F, 0.5F);
        }
    }

    @Override
    protected void onSpecialCut(ServerLevel level, String id) {
        collapseCalm = false;
        marks.clear();
    }

    @Override
    protected void performSpecial(ServerLevel server, String id, int tick, LivingEntity target) {
        switch (id) {
            case "rubble_shield" -> rubbleShield(server, tick, target);
            case "core_beam" -> coreBeam(server, tick, target);
            case "leap" -> leap(server, tick, target);
            case "boulder" -> boulder(server, tick, target);
            case "fissure" -> fissure(server, tick, target);
            case "earthquake" -> earthquake(server, tick, target);
            case "rock_rain" -> rockRain(server, tick, target);
            case "dash" -> dash(server, tick, target);
            case "collapse" -> collapse(server, tick, target);
            default -> {}
        }
    }

    /** Six stones of crying obsidian rise and circle it. */
    private void rubbleShield(ServerLevel server, int tick, LivingEntity target) {
        if (tick == 10) {
            shieldStones = 6;
            shieldUntil = server.getGameTime() + 240;
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CRYING_OBSIDIAN.defaultBlockState()), getX(), getY() + 1, getZ(), 60, 2, 1, 2, 0.2);
            playSound(SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.5F, 0.5F);
            if (target instanceof ServerPlayer sp) sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden.shield_warning"), true);
        }
    }

    /** The core charges, then a beam sweeps across its prey; it vents after (an opening). */
    private void coreBeam(ServerLevel server, int tick, LivingEntity target) {
        Vec3 core = position().add(0, getBbHeight() * 0.6, 0);
        if (tick == 0) playSound(SoundEvents.BEACON_POWER_SELECT, 1.5F, 0.5F);
        if (tick < 30) {
            if (tick % 3 == 0) server.sendParticles(new DustParticleOptions(CORE, 1.4F), core.x, core.y, core.z, 6, 0.3, 0.3, 0.3, 0);
            beamDir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(core).normalize();
            face(target.position());
        } else if (tick < 50) {
            face(core.add(beamDir.x * 8, 0, beamDir.z * 8)); // its body held on the aim, the sweep drawn by the model
            if (tick == 30) fxBeam(core, beamDir, 20F, 0.7F, 20, CORE, AttackFxPayload.CORE | AttackFxPayload.SWEEP_SINE, 0.6F, 0.3F);
            double sweep = Math.sin((tick - 30) / 20.0 * Math.PI) * 0.6 - 0.3;
            Vec3 dir = new Vec3(beamDir.x * Math.cos(sweep) - beamDir.z * Math.sin(sweep), beamDir.y, beamDir.x * Math.sin(sweep) + beamDir.z * Math.cos(sweep));
            if (tick % 4 == 0) {
                for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(20), this::foe)) {
                    Vec3 to = near.position().add(0, near.getBbHeight() * 0.5, 0).subtract(core);
                    double along = to.dot(dir);
                    if (along < 0 || along > 20 || to.subtract(dir.scale(along)).length() > 1.4) continue;
                    near.invulnerableTime = 0;
                    near.hurtServer(server, damageSources().indirectMagic(this, this), hitFor(0.7));
                    near.igniteForSeconds(2);
                }
                playSound(SoundEvents.GUARDIAN_ATTACK, 1.0F, 0.5F);
            }
        } else if (tick % 2 == 0) { // the core glowing white-hot before it vents
            server.sendParticles(ParticleTypes.LAVA, core.x, core.y, core.z, 2, 0.3, 0.3, 0.3, 0);
        }
    }

    /**
     * The leap slam: it crouches while a shadow grows where it will land (a second), leaps in an arc (steered, at most
     * 1.9 a tick) and lands with a ring along the floor (jump it), a nova and cracks; its fists stay stuck in the floor
     * after (onSpecialEnd: an opening).
     */
    private void leap(ServerLevel server, int tick, LivingEntity target) {
        if (tick == 0) {
            leapReady = server.getGameTime() + (phase() >= 2 ? 140 : 240);
            leapFrom = position();
            Vec3 at = keepInside(target.position());
            leapTo = onFloor(at);
            specialHit.clear();
            fxZone(leapTo, 3.2F, LEAP_LAND + 4, SHADOW, AttackFxPayload.Z_SHADOW);
            playSound(SoundEvents.RAVAGER_ROAR, 1.6F, 0.5F);
        }
        if (tick < LEAP_UP) { // crouched, gathering, the floor cracking under it
            setDeltaMovement(0, getDeltaMovement().y, 0);
            steered = tickCount;
            face(leapTo);
            if (tick % 3 == 0) server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()), getX(), getY() + 0.1, getZ(), 8, 0.8, 0.05, 0.8, 0.1);
            if (tick == LEAP_UP - 4) playSound(SoundEvents.IRON_GOLEM_HURT, 1.4F, 0.4F);
        } else if (tick < LEAP_LAND) { // in the air: a real arc, 5.5 high at its top
            float s = (tick - LEAP_UP + 1) / (float) (LEAP_LAND - LEAP_UP);
            Vec3 p = leapFrom.lerp(leapTo, s).add(0, 5.5 * 4 * s * (1 - s), 0);
            steer(p, 1.9);
            face(leapTo);
            if (tick == LEAP_UP) {
                server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 2, 0.5, 0.1, 0.5, 0);
                playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.7F);
            }
        } else if (tick == LEAP_LAND) { // down: fists into the floor (where it really came down, if something stopped it)
            if (position().distanceTo(leapTo) > 2) leapTo = onFloor(position());
            steer(leapTo, 1.9);
            groundRing(leapTo.add(0, 0.05, 0), 0.6, 13, hitFor(0.6), CORE);
            fxNova(leapTo, 5F, 14, CORE);
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3 + random.nextDouble() * 0.4;
                fxLine(leapTo, new Vec3(Math.cos(a), 0, Math.sin(a)), 6F, 1.2F, 34, AttackFxPayload.L_OBSIDIAN);
            }
            for (LivingEntity near : foesNear(server, leapTo, 3.2, 2.5)) {
                if (!specialHit.add(near.getUUID())) continue;
                hitOnce(server, near, hitFor(1.1));
                fling(near, leapTo, 0.7, 0.75);
            }
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, leapTo.x, leapTo.y + 0.5, leapTo.z, 1, 0, 0, 0, 0);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()), leapTo.x, leapTo.y + 0.3, leapTo.z, 60, 2, 0.3, 2, 0.3);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.4F);
            playSound(SoundEvents.ANVIL_LAND, 1.6F, 0.4F);
        } else {
            steer(leapTo, 0.3); // settling
        }
    }

    /** A boulder ripped out of the floor and hurled at a prey far off; a shadow marks where it comes down. */
    private void boulder(ServerLevel server, int tick, LivingEntity target) {
        face(target.position());
        if (tick < BOULDER_THROW - 2) { // tearing it out beside it
            Vec3 side = position().add(Vec3.directionFromRotation(0, getYRot()).scale(1.3));
            if (tick % 2 == 0) server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()), side.x, getY() + 0.2, side.z, 6, 0.5, 0.1, 0.5, 0.15);
            if (tick == 0 || tick == 6) playSound(SoundEvents.STONE_BREAK, 1.5F, 0.4F);
        } else if (tick == BOULDER_THROW) {
            Vec3 from = position().add(0, getBbHeight() * 1.05, 0); // held over its head
            Vec3 lead = target.getKnownMovement().multiply(1, 0, 1);
            if (lead.length() > 0.35) lead = lead.normalize().scale(0.35);
            double speed = 1.15, ticks = from.distanceTo(target.position()) / speed;
            Vec3 aim = keepInside(target.position().add(lead.scale(Math.min(ticks, 12))));
            Vec3 floor = onFloor(aim);
            fxZone(floor, 1.3F, (int) ticks + 8, SHADOW, AttackFxPayload.Z_SHADOW);
            fireBolt(from, floor.add(0, 0.7, 0).subtract(from), hitFor(0.9), CRY, 0, speed);
            playSound(SoundEvents.SNOW_GOLEM_SHOOT, 1.5F, 0.3F);
            playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.4F, 0.5F);
        }
    }

    /**
     * Fissure: one fist dragged along the floor opens a crack that chases its prey for three seconds; every spot of it
     * glows half a second before spikes of obsidian burst out of it, and its head bursts at the end.
     */
    private void fissure(ServerLevel server, int tick, LivingEntity target) {
        if (tick == 0) {
            marks.clear();
            specialHit.clear();
            Vec3 ahead = Vec3.directionFromRotation(0, getYRot());
            crackHead = onFloor(position().add(ahead.scale(1.5)));
            crackDir = target.position().subtract(crackHead).multiply(1, 0, 1).normalize();
            playSound(SoundEvents.RAVAGER_ROAR, 1.4F, 0.6F);
        }
        if (tick < 64) face(crackHead);
        if (tick >= 8 && tick < 62) { // the head of the crack turns toward its prey, a little each tick
            Vec3 want = target.position().subtract(crackHead).multiply(1, 0, 1);
            if (want.lengthSqr() > 0.25) crackDir = crackDir.lerp(want.normalize(), 0.12).normalize();
            crackHead = keepInside(crackHead.add(crackDir.scale(0.38)));
            crackHead = new Vec3(crackHead.x, floorAt(crackHead), crackHead.z);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()), crackHead.x, crackHead.y + 0.1, crackHead.z, 2, 0.2, 0.02, 0.2, 0.05);
            if (tick % 3 == 2) {
                marks.add(new Mark(crackHead, crackDir, tick + 10));
                fxZone(crackHead, 0.9F, 14, CORE, AttackFxPayload.Z_SHADOW); // it glows first
            }
            if (tick % 6 == 0) server.playSound(null, crackHead.x, crackHead.y, crackHead.z, SoundEvents.STONE_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.5F);
        }
        for (java.util.Iterator<Mark> it = marks.iterator(); it.hasNext(); ) {
            Mark mark = it.next();
            if (mark.tick() != tick) continue;
            it.remove();
            fxLine(mark.at().subtract(mark.dir()), mark.dir(), 2F, 2F, 22, AttackFxPayload.L_OBSIDIAN);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()), mark.at().x, mark.at().y + 0.6, mark.at().z, 8, 0.3, 0.4, 0.3, 0.15);
            server.playSound(null, mark.at().x, mark.at().y, mark.at().z, SoundEvents.BASALT_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 0.6F);
            for (LivingEntity near : foesNear(server, mark.at(), 1.3, 2)) {
                if (!specialHit.add(near.getUUID())) continue; // once a fissure
                hitOnce(server, near, hitFor(0.8));
                near.setDeltaMovement(near.getDeltaMovement().add(0, 0.6, 0));
                near.hurtMarked = true;
            }
        }
        if (tick == 64) { // and its head bursts
            fxNova(crackHead, 3F, 12, CORE);
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + 0.4;
                fxLine(crackHead, new Vec3(Math.cos(a), 0, Math.sin(a)), 3F, 1.5F, 26, AttackFxPayload.L_OBSIDIAN);
            }
            for (LivingEntity near : foesNear(server, crackHead, 2.5, 2.5)) {
                if (!specialHit.add(near.getUUID())) continue;
                hitOnce(server, near, hitFor(1.1));
                fling(near, crackHead, 0.5, 0.7);
            }
            server.sendParticles(ParticleTypes.EXPLOSION, crackHead.x, crackHead.y + 0.5, crackHead.z, 3, 0.8, 0.3, 0.8, 0);
            server.playSound(null, crackHead.x, crackHead.y, crackHead.z, SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 0.6F);
        }
    }

    /** Earthquake: three stomps, each sending a ring out along the floor (jump each), the whole arena rumbling. */
    private void earthquake(ServerLevel server, int tick, LivingEntity target) {
        face(target.position());
        if (tick == 0 && target instanceof ServerPlayer sp) sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden.golem_quake"), true);
        Vec3 c = arena() != null ? arenaCenter() : position();
        if (tick % 2 == 0) { // dust and crumbs shaken off the whole floor
            for (int i = 0; i < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * 16;
                Vec3 p = c.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()), p.x, floorAt(p) + 0.1, p.z, 2, 0.3, 0.05, 0.3, 0.1);
            }
        }
        for (int stomp : STOMPS) {
            if (tick == stomp - 10) playSound(SoundEvents.WARDEN_SNIFF, 1.6F, 0.4F); // the foot comes up
            if (tick == stomp) {
                Vec3 feet = onFloor(position());
                groundRing(feet.add(0, 0.05, 0), 0.5, 18, hitFor(0.6), CORE);
                fxNova(feet, 3.5F, 10, CORE);
                server.sendParticles(ParticleTypes.EXPLOSION, feet.x, feet.y + 0.3, feet.z, 3, 1, 0.1, 1, 0);
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()), feet.x, feet.y + 0.2, feet.z, 40, 1.8, 0.2, 1.8, 0.25);
                playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.8F, 0.3F);
                playSound(SoundEvents.ANVIL_LAND, 1.4F, 0.3F);
                if (target instanceof ServerPlayer sp) sp.level().playSound(null, sp.blockPosition(), SoundEvents.DEEPSLATE_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 0.4F);
            }
        }
    }

    /** Rock rain (phase II, and the shell's pieces in phase III): boulders come down where shadows appear first. */
    private void rockRain(ServerLevel server, int tick, LivingEntity target) {
        face(target.position());
        if (tick == 0) playSound(SoundEvents.RAVAGER_ROAR, 1.4F, 0.7F);
        if (tick >= 8 && tick <= 44 && (tick - 8) % 6 == 0) {
            int n = (tick - 8) / 6;
            Vec3 spot;
            if (n % 2 == 0) { // on its prey, a little ahead of where it runs
                Vec3 lead = target.getKnownMovement().multiply(1, 0, 1).scale(10);
                spot = target.position().add(lead.length() > 3 ? lead.normalize().scale(3) : lead);
            } else { // round it
                double a = random.nextDouble() * Math.PI * 2, r = 2 + random.nextDouble() * 4;
                spot = target.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            }
            Vec3 floor = onFloor(keepInside(spot));
            fxZone(floor, 1.4F, 26, SHADOW, AttackFxPayload.Z_SHADOW); // its shadow grows as it falls
            fireBolt(floor.add(0, 18, 0), new Vec3(0, -1, 0), hitFor(0.9), CRY, 0, 0.9);
            server.playSound(null, floor.x, floor.y, floor.z, SoundEvents.STONE_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 0.4F);
        }
    }

    /** Phase III: the core marks a line at its prey, holds back, then rushes along it (steered, at most 1.3 a tick). */
    private void dash(ServerLevel server, int tick, LivingEntity target) {
        Vec3 core = position().add(0, getBbHeight() * 0.4, 0);
        if (tick == 0) {
            specialHit.clear();
            Vec3 mid = target.position().add(0, target.getBbHeight() * 0.5, 0);
            dashDir = mid.subtract(core).normalize();
            Vec3 past = keepInside(mid.add(dashDir.multiply(1, 0, 1).scale(4)));
            double up = getBbHeight() * 0.4;
            dashTo = new Vec3(past.x, Math.max(floorAt(past) + 0.2, past.y - up), past.z);
            Vec3 line = dashTo.add(0, up, 0).subtract(core);
            fxBeam(core, line, (float) line.length(), 0.35F, DASH_GO + 2, CORE, AttackFxPayload.CORE, 0, 0);
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 1.4F, 0.8F);
            holdAt = position();
        }
        if (tick < DASH_GO) { // drawn back a little, gathering
            steer(holdAt.subtract(dashDir.scale(1.2 * tick / (double) DASH_GO)), 0.12);
            if (tick % 2 == 0) server.sendParticles(ParticleTypes.REVERSE_PORTAL, core.x, core.y, core.z, 6, 0.4, 0.4, 0.4, 0.05);
        } else if (tick < DASH_END) {
            if (tick == DASH_GO) playSound(SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.6F, 0.5F);
            steer(dashTo, 1.3);
            server.sendParticles(ParticleTypes.PORTAL, core.x, core.y, core.z, 8, 0.4, 0.4, 0.4, 0.3);
            for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(1.6), this::foe)) {
                if (!specialHit.add(near.getUUID())) continue;
                hitOnce(server, near, hitFor(1.1));
                near.setDeltaMovement(near.getDeltaMovement().add(dashDir.x * 1.1, 0.45, dashDir.z * 1.1));
                near.hurtMarked = true;
                playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.4F, 0.6F);
            }
        } else {
            steer(position(), 0); // braking
        }
    }

    /**
     * Phase III collapse: it pulls hard (a ring tightening on the floor shows how far the blast will reach), lets go
     * (nothing pulls: run), then bursts across the floor.
     */
    private void collapse(ServerLevel server, int tick, LivingEntity target) {
        Vec3 core = position().add(0, getBbHeight() * 0.4, 0), floor = onFloor(position());
        if (tick == 0) {
            nextCollapse = server.getGameTime() + 300;
            holdAt = position();
            fxColumn(floor, (float) COLLAPSE_RADIUS, 5F, COLLAPSE_BLAST, COLLAPSE_BLAST + 14, CORE);
            fxZone(floor, (float) COLLAPSE_RADIUS, COLLAPSE_BLAST, SHADOW, AttackFxPayload.Z_SHADOW);
            com.lealex.alchyforbidden.network.SpellFxPayload.send(server, com.lealex.alchyforbidden.network.SpellFxPayload.HOLE, core, -1, COLLAPSE_BLAST, 2.4F);
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 1.8F, 0.4F);
            if (target instanceof ServerPlayer sp) sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden.golem_collapse"), true);
        }
        steer(holdAt, 0.1);
        if (tick < COLLAPSE_PULL) { // the pull (with the black hole's own: still outrun when sprinting)
            for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(18), this::foe)) {
                Vec3 in = core.subtract(near.position()).multiply(1, 0, 1);
                if (in.lengthSqr() < 1) continue;
                in = in.normalize().scale(0.05);
                near.setDeltaMovement(near.getDeltaMovement().add(in.x, 0, in.z));
                near.hurtMarked = true;
            }
            if (tick % 3 == 0) {
                for (int i = 0; i < 10; i++) {
                    double a = random.nextDouble() * Math.PI * 2, r = 4 + random.nextDouble() * 10;
                    server.sendParticles(ParticleTypes.PORTAL, core.x + Math.cos(a) * r, core.y, core.z + Math.sin(a) * r, 0, -Math.cos(a), 0, -Math.sin(a), 1.4);
                }
            }
        } else if (tick < COLLAPSE_BLAST) { // it lets go and trembles: run
            collapseCalm = true;
            if (tick % 2 == 0) server.sendParticles(new DustParticleOptions(CORE, 2.0F), core.x, core.y, core.z, 8, 0.5, 0.5, 0.5, 0);
            if (tick % 6 == 0) playSound(SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.6F + (tick - COLLAPSE_PULL) * 0.03F);
        } else if (tick == COLLAPSE_BLAST) {
            for (LivingEntity near : foesNear(server, floor, COLLAPSE_RADIUS, 4)) {
                hitOnce(server, near, hitFor(1.8));
                fling(near, core, 1.4, 0.8);
            }
            groundRing(floor.add(0, 0.05, 0), 0.6, 16, hitFor(0.5), CORE);
            fxNova(floor, (float) COLLAPSE_RADIUS + 1, 16, CORE);
            fxNova(core, 5F, 12, 0xFFFFFF);
            server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, core.x, core.y, core.z, 2, 1, 0.5, 1, 0);
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, core.x, core.y, core.z, 120, 2, 1, 2, 0.6);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.4F, 0.4F);
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.8F, 0.6F);
        } else {
            collapseCalm = false;
        }
    }

    /** What an opening shows (server particles): fists stuck in the floor, a stagger, the core venting steam. */
    private void opening(ServerLevel server) {
        Vec3 fists = position().add(Vec3.directionFromRotation(0, getYRot()).scale(1.4));
        switch (punishKind) {
            case STUCK -> {
                if (tickCount % 3 == 0) server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()), fists.x, getY() + 0.2, fists.z, 5, 0.6, 0.1, 0.4, 0.1);
                if (tickCount % 10 == 0) playSound(SoundEvents.STONE_BREAK, 1.0F, 0.4F);
            }
            case STAGGER -> {
                if (tickCount % 4 == 0) server.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight() + 0.2, getZ(), 6, 0.5, 0.2, 0.5, 0.1);
            }
            case VENT -> {
                Vec3 core = position().add(0, getBbHeight() * 0.6, 0);
                server.sendParticles(ParticleTypes.CLOUD, core.x, core.y, core.z, 3, 0.3, 0.3, 0.3, 0.06);
                if (tickCount % 3 == 0) server.sendParticles(ParticleTypes.LARGE_SMOKE, core.x, core.y + 0.5, core.z, 2, 0.3, 0.2, 0.3, 0.03);
                if (tickCount % 10 == 0) playSound(SoundEvents.FIRE_EXTINGUISH, 1.2F, 0.5F);
            }
            default -> {}
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RESPAWN_ANCHOR_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RESPAWN_ANCHOR_DEPLETE.value();
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(SoundEvents.IRON_GOLEM_STEP, 1.5F, 0.4F);
    }

    @Override
    public float getVoicePitch() {
        return gradePitch(0.4F);
    }
}
