package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.block.entity.AltarBlockEntity;
import com.lealex.alchyforbidden.block.entity.GrowingCoreBlockEntity;
import com.lealex.alchyforbidden.block.entity.RelayBlockEntity;
import com.lealex.alchyforbidden.item.AttuningForkItem;
import com.lealex.alchyforbidden.registry.ModRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * While an Attuning Fork is held: every link near the player drawn as a thread of motes flowing from the sender to its
 * target (relay crystals, sources, altars), and a ring round the sender the tuner starts from.
 */
public final class RelayLinks {
    private RelayLinks() {}

    /** Chunks round the player looked through (only while a tuner is held, every few ticks). */
    private static final int CHUNKS = 3;

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null || minecraft.isPaused()) return;
        ItemStack tuner = com.lealex.alchyx.client.LinkLines.inHand(minecraft.player, AttuningForkItem.class);
        if (tuner == null) return;
        long time = level.getGameTime();
        attunedTo(level, player, tuner, time);
        if (time % 4 != 0) return;
        int cx = player.blockPosition().getX() >> 4, cz = player.blockPosition().getZ() >> 4;
        for (int dx = -CHUNKS; dx <= CHUNKS; dx++) {
            for (int dz = -CHUNKS; dz <= CHUNKS; dz++) {
                if (!level.hasChunk(cx + dx, cz + dz)) continue;
                LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    BlockPos link = null;
                    if (entity instanceof RelayBlockEntity relay) { // every branch
                        for (BlockPos branch : relay.targets()) {
                            if (com.lealex.alchyforbidden.essence.Relays.leadsSomewhere(level, branch)) {
                                thread(level, AttuningForkItem.point(level, entity.getBlockPos()), AttuningForkItem.point(level, branch));
                            }
                        }
                        continue;
                    } else if (entity instanceof GrowingCoreBlockEntity core && !(entity instanceof AltarBlockEntity)
                            && !(entity instanceof com.lealex.alchyforbidden.essence.EssenceReceiver)) link = core.relayLink();
                    if (link != null && com.lealex.alchyforbidden.essence.Relays.leadsSomewhere(level, link)) thread(level, AttuningForkItem.point(level, entity.getBlockPos()), AttuningForkItem.point(level, link));
                }
            }
        }
    }

    /**
     * What the fork is attuned to (the sender a link will start from): a column of light rising over it, a ring turning
     * round it and a bright mark bobbing on top, every tick; and a faint thread from it to the player's hand, the link
     * still to be made.
     */
    private static void attunedTo(ClientLevel level, Player player, ItemStack tuner, long time) {
        GlobalPos from = tuner.get(ModRegistries.FORK_FROM.get());
        if (from == null || from.dimension() != level.dimension()) return;
        Vec3 at = AttuningForkItem.point(level, from.pos());
        var random = level.getRandom();
        if (com.lealex.alchyx.AlchyXClientConfig.spawn(random)) { // the column, rising
            level.addParticle(ParticleTypes.END_ROD, at.x + (random.nextDouble() - 0.5) * 0.2, at.y + 0.3, at.z + (random.nextDouble() - 0.5) * 0.2, 0, 0.07, 0);
        }
        for (int i = 0; i < 3; i++) { // the ring, turning
            double a = time * 0.18 + i * Math.PI * 2 / 3;
            level.addParticle(new DustParticleOptions(0xB98CFF, 1.0F), at.x + Math.cos(a) * 0.7, at.y + 0.1, at.z + Math.sin(a) * 0.7, 0, 0, 0);
        }
        double top = at.y + 2.2 + Math.sin(time * 0.15) * 0.15; // the mark on top: a small arrow pointing down at it
        DustParticleOptions bright = new DustParticleOptions(0xE6D6FF, 1.2F);
        level.addParticle(bright, at.x, top, at.z, 0, 0, 0);
        level.addParticle(bright, at.x - 0.15, top + 0.15, at.z, 0, 0, 0);
        level.addParticle(bright, at.x + 0.15, top + 0.15, at.z, 0, 0, 0);
        if (time % 3 == 0) { // the link still to be made: from it to the hand
            Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.6)).add(0, -0.4, 0);
            double length = at.distanceTo(hand);
            if (length > 1 && length < 48) {
                com.lealex.alchyx.client.LinkLines.line(level, at, hand, new DustParticleOptions(0x7A5AB0, 0.5F), (int) length, 3); // one a block
            }
        }
    }

    private static final DustParticleOptions THREAD = new DustParticleOptions(0xB98CFF, 0.6F);

    /** A link: AlchyX's line of motes (one every half block, drifting toward the target, fewer with fewer particles). */
    private static void thread(ClientLevel level, Vec3 a, Vec3 b) {
        double length = a.distanceTo(b);
        if (length < 0.1 || length > 96) return;
        com.lealex.alchyx.client.LinkLines.line(level, a, b, THREAD, 192, 4);
    }
}
