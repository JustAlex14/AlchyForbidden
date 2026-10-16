package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.block.entity.AltarBlockEntity;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.lealex.alchyx.animation.LoadedCores;
import com.lealex.alchyx.block.entity.MultiblockCoreBlockEntity;
import com.lealex.alchyx.client.Veil;
import com.lealex.alchyx.multiblock.MultiblockPattern;
import com.lealex.alchyx.multiblock.MultiblockPatterns;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * The eye's vision: while the player holds an eye, every formed altar near them shows the blocks of the tier it would
 * grow next as violet, see-through shapes that breathe (AlchyX Veil). Not ghost blocks to build: what the rift will
 * grow by itself. Worked out every 10 ticks, only while an eye is in a hand.
 */
public final class AltarVision {
    private AltarVision() {}

    private static final int REFRESH_TICKS = 10;
    private static final double RANGE = 32.0;
    private static final ContextKey<List<Seen>> RENDER_KEY =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "altar_vision"));

    private record Seen(BlockPos pos, BlockState look) {}

    private static List<Seen> seen = List.of();

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            seen = List.of();
            return;
        }
        if (level.getGameTime() % REFRESH_TICKS != 0) return;
        boolean holding = player.getItemInHand(InteractionHand.MAIN_HAND).is(ModRegistries.RIFT_EYE.get())
                || player.getItemInHand(InteractionHand.OFF_HAND).is(ModRegistries.RIFT_EYE.get());
        if (!holding) {
            seen = List.of();
            return;
        }
        List<Seen> found = new ArrayList<>();
        for (MultiblockCoreBlockEntity core : LoadedCores.snapshot()) {
            if (!(core instanceof com.lealex.alchyforbidden.block.entity.GrowingCoreBlockEntity altar) || altar.getLevel() != level) continue;
            if (Vec3.atCenterOf(altar.getBlockPos()).distanceToSqr(player.position()) > RANGE * RANGE) continue;
            int next = altar.nextTier();
            MultiblockPattern pattern = next == 0 ? null : MultiblockPatterns.client().get(altar.patternIdFor(next));
            if (pattern == null) continue;
            for (MultiblockPattern.Conversion conversion : pattern.conversions(altar.coreFor(next), altar.rotation())) {
                // Only where something new will stand: a part already there that only changes its look (the floor,
                // a stand becoming circle) is left alone, or its see-through double sits on it and the two seem fused
                if (conversion.result().hidden() || conversion.pos().equals(altar.getBlockPos())
                        || AltarBlockEntity.grown(level, conversion)
                        || com.lealex.alchyx.multiblock.ShellParts.isShell(level.getBlockState(conversion.pos()))
                        || level.getBlockState(conversion.pos()).is(AltarBlockEntity.REAL_PARTS)) continue;
                found.add(new Seen(conversion.pos().immutable(), conversion.disguise(Blocks.AIR.defaultBlockState())));
            }
            // where the core will rise to
            if (!altar.coreFor(next).equals(altar.getBlockPos())) found.add(new Seen(altar.coreFor(next), altar.getBlockState()));
        }
        seen = found;
    }

    public static void onExtract(ExtractLevelRenderStateEvent event) {
        event.getRenderState().setRenderData(RENDER_KEY, seen.isEmpty() ? null : seen);
    }

    public static void onSubmit(SubmitCustomGeometryEvent event) {
        List<Seen> cells = event.getLevelRenderState().getRenderData(RENDER_KEY);
        Minecraft minecraft = Minecraft.getInstance();
        if (cells == null || minecraft.level == null) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        float time = minecraft.level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (Seen cell : cells) {
            // each block breathes a little out of step with the next, so the vision shimmers
            float breath = 0.5F + 0.5F * (float) Math.sin(time / 9.0 + (cell.pos().getX() * 7 + cell.pos().getY() * 3 + cell.pos().getZ() * 5) * 0.6);
            int alpha = 50 + Math.round(70 * breath);
            int color = (alpha << 24) | 0xB88CFF;
            poseStack.pushPose();
            poseStack.translate(cell.pos().getX() - camera.x, cell.pos().getY() - camera.y, cell.pos().getZ() - camera.z);
            // a hair smaller than a block, so it never fights with the faces around it
            poseStack.translate(0.5, 0.5, 0.5);
            poseStack.scale(0.98F, 0.98F, 0.98F);
            poseStack.translate(-0.5, -0.5, -0.5);
            Veil.submit(poseStack, event.getSubmitNodeCollector(), minecraft.level, cell.pos(), cell.look(), color);
            poseStack.popPose();
        }
    }
}
