package com.lealex.alchyforbidden.block.entity;

import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.essence.Essence;
import com.lealex.alchyforbidden.network.AltarRiftPayload;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.lealex.alchyx.multiblock.MultiblockPattern;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Echo Resonator: the first multiblock that makes Sculk Corruption (the altar only spends it). A sculk shrieker
 * stands at each corner. An ender pearl is thrown out of the rift over the core and flies round through the four
 * shriekers; each time it passes one, the shrieker's echo turns into a sonic boom, and the boom is Corruption,
 * sent to the nearest awake altar (a stream of sculk into its eye) or kept here until one has room. At each pass the
 * pearl has a 45% chance (config) to break, and the next one is thrown. The third tier is grown only after a warden
 * has been killed beside it: the dead warden then hangs in the rift, and the void at its core (where its sonic boom
 * comes from) blasts each shrieker in turn: no pearls any more.
 *
 * It grows like every AlchyForbidden multiblock (GrowingCoreBlockEntity: tier 1 by hand, the rest out of the rift);
 * its core stays put. It works only while it has pearls (or the heart) and somewhere to put the Corruption.
 */
public class ResonatorBlockEntity extends GrowingCoreBlockEntity implements net.minecraft.world.MenuProvider {
    public static final String PEARL = "pearl";
    /** The places the pearl flies through, in order: the four corners. Pattern anchors emitter_0..3. */
    public static final int CORNERS = 4;

    private int pearls = 0;
    private int eyes = 0;            // eyes of ender: thrown first, they break less often
    private boolean flyingEye = false; // what is on its way round: an eye, or a pearl
    private int buffer = 0;          // Corruption made with no altar to take it
    private boolean flying = false;  // a pearl (or the heart) is on its way round
    private long launchedAt = 0L;    // when the current lap began
    private int leg = 0;             // the leg it is on (0 = out of the rift to the first corner, then corner to corner)
    private boolean running = false; // the rift over the core is open
    private @Nullable BlockPos linkedAltar; // the altar its Corruption went to last (for the screen)
    private boolean riftShown = false;     // tier 3: the rift over the warden was opened since this loaded
    /**
     * Tier 3's first moments (the body laid on the table out of a rift, the crystal coming down from it, the heart
     * pulled out of the chest): when they began, 0 = not yet. client/DeadWardenFigure plays them from it.
     */
    private long awakenedAt = 0L;
    public static final int LAY_TICKS = 50, CRYSTAL_TICKS = 50, HEART_TICKS = 60;
    public static final int INTRO_TICKS = LAY_TICKS + CRYSTAL_TICKS + HEART_TICKS;
    /** The rift those come out of, this many blocks over the core. */
    public static final int INTRO_RIFT = 6;
    private boolean wardenSlain = false;    // a warden died beside it (what its third tier asks for besides items)
    private int blastTarget = 0;            // tier 3: the shrieker the warden blasts now
    private long blastStart = Long.MIN_VALUE / 2; // tier 3: when the warden began charging that blast

    /** Ticks a stream of the warden's core takes to reach a shrieker (client/AltarRifts' stream). */
    public static final int STREAM_TICKS = 26;
    /** How high the rift crystal floats over the table (the core's top), where the streams to the shriekers start. */
    public static final float CRYSTAL_Y = 2.7F;

    // The resonators loaded on the server, per level (a warden's death looks among them)
    private static final java.util.Map<net.minecraft.world.level.Level, java.util.Set<ResonatorBlockEntity>> LOADED = new java.util.WeakHashMap<>();

    public static List<ResonatorBlockEntity> loaded(net.minecraft.world.level.Level level) {
        java.util.Set<ResonatorBlockEntity> set = LOADED.get(level);
        return set == null ? List.of() : new java.util.ArrayList<>(set);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            LOADED.computeIfAbsent(level, key -> java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>())).add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && LOADED.containsKey(level)) LOADED.get(level).remove(this);
    }

    public ResonatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.RESONATOR_BE.get(), pos, state);
    }

    @Override
    protected String patternBase() {
        return "resonator";
    }

    @Override
    protected List<Config.Cost> costFor(int tier) {
        return Config.resonatorCostFor(tier);
    }

    @Override
    protected String messagePrefix() {
        return "resonator";
    }

    @Override
    protected String machineName() {
        return "Echo resonator";
    }

    @Override
    public net.minecraft.core.particles.ParticleOptions waveParticle() {
        return ParticleTypes.SCULK_SOUL;
    }

    /** The rift: over the core, and at the last tier one higher, over the warden lying on the table. */
    @Override
    protected BlockPos riftPos() {
        return worldPosition.above(standing >= 3 ? 3 : 2);
    }

    /** At the third tier the dead warden blasts the shriekers: no pearls. */
    public boolean wardenWorks() {
        return isFormed() && standing >= 3;
    }

    /** When the last tier's first moments began (0: not yet). Synced. */
    public long awakenedAt() {
        return awakenedAt;
    }

    /** True once the body lies on the table, the crystal hangs over it and the heart has been pulled out. */
    public boolean awake(long now) {
        return awakenedAt != 0 && now - awakenedAt >= INTRO_TICKS;
    }

    public boolean wardenSlain() {
        return wardenSlain;
    }

    public int blastTarget() {
        return blastTarget;
    }

    public long blastStart() {
        return blastStart;
    }

    /** A warden died beside it: its third tier can grow (if the rest is given). Its body goes into the rift. */
    public void wardenDied(net.minecraft.world.entity.LivingEntity warden) {
        if (wardenSlain || !isFormed() || tier >= maxTier()) return;
        wardenSlain = true;
        setChanged();
        syncToClients();
        if (level instanceof ServerLevel server) { // its body is taken by the rift (client/WardenTaken)
            AltarRiftPayload.part(server, AltarRiftPayload.TAKEN, warden.blockPosition().above(5), warden.blockPosition(),
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), (int) warden.getYRot());
            playSound(warden.blockPosition(), SoundEvents.PORTAL_TRIGGER, 0.5f, 0.6f);
        }
        say(Component.translatable("resonator.alchyforbidden.warden_slain"));
        tryGrowing();
    }

    @Override
    protected boolean extraPaid(int tier) {
        return tier < 3 || wardenSlain;
    }

    @Override
    protected @Nullable Component extraWanted(int tier) {
        return tier >= 3 ? Component.translatable("resonator.alchyforbidden.wants_warden") : null;
    }

    public int pearls() {
        return pearls;
    }

    public int eyes() {
        return eyes;
    }

    /** What flies the round now (an eye of ender or a pearl). Synced for the screen. */
    public net.minecraft.world.item.Item flyingItem() {
        return flyingEye ? Items.ENDER_EYE : Items.ENDER_PEARL;
    }

    /** Room left for pearls and eyes together. */
    private int room() {
        return Math.max(0, Config.resonatorMaxPearls() - pearls - eyes);
    }

    public int buffer() {
        return buffer;
    }

    /** Ticks for one leg of the pearl's way (faster at higher tiers). Both sides read it. */
    public int legTicks() {
        return Math.max(4, Config.resonatorLegTicks(standing));
    }

    public boolean isFlying() {
        return flying;
    }

    public long launchedAt() {
        return launchedAt;
    }

    // ---- Pearls in ----

    /** Right-click with ender pearls or eyes of ender: they go in (up to the config's most). True if any went in. */
    public boolean feed(Player player, ItemStack stack) {
        boolean eye = stack.is(Items.ENDER_EYE);
        if (!eye && !stack.is(Items.ENDER_PEARL)) return false;
        int take = Math.min(stack.getCount(), room());
        if (take <= 0) return false;
        if (eye) eyes += take;
        else pearls += take;
        if (!player.getAbilities().instabuild) stack.shrink(take);
        setChanged();
        syncToClients();
        playSound(worldPosition, SoundEvents.ENDER_EYE_DEATH, 0.6f, 1.4f);
        return true;
    }

    /** Sneak + right-click with an empty hand: the pearls and eyes come back. */
    public void takePearls(Player player) {
        if (pearls + eyes <= 0) return;
        for (ItemStack back : new ItemStack[] {new ItemStack(Items.ENDER_PEARL, pearls), new ItemStack(Items.ENDER_EYE, eyes)}) {
            if (!back.isEmpty() && !player.getInventory().add(back)) player.drop(back, false);
        }
        pearls = 0;
        eyes = 0;
        setChanged();
        syncToClients();
    }

    @Override
    public void openGui(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        tryGrowing();
        relink();
        serverPlayer.openMenu(this, buffer -> buffer.writeBlockPos(worldPosition));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.alchyforbidden.resonator");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory, Player player) {
        return new com.lealex.alchyforbidden.menu.ResonatorMenu(containerId, inventory, this);
    }

    /** The altar its Corruption goes to (null: none awake within reach with room). Synced for the screen. */
    public @Nullable BlockPos linkedAltar() {
        return linkedAltar;
    }

    private void relink() {
        AltarBlockEntity altar = nearestAltar();
        BlockPos now = altar == null ? null : altar.getBlockPos().immutable();
        if (java.util.Objects.equals(now, linkedAltar)) return;
        linkedAltar = now;
        setChanged();
        syncToClients();
    }

    /** The menu's pearl slot sets the count (server). */
    public void setPearls(int count) {
        pearls = Math.max(0, Math.min(Config.resonatorMaxPearls() - eyes, count));
        setChanged();
        syncToClients();
    }

    /** The menu's eye slot sets the count (server). */
    public void setEyes(int count) {
        eyes = Math.max(0, Math.min(Config.resonatorMaxPearls() - pearls, count));
        setChanged();
        syncToClients();
    }

    public Component statusLine() {
        if (!isFormed()) {
            String problem = getStructureProblem();
            return isForming() ? Component.translatable("resonator.alchyforbidden.forming")
                    : Component.translatable("resonator.alchyforbidden.unformed", problem == null ? "" : problem);
        }
        if (isGrowing()) return Component.translatable("altar.alchyforbidden.growing");
        Component work = wardenWorks() ? Component.translatable("resonator.alchyforbidden.warden", buffer)
                : Component.translatable("resonator.alchyforbidden.pearls", pearls, eyes, buffer);
        if (standing < tier || tier >= maxTier()) return work;
        return Component.empty().append(work).append(" ").append(wantsLine());
    }

    // ---- The pearl's round ----

    @Override
    protected void tickWorking() {
        if (level == null || !isFormed()) return;
        long now = level.getGameTime();
        if (wardenWorks()) {
            if (awakenedAt == 0) { // the last tier stands: its first moments
                awakenedAt = now;
                if (level instanceof ServerLevel server) AltarRiftPayload.open(server, worldPosition.above(INTRO_RIFT), 3);
                playSound(worldPosition.above(INTRO_RIFT), SoundEvents.WARDEN_EMERGE, 0.8f, 0.6f);
                setChanged();
                syncToClients();
                return;
            }
            long intro = now - awakenedAt;
            if (intro < INTRO_TICKS) {
                BlockPos table = worldPosition.above();
                if (intro == LAY_TICKS) playSound(table, SoundEvents.WARDEN_DEATH, 0.5f, 0.6f); // laid down on the table
                if (intro == LAY_TICKS + 10) playSound(worldPosition.above(4), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.5f);
                if (intro == LAY_TICKS + CRYSTAL_TICKS) { // the heart torn out
                    playSound(table, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.5f);
                    playSound(table, SoundEvents.SOUL_ESCAPE.value(), 1.0f, 0.6f);
                }
                if (intro == INTRO_TICKS - 20 && level instanceof ServerLevel server) AltarRiftPayload.close(server, worldPosition.above(INTRO_RIFT));
                return;
            }
            blasts(now);
            return;
        }
        if (!flying) {
            if (now % 40 == 0) relink();
            boolean full = buffer >= Config.resonatorBuffer() && nearestAltar() == null;
            if (full || pearls + eyes <= 0) {
                if (running) stop();
                return;
            }
            launch(now);
            return;
        }
        if (now - launchedAt < (long) (leg + 1) * legTicks()) return;
        // out of the rift to the first shrieker, then round them, one to the next, until it breaks
        boolean broke = boom(leg % CORNERS);
        leg++;
        if (broke) {
            flying = false;
            setAnimationDisplay(PEARL, ItemStack.EMPTY);
            setChanged();
            syncToClients();
        }
    }

    /**
     * Tier 3: the shriekers draw on the dead warden's core, one after the other: a stream of its energy flows from the
     * core to the shrieker (client/AltarRifts), and as it arrives the shrieker screams and booms: Corruption.
     */
    private void blasts(long now) {
        if (flying) { // a pearl left over from the second tier
            flying = false;
            setAnimationDisplay(PEARL, ItemStack.EMPTY);
        }
        boolean full = buffer >= Config.resonatorBuffer() && nearestAltar() == null;
        long since = now - blastStart;
        if (since >= Config.resonatorBlastTicks() || since < 0) {
            if (full) {
                if (now % 40 == 0) relink();
                return;
            }
            blastTarget = (blastTarget + 1) % CORNERS;
            blastStart = now;
            running = true; // (no rift at the last tier: the crystal over the warden does the work)
            setChanged();
            syncToClients();
            if (level instanceof ServerLevel server) { // from the crystal (the stream's end is drawn one block over the pos it is given)
                AltarRiftPayload.part(server, AltarRiftPayload.ABSORB, BlockPos.containing(crystalCenter()), emitter(blastTarget).below(),
                        net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 0);
            }
            playSound(BlockPos.containing(wardenCore()), SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.6f);
        } else if (since == STREAM_TICKS) {
            boom(blastTarget);
        }
    }

    /** Where the void at the dead warden's core is (its chest), the blasts' start. */
    /** Where the rift crystal floats over the warden (client/DeadWardenFigure.CRYSTAL_Y over the table). */
    public Vec3 crystalCenter() {
        return Vec3.atBottomCenterOf(worldPosition.above()).add(0, CRYSTAL_Y, 0);
    }

    public Vec3 wardenCore() {
        return Vec3.atBottomCenterOf(worldPosition.above()).add(0, 0.45, 0); // its chest, lying on the table
    }

    private void launch(long now) {
        flyingEye = eyes > 0; // the eyes first: they last longer
        if (flyingEye) eyes--;
        else pearls--;
        flying = true;
        leg = 0;
        launchedAt = now;
        setAnimationDisplay(PEARL, new ItemStack(flyingItem()));
        if (level instanceof ServerLevel server) AltarRiftPayload.open(server, riftPos(), 2); // each pearl comes through the rift
        running = true;
        setChanged();
        syncToClients();
        playSound(riftPos(), SoundEvents.ENDER_PEARL_THROW, 0.6f, 0.7f);
    }

    private void stop() {
        running = false;
        setAnimationDisplay(PEARL, ItemStack.EMPTY);
        if (level instanceof ServerLevel server) AltarRiftPayload.close(server, riftPos());
        setChanged();
        syncToClients();
    }

    /** The pearl passes a corner's shrieker: a sonic boom, Corruption. True if the pearl broke. */
    private boolean boom(int corner) {
        if (!(level instanceof ServerLevel server)) return false;
        BlockPos emitter = emitter(corner);
        Vec3 at = Vec3.atCenterOf(emitter).add(0, 0.9, 0);
        server.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        server.sendParticles(ParticleTypes.SCULK_CHARGE_POP, at.x, at.y - 0.4, at.z, 6, 0.3, 0.1, 0.3, 0.01);
        playSound(emitter, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5f, 1.2f);
        playSound(emitter, SoundEvents.WARDEN_SONIC_BOOM, 0.35f, 1.5f);
        deliver(Config.resonatorPerBoom(standing), emitter);
        RandomSource random = level.getRandom();
        int chance = flyingEye ? Config.resonatorEyeBreakChance() : Config.resonatorBreakChance();
        if (!wardenWorks() && random.nextInt(100) < chance) {
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, flyingItem()), at.x, at.y, at.z, 10, 0.15, 0.15, 0.15, 0.08);
            playSound(emitter, SoundEvents.GLASS_BREAK, 0.5f, 1.6f);
            return true;
        }
        return false;
    }

    /** The Corruption goes to the nearest awake altar with room (a stream into its eye), the rest waits here. */
    private void deliver(int amount, BlockPos from) {
        if (!(level instanceof ServerLevel server)) return;
        int total = amount + buffer;
        AltarBlockEntity altar = nearestAltar();
        linkedAltar = altar == null ? null : altar.getBlockPos().immutable();
        if (altar != null) {
            int taken = altar.addEssence(Essence.CORRUPTION, total);
            if (taken > 0) {
                AltarRiftPayload.part(server, AltarRiftPayload.ABSORB, from, altar.getBlockPos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 0);
                total -= taken;
            }
        }
        buffer = Math.min(Config.resonatorBuffer(), total);
        setChanged();
        syncToClients();
    }

    private @Nullable AltarBlockEntity nearestAltar() {
        if (level == null) return null;
        int reach = Config.resonatorReach();
        AltarBlockEntity best = null;
        double bestDistance = (double) reach * reach;
        for (AltarBlockEntity altar : AltarBlockEntity.loaded(level)) {
            if (!altar.isFormed() || !altar.hasEye()) continue;
            if (altar.essence(Essence.CORRUPTION) >= altar.reserveCap()) continue;
            double distance = altar.getBlockPos().distSqr(worldPosition);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = altar;
            }
        }
        return best;
    }

    /** Where corner i's shrieker stands (pattern anchor emitter_i), or the core if the pattern has none. */
    public BlockPos emitter(int corner) {
        MultiblockPattern pattern = pattern();
        BlockPos at = pattern == null ? null : pattern.anchor(worldPosition, rotation(), "emitter_" + corner);
        return at == null ? worldPosition : at;
    }

    /**
     * Where the pearl is (the middle of it), from the round's start and the time: out of the rift to the first
     * corner, round the four, back into the rift; each leg a little arc. Both sides (client/EchoPearlFigure).
     */
    public Vec3 pearlAt(float time) {
        float t = Math.max(0, time - launchedAt) / legTicks();
        int index = (int) t;
        float along = Mth.clamp(t - index, 0, 1);
        Vec3 rift = Vec3.atCenterOf(riftPos());
        Vec3 from = index == 0 ? rift : Vec3.atCenterOf(emitter((index - 1) % CORNERS)).add(0, 0.9, 0);
        Vec3 to = Vec3.atCenterOf(emitter(index % CORNERS)).add(0, 0.9, 0);
        Vec3 straight = from.lerp(to, along);
        // out of the rift it climbs high, then from shrieker to shrieker a little arc
        float lift = index == 0 ? 2.2F : 0.8F;
        return straight.add(0, Mth.sin(along * Mth.PI) * lift, 0);
    }

    /** A growth begins: the pearl in flight comes back to the stock (the shriekers are about to move). */
    @Override
    protected void onGrowthStart() {
        if (flying) {
            flying = false;
            if (!wardenWorks() && room() > 0) {
                if (flyingEye) eyes++;
                else pearls++;
            }
            setAnimationDisplay(PEARL, ItemStack.EMPTY);
        }
        if (running && level instanceof ServerLevel server) AltarRiftPayload.close(server, riftPos());
        running = false;
        riftShown = false;
    }

    @Override
    protected void onStructureBroken() {
        if (awakenedAt != 0 && level instanceof ServerLevel server && !awake(level.getGameTime())) {
            AltarRiftPayload.close(server, worldPosition.above(INTRO_RIFT));
        }
        awakenedAt = 0L; // grown back, it plays its first moments again
        if (flying) {
            flying = false;
            setAnimationDisplay(PEARL, ItemStack.EMPTY);
        }
        if (running && level instanceof ServerLevel server) AltarRiftPayload.close(server, riftPos());
        running = false;
    }

    @Override
    protected void clientWorking(RandomSource random, long elapsed, double cx, double cy, double cz) {
        if (level == null) return;
        if (wardenWorks() && !awake(level.getGameTime())) { // its first moments (the figure draws them; the particles here)
            introParticles(random, level.getGameTime() - awakenedAt);
            return;
        }
        if (wardenWorks()) { // the soul torn up out of the warden's core into the crystal
            Vec3 core = Vec3.atBottomCenterOf(worldPosition.above()).add(0, 1.15, 0);
            Vec3 crystal = crystalCenter();
            if (random.nextInt(2) == 0) {
                double sx = (random.nextDouble() - 0.5) * 0.2, sz = (random.nextDouble() - 0.5) * 0.2;
                level.addParticle(ParticleTypes.SCULK_SOUL, core.x + sx, core.y + 0.15, core.z + sz, 0, (crystal.y - core.y) * 0.045, 0);
            }
            if (random.nextInt(6) == 0) level.addParticle(ParticleTypes.SOUL, core.x, core.y + 0.2, core.z, 0, 0.06, 0);
            return;
        }
        if (!flying) return;
        Vec3 at = pearlAt(level.getGameTime());
        level.addParticle(ModRegistries.RIFT_MOTE.get(), at.x, at.y, at.z, 0, 0, 0);
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.SCULK_SOUL, at.x, at.y, at.z, 0, 0.01, 0);
    }

    /**
     * Client: the particles of the last tier's first moments (intro: ticks since they began). The body coming down
     * out of the rift trails souls and motes and lands in a burst; the crystal comes down in a falling spiral of motes
     * and glyphs; the heart is torn out of the chest in a gush of sculk souls, with a stream of souls following it up.
     */
    private void introParticles(RandomSource random, long intro) {
        if (level == null || awakenedAt == 0) return;
        Vec3 table = Vec3.atBottomCenterOf(worldPosition.above());
        double riftY = worldPosition.getY() + INTRO_RIFT + 0.5;
        if (intro < LAY_TICKS) {
            float k = (float) intro / LAY_TICKS;
            k = k * k * (3 - 2 * k);
            double y = Mth.lerp(k, riftY, table.y + 0.4);
            for (int i = 0; i < 2; i++) { // the body's trail
                level.addParticle(random.nextBoolean() ? ParticleTypes.SCULK_SOUL : ModRegistries.RIFT_MOTE.get(),
                        table.x + (random.nextDouble() - 0.5) * 1.6, y + random.nextDouble() * 0.6, table.z + (random.nextDouble() - 0.5) * 0.8,
                        0, 0.02, 0);
            }
            if (random.nextInt(2) == 0) { // motes dripping from the rift
                level.addParticle(ModRegistries.RIFT_MOTE.get(), table.x + (random.nextDouble() - 0.5) * 2, riftY, table.z + (random.nextDouble() - 0.5) * 2,
                        0, -0.08, 0);
            }
        } else if (intro == LAY_TICKS) { // laid down: a burst off the table
            for (int i = 0; i < 24; i++) {
                double a = random.nextDouble() * Math.PI * 2, s = 0.08 + random.nextDouble() * 0.1;
                level.addParticle(ParticleTypes.SCULK_CHARGE_POP, table.x + Math.cos(a) * 0.6, table.y + 0.1, table.z + Math.sin(a) * 0.6,
                        Math.cos(a) * s, 0.02, Math.sin(a) * s);
            }
            for (int i = 0; i < 10; i++) {
                level.addParticle(ParticleTypes.SCULK_SOUL, table.x + (random.nextDouble() - 0.5) * 2, table.y + 0.5, table.z + (random.nextDouble() - 0.5),
                        0, 0.06, 0);
            }
        } else if (intro < LAY_TICKS + CRYSTAL_TICKS) {
            float k = (float) (intro - LAY_TICKS) / CRYSTAL_TICKS;
            k = k * k * (3 - 2 * k);
            double y = Mth.lerp(k, riftY, table.y + CRYSTAL_Y);
            double turn = intro * 0.35;
            for (int i = 0; i < 3; i++) { // a falling spiral round the crystal, glyphs drawn into it
                double a = turn + i * Math.PI * 2 / 3, r = 0.9 - 0.5 * k;
                level.addParticle(ModRegistries.RIFT_MOTE.get(), table.x + Math.cos(a) * r, y, table.z + Math.sin(a) * r, 0, -0.02, 0);
            }
            if (random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.ENCHANT, table.x + (random.nextDouble() - 0.5) * 3, y - 1 + random.nextDouble() * 2,
                        table.z + (random.nextDouble() - 0.5) * 3, 0, 0, 0);
            }
        } else {
            long pulled = intro - LAY_TICKS - CRYSTAL_TICKS;
            Vec3 chest = table.add(0, 0.45, 0), crystal = table.add(0, CRYSTAL_Y, 0);
            if (pulled == 0) { // torn out: a gush of souls from the chest
                for (int i = 0; i < 30; i++) {
                    double a = random.nextDouble() * Math.PI * 2, s = 0.05 + random.nextDouble() * 0.12;
                    level.addParticle(random.nextInt(3) == 0 ? ParticleTypes.SOUL : ParticleTypes.SCULK_SOUL, chest.x, chest.y + 0.2, chest.z,
                            Math.cos(a) * s, 0.08 + random.nextDouble() * 0.12, Math.sin(a) * s);
                }
            }
            float k = Mth.clamp((float) pulled / HEART_TICKS, 0, 1);
            for (int i = 0; i < 2; i++) { // souls streaming up after the heart, sculk flecks off the wound
                level.addParticle(ParticleTypes.SCULK_SOUL, chest.x + (random.nextDouble() - 0.5) * 0.4, chest.y + 0.2, chest.z + (random.nextDouble() - 0.5) * 0.4,
                        0, (crystal.y - chest.y) * 0.04, 0);
            }
            if (random.nextInt(2) == 0) {
                level.addParticle(ModRegistries.SCULK_FLECK.get(), chest.x + (random.nextDouble() - 0.5) * 0.8, chest.y + 0.1, chest.z + (random.nextDouble() - 0.5) * 0.8,
                        0, 0.03, 0);
            }
            if (k > 0.9F && random.nextInt(2) == 0) { // the crystal drinking it in
                double a = random.nextDouble() * Math.PI * 2;
                level.addParticle(ModRegistries.RIFT_MOTE.get(), crystal.x + Math.cos(a) * 1.2, crystal.y, crystal.z + Math.sin(a) * 1.2,
                        -Math.cos(a) * 0.08, 0, -Math.sin(a) * 0.08);
            }
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (isMoving()) return;
        super.preRemoveSideEffects(pos, state);
        if (level == null || level.isClientSide()) return;
        if (running && level instanceof ServerLevel server) AltarRiftPayload.close(server, riftPos());
        for (int k = 0; k < 2; k++) {
            int left = k == 0 ? pearls : eyes;
            while (left > 0) {
                int count = Math.min(16, left);
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        new ItemStack(k == 0 ? Items.ENDER_PEARL : Items.ENDER_EYE, count));
                left -= count;
            }
        }
        pearls = 0;
        eyes = 0;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("pearls", pearls);
        output.putInt("eyes", eyes);
        output.putBoolean("flyingEye", flyingEye);
        output.putInt("buffer", buffer);
        output.putBoolean("flying", flying);
        output.putLong("launchedAt", launchedAt);
        output.putInt("leg", leg);
        output.putBoolean("running", running);
        output.putBoolean("wardenSlain", wardenSlain);
        output.putInt("blastTarget", blastTarget);
        output.putLong("blastStart", blastStart);
        output.putLong("awakenedAt", awakenedAt);
        if (linkedAltar != null) output.store("linkedAltar", BlockPos.CODEC, linkedAltar);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        pearls = input.getIntOr("pearls", 0);
        eyes = input.getIntOr("eyes", 0);
        flyingEye = input.getBooleanOr("flyingEye", false);
        buffer = input.getIntOr("buffer", 0);
        flying = input.getBooleanOr("flying", false);
        launchedAt = input.getLongOr("launchedAt", 0L);
        leg = input.getIntOr("leg", 0);
        running = input.getBooleanOr("running", false);
        wardenSlain = input.getBooleanOr("wardenSlain", false);
        blastTarget = input.getIntOr("blastTarget", 0);
        blastStart = input.getLongOr("blastStart", Long.MIN_VALUE / 2);
        awakenedAt = input.getLongOr("awakenedAt", standing >= 3 ? 1L : 0L); // (saved before it existed: done if it stood at its last tier)
        linkedAltar = input.read("linkedAltar", BlockPos.CODEC).orElse(null);
    }
}
