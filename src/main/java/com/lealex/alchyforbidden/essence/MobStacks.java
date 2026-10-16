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

    private record Pending(Mob mob, BlockPos spawner) {}

    private static final List<Pending> PENDING = new ArrayList<>();

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
            if (bound(spawner)) PENDING.add(new Pending(event.getEntity(), spawner.getBlockPos()));
        });
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        List<Pending> now = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Pending pending : now) {
            Mob mob = pending.mob();
            if (!mob.isAlive() || !(mob.level() instanceof ServerLevel level)) continue;
            AABB near = new AABB(pending.spawner()).inflate(REACH);
            Mob stack = null;
            for (Mob other : level.getEntitiesOfClass(Mob.class, near, other -> other != mob && other.isAlive()
                    && other.getType() == mob.getType() && other.hasData(ModRegistries.MOB_STACK) && count(other) < STACK_CAP)) {
                stack = other;
                break;
            }
            if (stack == null) { // the first: the others fold into it
                mob.setData(ModRegistries.MOB_STACK, 1);
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

    /** A condensed mob's drops, as many times as it holds mobs (before the Killing Pit takes them). */
    public static void onDrops(LivingDropsEvent event) {
        int count = count(event.getEntity());
        if (count <= 1 || event.getEntity().level().isClientSide()) return;
        List<ItemEntity> more = new ArrayList<>();
        for (ItemEntity item : event.getDrops()) {
            ItemStack stack = item.getItem();
            int total = stack.getCount() * count;
            int max = Math.max(1, stack.getMaxStackSize());
            item.setItem(stack.copyWithCount(Math.min(max, total)));
            total -= Math.min(max, total);
            while (total > 0) {
                int part = Math.min(max, total);
                more.add(new ItemEntity(item.level(), item.getX(), item.getY(), item.getZ(), stack.copyWithCount(part)));
                total -= part;
            }
        }
        event.getDrops().addAll(more);
    }

    public static void onExperience(LivingExperienceDropEvent event) {
        int count = count(event.getEntity());
        if (count > 1) event.setDroppedExperience(event.getDroppedExperience() * count);
    }
}
