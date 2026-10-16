package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.block.entity.ResonatorBlockEntity;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The resonator's pearl on its round (animation files: {"type": "figure", "figure": "alchyforbidden:echo_pearl",
 * "at": "core", "display": "pearl"}): drawn where the resonator says it is (ResonatorBlockEntity.pearlAt), spinning,
 * facing the viewer. At the third tier it is the warden's heart, larger, beating.
 */
public final class EchoPearlFigure implements CodedFigure {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "echo_pearl");

    private static final class Memory {
        ItemStackRenderState state;
        ItemStack item = ItemStack.EMPTY;
        long made;
    }

    private static final Map<Object, Memory> MEMORY = new WeakHashMap<>();

    public static void register() {
        Figures.register(ID, new EchoPearlFigure());
    }

    @Override
    public void submit(Context context, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack shown = context.display();
        if (shown.isEmpty() || minecraft.level == null || !(context.owner() instanceof ResonatorBlockEntity resonator) || !resonator.isFlying()) return;
        Memory memory = MEMORY.computeIfAbsent(context.owner(), key -> new Memory());
        long now = minecraft.level.getGameTime();
        if (memory.state == null || !ItemStack.isSameItem(memory.item, shown) || now - memory.made > 100 || now < memory.made) {
            memory.item = shown.copy();
            memory.made = now;
            memory.state = new ItemStackRenderState();
            minecraft.getItemModelResolver().updateForTopItem(memory.state, shown, ItemDisplayContext.FIXED, minecraft.level, null, 0);
        }
        Vec3 at = resonator.pearlAt(context.time());
        Vec3 offset = at.subtract(context.at());
        Vec3 toViewer = camera.pos.subtract(at);
        float yaw = (float) Math.toDegrees(Math.atan2(toViewer.x, toViewer.z));
        float scale = 0.4F;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(context.yaw() - 180)); // back to the block's own frame
        poseStack.translate(offset.x, offset.y, offset.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(context.time() * 12)); // tumbling on its way
        poseStack.scale(scale, scale, scale);
        memory.state.submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
