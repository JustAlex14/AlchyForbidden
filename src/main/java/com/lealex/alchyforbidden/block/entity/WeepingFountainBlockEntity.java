package com.lealex.alchyforbidden.block.entity;

import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.essence.Essence;
import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The Weeping Fountain: a basin of stone round its core, from the second tier a spout rising from its middle, from
 * the third a taller spout under a rift lantern and four lamp pillars round it. It weeps what the mod is built on and
 * the world gives grudgingly (crying obsidian, echo shards, ghast tears...): the player picks one of the products its
 * tier knows (Config.fountainProducts, its screen), and the fountain condenses it again and again, each time paying
 * that product's essence from the nearest awake altar within reach that holds it, the time shortened by its tier.
 * What it weeps goes into a nine-slot store (hoppers and pipes take it); a full store, or no altar able to pay,
 * stops it until there is room or essence.
 */
public class WeepingFountainBlockEntity extends GrowingCoreBlockEntity implements net.minecraft.world.MenuProvider,
        com.lealex.alchyforbidden.essence.EssenceReceiver {
    /** Why it is not weeping, for its screen (synced). */
    public static final int WORKING = 0, NOTHING_CHOSEN = 1, THIRSTY = 2, FULL = 3, LOCKED = 4;

    private @Nullable Identifier chosen;   // the product picked (synced)
    private long cycleStart = 0L;          // when this condensing began (synced: the screen's bar)
    private int cycleTicks = 0;            // how long it takes (0: not paid for yet)
    private int state = NOTHING_CHOSEN;    // synced
    /** Essence sent to it along links (a Lament Well, a Killing Pit, a resonator, through obelisks): drunk first. Synced. */
    private final int[] held = new int[Essence.values().length];
    private long madeAt = -1000L;          // when it last wept one (synced: the show over the pool)
    private int madeEvery = 200;           // how long that one took (synced: a fast fountain's show is quicker)
    private ItemStack made = ItemStack.EMPTY; // what it wept then (synced)

    /** How long the show of a wept item lasts: it forms over the spout, falls into the pool and sinks. */
    public static final int MADE_TICKS = 40;

    private static final java.util.Map<net.minecraft.world.level.Level, java.util.Set<WeepingFountainBlockEntity>> LOADED = new java.util.WeakHashMap<>();

    /** The fountains loaded in a level (both sides: the client draws their water). */
    public static List<WeepingFountainBlockEntity> loaded(net.minecraft.world.level.Level level) {
        java.util.Set<WeepingFountainBlockEntity> set = LOADED.get(level);
        return set == null ? List.of() : new java.util.ArrayList<>(set);
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

    public long madeAt() {
        return madeAt;
    }

    /** Client: how fast the show runs, so a fountain weeping every half second still shows each one fall (1 = full length). */
    public float showSpeed() {
        return Math.max(1F, (float) MADE_TICKS / Math.max(1, madeEvery));
    }

    public ItemStack made() {
        return made;
    }

    /** The top of the water in its basin. */
    public double waterY() {
        return worldPosition.getY() + 1.8;
    }

    private final net.minecraft.world.SimpleContainer store = new net.minecraft.world.SimpleContainer(9) {
        @Override
        public void setChanged() {
            super.setChanged();
            WeepingFountainBlockEntity.this.setChanged();
        }
    };
    private final net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> storeOut =
            net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(store);
    private final net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> automation = new TakeOnly();

    public WeepingFountainBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.WEEPING_FOUNTAIN_BE.get(), pos, state);
    }

    @Override
    protected String patternBase() {
        return "weeping_fountain";
    }

    @Override
    protected List<Config.Cost> costFor(int tier) {
        return Config.fountainCostFor(tier);
    }

    @Override
    protected String messagePrefix() {
        return "weeping_fountain";
    }

    @Override
    protected String machineName() {
        return "Weeping fountain";
    }

    @Override
    public net.minecraft.core.particles.ParticleOptions waveParticle() {
        return ModRegistries.SORROW_MOTE.get();
    }

    /** The rift opens over the spout. */
    @Override
    protected BlockPos riftPos() {
        return worldPosition.above(7);
    }

    @Override
    protected int riftSize() {
        return 3;
    }

    /** Where it weeps from: the spout's top (just over the basin at the first tier). */
    public BlockPos spoutTop() {
        return worldPosition.above(standing >= 3 ? 4 : standing == 2 ? 3 : 1);
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
        return Math.max(0, Config.fountainStore() - held[essence.ordinal()]);
    }

    /** Streams land in its spout. */
    @Override
    public BlockPos intakePos() {
        return spoutTop();
    }

    @Override
    public BlockPos receiverPos() {
        return worldPosition;
    }

    public int held(Essence essence) {
        return held[essence.ordinal()];
    }

    // ---- Its products ----

    /** Every product its config names, in order (the screen's grid). */
    public static List<Config.Product> products() {
        return Config.fountainProducts();
    }

    public Config.@Nullable Product chosen() {
        if (chosen == null) return null;
        for (Config.Product product : products()) {
            if (BuiltInRegistries.ITEM.getKey(product.item()).equals(chosen)) return product;
        }
        return null;
    }

    /** The player picks a product (by its place in the list): one its tier knows, or the same again to stop. */
    public void choose(int index, Player player) {
        List<Config.Product> all = products();
        if (index < 0 || index >= all.size()) return;
        Config.Product product = all.get(index);
        if (product.tier() > standing) return;
        Identifier id = BuiltInRegistries.ITEM.getKey(product.item());
        chosen = id.equals(chosen) ? null : id;
        cycleTicks = 0; // what was paid for the old one is lost: the rift does not give back
        state = chosen == null ? NOTHING_CHOSEN : WORKING;
        playSound(worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 0.7f);
        setChanged();
        syncToClients();
    }


    // ---- Kindling (GrowingCoreBlockEntity) ----

    @Override
    public net.minecraft.world.item.Item kindling() {
        return com.lealex.alchyforbidden.registry.ModRegistries.MOURNING_PEARL.get();
    }

    @Override
    protected int kindleTicks() {
        return 2400;
    }

    @Override
    protected float kindleFactor() {
        return 2.0F;
    }

    @Override
    protected net.minecraft.core.particles.ParticleOptions kindleParticle() {
        return com.lealex.alchyforbidden.registry.ModRegistries.SORROW_MOTE.get();
    }

    /** How long a product takes at the tier it stands at, in ticks. */
    public int ticksFor(Config.Product product) {
        return Math.max(10, (int) Math.round(product.seconds() * 20 / (Config.fountainSpeed(standing) * kindle()))); // a Mourning Pearl: twice as fast
    }

    public int state() {
        return state;
    }

    /** How far this condensing has come, 0..1 (either side). */
    public float progress(long now) {
        if (cycleTicks <= 0) return 0F;
        return Math.min(1F, (now - cycleStart) / (float) cycleTicks);
    }

    public net.minecraft.world.SimpleContainer store() {
        return store;
    }

    @Override
    protected void tickWorking() {
        if (!(level instanceof ServerLevel server) || !isFormed()) return;
        long now = level.getGameTime();
        // every tick while it weeps (a fast tier wastes none), else a look every ten ticks
        boolean due = cycleTicks > 0 ? now - cycleStart >= cycleTicks : state == WORKING || now % 10 == 0;
        if (now % 40 == 0) drinkAll(server);
        if (!due) return;
        Config.Product product = chosen();
        int was = state;
        if (product == null) {
            state = NOTHING_CHOSEN;
        } else if (product.tier() > standing) {
            state = LOCKED;
        } else if (cycleTicks <= 0) { // pay for the next one
            if (!store.canAddItem(new ItemStack(product.item()))) {
                state = FULL;
            } else {
                // what links sent it first, then the nearest altar holding enough
                boolean paid = false;
                if (held[product.essence().ordinal()] >= product.cost()) {
                    held[product.essence().ordinal()] -= product.cost();
                    paid = true;
                } else if (drinkFromSource(server, product.essence(), product.cost())) {
                    paid = true;
                } else {
                    AltarBlockEntity altar = payer(product.essence(), product.cost());
                    if (altar != null && altar.spendEssence(product.essence(), product.cost())) {
                        com.lealex.alchyforbidden.essence.Relays.guide(server, altar.getBlockPos().above(), spoutTop(), product.essence()); // drawn from the altar
                        paid = true;
                    }
                }
                if (paid) {
                    cycleStart = now;
                    cycleTicks = ticksFor(product);
                    state = WORKING;
                    setChanged();
                    syncToClients();
                } else {
                    state = THIRSTY;
                }
            }
        } else if (now - cycleStart >= cycleTicks) { // it weeps one
            ItemStack made = new ItemStack(product.item());
            if (store.canAddItem(made)) {
                this.made = made.copy();
                madeEvery = cycleTicks; // (the show keeps up with a fast fountain)
                store.addItem(made);
                cycleTicks = 0;
                madeAt = now; // the clients show it forming over the spout and falling into the pool (clientWorking, FountainWater)
                playSound(worldPosition, SoundEvents.AMETHYST_CLUSTER_PLACE, 0.8f, 0.6f);
                playSound(worldPosition, SoundEvents.SOUL_ESCAPE.value(), 0.5f, 0.7f);
                syncToClients();
            } else {
                state = FULL;
            }
        }
        if (state != was) {
            setChanged();
            syncToClients();
        }
    }

    /**
     * Every two seconds it drinks what the producing multiblocks within reach keep waiting (a Lament Well's Sorrow, a
     * Killing Pit's Wrath, an Echo Resonator's Corruption: what no altar or link took), all three essences, as far as
     * it has room; a guided stream from each source that gave into the spout.
     */
    private void drinkAll(ServerLevel server) {
        double reach = (double) Config.fountainReach() * Config.fountainReach();
        boolean drank = false;
        for (Essence essence : Essence.values()) {
            List<? extends GrowingCoreBlockEntity> sources = switch (essence) {
                case SORROW -> LamentWellBlockEntity.loaded(server);
                case WRATH -> KillingPitBlockEntity.loaded(server);
                case CORRUPTION -> ResonatorBlockEntity.loaded(server);
            };
            for (GrowingCoreBlockEntity source : sources) {
                int room = room(essence);
                if (room <= 0) break;
                if (source.isRemoved() || !source.isFormed() || source.getBlockPos().distSqr(worldPosition) > reach) continue;
                int amount = Math.min(Math.min(room, 200), buffered(source));
                if (amount <= 0) continue;
                boolean took = source instanceof LamentWellBlockEntity well ? well.takeBuffered(amount)
                        : source instanceof KillingPitBlockEntity pit ? pit.takeBuffered(amount)
                        : source instanceof ResonatorBlockEntity resonator && resonator.takeBuffered(amount);
                if (!took) continue;
                held[essence.ordinal()] += amount;
                com.lealex.alchyforbidden.essence.Relays.guide(server, source.relayOrigin(), spoutTop(), essence);
                drank = true;
            }
        }
        if (drank) {
            setChanged();
            syncToClients();
        }
    }

    /**
     * Drinks from a producing multiblock nearby that keeps that much waiting (a Lament Well's Sorrow, a Killing Pit's
     * Wrath, an Echo Resonator's Corruption: what no altar had room for), the nearest first; a stream from it to the
     * spout. True if one paid.
     */
    private boolean drinkFromSource(ServerLevel server, Essence essence, int cost) {
        double reach = (double) Config.fountainReach() * Config.fountainReach();
        List<? extends GrowingCoreBlockEntity> sources = switch (essence) {
            case SORROW -> LamentWellBlockEntity.loaded(server);
            case WRATH -> KillingPitBlockEntity.loaded(server);
            case CORRUPTION -> ResonatorBlockEntity.loaded(server);
        };
        GrowingCoreBlockEntity best = null;
        double bestDistance = reach;
        for (GrowingCoreBlockEntity source : sources) {
            if (source.isRemoved() || !source.isFormed() || buffered(source) < cost) continue;
            double distance = source.getBlockPos().distSqr(worldPosition);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = source;
            }
        }
        if (best == null) return false;
        boolean took = best instanceof LamentWellBlockEntity well ? well.takeBuffered(cost)
                : best instanceof KillingPitBlockEntity pit ? pit.takeBuffered(cost)
                : best instanceof ResonatorBlockEntity resonator && resonator.takeBuffered(cost);
        if (took) com.lealex.alchyforbidden.essence.Relays.guide(server, best.relayOrigin(), spoutTop(), essence);
        return took;
    }

    private static int buffered(GrowingCoreBlockEntity source) {
        if (source instanceof LamentWellBlockEntity well) return well.buffer();
        if (source instanceof KillingPitBlockEntity pit) return pit.buffer();
        return source instanceof ResonatorBlockEntity resonator ? resonator.buffer() : 0;
    }

    /** The nearest awake altar within reach holding that much of the essence. */
    private @Nullable AltarBlockEntity payer(Essence essence, int cost) {
        if (level == null) return null;
        AltarBlockEntity best = null;
        double bestDistance = (double) Config.fountainReach() * Config.fountainReach();
        for (AltarBlockEntity altar : AltarBlockEntity.loaded(level)) {
            if (!altar.isFormed() || !altar.hasEye() || altar.essence(essence) < cost) continue;
            double distance = altar.getBlockPos().distSqr(worldPosition);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = altar;
            }
        }
        return best;
    }

    // ---- Client: tears falling from the spout into the basin ----

    @Override
    protected void clientWorking(RandomSource random, long elapsed, double cx, double cy, double cz) {
        kindledParticles(random, cx, cy, cz);
        if (level == null || !isFormed()) return;
        madeShow(random);
        BlockPos top = spoutTop();
        double tx = top.getX() + 0.5, ty = top.getY() + (standing >= 2 ? 0.95 : 0.6), tz = top.getZ() + 0.5;
        boolean working = state == WORKING && cycleTicks > 0;
        if (random.nextInt(working ? 2 : 6) == 0 && com.lealex.alchyx.AlchyXClientConfig.spawn(random)) {
            double a = random.nextDouble() * Math.PI * 2, r = standing >= 2 ? 0.45 + random.nextDouble() * 0.15 : 0.3;
            level.addParticle(ParticleTypes.FALLING_OBSIDIAN_TEAR, tx + Math.cos(a) * r, ty, tz + Math.sin(a) * r, 0, 0, 0);
        }
        if (working && random.nextInt(3) == 0 && com.lealex.alchyx.AlchyXClientConfig.spawn(random)) {
            double a = random.nextDouble() * Math.PI * 2;
            level.addParticle(ModRegistries.SORROW_MOTE.get(), cx + Math.cos(a) * 0.9, worldPosition.getY() + 1.1, cz + Math.sin(a) * 0.9, 0, 0.03, 0);
        }
    }

    /** The wept item's show: it forms over the spout, falls into the pool, splashes and sinks. */
    public static final int FORM = 12, FALL = 12, SINK = 12;

    /** Which way the wept item tips off the spout (one of the four sides, changing with each one). */
    private int[] dropSide() {
        int side = (int) Math.floorMod(madeAt, 4L);
        return new int[][] {{1, 0}, {0, 1}, {-1, 0}, {0, -1}}[side];
    }

    /** Client: where the wept item is t ticks after it was made. */
    public net.minecraft.world.phys.Vec3 madePos(float t) {
        double cx = worldPosition.getX() + 0.5, cz = worldPosition.getZ() + 0.5;
        int[] side = dropSide();
        boolean spout = standing >= 2;
        double fromOff = spout ? 0.55 : 0, toOff = spout ? 1.0 : 0; // off the spout's bowl, into the pool beside it
        double fromY = spout ? spoutTop().getY() + 1.2 : worldPosition.getY() + 2.9;
        double water = waterY();
        if (t < FORM) { // forming, hanging over the spout
            return new net.minecraft.world.phys.Vec3(cx + side[0] * fromOff, fromY + 0.04 * Math.sin(t * 0.6), cz + side[1] * fromOff);
        }
        if (t < FORM + FALL) { // falling, faster and faster
            double k = (t - FORM) / FALL;
            double off = fromOff + (toOff - fromOff) * (1 - (1 - k) * (1 - k));
            return new net.minecraft.world.phys.Vec3(cx + side[0] * off, fromY + (water - fromY) * k * k, cz + side[1] * off);
        }
        double k = Math.min(1, (t - FORM - FALL) / SINK); // sinking out of sight
        return new net.minecraft.world.phys.Vec3(cx + side[0] * toOff, water - 0.5 * k, cz + side[1] * toOff);
    }

    /** Client: the wept item's size t ticks after it was made (grows as it forms, shrinks as it sinks). */
    public float madeScale(float t) {
        if (t < FORM) return Math.max(0F, t / FORM);
        if (t < FORM + FALL) return 1F;
        return Math.max(0F, 1F - (t - FORM - FALL) / SINK);
    }

    /** Client: essence gathering into the item as it forms, a trail as it falls, the splash, bubbles as it sinks. */
    private void madeShow(RandomSource random) {
        if (level == null || made.isEmpty()) return;
        float speed = showSpeed();
        long ticks = level.getGameTime() - madeAt;
        long t = (long) (ticks * speed);
        long previous = (long) ((ticks - 1) * speed);
        if (ticks < 0 || t > MADE_TICKS) return;
        Config.Product product = chosen();
        net.minecraft.core.particles.ParticleOptions mote = product == null ? ModRegistries.SORROW_MOTE.get() : switch (product.essence()) {
            case WRATH -> ModRegistries.WRATH_MOTE.get();
            case SORROW -> ModRegistries.SORROW_MOTE.get();
            case CORRUPTION -> ModRegistries.CORRUPTION_MOTE.get();
        };
        net.minecraft.world.phys.Vec3 at = madePos(t);
        if (t < FORM) { // drawn in from round it
            for (int i = 0; i < 2; i++) {
                if (!com.lealex.alchyx.AlchyXClientConfig.spawn(random)) continue;
                double a = random.nextDouble() * Math.PI * 2, b = (random.nextDouble() - 0.5) * Math.PI;
                double dx = Math.cos(a) * Math.cos(b) * 0.7, dy = Math.sin(b) * 0.7, dz = Math.sin(a) * Math.cos(b) * 0.7;
                level.addParticle(mote, at.x + dx, at.y + dy, at.z + dz, -dx * 0.08, -dy * 0.08, -dz * 0.08);
            }
        } else if (t < FORM + FALL) { // a trail behind it
            if (com.lealex.alchyx.AlchyXClientConfig.spawn(random)) level.addParticle(mote, at.x, at.y + 0.15, at.z, 0, 0.01, 0);
            if (random.nextInt(2) == 0) level.addParticle(ParticleTypes.FALLING_WATER, at.x, at.y, at.z, 0, 0, 0);
        } else if (t >= FORM + FALL && previous < FORM + FALL) { // into the water
            double water = waterY();
            for (int i = 0; i < com.lealex.alchyx.AlchyXClientConfig.count(22, random); i++) {
                double a = random.nextDouble() * Math.PI * 2, r = 0.1 + random.nextDouble() * 0.5;
                level.addParticle(ParticleTypes.SPLASH, at.x + Math.cos(a) * r, water + 0.05, at.z + Math.sin(a) * r, Math.cos(a) * 0.05, 0.15, Math.sin(a) * 0.05);
            }
            for (int i = 0; i < com.lealex.alchyx.AlchyXClientConfig.count(8, random); i++) {
                level.addParticle(mote, at.x, water + 0.1, at.z, (random.nextDouble() - 0.5) * 0.12, random.nextDouble() * 0.08, (random.nextDouble() - 0.5) * 0.12);
            }
            level.playLocalSound(at.x, water, at.z, SoundEvents.GENERIC_SPLASH, net.minecraft.sounds.SoundSource.BLOCKS, 0.5F,
                    1.2F + random.nextFloat() * 0.3F, false);
        } else if (random.nextInt(2) == 0) { // bubbles as it sinks
            level.addParticle(ParticleTypes.BUBBLE_POP, at.x + (random.nextDouble() - 0.5) * 0.3, waterY() + 0.02, at.z + (random.nextDouble() - 0.5) * 0.3, 0, 0.01, 0);
        }
    }

    // ---- Store, screen, saving ----

    @Override
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> getItemHandler() {
        return automation;
    }

    /** Hoppers and pipes take what it wept; nothing goes in. */
    private class TakeOnly implements net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> {
        @Override public int size() { return storeOut.size(); }
        @Override public net.neoforged.neoforge.transfer.item.ItemResource getResource(int index) { return storeOut.getResource(index); }
        @Override public long getAmountAsLong(int index) { return storeOut.getAmountAsLong(index); }
        @Override public long getCapacityAsLong(int index, net.neoforged.neoforge.transfer.item.ItemResource resource) { return storeOut.getCapacityAsLong(index, resource); }
        @Override public boolean isValid(int index, net.neoforged.neoforge.transfer.item.ItemResource resource) { return false; }
        @Override public int insert(int index, net.neoforged.neoforge.transfer.item.ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) { return 0; }
        @Override public int extract(int index, net.neoforged.neoforge.transfer.item.ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            return storeOut.extract(index, resource, amount, transaction);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (isMoving()) return;
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) {
            net.minecraft.world.Containers.dropContents(server, pos, store);
            com.lealex.alchyforbidden.essence.Relays.unlinkTo(server, pos, false); // links that sent to it are cut
        }
    }

    @Override
    public void openGui(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        tryGrowing();
        serverPlayer.openMenu(this, out -> out.writeBlockPos(worldPosition));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.alchyforbidden.weeping_fountain");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, Player player) {
        return new com.lealex.alchyforbidden.menu.SourceMenu(id, inventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (chosen != null) output.store("chosen", Identifier.CODEC, chosen);
        output.putLong("cycleStart", cycleStart);
        output.putInt("cycleTicks", cycleTicks);
        output.putInt("state", state);
        output.putIntArray("held", held.clone());
        output.putLong("madeAt", madeAt);
        output.putInt("madeEvery", madeEvery);
        if (!made.isEmpty()) output.store("made", ItemStack.OPTIONAL_CODEC, made);
        output.store("store", ItemStack.OPTIONAL_CODEC.listOf(), store.getItems());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        chosen = input.read("chosen", Identifier.CODEC).orElse(null);
        cycleStart = input.getLongOr("cycleStart", 0L);
        cycleTicks = input.getIntOr("cycleTicks", 0);
        state = input.getIntOr("state", NOTHING_CHOSEN);
        madeAt = input.getLongOr("madeAt", -1000L);
        madeEvery = input.getIntOr("madeEvery", 200);
        made = input.read("made", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        int[] kept = input.getIntArray("held").orElse(new int[0]);
        java.util.Arrays.fill(held, 0);
        for (int i = 0; i < held.length && i < kept.length; i++) held[i] = kept[i];
        store.clearContent();
        List<ItemStack> wept = input.read("store", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < wept.size() && i < store.getContainerSize(); i++) store.setItem(i, wept.get(i));
    }

    /** Its three essences beside the crosshair (AlchyX LookMeters). */
    @Override
    public void lookMeters(java.util.List<LookMeter> out) {
        for (com.lealex.alchyforbidden.essence.Essence e : com.lealex.alchyforbidden.essence.Essence.values()) {
            out.add(new LookMeter(held(e), com.lealex.alchyforbidden.Config.fountainStore(), 0xFF000000 | e.color(), e.icon()));
        }
    }
}
