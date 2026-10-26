package com.lealex.alchyforbidden.familiar;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The Choir (ring III, a great one): a floating crown of five sculk heads round a heart of light, singing. It flies and
 * fights from afar with its voices.
 * Traits: Harmony (5, it and its side round it mend), Discord (10, what its voices strike is weakened and reels),
 * Choir of Many (15, each head sings a small bolt of its own now and then), Requiem (20, near death, one great shriek
 * that heals it).
 * Moves: Sonic Boom (5, a beam of sound through its prey), Wail of Sorrow (10, a cone of grief that darkens and
 * slows), Canticle (15, each head in turn throws a bolt), Crescendo (20, a long rising note, then a shockwave).
 */
public class TheChoir extends RiftBeast implements RangedAttackMob {
    private static final int VOICE = 0x3FD8C8;
    private boolean requiemSung;

    public TheChoir(EntityType<? extends TheChoir> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 120.0)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FLYING_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ARMOR, 6.0);
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

    // ---- Its entrance: it comes down folded small, its heads closed round the light; they open and sing ----

    @Override
    protected Vec3 introPath(Vec3 home, float p) {
        return home.add(0, 3 + 7 * (1 - ease(p / 0.55F)), 0);
    }

    @Override
    protected void introEffects(ServerLevel level, int t, Vec3 home) {
        if (t < 60 && t % 10 == 0) playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4F, 0.5F + t / 80F);
        if (t == 62) {
            fxNova(position(), 6F, 18, VOICE);
            level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.WARDEN_SONIC_BOOM, net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 1.4F);
        }
        if (t > 62 && t % 6 == 0) level.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE, getX(), getY() + 1.5, getZ(), 3, 1.0, 0.5, 1.0, 1);
    }

    private static float ease(float x) {
        x = Math.max(0F, Math.min(1F, x));
        return x * x * (3 - 2 * x);
    }

    @Override
    public RiftCreature creature() {
        return RiftCreature.THE_CHOIR;
    }

    @Override
    protected void addAttackGoals() {
        goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 40, 50, 16.0F));
    }

    private Vec3 heart() {
        return position().add(0, getBbHeight() * 0.5, 0);
    }

    /** Its voice: a beam of sound to its prey (the warden's own particles), sonic damage through armour. */
    private void sing(ServerLevel level, LivingEntity target, float factor) {
        Vec3 from = heart(), to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        fxBeam(from, to.subtract(from), (float) from.distanceTo(to), 0.7F, 12, VOICE, com.lealex.alchyforbidden.network.AttackFxPayload.SONIC, 0, 0);
        target.invulnerableTime = 0;
        target.hurtServer(level, damageSources().sonicBoom(this), hitFor(factor));
        onHitAffixes(level, target);
        discord(target);
        Vec3 push = to.subtract(from).normalize();
        target.setDeltaMovement(target.getDeltaMovement().add(push.x * 0.6, 0.2, push.z * 0.6));
        target.hurtMarked = true;
        playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.0F, 1.5F);
    }

    /** Discord (10): what its voices strike is weakened and reels. */
    private void discord(LivingEntity target) {
        if (!knows(10)) return;
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), this);
        target.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0), this);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        if (level() instanceof ServerLevel server) {
            sing(server, target, 1.0F);
            server.broadcastEntityEvent(this, SWING);
        }
    }

    @Override
    protected void onBoltHit(ServerLevel level, LivingEntity target, float damage) {
        discord(target);
    }

    /** The five heads' places round it (for bolts from each). */
    private Vec3 headAt(int i) {
        double a = i * Math.PI * 2 / 5 + tickCount * 0.02;
        return heart().add(Math.cos(a) * 0.9, 0.2, Math.sin(a) * 0.9);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && knows(20) && !requiemSung && getHealth() < getMaxHealth() * 0.25F) { // Requiem
            requiemSung = true;
            for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(10), this::foe)) {
                near.invulnerableTime = 0;
                near.hurtServer(level, damageSources().sonicBoom(this), hitFor(3.0));
            }
            heal(getMaxHealth() * 0.2F);
            fxNova(position(), 10F, 18, VOICE);
            playSound(SoundEvents.WARDEN_ROAR, 1.5F, 1.4F);
        }
        return hurt;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel server) {
            // keep floating a few blocks over the ground, when nothing steers it (following, it aims over its owner's head)
            BlockPos below = blockPosition().below(3);
            if (getMoveControl().hasWanted()) {
                // steered: the move control decides the height
            } else if (!level().getBlockState(below).isAir() || !level().getBlockState(below.above()).isAir()) {
                setDeltaMovement(getDeltaMovement().add(0, 0.04, 0));
            } else if (level().getBlockState(blockPosition().below(5)).isAir() && getTarget() == null) {
                setDeltaMovement(getDeltaMovement().add(0, -0.02, 0));
            }
            if (knows(5) && tickCount % 40 == 0) { // Harmony
                heal(2.0F);
                for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8),
                        e -> e != this && (e == ownerPlayer() || (e instanceof RiftBeast beast && isAlliedTo(beast))))) {
                    near.heal(2.0F);
                    server.sendParticles(ParticleTypes.NOTE, near.getX(), near.getY() + near.getBbHeight() + 0.3, near.getZ(), 1, 0, 0, 0, 0);
                }
            }
            if (knows(15) && tickCount % 100 == 0 && getTarget() != null && getTarget().isAlive()) { // Choir of Many
                for (int i = 0; i < 5; i++) fireBolt(headAt(i), getTarget(), hitFor(0.35), VOICE, 0, 0.9);
            }
        }
        if (level().isClientSide() && random.nextInt(4) == 0) {
            level().addParticle(ParticleTypes.NOTE, getRandomX(0.8), getY() + getBbHeight() * 0.7, getRandomZ(0.8), random.nextDouble(), 0, 0);
        }
    }

    @Override
    protected void performMove(RiftCreature.Move move, int tick, LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) return;
        setDeltaMovement(getDeltaMovement().multiply(0.5, 0.5, 0.5));
        switch (move.id()) {
            case "sonic_boom" -> {
                if (tick == 0) playSound(SoundEvents.WARDEN_SONIC_CHARGE, 1.0F, 1.4F);
                if (tick == move.strike()) sing(server, target, 1.5F);
            }
            case "wail_of_sorrow" -> {
                if (tick == move.strike()) {
                    // from its heart to their middles: from its feet (it hovers 3 up) to their feet the cone and its beam
                    // pointed into the ground just past the prey
                    Vec3 look = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(heart()).normalize();
                    for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(10), this::foe)) {
                        Vec3 to = near.position().add(0, near.getBbHeight() * 0.5, 0).subtract(heart()).normalize();
                        if (to.dot(look) < 0.5) continue;
                        near.hurtServer(server, damageSources().sonicBoom(this), hitFor(1.0));
                        near.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0), this);
                        near.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2), this);
                        discord(near);
                    }
                    fxBeam(heart(), look, 10F, 3F, 16, VOICE, com.lealex.alchyforbidden.network.AttackFxPayload.SONIC, 0, 0);
                    playSound(SoundEvents.GHAST_SCREAM, 1.2F, 0.5F);
                }
            }
            case "canticle" -> {
                if (tick >= move.strike() && (tick - move.strike()) % 4 == 0 && tick < move.strike() + 20) {
                    int head = (tick - move.strike()) / 4;
                    fireBolt(headAt(head), target, hitFor(0.6), VOICE, 1, 1.1);
                    playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0F, 0.6F + head * 0.15F);
                }
            }
            case "crescendo" -> {
                if (tick < move.strike() && tick % 5 == 0) {
                    playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.2F, 0.5F + tick / (float) move.strike());
                    server.sendParticles(ParticleTypes.NOTE, getX(), getY() + getBbHeight(), getZ(), 3, 0.6, 0.3, 0.6, 1);
                }
                if (tick == move.strike()) {
                    for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8), this::foe)) {
                        near.invulnerableTime = 0;
                        near.hurtServer(server, damageSources().sonicBoom(this), hitFor(3.0));
                        discord(near);
                        Vec3 out = near.position().subtract(position()).multiply(1, 0, 1).normalize();
                        near.setDeltaMovement(out.x * 1.4, 0.5, out.z * 1.4);
                        near.hurtMarked = true;
                    }
                    fxNova(position(), 8F, 16, VOICE);
                    fxNova(position().add(0, 1.5, 0), 6F, 22, 0xFFFFFF);
                    playSound(SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 0.7F);
                }
            }
            default -> {}
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SCULK_SHRIEKER_SHRIEK;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SCULK_BLOCK_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WARDEN_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return gradePitch(1.0F + random.nextFloat() * 0.2F);
    }

    @Override
    public boolean causeFallDamage(double fallDistance, float multiplier, DamageSource source) {
        return false;
    }
}
