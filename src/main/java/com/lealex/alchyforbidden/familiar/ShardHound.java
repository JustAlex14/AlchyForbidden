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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The Shard Hound (ring I): a lean hound of dark hide grown over with amethyst, crystals down its spine and on its
 * shoulders. Fast, it runs its prey down.
 * Traits: Keen Fangs (5, one bite in five strikes twice as hard), Pack Bond (10, its owner and their familiars near it
 * run faster), Shatter (15, struck, it sheds shards into what stands round it), Prismatic Hide (20, half damage from
 * arrows and bolts).
 * Moves: Crystal Charge (5, a dash straight through its prey and whatever stands in the way), Shard Volley (10, it shakes
 * its spines loose in a ring of shards), Howl (15, foes round it weakened and slowed, its side strengthened), Crystallize
 * (20, its prey locked in amethyst, then the crystal bursts).
 */
public class ShardHound extends RiftBeast {
    private static final int SHARD = 0xC48CFF;
    private long shatterReady;
    private final java.util.Set<java.util.UUID> charged = new java.util.HashSet<>();
    private Vec3 chargeDir = Vec3.ZERO;
    private double chargeSpeed;

    public ShardHound(EntityType<? extends ShardHound> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.ATTACK_DAMAGE, 4.5)
                .add(Attributes.MOVEMENT_SPEED, 0.36)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ARMOR, 2.0);
    }

    @Override
    protected int boltStyle(int color, int fire, boolean boomerang) {
        return com.lealex.alchyforbidden.network.AttackFxPayload.SHARD;
    }

    @Override
    public RiftCreature creature() {
        return RiftCreature.SHARD_HOUND;
    }

    /** Keen Fangs (5): one bite in five strikes twice as hard. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && knows(5) && target instanceof LivingEntity living && random.nextInt(5) == 0 && living.isAlive()) {
            living.invulnerableTime = 0;
            living.hurtServer(level, damageSources().mobAttack(this), hitFor(1.0));
            level.sendParticles(ParticleTypes.CRIT, living.getX(), living.getY() + living.getBbHeight() * 0.6, living.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
            playSound(SoundEvents.AMETHYST_BLOCK_BREAK, 0.8F, 1.4F);
        }
        return hit;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (knows(20) && source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE)) amount *= 0.5F; // Prismatic Hide
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && knows(15) && level.getGameTime() >= shatterReady) { // Shatter
            shatterReady = level.getGameTime() + 60;
            for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(2.5), this::foe)) {
                near.hurtServer(level, damageSources().mobAttack(this), hitFor(0.5));
            }
            shardBurst(level, position().add(0, 0.6, 0), 14);
        }
        return hurt;
    }

    private static void shardBurst(ServerLevel level, Vec3 at, int count) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_CLUSTER.defaultBlockState()), at.x, at.y, at.z, count, 0.4, 0.3, 0.4, 0.2);
        level.playSound(null, net.minecraft.core.BlockPos.containing(at), SoundEvents.AMETHYST_CLUSTER_BREAK, net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 1.2F);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel server && knows(10) && familiar() && tickCount % 40 == 0) { // Pack Bond
            Player owner = ownerPlayer();
            for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(10),
                    e -> e == owner || (e instanceof RiftBeast beast && beast.familiar() && isAlliedTo(beast)))) {
                near.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, true, false), this);
            }
        }
        if (level().isClientSide() && random.nextInt(familiar() ? 10 : 5) == 0) {
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, getRandomX(0.5), getY() + 0.8, getRandomZ(0.5), 0, 0.02, 0);
        }
    }

    @Override
    protected void performMove(RiftCreature.Move move, int tick, LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) return;
        switch (move.id()) {
            case "crystal_charge" -> {
                if (tick == 0) charged.clear();
                if (tick < move.strike()) {
                    setDeltaMovement(0, getDeltaMovement().y, 0);
                    if (tick % 3 == 0) playSound(SoundEvents.WOLF_GROWL_BABY.value(), 0.8F, 0.55F);
                } else if (tick == move.strike()) {
                    Vec3 to = target.position().subtract(position()).multiply(1, 0, 1);
                    chargeDir = to.normalize();
                    chargeSpeed = Math.min(1.4, (to.length() + 3) / 10); // through its prey and about 3 blocks past it in 10 ticks
                    setDeltaMovement(chargeDir.x * chargeSpeed, 0.1, chargeDir.z * chargeSpeed);
                    hurtMarked = true;
                    playSound(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.6F);
                } else if (tick < move.strike() + 10) {
                    // kept up every tick: a velocity set once was eaten by ground friction in a few blocks
                    setDeltaMovement(chargeDir.x * chargeSpeed, getDeltaMovement().y, chargeDir.z * chargeSpeed);
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()), getX(), getY() + 0.4, getZ(), 3, 0.2, 0.2, 0.2, 0.05);
                    for (LivingEntity hit : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.6), e -> foe(e) && !charged.contains(e.getUUID()))) {
                        charged.add(hit.getUUID());
                        hit.hurtServer(server, damageSources().mobAttack(this), hitFor(1.5));
                        onHitAffixes(server, hit);
                        Vec3 side = hit.position().subtract(position()).normalize();
                        hit.setDeltaMovement(hit.getDeltaMovement().add(side.x * 0.6, 0.3, side.z * 0.6));
                        hit.hurtMarked = true;
                    }
                } else {
                    setDeltaMovement(getDeltaMovement().multiply(0.5, 1, 0.5));
                }
            }
            case "shard_volley" -> {
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick == 0) playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.6F);
                if (tick == move.strike()) {
                    Vec3 from = position().add(0, 0.8, 0);
                    for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI * 2 / 8;
                        fireBolt(from, new Vec3(Math.cos(a), 0.05, Math.sin(a)), hitFor(0.8), SHARD, 0, 0.9);
                    }
                    fireBolt(from, target, hitFor(0.8), SHARD, 0, 1.0);
                    shardBurst(server, from, 20);
                }
            }
            case "howl" -> {
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick == move.strike()) {
                    playSound(SoundEvents.WARDEN_ROAR, 1.2F, 1.7F);
                    for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8), e -> e != this && e.isAlive())) {
                        if (foe(near)) {
                            near.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1), this);
                            near.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1), this);
                        } else if (near == ownerPlayer() || (near instanceof RiftBeast beast && isAlliedTo(beast))) {
                            near.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 160, 0), this);
                        }
                    }
                    for (int i = 0; i < 3; i++) server.sendParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 1.0 + i * 0.3, getZ(), 1, 0, 0, 0, 0);
                }
            }
            case "crystallize" -> {
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick == move.strike()) {
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 9), this);
                    target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 60, 2), this);
                    playSound(SoundEvents.AMETHYST_BLOCK_PLACE, 1.2F, 0.6F);
                }
                if (tick > move.strike() && tick < move.strike() + 30 && tick % 3 == 0) { // the crystal growing round it
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()), target.getX(), target.getY() + target.getBbHeight() * 0.5,
                            target.getZ(), 6, target.getBbWidth() * 0.5, target.getBbHeight() * 0.4, target.getBbWidth() * 0.5, 0);
                    server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(SHARD, 1.4F), target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                            4, target.getBbWidth() * 0.6, target.getBbHeight() * 0.5, target.getBbWidth() * 0.6, 0);
                }
                if (tick == move.strike() + 30 && target.isAlive()) { // and bursting: the hold ends with the crystal
                    unhold(target);
                    target.invulnerableTime = 0;
                    target.hurtServer(server, damageSources().mobAttack(this), hitFor(2.0));
                    onHitAffixes(server, target);
                    shardBurst(server, target.position().add(0, target.getBbHeight() * 0.5, 0), 40);
                }
            }
            default -> {}
        }
    }

    /** Takes off the crystal's hold (Slowness X, Mining Fatigue III), not someone else's milder effects. */
    private static void unhold(LivingEntity target) {
        MobEffectInstance slow = target.getEffect(MobEffects.SLOWNESS), tired = target.getEffect(MobEffects.MINING_FATIGUE);
        if (slow != null && slow.getAmplifier() == 9) target.removeEffect(MobEffects.SLOWNESS);
        if (tired != null && tired.getAmplifier() == 2) target.removeEffect(MobEffects.MINING_FATIGUE);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WOLF_GROWL_BABY.value();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.AMETHYST_BLOCK_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.AMETHYST_CLUSTER_BREAK;
    }

    @Override
    public float getVoicePitch() {
        return gradePitch(0.55F + random.nextFloat() * 0.15F);
    }
}
