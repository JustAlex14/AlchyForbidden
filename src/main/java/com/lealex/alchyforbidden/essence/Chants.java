package com.lealex.alchyforbidden.essence;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * A player chanting at a multiblock (the Binding Circle's imbuing, the altar's rites), seen like the bier's scene: the
 * screen closes, the player walks to a spot facing the core (AlchyX WalkScenes, the camera behind them), stands there
 * with the arms raised, swaying, for the ritual (client/ChantPose, from the mark ModRegistries.CHANTING: anchor x y z,
 * start, ticks; synced to everyone who sees them), then the screen opens again.
 */
@EventBusSubscriber(modid = AlchyForbidden.MODID)
public final class Chants {
    private Chants() {}

    public static final Identifier SCENE = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "chant");

    private record Pending(int ticks, Runnable onArrive, Runnable onEnd) {}

    private record Going(long until, Runnable onEnd) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Going> CHANTING = new HashMap<>();

    static {
        com.lealex.alchyx.scene.WalkScenes.onArrive(SCENE, Chants::arrive);
    }

    /**
     * Closes the screen and sends the player walking to `spot` (feet) facing the anchor; there, `onArrive` runs (the
     * ritual begins) and they chant for `ticks`; then `onEnd` (the screen again).
     */
    public static void begin(ServerPlayer player, BlockPos anchor, Vec3 spot, int ticks, Runnable onArrive, Runnable onEnd) {
        stop(player, false);
        player.closeContainer();
        PENDING.put(player.getUUID(), new Pending(ticks, onArrive, onEnd));
        double dx = anchor.getX() + 0.5 - spot.x, dz = anchor.getZ() + 0.5 - spot.z;
        float face = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        com.lealex.alchyx.scene.WalkScenes.walk(player, SCENE, anchor, spot, face, anchor.offset(-6, -4, -6), anchor.offset(6, 6, 6));
    }

    /** A spot on the floor `distance` blocks from the core toward the player (south when they stand on it), at height y. */
    public static Vec3 spotToward(Player player, BlockPos core, double distance, double y) {
        double dx = player.getX() - (core.getX() + 0.5), dz = player.getZ() - (core.getZ() + 0.5);
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.3) {
            dx = 0;
            dz = 1;
            length = 1;
        }
        return new Vec3(core.getX() + 0.5 + dx / length * distance, y, core.getZ() + 0.5 + dz / length * distance);
    }

    private static void arrive(ServerPlayer player, BlockPos anchor) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending == null) return;
        long now = player.level().getGameTime();
        com.lealex.alchyx.scene.WalkScenes.hold(player, true);
        player.setData(ModRegistries.CHANTING, List.of(anchor.getX(), anchor.getY(), anchor.getZ(), (int) now, pending.ticks()));
        CHANTING.put(player.getUUID(), new Going(now + pending.ticks() + 6, pending.onEnd()));
        pending.onArrive().run();
    }

    private static void stop(ServerPlayer player, boolean finished) {
        PENDING.remove(player.getUUID());
        Going going = CHANTING.remove(player.getUUID());
        if (!player.getData(ModRegistries.CHANTING).isEmpty()) player.setData(ModRegistries.CHANTING, List.of());
        com.lealex.alchyx.scene.WalkScenes.hold(player, false);
        if (finished && going != null && player.isAlive()) going.onEnd().run();
    }

    // ---- What the client reads ----

    /** The core a player chants at, or null. */
    public static @Nullable BlockPos anchor(Player player) {
        List<Integer> data = player.getData(ModRegistries.CHANTING);
        return data.size() < 5 ? null : new BlockPos(data.get(0), data.get(1), data.get(2));
    }

    /** Ticks into the chant. */
    public static float time(Player player, float partial) {
        List<Integer> data = player.getData(ModRegistries.CHANTING);
        return data.size() < 5 ? 0F : (int) player.level().getGameTime() - data.get(3) + partial;
    }

    /** How long the chant lasts. */
    public static int length(Player player) {
        List<Integer> data = player.getData(ModRegistries.CHANTING);
        return data.size() < 5 ? 0 : data.get(4);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Going going = CHANTING.get(player.getUUID());
        if (going == null) return;
        if (!player.isAlive() || player.level().getGameTime() >= going.until()) stop(player, true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        CHANTING.remove(event.getEntity().getUUID());
    }
}
