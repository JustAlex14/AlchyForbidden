package com.lealex.alchyforbidden.essence;

import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.block.entity.AltarBlockEntity;
import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Body inscriptions: what the Inscribing Bier has carved into a player (attachment INSCRIPTIONS, one id per part,
 * saved, kept through death, synced to them). Each carving carried is a step of corruption, and corruption is power:
 * every carving beyond the first makes all of them Config.powerPerCarving percent stronger (power()).
 * Attributes (reach, speed, health...) are kept on the body as transient modifiers checked every second; effects
 * (night vision, resistance...) are hidden and kept topped up. The walkers and the chained wings move the player on
 * its own side (client/InscriptionClient); the chained wings are kept gliding here once the client asked (GlidePayload).
 */
public final class Inscriptions {
    private Inscriptions() {}

    /** Players gliding on Chained Wings (the server keeps their glide flag up, the game would drop it without an elytra). */
    private static final Set<UUID> GLIDING = new HashSet<>();
    private static final String GRANTED_FLIGHT = AlchyForbidden.MODID + ":granted_flight";

    private record Modifier(Inscription inscription, Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
        Identifier id() {
            return Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, inscription.id() + "_" + attribute.unwrapKey().map(k -> k.identifier().getPath()).orElse("x"));
        }
    }

    private static final List<Modifier> MODIFIERS = List.of(
            new Modifier(Inscription.DEEP_HEART, Attributes.MAX_HEALTH, 6.0, AttributeModifier.Operation.ADD_VALUE),
            new Modifier(Inscription.DEEP_HEART, Attributes.KNOCKBACK_RESISTANCE, 0.3, AttributeModifier.Operation.ADD_VALUE),
            new Modifier(Inscription.HOLLOW_LUNGS, Attributes.OXYGEN_BONUS, 4.0, AttributeModifier.Operation.ADD_VALUE),
            new Modifier(Inscription.LONG_REACH, Attributes.ENTITY_INTERACTION_RANGE, 2.0, AttributeModifier.Operation.ADD_VALUE),
            new Modifier(Inscription.LONG_REACH, Attributes.BLOCK_INTERACTION_RANGE, 2.0, AttributeModifier.Operation.ADD_VALUE),
            new Modifier(Inscription.QUICK_HANDS, Attributes.ATTACK_SPEED, 1.0, AttributeModifier.Operation.ADD_VALUE),
            new Modifier(Inscription.RIFT_GRIP, Attributes.BLOCK_BREAK_SPEED, 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
            new Modifier(Inscription.SWIFT_STRIDE, Attributes.MOVEMENT_SPEED, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
            new Modifier(Inscription.SWIFT_STRIDE, Attributes.STEP_HEIGHT, 0.5, AttributeModifier.Operation.ADD_VALUE),
            new Modifier(Inscription.HIGH_LEAP, Attributes.JUMP_STRENGTH, 0.2, AttributeModifier.Operation.ADD_VALUE),
            new Modifier(Inscription.HIGH_LEAP, Attributes.SAFE_FALL_DISTANCE, 4.0, AttributeModifier.Operation.ADD_VALUE),
            new Modifier(Inscription.HEAVY_HAND, Attributes.ATTACK_DAMAGE, 1.0, AttributeModifier.Operation.ADD_VALUE),
            new Modifier(Inscription.HEAVY_HAND, Attributes.ATTACK_KNOCKBACK, 0.8, AttributeModifier.Operation.ADD_VALUE),
            new Modifier(Inscription.BARK_SKIN, Attributes.ARMOR, 3.0, AttributeModifier.Operation.ADD_VALUE),
            new Modifier(Inscription.BARK_SKIN, Attributes.ARMOR_TOUGHNESS, 1.0, AttributeModifier.Operation.ADD_VALUE));

    // ---- What the body carries ----

    public static List<Inscription> of(Player player) {
        List<Inscription> out = new ArrayList<>();
        for (String id : player.getData(ModRegistries.INSCRIPTIONS)) {
            Inscription inscription = Inscription.byId(id);
            if (inscription != null) out.add(inscription);
        }
        return out;
    }

    /** The highest carving level (5 levels, 0..4). */
    public static final int MAX_LEVEL = 4;

    public static int carvingLevel(Player player) {
        return Math.max(0, Math.min(MAX_LEVEL, player.getData(ModRegistries.CARVING_LEVEL)));
    }

    /** How many carvings this body can carry: 3 a level (3 to 15), never over the config's cap. */
    public static int capacity(Player player) {
        return Math.min(Config.maxInscriptions(), 3 * (carvingLevel(player) + 1));
    }

    public static boolean has(Player player, Inscription inscription) {
        return player.getData(ModRegistries.INSCRIPTIONS).contains(inscription.id());
    }

    /** The carvings on one part of the body. */
    public static List<Inscription> on(Player player, Inscription.Part part) {
        List<Inscription> out = new ArrayList<>();
        for (Inscription inscription : of(player)) if (inscription.part() == part) out.add(inscription);
        return out;
    }

    /** How strong its carvings are: 1 with one, more with each carving beyond it. Either side. */
    public static double power(Player player) {
        return 1.0 + Math.max(0, corruption(player) - 1) * Config.powerPerCarving() / 100.0;
    }

    /** How corrupted the body is: one step per carving. */
    public static int corruption(Player player) {
        return of(player).size();
    }

    public static Component name(Inscription inscription) {
        return Component.translatable("inscription.alchyforbidden." + inscription.id()).withStyle(style -> style.withColor(inscription.color()));
    }

    /** Carves it in (the Bier checked the price). */
    public static void carve(ServerPlayer player, Inscription inscription) {
        List<String> now = new ArrayList<>(player.getData(ModRegistries.INSCRIPTIONS));
        if (now.contains(inscription.id())) return;
        now.add(inscription.id());
        player.setData(ModRegistries.INSCRIPTIONS, List.copyOf(now));
        refresh(player);
        int corruption = now.size();
        // one line over the hotbar, not the chat: the carving, and how strong the carvings now are
        net.minecraft.network.chat.MutableComponent line = Component.translatable("inscription.alchyforbidden.carved", name(inscription), corruption, Config.maxInscriptions());
        if (corruption > 1) line.append(" ").append(Component.translatable("inscription.alchyforbidden.stronger",
                String.format(java.util.Locale.ROOT, "%.0f", power(player) * 100)).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        player.sendSystemMessage(line, true);
    }

    /** Burns that carving away. */
    public static void burn(ServerPlayer player, Inscription old) {
        List<String> now = new ArrayList<>(player.getData(ModRegistries.INSCRIPTIONS));
        if (!now.remove(old.id())) return;
        player.setData(ModRegistries.INSCRIPTIONS, List.copyOf(now));
        refresh(player);
        player.sendSystemMessage(Component.translatable("inscription.alchyforbidden.burned", name(old), now.size(), Config.maxInscriptions()), true);
    }

    // ---- The essence reserve carved in the chest ----

    public static int reserve(Player player, Essence essence) {
        List<Integer> held = player.getData(ModRegistries.ESSENCE_RESERVE);
        return essence.ordinal() < held.size() ? held.get(essence.ordinal()) : 0;
    }

    /** How much of each essence the carved reserve holds (more the stronger the body). */
    public static int reserveCap(Player player) {
        return (int) Math.round(Config.reserveCapacity() * power(player));
    }

    /** Fills the reserve with what it has room for; returns what it took (0 without the carving). */
    public static int store(Player player, Essence essence, int amount) {
        if (amount <= 0 || !has(player, Inscription.ESSENCE_RESERVE)) return 0;
        int now = reserve(player, essence);
        int taken = Math.min(amount, reserveCap(player) - now);
        if (taken <= 0) return 0;
        setReserve(player, essence, now + taken);
        return taken;
    }

    /** Takes that much out of the reserve: true if it held it. */
    public static boolean spend(Player player, Essence essence, int amount) {
        int now = reserve(player, essence);
        if (amount <= 0 || now < amount) return false;
        setReserve(player, essence, now - amount);
        return true;
    }

    // ---- Upkeep: what the active carvings cost while they work ----

    /** True if the body can pay this carving's upkeep (once): its reserve plus its vessels. Either side; creative pays nothing. */
    public static boolean canPay(Player player, Inscription inscription) {
        Config.Upkeep upkeep = Config.upkeep(inscription);
        return upkeep == null || canPayEssence(player, upkeep.essence(), upkeep.amount());
    }

    /** True if the body holds that much of an essence in its reserve and vessels together (creative: always). Either side. */
    public static boolean canPayEssence(Player player, Essence essence, int amount) {
        return amount <= 0 || player.getAbilities().instabuild || reserve(player, essence) + inVessels(player, essence) >= amount;
    }

    /** Takes that much of an essence from the body: its reserve first, then the vessels it carries (server). False if it cannot. */
    public static boolean payEssence(Player player, Essence essence, int amount) {
        if (amount <= 0 || player.getAbilities().instabuild) return true;
        if (!canPayEssence(player, essence, amount)) return false;
        int left = amount;
        int fromReserve = Math.min(left, reserve(player, essence));
        if (fromReserve > 0) {
            setReserve(player, essence, reserve(player, essence) - fromReserve);
            left -= fromReserve;
        }
        for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            VesselContents contents = com.lealex.alchyforbidden.item.VesselItem.contents(stack);
            if (contents != null && contents.essence() == essence) left -= com.lealex.alchyforbidden.item.VesselItem.drain(stack, left);
        }
        return true;
    }

    /** Pays this carving's upkeep once (server): the reserve first, then the vessels carried. False (nothing taken) if it cannot. */
    public static boolean pay(Player player, Inscription inscription) {
        Config.Upkeep upkeep = Config.upkeep(inscription);
        return upkeep == null || payEssence(player, upkeep.essence(), upkeep.amount());
    }

    /** What the body itself holds of an essence: its carved reserve and the vessels it carries. */
    public static int carried(Player player, Essence essence) {
        return reserve(player, essence) + inVessels(player, essence);
    }

    private static int inVessels(Player player, Essence essence) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            VesselContents contents = com.lealex.alchyforbidden.item.VesselItem.contents(player.getInventory().getItem(i));
            if (contents != null && contents.essence() == essence) total += contents.amount();
        }
        return total;
    }

    /** Tells the body a carving stopped for want of its essence (action bar, at most every few seconds). */
    private static void starved(ServerPlayer player, Inscription inscription) {
        Config.Upkeep upkeep = Config.upkeep(inscription);
        if (upkeep == null || player.tickCount % 60 >= 20) return;
        player.sendSystemMessage(Component.translatable("inscription.alchyforbidden.starved", name(inscription), upkeep.essence().icon())
                .withStyle(net.minecraft.ChatFormatting.RED), true);
    }

    /** Once a second: the carvings at work pay for that second; those that cannot stop (server). */
    private static void upkeep(ServerPlayer player, List<String> carried) {
        if (player.getAbilities().instabuild) return;
        if (carried.contains(Inscription.ECHO_WINGS.id()) && player.getAbilities().flying && !pay(player, Inscription.ECHO_WINGS)) {
            player.getAbilities().flying = false; // refresh() takes the wings' flight away until it can be paid
            player.onUpdateAbilities();
            starved(player, Inscription.ECHO_WINGS);
        }
        if (GLIDING.contains(player.getUUID()) && !pay(player, Inscription.CHAINED_WINGS)) {
            GLIDING.remove(player.getUUID());
            player.stopFallFlying();
            starved(player, Inscription.CHAINED_WINGS);
        }
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
        net.minecraft.world.level.material.FluidState fluid = player.level().getFluidState(below);
        boolean onAir = player.onGround() && player.level().getBlockState(below).getCollisionShape(player.level(), below).isEmpty();
        if (onAir && fluid.isEmpty() && carried.contains(Inscription.SKY_WALKER.id()) && !pay(player, Inscription.SKY_WALKER)) starved(player, Inscription.SKY_WALKER);
        if (onAir && fluid.is(net.minecraft.tags.FluidTags.WATER) && carried.contains(Inscription.WATER_WALKER.id()) && !pay(player, Inscription.WATER_WALKER)) {
            starved(player, Inscription.WATER_WALKER);
        }
        if (onAir && fluid.is(net.minecraft.tags.FluidTags.LAVA) && carried.contains(Inscription.LAVA_WALKER.id()) && !pay(player, Inscription.LAVA_WALKER)) {
            starved(player, Inscription.LAVA_WALKER);
        }
        if (player.horizontalCollision && !player.onGround() && carried.contains(Inscription.WALL_CLIMBER.id()) && !pay(player, Inscription.WALL_CLIMBER)) {
            starved(player, Inscription.WALL_CLIMBER);
        }
    }

    /** Pours up to `amount` of an essence into a body's carved reserve (none without one); returns what it took. */
    public static int giveReserve(Player player, Essence essence, int amount) {
        if (amount <= 0 || !has(player, Inscription.ESSENCE_RESERVE)) return 0;
        int held = reserve(player, essence), taken = Math.max(0, Math.min(amount, reserveCap(player) - held));
        if (taken > 0) setReserve(player, essence, held + taken);
        return taken;
    }

    private static void setReserve(Player player, Essence essence, int value) {
        List<Integer> held = new ArrayList<>(player.getData(ModRegistries.ESSENCE_RESERVE));
        while (held.size() < Essence.values().length) held.add(0);
        held.set(essence.ordinal(), Math.max(0, value));
        player.setData(ModRegistries.ESSENCE_RESERVE, List.copyOf(held));
    }

    // ---- What they do ----

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator()) return;
        List<String> carried = player.getData(ModRegistries.INSCRIPTIONS);
        if (GLIDING.contains(player.getUUID())) keepGliding(player, carried.contains(Inscription.CHAINED_WINGS.id()));
        if (player.horizontalCollision && carried.contains(Inscription.WALL_CLIMBER.id())) player.resetFallDistance();
        if (player.tickCount % 20 != 0) return;
        upkeep(player, carried);
        refresh(player);
        if (carried.isEmpty()) return;
        ServerLevel level = player.level();
        if (carried.contains(Inscription.NIGHT_EYES.id())) keep(player, MobEffects.NIGHT_VISION, 0, 260, 400);
        double power = power(player);
        if (carried.contains(Inscription.STONE_SHELL.id())) keep(player, MobEffects.RESISTANCE, power >= 1.8 ? 1 : 0, 40, 80);
        if (carried.contains(Inscription.FIRE_SKIN.id())) keep(player, MobEffects.FIRE_RESISTANCE, 0, 40, 80);
        if (carried.contains(Inscription.SHADOW_SKIN.id()) && player.isShiftKeyDown()) {
            if (pay(player, Inscription.SHADOW_SKIN)) keep(player, MobEffects.INVISIBILITY, 0, 25, 40);
            else starved(player, Inscription.SHADOW_SKIN);
        }
        if (carried.contains(Inscription.TOUGH_METABOLISM.id())) { // anything harmful already on it (from before the carving) goes too
            for (MobEffectInstance effect : List.copyOf(player.getActiveEffects())) {
                if (effect.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) player.removeEffect(effect.getEffect());
            }
        }
        if (carried.contains(Inscription.PALE_MIND.id())) {
            player.removeEffect(MobEffects.POISON);
            player.removeEffect(MobEffects.WITHER);
            player.removeEffect(MobEffects.HUNGER);
        }
        // Iron Gut: the stomach makes do, a point of food back every 30 s (sooner on a stronger body)
        if (carried.contains(Inscription.IRON_GUT.id()) && player.getFoodData().getFoodLevel() < 20
                && player.tickCount % Math.max(200, (int) Math.round(600 / power)) < 20) {
            player.getFoodData().setFoodLevel(player.getFoodData().getFoodLevel() + 1);
        }
        if (carried.contains(Inscription.CLEAR_MIND.id())) {
            player.removeEffect(MobEffects.BLINDNESS);
            player.removeEffect(MobEffects.DARKNESS);
            player.removeEffect(MobEffects.NAUSEA);
        }
        if (carried.contains(Inscription.ECHO_SIGHT.id())) {
            double reach = Math.min(48, Config.echoSightRange() * power);
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(reach),
                    mob -> mob instanceof Enemy && mob.isAlive())) {
                MobEffectInstance glow = mob.getEffect(MobEffects.GLOWING);
                if (glow == null || glow.getDuration() <= 30) mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, true, false, false));
            }
        }
        if (carried.contains(Inscription.ESSENCE_RESERVE.id())) pourIntoAltar(player);
        int corruption = carried.size();
    }

    /** Keeps a hidden effect on: topped up to `duration` once under `below` ticks (night vision flickers under 200). */
    private static void keep(Player player, Holder<MobEffect> effect, int amplifier, int below, int duration) {
        MobEffectInstance now = player.getEffect(effect);
        if (now == null || now.getDuration() < below) player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, false));
    }

    /** Puts the attribute carvings on the body and takes off those it no longer carries; the Echo Wings' flight too. */
    public static void refresh(Player player) {
        for (Modifier modifier : MODIFIERS) {
            AttributeInstance instance = player.getAttribute(modifier.attribute());
            if (instance == null) continue;
            boolean carried = has(player, modifier.inscription());
            AttributeModifier on = instance.getModifier(modifier.id());
            double amount = modifier.amount() * power(player);
            if (carried && (on == null || Math.abs(on.amount() - amount) > 1.0E-6)) {
                instance.addOrUpdateTransientModifier(new AttributeModifier(modifier.id(), amount, modifier.operation()));
            } else if (!carried && on != null) {
                instance.removeModifier(modifier.id());
                if (modifier.attribute() == Attributes.MAX_HEALTH && player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
            }
        }
        if (!(player instanceof ServerPlayer server)) return;
        boolean wings = has(player, Inscription.ECHO_WINGS) && canPay(player, Inscription.ECHO_WINGS); // (none left: no flight)
        boolean granted = player.getPersistentData().getBooleanOr(GRANTED_FLIGHT, false);
        float flySpeed = wings ? (float) (0.05 * power(player)) : 0.05F; // the wings fly faster on a stronger body
        if (Math.abs(player.getAbilities().getFlyingSpeed() - flySpeed) > 1.0E-4 && (wings || player.getPersistentData().getBooleanOr(GRANTED_FLIGHT, false))) {
            player.getAbilities().setFlyingSpeed(flySpeed);
            server.onUpdateAbilities();
        }
        if (wings && !player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.getPersistentData().putBoolean(GRANTED_FLIGHT, true);
            server.onUpdateAbilities();
        } else if (!wings && granted) {
            player.getPersistentData().remove(GRANTED_FLIGHT);
            if (!player.isCreative() && !player.isSpectator()) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                server.onUpdateAbilities();
            }
        }
    }

    /** The essence reserve empties into an awake altar the body stands near. */
    private static void pourIntoAltar(ServerPlayer player) {
        AltarBlockEntity altar = null;
        double best = 36;
        for (AltarBlockEntity candidate : AltarBlockEntity.loaded(player.level())) {
            if (!candidate.isFormed() || !candidate.hasEye()) continue;
            double distance = candidate.getBlockPos().distToCenterSqr(player.position());
            if (distance <= best) {
                best = distance;
                altar = candidate;
            }
        }
        if (altar == null) return;
        for (Essence essence : Essence.values()) {
            int held = reserve(player, essence);
            if (held <= 0) continue;
            int given = altar.addEssence(essence, Math.min(held, 100));
            if (given <= 0) continue;
            setReserve(player, essence, held - given);
            Relays.guide(player.level(), player.blockPosition().above(), altar.getBlockPos().above(), essence);
        }
    }

    // ---- Chained Wings: an elytra's glide without one ----

    /** The client asked to glide (jump pressed in the air, client/InscriptionClient). */
    public static void startGliding(ServerPlayer player) {
        if (!has(player, Inscription.CHAINED_WINGS) || player.onGround() || player.isInWater() || player.isPassenger()
                || player.getAbilities().flying || !canPay(player, Inscription.CHAINED_WINGS)) return;
        GLIDING.add(player.getUUID());
        player.startFallFlying();
    }

    private static void keepGliding(ServerPlayer player, boolean wings) {
        if (!wings || player.onGround() || player.isInWater() || player.isPassenger() || player.isRemoved() || player.getAbilities().flying) {
            GLIDING.remove(player.getUUID());
            return;
        }
        player.startFallFlying(); // (the game took the flag down this tick: no elytra; it goes up again before it is sent)
    }

    // ---- Second Leap, Second Breath ----

    /** Second Leap: the client leapt again in the air (network/LeapPayload): no fall from before it, a puff and a whoosh. */
    public static void leapt(ServerPlayer player) {
        if (!has(player, Inscription.SECOND_LEAP) || player.onGround()) return;
        if (!pay(player, Inscription.SECOND_LEAP)) {
            starved(player, Inscription.SECOND_LEAP);
            return;
        }
        player.resetFallDistance();
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 8, 0.3, 0.05, 0.3, 0.02);
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, player.getX(), player.getY(), player.getZ(), 4, 0.25, 0.05, 0.25, 0.01);
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.6F, 1.3F);
    }

    /** How many leaps in the air a body with Second Leap has (two on a strong one). */
    public static int leaps(Player player) {
        return has(player, Inscription.SECOND_LEAP) ? (power(player) >= 2.0 ? 2 : 1) : 0;
    }

    private static final String BREATH_USED = AlchyForbidden.MODID + ":second_breath";

    /**
     * Second Breath: a death refused, once in a while (5 minutes, shorter on a stronger body): back up with a little
     * health, regeneration and absorption, a burst of souls and a heartbeat.
     */
    public static void onDeath(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !has(player, Inscription.SECOND_BREATH)) return;
        if (event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return; // the void, /kill
        long now = player.level().getGameTime();
        long last = player.getPersistentData().getLongOr(BREATH_USED, Long.MIN_VALUE / 2);
        long cooldown = Math.round(6000 / power(player));
        if (now - last < cooldown) return;
        if (!pay(player, Inscription.SECOND_BREATH)) return; // nothing left to buy the breath with
        event.setCanceled(true);
        player.getPersistentData().putLong(BREATH_USED, now);
        player.setHealth(Math.max(4F, player.getMaxHealth() * 0.25F));
        player.removeAllEffects();
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 300, 1));
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 20, 0.5, 0.6, 0.5, 0.03);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5F, 0.8F);
        level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 0.6F);
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("inscription.alchyforbidden.second_breath.saved")
                .withStyle(net.minecraft.ChatFormatting.GOLD), true);
    }

    // ---- Events ----

    /** Tough Metabolism: no harmful effect takes hold (potions, arrows, mobs, food, the Wither, Darkness...). */
    public static void onEffectApplicable(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof Player player && has(player, Inscription.TOUGH_METABOLISM)
                && event.getEffectInstance().getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
            event.setResult(net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /** Silent Step: half the fall damage. */
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && has(player, Inscription.SILENT_STEP)) {
            event.setDamageMultiplier(event.getDamageMultiplier() * (float) Math.max(0.1, 1 - 0.5 * power(player)));
        }
    }

    /** Silent Step: its steps, landings and strokes make no vibration. */
    public static void onGameEvent(VanillaGameEvent event) {
        if (!(event.getCause() instanceof Player player) || !has(player, Inscription.SILENT_STEP)) return;
        var heard = event.getVanillaEvent();
        if (heard.equals(GameEvent.STEP) || heard.equals(GameEvent.HIT_GROUND) || heard.equals(GameEvent.SWIM) || heard.equals(GameEvent.SPLASH)) {
            event.setCanceled(true);
        }
    }

    /**
     * Wrathful Arms: the lower its health, the harder it hits. Soul Siphon: a share of each blow struck heals it. Thorn
     * Skin: a quarter of a blow back at who struck it. Dread Mantle: who strikes it is slowed and weakened.
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getDirectEntity() instanceof Player player && has(player, Inscription.SOUL_SIPHON) && event.getEntity() != player) {
            player.heal(event.getAmount() * (float) (0.12 * power(player)));
            if (player.level() instanceof ServerLevel level) {
                LivingEntity hit = event.getEntity();
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, hit.getX(), hit.getY() + hit.getBbHeight() * 0.6, hit.getZ(), 3, 0.2, 0.2, 0.2, 0.05);
            }
        }
        if (event.getEntity() instanceof Player player && has(player, Inscription.DREAD_MANTLE)
                && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player) {
            int ticks = (int) Math.round(60 * power(player));
            attacker.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 1), player);
            attacker.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0), player);
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.SCULK_SOUL, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.5, attacker.getZ(), 4, 0.25, 0.3, 0.25, 0.02);
            }
        }
        if (event.getSource().getEntity() instanceof Player player && has(player, Inscription.WRATHFUL_ARMS)) {
            float missing = 1F - player.getHealth() / Math.max(1F, player.getMaxHealth());
            event.setAmount(event.getAmount() * (1F + (float) (0.6 * power(player)) * Math.max(0F, missing)));
        }
        if (event.getEntity() instanceof Player player && has(player, Inscription.THORN_SKIN)
                && event.getSource().getDirectEntity() instanceof LivingEntity attacker && attacker != player
                && player.level() instanceof ServerLevel level) {
            attacker.hurtServer(level, player.damageSources().thorns(player), Math.max(1F, event.getAmount() * (float) (0.25 * power(player))));
        }
    }

}
