package com.lealex.alchyforbidden.essence;

import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.block.entity.AltarBlockEntity;
import com.lealex.alchyforbidden.block.entity.RelayBlockEntity;
import com.lealex.alchyforbidden.network.AltarRiftPayload;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Essences carried through Echo Obelisks. A source (Killing Pit, Lament Well, Echo Resonator) attuned to a relay sends
 * there instead of to the nearest altar; each relay sends on to its own target, relay after relay, until an altar.
 * Every link must be within Config.relayRange; a chain longer than Config.relayMaxHops, a loop, a missing or unloaded
 * relay or no altar at the end breaks it (the source keeps what it makes, as when no altar has room). The essence
 * reaches the altar at once; the streams show it going hop by hop.
 */
public final class Relays {
    private Relays() {}

    /** How long one hop's stream flies (client/AltarRifts' stream is 26 ticks). */
    private static final int HOP_TICKS = AltarRiftPayload.GUIDE_TICKS;

    /** The relays (their feet) a route goes through, and what it ends at (an altar, a Weeping Fountain). */
    public record Route(List<BlockPos> relays, EssenceReceiver receiver) {}

    /**
     * One hop's stream, fired at `at`; hops of one delivery share `id` (a broken obelisk stops the rest of them).
     * `end`: the receiver's place for the last hop (null for a hop into an obelisk).
     */
    private record Hop(ServerLevel level, long at, BlockPos from, BlockPos to, Essence essence, boolean fromRelay, long id, @Nullable BlockPos end) {}

    private static long nextId = 0;

    private static final List<Hop> HOPS = new ArrayList<>();

    /** The altar a link points at: there, or up to two blocks over or under it (an altar rises a block a tier, sinks back when broken). */
    public static @Nullable AltarBlockEntity altarAt(Level level, BlockPos pos) {
        for (int dy : new int[] {0, 1, -1, 2, -2}) {
            if (level.getBlockEntity(pos.above(dy)) instanceof AltarBlockEntity altar) return altar;
        }
        return null;
    }

    /** What receives essence at a link's place: an altar (there or up to two blocks off), or a Weeping Fountain's core. */
    public static @Nullable EssenceReceiver receiverAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof EssenceReceiver receiver && !(receiver instanceof AltarBlockEntity)) return receiver; // a fountain, a bier
        return altarAt(level, pos);
    }

    /** Whether a link to pos still leads somewhere (an obelisk's foot, an altar, a fountain): the fork draws only those. */
    public static boolean leadsSomewhere(Level level, BlockPos pos) {
        return !com.lealex.alchyx.link.Links.goneEntity(level, pos, entity -> entity instanceof RelayBlockEntity || receiverAt(level, pos) != null);
    }

    /**
     * An obelisk or an altar at pos is broken (not unloaded: called from preRemoveSideEffects): every link within reach
     * that pointed at it is cut, so nothing keeps sending (or showing a thread) to an empty place. For an altar, links
     * to the cells a risen or sunk altar would answer from count too.
     */
    public static void unlinkTo(ServerLevel level, BlockPos pos, boolean altar) {
        com.lealex.alchyx.link.Links.forEachNear(level, pos, Config.relayRange(), entity -> {
            java.util.function.Predicate<BlockPos> hits = link -> altar
                    ? link.getX() == pos.getX() && link.getZ() == pos.getZ() && Math.abs(link.getY() - pos.getY()) <= 2 : link.equals(pos);
            if (entity instanceof RelayBlockEntity relay) {
                for (BlockPos link : List.copyOf(relay.targets())) if (hits.test(link)) relay.removeTarget(link);
            } else if (entity instanceof com.lealex.alchyforbidden.block.entity.GrowingCoreBlockEntity core && core.relayLink() != null
                    && hits.test(core.relayLink())) {
                core.setRelayLink(null);
            }
        });
    }

    /** The route from a sender at {@code from} linked to {@code link} to anything at all (its screen), or null. Either side. */
    public static @Nullable Route route(Level level, BlockPos from, BlockPos link) {
        return route(level, from, link, null);
    }

    /**
     * The route from a sender at {@code from} linked to {@code link} to something with room for the essence (any receiver
     * when it is null), or null when none can be reached. An obelisk's branches are tried in turn from the one after
     * the last used, so they share what comes through; a branch whose end is full is passed over. Either side.
     */
    public static @Nullable Route route(Level level, BlockPos from, BlockPos link, @Nullable Essence essence) {
        return route(level, from, link, essence, true);
    }

    /** As above; {@code advance} false only looks (a screen asking where it would go) and leaves the obelisks' turns. */
    public static @Nullable Route route(Level level, BlockPos from, BlockPos link, @Nullable Essence essence, boolean advance) {
        Set<BlockPos> path = new HashSet<>();
        path.add(from);
        return search(level, from, link, essence, advance, new ArrayList<>(), path);
    }

    private static @Nullable Route search(Level level, BlockPos at, BlockPos next, @Nullable Essence essence, boolean advance,
                                          List<BlockPos> relays, Set<BlockPos> path) {
        long range = (long) Config.relayRange() * Config.relayRange();
        if (at.distSqr(next) > range || !level.isLoaded(next)) return null;
        BlockEntity entity = level.getBlockEntity(next);
        if (entity instanceof RelayBlockEntity relay) {
            if (path.contains(next) || relays.size() >= Config.relayMaxHops()) return null;
            List<BlockPos> targets = relay.targets();
            if (targets.isEmpty()) return null;
            path.add(next);
            relays.add(next);
            for (int k = 0; k < targets.size(); k++) {
                int index = (relay.turn() + k) % targets.size();
                Route found = search(level, next, targets.get(index), essence, advance, relays, path);
                if (found != null) {
                    if (essence != null && advance) relay.setTurn(index + 1); // the next delivery tries the next branch first
                    return found;
                }
            }
            relays.removeLast(); // (it stays in path: any other way through it leads nowhere either)
            return null;
        }
        EssenceReceiver receiver = null;
        if (entity instanceof EssenceReceiver other && !(other instanceof AltarBlockEntity)
                && entity instanceof com.lealex.alchyx.block.entity.MultiblockCoreBlockEntity core && core.isFormed()) {
            receiver = other;
        } else {
            AltarBlockEntity altar = altarAt(level, next);
            if (altar != null && altar.isFormed() && altar.hasEye()) receiver = altar;
        }
        if (receiver == null || (essence != null && receiver.room(essence) <= 0)) return null;
        return new Route(List.copyOf(relays), receiver);
    }

    /**
     * Sends an essence from a sender linked to {@code link}, split over its branches as far as they have room (a few
     * deliveries at most). Returns what nothing took (the sender keeps it).
     */
    public static int send(ServerLevel level, BlockPos sender, BlockPos link, Essence essence, int amount, BlockPos start) {
        for (int tries = 0; tries < 4 && amount > 0; tries++) {
            Route route = route(level, sender, link, essence);
            if (route == null) break;
            int taken = deliver(level, essence, amount, start, route);
            if (taken <= 0) break;
            amount -= taken;
        }
        return amount;
    }

    /** Sends what it can along the route; the streams fly hop by hop from {@code start}. Returns how much the altar took. */
    public static int deliver(ServerLevel level, Essence essence, int amount, BlockPos start, Route route) {
        if (amount <= 0) return 0;
        int taken = route.receiver().addEssence(essence, amount);
        if (taken <= 0) return 0;
        long now = level.getGameTime();
        BlockPos from = start;
        long id = nextId++;
        int i = 0;
        for (BlockPos relay : route.relays()) {
            BlockPos crystal = com.lealex.alchyforbidden.block.RelayBlock.top(level, relay);
            HOPS.add(new Hop(level, now + (long) i * HOP_TICKS, from, crystal, essence, i > 0, id, null));
            from = crystal;
            i++;
        }
        HOPS.add(new Hop(level, now + (long) i * HOP_TICKS, from, route.receiver().intakePos(), essence, i > 0, id,
                route.receiver().receiverPos())); // into an altar's eye, a fountain's spout
        return taken;
    }

    /** A guided stream of that essence from the centre of one block to the centre of another. */
    public static void guide(ServerLevel level, BlockPos from, BlockPos to, Essence essence) {
        AltarRiftPayload.part(level, AltarRiftPayload.GUIDE, from, to, Blocks.AIR.defaultBlockState(), essence.ordinal() + 1);
    }

    /** The server is stopping: the streams still on their way go with it (they hold its levels). */
    public static void clear() {
        HOPS.clear();
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (HOPS.isEmpty()) return;
        for (int i = HOPS.size() - 1; i >= 0; i--) {
            Hop hop = HOPS.get(i);
            if (hop.level().getGameTime() < hop.at()) continue;
            HOPS.remove(i);
            if (!hop.level().isLoaded(hop.from())) continue;
            // an end broken since it was sent: the essence is already in the altar (it arrives at once), so only the
            // rest of the show is dropped, no stream flying out of or into nothing
            boolean fromGone = hop.fromRelay() && !hop.level().getBlockState(hop.from()).is(com.lealex.alchyforbidden.registry.ModRegistries.ECHO_OBELISK.get());
            boolean toGone = hop.end() == null ? !hop.level().getBlockState(hop.to()).is(com.lealex.alchyforbidden.registry.ModRegistries.ECHO_OBELISK.get())
                    : receiverAt(hop.level(), hop.end()) == null;
            if (fromGone || toGone) {
                long dead = hop.id();
                HOPS.removeIf(other -> other.id() == dead);
                i = HOPS.size();
                continue;
            }
            // the stream, guided straight from the centre of `from` to the centre of `to` (an obelisk's crystal, an altar's eye)
            guide(hop.level(), hop.from(), hop.to(), hop.essence());
            if (hop.fromRelay()) { // the crystal flares as it sends on
                hop.level().sendParticles(new DustParticleOptions(hop.essence().color(), 1.2F), hop.from().getX() + 0.5, hop.from().getY() + 0.5,
                        hop.from().getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.01);
            }
        }
    }
}
