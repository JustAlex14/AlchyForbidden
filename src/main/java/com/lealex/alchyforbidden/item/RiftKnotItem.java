package com.lealex.alchyforbidden.item;

import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

/**
 * The Rift Knot: right-click a spawner (vanilla's, or another mod's that keeps the game's spawner block entity, as
 * Apotheosis's do) to bind it, again to set it free. A bound spawner's mobs fold into one condensed mob of their kind
 * (essence/MobStacks). The knot is not used up.
 * <p>
 * Its gathering point: sneak-right-click a block to mark it on the knot (sneak-right-click the air to forget it). A
 * spawner knotted with a marked knot sends its condensed mob onto that block instead of round itself (within
 * MARK_RANGE); sneak-right-click a knotted spawner to give it the knot's mark (or, with no mark, take its point away).
 */
public class RiftKnotItem extends Item {
    /** How far from its spawner a gathering point may be. */
    public static final int MARK_RANGE = 32;

    public RiftKnotItem(Properties properties) {
        super(properties);
    }

    private static boolean isSpawner(Level level, BlockPos pos, BlockEntity entity) {
        return entity != null && (entity instanceof SpawnerBlockEntity || level.getBlockState(pos).is(Blocks.SPAWNER));
    }

    private static void say(Player player, Component line) {
        if (player instanceof ServerPlayer serverPlayer) serverPlayer.sendSystemMessage(line, true);
    }

    /** The knot's mark for that spawner: null when it has none, it is in another dimension, or too far. */
    private static BlockPos markFor(ItemStack knot, ServerLevel level, BlockPos spawner, Player player) {
        GlobalPos mark = knot.get(ModRegistries.KNOT_MARK.get());
        if (mark == null) return null;
        if (mark.dimension() != level.dimension() || !mark.pos().closerThan(spawner, MARK_RANGE)) {
            say(player, Component.translatable("item.alchyforbidden.rift_knot.too_far", MARK_RANGE).withStyle(ChatFormatting.RED));
            return null;
        }
        return mark.pos();
    }

    private static void setTarget(BlockEntity spawner, BlockPos target) {
        // no point: its own position (the mobs gather round it, as before)
        spawner.setData(ModRegistries.KNOT_TARGET, target == null ? spawner.getBlockPos() : target);
        spawner.syncData(ModRegistries.KNOT_TARGET);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack knot = context.getItemInHand();
        BlockEntity spawner = level.getBlockEntity(pos);
        boolean sneaking = player != null && player.isSecondaryUseActive();
        if (!isSpawner(level, pos, spawner)) {
            if (!sneaking) return InteractionResult.PASS;
            // marking a gathering point
            if (level instanceof ServerLevel server) {
                knot.set(ModRegistries.KNOT_MARK.get(), GlobalPos.of(server.dimension(), pos.immutable()));
                server.sendParticles(ModRegistries.RIFT_MOTE.get(), pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.3, 0.05, 0.3, 0.02);
                server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 0.7f);
                say(player, Component.translatable("item.alchyforbidden.rift_knot.marked", pos.getX(), pos.getY(), pos.getZ()));
            }
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel server) {
            boolean knotted = spawner.getData(ModRegistries.KNOTTED);
            if (sneaking && knotted) { // its gathering point, from the knot's mark
                BlockPos target = markFor(knot, server, pos, player);
                setTarget(spawner, target);
                spawner.setChanged();
                server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0f, 0.7f);
                say(player, target == null ? Component.translatable("item.alchyforbidden.rift_knot.target_cleared")
                        : Component.translatable("item.alchyforbidden.rift_knot.target_set", target.getX(), target.getY(), target.getZ()));
                return InteractionResult.SUCCESS;
            }
            boolean bind = !knotted;
            spawner.setData(ModRegistries.KNOTTED, bind);
            spawner.syncData(ModRegistries.KNOTTED); // its rift and circle, on every client
            setTarget(spawner, bind ? markFor(knot, server, pos, player) : null);
            spawner.setChanged();
            server.sendParticles(bind ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    30, 0.4, 0.4, 0.4, 0.1);
            server.playSound(null, pos, bind ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1.0f, bind ? 0.6f : 1.2f);
            say(player, Component.translatable(bind ? "item.alchyforbidden.rift_knot.bound" : "item.alchyforbidden.rift_knot.freed"));
        }
        return InteractionResult.SUCCESS;
    }

    /** Sneak-right-click the air: the knot forgets its mark. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack knot = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || !knot.has(ModRegistries.KNOT_MARK.get())) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            knot.remove(ModRegistries.KNOT_MARK.get());
            say(player, Component.translatable("item.alchyforbidden.rift_knot.unmarked"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModRegistries.KNOT_MARK.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        GlobalPos mark = stack.get(ModRegistries.KNOT_MARK.get());
        if (mark != null) {
            builder.accept(Component.translatable("item.alchyforbidden.rift_knot.tooltip.mark", mark.pos().getX(), mark.pos().getY(), mark.pos().getZ())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        builder.accept(Component.translatable("item.alchyforbidden.rift_knot.tooltip.1").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.alchyforbidden.rift_knot.tooltip.2").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.alchyforbidden.rift_knot.tooltip.3").withStyle(ChatFormatting.GRAY));
    }

    /** Where a knotted spawner's mobs gather: its point, or null to gather round the spawner itself. */
    public static BlockPos target(BlockEntity spawner) {
        if (!spawner.hasData(ModRegistries.KNOT_TARGET)) return null;
        BlockPos target = spawner.getData(ModRegistries.KNOT_TARGET);
        return target.equals(spawner.getBlockPos()) ? null : target;
    }
}
