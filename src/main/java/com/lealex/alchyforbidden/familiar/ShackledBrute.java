package com.lealex.alchyforbidden.familiar;

import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Shackled Brute (ring II, a wall): a hunched giant bound in obsidian chains, its manacles still on, arms long
 * enough to drag on the ground. Slow, hard to hurt, it hits like a falling stone.
 * Traits: Crushing Blows (5, its blows slow and throw far), Taunt (10, monsters after its owner turn on it), Iron Will
 * (15, badly hurt, it takes far less), Chained Blows (20, each blow drags its prey in and holds it a moment).
 * Moves: Ground Slam (5, a shockwave round it), Chain Hook (10, a chain thrown that drags its prey to it), Earthbreaker
 * (15, it leaps onto its prey and breaks the ground), Wrecking Flail (20, its chains whirled round it, pulling foes in).
 */
public class ShackledBrute extends RiftBeast {
    private boolean landed;

    public ShackledBrute(EntityType<? extends ShackledBrute> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0);
    }

    @Override
    public RiftCreature creature() {
        return RiftCreature.SHACKLED_BRUTE;
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && target instanceof LivingEntity living) {
            if (knows(5)) { // Crushing Blows
                living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), this);
                Vec3 away = living.position().subtract(position()).multiply(1, 0, 1).normalize();
                living.setDeltaMovement(living.getDeltaMovement().add(away.x * 0.6, 0.25, away.z * 0.6));
                living.hurtMarked = true;
            }
            if (knows(20)) { // Chained Blows: dragged back in and held
                Vec3 in = position().subtract(living.position()).multiply(1, 0, 1).normalize();
                living.setDeltaMovement(in.x * 0.5, 0.1, in.z * 0.5);
                living.hurtMarked = true;
                living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 6), this);
                level.playSound(null, blockPosition(), SoundEvents.CHAIN_HIT, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 0.6F);
            }
            level.playSound(null, living.blockPosition(), SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.NEUTRAL, 0.4F, 0.6F);
        }
        return hit;
    }

    /** Iron Will (15): under 30% health it takes 40% less. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (knows(15) && getHealth() < getMaxHealth() * 0.3F) amount *= 0.6F;
        return super.hurtServer(level, source, amount);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel server && knows(10) && familiar() && tickCount % 60 == 0) { // Taunt
            Player owner = ownerPlayer();
            if (owner != null) {
                for (Mob mob : server.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(8), m -> m.getTarget() == owner && m != this && canAttack(m))) {
                    mob.setTarget(this);
                    server.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight() + 0.3, mob.getZ(), 1, 0, 0, 0, 0);
                }
            }
        }
        if (level().isClientSide() && getDeltaMovement().horizontalDistanceSqr() > 0.002 && random.nextInt(6) == 0) { // the chains dragging
            level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, level().getBlockState(blockPosition().below())), getX(), getY() + 0.05, getZ(), 0, 0.05, 0);
        }
    }

    private void slam(ServerLevel level, Vec3 at, double radius, float damage, double up) {
        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius, 2, radius), this::foe)) {
            near.hurtServer(level, damageSources().mobAttack(this), damage);
            onHitAffixes(level, near);
            Vec3 away = near.position().subtract(at).multiply(1, 0, 1).normalize();
            near.setDeltaMovement(near.getDeltaMovement().add(away.x * 0.5, up, away.z * 0.5));
            near.hurtMarked = true;
        }
        for (int i = 0; i < 20; i++) {
            double a = i * Math.PI * 2 / 20;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(net.minecraft.core.BlockPos.containing(at).below())),
                    at.x + Math.cos(a) * radius * 0.7, at.y + 0.1, at.z + Math.sin(a) * radius * 0.7, 4, 0.2, 0.1, 0.2, 0.1);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.2, at.z, 1, 0, 0, 0, 0);
        level.playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, 0.6F);
    }

    private void chainLine(ServerLevel level, Vec3 from, Vec3 to) {
        int steps = (int) Math.max(4, from.distanceTo(to) * 3);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(0x2A2236, 1.2F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    @Override
    protected void performMove(RiftCreature.Move move, int tick, LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) return;
        switch (move.id()) {
            case "ground_slam" -> {
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick == 0) playSound(SoundEvents.RAVAGER_ROAR, 0.6F, 1.4F);
                if (tick == move.strike()) slam(server, position(), 4, hitFor(1.3), 0.45);
            }
            case "chain_hook" -> {
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick == move.strike()) {
                    chainLine(server, position().add(0, 1.4, 0), target.position().add(0, target.getBbHeight() * 0.5, 0));
                    target.hurtServer(server, damageSources().mobAttack(this), hitFor(0.6));
                    // pulled to its feet: about 6.5 blocks of flight per block a tick (0.25 x the distance flung it far past)
                    Vec3 in = position().subtract(target.position());
                    Vec3 flat = in.multiply(1, 0, 1);
                    double d = flat.length(), pull = Math.min(1.8, Math.max(0, d - 1.5) / 6.5);
                    Vec3 v = d < 0.01 ? Vec3.ZERO : flat.scale(pull / d);
                    target.setDeltaMovement(v.x, 0.4 + Math.max(0, in.y) * 0.05, v.z);
                    target.hurtMarked = true;
                    playSound(SoundEvents.CHAIN_BREAK, 1.0F, 0.6F);
                }
            }
            case "earthbreaker" -> {
                if (tick == 0) landed = false;
                // the leap at tick 10 (strike - 14): a 0.55 jump is about 15 ticks in the air, so it comes down by the strike
                // (a 0.9 jump at strike - 10 was still in the air when the move ended: it never slammed)
                int leap = move.strike() - 14;
                if (tick < leap) setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick == leap) {
                    setDeltaMovement(getDeltaMovement().x, 0.55, getDeltaMovement().z);
                    hurtMarked = true;
                    playSound(SoundEvents.RAVAGER_ATTACK, 1.0F, 0.7F);
                }
                if (!landed && tick >= leap) { // in the air: steered onto its prey every tick, to be over it by the strike
                    Vec3 flat = target.position().subtract(position()).multiply(1, 0, 1);
                    double d = flat.length(), speed = d < 0.8 ? 0 : Math.min(1.2, d / Math.max(2, move.strike() - tick));
                    Vec3 v = d < 0.01 ? Vec3.ZERO : flat.scale(speed / d);
                    setDeltaMovement(v.x, getDeltaMovement().y, v.z);
                }
                if (!landed && tick > leap + 3 && onGround()) { // the landing
                    landed = true;
                    slam(server, position(), 5, hitFor(1.8), 0.6);
                    for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5), this::foe)) {
                        near.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2), this);
                    }
                }
            }
            case "wrecking_flail" -> {
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick >= move.strike() && (tick - move.strike()) % 10 == 0) {
                    for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(4, 1, 4), this::foe)) {
                        near.invulnerableTime = 0;
                        near.hurtServer(server, damageSources().mobAttack(this), hitFor(0.7));
                        onHitAffixes(server, near);
                        Vec3 in = position().subtract(near.position()).multiply(1, 0, 1).normalize();
                        near.setDeltaMovement(in.x * 0.3, 0.15, in.z * 0.3);
                        near.hurtMarked = true;
                    }
                    playSound(SoundEvents.CHAIN_HIT, 1.0F, 0.5F + (tick % 20) * 0.02F);
                }
                if (tick >= move.strike()) { // the chains' circle
                    double a = tick * 0.6;
                    for (int k = 0; k < 2; k++) {
                        double b = a + k * Math.PI;
                        chainLine(server, position().add(0, 1.2, 0), position().add(Math.cos(b) * 3.5, 1.0, Math.sin(b) * 3.5));
                    }
                }
            }
            default -> {}
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(SoundEvents.CHAIN_STEP, 0.6F, 0.6F);
        playSound(SoundEvents.IRON_GOLEM_STEP, 0.6F, 0.8F);
    }

    @Override
    public float getVoicePitch() {
        return gradePitch(0.7F + random.nextFloat() * 0.1F);
    }
}
