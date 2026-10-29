package com.lealex.alchyforbidden.familiar;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.item.FamiliarRingItem;
import com.lealex.alchyforbidden.network.SpellFxPayload;
import com.lealex.alchyforbidden.network.VoidArenaPayload;
import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The void trials: a tier 4 Rift Maw does not call its great ones into its own arena but takes the challenger through
 * the rift to an arena of stone hanging in the void (dimension alchyforbidden:void_arena, one arena per trial, 1000
 * blocks apart). Everyone standing on the maw's own blocks when the rift opens is taken with them and fights beside
 * them (a party); a player who comes into the arena some other way (a teleport) joins the party too.
 *
 * Nobody dies there: a blow that would kill casts that player back out through the rift, unworthy. The trial is the
 * challenger's: if they are cast out, leave or log out, it is lost for everyone (lure and essence gone, the ring given
 * back blank). A companion cast out only goes home. Slain, the great one is bound in the challenger's ring.
 */
@EventBusSubscriber(modid = AlchyForbidden.MODID)
public final class VoidTrials {
    private VoidTrials() {}

    public static final ResourceKey<Level> VOID = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "void_arena"));
    private static final int INTRO = 70, OUTRO = 70, FLOOR_Y = 100, SPACING = 1000;
    /** The pull: each one is lifted, dragged into the maw's rift and swallowed (shrinking) over these ticks. */
    private static final int PULL = 64, LIFT = 10, SHRINK_FROM = 48, REGROW = 24; // under 80 ticks off the ground (servers kick floating players)
    /** If a pillar is near, they reach it and cling to it from GRAB until LET_GO, then are torn away. */
    private static final int GRAB = 16, LET_GO = 40;
    /** How far from the challenger the others on the maw may stand (the maw's blocks decide; this only bounds the search). */
    private static final double PARTY_SEARCH = 24;
    private static final Identifier SHRINK = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "void_pull");
    private static final String RETURN = "alchyforbidden:void_return";

    /** One player in a trial (the challenger is member 0). */
    private static final class Member {
        final UUID id;
        final int index;
        @Nullable Vec3 grip, gripBlock;
        boolean pulling = true, unworthy;
        long enteredAt;

        Member(UUID id, int index) {
            this.id = id;
            this.index = index;
        }
    }

    private static final class Trial {
        UUID player; // the challenger: the ritual, the ring and the bound creature are theirs
        RiftCreature creature;
        int corruption, slot, phase; // phase: -1 the challenger pulled into the rift, 0 the rift opening, 1 the fight, 2 won (the way home opening)
        Vec3 riftAt = Vec3.ZERO;
        long pullAt;
        List<String> affixes;
        ItemStack ring;
        BlockPos center;
        long phaseAt;
        @Nullable UUID foe;
        boolean slain;
        final Map<UUID, Member> members = new LinkedHashMap<>();
        int joined; // members ever taken in (each one's place in the arena)
    }

    /** Trials by challenger, and every player in one (challengers and companions) to their trial. */
    private static final Map<UUID, Trial> TRIALS = new HashMap<>();
    private static final Map<UUID, Trial> MEMBERS = new HashMap<>();

    public static boolean inTrial(Entity entity) {
        return MEMBERS.containsKey(entity.getUUID());
    }

    /** True when these two players fight in the same void trial. */
    public static boolean sameParty(UUID a, UUID b) {
        Trial trial = MEMBERS.get(a);
        return trial != null && trial == MEMBERS.get(b);
    }

    /** The members of the trial this player is in who stand in the arena now (not still being pulled); empty when none. */
    public static List<ServerPlayer> party(ServerLevel level, UUID anyMember) {
        Trial trial = MEMBERS.get(anyMember);
        List<ServerPlayer> out = new ArrayList<>();
        if (trial == null) return out;
        for (Member m : trial.members.values()) {
            if (!m.pulling && level.getPlayerByUUID(m.id) instanceof ServerPlayer p && p.isAlive()) out.add(p);
        }
        return out;
    }

    /** Takes the challenger into the void to fight this creature; false when the void cannot be reached (then the maw fights it at home). */
    public static boolean start(ServerPlayer player, RiftCreature creature, int corruption, List<String> affixes, ItemStack ring) {
        return start(player, creature, corruption, affixes, ring, player.position().add(0, 5, 0), pos -> false);
    }

    /**
     * As above, the challenger first pulled up into the rift at riftAt (the maw's), then sent through; every other player
     * standing on a block of the maw (onMaw: the multiblock's own cells) is pulled in with them.
     */
    public static boolean start(ServerPlayer player, RiftCreature creature, int corruption, List<String> affixes, ItemStack ring, Vec3 riftAt,
            Predicate<BlockPos> onMaw) {
        MinecraftServer server = player.level().getServer();
        ServerLevel arena = server == null ? null : server.getLevel(VOID);
        if (arena == null || MEMBERS.containsKey(player.getUUID())) return false;
        Trial trial = new Trial();
        trial.player = player.getUUID();
        trial.creature = creature;
        trial.corruption = corruption;
        trial.affixes = new ArrayList<>(affixes);
        trial.ring = ring;
        trial.slot = freeSlot();
        trial.center = new BlockPos(trial.slot * SPACING, FLOOR_Y, 0);
        trial.phaseAt = arena.getGameTime();
        build(arena, trial.center, Config.voidArenaRadius());
        TRIALS.put(trial.player, trial);
        // the pull: a great rift tears open over the maw and draws the challenger (and all on the maw) up into it
        trial.phase = -1;
        trial.riftAt = riftAt;
        trial.pullAt = player.level().getGameTime();
        ServerLevel here = (ServerLevel) player.level();
        SpellFxPayload.send(here, SpellFxPayload.RIFT, riftAt, -1, PULL + 20, 6.5F);
        take(here, player, trial);
        for (ServerPlayer other : new ArrayList<>(here.players())) {
            if (other == player || !other.isAlive() || other.isSpectator() || MEMBERS.containsKey(other.getUUID())) continue;
            if (other.distanceTo(player) > PARTY_SEARCH || !standsOn(onMaw, other)) continue;
            take(here, other, trial);
            other.sendSystemMessage(Component.translatable("void_trial.alchyforbidden.taken", player.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE));
        }
        here.playSound(null, BlockPos.containing(riftAt), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.2F, 0.5F);
        here.playSound(null, BlockPos.containing(riftAt), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.5F, 0.6F);
        return true;
    }

    /** On the maw: its feet in one of its cells, or standing on one (or just jumping off it). */
    private static boolean standsOn(Predicate<BlockPos> onMaw, ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        return onMaw.test(feet) || onMaw.test(feet.below()) || onMaw.test(feet.below(2));
    }

    /** One more into the trial, to be pulled into the rift: the way home is kept on them, they get the pull cutscene. */
    private static void take(ServerLevel here, ServerPlayer player, Trial trial) {
        Member member = new Member(player.getUUID(), trial.joined++);
        trial.members.put(member.id, member);
        MEMBERS.put(member.id, trial);
        keepWayHome(player);
        findGrip(here, player, member);
        Vec3 gb = member.gripBlock == null ? Vec3.ZERO : member.gripBlock;
        Vec3 riftAt = trial.riftAt;
        PacketDistributor.sendToPlayer(player, new com.lealex.alchyforbidden.network.VoidPullPayload(riftAt.x, riftAt.y, riftAt.z, PULL, PULL + REGROW + 30,
                gb.x, gb.y, gb.z, member.grip == null ? -1 : GRAB, member.grip == null ? -1 : LET_GO));
    }

    /** The way home, kept on the player too, so a crash or a logout still brings them back. */
    private static void keepWayHome(ServerPlayer player) {
        com.lealex.alchyx.util.ReturnPoints.keep(player, RETURN);
    }

    /** Something to cling to: the nearest solid block within 6 blocks, at their height or up to 2 above. */
    private static void findGrip(ServerLevel level, ServerPlayer player, Member member) {
        BlockPos feet = player.blockPosition();
        double best = Double.MAX_VALUE;
        for (int dy = 0; dy <= 2; dy++) {
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    BlockPos at = feet.offset(dx, dy, dz);
                    if (dx * dx + dz * dz < 2 || !level.getBlockState(at).isCollisionShapeFullBlock(level, at)) continue;
                    if (!level.getBlockState(at.above()).isCollisionShapeFullBlock(level, at.above()) && dy == 0) continue; // a pillar, not a floor stone
                    double d = dx * dx + dz * dz + dy * 0.5;
                    if (d < best) {
                        best = d;
                        member.gripBlock = Vec3.atCenterOf(at);
                    }
                }
            }
        }
        if (member.gripBlock == null) return;
        Vec3 out = player.position().subtract(member.gripBlock).multiply(1, 0, 1);
        if (out.lengthSqr() < 1e-4) out = new Vec3(1, 0, 0);
        member.grip = new Vec3(member.gripBlock.x, player.getY() + 1.0, member.gripBlock.z).add(out.normalize().scale(0.95)); // hanging off its side
    }

    /** One tick of one player's pull; at its end they are through, into the void. */
    private static void pull(ServerPlayer player, Member member, Trial trial) {
        ServerLevel here = (ServerLevel) player.level();
        long t = here.getGameTime() - trial.pullAt;
        Vec3 rift = trial.riftAt, body = player.position().add(0, player.getBbHeight() * 0.5, 0), to = rift.subtract(body);
        double dist = to.length();
        boolean lead = member.id.equals(trial.player);
        if (lead) { // the rift's own show, once
            for (int i = 0; i < 10; i++) { // everything round streams into the rift
                double a = here.getRandom().nextDouble() * Math.PI * 2, r = 3 + here.getRandom().nextDouble() * 7, y = rift.y - 5 + here.getRandom().nextDouble() * 7;
                Vec3 from = new Vec3(rift.x + Math.cos(a) * r, y, rift.z + Math.sin(a) * r), in = rift.subtract(from);
                here.sendParticles(i % 3 == 0 ? ModRegistries.RIFT_MOTE.get() : ParticleTypes.REVERSE_PORTAL, from.x, from.y, from.z, 0, in.x, in.y, in.z, 0.09);
            }
            for (int i = 0; i < 3; i++) { // a whirl round the mouth of the rift
                double a = t * 0.35 + i * Math.PI * 2 / 3, r = 2.2 + Math.sin(t * 0.1) * 0.4;
                here.sendParticles(ParticleTypes.PORTAL, rift.x + Math.cos(a) * r, rift.y + Math.sin(t * 0.2 + i) * 0.4, rift.z + Math.sin(a) * r, 0, -Math.cos(a), 0, -Math.sin(a), 0.6);
            }
        }
        here.sendParticles(ParticleTypes.REVERSE_PORTAL, body.x, body.y, body.z, 4, 0.3, 0.5, 0.3, 0.05); // torn off the body
        if (t % 10 == 0) here.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 1.2F, 0.6F + t / 100F);
        if (t == LIFT) {
            here.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.HOSTILE, 0.6F, 0.6F);
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0, false, false));
        }
        boolean clinging = member.grip != null && t >= LIFT && t < LET_GO;
        if (t < LIFT) { // lifted off the ground
            player.setDeltaMovement(player.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, 0.12, 0));
        } else if (clinging) { // it reaches the pillar and clings to it, the rift tugging at it
            Vec3 tug = to.normalize().scale(0.25 * Math.sin(t * 1.3) * Math.min(1, (t - GRAB) / 8.0));
            Vec3 hold = member.grip.add(t >= GRAB ? tug : Vec3.ZERO).subtract(player.position());
            player.setDeltaMovement(hold.scale(t < GRAB ? 0.35 : 0.6));
            if (t >= GRAB && t % 5 == 0) {
                here.playSound(null, player.blockPosition(), SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.8F, 0.6F + here.getRandom().nextFloat() * 0.3F);
                here.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, here.getBlockState(BlockPos.containing(member.gripBlock))),
                        member.grip.x, member.grip.y + 0.6, member.grip.z, 4, 0.2, 0.2, 0.2, 0.05);
            }
            if (t == LET_GO - 1) here.playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, SoundSource.PLAYERS, 1.0F, 0.6F); // torn away
        } else { // then dragged faster and faster into it, turning round it
            int from = member.grip != null ? LET_GO : LIFT;
            double speed = Math.min(dist * 0.5, 0.25 + (t - from) / (double) Math.max(1, PULL - from) * 1.1);
            Vec3 pullV = to.normalize().scale(speed);
            double swirl = 0.15 * Math.min(1, dist / 4), d = Math.max(dist, 0.1);
            player.setDeltaMovement(pullV.add(-to.z / d * swirl, 0, to.x / d * swirl));
        }
        player.hurtMarked = true;
        player.fallDistance = 0;
        if (t >= SHRINK_FROM) scale(player, -0.85 * Math.min(1, (t - SHRINK_FROM) / (double) (PULL - SHRINK_FROM)));
        if (t == PULL - 8) player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
        if (t >= PULL || (t > (member.grip != null ? LET_GO : LIFT) + 6 && dist < 0.8)) enter(player, member, trial);
    }

    /** Their size (the vacuum shrinking them into the rift); 0 = their own. */
    private static void scale(ServerPlayer player, double by) {
        var size = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE);
        if (size == null) return;
        size.removeModifier(SHRINK);
        if (by < -0.001) size.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SHRINK, by,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** Where a member lands: near the edge, the challenger in front, the others spread to either side of them. */
    private static Vec3 landing(Trial trial, int index) {
        int r = Config.voidArenaRadius();
        double a = ((index + 1) / 2) * 0.35 * (index % 2 == 1 ? 1 : -1); // 0, +0.35, -0.35, +0.7...
        Vec3 c = Vec3.atBottomCenterOf(trial.center.above());
        return c.add(Math.sin(a) * (r - 4), 0, -Math.cos(a) * (r - 4));
    }

    /** Through the rift: into the void arena, spat out small and growing back. */
    private static void enter(ServerPlayer player, Member member, Trial trial) {
        MinecraftServer server = player.level().getServer();
        ServerLevel arena = server == null ? null : server.getLevel(VOID);
        if (arena == null) {
            drop(player, member, trial, "void_trial.alchyforbidden.vanished", false);
            return;
        }
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.REVERSE_PORTAL, trial.riftAt.x, trial.riftAt.y, trial.riftAt.z, 120, 0.6, 0.6, 0.6, 0.4);
        player.level().playSound(null, BlockPos.containing(trial.riftAt), SoundEvents.PORTAL_TRAVEL, SoundSource.HOSTILE, 0.5F, 0.7F);
        Vec3 spot = landing(trial, member.index);
        Vec3 c = Vec3.atBottomCenterOf(trial.center.above());
        float yaw = (float) (Math.atan2(c.z - spot.z, c.x - spot.x) * (180 / Math.PI)) - 90F; // facing the heart
        player.teleportTo(arena, spot.x, spot.y + 1.5, spot.z, Set.of(), yaw, 10F, true);
        Vec3 in = c.subtract(spot).normalize().scale(0.25);
        player.setDeltaMovement(in.x, 0.25, in.z);
        player.hurtMarked = true;
        member.pulling = false;
        member.enteredAt = arena.getGameTime();
        int r = Config.voidArenaRadius();
        PacketDistributor.sendToPlayer(player, new VoidArenaPayload(trial.center, r, arena.getGameTime(), true));
        // short lines, so they fit the screen at any gui scale: the name under the title, its grade in the action bar
        title(player, Component.translatable("void_trial.alchyforbidden.title").withStyle(ChatFormatting.DARK_PURPLE), Component.empty(), 10, 40, 15);
        SpellFxPayload.send(arena, SpellFxPayload.RIFT, spot.add(0, 2.5, 0), -1, 30, 2.5F); // the rift it is spat out of
        arena.sendParticles(ParticleTypes.REVERSE_PORTAL, spot.x, spot.y + 1.5, spot.z, 100, 0.8, 0.8, 0.8, 0.3);
        if (member.id.equals(trial.player) && trial.phase == -1) { // the challenger is through: the great rift opens over the heart
            trial.phase = 0;
            trial.phaseAt = arena.getGameTime();
            Vec3 riftAt = c.add(0, 7, 0);
            SpellFxPayload.send(arena, SpellFxPayload.RIFT, riftAt, -1, INTRO + 30, 8.0F);
            arena.playSound(null, trial.center, SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.0F, 0.5F);
            arena.playSound(null, trial.center, SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 1.5F, 0.5F);
        }
    }

    /** A player who came into the arena some other way (a teleport): they join the party where they stand. */
    private static void join(ServerLevel arena, ServerPlayer player, Trial trial) {
        Member member = new Member(player.getUUID(), trial.joined++);
        member.pulling = false;
        member.enteredAt = arena.getGameTime() - REGROW; // full size already
        trial.members.put(member.id, member);
        MEMBERS.put(member.id, trial);
        PacketDistributor.sendToPlayer(player, new VoidArenaPayload(trial.center, Config.voidArenaRadius(), arena.getGameTime(), true));
        title(player, Component.translatable("void_trial.alchyforbidden.title").withStyle(ChatFormatting.DARK_PURPLE), Component.empty(), 10, 40, 15);
        ServerPlayer lead = arena.getServer().getPlayerList().getPlayer(trial.player);
        player.sendSystemMessage(Component.translatable("void_trial.alchyforbidden.joined",
                lead == null ? Component.literal("?") : lead.getDisplayName()).withStyle(ChatFormatting.DARK_PURPLE));
        if (trial.phase == 1 && trial.foe != null && arena.getEntity(trial.foe) instanceof RiftBeast beast) beast.watchIntro(player);
    }

    // ---- For trailers: a void arena to stage entrances in, with the player taken there and back ----

    private static final BlockPos SHOWCASE = new BlockPos(-SPACING, FLOOR_Y, 0);

    /** Takes the player into a void arena of their own (built once), on its south edge facing its heart. */
    public static boolean showcaseArena(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel arena = server == null ? null : server.getLevel(VOID);
        if (arena == null) return false;
        int r = Config.voidArenaRadius();
        build(arena, SHOWCASE, r);
        if (player.level().dimension() != VOID) keepWayHome(player);
        Vec3 spot = Vec3.atBottomCenterOf(SHOWCASE.above()).add(0, 0, r - 4);
        player.teleportTo(arena, spot.x, spot.y, spot.z, Set.of(), 180F, 5F, true);
        player.resetFallDistance();
        PacketDistributor.sendToPlayer(player, new VoidArenaPayload(SHOWCASE, r, arena.getGameTime(), true));
        return true;
    }

    /** Back where they were before the showcase arena. */
    public static void showcaseReturn(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new VoidArenaPayload(SHOWCASE, 0, 0, false));
        if (player.level().dimension() == VOID) sendHome(player);
    }

    private static int freeSlot() {
        for (int i = 1; ; i++) {
            boolean used = false;
            for (Trial t : TRIALS.values()) used |= t.slot == i;
            if (!used) return i;
        }
    }

    private static @Nullable BlockState look(String id) {
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, id));
        return block == Blocks.AIR ? null : block.defaultBlockState();
    }

    /** The arena: a disc of the altar's stone hanging in the void, runes round it, lamp pillars at its edge. Built once per place. */
    private static void build(ServerLevel level, BlockPos center, int r) {
        BlockState flag = look("altar_flagstone"), veined = look("veined_deepslate"), slab = look("sacrificial_slab"), rune = look("mage_rune"),
                base = look("pillar_base"), shaft = look("warden_pillar"), cap = look("pillar_capital"), lantern = look("rift_lantern");
        if (flag == null || veined == null || slab == null || rune == null) return;
        if (level.getBlockState(center).is(slab.getBlock())) return; // already built
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.5) continue;
                BlockPos at = center.offset(dx, 0, dz);
                BlockState state;
                if (d > r - 1.5) state = veined;                                     // the rim
                else if (d < 2.5) state = slab;                                      // the heart
                else if (Math.abs(d - r * 0.55) < 0.6) state = veined;               // a ring of veins
                else if ((dx == 0 || dz == 0) && d < r - 2) state = slab;            // channels to the heart
                else state = flag;
                level.setBlock(at, state, Block.UPDATE_CLIENTS);
                if (d > r - 2.5) level.setBlock(at.below(), veined, Block.UPDATE_CLIENTS);
            }
        }
        for (int i = 0; i < 8; i++) { // runes standing round the heart, lamp pillars at the edge
            double a = i * Math.PI / 4;
            BlockPos runeAt = center.offset((int) Math.round(Math.cos(a) * (r * 0.55 + 1.5)), 1, (int) Math.round(Math.sin(a) * (r * 0.55 + 1.5)));
            if (i % 2 == 1) level.setBlock(runeAt, rune, Block.UPDATE_CLIENTS);
            if (base == null || shaft == null || cap == null || lantern == null) continue;
            BlockPos foot = center.offset((int) Math.round(Math.cos(a + Math.PI / 8) * (r - 1)), 1, (int) Math.round(Math.sin(a + Math.PI / 8) * (r - 1)));
            level.setBlock(foot, base, Block.UPDATE_CLIENTS);
            level.setBlock(foot.above(), shaft, Block.UPDATE_CLIENTS);
            level.setBlock(foot.above(2), shaft, Block.UPDATE_CLIENTS);
            level.setBlock(foot.above(3), cap, Block.UPDATE_CLIENTS);
            level.setBlock(foot.above(4), lantern, Block.UPDATE_CLIENTS);
        }
    }

    // ---- The trials, every tick ----

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TRIALS.isEmpty()) return;
        MinecraftServer server = event.getServer();
        ServerLevel arena = server.getLevel(VOID);
        for (Trial trial : new ArrayList<>(TRIALS.values())) {
            if (TRIALS.get(trial.player) != trial) continue; // ended this tick
            ServerPlayer lead = server.getPlayerList().getPlayer(trial.player);
            if (arena == null || lead == null) {
                lose(server, trial, "void_trial.alchyforbidden.lead_gone", false); // the challenger logged out (onLogout gives the ring back)
                continue;
            }
            tick(server, arena, lead, trial);
        }
    }

    private static void tick(MinecraftServer server, ServerLevel arena, ServerPlayer lead, Trial trial) {
        Vec3 c = Vec3.atBottomCenterOf(trial.center.above());
        int r = Config.voidArenaRadius();
        // each member on their own: pulled, held inside the wall, cast out when struck down or fallen
        for (Member member : new ArrayList<>(trial.members.values())) {
            if (TRIALS.get(trial.player) != trial) return; // the trial ended meanwhile
            ServerPlayer player = server.getPlayerList().getPlayer(member.id);
            if (player == null) { // a companion logged out (the way home is kept on them)
                leave(member, trial);
                continue;
            }
            if (member.pulling) {
                if (!player.isAlive()) drop(player, member, trial, "void_trial.alchyforbidden.fled", false);
                else pull(player, member, trial);
                continue;
            }
            if (player.level() != arena || !player.isAlive()) {
                drop(player, member, trial, "void_trial.alchyforbidden.fled", false);
                continue;
            }
            if (member.unworthy && trial.phase < 2) { // after the win nothing makes them unworthy
                drop(player, member, trial, "void_trial.alchyforbidden.unworthy", true);
                continue;
            }
            if (player.getY() < trial.center.getY() - 6) { // fallen from the stone: the void spits them out
                drop(player, member, trial, "void_trial.alchyforbidden.unworthy", true);
                continue;
            }
            double dx = player.getX() - c.x, dz = player.getZ() - c.z, d = Math.sqrt(dx * dx + dz * dz);
            if (d > r - 0.7 && trial.phase < 2) { // the wall throws them back
                Vec3 in = new Vec3(-dx, 0, -dz).normalize();
                player.setDeltaMovement(in.x * 0.9, 0.35, in.z * 0.9);
                player.hurtMarked = true;
                arena.sendParticles(ModRegistries.RIFT_MOTE.get(), player.getX() - in.x * 0.6, player.getY() + 1, player.getZ() - in.z * 0.6, 12, 0.1, 0.6, 0.1, 0.05);
            }
            long since = arena.getGameTime() - member.enteredAt;
            if (since <= REGROW) scale(player, since >= REGROW ? 0 : -0.85 * (1 - since / (double) REGROW)); // growing back
            if (trial.phase == 2 && (arena.getGameTime() - trial.phaseAt) % 4 == 0) {
                arena.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(), 6, 0.6, 0.8, 0.6, 0.2);
            }
        }
        if (TRIALS.get(trial.player) != trial || trial.phase == -1) return;
        // anyone else who came into this arena (a teleport) fights beside them
        if (trial.phase < 2 && arena.getGameTime() % 10 == 0) {
            for (ServerPlayer other : new ArrayList<>(arena.players())) {
                if (MEMBERS.containsKey(other.getUUID()) || !other.isAlive() || other.isSpectator()) continue;
                double ox = other.getX() - c.x, oz = other.getZ() - c.z;
                if (ox * ox + oz * oz < (r + 6) * (r + 6) && Math.abs(other.getY() - c.y) < 40) join(arena, other, trial);
            }
        }
        long elapsed = arena.getGameTime() - trial.phaseAt;
        switch (trial.phase) {
            case 0 -> {
                if (elapsed % 10 == 0) arena.sendParticles(ParticleTypes.PORTAL, c.x, c.y + 0.2, c.z, 30, 2, 0.1, 2, 1.5);
                Vec3 great = c.add(0, 6, 0); // the great rift breathing, the void drawn up into it
                for (int i = 0; i < 6; i++) {
                    double a = arena.getRandom().nextDouble() * Math.PI * 2, rr = 4 + arena.getRandom().nextDouble() * 8;
                    Vec3 from = new Vec3(great.x + Math.cos(a) * rr, c.y + arena.getRandom().nextDouble() * 2, great.z + Math.sin(a) * rr), in = great.subtract(from);
                    arena.sendParticles(i % 2 == 0 ? ModRegistries.RIFT_MOTE.get() : ParticleTypes.REVERSE_PORTAL, from.x, from.y, from.z, 0, in.x, in.y, in.z, 0.07);
                }
                for (int i = 0; i < 4; i++) {
                    double a = elapsed * 0.3 + i * Math.PI / 2;
                    arena.sendParticles(ParticleTypes.PORTAL, great.x + Math.cos(a) * 3.5, great.y, great.z + Math.sin(a) * 3.5, 0, -Math.cos(a), 0, -Math.sin(a), 0.5);
                }
                if (elapsed >= INTRO) callForth(arena, lead, trial, c);
            }
            case 1 -> {
                if (elapsed > Config.voidTrialSeconds() * 20L) {
                    lose(server, trial, "void_trial.alchyforbidden.time", true);
                    return;
                }
                Entity entity = trial.foe == null ? null : arena.getEntity(trial.foe);
                if (trial.slain) {
                    won(arena, lead, trial, c);
                    return;
                }
                if (!(entity instanceof RiftBeast beast) || entity.isRemoved()) {
                    lose(server, trial, "void_trial.alchyforbidden.vanished", false);
                    return;
                }
                double bx = beast.getX() - c.x, bz = beast.getZ() - c.z;
                double bd = Math.sqrt(bx * bx + bz * bz);
                if (beast.getY() < trial.center.getY() - 4) beast.teleportTo(c.x, c.y + 0.5, c.z); // fell off: back to the middle
                else if (bd > r - 1) { // at the wall: set back inside where it is, so it does not jump across the arena
                    double k = (r - 2.5) / bd;
                    beast.teleportTo(c.x + bx * k, beast.getY(), c.z + bz * k);
                    beast.setDeltaMovement(Vec3.ZERO);
                }
                if (beast.getTarget() == null || !beast.getTarget().isAlive()) {
                    ServerPlayer near = nearest(party(arena, trial.player), beast);
                    if (near != null) beast.setTarget(near);
                }
            }
            default -> { // won: a moment to breathe, then home
                if (elapsed >= OUTRO) homeAll(server, trial);
            }
        }
    }

    private static @Nullable ServerPlayer nearest(List<ServerPlayer> players, Entity from) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : players) {
            double d = p.distanceToSqr(from);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private static void callForth(ServerLevel arena, ServerPlayer lead, Trial trial, Vec3 c) {
        RiftBeast beast = trial.creature.type().create(arena, EntitySpawnReason.MOB_SUMMONED);
        if (beast == null) {
            lose(arena.getServer(), trial, "void_trial.alchyforbidden.vanished", false);
            return;
        }
        beast.snapTo(c.x, c.y + 0.2, c.z, 180F, 0);
        beast.becomeArenaFoe(trial.center, lead.getUUID(), trial.corruption, trial.affixes);
        if (!arena.addFreshEntity(beast)) {
            lose(arena.getServer(), trial, "void_trial.alchyforbidden.vanished", false);
            return;
        }
        beast.setTarget(lead);
        beast.introduce(lead);
        for (ServerPlayer other : party(arena, trial.player)) if (other != lead) beast.watchIntro(other); // everyone sees it come
        trial.foe = beast.getUUID();
        trial.phase = 1;
        trial.phaseAt = arena.getGameTime() + RiftBeast.INTRO_TICKS; // the trial's clock starts after the entrance
        arena.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 1.5, c.z, 1, 0, 0, 0, 0);
        arena.sendParticles(ModRegistries.RIFT_MOTE.get(), c.x, c.y + 1.5, c.z, 120, 1.2, 1.5, 1.2, 0.2);
        arena.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y + 1.5, c.z, 40, 1, 1, 1, 0.08);
        arena.playSound(null, trial.center, SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 2.0F, 0.6F);
        arena.playSound(null, trial.center, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8F, 0.6F);
    }

    /** Called by a great one dying in the void (RiftBeast.die): the trial is won. */
    public static void foeSlain(RiftBeast beast) {
        for (Trial trial : TRIALS.values()) {
            if (beast.getUUID().equals(trial.foe) && trial.phase == 1) trial.slain = true;
        }
    }

    private static void won(ServerLevel arena, ServerPlayer lead, Trial trial, Vec3 c) {
        ItemStack bound = trial.ring.copy();
        FamiliarRingItem.setData(bound, FamiliarData.fresh(trial.creature, trial.corruption, trial.affixes));
        trial.ring = ItemStack.EMPTY;
        give(lead, bound);
        int residue = (trial.corruption + 2) * Config.residuePerGrade();
        if (residue > 0) give(lead, new ItemStack(ModRegistries.RIFT_RESIDUE.get(), residue));
        if (Config.voidTrialPrisms() > 0) give(lead, new ItemStack(ModRegistries.VOID_PRISM.get(), Config.voidTrialPrisms()));
        trial.phase = 2;
        trial.phaseAt = arena.getGameTime();
        arena.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y + 1, c.z, 80, 1.5, 1.5, 1.5, 0.4);
        arena.playSound(null, trial.center, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
        arena.playSound(null, trial.center, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.0F);
        title(lead, Component.translatable("void_trial.alchyforbidden.worthy").withStyle(ChatFormatting.LIGHT_PURPLE),
                Component.translatable("void_trial.alchyforbidden.bound_short").withStyle(ChatFormatting.GRAY), 10, 60, 20);
        lead.sendSystemMessage(Component.translatable("void_trial.alchyforbidden.bound", trial.creature.fullName(trial.corruption)).withStyle(ChatFormatting.LIGHT_PURPLE));
        SpellFxPayload.send(arena, SpellFxPayload.RIFT, lead.position().add(0, 1.2, 0), -1, OUTRO + 10, 2.0F);
        openLastChapter(lead);
        for (ServerPlayer other : party(arena, trial.player)) { // the companions: residue of their own for the fight
            if (other == lead) continue;
            openLastChapter(other);
            if (residue > 0) give(other, new ItemStack(ModRegistries.RIFT_RESIDUE.get(), residue));
            title(other, Component.translatable("void_trial.alchyforbidden.worthy").withStyle(ChatFormatting.LIGHT_PURPLE),
                    Component.translatable("void_trial.alchyforbidden.won_beside").withStyle(ChatFormatting.GRAY), 10, 60, 20);
            other.sendSystemMessage(Component.translatable("void_trial.alchyforbidden.won_beside_line", trial.creature.fullName(trial.corruption), lead.getDisplayName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** The grimoire's last chapter opens to whoever won a trial (advancement alchyforbidden:book/door, criterion "won"). */
    private static void openLastChapter(ServerPlayer player) {
        var holder = player.level().getServer().getAdvancements().get(net.minecraft.resources.Identifier.fromNamespaceAndPath("alchyforbidden", "book/door"));
        if (holder != null) player.getAdvancements().award(holder, "won");
    }

    /** A member falls out of the trial: the challenger takes the whole trial with them; a companion only goes home. */
    private static void drop(ServerPlayer player, Member member, Trial trial, String why, boolean unworthy) {
        if (member.id.equals(trial.player)) {
            MinecraftServer server = player.level().getServer();
            if (server != null) lose(server, trial, why, unworthy);
            return;
        }
        leave(member, trial);
        castOut(player, trial, why, unworthy);
    }

    private static void leave(Member member, Trial trial) {
        trial.members.remove(member.id);
        MEMBERS.remove(member.id, trial);
    }

    /** One player back through the rift: home, with the unworthy's titles and weakness when struck down. */
    private static void castOut(ServerPlayer player, Trial trial, String why, boolean unworthy) {
        home(player, trial);
        if (unworthy) {
            title(player, Component.translatable("void_trial.alchyforbidden.unworthy_title").withStyle(ChatFormatting.DARK_RED),
                    Component.translatable("void_trial.alchyforbidden.unworthy_sub").withStyle(ChatFormatting.GRAY), 5, 70, 30);
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 1));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0));
        }
        player.sendSystemMessage(Component.translatable(why).withStyle(ChatFormatting.RED));
        player.level().playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2F, 0.5F);
    }

    /**
     * The trial is lost: the great one goes back, the challenger's ring comes back blank, everyone is cast out of the rift
     * (the challenger with the reason; the companions told the challenger fell, or the same reason).
     */
    private static void lose(MinecraftServer server, Trial trial, String why, boolean unworthy) {
        end(trial);
        ServerLevel arena = server.getLevel(VOID);
        if (arena != null && trial.foe != null && arena.getEntity(trial.foe) instanceof RiftBeast beast && beast.isAlive()) {
            Familiars.riftPuff(arena, beast.middle(), beast.creature().color(), false);
            beast.discard();
        }
        ServerPlayer lead = server.getPlayerList().getPlayer(trial.player);
        if (lead != null && !trial.ring.isEmpty()) give(lead, trial.ring);
        trial.ring = ItemStack.EMPTY;
        for (Member member : trial.members.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member.id);
            if (player == null) continue; // logged out: the way home is kept on them
            boolean isLead = member.id.equals(trial.player);
            if (isLead) castOut(player, trial, why, unworthy);
            else castOut(player, trial, why.endsWith(".time") || why.endsWith(".vanished") ? why : "void_trial.alchyforbidden.party_lost", false);
        }
    }

    /** Won: everyone home together. */
    private static void homeAll(MinecraftServer server, Trial trial) {
        end(trial);
        for (Member member : trial.members.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(member.id);
            if (player != null) home(player, trial);
        }
    }

    private static void end(Trial trial) {
        TRIALS.remove(trial.player, trial);
        for (UUID id : trial.members.keySet()) MEMBERS.remove(id, trial);
    }

    /** Back through the rift to where they came from. */
    private static void home(ServerPlayer player, Trial trial) {
        scale(player, 0);
        PacketDistributor.sendToPlayer(player, new VoidArenaPayload(trial.center, 0, 0, false));
        sendHome(player);
    }

    private static void sendHome(ServerPlayer player) {
        if (player.level().getServer() == null) return;
        com.lealex.alchyx.util.ReturnPoints.sendBack(player, RETURN, VOID); // never back into the void: the world's spawn then
        player.setHealth(Math.max(player.getHealth(), 6F));
        player.resetFallDistance();
        if (player.level() instanceof ServerLevel home) {
            SpellFxPayload.send(home, SpellFxPayload.RIFT, player.position().add(0, 1.2, 0), -1, 30, 1.6F);
            home.sendParticles(ModRegistries.RIFT_MOTE.get(), player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.8, 0.4, 0.1);
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void title(ServerPlayer player, Component title, Component subtitle, int in, int stay, int out) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    // ---- Nobody dies in the void ----

    private static @Nullable Member member(ServerPlayer player) {
        Trial trial = MEMBERS.get(player.getUUID());
        return trial == null ? null : trial.members.get(player.getUUID());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Member member = member(player);
        if (member == null) return;
        Trial trial = MEMBERS.get(player.getUUID());
        if (member.pulling || trial.phase == 2) { // nothing touches them while the rift takes them, nor once they have won
            event.setNewDamage(0);
            return;
        }
        if (event.getNewDamage() >= player.getHealth() + player.getAbsorptionAmount()) { // the killing blow: cast out instead
            event.setNewDamage(0);
            member.unworthy = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Member member = member(player);
        if (member == null) return;
        event.setCanceled(true);
        player.setHealth(1F);
        if (MEMBERS.get(player.getUUID()).phase < 2) member.unworthy = true;
    }

    // ---- Coming into the void another way: the way home is kept first ----

    @SubscribeEvent
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getDimension() == VOID && player.level().dimension() != VOID
                && !com.lealex.alchyx.util.ReturnPoints.has(player, RETURN)) {
            keepWayHome(player); // a teleport into the void: they go back here when the trial ends
        }
    }

    // ---- Logging out mid-trial: the challenger loses it; anyone wakes at home ----

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Trial trial = MEMBERS.get(player.getUUID());
        if (trial == null) return;
        if (!player.getUUID().equals(trial.player)) {
            Member member = trial.members.get(player.getUUID());
            if (member != null) leave(member, trial);
            return;
        }
        if (!trial.ring.isEmpty()) give(player, trial.ring); // into the inventory before it is saved
        trial.ring = ItemStack.EMPTY;
        MinecraftServer server = player.level().getServer();
        trial.members.remove(player.getUUID());
        MEMBERS.remove(player.getUUID(), trial);
        if (server != null) lose(server, trial, "void_trial.alchyforbidden.lead_gone", false); // the others go home
        else end(trial);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // in the void, or gone mid-pull (still in the air over the maw, the way home kept on them): back where it began
        if (event.getEntity() instanceof ServerPlayer player && !MEMBERS.containsKey(player.getUUID())
                && (player.level().dimension() == VOID || com.lealex.alchyx.util.ReturnPoints.has(player, RETURN))) {
            scale(player, 0);
            sendHome(player);
            player.sendSystemMessage(Component.translatable("void_trial.alchyforbidden.fled").withStyle(ChatFormatting.RED));
        }
    }

    // ---- A server stopping mid-fight: no arena block is left standing in the saved world ----

    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        RiftBeast.clearAllTempBlocks(); // before the worlds are saved (players logging out end their trials after this)
    }

    @SubscribeEvent
    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        TRIALS.clear(); // a single player world closed: nothing carried into the next one
        MEMBERS.clear();
    }
}
