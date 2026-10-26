package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.essence.Inscription;
import com.lealex.alchyforbidden.essence.Inscriptions;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * The wings two carvings grow on a player's back, each ONE big pixel-art picture of a dragon's wing (tools/textures.py
 * carved_wings(), 64 x 64) laid on a single plane hinged at the shoulder blade, its bones standing out of it as cubes
 * (BONES: the arm and four fingers, three cubes each stepping thinner, knuckle cubes at the joints, a wrist, a claw), the right wing the left one mirrored,
 * parented to the body (they lean with it). Two passes: the wing, then its glow (a strip of GLOW_FRAMES frames stepped
 * through by time: a pulse running out along the veins, the sculk's hearts beating). Echo Wings, corrupted by the
 * sculk: swept back and folded along the back, spread and beating back and forth while the player flies. Chained Wings,
 * the same wing in iron and violet: folded, held out while it glides (the echo ones too) (not drawn when the echo wings are carved too).
 * Four quads a player a frame.
 */
public final class WingLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** Which wings a player carries and how it moves: bit 1 echo, 2 chained, 4 flying (creative-style). */
    static final ContextKey<Integer> WINGS = new ContextKey<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "wings"));
    /** How strong its carvings are (bigger wings). */
    static final ContextKey<Float> POWER = new ContextKey<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "wing_power"));
    private static final int GLOW_FRAMES = 8;
    private static final Identifier ECHO = tex("echo_wing"), ECHO_GLOW = tex("echo_wing_glow");
    private static final Identifier CHAINED = tex("chained_wing"), CHAINED_GLOW = tex("chained_wing_glow");
    private static final Identifier BONE = tex("wing_bone");

    /**
     * The bones, as cubes on the wing (in the 128-unit drawing the picture was made from): the arm from the hinge to
     * the wrist, four fingers from the wrist to the tips; each is three cubes stepping thinner, with a knuckle cube at
     * every joint.
     */
    private static final float[][] BONES = {
            {4, 64, 70, 18, 5.0F}, {70, 18, 124, 26, 3.4F}, {70, 18, 121, 62, 3.4F}, {70, 18, 106, 98, 3.4F}, {70, 18, 76, 122, 3.4F}};

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/entity/" + name + ".png");
    }

    /** A wing's picture, in blocks (it is square), and where the hinge sits in it (the arm's root: 4 / 128, 64 / 128). */
    private static final float SIZE = 2.2F, HINGE_U = 4F / 128F, HINGE_V = 64F / 128F;

    public WingLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (com.lealex.alchyx.client.scene.BodyPoses.posed(state)) return; // posed in a scene (lying on the bier): folded away
        int wings = state.getRenderDataOrDefault(WINGS, 0);
        if (wings == 0 || state.isInvisible) return;
        boolean echo = (wings & 1) != 0;
        boolean flying = echo && (wings & 4) != 0 && !state.isFallFlying;
        boolean open = flying || state.isFallFlying; // both open while gliding
        float power = state.getRenderDataOrDefault(POWER, 1F);
        float t = state.ageInTicks;
        // how far each wing is swept back from straight out (radians), how much it is lifted, its size
        float sweep, lift, size;
        if (flying) { // flying: beating back and forth
            float beat = 0.5F + 0.5F * Mth.sin(t * 0.32F);
            sweep = 0.2F + beat * 0.75F;
            lift = 0.15F + (1 - beat) * 0.15F;
            size = SIZE * (float) Math.pow(power, 0.3);
        } else if (open) { // gliding: held out
            sweep = 0.3F + Mth.sin(t * 0.08F) * 0.04F;
            lift = 0.1F;
            size = SIZE * (float) Math.pow(power, 0.3);
        } else { // folded along the back, breathing
            sweep = 1.3F + Mth.sin(t * 0.05F) * 0.03F;
            lift = 0.2F;
            size = SIZE * 0.7F;
        }
        int frame = Math.floorMod((int) (t / 3), GLOW_FRAMES);
        float s = size;
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack); // they ride the body (it leans when crouching)
        poseStack.translate(0.0F, 0.18F, 0.16F); // the shoulder blades, just behind them
        for (int side = -1; side <= 1; side += 2) {
            final int sd = side;
            poseStack.pushPose();
            poseStack.translate(side * 0.06F, 0.0F, 0.0F);
            poseStack.mulPose(Axis.YP.rotation(side * -sweep)); // swept back (behind the body is +z)
            poseStack.mulPose(Axis.ZP.rotation(side * -lift));  // the outer end raised a little (up is -y here)
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(echo ? ECHO : CHAINED), (pose, buffer) ->
                    wing(pose, buffer, sd, s, 0F, 1F, light));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(BONE), (pose, buffer) -> bones(pose, buffer, sd, s, light));
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(echo ? ECHO_GLOW : CHAINED_GLOW), (pose, buffer) ->
                    wing(pose, buffer, sd, s, frame / (float) GLOW_FRAMES, (frame + 1) / (float) GLOW_FRAMES, LightCoordsUtil.FULL_BRIGHT));
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /**
     * One wing's plane, the hinge at the origin: across = outward (side), down = +y (the body's frame is y down). The
     * picture's hinge (HINGE_U, HINGE_V) sits on the origin; the other side mirrors the u.
     */
    private static void wing(PoseStack.Pose pose, VertexConsumer buffer, int side, float size, float v0, float v1, int light) {
        float x0 = -HINGE_U * size, x1 = (1 - HINGE_U) * size;
        float y0 = -HINGE_V * size, y1 = (1 - HINGE_V) * size;
        float u0 = 0F, u1 = 1F;
        float[][] p = {
                {side * x0, y0, u0, v0}, {side * x1, y0, u1, v0}, {side * x1, y1, u1, v1}, {side * x0, y1, u0, v1}};
        int color = 0xFFFFFFFF;
        for (int pass = 0; pass < 2; pass++) { // both faces
            for (int k = 0; k < 4; k++) {
                float[] q = p[pass == 0 ? k : 3 - k];
                float v = v0 + (v1 - v0) * (q[1] - y0) / (y1 - y0);
                buffer.addVertex(pose, q[0], q[1], 0).setColor(color).setUv(q[2], v).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light).setNormal(pose, 0, 0, pass == 0 ? 1 : -1);
            }
        }
    }

    /** The bones' cubes, in the wing's own frame (the same mapping as the picture: hinge at the origin, y down). */
    private static void bones(PoseStack.Pose pose, VertexConsumer buffer, int side, float size, int light) {
        float unit = size / 128F;
        for (float[] b : BONES) {
            float ax = side * (b[0] - HINGE_U * 128F) * unit, ay = (b[1] - HINGE_V * 128F) * unit;
            float bx = side * (b[2] - HINGE_U * 128F) * unit, by = (b[3] - HINGE_V * 128F) * unit;
            float thick = b[4] * unit;
            for (int k = 0; k < 3; k++) { // three cubes, each thinner than the last
                float t0 = k / 3F, t1 = (k + 1) / 3F;
                float h = thick * (1F - k * 0.22F) * 0.5F;
                com.lealex.alchyx.client.Cubes.beam(pose, buffer, ax + (bx - ax) * t0, ay + (by - ay) * t0, ax + (bx - ax) * t1, ay + (by - ay) * t1, h, h, 0xFFFFFFFF, light);
                if (k > 0) { // a knuckle at the joint
                    float jx = ax + (bx - ax) * t0, jy = ay + (by - ay) * t0, kh = h * 1.45F;
                    float dx = (bx - ax), dy = (by - ay), len = Mth.sqrt(dx * dx + dy * dy);
                    float ux = dx / len * kh, uy = dy / len * kh;
                    com.lealex.alchyx.client.Cubes.beam(pose, buffer, jx - ux, jy - uy, jx + ux, jy + uy, kh, kh, 0xFFFFFFFF, light);
                }
            }
        }
        float wx = side * (70 - HINGE_U * 128F) * unit, wy = (18 - HINGE_V * 128F) * unit, w = 3.6F * unit; // the wrist
        com.lealex.alchyx.client.Cubes.beam(pose, buffer, wx - w, wy, wx + w, wy, w, w, 0xFFFFFFFF, light);
        float cx = side * (68 - HINGE_U * 128F) * unit, cy = (8 - HINGE_V * 128F) * unit; // the claw, up from the wrist
        com.lealex.alchyx.client.Cubes.beam(pose, buffer, wx, wy, cx, cy, w * 0.45F, w * 0.45F, 0xFFFFFFFF, light); // from inside the wrist: no face shared with it
        com.lealex.alchyx.client.Cubes.beam(pose, buffer, 0, -w, 0, w, w * 1.1F, w * 1.1F, 0xFFFFFFFF, light); // the shoulder
    }

    // ---- Registration (client setup) ----

    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            var renderer = event.getPlayerRenderer(skin);
            if (renderer != null) renderer.addLayer(new WingLayer(renderer));
        }
    }

    public static void onRegisterModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends net.minecraft.world.entity.Avatar & net.minecraft.client.entity.ClientAvatarEntity> void accept(T entity, AvatarRenderState state) {
                int wings = 0;
                float power = 1F;
                if (entity instanceof Player player) {
                    if (Inscriptions.has(player, Inscription.ECHO_WINGS)) wings |= 1;
                    if (Inscriptions.has(player, Inscription.CHAINED_WINGS)) wings |= 2;
                    // spread only for the wings' own flight: a creative / spectator flight is not theirs
                    if (player.getAbilities().flying && !player.isCreative() && !player.isSpectator()) wings |= 4;
                    if (wings != 0) power = (float) Inscriptions.power(player);
                }
                state.setRenderData(WINGS, wings);
                state.setRenderData(POWER, power);
            }
        });
    }
}
