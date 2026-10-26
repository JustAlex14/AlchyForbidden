package com.lealex.alchyforbidden.familiar;

import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.network.AttackFxPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Ossuarch (ring IV, a great one of the void): a dragon's skeleton grown through with sculk, a rift burning where its
 * heart was. In its arena it drives its own body every tick (the plain melee goal is held off: it could never reach
 * from the air).
 * Phase I, flying: it circles the arena 4.5 up, banking, a volley of three bone spikes at its prey every 2.5 s; moves
 * bone rain (shadows mark each bone), a strafing breath, the wyrm dive (crashes it: x1.5); specials the strafing run
 * (pulls up, its shadow lines the floor, then a low pass breathing a burning line through where its prey stood, then it
 * lands to catch its breath: x1.5) and the bone barrage (bones over half the arena, each shadowed first).
 * Phase II, grounded (its wing broken): it crawls after its prey and bites, a claw rake every third blow; tail sweep (a
 * shadow round it first), bone walls (their shadows first), risen bones, a sonic roar, and the charge (rears, a shadow
 * line, then a straight run: into a block, a bone wall or the arena's wall it crashes, stunned, x1.5).
 * Phase III (Void-Rifted only): void wings, a faster wider circle and four spikes a volley, the strafing run with void
 * fire, the barrage, bone wyverns, and a breath over the whole arena that only its bone walls hide from (it lies spent
 * after, x1.5); every twenty seconds it eats one of its minions to mend.
 * As a familiar it rests drawn small; in a fight it hovers low enough to bite.
 */
public class BoneDragon extends RiftBeast {
    private static final int BONE = 0xD8D0C0, SCULK = 0x2FC6B0, VOID = 0x8A4AE8, SHADOW = 0x100818, BREATH = 0x9FF2F8;
    /**
     * What its model shows (visual()): bit 1 crashed (sprawled), bit 2 striding on the floor (the legs walk on their own
     * clock), bit 4 resting after a strafing run (crouched, panting), bit 8 a claw rake (a forefoot raised, then swept);
     * bits 8..11 its bank while circling, 0..15 with 8 level (below 8 rolls one way, above the other).
     */
    public static final int V_CRASHED = 1, V_STRIDING = 2, V_RESTING = 4, V_CLAW = 8, V_BANK_SHIFT = 8;
    private static final double LOW = 2.5, STRAFE_SPEED = 0.75, CHARGE_SPEED = 0.8;
    private static final int STRAFE_AIM = 12, STRAFE_DIVE = 32, STRAFE_OUT = 14, CHARGE_REAR = 20, CHARGE_STOP = 12;
    private long plateReady, nextMeal, stunUntil, wallsUntil, nextVolley, nextShot, nextTurn, nextBite, clawAt, clawUntil;
    private boolean diveLanded, crashing, restPose, striding, circling;
    private int steered = -1, orbitDir = 1, volleyLeft, attacks, bank;
    private float breathYaw;
    private String lastSpecial = "";
    private final java.util.List<java.util.UUID> minions = new java.util.ArrayList<>();
    private Vec3 breathDir = Vec3.ZERO, breathFrom = Vec3.ZERO, diveAt = Vec3.ZERO;
    // the strafing run: its line, how long it lasts (set at the aim), what it has struck, where it last scorched the floor
    private Vec3 strafeFrom = Vec3.ZERO, strafeTo = Vec3.ZERO, strafeDir = Vec3.ZERO;
    private @org.jspecify.annotations.Nullable Vec3 scorchedAt;
    private int strafeLen = 120;
    private boolean strafeCrashed;
    private final java.util.Set<java.util.UUID> strafeHit = new java.util.HashSet<>();
    // the charge: its heading and end (set at the aim), whether that end is the arena's wall, how it ended
    private Vec3 chargeDir = Vec3.ZERO, chargeEnd = Vec3.ZERO;
    private int chargeLen = 60;
    private boolean chargeWall, chargeCrashed, chargeStopped;
    private final java.util.Set<java.util.UUID> chargeHit = new java.util.HashSet<>();
    // the bone walls: the cells and lines chosen at the warning, raised a moment later
    private final java.util.List<BlockPos> wallCells = new java.util.ArrayList<>();
    private final java.util.List<Vec3[]> wallLines = new java.util.ArrayList<>();

    /** An ability drives its body this tick: straight toward a point, never faster than maxStep a tick (the hover waits). */
    private void steer(Vec3 to, double maxStep) {
        Vec3 d = to.subtract(position());
        double l = d.length();
        setDeltaMovement(l > maxStep ? d.scale(maxStep / l) : d);
        getNavigation().stop();
        steered = tickCount;
    }

    /** Turns body and head toward a point (step degrees a tick at most). */
    private void turnTo(Vec3 at, float step) {
        hold(Mth.approachDegrees(getYRot(), yawOf(at.subtract(position())), step));
    }

    /** Turns its body toward a heading (step degrees a tick); the head is left to its look. */
    private void face(float yaw, float step) {
        setYRot(Mth.approachDegrees(getYRot(), yaw, step));
        setYBodyRot(getYRot());
    }

    private static float yawOf(Vec3 v) {
        return (float) (Math.atan2(v.z, v.x) * (180 / Math.PI)) - 90F;
    }

    /** Body and head held on this heading (the look control told the same, so it does not pull the head away). */
    private void hold(float yaw) {
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        Vec3 ahead = position().add(Vec3.directionFromRotation(0, yaw).scale(8)).add(0, getBbHeight() * 0.5, 0);
        getLookControl().setLookAt(ahead.x, ahead.y, ahead.z);
    }

    /** In its arena it drives its own body: no path is followed (the abilities, the circle and the crawl steer it). */
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!familiar() && (arena() != null || stunned() || crashing || specialShown() >= 0)) {
            getNavigation().stop();
            getMoveControl().setWait();
        }
    }

    public BoneDragon(EntityType<? extends BoneDragon> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 380.0)
                .add(Attributes.ATTACK_DAMAGE, 15.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FLYING_SPEED, 0.4)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 3.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation navigation = new FlyingPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    protected boolean flies() {
        return true;
    }

    @Override
    public void travel(Vec3 input) {
        travelFlying(input, getSpeed());
    }

    @Override
    public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public float dormantScale() {
        return 0.1F; // resting, the whole of it drawn small
    }

    /**
     * In its arena fight this holds the walking and looking (so the plain melee goal, which cannot reach it from the air,
     * never runs); the circle, the crawl and the volleys are driven from aiStep. Moves and specials still come first.
     */
    private final class Hunt extends Goal {
        Hunt() {
            setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return arenaFight() && !dormant() && target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            getNavigation().stop();
        }
    }

    @Override
    protected void addAttackGoals() {
        goalSelector.addGoal(2, new Hunt());
    }

    // ---- Its entrance: it bursts out of a rift high above, swoops down round the arena and roars ----

    private Vec3 introFrom = Vec3.ZERO, introSide = Vec3.ZERO;

    @Override
    protected Vec3 introPath(Vec3 home, float p) {
        if (introFrom == Vec3.ZERO) return home.add(0, 4.5, 0);
        float q = ease(p / 0.7F);
        Vec3 end = home.add(0, 4.5, 0);
        return introFrom.lerp(end, q).add(introSide.scale(Math.sin(q * Math.PI) * 6));
    }

    @Override
    protected void introEffects(ServerLevel level, int t, Vec3 home) {
        if (t == 0) {
            Vec3 away = Vec3.directionFromRotation(0, getYRot()).scale(-1);
            introFrom = home.add(away.scale(14)).add(0, 16, 0);
            introSide = new Vec3(-away.z, 0, away.x);
            com.lealex.alchyforbidden.network.SpellFxPayload.send(level, com.lealex.alchyforbidden.network.SpellFxPayload.RIFT, introFrom, -1, 45, 6.0F);
            level.playSound(null, BlockPos.containing(introFrom), SoundEvents.END_PORTAL_SPAWN, net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.6F);
        }
        if (t < 70) {
            if (t % 7 == 0) playSound(SoundEvents.ENDER_DRAGON_FLAP, 2.0F, 0.6F);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 3, 0.8, 0.4, 0.8, 0.02);
            if (t % 2 == 0) level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 1, getZ(), 1, 1.0, 0.4, 1.0, 0.01);
        }
        if (t == 74) { // the roar
            fxNova(home, 8F, 18, BONE);
            fxBeam(position().add(0, 3, 0), new Vec3(0, 1, 0), 10F, 2.2F, 16, BREATH, AttackFxPayload.SONIC, 0, 0);
            level.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, net.minecraft.sounds.SoundSource.HOSTILE, 2.5F, 0.6F);
        }
    }

    private static float ease(float x) {
        x = Math.max(0F, Math.min(1F, x));
        return x * x * (3 - 2 * x);
    }

    @Override
    public RiftCreature creature() {
        return RiftCreature.BONE_DRAGON;
    }

    /** Its arena fight (not as a familiar, not an echo of it). */
    private boolean arenaFight() {
        return arena() != null && !familiar() && !echo();
    }

    /** It is on the ground: phase II (its wing broken). */
    public boolean grounded() {
        return phase() == 2 && !familiar();
    }

    private double hoverHeight() {
        if (familiar()) return getTarget() != null && getTarget().isAlive() ? 0.4 : 2.0; // low enough to bite in a fight
        if (stunned()) return 0.1;
        return switch (phase()) {
            case 2 -> 0.1;
            case 3 -> 5.0;
            default -> 4.5;
        };
    }

    /** Crashed on the floor (a dive, a charge into a wall, the arena breath) or resting after a strafing run: x1.5. */
    public boolean stunned() {
        return arena() != null && !familiar() && level().getGameTime() < stunUntil;
    }

    /** Down for so many ticks: crashed (sprawled) or resting (crouched, panting); aiStep keeps it on the floor. */
    private void stun(ServerLevel level, int ticks, LivingEntity target, boolean rest, String line) {
        stunUntil = level.getGameTime() + ticks;
        restPose = rest;
        if (rest) {
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.5F, 0.5F);
            playSound(SoundEvents.SKELETON_AMBIENT, 1.5F, 0.4F);
        } else {
            playSound(SoundEvents.SKELETON_HURT, 2.0F, 0.3F);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), getX(), getY() + 0.5, getZ(), 40, 1.5, 0.3, 1.5, 0.15);
        }
        if (target instanceof ServerPlayer sp) sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden." + line), true);
    }

    @Override
    protected void onSpecialEnd(ServerLevel level, String id, LivingEntity target) {
        switch (id) {
            case "arena_breath" -> stun(level, 90, target, false, "dragon_down"); // spent
            case "strafing_run" -> {
                if (strafeCrashed) stun(level, 60, target, false, "dragon_wall"); // flew into its own bone wall
                else stun(level, phase() >= 3 ? 40 : 50, target, true, "dragon_rest"); // lands to catch its breath
            }
            case "charge" -> {
                if (chargeCrashed) stun(level, 60, target, false, "dragon_wall");
            }
            default -> {}
        }
    }

    @Override
    protected void onSpecialCut(ServerLevel level, String id) {
        strafeCrashed = chargeCrashed = false;
        chargeStopped = true;
        wallCells.clear();
        wallLines.clear();
    }

    @Override
    protected int boltStyle(int color, int fire, boolean boomerang) {
        return AttackFxPayload.BONE;
    }

    @Override
    protected float boltSize() {
        return 1.5F;
    }

    @Override
    protected java.util.List<String> specialIds() {
        // (the order is what the model is told: new ones only at the end)
        return java.util.List.of("tail_sweep", "bone_walls", "raise_bones", "wyverns", "arena_breath", "strafing_run", "bone_barrage", "charge");
    }

    @Override
    protected boolean moveAllowed(RiftCreature.Move move) {
        if (familiar()) return true;
        if (stunned() || crashing) return false;
        return switch (move.id()) {
            case "bone_rain", "sonic_breath", "wyrm_dive" -> phase() != 2;
            case "sonic_roar" -> phase() == 2;
            default -> true;
        };
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (stunned()) return false; // crashed on the floor: no bites
        float before = target instanceof LivingEntity l ? l.getHealth() : 0;
        boolean hit = super.doHurtTarget(level, target);
        if (hit && knows(10) && target instanceof LivingEntity l) heal(Math.max(0, before - l.getHealth()) * 0.25F); // Sculk Marrow
        if (hit) playSound(SoundEvents.SKELETON_HURT, 1.5F, 0.4F);
        return hit;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (knows(5) && source.getDirectEntity() != source.getEntity()) amount *= 0.5F; // Hollow Bones
        if (stunned()) amount *= 1.5F;
        // Bone Armour (never spent on a blow that would not land anyway: changing phase, its entrance)
        if (knows(15) && !shifting() && !introducing() && !isInvulnerableTo(level, source)
                && level.getGameTime() >= plateReady && source.getEntity() != null && amount >= 3) {
            plateReady = level.getGameTime() + 200;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), getX(), getY() + 1, getZ(), 30, 0.8, 0.6, 0.8, 0.2);
            playSound(SoundEvents.SKELETON_HURT, 1.5F, 0.6F);
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public void die(DamageSource source) {
        // Death Rattle (not when slain in its trial: the shards would strike the one who already won)
        if (knows(20) && level() instanceof ServerLevel server && arena() != null) {
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), getX(), getY() + 1, getZ(), 80, 1.5, 0.8, 1.5, 0.4);
            playSound(SoundEvents.SKELETON_DEATH, 2.0F, 0.4F);
        } else if (knows(20) && level() instanceof ServerLevel) {
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24;
                fireBolt(position().add(0, 1, 0), new Vec3(Math.cos(a), 0.2 * Math.sin(i), Math.sin(a)), hitFor(0.8), BONE, 1, 0.9);
            }
            playSound(SoundEvents.SKELETON_DEATH, 2.0F, 0.4F);
        }
        dismiss();
        super.die(source);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        dismiss();
        super.remove(reason);
    }

    private void dismiss() {
        if (level() instanceof ServerLevel server) for (java.util.UUID id : minions) if (server.getEntity(id) instanceof Entity e) e.discard();
        minions.clear();
    }

    @Override
    protected void onPhase(ServerLevel level, int phase) {
        stunUntil = 0; // a new phase gets it up (its roar is its own pause)
        volleyLeft = 0;
        clawAt = clawUntil = 0;
        if (phase == 2) { // its wing breaks: it falls (aiStep drops it, the shift would hold a velocity set here)
            crashing = true;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), getX(), getY() + 2, getZ(), 80, 1.5, 1, 1.5, 0.3);
            level.playSound(null, blockPosition(), SoundEvents.SKELETON_DEATH, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.3F);
        } else if (phase == 3) { // the rift gathers its bones and gives it wings of the void (the circle lifts it)
            crashing = false;
            level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 1, getZ(), 60, 1.5, 1.5, 1.5, 0.1);
            level.sendParticles(new DustParticleOptions(VOID, 2.0F), getX(), getY() + 1, getZ(), 80, 3, 1, 3, 0);
            level.playSound(null, blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.6F);
            nextMeal = level.getGameTime() + 400;
        }
    }

    @Override
    public void aiStep() {
        striding = circling = false; // set again this tick by whatever drives it (moves and specials run inside super)
        super.aiStep();
        if (level() instanceof ServerLevel server && !dormant()) {
            double floor = floorUnder();
            LivingEntity target = getTarget();
            boolean fighting = target != null && target.isAlive();
            if (crashing) { // its wing broken, it drops onto the floor
                steer(new Vec3(getX(), floor + 0.05, getZ()), 0.9);
                if (getY() - floor < 0.3) {
                    crashing = false;
                    fxNova(new Vec3(getX(), floor, getZ()), 4F, 12, BONE);
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), getX(), floor + 0.3, getZ(), 50, 1.5, 0.2, 1.5, 0.2);
                    server.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 0.5F);
                }
            } else if (stunned()) { // crashed or resting: it lies on the floor (a resting one comes down gently)
                steer(new Vec3(getX(), floor + 0.05, getZ()), restPose ? 0.35 : 0.9);
                if (restPose && tickCount % 10 == 0) server.sendParticles(ParticleTypes.SCULK_SOUL, mouth().x, mouth().y, mouth().z, 2, 0.3, 0.2, 0.3, 0.01);
            } else if (arenaFight() && fighting && !busy() && !shifting() && !introducing()) {
                if (grounded()) stalk(server, target, floor);
                else {
                    circle(target, floor, 1.0);
                    if (volleyLeft == 0 && server.getGameTime() >= nextVolley && hasLineOfSight(target)) { // a volley of bone spikes
                        volleyLeft = phase() >= 3 ? 4 : 3;
                        nextShot = server.getGameTime();
                        nextVolley = server.getGameTime() + (phase() >= 3 ? 45 : 50);
                        server.broadcastEntityEvent(this, SWING); // its jaws open
                        playSound(SoundEvents.ENDER_DRAGON_SHOOT, 1.2F, 0.7F);
                    }
                }
            }
            if (volleyLeft > 0 && server.getGameTime() >= nextShot) { // one spike every 4 ticks, each at where its prey is now
                if (fighting && !stunned() && !shifting()) {
                    fireBolt(mouth(), target, hitFor(0.3), BONE, 0, 0.95);
                    playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 0.5F);
                    volleyLeft--;
                    nextShot = server.getGameTime() + 4;
                } else volleyLeft = 0;
            }
            if (clawAt > 0 && server.getGameTime() >= clawAt) { // the raised forefoot comes down
                clawAt = 0;
                if (fighting && !stunned() && !shifting()) claw(server);
            }
            if (steered != tickCount) {
                double dy = floor + hoverHeight() - getY();
                setDeltaMovement(getDeltaMovement().x, Math.max(-0.4, Math.min(0.3, dy * 0.1)), getDeltaMovement().z);
                if (grounded()) setDeltaMovement(getDeltaMovement().multiply(0.6, 1, 0.6)); // dragging itself
            }
            // its bank eases toward the circle's turn (one step a tick, so the roll never snaps)
            int bankAim = circling ? (orbitDir > 0 ? -6 : 6) : 0;
            bank += Integer.signum(bankAim - bank);
            int shown = (stunned() ? (restPose ? V_RESTING : V_CRASHED) : 0) | (striding ? V_STRIDING : 0)
                    | (server.getGameTime() < clawUntil ? V_CLAW : 0) | ((bank + 8) & 15) << V_BANK_SHIFT;
            if (visual() != shown) setVisual(shown);
            minions.removeIf(id -> !(server.getEntity(id) instanceof Entity e) || !e.isAlive());
            if (phase() == 3 && !familiar() && server.getGameTime() >= nextMeal) eatMinion(server);
        }
        if (level().isClientSide() && !dormant()) {
            if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.SCULK_SOUL, getRandomX(1.0), getRandomY(), getRandomZ(1.0), 0, 0.03, 0);
            if (phase() >= 3 && !familiar()) { // void wings
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 side = Vec3.directionFromRotation(0, getYRot() + 90 * s).scale(2 + random.nextDouble() * 3);
                    level().addParticle(ParticleTypes.REVERSE_PORTAL, getX() + side.x, getY() + getBbHeight() * 0.7, getZ() + side.z, 0, -0.02, 0);
                }
            }
        }
    }

    // ---- How it hunts between its abilities ----

    /**
     * Flying: round the arena's middle (radius 9, 10 in phase III) at its hover height, 0.25 a tick (0.33), turning
     * the other way now and then; its body along its flight turned partway to its prey, its head on it. pace scales
     * the speed (a move or special that keeps it circling slows it).
     */
    private void circle(LivingEntity target, double floor, double pace) {
        long now = level().getGameTime();
        if (now >= nextTurn) {
            if (nextTurn > 0) orbitDir = -orbitDir;
            nextTurn = now + 140 + random.nextInt(100);
        }
        boolean last = phase() >= 3;
        Vec3 c = arenaCenter();
        double radius = last ? 10 : 9, speed = (last ? 0.33 : 0.25) * pace;
        double a = Math.atan2(getZ() - c.z, getX() - c.x) + orbitDir * speed / radius;
        double h = floor + hoverHeight() + Math.sin(tickCount * 0.06) * 0.35;
        Vec3 p = new Vec3(c.x + Math.cos(a) * radius, h, c.z + Math.sin(a) * radius);
        Vec3 heading = p.subtract(position());
        steer(p, speed);
        circling = true;
        float flight = heading.horizontalDistanceSqr() > 1e-4 ? yawOf(heading) : getYRot(), prey = yawOf(target.position().subtract(position()));
        face(flight + 0.4F * Mth.wrapDegrees(prey - flight), 6F);
        getLookControl().setLookAt(target, 20F, 20F);
    }

    /**
     * Phase II: it crawls after its prey (a limping 0.11..0.2 a tick, up over a rune in its way) and, close and facing
     * it, strikes every 26 ticks: two bites, then a claw rake (a forefoot raised 8 ticks first).
     */
    private void stalk(ServerLevel level, LivingEntity target, double floor) {
        Vec3 flat = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
        double dist = flat.length();
        face(yawOf(flat), 7F);
        getLookControl().setLookAt(target, 30F, 30F);
        if (dist > 4.6) {
            Vec3 dir = flat.normalize();
            double pace = 0.11 + 0.09 * Math.max(0, Math.sin(tickCount * 0.3)); // a lunge, a drag
            BlockPos ahead = BlockPos.containing(getX() + dir.x * 2.2, floor + 0.5, getZ() + dir.z * 2.2);
            boolean step = horizontalCollision || !level.getBlockState(ahead).isAir(); // something low in its way: up over it
            Vec3 to = keepInside(position().add(dir.scale(Math.min(dist - 4, 2))));
            steer(new Vec3(to.x, floor + (step ? 1.3 : 0.1), to.z), pace);
            striding = true;
            if (tickCount % 8 == 0) playSound(SoundEvents.RAVAGER_STEP, 0.8F, 0.5F);
            return;
        }
        steer(new Vec3(getX(), floor + 0.1, getZ()), 0.1);
        long now = level.getGameTime();
        float off = Math.abs(Mth.wrapDegrees(yawOf(flat) - getYRot()));
        if (now < nextBite || clawAt > 0 || off > 50) return;
        nextBite = now + 26;
        if (++attacks % 3 == 0) { // a claw rake: raised first, so it can be seen coming
            clawAt = now + 8;
            clawUntil = now + 18;
            playSound(SoundEvents.RAVAGER_ROAR, 0.8F, 1.4F);
        } else {
            target.invulnerableTime = 0; // a bite right after a spike still lands
            doHurtTarget(level, target);
        }
    }

    /** The claw rake: everything in an arc before it within 5.5, thrown aside. */
    private void claw(ServerLevel level) {
        level.broadcastEntityEvent(this, SWING);
        Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5.5, 2, 5.5), this::foe)) {
            Vec3 to = near.position().subtract(position()).multiply(1, 0, 1);
            if (to.length() > 5.5 || to.lengthSqr() < 1e-4 || to.normalize().dot(fwd) < 0.2) continue;
            if (hitOnce(level, near, hitFor(1.1))) {
                Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
                if (side.dot(to) < 0) side = side.scale(-1);
                near.setDeltaMovement(side.x * 1.2 + fwd.x * 0.3, 0.45, side.z * 1.2 + fwd.z * 0.3);
                near.hurtMarked = true;
            }
        }
        fxNova(position().add(fwd.scale(3)).add(0, 0.1, 0), 3F, 8, BONE);
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.5F);
        playSound(SoundEvents.RAVAGER_ATTACK, 1.2F, 0.8F);
    }

    /** Phase III: every twenty seconds it eats one of its own to mend. */
    private void eatMinion(ServerLevel level) {
        nextMeal = level.getGameTime() + 400;
        Entity meal = null;
        for (java.util.UUID id : minions) if (level.getEntity(id) instanceof Entity e && (meal == null || e.distanceToSqr(this) < meal.distanceToSqr(this))) meal = e;
        if (meal == null) return;
        for (int k = 0; k <= 8; k++) {
            Vec3 p = meal.position().add(0, 0.8, 0).lerp(middle(), k / 8.0);
            level.sendParticles(ParticleTypes.SCULK_SOUL, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.02);
        }
        meal.discard();
        heal(getMaxHealth() * 0.08F);
        playSound(SoundEvents.GENERIC_EAT.value(), 2.0F, 0.4F);
    }

    private double floorUnder() {
        BlockPos.MutableBlockPos at = blockPosition().mutable();
        for (int i = 0; i < 20; i++) {
            if (!level().getBlockState(at).isAir()) return at.getY() + 1;
            at.move(0, -1, 0);
        }
        return getY() - hoverHeight();
    }

    /** The top of its arena's floor (in the arena; elsewhere the floor under it). */
    private Vec3 arenaCenter() {
        return arena() == null ? new Vec3(getX(), floorUnder(), getZ()) : Vec3.atBottomCenterOf(arena().above());
    }

    private double arenaFloor() {
        return arenaCenter().y;
    }

    /** A spot held inside its arena (minions raised past the rim would fall into the void; its runs stop short of the wall). */
    private Vec3 keepInside(Vec3 p) {
        if (arena() == null) return p;
        Vec3 c = arenaCenter(), off = p.subtract(c).multiply(1, 0, 1);
        double lim = Config.voidArenaRadius() - 3.5;
        return off.length() <= lim ? p : c.add(off.normalize().scale(lim)).add(0, p.y - c.y, 0);
    }

    /** The farthest point from there along a flat heading, at most max away, still inside the arena (keepInside's limit). */
    private Vec3 edgeAlong(Vec3 from, Vec3 dir, double max) {
        if (arena() == null) return from.add(dir.x * max, 0, dir.z * max);
        Vec3 c = arenaCenter();
        double lim = Config.voidArenaRadius() - 3.5, ox = from.x - c.x, oz = from.z - c.z;
        double b = ox * dir.x + oz * dir.z, disc = b * b - (ox * ox + oz * oz - lim * lim);
        double t = disc <= 0 ? 0 : Math.max(0, Math.min(max, -b + Math.sqrt(disc)));
        return from.add(dir.x * t, 0, dir.z * t);
    }

    /** Its jaws: the skull reaches far ahead of its body (the model drawn 2.6 times larger). */
    private Vec3 mouth() {
        if (familiar() && dormant()) return middle();
        double reach = grounded() || stunned() ? 4.2 : 5.0, up = grounded() || stunned() ? 1.6 : 2.6;
        return position().add(Vec3.directionFromRotation(0, yBodyRot).scale(reach)).add(0, up, 0);
    }

    /** Whether its breath reaches this one (blocks give cover): a clear line to its middle, as the breath is aimed. */
    private boolean breathReaches(ServerLevel level, Vec3 from, LivingEntity near) {
        var hit = level.clip(new net.minecraft.world.level.ClipContext(from, near.position().add(0, near.getBbHeight() * 0.5, 0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /** Shadow patches along a line on the floor (the warning for a run). */
    private void shadowLine(Vec3 from, Vec3 to, float radius, int life) {
        double len = from.distanceTo(to);
        int n = Math.max(1, (int) Math.ceil(len / 2.0));
        for (int k = 0; k <= n; k++) fxZone(from.lerp(to, k / (double) n), radius, life, SHADOW, AttackFxPayload.Z_SHADOW);
    }

    /**
     * The breath: aimed (a thin line), then a ray of sound sweeping as the client draws it. Its origin and heading are
     * fixed at the aim and its body held on it, so the ray, the model's head and the hits stay together. Over the whole
     * arena it first comes down low (its bone walls then stand between it and its prey) and breathes from its middle,
     * the head going round with the turn, the ray reaching chest height far off so it sweeps the whole floor.
     */
    private void breath(ServerLevel server, int tick, int strike, int length, LivingEntity target, boolean wholeArena) {
        int aim = wholeArena ? strike / 2 : 0;
        float reach = wholeArena ? 28F : 24F;
        if (wholeArena) steer(new Vec3(getX(), floorUnder() + 1.0, getZ()), tick < aim ? 0.4 : 0.05);
        else setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
        if (tick == 0) {
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 1.5F, 0.6F);
            if (wholeArena && target instanceof ServerPlayer sp) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden.breath_warning"), true);
            }
        }
        if (tick < aim) return;
        if (tick == aim) {
            Vec3 chest = target.position().add(0, target.getBbHeight() * 0.6, 0); // at the chest, not the feet
            breathYaw = yawOf(chest.subtract(position()));
            hold(breathYaw);
            if (wholeArena) {
                breathFrom = position().add(0, 2.6, 0);
                Vec3 flat = chest.subtract(breathFrom).multiply(1, 0, 1);
                Vec3 h = flat.lengthSqr() > 0.01 ? flat.normalize() : Vec3.directionFromRotation(0, breathYaw);
                breathDir = new Vec3(h.x, (chest.y - breathFrom.y) / Math.max(16, flat.length()), h.z).normalize();
            } else {
                breathFrom = mouth();
                breathDir = chest.subtract(breathFrom).normalize();
            }
            fxBeam(breathFrom, breathDir, reach, 0.12F, strike - aim, 0xFFFFFF, AttackFxPayload.CORE, 0, 0); // the aim, a thin line before the breath
        }
        hold(breathYaw);
        if (tick < strike) return;
        if (tick == strike) { // the breath drawn as a ray of sound sweeping as it does here
            if (wholeArena) fxBeam(breathFrom, breathDir, reach, 1.3F, length - strike, BREATH, AttackFxPayload.SONIC | AttackFxPayload.SWEEP_TURN, (float) (Math.PI * 2), 0);
            else fxBeam(breathFrom, breathDir, reach, 1.3F, length - strike, BREATH, AttackFxPayload.SONIC | AttackFxPayload.SWEEP_SINE, 0.8F, 0.4F);
        }
        double turn = wholeArena ? (tick - strike) / (double) (length - strike) * Math.PI * 2
                : Math.sin((tick - strike) / (double) (length - strike) * Math.PI) * 0.8 - 0.4;
        Vec3 dir = new Vec3(breathDir.x * Math.cos(turn) - breathDir.z * Math.sin(turn), breathDir.y, breathDir.x * Math.sin(turn) + breathDir.z * Math.cos(turn));
        if (tick % 4 != 0) return;
        for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach), this::foe)) {
            Vec3 to = near.position().add(0, near.getBbHeight() * 0.5, 0).subtract(breathFrom);
            double along = to.dot(dir);
            if (along < 0 || along > reach || to.subtract(dir.scale(along)).length() > 2.5 || !breathReaches(server, breathFrom, near)) continue;
            near.invulnerableTime = 0;
            near.hurtServer(server, damageSources().sonicBoom(this), hitFor(0.8));
            near.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), this);
            near.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1), this);
            onHitAffixes(server, near);
        }
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 0.8F, 0.7F);
    }

    /** One bone falling from high over a spot on the floor, its shadow there first (it lands about 11 ticks later). */
    private void boneFrom(Vec3 spot, float damage) {
        fxZone(spot, 1.1F, 22, SHADOW, AttackFxPayload.Z_SHADOW);
        fireBolt(spot.add(0, 11, 0), new Vec3(0, -1, 0), damage, BONE, 0, 1.0);
    }

    // ---- The moves (as a familiar, and in its fight by phase) ----

    @Override
    protected void performMove(RiftCreature.Move move, int tick, LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) return;
        switch (move.id()) {
            case "bone_rain" -> { // bones fall round its prey, each one's shadow on the floor first
                if (arenaFight() && !grounded()) circle(target, floorUnder(), 0.5); // it keeps circling, slower
                else setDeltaMovement(getDeltaMovement().multiply(0.5, 1, 0.5));
                if (tick == 0) playSound(SoundEvents.ENDER_DRAGON_FLAP, 1.5F, 0.6F);
                if (tick >= move.strike() && tick % 2 == 0) {
                    double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * 5;
                    Vec3 spot = target.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                    boneFrom(new Vec3(spot.x, arena() != null ? arenaFloor() : target.getY(), spot.z), hitFor(0.6));
                }
            }
            case "sonic_breath" -> breath(server, tick, move.strike(), move.length(), target, false);
            case "wyrm_dive" -> { // its shadow marks the floor, then it falls on that spot
                if (tick == 0) {
                    diveLanded = false;
                    diveAt = target.position();
                    fxZone(diveAt, 2.6F, move.strike(), SHADOW, AttackFxPayload.Z_SHADOW, target); // its shadow on the floor, creeping after the prey
                    playSound(SoundEvents.PHANTOM_SWOOP, 1.5F, 0.5F);
                }
                if (diveLanded) return; // crashed (the stun holds it on the floor) or, as a familiar, thrown back up
                if (tick < move.strike()) { // it climbs, drawing in over the spot
                    diveAt = diveAt.lerp(target.position(), 0.1);
                    Vec3 flat = new Vec3(diveAt.x - getX(), 0, diveAt.z - getZ());
                    Vec3 drift = flat.lengthSqr() > 1 ? flat.normalize().scale(0.12) : Vec3.ZERO;
                    steer(position().add(drift.x, 0.1, drift.z), 0.2);
                    turnTo(diveAt, 15F);
                    return;
                }
                if (tick == move.strike()) fxZone(diveAt, 2.6F, 16, SHADOW, AttackFxPayload.Z_SHADOW); // the shadow stops where it will land
                turnTo(diveAt, 30F);
                steer(diveAt, Math.min(1.6, 0.7 + 0.15 * (tick - move.strike()))); // gathering speed as it falls, never a jump across
                if (distanceToSqr(diveAt) < 2.25 || onGround() || horizontalCollision || tick >= move.length() - 1) {
                    diveLanded = true; // it strikes once
                    Vec3 at = distanceToSqr(diveAt) < 2.25 ? diveAt : new Vec3(getX(), floorUnder(), getZ());
                    for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(3, 2, 3), this::foe)) {
                        near.invulnerableTime = 0;
                        near.hurtServer(server, damageSources().mobAttack(this), hitFor(1.8));
                        onHitAffixes(server, near);
                        near.setDeltaMovement(near.getDeltaMovement().add(0, 0.7, 0));
                        near.hurtMarked = true;
                    }
                    fxNova(at, 4F, 14, BONE);
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 0.6F);
                    if (arenaFight()) stun(server, 70, target, false, "dragon_down"); // it crashes into the floor and lies there
                    else setDeltaMovement(0, 0.8, 0);
                }
            }
            case "sonic_roar" -> { // a roar in a cone before it
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick == 0) playSound(SoundEvents.ENDER_DRAGON_GROWL, 1.5F, 0.8F);
                if (tick <= move.strike()) turnTo(target.position(), 20F); // facing its prey, so the roar leaves its jaws toward it
                if (tick == move.strike()) {
                    Vec3 look = target.position().subtract(position()).multiply(1, 0, 1).normalize();
                    for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(10, 3, 10), this::foe)) {
                        Vec3 to = near.position().subtract(position()).multiply(1, 0, 1);
                        if (to.length() > 10 || to.normalize().dot(look) < 0.5) continue;
                        near.invulnerableTime = 0;
                        near.hurtServer(server, damageSources().sonicBoom(this), hitFor(1.2));
                        near.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1), this);
                        near.setDeltaMovement(to.normalize().x * 1.4, 0.5, to.normalize().z * 1.4);
                        near.hurtMarked = true;
                    }
                    Vec3 jaws = mouth(); // drawn from its jaws to where the cone ends (10 from its middle), not past it
                    fxBeam(jaws, look, (float) Math.max(3, 10 - jaws.subtract(position()).horizontalDistance()), 2.8F, 14, BREATH, AttackFxPayload.SONIC, 0, 0);
                    playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.5F, 0.5F);
                }
            }
            default -> {}
        }
    }

    // ---- The fight's specials (one every 2 to 4 seconds once the last ends) ----

    @Override
    protected int specialGap() {
        return 40 + random.nextInt(40);
    }

    @Override
    protected String nextSpecial(int phase, LivingEntity target) {
        if (stunned() || crashing) return null;
        String pick = pickSpecial(phase, target);
        if (pick != null && pick.equals(lastSpecial) && !pick.equals("bone_walls")) pick = pickSpecial(phase, target); // seldom the same twice
        if (pick != null) lastSpecial = pick;
        return pick;
    }

    private String pickSpecial(int phase, LivingEntity target) {
        boolean walls = level().getGameTime() + specialLength("arena_breath") + 20 < wallsUntil; // they must stand through the whole breath
        double near = distanceTo(target);
        return switch (phase) {
            case 2 -> switch (random.nextInt(5)) {
                case 0, 1 -> "charge";
                case 2 -> near < 7 ? "tail_sweep" : "charge"; // the tail only reaches what is close
                case 3 -> "bone_walls";
                default -> minions.size() < 4 ? "raise_bones" : "charge";
            };
            case 3 -> switch (random.nextInt(6)) {
                case 0, 1 -> "strafing_run";
                case 2 -> minions.size() < 4 ? "wyverns" : "bone_barrage";
                case 3 -> "bone_barrage";
                default -> walls ? "arena_breath" : "bone_walls"; // never the breath without cover
            };
            default -> random.nextInt(5) < 3 ? "strafing_run" : "bone_barrage";
        };
    }

    @Override
    protected int specialLength(String id) {
        return switch (id) {
            case "arena_breath" -> 80;
            case "bone_walls", "raise_bones", "wyverns" -> 24;
            case "strafing_run" -> strafeLen;  // set at its aim from the pass's length
            case "bone_barrage" -> 54;
            case "charge" -> chargeLen;        // set at its aim from the run's length
            default -> 22;                     // tail sweep
        };
    }

    @Override
    protected void performSpecial(ServerLevel server, String id, int tick, LivingEntity target) {
        switch (id) {
            case "tail_sweep" -> { // a shadow round it, then the tail whips round
                steer(new Vec3(getX(), floorUnder() + 0.1, getZ()), 0.05);
                if (tick == 0) {
                    fxZone(new Vec3(getX(), floorUnder(), getZ()), 6F, 18, SHADOW, AttackFxPayload.Z_SHADOW);
                    playSound(SoundEvents.SKELETON_AMBIENT, 2.0F, 0.4F);
                }
                if (tick == 14) {
                    for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(6, 2, 6), this::foe)) {
                        if (near.distanceToSqr(this) > 36) continue;
                        if (!hitOnce(server, near, hitFor(1.5))) continue;
                        Vec3 out = near.position().subtract(position()).multiply(1, 0, 1).normalize();
                        near.setDeltaMovement(out.x * 1.6, 0.5, out.z * 1.6);
                        near.hurtMarked = true;
                    }
                    fxNova(position(), 6F, 10, BONE);
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.4F);
                }
            }
            case "bone_walls" -> boneWalls(server, tick, target);
            case "raise_bones" -> { // skeleton creatures of the rift rise from the floor
                if (tick == 16) {
                    for (int i = 0; i < 2; i++) {
                        double a = random.nextDouble() * Math.PI * 2;
                        RiftBeast risen = spawnMinion(server, RiftCreature.HOLLOW_STALKER, keepInside(target.position().add(Math.cos(a) * 4, 0, Math.sin(a) * 4)), 500);
                        if (risen != null) {
                            risen.setCustomName(net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden.risen"));
                            minions.add(risen.getUUID());
                        }
                    }
                    playSound(SoundEvents.SKELETON_AMBIENT, 2.0F, 0.5F);
                }
            }
            case "wyverns" -> {
                if (tick == 10) {
                    for (int i = 0; i < 2 && minions.size() < 5; i++) {
                        net.minecraft.world.entity.monster.Phantom wyvern = EntityType.PHANTOM.create(server, EntitySpawnReason.MOB_SUMMONED);
                        if (wyvern == null) continue;
                        wyvern.snapTo(getX() + random.nextDouble() * 4 - 2, getY() + 2, getZ() + random.nextDouble() * 4 - 2, 0, 0);
                        wyvern.setCustomName(net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden.wyvern"));
                        wyvern.setTarget(target);
                        if (server.addFreshEntity(wyvern)) minions.add(wyvern.getUUID());
                    }
                    playSound(SoundEvents.PHANTOM_AMBIENT, 1.5F, 0.5F);
                }
            }
            case "arena_breath" -> breath(server, tick, 24, 80, target, true); // the whole arena: hide behind the bone walls
            case "strafing_run" -> strafingRun(server, tick, target);
            case "bone_barrage" -> { // wings flung up, bones over half the arena, each shadowed on the floor before it lands
                circle(target, floorUnder(), 0.4);
                if (tick == 0) {
                    playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 0.9F);
                    playSound(SoundEvents.ENDER_DRAGON_FLAP, 2.0F, 0.5F);
                }
                if (tick >= 10 && tick < 42) {
                    double floorY = arenaFloor();
                    for (int n = 0; n < (phase() >= 3 ? 2 : 1); n++) {
                        double a = random.nextDouble() * Math.PI * 2, r = random.nextInt(3) == 0 ? random.nextDouble() * 2 : 2 + random.nextDouble() * 7;
                        Vec3 spot = keepInside(new Vec3(target.getX() + Math.cos(a) * r, floorY, target.getZ() + Math.sin(a) * r));
                        boneFrom(new Vec3(spot.x, floorY, spot.z), hitFor(0.45));
                    }
                    if (tick % 4 == 0) playSound(SoundEvents.SKELETON_SHOOT, 1.2F, 0.4F);
                }
            }
            case "charge" -> charge(server, tick, target);
            default -> {}
        }
    }

    /**
     * Bone walls: three 5-wide walls burst from the floor round it toward its prey. Their places are chosen at tick 4
     * and shadowed on the floor; they rise at 16 (nobody is walled in: whoever stands on a cell is thrown up instead).
     */
    private void boneWalls(ServerLevel server, int tick, LivingEntity target) {
        if (tick == 4) {
            wallCells.clear();
            wallLines.clear();
            Vec3 to = target.position().subtract(position()).multiply(1, 0, 1);
            to = to.lengthSqr() > 0.01 ? to.normalize() : Vec3.directionFromRotation(0, getYRot());
            int floorY = (int) Math.floor(floorUnder() + 0.01); // the first layer of air over the floor under it (it may hover high)
            for (int k = 0; k < 3; k++) {
                double a = (k - 1) * 0.9;
                Vec3 dir = new Vec3(to.x * Math.cos(a) - to.z * Math.sin(a), 0, to.x * Math.sin(a) + to.z * Math.cos(a));
                Vec3 mid = position().add(dir.scale(5));
                Vec3 across = new Vec3(-dir.z, 0, dir.x);
                int wall = 0;
                for (int w = -2; w <= 2; w++) {
                    Vec3 at = mid.add(across.scale(w));
                    BlockPos ground = BlockPos.containing(at.x, floorY - 1, at.z);
                    for (int i = 0; i < 2 && server.getBlockState(ground).isAir(); i++) ground = ground.below();
                    // only on the floor with room over it: not on runes or pillars, not over the void past the rim
                    if (server.getBlockState(ground).isAir() || !server.getBlockState(ground.above()).isAir()) continue;
                    wallCells.add(ground);
                    wall++;
                }
                if (wall > 0) {
                    Vec3 foot = new Vec3(mid.x, floorY, mid.z);
                    wallLines.add(new Vec3[] {foot.subtract(across.scale(2.5)), across});
                    shadowLine(foot.subtract(across.scale(2)), foot.add(across.scale(2)), 1.2F, 16);
                }
            }
            playSound(SoundEvents.BONE_BLOCK_BREAK, 1.5F, 0.5F);
        }
        if (tick == 16) {
            int placed = 0;
            for (BlockPos ground : wallCells) {
                AABB column = new AABB(ground.above()).expandTowards(0, 2, 0);
                boolean someone = !server.getEntitiesOfClass(LivingEntity.class, column, e -> e != this && e.isAlive()).isEmpty();
                if (!someone) for (int h = 1; h <= 3; h++) if (tempBlock(server, ground.above(h), Blocks.BONE_BLOCK.defaultBlockState(), 300)) placed++;
                for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, new AABB(ground.above()).inflate(0.5, 2, 0.5), this::foe)) {
                    if (!hitOnce(server, near, hitFor(1.0))) continue;
                    near.setDeltaMovement(near.getDeltaMovement().add(0, 0.8, 0));
                    near.hurtMarked = true;
                }
            }
            for (Vec3[] line : wallLines) fxLine(line[0], line[1], 5F, 2F, 14, AttackFxPayload.L_BONE);
            if (placed > 0) wallsUntil = server.getGameTime() + 300; // the whole-arena breath waits for real cover
            wallCells.clear();
            wallLines.clear();
            playSound(SoundEvents.BONE_BLOCK_PLACE, 2.0F, 0.5F);
        }
    }

    /**
     * The strafing run (phases I and III): it pulls up and turns on its prey; at STRAFE_AIM its line is fixed (from where
     * it is through where its prey stands, 10 past, held inside the arena) and shadowed on the floor; it swoops down to
     * the line's start by STRAFE_DIVE and flies it low (2.5 over the floor, 0.75 a tick), breathing down: a line of
     * burning floor (sculk fire, void fire in phase III) behind its jaws, and what it passes over is struck once
     * (Wither II). Into its own bone wall it crashes; else it glides out and lands to catch its breath (onSpecialEnd).
     */
    private void strafingRun(ServerLevel server, int tick, LivingEntity target) {
        double floorY = arenaFloor();
        boolean last = phase() >= 3;
        if (tick == 0) {
            strafeLen = 140;
            strafeCrashed = false;
            strafeHit.clear();
            scorchedAt = null;
            playSound(SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 0.8F);
            playSound(SoundEvents.ENDER_DRAGON_FLAP, 2.0F, 0.6F);
        }
        if (tick < STRAFE_AIM) { // pulling up, turning on its prey
            steer(new Vec3(getX(), floorY + 7, getZ()), 0.3);
            turnTo(target.position(), 10F);
            return;
        }
        if (tick == STRAFE_AIM) {
            Vec3 prey = new Vec3(target.getX(), floorY, target.getZ());
            Vec3 from = keepInside(new Vec3(getX(), floorY, getZ()));
            Vec3 flat = prey.subtract(from);
            Vec3 dir = flat.lengthSqr() > 4 ? flat.normalize() : Vec3.directionFromRotation(0, getYRot());
            if (flat.length() < 8) from = keepInside(prey.subtract(dir.scale(8))); // never a short pass: from at least 8 before its prey
            Vec3 run = prey.subtract(from);
            strafeDir = run.lengthSqr() > 1 ? run.normalize() : dir;
            strafeFrom = from;
            strafeTo = edgeAlong(from, strafeDir, run.length() + 10);
            int pass = Math.max(1, (int) Math.ceil(strafeFrom.distanceTo(strafeTo) / STRAFE_SPEED));
            strafeLen = STRAFE_DIVE + pass + STRAFE_OUT;
            shadowLine(strafeFrom, strafeTo, 1.7F, strafeLen - tick - STRAFE_OUT + 10); // its pass shadowed on the floor until it is flown
            playSound(SoundEvents.PHANTOM_SWOOP, 2.0F, 0.5F);
            if (target instanceof ServerPlayer sp) sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden.strafe_warning"), true);
        }
        if (tick < STRAFE_DIVE) { // swooping down to the start of its pass
            steer(strafeFrom.add(0, LOW, 0), Math.min(0.75, 0.25 + 0.03 * (tick - STRAFE_AIM)));
            turnTo(strafeTo, 12F);
            return;
        }
        int pass = strafeLen - STRAFE_DIVE - STRAFE_OUT, k = tick - STRAFE_DIVE;
        if (k <= pass && !strafeCrashed) {
            Vec3 along = strafeFrom.lerp(strafeTo, Math.min(1.0, k / (double) pass)).add(0, LOW, 0);
            steer(along, STRAFE_SPEED * 1.4); // a lagging body catches up, never faster than that
            hold(yawOf(strafeDir));
            int colour = last ? VOID : SCULK;
            // its breath, down from its lowered jaws to the floor ahead of it
            Vec3 jaws = position().add(strafeDir.scale(3.8)).add(0, 1.4, 0);
            Vec3 down = strafeDir.scale(0.55).add(0, -1, 0).normalize();
            double reach = (jaws.y - floorY) / -down.y;
            if (k % 3 == 0) fxBeam(jaws, down, (float) reach, 0.8F, 4, last ? VOID : BREATH, AttackFxPayload.SONIC, 0, 0);
            Vec3 lands = jaws.add(down.scale(reach));
            server.sendParticles(last ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.SOUL_FIRE_FLAME, lands.x, floorY + 0.3, lands.z, 6, 0.6, 0.2, 0.6, 0.03);
            if (scorchedAt == null || scorchedAt.distanceToSqr(lands.x, floorY, lands.z) >= 1.6 * 1.6) { // the burning line it leaves
                scorchedAt = new Vec3(lands.x, floorY, lands.z);
                floorZone(scorchedAt.add(0, 0.02, 0), 1.6, 120, hitFor(0.12), colour, true, false);
            }
            // what it passes over (under its body or its breath) is struck once
            Vec3 p0 = position(), p1 = position().add(strafeDir.scale(5.5));
            AABB under = new AABB(p0.x, floorY - 1, p0.z, p1.x, floorY + 4.5, p1.z).inflate(2.2, 0, 2.2);
            for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, under, this::foe)) {
                if (flatDistanceToSegment(near.position(), p0, p1) > 2.2 || near.getY() > getY() + 1 || strafeHit.contains(near.getUUID())) continue;
                strafeHit.add(near.getUUID());
                if (!hitOnce(server, near, hitFor(0.9))) continue;
                near.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1), this);
                if (last) near.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), this);
                near.setDeltaMovement(near.getDeltaMovement().add(strafeDir.x * 0.6, 0.4, strafeDir.z * 0.6));
                near.hurtMarked = true;
            }
            if (k % 5 == 0) playSound(SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 1.2F);
            if (horizontalCollision) { // into a block (its own bone wall): it crashes and the run ends
                strafeCrashed = true;
                strafeLen = tick + 1;
                fxNova(new Vec3(getX(), floorY, getZ()), 4F, 12, BONE);
                playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 0.6F);
            }
        } else if (!strafeCrashed) { // pulling out of it, gliding to a stop
            Vec3 on = keepInside(position().add(strafeDir.scale(0.3)));
            steer(new Vec3(on.x, floorY + LOW, on.z), 0.3);
        }
    }

    private static double flatDistanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        double abx = b.x - a.x, abz = b.z - a.z, len = abx * abx + abz * abz;
        double t = len < 1e-6 ? 0 : Math.max(0, Math.min(1, ((p.x - a.x) * abx + (p.z - a.z) * abz) / len));
        double dx = p.x - (a.x + abx * t), dz = p.z - (a.z + abz * t);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * The charge (phase II): it rears and paws, turning on its prey; at tick 10 its line is fixed (through its prey, 7
     * past, held inside the arena) and shadowed; from CHARGE_REAR it runs it at 0.8 a tick, striking once and throwing
     * aside what it meets. Into a block (rune, pillar, bone wall) or the arena's wall it crashes: stunned (onSpecialEnd).
     * Stopping short of the wall, it only skids to a halt.
     */
    private void charge(ServerLevel server, int tick, LivingEntity target) {
        double floorY = arenaFloor();
        if (tick == 0) {
            chargeLen = 90;
            chargeCrashed = chargeStopped = false;
            chargeHit.clear();
            chargeDir = Vec3.directionFromRotation(0, getYRot());
            chargeEnd = position();
            playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 0.6F);
        }
        if (tick < CHARGE_REAR) { // rearing, pawing the floor
            steer(new Vec3(getX(), floorUnder() + 0.1, getZ()), 0.05);
            if (tick < 10) turnTo(target.position(), 12F);
            if (tick == 10) {
                Vec3 from = keepInside(new Vec3(getX(), floorY, getZ()));
                Vec3 flat = new Vec3(target.getX() - from.x, 0, target.getZ() - from.z);
                chargeDir = flat.lengthSqr() > 1 ? flat.normalize() : Vec3.directionFromRotation(0, getYRot());
                double want = flat.length() + 7;
                chargeEnd = edgeAlong(from, chargeDir, want);
                chargeWall = from.distanceTo(chargeEnd) < want - 0.5; // its run meets the arena's wall
                int run = Math.max(1, (int) Math.ceil(from.distanceTo(chargeEnd) / CHARGE_SPEED));
                chargeLen = CHARGE_REAR + run + CHARGE_STOP;
                shadowLine(from, chargeEnd, 1.6F, chargeLen - tick);
                if (target instanceof ServerPlayer sp) sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden.charge_warning"), true);
            }
            if (tick >= 10) hold(yawOf(chargeDir));
            if (tick % 5 == 0) server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), getX(), floorY + 0.2, getZ(), 8, 1.2, 0.1, 1.2, 0.05);
            return;
        }
        if (chargeStopped) { // skidding to a halt (or lying where it crashed until the stun takes it)
            if (!chargeCrashed) steer(new Vec3(getX() + chargeDir.x * 0.15, floorUnder() + 0.1, getZ() + chargeDir.z * 0.15), 0.15);
            return;
        }
        steer(new Vec3(chargeEnd.x, floorUnder() + 0.1, chargeEnd.z), CHARGE_SPEED);
        hold(yawOf(chargeDir));
        striding = true;
        for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.8, 0.5, 0.8), this::foe)) {
            if (!chargeHit.add(near.getUUID()) || !hitOnce(server, near, hitFor(1.4))) continue;
            Vec3 to = near.position().subtract(position()).multiply(1, 0, 1);
            Vec3 side = new Vec3(-chargeDir.z, 0, chargeDir.x);
            if (side.dot(to) < 0) side = side.scale(-1);
            near.setDeltaMovement(side.x * 1.3 + chargeDir.x * 0.6, 0.55, side.z * 1.3 + chargeDir.z * 0.6);
            near.hurtMarked = true;
        }
        if (tick % 3 == 0) {
            playSound(SoundEvents.RAVAGER_STEP, 1.4F, 0.6F);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), getX(), floorY + 0.2, getZ(), 6, 1.0, 0.1, 1.0, 0.1);
        }
        boolean arrived = new Vec3(getX(), 0, getZ()).distanceToSqr(chargeEnd.x, 0, chargeEnd.z) < 0.36;
        if (horizontalCollision || (arrived && chargeWall)) { // slammed into something: it crashes and the charge ends
            chargeStopped = chargeCrashed = true;
            chargeLen = tick + 1;
            Vec3 front = position().add(chargeDir.scale(2.5));
            fxNova(new Vec3(front.x, floorY, front.z), 4F, 12, BONE);
            server.sendParticles(ParticleTypes.EXPLOSION, front.x, floorY + 1, front.z, 3, 0.5, 0.5, 0.5, 0);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.6F, 0.5F);
            playSound(SoundEvents.RAVAGER_STUNNED, 2.0F, 0.6F);
        } else if (arrived || tick >= chargeLen - CHARGE_STOP) {
            chargeStopped = true;
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDER_DRAGON_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return gradePitch(0.6F);
    }
}
