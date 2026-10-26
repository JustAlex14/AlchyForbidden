package com.lealex.alchyforbidden.familiar;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jspecify.annotations.Nullable;

/**
 * Familiars: the creatures bound in familiar rings (item/FamiliarRingItem), worn on the left hand (ModRegistries
 * WORN_FAMILIARS, set in a Rift Maw's Rings tab; FAMILIAR_SLOTS of them). Their key calls every worn familiar out of a
 * small rift beside the player, or sends them all back. Out, a familiar is a real creature (familiar/RiftBeast) that is
 * never saved: it goes back to its ring when its owner leaves, changes world, dies, or the ring is taken off. Killing
 * gives it experience (levels: health and damage); falling cracks its ring, which mends over time or for Sorrow at a maw.
 */
@EventBusSubscriber(modid = AlchyForbidden.MODID)
public final class Familiars {
    private Familiars() {}

    /** Server: each player's familiars out, by ring slot. */
    private static final Map<UUID, Map<Integer, UUID>> OUT = new HashMap<>();

    // ---- The rings worn ----

    public static int slots(Player player) {
        // Every left-hand slot is open (the attachment is kept for old saves and later use).
        return com.lealex.alchyforbidden.menu.SourceMenu.FAM_MAX;
    }

    /** The worn familiar rings, one per slot (null for an empty one). */
    public static List<FamiliarData.@Nullable Worn> worn(Player player) {
        List<String> saved = player.getData(ModRegistries.WORN_FAMILIARS);
        List<FamiliarData.@Nullable Worn> out = new ArrayList<>();
        for (int i = 0; i < slots(player); i++) out.add(i < saved.size() ? FamiliarData.decode(saved.get(i)) : null);
        return out;
    }

    public static void setWorn(Player player, List<FamiliarData.@Nullable Worn> rings) {
        List<String> out = new ArrayList<>();
        for (FamiliarData.Worn ring : rings) out.add(ring == null ? "" : ring.data().encode(ring.tier()));
        player.setData(ModRegistries.WORN_FAMILIARS, List.copyOf(out));
    }

    private static void setSlot(Player player, int slot, FamiliarData data) {
        List<FamiliarData.@Nullable Worn> rings = worn(player);
        if (slot < 0 || slot >= rings.size() || rings.get(slot) == null) return;
        rings.set(slot, new FamiliarData.Worn(rings.get(slot).tier(), data));
        setWorn(player, rings);
    }

    public static FamiliarData.@Nullable Worn ofStack(ItemStack stack) {
        if (!(stack.getItem() instanceof com.lealex.alchyforbidden.item.FamiliarRingItem ring)) return null;
        FamiliarData data = com.lealex.alchyforbidden.item.FamiliarRingItem.data(stack);
        if (data == null) return null;
        var renamed = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME); // a ring renamed in an anvil names its familiar
        if (renamed != null) data = data.named(renamed.getString());
        return new FamiliarData.Worn(ring.tier(), data);
    }

    /** The owner named it (a name tag): into its ring, over its head. */
    public static void rename(ServerPlayer player, RiftBeast beast, String name) {
        FamiliarData.Worn ring = beast.slot() >= 0 && beast.slot() < worn(player).size() ? worn(player).get(beast.slot()) : null;
        if (ring == null) return;
        FamiliarData next = ring.data().named(name);
        setSlot(player, beast.slot(), next);
        beast.setCustomName(next.title());
        if (beast.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, beast.getX(), beast.getY() + beast.getBbHeight() + 0.3, beast.getZ(), 4, 0.3, 0.1, 0.3, 0);
        }
        player.sendSystemMessage(Component.translatable("familiar.alchyforbidden.named", next.title()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    public static ItemStack toStack(FamiliarData.Worn worn) {
        ItemStack stack = new ItemStack(ModRegistries.familiarRingOfTier(worn.tier()));
        com.lealex.alchyforbidden.item.FamiliarRingItem.setData(stack, worn.data());
        return stack;
    }

    /** The slots whose familiar is out (synced to the player: their screens show it). */
    public static List<Integer> outSlots(Player player) {
        return player.getData(ModRegistries.FAMILIARS_OUT);
    }

    private static void syncOut(ServerPlayer player) {
        Map<Integer, UUID> mine = OUT.get(player.getUUID());
        player.setData(ModRegistries.FAMILIARS_OUT, mine == null ? List.of() : List.copyOf(mine.keySet()));
    }

    // ---- Calling and sending back ----

    /** The key: everyone back if any is out, else everyone out. */
    public static void toggle(ServerPlayer player, int slot) {
        // one familiar out at a time: the key sends back the one out; on another slot it swaps them
        Map<Integer, UUID> mine = OUT.get(player.getUUID());
        boolean wasOut = mine != null && mine.containsKey(slot);
        if (mine != null && !mine.isEmpty()) recallAll(player);
        if (wasOut) return;
        List<FamiliarData.@Nullable Worn> rings = worn(player);
        if (rings.stream().allMatch(r -> r == null)) {
            player.sendSystemMessage(Component.translatable("familiar.alchyforbidden.none").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        FamiliarData.Worn ring = slot >= 0 && slot < rings.size() ? rings.get(slot) : null;
        if (ring == null) { // the slot picked is empty: the first ring worn
            for (int i = 0; i < rings.size() && ring == null; i++) if (rings.get(i) != null) { ring = rings.get(i); slot = i; }
        }
        if (ring == null) return;
        long now = player.level().getGameTime();
        if (ring.data().cracked(now)) {
            player.sendSystemMessage(Component.translatable("familiar.alchyforbidden.cracked", ring.data().title(),
                    (ring.data().crackedUntil() - now) / 20 + 1).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        call(player, slot, ring.data());
    }

    private static boolean call(ServerPlayer player, int slot, FamiliarData data) {
        RiftCreature creature = data.creatureOrNull();
        if (creature == null) return false;
        ServerLevel level = player.level();
        RiftBeast beast = creature.type().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (beast == null) return false;
        Vec3 at = besideOwner(player, slot);
        beast.snapTo(at.x, at.y, at.z, player.getYRot(), 0);
        beast.becomeFamiliar(player, slot, data);
        if (!level.addFreshEntity(beast)) return false;
        OUT.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(slot, beast.getUUID());
        syncOut(player);
        Vec3 mid = at.add(0, beast.getBbHeight() * 0.5, 0);
        riftPuff(level, mid, creature.color(), true);
        if (data.corruption() >= 2) { // the altered ones come through louder
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, mid.x, mid.y, mid.z, 30 * data.corruption(), 0.6, 0.6, 0.6, 0.15);
            level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(RiftCreature.gradeColor(data.corruption()), 1.6F), mid.x, mid.y, mid.z, 20, 0.7, 0.5, 0.7, 0);
        }
        if (data.corruption() >= 3) {
            level.sendParticles(ParticleTypes.SONIC_BOOM, mid.x, mid.y, mid.z, 1, 0, 0, 0, 0);
            level.playSound(null, BlockPos.containing(at), SoundEvents.WARDEN_EMERGE, SoundSource.NEUTRAL, 0.6F, 1.5F);
        }
        return true;
    }

    /** A free spot beside the player for a ring slot (behind them, spread out by slot; else round them), on the ground. */
    private static Vec3 besideOwner(Player player, int slot) {
        for (int attempt = 0; attempt < 12; attempt++) {
            double yaw = Math.toRadians(player.getYRot() + 180 + (slot - 1) * 50 + attempt * 30), r = attempt < 6 ? 2 : 3;
            Vec3 want = player.position().add(-Math.sin(yaw) * r, 0, Math.cos(yaw) * r);
            BlockPos pos = BlockPos.containing(want);
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos at = pos.above(dy);
                if (player.level().getBlockState(at).getCollisionShape(player.level(), at).isEmpty()
                        && player.level().getBlockState(at.above()).getCollisionShape(player.level(), at.above()).isEmpty()
                        && player.level().getBlockState(at.below()).isFaceSturdy(player.level(), at.below(), net.minecraft.core.Direction.UP)) {
                    return new Vec3(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
                }
            }
        }
        return player.position();
    }

    /** Left far behind: it slips through a small rift to its owner. */
    public static void slipTo(RiftBeast beast, Player owner) {
        if (!(beast.level() instanceof ServerLevel level)) return;
        riftPuff(level, beast.middle(), beast.creature().color(), false);
        Vec3 at = besideOwner(owner, beast.slot());
        beast.getNavigation().stop();
        beast.teleportTo(at.x, at.y, at.z);
        riftPuff(level, at.add(0, beast.getBbHeight() * 0.5, 0), beast.creature().color(), true);
    }

    public static void recallAll(ServerPlayer player) {
        Map<Integer, UUID> mine = OUT.remove(player.getUUID());
        if (mine != null) {
            for (UUID id : mine.values()) {
                for (ServerLevel level : player.level().getServer().getAllLevels()) {
                    Entity entity = level.getEntity(id);
                    if (entity instanceof RiftBeast beast) {
                        riftPuff(level, beast.middle(), beast.creature().color(), false);
                        beast.discard();
                    }
                }
            }
        }
        syncOut(player);
    }

    /** A familiar out, once a tick: gone with its owner, or when it is no longer counted as out. */
    static void tickFamiliar(ServerLevel level, RiftBeast beast) {
        UUID owner = beast.owner();
        ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        Map<Integer, UUID> mine = owner == null ? null : OUT.get(owner);
        boolean counted = mine != null && beast.getUUID().equals(mine.get(beast.slot()));
        if (player == null || !counted || player.level() != level || !player.isAlive()) {
            if (counted) {
                mine.remove(beast.slot());
                if (player != null) syncOut(player);
            }
            beast.discard();
        }
    }

    /** It fell: back to its ring, cracked for a while. */
    static void fell(RiftBeast beast) {
        if (!(beast.level() instanceof ServerLevel level) || beast.owner() == null) return;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(beast.owner());
        Map<Integer, UUID> mine = OUT.get(beast.owner());
        if (mine != null) mine.remove(beast.slot());
        if (player == null) return;
        FamiliarData.Worn ring = beast.slot() < worn(player).size() ? worn(player).get(beast.slot()) : null;
        if (ring != null) {
            setSlot(player, beast.slot(), ring.data().crackedTill(level.getGameTime() + Config.familiarMendSeconds() * 20L));
            player.sendSystemMessage(Component.translatable("familiar.alchyforbidden.fell", ring.data().title(),
                    Config.familiarMendSeconds()).withStyle(ChatFormatting.RED), true);
        }
        riftPuff(level, beast.middle(), beast.creature().color(), false);
        syncOut(player);
    }

    /** Writes the ring in this left-hand slot (the maw's rituals). */
    public static void setRing(Player player, int slot, FamiliarData data) {
        setSlot(player, slot, data);
    }

    /** Sorrow paid at a maw: the ring in this slot is whole again. */
    public static void mend(ServerPlayer player, int slot) {
        List<FamiliarData.@Nullable Worn> rings = worn(player);
        if (slot < 0 || slot >= rings.size() || rings.get(slot) == null) return;
        setSlot(player, slot, rings.get(slot).data().crackedTill(0L));
    }

    // ---- Events ----

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) return;
        if (dead instanceof ServerPlayer player) {
            recallAll(player);
            return;
        }
        if (!(dead.level() instanceof ServerLevel level)) return;
        int worth = Math.max(1, Math.round(dead.getMaxHealth() * Config.familiarXpPerHealth() / 100F));
        if (event.getSource().getEntity() instanceof RiftBeast beast && beast.familiar() && beast.owner() != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(beast.owner());
            if (player == null) return;
            beast.onKill(level, dead);
            beast.fedOnKill();
            giveXp(player, beast, worth);
        } else if (event.getSource().getEntity() instanceof ServerPlayer player && Config.familiarSharedXp() > 0) { // the owner's kill, shared
            Map<Integer, UUID> mine = OUT.get(player.getUUID());
            if (mine == null || mine.isEmpty()) return;
            int share = Math.max(1, Math.round(worth * Config.familiarSharedXp() / 100F));
            for (UUID id : List.copyOf(mine.values())) {
                if (level.getEntity(id) instanceof RiftBeast beast && beast.isAlive()) giveXp(player, beast, share);
            }
        }
    }

    /** Experience for the familiar out in its ring's slot: levels, stats, the milestone's trait told. */
    public static void giveXp(ServerPlayer player, RiftBeast beast, int gained) {
        FamiliarData.Worn ring = beast.slot() >= 0 && beast.slot() < worn(player).size() ? worn(player).get(beast.slot()) : null;
        if (ring == null || !(beast.level() instanceof ServerLevel level)) return;
        FamiliarData next = ring.data().withXp(gained);
        setSlot(player, beast.slot(), next);
        if (next.level() <= ring.data().level()) return;
        beast.levelUp(next);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, beast.getX(), beast.getY() + beast.getBbHeight(), beast.getZ(), 20, 0.3, 0.4, 0.3, 0.2);
        level.playSound(null, beast.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 0.7F);
        player.sendSystemMessage(Component.translatable("familiar.alchyforbidden.level", next.title(), next.level())
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        for (int lv = ring.data().level() + 1; lv <= next.level(); lv++) { // a milestone passed: what it learned, in chat (kept)
            RiftCreature.Trait trait = beast.creature().traitAt(lv);
            if (trait == null) continue;
            player.sendSystemMessage(Component.translatable("familiar.alchyforbidden.learned", next.title(),
                    beast.creature().traitName(trait).copy().withStyle(ChatFormatting.AQUA), beast.creature().traitDescription(trait)).withStyle(ChatFormatting.GRAY));
            level.sendParticles(ParticleTypes.SCULK_SOUL, beast.getX(), beast.getY() + beast.getBbHeight() * 0.5, beast.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
            level.playSound(null, beast.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
        }
        for (int lv = ring.data().level() + 1; lv <= next.level(); lv++) { // and the move it learned there
            RiftCreature.Move move = beast.creature().moveAt(lv);
            if (move == null) continue;
            player.sendSystemMessage(Component.translatable("familiar.alchyforbidden.learned_move", next.title(),
                    beast.creature().moveName(move).copy().withStyle(ChatFormatting.GOLD), beast.creature().moveDescription(move)).withStyle(ChatFormatting.GRAY));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) recallAll(player);
    }

    @SubscribeEvent
    public static void onChangeWorld(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) recallAll(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncOut(player); // nothing survives a relog
    }

    /** A small rift opening (or closing) where a familiar comes or goes. */
    public static void riftPuff(ServerLevel level, Vec3 at, int color, boolean opening) {
        com.lealex.alchyforbidden.network.SpellFxPayload.send(level, com.lealex.alchyforbidden.network.SpellFxPayload.RIFT, at, -1, 14, 1.1F);
        level.sendParticles(ModRegistries.RIFT_MOTE.get(), at.x, at.y, at.z, 16, 0.3, 0.4, 0.3, 0.03);
        level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(color, 1.2F), at.x, at.y, at.z, 12, 0.35, 0.45, 0.35, 0.0);
        level.playSound(null, BlockPos.containing(at), opening ? SoundEvents.ILLUSIONER_MIRROR_MOVE : SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.NEUTRAL, 0.7F, opening ? 0.8F : 1.4F);
    }
}
