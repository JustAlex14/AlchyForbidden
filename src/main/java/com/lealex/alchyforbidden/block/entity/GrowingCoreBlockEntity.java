package com.lealex.alchyforbidden.block.entity;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.network.AltarRiftPayload;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.lealex.alchyx.block.ChamberShellBlock;
import com.lealex.alchyx.block.entity.MultiblockCoreBlockEntity;
import com.lealex.alchyx.multiblock.CoreTracker;
import com.lealex.alchyx.multiblock.MultiblockPattern;
import com.lealex.alchyx.multiblock.MultiblockPatterns;
import com.lealex.alchyx.multiblock.ShellParts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Every AlchyForbidden multiblock grows the same way (author's decision): the player builds tier 1 by hand, it forms
 * into AlchyX shells, and the rift itself grows every later tier once the core has been given what the tier asks for.
 * This class is that growth, shared: the earned tier and the tier the structure stands at (patterns
 * {@code <base>_1..<max>}, all the same box), the gifts, the phases (GATHER: the rift opens over the core; GROW: the
 * parts it no longer wants sink into the rift, then every new part flies out of it, a few per tick; SETTLE: the burst),
 * the noise of a growth, the core rising when a tier puts it higher, and the core sinking back when the structure
 * breaks (the earned tier is kept: tier 1 built again grows the rest back for nothing).
 *
 * Between growths it does nothing of its own beyond {@link #tickWorking()} (the subclass's work) and AlchyX's tick.
 */
public abstract class GrowingCoreBlockEntity extends MultiblockCoreBlockEntity {
    /** Blocks the rift may eat where it grows (natural ground, sculk's own spread, the mod's own looks). */
    public static final TagKey<Block> RIFT_EATS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "rift_eats"));
    /** The real blocks the multiblocks grow (sculk veins, soul lanterns), which a later tier may clear again. */
    public static final TagKey<Block> REAL_PARTS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "altar_real_parts"));

    /** The game's own blocks the multiblocks wear as looks (the resonator's shriekers): a later tier may clear them. */
    public static final TagKey<Block> OWN_LOOKS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "own_looks"));

    protected static final int IDLE = 0, GATHER = 1, GROW = 2, SETTLE = 3;
    /** The first phase number a subclass may use for its own work (the altar's rite). */
    protected static final int OWN_PHASES = 4;
    private static final int SETTLE_TICKS = 30; // longer than a part's flight down from the rift (client/AltarRifts: 20)
    private static final double MESSAGE_RANGE = 32.0;

    protected int tier = 1;        // the tier earned
    protected int standing = 1;    // the tier the structure stands at (its pattern); below tier while it grows back
    protected int phase = IDLE;
    protected long phaseStart = 0L;
    protected int goal = 1;        // the tier being grown
    private final Map<Identifier, Integer> offered = new HashMap<>(); // what was given toward the next tier

    // Not saved: the growth's order (worked out again when needed), what it clears, the last place reported in the way
    private @Nullable List<MultiblockPattern.Conversion> plan;
    private @Nullable List<BlockPos> clears;
    private @Nullable BlockPos blockedAt;
    private boolean moving = false; // the core is moving: its old block entity goes without breaking anything
    /**
     * Parts on their way down from the rift that are set down only when their flight lands (client/AltarRifts flies
     * them for FLY_TICKS), so they never show early nor double up with the flying one: real parts, and shells that
     * change their look (the old look stays until the new one lands on it).
     */
    private record Landing(BlockPos pos, BlockState look, long at, boolean real, boolean hidden, boolean restore) {}
    private final List<Landing> landing = new ArrayList<>();
    /** How long a part flies from the rift to its place (client/AltarRifts FLY_TICKS). */
    private static final int FLY_TICKS = 20;
    /** How long the platform takes to rise one block (client/AltarRifts RISE_TICKS). */
    private static final int RISE_TICKS = 60;
    private boolean risen = false; // this growth's platform has started rising
    private long riseUntil = 0L;   // and stands at its new height from then

    protected GrowingCoreBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---- What each multiblock says ----

    /** The patterns' common name: tier N is {@code alchyforbidden:<base>_N}. */
    protected abstract String patternBase();

    /** Its highest tier. */
    public int maxTier() {
        return 3;
    }

    /** What growing to this tier costs. */
    protected abstract List<Config.Cost> costFor(int tier);

    /** How many blocks above the floor the core stands at a tier (the altar rises one per tier; most stay put). */
    protected int rise(int tier) {
        return 0;
    }

    /**
     * True for a cell of the platform that rises a block each tier instead of coming out of the rift (the altar's
     * stepped middle); {@link #platformBase} for the floor under it, which changes where it stands. None by default.
     */
    protected boolean platform(BlockPos pos) {
        return false;
    }

    protected boolean platformBase(BlockPos pos) {
        return false;
    }

    /** True if it may grow now (the altar needs its eye). */
    protected boolean canGrow() {
        return true;
    }

    /** The first word of its message keys: "<prefix>.alchyforbidden.<key>". */
    protected String messagePrefix() {
        return "altar";
    }

    /** Work done every tick while it is formed and not growing (the altar: nothing, the resonator: its pearl). */
    protected void tickWorking() {}

    /** Work of a subclass's own phase (OWN_PHASES and up). */
    protected void tickOwnPhase(int phase, long elapsed) {}

    /** Called as a growth begins (the resonator calls its pearl back: its path is about to change). */
    protected void onGrowthStart() {}

    /** Called just before the core sinks back when its structure breaks (drop what lived on the grown parts...). */
    protected void onStructureBroken() {}

    /** Where the great rift opens while it grows: two blocks over the core. */
    protected BlockPos riftPos() {
        return worldPosition.above(2);
    }

    protected int riftSize() {
        return goal >= 3 ? 6 : 4;
    }

    /** Where the end-of-growth burst flashes. */
    protected Vec3 burstPosition() {
        return Vec3.atCenterOf(worldPosition.above());
    }

    /** What the core leaves where it stood when it rises, if the next tier names nothing there. */
    protected BlockState pedestalFallback() {
        return ModRegistries.ALTAR_PEDESTAL.get().defaultBlockState();
    }

    // ---- State ----

    @Override
    public Identifier patternId() {
        return patternOf(standing);
    }

    /** The pattern of a tier (the eye's vision shows the next one). */
    public Identifier patternIdFor(int tier) {
        return patternOf(tier);
    }

    protected Identifier patternOf(int tier) {
        return Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, patternBase() + "_" + tier);
    }

    public int tier() {
        return tier;
    }

    public int standing() {
        return standing;
    }

    /** True while it gathers, grows or settles, or works in a phase of its own. */
    public boolean isGrowing() {
        return phase != IDLE;
    }

    /** The tier it would grow next (0 = none): for the eye's vision. */
    public int nextTier() {
        if (!isFormed() || isGrowing()) return 0;
        if (standing < tier) return tier;
        return tier < maxTier() ? tier + 1 : 0;
    }

    /** Where the core stands at a tier (it rises or sinks by the patterns' core layers, over the same floor). */
    public BlockPos coreFor(int tier) {
        return worldPosition.above(rise(tier) - rise(standing));
    }

    // Grown parts are hidden while they fly down from the rift: no puff where they will land
    @Override
    public boolean veilPuffs() {
        return false;
    }

    // While it gathers and grows it reshapes itself: AlchyX holds its structure checks until the new tier stands
    @Override
    protected boolean holdsStructure() {
        return phase == GATHER || phase == GROW;
    }

    protected void setPhase(int next) {
        phase = next;
        phaseStart = level == null ? 0L : level.getGameTime();
        setChanged();
        syncToClients();
    }

    // ---- Giving ----

    /** What the next tier asks for (for screens). */
    public List<Config.Cost> nextCosts() {
        return tier < maxTier() ? costFor(tier + 1) : List.of();
    }

    public int givenOf(Item item) {
        return offered.getOrDefault(BuiltInRegistries.ITEM.getKey(item), 0);
    }

    /** True if this stack is something it asks for now (both sides; the client asks its own copy of the config). */
    public boolean wants(ItemStack stack) {
        if (stack.isEmpty() || !isFormed() || !canGrow() || isGrowing() || tier >= maxTier() || standing < tier) return false;
        for (Config.Cost cost : costFor(tier + 1)) {
            if (stack.is(cost.item()) && givenOf(cost.item()) < cost.count()) return true;
        }
        return false;
    }

    /** Takes what it still needs of this stack; once everything is given, the next tier grows. */
    public void offer(Player player, ItemStack stack) {
        if (level == null || !wants(stack)) return;
        for (Config.Cost cost : costFor(tier + 1)) {
            if (!stack.is(cost.item())) continue;
            int take = Math.min(cost.count() - givenOf(cost.item()), stack.getCount());
            if (take <= 0) continue;
            offered.merge(BuiltInRegistries.ITEM.getKey(cost.item()), take, Integer::sum);
            if (!player.getAbilities().instabuild) stack.shrink(take);
            setChanged();
            syncToClients();
            playSound(worldPosition, ModRegistries.ALTAR_OFFER.get(), 0.8f, 0.9f + level.getRandom().nextFloat() * 0.2f);
            if (level instanceof ServerLevel server) {
                server.sendParticles(ModRegistries.RIFT_MOTE.get(), worldPosition.getX() + 0.5, worldPosition.getY() + 1.1,
                        worldPosition.getZ() + 0.5, 12, 0.25, 0.15, 0.25, 0.02);
            }
            break;
        }
        if (paid()) startGrowing(tier + 1);
        else if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(wantsLine(), true);
    }

    protected boolean paid() {
        for (Config.Cost cost : costFor(tier + 1)) {
            if (givenOf(cost.item()) < cost.count()) return false;
        }
        return extraPaid(tier + 1);
    }

    /** Something a tier asks for besides items (the resonator's third: a warden killed beside it). Default none. */
    protected boolean extraPaid(int tier) {
        return true;
    }

    /** That something, said in the wants line (null = nothing more). */
    protected @Nullable Component extraWanted(int tier) {
        return null;
    }

    /** Tries the growth it is owed: back to its earned tier, or the next one once paid for. */
    protected void tryGrowing() {
        if (!isFormed() || isGrowing() || !canGrow()) return;
        if (standing < tier) startGrowing(tier);
        else if (tier < maxTier() && paid()) startGrowing(tier + 1);
    }

    /** What the next tier still asks for, e.g. "Tier 1. It wants 2 Echo Shard, 16 Sculk". */
    public Component wantsLine() {
        MutableComponent list = Component.empty();
        boolean first = true;
        for (Config.Cost cost : costFor(tier + 1)) {
            int missing = cost.count() - givenOf(cost.item());
            if (missing <= 0) continue;
            if (!first) list.append(", ");
            list.append(missing + " ").append(cost.item().getName(new ItemStack(cost.item())));
            first = false;
        }
        Component extra = extraPaid(tier + 1) ? null : extraWanted(tier + 1);
        if (extra != null) {
            if (!first) list.append(", ");
            list.append(extra);
        }
        return Component.translatable(messagePrefix() + ".alchyforbidden.wants", tier, list);
    }

    // ---- Growing ----

    /** Starts growing toward a tier, if nothing stands in the way of its blocks (otherwise says where). */
    protected void startGrowing(int toTier) {
        if (level == null || isGrowing() || !canGrow()) return;
        MultiblockPattern target = MultiblockPatterns.get(patternOf(toTier));
        if (target == null) {
            AlchyForbidden.LOGGER.warn("no pattern {} loaded: {} cannot grow", patternOf(toTier), machineName());
            return;
        }
        List<MultiblockPattern.Conversion> order = order(target, coreFor(toTier));
        clears = clearsFor(target, coreFor(toTier));
        List<BlockPos> needed = new ArrayList<>();
        for (MultiblockPattern.Conversion conversion : order) {
            if (!grown(level, conversion)) needed.add(conversion.pos());
        }
        int up = rise(toTier) - rise(standing);
        for (int i = 1; i <= up; i++) needed.add(worldPosition.above(i)); // where the core rises to
        for (BlockPos pos : needed) {
            if (pos.equals(worldPosition)) continue; // the core's own cell: the move fills it
            BlockState current = level.getBlockState(pos);
            if (!roomFor(current) && !ShellParts.isShell(current) && !current.is(getBlockState().getBlock())) {
                if (!pos.equals(blockedAt)) {
                    blockedAt = pos.immutable();
                    say(Component.translatable("altar.alchyforbidden.blocked", current.getBlock().getName(), pos.getX(), pos.getY(), pos.getZ()));
                }
                return;
            }
        }
        blockedAt = null;
        if (toTier > tier) { // paid for: the gifts are spent
            tier = toTier;
            offered.clear();
        }
        goal = toTier;
        plan = order;
        risen = false;
        riseUntil = 0L;
        onGrowthStart();
        setPhase(GATHER);
        if (level instanceof ServerLevel server) AltarRiftPayload.open(server, riftPos(), riftSize()); // the great rift
        setAnimationWorking(true);
        playSound(worldPosition, ModRegistries.ALTAR_GATHER.get(), 1.2f, 0.8f);
        say(Component.translatable(messagePrefix() + ".alchyforbidden.gathers"));
    }

    /** Where the rift may put a block: air, plants and snow, water, and the natural ground in #alchyforbidden:rift_eats. */
    protected static boolean roomFor(BlockState state) {
        return state.isAir() || state.canBeReplaced() || state.is(RIFT_EATS);
    }

    /**
     * True if this cell already holds what the tier puts there: the real block, or a shell wearing that very look (a
     * shell with another look, like a step that becomes a solid block at the next tier, is grown again). Both sides.
     */
    public static boolean grown(Level level, MultiblockPattern.Conversion conversion) {
        BlockState current = level.getBlockState(conversion.pos());
        if (conversion.result().real() || !ShellParts.isShell(current)) return conversion.isDone(current);
        BlockState disguise = ChamberShellBlock.disguiseAt(level, conversion.pos());
        return disguise == null || disguise == conversion.disguise(Blocks.AIR.defaultBlockState());
    }

    /** The cells of a tier, lowest first, then nearest; the column over the core comes last (the middle rises at the end). */
    private List<MultiblockPattern.Conversion> order(MultiblockPattern target, BlockPos core) {
        List<MultiblockPattern.Conversion> cells = new ArrayList<>(target.conversions(core, rotation()));
        cells.sort(Comparator.<MultiblockPattern.Conversion>comparingInt(c -> isCenter(c.pos()) ? 1 : 0)
                .thenComparingInt(c -> c.pos().getY())
                .thenComparingDouble(c -> c.pos().distSqr(worldPosition)));
        return cells;
    }

    /** Its own parts standing where the new tier asks for air (a tier can reshape the one below), highest first. */
    private List<BlockPos> clearsFor(MultiblockPattern target, BlockPos core) {
        List<BlockPos> found = new ArrayList<>();
        if (level == null) return found;
        for (MultiblockPattern.PreviewCell cell : target.previewCells()) {
            if (cell.display() != null) continue; // only the cells that must be air
            BlockPos pos = core.offset(cell.offset());
            if (ownPart(level, pos)) found.add(pos.immutable());
        }
        found.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
        return found;
    }

    /** Shells wearing one of this mod's looks (or a vanilla look the mod grows: real parts), never anything else. */
    protected static boolean ownPart(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(REAL_PARTS)) return true;
        if (!ShellParts.isShell(state)) return false;
        BlockState look = ChamberShellBlock.disguiseAt(level, pos);
        return look != null && (BuiltInRegistries.BLOCK.getKey(look.getBlock()).getNamespace().equals(AlchyForbidden.MODID)
                || look.is(OWN_LOOKS));
    }

    private boolean isCenter(BlockPos pos) {
        return pos.getX() == worldPosition.getX() && pos.getZ() == worldPosition.getZ();
    }

    @Override
    protected void tickFormed() {
        if (level == null) return;
        if (phase == IDLE) {
            tickWorking();
            return;
        }
        long elapsed = level.getGameTime() - phaseStart;
        switch (phase) {
            case GATHER -> {
                if (elapsed % 20 == 10) playSound(worldPosition, ModRegistries.ALTAR_GATHER.get(), 0.9f, 0.7f + elapsed / 200f);
                if (elapsed >= Config.gatherTicks()) {
                    setPhase(GROW);
                    if (level instanceof ServerLevel server) AltarNoise.sound(server, worldPosition, this::say);
                }
            }
            case GROW -> {
                land();
                if (elapsed % Config.ticksPerStep() != 0) return;
                for (int i = 0; i < Config.blocksPerStep(); i++) {
                    if (!growOne()) break;
                }
            }
            case SETTLE -> {
                if (elapsed >= SETTLE_TICKS) finish();
            }
            default -> tickOwnPhase(phase, elapsed);
        }
    }

    /** Grows (or clears) the next block of the tier. False when there is none left, or the next one is in the way. */
    private boolean growOne() {
        if (level == null) return false;
        if (plan == null) {
            MultiblockPattern target = MultiblockPatterns.get(patternOf(goal));
            if (target == null) {
                setPhase(IDLE);
                setAnimationWorking(false);
                return false;
            }
            plan = order(target, coreFor(goal));
            clears = clearsFor(target, coreFor(goal));
        }
        // First what the new tier has no place for: it flies up into the rift
        if (clears != null && !clears.isEmpty()) {
            BlockPos pos = clears.removeFirst();
            if (ownPart(level, pos)) {
                BlockState gone = ShellParts.isShell(level.getBlockState(pos)) ? ChamberShellBlock.disguiseAt(level, pos) : level.getBlockState(pos);
                if (gone != null && level instanceof ServerLevel server) {
                    AltarRiftPayload.part(server, AltarRiftPayload.SINK, riftPos(), pos, gone, riftSize());
                }
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                if (level instanceof ServerLevel server) {
                    server.sendParticles(ModRegistries.RIFT_MOTE.get(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
                }
                playSound(pos, ModRegistries.ALTAR_GROW.get(), 0.7f, 0.5f);
                return true;
            }
        }
        boolean platformLeft = false;
        for (MultiblockPattern.Conversion conversion : plan) {
            BlockPos pos = conversion.pos();
            if (pos.equals(worldPosition)) continue; // the core's own cell: the move fills it
            BlockState current = level.getBlockState(pos);
            if (grown(level, conversion) || landingAt(pos)) continue;
            if (!roomFor(current) && !ShellParts.isShell(current)) { // someone put something there meanwhile: wait
                if (!pos.equals(blockedAt)) {
                    blockedAt = pos.immutable();
                    say(Component.translatable("altar.alchyforbidden.blocked", current.getBlock().getName(), pos.getX(), pos.getY(), pos.getZ()));
                }
                return false;
            }
            blockedAt = null;
            BlockState look = conversion.disguise(Blocks.AIR.defaultBlockState());
            if (!conversion.result().real() && platformBase(pos)) { // the floor under the platform: changed where it lies
                ChamberShellBlock.place(level, pos, look, conversion.result().hidden(), conversion.result().restore());
                return true;
            }
            if (!conversion.result().real() && platform(pos) && !risen) { // the platform rises last, all at once
                platformLeft = true;
                continue;
            }
            BlockState there = ShellParts.isShell(current) ? ChamberShellBlock.disguiseAt(level, pos) : current;
            if (there != null && !there.isAir() && there.getBlock() == look.getBlock()) {
                // Already standing there (a real part from an older layout, or the same block turned another way):
                // changed where it stands, nothing flies out of the rift to land on it
                if (conversion.result().real()) level.setBlock(pos, look, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                else ChamberShellBlock.place(level, pos, look, conversion.result().hidden(), conversion.result().restore());
                return true;
            }
            if (look.is(Blocks.SCULK_VEIN)) {
                // Sculk is not brought: it creeps over the stone where it stands
                if (conversion.result().real()) level.setBlock(pos, look, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                else ChamberShellBlock.place(level, pos, look, conversion.result().hidden(), conversion.result().restore());
                if (level instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.SCULK_CHARGE_POP, pos.getX() + 0.5, pos.getY() + 0.15, pos.getZ() + 0.5, 8, 0.35, 0.05, 0.35, 0.01);
                    server.sendParticles(ModRegistries.SCULK_FLECK.get(), pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.01);
                }
                playSound(pos, SoundEvents.SCULK_BLOCK_SPREAD, 0.8f, 0.8f + level.getRandom().nextFloat() * 0.3f);
                return true;
            }
            if (conversion.result().real() || ShellParts.isShell(current)) { // set down as it lands
                landing.add(new Landing(pos.immutable(), look, level.getGameTime() + FLY_TICKS, conversion.result().real(),
                        conversion.result().hidden(), conversion.result().restore()));
            } else {
                // veiled: hidden on the clients until it has flown down from the rift (client/AltarRifts), and
                // see-through until then so the blocks around it show no holes (finish() settles it)
                ChamberShellBlock.place(level, pos, look, conversion.result().hidden(), conversion.result().restore(),
                        conversion.result().hidden() ? 0L : worldPosition.asLong(), true);
            }
            if (level instanceof ServerLevel server) AltarRiftPayload.part(server, AltarRiftPayload.EMERGE, riftPos(), pos, look, riftSize());
            if (isCenter(pos)) playSound(pos, ModRegistries.ALTAR_AWAKEN.get(), 0.7f, 0.6f); // the middle rises
            RandomSource random = level.getRandom();
            playSound(pos, ModRegistries.ALTAR_GROW.get(), 0.8f, 0.8f + random.nextFloat() * 0.4f);
            return true;
        }
        if (!landing.isEmpty()) return false; // the last parts are still flying down
        if (platformLeft) {
            risePlatform();
            return true;
        }
        if (level.getGameTime() < riseUntil) return false; // the platform is still rising (the core moves as it stops)
        // Everything stands: the core rises to its new place if the tier puts it higher, the structure is the new tier
        // at once, then the burst
        BlockPos to = coreFor(goal);
        BlockState pedestal = pedestalLook();
        BlockPos rift = riftPos(); // (worked out before the tier changes)
        standing = goal;
        plan = null;
        clears = null;
        phase = SETTLE;
        phaseStart = level.getGameTime();
        GrowingCoreBlockEntity core = to.equals(worldPosition) ? this : moveCore(to, pedestal);
        if (core == null) return false;
        if (level instanceof ServerLevel server) AltarRiftPayload.close(server, rift);
        core.risen();
        return false;
    }

    /** The look the core leaves where it stood when it rises: the tier's cell there, or the fallback. */
    private BlockState pedestalLook() {
        MultiblockPattern target = MultiblockPatterns.get(patternOf(goal));
        if (target != null) {
            for (MultiblockPattern.Conversion conversion : target.conversions(coreFor(goal), rotation())) {
                if (conversion.pos().equals(worldPosition)) return conversion.disguise(Blocks.AIR.defaultBlockState());
            }
        }
        return pedestalFallback();
    }

    /** At its new tier (and place): known to AlchyX under its new pattern, checked, the burst. */
    private void risen() {
        if (level == null) return;
        CoreTracker.add(level, worldPosition, patternId());
        requestStructureCheck();
        setChanged();
        syncToClients();
        awaken();
    }

    /**
     * Moves the core block (and everything it holds: tier, gifts, contents, AlchyX's state) to another place, leaving
     * {@code leave} as a shell where it stood, or air when null. Returns the block entity at the new place.
     */
    private @Nullable GrowingCoreBlockEntity moveCore(BlockPos to, @Nullable BlockState leave) {
        if (level == null) return null;
        Level world = level;
        BlockPos from = worldPosition;
        net.minecraft.nbt.CompoundTag data = saveCustomOnly(world.registryAccess());
        BlockState state = getBlockState();
        if (state.hasProperty(com.lealex.alchyforbidden.block.AltarBlock.RISING)) {
            state = state.setValue(com.lealex.alchyforbidden.block.AltarBlock.RISING, false); // shown again where it arrives
        }
        world.setBlock(to, state, Block.UPDATE_ALL);
        if (!(world.getBlockEntity(to) instanceof GrowingCoreBlockEntity moved)) return null;
        moved.loadCustomOnly(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, world.registryAccess(), data));
        moved.plan = null;
        moving = true;
        if (leave == null) world.setBlock(from, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        else ChamberShellBlock.place(world, from, leave, false, false, 0L);
        CoreTracker.add(world, to, moved.patternId());
        return moved;
    }

    /** True while the old block entity of a moving core goes (it must not drop or break anything). */
    protected boolean isMoving() {
        return moving;
    }

    /** The tier is grown: a burst of the rift. */
    private void awaken() {
        if (!(level instanceof ServerLevel server)) return;
        playSound(worldPosition, ModRegistries.ALTAR_AWAKEN.get(), 1.4f, 1.0f);
        server.sendParticles(ModRegistries.RIFT_MOTE.get(), worldPosition.getX() + 0.5, worldPosition.getY() + 1.2,
                worldPosition.getZ() + 0.5, 60, 1.6, 0.8, 1.6, 0.08);
        Vec3 burst = burstPosition();
        server.sendParticles(ParticleTypes.SONIC_BOOM, burst.x, burst.y, burst.z, 1, 0, 0, 0, 0);
    }

    /**
     * The whole platform rises together: every cell of it that changes is placed now, veiled (hidden on the clients
     * while client/AltarRifts slides its look up out of the block below it), see-through until finish() settles it.
     */
    private void risePlatform() {
        if (level == null || plan == null) return;
        risen = true;
        riseUntil = level.getGameTime() + RISE_TICKS;
        for (MultiblockPattern.Conversion conversion : plan) {
            BlockPos pos = conversion.pos();
            if (pos.equals(worldPosition) || conversion.result().real() || !platform(pos) || grown(level, conversion)) continue;
            BlockState current = level.getBlockState(pos);
            if (!roomFor(current) && !ShellParts.isShell(current)) continue; // something in the way: growOne reports it
            BlockState look = conversion.disguise(Blocks.AIR.defaultBlockState());
            // (a shell already there, the stand that rises off it or the old circle, is converted again: hidden too)
            ChamberShellBlock.place(level, pos, look, conversion.result().hidden(), conversion.result().restore(),
                    conversion.result().hidden() ? 0L : worldPosition.asLong(), true);
            if (level instanceof ServerLevel server) AltarRiftPayload.part(server, AltarRiftPayload.RISE, pos, pos, look, 0);
        }
        // The core rises with it (the altar: one block a tier): hidden where it stands, slid up to where it will be,
        // the pedestal it leaves rising under it; it really moves when the rise ends
        BlockPos to = coreFor(goal);
        BlockState core = getBlockState();
        if (to.equals(worldPosition.above()) && core.hasProperty(com.lealex.alchyforbidden.block.AltarBlock.RISING)
                && level instanceof ServerLevel server) {
            level.setBlock(worldPosition, core.setValue(com.lealex.alchyforbidden.block.AltarBlock.RISING, true), Block.UPDATE_CLIENTS);
            AltarRiftPayload.part(server, AltarRiftPayload.RISE, to, to, core.setValue(com.lealex.alchyforbidden.block.AltarBlock.RISING, false), 0);
            AltarRiftPayload.part(server, AltarRiftPayload.RISE, worldPosition, worldPosition, pedestalLook(), 0);
        }
        playSound(worldPosition, SoundEvents.PISTON_EXTEND, 0.8f, 0.45f);
        playSound(worldPosition, ModRegistries.ALTAR_AWAKEN.get(), 0.6f, 0.5f);
    }

    /** Sets down the real parts whose flight from the rift has landed (a cell filled meanwhile waits for growOne). */
    private void land() {
        if (level == null || landing.isEmpty()) return;
        long now = level.getGameTime();
        landing.removeIf(part -> {
            if (part.at() > now) return false;
            BlockState there = level.getBlockState(part.pos());
            if (!part.real() && (ShellParts.isShell(there) || roomFor(there))) {
                ChamberShellBlock.place(level, part.pos(), part.look(), part.hidden(), part.restore());
            } else if (part.real() && roomFor(there)) {
                level.setBlock(part.pos(), part.look(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            return true;
        });
    }

    private boolean landingAt(BlockPos pos) {
        for (Landing part : landing) {
            if (part.pos().equals(pos)) return true;
        }
        return false;
    }

    /** The burst is over: it rests at its new tier (every part has landed: they become opaque again). */
    private void finish() {
        if (level == null) return;
        MultiblockPattern pattern = pattern();
        if (pattern != null) {
            for (MultiblockPattern.Conversion conversion : pattern.conversions(worldPosition, rotation())) {
                ChamberShellBlock.settle(level, conversion.pos());
            }
        }
        setPhase(IDLE);
        setAnimationWorking(false);
        say(Component.translatable(messagePrefix() + ".alchyforbidden.grown", standing));
    }

    @Override
    protected void onStructureChanged(boolean nowFormed) {
        if (level == null) return;
        if (!nowFormed) {
            // The parts are gone (AlchyX destroys every part in the box, grown ones included). The tier is kept:
            // rebuilt by hand, the first tier grows the rest back. A risen core sinks back to its first tier's place.
            boolean changed = standing != 1 || phase != IDLE;
            int sink = rise(standing) - rise(1);
            if ((phase == GATHER || phase == GROW) && level instanceof ServerLevel server) AltarRiftPayload.close(server, riftPos());
            onStructureBroken();
            standing = 1;
            plan = null;
            landing.clear();
            riseUntil = 0L;
            BlockState core = getBlockState();
            if (core.hasProperty(com.lealex.alchyforbidden.block.AltarBlock.RISING) && core.getValue(com.lealex.alchyforbidden.block.AltarBlock.RISING)) {
                level.setBlock(worldPosition, core.setValue(com.lealex.alchyforbidden.block.AltarBlock.RISING, false), Block.UPDATE_CLIENTS);
            }
            phase = IDLE;
            setAnimationWorking(false);
            if (sink > 0 && free(worldPosition.below(sink), sink)) {
                GrowingCoreBlockEntity moved = moveCore(worldPosition.below(sink), null);
                if (moved != null) {
                    moved.setChanged();
                    moved.syncToClients();
                }
                return;
            }
            if (changed) {
                setChanged();
                syncToClients();
                CoreTracker.add(level, worldPosition, patternId());
            }
        } else if (tier > standing) {
            startGrowing(tier);
        }
    }

    /** True if the cells from {@code bottom} up, {@code count} of them, are free for the core to sink through. */
    private boolean free(BlockPos bottom, int count) {
        if (level == null) return false;
        for (int i = 0; i < count; i++) {
            if (!roomFor(level.getBlockState(bottom.above(i)))) return false;
        }
        return true;
    }

    // ---- Client: the gathering drawn around the core ----

    @Override
    public void clientTick() {
        if (level == null) return;
        RandomSource random = level.getRandom();
        long elapsed = level.getGameTime() - phaseStart;
        double cx = worldPosition.getX() + 0.5, cy = worldPosition.getY() + 0.9, cz = worldPosition.getZ() + 0.5;
        if (phase == GATHER) {
            // motes drawn in from a ring around the core, more and more of them, turning as they come
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
        } else if (phase == GROW) {
            for (int i = 0; i < 2; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                level.addParticle(ModRegistries.SCULK_FLECK.get(), cx + Math.cos(angle) * 0.6, cy, cz + Math.sin(angle) * 0.6,
                        Math.cos(angle) * 0.05, 0.08, Math.sin(angle) * 0.05);
            }
        } else {
            clientWorking(random, elapsed, cx, cy, cz);
        }
    }

    /** Client effects outside the growth (the altar's rite, the resonator's pearl). */
    protected void clientWorking(RandomSource random, long elapsed, double cx, double cy, double cz) {}

    // ---- Messages ----

    protected void say(Component message) {
        if (!(level instanceof ServerLevel server)) return;
        Vec3 center = Vec3.atCenterOf(worldPosition);
        for (ServerPlayer player : server.players()) {
            if (player.distanceToSqr(center) <= MESSAGE_RANGE * MESSAGE_RANGE) player.sendSystemMessage(message);
        }
    }

    // ---- Removal: what was given comes back ----

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (moving) return; // the core moved: its structure and gifts went with it
        super.preRemoveSideEffects(pos, state);
        if (level == null || level.isClientSide()) return;
        for (Map.Entry<Identifier, Integer> entry : offered.entrySet()) {
            Item item = BuiltInRegistries.ITEM.getValue(entry.getKey());
            int left = entry.getValue();
            while (left > 0) {
                int count = Math.min(left, item.getDefaultMaxStackSize());
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(item, count));
                left -= count;
            }
        }
        offered.clear();
    }

    // ---- Saving ----

    private static final com.mojang.serialization.Codec<Map<Identifier, Integer>> OFFERED_CODEC =
            com.mojang.serialization.Codec.unboundedMap(Identifier.CODEC, com.mojang.serialization.Codec.INT);

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("tier", tier);
        output.putInt("standing", standing);
        output.putInt("phase", phase);
        output.putLong("phaseStart", phaseStart);
        output.putInt("goal", goal);
        if (!offered.isEmpty()) output.store("offered", OFFERED_CODEC, offered);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        int oldStanding = standing;
        super.loadAdditional(input);
        tier = Mth.clamp(input.getIntOr("tier", 1), 1, maxTier());
        standing = Mth.clamp(input.getIntOr("standing", 1), 1, tier);
        phase = input.getIntOr("phase", IDLE);
        phaseStart = input.getLongOr("phaseStart", 0L);
        goal = Mth.clamp(input.getIntOr("goal", tier), 1, maxTier());
        offered.clear();
        input.read("offered", OFFERED_CODEC).ifPresent(offered::putAll);
        if (standing != oldStanding) plan = null;
    }
}
