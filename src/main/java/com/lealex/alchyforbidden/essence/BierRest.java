package com.lealex.alchyforbidden.essence;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.block.entity.InscribingBierBlockEntity;
import com.lealex.alchyforbidden.menu.InscribingMenu;
import com.lealex.alchyforbidden.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lying on the Inscribing Bier: a right-click on any of its blocks (InscribingBierBlockEntity.openGui) sends the
 * player walking to the long side of the slab nearest them (AlchyX WalkScenes: the path, the camera, passing through
 * the bier's own blocks); once there (the scene's arrival) the server marks them lying (ModRegistries.RESTING,
 * synced, so everyone sees them on the slab: client/WingLayer's state modifier lays the model down) and opens the
 * screen once their client says the lying down has played out (ready; LIE_TICKS at the latest). They get up when
 * the screen closes, the bier goes, or they leave it.
 * <p>
 * Not a bed: the game never sees them sleeping (no night skip, no spawn point), only the model lies down.
 */
@EventBusSubscriber(modid = AlchyForbidden.MODID)
public final class BierRest {
    private BierRest() {}

    /**
     * The longest they lie on the slab before the screen opens: normally their client says when its lying down and fade
     * are done (ready), this is only for a client that never says.
     */
    public static final int LIE_TICKS = 160;

    /** The walk scene to the bier (AlchyX WalkScenes / SceneWalk). */
    public static final net.minecraft.resources.Identifier SCENE = net.minecraft.resources.Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "bier");
    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> SLAB = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.BLOCK, net.minecraft.resources.Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "altar_base"));

    /** Where each walking player will lie: head facing, side (worked out when the walk starts). */
    private static final Map<UUID, Direction[]> PENDING = new HashMap<>();

    static {
        com.lealex.alchyx.scene.WalkScenes.onArrive(SCENE, (player, anchor) -> {
            Direction[] way = PENDING.remove(player.getUUID());
            if (way != null) lie(player, anchor, way[0], way[1]);
        });
    }

    /** When each lying player's screen opens (game time). */
    private static final Map<UUID, Long> OPENING = new HashMap<>();

    /** What a lying body goes through while the screen is closed: a carving traced into it, or one burnt out. */
    public enum Act { NONE, CARVE, BURN, EXPAND }

    public static final int CARVE_ACT = InscribingBierBlockEntity.CARVE_TICKS + 12, BURN_ACT = 44, EXPAND_ACT = 80;

    /** When each acting player's act ends and their screen opens again (game time). */
    private static final Map<UUID, Long> ACTING = new HashMap<>();

    /**
     * A carving or a burning on a lying player: the screen closes, everyone sees the body on the slab go through it
     * (client/BierLying, from the mark: act, part, start), the camera watching; the screen opens again after it.
     * Not lying (sneak-opened screen): nothing more than the show itself.
     */
    public static void act(ServerPlayer player, Act act, Inscription.Part part) {
        List<Integer> data = player.getData(ModRegistries.RESTING);
        if (data.size() < 6) return;
        int ticks = act == Act.CARVE ? CARVE_ACT : act == Act.EXPAND ? EXPAND_ACT : BURN_ACT;
        player.setData(ModRegistries.RESTING, List.of(data.get(0), data.get(1), data.get(2), data.get(3), data.get(4), data.get(5),
                act.ordinal(), part.ordinal(), (int) player.level().getGameTime()));
        ACTING.put(player.getUUID(), player.level().getGameTime() + ticks);
        player.closeContainer();
    }

    /** The act a lying player is going through, NONE if none. */
    public static Act act(Player player) {
        List<Integer> data = player.getData(ModRegistries.RESTING);
        return data.size() < 9 ? Act.NONE : Act.values()[Math.floorMod(data.get(6), Act.values().length)];
    }

    /** The body part of the act. */
    public static Inscription.Part actPart(Player player) {
        List<Integer> data = player.getData(ModRegistries.RESTING);
        return data.size() < 9 ? Inscription.Part.CHEST : Inscription.Part.values()[Math.floorMod(data.get(7), Inscription.Part.values().length)];
    }

    /** Ticks into the act (game time kept as an int both sides). */
    public static float actTime(Player player, float partialTick) {
        List<Integer> data = player.getData(ModRegistries.RESTING);
        return data.size() < 9 ? 0F : (int) player.level().getGameTime() - data.get(8) + partialTick;
    }

    /** Ticks since the player lay down on the slab (the lying-down animation plays from this, once). */
    public static float lieTime(Player player, float partialTick) {
        List<Integer> data = player.getData(ModRegistries.RESTING);
        return data.size() < 6 ? 0F : (int) player.level().getGameTime() - data.get(5) + partialTick;
    }

    /**
     * Where a body part is: on the body lying on the slab, or on the standing player if they are not lying. Along the
     * lying body from its feet (half a body short of the core's centre), just over the stone.
     */
    public static Vec3 partAt(Player player, Inscription.Part part) {
        BlockPos pos = lyingOn(player);
        Direction head = facing(player);
        double along = InscribingBierBlockEntity.partHeight(part);
        if (pos == null || head == null) return player.position().add(0, along, 0);
        double fx = pos.getX() + 0.5 - head.getStepX() * 0.94, fz = pos.getZ() + 0.5 - head.getStepZ() * 0.94;
        return new Vec3(fx + head.getStepX() * along, pos.getY() + 1.3, fz + head.getStepZ() * along);
    }

    /** The bier a player is lying on, or null. */
    public static @Nullable BlockPos lyingOn(Player player) {
        List<Integer> data = player.getData(ModRegistries.RESTING);
        return data.size() < 6 ? null : new BlockPos(data.get(0), data.get(1), data.get(2));
    }

    /** Where a lying player's head points (as a bed's facing), or null when not lying. */
    public static @Nullable Direction facing(Player player) {
        List<Integer> data = player.getData(ModRegistries.RESTING);
        return data.size() < 6 ? null : Direction.from3DDataValue(data.get(3));
    }

    /** The long side of the slab a lying player got on from (where they stand), or null when not lying. */
    public static @Nullable Direction side(Player player) {
        List<Integer> data = player.getData(ModRegistries.RESTING);
        return data.size() < 6 ? null : Direction.from3DDataValue(data.get(4));
    }

    /**
     * Send the player walking to the bier at pos (false when they already lie on it: the screen opens then): to the
     * slab's long side nearest them, 1.3 out on the floor, to lie with the head toward the end they came from. The
     * slab's axis from the #altar_base blocks either side of the core.
     */
    public static boolean walk(ServerPlayer player, BlockPos pos) {
        if (lyingOn(player) != null) return false;
        net.minecraft.world.level.Level level = player.level();
        boolean alongZ = !(level.getBlockState(pos.east()).is(SLAB) && level.getBlockState(pos.west()).is(SLAB))
                && level.getBlockState(pos.north()).is(SLAB) && level.getBlockState(pos.south()).is(SLAB);
        Vec3 c = Vec3.atBottomCenterOf(pos);
        double rx = player.getX() - c.x, rz = player.getZ() - c.z;
        Direction side = alongZ ? Direction.EAST : Direction.SOUTH;
        if (rx * side.getStepX() + rz * side.getStepZ() < 0) side = side.getOpposite();
        Direction head = alongZ ? Direction.SOUTH : Direction.EAST;
        if (rx * head.getStepX() + rz * head.getStepZ() < 0) head = head.getOpposite();
        PENDING.put(player.getUUID(), new Direction[] {head, side});
        Vec3 spot = new Vec3(c.x + side.getStepX() * 1.3, pos.getY(), c.z + side.getStepZ() * 1.3);
        com.lealex.alchyx.scene.WalkScenes.walk(player, SCENE, pos, spot, side.getOpposite().toYRot(),
                pos.offset(-4, -1, -4), pos.offset(4, 5, 4), head.get3DDataValue(), side.get3DDataValue());
        return true;
    }

    /** The client laid the player down on the bier at pos: mark them lying and open the screen soon. */
    public static void lie(ServerPlayer player, BlockPos pos, Direction facing, Direction side) {
        if (!facing.getAxis().isHorizontal() || !side.getAxis().isHorizontal()) return;
        if (!(player.level().getBlockEntity(pos) instanceof InscribingBierBlockEntity bier) || !bier.isFormed()) return;
        if (!bier.withinReach(player)) return; // anywhere they could have clicked it from (the walk may stall far off)
        player.setData(ModRegistries.RESTING, List.of(pos.getX(), pos.getY(), pos.getZ(), facing.get3DDataValue(), side.get3DDataValue(),
                (int) player.level().getGameTime()));
        com.lealex.alchyx.scene.WalkScenes.hold(player, true); // standing beside the slab, maybe in a pillar's shell
        OPENING.put(player.getUUID(), player.level().getGameTime() + LIE_TICKS); // unless their client says it is ready first
    }

    /** The client's lying down has played out (network/BierReadyPayload): the screen opens now. */
    public static void ready(ServerPlayer player) {
        if (lyingOn(player) != null && OPENING.containsKey(player.getUUID())) OPENING.put(player.getUUID(), player.level().getGameTime());
    }

    private static void stand(ServerPlayer player) {
        OPENING.remove(player.getUUID());
        ACTING.remove(player.getUUID());
        PENDING.remove(player.getUUID());
        com.lealex.alchyx.scene.WalkScenes.hold(player, false);
        if (!player.getData(ModRegistries.RESTING).isEmpty()) player.setData(ModRegistries.RESTING, List.of());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BlockPos pos = lyingOn(player);
        if (pos == null) return;
        if (!player.isAlive() || !(player.level().getBlockEntity(pos) instanceof InscribingBierBlockEntity bier)
                || !bier.withinReach(player)) {
            stand(player);
            return;
        }
        Long acting = ACTING.get(player.getUUID());
        if (acting != null) { // the body goes through it; then the screen again
            long now = player.level().getGameTime();
            if (act(player) == Act.BURN && now % 2 == 0) burning(player);
            if (act(player) == Act.EXPAND) expanding(player, (int) actTime(player, 0F));
            if (now < acting) return;
            ACTING.remove(player.getUUID());
            List<Integer> data = player.getData(ModRegistries.RESTING);
            if (data.size() >= 6) player.setData(ModRegistries.RESTING, List.copyOf(data.subList(0, 6)));
            bier.openGui(player);
        }
        Long due = OPENING.get(player.getUUID());
        if (due != null) {
            if (player.level().getGameTime() < due) return;
            OPENING.remove(player.getUUID());
            bier.openGui(player);
        }
        if (!(player.containerMenu instanceof InscribingMenu)) stand(player); // the screen closed (or never opened)
    }

    /**
     * A body opened for more carvings: the three essences spiral in from round the bier into its chest, faster and
     * closer, the body rising off the stone (client/BierLying); at the end a flash, a ring of souls and a heartbeat.
     */
    private static void expanding(ServerPlayer player, int t) {
        net.minecraft.server.level.ServerLevel server = player.level();
        Vec3 chest = partAt(player, Inscription.Part.CHEST).add(0, Math.min(1F, t / 30F) * 0.4, 0);
        if (t < EXPAND_ACT - 14) {
            float f = t / (float) (EXPAND_ACT - 14);
            double r = 2.6 * (1 - f) + 0.3;
            for (int k = 0; k < 3; k++) {
                double a = t * 0.35 + k * Math.PI * 2 / 3;
                Essence essence = Essence.values()[k];
                var mote = switch (essence) {
                    case WRATH -> ModRegistries.WRATH_MOTE.get();
                    case SORROW -> ModRegistries.SORROW_MOTE.get();
                    case CORRUPTION -> ModRegistries.CORRUPTION_MOTE.get();
                };
                server.sendParticles(mote, chest.x + Math.cos(a) * r, chest.y + Math.sin(t * 0.2 + k) * 0.3, chest.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            if (t % 6 == 0) server.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT, chest.x, chest.y + 0.5, chest.z, 6, 0.8, 0.4, 0.8, 0.5);
            if (t % 20 == 0) server.playSound(null, BlockPos.containing(chest), net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.8F + f * 0.6F);
        } else if (t == EXPAND_ACT - 14) {
            server.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(net.minecraft.core.particles.ParticleTypes.FLASH, 0xFFB98CFF), chest.x, chest.y, chest.z, 1, 0, 0, 0, 0);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.SCULK_SOUL, chest.x, chest.y, chest.z, 30, 0.6, 0.4, 0.6, 0.08);
            server.sendParticles(ModRegistries.RIFT_MOTE.get(), chest.x, chest.y, chest.z, 40, 0.8, 0.5, 0.8, 0.12);
            server.playSound(null, BlockPos.containing(chest), net.minecraft.sounds.SoundEvents.TOTEM_USE, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 0.6F);
            server.playSound(null, BlockPos.containing(chest), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.5F);
        }
    }

    /** A carving burning out: soul fire and smoke off the part, now and then a crackle. */
    private static void burning(ServerPlayer player) {
        net.minecraft.server.level.ServerLevel server = player.level();
        Vec3 at = partAt(player, actPart(player));
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 3, 0.18, 0.08, 0.18, 0.02);
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, at.x, at.y + 0.1, at.z, 2, 0.15, 0.05, 0.15, 0.01);
        if (player.level().getGameTime() % 10 == 0) {
            server.playSound(null, BlockPos.containing(at), net.minecraft.sounds.SoundEvents.FIRE_AMBIENT, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.7F);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        OPENING.remove(event.getEntity().getUUID());
        ACTING.remove(event.getEntity().getUUID());
        PENDING.remove(event.getEntity().getUUID());
    }
}
