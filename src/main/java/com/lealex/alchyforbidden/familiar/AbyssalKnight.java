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
 * The Abyssal Knight (ring III, a great one): a knight in black plate gone violet at the seams, a horned helm, a greatsword
 * longer than a man. It walks into the fight and cuts through it.
 * Traits: Abyssal Plate (5, a quarter less from blows up close), Cleave (10, its swings strike everything in front of
 * it), Unyielding (15, nothing slows or pushes it), Echo Knight (20, badly hurt, its echo rises to fight beside it).
 * Moves: Rift Slash (5, a wave of rift flies from its blade), Void Charge (10, a charge that throws up what it meets),
 * Abyssal Smite (15, a column of rift falls on its prey), Rift Collapse (20, its blade planted: all round is pulled in,
 * then the rift bursts).
 */
public class AbyssalKnight extends RiftBeast {
    private static final int RIFT = 0x8A4AE8;
    private boolean echoCalled;
    private Vec3 smiteAt = Vec3.ZERO;
    private final java.util.Set<java.util.UUID> charged = new java.util.HashSet<>();
    private Vec3 chargeDir = Vec3.ZERO;
    private double chargeSpeed;

    public AbyssalKnight(EntityType<? extends AbyssalKnight> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 160.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.ATTACK_KNOCKBACK, 0.6);
    }

    @Override
    public int swingLength() {
        return 14; // a heavy, two-handed blow
    }

    // ---- Its entrance: it rises kneeling out of a rift in the floor, stands, and lifts its blade ----

    @Override
    protected Vec3 introPath(Vec3 home, float p) {
        return home.add(0, -2.3 * (1 - ease((p - 0.1F) / 0.45F)), 0); // its eyes (2.6 up) stay over the floor for the camera
    }

    @Override
    protected void introEffects(ServerLevel level, int t, Vec3 home) {
        if (t == 0) {
            com.lealex.alchyforbidden.network.SpellFxPayload.send(level, com.lealex.alchyforbidden.network.SpellFxPayload.RIFT, home.add(0, 0.3, 0), -1, 66, 3.0F);
            level.playSound(null, net.minecraft.core.BlockPos.containing(home), net.minecraft.sounds.SoundEvents.WARDEN_EMERGE, net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 0.7F);
        }
        if (t < 56) level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, home.x, home.y + 0.3, home.z, 4, 0.8, 0.2, 0.8, 0.05);
        if (t == 62) level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_NETHERITE.value(), net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.6F);
        if (t == 76) { // the blade raised
            fxNova(home, 5F, 16, RIFT);
            level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.RAVAGER_ROAR, net.minecraft.sounds.SoundSource.HOSTILE, 1.4F, 0.8F);
        }
    }

    private static float ease(float x) {
        x = Math.max(0F, Math.min(1F, x));
        return x * x * (3 - 2 * x);
    }

    @Override
    public RiftCreature creature() {
        return RiftCreature.ABYSSAL_KNIGHT;
    }

    /** Cleave (10): its swing also strikes everything in front of it within 3. */
    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit && knows(10)) {
            Vec3 look = getLookAngle().multiply(1, 0, 1).normalize();
            for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3), e -> e != target && foe(e))) {
                Vec3 to = near.position().subtract(position()).multiply(1, 0, 1).normalize();
                if (to.dot(look) > 0.3) {
                    near.hurtServer(level, damageSources().mobAttack(this), hitFor(0.7));
                    onHitAffixes(level, near);
                }
            }
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, getX() + look.x * 1.5, getY() + 1.4, getZ() + look.z * 1.5, 3, 0.6, 0.2, 0.6, 0);
        }
        if (hit) playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.6F);
        return hit;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (knows(5) && source.getDirectEntity() == source.getEntity() && source.getEntity() != null && source.getEntity().distanceToSqr(this) < 16) amount *= 0.75F;
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && knows(20) && !echoCalled && getHealth() < getMaxHealth() * 0.5F) { // Echo Knight
            echoCalled = true;
            spawnEcho(level, 300);
            playSound(SoundEvents.WITHER_SPAWN, 0.6F, 1.4F);
        }
        return hurt;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide() && knows(15) && hasEffect(MobEffects.SLOWNESS)) removeEffect(MobEffects.SLOWNESS); // Unyielding
        if (level().isClientSide() && random.nextInt(3) == 0) {
            level().addParticle(new net.minecraft.core.particles.DustParticleOptions(RIFT, 0.9F), getRandomX(0.7), getRandomY(), getRandomZ(0.7), 0, 0.02, 0);
        }
    }

    @Override
    public boolean isPushable() {
        return !knows(15) && super.isPushable();
    }

    @Override
    protected void performMove(RiftCreature.Move move, int tick, LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) return;
        switch (move.id()) {
            case "rift_slash" -> { // a wave of rift that flies from the blade, 8 blocks, through everything in its arc
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick == 0) playSound(SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.0F, 0.6F);
                if (tick == move.strike()) {
                    Vec3 look = target.position().subtract(position()).multiply(1, 0, 1).normalize();
                    fxBeam(position().add(0, 1.1, 0), look, 8.5F, 2.4F, 10, RIFT, com.lealex.alchyforbidden.network.AttackFxPayload.CORE, 0, 0); // the wave off the blade
                    for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(8, 2, 8), this::foe)) {
                        Vec3 to = near.position().subtract(position()).multiply(1, 0, 1);
                        if (to.length() <= 8.5 && to.normalize().dot(look) > 0.7) {
                            near.hurtServer(server, damageSources().indirectMagic(this, this), hitFor(1.4));
                            onHitAffixes(server, near);
                        }
                    }
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.5F);
                }
            }
            case "void_charge" -> {
                if (tick == 0) charged.clear();
                if (tick < move.strike()) {
                    setDeltaMovement(0, getDeltaMovement().y, 0);
                } else if (tick == move.strike()) {
                    Vec3 to = target.position().subtract(position()).multiply(1, 0, 1);
                    chargeDir = to.normalize();
                    chargeSpeed = Math.min(1.3, (to.length() + 1) / 12); // reaches its prey within the 12 ticks of the charge
                    setDeltaMovement(chargeDir.x * chargeSpeed, 0.1, chargeDir.z * chargeSpeed);
                    hurtMarked = true;
                    playSound(SoundEvents.RAVAGER_ROAR, 0.8F, 1.3F);
                } else if (tick < move.strike() + 12) {
                    // driven every tick until it meets its prey (set once, ground friction stopped it in a few blocks)
                    if (charged.contains(target.getUUID())) setDeltaMovement(getDeltaMovement().multiply(0.3, 1, 0.3));
                    else setDeltaMovement(chargeDir.x * chargeSpeed, getDeltaMovement().y, chargeDir.z * chargeSpeed);
                    server.sendParticles(ParticleTypes.REVERSE_PORTAL, getX(), getY() + 1, getZ(), 4, 0.3, 0.5, 0.3, 0.02);
                    for (LivingEntity hit : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.8), e -> foe(e) && !charged.contains(e.getUUID()))) {
                        charged.add(hit.getUUID());
                        hit.hurtServer(server, damageSources().mobAttack(this), hitFor(1.2));
                        onHitAffixes(server, hit);
                        hit.setDeltaMovement(hit.getDeltaMovement().add(0, 0.9, 0));
                        hit.hurtMarked = true;
                    }
                }
            }
            case "abyssal_smite" -> {
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick == 0) {
                    smiteAt = target.position();
                    com.lealex.alchyforbidden.network.SpellFxPayload.send(server, com.lealex.alchyforbidden.network.SpellFxPayload.RIFT, smiteAt.add(0, 6, 0), -1, move.strike() + 10, 1.8F);
                    fxColumn(smiteAt, 2.2F, 6F, move.strike(), move.strike() + 14, RIFT); // the mark, then the column
                    playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F, 0.6F);
                }
                if (tick == move.strike()) {
                    fxNova(smiteAt, 3.5F, 12, RIFT);
                    for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(smiteAt, smiteAt).inflate(3, 3, 3), this::foe)) {
                        near.hurtServer(server, damageSources().indirectMagic(this, this), hitFor(2.0));
                        onHitAffixes(server, near);
                    }
                    server.playSound(null, net.minecraft.core.BlockPos.containing(smiteAt), SoundEvents.LIGHTNING_BOLT_THUNDER, net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 0.6F);
                }
            }
            case "rift_collapse" -> {
                setDeltaMovement(0, getDeltaMovement().y, 0);
                if (tick == 0) {
                    com.lealex.alchyforbidden.network.SpellFxPayload.send(server, com.lealex.alchyforbidden.network.SpellFxPayload.HOLE, position().add(0, 1.5, 0), -1, move.length(), 1.8F);
                    playSound(SoundEvents.WARDEN_SONIC_CHARGE, 1.0F, 0.5F);
                }
                if (tick < move.strike()) { // all round pulled in toward the planted blade
                    for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(10), this::foe)) {
                        Vec3 in = position().subtract(near.position()).normalize().scale(0.12);
                        near.setDeltaMovement(near.getDeltaMovement().add(in.x, 0.02, in.z));
                        near.hurtMarked = true;
                    }
                }
                if (tick == move.strike()) {
                    for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(5), this::foe)) {
                        near.hurtServer(server, damageSources().indirectMagic(this, this), hitFor(2.5));
                        onHitAffixes(server, near);
                        Vec3 out = near.position().subtract(position()).multiply(1, 0, 1).normalize();
                        near.setDeltaMovement(out.x * 1.2, 0.6, out.z * 1.2);
                        near.hurtMarked = true;
                    }
                    fxNova(position(), 5.5F, 16, RIFT);
                    playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.5F, 0.5F);
                }
            }
            default -> {}
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(SoundEvents.NETHERITE_BLOCK_STEP, 0.8F, 0.6F);
    }

    @Override
    public float getVoicePitch() {
        return gradePitch(0.6F + random.nextFloat() * 0.1F);
    }
}
