package com.lealex.alchyforbidden.item;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.block.entity.AltarBlockEntity;
import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jspecify.annotations.Nullable;

/**
 * The eye. Crafted from an eye of ender, echo shards and an amethyst shard, it is what the rift's eye sees through
 * up here. Used, it says in one line what it wants next and remembers where that is (the GAZE component); held, it
 * looks that way (client/EyeGaze draws motes toward the spot). It never says what it is.
 *
 * What it wants, in order: the altar near the player (its base, its next tier's gifts, or rest when it is whole);
 * with no altar near, the altar set down if the player carries one; else the echo the altar is carved with,
 * pointing at the nearest ancient city when the player has none.
 */
public class RiftEyeItem extends Item {
    /** The structures the eye points at for echo (data/alchyforbidden/tags/worldgen/structure/eye_seeks.json). */
    public static final TagKey<Structure> SEEKS = TagKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "eye_seeks"));
    private static final int COOLDOWN = 30;

    public RiftEyeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            Goal goal = goalFor(server, serverPlayer);
            if (goal.at() == null) stack.remove(ModRegistries.GAZE.get());
            else stack.set(ModRegistries.GAZE.get(), GlobalPos.of(server.dimension(), goal.at()));
            serverPlayer.sendSystemMessage(goal.line().copy().withStyle(ChatFormatting.LIGHT_PURPLE), true);
            server.playSound(null, player.blockPosition(), ModRegistries.EYE_WHISPER.get(), SoundSource.PLAYERS, 0.7f,
                    0.9f + server.getRandom().nextFloat() * 0.2f);
            player.getCooldowns().addCooldown(stack, COOLDOWN);
        }
        return InteractionResult.SUCCESS;
    }

    /** What the eye wants: one line, and where (null = nowhere to look). */
    private record Goal(Component line, @Nullable BlockPos at) {}

    private static Goal goalFor(ServerLevel level, ServerPlayer player) {
        AltarBlockEntity altar = nearestAltar(level, player.blockPosition(), Config.eyeAltarRange());
        if (altar != null) return new Goal(altar.statusLine(), altar.getBlockPos());
        if (player.getInventory().hasAnyMatching(stack -> stack.is(ModRegistries.ALTAR_ITEM.get()))) {
            return new Goal(Component.translatable("eye.alchyforbidden.set_altar"), null);
        }
        if (player.getInventory().hasAnyMatching(stack -> stack.is(Items.ECHO_SHARD))) {
            return new Goal(Component.translatable("eye.alchyforbidden.carve_altar"), null);
        }
        int chunks = Config.eyeSearchChunks();
        BlockPos city = chunks <= 0 ? null : level.findNearestMapStructure(SEEKS, player.blockPosition(), chunks, false);
        if (city == null) return new Goal(Component.translatable("eye.alchyforbidden.no_echo"), null);
        return new Goal(Component.translatable("eye.alchyforbidden.seek_echo"), city);
    }

    /** The nearest altar core within range, read from the loaded chunks' block entities. */
    private static @Nullable AltarBlockEntity nearestAltar(ServerLevel level, BlockPos from, int range) {
        AltarBlockEntity best = null;
        double bestDistance = (double) range * range;
        for (int cx = (from.getX() - range) >> 4; cx <= (from.getX() + range) >> 4; cx++) {
            for (int cz = (from.getZ() - range) >> 4; cz <= (from.getZ() + range) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (!(entity instanceof AltarBlockEntity altar)) continue;
                    double distance = entity.getBlockPos().distSqr(from);
                    if (distance <= bestDistance) {
                        bestDistance = distance;
                        best = altar;
                    }
                }
            }
        }
        return best;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.alchyforbidden.rift_eye.tooltip.1").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.alchyforbidden.rift_eye.tooltip.2").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("item.alchyforbidden.rift_eye.tooltip.3").withStyle(ChatFormatting.GRAY));
    }
}
