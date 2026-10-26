package com.lealex.alchyforbidden.item;

import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.block.RelayBlock;
import com.lealex.alchyforbidden.block.entity.AltarBlockEntity;
import com.lealex.alchyforbidden.block.entity.GrowingCoreBlockEntity;
import com.lealex.alchyforbidden.block.entity.RelayBlockEntity;
import com.lealex.alchyforbidden.essence.Relays;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.lealex.alchyx.block.entity.MultiblockCoreBlockEntity;
import com.lealex.alchyx.multiblock.CoreTracker;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

/**
 * The Attuning Fork: links essence senders to Echo Obelisks and altars. Right-click a sender (a Killing Pit, Lament Well or
 * Echo Resonator, by any of its blocks, or a relay) to start, then a relay or an altar to link it there; after a relay
 * the tuner goes on from that relay, so a chain is laid click by click. Sneak + right-click a sender cuts its link
 * (a source goes back to its nearest altar); sneak + right-click the air forgets where it started. Held, it shows the
 * links nearby (client/RelayLinks). Clicks go through PlayerInteractEvent.RightClickBlock, before the blocks' own
 * (a multiblock part would open its screen).
 */
public class AttuningForkItem extends Item {
    public AttuningForkItem(Properties properties) {
        super(properties);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack tuner = event.getItemStack();
        if (!tuner.is(ModRegistries.ATTUNING_FORK.get())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ServerPlayer player) {
            click(level, player, event.getPos(), tuner);
        }
    }

    /** What a click on pos is for the tuner: a relay's foot, a core (a source or an altar), or null. */
    public static @Nullable BlockPos node(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModRegistries.ECHO_OBELISK.get())) return RelayBlock.foot(state, pos);
        if (level.getBlockEntity(pos) instanceof GrowingCoreBlockEntity) return pos;
        MultiblockCoreBlockEntity core = CoreTracker.formedCoreAt(level, pos);
        return core instanceof GrowingCoreBlockEntity ? core.getBlockPos() : null;
    }

    private static boolean isSender(BlockEntity entity) {
        return entity instanceof RelayBlockEntity || (entity instanceof GrowingCoreBlockEntity && !(entity instanceof AltarBlockEntity)
                && !(entity instanceof com.lealex.alchyforbidden.essence.EssenceReceiver)); // (a fountain, a bier: they only take)
    }

    private static boolean linked(BlockEntity entity) {
        if (entity instanceof RelayBlockEntity relay) return !relay.targets().isEmpty();
        return entity instanceof GrowingCoreBlockEntity core && core.relayLink() != null;
    }

    /** Cuts every link it sends along (an obelisk: all its branches). */
    private static void cut(BlockEntity entity) {
        if (entity instanceof RelayBlockEntity relay) {
            for (BlockPos other : List.copyOf(relay.targets())) { // two-way links: the other end lets go too
                if (relay.getLevel() != null && relay.getLevel().getBlockEntity(other) instanceof RelayBlockEntity back) back.removeTarget(relay.getBlockPos());
            }
            relay.clearTargets();
        }
        else if (entity instanceof GrowingCoreBlockEntity core) core.setRelayLink(null);
    }

    /** Where a node's link is drawn from / to: a relay's crystal, a core's origin. */
    public static Vec3 point(Level level, BlockPos node) {
        BlockEntity entity = level.getBlockEntity(node);
        if (entity instanceof RelayBlockEntity) return RelayBlock.crystal(level, node);
        if (entity instanceof com.lealex.alchyforbidden.essence.EssenceReceiver receiver && !(entity instanceof AltarBlockEntity)) return Vec3.atCenterOf(receiver.intakePos());
        if (entity instanceof GrowingCoreBlockEntity core && !(entity instanceof AltarBlockEntity)) return Vec3.atCenterOf(core.relayOrigin());
        AltarBlockEntity altar = Relays.altarAt(level, node);
        return Vec3.atCenterOf(altar != null ? altar.getBlockPos() : node).add(0, 1, 0);
    }

    private static Component name(Level level, BlockPos pos) {
        return level.getBlockState(pos).getBlock().getName();
    }

    private static void say(ServerPlayer player, Component line) {
        player.sendSystemMessage(line, true);
    }

    private static void click(ServerLevel level, ServerPlayer player, BlockPos clicked, ItemStack tuner) {
        BlockPos node = node(level, clicked);
        if (node == null) {
            say(player, Component.translatable("item.alchyforbidden.attuning_fork.nothing").withStyle(ChatFormatting.GRAY));
            return;
        }
        BlockEntity entity = level.getBlockEntity(node);
        if (player.isSecondaryUseActive()) { // cut its link
            if (entity != null && isSender(entity) && linked(entity)) {
                cut(entity);
                level.playSound(null, node, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.8f, 1.3f);
                say(player, Component.translatable(entity instanceof RelayBlockEntity ? "item.alchyforbidden.attuning_fork.cut_relay"
                        : "item.alchyforbidden.attuning_fork.cut", name(level, node)));
            }
            return;
        }
        GlobalPos from = tuner.get(ModRegistries.FORK_FROM.get());
        BlockEntity sender = from != null && from.dimension() == level.dimension() ? level.getBlockEntity(from.pos()) : null;
        boolean target = entity instanceof RelayBlockEntity || Relays.receiverAt(level, node) != null;
        if (sender != null && isSender(sender) && target && !from.pos().equals(node)) {
            link(level, player, tuner, from.pos(), sender, node, entity);
            return;
        }
        if (entity != null && isSender(entity)) { // start from here
            tuner.set(ModRegistries.FORK_FROM.get(), GlobalPos.of(level.dimension(), node));
            level.playSound(null, node, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 0.8f);
            say(player, Component.translatable("item.alchyforbidden.attuning_fork.from", name(level, node)));
            return;
        }
        say(player, Component.translatable("item.alchyforbidden.attuning_fork.not_sender").withStyle(ChatFormatting.GRAY));
    }

    private static void link(ServerLevel level, ServerPlayer player, ItemStack tuner, BlockPos from, BlockEntity sender, BlockPos to,
                             @Nullable BlockEntity target) {
        int range = Config.relayRange();
        if (from.distSqr(to) > (double) range * range) {
            say(player, Component.translatable("item.alchyforbidden.attuning_fork.too_far", range).withStyle(ChatFormatting.RED));
            return;
        }
        // two obelisks are joined both ways: essence can go either way along the link (the search never walks a path
        // back on itself, so a loop of obelisks is harmless)
        RelayBlockEntity back = sender instanceof RelayBlockEntity && target instanceof RelayBlockEntity other ? other : null;
        // the same place again: that link is taken away (an old one-way link is made two-way first)
        if (sender instanceof RelayBlockEntity relay && relay.hasTarget(to)) {
            if (back != null && !back.hasTarget(from)) {
                if (!back.addTarget(from)) {
                    say(player, Component.translatable("item.alchyforbidden.attuning_fork.full", Config.obeliskBranches()).withStyle(ChatFormatting.RED));
                    return;
                }
                level.playSound(null, to, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 1.2f);
                say(player, Component.translatable("item.alchyforbidden.attuning_fork.both_ways", name(level, from), name(level, to)));
                return;
            }
            relay.removeTarget(to);
            if (back != null) back.removeTarget(from);
            level.playSound(null, to, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.8f, 1.3f);
            say(player, Component.translatable("item.alchyforbidden.attuning_fork.unlinked_one", name(level, from), name(level, to)));
            return;
        }
        if (back != null && back.hasTarget(from)) { // already joined from the other end
            if (sender instanceof RelayBlockEntity relay && !relay.addTarget(to)) {
                say(player, Component.translatable("item.alchyforbidden.attuning_fork.full", Config.obeliskBranches()).withStyle(ChatFormatting.RED));
                return;
            }
        } else if (sender instanceof RelayBlockEntity relay) {
            boolean roomHere = relay.targets().size() < Config.obeliskBranches();
            boolean roomThere = back == null || back.targets().size() < Config.obeliskBranches();
            if (!roomHere || !roomThere) {
                say(player, Component.translatable("item.alchyforbidden.attuning_fork.full", Config.obeliskBranches()).withStyle(ChatFormatting.RED));
                return;
            }
            relay.addTarget(to);
            if (back != null) back.addTarget(from);
        } else if (sender instanceof GrowingCoreBlockEntity core) {
            core.setRelayLink(to);
        }
        // a thread of motes along the new link
        Vec3 a = point(level, from), b = point(level, to);
        int steps = (int) Math.max(2, a.distanceTo(b) * 2);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = a.lerp(b, i / (double) steps);
            level.sendParticles(ModRegistries.RIFT_MOTE.get(), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        level.playSound(null, to, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 1.2f);
        if (target instanceof RelayBlockEntity) { // go on from this relay
            tuner.set(ModRegistries.FORK_FROM.get(), GlobalPos.of(level.dimension(), to));
            say(player, Component.translatable("item.alchyforbidden.attuning_fork.linked_on", name(level, from)));
        } else {
            tuner.remove(ModRegistries.FORK_FROM.get());
            say(player, Component.translatable("item.alchyforbidden.attuning_fork.linked_to", name(level, from), name(level, to)));
        }
    }

    /** Sneak + right-click the air: forget where it started. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack tuner = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || !tuner.has(ModRegistries.FORK_FROM.get())) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) {
            tuner.remove(ModRegistries.FORK_FROM.get());
            say(serverPlayer, Component.translatable("item.alchyforbidden.attuning_fork.forgot"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModRegistries.FORK_FROM.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        GlobalPos from = stack.get(ModRegistries.FORK_FROM.get());
        if (from != null) {
            builder.accept(Component.translatable("item.alchyforbidden.attuning_fork.tooltip.from", from.pos().getX(), from.pos().getY(), from.pos().getZ())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        builder.accept(Component.translatable("item.alchyforbidden.attuning_fork.tooltip.1").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.alchyforbidden.attuning_fork.tooltip.2").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.alchyforbidden.attuning_fork.tooltip.3", Config.relayRange()).withStyle(ChatFormatting.GRAY));
    }
}
