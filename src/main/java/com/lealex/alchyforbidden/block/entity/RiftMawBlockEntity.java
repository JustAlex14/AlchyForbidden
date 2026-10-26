package com.lealex.alchyforbidden.block.entity;

import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.essence.Essence;
import com.lealex.alchyforbidden.essence.Inscriptions;
import com.lealex.alchyforbidden.familiar.FamiliarData;
import com.lealex.alchyforbidden.familiar.Familiars;
import com.lealex.alchyforbidden.familiar.RiftBeast;
import com.lealex.alchyforbidden.familiar.RiftCreature;
import com.lealex.alchyforbidden.item.FamiliarRingItem;
import com.lealex.alchyforbidden.item.RiftLureItem;
import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Rift Maw: a pit round its core where the rift is made to give up its creatures. Its ritual takes a creature's lure,
 * a blank familiar ring and the creature's essences (lost if the ritual fails), opens the rift over the pit and walls
 * the challenger in an arena round it (Config arenaRadius by tier). Walking into the wall throws them back; leaving it
 * any other way (a teleport, another world, death, logging out, time running out) loses the ritual at once. Slain in
 * the arena, the creature is bound in the ring. Each tier calls stronger creatures (their tier) and more often altered
 * ones (Rifted, Deep-Rifted, Void-Rifted). Its Rings tab is where familiar rings are worn on the left hand, and where a
 * cracked one is mended for Sorrow (menu/SourceMenu).
 */
public class RiftMawBlockEntity extends GrowingCoreBlockEntity implements net.minecraft.world.MenuProvider,
        com.lealex.alchyforbidden.essence.EssenceReceiver {
    public static final int NONE = 0, OPENING = 1, FIGHT = 2;
    /** Ticks the rift takes to open before the creature comes through. */
    public static final int OPEN_TICKS = 60;
    private final int[] held = new int[Essence.values().length]; // synced
    private int phase = NONE;                                   // synced
    private long phaseAt;                                       // synced
    private String creature = "";                               // synced
    private int corruption;                                     // synced
    private java.util.List<String> affixes = java.util.List.of(); // rolled with the grade
    private @Nullable UUID challenger;
    private @Nullable UUID foe;
    private ItemStack ring = ItemStack.EMPTY;
    private boolean slain;
    private boolean cutShort; // loaded mid-ritual: the ring is given back on the next tick
    private static final java.util.Map<net.minecraft.world.level.Level, java.util.Set<RiftMawBlockEntity>> LOADED = new java.util.WeakHashMap<>();

    /** The maws loaded in a level (either side): the client draws their arena walls from it. */
    public static java.util.List<RiftMawBlockEntity> loaded(net.minecraft.world.level.Level level) {
        java.util.Set<RiftMawBlockEntity> set = LOADED.get(level);
        return set == null ? java.util.List.of() : new java.util.ArrayList<>(set);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && LOADED.containsKey(level)) LOADED.get(level).remove(this);
    }

    public RiftMawBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.RIFT_MAW_BE.get(), pos, state);
    }

    @Override
    protected String patternBase() {
        return "rift_maw";
    }

    @Override
    public int maxTier() {
        return 4; // the fourth tier opens the void trials
    }

    @Override
    protected List<Config.Cost> costFor(int tier) {
        return Config.mawCostFor(tier);
    }

    @Override
    protected String messagePrefix() {
        return "rift_maw";
    }

    @Override
    protected String machineName() {
        return "Rift maw";
    }

    @Override
    public net.minecraft.core.particles.ParticleOptions waveParticle() {
        return ModRegistries.RIFT_MOTE.get();
    }

    @Override
    protected int riftSize() {
        return 3;
    }

    @Override
    protected boolean canGrow() {
        return phase == NONE && super.canGrow();
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
        return Math.max(0, Config.mawStore() - held[essence.ordinal()]);
    }

    @Override
    public BlockPos intakePos() {
        return worldPosition.above();
    }

    @Override
    public BlockPos receiverPos() {
        return worldPosition;
    }

    public int held(Essence essence) {
        return held[essence.ordinal()];
    }

    // ---- What the screen reads ----

    public int phase() {
        return phase;
    }

    public long phaseAt() {
        return phaseAt;
    }

    public @Nullable RiftCreature ritualCreature() {
        return RiftCreature.byId(creature);
    }

    public int ritualCorruption() {
        return corruption;
    }

    public int arenaRadius() {
        return Config.arenaRadius(standing);
    }

    public Vec3 center() {
        return Vec3.atBottomCenterOf(worldPosition.above());
    }

    // ---- Essence ----

    private List<AltarBlockEntity> altars() {
        List<AltarBlockEntity> out = new ArrayList<>();
        if (level == null) return out;
        double reach = (double) Config.mawReach() * Config.mawReach();
        for (AltarBlockEntity altar : AltarBlockEntity.loaded(level)) {
            if (altar.isFormed() && altar.hasEye() && altar.getBlockPos().distSqr(worldPosition) <= reach) out.add(altar);
        }
        out.sort((a, b) -> Double.compare(a.getBlockPos().distSqr(worldPosition), b.getBlockPos().distSqr(worldPosition)));
        return out;
    }

    /** All it could draw of an essence for this player: its store, their body, the altars round it. */
    public int available(Player player, Essence essence) {
        int total = held[essence.ordinal()] + Inscriptions.carried(player, essence);
        for (AltarBlockEntity altar : altars()) total += altar.essence(essence);
        return total;
    }

    private void draw(ServerPlayer player, Essence essence, int amount) {
        ServerLevel server = (ServerLevel) level;
        int left = amount;
        int own = Math.min(left, held[essence.ordinal()]);
        held[essence.ordinal()] -= own;
        left -= own;
        int body = Math.min(left, Inscriptions.carried(player, essence));
        if (body > 0 && Inscriptions.payEssence(player, essence, body)) {
            left -= body;
            com.lealex.alchyforbidden.essence.Relays.guide(server, player.blockPosition().above(), worldPosition.above(), essence);
        }
        for (AltarBlockEntity altar : altars()) {
            if (left <= 0) break;
            int take = Math.min(left, altar.essence(essence));
            if (take > 0 && altar.spendEssence(essence, take)) {
                left -= take;
                com.lealex.alchyforbidden.essence.Relays.guide(server, altar.getBlockPos().above(), worldPosition.above(), essence);
            }
        }
        setChanged();
        syncToClients();
    }

    // ---- The ritual ----

    // ---- The affix filter: leaving affixes out of the roll costs Void Prisms and residue, the fewer kept the dearer ----

    /** [Void Prisms, Rift Residue] a filter costs. */
    public static int[] filterCost(int mask) {
        int out = com.lealex.alchyforbidden.familiar.RiftAffix.excluded(mask);
        if (out <= 0) return new int[2];
        return new int[] {(out * out + Config.filterPrismDivisor() - 1) / Config.filterPrismDivisor(), out * Config.filterResiduePer()};
    }

    /** Why the player cannot pay for this filter (null: they can, or it costs nothing). extraResidue: residue already asked for. */
    public @Nullable Component filterRefusal(Player player, int mask, int extraResidue, ItemStack prisms) {
        if (player.getAbilities().instabuild) return null;
        int[] cost = filterCost(mask);
        int held = prisms.is(ModRegistries.VOID_PRISM.get()) ? prisms.getCount() : 0;
        if (cost[0] > 0 && held + SoulCircleBlockEntity.count(player, ModRegistries.VOID_PRISM.get()) < cost[0]) {
            return Component.translatable("rift_maw.alchyforbidden.filter_prisms", cost[0]);
        }
        if (cost[1] + extraResidue > 0 && SoulCircleBlockEntity.count(player, ModRegistries.RIFT_RESIDUE.get()) < cost[1] + extraResidue) {
            return Component.translatable("rift_maw.alchyforbidden.corrupt_residue", cost[1] + extraResidue);
        }
        return null;
    }

    private static void take(Player player, net.minecraft.world.item.Item item, int count) {
        int left = count;
        for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.is(item)) continue;
            int taken = Math.min(left, stack.getCount());
            stack.shrink(taken);
            left -= taken;
        }
    }

    /** Pays a filter: the prisms from the maw's prism slot first, then the inventory; the residue from the inventory. */
    private static void payFilter(Player player, int mask, ItemStack prisms) {
        if (player.getAbilities().instabuild) return;
        int[] cost = filterCost(mask);
        int fromSlot = prisms.is(ModRegistries.VOID_PRISM.get()) ? Math.min(cost[0], prisms.getCount()) : 0;
        prisms.shrink(fromSlot);
        take(player, ModRegistries.VOID_PRISM.get(), cost[0] - fromSlot);
        take(player, ModRegistries.RIFT_RESIDUE.get(), cost[1]);
    }

    public @Nullable Component refusal(Player player, ItemStack lure, ItemStack ringStack, int mask, ItemStack prisms) {
        Component refusal = refusal(player, lure, ringStack);
        return refusal != null ? refusal : filterRefusal(player, mask, 0, prisms);
    }

    /** Why this player cannot begin the ritual with this lure and ring now, or null. */
    public @Nullable Component refusal(Player player, ItemStack lure, ItemStack ringStack) {
        if (!isFormed()) return Component.translatable("rift_maw.alchyforbidden.unformed");
        if (phase != NONE) return Component.translatable("rift_maw.alchyforbidden.busy");
        if (!(lure.getItem() instanceof RiftLureItem lureItem)) return Component.translatable("rift_maw.alchyforbidden.no_lure");
        RiftCreature called = lureItem.creature();
        if (called.tier() > standing) return Component.translatable("rift_maw.alchyforbidden.tier", called.tier());
        if (!(ringStack.getItem() instanceof FamiliarRingItem ringItem)) return Component.translatable("rift_maw.alchyforbidden.no_ring");
        if (FamiliarRingItem.data(ringStack) != null) return Component.translatable("rift_maw.alchyforbidden.ring_full");
        if (ringItem.tier() < called.tier()) return Component.translatable("rift_maw.alchyforbidden.ring_tier", called.tier());
        if (horizontal(player.position()) > arenaRadius() - 1) return Component.translatable("rift_maw.alchyforbidden.step_in");
        int[] costs = called.summonCosts();
        for (Essence essence : Essence.values()) {
            int need = costs[essence.ordinal()];
            if (need > 0 && !player.getAbilities().instabuild && available(player, essence) < need) {
                return Component.translatable("rift_maw.alchyforbidden.thirsty", need, essence.icon(), Config.mawReach());
            }
        }
        return null;
    }

    /** The screen's Begin: pays, takes the lure and the ring, opens the rift and the arena. */
    public void begin(ServerPlayer player, ItemStack lure, ItemStack ringStack, ItemStack catalyst, int mask, ItemStack prisms) {
        Component refusal = refusal(player, lure, ringStack, mask, prisms);
        if (refusal != null) {
            player.sendSystemMessage(refusal.copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        RiftCreature called = ((RiftLureItem) lure.getItem()).creature();
        payFilter(player, mask, prisms);
        if (!player.getAbilities().instabuild) {
            int[] costs = called.summonCosts();
            for (Essence essence : Essence.values()) if (costs[essence.ordinal()] > 0) draw(player, essence, costs[essence.ordinal()]);
            lure.shrink(1);
        }
        ring = ringStack.split(1);
        creature = called.id();
        int residue = catalyst.is(ModRegistries.RIFT_RESIDUE.get()) ? Math.min(catalyst.getCount(), Config.residueMax()) : 0;
        corruption = roll(level.getRandom(), odds(residue));
        if (residue > 0) catalyst.shrink(residue); // the rift drinks its catalyst, whatever comes
        affixes = com.lealex.alchyforbidden.familiar.RiftAffix.roll(level.getRandom(), corruption, mask);
        challenger = player.getUUID();
        foe = null;
        slain = false;
        phase = OPENING;
        phaseAt = level.getGameTime();
        setChanged();
        syncToClients();
        player.closeContainer();
        ServerLevel server = (ServerLevel) level;
        com.lealex.alchyforbidden.network.SpellFxPayload.send(server, com.lealex.alchyforbidden.network.SpellFxPayload.RIFT,
                center().add(0, 3, 0), -1, OPEN_TICKS + 20, 3.6F);
        playSound(worldPosition, SoundEvents.WARDEN_EMERGE, 1.2f, 0.7f);
        playSound(worldPosition, SoundEvents.END_PORTAL_SPAWN, 0.6f, 0.6f);
        player.sendSystemMessage(Component.translatable("rift_maw.alchyforbidden.opened", called.displayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    // ---- The odds of an altered creature: the tier, the catalyst, the hour ----

    /** The rift's mood here and now: [night, new moon, deep dark] (either side). */
    public boolean[] conditions() {
        if (level == null) return new boolean[3];
        boolean night = level.isDarkOutside();
        boolean newMoon = night && Math.floorMod(level.getOverworldClockTime() / 24000L, 8L) == 4;
        boolean deep = level.getBiome(worldPosition).is(net.minecraft.world.level.biome.Biomes.DEEP_DARK);
        return new boolean[] {night, newMoon, deep};
    }

    /** What the hour and the place add, in percent (a new moon's instead of a plain night's). */
    public int conditionBonus() {
        boolean[] c = conditions();
        return (c[1] ? Config.newMoonOdds() : c[0] ? Config.nightOdds() : 0) + (c[2] ? Config.deepDarkOdds() : 0);
    }

    /** The odds [plain, Rifted, Deep-Rifted, Void-Rifted] with this much residue as the catalyst. */
    public int[] odds(int residue) {
        int[] base = Config.alteredChances(standing).clone();
        int total = base[0] + base[1] + base[2] + base[3];
        if (total <= 0) return base;
        int bonus = Math.min(Math.min(residue, Config.residueMax()) * Config.residueOdds() + conditionBonus(), 100);
        // the bonus is in percent of the whole: taken from the plain chance, given to the grades this tier can call
        int moved = Math.min(base[0], Math.round(total * bonus / 100F));
        if (moved <= 0) return base;
        boolean voidOpen = standing >= 2;
        int toVoid = voidOpen ? moved * 20 / 100 : 0, toDeep = moved * (voidOpen ? 30 : 40) / 100, toRifted = moved - toVoid - toDeep;
        return new int[] {base[0] - moved, base[1] + toRifted, base[2] + toDeep, base[3] + toVoid};
    }

    private static int roll(RandomSource random, int[] chances) {
        int total = 0;
        for (int c : chances) total += c;
        if (total <= 0) return 0;
        int r = random.nextInt(total);
        for (int i = 0; i < chances.length; i++) {
            r -= chances[i];
            if (r < 0) return i;
        }
        return 0;
    }

    private double horizontal(Vec3 at) {
        double dx = at.x - (worldPosition.getX() + 0.5), dz = at.z - (worldPosition.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    protected void tickWorking() {
        if (cutShort) {
            cutShort = false;
            giveBack(null);
        }
        if (phase == NONE || !(level instanceof ServerLevel server)) return;
        long elapsed = level.getGameTime() - phaseAt;
        ServerPlayer player = challenger == null ? null : server.getServer().getPlayerList().getPlayer(challenger);
        if (player == null) {
            lose(null, "rift_maw.alchyforbidden.lost_gone");
            return;
        }
        if (player.level() != level || !player.isAlive()) {
            lose(player, player.isAlive() ? "rift_maw.alchyforbidden.lost_fled" : "rift_maw.alchyforbidden.lost_dead");
            return;
        }
        int radius = arenaRadius();
        double d = horizontal(player.position()), dy = player.getY() - worldPosition.getY();
        if (d > radius + 1.5 || dy > 14 || dy < -6) {
            lose(player, "rift_maw.alchyforbidden.lost_fled");
            return;
        }
        if (d > radius - 0.7) wallThrows(server, player, d);
        if (phase == OPENING) {
            if (elapsed >= OPEN_TICKS) callForth(server, player);
            return;
        }
        if (elapsed > Config.ritualSeconds() * 20L) {
            lose(player, "rift_maw.alchyforbidden.lost_time");
            return;
        }
        Entity entity = foe == null ? null : server.getEntity(foe);
        if (slain) {
            win(server, player);
            return;
        }
        if (!(entity instanceof RiftBeast beast) || entity.isRemoved()) {
            lose(player, "rift_maw.alchyforbidden.lost_vanished");
            return;
        }
        if (horizontal(beast.position()) > radius - 0.5) { // the wall holds it in as well
            Vec3 c = center(), in = c.subtract(beast.position()).multiply(1, 0, 1).normalize();
            beast.teleportTo(beast.getX() + in.x * 1.5, beast.getY(), beast.getZ() + in.z * 1.5);
        }
        if (beast.getTarget() == null) beast.setTarget(player);
    }

    /** The wall: thrown back toward the middle, hurt a little. */
    private void wallThrows(ServerLevel server, ServerPlayer player, double d) {
        Vec3 in = center().subtract(player.position()).multiply(1, 0, 1).normalize();
        player.setDeltaMovement(in.x * 0.9, 0.35, in.z * 0.9);
        player.hurtMarked = true;
        if (level.getGameTime() % 10 == 0) player.hurtServer(server, player.damageSources().magic(), 2.0F);
        server.sendParticles(ModRegistries.RIFT_MOTE.get(), player.getX() - in.x * 0.6, player.getY() + 1, player.getZ() - in.z * 0.6, 12, 0.1, 0.6, 0.1, 0.05);
        if (level.getGameTime() % 6 == 0) playSound(player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.5f);
    }

    /** The creature comes through the rift. */
    private void callForth(ServerLevel server, ServerPlayer player) {
        RiftCreature called = ritualCreature();
        if (called != null && called.tier() >= 4 && com.lealex.alchyforbidden.familiar.VoidTrials.start(player, called, corruption, affixes, ring, center().add(0, 5, 0), this::structureContains)) {
            ring = ItemStack.EMPTY; // the trial holds it now
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 80, 0.5, 1, 0.5, 0.3);
            playSound(worldPosition, SoundEvents.PORTAL_TRAVEL, 0.4f, 0.6f);
            end();
            return;
        }
        RiftBeast beast = called == null ? null : called.type().create(server, EntitySpawnReason.MOB_SUMMONED);
        if (beast == null) {
            lose(player, "rift_maw.alchyforbidden.lost_vanished");
            return;
        }
        Vec3 at = center().add(0, 0.2, 0);
        beast.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360, 0);
        beast.becomeArenaFoe(worldPosition, player.getUUID(), corruption, affixes);
        if (!server.addFreshEntity(beast)) {
            lose(player, "rift_maw.alchyforbidden.lost_vanished");
            return;
        }
        beast.setTarget(player);
        if (called.boss()) beast.introduce(player); // a great one makes its entrance
        foe = beast.getUUID();
        phase = FIGHT;
        phaseAt = level.getGameTime() + (called.boss() ? RiftBeast.INTRO_TICKS : 0); // the ritual's clock starts after the entrance
        setChanged();
        syncToClients();
        server.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
        server.sendParticles(ModRegistries.RIFT_MOTE.get(), at.x, at.y + 1, at.z, 60, 0.6, 0.8, 0.6, 0.12);
        server.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 1, at.z, 20, 0.5, 0.6, 0.5, 0.05);
        playSound(worldPosition, SoundEvents.WARDEN_ROAR, 1.0f, 1.3f);
        player.sendSystemMessage(Component.translatable("rift_maw.alchyforbidden.came", called.fullName(corruption)), true);
    }

    /** Called by the creature as it dies in the arena. */
    public void foeSlain(RiftBeast beast) {
        if (phase == FIGHT && beast.getUUID().equals(foe)) {
            slain = true;
            setChanged();
        }
    }

    private void win(ServerLevel server, ServerPlayer player) {
        RiftCreature called = ritualCreature();
        ItemStack bound = ring.copy();
        if (called != null) FamiliarRingItem.setData(bound, FamiliarData.fresh(called, corruption, affixes));
        ring = ItemStack.EMPTY;
        if (corruption > 0) { // what an altered one leaves behind
            ItemStack residue = new ItemStack(ModRegistries.RIFT_RESIDUE.get(), corruption * Config.residuePerGrade());
            if (!player.getInventory().add(residue)) player.drop(residue, false);
        }
        end();
        Vec3 c = center();
        server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y + 1, c.z, 40, 0.6, 0.8, 0.6, 0.3);
        server.sendParticles(ModRegistries.RIFT_MOTE.get(), player.getX(), player.getY() + 1, player.getZ(), 40, 0.4, 0.8, 0.4, 0.08);
        playSound(worldPosition, SoundEvents.TOTEM_USE, 0.8f, 0.6f);
        playSound(worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.7f);
        if (!player.getInventory().add(bound)) player.drop(bound, false);
        if (called != null) {
            player.sendSystemMessage(Component.translatable("rift_maw.alchyforbidden.bound", called.fullName(corruption)).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** The ritual is lost: the creature goes back through the rift, the ring is given back blank. */
    private void lose(@Nullable ServerPlayer player, String why) {
        if (level instanceof ServerLevel server && foe != null && server.getEntity(foe) instanceof RiftBeast beast && beast.isAlive()) {
            Familiars.riftPuff(server, beast.middle(), beast.creature().color(), false);
            beast.discard();
        }
        giveBack(player);
        end();
        playSound(worldPosition, SoundEvents.SOUL_ESCAPE.value(), 1.2f, 0.5f);
        if (player != null) player.sendSystemMessage(Component.translatable(why).withStyle(ChatFormatting.RED));
    }

    private void giveBack(@Nullable ServerPlayer player) {
        if (ring.isEmpty() || level == null) return;
        ItemStack back = ring;
        ring = ItemStack.EMPTY;
        if (player != null && player.isAlive() && player.getInventory().add(back)) return;
        net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5, back);
    }

    private void end() {
        phase = NONE;
        phaseAt = level == null ? 0 : level.getGameTime();
        challenger = null;
        foe = null;
        slain = false;
        setChanged();
        syncToClients();
    }

    @Override
    protected void onStructureBroken() {
        if (phase != NONE && level instanceof ServerLevel server) {
            lose(challenger == null ? null : server.getServer().getPlayerList().getPlayer(challenger), "rift_maw.alchyforbidden.lost_broken");
        }
    }

    /** What corrupting the familiar in that slot further costs: [Corruption, residue] (null when it cannot be). */
    public static int @Nullable [] corruptCost(FamiliarData data) {
        if (data.corruption() >= 3) return null;
        return new int[] {Config.corruptCorruption() * (data.corruption() + 1), Config.corruptResidue() * (data.corruption() + 1)};
    }

    /** Why the familiar in that slot cannot be corrupted here now (null: it can). */
    public @Nullable Component corruptRefusal(Player player, int slot) {
        return corruptRefusal(player, slot, com.lealex.alchyforbidden.familiar.RiftAffix.ALL, ItemStack.EMPTY);
    }

    public @Nullable Component corruptRefusal(Player player, int slot, int mask, ItemStack prisms) {
        var rings = Familiars.worn(player);
        if (!isFormed()) return Component.translatable("rift_maw.alchyforbidden.unformed");
        if (slot < 0 || slot >= rings.size() || rings.get(slot) == null) return Component.translatable("rift_maw.alchyforbidden.no_ring");
        FamiliarData data = rings.get(slot).data();
        int[] cost = corruptCost(data);
        if (cost == null) return Component.translatable("rift_maw.alchyforbidden.corrupt_max");
        if (data.cracked(player.level().getGameTime())) return Component.translatable("rift_maw.alchyforbidden.corrupt_cracked");
        if (player.getAbilities().instabuild) return null;
        if (available(player, Essence.CORRUPTION) < cost[0]) {
            return Component.translatable("rift_maw.alchyforbidden.thirsty", cost[0], Essence.CORRUPTION.icon(), Config.mawReach());
        }
        if (com.lealex.alchyforbidden.familiar.RiftAffix.rollNew(level.getRandom(), data.affixes(), mask) == null) {
            return Component.translatable("rift_maw.alchyforbidden.filter_none");
        }
        if (SoulCircleBlockEntity.count(player, ModRegistries.RIFT_RESIDUE.get()) < cost[1]) {
            return Component.translatable("rift_maw.alchyforbidden.corrupt_residue", cost[1]);
        }
        return filterRefusal(player, mask, cost[1], prisms);
    }

    /**
     * The corrupting ritual: the familiar in that left-hand slot is pushed one grade deeper into the rift and gains an
     * affix; or (Config corruptFailChance) the ring rejects it and cracks for a long while. Paid either way.
     */
    public void corrupt(ServerPlayer player, int slot, int mask, ItemStack prisms) {
        Component refusal = corruptRefusal(player, slot, mask, prisms);
        if (refusal != null) {
            player.sendSystemMessage(refusal.copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        FamiliarData data = Familiars.worn(player).get(slot).data();
        int[] cost = corruptCost(data);
        if (!player.getAbilities().instabuild) {
            draw(player, Essence.CORRUPTION, cost[0]);
            take(player, ModRegistries.RIFT_RESIDUE.get(), cost[1]);
        }
        payFilter(player, mask, prisms);
        Familiars.recallAll(player);
        ServerLevel server = (ServerLevel) level;
        Vec3 c = center();
        if (level.getRandom().nextInt(100) < Config.corruptFailChance()) {
            Familiars.setRing(player, slot, data.crackedTill(level.getGameTime() + Config.corruptCrackSeconds() * 20L));
            server.sendParticles(ParticleTypes.SMOKE, c.x, c.y + 1, c.z, 40, 0.5, 0.6, 0.5, 0.05);
            playSound(worldPosition, SoundEvents.GLASS_BREAK, 1.0f, 0.5f);
            player.sendSystemMessage(Component.translatable("rift_maw.alchyforbidden.corrupt_failed", data.title(), Config.corruptCrackSeconds() / 60)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        var affix = com.lealex.alchyforbidden.familiar.RiftAffix.rollNew(level.getRandom(), data.affixes(), mask);
        FamiliarData next = data.corrupted(affix == null ? null : affix.id());
        Familiars.setRing(player, slot, next);
        server.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y + 1, c.z, 80, 0.8, 1.0, 0.8, 0.2);
        server.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 1.5, c.z, 1, 0, 0, 0, 0);
        com.lealex.alchyforbidden.network.SpellFxPayload.send(server, com.lealex.alchyforbidden.network.SpellFxPayload.RIFT, c.add(0, 2, 0), -1, 40, 2.0F);
        playSound(worldPosition, SoundEvents.WARDEN_EMERGE, 1.0f, 1.2f);
        playSound(worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 0.5f);
        player.sendSystemMessage(Component.translatable("rift_maw.alchyforbidden.corrupted", next.title(),
                affix == null ? Component.empty() : affix.displayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** What releasing this familiar gives back in Rift Residue: its tier, its grade, its levels. */
    public static int releaseResidue(FamiliarData data) {
        RiftCreature kind = data.creatureOrNull();
        int tier = kind == null ? 1 : kind.tier();
        return tier * Config.releasePerTier() + data.corruption() * Config.releasePerGrade() + data.level() / Config.releaseLevelsPerResidue();
    }

    /** Lets the familiar in that left-hand slot go back into the rift: the blank ring and its residue to the player. */
    public void release(ServerPlayer player, int slot) {
        var rings = Familiars.worn(player);
        if (!isFormed() || slot < 0 || slot >= rings.size() || rings.get(slot) == null) return;
        var ring = rings.get(slot);
        Familiars.recallAll(player);
        rings = Familiars.worn(player);
        rings.set(slot, null);
        Familiars.setWorn(player, rings);
        ItemStack blank = new ItemStack(ModRegistries.familiarRingOfTier(ring.tier()));
        ItemStack residue = new ItemStack(ModRegistries.RIFT_RESIDUE.get(), releaseResidue(ring.data()));
        for (ItemStack give : new ItemStack[] {blank, residue}) if (!give.isEmpty() && !player.getInventory().add(give)) player.drop(give, false);
        ServerLevel server = (ServerLevel) level;
        Vec3 c = center();
        server.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y + 1, c.z, 30, 0.6, 0.8, 0.6, 0.05);
        server.sendParticles(ModRegistries.RIFT_MOTE.get(), c.x, c.y + 1.5, c.z, 40, 0.5, 1.0, 0.5, 0.1);
        com.lealex.alchyforbidden.network.SpellFxPayload.send(server, com.lealex.alchyforbidden.network.SpellFxPayload.RIFT, c.add(0, 2, 0), -1, 30, 1.6F);
        playSound(worldPosition, SoundEvents.SOUL_ESCAPE.value(), 1.2f, 0.7f);
        player.sendSystemMessage(Component.translatable("rift_maw.alchyforbidden.released", ring.data().title(), residue.getCount())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Sorrow paid here mends the cracked ring in that left-hand slot. */
    public void mend(ServerPlayer player, int slot) {
        var rings = Familiars.worn(player);
        if (slot < 0 || slot >= rings.size() || rings.get(slot) == null || !isFormed()) return;
        var ringWorn = rings.get(slot);
        if (!ringWorn.data().cracked(level.getGameTime())) return;
        int cost = Config.familiarMendSorrow() * ringWorn.tier();
        if (!player.getAbilities().instabuild) {
            if (available(player, Essence.SORROW) < cost) {
                player.sendSystemMessage(Component.translatable("rift_maw.alchyforbidden.thirsty", cost, Essence.SORROW.icon(), Config.mawReach())
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            draw(player, Essence.SORROW, cost);
        }
        Familiars.mend(player, slot);
        playSound(worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
        player.sendSystemMessage(Component.translatable("rift_maw.alchyforbidden.mended").withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    // ---- Client: the arena's wall, the rift ----

    @Override
    protected void clientWorking(RandomSource random, long elapsed, double cx, double cy, double cz) {
        if (level == null || !isFormed()) return;
        if (phase == NONE) {
            if (random.nextInt(6) == 0 && com.lealex.alchyx.AlchyXClientConfig.spawn(random)) { // the pit breathing
                level.addParticle(ModRegistries.RIFT_MOTE.get(), cx + (random.nextDouble() - 0.5) * 2, worldPosition.getY() + 1.05, cz + (random.nextDouble() - 0.5) * 2, 0, 0.03, 0);
            }
            return;
        }
        int radius = arenaRadius();
        int count = com.lealex.alchyx.AlchyXClientConfig.count(radius * 2, random);
        for (int i = 0; i < count; i++) { // the wall: a curtain of motes round the arena, rising
            double a = random.nextDouble() * Math.PI * 2, h = random.nextDouble() * 4;
            double x = cx + Math.cos(a) * radius, z = cz + Math.sin(a) * radius;
            level.addParticle(ModRegistries.RIFT_MOTE.get(), x, worldPosition.getY() + 1 + h, z, 0, 0.04, 0);
            if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.REVERSE_PORTAL, x, worldPosition.getY() + 1.05, z, 0, 0.05, 0);
        }
        long t = level.getGameTime() - phaseAt;
        if (phase == OPENING) { // the rift drawing things up into it
            for (int i = 0; i < 4; i++) { // the rift's mouth whirling
                double a = t * 0.3 + i * Math.PI / 2, r = 1.6 + Math.sin(t * 0.1) * 0.3;
                level.addParticle(ParticleTypes.PORTAL, cx + Math.cos(a) * r, worldPosition.getY() + 4 + Math.sin(t * 0.2 + i) * 0.3, cz + Math.sin(a) * r,
                        -Math.cos(a) * 0.5, 0, -Math.sin(a) * 0.5);
                if (random.nextInt(2) == 0) level.addParticle(ModRegistries.RIFT_MOTE.get(), cx + Math.cos(a + 0.5) * (r + 1), worldPosition.getY() + 3 + random.nextDouble() * 2,
                        cz + Math.sin(a + 0.5) * (r + 1), -Math.cos(a) * 0.08, 0.02, -Math.sin(a) * 0.08);
            }
            for (int i = 0; i < 8; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = 1 + random.nextDouble() * radius * 0.6;
                level.addParticle(ParticleTypes.PORTAL, cx + Math.cos(a) * r, worldPosition.getY() + 1.1, cz + Math.sin(a) * r,
                        -Math.cos(a) * 0.6, 1.2 + t / 30.0, -Math.sin(a) * 0.6);
            }
            if (t % 20 == 0) level.playLocalSound(cx, cy + 3, cz, SoundEvents.WARDEN_HEARTBEAT, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.6F + t / 100F, false);
        }
    }

    @Override
    public void lookMeters(java.util.List<LookMeter> out) {
        for (Essence e : Essence.values()) out.add(new LookMeter(held(e), Config.mawStore(), 0xFF000000 | e.color(), e.icon()));
    }

    // ---- Screen, saving ----

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (isMoving()) return;
        if (phase != NONE && level instanceof ServerLevel server) {
            lose(challenger == null ? null : server.getServer().getPlayerList().getPlayer(challenger), "rift_maw.alchyforbidden.lost_broken");
        }
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) com.lealex.alchyforbidden.essence.Relays.unlinkTo(server, pos, false);
    }

    @Override
    public void openGui(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        tryGrowing();
        serverPlayer.openMenu(this, out -> out.writeBlockPos(worldPosition));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.alchyforbidden.rift_maw");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, Player player) {
        return new com.lealex.alchyforbidden.menu.SourceMenu(id, inventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putIntArray("held", held.clone());
        output.putInt("phase", phase);
        output.putLong("phaseAt", phaseAt);
        output.putString("creature", creature);
        output.putInt("corruption", corruption);
        output.putString("affixes", String.join(",", affixes));
        output.store("ring", ItemStack.OPTIONAL_CODEC, ring);
        if (challenger != null) output.store("challenger", UUIDUtil.CODEC, challenger);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int[] kept = input.getIntArray("held").orElse(new int[0]);
        java.util.Arrays.fill(held, 0);
        for (int i = 0; i < held.length && i < kept.length; i++) held[i] = kept[i];
        phase = input.getIntOr("phase", NONE);
        phaseAt = input.getLongOr("phaseAt", 0L);
        creature = input.getStringOr("creature", "");
        corruption = input.getIntOr("corruption", 0);
        String a = input.getStringOr("affixes", "");
        affixes = a.isEmpty() ? java.util.List.of() : java.util.List.of(a.split(","));
        ring = input.read("ring", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        challenger = input.read("challenger", UUIDUtil.CODEC).orElse(null);
    }

    /** Loaded mid-ritual (a world reopened, a chunk loaded again): the creature was never saved, so it is lost. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) LOADED.computeIfAbsent(level, key -> java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>())).add(this);
        if (level != null && !level.isClientSide() && phase != NONE) {
            cutShort = true;
            phase = NONE;
            challenger = null;
            setChanged();
        }
    }
}
