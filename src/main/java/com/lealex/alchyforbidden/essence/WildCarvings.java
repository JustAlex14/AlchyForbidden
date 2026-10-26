package com.lealex.alchyforbidden.essence;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The wilder carvings (the second wave): Hoarder's Eye pulls loot in, Hunter's Mark marks prey for harder blows,
 * Vampiric Heart drinks kills, Carrion Feast eats them, Executioner finishes the weak, Rift Fist bursts every fourth
 * blow, Vein Rip tears whole ore veins out, Leech Mantle bleeds the nearest monster, Grasping Tendrils drag monsters
 * in, Meteor Stomp turns a fall into a crater, Moon Step drifts down while sneaking, Ember Trail burns what a sprint
 * passes, Mirror Skin throws projectiles back, Toxic Ooze poisons who strikes it. All stronger on a stronger body
 * (Inscriptions.power).
 */
@EventBusSubscriber(modid = AlchyForbidden.MODID)
public final class WildCarvings {
    private WildCarvings() {}

    private static final Map<LivingEntity, Long> MARKED = new WeakHashMap<>();
    private static final Map<UUID, Integer> FIST_COUNT = new HashMap<>();
    private static boolean ripping;

    private static boolean has(Player player, Inscription carving) {
        return Inscriptions.has(player, carving);
    }

    private static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle, double step) {
        double length = from.distanceTo(to);
        for (double d = 0; d <= length; d += step) {
            Vec3 p = from.lerp(to, d / Math.max(0.001, length));
            level.sendParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    private static Vec3 middle(Entity entity) {
        return entity.position().add(0, entity.getBbHeight() * 0.5, 0);
    }

    // ---- Every tick: pulls, bleeds, drags, drifts, burns, oozes ----

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isSpectator() || player.getData(ModRegistries.INSCRIPTIONS).isEmpty()) return;
        ServerLevel level = player.level();
        double power = Inscriptions.power(player);
        int tick = player.tickCount;
        // Hoarder's Eye: drops and experience within reach drift to the body
        if (tick % 4 == 0 && has(player, Inscription.HOARDERS_EYE)) {
            double reach = 6 * power;
            for (Entity loot : level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(reach),
                    e -> (e instanceof ItemEntity || e instanceof ExperienceOrb) && e.isAlive())) {
                Vec3 pull = player.position().add(0, 0.6, 0).subtract(loot.position());
                if (pull.lengthSqr() < 1) continue;
                loot.setDeltaMovement(loot.getDeltaMovement().scale(0.5).add(pull.normalize().scale(0.35)));
                loot.hurtMarked = true;
                if (tick % 12 == 0) level.sendParticles(ParticleTypes.END_ROD, loot.getX(), loot.getY() + 0.2, loot.getZ(), 1, 0, 0, 0, 0);
            }
        }
        // Moon Step: sneaking in the air, the body drifts down like a feather
        if (has(player, Inscription.MOON_STEP) && player.isShiftKeyDown() && !player.onGround() && player.getDeltaMovement().y < 0) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, true, false, false));
            if (tick % 3 == 0) level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 1, 0.2, 0, 0.2, 0);
        }
        // Ember Trail: a sprint leaves fire behind it; whatever it passes burns
        if (tick % 3 == 0 && has(player, Inscription.EMBER_TRAIL) && player.isSprinting()) {
            level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.1, player.getZ(), 3, 0.2, 0.02, 0.2, 0.01);
            level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.1, 0, 0.1, 0);
            for (LivingEntity burnt : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(1.5), e -> e != player && e.isAlive() && hostile(player, e))) {
                burnt.igniteForSeconds((float) (3 * power));
                burnt.hurtServer(level, player.damageSources().inFire(), (float) power);
            }
        }
        // Toxic Ooze: the skin drips
        if (tick % 10 == 0 && has(player, Inscription.TOXIC_OOZE)) {
            level.sendParticles(ParticleTypes.ITEM_SLIME, player.getX(), player.getY() + 0.8, player.getZ(), 2, 0.25, 0.4, 0.25, 0);
        }
        // Leech Mantle: every 3 s the nearest monster bleeds into the body
        if (tick % 60 == 0 && has(player, Inscription.LEECH_MANTLE)) {
            LivingEntity prey = nearestMonster(player, 6 * Math.sqrt(power), 0);
            if (prey != null) {
                float drawn = (float) (1.5 * power);
                prey.hurtServer(level, player.damageSources().indirectMagic(player, player), drawn);
                player.heal(drawn);
                line(level, middle(prey), middle(player), new DustParticleOptions(0x8A1020, 1.0F), 0.3);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), prey.getX(), prey.getY() + prey.getBbHeight() * 0.6, prey.getZ(), 8, 0.2, 0.2, 0.2, 0.1);
                level.playSound(null, prey.blockPosition(), SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 0.6F, 0.5F);
            }
        }
        // Grasping Tendrils: every 2 s the nearest monster out of reach is dragged in by tendrils of sculk
        if (tick % 40 == 0 && has(player, Inscription.GRASPING_TENDRILS)) {
            LivingEntity prey = nearestMonster(player, 8 * Math.sqrt(power), 2.5);
            if (prey != null) {
                Vec3 pull = middle(player).subtract(middle(prey)).normalize().scale(1.1);
                prey.setDeltaMovement(pull.x, 0.25, pull.z);
                prey.hurtMarked = true;
                prey.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 2), player);
                line(level, middle(player), middle(prey), new DustParticleOptions(0x0E7A70, 1.2F), 0.25);
                line(level, middle(player), middle(prey), ParticleTypes.SCULK_CHARGE_POP, 0.8);
                level.playSound(null, prey.blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 1.0F, 0.6F);
            }
        }
    }

    private static LivingEntity nearestMonster(Player player, double reach, double notWithin) {
        LivingEntity best = null;
        double bestDistance = reach * reach;
        for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach),
                e -> e instanceof Enemy && e.isAlive())) {
            double d = living.distanceToSqr(player);
            if (d < bestDistance && d > notWithin * notWithin) {
                bestDistance = d;
                best = living;
            }
        }
        return best;
    }

    // ---- Blows: marks, executions, fists, ooze ----

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity hit = event.getEntity();
        if (!(hit.level() instanceof ServerLevel level)) return;
        if (event.getSource().getEntity() instanceof Player player && hit != player) {
            double power = Inscriptions.power(player);
            // Hunter's Mark: the first blow marks (it glows); the mark takes harder blows for a while
            if (has(player, Inscription.HUNTERS_MARK)) {
                Long until = MARKED.get(hit);
                if (until != null && until > level.getGameTime()) {
                    event.setAmount(event.getAmount() * (float) (1 + 0.25 * power));
                    level.sendParticles(ParticleTypes.CRIT, hit.getX(), hit.getY() + hit.getBbHeight(), hit.getZ(), 4, 0.2, 0.2, 0.2, 0.1);
                } else {
                    MARKED.put(hit, level.getGameTime() + 160);
                    hit.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0), player);
                }
            }
            if (event.getSource().getDirectEntity() == player) {
                // Executioner: a blow that leaves it weak enough kills it outright, messily
                if (has(player, Inscription.EXECUTIONER) && !hit.getType().builtInRegistryHolder().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES)
                        && hit.getHealth() - event.getAmount() <= hit.getMaxHealth() * Math.min(0.4, 0.15 * power)) {
                    event.setAmount(Math.max(event.getAmount(), hit.getHealth() + hit.getAbsorptionAmount() + 100));
                    Vec3 m = middle(hit);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), m.x, m.y, m.z, 30, 0.3, 0.4, 0.3, 0.2);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), m.x, m.y, m.z, 12, 0.3, 0.3, 0.3, 0.2);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, m.x, m.y, m.z, 1, 0, 0, 0, 0);
                    level.playSound(null, hit.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.5F);
                    level.playSound(null, hit.blockPosition(), SoundEvents.SLIME_DEATH, SoundSource.PLAYERS, 1.0F, 0.6F);
                }
                // Rift Fist: every fourth blow bursts, throwing everything round it back
                if (has(player, Inscription.RIFT_FIST)) {
                    int count = FIST_COUNT.merge(player.getUUID(), 1, Integer::sum);
                    if (count >= 4) {
                        FIST_COUNT.put(player.getUUID(), 0);
                        Vec3 m = middle(hit);
                        level.sendParticles(ParticleTypes.SONIC_BOOM, m.x, m.y, m.z, 1, 0, 0, 0, 0);
                        level.sendParticles(ModRegistries.RIFT_MOTE.get(), m.x, m.y, m.z, 20, 0.6, 0.5, 0.6, 0.15);
                        level.playSound(null, hit.blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.2F, 0.8F);
                        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, hit.getBoundingBox().inflate(3), e -> e != player && e.isAlive())) {
                            Vec3 away = near.position().subtract(player.position()).multiply(1, 0, 1).normalize();
                            near.push(away.x * 1.4, 0.5, away.z * 1.4);
                            near.hurtMarked = true;
                            if (near != hit) near.hurtServer(level, player.damageSources().indirectMagic(player, player), (float) (3 * power));
                        }
                    }
                }
            }
        }
        // Toxic Ooze: who strikes the body is poisoned and sickened
        if (hit instanceof Player player && has(player, Inscription.TOXIC_OOZE) && event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player) {
            double power = Inscriptions.power(player);
            attacker.addEffect(new MobEffectInstance(MobEffects.POISON, (int) Math.round(60 * power), 1), player);
            attacker.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0), player);
            level.sendParticles(ParticleTypes.ITEM_SLIME, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.6, attacker.getZ(), 10, 0.3, 0.3, 0.3, 0.05);
            level.playSound(null, attacker.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
    }

    // ---- Kills: the heart drinks them, the stomach eats them ----

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getEntity() == player) return;
        ServerLevel level = player.level();
        LivingEntity dead = event.getEntity();
        double power = Inscriptions.power(player);
        if (has(player, Inscription.VAMPIRIC_HEART)) {
            player.heal((float) (4 * power));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0));
            line(level, middle(dead), middle(player), new DustParticleOptions(0xC01030, 1.2F), 0.25);
            level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8F, 1.4F);
        }
        if (has(player, Inscription.CARRION_FEAST)) {
            var food = player.getFoodData();
            food.setFoodLevel(Math.min(20, food.getFoodLevel() + (int) Math.round(2 * power)));
            food.setSaturation(Math.min(food.getFoodLevel(), food.getSaturationLevel() + (float) power));
            Vec3 m = middle(dead);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState()), m.x, m.y, m.z, 16, 0.3, 0.3, 0.3, 0.1);
            line(level, m, player.getEyePosition(), new DustParticleOptions(0x6A2020, 0.8F), 0.4);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.0F, 0.7F);
        }
    }

    // ---- Falls: a crater ----

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !has(player, Inscription.METEOR_STOMP) || event.getDistance() < 4) return;
        ServerLevel level = player.level();
        double power = Inscriptions.power(player);
        double reach = 3 + power;
        float damage = (float) ((event.getDistance() - 2) * 1.5 * power);
        for (LivingEntity crushed : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(reach), e -> e != player && e.isAlive())) {
            crushed.hurtServer(level, player.damageSources().playerAttack(player), damage);
            Vec3 away = crushed.position().subtract(player.position()).multiply(1, 0, 1).normalize();
            crushed.push(away.x * 1.2, 0.6, away.z * 1.2);
            crushed.hurtMarked = true;
        }
        event.setDamageMultiplier(event.getDamageMultiplier() * 0.3F);
        BlockState ground = level.getBlockState(player.blockPosition().below());
        level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY(), player.getZ(), 2, 0.5, 0.1, 0.5, 0);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? Blocks.DIRT.defaultBlockState() : ground),
                    player.getX() + Math.cos(a) * reach * 0.6, player.getY() + 0.1, player.getZ() + Math.sin(a) * reach * 0.6, 3, 0.1, 0.1, 0.1, 0.15);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    // ---- Mining: whole veins ----

    @SubscribeEvent
    public static void onBreak(net.neoforged.neoforge.event.level.block.BreakBlockEvent event) {
        if (ripping || !(event.getPlayer() instanceof ServerPlayer player) || !has(player, Inscription.VEIN_RIP) || player.isShiftKeyDown()) return;
        BlockState state = event.getState();
        if (!state.is(net.neoforged.neoforge.common.Tags.Blocks.ORES) || !(event.getLevel() instanceof ServerLevel level)) return;
        int most = (int) Math.min(64, Math.round(12 * Inscriptions.power(player)));
        Block block = state.getBlock();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(event.getPos());
        seen.add(event.getPos());
        int torn = 0;
        ripping = true;
        try {
            while (!open.isEmpty() && torn < most) {
                BlockPos at = open.poll();
                for (Direction d : Direction.values()) {
                    BlockPos next = at.relative(d);
                    if (!seen.add(next) || !level.getBlockState(next).is(block) || !level.mayInteract(player, next)) continue;
                    open.add(next);
                    level.destroyBlock(next, true, player);
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, next.getX() + 0.5, next.getY() + 0.5, next.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.05);
                    if (++torn >= most) break;
                }
            }
        } finally {
            ripping = false;
        }
        if (torn > 0) level.playSound(null, event.getPos(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.5F);
    }

    // ---- Projectiles: thrown back ----

    @SubscribeEvent
    public static void onProjectile(ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof ServerPlayer player)
                || !has(player, Inscription.MIRROR_SKIN)) return;
        Projectile projectile = event.getProjectile();
        if (projectile.getOwner() == player || player.getRandom().nextFloat() > Math.min(0.6, 0.2 * Inscriptions.power(player))) return;
        event.setCanceled(true);
        projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1.2));
        projectile.setOwner(player);
        projectile.hurtMarked = true;
        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.END_ROD, projectile.getX(), projectile.getY(), projectile.getZ(), 10, 0.2, 0.2, 0.2, 0.08);
        level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 1.4F);
    }

    /** Only what is hostile burns in the trail: monsters and wild rift creatures, never its familiars, pets or other people. */
    private static boolean hostile(Player player, LivingEntity e) {
        boolean foe = e instanceof net.minecraft.world.entity.monster.Enemy
                || e instanceof com.lealex.alchyforbidden.familiar.RiftBeast beast && !beast.familiar();
        return foe && Spells.strikes(player, e);
    }
}
