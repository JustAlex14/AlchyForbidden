package com.lealex.alchyforbidden.familiar;

import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.network.AttackFxPayload;
import com.lealex.alchyforbidden.network.SpellFxPayload;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.phys.Vec3;

/**
 * The Weeping Chandelier (ring IV, a great one of the void): a great iron chandelier hanging from a chain lost in the
 * rift, its candles burning with soul fire, the ghosts of the guests who died under it circling it.
 * Phase I, hanging: the pendulum swing, wax rain that leaves burning, slowing puddles, six homing soul flames, a chain
 * lash along the floor to jump. Phase II, fallen: it crashes with a shockwave and drags itself on its arms; three
 * ghosts that heal it until they are killed; a ring of candles lit one by one round the arena (break them, or the nova
 * burns everything when all are lit); six seconds of blackout; a toll that rolls along the floor (jump it).
 * Phase III, the last dance (Void-Rifted only): it rises upside down raining fire, the arena goes dark but near the
 * candles, ghost dancers waltz and hurt and slow what they touch, and the floor turns to burning wax from the edge in.
 * As a familiar it rests as a few floating candles; in a fight it hangs over its owner, rains wax and flames, lashes
 * its chain and frees its ghosts.
 */
public class WeepingChandelier extends RiftBeast implements RangedAttackMob {
    private static final int SOUL = 0x6CE0E8, WAX = 0xF0C060, CHAIN = 0x8A8698;
    private final java.util.List<java.util.UUID> guests = new java.util.ArrayList<>(), dancers = new java.util.ArrayList<>();
    private final java.util.List<BlockPos> candles = new java.util.ArrayList<>();
    private final java.util.Set<java.util.UUID> swingHit = new java.util.HashSet<>();
    private long nextDance, waxFrom, gutterUntil, nextGutter, nextClaw;
    private boolean snuffed, crashing, landed;
    private int steered = -1;
    private Vec3 swingFrom = Vec3.ZERO, swingTo = Vec3.ZERO, swingAim = Vec3.ZERO, rainAt = Vec3.ZERO;

    /** An ability drives its body this tick: straight toward a point, never faster than maxStep a tick (the hover waits). */
    private void steer(Vec3 to, double maxStep) {
        Vec3 d = to.subtract(position());
        double l = d.length();
        setDeltaMovement(l > maxStep ? d.scale(maxStep / l) : d);
        getNavigation().stop();
        steered = tickCount;
    }

    /** A crash onto the floor: a ring rolls out (jump it), the floor shakes. */
    private void impact(ServerLevel level, float damage, int color) {
        Vec3 floor = floorBelow();
        groundRing(floor.add(0, 0.05, 0), 0.6, 14, damage, color);
        fxNova(floor, 5F, 14, color);
        level.sendParticles(ParticleTypes.EXPLOSION, floor.x, floor.y + 0.5, floor.z, 3, 1.2, 0.2, 1.2, 0);
        level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.HOSTILE, 1.6F, 0.4F);
        level.playSound(null, blockPosition(), SoundEvents.CHAIN_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 1.2F, 0.6F);
        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(1.5, 1, 1.5), this::foe)) { // what was under it
            near.invulnerableTime = 0;
            near.hurtServer(level, damageSources().mobAttack(this), damage * 1.2F);
        }
    }

    public WeepingChandelier(EntityType<? extends WeepingChandelier> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 320.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FLYING_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
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

    // ---- Its entrance: it comes down slowly out of a rift on its chain, dark; then its candles catch one by one ----

    @Override
    protected Vec3 introPath(Vec3 home, float p) {
        return home.add(0, 4 + 9 * (1 - ease(p / 0.55F)), 0);
    }

    @Override
    protected void introEffects(ServerLevel level, int t, Vec3 home) {
        if (t == 0) {
            com.lealex.alchyforbidden.network.SpellFxPayload.send(level, com.lealex.alchyforbidden.network.SpellFxPayload.RIFT, home.add(0, 14, 0), -1, 70, 5.0F);
            level.playSound(null, net.minecraft.core.BlockPos.containing(home), net.minecraft.sounds.SoundEvents.END_PORTAL_SPAWN, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.5F);
        }
        if (t < 56) {
            if (t % 7 == 0) playSound(net.minecraft.sounds.SoundEvents.CHAIN_STEP, 1.4F, 0.4F + t / 160F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, getX(), getY() + 3.5, getZ(), 2, 0.3, 0.6, 0.3, 0.02);
        }
        if (t >= 58 && t < 82 && (t - 58) % 4 == 0) { // its candles catch, one by one
            int i = (t - 58) / 4;
            Vec3 c = candle(i);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + 0.3, c.z, 14, 0.1, 0.25, 0.1, 0.04);
            level.playSound(null, net.minecraft.core.BlockPos.containing(c), net.minecraft.sounds.SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.HOSTILE, 0.9F, 0.6F + i * 0.12F);
        }
        if (t == 84) { // all lit: a breath of soul fire, its guests come round
            fxNova(position(), 6F, 18, SOUL);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, getX(), getY() + 1.5, getZ(), 30, 1.5, 0.8, 1.5, 0.04);
            level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.BELL_RESONATE, net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.5F);
        }
    }

    private static float ease(float x) {
        x = Math.max(0F, Math.min(1F, x));
        return x * x * (3 - 2 * x);
    }

    @Override
    public RiftCreature creature() {
        return RiftCreature.WEEPING_CHANDELIER;
    }

    @Override
    protected void addAttackGoals() {
        goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 30, 40, 18.0F));
    }

    private double hoverHeight() {
        if (familiar()) return 3.0; // over its owner's head
        if (guttering()) return 0.6; // its flames gutter: it sinks to the floor, open to blows
        return switch (phase()) {
            case 2 -> 0.3; // carried low on its arms
            case 3 -> 4.5;
            default -> 4.0;
        };
    }

    /** Its flames guttering after a great effort: low, slow, taking half again as much. */
    public boolean guttering() {
        return arena() != null && !familiar() && level().getGameTime() < gutterUntil;
    }

    private void gutter(ServerLevel level, int ticks, LivingEntity target) {
        gutterUntil = level.getGameTime() + ticks;
        playSound(SoundEvents.FIRE_EXTINGUISH, 1.5F, 0.5F);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 30, 1.2, 0.6, 1.2, 0.02);
        if (target instanceof ServerPlayer sp) sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden.gutter"), true);
    }

    @Override
    protected void onSpecialEnd(ServerLevel level, String id, LivingEntity target) {
        switch (id) {
            case "pendulum", "toll" -> gutter(level, 70, target);
            case "blackout" -> gutter(level, 50, target);
            case "candle_ring" -> { if (snuffed) gutter(level, 140, target); } // its candles broken: a long opening
            default -> {}
        }
    }

    @Override
    protected int boltStyle(int color, int fire, boolean boomerang) {
        return color == WAX ? AttackFxPayload.WAX : AttackFxPayload.ORB;
    }

    @Override
    protected float boltSize() {
        return 1.25F;
    }

    @Override
    protected java.util.List<String> specialIds() {
        return java.util.List.of("pendulum", "candle_ring", "blackout", "toll");
    }

    @Override
    protected boolean moveAllowed(RiftCreature.Move move) {
        if (familiar()) return true;
        if (guttering()) return false;
        return switch (move.id()) {
            case "chain_lash", "soul_flames" -> phase() != 2;
            case "free_ghosts" -> phase() == 2;
            default -> true;
        };
    }

    private Vec3 candle(int i) {
        double a = i * Math.PI * 2 / 6 + tickCount * 0.02;
        return position().add(Math.cos(a) * 1.4, getBbHeight() * (phase() == 3 && !familiar() ? 0.2 : 0.75), Math.sin(a) * 1.4);
    }

    private Vec3 floorBelow() {
        BlockPos.MutableBlockPos at = blockPosition().mutable();
        for (int i = 0; i < 20; i++) {
            if (!level().getBlockState(at).isAir()) return new Vec3(getX(), at.getY() + 1, getZ());
            at.move(0, -1, 0);
        }
        return new Vec3(getX(), getY() - hoverHeight(), getZ());
    }

    private Vec3 arenaCenter() {
        return arena() == null ? position() : Vec3.atBottomCenterOf(arena().above());
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        if (!(level() instanceof ServerLevel server) || guttering()) return; // its flames down to embers: nothing to throw
        fireBolt(candle(tickCount % 6), target, hitFor(0.8), SOUL, 0, 0.9);
        server.broadcastEntityEvent(this, SWING);
        playSound(SoundEvents.SOUL_ESCAPE.value(), 0.8F, 1.4F);
    }

    @Override
    protected void onBoltHit(ServerLevel level, LivingEntity target, float damage) {
        if (knows(5)) target.igniteForSeconds(3); // Soul Candles
        if (knows(15)) target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), this); // Molten Wax
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (knows(20) && source.getDirectEntity() == source.getEntity() && source.getEntity() != null) amount *= 0.75F; // Eternal Flame
        if (guttering()) amount *= 1.5F;
        return super.hurtServer(level, source, amount);
    }

    @Override
    protected void onPhase(ServerLevel level, int phase) {
        if (phase == 2) { // the chain snaps (it falls once the shift is over: crashing, in aiStep)
            crashing = true;
            level.playSound(null, blockPosition(), SoundEvents.CHAIN_BREAK, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.5F);
        } else if (phase == 3) { // the last dance
            level.playSound(null, blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, net.minecraft.sounds.SoundSource.HOSTILE, 1.5F, 0.5F);
            nextDance = 0;
            waxFrom = level.getGameTime();
            removeCandles(level);
            int full = Config.voidArenaRadius();
            fxEdge(arenaCenter(), full, 5F, 1F / 40F, 12000, 0xE89A3A); // the floor burning from the edge in, as lastDance counts it
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level() instanceof ServerLevel server && !dormant()) {
            LivingEntity target = getTarget();
            boolean fighting = target != null && target.isAlive();
            boolean arenaFight = arena() != null && !familiar();
            if (crashing && shifting()) { // the chain giving way: it hangs where it is through the roar (the hover sank it gently before)
                setDeltaMovement(Vec3.ZERO);
                steered = tickCount;
            } else if (crashing) { // the chain gone, it drops onto the floor
                steer(floorBelow().add(0, 0.3, 0), 0.9);
                if (getY() - floorBelow().y < 0.6) {
                    crashing = false;
                    impact(server, hitFor(1.2), CHAIN);
                }
            }
            if (steered != tickCount) {
                double want = floorBelow().y + hoverHeight();
                if (arenaFight && fighting && phase() == 2 && !guttering() && !shifting()) {
                    crawl(server, target);
                } else if (arenaFight && fighting && phase() == 3 && !guttering() && !shifting()) { // the last dance: it waltzes round the arena
                    Vec3 c = arenaCenter();
                    double a = tickCount * 0.02;
                    steer(new Vec3(c.x + Math.cos(a) * 6, want, c.z + Math.sin(a) * 6), 0.14);
                } else if (!getMoveControl().hasWanted() || shifting() || arenaFight) {
                    double dy = want - getY();
                    setDeltaMovement(getDeltaMovement().x * (arenaFight ? 0.8 : 1), Math.max(-0.3, Math.min(0.3, dy * 0.1)), getDeltaMovement().z * (arenaFight ? 0.8 : 1));
                }
            }
            guests.removeIf(id -> !(server.getEntity(id) instanceof LivingEntity g) || !g.isAlive());
            if (arena() != null && !familiar() && tickCount % 20 == 0 && !guests.isEmpty()) { // its ghosts mend it while they live
                heal(guests.size() * 2.0F);
                for (java.util.UUID id : guests) {
                    if (server.getEntity(id) instanceof Entity g) {
                        Vec3 from = g.position().add(0, 0.8, 0), to = middle().subtract(from); // a thread of soul light mending it
                        fxBeam(from, to, (float) to.length(), 0.18F, 16, SOUL, AttackFxPayload.CORE, 0, 0);
                    }
                }
            }
            if (phase() == 3 && fighting && !familiar()) lastDance(server, target);
            Vec3 v = getDeltaMovement(); // bit 1: guttering (flames to embers); bit 2: scuttling (the legs walk)
            int shown = (guttering() ? 1 : 0) | (v.x * v.x + v.z * v.z > 0.0009 ? 2 : 0);
            if (visual() != shown) setVisual(shown);
        }
        if (level().isClientSide() && !dormant()) {
            for (int i = 0; i < 2; i++) {
                Vec3 c = candle(random.nextInt(6));
                level().addParticle(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + 0.2, c.z, 0, 0.02, 0);
            }
        }
    }

    /** Phase II: fallen, it scuttles after its prey on its arms like a spider, in bursts, and stabs with them up close. */
    private void crawl(ServerLevel level, LivingEntity target) {
        Vec3 flat = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
        double dist = flat.length(), want = floorBelow().y + hoverHeight();
        float yaw = (float) (Math.atan2(flat.z, flat.x) * (180 / Math.PI)) - 90F;
        setYRot(net.minecraft.util.Mth.approachDegrees(getYRot(), yaw, 8F));
        setYBodyRot(getYRot());
        if (dist > 3.0) {
            double burst = 0.07 + 0.2 * Math.max(0, Math.sin(tickCount * 0.35)); // scuttling: quick, pause, quick
            Vec3 to = position().add(flat.normalize().scale(Math.min(dist - 2.5, 2)));
            steer(new Vec3(to.x, want, to.z), burst);
        } else {
            steer(new Vec3(getX(), want, getZ()), 0.1);
            if (level.getGameTime() >= nextClaw) { // an arm stabs down at it
                nextClaw = level.getGameTime() + 30;
                level.broadcastEntityEvent(this, SWING);
                target.invulnerableTime = 0;
                target.hurtServer(level, damageSources().mobAttack(this), hitFor(1.0));
                onHitAffixes(level, target);
                target.setDeltaMovement(target.getDeltaMovement().add(flat.normalize().x * 0.8, 0.35, flat.normalize().z * 0.8));
                target.hurtMarked = true;
                playSound(SoundEvents.CHAIN_HIT, 1.4F, 0.7F);
            }
        }
        if (tickCount % 6 == 0) playSound(SoundEvents.CHAIN_STEP, 0.8F, 0.6F + random.nextFloat() * 0.2F);
    }

    /** Phase III: fire rains, the dancers waltz (hurting and slowing what they touch), the floor burns from the edge in. */
    private void lastDance(ServerLevel level, LivingEntity target) {
        long now = level.getGameTime();
        dancers.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity d) || !d.isAlive());
        if (now >= nextGutter && arena() != null) { // even dancing it must stop to breathe
            nextGutter = now + 260;
            if (nextDance > 0) gutter(level, 70, target);
        }
        if (now >= nextDance) {
            nextDance = now + 120;
            for (int i = 0; i < 2 && dancers.size() < 6; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                RiftBeast dancer = spawnMinion(level, RiftCreature.VEIL_WISP, target.position().add(Math.cos(a) * 6, 0.5, Math.sin(a) * 6), 400);
                if (dancer != null) {
                    dancer.setCustomName(net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden.dancer"));
                    dancers.add(dancer.getUUID());
                }
            }
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0), this); // dark but near the candles
        }
        if (now % 10 == 0) { // the dancers' touch
            for (java.util.UUID id : dancers) {
                if (!(level.getEntity(id) instanceof Entity d)) continue;
                double a = now * 0.1 + id.hashCode();
                d.setDeltaMovement(Math.cos(a) * 0.25, d.getDeltaMovement().y, Math.sin(a) * 0.25); // waltzing
                for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, d.getBoundingBox().inflate(1.0), this::foe)) {
                    near.invulnerableTime = 0;
                    near.hurtServer(level, damageSources().indirectMagic(this, this), 2.0F);
                    near.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2), this);
                }
            }
        }
        if (now % 15 == 0) { // fire rain
            double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * 6;
            fireBolt(target.position().add(Math.cos(a) * r, 9, Math.sin(a) * r), new Vec3(0, -1, 0), hitFor(0.6), WAX, 0, 0.8, null, false, 3);
        }
        // the wax floor creeping in from the edge
        int full = Config.voidArenaRadius();
        double edge = Math.max(5, full - (now - waxFrom) / 40.0);
        Vec3 c = arenaCenter();
        if (now % 10 == 0) {
            for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(full + 2, 4, full + 2), this::foe)) {
                double dx = near.getX() - c.x, dz = near.getZ() - c.z;
                if (dx * dx + dz * dz < edge * edge) continue;
                near.invulnerableTime = 0;
                near.hurtServer(level, damageSources().inFire(), 2.0F);
                near.igniteForSeconds(3);
                near.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2), this);
            }
        }
    }

    // ---- The moves (as a familiar, and in its fight by phase) ----

    @Override
    protected void performMove(RiftCreature.Move move, int tick, LivingEntity target) {
        if (!(level() instanceof ServerLevel server)) return;
        switch (move.id()) {
            case "wax_rain" -> { // burning wax falls round its prey: puddles that burn and slow
                if (tick == 0) {
                    rainAt = target.position();
                    playSound(SoundEvents.FIRE_AMBIENT, 1.5F, 0.6F);
                }
                if (tick >= move.strike() && tick % 5 == 0) {
                    rainAt = rainAt.lerp(target.position(), 0.3);
                    double a = random.nextDouble() * Math.PI * 2, r = random.nextDouble() * 3;
                    Vec3 at = rainAt.add(Math.cos(a) * r, 0, Math.sin(a) * r);
                    fxColumn(at, 0.35F, 9F, 0, 10, WAX); // a stream of wax pouring down
                    floorZone(at.add(0, 0.1, 0), 1.6, 140, 1.0F, WAX, true, true);
                }
            }
            case "soul_flames" -> { // six homing soul flames from its candles
                if (tick >= move.strike() && (tick - move.strike()) % 3 == 0 && (tick - move.strike()) / 3 < 6) {
                    Vec3 from = candle((tick - move.strike()) / 3);
                    fireBolt(from, new Vec3(random.nextDouble() - 0.5, 0.6, random.nextDouble() - 0.5), hitFor(0.7), SOUL, 0, 1.0, target, false, 3);
                    playSound(SoundEvents.SOUL_ESCAPE.value(), 1.0F, 1.6F);
                }
            }
            case "chain_lash" -> { // the chain drop: it hauls itself up its chain, then lets go and crashes onto the floor
                Vec3 floor = floorBelow();
                if (tick == 0) {
                    landed = false;
                    playSound(SoundEvents.CHAIN_PLACE, 1.5F, 0.5F);
                }
                if (tick < 10) steer(new Vec3(getX(), floor.y + hoverHeight() + 2.5, getZ()), 0.3);                  // up
                else if (tick < move.strike()) steer(new Vec3(getX(), floor.y + hoverHeight() + 2.5, getZ()), 0.05); // a breath, trembling
                else if (!landed) { // let go
                    steer(floor.add(0, 0.3, 0), 1.6);
                    if (getY() - floor.y < 0.7 || tick > move.strike() + 4) {
                        landed = true;
                        impact(server, hitFor(1.2), CHAIN);
                    }
                } else steer(floor.add(0, 0.3, 0), 0.2); // a moment stuck on the floor
            }
            case "free_ghosts" -> { // three drowned guests come out of it; in its fight they mend it until they die
                if (tick == move.strike()) {
                    for (int i = 0; i < 3; i++) {
                        double a = i * Math.PI * 2 / 3;
                        RiftBeast ghost = spawnMinion(server, RiftCreature.VEIL_WISP, position().add(Math.cos(a) * 3, 0.5, Math.sin(a) * 3), 600);
                        if (ghost != null) {
                            ghost.setCustomName(net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden.guest"));
                            guests.add(ghost.getUUID());
                        }
                    }
                    playSound(SoundEvents.SOUL_ESCAPE.value(), 2.0F, 0.6F);
                    if (arena() != null && !familiar() && target instanceof ServerPlayer sp) {
                        sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden.guests_warning"), true);
                    }
                }
            }
            default -> {}
        }
    }

    // ---- The fight's specials ----

    @Override
    protected String nextSpecial(int phase, LivingEntity target) {
        if (guttering()) return null;
        return switch (phase) {
            case 1 -> "pendulum";
            case 2 -> switch (random.nextInt(3)) {
                case 0 -> candles.isEmpty() ? "candle_ring" : "toll";
                case 1 -> "blackout";
                default -> "toll";
            };
            default -> null; // the last dance runs on its own
        };
    }

    @Override
    protected int specialLength(String id) {
        return switch (id) {
            case "pendulum" -> 60;
            case "candle_ring" -> 90;
            case "blackout" -> 20;
            case "toll" -> 32;
            default -> 20;
        };
    }

    @Override
    protected void performSpecial(ServerLevel server, String id, int tick, LivingEntity target) {
        switch (id) {
            case "pendulum" -> { // drawn back and up on its chain, then a slow great arc low through where its prey stood
                double floorY = floorBelow().y;
                if (tick == 0) {
                    // drawn back on the far side of its prey, but never more than 7 blocks out: a longer arc could not keep
                    // within the swing's speed cap and cut the corner high over the prey's head
                    Vec3 flat = position().subtract(target.position()).multiply(1, 0, 1);
                    double out = Math.min(flat.length() + 3, 7);
                    Vec3 start = target.position().add(flat.normalize().scale(out));
                    swingFrom = keepInside(new Vec3(start.x, getY() + 2, start.z));
                    swingHit.clear();
                    playSound(SoundEvents.CHAIN_PLACE, 1.5F, 0.5F);
                }
                if (tick < 20) steer(swingFrom, 0.55); // a glide, not a jump
                if (tick == 8) { // the aim is set: its shadow sweeps the floor where it will swing through
                    swingAim = new Vec3(target.getX(), floorY + 1.0, target.getZ());
                    Vec3 dir = swingAim.subtract(swingFrom).multiply(1, 0, 1).normalize();
                    swingTo = keepInside(swingAim.add(dir.scale(5)).add(0, hoverHeight() + 1, 0));
                    for (int k = 0; k <= 8; k++) {
                        Vec3 at = swingFrom.lerp(swingTo, k / 8.0);
                        fxZone(new Vec3(at.x, floorY, at.z), 1.4F, 44, 0x100818, AttackFxPayload.Z_SHADOW);
                    }
                }
                if (tick >= 20 && tick < 50) {
                    double s = (tick - 19) / 30.0, u = (1 - Math.cos(s * Math.PI)) / 2; // slow, fastest at the bottom, slow
                    Vec3 flat = swingFrom.lerp(swingTo, u);
                    double mid = (swingFrom.y + swingTo.y) / 2, depth = Math.max(0, mid - swingAim.y);
                    Vec3 next = new Vec3(flat.x, flat.y - Math.sin(u * Math.PI) * depth, flat.z);
                    steer(next, 0.9);
                    if (tick % 4 == 0) playSound(SoundEvents.CHAIN_STEP, 1.2F, 0.5F);
                    for (LivingEntity hit : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(1.2), this::foe)) {
                        if (!swingHit.add(hit.getUUID())) continue; // once a swing (a burning prey was often skipped before)
                        hit.invulnerableTime = 0;
                        hit.hurtServer(server, damageSources().mobAttack(this), hitFor(1.5));
                        hit.igniteForSeconds(4);
                        Vec3 out = hit.position().subtract(position()).multiply(1, 0, 1).normalize();
                        hit.setDeltaMovement(out.x * 1.2, 0.5, out.z * 1.2);
                        hit.hurtMarked = true;
                    }
                    server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1, getZ(), 4, 0.8, 0.4, 0.8, 0.01);
                }
                if (tick >= 50) steer(swingTo, 0.1); // swinging to a stop
            }
            case "candle_ring" -> { // eight real candles light round the arena one by one; all lit at the end = the nova
                Vec3 c = arenaCenter();
                double r = Config.voidArenaRadius() * 0.6;
                if (tick == 0) {
                    removeCandles(server);
                    if (target instanceof ServerPlayer sp) sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden.nova_warning"), true);
                }
                if (tick % 8 == 0 && tick / 8 < 8) {
                    double a = (tick / 8) * Math.PI / 4 + Math.PI / 8; // between the arena's standing runes (they stand on the 45 degree lines)
                    BlockPos at = BlockPos.containing(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r);
                    for (int up = 0; up < 3 && !server.getBlockState(at).isAir(); up++) at = at.above(); // never on something
                    if (tempBlock(server, at, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 4).setValue(CandleBlock.LIT, true), 400)) candles.add(at);
                    server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 0.5, 12, 0.2, 0.4, 0.2, 0.02);
                    playSound(SoundEvents.FLINTANDSTEEL_USE, 1.2F, 0.6F + tick / 120F);
                }
                if (tick == specialLength(id) - 1) {
                    snuffed = false;
                    long lit = candles.stream().filter(p -> server.getBlockState(p).is(Blocks.CANDLE) && server.getBlockState(p).getValue(CandleBlock.LIT)).count();
                    if (!candles.isEmpty() && lit >= candles.size()) { // every candle it lit still burns: the nova
                        for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(24, 8, 24), this::foe)) {
                            near.invulnerableTime = 0;
                            near.hurtServer(server, damageSources().indirectMagic(this, this), hitFor(3.0));
                            near.igniteForSeconds(6);
                        }
                        Vec3 floor = floorBelow();
                        fxNova(floor, 24F, 30, SOUL);                       // the blast across the whole arena
                        fxNova(floor.add(0, 1.5, 0), 20F, 40, 0xFFFFFF);
                        fxColumn(floor, 3F, 14F, 0, 24, SOUL);              // a pillar of soul fire out of it
                        for (BlockPos p : candles) fxColumn(Vec3.atBottomCenterOf(p), 0.6F, 8F, 0, 20, SOUL); // every candle flaring
                        server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, floor.x, floor.y + 1, floor.z, 300, 8, 1, 8, 0.15);
                        SpellFxPayload.send(server, SpellFxPayload.RIFT, position().add(0, 2, 0), -1, 20, 4.0F);
                        playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.5F);
                    } else {
                        server.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1, getZ(), 60, 1.5, 0.8, 1.5, 0.05);
                        playSound(SoundEvents.FIRE_EXTINGUISH, 1.5F, 0.6F);
                        snuffed = true;
                        if (target instanceof ServerPlayer sp) sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden.nova_snuffed"), true);
                    }
                    removeCandles(server);
                }
                if (tick > 64 && tick % 4 == 0) playSound(SoundEvents.BLAZE_SHOOT, 0.6F, 0.5F + (tick - 64) / 40F); // rising toward the nova
            }
            case "blackout" -> { // every flame goes out for six seconds
                if (tick == 0) {
                    for (LivingEntity near : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(30), this::foe)) {
                        near.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0), this);
                        near.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0), this);
                    }
                    server.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 80, 2, 1, 2, 0.05);
                    playSound(SoundEvents.FIRE_EXTINGUISH, 2.0F, 0.4F);
                }
            }
            case "toll" -> { // it rears up on its arms and slams itself down like a bell: the toll rolls along the floor, jump it
                Vec3 floor = floorBelow();
                if (tick == 0) {
                    landed = false;
                    playSound(SoundEvents.BELL_BLOCK, 2.0F, 0.5F);
                }
                if (tick < 14) steer(new Vec3(getX(), floor.y + hoverHeight() + 2.2, getZ()), 0.2);
                else if (tick < 18) steer(new Vec3(getX(), floor.y + hoverHeight() + 2.2, getZ()), 0.03);
                else if (!landed) {
                    steer(floor.add(0, 0.3, 0), 1.4);
                    if (getY() - floor.y < 0.7 || tick > 22) {
                        landed = true;
                        impact(server, hitFor(1.0), SOUL);
                        groundRing(floor.add(0, 0.05, 0), 0.5, 22, hitFor(1.3), SOUL); // the toll, wider
                        playSound(SoundEvents.BELL_RESONATE, 2.0F, 0.5F);
                    }
                } else steer(floor.add(0, 0.3, 0), 0.15);
            }
            default -> {}
        }
    }

    /** A point held within the arena's wall (so it is never set back inside, which looks like a jump). */
    private Vec3 keepInside(Vec3 p) {
        if (arena() == null) return p;
        Vec3 c = arenaCenter(), off = p.subtract(c).multiply(1, 0, 1);
        double lim = Config.voidArenaRadius() - 3.5;
        return off.length() <= lim ? p : c.add(off.normalize().scale(lim)).add(0, p.y - c.y, 0);
    }

    private void removeCandles(ServerLevel level) {
        for (BlockPos pos : candles) if (level.getBlockState(pos).is(Blocks.CANDLE)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        candles.clear();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (level() instanceof ServerLevel server) {
            removeCandles(server);
            for (java.util.UUID id : guests) if (server.getEntity(id) instanceof Entity e) e.discard();
            for (java.util.UUID id : dancers) if (server.getEntity(id) instanceof Entity e) e.discard();
        }
        super.remove(reason);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SOUL_ESCAPE.value();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.CHAIN_HIT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.CHAIN_BREAK;
    }

    @Override
    public float getVoicePitch() {
        return gradePitch(0.6F + random.nextFloat() * 0.1F);
    }
}
