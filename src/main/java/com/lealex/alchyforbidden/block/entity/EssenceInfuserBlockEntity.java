package com.lealex.alchyforbidden.block.entity;

import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.essence.Essence;
import com.lealex.alchyforbidden.essence.Inscription;
import com.lealex.alchyforbidden.essence.Inscriptions;
import com.lealex.alchyforbidden.item.VesselItem;
import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
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
 * The Essence Infuser: a floor round its core with offering stands, from the second tier pillars and runes, from the
 * third lamp pillars and sculk. It keeps a store of the three essences (fed by the Attuning Fork's links, like the
 * fountain and the bier; when the store runs dry it draws from the nearest awake altar within reach) and pours the
 * essence picked in its screen into the vessel laid in it (Config.infuserRate by tier, a second), hoppers and pipes
 * putting vessels in and taking them out. At its third tier it can be bound to a player whose body carries an Essence
 * Reserve: while the infuser is loaded it pours up to Config.infuserBodyRate of each essence a second into that
 * reserve, wherever the player is (online).
 */
public class EssenceInfuserBlockEntity extends GrowingCoreBlockEntity implements net.minecraft.world.MenuProvider,
        com.lealex.alchyforbidden.essence.EssenceReceiver {
    private final int[] held = new int[Essence.values().length]; // synced
    private Essence chosen = Essence.SORROW;                    // synced
    private @Nullable UUID bound;                               // the body it feeds (tier 3), synced with its name
    private String boundName = "";
    private boolean pouring;                                    // synced: it filled a vessel this last second (particles)

    private final net.minecraft.world.SimpleContainer vessel = new net.minecraft.world.SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            EssenceInfuserBlockEntity.this.setChanged();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof VesselItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    };
    private final net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> automation =
            net.neoforged.neoforge.transfer.item.VanillaContainerWrapper.of(vessel);

    public EssenceInfuserBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.ESSENCE_INFUSER_BE.get(), pos, state);
    }

    @Override
    protected String patternBase() {
        return "essence_infuser";
    }

    @Override
    protected List<Config.Cost> costFor(int tier) {
        return Config.infuserCostFor(tier);
    }

    @Override
    protected String messagePrefix() {
        return "essence_infuser";
    }

    @Override
    protected String machineName() {
        return "Essence infuser";
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
        return Math.max(0, Config.infuserStore() - held[essence.ordinal()]);
    }

    /** Streams land just over the core. */
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

    public Essence chosen() {
        return chosen;
    }

    public void choose(Essence essence) {
        if (!essence.carried() || essence == chosen) return;
        chosen = essence;
        playSound(worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 0.8f + essence.ordinal() * 0.15f);
        setChanged();
        syncToClients();
    }

    public net.minecraft.world.SimpleContainer vessel() {
        return vessel;
    }

    public boolean pouring() {
        return pouring;
    }

    public @Nullable UUID bound() {
        return bound;
    }

    public String boundName() {
        return boundName;
    }

    /** Its screen's binding: to this player (tier 3, an Essence Reserve carved), or let go of the one it holds. */
    public void bind(ServerPlayer player, boolean on) {
        if (!on) {
            if (bound != null) player.sendSystemMessage(Component.translatable("essence_infuser.alchyforbidden.unbound"), true);
            bound = null;
            boundName = "";
        } else if (standing < 3) {
            player.sendSystemMessage(Component.translatable("essence_infuser.alchyforbidden.too_low").withStyle(ChatFormatting.RED), true);
            return;
        } else if (!Inscriptions.has(player, Inscription.ESSENCE_RESERVE)) {
            player.sendSystemMessage(Component.translatable("essence_infuser.alchyforbidden.no_reserve").withStyle(ChatFormatting.RED), true);
            return;
        } else {
            bound = player.getUUID();
            boundName = player.getGameProfile().name();
            player.sendSystemMessage(Component.translatable("essence_infuser.alchyforbidden.bound").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            playSound(worldPosition, SoundEvents.SCULK_CATALYST_BLOOM, 1.0f, 0.8f);
        }
        setChanged();
        syncToClients();
    }

    // ---- The work ----

    @Override
    protected void tickWorking() {
        if (!(level instanceof ServerLevel server) || !isFormed() || level.getGameTime() % 20 != 0) return;
        boolean was = pouring;
        pouring = false;
        // the vessel laid in it: the essence picked, a second's worth
        ItemStack stack = vessel.getItem(0);
        if (stack.getItem() instanceof VesselItem) {
            int want = Config.infuserRate(standing);
            if (held[chosen.ordinal()] < want) drawFromAltar(server, chosen, want - held[chosen.ordinal()]);
            int poured = VesselItem.fill(stack, chosen, Math.min(want, held[chosen.ordinal()]));
            if (poured > 0) {
                held[chosen.ordinal()] -= poured;
                vessel.setChanged();
                pouring = true;
            }
        }
        // the bound body, wherever it is
        if (standing >= 3 && bound != null) feedBody(server);
        if (pouring != was || pouring) {
            setChanged();
            syncToClients();
        }
    }

    /** Up to the body rate of each essence into the bound player's reserve (online, carved); a stream when near. */
    private void feedBody(ServerLevel server) {
        ServerPlayer player = server.getServer().getPlayerList().getPlayer(bound);
        if (player == null || !Inscriptions.has(player, Inscription.ESSENCE_RESERVE)) return;
        int rate = Config.infuserBodyRate();
        for (Essence essence : Essence.values()) { // (the body shows it: its reserve's own burst as it fills, client/InscriptionVisuals)
            int given = Inscriptions.giveReserve(player, essence, Math.min(rate, held[essence.ordinal()]));
            if (given <= 0) continue;
            held[essence.ordinal()] -= given;
            pouring = true;
        }
    }

    /** Its store runs dry: the nearest awake altar within reach holding the essence gives what it needs (up to 200). */
    private void drawFromAltar(ServerLevel server, Essence essence, int amount) {
        AltarBlockEntity best = null;
        double bestDistance = (double) Config.infuserReach() * Config.infuserReach();
        for (AltarBlockEntity altar : AltarBlockEntity.loaded(server)) {
            if (!altar.isFormed() || !altar.hasEye() || altar.essence(essence) <= 0) continue;
            double distance = altar.getBlockPos().distSqr(worldPosition);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = altar;
            }
        }
        if (best == null) return;
        int take = Math.min(Math.min(200, Math.max(amount, 1)), Math.min(best.essence(essence), room(essence)));
        if (take > 0 && best.spendEssence(essence, take)) {
            held[essence.ordinal()] += take;
            com.lealex.alchyforbidden.essence.Relays.guide(server, best.getBlockPos().above(), worldPosition.above(), essence);
        }
    }

    // ---- Client: the essence winding up into the vessel ----

    @Override
    protected void clientWorking(RandomSource random, long elapsed, double cx, double cy, double cz) {
        if (level == null || !isFormed()) return;
        net.minecraft.core.particles.ParticleOptions mote = switch (chosen) {
            case WRATH -> ModRegistries.WRATH_MOTE.get();
            case SORROW -> ModRegistries.SORROW_MOTE.get();
            case CORRUPTION -> ModRegistries.CORRUPTION_MOTE.get();
        };
        if (pouring && com.lealex.alchyx.AlchyXClientConfig.spawn(random)) { // spiralling in from the stands to over the core
            double a = level.getGameTime() * 0.3 + random.nextDouble() * 0.5, r = 1.6;
            level.addParticle(mote, cx + Math.cos(a) * r, cy + 0.2, cz + Math.sin(a) * r, -Math.cos(a) * 0.08, 0.04, -Math.sin(a) * 0.08);
        } else if (random.nextInt(12) == 0) {
            level.addParticle(mote, cx + (random.nextDouble() - 0.5) * 0.6, cy + 0.3, cz + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
        }
    }

    // ---- Automation, screen, saving ----

    @Override
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> getItemHandler() {
        return automation;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (isMoving()) return;
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) {
            net.minecraft.world.Containers.dropContents(server, pos, vessel);
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
        return Component.translatable("block.alchyforbidden.essence_infuser");
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory, Player player) {
        return new com.lealex.alchyforbidden.menu.SourceMenu(id, inventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putIntArray("held", held.clone());
        output.putInt("chosen", chosen.ordinal());
        if (bound != null) {
            output.store("bound", UUIDUtil.CODEC, bound);
            output.putString("boundName", boundName);
        }
        output.putBoolean("pouring", pouring);
        output.store("vessel", ItemStack.OPTIONAL_CODEC, vessel.getItem(0));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int[] kept = input.getIntArray("held").orElse(new int[0]);
        java.util.Arrays.fill(held, 0);
        for (int i = 0; i < held.length && i < kept.length; i++) held[i] = kept[i];
        chosen = Essence.values()[Math.floorMod(input.getIntOr("chosen", Essence.SORROW.ordinal()), Essence.values().length)];
        bound = input.read("bound", UUIDUtil.CODEC).orElse(null);
        boundName = input.getStringOr("boundName", "");
        pouring = input.getBooleanOr("pouring", false);
        vessel.setItem(0, input.read("vessel", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }

    /** Its three essences beside the crosshair (AlchyX LookMeters). */
    @Override
    public void lookMeters(java.util.List<LookMeter> out) {
        for (com.lealex.alchyforbidden.essence.Essence e : com.lealex.alchyforbidden.essence.Essence.values()) {
            out.add(new LookMeter(held(e), com.lealex.alchyforbidden.Config.infuserStore(), 0xFF000000 | e.color(), e.icon()));
        }
    }
}
