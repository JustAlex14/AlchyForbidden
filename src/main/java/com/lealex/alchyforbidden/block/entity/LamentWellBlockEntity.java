package com.lealex.alchyforbidden.block.entity;

import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.essence.Essence;
import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Lament Well: a waist-high ring of stone round a one-block hole over its core, a rift open in the hole
 * (client/WellRifts); from the second tier posts, a beam and a soul lantern over it, from the third a roof.
 * It mourns: a mob that dies near it (ghostRange by tier) leaves a ghost weeping round it for ghostTicks, up to
 * maxGhosts by tier, each adding ghostSorrow by tier each second. Mob farms, a Killing Pit beside it, a fight round
 * it all feed it. From the second tier, offerings of loss thrown in its hole (or given to it) are taken for a burst:
 * a named thing, a tool or armour nearly broken, a music disc, a totem of undying. Sent to the nearest awake altar
 * in a teal stream every five seconds, kept here when none has room.
 */
public class LamentWellBlockEntity extends GrowingCoreBlockEntity implements net.minecraft.world.MenuProvider {
    public static final int DEPTH = 1; // the hole over the core
    private int buffer = 0;          // Sorrow no altar had room for
    private float grief = 0;         // made, not sent yet (a stream every few seconds, not every tick)
    private final java.util.ArrayDeque<Long> ghosts = new java.util.ArrayDeque<>(); // when each ghost fades
    private int ghostCount = 0;      // synced: how many weep round it (client: their souls)

    private static final java.util.Map<net.minecraft.world.level.Level, java.util.Set<LamentWellBlockEntity>> LOADED = new java.util.WeakHashMap<>();

    public static List<LamentWellBlockEntity> loaded(net.minecraft.world.level.Level level) {
        java.util.Set<LamentWellBlockEntity> set = LOADED.get(level);
        return set == null ? List.of() : new java.util.ArrayList<>(set);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) { // (both sides: the server's deaths, the client's rift in the well; per level)
            LOADED.computeIfAbsent(level, key -> java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>())).add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && LOADED.containsKey(level)) LOADED.get(level).remove(this);
    }

    public LamentWellBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.LAMENT_WELL_BE.get(), pos, state);
    }

    @Override
    protected String patternBase() {
        return "lament_well";
    }

    @Override
    protected List<Config.Cost> costFor(int tier) {
        return Config.wellCostFor(tier);
    }

    @Override
    protected String messagePrefix() {
        return "lament_well";
    }

    @Override
    protected String machineName() {
        return "Lament well";
    }

    @Override
    public net.minecraft.core.particles.ParticleOptions waveParticle() {
        return ParticleTypes.SOUL;
    }

    /** The rift opens over the well's mouth. */
    @Override
    protected BlockPos riftPos() {
        return worldPosition.above(DEPTH + 4);
    }

    @Override
    protected int riftSize() {
        return 3;
    }

    /** The shaft over the core. */
    public AABB shaft() {
        return new AABB(worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(),
                worldPosition.getX() + 1, worldPosition.getY() + 1 + DEPTH, worldPosition.getZ() + 1);
    }

    /** Sorrow its ghosts make each second now (either side: the ghost count is synced). */
    public int ratePerSecond() {
        return ghostCount * Config.wellGhostSorrow(standing);
    }

    @Override
    protected void tickWorking() {
        if (!(level instanceof ServerLevel server) || !isFormed()) return;
        long now = level.getGameTime();
        if (now % 20 == 0) {
            // ghosts weeping round it, until they fade
            while (!ghosts.isEmpty() && ghosts.peekFirst() <= now) ghosts.pollFirst();
            if (ghosts.size() != ghostCount) {
                ghostCount = ghosts.size();
                syncToClients();
            }
            grief += ghosts.size() * Config.wellGhostSorrow(standing);
            if (now % 200 == 0 && !ghosts.isEmpty()) playSound(worldPosition.above(DEPTH), SoundEvents.SOUL_ESCAPE.value(), 0.5f, 0.5f);
        }
        if (standing >= 2 && now % 10 == 0) { // offerings thrown down the shaft
            for (ItemEntity item : server.getEntitiesOfClass(ItemEntity.class, shaft().inflate(0.3), ItemEntity::isAlive)) {
                int each = offering(item.getItem());
                if (each <= 0) continue;
                grief += each * item.getItem().getCount();
                Vec3 at = item.position();
                server.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.2, at.z, 8, 0.2, 0.2, 0.2, 0.02);
                playSound(BlockPos.containing(at), SoundEvents.SOUL_ESCAPE.value(), 0.8f, 0.7f);
                item.discard();
            }
        }
        if (now % 100 == 0 && grief + buffer >= 1) send();
    }

    /** What an offering of loss is worth (0: not one). Only from the second tier. */
    public int offering(ItemStack stack) {
        if (standing < 2 || stack.isEmpty()) return 0;
        if (stack.is(Items.TOTEM_OF_UNDYING)) return Config.wellTotem();
        if (stack.has(DataComponents.JUKEBOX_PLAYABLE)) return Config.wellDisc();
        if (stack.has(DataComponents.CUSTOM_NAME)) return Config.wellNamed();
        if (stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() * 0.9F) return Config.wellBroken();
        return 0;
    }

    /** Given to the core by hand: an offering of loss, all of the stack. */
    public boolean offerLoss(Player player, ItemStack stack) {
        int each = offering(stack);
        if (each <= 0) return false;
        grief += each * stack.getCount();
        if (!player.getAbilities().instabuild) stack.setCount(0);
        if (level instanceof ServerLevel server) {
            Vec3 at = Vec3.atCenterOf(worldPosition.above());
            server.sendParticles(ParticleTypes.SOUL, at.x, at.y, at.z, 12, 0.2, 0.4, 0.2, 0.03);
        }
        playSound(worldPosition, SoundEvents.SOUL_ESCAPE.value(), 0.8f, 0.7f);
        send();
        return true;
    }

    /** A mob died near it: its ghost weeps round the well for a while. */
    public void mourn(LivingEntity dead) {
        if (!isFormed() || level == null || ghosts.size() >= Config.wellMaxGhosts(standing)) return;
        int range = Config.wellGhostRange(standing);
        if (dead.distanceToSqr(Vec3.atCenterOf(worldPosition.above(DEPTH))) > (double) range * range) return;
        ghosts.addLast(level.getGameTime() + Config.wellGhostTicks());
        ghostCount = ghosts.size();
        if (level instanceof ServerLevel server) {
            Vec3 at = dead.position();
            server.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.8, at.z, 10, 0.3, 0.5, 0.3, 0.02);
            playSound(dead.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), 0.6f, 0.6f);
        }
        setChanged();
        syncToClients();
    }

    private void send() {
        int whole = (int) grief;
        grief -= whole;
        int left = sendEssence(Essence.SORROW, buffer + whole, worldPosition.above(DEPTH), Config.wellReach());
        buffer = Math.min(Config.wellBuffer(), left);
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
        return Component.translatable("block.alchyforbidden.lament_well");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, Player player) {
        return new com.lealex.alchyforbidden.menu.SourceMenu(id, inventory, this);
    }

    public int buffer() {
        return buffer;
    }

    public int ghostCount() {
        return ghostCount;
    }

    /** Client: souls rising out of the hole, motes from the rift in it, each ghost on its slow round over the ring. */
    @Override
    protected void clientWorking(RandomSource random, long elapsed, double cx, double cy, double cz) {
        if (level == null || !isFormed()) return;
        double x = worldPosition.getX() + 0.5, z = worldPosition.getZ() + 0.5, hole = worldPosition.getY() + 1.2;
        if (random.nextInt(6) == 0) level.addParticle(ModRegistries.RIFT_MOTE.get(), x + (random.nextDouble() - 0.5) * 0.6, hole, z + (random.nextDouble() - 0.5) * 0.6, 0, 0.03, 0);
        if (random.nextInt(8) == 0) level.addParticle(ParticleTypes.SOUL, x, hole + 0.2, z, 0, 0.04, 0);
        for (int g = 0; g < ghostCount; g++) {
            double angle = elapsed * 0.04 + g * Math.PI * 2 / Math.max(1, ghostCount);
            double r = 2.0 + 0.4 * Math.sin(elapsed * 0.03 + g);
            if (random.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.SOUL, x + Math.cos(angle) * r, hole + 0.8 + Math.sin(elapsed * 0.08 + g) * 0.4,
                        z + Math.sin(angle) * r, 0, 0.005, 0);
            }
            if (random.nextInt(10) == 0) {
                level.addParticle(new DustParticleOptions(0x3FC6D0, 0.8F), x + Math.cos(angle) * r, hole + 0.6, z + Math.sin(angle) * r, 0, -0.02, 0);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("sorrow", buffer);
        output.putFloat("grief", grief);
        output.putInt("ghosts", ghostCount);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        buffer = input.getIntOr("sorrow", 0);
        grief = input.getFloatOr("grief", 0);
        ghostCount = input.getIntOr("ghosts", 0);
    }
}
