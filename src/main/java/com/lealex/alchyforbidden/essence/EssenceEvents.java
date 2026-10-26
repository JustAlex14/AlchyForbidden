package com.lealex.alchyforbidden.essence;

import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.block.entity.AltarBlockEntity;
import com.lealex.alchyforbidden.item.VesselItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Where the essences come from (for now; later multiblocks will make Sculk Corruption):
 * WRATH: a hostile mob killed by a player fills a vessel the player carries, more for a bigger mob.
 * SORROW: a player standing in the dark fills a vessel they carry, every second, more in the deep dark.
 * SCULK CORRUPTION: a mob dying near a sculk catalyst gives it to the nearest awake altar instead of the catalyst
 * (no sculk spreads over the world, no experience drops). With no altar near, the killer's carved reserve and vessels
 * drink it instead.
 * Cheap: a death looks at the loaded altars of its level (a handful), the dark is checked once a second per player.
 */
public final class EssenceEvents {
    private EssenceEvents() {}

    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;
        // Wrath, for the one who killed it
        if (dead instanceof Enemy && event.getSource().getEntity() instanceof ServerPlayer killer) {
            int wrath = Math.max(1, Math.round(dead.getMaxHealth() / 10F * Config.wrathPer10Health()));
            wrath -= Inscriptions.store(killer, Essence.WRATH, wrath); // a carved reserve first, then the vessels
            pour(killer.getInventory(), Essence.WRATH, wrath);
        }
        // Sculk Corruption: a death near a sculk catalyst, with an awake altar near, does not feed the catalyst (which
        // would spread sculk over the world): the nearest altar takes it all, the mob's experience with it
        BlockPos at = dead.blockPosition();
        int reach = Config.altarReach();
        AltarBlockEntity nearest = null;
        double best = (double) reach * reach;
        for (AltarBlockEntity altar : AltarBlockEntity.loaded(level)) {
            if (!altar.isFormed() || !altar.hasEye()) continue;
            double distance = altar.getBlockPos().distSqr(at);
            if (distance <= best) {
                best = distance;
                nearest = altar;
            }
        }
        if (nearest == null && event.getSource().getEntity() instanceof ServerPlayer killer
                && (Inscriptions.has(killer, Inscription.ESSENCE_RESERVE) || carriesVessel(killer.getInventory()))
                && !dead.wasExperienceConsumed() && catalystNear(level, at, Config.catalystReach())) { // no altar near: the reserve, then vessels drink it
            int experience = dead.getExperienceReward(level, killer);
            dead.skipDropExperience();
            int corruption = Config.corruptionPerDeath() + experience * Config.corruptionPerExperience();
            corruption -= Inscriptions.store(killer, Essence.CORRUPTION, corruption);
            pour(killer.getInventory(), Essence.CORRUPTION, corruption);
            return;
        }
        if (nearest == null || dead.wasExperienceConsumed() || !catalystNear(level, at, Config.catalystReach())) return;
        int experience = dead.getExperienceReward(level, event.getSource().getEntity());
        dead.skipDropExperience(); // the catalyst now leaves it alone: no spread, no bloom, no orbs
        nearest.addEssence(Essence.CORRUPTION, Config.corruptionPerDeath() + experience * Config.corruptionPerExperience());
        com.lealex.alchyforbidden.network.AltarRiftPayload.part(level, com.lealex.alchyforbidden.network.AltarRiftPayload.ABSORB,
                at, nearest.getBlockPos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 0);
    }

    private static boolean catalystNear(ServerLevel level, BlockPos at, int reach) {
        for (int cx = (at.getX() - reach) >> 4; cx <= (at.getX() + reach) >> 4; cx++) {
            for (int cz = (at.getZ() - reach) >> 4; cz <= (at.getZ() + reach) >> 4; cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (net.minecraft.world.level.block.entity.BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof net.minecraft.world.level.block.entity.SculkCatalystBlockEntity)) continue;
                    BlockPos pos = entity.getBlockPos();
                    if (Math.abs(pos.getX() - at.getX()) <= reach && Math.abs(pos.getY() - at.getY()) <= reach
                            && Math.abs(pos.getZ() - at.getZ()) <= reach) return true;
                }
            }
        }
        return false;
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0 || player.isSpectator()) return;
        boolean reserve = Inscriptions.has(player, Inscription.ESSENCE_RESERVE);
        if (!reserve && !carriesVessel(player.getInventory())) return;
        ServerLevel level = player.level();
        BlockPos at = player.blockPosition();
        if (level.getMaxLocalRawBrightness(at) > Config.sorrowMaxLight()) return;
        int sorrow = level.getBiome(at).is(Biomes.DEEP_DARK) ? Config.sorrowPerSecondDeepDark() : Config.sorrowPerSecond();
        sorrow -= Inscriptions.store(player, Essence.SORROW, sorrow);
        pour(player.getInventory(), Essence.SORROW, sorrow);
    }

    private static boolean carriesVessel(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).getItem() instanceof VesselItem) return true;
        }
        return false;
    }

    /** Into the vessels the inventory holds: first those that already hold this essence, then empty ones. */
    private static void pour(Inventory inventory, Essence essence, int amount) {
        for (int pass = 0; pass < 2 && amount > 0; pass++) {
            for (int i = 0; i < inventory.getContainerSize() && amount > 0; i++) {
                ItemStack stack = inventory.getItem(i);
                if (!(stack.getItem() instanceof VesselItem)) continue;
                boolean holdsIt = VesselItem.contents(stack) != null;
                if ((pass == 0) != holdsIt) continue;
                amount -= VesselItem.fill(stack, essence, amount);
            }
        }
    }
}
