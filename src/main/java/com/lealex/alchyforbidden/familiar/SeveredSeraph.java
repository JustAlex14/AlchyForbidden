package com.lealex.alchyforbidden.familiar;

import com.lealex.alchyforbidden.network.AttackFxPayload;
import com.lealex.alchyforbidden.network.SpellFxPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Severed Seraph (ring IV, a great one of the void): an angel that fell into the rift and came apart, its wings
 * blades of pale light, its halo broken and burning, its face a mask with nothing behind it.
 * Phase I: a dive strike warned by a column of light, a fan of blades that come back like boomerangs, a storm of burning
 * feathers. Phase II, its wings burning: halo shards orbit it as blades, a blink slash three times, a halo ring along
 * the floor to jump, a judgement beam two seconds after its mark. Phase III (Void-Rifted only): the mask breaks, three
 * mirror images that cannot be hurt fight beside it, and judgement beams chase its prey. As a familiar it rests as its
 * broken halo at its owner's shoulder; in a fight it is a fast, blinking striker.
 * Traits: Feathered Blades (5, each blow throws two blades), Grace (10, one blow in four passes), Severed Halo (15, its
 * halo burns what stands close), Ascension (20, once a call, badly hurt, it rises and mends).
 */
public class SeveredSeraph extends RiftBeast {
    private static final int LIGHT = 0xE8E0FF, VIOLET = 0xB98CFF;
    private boolean ascended, diveLanded;
    private long nextMirror;
    private int steered = -1;
    private Vec3 diveAt = Vec3.ZERO;
    /** After each blow it strikes in its fight it hangs still a moment, low: the time to hit it back. */
    private long lingerUntil;
    private final java.util.List<Vec3> marks = new java.util.ArrayList<>();
    private final java.util.List<Long> markTimes = new java.util.ArrayList<>();
    private final java.util.List<java.util.UUID> mirrors = new java.util.ArrayList<>();

    public SeveredSeraph(EntityType<? extends SeveredSeraph> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)
                .add(Attributes.ATTACK_DAMAGE, 14.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FLYING_SPEED, 0.3) // slower than it was (0.42): it could not be caught
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 3.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
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
        return 0.28F; // resting, the whole of it drawn small
    }

    // ---- Its entrance: a column of light, it comes down through it folded in its wings, then opens them and its halo lights ----

    @Override
    protected Vec3 introPath(Vec3 home, float p) {
        return home.add(0, 1.6 + 11 * (1 - ease(p / 0.6F)), 0);
    }

    @Override
    protected void introEffects(ServerLevel level, int t, Vec3 home) {
        if (t == 0) {
            fxColumn(home, 1.4F, 16F, 0, 64, LIGHT);
            level.playSound(null, net.minecraft.core.BlockPos.containing(home), net.minecraft.sounds.SoundEvents.BEACON_AMBIENT, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.7F);
        }
        if (t < 60 && t % 2 == 0) level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, getX(), getY() + 1.5, getZ(), 2, 0.6, 0.8, 0.6, 0.01);
        if (t == 64) { // its wings open, its halo lights
            fxNova(home, 5F, 16, LIGHT);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, getX(), getY() + 1.5, getZ(), 40, 1.5, 1.0, 1.5, 0.08);
            level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 1.2F);
        }
        if (t == 80) level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.8F);
    }

    private static float ease(float x) {
        x = Math.max(0F, Math.min(1F, x));
        return x * x * (3 - 2 * x);
    }

    @Override
    public RiftCreature creature() {
        return RiftCreature.SEVERED_SERAPH;
    }

    private Vec3 heart() {
        return position().add(0, getBbHeight() * 0.6, 0);
    }

    /** An ability drives its body this tick: straight toward a point, never faster than maxStep a tick (the hover waits). */
    private void steer(Vec3 to, double maxStep) {
        Vec3 d = to.subtract(position());
        double l = d.length();
        setDeltaMovement(l > maxStep ? d.scale(maxStep / l) : d);
        getNavigation().stop();
        steered = tickCount;
    }

    @Override
    protected int boltStyle(int color, int fire, boolean boomerang) {
        return fire > 0 ? AttackFxPayload.FEATHER : AttackFxPayload.BLADE;
    }

    @Override
    protected float boltSize() {
        return 1.3F;
    }

    @Override
    protected java.util.List<String> specialIds() {
        return java.util.List.of("halo_ring", "judgement", "chasing_judgement");
    }

    @Override
    protected boolean moveAllowed(RiftCreature.Move move) {
        if (familiar()) return true;
        return !move.id().equals("blink_slash") || phase() >= 2;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && knows(5) && target instanceof LivingEntity living) { // Feathered Blades (four once its wings burn)
            int blades = phase() >= 2 && !familiar() ? 4 : 2;
            Vec3 dir = living.position().add(0, living.getBbHeight() * 0.5, 0).subtract(heart()).normalize(); // at its middle, not level over its head
            for (int k = 0; k < blades; k++) {
                double a = (k - (blades - 1) / 2.0) * 0.25;
                Vec3 d = new Vec3(dir.x * Math.cos(a) - dir.z * Math.sin(a), dir.y, dir.x * Math.sin(a) + dir.z * Math.cos(a));
                fireBolt(heart(), d, hitFor(0.4), LIGHT, 1, 1.2);
            }
        }
        if (hit) playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.4F);
        if (hit && arena() != null && !familiar()) lingerUntil = level.getGameTime() + 25;
        return hit;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (knows(10) && random.nextInt(7) == 0 && level.getGameTime() >= lingerUntil && source.getEntity() != null) { // Grace (never while it lingers)
            level.sendParticles(new DustParticleOptions(LIGHT, 1.2F), getX(), getY() + 1, getZ(), 12, 0.4, 0.6, 0.4, 0);
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
            return false;
        }
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && knows(20) && !ascended && getHealth() < getMaxHealth() * 0.3F) { // Ascension
            ascended = true;
            heal(getMaxHealth() * 0.25F);
            addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 200, 1));
            setDeltaMovement(0, 1.0, 0);
            hurtMarked = true;
            fxColumn(position(), 1.3F, 18F, 0, 24, LIGHT);
            playSound(SoundEvents.BEACON_ACTIVATE, 1.5F, 0.8F);
        }
        return hurt;
    }

    @Override
    protected void onPhase(ServerLevel level, int phase) {
        if (phase == 2) { // its wings catch fire
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.5, getZ(), 80, 2, 0.8, 2, 0.05);
            playSound(SoundEvents.BLAZE_AMBIENT, 1.5F, 0.5F);
        } else if (phase == 3) { // the mask breaks
            level.sendParticles(new DustParticleOptions(LIGHT, 2.0F), getX(), getY() + getBbHeight(), getZ(), 60, 0.6, 0.6, 0.6, 0);
            playSound(SoundEvents.GLASS_BREAK, 2.0F, 0.5F);
            nextMirror = 0;
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel server && !dormant()) {
            BlockPos below = blockPosition().below(2);
            if (steered != tickCount && server.getGameTime() < lingerUntil && !busy()) { // it lingers after its blow, sinking a little
                steer(position().add(0, -0.04, 0), 0.05);
            }
            if (steered == tickCount) {
                // an ability drives it this tick
            } else if (!level().getBlockState(below).isAir()) {
                setDeltaMovement(getDeltaMovement().add(0, 0.03, 0));
            } else if (level().getBlockState(blockPosition().below(4)).isAir() && getTarget() == null) {
                setDeltaMovement(getDeltaMovement().add(0, -0.02, 0));
            }
            if (knows(15) && isAlive() && tickCount % 20 == 0) { // Severed Halo
                for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3), this::foe)) near.igniteForSeconds(3);
            }
            boolean arenaFight = arena() != null && !familiar() && !echo() && isAlive(); // nothing more once it is slain
            if (arenaFight && phase() >= 2 && tickCount % 10 == 0) { // halo shards orbiting as blades
                for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.8), this::foe)) {
                    near.invulnerableTime = 0;
                    near.hurtServer(server, damageSources().indirectMagic(this, this), hitFor(0.35));
                }
            }
            mirrors.removeIf(id -> !(server.getEntity(id) instanceof Entity e) || !e.isAlive());
            if (arenaFight && phase() >= 3 && getTarget() != null && server.getGameTime() >= nextMirror && mirrors.isEmpty()) { // three mirror images
                nextMirror = server.getGameTime() + 400;
                for (int i = 0; i < 3; i++) {
                    RiftBeast mirror = spawnMinion(server, RiftCreature.SEVERED_SERAPH, position().add(Math.cos(i * 2.1) * 3, 0.5, Math.sin(i * 2.1) * 3), 300);
                    if (mirror == null) continue;
                    mirror.setInvulnerable(true); // only the real one can be hurt
                    mirror.setCustomName(getDisplayName());
                    mirrors.add(mirror.getUUID());
                }
                playSound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.5F, 0.8F);
            }
            if (isAlive()) tickMarks(server); // no beam falls once it is slain
        }
        if (level().isClientSide() && !dormant()) { // the halo shards are its model's (phase II)
            if (phase() >= 2 && !familiar() && random.nextInt(2) == 0) level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getRandomX(1.4), getY() + getBbHeight() * 0.7, getRandomZ(1.4), 0, 0.02, 0);
        }
    }

    // ---- Judgement: a mark, then two seconds later a beam of light on it ----

    private void mark(ServerLevel level, Vec3 at) {
        marks.add(at);
        markTimes.add(level.getGameTime() + 40);
        SpellFxPayload.send(level, SpellFxPayload.RIFT, at.add(0, 8, 0), -1, 50, 1.2F);
        fxColumn(at, 1.8F, 8F, 40, 54, LIGHT); // the mark, then the beam of judgement
        playSound(SoundEvents.BEACON_POWER_SELECT, 1.2F, 1.2F);
    }

    private void tickMarks(ServerLevel level) {
        long now = level.getGameTime();
        for (int i = marks.size() - 1; i >= 0; i--) {
            Vec3 at = marks.get(i);
            if (now < markTimes.get(i)) continue;
            for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(1.8, 3, 1.8), this::foe)) {
                near.invulnerableTime = 0;
                near.hurtServer(level, damageSources().indirectMagic(this, this), hitFor(2.2));
                onHitAffixes(level, near);
            }
            level.playSound(null, BlockPos.containing(at), SoundEvents.LIGHTNING_BOLT_THUNDER, net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 1.6F);
            marks.remove(i);
            markTimes.remove(i);
        }
    }

    // ---- The moves (as a familiar, and in its fight by phase) ----

    @Override
    protected void performMove(RiftCreature.Move move, int tick, LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) return;
        switch (move.id()) {
            case "heavenly_dive" -> { // a column of light warns where it will fall, then it falls there
                if (tick == 0) {
                    diveLanded = false;
                    setVisual(0);
                    diveAt = target.position();
                    fxColumn(diveAt, 1.6F, 10F, move.strike(), move.strike() + 14, LIGHT, target, 0.08F); // the warning follows the prey as the aim does
                    playSound(SoundEvents.PHANTOM_SWOOP, 1.2F, 0.6F);
                }
                if (diveLanded) { // a moment knelt where it struck
                    steer(position(), 0.05);
                    return;
                }
                if (tick < move.strike()) { // it climbs, drawing in over the spot
                    diveAt = diveAt.lerp(target.position(), 0.08);
                    Vec3 flat = new Vec3(diveAt.x - getX(), 0, diveAt.z - getZ());
                    Vec3 drift = flat.lengthSqr() > 1 ? flat.normalize().scale(0.15) : Vec3.ZERO;
                    steer(position().add(drift.x, 0.3, drift.z), 0.4);
                    return;
                }
                steer(diveAt, Math.min(1.5, 0.8 + 0.2 * (tick - move.strike()))); // gathering speed as it falls, never a jump across
                if (distanceToSqr(diveAt) < 2.25 || onGround() || horizontalCollision || tick >= move.length() - 1) {
                    diveLanded = true; // it strikes once
                    setVisual(1);
                    Vec3 at = distanceToSqr(diveAt) < 2.25 ? diveAt : position();
                    for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(3, 2, 3), this::foe)) {
                        near.invulnerableTime = 0;
                        near.hurtServer(server, damageSources().mobAttack(this), hitFor(1.8));
                        onHitAffixes(server, near);
                        near.setDeltaMovement(near.getDeltaMovement().add(0, 0.6, 0));
                        near.hurtMarked = true;
                    }
                    fxNova(at, 3.5F, 12, LIGHT);
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 1.2F);
                }
            }
            case "blade_fan" -> { // seven blades that fly out and come back
                setDeltaMovement(getDeltaMovement().multiply(0.4, 0.4, 0.4));
                if (tick == move.strike()) {
                    Vec3 dir = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(heart()).normalize();
                    for (int i = -3; i <= 3; i++) {
                        double a = i * 0.18;
                        Vec3 d = new Vec3(dir.x * Math.cos(a) - dir.z * Math.sin(a), dir.y, dir.x * Math.sin(a) + dir.z * Math.cos(a));
                        fireBolt(heart(), d, hitFor(0.6), i % 2 == 0 ? LIGHT : VIOLET, 2, 0.8, null, true, 0);
                    }
                    playSound(SoundEvents.TRIDENT_THROW.value(), 1.2F, 1.2F);
                }
            }
            case "feather_storm" -> { // burning feathers thrown all round it
                setDeltaMovement(getDeltaMovement().multiply(0.3, 0.3, 0.3));
                if (tick >= move.strike() && tick % 2 == 0) {
                    double a = tick * 0.6;
                    Vec3 from = heart(); // thrown down to its prey's height where it stands, not level over its head
                    double flat = Math.max(2, Math.sqrt((target.getX() - getX()) * (target.getX() - getX()) + (target.getZ() - getZ()) * (target.getZ() - getZ())));
                    double drop = Math.max(-0.6, Math.min(0.1, (target.getY() + target.getBbHeight() * 0.5 - from.y) / flat));
                    for (int k = 0; k < 3; k++) {
                        double b = a + k * Math.PI * 2 / 3;
                        fireBolt(from, new Vec3(Math.cos(b), drop, Math.sin(b)), hitFor(0.4), LIGHT, 0, 0.7, null, false, 3);
                    }
                    if (tick % 6 == 0) playSound(SoundEvents.PHANTOM_FLAP, 1.0F, 1.4F);
                }
            }
            case "blink_slash" -> { // three times: through a small rift to behind its prey, and a cut
                if (tick >= move.strike() && (tick - move.strike()) % 10 == 0 && (tick - move.strike()) / 10 < 3) {
                    Vec3 back = target.position().subtract(Vec3.directionFromRotation(0, target.getYRot()).scale(1.8)).add(0, 0.3, 0);
                    Vec3 from = middle();
                    teleportTo(back.x, back.y, back.z);
                    float yaw = (float) (Math.atan2(target.getZ() - back.z, target.getX() - back.x) * (180 / Math.PI)) - 90F; // facing the one it cuts
                    setYRot(yaw);
                    setYBodyRot(yaw);
                    setYHeadRot(yaw);
                    Familiars.riftPuff(server, from, creature().color(), false);
                    Familiars.riftPuff(server, middle(), creature().color(), true);
                    target.invulnerableTime = 0;
                    target.hurtServer(server, damageSources().mobAttack(this), hitFor(1.0));
                    onHitAffixes(server, target);
                    server.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 1, target.getZ(), 2, 0.3, 0.2, 0.3, 0);
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 1.6F);
                }
            }
            default -> {}
        }
    }

    @Override
    protected void endMove(RiftCreature.Move move) {
        if (move.id().equals("heavenly_dive")) setVisual(0); // up from its landing
    }

    // ---- The fight's specials ----

    @Override
    protected String nextSpecial(int phase, LivingEntity target) {
        if (echo()) return null;
        return switch (phase) {
            case 2 -> random.nextInt(2) == 0 ? "halo_ring" : "judgement";
            case 3 -> random.nextInt(3) == 0 ? "halo_ring" : "chasing_judgement";
            default -> null;
        };
    }

    @Override
    protected int specialLength(String id) {
        return id.equals("chasing_judgement") ? 120 : 20;
    }

    @Override
    protected void performSpecial(ServerLevel server, String id, int tick, LivingEntity target) {
        switch (id) {
            case "halo_ring" -> { // its halo drops and rolls out along the floor: jump it
                setDeltaMovement(getDeltaMovement().multiply(0.3, 0.3, 0.3));
                if (tick == 10) {
                    BlockPos.MutableBlockPos at = blockPosition().mutable();
                    for (int i = 0; i < 12 && level().getBlockState(at).isAir(); i++) at.move(0, -1, 0);
                    groundRing(new Vec3(getX(), at.getY() + 1.05, getZ()), 0.55, 20, hitFor(1.2), VIOLET);
                    playSound(SoundEvents.BEACON_DEACTIVATE, 1.5F, 1.4F);
                }
            }
            case "judgement" -> {
                if (tick == 0) mark(server, target.position());
            }
            case "chasing_judgement" -> { // marks that chase its prey
                if (tick % 30 == 0) { // led ahead of where it runs (a player's movement is only known from its client)
                    mark(server, target.position().add(target.getKnownMovement().multiply(1, 0, 1).scale(10)));
                }
            }
            default -> {}
        }
    }

    @Override
    public void die(DamageSource source) {
        dismissMirrors(); // its images go with it, not a second later
        super.die(source);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        dismissMirrors();
        super.remove(reason);
    }

    private void dismissMirrors() {
        if (level() instanceof ServerLevel server) for (java.util.UUID id : mirrors) if (server.getEntity(id) instanceof Entity e) e.discard();
        mirrors.clear();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BEACON_DEACTIVATE;
    }

    @Override
    public float getVoicePitch() {
        return gradePitch(0.6F + random.nextFloat() * 0.1F);
    }
}
