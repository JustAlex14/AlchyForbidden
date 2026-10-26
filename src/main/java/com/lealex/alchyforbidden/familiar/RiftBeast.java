package com.lealex.alchyforbidden.familiar;

import com.lealex.alchyforbidden.network.AttackFxPayload;

import com.lealex.alchyforbidden.AlchyForbidden;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A creature of the rift. Three lives: WILD (a stray one, summoned by a command: it hunts players), ARENA (called by a
 * Rift Maw's ritual: it hunts only its challenger and is never saved; the maw watches it) and FAMILIAR (bound in a
 * familiar ring and called out by its wearer: it follows them and fights what they fight, never saved: the ring keeps
 * it). Its grade (0..3: Rifted, Deep-Rifted, Void-Rifted) and, as a familiar, its level raise its health, damage and
 * speed. Not a Monster on purpose: iron golems leave a familiar alone, and spells decide by {@link #wild()}.
 */
public abstract class RiftBeast extends PathfinderMob {
    private static final EntityDataAccessor<Integer> DATA_CORRUPTION = SynchedEntityData.defineId(RiftBeast.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_LEVEL = SynchedEntityData.defineId(RiftBeast.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FAMILIAR = SynchedEntityData.defineId(RiftBeast.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ECHO = SynchedEntityData.defineId(RiftBeast.class, EntityDataSerializers.BOOLEAN);
    /** The fight's phase (1..3) of a great one of the void; 1 for everything else. */
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(RiftBeast.class, EntityDataSerializers.INT);
    /** A great one of the void bound as a familiar rests small at its owner's shoulder until a fight wakes it. */
    private static final EntityDataAccessor<Boolean> DATA_DORMANT = SynchedEntityData.defineId(RiftBeast.class, EntityDataSerializers.BOOLEAN);
    /** The special a great one plays now (its index in specialIds(), -1 none), for its model. */
    private static final EntityDataAccessor<Integer> DATA_SPECIAL = SynchedEntityData.defineId(RiftBeast.class, EntityDataSerializers.INT);
    /** One number a creature's model may read for what it shows (stones round it, shards, candles lit...). */
    private static final EntityDataAccessor<Integer> DATA_VISUAL = SynchedEntityData.defineId(RiftBeast.class, EntityDataSerializers.INT);
    public static final byte PHASE_EVENT = 71, UNFOLD_EVENT = 72;
    /** How long a phase change holds it (the roar, the arena changing), in ticks. */
    public static final int PHASE_TICKS = 40;
    /** How small a great one rests as a familiar (each its own: the whole model is drawn that small). */
    public float dormantScale() {
        return 0.3F;
    }

    /** Where it drifts while resting (kept until its owner moves away from it). */
    private @Nullable Vec3 restSpot;
    /** Where its owner stood last tick: a jump of more than 8 blocks in one tick is a teleport, and it follows through a rift. */
    private @Nullable Vec3 lastOwnerPos;
    private long phaseShiftUntil, restingSince = -1;
    /** Client: ticks since the last phase change began, and how far it has folded small (0 full, 1 dormant). */
    public int clientPhaseTicks = 1000;
    public float dormantAnim, dormantAnimO;
    private static final Identifier FRENZY_SPEED = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "frenzy_speed");
    private static final Identifier FRENZY_DAMAGE = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "frenzy_damage");
    private static final Identifier HUNGER = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "hunger");
    private static final Identifier HEALTH = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "rift_health");
    private static final Identifier DAMAGE = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "rift_damage");
    private static final Identifier SPEED = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "rift_speed");
    private static final Identifier ARMOR = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "rift_armor");
    private static final Identifier TOUGHNESS = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "rift_toughness");
    /** The entity events that start move 0..3 on clients. */
    public static final byte MOVE_EVENT = 80;
    /** The entity event that stops a move cut short on clients. */
    public static final byte MOVE_STOP = 79;
    /** The entity event that starts the attack swing on clients. */
    public static final byte SWING = 4;
    /** The entity event that starts the petting on clients, and how long it lasts. */
    public static final byte PET = 70;
    public static final int PET_TICKS = 50;

    private @Nullable UUID owner;
    private int slot = -1;
    private @Nullable BlockPos arena;
    private @Nullable UUID challenger;
    /** Client: ticks left of the attack swing, of the petting. */
    public int swingTicks, petTicks;
    /** Server: the game time the petting ends. */
    private long pettedUntil;
    // ---- the affixes (familiar/RiftAffix) and what they keep track of, all server side ----
    private java.util.List<String> affixes = java.util.List.of();
    private FamiliarData shape = null;      // its grade and level, for an echo of it
    private boolean graveUsed, splitUsed, wardUp = true;
    private long wardBack, blinkReady;
    private int hunger, echoTicks;
    private net.minecraft.server.level.@Nullable ServerBossEvent bossBar;
    private long riftVolley = -1;
    private final java.util.List<Vec3> volleyFrom = new java.util.ArrayList<>();

    protected RiftBeast(EntityType<? extends RiftBeast> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public abstract RiftCreature creature();

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CORRUPTION, 0);
        builder.define(DATA_LEVEL, 1);
        builder.define(DATA_FAMILIAR, false);
        builder.define(DATA_ECHO, false);
        builder.define(DATA_PHASE, 1);
        builder.define(DATA_DORMANT, false);
        builder.define(DATA_SPECIAL, -1);
        builder.define(DATA_VISUAL, 0);
    }

    public int visual() {
        return entityData.get(DATA_VISUAL);
    }

    protected void setVisual(int value) {
        entityData.set(DATA_VISUAL, value);
    }

    public int specialShown() {
        return entityData.get(DATA_SPECIAL);
    }

    /** The ids of its specials, in a fixed order (their index is what the model is told). */
    protected java.util.List<String> specialIds() {
        return java.util.List.of();
    }

    public int clientSpecialTicks;

    // ---- Phases (the void's great ones) and the dormant form ----

    public int phase() {
        return entityData.get(DATA_PHASE);
    }

    public boolean dormant() {
        return entityData.get(DATA_DORMANT);
    }

    /** How many phases its fight has: 2 for a great one of the void in its arena, 3 when it came Void-Rifted; else 1. */
    public int maxPhase() {
        if (arena == null || echo() || creature().tier() < 4) return 1;
        return corruption() >= 3 ? 3 : 2;
    }

    /** It is changing phase: held, untouchable, roaring. */
    public boolean shifting() {
        return level().getGameTime() < phaseShiftUntil;
    }

    /** A new phase began (server): each great one changes its arena, its body, its moves. */
    protected void onPhase(ServerLevel level, int phase) {}

    // ---- A great one's fight specials: attacks it has only in its arena, by phase, between its moves ----

    private @Nullable String special;
    private int specialTick;
    private long specialReady;
    private boolean moving;

    /** The next special to use in this phase (null: none now). Arena bosses only. */
    protected @Nullable String nextSpecial(int phase, LivingEntity target) {
        return null;
    }

    /** How long a special plays, in ticks. */
    protected int specialLength(String id) {
        return 40;
    }

    /** One tick of a special. */
    protected void performSpecial(ServerLevel level, String id, int tick, LivingEntity target) {}

    /** Ticks before its next special once one ends (a busier great one returns less). */
    protected int specialGap() {
        return 80 + random.nextInt(60);
    }

    /** A special played to its end (not cut short). */
    protected void onSpecialEnd(ServerLevel level, String id, LivingEntity target) {}

    /** A special cut short (its target gone, a phase change): undo what it set (a pose number, a hold...). */
    protected void onSpecialCut(ServerLevel level, String id) {}

    /** It is busy with a move or a special. */
    public boolean busy() {
        return moving || special != null;
    }

    private void tickSpecial(ServerLevel level) {
        LivingEntity target = getTarget();
        if (special != null) {
            if (target == null || !target.isAlive() || shifting() || introducing()) {
                String cut = special;
                special = null;
                entityData.set(DATA_SPECIAL, -1);
                specialReady = level.getGameTime() + 40;
                onSpecialCut(level, cut);
                return;
            }
            performSpecial(level, special, specialTick++, target);
            if (specialTick >= specialLength(special)) {
                String done = special;
                special = null;
                onSpecialEnd(level, done, target);
                entityData.set(DATA_SPECIAL, -1);
                specialReady = level.getGameTime() + specialGap();
            }
            return;
        }
        if (maxPhase() <= 1 || target == null || !target.isAlive() || moving || shifting() || introducing() || level.getGameTime() < specialReady) return;
        special = nextSpecial(phase(), target);
        specialTick = 0;
        if (special == null) specialReady = level.getGameTime() + 40;
        else entityData.set(DATA_SPECIAL, specialIds().indexOf(special));
    }

    /** Whether a move may be used now (a great one uses some moves only in some phases). */
    protected boolean moveAllowed(RiftCreature.Move move) {
        return true;
    }

    private void tickPhase(ServerLevel level) {
        int max = maxPhase();
        if (max <= 1 || shifting()) return;
        float health = getHealth() / getMaxHealth();
        int reached = health <= 0.25F && max >= 3 ? 3 : health <= 0.6F ? 2 : 1;
        if (reached <= phase()) return;
        // one phase at a time: a big blow that crosses two still plays the second phase (and its onPhase) before the third
        int want = phase() + 1;
        entityData.set(DATA_PHASE, want);
        phaseShiftUntil = level.getGameTime() + PHASE_TICKS;
        level.broadcastEntityEvent(this, PHASE_EVENT);
        getNavigation().stop();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SONIC_BOOM, getX(), getY() + getBbHeight() * 0.5, getZ(), 1, 0, 0, 0, 0);
        level.sendParticles(com.lealex.alchyforbidden.registry.ModRegistries.RIFT_MOTE.get(), getX(), getY() + getBbHeight() * 0.5, getZ(), 80, 1.2, 1.2, 1.2, 0.2);
        level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.WARDEN_ROAR, net.minecraft.sounds.SoundSource.HOSTILE, 2.0F, 0.6F);
        for (ServerPlayer sp : challengers(level)) {
            sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden.phase_" + want, getDisplayName())
                    .withStyle(want >= 3 ? net.minecraft.ChatFormatting.WHITE : net.minecraft.ChatFormatting.RED), true);
        }
        onPhase(level, want);
    }

    @Override
    public net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
        net.minecraft.world.entity.EntityDimensions full = super.getDefaultDimensions(pose);
        if (!dormant()) return full;
        float s = dormantScale(); // small, but never too small to click (feeding, petting, naming)
        return net.minecraft.world.entity.EntityDimensions.scalable(Math.max(0.7F, full.width() * s), Math.max(0.7F, full.height() * s));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_DORMANT.equals(key)) refreshDimensions();
        if (DATA_SPECIAL.equals(key)) clientSpecialTicks = 0;
    }

    /** A familiar whose owner teleported (ender pearl, chorus fruit, a command...) comes after them through a rift at once. */
    private void followTeleport() {
        if (!familiar()) return;
        Player owner = ownerPlayer();
        if (owner == null) {
            lastOwnerPos = null;
            return;
        }
        Vec3 now = owner.position();
        Vec3 before = lastOwnerPos;
        lastOwnerPos = now;
        if (before == null || before.distanceToSqr(now) < 8 * 8 || distanceToSqr(owner) < 6 * 6) return;
        restSpot = null;
        Familiars.slipTo(this, owner);
    }

    /** A familiar great one at rest: small, floating at its owner's shoulder; a fight wakes it, three quiet seconds fold it again. */
    private void tickDormant(ServerLevel level) {
        if (!familiar() || echo() || creature().tier() < 4) return;
        Player owner = ownerPlayer();
        if (owner == null) return;
        LivingEntity target = getTarget();
        boolean fighting = target != null && target.isAlive() && target != owner;
        if (dormant()) {
            if (fighting) {
                unfold(level, owner);
                return;
            }
            // it drifts lazily to a spot beside its owner at chest height and stays there while they stand still,
            // so it can be fed and petted; it only picks a new spot once they have walked off
            Vec3 chest = owner.position().add(0, owner.getBbHeight() * 0.6, 0);
            if (restSpot == null || restSpot.distanceToSqr(chest) > 3.2 * 3.2) {
                Vec3 side = Vec3.directionFromRotation(0, owner.getYRot() + 70 + slot() * 35).scale(1.5);
                restSpot = chest.add(side.x, 0, side.z);
            }
            Vec3 spot = restSpot.add(0, Math.sin(tickCount * 0.06) * 0.12, 0);
            double far = distanceToSqr(spot);
            Vec3 at = far > 48 * 48 ? spot : position().lerp(spot, petting() ? 0 : far > 36 ? 0.3 : far > 9 ? 0.18 : 0.06); // flies after a running owner, snaps only when far off
            setPos(at.x, at.y, at.z);
            setDeltaMovement(Vec3.ZERO);
            getNavigation().stop();
            double dx = owner.getX() - getX(), dz = owner.getZ() - getZ(); // it looks at its owner
            float yaw = (float) (Math.atan2(dz, dx) * (180 / Math.PI)) - 90F;
            setYRot(net.minecraft.util.Mth.approachDegrees(getYRot(), yaw, 6F));
            setYBodyRot(getYRot());
            setYHeadRot(getYRot());
            return;
        }
        if (fighting) {
            restingSince = -1;
        } else if (restingSince < 0) {
            restingSince = level.getGameTime();
        } else if (level.getGameTime() - restingSince > 60) {
            fold(level);
        }
    }

    private void fold(ServerLevel level) {
        restingSince = -1;
        restSpot = null;
        Familiars.riftPuff(level, middle(), creature().color(), false);
        entityData.set(DATA_DORMANT, true);
        setNoGravity(true);
        noPhysics = true;
        refreshDimensions();
        level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.NEUTRAL, 0.8F, 0.6F);
    }

    private void unfold(ServerLevel level, Player owner) {
        entityData.set(DATA_DORMANT, false);
        noPhysics = false;
        setNoGravity(flies());
        refreshDimensions();
        Familiars.slipTo(this, owner);
        level.broadcastEntityEvent(this, UNFOLD_EVENT);
        level.sendParticles(com.lealex.alchyforbidden.registry.ModRegistries.RIFT_MOTE.get(), getX(), getY() + getBbHeight() * 0.5, getZ(), 60, 0.8, 1.2, 0.8, 0.15);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, getX(), getY() + getBbHeight() * 0.5, getZ(), 60, 0.8, 1.2, 0.8, 0.2);
        level.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.WARDEN_EMERGE, net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 0.9F);
    }

    // ---- What it is ----

    /** Its name wherever the game names it (Jade, the boss bar, death messages): coloured, its grade after it. */
    @Override
    protected net.minecraft.network.chat.Component getTypeName() {
        return creature().fullName(corruption());
    }

    // ---- A great one's entrance ----

    public static final int INTRO_TICKS = 100, INTRO_TITLE = 58;
    public static final byte INTRO_EVENT = 73;
    private long introUntil;
    private Vec3 introHome = Vec3.ZERO;
    private java.util.@Nullable UUID introViewer;
    /** Everyone shown its entrance (the viewer and, in a void trial, the challenger's companions). */
    private final java.util.Set<java.util.UUID> introWatchers = new java.util.HashSet<>();
    /** Client: ticks since its entrance began (-1: none). */
    public int clientIntroTicks = -1;

    /** Where it is during its entrance, p = 0..1 through it (default: where it came through, still). */
    protected Vec3 introPath(Vec3 home, float p) {
        return home;
    }

    /** Its entrance's sounds and sights, each tick t (0..INTRO_TICKS). */
    protected void introEffects(ServerLevel level, int t, Vec3 home) {}

    /** Ticks into its entrance (server), -1 when none. */
    protected int introTick() {
        return introUntil <= 0 ? -1 : (int) (INTRO_TICKS - (introUntil - level().getGameTime()));
    }

    /**
     * Its entrance, shown to its challenger (client/BossIntro): it stands still and untouchable, facing them, while the
     * view closes in on it and its name comes up; the challenger is held meanwhile. Then the fight begins.
     */
    public void introduce(net.minecraft.server.level.ServerPlayer viewer) {
        introUntil = level().getGameTime() + INTRO_TICKS;
        introHome = position();
        introViewer = viewer.getUUID();
        introWatchers.clear();
        introWatchers.add(viewer.getUUID());
        setNoAi(true);
        setInvulnerable(true);
        if (level() instanceof ServerLevel server) server.broadcastEntityEvent(this, INTRO_EVENT);
        double dx = viewer.getX() - getX(), dz = viewer.getZ() - getZ();
        float yaw = (float) (Math.atan2(dz, dx) * (180 / Math.PI)) - 90F;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        viewer.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, INTRO_TICKS, 9, false, false));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer, new com.lealex.alchyforbidden.network.BossIntroPayload(getId(), INTRO_TICKS));
    }

    /** Another player sees the entrance too (held, the view on it, its name); after it, only its name. */
    public void watchIntro(net.minecraft.server.level.ServerPlayer viewer) {
        if (introUntil <= 0 || !introWatchers.add(viewer.getUUID())) return;
        int left = (int) Math.max(1, introUntil - level().getGameTime());
        viewer.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, left, 9, false, false));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer, new com.lealex.alchyforbidden.network.BossIntroPayload(getId(), left));
    }

    /** Its challenger, or someone fighting beside them in the same void trial. */
    public boolean challenges(Player player) {
        return challenger != null && (player.getUUID().equals(challenger) || VoidTrials.sameParty(challenger, player.getUUID()));
    }

    /** Everyone it fights here: its challenger and their party (an empty list when none is near). */
    private java.util.List<ServerPlayer> challengers(ServerLevel level) {
        java.util.List<ServerPlayer> party = challenger == null ? new java.util.ArrayList<>() : VoidTrials.party(level, challenger);
        if (party.isEmpty() && challenger != null && level.getPlayerByUUID(challenger) instanceof ServerPlayer sp) party.add(sp);
        return party;
    }

    /** Its name comes up once it has arrived: short, so it fits any gui scale. */
    private void introTitle(net.minecraft.server.level.ServerPlayer viewer) {
        viewer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(12, 30, 12));
        viewer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(corruption() > 0
                ? net.minecraft.network.chat.Component.literal(RiftCreature.grade(corruption()).trim()).withStyle(s -> s.withColor(RiftCreature.gradeColor(corruption())))
                : net.minecraft.network.chat.Component.translatable("void_trial.alchyforbidden.great_one").withStyle(net.minecraft.ChatFormatting.GRAY)));
        viewer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                creature().displayName().copy().withStyle(s -> s.withColor(creature().color()))));
    }

    public boolean introducing() {
        return introUntil > 0;
    }

    private void tickIntro(ServerLevel level) {
        int t = introTick();
        if (t >= 0 && t < INTRO_TICKS) {
            introEffects(level, t, introHome);
            if (t == INTRO_TITLE) for (java.util.UUID id : introWatchers) {
                if (level.getPlayerByUUID(id) instanceof net.minecraft.server.level.ServerPlayer viewer) introTitle(viewer);
            }
        }
        if (introUntil > 0 && level.getGameTime() >= introUntil) {
            introUntil = 0;
            setNoAi(false);
            setInvulnerable(false);
            specialReady = Math.max(specialReady, level.getGameTime() + 60); // a breath before its first special
        }
    }

    public int corruption() {
        return entityData.get(DATA_CORRUPTION);
    }

    public int beastLevel() {
        return entityData.get(DATA_LEVEL);
    }

    public boolean familiar() {
        return entityData.get(DATA_FAMILIAR);
    }

    /** Hostile to whoever is near (a stray or an arena creature). */
    public boolean wild() {
        return !familiar();
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public int slot() {
        return slot;
    }

    public @Nullable BlockPos arena() {
        return arena;
    }

    public @Nullable UUID challenger() {
        return challenger;
    }

    public @Nullable Player ownerPlayer() {
        return owner == null ? null : level().getPlayerByUUID(owner);
    }

    /** Bound to this player, from this ring slot, with the ring's grade and level. */
    public void becomeFamiliar(ServerPlayer player, int slot, FamiliarData data) {
        this.owner = player.getUUID();
        this.slot = slot;
        entityData.set(DATA_FAMILIAR, true);
        if (creature().tier() >= 4) { // a great one comes out small and resting
            entityData.set(DATA_DORMANT, true);
            setNoGravity(true);
            noPhysics = true;
        }
        entityData.set(DATA_CORRUPTION, data.corruption());
        entityData.set(DATA_LEVEL, data.level());
        affixes = data.affixes();
        shape = data;
        applyStats(data);
        setHealth(getMaxHealth());
        setCustomName(data.title());
    }

    /** Called by a Rift Maw: it fights this challenger in the arena round that maw. */
    public void becomeArenaFoe(BlockPos maw, UUID challenger, int corruption, java.util.List<String> affixes) {
        this.arena = maw;
        this.challenger = challenger;
        entityData.set(DATA_CORRUPTION, corruption);
        this.affixes = affixes;
        this.shape = FamiliarData.fresh(creature(), corruption, affixes);
        applyStats(shape);
        setHealth(getMaxHealth());
        setPersistenceRequired();
        if (corruption > 0) setCustomName(creature().fullName(corruption));
    }

    /** Client: a picture of it at this grade (the bestiary's frame). */
    public void previewGrade(int corruption) {
        entityData.set(DATA_CORRUPTION, Math.max(0, Math.min(3, corruption)));
    }

    /** A new level (familiar): stats again, health topped up by what it gained. */
    public void levelUp(FamiliarData data) {
        float before = getMaxHealth();
        entityData.set(DATA_LEVEL, data.level());
        applyStats(data);
        heal(getMaxHealth() - before);
    }

    private void applyStats(FamiliarData data) {
        modify(Attributes.MAX_HEALTH, HEALTH, data.healthFactor() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modify(Attributes.ATTACK_DAMAGE, DAMAGE, data.damageFactor() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modify(Attributes.MOVEMENT_SPEED, SPEED, data.speedFactor() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modify(Attributes.ARMOR, ARMOR, data.armorBonus(), AttributeModifier.Operation.ADD_VALUE);
        modify(Attributes.ARMOR_TOUGHNESS, TOUGHNESS, data.toughnessBonus(), AttributeModifier.Operation.ADD_VALUE);
    }

    private void modify(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id, double amount,
                        AttributeModifier.Operation operation) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        if (Math.abs(amount) > 1e-6) instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
    }

    // ---- Moves: learned at the milestones, used in turn in a fight ----

    private final long[] moveReady = new long[4];
    private int rotation;
    /** Client: the move being played (-1 none) and its ticks so far. */
    public int clientMove = -1, clientMoveTicks;

    /** Plays one tick of a move (server). tick 0 is its start; its effect usually lands at move.strike(). */
    protected void performMove(RiftCreature.Move move, int tick, LivingEntity target) {}

    /** It ended (or was cut short): undo anything it set (hidden, held...). */
    protected void endMove(RiftCreature.Move move) {}

    /** The attack damage times this. */
    protected float hitFor(double factor) {
        return (float) (getAttributeValue(Attributes.ATTACK_DAMAGE) * factor);
    }

    /** Whether it should hurt this one in an area move: its target, and monsters, never its own side. */
    protected boolean foe(LivingEntity other) {
        // (what fights it counts too: the challenger's own familiars and pets in an arena)
        return other != this && other.isAlive() && canAttack(other) && (other == getTarget() || other instanceof net.minecraft.world.entity.monster.Enemy
                || (other instanceof Mob mob && mob.getTarget() != null && (mob.getTarget() == this || isAlliedTo(mob.getTarget()))));
    }

    /** Picks the next known move, in turn, that is ready and fits the distance (null: none). */
    private int nextMove(LivingEntity target) {
        var moves = creature().moves();
        if (moves.isEmpty() || !hasLineOfSight(target)) return -1;
        long now = level().getGameTime();
        double d = Math.sqrt(distanceToSqr(target));
        int count = Math.min(moves.size(), moveReady.length);
        for (int k = 0; k < count; k++) {
            int i = (rotation + k) % count;
            RiftCreature.Move move = moves.get(i);
            if (knows(move.level()) && moveAllowed(move) && now >= moveReady[i] && d >= move.minRange() && d <= move.maxRange()) return i;
        }
        return -1;
    }

    private final class UseMoves extends Goal {
        private int index = -1, tick;
        private @Nullable LivingEntity target;

        UseMoves() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = getTarget();
            if (!(familiar() || arena != null) || t == null || !t.isAlive() || petting() || dormant() || shifting() || special != null || level().getGameTime() % 5 != 0) return false;
            index = nextMove(t);
            target = t;
            return index >= 0;
        }

        @Override
        public boolean canContinueToUse() {
            // cut short (endMove undoes it) when its target is gone, or it starts changing phase, is petted or folds small
            return index >= 0 && tick < creature().moves().get(index).length() && target != null && target.isAlive() && !target.isRemoved() && isAlive()
                    && !shifting() && !petting() && !dormant();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            tick = 0;
            moving = true;
            getNavigation().stop();
            if (level() instanceof ServerLevel server) server.broadcastEntityEvent(RiftBeast.this, (byte) (MOVE_EVENT + index));
        }

        @Override
        public void tick() {
            if (target == null) return;
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            performMove(creature().moves().get(index), tick, target);
            tick++;
        }

        @Override
        public void stop() {
            moving = false;
            if (index >= 0) {
                RiftCreature.Move move = creature().moves().get(index);
                if (tick < move.length() && level() instanceof ServerLevel server) server.broadcastEntityEvent(RiftBeast.this, MOVE_STOP); // the model lets go too
                endMove(move);
                moveReady[index] = level().getGameTime() + move.cooldown();
                rotation = index + 1;
            }
            index = -1;
            target = null;
        }
    }

    /**
     * While a special plays (or it changes phase) the walking, melee and ranged goals are held: the special moves it
     * itself. Never at once with UseMoves (a special waits for a move to end, a move for a special).
     */
    private final class HoldForSpecial extends Goal {
        HoldForSpecial() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !moving && (special != null || shifting());
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

    /**
     * A move's blow on one target: the vanilla hit guard reset first (so a strike right after a melee bite still lands),
     * its affixes added when it landed. For moves that hit once; true if it hurt.
     */
    protected boolean hitOnce(ServerLevel level, LivingEntity target, float damage) {
        target.invulnerableTime = 0;
        boolean hurt = target.hurtServer(level, damageSources().mobAttack(this), damage);
        if (hurt) onHitAffixes(level, target);
        return hurt;
    }

    // ---- Goals ----

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new UseMoves());
        goalSelector.addGoal(1, new HoldForSpecial());
        addAttackGoals();
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(4, new FollowOwner());
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return wild() && super.canUse();
            }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F) {
            @Override
            public boolean canUse() { // a familiar at rest watches its owner, not whoever passes
                return super.canUse() && (wild() || lookAt == ownerPlayer());
            }
        });
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnerHurtBy());
        targetSelector.addGoal(2, new OwnerHurts());
        targetSelector.addGoal(3, new HurtByTargetGoal(this));
        targetSelector.addGoal(4, new Challenger());
        targetSelector.addGoal(5, new GuardOwner());
    }

    /** Each creature's own moves, before the plain melee (a leap, a charge...). */
    protected void addAttackGoals() {}

    /** A treat from its owner: eaten (the petting animation, crumbs, munching), experience given. */
    private net.minecraft.world.InteractionResult feed(Player player, net.minecraft.world.item.ItemStack held, com.lealex.alchyforbidden.item.RiftTreatItem treat) {
        if (beastLevel() >= FamiliarData.MAX_LEVEL) {
            if (player instanceof ServerPlayer sp) sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("familiar.alchyforbidden.full_level"), true);
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (level() instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            net.minecraft.world.item.ItemStack eaten = held.copyWithCount(1);
            if (!player.getAbilities().instabuild) held.shrink(1);
            pettedUntil = level().getGameTime() + PET_TICKS;
            server.broadcastEntityEvent(this, PET);
            server.sendParticles(new net.minecraft.core.particles.ItemParticleOption(net.minecraft.core.particles.ParticleTypes.ITEM, eaten.getItem()),
                    getX(), getY() + getBbHeight() * 0.8, getZ(), 14, 0.2, 0.15, 0.2, 0.08);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getBbHeight() + 0.2, getZ(), 6, 0.3, 0.2, 0.3, 0);
            playSound(net.minecraft.sounds.SoundEvents.GENERIC_EAT.value(), 0.8F, 0.8F);
            Familiars.giveXp(serverPlayer, this, treat.xp());
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (familiar()) {
            if (target == ownerPlayer() || isAlliedTo(target)) return false;
            if (target instanceof Player && !com.lealex.alchyforbidden.Config.spellHitsPlayers()) return false;
        } else if (arena != null && challenger != null && target instanceof Player player && !challenges(player)) {
            return false; // an arena foe fights its challenger (and their party in the void) only
        }
        return super.canAttack(target);
    }

    @Override
    protected boolean considersEntityAsAlly(Entity other) {
        if (familiar() && owner != null) {
            if (other.getUUID().equals(owner)) return true;
            if (other instanceof RiftBeast beast && owner.equals(beast.owner)) return true;
            if (other instanceof net.minecraft.world.entity.OwnableEntity pet && pet.getOwnerReference() != null
                    && owner.equals(pet.getOwnerReference().getUUID())) return true;
        }
        return super.considersEntityAsAlly(other);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, Entity target) {
        boolean hit = super.doHurtTarget(level, target);
        if (hit) {
            level.broadcastEntityEvent(this, SWING);
            if (target instanceof LivingEntity living) onHitAffixes(level, living);
        }
        return hit;
    }

    /** What its affixes add to a blow that landed (moves call it too). */
    protected void onHitAffixes(ServerLevel level, LivingEntity target) {
        float struck = hitFor(1.0);
        if (has(RiftAffix.VAMPIRIC)) {
            heal(struck * 0.25F);
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(0xB01020, 1.0F), getX(), getY() + getBbHeight() * 0.7, getZ(), 5, 0.3, 0.3, 0.3, 0);
        }
        if (has(RiftAffix.VOIDTOUCHED) && target.isAlive()) { // a part that no armour stops
            target.setHealth(Math.max(0.5F, target.getHealth() - struck * 0.25F));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 8, 0.2, 0.3, 0.2, 0.05);
        }
        if (has(RiftAffix.CHILLING)) {
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 60, 1), this);
            target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 60, target.getTicksFrozen() + 60));
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        }
        if (has(RiftAffix.BURNING)) target.igniteForTicks(80);
        if (has(RiftAffix.WITHERING)) target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER, 80, 0), this);
    }

    /** Hungering: each kill while it is out makes it stronger, up to five times. */
    public void fedOnKill() {
        if (!has(RiftAffix.HUNGERING) || hunger >= 5) return;
        hunger++;
        modify(Attributes.ATTACK_DAMAGE, HUNGER, 0.1 * hunger, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (level() instanceof ServerLevel server) server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(0xB01020, 1.4F),
                getX(), getY() + getBbHeight(), getZ(), 10, 0.3, 0.2, 0.3, 0);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        // untouchable while it changes phase or makes its entrance (checked first, so no ward or grave is spent on it)
        if (shifting() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (isInvulnerableTo(level, source)) return false;
        if (has(RiftAffix.WARDED) && wardUp && amount >= 4) { // the ward takes the first heavy blow
            wardUp = false;
            wardBack = level.getGameTime() + 600;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT, getX(), getY() + getBbHeight() * 0.5, getZ(), 20, 0.5, 0.5, 0.5, 0.2);
            playSound(net.minecraft.sounds.SoundEvents.SHIELD_BLOCK.value(), 1.0F, 0.6F);
            return false;
        }
        boolean lethal = amount >= getHealth() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (lethal && has(RiftAffix.GRAVE_BOUND) && !graveUsed) { // it refuses to die, once a call
            graveUsed = true;
            setHealth(getMaxHealth() * 0.3F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, getX(), getY() + getBbHeight() * 0.5, getZ(), 24, 0.4, 0.5, 0.4, 0.05);
            playSound(net.minecraft.sounds.SoundEvents.TOTEM_USE, 0.7F, 0.6F);
            return false;
        }
        boolean hurt = super.hurtServer(level, source, amount);
        if (!hurt || !isAlive()) return hurt;
        Entity attacker = source.getEntity();
        if (has(RiftAffix.THORNED) && attacker instanceof LivingEntity living && source.getDirectEntity() == attacker && !source.is(net.minecraft.world.damagesource.DamageTypes.THORNS)) {
            living.hurtServer(level, damageSources().thorns(this), amount * 0.25F);
        }
        if (has(RiftAffix.BLINKING) && level.getGameTime() >= blinkReady && random.nextInt(4) == 0) blink(level, attacker);
        if (has(RiftAffix.SPLITTING) && !splitUsed && getHealth() < getMaxHealth() * 0.6F) {
            splitUsed = true;
            splitEcho(level);
        }
        return hurt;
    }

    // ---- Bolts: what the ranged ones throw (travelling particles, hits checked each tick, never an entity) ----

    private static final java.util.concurrent.atomic.AtomicInteger BOLT_IDS = new java.util.concurrent.atomic.AtomicInteger();

    private static final class Bolt {
        final int id = BOLT_IDS.incrementAndGet();
        Vec3 pos, vel;
        int life, pierce, color, fire, age;
        float damage;
        @Nullable UUID homing;        // turns toward this one each tick
        boolean boomerang;            // halfway through its life it comes back to the thrower
        final java.util.Set<UUID> hit = new java.util.HashSet<>();
    }

    /** A bolt that follows its target (homing), or comes back like a boomerang, or sets alight (fire seconds). */
    protected void fireBolt(Vec3 from, Vec3 direction, float damage, int color, int pierce, double speed, @Nullable LivingEntity homing, boolean boomerang, int fire) {
        Bolt bolt = makeBolt(from, direction, damage, color, pierce, speed);
        bolt.homing = homing == null ? null : homing.getUUID();
        bolt.boomerang = boomerang;
        bolt.fire = fire;
        if (boomerang) bolt.life = 40;
        else if (homing != null) bolt.life = 140; // a hunting bolt lasts until it reaches its prey
        launch(bolt, homing);
    }

    /** What its bolts look like (client/AttackFx draws them as shapes): by colour, fire and whether they come back. */
    protected int boltStyle(int color, int fire, boolean boomerang) {
        return AttackFxPayload.ORB;
    }

    /** How big its bolts are drawn. */
    protected float boltSize() {
        return 1F;
    }

    private void launch(Bolt bolt, @Nullable LivingEntity homing) {
        bolts.add(bolt);
        if (level() instanceof ServerLevel) {
            new AttackFxPayload(AttackFxPayload.BOLT, bolt.id, bolt.pos.x, bolt.pos.y, bolt.pos.z, (float) bolt.vel.x, (float) bolt.vel.y, (float) bolt.vel.z,
                    bolt.life, 0, boltSize(), 0, bolt.boomerang ? 1 : 0, 0, bolt.color, boltStyle(bolt.color, bolt.fire, bolt.boomerang),
                    homing == null ? -1 : homing.getId(), getId()).send(this);
        }
    }

    private void endBolt(Bolt bolt, Vec3 at, boolean struck) {
        new AttackFxPayload(AttackFxPayload.BOLT_END, bolt.id, at.x, at.y, at.z, 0, 0, 0, 7, 0, boltSize(), 0, struck ? 1 : 0, 0, bolt.color, 0, -1, getId()).send(this);
    }

    // ---- Attack visuals (client/AttackFx): shapes, not dust ----

    /** A ray from there along dir: sweep = AttackFxPayload.SWEEP_SINE (turns by amp, less offset, and back) or SWEEP_TURN (amp radians in all). */
    protected void fxBeam(Vec3 from, Vec3 dir, float length, float width, int life, int color, int style, float amp, float offset) {
        new AttackFxPayload(AttackFxPayload.BEAM, 0, from.x, from.y, from.z, (float) dir.x, (float) dir.y, (float) dir.z, life, 0, width, length, amp, offset,
                color, style, -1, getId()).send(this);
    }

    /** A column of light: a faint warning for delay ticks, then the strike (life counts from the warning). */
    protected void fxColumn(Vec3 at, float radius, float height, int delay, int life, int color) {
        fxColumn(at, radius, height, delay, life, color, null, 0);
    }

    /** A column of light whose warning creeps after someone (rate: how much of the way each tick) until it strikes. */
    protected void fxColumn(Vec3 at, float radius, float height, int delay, int life, int color, @Nullable Entity follows, float rate) {
        new AttackFxPayload(AttackFxPayload.COLUMN, 0, at.x, at.y, at.z, 0, 0, 0, life, delay, radius, height, 0, rate, color, 0,
                follows == null ? -1 : follows.getId(), getId()).send(this);
    }

    /** Spikes rising along the floor from there along dir (speed blocks a tick). */
    protected void fxLine(Vec3 from, Vec3 dir, float length, float speed, int life, int style) {
        new AttackFxPayload(AttackFxPayload.LINE, 0, from.x, from.y, from.z, (float) dir.x, 0, (float) dir.z, life, 0, speed, length, 0, 0, 0, style, -1, getId()).send(this);
    }

    /** A blast going out to radius over life ticks. */
    protected void fxNova(Vec3 at, float radius, int life, int color) {
        new AttackFxPayload(AttackFxPayload.NOVA, 0, at.x, at.y, at.z, 0, 0, 0, life, 0, radius, 0, 0, 0, color, 0, -1, getId()).send(this);
    }

    /** A burning ring closing in from radius from to radius to (speed blocks a tick). */
    protected void fxEdge(Vec3 center, float from, float to, float speed, int life, int color) {
        new AttackFxPayload(AttackFxPayload.EDGE, 0, center.x, center.y, center.z, 0, 0, 0, life, 0, speed, from, to, 0, color, 0, -1, getId()).send(this);
    }

    /** A patch of floor drawn there (no harm of its own: floorZone hurts). */
    protected void fxZone(Vec3 at, float radius, int life, int color, int style) {
        fxZone(at, radius, life, color, style, null);
    }

    /** A patch of floor that creeps after someone (a tenth of the way each tick, on the floor's height where it began). */
    protected void fxZone(Vec3 at, float radius, int life, int color, int style, @Nullable Entity follows) {
        new AttackFxPayload(AttackFxPayload.ZONE, BOLT_IDS.incrementAndGet(), at.x, at.y, at.z, 0, 0, 0, life, 0, radius, 0, 0, 0, color, style,
                follows == null ? -1 : follows.getId(), getId()).send(this);
    }

    // ---- Arena tools for the great ones: rings to jump, burning floor, blocks that stand a while ----

    private static final class Ring {
        Vec3 center;
        double radius, speed, max;
        float damage;
        int color;
        final java.util.Set<UUID> hit = new java.util.HashSet<>();
    }

    private static final class Zone {
        Vec3 center;
        double radius;
        long until;
        int color;
        float damage;
        boolean burn, slow;
    }

    private final java.util.List<Ring> rings = new java.util.ArrayList<>();
    private final java.util.List<Zone> zones = new java.util.ArrayList<>();
    /** Each temp block and the block it put there (only that block is taken back: what a player set there instead stays). */
    private final java.util.Map<BlockPos, net.minecraft.world.level.block.Block> tempBlocks = new java.util.LinkedHashMap<>();
    private long tempBlocksUntil;
    /** The creatures holding temp blocks now (server), so a stopping server can take them all back before the world is saved. */
    private static final java.util.Set<RiftBeast> HOLDING = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /** Takes back every temp block of every creature (server stopping). */
    public static void clearAllTempBlocks() {
        for (RiftBeast beast : new java.util.ArrayList<>(HOLDING)) beast.clearTempBlocks();
        HOLDING.clear();
    }

    /** Minions and echoes it called (they go with it). */
    private final java.util.List<UUID> called = new java.util.ArrayList<>();

    /** A ring along the floor going out from there: whoever is on the ground as it passes is hit (jump it). */
    protected void groundRing(Vec3 center, double speed, double max, float damage, int color) {
        Ring ring = new Ring();
        ring.center = center;
        ring.radius = 0.5;
        ring.speed = speed;
        ring.max = max;
        ring.damage = damage;
        ring.color = color;
        rings.add(ring);
        if (level() instanceof ServerLevel) {
            new AttackFxPayload(AttackFxPayload.RING, 0, center.x, center.y, center.z, 0, 0, 0, (int) Math.ceil((max - 0.5) / speed) + 4, 0, (float) speed,
                    (float) max, 0, 0, color, 0, -1, getId()).send(this);
        }
    }

    /** A patch of floor that hurts (burning and / or slowing) for so many ticks. */
    protected void floorZone(Vec3 center, double radius, int ticks, float damage, int color, boolean burn, boolean slow) {
        Zone zone = new Zone();
        zone.center = center;
        zone.radius = radius;
        zone.until = level().getGameTime() + ticks;
        zone.color = color;
        zone.damage = damage;
        zone.burn = burn;
        zone.slow = slow;
        zones.add(zone);
        if (level() instanceof ServerLevel) {
            fxZone(center, (float) radius, ticks, color, burn && slow ? AttackFxPayload.Z_WAX : burn ? AttackFxPayload.Z_FIRE : AttackFxPayload.Z_SHADOW);
        }
    }

    /** Places a block that stands for a while (only in an arena, never as a familiar); taken back with the creature. */
    protected boolean tempBlock(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, int ticks) {
        if (arena == null || familiar() || !level.getBlockState(pos).isAir()) return false;
        level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
        tempBlocks.put(pos.immutable(), state.getBlock());
        HOLDING.add(this);
        tempBlocksUntil = Math.max(tempBlocksUntil, level.getGameTime() + ticks);
        return true;
    }

    protected void clearTempBlocks() {
        HOLDING.remove(this);
        if (level() instanceof ServerLevel level) {
            for (var entry : tempBlocks.entrySet()) {
                BlockPos pos = entry.getKey();
                var state = level.getBlockState(pos);
                if (state.is(entry.getValue())) {
                    level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, state),
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.05);
                    level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                }
            }
        }
        tempBlocks.clear();
    }

    private void tickArenaTools(ServerLevel level) {
        // over copies: a hit may make it lay more (a trait answering) while the lists are walked
        for (Ring ring : new java.util.ArrayList<>(rings)) {
            ring.radius += ring.speed;
            for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(ring.center, ring.center).inflate(ring.radius + 1, 2, ring.radius + 1), this::foe)) {
                double dx = near.getX() - ring.center.x, dz = near.getZ() - ring.center.z;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (Math.abs(d - ring.radius) > 0.9 || !near.onGround() || near.getY() > ring.center.y + 1 || !ring.hit.add(near.getUUID())) continue;
                near.invulnerableTime = 0;
                near.hurtServer(level, damageSources().indirectMagic(this, this), ring.damage);
                near.setDeltaMovement(near.getDeltaMovement().add(dx / Math.max(d, 0.1) * 0.6, 0.4, dz / Math.max(d, 0.1) * 0.6));
                near.hurtMarked = true;
            }
            if (ring.radius >= ring.max) rings.remove(ring);
        }
        long now = level.getGameTime();
        for (Zone zone : new java.util.ArrayList<>(zones)) {
            if (now >= zone.until) {
                zones.remove(zone);
                continue;
            }
            if (now % 10 != 0) continue;
            for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(zone.center, zone.center).inflate(zone.radius, 1.5, zone.radius), this::foe)) {
                double dx = near.getX() - zone.center.x, dz = near.getZ() - zone.center.z;
                if (dx * dx + dz * dz > zone.radius * zone.radius) continue;
                if (zone.damage > 0) {
                    near.invulnerableTime = 0;
                    near.hurtServer(level, damageSources().indirectMagic(this, this), zone.damage);
                }
                if (zone.burn) near.igniteForSeconds(3);
                if (zone.slow) near.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 30, 2), this);
            }
        }
        if (!tempBlocks.isEmpty() && now >= tempBlocksUntil) clearTempBlocks();
    }

    private final java.util.List<Bolt> bolts = new java.util.ArrayList<>();

    /** Throws a bolt from there at the target's middle (speed in blocks a tick, pierce = how many more it goes through). */
    protected void fireBolt(Vec3 from, LivingEntity target, float damage, int color, int pierce, double speed) {
        fireBolt(from, target.position().add(0, target.getBbHeight() * 0.55, 0).subtract(from), damage, color, pierce, speed);
    }

    /** Throws a bolt from there along a direction. */
    protected void fireBolt(Vec3 from, Vec3 direction, float damage, int color, int pierce, double speed) {
        launch(makeBolt(from, direction, damage, color, pierce, speed), null);
    }

    private Bolt makeBolt(Vec3 from, Vec3 direction, float damage, int color, int pierce, double speed) {
        Bolt bolt = new Bolt();
        bolt.pos = from;
        bolt.vel = direction.normalize().scale(speed);
        bolt.life = 60;
        bolt.damage = damage;
        bolt.color = color;
        bolt.pierce = pierce;
        return bolt;
    }

    /** A hunting bolt's next velocity: it turns harder and flies faster the longer it hunts, so it cannot circle its prey
     *  for ever (client/AttackFx runs the same rule to draw it where the server has it). */
    public static Vec3 homeIn(Vec3 vel, Vec3 pos, Vec3 aim, int age) {
        double speed = Math.min(1.8, Math.max(vel.length(), 0.7) + 0.03);
        double turn = Math.min(0.45, 0.14 + age * 0.012);
        Vec3 want = aim.subtract(pos).normalize().scale(speed);
        return vel.normalize().scale(speed).lerp(want, turn);
    }

    /** A bolt of its hit this one (after the damage): a trait may answer. */
    protected void onBoltHit(ServerLevel level, LivingEntity target, float damage) {}

    private void tickBolts(ServerLevel level) {
        // over a copy: a hit may make it throw more (a trait, a death rattle) while the list is walked
        for (Bolt bolt : new java.util.ArrayList<>(bolts)) {
            if (isRemoved() || !bolts.contains(bolt)) continue; // ended meanwhile
            bolt.age++;
            if (bolt.homing != null && level.getEntity(bolt.homing) instanceof LivingEntity prey && prey.isAlive()) {
                bolt.vel = homeIn(bolt.vel, bolt.pos, prey.position().add(0, prey.getBbHeight() * 0.55, 0), bolt.age);
            }
            if (bolt.boomerang && bolt.age > 18) { // back to its thrower
                Vec3 home = middle().subtract(bolt.pos);
                if (home.lengthSqr() < 1.5) {
                    endBolt(bolt, bolt.pos, false);
                    bolts.remove(bolt);
                    continue;
                }
                bolt.vel = home.normalize().scale(bolt.vel.length());
                if (bolt.age == 19) bolt.hit.clear(); // it may cut the same ones again on the way back
            }
            Vec3 next = bolt.pos.add(bolt.vel);
            var block = level.clip(new net.minecraft.world.level.ClipContext(bolt.pos, next, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, this));
            boolean wall = block.getType() != net.minecraft.world.phys.HitResult.Type.MISS;
            Vec3 end = wall ? block.getLocation() : next;
            boolean struckAny = false;
            net.minecraft.world.phys.AABB sweep = new net.minecraft.world.phys.AABB(bolt.pos, end).inflate(0.4);
            for (LivingEntity struck : level.getEntitiesOfClass(LivingEntity.class, sweep, e -> e != this && !bolt.hit.contains(e.getUUID()) && foe(e))) {
                bolt.hit.add(struck.getUUID());
                struck.invulnerableTime = 0; // bolts land one after the other
                struck.hurtServer(level, damageSources().indirectMagic(this, this), bolt.damage);
                if (bolt.fire > 0) struck.igniteForSeconds(bolt.fire);
                onHitAffixes(level, struck);
                onBoltHit(level, struck, bolt.damage);
                struckAny = true;
                if (bolt.pierce-- <= 0) {
                    bolt.life = 0;
                    break;
                }
            }
            bolt.pos = end;
            if (wall || --bolt.life <= 0) {
                endBolt(bolt, end, wall || struckAny || bolt.homing != null); // a hunting bolt burns out with a flash, not by vanishing
                bolts.remove(bolt);
            }
        }
    }

    /** Every bolt still flying ends where it is (it is going: clients would draw them on to the end of their life). */
    private void endAllBolts() {
        if (!(level() instanceof ServerLevel)) return;
        for (Bolt bolt : bolts) endBolt(bolt, bolt.pos, false);
        bolts.clear();
    }

    /** Blinking: through a small rift to beside whoever struck it. */
    private void blink(ServerLevel level, @Nullable Entity attacker) {
        blinkReady = level.getGameTime() + 60;
        Vec3 around = attacker != null ? attacker.position() : position();
        for (int i = 0; i < 8; i++) {
            double a = random.nextDouble() * Math.PI * 2, r = 2 + random.nextDouble() * 2;
            Vec3 to = around.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            Vec3 from = position();
            if (randomTeleport(to.x, to.y, to.z, false)) {
                Familiars.riftPuff(level, from.add(0, getBbHeight() * 0.5, 0), creature().color(), false);
                Familiars.riftPuff(level, middle(), creature().color(), true);
                return;
            }
        }
    }

    /** Splitting: an echo of it fights beside it for ten seconds. */
    private void splitEcho(ServerLevel level) {
        spawnEcho(level, 200);
    }

    /**
     * A lesser creature of the rift born at its side for so many ticks (the Rift Mother's brood): on its side, never saved,
     * fading like an echo, at the creature's plain strength.
     */
    protected @Nullable RiftBeast spawnMinion(ServerLevel level, RiftCreature kind, Vec3 at, int ticks) {
        RiftBeast minion = kind.type().create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (minion == null) return null;
        minion.snapTo(at.x, at.y, at.z, random.nextFloat() * 360, 0);
        minion.owner = owner;
        minion.slot = -1;
        minion.challenger = challenger;
        minion.arena = arena; // an arena foe's brood fights its challenger only (echo-flagged: no specials, phases or bar)
        minion.entityData.set(DATA_FAMILIAR, familiar());
        minion.entityData.set(DATA_ECHO, true);
        minion.echoTicks = ticks;
        minion.setTarget(getTarget());
        minion.setCustomName(net.minecraft.network.chat.Component.translatable("familiar.alchyforbidden.brood_of", kind.displayName()));
        if (!level.addFreshEntity(minion)) return null;
        called.removeIf(id -> !(level.getEntity(id) instanceof Entity e) || !e.isAlive());
        called.add(minion.getUUID());
        Familiars.riftPuff(level, minion.middle(), kind.color(), true);
        return minion;
    }

    /** An echo of it fights beside it for so many ticks (Splitting, a boss's own call). */
    protected void spawnEcho(ServerLevel level, int ticks) {
        RiftBeast copy = (RiftBeast) getType().create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (copy == null) return;
        copy.snapTo(getX() + random.nextDouble() - 0.5, getY(), getZ() + random.nextDouble() - 0.5, getYRot(), 0);
        copy.owner = owner;
        copy.slot = -1;
        copy.challenger = challenger;
        copy.entityData.set(DATA_FAMILIAR, familiar());
        copy.entityData.set(DATA_ECHO, true);
        copy.entityData.set(DATA_CORRUPTION, corruption());
        copy.entityData.set(DATA_LEVEL, beastLevel());
        if (shape != null) copy.applyStats(shape);
        copy.setHealth(copy.getMaxHealth() * 0.5F);
        copy.echoTicks = ticks;
        copy.setTarget(getTarget());
        copy.setCustomName(net.minecraft.network.chat.Component.translatable("familiar.alchyforbidden.echo_of", getDisplayName()));
        if (level.addFreshEntity(copy)) {
            called.removeIf(id -> !(level.getEntity(id) instanceof Entity e) || !e.isAlive());
            called.add(copy.getUUID());
            Familiars.riftPuff(level, copy.middle(), creature().color(), true);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == SWING) swingTicks = swingLength();
        else if (id == PHASE_EVENT) clientPhaseTicks = 0;
        else if (id == INTRO_EVENT) clientIntroTicks = 0;
        else if (id == UNFOLD_EVENT) dormantAnim = dormantAnimO = 1;
        else if (id == PET) petTicks = PET_TICKS;
        else if (id >= MOVE_EVENT && id < MOVE_EVENT + 4) {
            clientMove = id - MOVE_EVENT;
            clientMoveTicks = 0;
        }
        else if (id == MOVE_STOP) clientMove = -1;
        else super.handleEntityEvent(id);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (swingTicks > 0) swingTicks--;
        if (petTicks > 0) petTicks--;
        if (clientMove >= 0 && ++clientMoveTicks >= creature().moves().get(clientMove).length()) clientMove = -1;
        if (level() instanceof ServerLevel introLevel) tickIntro(introLevel);
        if (level().isClientSide()) {
            clientPhaseTicks++;
            clientSpecialTicks++;
            if (clientIntroTicks >= 0 && ++clientIntroTicks > INTRO_TICKS) clientIntroTicks = -1;
            dormantAnimO = dormantAnim;
            dormantAnim = dormant() ? Math.min(1F, dormantAnim + 0.08F) : Math.max(0F, dormantAnim - 0.08F);
        }
        if (level().isClientSide()) gradeVisuals();
        if (level() instanceof ServerLevel server) {
            if (echo()) {
                if (--echoTicks <= 0 || (familiar() && ownerPlayer() == null)) {
                    Familiars.riftPuff(server, middle(), creature().color(), false);
                    discard();
                    return;
                }
            } else if (familiar()) {
                Familiars.tickFamiliar(server, this);
            }
            if (has(RiftAffix.FRENZIED)) {
                boolean low = getHealth() < getMaxHealth() * 0.5F;
                AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null && speed.hasModifier(FRENZY_SPEED) != low) { // only when it crosses half health (each change is a packet)
                    modify(Attributes.MOVEMENT_SPEED, FRENZY_SPEED, low ? 0.25 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                    modify(Attributes.ATTACK_DAMAGE, FRENZY_DAMAGE, low ? 0.25 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                }
                if (low && tickCount % 10 == 0) server.sendParticles(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER, getX(), getY() + getBbHeight() + 0.2, getZ(), 1, 0.2, 0.1, 0.2, 0);
            }
            if (!wardUp && server.getGameTime() >= wardBack) wardUp = true;
            if (!echo()) {
                tickPhase(server);
                followTeleport();
                tickDormant(server);
                if (arena != null && !familiar()) tickSpecial(server);
            }
            if (shifting()) { // held while it changes
                getNavigation().stop();
                setDeltaMovement(0, Math.min(0, getDeltaMovement().y) * (flies() ? 0 : 1), 0);
            }
            if (!bolts.isEmpty()) tickBolts(server);
            if (!rings.isEmpty() || !zones.isEmpty() || !tempBlocks.isEmpty()) tickArenaTools(server);
            if (arena != null && (corruption() >= 3 || creature().boss()) && !echo()) voidFoe(server);
            if (petting()) { // held still in the owner's hand
                getNavigation().stop();
                setDeltaMovement(0, getDeltaMovement().y, 0);
                Player owner = ownerPlayer();
                if (owner != null) getLookControl().setLookAt(owner.getX(), owner.getEyeY(), owner.getZ());
                if (level().getGameTime() % 6 == 0) {
                    playSound(net.minecraft.sounds.SoundEvents.SCULK_CLICKING, 0.5F, 1.5F + random.nextFloat() * 0.3F);
                    server.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL, getX(), getY() + getBbHeight() + 0.2, getZ(), 1, 0.2, 0.1, 0.2, 0.01);
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        int it = level().isClientSide() ? -1 : introTick();
        if (it >= 0 && !isRemoved()) { // its entrance: where the path puts it, facing its challenger
            Vec3 at = introPath(introHome, Math.min(1F, it / (float) INTRO_TICKS));
            setPos(at.x, at.y, at.z);
            setDeltaMovement(Vec3.ZERO);
            if (introViewer != null && level().getPlayerByUUID(introViewer) instanceof Player viewer) {
                float yaw = (float) (Math.atan2(viewer.getZ() - getZ(), viewer.getX() - getX()) * (180 / Math.PI)) - 90F;
                setYRot(yaw);
                setYBodyRot(yaw);
                setYHeadRot(yaw);
            }
        }
        // held while it changes phase, after its own aiStep too (a hovering great one's lift would carry it on)
        if (!level().isClientSide() && shifting() && !isRemoved()) {
            getNavigation().stop();
            setDeltaMovement(0, Math.min(0, getDeltaMovement().y) * (flies() ? 0 : 1), 0);
        }
    }

    /** A name tag on a familiar: only its owner may name it, and the name goes into its ring. */
    @Override
    public net.minecraft.world.InteractionResult interact(Player player, net.minecraft.world.InteractionHand hand, Vec3 location) {
        net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
        if (familiar() && held.is(net.minecraft.world.item.Items.NAME_TAG) && held.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            if (owner == null || !owner.equals(player.getUUID())) return net.minecraft.world.InteractionResult.FAIL;
            if (player instanceof ServerPlayer serverPlayer) {
                Familiars.rename(serverPlayer, this, held.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME).getString());
                if (!player.getAbilities().instabuild) held.shrink(1);
            }
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return super.interact(player, hand, location);
    }

    /**
     * A familiar that has reached this level knows its trait and move there. An arena foe knows as much as its grade
     * gives it (Rifted level 5, Deep-Rifted 10, Void-Rifted 15); a stray or an echo nothing.
     */
    public boolean knows(int level) {
        if (echo()) return false;
        if (familiar()) return beastLevel() >= level;
        if (arena == null) return false;
        if (creature().tier() >= 4) return true; // the void's great ones fight with everything they have
        return (creature().boss() ? 10 + corruption() * 5 : corruption() * 5) >= level; // a boss knows more from the start
    }

    public boolean has(RiftAffix affix) {
        return !echo() && affixes.contains(affix.id());
    }

    public java.util.List<String> affixes() {
        return affixes;
    }

    /** A copy of it the Splitting affix tore out of the rift for a moment. */
    public boolean echo() {
        return entityData.get(DATA_ECHO);
    }

    /** It struck the last blow on this one (familiars only; a trait may answer). */
    public void onKill(ServerLevel level, LivingEntity dead) {}

    /** Being petted by its owner (server). */
    public boolean petting() {
        return level().getGameTime() < pettedUntil;
    }

    /**
     * Every summon can be petted: its owner, empty-handed, right-clicks it. It stops, turns to them and plays its own
     * petting animation (each model's, from {@link #petTicks}); a little health comes back. Not in a fight.
     */
    @Override
    protected net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        if (!familiar() || owner == null || !owner.equals(player.getUUID()) || hand != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return super.mobInteract(player, hand);
        }
        net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof com.lealex.alchyforbidden.item.RiftTreatItem treat) return feed(player, held, treat);
        if (!held.isEmpty()) return super.mobInteract(player, hand);
        if (getTarget() != null && getTarget().isAlive()) return net.minecraft.world.InteractionResult.PASS;
        if (level() instanceof ServerLevel server && !petting()) {
            pettedUntil = level().getGameTime() + PET_TICKS;
            server.broadcastEntityEvent(this, PET);
            heal(2.0F);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, getX(), getY() + getBbHeight() + 0.3, getZ(), 3, 0.3, 0.1, 0.3, 0);
            server.sendParticles(com.lealex.alchyforbidden.registry.ModRegistries.RIFT_MOTE.get(), getX(), getY() + getBbHeight() * 0.6, getZ(), 10, 0.3, 0.2, 0.3, 0.02);
            playSound(net.minecraft.sounds.SoundEvents.SCULK_CATALYST_BLOOM, 0.6F, 1.4F);
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    /** A Void-Rifted arena foe: a boss bar for its challenger, and below half its health rifts open round them and spit. */
    private void voidFoe(ServerLevel server) {
        java.util.List<ServerPlayer> party = challengers(server);
        Player foe = getTarget() instanceof Player hunted && hunted.isAlive() && challenges(hunted) ? hunted
                : challenger == null ? null : server.getPlayerByUUID(challenger);
        if (bossBar == null) {
            bossBar = new net.minecraft.server.level.ServerBossEvent(getUUID(), getDisplayName(), net.minecraft.world.BossEvent.BossBarColor.PURPLE,
                    net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_10);
            bossBar.setDarkenScreen(true);
        }
        for (ServerPlayer sp : party) bossBar.addPlayer(sp); // the whole party sees it (adding one already there does nothing)
        for (ServerPlayer sp : new java.util.ArrayList<>(bossBar.getPlayers())) if (!party.contains(sp)) bossBar.removePlayer(sp); // cast out: no bar at home
        bossBar.setProgress(getHealth() / getMaxHealth());
        bossBar.setColor(phase() >= 3 ? net.minecraft.world.BossEvent.BossBarColor.WHITE : phase() == 2 ? net.minecraft.world.BossEvent.BossBarColor.RED
                : net.minecraft.world.BossEvent.BossBarColor.PURPLE);
        if (foe == null || corruption() < 3 || getHealth() > getMaxHealth() * 0.5F) return; // the rift volleys: Void-Rifted only
        long now = server.getGameTime();
        if (riftVolley < 0 || now >= riftVolley + 80) { // the rifts open
            riftVolley = now;
            volleyFrom.clear();
            for (int i = 0; i < 3; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                Vec3 at = foe.position().add(Math.cos(a) * 3.5, 1.5 + random.nextDouble(), Math.sin(a) * 3.5);
                volleyFrom.add(at);
                com.lealex.alchyforbidden.network.SpellFxPayload.send(server, com.lealex.alchyforbidden.network.SpellFxPayload.RIFT, at, -1, 24, 0.9F);
            }
            server.playSound(null, foe.blockPosition(), net.minecraft.sounds.SoundEvents.ILLUSIONER_PREPARE_MIRROR, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.6F);
        } else if (now == riftVolley + 16) { // and spit at them
            for (Vec3 from : volleyFrom) {
                Vec3 to = foe.position().add(0, 1, 0);
                for (int k = 0; k <= 8; k++) {
                    Vec3 p = from.lerp(to, k / 8.0);
                    server.sendParticles(com.lealex.alchyforbidden.registry.ModRegistries.RIFT_MOTE.get(), p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
                }
                if (foe.distanceToSqr(from) < 100) {
                    foe.invulnerableTime = 0; // three rifts spit together: each one lands
                    foe.hurtServer(server, damageSources().indirectMagic(this, this), 2.5F);
                    foe.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 40, 0), this);
                }
            }
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (bossBar != null) bossBar.removeAllPlayers();
        clearTempBlocks();
        endAllBolts();
        if (level() instanceof ServerLevel server) { // its brood and echoes go with it (a fight won, lost or ended)
            for (UUID id : called) if (server.getEntity(id) instanceof Entity e && e.isAlive()) e.discard();
        }
        called.clear();
        super.remove(reason);
    }

    /** The look of its grade (client): Rifted motes at the eyes, Deep-Rifted an aura and a trail, Void-Rifted flickering tears. */
    private void gradeVisuals() {
        int c = corruption();
        if (echo()) {
            if (random.nextInt(2) == 0) level().addParticle(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0, 0);
            return;
        }
        if (c <= 0) return;
        if (random.nextInt(c >= 2 ? 6 : 12) == 0) {
            level().addParticle(com.lealex.alchyforbidden.registry.ModRegistries.RIFT_MOTE.get(), getX() + getLookAngle().x * 0.6, getEyeY(), getZ() + getLookAngle().z * 0.6, 0, 0.02, 0);
        }
        boolean moving = getDeltaMovement().horizontalDistanceSqr() > 0.002;
        if (c >= 2) {
            if (tickCount % 3 == 0) {
                double a = tickCount * 0.3, r = getBbWidth() * 0.9;
                level().addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL, getX() + Math.cos(a) * r, getY() + 0.3, getZ() + Math.sin(a) * r, 0, 0.1, 0);
            }
            if (moving && random.nextInt(2) == 0) level().addParticle(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.1, getZ(), 0, 0, 0);
        }
        if (c >= 3) {
            if (random.nextInt(3) == 0) level().addParticle(new net.minecraft.core.particles.DustParticleOptions(0x1A0A2E, 1.6F), getRandomX(0.7), getRandomY(), getRandomZ(0.7), 0, 0, 0);
            if (random.nextInt(20) == 0) level().addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, getRandomX(0.6), getRandomY(), getRandomZ(0.6), 0, 0.01, 0);
            if (moving && tickCount % 12 == 0) { // a small tear where it steps
                for (int i = 0; i < 6; i++) level().addParticle(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, getX(), getY() + 0.05, getZ(),
                        (random.nextDouble() - 0.5) * 0.2, 0.05, (random.nextDouble() - 0.5) * 0.2);
            }
        }
    }

    /** The voice drops with the grade; a Void-Rifted one warbles. */
    protected float gradePitch(float base) {
        int c = corruption();
        float pitch = base * (1F - 0.1F * c);
        return c >= 3 ? pitch * (0.8F + random.nextFloat() * 0.5F) : pitch;
    }

    // ---- Death, saving ----

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (arena != null && level() instanceof ServerLevel) { // slain in its arena: what it threw is gone with it
            for (Bolt bolt : new java.util.ArrayList<>(bolts)) endBolt(bolt, bolt.pos, false);
            bolts.clear();
            rings.clear();
            zones.clear();
        }
        if (echo()) return;
        if (familiar() && level() instanceof ServerLevel) Familiars.fell(this);
        if (arena != null && level() instanceof ServerLevel server) {
            if (server.getBlockEntity(arena) instanceof com.lealex.alchyforbidden.block.entity.RiftMawBlockEntity maw) maw.foeSlain(this);
            else VoidTrials.foeSlain(this); // a great one fought in the void
        }
    }

    @Override
    protected boolean shouldDropLoot(ServerLevel level) {
        return wild() && arena == null;
    }

    @Override
    protected int getBaseExperienceReward(ServerLevel level) {
        return wild() && arena == null ? 5 + 5 * creature().tier() : 0;
    }

    /** Familiars and arena foes live only as long as their ring or ritual: never written to the world. */
    @Override
    public boolean shouldBeSaved() {
        return !familiar() && arena == null && !echo() && super.shouldBeSaved();
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("corruption", corruption());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        entityData.set(DATA_CORRUPTION, Math.max(0, Math.min(3, input.getIntOr("corruption", 0))));
    }

    // ---- The goals themselves ----

    /**
     * A familiar keeps near its owner the way a tamed wolf does: it sets off once they are START blocks away, walks
     * (runs when far) until within STOP, and slips through a small rift to them past SLIP blocks or when it cannot
     * find a way. In a fight it lets its owner get up to LEASH blocks away before it breaks off.
     */
    /** How many ticks its attack animation lasts (client). */
    public int swingLength() {
        return 10;
    }

    /** A flyer follows over its owner's head and counts the distance on the ground only (The Choir). */
    protected boolean flies() {
        return false;
    }

    private final class FollowOwner extends Goal {
        private static final double START = 3.5, STOP = 2.2, SLIP = 40, LEASH = 24; // (SLIP was 16: it blinked to a sprinting owner)
        private int repath, stuck;
        private boolean direct;

        FollowOwner() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        private boolean fighting(Player owner) {
            return getTarget() != null && getTarget().isAlive() && distanceToSqr(owner) < LEASH * LEASH;
        }

        /** How far the owner is: for a flyer across the ground only, so hovering over them is being near. */
        private double away(Player owner) {
            if (!flies()) return distanceToSqr(owner);
            double dx = owner.getX() - getX(), dz = owner.getZ() - getZ();
            return dx * dx + dz * dz;
        }

        /** Where it heads: the owner's feet, or for a flyer a spot over their head, a little behind them. */
        private Vec3 goalFor(Player owner) {
            if (!flies()) return owner.position();
            Vec3 back = Vec3.directionFromRotation(0, owner.getYRot()).scale(-1.5);
            return owner.position().add(back.x, 2.5, back.z);
        }

        @Override
        public boolean canUse() {
            Player owner = ownerPlayer();
            return familiar() && owner != null && !owner.isSpectator() && !fighting(owner) && !petting() && !dormant() && away(owner) > START * START;
        }

        @Override
        public boolean canContinueToUse() {
            Player owner = ownerPlayer();
            return familiar() && owner != null && !fighting(owner) && !petting() && away(owner) > STOP * STOP;
        }

        @Override
        public void start() {
            repath = 0;
            stuck = 0;
            direct = false;
            Player owner = ownerPlayer();
            if (getTarget() != null && owner != null && distanceToSqr(owner) >= LEASH * LEASH) setTarget(null); // too far: back to them
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true; // steering by hand: a tick missed is a tick standing still
        }

        @Override
        public void tick() {
            Player owner = ownerPlayer();
            if (owner == null) return;
            double d = distanceToSqr(owner);
            if (d < SLIP * SLIP) getLookControl().setLookAt(owner, 10.0F, getMaxHeadXRot());
            double speed = flies() ? (d > 256 ? 1.6 : d > 64 ? 1.2 : 0.7) : d > 256 ? 2.0 : d > 64 ? 1.5 : 1.15; // keeps up, runs to catch up a sprinting owner (a flyer drifts)
            // straight at them: the move control lets go after each tick, so it is told again every tick
            if (direct && d < 14 * 14 && tickCount % 10 != 0) {
                Vec3 goal = goalFor(owner);
                getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, speed);
                return;
            }
            if (--repath > 0 && !direct) return;
            repath = adjustedTickDelay(10);
            if (d >= SLIP * SLIP || stuck >= 4) {
                Familiars.slipTo(RiftBeast.this, owner);
                stuck = 0;
                return;
            }
            Vec3 goal = goalFor(owner);
            direct = straightTo(goal); // open ground between them: straight at the owner, not block by block (checked every 10 ticks)
            if (direct) {
                getNavigation().stop();
                getMoveControl().setWantedPosition(goal.x, goal.y, goal.z, speed);
                stuck = 0;
                return;
            }
            boolean found = getNavigation().moveTo(owner, speed);
            stuck = found ? 0 : stuck + 1;
        }

        /** Nothing in the way at body height, the same floor level, and ground under every step of the way. */
        private boolean straightTo(Vec3 goal) {
            Vec3 from = position();
            double dy = goal.y - from.y;
            if ((!flies() && Math.abs(dy) > 0.6) || from.distanceToSqr(goal) > 14 * 14) return false;
            Vec3 mid = new Vec3(0, getBbHeight() * 0.5, 0);
            if (level().clip(new net.minecraft.world.level.ClipContext(from.add(mid), goal.add(mid), net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.ANY, RiftBeast.this)).getType() != net.minecraft.world.phys.HitResult.Type.MISS) return false;
            if (flies()) return true; // nothing to stand on: open air is enough
            int steps = (int) Math.ceil(from.distanceTo(goal));
            for (int i = 1; i <= steps; i++) {
                Vec3 at = from.lerp(goal, i / (double) steps);
                BlockPos below = BlockPos.containing(at.x, at.y - 0.5, at.z);
                if (level().getBlockState(below).getCollisionShape(level(), below).isEmpty()) return false; // a hole or a drop
            }
            return true;
        }
    }

    /** A familiar turns on whatever hurt its owner. */
    private final class OwnerHurtBy extends TargetGoal {
        private int seen;

        OwnerHurtBy() {
            super(RiftBeast.this, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            Player owner = ownerPlayer();
            if (!familiar() || owner == null) return false;
            LivingEntity attacker = owner.getLastHurtByMob();
            return owner.getLastHurtByMobTimestamp() != seen && canAttack(attacker, TargetingConditions.DEFAULT) && RiftBeast.this.canAttack(attacker);
        }

        @Override
        public void start() {
            Player owner = ownerPlayer();
            if (owner != null) {
                mob.setTarget(owner.getLastHurtByMob());
                seen = owner.getLastHurtByMobTimestamp();
            }
            super.start();
        }
    }

    /** A familiar goes for whatever its owner strikes. */
    private final class OwnerHurts extends TargetGoal {
        private int seen;

        OwnerHurts() {
            super(RiftBeast.this, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            Player owner = ownerPlayer();
            if (!familiar() || owner == null) return false;
            LivingEntity struck = owner.getLastHurtMob();
            return owner.getLastHurtMobTimestamp() != seen && canAttack(struck, TargetingConditions.DEFAULT) && RiftBeast.this.canAttack(struck);
        }

        @Override
        public void start() {
            Player owner = ownerPlayer();
            if (owner != null) {
                mob.setTarget(owner.getLastHurtMob());
                seen = owner.getLastHurtMobTimestamp();
            }
            super.start();
        }
    }

    private @Nullable Player nearestChallenger(ServerLevel server) {
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : challengers(server)) {
            double d = p.distanceToSqr(this);
            if (p.isAlive() && d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    /** An arena foe never lets go of its challenger (or the nearest of their party); a stray hunts the nearest player. */
    private final class Challenger extends TargetGoal {
        private @Nullable Player found;

        Challenger() {
            super(RiftBeast.this, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (familiar() || getTarget() != null) return false;
            if (challenger != null) {
                found = level() instanceof ServerLevel server ? nearestChallenger(server) : null;
            } else {
                found = level().getNearestPlayer(RiftBeast.this, 16);
                if (found != null && (found.isCreative() || found.isSpectator())) found = null;
            }
            return found != null && found.isAlive();
        }

        @Override
        public void start() {
            mob.setTarget(found);
            super.start();
        }
    }

    /** A familiar also takes on monsters that come for its owner. */
    private final class GuardOwner extends TargetGoal {
        private @Nullable Mob found;

        GuardOwner() {
            super(RiftBeast.this, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            Player owner = ownerPlayer();
            if (!familiar() || owner == null || getTarget() != null) return false;
            found = null;
            double best = Double.MAX_VALUE;
            for (Mob mob : level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(12),
                    m -> m.getTarget() == owner && m != RiftBeast.this && m.isAlive())) {
                double d = mob.distanceToSqr(owner);
                if (d < best && RiftBeast.this.canAttack(mob)) {
                    best = d;
                    found = mob;
                }
            }
            return found != null;
        }

        @Override
        public void start() {
            mob.setTarget(found);
            super.start();
        }
    }

    /** Where it stands as a vector (for effects). */
    public Vec3 middle() {
        return position().add(0, getBbHeight() * 0.5, 0);
    }
}
