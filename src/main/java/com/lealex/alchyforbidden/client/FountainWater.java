package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.block.entity.WeepingFountainBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;

/**
 * The water in every formed Weeping Fountain's basin (the 3 x 3 over its core): the game's own still water, tinted
 * a deep weeping blue, rippling a little; and the item it has just wept, rising out of the pool, turning, held over
 * the spout and gone into the store.
 */
public final class FountainWater {
    private FountainWater() {}

    private static final ContextKey<List<Shown>> RENDER_KEY =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "fountain_water"));
    private static final Identifier WATER = Identifier.withDefaultNamespace("textures/block/water_still.png");
    private static final int FRAMES = 32; // water_still.png is a strip of 32 frames
    private static final int TINT = 0x3A5CC8;

    private record Shown(BlockPos core, double water, int light, int frame, float time, @Nullable ItemStackRenderState item,
                         Vec3 itemAt, float itemScale, float itemTurn) {}

    /** The item state per fountain, made again when it weeps another. */
    private static final Map<WeepingFountainBlockEntity, Made> MADE = new WeakHashMap<>();

    private static final class Made {
        long at;
        ItemStackRenderState state;
    }

    public static void onExtract(ExtractLevelRenderStateEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            event.getRenderState().setRenderData(RENDER_KEY, null);
            return;
        }
        float time = level.getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<Shown> shown = new ArrayList<>();
        for (WeepingFountainBlockEntity fountain : WeepingFountainBlockEntity.loaded(level)) {
            if (fountain.isRemoved() || !fountain.isFormed()) continue;
            BlockPos core = fountain.getBlockPos();
            BlockPos pool = core.above();
            int light = LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, pool), level.getBrightness(LightLayer.SKY, pool));
            ItemStackRenderState item = null;
            Vec3 itemAt = Vec3.ZERO;
            float scale = 0, turn = 0;
            float t = (time - fountain.madeAt()) * fountain.showSpeed();
            if (!fountain.made().isEmpty() && t >= 0 && t < WeepingFountainBlockEntity.MADE_TICKS) {
                Made made = MADE.computeIfAbsent(fountain, key -> new Made());
                if (made.state == null || made.at != fountain.madeAt()) {
                    made.at = fountain.madeAt();
                    made.state = new ItemStackRenderState();
                    minecraft.getItemModelResolver().updateForTopItem(made.state, fountain.made(), ItemDisplayContext.GROUND, level, null, 0);
                }
                item = made.state;
                itemAt = fountain.madePos(t);
                scale = fountain.madeScale(t);
                turn = t * (t < WeepingFountainBlockEntity.FORM ? 18F : 9F);
            }
            shown.add(new Shown(core, fountain.waterY() + 0.02 * Mth.sin(time * 0.08F), light, (int) (time / 2) % FRAMES, time,
                    item, itemAt, scale, turn));
        }
        event.getRenderState().setRenderData(RENDER_KEY, shown.isEmpty() ? null : shown);
    }

    public static void onSubmit(SubmitCustomGeometryEvent event) {
        List<Shown> shown = event.getLevelRenderState().getRenderData(RENDER_KEY);
        if (shown == null) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        for (Shown pool : shown) {
            poseStack.pushPose();
            poseStack.translate(pool.core().getX() - camera.x, pool.water() - camera.y, pool.core().getZ() - camera.z);
            float v0 = pool.frame() / (float) FRAMES, v1 = (pool.frame() + 1) / (float) FRAMES;
            int color = 0xC0 << 24 | TINT;
            int light = pool.light();
            event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(WATER), (pose, buffer) -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        float x0 = dx, x1 = dx + 1, z0 = dz, z1 = dz + 1;
                        float[][] corners = {{x0, z0, 0, v0}, {x0, z1, 0, v1}, {x1, z1, 1, v1}, {x1, z0, 1, v0}};
                        for (int pass = 0; pass < 2; pass++) { // seen from above and from under it
                            for (int k = 0; k < 4; k++) {
                                float[] c = corners[pass == 0 ? k : 3 - k];
                                buffer.addVertex(pose, c[0], 0, c[1]).setColor(color).setUv(c[2], c[3]).setOverlay(OverlayTexture.NO_OVERLAY)
                                        .setLight(light).setNormal(pose, 0, pass == 0 ? 1 : -1, 0);
                            }
                        }
                    }
                }
            });
            poseStack.popPose();
            if (pool.item() != null && pool.itemScale() > 0.01F) {
                poseStack.pushPose();
                poseStack.translate(pool.itemAt().x - camera.x, pool.itemAt().y - camera.y, pool.itemAt().z - camera.z);
                poseStack.mulPose(Axis.YP.rotationDegrees(pool.itemTurn()));
                float s = pool.itemScale() * 1.2F;
                poseStack.scale(s, s, s);
                pool.item().submit(poseStack, event.getSubmitNodeCollector(), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
                poseStack.popPose();
            }
        }
    }
}
