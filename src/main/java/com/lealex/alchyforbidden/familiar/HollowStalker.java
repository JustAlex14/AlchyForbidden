package com.lealex.alchyforbidden.familiar;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Hollow Stalker (ring II, an assassin): too tall, too thin, its chest an empty cage, a blank mask split by one rift
 * slit, long claws. It strikes from behind and from nowhere.
 * Traits: Backstab (5, half as hard again from behind), Lurker (10, stronger and faster in the dark), Bleed (15, its claws
 * leave wounds that wither), Death Mark (20, prey under 30% health takes double from it).
 * Moves: Shadow Step (5, it steps through a rift behind its prey and strikes), Fade (10, gone from sight; its next blow
 * strikes twice as hard), Rending Flurry (15, four slashes in a breath), Hollow Grasp (20, it lifts its prey up and slams
 * it down).
 */
public class HollowStalker extends RiftBeast {
    private boolean faded;
    /** When Fade wears off if no blow came (it stayed invisible for good once its prey died or the fight ended). */
    private long fadeEnd;
    private int grasped = -1;

    public HollowStalker(EntityType<? extends HollowStalker> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 34.0)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 28.0)
                .add(Attributes.ARMOR, 3.0);
    }

    @Override
    public RiftCreature creature() {
        return RiftCreature.HOLLOW_STALKER;
    }

    private boolean dark() {
        return level().getMaxLocalRawBrightness(blockPosition()) <= 7;
    }

    /** How much harder this blow lands on that prey (Backstab, Lurker, Death Mark, Fade). */
    private float bonus(LivingEntity target) {
        float f = 1F;
        if (knows(5)) { // from behind: the prey faces away from it
            Vec3 facing = target.getLookAngle().multiply(1, 0, 1).normalize(), toMe = position().subtract(target.position()).multiply(1, 0, 1).normalize();
            if (facing.dot(toMe) < -0.3) f += 0.5F;
        }
        if (knows(10) && dark()) f += 0.25F;
        if (knows(20) && target.getHealth() < target.getMaxHealth() * 0.3F) f *= 2F;
        if (faded) f *= 2F;
        return f;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        if (!(target instanceof LivingEntity living)) return super.doHurtTarget(level, target);
        float extra = bonus(living) - 1F;
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            if (extra > 0 && living.isAlive()) {
                living.invulnerableTime = 0;
                living.hurtServer(level, damageSources().mobAttack(this), hitFor(extra));
                level.sendParticles(ParticleTypes.CRIT, living.getX(), living.getY() + living.getBbHeight() * 0.6, living.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
            }
            if (knows(15)) living.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0), this); // Bleed
            unfade(level);
        }
        return hit;
    }

    private void unfade(ServerLevel level) {
        if (!faded) return;
        faded = false;
        setInvisible(false);
        Familiars.riftPuff(level, middle(), creature().color(), true);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel server && faded && (server.getGameTime() >= fadeEnd || getTarget() == null || !getTarget().isAlive())) unfade(server);
        if (level() instanceof ServerLevel server && knows(10) && tickCount % 40 == 0 && dark()) {
            addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, true, false)); // Lurker
        }
        if (level().isClientSide() && random.nextInt(familiar() ? 8 : 4) == 0) {
            level().addParticle(ParticleTypes.SMOKE, getRandomX(0.4), getY() + 1.4, getRandomZ(0.4), 0, 0.01, 0);
        }
    }

    /** Shadow Step's and the moves' strike: a blow with every bonus it has. */
    private void strike(ServerLevel level, LivingEntity target, float factor) {
        target.invulnerableTime = 0;
        target.hurtServer(level, damageSources().mobAttack(this), hitFor(factor * bonus(target)));
        onHitAffixes(level, target);
        if (knows(15)) target.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 0), this);
        level.broadcastEntityEvent(this, SWING);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 1, 0, 0, 0, 0);
        unfade(level);
    }

    @Override
    protected void performMove(RiftCreature.Move move, int tick, LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) return;
        switch (move.id()) {
            case "shadow_step" -> {
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick == move.strike()) {
                    Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(1.4));
                    Vec3 from = position();
                    if (randomTeleport(behind.x, target.getY(), behind.z, false)) {
                        Familiars.riftPuff(server, from.add(0, 1, 0), creature().color(), false);
                        Familiars.riftPuff(server, middle(), creature().color(), true);
                        getLookControl().setLookAt(target);
                        strike(server, target, 1.3F);
                    }
                }
            }
            case "fade" -> {
                if (tick == move.strike()) {
                    faded = true;
                    fadeEnd = server.getGameTime() + 200; // 10 s at most
                    setInvisible(true);
                    server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 20, 0.3, 0.6, 0.3, 0.02);
                    playSound(SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 0.6F);
                }
            }
            case "rending_flurry" -> {
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick >= move.strike() && (tick - move.strike()) % 5 == 0 && tick < move.strike() + 20 && distanceToSqr(target) < 9) {
                    strike(server, target, 0.6F);
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.4F);
                }
            }
            case "hollow_grasp" -> {
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick == move.strike() && distanceToSqr(target) < 12) {
                    grasped = target.getId();
                    target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 26, 2), this);
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 9), this);
                    playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 0.8F, 0.6F);
                }
                if (tick == move.strike() + 28 && grasped == target.getId() && target.isAlive()) { // the slam
                    target.removeEffect(MobEffects.LEVITATION);
                    target.setDeltaMovement(target.getDeltaMovement().x, -1.6, target.getDeltaMovement().z);
                    target.hurtMarked = true;
                    strike(server, target, 2.0F);
                    server.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY(), target.getZ(), 1, 0, 0, 0, 0);
                    grasped = -1;
                }
                if (tick > move.strike() && tick < move.strike() + 28 && grasped == target.getId()) { // the grip, a thread of shadow
                    Vec3 hand = position().add(getLookAngle().scale(0.8)).add(0, 1.8, 0), on = target.position().add(0, target.getBbHeight() * 0.5, 0);
                    for (int k = 0; k <= 6; k++) {
                        Vec3 p = hand.lerp(on, k / 6.0);
                        server.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 1, 0, 0, 0, 0);
                    }
                }
            }
            default -> {}
        }
    }

    @Override
    protected void endMove(RiftCreature.Move move) {
        if (move.id().equals("hollow_grasp")) grasped = -1;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ENDERMAN_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDERMAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDERMAN_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return gradePitch(0.5F + random.nextFloat() * 0.1F);
    }
}
