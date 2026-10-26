package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.essence.BierRest;
import com.lealex.alchyforbidden.essence.Inscription;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * The Inscribing Bier's knife carving a lying body (essence/BierRest.Act.CARVE): it rides on the carved part's own
 * model part (so it follows the body as it arches, an arm held out, a knee drawn up) and cuts strokes into the skin
 * there, one every 8 ticks between hashed points of the part's face, lifting between them, with sparks of violet at
 * the tip while it cuts. It grows in at the start and shrinks away at the end. Drawn in cubes from
 * textures/entity/bier_knife.png (tools/textures.py bier_knife(): blade, guard, grip, pommel in bands).
 */
public class BierKnifeLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** The knife this frame: carved part (ordinal), ticks into the act, the entity's id, mirrored (1: the left arm / leg). Empty: none. */
    public static final ContextKey<float[]> KNIFE = new ContextKey<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "bier_knife"));
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/entity/bier_knife.png");
    private static final int STROKE = 8;
    /** The last tick sparks were thrown for each entity id (once a tick, however many frames). */
    private static final Map<Integer, Long> SPARKED = new HashMap<>();

    /** A part's face the knife works: which model part, the face's middle and its half sizes (blocks), out = -z or +z. */
    private record Face(int part, float x, float y, float z, float halfX, float halfY, boolean back) {}

    public BierKnifeLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    private static Face face(Inscription.Part part) {
        float px = 1F / 16F;
        return switch (part) {
            case HEAD -> new Face(0, 0F, -4 * px, -4 * px, 0.14F, 0.11F, false); // the forehead and face
            case CHEST -> new Face(1, 0F, 5 * px, -2 * px, 0.17F, 0.2F, false);
            case BACK -> new Face(1, 0F, 5 * px, 2 * px, 0.17F, 0.22F, true); // the body rolled on its side
            case ARMS -> new Face(2, -1 * px, 6 * px, -2 * px, 0.05F, 0.2F, false); // the right forearm, held out
            case LEGS -> new Face(3, 0F, 4 * px, -2 * px, 0.06F, 0.16F, false); // the right thigh, knee up
            case FEET -> new Face(3, 0F, 10.5F * px, -2 * px, 0.06F, 0.06F, false);
            case SKIN -> new Face(1, 0F, 6 * px, -2 * px, 0.2F, 0.32F, false); // the whole front, long strokes
        };
    }

    private ModelPart modelPart(int which, boolean mirror) {
        PlayerModel model = getParentModel();
        return switch (which) {
            case 0 -> model.head;
            case 2 -> mirror ? model.leftArm : model.rightArm;
            case 3 -> mirror ? model.leftLeg : model.rightLeg;
            default -> model.body;
        };
    }

    /** A hashed number in -1..1 for a stroke. */
    private static float hash(int stroke, int k) {
        int h = stroke * 0x9E3779B1 + k * 0x85EBCA77;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h & 0xFFFF) / 32767.5F - 1F;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        float[] knife = state.getRenderData(KNIFE);
        if (knife == null || knife.length != 4) return;
        boolean mirror = knife[3] > 0.5F;
        Inscription.Part part = Inscription.Part.values()[Math.floorMod((int) knife[0], Inscription.Part.values().length)];
        float t = knife[1];
        float p = Mth.clamp(t / BierRest.CARVE_ACT, 0F, 1F);
        float grow = smooth((p - 0.06F) / 0.08F) * smooth((0.94F - p) / 0.08F); // grows in, shrinks away
        if (grow <= 0.001F) return;
        Face face = face(part);
        // the stroke: from one hashed point of the face to another, cutting in the middle of it, lifted at its ends
        int stroke = Mth.floor(t / STROKE);
        float f = (t - stroke * STROKE) / STROKE;
        float cut = smooth((f - 0.15F) / 0.7F);
        float u = Mth.lerp(cut, hash(stroke, 1), hash(stroke, 3)) * face.halfX(), v = Mth.lerp(cut, hash(stroke, 2), hash(stroke, 4)) * face.halfY();
        boolean touching = f > 0.15F && f < 0.85F;
        float lift = touching ? 0.004F : 0.004F + 0.07F * Mth.sin(Mth.clamp(f < 0.5F ? (0.15F - f) / 0.15F : (f - 0.85F) / 0.15F, 0F, 1F) * Mth.HALF_PI);

        poseStack.pushPose();
        modelPart(face.part(), mirror).translateAndRotate(poseStack);
        float out = face.back() ? 1F : -1F;
        poseStack.translate((mirror ? -face.x() : face.x()) + u, face.y() + v, face.z() + out * lift);
        poseStack.mulPose(Axis.XP.rotation(face.back() ? Mth.HALF_PI : -Mth.HALF_PI)); // the knife's +y out of the skin
        poseStack.mulPose(Axis.ZP.rotation(0.45F + 0.1F * hash(stroke, 5))); // held at a slant
        poseStack.mulPose(Axis.XP.rotation(0.3F * (hash(stroke, 3) - hash(stroke, 1)))); // leaning into the stroke
        poseStack.scale(grow, grow, grow);
        if (touching) spark(poseStack, state, (int) knife[2], part);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(TEXTURE), (pose, buffer) -> {
            // tip, blade, guard, grip, pommel (uv bands of the sheet: blade 0-7, guard 8-10, grip 11-14, pommel 15)
            box(pose, buffer, -0.012F, 0.0F, -0.005F, 0.012F, 0.07F, 0.005F, 0, 0, 8, light);
            box(pose, buffer, -0.026F, 0.07F, -0.007F, 0.026F, 0.28F, 0.007F, 0, 0, 8, light);
            box(pose, buffer, -0.065F, 0.28F, -0.018F, 0.065F, 0.305F, 0.018F, 0, 8, 11, light);
            box(pose, buffer, -0.018F, 0.305F, -0.018F, 0.018F, 0.47F, 0.018F, 0, 11, 15, light);
            box(pose, buffer, -0.026F, 0.47F, -0.026F, 0.026F, 0.5F, 0.026F, 0, 15, 16, light);
        });
        poseStack.popPose();
    }

    /** Violet sparks off the tip while it cuts, once a tick. */
    private static void spark(PoseStack poseStack, AvatarRenderState state, int id, Inscription.Part part) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.isPaused()) return;
        long now = minecraft.level.getGameTime();
        Long last = SPARKED.get(id);
        if (last != null && last == now) return;
        SPARKED.put(id, now);
        if (SPARKED.size() > 64) SPARKED.clear();
        Vector3f tip = poseStack.last().pose().transformPosition(0F, 0F, 0F, new Vector3f()); // from the camera
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        double x = camera.x + tip.x(), y = camera.y + tip.y(), z = camera.z + tip.z();
        var random = minecraft.level.getRandom();
        minecraft.level.addParticle(new DustParticleOptions(0x8A5CFF, 0.45F), x, y, z, 0, 0.01, 0);
        if (random.nextInt(3) == 0) minecraft.level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                (random.nextFloat() - 0.5F) * 0.06F, 0.04F, (random.nextFloat() - 0.5F) * 0.06F);
        if (random.nextInt(9) == 0) minecraft.level.addParticle(ParticleTypes.SCULK_CHARGE_POP, x, y, z, 0, 0.01, 0);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    /** An upright box with one band of the sheet (rows v0..v1 of 16) on every face (AlchyX Cubes). */
    private static void box(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1,
                            int u0, int v0, int v1, int light) {
        com.lealex.alchyx.client.Cubes.box(pose, buffer, x0, y0, z0, x1, y1, z1, u0 / 16F, v0 / 16F, 1F, v1 / 16F, 0xFFFFFFFF, light);
    }

    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (var skin : event.getSkins()) {
            var renderer = event.getPlayerRenderer(skin);
            if (renderer != null) renderer.addLayer(new BierKnifeLayer(renderer));
        }
    }
}
