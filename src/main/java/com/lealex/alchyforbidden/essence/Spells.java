package com.lealex.alchyforbidden.essence;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Spells held in rings (item/SpellRingItem, imbued at the Binding Circle, block/entity/SoulCircleBlockEntity): the rings
 * a player wears in order (ModRegistries.WORN_RINGS, set in the circle's Rings tab), how many they can wear
 * (SOUL_SLOTS: 2 + the highest circle tier they used), and when each spell can be cast again (SPELL_COOLDOWNS,
 * "id=game time"). All synced to the player, kept through death (but the cooldowns). A ring's gems change its cost,
 * cooldown, damage, durations and ranges. Casting (network/CastPayload from a key, client/SpellKeys) pays the spell's essence from the
 * carved reserve, then the vessels carried (Inscriptions.payEssence), and runs it here; the lasting ones (the black
 * hole, the chains' hold) tick in {@link #onServerTick}. Spells grow with the body's power (its carvings).
 */
@EventBusSubscriber(modid = AlchyForbidden.MODID)
public final class Spells {
    private Spells() {}

    // ---- The rings a player wears ----

    /** A worn ring: its tier and what it holds. */
    public record Worn(int tier, RingContents contents) {
        public @Nullable Spell spell() {
            return contents.spellOrNull();
        }

        String encode() {
            return tier + "|" + contents.spell() + "|" + String.join(",", contents.gems());
        }

        static @Nullable Worn decode(String text) {
            if (text.isEmpty()) return null;
            String[] parts = text.split("\\|", -1);
            if (parts.length < 2) return null;
            try {
                List<String> gems = parts.length > 2 && !parts[2].isEmpty() ? List.of(parts[2].split(",", -1)) : List.of();
                return new Worn(Integer.parseInt(parts[0]), new RingContents(parts[1], gems));
            } catch (NumberFormatException bad) {
                return null;
            }
        }

        public net.minecraft.world.item.ItemStack toStack() {
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(ModRegistries.ringOfTier(tier));
            com.lealex.alchyforbidden.item.SpellRingItem.setContents(stack, contents);
            return stack;
        }

        public static @Nullable Worn of(net.minecraft.world.item.ItemStack stack) {
            if (!(stack.getItem() instanceof com.lealex.alchyforbidden.item.SpellRingItem ring)) return null;
            return new Worn(ring.tier(), com.lealex.alchyforbidden.item.SpellRingItem.contents(stack));
        }
    }

    public static int soulSlots(Player player) {
        return player.getData(ModRegistries.SOUL_SLOTS);
    }

    /** The ring slots grow with the circle used: 2 + its tier, never fewer than before. */
    public static void raiseSlots(Player player, int circleTier) {
        int slots = Math.max(soulSlots(player), 2 + circleTier);
        if (slots != soulSlots(player)) player.setData(ModRegistries.SOUL_SLOTS, slots);
    }

    /** The worn rings, one per slot (null for an empty one). */
    public static List<@Nullable Worn> worn(Player player) {
        List<String> saved = player.getData(ModRegistries.WORN_RINGS);
        List<@Nullable Worn> out = new ArrayList<>();
        for (int i = 0; i < soulSlots(player); i++) out.add(i < saved.size() ? Worn.decode(saved.get(i)) : null);
        return out;
    }

    public static void setWorn(Player player, List<@Nullable Worn> rings) {
        List<String> out = new ArrayList<>();
        for (Worn ring : rings) out.add(ring == null ? "" : ring.encode());
        player.setData(ModRegistries.WORN_RINGS, List.copyOf(out));
    }

    /** The spell of each slot's ring (null when empty or blank). */
    public static List<@Nullable Spell> slots(Player player) {
        List<@Nullable Spell> out = new ArrayList<>();
        for (Worn ring : worn(player)) out.add(ring == null ? null : ring.spell());
        return out;
    }

    /** True if a ring the player wears holds that spell. */
    public static boolean knows(Player player, Spell spell) {
        for (Worn ring : worn(player)) if (ring != null && ring.spell() == spell) return true;
        return false;
    }

    /** What casting it from that ring costs in each essence (Wrath / Sorrow / Corruption), its gems counted. */
    public static int[] costs(Spell spell, RingContents ring) {
        int[] out = spell.castCosts();
        for (int i = 0; i < out.length; i++) if (out[i] > 0) out[i] = (int) Math.max(1, Math.round(out[i] * ring.costFactor()));
        return out;
    }

    /** True if the body can pay all of it (reserve and vessels; creative pays nothing). */
    public static boolean canPay(Player player, int[] costs) {
        for (Essence e : Essence.values()) if (costs[e.ordinal()] > 0 && !Inscriptions.canPayEssence(player, e, costs[e.ordinal()])) return false;
        return true;
    }

    /** A cost as text: each essence's amount and its icon ("8 [flame] 2 [spore]"). */
    public static Component costLine(int[] costs) {
        net.minecraft.network.chat.MutableComponent line = Component.empty();
        boolean first = true;
        for (Essence e : Essence.values()) {
            if (costs[e.ordinal()] <= 0) continue;
            if (!first) line.append(" ");
            line.append(Component.literal(costs[e.ordinal()] + "")).append(e.icon());
            first = false;
        }
        return line;
    }

    /** Its cooldown from that ring, in ticks. */
    public static int cooldown(Spell spell, RingContents ring) {
        return (int) Math.max(10, Math.round(spell.cooldown() * ring.cooldownFactor()));
    }

    // ---- Cooldowns ----

    /** The game time a spell can be cast again (0: now). Either side. */
    public static long readyAt(Player player, Spell spell) {
        for (String entry : player.getData(ModRegistries.SPELL_COOLDOWNS)) {
            int at = entry.indexOf('=');
            if (at > 0 && entry.substring(0, at).equals(spell.id())) {
                try {
                    return Long.parseLong(entry.substring(at + 1));
                } catch (NumberFormatException bad) {
                    return 0;
                }
            }
        }
        return 0;
    }

    private static void startCooldown(ServerPlayer player, Spell spell, int ticks) {
        long now = player.level().getGameTime();
        List<String> out = new ArrayList<>();
        for (String entry : player.getData(ModRegistries.SPELL_COOLDOWNS)) {
            int at = entry.indexOf('=');
            if (at <= 0 || entry.substring(0, at).equals(spell.id())) continue;
            try {
                if (Long.parseLong(entry.substring(at + 1)) > now) out.add(entry); // the others still cooling
            } catch (NumberFormatException ignored) {
            }
        }
        out.add(spell.id() + "=" + (now + ticks));
        player.setData(ModRegistries.SPELL_COOLDOWNS, List.copyOf(out));
    }

    // ---- Casting ----

    /** The ring's gems for the cast in progress (server thread, one cast at a time). */
    private static double damageFactor = 1, durationFactor = 1, rangeFactor = 1;

    private static float dmg(double amount) {
        return (float) (amount * damageFactor);
    }

    private static int ticks(double amount) {
        return (int) Math.round(amount * durationFactor);
    }

    private static double reach(double amount) {
        return amount * rangeFactor;
    }

    /** A cast from the player's key (the slot they had picked). */
    public static void cast(ServerPlayer player, int slot) {
        List<@Nullable Worn> rings = worn(player);
        if (slot < 0 || slot >= rings.size() || player.isSpectator() || !player.isAlive()) return;
        Worn ring = rings.get(slot);
        Spell spell = ring == null ? null : ring.spell();
        if (spell == null) return;
        if (player.level().getGameTime() < readyAt(player, spell)) return;
        int[] cost = costs(spell, ring.contents());
        if (!canPay(player, cost)) {
            player.sendSystemMessage(Component.translatable("spell.alchyforbidden.no_essence_all", spell.displayName(), costLine(cost))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        damageFactor = ring.contents().damageFactor() * spell.power();
        durationFactor = ring.contents().durationFactor();
        rangeFactor = ring.contents().rangeFactor();
        double power = Inscriptions.power(player);
        boolean done = switch (spell) {
            case RIFT_BOLT -> riftBolt(player, power);
            case MIST_STEP -> mistStep(player);
            case SOUL_SHIELD -> soulShield(player, power);
            case SCULK_FANGS -> sculkFangs(player);
            case WAIL -> wail(player, power);
            case RIFT_CHAINS -> riftChains(player, power);
            case BLACK_HOLE -> blackHole(player, power);
            case SONIC_BOOM -> sonicBoom(player, power);
            case ECHO_PULSE -> echoPulse(player, power);
            case SOUL_DRAIN -> soulDrain(player, power);
            case WRATH_NOVA -> wrathNova(player, power);
            case RIFT_GATE -> riftGate(player);
            case BLOOD_RITE -> bloodRite(player, power);
            case GREAT_LEAP -> greatLeap(player, power);
            case DRIFTING_SOUL -> driftingSoul(player, power);
            case SHADOW_VEIL -> shadowVeil(player, power);
            case RIFT_POCKET -> riftPocket(player);
            case RIFT_ANCHOR -> riftAnchor(player);
            case BONE_SPEAR -> boneSpear(player, power);
            case BILE_SPRAY -> bileSpray(player, power);
            case WISP_LIGHT -> wispLight(player);
            case SCULK_SNARE -> sculkSnare(player, power);
            case GORE_BURST -> goreBurst(player, power);
            case RIFT_METEOR -> riftMeteor(player, power);
            case STASIS_FIELD -> stasisField(player, power);
            case VERDANT_PULSE -> verdantPulse(player);
            case RIFT_BORE -> riftBore(player);
            case SCULK_PLAGUE -> sculkPlague(player, power);
            case SOUL_STORM -> soulStorm(player, power);
            case ABYSSAL_FORM -> abyssalForm(player, power);
            case SHADOW_STRIKE -> shadowStrike(player, power);
        };
        damageFactor = durationFactor = rangeFactor = 1;
        if (done) { // paid only for a spell that went off (no room to step to, nothing to chain: nothing taken)
            for (Essence e : Essence.values()) if (cost[e.ordinal()] > 0) Inscriptions.payEssence(player, e, cost[e.ordinal()]);
            startCooldown(player, spell, cooldown(spell, ring.contents()));
        }
    }

    private static Vec3 eye(Player player) {
        return player.getEyePosition();
    }

    /**
     * Whether a spell cast by `caster` strikes this entity (config spells.targets / hitPlayers): never the caster, its
     * own pets, the dead or spectators; players only when allowed; else hostile mobs, every mob, or every living thing.
     */
    public static boolean strikes(@Nullable Player caster, Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.isSpectator() || living == caster) return false;
        if (entity instanceof Player) return com.lealex.alchyforbidden.Config.spellHitsPlayers();
        if (caster != null && entity instanceof net.minecraft.world.entity.OwnableEntity pet && caster.getUUID().equals(petOwner(pet))) return false;
        if (entity instanceof com.lealex.alchyforbidden.familiar.RiftBeast beast) { // creatures of the rift: wild ones always, familiars as players
            return beast.wild() || (caster != null && !caster.getUUID().equals(beast.owner()) && com.lealex.alchyforbidden.Config.spellHitsPlayers());
        }
        return switch (com.lealex.alchyforbidden.Config.spellTargets()) {
            case HOSTILE -> entity instanceof net.minecraft.world.entity.monster.Enemy;
            case MOBS -> entity instanceof Mob;
            case ALL_LIVING -> !(entity instanceof net.minecraft.world.entity.decoration.ArmorStand);
        };
    }

    private static java.util.@Nullable UUID petOwner(net.minecraft.world.entity.OwnableEntity pet) {
        var owner = pet.getOwnerReference();
        return owner == null ? null : owner.getUUID();
    }

    /** What the player looks at within reach: a living thing (not themself), or null. */
    private static @Nullable LivingEntity target(ServerPlayer player, double reach) {
        Vec3 from = eye(player), to = from.add(player.getLookAngle().scale(reach));
        BlockHitResult block = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) to = block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, from, to, new AABB(from, to).inflate(1.0),
                entity -> strikes(player, entity), from.distanceToSqr(to));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    /** Where the player looks, a block's face within reach, or the end of the reach. */
    private static Vec3 lookPoint(ServerPlayer player, double reach) {
        Vec3 from = eye(player), to = from.add(player.getLookAngle().scale(reach));
        BlockHitResult block = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return block.getType() == HitResult.Type.MISS ? to : block.getLocation();
    }

    private static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle, double step) {
        double length = from.distanceTo(to);
        for (double d = 0; d <= length; d += step) {
            Vec3 p = from.lerp(to, d / Math.max(0.001, length));
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Wrath: a bolt of the rift along the look, hurting the first thing it meets. */
    private static boolean riftBolt(ServerPlayer player, double power) {
        ServerLevel level = player.level();
        LivingEntity hit = target(player, reach(24));
        Vec3 from = eye(player).add(player.getLookAngle().scale(0.6)).subtract(0, 0.2, 0);
        Vec3 to = hit != null ? hit.position().add(0, hit.getBbHeight() * 0.6, 0) : lookPoint(player, reach(24));
        com.lealex.alchyforbidden.network.SpellFxPayload.send(level, com.lealex.alchyforbidden.network.SpellFxPayload.RIFT, from, -1, 12, 0.5F);
        line(level, from, to, new DustParticleOptions(Spell.RIFT_BOLT.color(), 1.0F), 0.35);
        line(level, from, to, ModRegistries.RIFT_MOTE.get(), 1.2);
        if (hit != null) {
            hit.hurtServer(level, player.damageSources().indirectMagic(player, player), dmg(6 * power));
            level.sendParticles(ModRegistries.WRATH_MOTE.get(), to.x, to.y, to.z, 12, 0.25, 0.25, 0.25, 0.05);
        }
        sound(player, SoundEvents.BLAZE_SHOOT, 0.7F, 0.6F);
        return true;
    }

    /** Sorrow: a step through the mist to where the player looks (up to ten blocks), landing on room for a body. */
    private static boolean mistStep(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 look = player.getLookAngle();
        Vec3 to = lookPoint(player, reach(10)).subtract(look.scale(0.6));
        BlockPos feet = BlockPos.containing(to.x, to.y - 0.9, to.z);
        for (int up = 0; up < 3; up++) { // room for the body there (or a little above)
            BlockPos at = feet.above(up);
            if (level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()) {
                Vec3 start = player.position();
                level.sendParticles(ModRegistries.SORROW_MOTE.get(), start.x, start.y + 1, start.z, 20, 0.3, 0.6, 0.3, 0.03);
                level.sendParticles(ParticleTypes.CLOUD, start.x, start.y + 0.5, start.z, 8, 0.3, 0.3, 0.3, 0.02);
                player.teleportTo(to.x, at.getY(), to.z);
                player.resetFallDistance();
                level.sendParticles(ModRegistries.SORROW_MOTE.get(), to.x, at.getY() + 1, to.z, 20, 0.3, 0.6, 0.3, 0.03);
                sound(player, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 0.6F);
                return true;
            }
        }
        player.sendSystemMessage(Component.translatable("spell.alchyforbidden.mist_step.no_room").withStyle(ChatFormatting.GRAY), true);
        return false;
    }

    /** Sorrow: the souls close round the body (absorption, resistance for a while). */
    private static boolean soulShield(ServerPlayer player, double power) {
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticks(200), power >= 2.0 ? 2 : 1));
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ticks(100), 0));
        ServerLevel level = player.level();
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX() + Math.cos(a) * 0.9, player.getY() + 0.2 + (i % 4) * 0.45,
                    player.getZ() + Math.sin(a) * 0.9, 1, 0, 0.02, 0, 0.01);
        }
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 10, 0.4, 0.6, 0.4, 0.02);
        sound(player, SoundEvents.SOUL_ESCAPE.value(), 1.0F, 0.7F);
        return true;
    }

    /** Wrath: a line of fangs bursting out of the ground ahead, ten blocks. */
    private static boolean sculkFangs(ServerPlayer player) {
        ServerLevel level = player.level();
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double dx = -Mth.sin(yaw), dz = Mth.cos(yaw);
        for (int i = 1, n = (int) reach(10); i <= n; i++) {
            double x = player.getX() + dx * i * 1.1, z = player.getZ() + dz * i * 1.1;
            BlockPos ground = BlockPos.containing(x, player.getY() + 1, z);
            for (int down = 0; down < 4 && level.getBlockState(ground.below()).getCollisionShape(level, ground.below()).isEmpty(); down++) ground = ground.below();
            level.addFreshEntity(new EvokerFangs(level, x, ground.getY(), z, player.getYRot() * Mth.DEG_TO_RAD, i * 2, player));
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, x, ground.getY() + 0.1, z, 3, 0.2, 0.05, 0.2, 0.01);
        }
        sound(player, SoundEvents.EVOKER_CAST_SPELL, 0.8F, 0.7F);
        return true;
    }

    /** Sorrow: a wail that weighs on every creature near (slowness, weakness). */
    private static boolean wail(ServerPlayer player, double power) {
        ServerLevel level = player.level();
        double reach = reach(8 * Math.sqrt(power));
        int ticks = ticks(120 * power);
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach), e -> strikes(player, e))) {
            mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 1), player);
            mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0), player);
            level.sendParticles(ParticleTypes.SOUL, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 3, 0.2, 0.2, 0.2, 0.02);
        }
        for (int ring = 1; ring <= 3; ring++) {
            for (int i = 0; i < 20 * ring; i++) {
                double a = i * Math.PI * 2 / (20 * ring), r = reach * ring / 3;
                level.sendParticles(ModRegistries.SORROW_MOTE.get(), player.getX() + Math.cos(a) * r, player.getY() + 1, player.getZ() + Math.sin(a) * r, 1, 0, 0.02, 0, 0);
            }
        }
        sound(player, SoundEvents.GHAST_HURT, 1.0F, 0.4F);
        return true;
    }

    /** Corruption: amethyst and iron chains out of the rift bind the creature looked at, holding it where it stands. */
    private static boolean riftChains(ServerPlayer player, double power) {
        LivingEntity target = target(player, reach(16));
        if (target == null) {
            player.sendSystemMessage(Component.translatable("spell.alchyforbidden.no_target").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        ServerLevel level = player.level();
        int ticks = ticks(80 * power);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 9), player);
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1), player);
        HOLDS.add(new Hold(level, target, level.getGameTime() + ticks));
        com.lealex.alchyforbidden.network.SpellFxPayload.send(level, com.lealex.alchyforbidden.network.SpellFxPayload.CHAINS, target.position(), target.getId(), ticks, 1F);
        level.sendParticles(ModRegistries.RIFT_MOTE.get(), target.getX(), target.getY() + 2, target.getZ(), 24, 1.5, 0.8, 1.5, 0.02);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.2F, 0.5F);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.3F, 1.8F);
        sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 0.6F);
        return true;
    }

    /** Corruption: a small black hole opens where the player looks, pulling every creature round it in, hurting them. */
    private static boolean blackHole(ServerPlayer player, double power) {
        ServerLevel level = player.level();
        Vec3 at = lookPoint(player, reach(16)).subtract(player.getLookAngle().scale(1.0)).add(0, 0.8, 0);
        VORTICES.add(new Vortex(level, at, player, level.getGameTime() + ticks(80), reach(6 + power), power * damageFactor));
        com.lealex.alchyforbidden.network.SpellFxPayload.send(level, com.lealex.alchyforbidden.network.SpellFxPayload.HOLE, at, -1, ticks(80), (float) (1.0 + 0.25 * power));
        level.playSound(null, at.x, at.y, at.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.6F, 1.6F);
        return true;
    }

    /** Corruption: the warden's own scream from the body, through everything in a line. */
    private static boolean sonicBoom(ServerPlayer player, double power) {
        ServerLevel level = player.level();
        Vec3 from = eye(player).subtract(0, 0.2, 0), dir = player.getLookAngle();
        List<LivingEntity> struck = new ArrayList<>();
        for (int i = 1, n = (int) reach(15); i <= n; i++) {
            Vec3 p = from.add(dir.scale(i));
            level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(1.0),
                    e -> strikes(player, e) && !struck.contains(e))) {
                struck.add(living);
            }
        }
        for (LivingEntity living : struck) {
            living.hurtServer(level, player.damageSources().sonicBoom(player), dmg(10 * power));
            living.push(dir.x * 1.5, 0.3, dir.z * 1.5);
        }
        sound(player, SoundEvents.WARDEN_SONIC_BOOM, 1.5F, 1.0F);
        return true;
    }

    /** A creature the black hole can take whole: not a boss, at or under 20% of its health per power (at most half). */
    private static boolean executable(LivingEntity living, double power) {
        if (living.getType().builtInRegistryHolder().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES)) return false;
        double share = Math.min(0.5, 0.2 * power);
        return living.getHealth() <= living.getMaxHealth() * share;
    }

    /** The rift takes it: dead at once, crushed in a burst of the void. */
    private static void execute(ServerLevel level, Vortex vortex, LivingEntity living) {
        Vec3 p = living.position().add(0, living.getBbHeight() * 0.5, 0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 30, 0.3, 0.4, 0.3, 0.15);
        level.sendParticles(ParticleTypes.SCULK_SOUL, p.x, p.y, p.z, 6, 0.2, 0.3, 0.2, 0.05);
        level.sendParticles(ModRegistries.CORRUPTION_MOTE.get(), p.x, p.y, p.z, 14, 0.3, 0.3, 0.3, 0.03);
        line(level, p, vortex.at(), ModRegistries.RIFT_MOTE.get(), 0.3);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, p.x, p.y, p.z, SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.PLAYERS, 0.4F, 1.6F);
        living.hurtServer(level, vortex.owner().damageSources().indirectMagic(vortex.owner(), vortex.owner()), Float.MAX_VALUE);
        if (living.isAlive()) living.kill(level);
    }

    /** Corruption: a pulse of the sculk's sense: every creature within reach glows (seen through walls) for a while. */
    private static boolean echoPulse(ServerPlayer player, double power) {
        ServerLevel level = player.level();
        double reach = reach(24 * Math.sqrt(power));
        int ticks = ticks(200 * power);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach), e -> strikes(player, e))) {
            living.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0), player);
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, living.getX(), living.getY() + living.getBbHeight(), living.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
        }
        for (int ring = 1; ring <= 4; ring++) {
            int n = 16 * ring;
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n, r = reach * ring / 4 * 0.5;
                level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX() + Math.cos(a) * r, player.getY() + 0.2, player.getZ() + Math.sin(a) * r, 1, 0, 0.02, 0, 0);
            }
        }
        sound(player, SoundEvents.SCULK_CLICKING, 1.2F, 0.6F);
        sound(player, SoundEvents.SCULK_CLICKING_STOP, 1.0F, 0.8F);
        return true;
    }

    /** Sorrow: the soul of the creature looked at is pulled out through the air into the body, healing it. */
    private static boolean soulDrain(ServerPlayer player, double power) {
        LivingEntity target = target(player, reach(16));
        if (target == null) {
            player.sendSystemMessage(Component.translatable("spell.alchyforbidden.no_target").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        ServerLevel level = player.level();
        float damage = dmg(6 * power);
        float before = target.getHealth();
        target.hurtServer(level, player.damageSources().indirectMagic(player, player), damage);
        float taken = Math.max(0, before - Math.max(0, target.getHealth()));
        player.heal(taken * 0.6F);
        Vec3 from = target.position().add(0, target.getBbHeight() * 0.6, 0), to = player.position().add(0, 1.1, 0);
        line(level, from, to, ParticleTypes.SOUL, 0.5);
        line(level, from, to, ModRegistries.SORROW_MOTE.get(), 0.35);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, to.x, to.y, to.z, 10, 0.3, 0.4, 0.3, 0.02);
        sound(player, SoundEvents.SOUL_ESCAPE.value(), 1.2F, 0.5F);
        return true;
    }

    /** Wrath: the body bursts into a ring of fire: everything round it burns, is hurt and thrown back. */
    private static boolean wrathNova(ServerPlayer player, double power) {
        ServerLevel level = player.level();
        double reach = reach(5 * Math.sqrt(power));
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach),
                e -> strikes(player, e) && e.distanceTo(player) <= reach)) {
            living.hurtServer(level, player.damageSources().indirectMagic(player, player), dmg(5 * power));
            living.igniteForSeconds(dmg(4 * power));
            Vec3 away = living.position().subtract(player.position()).multiply(1, 0, 1).normalize();
            living.push(away.x * 1.2, 0.45, away.z * 1.2);
            living.hurtMarked = true;
        }
        for (int ring = 1; ring <= 3; ring++) {
            int n = 18 * ring;
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n, r = reach * ring / 3;
                level.sendParticles(ParticleTypes.FLAME, player.getX() + Math.cos(a) * r, player.getY() + 0.3, player.getZ() + Math.sin(a) * r, 1,
                        Math.cos(a) * 0.05, 0.05, Math.sin(a) * 0.05, 0.02);
            }
        }
        level.sendParticles(ModRegistries.WRATH_MOTE.get(), player.getX(), player.getY() + 1, player.getZ(), 30, 0.8, 0.6, 0.8, 0.08);
        level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.5, player.getZ(), 8, 0.5, 0.2, 0.5, 0);
        sound(player, SoundEvents.BLAZE_SHOOT, 1.2F, 0.4F);
        sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.4F);
        return true;
    }

    /** Corruption: a rift opens before the body and another where it looks (up to 40 blocks): it steps through. */
    private static boolean riftGate(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 look = player.getLookAngle();
        Vec3 to = lookPoint(player, reach(40)).subtract(look.scale(0.8));
        BlockPos feet = BlockPos.containing(to.x, to.y - 0.9, to.z);
        for (int up = 0; up < 3; up++) {
            BlockPos at = feet.above(up);
            if (level.getBlockState(at).getCollisionShape(level, at).isEmpty() && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()) {
                Vec3 start = player.position().add(look.multiply(1, 0, 1).normalize().scale(0.8)).add(0, 1.0, 0);
                Vec3 end = new Vec3(to.x, at.getY() + 1.0, to.z);
                com.lealex.alchyforbidden.network.SpellFxPayload.send(level, com.lealex.alchyforbidden.network.SpellFxPayload.RIFT, start, -1, 30, 1.4F);
                com.lealex.alchyforbidden.network.SpellFxPayload.send(level, com.lealex.alchyforbidden.network.SpellFxPayload.RIFT, end, -1, 30, 1.4F);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, start.x, start.y, start.z, 30, 0.3, 0.6, 0.3, 0.1);
                player.teleportTo(to.x, at.getY(), to.z);
                player.resetFallDistance();
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, end.x, end.y, end.z, 30, 0.3, 0.6, 0.3, 0.1);
                level.playSound(null, start.x, start.y, start.z, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.25F, 1.6F);
                level.playSound(null, end.x, end.y, end.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.5F);
                return true;
            }
        }
        player.sendSystemMessage(Component.translatable("spell.alchyforbidden.mist_step.no_room").withStyle(ChatFormatting.GRAY), true);
        return false;
    }

    /** Wrath: blood for strength: 6 health paid (never the last), then Strength II, Speed II and Haste for a while. */
    private static boolean bloodRite(ServerPlayer player, double power) {
        if (player.getHealth() <= 7 && !player.getAbilities().instabuild) {
            player.sendSystemMessage(Component.translatable("spell.alchyforbidden.blood_rite.weak").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        ServerLevel level = player.level();
        if (!player.getAbilities().instabuild) player.hurtServer(level, player.damageSources().magic(), 6.0F);
        int ticks = ticks(300 * power);
        player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, ticks, 1));
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1));
        player.addEffect(new MobEffectInstance(MobEffects.HASTE, ticks, 1));
        level.sendParticles(new DustParticleOptions(Spell.BLOOD_RITE.color(), 1.4F), player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.7, 0.4, 0);
        level.sendParticles(ModRegistries.WRATH_MOTE.get(), player.getX(), player.getY() + 1, player.getZ(), 20, 0.5, 0.6, 0.5, 0.05);
        sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.5F, 1.2F);
        sound(player, SoundEvents.RAVAGER_ROAR, 0.5F, 1.4F);
        return true;
    }

    /** Who leapt and has not landed yet: their landing hurts nothing. */
    private static final java.util.Set<java.util.UUID> LEAPING = new java.util.HashSet<>();

    /** Wrath: a great leap the way the body looks, its landing soft. */
    private static boolean greatLeap(ServerPlayer player, double power) {
        Vec3 look = player.getLookAngle();
        double push = 1.4 * Math.sqrt(power) * Math.sqrt(rangeFactor);
        player.setDeltaMovement(look.x * push, Math.max(0.55, look.y * push + 0.5), look.z * push);
        player.hurtMarked = true;
        LEAPING.add(player.getUUID());
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 12, 0.4, 0.05, 0.4, 0.05);
        level.sendParticles(ModRegistries.WRATH_MOTE.get(), player.getX(), player.getY() + 0.3, player.getZ(), 10, 0.3, 0.1, 0.3, 0.05);
        sound(player, SoundEvents.BREEZE_JUMP, 1.0F, 0.7F);
        return true;
    }

    @SubscribeEvent
    public static void onFall(net.neoforged.neoforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && LEAPING.remove(player.getUUID())) event.setDamageMultiplier(0);
    }

    /** Sorrow: the body drifts down like a soul (slow falling for a while). */
    private static boolean driftingSoul(ServerPlayer player, double power) {
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, ticks(600 * power), 0));
        player.level().sendParticles(ModRegistries.SORROW_MOTE.get(), player.getX(), player.getY() + 1, player.getZ(), 16, 0.4, 0.6, 0.4, 0.02);
        sound(player, SoundEvents.SOUL_ESCAPE.value(), 0.8F, 1.3F);
        return true;
    }

    /** Sorrow: the body wraps itself in shadow: invisible for a while, and the creatures hunting it lose it. */
    private static boolean shadowVeil(ServerPlayer player, double power) {
        ServerLevel level = player.level();
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks(600 * power), 0, false, false, true));
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24), m -> m.getTarget() == player)) mob.setTarget(null);
        level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1, player.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.5, player.getZ(), 12, 0.4, 0.4, 0.4, 0.01);
        sound(player, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 0.6F);
        return true;
    }

    /** Corruption: a small rift to the body's own pocket of the void: its ender chest, wherever it stands. */
    private static boolean riftPocket(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 at = eye(player).add(player.getLookAngle().scale(1.2)).subtract(0, 0.3, 0);
        com.lealex.alchyforbidden.network.SpellFxPayload.send(level, com.lealex.alchyforbidden.network.SpellFxPayload.RIFT, at, -1, 40, 0.6F);
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, p) ->
                net.minecraft.world.inventory.ChestMenu.threeRows(id, inventory, p.getEnderChestInventory()), Component.translatable("container.enderchest")));
        sound(player, SoundEvents.ENDER_CHEST_OPEN, 1.0F, 0.7F);
        return true;
    }

    private static final String ANCHOR = AlchyForbidden.MODID + ":rift_anchor", ANCHOR_WORLD = AlchyForbidden.MODID + ":rift_anchor_world";

    /** Corruption: sneaking, the body drops an anchor where it stands (free); otherwise a rift takes it back to the anchor (same world). */
    private static boolean riftAnchor(ServerPlayer player) {
        ServerLevel level = player.level();
        var data = player.getPersistentData();
        String world = level.dimension().identifier().toString();
        if (player.isShiftKeyDown()) {
            data.putLong(ANCHOR, player.blockPosition().asLong());
            data.putString(ANCHOR_WORLD, world);
            level.sendParticles(ModRegistries.RIFT_MOTE.get(), player.getX(), player.getY() + 0.2, player.getZ(), 30, 0.6, 0.05, 0.6, 0.01);
            sound(player, SoundEvents.LODESTONE_COMPASS_LOCK, 1.0F, 0.7F);
            player.sendSystemMessage(Component.translatable("spell.alchyforbidden.rift_anchor.set"), true);
            return false; // setting it costs nothing
        }
        if (!data.contains(ANCHOR) || !world.equals(data.getStringOr(ANCHOR_WORLD, ""))) {
            player.sendSystemMessage(Component.translatable("spell.alchyforbidden.rift_anchor.none").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        BlockPos to = BlockPos.of(data.getLongOr(ANCHOR, 0L));
        Vec3 from = player.position().add(0, 1, 0);
        com.lealex.alchyforbidden.network.SpellFxPayload.send(level, com.lealex.alchyforbidden.network.SpellFxPayload.RIFT, from, -1, 30, 1.6F);
        com.lealex.alchyforbidden.network.SpellFxPayload.send(level, com.lealex.alchyforbidden.network.SpellFxPayload.RIFT, Vec3.atBottomCenterOf(to).add(0, 1, 0), -1, 30, 1.6F);
        player.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        player.resetFallDistance();
        level.playSound(null, from.x, from.y, from.z, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.25F, 1.4F);
        level.playSound(null, to, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.5F);
        return true;
    }

    // ---- The second wave ----

    /** Wrath: a spear of bone flung the way the body looks, through the first thing it meets. */
    private static boolean boneSpear(ServerPlayer player, double power) {
        ServerLevel level = player.level();
        LivingEntity hit = target(player, reach(20));
        Vec3 from = eye(player).add(player.getLookAngle().scale(0.6)).subtract(0, 0.2, 0);
        Vec3 to = hit != null ? hit.position().add(0, hit.getBbHeight() * 0.6, 0) : lookPoint(player, reach(20));
        line(level, from, to, new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.BONE_BLOCK.defaultBlockState()), 0.3);
        line(level, from, to, ParticleTypes.CRIT, 0.8);
        if (hit != null) {
            hit.hurtServer(level, player.damageSources().indirectMagic(player, player), dmg(5 * power));
            Vec3 dir = player.getLookAngle();
            hit.push(dir.x * 0.8, 0.2, dir.z * 0.8);
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.BONE_BLOCK.defaultBlockState()), to.x, to.y, to.z, 16, 0.2, 0.2, 0.2, 0.15);
        }
        sound(player, SoundEvents.SKELETON_SHOOT, 1.0F, 0.6F);
        sound(player, SoundEvents.BONE_BLOCK_BREAK, 1.0F, 0.7F);
        return true;
    }

    /** Corruption: the body retches a cone of sculk bile: poisoned, slowed, hurt. */
    private static boolean bileSpray(ServerPlayer player, double power) {
        ServerLevel level = player.level();
        Vec3 look = player.getLookAngle(), from = eye(player).subtract(0, 0.3, 0);
        double reach = reach(6);
        for (int i = 0; i < 40; i++) { // the spray
            Vec3 dir = look.add((player.getRandom().nextDouble() - 0.5) * 0.6, (player.getRandom().nextDouble() - 0.5) * 0.4, (player.getRandom().nextDouble() - 0.5) * 0.6).normalize();
            double d = player.getRandom().nextDouble() * reach;
            Vec3 p = from.add(dir.scale(d));
            level.sendParticles(i % 3 == 0 ? ParticleTypes.ITEM_SLIME : new DustParticleOptions(0x6A9A20, 1.2F), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
        }
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach), e -> strikes(player, e))) {
            Vec3 to = living.position().add(0, living.getBbHeight() * 0.5, 0).subtract(from);
            if (to.length() > reach || to.normalize().dot(look) < 0.8) continue;
            living.hurtServer(level, player.damageSources().indirectMagic(player, player), dmg(2 * power));
            living.addEffect(new MobEffectInstance(MobEffects.POISON, ticks(100 * power), 1), player);
            living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks(80 * power), 1), player);
        }
        sound(player, SoundEvents.PLAYER_BURP, 1.2F, 0.5F);
        sound(player, SoundEvents.SLIME_SQUISH, 1.0F, 0.5F);
        return true;
    }

    private record Light(ServerLevel level, BlockPos pos, long until) {}

    private static final List<Light> LIGHTS = new ArrayList<>();

    /** Sorrow: a wisp of soul light set where the body looks; it fades after a minute. */
    private static boolean wispLight(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 at = lookPoint(player, reach(16)).subtract(player.getLookAngle().scale(0.5));
        BlockPos pos = BlockPos.containing(at);
        if (!level.getBlockState(pos).isAir() || !level.mayInteract(player, pos)) return false;
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.LIGHT.defaultBlockState(), 3);
        LIGHTS.add(new Light(level, pos, level.getGameTime() + ticks(1200)));
        level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.2, 0.2, 0.2, 0.03);
        sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.6F);
        return true;
    }

    private record Zone(ServerLevel level, Vec3 at, ServerPlayer owner, long until, double radius) {}

    private static final List<Zone> SNARES = new ArrayList<>();

    /** Corruption: a patch of sculk spreads where the body looks: whatever walks in is held fast. */
    private static boolean sculkSnare(ServerPlayer player, double power) {
        ServerLevel level = player.level();
        Vec3 at = lookPoint(player, reach(16));
        SNARES.add(new Zone(level, at, player, level.getGameTime() + ticks(400), reach(2.5)));
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, at.x, at.y + 0.1, at.z, 30, 1.2, 0.05, 1.2, 0.01);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 1.2F, 0.6F);
        return true;
    }

    /** Wrath: the creature looked at bursts from within; if it dies it explodes on everything round it. */
    private static boolean goreBurst(ServerPlayer player, double power) {
        LivingEntity target = target(player, reach(16));
        if (target == null) {
            player.sendSystemMessage(Component.translatable("spell.alchyforbidden.no_target").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        ServerLevel level = player.level();
        target.hurtServer(level, player.damageSources().indirectMagic(player, player), dmg(8 * power));
        Vec3 m = target.position().add(0, target.getBbHeight() * 0.5, 0);
        var blood = new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
        level.sendParticles(blood, m.x, m.y, m.z, 20, 0.3, 0.4, 0.3, 0.2);
        if (!target.isAlive()) { // it bursts
            level.sendParticles(blood, m.x, m.y, m.z, 60, 0.6, 0.6, 0.6, 0.35);
            level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.BONE_BLOCK.defaultBlockState()), m.x, m.y, m.z, 20, 0.5, 0.5, 0.5, 0.3);
            level.sendParticles(ParticleTypes.EXPLOSION, m.x, m.y, m.z, 2, 0.3, 0.3, 0.3, 0);
            for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(reach(4)), e -> strikes(player, e))) {
                near.hurtServer(level, player.damageSources().indirectMagic(player, player), dmg(6 * power));
                Vec3 away = near.position().subtract(m).multiply(1, 0, 1).normalize();
                near.push(away.x, 0.4, away.z);
            }
            level.playSound(null, m.x, m.y, m.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
            level.playSound(null, m.x, m.y, m.z, SoundEvents.SLIME_DEATH, SoundSource.PLAYERS, 1.5F, 0.5F);
        }
        sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.2F, 1.6F);
        return true;
    }

    private record Delayed(ServerLevel level, Vec3 at, ServerPlayer owner, long when, double power, double reach) {}

    private static final List<Delayed> METEORS = new ArrayList<>();

    /** Wrath: a rift tears open high over where the body looks and a burning stone falls out of it. */
    private static boolean riftMeteor(ServerPlayer player, double power) {
        ServerLevel level = player.level();
        Vec3 at = lookPoint(player, reach(30));
        METEORS.add(new Delayed(level, at, player, level.getGameTime() + 25, power * damageFactor, reach(4)));
        com.lealex.alchyforbidden.network.SpellFxPayload.send(level, com.lealex.alchyforbidden.network.SpellFxPayload.RIFT, at.add(0, 12, 0), -1, 40, 2.4F);
        level.playSound(null, at.x, at.y + 12, at.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.6F, 0.5F);
        return true;
    }

    /** Sorrow: time stops round the body: every creature near is frozen where it stands. */
    private static boolean stasisField(ServerPlayer player, double power) {
        ServerLevel level = player.level();
        double reach = reach(10);
        int ticks = ticks(100 * power);
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach), e -> strikes(player, e))) {
            mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 9), player);
            mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 3), player);
            mob.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, ticks, 3), player);
            HOLDS.add(new Hold(level, mob, level.getGameTime() + ticks));
            level.sendParticles(ParticleTypes.END_ROD, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 4, 0.2, 0.2, 0.2, 0.01);
        }
        for (int ring = 1; ring <= 3; ring++) {
            for (int i = 0; i < 24 * ring; i++) {
                double a = i * Math.PI * 2 / (24 * ring), r = reach * ring / 3;
                level.sendParticles(ParticleTypes.ENCHANT, player.getX() + Math.cos(a) * r, player.getY() + 1, player.getZ() + Math.sin(a) * r, 1, 0, 0.05, 0, 0);
            }
        }
        sound(player, SoundEvents.BELL_RESONATE, 1.2F, 0.6F);
        sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.5F);
        return true;
    }

    /** Sorrow: a pulse of growth: every plant round the body grows as under bone meal, twice. */
    private static boolean verdantPulse(ServerPlayer player) {
        ServerLevel level = player.level();
        int reach = (int) Math.round(reach(5));
        int grown = 0;
        for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-reach, -2, -reach), player.blockPosition().offset(reach, 2, reach))) {
            var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock plant) || state.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) continue;
            for (int k = 0; k < 2; k++) {
                state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock now) || !now.isValidBonemealTarget(level, pos, state)) break;
                now.performBonemeal(level, level.getRandom(), pos.immutable(), state);
                grown++;
            }
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0);
        }
        if (grown == 0) return false;
        sound(player, SoundEvents.BONE_MEAL_USE, 1.2F, 0.8F);
        return true;
    }

    /** Corruption: a rift bores a tunnel three wide and three high ahead of the body (its blocks dropped). */
    private static boolean riftBore(ServerPlayer player) {
        ServerLevel level = player.level();
        net.minecraft.core.Direction facing = net.minecraft.core.Direction.getApproximateNearest(player.getLookAngle());
        net.minecraft.core.Direction side = facing.getAxis().isHorizontal() ? facing.getClockWise() : net.minecraft.core.Direction.EAST;
        net.minecraft.core.Direction up = facing.getAxis().isHorizontal() ? net.minecraft.core.Direction.UP : net.minecraft.core.Direction.NORTH;
        BlockPos start = BlockPos.containing(eye(player)).relative(facing);
        int length = (int) Math.round(reach(6)), bored = 0;
        for (int d = 0; d < length; d++) {
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    BlockPos pos = start.relative(facing, d).relative(side, a).relative(up, b);
                    var state = level.getBlockState(pos);
                    if (state.isAir() || level.getBlockEntity(pos) != null || state.getDestroySpeed(level, pos) < 0 || state.getDestroySpeed(level, pos) > 30
                            || !level.mayInteract(player, pos)) continue;
                    level.destroyBlock(pos, true, player);
                    bored++;
                    if (bored % 3 == 0) level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.05);
                }
            }
        }
        if (bored == 0) return false;
        com.lealex.alchyforbidden.network.SpellFxPayload.send(level, com.lealex.alchyforbidden.network.SpellFxPayload.RIFT, Vec3.atCenterOf(start), -1, 20, 1.6F);
        sound(player, SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 1.6F);
        return true;
    }

    private record Plague(ServerLevel level, LivingEntity victim, ServerPlayer owner, long until, double power) {}

    private static final List<Plague> PLAGUES = new ArrayList<>();
    /** The most creatures the plague burns in at once (it jumps on every death: a farm would feed it without end). */
    private static final int MAX_PLAGUES = 64;

    /** Corruption: sculk takes root in every creature near: it rots them; when one dies the plague jumps on. */
    private static boolean sculkPlague(ServerPlayer player, double power) {
        ServerLevel level = player.level();
        long until = level.getGameTime() + ticks(200);
        int caught = 0;
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach(10)), e -> strikes(player, e))) {
            PLAGUES.add(new Plague(level, living, player, until, power * damageFactor));
            caught++;
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(), 40, 2, 1, 2, 0.05);
        sound(player, SoundEvents.SCULK_CATALYST_BLOOM, 1.5F, 0.5F);
        sound(player, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6F, 0.7F);
        return true; // (cast even on nothing: the cloud still spreads)
    }

    private record Storm(ServerLevel level, ServerPlayer owner, long until, double power, double reach) {}

    private static final List<Storm> STORMS = new ArrayList<>();

    /** Sorrow: a storm of souls round the body: for six seconds they fall on every monster near, feeding it back. */
    private static boolean soulStorm(ServerPlayer player, double power) {
        ServerLevel level = player.level();
        STORMS.add(new Storm(level, player, level.getGameTime() + ticks(120), power * damageFactor, reach(8)));
        sound(player, SoundEvents.SOUL_ESCAPE.value(), 1.5F, 0.4F);
        sound(player, SoundEvents.WITHER_AMBIENT, 0.5F, 1.6F);
        return true;
    }

    private record Aura(ServerPlayer owner, long until) {}

    private static final List<Aura> AURAS = new ArrayList<>();

    /** Corruption: the body becomes the rift's avatar for a while: stronger, harder, faster, healing, wreathed in it. */
    private static boolean abyssalForm(ServerPlayer player, double power) {
        int t = ticks(300 * power);
        player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, t, 2));
        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, t, 1));
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, t, 1));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, t, 1));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, t, 1));
        AURAS.add(new Aura(player, player.level().getGameTime() + t));
        com.lealex.alchyforbidden.network.SpellFxPayload.send(player.level(), com.lealex.alchyforbidden.network.SpellFxPayload.RIFT, player.position().add(0, 1, 0), -1, 30, 2.6F);
        player.level().sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 60, 0.6, 1, 0.6, 0.2);
        sound(player, SoundEvents.WARDEN_ROAR, 0.7F, 1.2F);
        return true;
    }

    private record Strike(ServerLevel level, ServerPlayer owner, LivingEntity target, long when, double power) {}

    private static final List<Strike> STRIKES = new ArrayList<>();
    /** How long a shadow takes to reach its prey (client/SpellFx dashes it over the same time) and how long it lasts. */
    public static final int SHADOW_DASH = 10, SHADOW_TICKS = 26;

    /** Wrath, Sorrow and Corruption: shadows of the body tear out of it and fall on the monsters round it, one each. */
    private static boolean shadowStrike(ServerPlayer player, double power) {
        ServerLevel level = player.level();
        int most = power >= 2.0 ? 5 : power >= 1.4 ? 4 : 3;
        List<LivingEntity> prey = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach(12)),
                e -> strikes(player, e));
        prey.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player)));
        if (prey.isEmpty()) {
            player.sendSystemMessage(Component.translatable("spell.alchyforbidden.no_target").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        for (int i = 0; i < Math.min(most, prey.size()); i++) {
            LivingEntity target = prey.get(i);
            long when = level.getGameTime() + SHADOW_DASH + i * 3L;
            STRIKES.add(new Strike(level, player, target, when, power * damageFactor));
            // the shadow: from beside the body to the prey (drawn by the clients), a little later each
            com.lealex.alchyforbidden.network.SpellFxPayload.send(level, com.lealex.alchyforbidden.network.SpellFxPayload.SHADOW,
                    player.position(), target.getId(), SHADOW_TICKS + i * 3, i * 3, player.getId());
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
        level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1, player.getZ(), 20, 0.5, 0.8, 0.5, 0.05);
        sound(player, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.2F, 0.6F);
        sound(player, SoundEvents.WARDEN_ROAR, 0.3F, 2.0F);
        return true;
    }

    /** Ticks the second wave's lasting spells (from onServerTick). */
    private static void tickWild() {
        for (Iterator<Strike> it = STRIKES.iterator(); it.hasNext(); ) {
            Strike strike = it.next();
            if (strike.level().getGameTime() < strike.when()) continue;
            it.remove();
            LivingEntity target = strike.target();
            if (!target.isAlive()) continue;
            ServerLevel level = strike.level();
            target.hurtServer(level, strike.owner().damageSources().indirectMagic(strike.owner(), strike.owner()), (float) (7 * strike.power()));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1), strike.owner());
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), strike.owner());
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 2, 0.2, 0.2, 0.2, 0);
            level.sendParticles(ParticleTypes.SQUID_INK, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
            level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
        for (Iterator<Light> it = LIGHTS.iterator(); it.hasNext(); ) {
            Light light = it.next();
            if (light.level().getGameTime() < light.until()) continue;
            if (light.level().getBlockState(light.pos()).is(net.minecraft.world.level.block.Blocks.LIGHT)) light.level().removeBlock(light.pos(), false);
            it.remove();
        }
        for (Iterator<Zone> it = SNARES.iterator(); it.hasNext(); ) {
            Zone zone = it.next();
            ServerLevel level = zone.level();
            long now = level.getGameTime();
            if (now > zone.until()) {
                it.remove();
                continue;
            }
            Vec3 c = zone.at();
            if (now % 4 == 0) {
                for (int i = 0; i < 6; i++) {
                    double a = (now * 0.2 + i) % (Math.PI * 2), r = zone.radius() * level.getRandom().nextDouble();
                    level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, c.x + Math.cos(a) * r, c.y + 0.05, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
            for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(zone.radius(), 1.5, zone.radius()), e -> strikes(zone.owner(), e))) {
                mob.setDeltaMovement(0, Math.min(0, mob.getDeltaMovement().y), 0);
                mob.hurtMarked = true;
                mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20, 9), zone.owner());
                if (now % 10 == 0) level.sendParticles(ParticleTypes.SCULK_SOUL, mob.getX(), mob.getY() + 0.2, mob.getZ(), 1, 0.2, 0.1, 0.2, 0.01);
            }
        }
        for (Iterator<Delayed> it = METEORS.iterator(); it.hasNext(); ) {
            Delayed meteor = it.next();
            ServerLevel level = meteor.level();
            long now = level.getGameTime(), left = meteor.when() - now;
            Vec3 c = meteor.at();
            if (left > 0) { // falling out of the rift
                double y = c.y + 12 * (left / 25.0);
                level.sendParticles(ParticleTypes.FLAME, c.x, y, c.z, 8, 0.3, 0.3, 0.3, 0.02);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, y + 0.5, c.z, 3, 0.2, 0.2, 0.2, 0.01);
                level.sendParticles(ParticleTypes.LAVA, c.x, y, c.z, 1, 0.2, 0.2, 0.2, 0);
                continue;
            }
            it.remove();
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 30, 1.5, 0.5, 1.5, 0);
            level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 60, 2, 0.5, 2, 0.1);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 0.6F);
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(meteor.reach()), e -> strikes(meteor.owner(), e))) {
                living.hurtServer(level, meteor.owner().damageSources().indirectMagic(meteor.owner(), meteor.owner()), (float) (10 * meteor.power()));
                living.igniteForSeconds(5);
                Vec3 away = living.position().subtract(c).multiply(1, 0, 1).normalize();
                living.push(away.x * 1.2, 0.7, away.z * 1.2);
                living.hurtMarked = true;
            }
        }
        java.util.List<Plague> spread = new ArrayList<>();
        for (Iterator<Plague> it = PLAGUES.iterator(); it.hasNext(); ) {
            Plague plague = it.next();
            ServerLevel level = plague.level();
            long now = level.getGameTime();
            LivingEntity victim = plague.victim();
            if (!victim.isAlive() || victim.isRemoved()) { // it died: the plague jumps to whatever is near
                if (now < plague.until()) {
                    java.util.Set<LivingEntity> sick = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                    for (Plague p : PLAGUES) sick.add(p.victim());
                    for (Plague p : spread) sick.add(p.victim());
                    for (LivingEntity next : level.getEntitiesOfClass(LivingEntity.class, victim.getBoundingBox().inflate(4),
                            e -> strikes(plague.owner(), e))) {
                        if (PLAGUES.size() + spread.size() >= MAX_PLAGUES) break;
                        if (sick.add(next)) spread.add(new Plague(level, next, plague.owner(), plague.until(), plague.power()));
                    }
                    level.sendParticles(ParticleTypes.SCULK_SOUL, victim.getX(), victim.getY() + 0.5, victim.getZ(), 20, 0.4, 0.4, 0.4, 0.08);
                }
                it.remove();
                continue;
            }
            if (now > plague.until()) {
                it.remove();
                continue;
            }
            if (now % 20 == 0) {
                victim.hurtServer(level, plague.owner().damageSources().indirectMagic(plague.owner(), plague.owner()), (float) (1.5 * plague.power()));
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0), plague.owner());
            }
            if (now % 5 == 0) {
                level.sendParticles(new DustParticleOptions(0x0A5A50, 1.0F), victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 3, 0.25, 0.3, 0.25, 0);
                level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, victim.getX(), victim.getY() + victim.getBbHeight() * 0.3, victim.getZ(), 1, 0.2, 0.2, 0.2, 0);
            }
        }
        PLAGUES.addAll(spread);
        for (Iterator<Storm> it = STORMS.iterator(); it.hasNext(); ) {
            Storm storm = it.next();
            ServerLevel level = storm.level();
            long now = level.getGameTime();
            ServerPlayer owner = storm.owner();
            if (now > storm.until() || !owner.isAlive() || owner.level() != level) {
                it.remove();
                continue;
            }
            for (int i = 0; i < 3; i++) { // souls swirling round the body
                double a = now * 0.3 + i * Math.PI * 2 / 3, r = 1.5 + Math.sin(now * 0.1) * 0.5;
                level.sendParticles(ParticleTypes.SOUL, owner.getX() + Math.cos(a) * r, owner.getY() + 1 + Math.sin(now * 0.2 + i), owner.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0.01);
            }
            if (now % 5 != 0) continue;
            java.util.List<LivingEntity> prey = level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(storm.reach()), e -> strikes(owner, e));
            for (int k = 0; k < 2 && !prey.isEmpty(); k++) {
                LivingEntity hit = prey.remove(level.getRandom().nextInt(prey.size()));
                hit.hurtServer(level, owner.damageSources().indirectMagic(owner, owner), (float) (3 * storm.power()));
                owner.heal(0.5F);
                for (int y = 0; y < 6; y++) level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, hit.getX(), hit.getY() + 4 - y * 0.6, hit.getZ(), 1, 0.05, 0, 0.05, 0);
                level.sendParticles(ParticleTypes.SOUL, hit.getX(), hit.getY() + 1, hit.getZ(), 4, 0.3, 0.3, 0.3, 0.05);
            }
            if (now % 20 == 0) level.playSound(null, owner.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.6F, 0.5F);
        }
        for (Iterator<Aura> it = AURAS.iterator(); it.hasNext(); ) {
            Aura aura = it.next();
            ServerPlayer owner = aura.owner();
            long now = owner.level().getGameTime();
            if (now > aura.until() || !owner.isAlive()) {
                it.remove();
                continue;
            }
            if (now % 2 == 0) {
                double a = now * 0.4;
                owner.level().sendParticles(ModRegistries.RIFT_MOTE.get(), owner.getX() + Math.cos(a) * 0.8, owner.getY() + 0.2 + (now % 20) * 0.1, owner.getZ() + Math.sin(a) * 0.8, 1, 0, 0, 0, 0);
                owner.level().sendParticles(ParticleTypes.PORTAL, owner.getX(), owner.getY() + 1, owner.getZ(), 2, 0.4, 0.6, 0.4, 0.3);
            }
        }
    }

    // ---- Lasting spells ----

    private record Hold(ServerLevel level, LivingEntity target, long until) {}

    private record Vortex(ServerLevel level, Vec3 at, ServerPlayer owner, long until, double reach, double power) {}

    private static final List<Hold> HOLDS = new ArrayList<>();
    private static final List<Vortex> VORTICES = new ArrayList<>();

    /** The server is stopping: wisp lights still burning are put out (they are not saved), and every lasting spell's
     *  list is emptied (they hold the levels and players of a world that is going away). */
    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (Light light : LIGHTS) {
            if (light.level().getBlockState(light.pos()).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
                light.level().setBlock(light.pos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        }
        LIGHTS.clear();
        SNARES.clear();
        METEORS.clear();
        PLAGUES.clear();
        STORMS.clear();
        AURAS.clear();
        STRIKES.clear();
        HOLDS.clear();
        VORTICES.clear();
        LEAPING.clear();
        Relays.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        tickWild();
        for (Iterator<Hold> it = HOLDS.iterator(); it.hasNext(); ) {
            Hold hold = it.next();
            long now = hold.level().getGameTime();
            if (now > hold.until() || !hold.target().isAlive() || hold.target().isRemoved()) {
                it.remove();
                continue;
            }
            LivingEntity target = hold.target();
            target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0); // held where it stands
            target.hurtMarked = true;
            if (now % 4 == 0) { // the chains round it
                double a = now * 0.4;
                for (int k = 0; k < 3; k++) {
                    double b = a + k * Math.PI * 2 / 3;
                    hold.level().sendParticles(new DustParticleOptions(Spell.RIFT_CHAINS.color(), 0.8F), target.getX() + Math.cos(b) * 0.6,
                            target.getY() + 0.2 + k * target.getBbHeight() / 3, target.getZ() + Math.sin(b) * 0.6, 1, 0, 0, 0, 0);
                }
            }
        }
        for (Iterator<Vortex> it = VORTICES.iterator(); it.hasNext(); ) {
            Vortex vortex = it.next();
            ServerLevel level = vortex.level();
            long now = level.getGameTime();
            if (now > vortex.until()) {
                // as it collapses it takes every weakened creature still in its pull
                for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(vortex.at(), vortex.at()).inflate(vortex.reach() * 0.5),
                        e -> strikes(vortex.owner(), e) && executable(e, vortex.power()))) {
                    execute(level, vortex, living);
                }
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, vortex.at().x, vortex.at().y, vortex.at().z, 40, 0.4, 0.4, 0.4, 0.3);
                level.playSound(null, vortex.at().x, vortex.at().y, vortex.at().z, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 0.8F, 0.5F);
                it.remove();
                continue;
            }
            Vec3 c = vortex.at();
            for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(vortex.reach()),
                    e -> strikes(vortex.owner(), e))) {
                Vec3 pull = c.subtract(living.position().add(0, living.getBbHeight() * 0.5, 0));
                double d = Math.max(0.6, pull.length());
                living.setDeltaMovement(living.getDeltaMovement().add(pull.normalize().scale(0.12 * Math.min(2.0, vortex.reach() / d))));
                living.hurtMarked = true;
                if (now % 20 == 0) living.hurtServer(level, vortex.owner().damageSources().indirectMagic(vortex.owner(), vortex.owner()), (float) (2 * vortex.power()));
                if (living.isAlive() && d < 1.6 && executable(living, vortex.power())) execute(level, vortex, living);
            }
            // drawn in from round it, spiralling, dark
            for (int k = 0; k < 4; k++) {
                double a = now * 0.35 + k * Math.PI / 2, r = 1.2 + (now % 10) * 0.12;
                level.sendParticles(ParticleTypes.PORTAL, c.x + Math.cos(a) * r, c.y + Math.sin(now * 0.2 + k) * 0.4, c.z + Math.sin(a) * r, 1,
                        -Math.cos(a) * 0.3, 0, -Math.sin(a) * 0.3, 0.6);
            }
            level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 2, 0.15, 0.15, 0.15, 0.0);
            level.sendParticles(ModRegistries.RIFT_MOTE.get(), c.x, c.y, c.z, 2, 0.3, 0.3, 0.3, 0.01);
            if (now % 20 == 0) level.playSound(null, c.x, c.y, c.z, SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.4F, 1.8F);
        }
    }
}
