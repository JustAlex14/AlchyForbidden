package com.lealex.alchyforbidden.block.entity;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.network.AltarRiftPayload;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.lealex.alchyx.block.entity.TieredCoreBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Every AlchyForbidden multiblock grows the same way (author's decision): the player builds tier 1 by hand, it forms
 * into AlchyX shells, and the rift itself grows every later tier once the core has been given what the tier asks for.
 * The growth itself is AlchyX's (TieredCoreBlockEntity: tiers, gifts, phases, clears, flights, rising columns, the core
 * moving); this class is the rift's look on it: the great rift over the core while it grows (client/AltarRifts, opened
 * as it gathers, raised with the core, closed when the tier stands), the parts flying out of it with rift motes and
 * sculk flecks (style alchyforbidden:rift), the mod's sounds and particles, the noise of a growth (AltarNoise), sculk
 * creeping in place, pillars rising out of the ground, the costs from the config, and the essences: sending to altars
 * and the obelisk link a source keeps.
 */
public abstract class GrowingCoreBlockEntity extends TieredCoreBlockEntity {
    /** Blocks the rift may eat where it grows (natural ground, sculk's own spread, the mod's own looks). */
    public static final TagKey<Block> RIFT_EATS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "rift_eats"));
    /** The real blocks the multiblocks grow (sculk veins, soul lanterns), which a later tier may clear again. */
    public static final TagKey<Block> REAL_PARTS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "altar_real_parts"));
    /** The game's own blocks the multiblocks wear as looks (the resonator's shriekers): a later tier may clear them. */
    public static final TagKey<Block> OWN_LOOKS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "own_looks"));
    /** The flights' particles (client: AlchyForbiddenClient registers the style with AlchyX GrowthParts). */
    public static final Identifier RIFT_STYLE = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "rift");

    /** A source's link to an Echo Obelisk or an altar (the Attuning Fork): it sends there instead of to the nearest altar. */
    private @Nullable BlockPos relayLink;

    protected GrowingCoreBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---- What each multiblock says ----

    /** The patterns' common name: tier N is {@code alchyforbidden:<base>_N}. */
    protected abstract String patternBase();

    /** What growing to this tier costs (from the config). */
    protected abstract List<Config.Cost> costFor(int tier);

    @Override
    protected Identifier patternOf(int tier) {
        return Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, patternBase() + "_" + tier);
    }

    @Override
    protected List<TierCost> tierCosts(int tier) {
        List<TierCost> out = new ArrayList<>();
        for (Config.Cost cost : costFor(tier)) out.add(new TierCost(cost.item(), cost.count()));
        return out;
    }

    /** What the next tier asks for (for screens). */
    public List<Config.Cost> nextCosts() {
        return tier < maxTier() ? costFor(tier + 1) : List.of();
    }

    /** The look of a grown pillar: a pillar's foot, shaft or crown, or the rift lantern it carries. */
    protected static boolean pillarLook(BlockState look) {
        return look.is(ModRegistries.PILLAR_BASE.get()) || look.is(ModRegistries.WARDEN_PILLAR.get())
                || look.is(ModRegistries.PILLAR_CAPITAL.get()) || look.is(ModRegistries.RIFT_LANTERN.get());
    }

    @Override
    protected boolean pillar(BlockState look) {
        return pillarLook(look);
    }

    /** The first word of its message keys: "<prefix>.alchyforbidden.<key>". */
    protected String messagePrefix() {
        return "altar";
    }

    @Override
    protected Component growthText(String key, Object... args) {
        // (the 'blocked' line is the altar's for every multiblock)
        return Component.translatable((key.equals("blocked") ? "altar" : messagePrefix()) + ".alchyforbidden." + key, args);
    }

    @Override
    protected int gatherTicks() {
        return Config.gatherTicks();
    }

    @Override
    protected int ticksPerStep() {
        return Config.ticksPerStep();
    }

    @Override
    protected int blocksPerStep() {
        return Config.blocksPerStep();
    }

    @Override
    protected boolean eats(BlockState state) {
        return state.is(RIFT_EATS);
    }

    /** Shells wearing one of this mod's looks (or a vanilla look the mod grows), never anything else. */
    @Override
    protected boolean ownLook(BlockState look) {
        return BuiltInRegistries.BLOCK.getKey(look.getBlock()).getNamespace().equals(AlchyForbidden.MODID) || look.is(OWN_LOOKS);
    }

    @Override
    protected boolean ownRealPart(BlockState state) {
        return state.is(REAL_PARTS);
    }

    /** Sculk is not brought: it creeps over the stone where it stands. */
    @Override
    protected boolean growsInPlace(BlockState look) {
        return look.is(net.minecraft.world.level.block.Blocks.SCULK_VEIN);
    }

    @Override
    protected Identifier growthStyle() {
        return RIFT_STYLE;
    }

    /** The altar's core block hides while it rises (AltarBlock.RISING: an empty model); the others do not rise. */
    @Override
    protected @Nullable BlockState coreHidden(BlockState state, boolean hidden) {
        return state.hasProperty(com.lealex.alchyforbidden.block.AltarBlock.RISING)
                ? state.setValue(com.lealex.alchyforbidden.block.AltarBlock.RISING, hidden) : null;
    }

    @Override
    protected @Nullable BlockState pedestalFallback() {
        return ModRegistries.ALTAR_PEDESTAL.get().defaultBlockState();
    }

    // ---- The rift ----

    /** Where the great rift opens while it grows: two blocks over the core. */
    protected BlockPos riftPos() {
        return worldPosition.above(2);
    }

    protected int riftSize() {
        return goal >= 3 ? 6 : 4;
    }

    /** Where the growth's rift is now: where it opened (riftPos, its key for closing), raised with the core. */
    protected BlockPos riftNow() {
        return riftPos().above(coreLift());
    }

    @Override
    protected BlockPos growthSource() {
        return riftNow();
    }

    /** Where the end-of-growth burst flashes. */
    protected Vec3 burstPosition() {
        return Vec3.atCenterOf(worldPosition.above());
    }

    @Override
    protected void onOffered(Player player, Item item) {
        if (level == null) return;
        playSound(worldPosition, ModRegistries.ALTAR_OFFER.get(), 0.8f, 0.9f + level.getRandom().nextFloat() * 0.2f);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ModRegistries.RIFT_MOTE.get(), worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                    worldPosition.getZ() + 0.5, 12, 0.25, 0.15, 0.25, 0.02);
        }
    }

    @Override
    protected void onGatherStart() {
        if (level instanceof ServerLevel server) AltarRiftPayload.open(server, riftPos(), riftSize()); // the great rift
        playSound(worldPosition, ModRegistries.ALTAR_GATHER.get(), 1.2f, 0.8f);
    }

    @Override
    protected void gatherTick(long elapsed) {
        if (elapsed % 20 == 10) playSound(worldPosition, ModRegistries.ALTAR_GATHER.get(), 0.9f, 0.7f + elapsed / 200f);
    }

    @Override
    protected void onGrowStart() {
        if (level instanceof ServerLevel server) AltarNoise.sound(server, worldPosition, this::say);
    }

    @Override
    protected void onPartCleared(BlockPos pos, BlockState gone) {
        if (level instanceof ServerLevel server) {
            server.sendParticles(ModRegistries.RIFT_MOTE.get(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
        }
        playSound(pos, ModRegistries.ALTAR_GROW.get(), 0.7f, 0.5f);
    }

    @Override
    protected void onPartEmerging(BlockPos pos, BlockState look) {
        if (level == null) return;
        if (pos.getX() == worldPosition.getX() && pos.getZ() == worldPosition.getZ()) playSound(pos, ModRegistries.ALTAR_AWAKEN.get(), 0.7f, 0.6f); // the middle rises
        playSound(pos, ModRegistries.ALTAR_GROW.get(), 0.8f, 0.8f + level.getRandom().nextFloat() * 0.4f);
    }

    @Override
    protected void onPartGrewInPlace(BlockPos pos, BlockState look) {
        if (level == null) return;
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SCULK_CHARGE_POP, pos.getX() + 0.5, pos.getY() + 0.15, pos.getZ() + 0.5, 8, 0.35, 0.05, 0.35, 0.01);
            server.sendParticles(ModRegistries.SCULK_FLECK.get(), pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.01);
        }
        playSound(pos, SoundEvents.SCULK_BLOCK_SPREAD, 0.8f, 0.8f + level.getRandom().nextFloat() * 0.3f);
    }

    @Override
    protected void onColumnRising(BlockPos start) {
        playSound(start, SoundEvents.PISTON_EXTEND, 0.7f, 0.45f);
        playSound(start, ModRegistries.ALTAR_AWAKEN.get(), 0.5f, 0.5f);
    }

    /** The rift over the core goes up with it, and what is still to come comes out of it up there. */
    @Override
    protected void onCoreRising(int up) {
        if (level instanceof ServerLevel server) {
            AltarRiftPayload.part(server, AltarRiftPayload.MOVE, riftPos(), riftPos().above(up), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    com.lealex.alchyx.growth.GrowthPayload.riseTicks(up));
        }
    }

    @Override
    protected void onGrowthEnding() {
        if (level instanceof ServerLevel server) AltarRiftPayload.close(server, riftPos()); // (where it opened: before the core moves)
    }

    @Override
    protected void onGrowthAborted() {
        if (level instanceof ServerLevel server) AltarRiftPayload.close(server, riftPos());
    }

    /** The tier is grown: a burst of the rift. */
    @Override
    protected void onAwakened() {
        if (!(level instanceof ServerLevel server)) return;
        playSound(worldPosition, ModRegistries.ALTAR_AWAKEN.get(), 1.4f, 1.0f);
        server.sendParticles(ModRegistries.RIFT_MOTE.get(), worldPosition.getX() + 0.5, worldPosition.getY() + 1.2,
                worldPosition.getZ() + 0.5, 60, 1.6, 0.8, 1.6, 0.08);
        Vec3 burst = burstPosition();
        server.sendParticles(ParticleTypes.SONIC_BOOM, burst.x, burst.y, burst.z, 1, 0, 0, 0, 0);
    }

    /** Client: the gathering drawn around the core (motes drawn in from a ring), flecks while it grows. */
    @Override
    protected void clientGrowing(int phase, RandomSource random, long elapsed, double cx, double cy, double cz) {
        if (level == null) return;
        if (phase == GATHER) {
            float progress = Mth.clamp((float) elapsed / Math.max(1, Config.gatherTicks()), 0f, 1f);
            int count = 1 + (int) (progress * 4);
            for (int i = 0; i < count; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double radius = 2.5 + random.nextDouble() * 1.5;
                double x = cx + Math.cos(angle) * radius, z = cz + Math.sin(angle) * radius;
                double y = cy - 0.6 + random.nextDouble() * 2.2;
                double vx = (cx - x) * 0.09 - Math.sin(angle) * 0.06, vz = (cz - z) * 0.09 + Math.cos(angle) * 0.06;
                level.addParticle(ModRegistries.RIFT_MOTE.get(), x, y, z, vx, (cy + 0.3 - y) * 0.05, vz);
            }
            if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.SCULK_SOUL, cx, cy + 0.2, cz, 0, 0.03, 0);
        } else {
            for (int i = 0; i < 2; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                level.addParticle(ModRegistries.SCULK_FLECK.get(), cx + Math.cos(angle) * 0.6, cy, cz + Math.sin(angle) * 0.6,
                        Math.cos(angle) * 0.05, 0.08, Math.sin(angle) * 0.05);
            }
        }
    }

    // ---- Essences ----

    /**
     * Sends some of an essence to the nearest awake altar with room within reach (a stream from {@code from} into its
     * eye, in the essence's colour). Returns what no altar took (the caller keeps it).
     */
    protected int sendEssence(com.lealex.alchyforbidden.essence.Essence essence, int amount, BlockPos from, int reach) {
        if (amount <= 0 || !(level instanceof ServerLevel server)) return amount;
        if (relayLink != null) { // through its relays (a broken chain keeps it here)
            return com.lealex.alchyforbidden.essence.Relays.send(server, worldPosition, relayLink, essence, amount, from);
        }
        AltarBlockEntity altar = nearestAltar(essence, reach);
        if (altar == null) return amount;
        int taken = altar.addEssence(essence, amount);
        if (taken > 0) {
            com.lealex.alchyforbidden.essence.Relays.guide(server, from, altar.getBlockPos().above(), essence); // into its eye
        }
        return amount - taken;
    }

    /** Where the altar it would feed with that essence stands (null for none): its screen. */
    public @Nullable BlockPos feedsAltar(com.lealex.alchyforbidden.essence.Essence essence, int reach) {
        if (relayLink != null && level != null) {
            // where that essence would really go now: an end with room for it (a full altar is passed over)
            var route = com.lealex.alchyforbidden.essence.Relays.route(level, worldPosition, relayLink, essence, false);
            return route == null ? null : route.receiver().receiverPos();
        }
        AltarBlockEntity altar = nearestAltar(essence, reach);
        return altar == null ? null : altar.getBlockPos();
    }

    /** The nearest awake altar within reach with room for that essence (null for none). */
    protected @Nullable AltarBlockEntity nearestAltar(com.lealex.alchyforbidden.essence.Essence essence, int reach) {
        if (level == null) return null;
        AltarBlockEntity best = null;
        double bestDistance = (double) reach * reach;
        for (AltarBlockEntity altar : AltarBlockEntity.loaded(level)) {
            if (!altar.isFormed() || !altar.hasEye() || altar.essence(essence) >= altar.reserveCap()) continue;
            double distance = altar.getBlockPos().distSqr(worldPosition);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = altar;
            }
        }
        return best;
    }

    public @Nullable BlockPos relayLink() {
        return relayLink;
    }

    public void setRelayLink(@Nullable BlockPos link) {
        relayLink = link == null ? null : link.immutable();
        setChanged();
        syncToClients();
    }

    /** Where its relay links start (the tuner's threads): over the core. */
    public BlockPos relayOrigin() {
        return worldPosition.above();
    }

    /** True if this cell holds what the tier puts there (AlchyX's rule). */
    public static boolean grown(Level level, com.lealex.alchyx.multiblock.MultiblockPattern.Conversion conversion) {
        return TieredCoreBlockEntity.grown(level, conversion);
    }

    // ---- Removal, saving ----

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (isMoving()) return; // the core moved: its structure and gifts went with it
        super.preRemoveSideEffects(pos, state);
        // an altar broken: links pointing at it are cut (a source's own link simply goes with it)
        if (this instanceof AltarBlockEntity && level instanceof ServerLevel server) com.lealex.alchyforbidden.essence.Relays.unlinkTo(server, pos, true);
    }

    // ---- Kindling: a component fed to a working multiblock makes it work harder for a while ----

    /** Game time the kindling burns until (saved, synced). */
    private long kindledUntil;
    /** At most this much kindling stacks up (10 minutes). */
    public static final int KINDLE_MAX = 12000;

    /** The component that kindles it (null: nothing does). */
    public net.minecraft.world.item.@Nullable Item kindling() {
        return null;
    }

    /** How long one kindles it for, in ticks. */
    protected int kindleTicks() {
        return 1200;
    }

    /** How much harder it works while kindled. */
    protected float kindleFactor() {
        return 1.5F;
    }

    /** The particle of its kindling (the essence's own mote). */
    protected net.minecraft.core.particles.@Nullable ParticleOptions kindleParticle() {
        return null;
    }

    public boolean kindled() {
        return level != null && level.getGameTime() < kindledUntil;
    }

    public int kindledSeconds() {
        return level == null ? 0 : (int) Math.max(0, (kindledUntil - level.getGameTime()) / 20);
    }

    /** The factor its work is multiplied by now (1 when not kindled). */
    public float kindle() {
        return kindled() ? kindleFactor() : 1F;
    }

    /** A right-click with its kindling: one is used, the time stacks up to KINDLE_MAX. True when the stack was its kindling (both sides). */
    public boolean tryKindle(Player player, net.minecraft.world.item.ItemStack stack) {
        var item = kindling();
        if (item == null || stack.isEmpty() || !stack.is(item) || !isFormed() || level == null || wants(stack)) return false; // its growth takes what it still needs first
        if (level.isClientSide()) return true;
        long now = level.getGameTime(), from = Math.max(now, kindledUntil);
        if (from + kindleTicks() - now > KINDLE_MAX) {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) sp.sendSystemMessage(Component.translatable("gui.alchyforbidden.kindle_full", kindledSeconds()), true);
            return true;
        }
        kindledUntil = from + kindleTicks();
        if (!player.getAbilities().instabuild) stack.shrink(1);
        setChanged();
        syncToClients();
        if (level instanceof ServerLevel server) {
            Vec3 c = Vec3.atCenterOf(worldPosition).add(0, 1, 0);
            var particle = kindleParticle();
            if (particle != null) server.sendParticles(particle, c.x, c.y, c.z, 30, 0.8, 0.6, 0.8, 0.05);
            server.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 12, 0.4, 0.4, 0.4, 0.06);
            server.playSound(null, worldPosition, SoundEvents.BLAZE_SHOOT, net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 0.6F);
            server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.8F);
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendSystemMessage(Component.translatable("gui.alchyforbidden.kindled", Math.round((kindleFactor() - 1) * 100), kindledSeconds()), true);
        }
        return true;
    }

    /** Client: the kindling's motes rising off it while it burns (called from clientWorking). */
    protected void kindledParticles(RandomSource random, double cx, double cy, double cz) {
        var particle = kindleParticle();
        if (particle == null || level == null || !kindled() || random.nextInt(3) != 0) return;
        double a = random.nextDouble() * Math.PI * 2, r = 1 + random.nextDouble() * 1.5;
        level.addParticle(particle, cx + Math.cos(a) * r, cy + 0.2, cz + Math.sin(a) * r, 0, 0.06, 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (relayLink != null) output.store("relayLink", BlockPos.CODEC, relayLink);
        output.putLong("kindled", kindledUntil);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        relayLink = input.read("relayLink", BlockPos.CODEC).orElse(null);
        kindledUntil = input.getLongOr("kindled", 0L);
    }
}
