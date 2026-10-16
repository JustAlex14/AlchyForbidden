package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.block.entity.ResonatorBlockEntity;
import com.lealex.alchyx.client.animation.CodedFigure;
import com.lealex.alchyx.client.animation.Figures;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.warden.WardenModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.WardenRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * The dead warden on the resonator's sacrificial table (animation file resonator_3: {"type": "figure", "figure":
 * "alchyforbidden:dead_warden", "at": [0, 1, 0]}): its soul is being torn out of it. It lies on its back, an arm and
 * a leg hanging off the table, the rift's violet cracks all over it. Over its chest hangs its heart, torn up out of
 * it toward the rift crystal, its veins still hanging down into the chest; the soul still in it glows with each
 * beat (brighter while a shrieker is fed: only the glow changes, the motion never jumps). Over it the rift crystal,
 * in the rift's violet: the altar's eye in the middle, looking at whoever watches; round it the open conduit's cage,
 * turning and bobbing as the game's own does;
 * round that one end crystal glass shell, turning. The soul rising into the crystal and the
 * crystal's streams to the shriekers are particles and AltarRifts streams (ResonatorBlockEntity).
 */
public final class DeadWardenFigure implements CodedFigure {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "dead_warden");
    private static final Identifier SKIN = Identifier.withDefaultNamespace("textures/entity/warden/warden.png");
    private static final Identifier CORRUPTION = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/entity/warden_corruption.png");
    private static final Identifier CRYSTAL = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/entity/rift_crystal.png");
    private static final Identifier CAGE = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/entity/rift_crystal_cage.png");
    private static final float CRYSTAL_SIZE = 1.6F; // the glass, of vanilla's end crystal model (the entity draws it at 2)
    private static final float CAGE_SIZE = 0.85F, EYE_SIZE = 0.4F;
    /** The axis the conduit's cage turns about (the game's own). */
    private static final org.joml.Vector3f CAGE_AXIS = new org.joml.Vector3f(0.5F, 1, 0.5F).normalize();
    private static final Identifier SOUL = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/entity/warden_soul.png");
    private static final Identifier SOUL_GLOW = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/entity/warden_soul_glow.png");
    private static final int DEAD = 0xFFA89CB4; // drained, with a violet cast
    /** Over the figure's place (the table's top): the crystal's middle, the warden's chest, the core held between. */
    public static final float CRYSTAL_Y = ResonatorBlockEntity.CRYSTAL_Y, CHEST_Y = 0.45F, CORE_Y = 1.15F;

    /** The warden's model, lying limp. */
    private static final class Torn extends WardenModel {
        Torn(ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(WardenRenderState state) {
            super.setupAnim(state); // the rest pose
            head.yRot = 0.7F;             // fallen to one side
            head.xRot = -0.15F;
            rightTendril.xRot = 0.9F;     // limp
            leftTendril.xRot = 0.9F;
            leftArm.zRot = -0.65F;        // out over the table's edge, hanging down from it
            leftArm.xRot = 1.25F;
            rightArm.zRot = 0.1F;
            rightArm.xRot = 0.15F;
            rightLeg.zRot = 0.45F;        // a leg slid off the edge
            rightLeg.xRot = 1.0F;
            leftLeg.xRot = 0.05F;
        }
    }

    private static Torn model;

    private static Torn model() {
        if (model == null) model = new Torn(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.WARDEN));
        return model;
    }

    // The crystal and the heart, box models in pixels (their sheets: tools/textures.py rift_crystal, warden_soul)
    private static ModelPart heart, cage;
    private static net.minecraft.client.model.object.crystal.EndCrystalModel shells;
    private static final ModelPart[] VEINS = new ModelPart[4];

    private static void bakeModels() {
        if (heart != null) return;
        // the crystal: the end crystal's model with its heart cube hidden (the eye takes its place)
        shells = new net.minecraft.client.model.object.crystal.EndCrystalModel(
                Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.END_CRYSTAL));
        shells.cube.visible = false;
        shells.innerGlass.visible = false; // one glass shell only
        cage = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.CONDUIT_CAGE);
        MeshDefinition mesh;
        // the heart: its body, two lobes and the vessel on top, the veins hanging down from under it
        mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3, -3, -2.5F, 6, 6, 5)
                .texOffs(24, 0).addBox(-3.5F, 2, -1.5F, 3, 3, 3).addBox(0.5F, 2, -1.5F, 3, 3, 3), PartPose.ZERO);
        root.addOrReplaceChild("vessel", CubeListBuilder.create().texOffs(40, 0).addBox(-1, 0, -1, 2, 4, 2),
                PartPose.offsetAndRotation(0.5F, 3.5F, 0.5F, 0, 0, -0.35F));
        float[][] roots = {{-2, -2.5F, -1.5F}, {2, -2.5F, 1}, {0.5F, -2.5F, -2}, {-1, -2.5F, 1.8F}};
        for (int i = 0; i < roots.length; i++) {
            root.addOrReplaceChild("vein" + i, CubeListBuilder.create().texOffs(48, 0).addBox(-0.5F, -10, -0.5F, 1, 10, 1),
                    PartPose.offset(roots[i][0], roots[i][1], roots[i][2]));
        }
        heart = LayerDefinition.create(mesh, 64, 32).bakeRoot();
        for (int i = 0; i < VEINS.length; i++) VEINS[i] = heart.getChild("vein" + i);
    }

    /** Per resonator: the crystal's and the core's spin ages, the last time they were advanced (a speed change never jumps). */
    private static final java.util.Map<Object, double[]> SPIN = new java.util.WeakHashMap<>();

    public static void register() {
        Figures.register(ID, new DeadWardenFigure());
    }

    @Override
    public void submit(Context context, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(context.owner() instanceof ResonatorBlockEntity resonator) || !resonator.wardenWorks()
                || resonator.awakenedAt() == 0) return; // (still in the rift: its first moments begin once the tier has settled)
        float time = context.time();
        float since = time - resonator.blastStart(); // a shrieker being fed: the heart glows brighter
        float fed = since >= 0 && since < ResonatorBlockEntity.STREAM_TICKS + 10
                ? Mth.sin(Mth.clamp(since / (ResonatorBlockEntity.STREAM_TICKS + 10), 0, 1) * Mth.PI) : 0;

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(context.yaw() - 180)); // back to the world's frame

        double[] spin = SPIN.computeIfAbsent(resonator, r -> new double[] {0, 0, time});
        double dt = Math.max(0, Math.min(time - spin[2], 10));
        spin[0] = (spin[0] + dt) % 120000;
        spin[1] = (spin[1] + dt) % 120000;
        spin[2] = time;
        // Its first moments (ResonatorBlockEntity.awakenedAt): the body comes down out of the rift onto the table,
        // then the crystal, then the crystal pulls the heart up out of the chest
        float intro = time - resonator.awakenedAt();
        float riftY = ResonatorBlockEntity.INTRO_RIFT - 1 + 0.5F; // the rift's middle, over the figure's place
        float lay = ease(intro / ResonatorBlockEntity.LAY_TICKS);
        float down = ease((intro - ResonatorBlockEntity.LAY_TICKS) / ResonatorBlockEntity.CRYSTAL_TICKS);
        float pull = ease((intro - ResonatorBlockEntity.LAY_TICKS - ResonatorBlockEntity.CRYSTAL_TICKS) / ResonatorBlockEntity.HEART_TICKS);

        if (down > 0) {
            poseStack.pushPose();
            poseStack.translate(0, (riftY - CRYSTAL_Y) * (1 - down) + CRYSTAL_Y, 0);
            poseStack.scale(down, down, down);
            poseStack.translate(0, -CRYSTAL_Y, 0);
            crystal(context, poseStack, collector, camera, (float) spin[0]);
            poseStack.popPose();
        }
        if (intro >= ResonatorBlockEntity.LAY_TICKS + ResonatorBlockEntity.CRYSTAL_TICKS) {
            float coreY = CORE_Y + Mth.sin(time * 0.05F) * 0.04F; // drifting a little on its veins
            float tremble = (1 - pull) * Mth.sin(time * 1.7F) * 0.03F; // torn loose, shaking
            heart(poseStack, collector, (float) spin[1], time, Mth.lerp(pull, CHEST_Y, coreY) + tremble, Math.max(fed, 1 - pull));
        }

        // The body on the table (laid down out of the rift, turning to its place)
        poseStack.pushPose();
        poseStack.translate(0, riftY * (1 - lay), 0);
        poseStack.mulPose(Axis.YP.rotationDegrees((1 - lay) * 160F));
        float size = 0.25F + 0.75F * lay;
        poseStack.scale(size, size, size);
        submitBody(poseStack, collector, time, context.light(), 1);
        poseStack.popPose();
        poseStack.popPose();
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /**
     * The dead warden lying on its back over the pose's origin (its length along x, head to -x, 0.4 up), drained and
     * cracked with the rift's violet as far as corruption (0..1) says. Also client/WardenTaken, as the rift takes it.
     */
    public static void submitBody(PoseStack poseStack, SubmitNodeCollector collector, float time, int light, float corruption) {
        poseStack.pushPose();
        poseStack.translate(0, 0.4, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(-90));
        poseStack.mulPose(Axis.XP.rotationDegrees(90));
        poseStack.translate(0, -1.45, 0);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        WardenRenderState state = new WardenRenderState();
        state.ageInTicks = time;
        float pulse = (0.55F + 0.25F * Mth.sin(time * 0.08F)) * corruption;
        int tint = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) { // from its own colour to drained as the rift takes it
            int dead = (DEAD >> shift) & 0xFF;
            tint |= Math.round(Mth.lerp(corruption, 255, dead)) << shift;
        }
        collector.submitModel(model(), state, poseStack, RenderTypes.entityCutout(SKIN), light, OverlayTexture.NO_OVERLAY, tint, null, 0, null);
        if (pulse > 0.01F) {
            collector.submitModel(model(), state, poseStack, RenderTypes.entityTranslucentEmissive(CORRUPTION), LightCoordsUtil.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, ((int) (255 * pulse) << 24) | 0xFFFFFF, null, 0, null);
        }
        poseStack.popPose();
    }

    /** Per resonator: the eye's item drawn, when it was made, which way it looks (eased toward the viewer). */
    private static final class Gaze {
        net.minecraft.client.renderer.item.ItemStackRenderState state;
        long made;
        float yaw, pitch;
        boolean aimed;
    }

    private static final java.util.Map<Object, Gaze> GAZES = new java.util.WeakHashMap<>();

    /**
     * The rift crystal (age: its accumulated spin): the end crystal's glass shells turning round their middle at
     * CRYSTAL_Y, and the altar's eye in it looking at the viewer (the item, so it blinks and glances as it does).
     */
    private static void crystal(Context context, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, float age) {
        bakeModels();
        net.minecraft.client.renderer.entity.state.EndCrystalRenderState state = new net.minecraft.client.renderer.entity.state.EndCrystalRenderState();
        state.ageInTicks = age;
        state.showsBottom = false;
        state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        poseStack.pushPose();
        // the model hangs its shells 1.5 over its origin, bobbing (vanilla getY): their middle kept at CRYSTAL_Y
        poseStack.translate(0, CRYSTAL_Y - CRYSTAL_SIZE * (1.5F + net.minecraft.client.renderer.entity.EndCrystalRenderer.getY(age) / 2.0F), 0);
        poseStack.scale(CRYSTAL_SIZE, CRYSTAL_SIZE, CRYSTAL_SIZE);
        collector.submitModel(shells, state, poseStack, CRYSTAL, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0, null);
        poseStack.popPose();
        // the open conduit's cage round the eye, moving as the game's own does: turning on a tilted axis, bobbing
        poseStack.pushPose();
        poseStack.translate(0, CRYSTAL_Y + (Mth.sin(age * 0.1F) * 0.5F + 0.5F) * 0.1F - 0.05F, 0);
        poseStack.mulPose(new org.joml.Quaternionf().rotationAxis(-age * 2.5F * Mth.DEG_TO_RAD, CAGE_AXIS));
        poseStack.scale(CAGE_SIZE, CAGE_SIZE, CAGE_SIZE);
        collector.submitModelPart(cage, poseStack, RenderTypes.entityCutout(CAGE), LightCoordsUtil.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF, null);
        poseStack.popPose();

        Minecraft minecraft = Minecraft.getInstance();
        Gaze gaze = GAZES.computeIfAbsent(context.owner(), key -> new Gaze());
        long now = minecraft.level.getGameTime();
        if (gaze.state == null || now - gaze.made > 100 || now < gaze.made) {
            gaze.made = now;
            gaze.state = new net.minecraft.client.renderer.item.ItemStackRenderState();
            minecraft.getItemModelResolver().updateForTopItem(gaze.state, new net.minecraft.world.item.ItemStack(
                    com.lealex.alchyforbidden.registry.ModRegistries.RIFT_EYE.get()), net.minecraft.world.item.ItemDisplayContext.FIXED,
                    minecraft.level, null, 0);
        }
        net.minecraft.world.phys.Vec3 toViewer = camera.pos.subtract(context.at().add(0, CRYSTAL_Y, 0));
        float wantYaw = (float) Math.toDegrees(Math.atan2(toViewer.x, toViewer.z));
        float wantPitch = (float) Math.toDegrees(Math.atan2(toViewer.y, Math.sqrt(toViewer.x * toViewer.x + toViewer.z * toViewer.z)));
        if (!gaze.aimed) {
            gaze.yaw = wantYaw;
            gaze.pitch = wantPitch;
            gaze.aimed = true;
        }
        gaze.yaw += Mth.wrapDegrees(wantYaw - gaze.yaw) * 0.15F;
        gaze.pitch += (wantPitch - gaze.pitch) * 0.15F;
        poseStack.pushPose();
        poseStack.translate(0, CRYSTAL_Y, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(gaze.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(-gaze.pitch));
        poseStack.scale(EYE_SIZE, EYE_SIZE, EYE_SIZE);
        gaze.state.submit(poseStack, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /**
     * The heart torn up out of the chest (beat: its accumulated beat), its veins swaying down to where they still
     * hold, the soul in it glowing with each beat.
     */
    private static void heart(PoseStack poseStack, SubmitNodeCollector collector, float beat, float time, float y, float fed) {
        bakeModels();
        float pulse = Math.max(0, Mth.sin(beat * 0.45F));
        pulse *= pulse;
        float size = 1.0F + 0.1F * pulse;
        for (int i = 0; i < VEINS.length; i++) { // they reach down to the chest, swaying
            VEINS[i].xRot = Mth.sin(time * 0.07F + i * 1.9F) * 0.15F;
            VEINS[i].zRot = Mth.cos(time * 0.05F + i * 2.3F) * 0.15F;
        }
        poseStack.pushPose();
        poseStack.translate(0, y, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.sin(time * 0.03F) * 20F)); // turning a little on its veins
        poseStack.scale(size, size, size);
        collector.submitModelPart(heart, poseStack, RenderTypes.entityCutout(SOUL), LightCoordsUtil.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, null, 0xFFFFFFFF, null);
        int glow = (int) (90 + 120 * pulse + 45 * fed);
        collector.submitModelPart(heart, poseStack, RenderTypes.entityTranslucentEmissive(SOUL_GLOW), LightCoordsUtil.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, null, Math.min(255, glow) << 24 | 0xFFFFFF, null);
        poseStack.popPose();
    }
}
