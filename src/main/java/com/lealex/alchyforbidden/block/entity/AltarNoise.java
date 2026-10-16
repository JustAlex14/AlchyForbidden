package com.lealex.alchyforbidden.block.entity;

import com.lealex.alchyforbidden.Config;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SculkShriekerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * What a growing altar's noise wakes. Called once per growth, as the blocks start to grow: wool around the altar
 * muffles it; otherwise the nearest sculk shrieker within reach shrieks at the nearest player (one warning toward the
 * warden, the game's own count), and with no shrieker in reach a few hostile mobs come.
 */
public final class AltarNoise {
    private AltarNoise() {}

    public static void sound(ServerLevel level, BlockPos altar, Consumer<Component> say) {
        if (!Config.loudGrowth()) return;
        if (woolAround(level, altar) >= Config.woolToMuffle()) {
            say.accept(Component.translatable("altar.alchyforbidden.muffled"));
            return;
        }
        Player nearest = level.getNearestPlayer(altar.getX() + 0.5, altar.getY() + 0.5, altar.getZ() + 0.5, 48, false);
        ServerPlayer player = nearest instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        SculkShriekerBlockEntity shrieker = nearestShrieker(level, altar, Config.shriekerReach());
        if (shrieker != null) {
            if (player != null) shrieker.tryShriek(level, player);
            say.accept(Component.translatable("altar.alchyforbidden.heard"));
            return;
        }
        if (level.getDifficulty() == Difficulty.PEACEFUL || Config.mobsDrawn() <= 0) return;
        List<EntityType<?>> kinds = Config.drawnMobs();
        if (kinds.isEmpty()) return;
        RandomSource random = level.getRandom();
        int drawn = 0;
        for (int i = 0; i < Config.mobsDrawn(); i++) {
            BlockPos spot = spotFor(level, altar, random);
            if (spot == null) continue;
            Entity entity = kinds.get(random.nextInt(kinds.size())).spawn(level, spot, EntitySpawnReason.EVENT);
            if (entity instanceof Mob mob && player != null) mob.setTarget(player);
            if (entity != null) drawn++;
        }
        if (drawn > 0) say.accept(Component.translatable("altar.alchyforbidden.drawn"));
    }

    /** Wool blocks and carpets in a cube around the altar. */
    private static int woolAround(ServerLevel level, BlockPos altar) {
        int reach = Config.woolRadius(), count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(altar.offset(-reach, -reach, -reach), altar.offset(reach, reach, reach))) {
            var state = level.getBlockState(pos);
            if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)) count++;
        }
        return count;
    }

    /** The nearest shrieker within reach, read from the loaded chunks' block entities (no block scan). */
    private static @Nullable SculkShriekerBlockEntity nearestShrieker(ServerLevel level, BlockPos altar, int reach) {
        if (reach <= 0) return null;
        SculkShriekerBlockEntity best = null;
        double bestDistance = (double) reach * reach;
        int minX = (altar.getX() - reach) >> 4, maxX = (altar.getX() + reach) >> 4;
        int minZ = (altar.getZ() - reach) >> 4, maxZ = (altar.getZ() + reach) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof SculkShriekerBlockEntity shrieker)) continue;
                    double distance = entity.getBlockPos().distSqr(altar);
                    if (distance <= bestDistance) {
                        bestDistance = distance;
                        best = shrieker;
                    }
                }
            }
        }
        return best;
    }

    /** A place 10 to 18 blocks from the altar where a mob can stand (feet and head free, solid floor), or null. */
    private static @Nullable BlockPos spotFor(ServerLevel level, BlockPos altar, RandomSource random) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int distance = 10 + random.nextInt(9);
            BlockPos column = altar.offset((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
            for (int dy = 6; dy >= -6; dy--) {
                BlockPos feet = column.above(dy);
                if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getFluidState(feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                        && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) {
                    return feet;
                }
            }
        }
        return null;
    }
}
