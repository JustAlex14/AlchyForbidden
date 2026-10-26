package com.lealex.alchyforbidden.familiar;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Veil Wisp (ring I, ranged): a hooded shroud of tatters drifting over the ground, a soul held between its hands. It
 * keeps its distance and throws rift bolts from the soul.
 * Traits: Soul Siphon (5, its bolts heal it), Veil (10, one blow in five passes through it), Piercing Bolts (15, a bolt
 * goes through its first foe to the next), Twin Flame (20, two bolts at once).
 * Moves: Rift Barrage (5, a fan of three bolts), Blink Away (10, slips away from a foe too close), Soul Snare (15, rift
 * chains hold its prey), Spectral Rain (20, bolts fall from above on its prey and round it).
 */
public class VeilWisp extends RiftBeast implements RangedAttackMob {
    private static final int BOLT = 0xB8A0FF;

    public VeilWisp(EntityType<? extends VeilWisp> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ARMOR, 1.0);
    }

    @Override
    public RiftCreature creature() {
        return RiftCreature.VEIL_WISP;
    }

    @Override
    protected void addAttackGoals() {
        goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 30, 40, 14.0F));
    }

    private Vec3 soul() {
        return position().add(getLookAngle().scale(0.5)).add(0, getBbHeight() * 0.55, 0);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        if (!(level() instanceof ServerLevel server)) return;
        int pierce = knows(15) ? 1 : 0;
        fireBolt(soul(), target, hitFor(1.0), BOLT, pierce, 1.1);
        if (knows(20)) { // Twin Flame: a second one from beside the first, both meeting on the prey (an angled one missed past a few blocks)
            Vec3 side = getLookAngle().multiply(1, 0, 1).normalize().yRot((float) (Math.PI / 2)).scale(0.45);
            fireBolt(soul().add(side).add(0, 0.2, 0), target, hitFor(1.0), BOLT, pierce, 1.1);
        }
        server.broadcastEntityEvent(this, SWING);
        playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 0.6F, 1.6F);
    }

    /** Soul Siphon (5): its bolts heal it a third of what they do. */
    @Override
    protected void onBoltHit(ServerLevel level, LivingEntity target, float damage) {
        if (knows(5)) heal(damage * 0.33F);
    }

    /** Veil (10): one blow in five goes through it as through mist. */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (knows(10) && random.nextInt(5) == 0 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + getBbHeight() * 0.5, getZ(), 14, 0.3, 0.5, 0.3, 0.05);
            playSound(SoundEvents.VEX_CHARGE, 0.6F, 1.6F);
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    protected void performMove(RiftCreature.Move move, int tick, LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) return;
        setDeltaMovement(0, getDeltaMovement().y, 0);
        switch (move.id()) {
            case "rift_barrage" -> {
                if (tick == 0) playSound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.7F, 1.5F);
                if (tick == move.strike()) {
                    Vec3 aim = target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(soul());
                    for (float a : new float[] {-0.22F, 0F, 0.22F}) fireBolt(soul(), aim.yRot(a), hitFor(0.9), BOLT, knows(15) ? 1 : 0, 1.2);
                    playSound(SoundEvents.ILLUSIONER_CAST_SPELL, 0.9F, 1.3F);
                }
            }
            case "blink_away" -> {
                if (tick == move.strike()) {
                    Vec3 from = position();
                    Vec3 away = position().subtract(target.position()).multiply(1, 0, 1).normalize();
                    for (int i = 0; i < 8; i++) {
                        Vec3 to = position().add(away.scale(6 + random.nextDouble() * 2)).add((random.nextDouble() - 0.5) * 3, 0, (random.nextDouble() - 0.5) * 3);
                        if (randomTeleport(to.x, to.y, to.z, false)) {
                            Familiars.riftPuff(server, from.add(0, 0.7, 0), creature().color(), false);
                            Familiars.riftPuff(server, middle(), creature().color(), true);
                            break;
                        }
                    }
                }
            }
            case "soul_snare" -> {
                if (tick == 0) playSound(SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, 0.7F, 1.4F);
                if (tick == move.strike()) {
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 50, 9), this);
                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 1), this);
                    com.lealex.alchyforbidden.network.SpellFxPayload.send(server, com.lealex.alchyforbidden.network.SpellFxPayload.CHAINS,
                            target.position(), target.getId(), 50, 1.0F);
                    playSound(SoundEvents.CHAIN_PLACE, 1.0F, 0.6F);
                }
            }
            case "spectral_rain" -> {
                if (tick == 0) playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 0.8F, 1.4F);
                if (tick >= move.strike() && tick < move.strike() + 36 && (tick - move.strike()) % 4 == 0) {
                    Vec3 ground = target.position().add((random.nextDouble() - 0.5) * 5, 0, (random.nextDouble() - 0.5) * 5);
                    Vec3 from = ground.add(0, 7, 0);
                    var roof = level().clip(new net.minecraft.world.level.ClipContext(ground.add(0, 1, 0), from, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                            net.minecraft.world.level.ClipContext.Fluid.NONE, this));
                    if (roof.getType() != net.minecraft.world.phys.HitResult.Type.MISS) from = roof.getLocation().add(0, -0.3, 0); // under a ceiling: from just below it
                    fireBolt(from, ground.subtract(from), hitFor(0.8), BOLT, 0, 1.0);
                    com.lealex.alchyforbidden.network.SpellFxPayload.send(server, com.lealex.alchyforbidden.network.SpellFxPayload.RIFT, from, -1, 10, 0.7F);
                }
            }
            default -> {}
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide() && random.nextInt(familiar() ? 4 : 2) == 0) { // the shroud fraying into mist
            level().addParticle(ParticleTypes.SOUL, getRandomX(0.4), getY() + 0.3 + random.nextDouble() * 0.4, getRandomZ(0.4), 0, 0.01, 0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VEX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VEX_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return gradePitch(0.6F + random.nextFloat() * 0.15F);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false; // it drifts
    }
}
