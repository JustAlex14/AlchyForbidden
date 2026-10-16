package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.block.entity.LamentWellBlockEntity;
import com.lealex.alchyx.client.RiftDecal;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * The rift open in the hole of every formed Lament Well (the block over its core): teal at its edge, breathing, a
 * little wider at each tier and with each ghost weeping round it.
 */
public final class WellRifts {
    private WellRifts() {}

    private static final ContextKey<List<Shown>> RENDER_KEY =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "well_rifts"));
    private static final int TEAL = 0x3FC6D0;

    private record Shown(BlockPos at, float size, float open, float time) {}

    public static void onExtract(ExtractLevelRenderStateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            event.getRenderState().setRenderData(RENDER_KEY, null);
            return;
        }
        float time = level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<Shown> shown = new ArrayList<>();
        for (LamentWellBlockEntity well : LamentWellBlockEntity.loaded(level)) {
            if (well.isRemoved() || !well.isFormed()) continue;
            float size = 0.5F + 0.1F * well.standing() + 0.02F * Math.min(16, well.ghostCount());
            shown.add(new Shown(well.getBlockPos().above(), size, 0.85F + 0.15F * Mth.sin(time * 0.07F), time));
        }
        event.getRenderState().setRenderData(RENDER_KEY, shown.isEmpty() ? null : shown);
    }

    public static void onSubmit(SubmitCustomGeometryEvent event) {
        List<Shown> shown = event.getLevelRenderState().getRenderData(RENDER_KEY);
        if (shown == null) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        for (Shown rift : shown) {
            poseStack.pushPose();
            poseStack.translate(rift.at().getX() + 0.5 - camera.x, rift.at().getY() + 0.45 - camera.y, rift.at().getZ() + 0.5 - camera.z);
            float breath = 1 + 0.05F * Mth.sin(rift.time() * 0.12F);
            RiftDecal.submit(poseStack, event.getSubmitNodeCollector(), event.getLevelRenderState().cameraRenderState,
                    rift.size() * breath, (int) (rift.at().asLong() & 3), rift.open(), TEAL);
            poseStack.popPose();
        }
    }
}
