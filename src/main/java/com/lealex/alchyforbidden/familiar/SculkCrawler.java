package com.lealex.alchyforbidden.familiar;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The Sculk Crawler (ring I): a low thing of sculk plates on four bent legs, one seam of light down its back. It leaps
 * at its prey and its bite leaves them slowed, the sculk clinging to the wound.
 */
public class SculkCrawler extends RiftBeast {
    public SculkCrawler(EntityType<? extends SculkCrawler> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
    }

    @Override
    public RiftCreature creature() {
        return RiftCreature.SCULK_CRAWLER;
    }

    @Override
    protected void addAttackGoals() {
        goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.45F) {
            @Override
            public void start() {
                super.start();
                leapAt = level().getGameTime();
            }
        });
    }

    /** When it last leapt at its prey (the melee goal stops while it is in the air, so the bite comes as it lands). */
    private long leapAt = -100;

    private static final net.minecraft.resources.Identifier POUNCE = net.minecraft.resources.Identifier.fromNamespaceAndPath("alchyforbidden", "pounce");
    private @org.jspecify.annotations.Nullable LivingEntity echoTarget;
    private long echoAt;

    /**
     * Its bite slows (longer and stronger by grade). Traits: Clinging Bite (5) also weakens; Pounce (15) bites half as hard
     * again from a leap; Echo Strike (20) one bite in six is struck again by its echo a moment later.
     */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        var damage = getAttribute(Attributes.ATTACK_DAMAGE);
        boolean pounce = knows(15) && (!onGround() || level.getGameTime() - leapAt <= 25) && damage != null;
        if (pounce) leapAt = -100; // one bite per leap
        if (pounce) damage.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(POUNCE, 0.5,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        boolean hit = super.doHurtTarget(level, target);
        if (pounce) damage.removeModifier(POUNCE);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40 + 20 * corruption(), corruption() >= 2 ? 1 : 0), this);
            if (knows(5)) living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), this);
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, living.getX(), living.getY() + living.getBbHeight() * 0.5, living.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
            if (pounce) level.sendParticles(ParticleTypes.CRIT, living.getX(), living.getY() + living.getBbHeight() * 0.6, living.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
            if (knows(20) && echoTarget == null && random.nextInt(6) == 0) {
                echoTarget = living;
                echoAt = level.getGameTime() + 12;
            }
        }
        return hit;
    }

    private boolean lungeHit, lungeFlying;

    /**
     * Its moves. LUNGE (5): crouches, then throws itself at its prey and bites it as it lands (x1.4). SCULK SPIT (10):
     * rears and spits a stream of sculk (x0.7, Slowness II, Darkness). BURROW (15): digs into the ground, goes under
     * unseen, bursts up beneath its prey (x1.8, thrown up). SHRIEK (20): rears, and its shriek hits every foe within 7
     * (x1.2, thrown back, Darkness).
     */
    @Override
    protected void performMove(RiftCreature.Move move, int tick, LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) return;
        switch (move.id()) {
            case "lunge" -> {
                if (tick == 0) {
                    lungeHit = false;
                    lungeFlying = false;
                }
                if (tick < move.strike()) {
                    setDeltaMovement(0, getDeltaMovement().y, 0);
                    if (tick % 3 == 0) playSound(SoundEvents.SCULK_CLICKING, 0.8F, 0.6F);
                } else if (tick == move.strike()) {
                    Vec3 to = target.position().subtract(position());
                    Vec3 flat = new Vec3(to.x, 0, to.z);
                    double d = flat.length();
                    Vec3 push = flat.normalize().scale(Math.min(0.9, 0.15 + d * 0.075));
                    setDeltaMovement(push.x, 0.42 + d * 0.02, push.z);
                    hurtMarked = true;
                    lungeFlying = true;
                    playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 0.6F, 1.6F);
                    server.sendParticles(ParticleTypes.SCULK_CHARGE_POP, getX(), getY() + 0.2, getZ(), 12, 0.4, 0.1, 0.4, 0.05);
                } else if (!lungeHit && distanceToSqr(target) < 3.2) {
                    lungeHit = true;
                    lungeFlying = false;
                    setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2)); // stops on its prey, does not fly past
                    target.invulnerableTime = 0;
                    target.hurtServer(server, damageSources().mobAttack(this), hitFor(1.4));
                    onHitAffixes(server, target);
                    server.broadcastEntityEvent(this, SWING);
                    server.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
                } else if (lungeFlying) { // in the air: kept on its prey every tick, landing ends the flight
                    if (onGround() && tick > move.strike() + 2) lungeFlying = false;
                    else {
                        Vec3 flat = target.position().subtract(position()).multiply(1, 0, 1);
                        double d = flat.length();
                        if (d > 0.05) {
                            Vec3 v = flat.scale(Math.min(0.9, d * 0.5) / d);
                            setDeltaMovement(v.x, getDeltaMovement().y, v.z);
                        }
                    }
                }
            }
            case "sculk_spit" -> {
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick == move.strike()) {
                    Vec3 mouth = position().add(getLookAngle().scale(0.9)).add(0, 0.6, 0), at = target.position().add(0, target.getBbHeight() * 0.6, 0);
                    int steps = (int) Math.max(4, mouth.distanceTo(at) * 3);
                    for (int i = 0; i <= steps; i++) {
                        Vec3 p = mouth.lerp(at, i / (double) steps);
                        server.sendParticles(i % 3 == 0 ? ParticleTypes.SCULK_SOUL : ParticleTypes.SCULK_CHARGE_POP, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
                    }
                    target.hurtServer(server, damageSources().mobAttack(this), hitFor(0.7));
                    onHitAffixes(server, target);
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1), this);
                    target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), this);
                    server.sendParticles(ParticleTypes.SCULK_CHARGE_POP, at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.05);
                    playSound(SoundEvents.LLAMA_SPIT, 1.0F, 0.6F);
                    playSound(SoundEvents.SCULK_CATALYST_BLOOM, 0.8F, 1.4F);
                }
            }
            case "burrow" -> {
                setDeltaMovement(0, getDeltaMovement().y, 0);
                BlockPos under = blockPosition().below();
                if (tick < 14) { // digging in
                    if (tick % 2 == 0) server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, level().getBlockState(under)),
                            getX(), getY() + 0.1, getZ(), 8, 0.4, 0.1, 0.4, 0.1);
                    if (tick % 4 == 0) playSound(SoundEvents.SCULK_BLOCK_BREAK, 0.8F, 0.7F);
                } else if (tick == 14) {
                    setInvisible(true);
                    setInvulnerable(true);
                } else if (tick < move.strike()) { // a ripple of sculk running under the ground toward its prey
                    Vec3 p = position().lerp(target.position(), (tick - 14) / (double) (move.strike() - 14));
                    server.sendParticles(ParticleTypes.SCULK_CHARGE_POP, p.x, Math.floor(target.getY()) + 0.1, p.z, 3, 0.2, 0.0, 0.2, 0.01);
                } else if (tick == move.strike()) {
                    teleportTo(target.getX(), target.getY(), target.getZ());
                    setInvisible(false);
                    setInvulnerable(false);
                    target.hurtServer(server, damageSources().mobAttack(this), hitFor(1.8));
                    onHitAffixes(server, target);
                    target.setDeltaMovement(target.getDeltaMovement().add(0, 0.7, 0));
                    target.hurtMarked = true;
                    server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, level().getBlockState(target.blockPosition().below())),
                            target.getX(), target.getY() + 0.2, target.getZ(), 40, 0.6, 0.3, 0.6, 0.2);
                    server.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY() + 0.5, target.getZ(), 12, 0.4, 0.4, 0.4, 0.05);
                    playSound(SoundEvents.WARDEN_EMERGE, 0.8F, 1.6F);
                }
            }
            case "shriek" -> {
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick == 0) playSound(SoundEvents.WARDEN_SONIC_CHARGE, 0.8F, 1.5F);
                if (tick == move.strike()) {
                    for (LivingEntity near : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(7), this::foe)) {
                        near.hurtServer(server, damageSources().sonicBoom(this), hitFor(1.2));
                        Vec3 away = near.position().subtract(position()).multiply(1, 0, 1).normalize();
                        near.setDeltaMovement(near.getDeltaMovement().add(away.x * 0.9, 0.35, away.z * 0.9));
                        near.hurtMarked = true;
                        near.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), this);
                    }
                    for (int i = 0; i < 16; i++) {
                        double a = i * Math.PI * 2 / 16;
                        server.sendParticles(ParticleTypes.SONIC_BOOM, getX() + Math.cos(a) * 3, getY() + 0.6, getZ() + Math.sin(a) * 3, 1, 0, 0, 0, 0);
                    }
                    playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 1.5F);
                }
            }
            default -> {}
        }
    }

    @Override
    protected void endMove(RiftCreature.Move move) {
        if (move.id().equals("burrow") && isInvisible()) {
            setInvisible(false);
            setInvulnerable(false);
        }
    }

    /** Sculk Burst (10): what it kills bursts into sculk, hurting what stands round it. */
    @Override
    public void onKill(ServerLevel level, LivingEntity dead) {
        if (!knows(10)) return;
        float hurt = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6F;
        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, dead.getBoundingBox().inflate(3), e -> e != dead && e.isAlive() && canAttack(e)
                && (e instanceof net.minecraft.world.entity.monster.Enemy || e == getTarget()))) {
            near.hurtServer(level, damageSources().mobAttack(this), hurt);
        }
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, dead.getX(), dead.getY() + 0.5, dead.getZ(), 30, 1.2, 0.5, 1.2, 0.05);
        level.sendParticles(ParticleTypes.SCULK_SOUL, dead.getX(), dead.getY() + 0.5, dead.getZ(), 10, 0.8, 0.4, 0.8, 0.04);
        playSound(SoundEvents.SCULK_CATALYST_BLOOM, 1.0F, 0.7F);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel server && echoTarget != null && server.getGameTime() >= echoAt) { // the echo's strike
            LivingEntity target = echoTarget;
            echoTarget = null;
            if (target.isAlive() && target.distanceToSqr(this) < 64) {
                target.hurtServer(server, damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.7F);
                server.sendParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 1, 0, 0, 0, 0);
                server.sendParticles(ParticleTypes.SCULK_SOUL, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
                server.playSound(null, target.blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT, net.minecraft.sounds.SoundSource.NEUTRAL, 0.7F, 1.4F);
            }
        }
        if (level().isClientSide() && random.nextInt(familiar() ? 8 : 4) == 0) {
            level().addParticle(ParticleTypes.SCULK_SOUL, getRandomX(0.5), getY() + 0.5, getRandomZ(0.5), 0, 0.02, 0);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SCULK_CLICKING;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SCULK_BLOCK_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SCULK_SHRIEKER_BREAK;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.SCULK_BLOCK_STEP, 0.3F, 1.2F);
    }

    @Override
    public float getVoicePitch() {
        return gradePitch(0.7F + random.nextFloat() * 0.2F);
    }
}
