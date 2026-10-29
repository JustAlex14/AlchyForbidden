package com.lealex.alchyforbidden.block.entity;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.lealex.alchyx.block.ChamberShellBlock;
import com.lealex.alchyx.block.entity.MultiblockCoreBlockEntity;
import com.lealex.alchyx.multiblock.CoreTracker;
import com.lealex.alchyx.multiblock.MultiblockPattern;
import com.lealex.alchyx.multiblock.ShellParts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The altar. The player builds its first tier by hand around the carved core; it forms into AlchyX shells like any
 * machine of the series. Every later tier is grown by the rift itself: the player gives the altar what the next tier
 * asks for (right-click on the core), then the altar gathers itself for a few seconds and grows the new blocks as
 * shells, a few per tick, with its own particles and sounds. The growth is loud (see {@link AltarNoise}).
 *
 * Each tier is its own pattern file (data/alchyforbidden/multiblock/altar_1..3.json), all the same size: a lower tier
 * leaves "any" where a higher one has blocks, so a tier is the one below plus what grows. {@link #patternId()} names
 * the tier the structure stands at, which AlchyX checks as usual.
 *
 * Between growths the altar does nothing of its own: it keeps its tier in its saved data, and its tick is AlchyX's
 * (a counter and a structure check every two seconds). If the structure is broken while the core stays, the tier is
 * kept: the player builds the first tier again and the altar grows the rest back by itself, for nothing.
 */
public class AltarBlockEntity extends GrowingCoreBlockEntity implements net.minecraft.world.MenuProvider, com.lealex.alchyforbidden.essence.EssenceReceiver {
    public static final int MAX_TIER = 3;
    private static final int RITE = OWN_PHASES;
    /** Stands the altar has: four at its first two tiers, eight once the outer ring stands. */
    public static final int MAX_STANDS = 8;
    /** The animation display (and pattern anchor) of the eye over the center. */
    public static final String EYE = "eye";
    // What lies on the stands (one item each), and the rites' results waiting to be taken
    private final net.minecraft.world.SimpleContainer stands = new net.minecraft.world.SimpleContainer(MAX_STANDS) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            standsChanged();
        }
    };
    private final net.minecraft.world.SimpleContainer output = new net.minecraft.world.SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            AltarBlockEntity.this.setChanged();
            syncToClients();
        }
    };
    private int riteTicks = 0;   // how long the rite under way lasts
    /** Stands filled by a hopper or a pipe: once they make a rite, it begins by itself (tickWorking). */
    private boolean autoFilled = false;
    // What hoppers and pipes reach through any part of the altar: the stands (in, one item each, only free ones that
    // have grown, never during a rite), then the result (out). See getItemHandler.
    private final net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> standsIn =
            net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(new StandsView());
    private final net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> resultOut =
            net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(output);
    private final net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> automation = new Automation();
    // The altar's reserve of each essence (Essence order), and what the rite under way spent (its look, its refund)
    private final int[] reserve = new int[3];
    private final int[] riteCost = new int[3];

    // The altars loaded on the server, per level (the essence events look among them): a handful
    private static final java.util.Map<net.minecraft.world.level.Level, java.util.Set<AltarBlockEntity>> LOADED = new java.util.WeakHashMap<>();

    public static List<AltarBlockEntity> loaded(net.minecraft.world.level.Level level) {
        java.util.Set<AltarBlockEntity> set = LOADED.get(level);
        return set == null ? List.of() : new ArrayList<>(set);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) { // (both sides: the server's essence events, the client's rite circles; per level)
            LOADED.computeIfAbsent(level, key -> java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>())).add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && LOADED.containsKey(level)) LOADED.get(level).remove(this);
    }

    // ---- The essences ----

    public int essence(com.lealex.alchyforbidden.essence.Essence essence) {
        return reserve[essence.ordinal()];
    }

    /** How much of each essence the altar holds at the tier it stands at. */
    public int reserveCap() {
        return Config.reserve(standing);
    }

    /** Takes from the reserve if it holds that much: true if it did (the Killing Pit's rift paying for a mob). */
    public boolean spendEssence(com.lealex.alchyforbidden.essence.Essence essence, int amount) {
        if (reserve[essence.ordinal()] < amount) return false;
        reserve[essence.ordinal()] -= amount;
        setChanged();
        syncToClients();
        return true;
    }

    /** Adds to the reserve (up to the cap); returns what it took. */
    @Override
    public int room(com.lealex.alchyforbidden.essence.Essence essence) {
        return Math.max(0, reserveCap() - reserve[essence.ordinal()]);
    }

    /** Streams land in its eye. */
    @Override
    public BlockPos intakePos() {
        return getBlockPos().above();
    }

    @Override
    public BlockPos receiverPos() {
        return getBlockPos();
    }

    @Override
    public int addEssence(com.lealex.alchyforbidden.essence.Essence essence, int amount) {
        int taken = Math.max(0, Math.min(amount, reserveCap() - reserve[essence.ordinal()]));
        if (taken > 0) {
            reserve[essence.ordinal()] += taken;
            setChanged();
            syncToClients();
        }
        return taken;
    }

    /**
     * The pour slot: a vessel empties into the reserve (as much as it has room for), sculk turns into Sculk
     * Corruption (config sculkOfferings: as many items as there is room for). Returns true if anything was taken.
     */
    public boolean pour(ItemStack stack) {
        if (level == null || stack.isEmpty() || !isFormed()) return false;
        if (stack.getItem() instanceof com.lealex.alchyforbidden.item.VesselItem) {
            com.lealex.alchyforbidden.essence.VesselContents held = com.lealex.alchyforbidden.item.VesselItem.contents(stack);
            if (held == null) return false;
            int room = reserveCap() - reserve[held.essence().ordinal()];
            int given = com.lealex.alchyforbidden.item.VesselItem.drain(stack, room);
            if (given <= 0) return false;
            addEssence(held.essence(), given);
            playSound(worldPosition, ModRegistries.ALTAR_OFFER.get(), 0.8f, held.essence() == com.lealex.alchyforbidden.essence.Essence.WRATH ? 0.7f : 1.2f);
            return true;
        }
        int each = Config.sculkOffering(stack.getItem());
        if (each <= 0) return false;
        int room = reserveCap() - reserve[com.lealex.alchyforbidden.essence.Essence.CORRUPTION.ordinal()];
        int items = Math.min(stack.getCount(), room / each);
        if (items <= 0) return false;
        addEssence(com.lealex.alchyforbidden.essence.Essence.CORRUPTION, items * each);
        stack.shrink(items);
        playSound(worldPosition, net.minecraft.sounds.SoundEvents.SCULK_CATALYST_BLOOM, 1.0f, 0.8f);
        return true;
    }

    /** What the rite under way spent of each essence (client: its colours). */
    public int[] riteCost() {
        return riteCost.clone();
    }

    public AltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.ALTAR_BE.get(), pos, state);
    }

    // ---- The stands and the rites ----

    public net.minecraft.world.SimpleContainer stands() {
        return stands;
    }

    public net.minecraft.world.SimpleContainer output() {
        return output;
    }

    /** How many stands the altar has now (none until it is formed). */
    public int standCount() {
        if (!isFormed()) return 0;
        return standing >= 3 ? MAX_STANDS : 4;
    }

    public boolean isRiting() {
        return phase == RITE;
    }

    /** How far the rite under way is, 0 to 1 (client: from the synced start). */
    public float riteProgress() {
        if (phase != RITE || level == null || riteTicks <= 0) return 0;
        return Mth.clamp((level.getGameTime() - phaseStart) / (float) riteTicks, 0f, 1f);
    }

    /** The items on the stands the altar has, one each. */
    public List<ItemStack> standItems() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < standCount(); i++) {
            if (!stands.getItem(i).isEmpty()) items.add(stands.getItem(i));
        }
        return items;
    }

    private void standsChanged() {
        setChanged();
        for (int i = 0; i < MAX_STANDS; i++) setAnimationDisplay("slot_" + i, stands.getItem(i));
        if (phase == RITE && level != null && !level.isClientSide()) {
            com.lealex.alchyforbidden.rite.AltarRite rite = com.lealex.alchyforbidden.rite.AltarRites.match(standItems());
            if (rite == null || !ItemStack.isSameItemSameComponents(rite.result(), getAnimationDisplay("result"))) {
                // something was taken off a stand: the rite breaks
                setAnimationDisplay("result", ItemStack.EMPTY);
                setAnimationWorking(false);
                setPhase(IDLE);
                for (int i = 0; i < 3; i++) { // what it spent comes back
                    reserve[i] = Math.min(reserveCap(), reserve[i] + riteCost[i]);
                    riteCost[i] = 0;
                }
                if (level instanceof ServerLevel server) com.lealex.alchyforbidden.network.AltarRiftPayload.close(server, riftPos());
                playSound(worldPosition, ModRegistries.EYE_WHISPER.get(), 1.0f, 0.5f);
                say(Component.translatable("altar.alchyforbidden.rite_broken"));
            }
        }
        syncToClients();
    }

    /** The stand (0-7) at this place of the structure, or -1. Stands are the pattern anchors "stand_0" and on. */
    public int standAt(BlockPos pos) {
        MultiblockPattern pattern = pattern();
        if (pattern == null) return -1;
        for (int i = 0; i < standCount(); i++) {
            BlockPos at = pattern.anchor(worldPosition, rotation(), "stand_" + i);
            if (pos.equals(at)) return i;
        }
        return -1;
    }

    /** A right-click on a stand: lays one of the held item on it, or gives back what lies there. */
    public void useStand(Player player, net.minecraft.world.InteractionHand hand, int index) {
        if (index < 0 || index >= standCount() || isRiting()) return;
        ItemStack held = player.getItemInHand(hand);
        ItemStack lying = stands.getItem(index);
        if (!lying.isEmpty()) {
            stands.setItem(index, ItemStack.EMPTY);
            if (!player.getInventory().add(lying)) player.drop(lying, false);
            playSound(worldPosition, net.minecraft.sounds.SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.6f, 0.8f);
        } else if (!held.isEmpty()) {
            stands.setItem(index, held.copyWithCount(1));
            if (!player.getAbilities().instabuild) held.shrink(1);
            playSound(worldPosition, net.minecraft.sounds.SoundEvents.ITEM_FRAME_ADD_ITEM, 0.6f, 0.8f);
        }
    }

    /** What the items on the stands would make, or empty (client: from the server's list of rites). */
    public ItemStack preview() {
        List<ItemStack> items = standItems();
        com.lealex.alchyforbidden.rite.AltarRite rite = level != null && level.isClientSide()
                ? com.lealex.alchyforbidden.rite.AltarRites.clientMatch(items) : com.lealex.alchyforbidden.rite.AltarRites.match(items);
        return rite == null ? ItemStack.EMPTY : rite.result();
    }

    /** Begins the rite the stands make (the screen's button). Says why not when it cannot. */
    public void beginRite(Player player) {
        if (player instanceof ServerPlayer serverPlayer && tryBeginRite(player)) {
            // the player walks out to the altar's foot and chants while it runs; the screen opens again after
            double distance = standing >= 3 ? 6.5 : standing == 2 ? 4.5 : 3.0;
            net.minecraft.world.phys.Vec3 spot = com.lealex.alchyforbidden.essence.Chants.spotToward(player, worldPosition, distance,
                    worldPosition.getY() - standing + 1);
            com.lealex.alchyforbidden.essence.Chants.begin(serverPlayer, worldPosition, spot, Math.max(20, riteTicks),
                    () -> {}, () -> { if (!isRemoved()) openGui(serverPlayer); });
        }
    }

    /** Begins the rite the stands make, if it can (the player, if any, is told why not). True if it began. */
    private boolean tryBeginRite(@Nullable Player player) {
        if (level == null) return false;
        com.lealex.alchyforbidden.rite.AltarRite rite = com.lealex.alchyforbidden.rite.AltarRites.match(standItems());
        Component problem = null;
        if (!isFormed()) problem = Component.translatable("altar.alchyforbidden.unformed", String.valueOf(getStructureProblem()));
        else if (phase != IDLE) problem = Component.translatable("altar.alchyforbidden.busy");
        else if (!hasEye()) problem = Component.translatable("altar.alchyforbidden.no_eye");
        else if (rite == null) problem = Component.translatable("altar.alchyforbidden.no_rite");
        else if (rite.tier() > standing) problem = Component.translatable("altar.alchyforbidden.rite_tier", rite.tier());
        else {
            for (com.lealex.alchyforbidden.essence.Essence essence : com.lealex.alchyforbidden.essence.Essence.values()) {
                int need = rite.costs()[essence.ordinal()];
                if (reserve[essence.ordinal()] < need) {
                    problem = Component.translatable("altar.alchyforbidden.rite_essence", need - reserve[essence.ordinal()], essence.icon());
                    break;
                }
            }
        }
        if (problem == null && !output.getItem(0).isEmpty() && (!ItemStack.isSameItemSameComponents(output.getItem(0), rite.result())
                || output.getItem(0).getCount() + rite.result().getCount() > output.getItem(0).getMaxStackSize())) {
            problem = Component.translatable("altar.alchyforbidden.output_full");
        }
        if (problem != null) {
            if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(problem, true);
            return false;
        }
        riteTicks = rite.ticks();
        int[] cost = rite.costs();
        for (int i = 0; i < 3; i++) {
            reserve[i] -= cost[i];
            riteCost[i] = cost[i];
        }
        setAnimationDisplay("result", rite.result());
        // a great rite opens the rift over the altar while it runs
        if (rite.totalCost() >= 100 && level instanceof ServerLevel server) {
            com.lealex.alchyforbidden.network.AltarRiftPayload.open(server, riftPos(), Math.min(5, 2 + rite.totalCost() / 200));
        }
        setPhase(RITE);
        startAnimationOperation(riteTicks);
        setAnimationWorking(true);
        playSound(worldPosition, ModRegistries.ALTAR_GATHER.get(), 1.0f, 1.1f);
        return true;
    }

    /** Stands a hopper filled: the rite they make begins by itself once they make one (looked at twice a second). */
    @Override
    protected void tickWorking() {
        if (!autoFilled || level == null || level.getGameTime() % 10 != 0) return;
        if (com.lealex.alchyforbidden.rite.AltarRites.match(standItems()) != null && tryBeginRite(null)) autoFilled = false;
    }

    /**
     * Clicked in the screen's list (rite: its place in the list): the stands give back what lies on them, then take
     * the rite's ingredients from the player's inventory, one each, if all of them are there.
     */
    public void fillStands(Player player, com.lealex.alchyforbidden.rite.AltarRite rite) {
        if (level == null || !(player instanceof ServerPlayer serverPlayer)) return;
        if (phase != IDLE) {
            serverPlayer.sendSystemMessage(Component.translatable("altar.alchyforbidden.busy"), true);
            return;
        }
        List<net.minecraft.world.item.crafting.Ingredient> wanted = rite.ingredients();
        if (wanted.size() > standCount()) {
            serverPlayer.sendSystemMessage(Component.translatable("altar.alchyforbidden.rite_tier", rite.tier()), true);
            return;
        }
        // first what lies on the stands goes back (it may be what the rite needs)
        for (int i = 0; i < MAX_STANDS; i++) {
            ItemStack lying = stands.getItem(i);
            if (lying.isEmpty()) continue;
            stands.setItem(i, ItemStack.EMPTY);
            if (!player.getInventory().add(lying)) player.drop(lying, false);
        }
        // then one of each ingredient, from the inventory (never the armour or the off hand)
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        int[] taken = new int[36];
        int[] from = new int[wanted.size()];
        List<Component> missing = new ArrayList<>();
        for (int k = 0; k < wanted.size(); k++) {
            from[k] = -1;
            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.getCount() - taken[slot] > 0 && wanted.get(k).test(stack)) {
                    from[k] = slot;
                    taken[slot]++;
                    break;
                }
            }
            if (from[k] < 0) {
                var items = wanted.get(k).items().toList();
                missing.add(items.isEmpty() ? Component.literal("?") : new ItemStack(items.getFirst()).getHoverName());
            }
        }
        if (!missing.isEmpty()) {
            MutableComponent list = Component.empty();
            for (int i = 0; i < missing.size(); i++) {
                if (i > 0) list.append(", ");
                list.append(missing.get(i));
            }
            serverPlayer.sendSystemMessage(Component.translatable("altar.alchyforbidden.rite_missing", list), true);
            return;
        }
        for (int k = 0; k < wanted.size(); k++) {
            ItemStack one = player.getAbilities().instabuild ? inventory.getItem(from[k]).copyWithCount(1) : inventory.removeItem(from[k], 1);
            stands.setItem(k, one);
        }
        playSound(worldPosition, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_PLACE, 0.8f, 0.8f);
    }

    @Override
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> getItemHandler() {
        return automation;
    }

    /** The stands as hoppers see them: one item on each free stand that has grown, nothing during a rite. */
    private class StandsView implements net.minecraft.world.Container {
        @Override public int getContainerSize() { return MAX_STANDS; }
        @Override public boolean isEmpty() { return stands.isEmpty(); }
        @Override public ItemStack getItem(int slot) { return stands.getItem(slot); }
        @Override public ItemStack removeItem(int slot, int amount) { return stands.removeItem(slot, amount); }
        @Override public ItemStack removeItemNoUpdate(int slot) { return stands.removeItemNoUpdate(slot); }
        @Override public void setItem(int slot, ItemStack stack) { stands.setItem(slot, stack); }
        @Override public void setItem(int slot, ItemStack stack, boolean insideTransaction) { stands.setItem(slot, stack, insideTransaction); }
        @Override public int getMaxStackSize() { return 1; }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() { stands.clearContent(); }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return isFormed() && phase == IDLE && slot < standCount() && stands.getItem(slot).isEmpty();
        }

        @Override
        public void setChanged() { // (only when an insert is kept)
            stands.setChanged();
            autoFilled = true;
        }
    }

    /** Stands 0-7 (in), the result as 8 (out). */
    private class Automation implements net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> {
        @Override public int size() { return MAX_STANDS + 1; }
        @Override public net.neoforged.neoforge.transfer.item.ItemResource getResource(int index) {
            return index < MAX_STANDS ? standsIn.getResource(index) : resultOut.getResource(0);
        }
        @Override public long getAmountAsLong(int index) {
            return index < MAX_STANDS ? standsIn.getAmountAsLong(index) : resultOut.getAmountAsLong(0);
        }
        @Override public long getCapacityAsLong(int index, net.neoforged.neoforge.transfer.item.ItemResource resource) {
            return index < MAX_STANDS ? standsIn.getCapacityAsLong(index, resource) : resultOut.getCapacityAsLong(0, resource);
        }
        @Override public boolean isValid(int index, net.neoforged.neoforge.transfer.item.ItemResource resource) {
            return index < MAX_STANDS && standsIn.isValid(index, resource);
        }
        @Override public int insert(int index, net.neoforged.neoforge.transfer.item.ItemResource resource, int amount,
                                    net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            return index < MAX_STANDS ? standsIn.insert(index, resource, amount, transaction) : 0;
        }
        @Override public int extract(int index, net.neoforged.neoforge.transfer.item.ItemResource resource, int amount,
                                     net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            return index == MAX_STANDS ? resultOut.extract(0, resource, amount, transaction) : 0;
        }
    }

    /** The rite is done: the stands are emptied, the result waits in the output. */
    private void finishRite() {
        if (level == null) return;
        ItemStack result = getAnimationDisplay("result").copy();
        setPhase(IDLE); // first: emptying the stands below must not read as the rite breaking
        for (int i = 0; i < MAX_STANDS; i++) stands.removeItemNoUpdate(i);
        ItemStack waiting = output.getItem(0);
        if (waiting.isEmpty()) output.setItem(0, result);
        else waiting.grow(result.getCount());
        setAnimationDisplay("result", ItemStack.EMPTY);
        standsChanged();
        output.setChanged();
        finishAnimationOperation();
        setAnimationWorking(false);
        int total = riteCost[0] + riteCost[1] + riteCost[2];
        playSound(worldPosition, ModRegistries.ALTAR_AWAKEN.get(), 1.0f + Math.min(1.0f, total / 400f), 1.4f - Math.min(0.6f, total / 1000f));
        if (level instanceof ServerLevel server) {
            Vec3 eye = eyePosition();
            int burst = 20 + Math.min(80, total / 4);
            server.sendParticles(ModRegistries.RIFT_MOTE.get(), eye.x, eye.y, eye.z, burst, 0.4, 0.4, 0.4, 0.08);
            if (riteCost[0] > 0) server.sendParticles(ParticleTypes.FLAME, eye.x, eye.y, eye.z, burst / 3, 0.3, 0.3, 0.3, 0.06);
            if (riteCost[1] > 0) server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, eye.x, eye.y, eye.z, burst / 3, 0.3, 0.3, 0.3, 0.05);
            if (riteCost[2] > 0) server.sendParticles(ParticleTypes.SCULK_SOUL, eye.x, eye.y, eye.z, burst / 4, 0.3, 0.3, 0.3, 0.03);
            if (total >= 200) server.sendParticles(ParticleTypes.SONIC_BOOM, eye.x, eye.y, eye.z, 1, 0, 0, 0, 0);
            if (total >= 100) com.lealex.alchyforbidden.network.AltarRiftPayload.close(server, riftPos());
        }
        java.util.Arrays.fill(riteCost, 0);
    }

    // ---- The screen ----

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.alchyforbidden.altar");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory, Player player) {
        return new com.lealex.alchyforbidden.menu.AltarMenu(containerId, inventory, this);
    }

    public static Identifier patternFor(int tier) {
        return Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "altar_" + tier);
    }

    // ---- How the altar grows (GrowingCoreBlockEntity) ----

    @Override
    protected String patternBase() {
        return "altar";
    }

    @Override
    public int maxTier() {
        return MAX_TIER;
    }

    @Override
    protected List<Config.Cost> costFor(int tier) {
        return Config.costFor(tier);
    }

    /**
     * The stepped middle (within the tier's own number of blocks of the core: the 3 x 3 and its steps at tier 2, the
     * 5 x 5 and its steps at tier 3) rises a block each tier instead of coming out of the rift; the floor under it
     * changes where it lies.
     */
    @Override
    protected boolean platform(BlockPos pos) {
        return platformLayer(pos) >= 1;
    }

    @Override
    protected boolean platformBase(BlockPos pos) {
        return platformLayer(pos) == 0;
    }

    /** Its pillars (by default) and the arches laid over them rise out of the ground. */
    @Override
    protected boolean platform(BlockPos pos, BlockState look) {
        return super.platform(pos, look) || archLayer(pos, look) > 0;
    }

    /** The arches (steps, beam, keystone, the lantern under it) come up from the floor to the top of the pillars. */
    @Override
    protected int riseFromBase(BlockPos pos, BlockState look) {
        return archLayer(pos, look);
    }

    /** Layer over the floor of a cell of the arches over the pillars (outside the platform, 5 up or more), 0 otherwise. */
    private int archLayer(BlockPos pos, BlockState look) {
        if (pillarLook(look) || look.isAir()) return 0;
        if (Math.abs(pos.getX() - worldPosition.getX()) <= goal && Math.abs(pos.getZ() - worldPosition.getZ()) <= goal) return 0;
        int layer = pos.getY() - (worldPosition.getY() - rise(standing) - 1);
        return layer >= 5 ? layer : 0;
    }

    /** Layer over the floor of a cell inside the platform's footprint for the tier being grown, -1 outside it. */
    private int platformLayer(BlockPos pos) {
        int reach = goal;
        if (Math.abs(pos.getX() - worldPosition.getX()) > reach || Math.abs(pos.getZ() - worldPosition.getZ()) > reach) return -1;
        int floor = worldPosition.getY() - rise(standing) - 1;
        return pos.getY() - floor;
    }

    /** The altar's core rises one block per tier (each pattern has its core one layer higher, over the same floor). */
    @Override
    protected int rise(int tier) {
        return tier - 1;
    }

    /** It grows only with the eye over its center. */
    @Override
    protected boolean canGrow() {
        return hasEye();
    }

    @Override
    protected Vec3 burstPosition() {
        return eyePosition();
    }

    @Override
    protected void tickOwnPhase(int phase, long elapsed) {
        if (phase != RITE) {
            setPhase(IDLE);
            return;
        }
        if (elapsed % 20 == 0) playSound(worldPosition, ModRegistries.ALTAR_GROW.get(), 0.5f, 1.2f + elapsed / 400f);
        if (elapsed >= riteTicks) finishRite();
    }

    /** The structure broke: a rite under way ends, the outer stands' items fall (the outer ring is gone). */
    @Override
    protected void onStructureBroken() {
        if (level == null) return;
        if (phase == RITE) {
            setAnimationDisplay("result", ItemStack.EMPTY);
            java.util.Arrays.fill(riteCost, 0);
        }
        phase = IDLE;
        for (int i = 4; i < MAX_STANDS; i++) {
            ItemStack lying = stands.removeItemNoUpdate(i);
            if (!lying.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, lying);
        }
        standsChanged();
    }

    @Override
    protected void clientWorking(RandomSource random, long elapsed, double cx, double cy, double cz) {
        if (phase == RITE) riteEffects(random, elapsed, cx, cy, cz);
    }

    @Override
    protected String machineName() {
        return "Rift altar";
    }

    @Override
    public net.minecraft.core.particles.ParticleOptions waveParticle() {
        return ParticleTypes.SCULK_SOUL;
    }

    // ---- The eye on the altar's center ----

    /** True once an eye sits over the altar's center: the altar is awake and can grow. Kept as the "eye" display. */
    /** A showcased altar is given its eye, so it may grow. */
    @Override
    protected void prepareShowcase() {
        placeEye(new ItemStack(ModRegistries.RIFT_EYE.get()));
    }

    public boolean hasEye() {
        return !getAnimationDisplay(EYE).isEmpty();
    }

    /** True if an eye can be set on this altar now (both sides). */
    public boolean takesEye(ItemStack stack) {
        return stack.is(ModRegistries.RIFT_EYE.get()) && isFormed() && !hasEye() && !isGrowing();
    }

    /** Sets the eye over the altar's center: it wakes (and grows back what it had, if anything is missing). */
    public void setEye(Player player, ItemStack stack) {
        if (level == null || !takesEye(stack)) return;
        placeEye(stack.copyWithCount(1));
        if (!player.getAbilities().instabuild) stack.shrink(1);
    }

    /** The eye goes over the center (from a click or the screen's eye slot): the altar wakes. */
    public void placeEye(ItemStack stack) {
        if (level == null || stack.isEmpty() || hasEye()) return;
        ItemStack eye = stack.copyWithCount(1);
        eye.remove(ModRegistries.GAZE.get());
        setAnimationDisplay(EYE, eye);
        playSound(worldPosition, ModRegistries.ALTAR_AWAKEN.get(), 1.0f, 1.3f);
        if (level instanceof ServerLevel server) {
            Vec3 at = eyePosition();
            server.sendParticles(ModRegistries.RIFT_MOTE.get(), at.x, at.y, at.z, 30, 0.3, 0.3, 0.3, 0.05);
            server.sendParticles(ParticleTypes.SCULK_SOUL, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.02);
        }
        say(Component.translatable("altar.alchyforbidden.eye_set"));
        tryGrowing();
    }

    /** Takes the eye back (sneak + right-click the core with an empty hand): the altar sleeps again. */
    public void takeEye(Player player) {
        ItemStack eye = removeEye();
        if (!eye.isEmpty() && !player.getInventory().add(eye)) player.drop(eye, false);
    }

    /** Takes the eye off the center (empty if there is none or the altar is busy): the altar sleeps again. */
    public ItemStack removeEye() {
        if (level == null || !hasEye() || isGrowing()) return ItemStack.EMPTY;
        ItemStack eye = getAnimationDisplay(EYE).copy();
        setAnimationDisplay(EYE, ItemStack.EMPTY);
        playSound(worldPosition, ModRegistries.EYE_WHISPER.get(), 0.8f, 0.7f);
        say(Component.translatable("altar.alchyforbidden.eye_taken"));
        return eye;
    }

    /** Where the eye floats: the pattern's "eye" anchor (above the center, one higher each tier). */
    private Vec3 eyePosition() {
        MultiblockPattern pattern = pattern();
        BlockPos at = pattern == null ? null : pattern.anchor(worldPosition, rotation(), EYE);
        return Vec3.atCenterOf(at == null ? worldPosition.above() : at);
    }

    // ---- What it says (right-click with an empty hand, and the eye) ----

    @Override
    public void openGui(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        // An eye in hand on any part of the altar: set it over the center
        if (takesEye(player.getMainHandItem())) {
            setEye(player, player.getMainHandItem());
            return;
        }
        serverPlayer.openMenu(this, buffer -> buffer.writeBlockPos(worldPosition));
        tryGrowing(); // it was in the way before: try again
    }

    /** One line about the altar's state: what is missing, what it wants, or that it grows. */
    public Component statusLine() {
        if (!isFormed()) {
            String problem = getStructureProblem();
            return isForming() ? Component.translatable("altar.alchyforbidden.forming")
                    : Component.translatable("altar.alchyforbidden.unformed", problem == null ? "" : problem);
        }
        if (phase == RITE) return Component.translatable("altar.alchyforbidden.riting");
        if (isGrowing()) return Component.translatable("altar.alchyforbidden.growing");
        if (!hasEye()) return Component.translatable("altar.alchyforbidden.no_eye");
        if (standing < tier) return Component.translatable("altar.alchyforbidden.regrow");
        if (tier >= MAX_TIER) return Component.translatable("altar.alchyforbidden.whole", tier);
        return wantsLine();
    }

    /**
     * A rite's look (client), from what it spends: Wrath = crimson sparks and embers thrown hard at the eye, Sorrow =
     * soul flames and souls drifting slowly up into it, Sculk Corruption = sculk flecks and charge pops spiralling in
     * from the floor. More of an essence, more of its particles; the last fifth of the rite draws everything in.
     */
    private void riteEffects(RandomSource random, long elapsed, double cx, double cy, double cz) {
        if (level == null) return;
        Vec3 eye = eyePosition();
        float progress = riteProgress();
        int total = riteCost[0] + riteCost[1] + riteCost[2];
        int rate = 1 + Math.min(6, total / 60);
        boolean drawingIn = progress > 0.8F;
        for (int i = 0; i < rate; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = (drawingIn ? 1.0 : 2.2) + random.nextDouble() * 1.2;
            double x = cx + Math.cos(angle) * radius, z = cz + Math.sin(angle) * radius, y = cy - 0.6 + random.nextDouble() * 1.4;
            double vx = (eye.x - x) * 0.07, vy = (eye.y - y) * 0.07, vz = (eye.z - z) * 0.07;
            int pick = pickEssence(random);
            if (pick == 0) { // Wrath: hard, straight, hot
                level.addParticle(new net.minecraft.core.particles.DustParticleOptions(com.lealex.alchyforbidden.essence.Essence.WRATH.color(), 1.1F), x, y, z, vx * 1.6, vy * 1.6, vz * 1.6);
                if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.FLAME, x, y, z, vx * 1.2, vy * 1.2, vz * 1.2);
            } else if (pick == 1) { // Sorrow: slow, rising, cold
                level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, vx * 0.5, vy * 0.5 + 0.02, vz * 0.5);
                if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.SOUL, x, y, z, 0, 0.04, 0);
            } else if (pick == 2) { // Sculk Corruption: from the floor, turning as it comes
                double fx = cx + Math.cos(angle) * radius, fz = cz + Math.sin(angle) * radius;
                level.addParticle(ModRegistries.SCULK_FLECK.get(), fx, cy - 0.9, fz, vx - Math.sin(angle) * 0.05, 0.12, vz + Math.cos(angle) * 0.05);
                if (random.nextInt(5) == 0) level.addParticle(ParticleTypes.SCULK_CHARGE_POP, fx, cy - 0.95, fz, 0, 0.02, 0);
            } else { // nothing spent: the rift's own motes
                level.addParticle(ModRegistries.RIFT_MOTE.get(), x, y, z, vx, vy, vz);
            }
        }
        if (total > 0 && elapsed % 20 == 0) {
            net.minecraft.sounds.SoundEvent sound = pickEssence(random) == 0 ? net.minecraft.sounds.SoundEvents.BLAZE_SHOOT
                    : pickEssence(random) == 1 ? net.minecraft.sounds.SoundEvents.SOUL_ESCAPE.value() : net.minecraft.sounds.SoundEvents.SCULK_BLOCK_SPREAD;
            level.playLocalSound(cx, cy, cz, sound, net.minecraft.sounds.SoundSource.BLOCKS, 0.4F, 0.6F + progress * 0.6F, false);
        }
    }

    /** One of the essences the rite spends, at random by how much of each (3 = none). */
    private int pickEssence(RandomSource random) {
        int total = riteCost[0] + riteCost[1] + riteCost[2];
        if (total <= 0) return 3;
        int roll = random.nextInt(total);
        return roll < riteCost[0] ? 0 : roll < riteCost[0] + riteCost[1] ? 1 : 2;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (isMoving()) return; // the core moved: its structure, gifts and eye went with it
        super.preRemoveSideEffects(pos, state); // (the gifts)
        if (level == null || level.isClientSide()) return;
        if (hasEye()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, getAnimationDisplay(EYE).copy());
        Containers.dropContents(level, pos, stands);
        Containers.dropContents(level, pos, output);
    }

    // ---- Saving ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("stands", ItemStack.OPTIONAL_CODEC.listOf(), stands.getItems());
        output.store("output", ItemStack.OPTIONAL_CODEC, this.output.getItem(0));
        output.putInt("riteTicks", riteTicks);
        output.putIntArray("essences", reserve.clone());
        output.putIntArray("riteCost", riteCost.clone());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        java.util.List<ItemStack> lying = input.read("stands", ItemStack.OPTIONAL_CODEC.listOf()).orElse(java.util.List.of());
        for (int i = 0; i < MAX_STANDS; i++) stands.getItems().set(i, i < lying.size() ? lying.get(i) : ItemStack.EMPTY);
        this.output.getItems().set(0, input.read("output", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        riteTicks = input.getIntOr("riteTicks", 0);
        int[] saved = input.getIntArray("essences").orElse(new int[3]);
        int[] spent = input.getIntArray("riteCost").orElse(new int[3]);
        for (int i = 0; i < 3; i++) {
            reserve[i] = i < saved.length ? saved[i] : 0;
            riteCost[i] = i < spent.length ? spent[i] : 0;
        }
    }

    /** Its three essences beside the crosshair (AlchyX LookMeters). */
    @Override
    public void lookMeters(java.util.List<LookMeter> out) {
        for (com.lealex.alchyforbidden.essence.Essence e : com.lealex.alchyforbidden.essence.Essence.values()) {
            out.add(new LookMeter(essence(e), reserveCap(), 0xFF000000 | e.color(), e.icon()));
        }
    }
}
