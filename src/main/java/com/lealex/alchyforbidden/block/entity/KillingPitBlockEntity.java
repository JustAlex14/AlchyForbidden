package com.lealex.alchyforbidden.block.entity;

import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.essence.Essence;
import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Killing Pit: a 3 x 3 pit two deep (three from the second tier), its floor spiked, the core under its middle.
 * Hostile mobs in it are hurt each second (config damage per tier) and give Wrath each second they suffer: base x
 * (max health / 20) x (1 + seconds suffered / tortureSeconds), up to max health x wrathCapPerHealth (bigger mobs give
 * more and go higher; one that heals and lives on climbs to its ceiling);
 * one that dies in it gives a last burst (as a kill gives a vessel, times the tier's percent). Sent to the nearest
 * awake altar in a crimson stream every five seconds, kept here when none has room. What dies in it drops nothing on
 * the ground: its drops go into the pit's nine-slot store (hoppers and pipes take them, or its screen), what does not
 * fit is voided, and everything is while its screen's Void is on; its experience is kept for the screen to give.
 * Tier 2: at night the rift calls hostile mobs from round it and drops them in. Tier 3: the rift heals a mob back to
 * full before it dies (under healBelow percent), each healing paid with Sculk Corruption from the nearest altar: it
 * suffers on, and its Wrath keeps growing.
 */
public class KillingPitBlockEntity extends GrowingCoreBlockEntity implements net.minecraft.world.MenuProvider {
    private int buffer = 0;          // Wrath no altar had room for
    private float rage = 0;          // made by the suffering, not sent yet (a stream every five seconds)
    private int ratePerSecond = 0;   // synced: what the suffering in it makes each second now (the screen)
    private boolean healing = true;  // tier 3: the rift heals what suffers in it (its screen turns it off and on)
    private boolean voidDrops = false; // the rift takes every drop; off: they go into its store (and what does not fit is taken)
    private int experience = 0;      // the experience of what died in it, kept for its screen to give
    private final net.minecraft.world.SimpleContainer drops = new net.minecraft.world.SimpleContainer(9) {
        @Override
        public void setChanged() {
            super.setChanged();
            KillingPitBlockEntity.this.setChanged();
        }
    };
    private final net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> dropsOut = net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(drops);
    private final net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> automation = new TakeOnly();
    /** How many seconds each mob in it has suffered (by its id): its Wrath each second grows with that. */
    private final java.util.Map<java.util.UUID, Integer> suffered = new java.util.HashMap<>();

    private static final java.util.Map<net.minecraft.world.level.Level, java.util.Set<KillingPitBlockEntity>> LOADED = new java.util.WeakHashMap<>();

    public static List<KillingPitBlockEntity> loaded(net.minecraft.world.level.Level level) {
        java.util.Set<KillingPitBlockEntity> set = LOADED.get(level);
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

    public KillingPitBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.KILLING_PIT_BE.get(), pos, state);
    }

    @Override
    protected String patternBase() {
        return "killing_pit";
    }

    @Override
    protected List<Config.Cost> costFor(int tier) {
        return Config.pitCostFor(tier);
    }

    @Override
    protected String messagePrefix() {
        return "killing_pit";
    }

    @Override
    protected String machineName() {
        return "Killing pit";
    }

    @Override
    public net.minecraft.core.particles.ParticleOptions waveParticle() {
        return ParticleTypes.CRIMSON_SPORE;
    }

    /** The rift over the pit. */
    @Override
    protected BlockPos riftPos() {
        return worldPosition.above(6);
    }

    @Override
    protected int riftSize() {
        return 3;
    }

    /** Inside the pit, where mobs are hurt and their deaths count: the 3 x 3 over the core, as deep as the tier. */
    public AABB inside() {
        int deep = standing >= 2 ? 3 : 2;
        return new AABB(worldPosition.getX() - 1, worldPosition.getY() + 1, worldPosition.getZ() - 1,
                worldPosition.getX() + 2, worldPosition.getY() + 1 + deep, worldPosition.getZ() + 2);
    }

    private List<Mob> hostilesInside() {
        return level == null ? List.of() : level.getEntitiesOfClass(Mob.class, inside().inflate(0.2), mob -> mob instanceof Enemy && mob.isAlive());
    }

    @Override
    protected void tickWorking() {
        if (!(level instanceof ServerLevel server) || !isFormed()) return;
        long now = level.getGameTime();
        if (now % 20 == 0) { // the spikes, and the Wrath of what suffers on them
            List<Mob> inside = hostilesInside();
            java.util.Set<java.util.UUID> here = new java.util.HashSet<>();
            float second = 0;
            int base = Config.pitWrathPerSecond(standing), every = Math.max(1, Config.pitTortureSeconds());
            double capPerHealth = Config.pitCapPerHealth();
            for (Mob mob : inside) {
                int seconds = suffered.merge(mob.getUUID(), 1, Integer::sum);
                here.add(mob.getUUID());
                // bigger mobs give more (in step with their max health), more the longer they suffer, up to a ceiling
                // set by their health too
                float health = mob.getMaxHealth();
                int stacked = com.lealex.alchyforbidden.essence.MobStacks.count(mob); // a condensed mob suffers for all it holds
                float rate = base * (health / 20F) * (1 + seconds / (float) every);
                second += (float) Math.min(rate, health * capPerHealth) * stacked;
                mob.hurtServer(server, server.damageSources().cactus(), Config.pitDamage(standing));
            }
            suffered.keySet().retainAll(here);
            rage += second;
            if (Math.round(second) != ratePerSecond) {
                ratePerSecond = Math.round(second);
                syncToClients();
            }
            if (now % 100 == 0 && (rage >= 1 || buffer > 0)) {
                int whole = (int) rage;
                rage -= whole;
                send(whole);
            }
        }
        // Tier 2: at night the rift calls hostile mobs from round it
        if (standing >= 2 && now % Math.max(20, Config.pitLureTicks()) == 0 && server.isDarkOutside()) lure(server);
        // Tier 3: it heals them before they die, paid with Corruption, so they suffer on (and their Wrath grows)
        if (standing >= 3 && healing && now % 20 == 10) mend(server);
    }

    private void lure(ServerLevel server) {
        int room = Config.pitMaxMobs() - hostilesInside().size();
        if (room <= 0) return;
        AABB around = new AABB(worldPosition).inflate(Config.pitLureRange());
        AABB in = inside();
        List<Mob> called = server.getEntitiesOfClass(Mob.class, around, mob -> mob instanceof Enemy && mob.isAlive()
                && !in.contains(mob.position()) && !mob.getType().builtInRegistryHolder().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES) && !mob.isPersistenceRequired());
        int count = Math.min(room, Config.pitLureCount());
        for (int i = 0; i < called.size() && i < count; i++) {
            Mob mob = called.get(i);
            Vec3 from = mob.position();
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 20, 0.3, 0.6, 0.3, 0.05);
            Vec3 to = Vec3.atBottomCenterOf(worldPosition.above(3));
            mob.teleportTo(to.x, to.y, to.z);
            server.sendParticles(ParticleTypes.PORTAL, to.x, to.y + 1, to.z, 30, 0.4, 0.6, 0.4, 0.4);
            playSound(worldPosition.above(3), SoundEvents.ENDERMAN_TELEPORT, 0.5f, 0.5f);
        }
    }

    private void mend(ServerLevel server) {
        float below = Config.pitHealBelow() / 100F;
        int cost = Config.pitHealCost();
        for (Mob mob : hostilesInside()) {
            if (mob.getHealth() >= mob.getMaxHealth() * below) continue;
            if (cost > 0) {
                AltarBlockEntity altar = payer(cost);
                if (altar == null || !altar.spendEssence(Essence.CORRUPTION, cost)) return;
            }
            mob.setHealth(mob.getMaxHealth());
            Vec3 at = mob.position();
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.8, at.z, 14, 0.3, 0.5, 0.3, 0.03);
            server.sendParticles(new DustParticleOptions(0x7A3FD6, 1.2F), at.x, at.y + 1.2, at.z, 8, 0.3, 0.3, 0.3, 0.01);
            playSound(mob.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6f, 0.5f);
        }
    }

    /** The nearest awake altar within reach holding that much Corruption. */
    private AltarBlockEntity payer(int cost) {
        if (level == null) return null;
        AltarBlockEntity best = null;
        double bestDistance = (double) Config.pitReach() * Config.pitReach();
        for (AltarBlockEntity altar : AltarBlockEntity.loaded(level)) {
            if (!altar.isFormed() || !altar.hasEye() || altar.essence(Essence.CORRUPTION) < cost) continue;
            double distance = altar.getBlockPos().distSqr(worldPosition);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = altar;
            }
        }
        return best;
    }

    // ---- Deaths in it (AlchyForbidden's death and drops events) ----

    public boolean holds(LivingEntity dead) {
        return isFormed() && dead instanceof Enemy && inside().inflate(0.5).contains(dead.position());
    }

    /** A hostile mob died in the pit: its Wrath. */
    public void killed(LivingEntity dead) {
        int stacked = com.lealex.alchyforbidden.essence.MobStacks.count(dead);
        int wrath = Math.max(1, Math.round(dead.getMaxHealth() / 10F * Config.wrathPer10Health() * Config.pitWrathPercent(standing) / 100F)) * stacked;
        if (level instanceof ServerLevel server) {
            Vec3 at = dead.position();
            server.sendParticles(new DustParticleOptions(0xD0304A, 1.4F), at.x, at.y + 0.6, at.z, 16, 0.3, 0.4, 0.3, 0.02);
            server.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 4, 0.2, 0.1, 0.2, 0.01);
        }
        send(wrath);
        // its experience is kept here (whoever or whatever killed it), no orbs
        if (level instanceof ServerLevel server) {
            int xp = dead.getExperienceReward(server, dead.getLastHurtByMob()) * stacked;
            dead.skipDropExperience();
            if (xp > 0 && experience < Config.pitMaxXp()) {
                experience = Math.min(Config.pitMaxXp(), experience + xp);
                setChanged();
                syncToClients();
            }
        }
    }

    /**
     * What dies in the pit drops nothing on the ground: its drops go into the pit's store, and what does not fit (or
     * everything, while it voids them) the rift takes.
     */
    public void collectDrops(java.util.Collection<ItemEntity> items) {
        boolean voided = false;
        for (ItemEntity item : items) {
            if (voidDrops) {
                voided = true;
                continue;
            }
            if (!drops.addItem(item.getItem().copy()).isEmpty()) voided = true;
        }
        if (voided && !items.isEmpty() && level instanceof ServerLevel server) {
            Vec3 at = items.iterator().next().position();
            server.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 0.3, at.z, 8, 0.2, 0.2, 0.2, 0.02);
        }
        items.clear();
    }

    public boolean voidsDrops() {
        return voidDrops;
    }

    /** The screen's button: void every drop, or keep them again. */
    public void toggleVoid() {
        voidDrops = !voidDrops;
        setChanged();
        syncToClients();
        playSound(worldPosition, SoundEvents.PORTAL_TRIGGER, 0.3f, voidDrops ? 1.4f : 1.8f);
    }

    /** Its stored experience (synced). */
    public int experience() {
        return experience;
    }

    /** The screen's button: the experience it kept, to the player. */
    public void giveExperience(Player player) {
        if (experience <= 0) return;
        player.giveExperiencePoints(experience);
        experience = 0;
        setChanged();
        syncToClients();
        if (level != null) level.playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.9f);
    }

    public net.minecraft.world.SimpleContainer drops() {
        return drops;
    }

    @Override
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> getItemHandler() {
        return automation;
    }

    /** Hoppers and pipes take what it keeps; nothing goes in. */
    private class TakeOnly implements net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> {
        @Override public int size() { return dropsOut.size(); }
        @Override public net.neoforged.neoforge.transfer.item.ItemResource getResource(int index) { return dropsOut.getResource(index); }
        @Override public long getAmountAsLong(int index) { return dropsOut.getAmountAsLong(index); }
        @Override public long getCapacityAsLong(int index, net.neoforged.neoforge.transfer.item.ItemResource resource) { return dropsOut.getCapacityAsLong(index, resource); }
        @Override public boolean isValid(int index, net.neoforged.neoforge.transfer.item.ItemResource resource) { return false; }
        @Override public int insert(int index, net.neoforged.neoforge.transfer.item.ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) { return 0; }
        @Override public int extract(int index, net.neoforged.neoforge.transfer.item.ItemResource resource, int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            return dropsOut.extract(index, resource, amount, transaction);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (isMoving()) return;
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) {
            net.minecraft.world.Containers.dropContents(server, pos, drops);
            if (experience > 0) net.minecraft.world.entity.ExperienceOrb.award(server, Vec3.atCenterOf(pos), experience);
        }
    }

    private void send(int wrath) {
        int left = sendEssence(Essence.WRATH, buffer + wrath, worldPosition.above(2), Config.pitReach());
        buffer = Math.min(Config.pitBuffer(), left);
        setChanged();
    }

    @Override
    public void openGui(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        tryGrowing();
        serverPlayer.openMenu(this, out -> out.writeBlockPos(worldPosition));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.alchyforbidden.killing_pit");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, Player player) {
        return new com.lealex.alchyforbidden.menu.SourceMenu(id, inventory, this);
    }

    public int buffer() {
        return buffer;
    }

    /** Tier 3: whether the rift heals what suffers in it. Synced. */
    public boolean healing() {
        return healing;
    }

    /** The screen's button: healing off, or on again. */
    public void toggleHealing() {
        healing = !healing;
        setChanged();
        syncToClients();
        playSound(worldPosition, healing ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.AMETHYST_BLOCK_BREAK, 0.6f, healing ? 0.6f : 0.8f);
    }

    /** Wrath the suffering in it makes each second now. Synced. */
    public int ratePerSecond() {
        return ratePerSecond;
    }

    /** Hostile mobs in it now (either side). */
    public int hostileCount() {
        return hostilesInside().size();
    }




    /** Client: embers off the spikes, more with mobs in it. */
    @Override
    protected void clientWorking(RandomSource random, long elapsed, double cx, double cy, double cz) {
        if (level == null || !isFormed() || random.nextInt(3) != 0) return;
        double x = worldPosition.getX() - 1 + random.nextDouble() * 3, z = worldPosition.getZ() - 1 + random.nextDouble() * 3;
        level.addParticle(new DustParticleOptions(0xB01828, 0.9F), x, worldPosition.getY() + 1.2, z, 0, 0.03, 0);
        if (random.nextInt(8) == 0) level.addParticle(ParticleTypes.SMALL_FLAME, x, worldPosition.getY() + 1.1, z, 0, 0.02, 0);
    }


    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("wrath", buffer);
        output.putInt("rate", ratePerSecond);
        output.putBoolean("healing", healing);
        output.putBoolean("voidDrops", voidDrops);
        output.putInt("experience", experience);
        output.store("drops", net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.listOf(), drops.getItems());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        buffer = input.getIntOr("wrath", 0);
        ratePerSecond = input.getIntOr("rate", 0);
        healing = input.getBooleanOr("healing", true);
        voidDrops = input.getBooleanOr("voidDrops", false);
        experience = input.getIntOr("experience", 0);
        drops.clearContent();
        List<net.minecraft.world.item.ItemStack> kept = input.read("drops", net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < kept.size() && i < drops.getContainerSize(); i++) drops.getItems().set(i, kept.get(i));
    }
}
