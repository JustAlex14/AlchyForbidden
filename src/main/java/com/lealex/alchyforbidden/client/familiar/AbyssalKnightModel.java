package com.lealex.alchyforbidden.client.familiar;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.client.familiar.mesh.AbyssalKnightMesh;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The Abyssal Knight (mesh from tools/models.py: a crested, horned helm with a burning visor and cheek guards, layered
 * pauldrons with spikes, a breastplate with a rune, belly plates, a belt, a skirt of plates, a torn cape, vambraces and
 * gauntlets, knee cops and pointed sabatons on jointed legs, a greatsword with a winged guard), drawn 1.6 times larger.
 * The sword's angle is set from the blade's angle in the world (arm + sword about X: 0 points down, -PI/2 forward, -PI
 * up, +PI/2 back). It carries the blade trailing behind; a blow sweeps it in a wide arc across everything in front. Petted, it kneels on its right knee, the sword planted before it under both hands, and bows its head.
 */
public class AbyssalKnightModel extends EntityModel<RiftBeastRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "abyssal_knight"), "main");
    private final ModelPart head, body, cape, rightArm, leftArm, rightLeg, leftLeg, rightShin, leftShin, sword;

    public AbyssalKnightModel(ModelPart root) {
        super(root);
        head = root.getChild("head");
        body = root.getChild("body");
        cape = body.getChild("cape");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
        rightShin = rightLeg.getChild("right_shin");
        leftShin = leftLeg.getChild("left_shin");
        sword = rightArm.getChild("sword");
    }

    public static LayerDefinition createBodyLayer() {
        return AbyssalKnightMesh.create();
    }

    @Override
    public void setupAnim(RiftBeastRenderState state) {
        super.setupAnim(state);
        float pos = state.walkAnimationPos * 0.6662F, speed = Math.min(1F, state.walkAnimationSpeed), age = state.ageInTicks, pet = state.pet;
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        rightLeg.xRot = Mth.cos(pos) * 0.9F * speed;
        leftLeg.xRot = Mth.cos(pos + Mth.PI) * 0.9F * speed;
        rightShin.xRot = Math.max(0, Mth.sin(pos)) * 0.7F * speed; // the knee bends as the leg comes forward
        leftShin.xRot = Math.max(0, Mth.sin(pos + Mth.PI)) * 0.7F * speed;
        leftArm.xRot = Mth.cos(pos) * 0.5F * speed;
        rightArm.xRot = -0.35F + Mth.cos(pos + Mth.PI) * 0.15F * speed;
        blade(1.1F); // trailing behind it, point down and back
        cape.xRot = 0.08F + 0.6F * speed + Mth.sin(age * 0.09F) * 0.05F; // streaming behind as it walks
        body.y += Mth.sin(age * 0.06F) * 0.2F;
        if (state.swingTime > 0) arc(state.swingTime);
        if (pet > 0) kneel(pet, 0.6F);
        if (state.move >= 0) move(state.move, state.moveTime);
        if (state.intro >= 0) intro(state.intro);
    }

    /**
     * The blow, t 0..1, two-handed: wound up over its right shoulder (to 0.3, leaning back, weight on the back foot),
     * then the cut (to 0.55) sweeping down and across everything in front, stepping into it with the left foot, knees
     * bent, body twisting through; then the follow-through, the blade carried on down past its legs back to the trail
     * (to 1). Each stretch eased, so it gathers, snaps and settles.
     */
    private void arc(float t) {
        int k = t < 0.3F ? 0 : t < 0.55F ? 1 : 2;
        float from = k == 0 ? 0 : k == 1 ? 0.3F : 0.55F, to = k == 0 ? 0.3F : k == 1 ? 0.55F : 1F;
        float u = Mth.clamp((t - from) / (to - from), 0, 1);
        u = k == 1 ? u * u * (3 - 2 * u) : k == 0 ? 1 - (1 - u) * (1 - u) : u * u * (3 - 2 * u); // gather fast, snap, settle
        float walkArm = rightArm.xRot;
        float armX = key(k, u, walkArm, -2.2F, -1.0F, walkArm);
        float turn = key(k, u, 0F, 1.15F, -1.1F, 0F);
        float twist = key(k, u, 0F, 0.45F, -0.5F, 0F);
        float lean = key(k, u, 0F, -0.12F, 0.28F, 0F);
        float step = key(k, u, 0F, -0.2F, 1F, 0F); // weight back, then into the left foot
        rightArm.xRot = armX;
        rightArm.yRot = turn - 0.1F;
        rightArm.zRot = 0.1F;
        leftArm.xRot = armX; // the other hand on the hilt below it
        leftArm.yRot = turn + 0.55F;
        leftArm.zRot = -0.1F;
        body.yRot = twist;
        body.xRot += lean;
        head.yRot += twist * 0.5F; // eyes on what it cuts
        head.xRot += lean * 0.5F;
        float drop = 1.4F * Math.max(0, step);
        head.y += drop;
        body.y += drop;
        rightArm.y += drop;
        leftArm.y += drop;
        leftLeg.xRot = -0.55F * step;
        leftShin.xRot = 0.5F * Math.max(0, step);
        rightLeg.xRot = 0.35F * step;
        rightShin.xRot = 0.35F * Math.max(0, step);
        cape.xRot += 0.4F * Math.max(0, step);
        cape.zRot = -0.25F * twist;
        blade(key(k, u, 1.1F, 2.75F, 5.0F, 7.38F)); // trail -> up over the shoulder -> forward and down -> on round to the trail
    }

    /** The value at stretch k (0..2) of four keys, u eased 0..1 inside it. */
    private static float key(int k, float u, float a, float b, float c, float d) {
        return k == 0 ? Mth.lerp(u, a, b) : k == 1 ? Mth.lerp(u, b, c) : Mth.lerp(u, c, d);
    }

    /** Sets the sword so the blade lies at this angle in the world (about X), whatever the arm does. */
    private void blade(float angle) {
        sword.xRot = angle - rightArm.xRot;
    }

    /**
     * Down on the right knee by f (0..1): hips 6 px lower, right thigh straight down to the knee on the ground and the
     * shin lying back, left thigh forward and its shin standing; the sword planted point down before it, both hands on
     * the pommel, the head bowed by bow.
     */
    private void kneel(float f, float bow) {
        float was = sword.xRot + rightArm.xRot, drop = 6F * f;
        head.y += drop;
        body.y += drop;
        rightArm.y += drop;
        leftArm.y += drop;
        rightLeg.y += drop;
        leftLeg.y += drop;
        rightLeg.xRot = Mth.lerp(f, rightLeg.xRot, 0.1F);
        rightShin.xRot = Mth.lerp(f, rightShin.xRot, Mth.HALF_PI);
        leftLeg.xRot = Mth.lerp(f, leftLeg.xRot, -Mth.HALF_PI);
        leftShin.xRot = Mth.lerp(f, leftShin.xRot, Mth.HALF_PI);
        body.xRot = Mth.lerp(f, body.xRot, 0.1F);
        head.xRot = Mth.lerp(f, head.xRot, bow);
        head.yRot = Mth.lerp(f, head.yRot, 0F);
        rightArm.xRot = Mth.lerp(f, rightArm.xRot, -1.15F);
        rightArm.yRot = Mth.lerp(f, rightArm.yRot, -0.25F);
        leftArm.xRot = Mth.lerp(f, leftArm.xRot, -1.15F);
        leftArm.yRot = Mth.lerp(f, leftArm.yRot, 0.45F);
        sword.yRot = Mth.lerp(f, sword.yRot, 0.25F);
        blade(Mth.lerp(f, was, -0.35F)); // point down into the ground before it
        cape.xRot = Mth.lerp(f, cape.xRot, 0.35F); // lying back over the heels
    }

    /** The moves (rift slash, void charge, abyssal smite, rift collapse). */
    private void move(int move, float t) {
        switch (move) {
            case 0 -> { // slash: drawn back to its right, then swept across in front, the blade flat
                float draw = t < 10 ? Mth.clamp(t / 6F, 0, 1) : 0, sweep = t >= 10 && t < 18 ? Mth.clamp((t - 10) / 3F, 0, 1) : 0;
                rightArm.xRot = -1.45F;
                rightArm.yRot = 1.0F * draw - 1.1F * sweep;
                rightArm.zRot = 0.2F * draw;
                body.yRot = 0.45F * draw - 0.55F * sweep;
                leftArm.xRot = -0.6F * draw;
                blade(-1.6F); // straight out
                cape.zRot = -0.3F * sweep;
            }
            case 1 -> { // charge: leaning in, both hands on the hilt, the point aimed ahead
                float lean = Mth.clamp(t / 8F, 0, 1);
                body.xRot = 0.35F * lean;
                head.y += 1F * lean;
                rightArm.xRot = -1.3F * lean - 0.35F * (1 - lean);
                leftArm.xRot = -1.3F * lean;
                leftArm.yRot = 0.5F * lean;
                blade(Mth.lerp(lean, 1.1F, -1.5F));
                cape.xRot = 1.1F * lean;
            }
            case 2 -> { // smite: raised high in both hands, held, brought down to the ground before it
                float up = t < 26 ? Mth.clamp(t / 8F, 0, 1) : 0, down = t >= 26 && t < 36 ? Mth.clamp((t - 26) / 3F, 0, 1) : 0;
                float arm = t < 26 ? -3.0F * up : Mth.lerp(down, -3.0F, -0.9F); // brought down from on high, not from the side
                rightArm.xRot = arm;
                leftArm.xRot = arm;
                leftArm.yRot = 0.45F * Math.max(up, down);
                rightArm.yRot = -0.15F * Math.max(up, down);
                body.xRot = t < 26 ? -0.1F * up : Mth.lerp(down, -0.1F, 0.35F);
                blade(up > 0 ? Mth.lerp(up, 1.1F, 2.98F) : Mth.lerp(down, -3.3F, -0.9F)); // up over the shoulder from behind, down before it
            }
            case 3 -> { // collapse: down on one knee, the blade driven into the ground before it
                float plant = Mth.clamp(t / 10F, 0, 1);
                kneel(plant, 0.25F);
                cape.xRot = 0.5F + Mth.sin(t * 0.8F) * 0.2F * plant; // the rift's pull
            }
            default -> {}
        }
    }

    /** Its entrance: kneeling as it rises out of the rift, standing, then the blade lifted high. */
    private void intro(float t) {
        float stand = Mth.clamp((t - 60) / 12F, 0, 1);
        if (stand < 1) kneel(1 - stand, 0.7F * (1 - stand));
        float raise = Mth.clamp((t - 74) / 6F, 0, 1);
        if (raise > 0) {
            rightArm.xRot = Mth.lerp(raise, rightArm.xRot, -2.9F);
            leftArm.xRot = Mth.lerp(raise, leftArm.xRot, -2.9F);
            leftArm.yRot = 0.4F * raise;
            head.xRot = -0.3F * raise;
            blade(Mth.lerp(raise, 1.1F, -3.1F));
        }
    }
}
