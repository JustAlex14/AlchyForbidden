package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.block.entity.RiftMawBlockEntity;
import com.lealex.alchyx.client.FlatDisc;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * The wall of a Rift Maw's arena while a ritual runs: a ring of rift light standing on the arena's edge (rising out of
 * the floor as the rift opens, its streaks flowing round and up, fading toward the top), and the rune band turning on
 * the floor just inside it, so the challenger always sees where they may not go.
 */
public final class ArenaWalls {
    private ArenaWalls() {}

    private static final ContextKey<List<Shown>> RENDER_KEY =
            new ContextKey<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "arena_walls"));
    private static final Identifier WALL = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/misc/arena_wall.png");
    private static final Identifier RUNES = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/misc/rite_runes.png");
    private static final int SEGMENTS = 64, VIOLET = 0xB98CFF;
    private static final float HEIGHT = 5.0F, RISE = 30F;

    private record Shown(Vec3 centre, float radius, float height, float time) {}

    private static com.lealex.alchyforbidden.network.@org.jspecify.annotations.Nullable VoidArenaPayload voidArena;

    /** The void arena the local player stands in (or none): its wall is drawn too. */
    public static void voidArena(com.lealex.alchyforbidden.network.VoidArenaPayload payload) {
        voidArena = payload.on() ? payload : null;
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
        for (RiftMawBlockEntity maw : RiftMawBlockEntity.loaded(level)) {
            if (maw.isRemoved() || !maw.isFormed() || maw.phase() == RiftMawBlockEntity.NONE) continue;
            float since = time - maw.phaseAt();
            float rise = maw.phase() == RiftMawBlockEntity.OPENING ? Mth.clamp(since / RISE, 0, 1) : 1;
            rise = rise * rise * (3 - 2 * rise);
            BlockPos core = maw.getBlockPos();
            shown.add(new Shown(new Vec3(core.getX() + 0.5, core.getY() + 1.02, core.getZ() + 0.5), maw.arenaRadius(), HEIGHT * rise, time));
        }
        var arena = voidArena;
        if (arena != null && level.dimension() == com.lealex.alchyforbidden.familiar.VoidTrials.VOID) {
            float rise = Mth.clamp((time - arena.openedAt()) / RISE, 0, 1);
            rise = rise * rise * (3 - 2 * rise);
            BlockPos c = arena.center();
            shown.add(new Shown(new Vec3(c.getX() + 0.5, c.getY() + 1.02, c.getZ() + 0.5), arena.radius(), HEIGHT * 1.6F * rise, time));
        }
        event.getRenderState().setRenderData(RENDER_KEY, shown.isEmpty() ? null : shown);
    }

    public static void onSubmit(SubmitCustomGeometryEvent event) {
        List<Shown> shown = event.getLevelRenderState().getRenderData(RENDER_KEY);
        if (shown == null) return;
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        for (Shown arena : shown) {
            poseStack.pushPose();
            poseStack.translate(arena.centre().x - camera.x, arena.centre().y - camera.y, arena.centre().z - camera.z);
            float r = arena.radius(), h = arena.height(), t = arena.time();
            if (h > 0.05F) {
                float u0 = t * 0.006F; // the slanted streaks slide round, so they seem to climb (the fade to the top stays put)
                int pulse = (int) (200 + 40 * Mth.sin(t * 0.1F));
                int color = pulse << 24 | 0xFFFFFF;
                float repeat = Math.max(4, Math.round(r * 1.5F)); // the texture's width, about once every 4 blocks of wall
                event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(WALL),
                        (pose, buffer) -> com.lealex.alchyx.client.RingWall.band(pose, buffer, r, h, u0, repeat, SEGMENTS, color));
            }
            // the rune band on the floor just inside the wall, turning
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(t * 0.6F));
            int a = (int) (170 * Math.min(1F, h / HEIGHT + 0.3F));
            event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(RUNES),
                    (pose, buffer) -> FlatDisc.quad(pose, buffer, r * 1.06F, a << 24 | VIOLET));
            poseStack.popPose();
            poseStack.popPose();
        }
    }
}
