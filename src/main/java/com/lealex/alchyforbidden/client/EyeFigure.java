package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyx.client.animation.CodedFigure;
import com.lealex.alchyx.client.animation.Figures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The eye over the altar (animation files: {"type": "figure", "figure": "alchyforbidden:eye", "display": "eye"}): it
 * drifts round above the core on a slow wandering path, leans a little toward the viewer, and always turns to look at
 * them, smoothly. Through a rite or a growth it drifts faster and closer in. Drawn from the eye item itself (the
 * animated texture blinks and glances on its own).
 */
public final class EyeFigure implements CodedFigure {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "eye");

    private static final class Memory {
        ItemStackRenderState state;
        ItemStack item = ItemStack.EMPTY;
        long made;
        float yaw, pitch;
        boolean aimed;
        float riseStart = -1; // when its altar began rising with the platform (-1: not rising)
    }

    private static final Map<Object, Memory> MEMORY = new WeakHashMap<>();
    /** As long as the platform takes to rise (client/AltarRifts RISE_TICKS). */
    private static final float RISE_TICKS = 60;

    public static void register() {
        Figures.register(ID, new EyeFigure());
    }

    @Override
    public void submit(Context context, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        ItemStack eye = context.display();
        Minecraft minecraft = Minecraft.getInstance();
        if (eye.isEmpty() || minecraft.level == null) return;
        Memory memory = MEMORY.computeIfAbsent(context.owner(), key -> new Memory());
        long now = minecraft.level.getGameTime();
        if (memory.state == null || !ItemStack.isSameItem(memory.item, eye) || now - memory.made > 100 || now < memory.made) {
            memory.item = eye.copy();
            memory.made = now;
            memory.state = new ItemStackRenderState();
            minecraft.getItemModelResolver().updateForTopItem(memory.state, eye, ItemDisplayContext.FIXED, minecraft.level, null, 0);
        }

        // Where it drifts: two slow circles laid over each other, a bob, a lean toward the viewer
        float t = context.time() * (context.working() ? 2.2F : 1.0F);
        float reach = context.working() ? 0.18F : 0.38F;
        double x = Mth.sin(t * 0.031F) * reach + Mth.sin(t * 0.083F) * 0.08F;
        double z = Mth.cos(t * 0.027F) * reach + Mth.cos(t * 0.071F) * 0.08F;
        double y = 0.55 + Mth.sin(t * 0.05F) * 0.12F;
        // Its altar rising with the platform (hidden where it stands, slid up a block): the eye goes up with it, until
        // the altar really moves and the eye's place is a block higher anyway
        if (context.owner() instanceof net.minecraft.world.level.block.entity.BlockEntity core
                && core.getBlockState().hasProperty(com.lealex.alchyforbidden.block.AltarBlock.RISING)
                && core.getBlockState().getValue(com.lealex.alchyforbidden.block.AltarBlock.RISING)) {
            if (memory.riseStart < 0) memory.riseStart = context.time();
            float k = Mth.clamp((context.time() - memory.riseStart) / RISE_TICKS, 0, 1);
            y += k * k * (3 - 2 * k);
        } else {
            memory.riseStart = -1;
        }
        Vec3 here = context.at().add(x, y, z);
        Vec3 toViewer = camera.pos.subtract(here);
        Vec3 flat = new Vec3(toViewer.x, 0, toViewer.z);
        if (flat.lengthSqr() > 1.0E-4) {
            Vec3 lean = flat.normalize().scale(0.15);
            x += lean.x;
            z += lean.z;
            here = context.at().add(x, y, z);
            toViewer = camera.pos.subtract(here);
        }

        // Which way it looks: at the viewer, turned smoothly toward them a little each frame
        float wantYaw = (float) Math.toDegrees(Math.atan2(toViewer.x, toViewer.z));
        float wantPitch = (float) Math.toDegrees(Math.atan2(toViewer.y, Math.sqrt(toViewer.x * toViewer.x + toViewer.z * toViewer.z)));
        if (!memory.aimed) {
            memory.yaw = wantYaw;
            memory.pitch = wantPitch;
            memory.aimed = true;
        }
        memory.yaw += Mth.wrapDegrees(wantYaw - memory.yaw) * 0.15F;
        memory.pitch += (wantPitch - memory.pitch) * 0.15F;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(context.yaw() - 180)); // back to the block's own frame
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(memory.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-memory.pitch));
        poseStack.scale(0.7F, 0.7F, 0.7F);
        memory.state.submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
