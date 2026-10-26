package com.lealex.alchyforbidden.essence;

import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Condensed mobs: a spawner bound with a Rift Knot (any spawner, vanilla's or another mod's, Apotheosis's included: the
 * binding is a data attachment on its block entity, and the spawner's own rules all still run) does not fill the
 * world with its mobs: each one it makes folds, the tick after, into one mob of its kind near the spawner, whose stack
 * grows (shown over its head, up to STACK_CAP). One condensed mob counts as all of them: its drops and experience are
 * multiplied by its count, and so are its Wrath in a Killing Pit and its ghosts at a Lament Well. Its health is a
 * single mob's.
 */
public final class MobStacks {
    private MobStacks() {}

    public static final int STACK_CAP = 64;
    /** How near the spawner the mob a new one folds into must be. */
    private static final double REACH = 8;
    /** How near its gathering point the stack must be (it may wander off a little). */
    private static final double TARGET_REACH = 3;

    private record Pending(Mob mob, BlockPos spawner) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    /** The knotted spawners this client knows of (the knot is synced with the spawner: client/KnotVisuals draws them). */
    private static final java.util.Set<BlockEntity> CLIENT_KNOTS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /** The knot's state reached this client (ModRegistries.KNOTTED's sync). */
    public static void knotSeen(BlockEntity spawner, boolean on) {
        if (on) CLIENT_KNOTS.add(spawner);
        else CLIENT_KNOTS.remove(spawner);
    }

    public static java.util.Set<BlockEntity> clientKnots() {
        return CLIENT_KNOTS;
    }

    /** How many mobs this one is (1 for a mob that is not condensed). */
    public static int count(Entity entity) {
        return entity.hasData(ModRegistries.MOB_STACK) ? Math.max(1, entity.getData(ModRegistries.MOB_STACK)) : 1;
    }

    public static boolean bound(BlockEntity spawner) {
        return spawner.getData(ModRegistries.KNOTTED);
    }

    /** A mob a bound spawner makes: it folds into its kind's stack the tick after it appears. */
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getSpawner() == null || event.getLevel().isClientSide()) return;
        event.getSpawner().ifLeft(spawner -> {
            if (!bound(spawner)) return;
            PENDING.add(new Pending(event.getEntity(), spawner.getBlockPos()));
        });
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        List<Pending> now = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Pending pending : now) {
            Mob mob = pending.mob();
            if (!mob.isAlive() || !(mob.level() instanceof ServerLevel level)) continue;
            // a rider it came with (a spider jockey's skeleton) is not the spawner's kind: gone with it, or it would be
            // left standing when the spider folds into its stack
            List<net.minecraft.world.entity.Entity> riders = new ArrayList<>();
            mob.getIndirectPassengers().forEach(riders::add);
            for (net.minecraft.world.entity.Entity rider : riders) rider.discard();
            // its gathering point, if the knot gave the spawner one: the stack stands there
            BlockEntity spawner = level.getBlockEntity(pending.spawner());
            BlockPos target = spawner == null ? null : com.lealex.alchyforbidden.item.RiftKnotItem.target(spawner);
            AABB near = target == null ? new AABB(pending.spawner()).inflate(REACH) : new AABB(target.above()).inflate(TARGET_REACH);
            Mob stack = null;
            for (Mob other : level.getEntitiesOfClass(Mob.class, near, other -> other != mob && other.isAlive()
                    && other.getType() == mob.getType() && other.hasData(ModRegistries.MOB_STACK) && count(other) < STACK_CAP)) {
                stack = other;
                break;
            }
            if (stack == null) { // the first: the others fold into it
                mob.setData(ModRegistries.MOB_STACK, 1);
                if (target != null) {
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + 0.8, mob.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
                    mob.teleportTo(target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5);
                    level.sendParticles(ModRegistries.RIFT_MOTE.get(), target.getX() + 0.5, target.getY() + 1.2, target.getZ() + 0.5, 12, 0.3, 0.4, 0.3, 0.03);
                }
                continue;
            }
            int folded = Math.min(STACK_CAP, count(stack) + count(mob));
            stack.setData(ModRegistries.MOB_STACK, folded);
            stack.setCustomName(Component.empty().append(stack.getType().getDescription()).append(" x" + folded));
            stack.setCustomNameVisible(true);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getY() + 0.8, mob.getZ(), 10, 0.3, 0.5, 0.3, 0.05);
            level.sendParticles(ModRegistries.RIFT_MOTE.get(), stack.getX(), stack.getY() + stack.getBbHeight(), stack.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
            mob.discard();
        }
    }

    /**
     * A condensed mob's drops: its loot table rolled again for every other mob it holds (so a rare drop comes at its
     * own chance per mob), added to what it dropped itself (before the Killing Pit takes them). What it wore or held
     * drops once: one body.
     */
    public static void onDrops(LivingDropsEvent event) {
        net.minecraft.world.entity.LivingEntity dead = event.getEntity();
        int count = count(dead);
        if (count <= 1 || !(dead.level() instanceof ServerLevel level)) return;
        var table = dead.getLootTable();
        if (table.isEmpty()) return;
        // gathered first, merged by kind, so 64 rolls make a few full stacks, not hundreds of item entities; past the
        // config's cap the rolls made stand for all of them (scaled up, rounded by chance: the same loot on average)
        int extra = count - 1, rolls = Math.min(extra, com.lealex.alchyforbidden.Config.stackLootRolls());
        List<ItemStack> rolled = new ArrayList<>();
        for (int i = 0; i < rolls; i++) {
            dead.dropFromLootTable(level, event.getSource(), event.isRecentlyHit(), table.get(), stack -> {
                for (ItemStack kept : rolled) {
                    if (kept.getCount() < kept.getMaxStackSize() && ItemStack.isSameItemSameComponents(kept, stack)) {
                        int moved = Math.min(stack.getCount(), kept.getMaxStackSize() - kept.getCount());
                        kept.grow(moved);
                        stack.shrink(moved);
                        if (stack.isEmpty()) return;
                    }
                }
                rolled.add(stack.copy());
            });
        }
        double scale = extra / (double) rolls;
        for (ItemStack stack : rolled) {
            double exact = stack.getCount() * scale;
            int total = (int) exact + (level.getRandom().nextDouble() < exact - (int) exact ? 1 : 0);
            int max = Math.max(1, stack.getMaxStackSize());
            while (total > 0) {
                int part = Math.min(max, total);
                ItemEntity item = new ItemEntity(level, dead.getX(), dead.getY(), dead.getZ(), stack.copyWithCount(part));
                item.setDefaultPickUpDelay();
                event.getDrops().add(item);
                total -= part;
            }
        }
    }

    public static void onExperience(LivingExperienceDropEvent event) {
        int count = count(event.getEntity());
        if (count > 1) event.setDroppedExperience(event.getDroppedExperience() * count);
    }
}
