package com.lealex.alchyforbidden.block.entity;

import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.essence.Essence;
import com.lealex.alchyforbidden.essence.Inscription;
import com.lealex.alchyforbidden.essence.Inscriptions;
import com.lealex.alchyforbidden.essence.Relays;
import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Inscribing Bier: a sacrificial slab under the rift's needles where the mage carved his own body. Its screen shows
 * the player's body and its parts; a carving (essence/Inscription) is paid with items from the inventory and essences
 * drawn from the nearest awake altars, and the needles trace it into the body (a show of streams and dust while the
 * player stands by). The tier opens stronger carvings. A carving can be burnt away (it hurts).
 */
public class InscribingBierBlockEntity extends GrowingCoreBlockEntity implements net.minecraft.world.MenuProvider,
        com.lealex.alchyforbidden.essence.EssenceReceiver {
    /** How long the needles trace a carving. */
    public static final int CARVE_TICKS = 60;

    private record Carving(ServerPlayer player, Inscription inscription, long start) {}

    private final List<Carving> carving = new ArrayList<>();
    /** Essence its links sent it (sources, obelisks): drawn first. Saved, synced. */
    private final int[] held = new int[Essence.values().length];

    private static final java.util.Map<Level, java.util.Set<InscribingBierBlockEntity>> LOADED = new java.util.WeakHashMap<>();

    public static List<InscribingBierBlockEntity> loaded(Level level) {
        java.util.Set<InscribingBierBlockEntity> set = LOADED.get(level);
        return set == null ? List.of() : new ArrayList<>(set);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) LOADED.computeIfAbsent(level, key -> java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>())).add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && LOADED.containsKey(level)) LOADED.get(level).remove(this);
    }

    public InscribingBierBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.INSCRIBING_BIER_BE.get(), pos, state);
    }

    @Override
    protected String patternBase() {
        return "inscribing_bier";
    }

    @Override
    protected List<Config.Cost> costFor(int tier) {
        return Config.bierCostFor(tier);
    }

    @Override
    protected String messagePrefix() {
        return "inscribing_bier";
    }

    @Override
    protected String machineName() {
        return "Inscribing bier";
    }

    @Override
    protected BlockPos riftPos() {
        return worldPosition.above(7);
    }

    @Override
    protected int riftSize() {
        return 3;
    }

    /** Where the needles hang over the slab (the arch's at the last tier). */
    public Vec3 needles() {
        return Vec3.atBottomCenterOf(worldPosition).add(0, standing >= 3 ? 3.0 : 2.6, 0);
    }

    // ---- Carving ----

    /** Why that carving cannot be made for that player now (null: it can). Either side (the screen greys the button). */
    public @Nullable Component refusal(Player player, Inscription inscription) {
        if (!isFormed()) return Component.translatable("inscribing_bier.alchyforbidden.not_whole");
        if (inscription.tier() > standing) return Component.translatable("inscribing_bier.alchyforbidden.locked", inscription.tier());
        if (Inscriptions.has(player, inscription)) return Component.translatable("inscribing_bier.alchyforbidden.already");
        int room = Config.carvingsPerPart(standing);
        if (Inscriptions.on(player, inscription.part()).size() >= room) {
            return Component.translatable("inscribing_bier.alchyforbidden.part_full", room, standing < maxTier() ? Component.translatable("inscribing_bier.alchyforbidden.grow_more") : Component.empty());
        }
        if (Inscriptions.corruption(player) >= Inscriptions.capacity(player)) {
            return Component.translatable("inscribing_bier.alchyforbidden.full_slots", Inscriptions.capacity(player));
        }
        if (!player.getAbilities().instabuild) {
            for (Inscription.Cost cost : inscription.costs()) {
                if (count(player, cost) < cost.count()) return Component.translatable("inscribing_bier.alchyforbidden.missing", cost.stack().getHoverName());
            }
        }
        // (essences only on the server: the client does not see every source's store, the screen marks them red instead)
        for (Essence essence : level == null || level.isClientSide() ? new Essence[0] : Essence.values()) {
            int need = inscription.essence(essence);
            if (need > 0 && available(player, essence) < need) {
                return Component.translatable("inscribing_bier.alchyforbidden.thirsty", need, essence.icon());
            }
        }
        return null;
    }

    public static int count(Player player, Inscription.Cost cost) {
        int have = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(cost.item())) have += stack.getCount();
        }
        return have;
    }

    /**
     * How much of an essence a carving could draw for that player, from (in this order) what its links sent the bier,
     * the player's carved reserve, the vessels they carry, what the producing multiblocks within reach keep waiting, and
     * the awake altars within reach. Either side (on the client a little may be missed: the server is the judge).
     */
    public int available(Player player, Essence essence) {
        long total = held[essence.ordinal()] + Inscriptions.reserve(player, essence);
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var contents = com.lealex.alchyforbidden.item.VesselItem.contents(player.getInventory().getItem(i));
            if (contents != null && contents.essence() == essence) total += contents.amount();
        }
        for (GrowingCoreBlockEntity source : sources(essence)) total += buffered(source);
        for (AltarBlockEntity altar : altars()) total += altar.essence(essence);
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** Takes that much of an essence from those places, in the same order; streams flow into the needles. */
    private void draw(ServerPlayer player, Essence essence, int amount) {
        ServerLevel server = (ServerLevel) level;
        BlockPos needleBlock = BlockPos.containing(needles());
        int from = Math.min(amount, held[essence.ordinal()]);
        held[essence.ordinal()] -= from;
        amount -= from;
        int fromBody = Math.min(amount, Inscriptions.reserve(player, essence));
        if (fromBody > 0 && Inscriptions.spend(player, essence, fromBody)) {
            amount -= fromBody;
            Relays.guide(server, player.blockPosition().above(), needleBlock, essence);
        }
        boolean vessels = false;
        for (int i = 0; i < player.getInventory().getContainerSize() && amount > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            var contents = com.lealex.alchyforbidden.item.VesselItem.contents(stack);
            if (contents == null || contents.essence() != essence) continue;
            amount -= com.lealex.alchyforbidden.item.VesselItem.drain(stack, amount);
            vessels = true;
        }
        if (vessels) Relays.guide(server, player.blockPosition().above(), needleBlock, essence);
        for (GrowingCoreBlockEntity source : sources(essence)) {
            if (amount <= 0) break;
            int take = Math.min(amount, buffered(source));
            if (take > 0 && takeBuffered(source, take)) {
                amount -= take;
                Relays.guide(server, source.relayOrigin(), needleBlock, essence);
            }
        }
        for (AltarBlockEntity altar : altars()) {
            if (amount <= 0) break;
            int take = Math.min(amount, altar.essence(essence));
            if (take > 0 && altar.spendEssence(essence, take)) {
                amount -= take;
                Relays.guide(server, altar.getBlockPos().above(), needleBlock, essence);
            }
        }
        setChanged();
        syncToClients();
    }

    private List<? extends GrowingCoreBlockEntity> sources(Essence essence) {
        if (level == null) return List.of();
        List<? extends GrowingCoreBlockEntity> all = switch (essence) {
            case SORROW -> LamentWellBlockEntity.loaded(level);
            case WRATH -> KillingPitBlockEntity.loaded(level);
            case CORRUPTION -> ResonatorBlockEntity.loaded(level);
        };
        double reach = (double) Config.bierReach() * Config.bierReach();
        List<GrowingCoreBlockEntity> near = new ArrayList<>();
        for (GrowingCoreBlockEntity source : all) {
            if (!source.isRemoved() && source.isFormed() && source.getBlockPos().distSqr(worldPosition) <= reach) near.add(source);
        }
        return near;
    }

    private static int buffered(GrowingCoreBlockEntity source) {
        if (source instanceof LamentWellBlockEntity well) return well.buffer();
        if (source instanceof KillingPitBlockEntity pit) return pit.buffer();
        return source instanceof ResonatorBlockEntity resonator ? resonator.buffer() : 0;
    }

    private static boolean takeBuffered(GrowingCoreBlockEntity source, int amount) {
        if (source instanceof LamentWellBlockEntity well) return well.takeBuffered(amount);
        if (source instanceof KillingPitBlockEntity pit) return pit.takeBuffered(amount);
        return source instanceof ResonatorBlockEntity resonator && resonator.takeBuffered(amount);
    }

    /** The awake altars within reach, nearest first. */
    private List<AltarBlockEntity> altars() {
        if (level == null) return List.of();
        double reach = (double) Config.bierReach() * Config.bierReach();
        List<AltarBlockEntity> near = new ArrayList<>();
        for (AltarBlockEntity altar : AltarBlockEntity.loaded(level)) {
            if (altar.isFormed() && altar.hasEye() && altar.getBlockPos().distSqr(worldPosition) <= reach) near.add(altar);
        }
        near.sort(java.util.Comparator.comparingDouble(altar -> altar.getBlockPos().distSqr(worldPosition)));
        return near;
    }

    // ---- What links send it ----

    @Override
    public int addEssence(Essence essence, int amount) {
        int taken = Math.max(0, Math.min(amount, room(essence)));
        if (taken > 0) {
            held[essence.ordinal()] += taken;
            setChanged();
            syncToClients();
        }
        return taken;
    }

    @Override
    public int room(Essence essence) {
        return Math.max(0, Config.bierStore() - held[essence.ordinal()]);
    }

    /** Streams land in its needles. */
    @Override
    public BlockPos intakePos() {
        return BlockPos.containing(needles());
    }

    @Override
    public BlockPos receiverPos() {
        return worldPosition;
    }

    public int held(Essence essence) {
        return held[essence.ordinal()];
    }

    /** The screen's Inscribe: pays and carves, the needles tracing it. */
    public void inscribe(ServerPlayer player, Inscription inscription) {
        Component refusal = refusal(player, inscription);
        if (refusal != null) {
            player.sendSystemMessage(refusal.copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        ServerLevel server = (ServerLevel) level;
        if (!player.getAbilities().instabuild) {
            for (Inscription.Cost cost : inscription.costs()) {
                int left = cost.count();
                for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (!stack.is(cost.item())) continue;
                    int taken = Math.min(left, stack.getCount());
                    stack.shrink(taken);
                    left -= taken;
                }
            }
        }
        for (Essence essence : Essence.values()) { // drawn into the needles from wherever it is
            int need = inscription.essence(essence);
            if (need > 0) draw(player, essence, need);
        }
        Inscriptions.carve(player, inscription);
        carving.add(new Carving(player, inscription, server.getGameTime()));
        com.lealex.alchyforbidden.essence.BierRest.act(player, com.lealex.alchyforbidden.essence.BierRest.Act.CARVE, inscription.part());
        server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 0.6f);
        server.playSound(null, worldPosition, SoundEvents.SCULK_CLICKING, SoundSource.BLOCKS, 1.0f, 0.8f);
    }

    // ---- Opening the body for more carvings (the screen's Slots tab) ----

    /** One step of opening: its items and its essences (Wrath / Sorrow / Corruption), and the bier tier it needs. */
    public record Expansion(java.util.List<Inscription.Cost> items, int[] essences, int bierTier) {}

    private static Inscription.Cost cost(String id, int count) {
        return new Inscription.Cost(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id)), count);
    }

    /** What opening the body from level `from` to the next one takes (null at the top). */
    public static @Nullable Expansion expansion(int from) {
        return switch (from) {
            case 0 -> new Expansion(java.util.List.of(cost("minecraft:amethyst_shard", 16), cost("minecraft:echo_shard", 2)), new int[] {40, 40, 40}, 1);
            case 1 -> new Expansion(java.util.List.of(cost("minecraft:amethyst_block", 4), cost("minecraft:echo_shard", 6), cost("minecraft:ghast_tear", 2)), new int[] {90, 90, 90}, 1);
            case 2 -> new Expansion(java.util.List.of(cost("minecraft:sculk_catalyst", 2), cost("minecraft:echo_shard", 12), cost("minecraft:crying_obsidian", 8)), new int[] {180, 180, 180}, 2);
            case 3 -> new Expansion(java.util.List.of(cost("minecraft:nether_star", 1), cost("minecraft:echo_shard", 16), cost("minecraft:totem_of_undying", 1)), new int[] {350, 350, 350}, 3);
            default -> null;
        };
    }

    /** Why this body cannot be opened further here now, or null. */
    public @Nullable Component expandRefusal(Player player) {
        if (!isFormed()) return Component.translatable("inscribing_bier.alchyforbidden.not_whole");
        Expansion next = expansion(Inscriptions.carvingLevel(player));
        if (next == null) return Component.translatable("inscribing_bier.alchyforbidden.slots_max");
        if (standing < next.bierTier()) return Component.translatable("inscribing_bier.alchyforbidden.slots_tier", next.bierTier());
        if (!player.getAbilities().instabuild) {
            for (Inscription.Cost cost : next.items()) {
                if (count(player, cost) < cost.count()) return Component.translatable("inscribing_bier.alchyforbidden.missing", cost.stack().getHoverName());
            }
        }
        for (Essence essence : level == null || level.isClientSide() ? new Essence[0] : Essence.values()) {
            int need = next.essences()[essence.ordinal()];
            if (need > 0 && !player.getAbilities().instabuild && available(player, essence) < need) {
                return Component.translatable("inscribing_bier.alchyforbidden.thirsty", need, essence.icon());
            }
        }
        return null;
    }

    /** The screen's Open: pays, the body takes three more carvings, and the opening plays on the slab. */
    public void expand(ServerPlayer player) {
        Component refusal = expandRefusal(player);
        if (refusal != null) {
            player.sendSystemMessage(refusal.copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        Expansion next = expansion(Inscriptions.carvingLevel(player));
        if (!player.getAbilities().instabuild) {
            for (Inscription.Cost cost : next.items()) {
                int left = cost.count();
                for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (!stack.is(cost.item())) continue;
                    int taken = Math.min(left, stack.getCount());
                    stack.shrink(taken);
                    left -= taken;
                }
            }
            for (Essence essence : Essence.values()) if (next.essences()[essence.ordinal()] > 0) draw(player, essence, next.essences()[essence.ordinal()]);
        }
        player.setData(ModRegistries.CARVING_LEVEL, Inscriptions.carvingLevel(player) + 1);
        com.lealex.alchyforbidden.essence.BierRest.act(player, com.lealex.alchyforbidden.essence.BierRest.Act.EXPAND, Inscription.Part.CHEST);
        player.sendSystemMessage(Component.translatable("inscribing_bier.alchyforbidden.slots_opened", Inscriptions.capacity(player)).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** The screen's Burn away: that carving is burnt out of the body, and that hurts. */
    public void burnAway(ServerPlayer player, Inscription inscription) {
        if (!Inscriptions.has(player, inscription) || !isFormed()) return;
        Inscription.Part part = inscription.part();
        Inscriptions.burn(player, inscription);
        ServerLevel server = (ServerLevel) level;
        Vec3 at = com.lealex.alchyforbidden.essence.BierRest.partAt(player, part);
        com.lealex.alchyforbidden.essence.BierRest.act(player, com.lealex.alchyforbidden.essence.BierRest.Act.BURN, part);
        server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 20, 0.25, 0.25, 0.25, 0.03);
        server.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.02);
        server.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 0.6f);
        if (!player.getAbilities().instabuild && player.getHealth() > 4) player.hurtServer(server, server.damageSources().magic(), 4.0F);
    }

    /** How high on the body a part is (feet = 0). */
    public static double partHeight(Inscription.Part part) {
        return switch (part) {
            case HEAD -> 1.65;
            case CHEST, BACK -> 1.2;
            case ARMS -> 1.05;
            case LEGS -> 0.6;
            case FEET -> 0.12;
            case SKIN -> 0.9;
        };
    }

    @Override
    protected void tickWorking() {
        if (!(level instanceof ServerLevel server) || carving.isEmpty()) return;
        long now = server.getGameTime();
        Vec3 needles = needles();
        carving.removeIf(c -> c.player().isRemoved() || now - c.start() > CARVE_TICKS);
        for (Carving c : carving) {
            long t = now - c.start();
            ServerPlayer player = c.player();
            Inscription inscription = c.inscription();
            Vec3 part = com.lealex.alchyforbidden.essence.BierRest.partAt(player, inscription.part()); // on the slab when lying
            // the needles trace it: a thread of light from them into the part, the glyph turning round it
            if (t % 4 == 0) {
                int steps = (int) Math.max(3, needles.distanceTo(part) * 3);
                for (int i = 0; i <= steps; i += 2) {
                    Vec3 p = needles.lerp(part, (i + (t / 4) % 2) / (double) steps);
                    server.sendParticles(new DustParticleOptions(inscription.color(), 0.8F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
            }
            double a = t * 0.45;
            double r = inscription.part() == Inscription.Part.SKIN ? 0.6 : 0.4;
            for (int k = 0; k < 3; k++) {
                double angle = a + k * Math.PI * 2 / 3;
                server.sendParticles(new DustParticleOptions(inscription.color(), 1.0F), part.x + Math.cos(angle) * r,
                        part.y + (inscription.part() == Inscription.Part.SKIN ? Math.sin(t * 0.2 + k) * 0.7 : 0), part.z + Math.sin(angle) * r, 1, 0, 0, 0, 0);
            }
            if (t % 10 == 0) {
                server.sendParticles(ModRegistries.RIFT_MOTE.get(), needles.x, needles.y, needles.z, 4, 0.15, 0.1, 0.15, 0.02);
                server.sendParticles(ParticleTypes.SCULK_CHARGE_POP, part.x, part.y, part.z, 3, 0.2, 0.2, 0.2, 0.01);
            }
            if (t % 20 == 10) server.playSound(null, player.blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 0.6f, 1.2f);
            if (t == CARVE_TICKS) { // it is written: it flares and sinks into the skin
                server.sendParticles(new DustParticleOptions(inscription.color(), 1.6F), part.x, part.y, part.z, 24, 0.3, 0.3, 0.3, 0.02);
                server.sendParticles(ParticleTypes.SCULK_SOUL, part.x, part.y, part.z, 6, 0.2, 0.3, 0.2, 0.03);
                server.playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 0.7f);
                server.playSound(null, player.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.0f, 0.8f);
            }
        }
    }

    // ---- Screen, saving ----

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (isMoving()) return;
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) Relays.unlinkTo(server, pos, false); // links that sent to it are cut
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        output.putIntArray("held", held.clone());
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        int[] kept = input.getIntArray("held").orElse(new int[0]);
        java.util.Arrays.fill(held, 0);
        for (int i = 0; i < held.length && i < kept.length; i++) held[i] = kept[i];
    }

    @Override
    public void openGui(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        // a click on any of its blocks lands here: walk to the slab and lie down first (the screen comes after)
        if (isFormed() && !player.isShiftKeyDown() && com.lealex.alchyforbidden.essence.BierRest.walk(serverPlayer, worldPosition)) return;
        tryGrowing();
        serverPlayer.openMenu(this, out -> out.writeBlockPos(worldPosition));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.alchyforbidden.inscribing_bier");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, Player player) {
        return new com.lealex.alchyforbidden.menu.InscribingMenu(id, inventory, this);
    }

    /** Its three essences beside the crosshair (AlchyX LookMeters). */
    @Override
    public void lookMeters(java.util.List<LookMeter> out) {
        for (com.lealex.alchyforbidden.essence.Essence e : com.lealex.alchyforbidden.essence.Essence.values()) {
            out.add(new LookMeter(held(e), com.lealex.alchyforbidden.Config.bierStore(), 0xFF000000 | e.color(), e.icon()));
        }
    }
}
