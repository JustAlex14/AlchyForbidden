package com.lealex.alchyforbidden.block.entity;

import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.essence.Essence;
import com.lealex.alchyforbidden.essence.Inscriptions;
import com.lealex.alchyforbidden.essence.Spell;
import com.lealex.alchyforbidden.essence.Spells;
import com.lealex.alchyforbidden.item.SpellRingItem;
import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
 * The Binding Circle: a ring of floor round its core, standing stones from the second tier, lamp pillars from the
 * third. It imbues spells into rings (item/SpellRingItem, essence/Spells): each tier opens its spells and lets the
 * player wear one more ring (2 + tier); imbuing costs the spell's items (from the inventory, creative free) and
 * essences, drawn from the circle's own store (fork links), then the body (carved reserve, vessels), then awake
 * altars within reach, a stream from each place that gave. Unbinding is a ritual: it hurts and costs Sorrow, and the
 * spell leaves the ring. Its Rings tab is where the rings are worn, ordered and given gems (menu/SourceMenu).
 */
public class SoulCircleBlockEntity extends GrowingCoreBlockEntity implements net.minecraft.world.MenuProvider,
        com.lealex.alchyforbidden.essence.EssenceReceiver {
    /** What the unbinding ritual costs. */
    public static final int UNBIND_SORROW = 40;
    private final int[] held = new int[Essence.values().length]; // synced
    private long ritualAt = -1000L;                             // synced: the last imbuing / unbinding's start
    private ItemStack ritualRing = ItemStack.EMPTY;             // synced: the ring it was done to (drawn rising over the circle)
    private int ritualColor = 0xB98CFF;                         // synced: the spell's colour
    private boolean ritualTearing;                              // synced: an unbinding
    /** How long the imbuing ritual is seen over the circle. */
    public static final int RITUAL_TICKS = 60;
    private static final java.util.Map<net.minecraft.world.level.Level, java.util.Set<SoulCircleBlockEntity>> LOADED = new java.util.WeakHashMap<>();

    public static List<SoulCircleBlockEntity> loaded(net.minecraft.world.level.Level level) {
        java.util.Set<SoulCircleBlockEntity> set = LOADED.get(level);
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

    public long ritualAt() {
        return ritualAt;
    }

    public ItemStack ritualRing() {
        return ritualRing;
    }

    public int ritualColor() {
        return ritualColor;
    }

    public boolean ritualTearing() {
        return ritualTearing;
    }

    /** Where the ring is t ticks into the ritual: it rises out of the circle, hangs turning, then flies up and fades. */
    public net.minecraft.world.phys.Vec3 ritualRingPos(float t) {
        double rise = t < 20 ? 1 - Math.pow(1 - t / 20.0, 3) : 1;
        double y = worldPosition.getY() + 1.1 + 1.4 * rise + 0.08 * Math.sin(t * 0.25);
        if (t > 48) y += Math.pow((t - 48) / 12.0, 2) * 1.5;
        return new net.minecraft.world.phys.Vec3(worldPosition.getX() + 0.5, y, worldPosition.getZ() + 0.5);
    }

    /** Its size: grows in, full, shrinks away at the end. */
    public float ritualRingScale(float t) {
        if (t < 8) return t / 8F;
        if (t > 50) return Math.max(0, 1 - (t - 50) / 10F);
        return 1F;
    }

    public SoulCircleBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.SOUL_CIRCLE_BE.get(), pos, state);
    }

    @Override
    protected String patternBase() {
        return "binding_circle";
    }

    @Override
    protected List<Config.Cost> costFor(int tier) {
        return Config.circleCostFor(tier);
    }

    @Override
    protected String messagePrefix() {
        return "binding_circle";
    }

    @Override
    protected String machineName() {
        return "Binding circle";
    }

    @Override
    public net.minecraft.core.particles.ParticleOptions waveParticle() {
        return ModRegistries.RIFT_MOTE.get();
    }

    @Override
    protected int riftSize() {
        return 3;
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
        return Math.max(0, Config.circleStore() - held[essence.ordinal()]);
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

    // ---- Binding ----

    private List<AltarBlockEntity> altars() {
        List<AltarBlockEntity> out = new ArrayList<>();
        if (level == null) return out;
        double reach = (double) Config.circleReach() * Config.circleReach();
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

    /** Takes that much: its store, then the body, then the altars (streams from each that gave). */
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

    /** Why this player cannot imbue this spell into that ring here now, or null. */
    public @Nullable Component refusal(Player player, Spell spell, ItemStack ring) {
        if (!isFormed()) return Component.translatable("binding_circle.alchyforbidden.unformed");
        if (spell.tier() > standing) return Component.translatable("binding_circle.alchyforbidden.tier", spell.tier());
        if (!(ring.getItem() instanceof SpellRingItem item)) return Component.translatable("binding_circle.alchyforbidden.no_ring");
        if (SpellRingItem.spell(ring) != null) return Component.translatable("binding_circle.alchyforbidden.ring_full");
        if (item.tier() < spell.tier()) return Component.translatable("binding_circle.alchyforbidden.ring_tier", spell.tier());
        if (!player.getAbilities().instabuild) {
            for (Spell.Cost cost : spell.bindItems()) {
                if (count(player, cost.item()) < cost.count()) return Component.translatable("binding_circle.alchyforbidden.items");
            }
        }
        for (Essence essence : Essence.values()) {
            int need = spell.bindEssence(essence);
            if (need > 0 && !player.getAbilities().instabuild && available(player, essence) < need) {
                return Component.translatable("binding_circle.alchyforbidden.thirsty", need, essence.icon(), Config.circleReach());
            }
        }
        return null;
    }

    public static int count(Player player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    /** The screen's Imbue: pays, and the spell goes into the ring. */
    public void imbue(ServerPlayer player, Spell spell, ItemStack ring) {
        Component refusal = refusal(player, spell, ring);
        if (refusal != null) {
            player.sendSystemMessage(refusal.copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!player.getAbilities().instabuild) {
            for (Spell.Cost cost : spell.bindItems()) {
                int left = cost.count();
                for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (!stack.is(cost.item())) continue;
                    int taken = Math.min(left, stack.getCount());
                    stack.shrink(taken);
                    left -= taken;
                }
            }
            for (Essence essence : Essence.values()) {
                if (spell.bindEssence(essence) > 0) draw(player, essence, spell.bindEssence(essence));
            }
        }
        SpellRingItem.setContents(ring, SpellRingItem.contents(ring).withSpell(spell.id()));
        Spells.raiseSlots(player, standing);
        chant(player, false, ring.copyWithCount(1), spell.color());
        player.sendSystemMessage(Component.translatable("binding_circle.alchyforbidden.bound", spell.displayName()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** The screen's Unbind: the ritual (it hurts, it costs Sorrow), the spell leaves the ring (its gems stay). */
    public void unbind(ServerPlayer player, ItemStack ring) {
        Spell spell = SpellRingItem.spell(ring);
        if (spell == null || !isFormed()) return;
        if (!player.getAbilities().instabuild) {
            if (available(player, Essence.SORROW) < UNBIND_SORROW) {
                player.sendSystemMessage(Component.translatable("binding_circle.alchyforbidden.thirsty", UNBIND_SORROW, Essence.SORROW.icon(),
                        Config.circleReach()).withStyle(ChatFormatting.RED), true);
                return;
            }
            draw(player, Essence.SORROW, UNBIND_SORROW);
            if (player.getHealth() > 4) player.hurtServer(player.level(), player.damageSources().magic(), 4.0F);
        }
        SpellRingItem.setContents(ring, SpellRingItem.contents(ring).withSpell(""));
        chant(player, true, ring.copyWithCount(1), spell.color());
        player.sendSystemMessage(Component.translatable("binding_circle.alchyforbidden.unbound", spell.displayName()), true);
    }

    /** The player walks to the circle's edge and chants; the ritual plays there, then the screen opens again. */
    private void chant(ServerPlayer player, boolean tearing, ItemStack ring, int color) {
        net.minecraft.world.phys.Vec3 spot = com.lealex.alchyforbidden.essence.Chants.spotToward(player, worldPosition, 2.7, worldPosition.getY() + 1);
        com.lealex.alchyforbidden.essence.Chants.begin(player, worldPosition, spot, RITUAL_TICKS,
                () -> ritual(player, tearing, ring, color), () -> { if (!isRemoved()) openGui(player); });
    }

    /** The circle flares: souls rise off its ring and into the body (bound) or out of it (unbound). */
    private void ritual(ServerPlayer player, boolean tearing, ItemStack ring, int color) {
        if (!(level instanceof ServerLevel server)) return;
        ritualAt = level.getGameTime();
        ritualRing = ring.copyWithCount(1);
        ritualColor = color;
        ritualTearing = tearing;
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true); // the casting hand
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI * 2 / 32;
            server.sendParticles(tearing ? ParticleTypes.SOUL_FIRE_FLAME : ModRegistries.RIFT_MOTE.get(), worldPosition.getX() + 0.5 + Math.cos(a) * 2.2,
                    worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5 + Math.sin(a) * 2.2, 1, 0, 0.06, 0, 0.01);
        }
        server.sendParticles(tearing ? ParticleTypes.SOUL : ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(), 16, 0.3, 0.6, 0.3, 0.03);
        playSound(worldPosition, tearing ? SoundEvents.SOUL_ESCAPE.value() : SoundEvents.SCULK_CATALYST_BLOOM, 1.2f, tearing ? 0.6f : 0.8f);
        playSound(worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.6f);
        setChanged();
        syncToClients();
    }

    // ---- Client: the circle breathing ----

    @Override
    protected void clientWorking(RandomSource random, long elapsed, double cx, double cy, double cz) {
        if (level == null || !isFormed()) return;
        if (random.nextInt(5) == 0 && com.lealex.alchyx.AlchyXClientConfig.spawn(random)) {
            double a = random.nextDouble() * Math.PI * 2, r = 1.5 + standing * 0.6;
            level.addParticle(ParticleTypes.ENCHANT, cx + Math.cos(a) * r, worldPosition.getY() + 0.6, cz + Math.sin(a) * r, -Math.cos(a) * 0.6, 0.5, -Math.sin(a) * 0.6);
        }
        long t = level.getGameTime() - ritualAt;
        if (t >= 0 && t < RITUAL_TICKS) ritualParticles(random, t);
    }

    /** The ritual seen round the ring: streams of the spell's colour from the four stands spiralling into it, then a flash. */
    private void ritualParticles(RandomSource random, long t) {
        if (level == null) return;
        net.minecraft.world.phys.Vec3 ring = ritualRingPos(t);
        var dust = new net.minecraft.core.particles.DustParticleOptions(ritualColor, 1.1F);
        if (t >= 8 && t < 48) {
            for (int k = 0; k < 4; k++) { // from the stands (middle of each side of the 5 x 5), winding in
                if (!com.lealex.alchyx.AlchyXClientConfig.spawn(random)) continue;
                double a = k * Math.PI / 2 + t * 0.15 * (ritualTearing ? -1 : 1);
                double f = (t % 10) / 10.0, r = 2.0 * (1 - f);
                double x = worldPosition.getX() + 0.5 + Math.cos(a) * r, z = worldPosition.getZ() + 0.5 + Math.sin(a) * r;
                double y = worldPosition.getY() + 1.4 + (ring.y - worldPosition.getY() - 1.4) * f;
                level.addParticle(dust, x, y, z, 0, 0, 0);
                if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.ENCHANT, x, y + 0.4, z, (ring.x - x) * 0.5, 0.2, (ring.z - z) * 0.5);
            }
            if (random.nextInt(2) == 0) {
                level.addParticle(ritualTearing ? ParticleTypes.SOUL_FIRE_FLAME : ModRegistries.RIFT_MOTE.get(), ring.x + (random.nextDouble() - 0.5) * 0.5,
                        ring.y + (random.nextDouble() - 0.5) * 0.5, ring.z + (random.nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
            }
        }
        if (t == 48) { // the spell sets: a flash round the ring
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24;
                level.addParticle(dust, ring.x, ring.y, ring.z, Math.cos(a) * 0.25, (random.nextDouble() - 0.5) * 0.1, Math.sin(a) * 0.25);
            }
            level.addParticle(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | ritualColor), ring.x, ring.y, ring.z, 0, 0, 0);
            level.addParticle(ritualTearing ? ParticleTypes.SOUL : ParticleTypes.END_ROD, ring.x, ring.y, ring.z, 0, 0.1, 0);
            level.playLocalSound(ring.x, ring.y, ring.z, ritualTearing ? SoundEvents.SOUL_ESCAPE.value() : SoundEvents.AMETHYST_BLOCK_CHIME,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1.2F, ritualTearing ? 0.5F : 1.4F, false);
        }
    }

    @Override
    public void lookMeters(java.util.List<LookMeter> out) {
        for (Essence e : Essence.values()) out.add(new LookMeter(held(e), Config.circleStore(), 0xFF000000 | e.color(), e.icon()));
    }

    // ---- Screen, saving ----

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (isMoving()) return;
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) com.lealex.alchyforbidden.essence.Relays.unlinkTo(server, pos, false);
    }

    @Override
    public void openGui(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        tryGrowing();
        if (isFormed()) Spells.raiseSlots(serverPlayer, standing);
        serverPlayer.openMenu(this, out -> out.writeBlockPos(worldPosition));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.alchyforbidden.binding_circle");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, Player player) {
        return new com.lealex.alchyforbidden.menu.SourceMenu(id, inventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putIntArray("held", held.clone());
        output.putLong("ritualAt", ritualAt);
        output.store("ritualRing", ItemStack.OPTIONAL_CODEC, ritualRing);
        output.putInt("ritualColor", ritualColor);
        output.putBoolean("ritualTearing", ritualTearing);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int[] kept = input.getIntArray("held").orElse(new int[0]);
        java.util.Arrays.fill(held, 0);
        for (int i = 0; i < held.length && i < kept.length; i++) held[i] = kept[i];
        ritualAt = input.getLongOr("ritualAt", -1000L);
        ritualRing = input.read("ritualRing", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        ritualColor = input.getIntOr("ritualColor", 0xB98CFF);
        ritualTearing = input.getBooleanOr("ritualTearing", false);
    }
}
