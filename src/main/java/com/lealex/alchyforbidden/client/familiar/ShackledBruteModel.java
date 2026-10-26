package com.lealex.alchyforbidden.client.familiar;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.client.familiar.mesh.ShackledBruteMesh;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The Shackled Brute: a huge torso hunched forward with an obsidian plate on its back and a chain wound round it, a small
 * head sunk low between its shoulders, long arms (upper, forearm, an obsidian manacle at the wrist with a broken chain
 * hanging), short thick legs. Mesh and 128 x 128 texture from tools/models.py. Petted, it kneels, lowers its head into the hand and
 * its chains go still.
 */
public class ShackledBruteModel extends EntityModel<RiftBeastRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "shackled_brute"), "main");
    private final ModelPart body, head, rightArm, leftArm, rightForearm, leftForearm, rightLeg, leftLeg;
    private final ModelPart[] hanging = new ModelPart[2];

    public ShackledBruteModel(ModelPart root) {
        super(root);
        body = root.getChild("body");
        head = body.getChild("head");
        rightArm = body.getChild("right_arm");
        leftArm = body.getChild("left_arm");
        rightForearm = rightArm.getChild("right_forearm");
        leftForearm = leftArm.getChild("left_forearm");
        hanging[0] = rightForearm.getChild("right_chain");
        hanging[1] = leftForearm.getChild("left_chain");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        return ShackledBruteMesh.create();
    }

    @Override
    public void setupAnim(RiftBeastRenderState state) {
        super.setupAnim(state);
        float pos = state.walkAnimationPos * 0.6662F, speed = Math.min(1F, state.walkAnimationSpeed), age = state.ageInTicks, pet = state.pet;
        rightLeg.xRot = Mth.cos(pos) * 0.9F * speed;
        leftLeg.xRot = Mth.cos(pos + Mth.PI) * 0.9F * speed;
        body.zRot = Mth.cos(pos) * 0.06F * speed;
        body.y += Mth.sin(age * 0.07F) * 0.3F; // a slow, heavy breath
        head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.6F;
        head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.5F;
        // arms swinging opposite the legs, both raised and brought down on a blow
        rightArm.xRot += Mth.cos(pos + Mth.PI) * 0.5F * speed - 1.6F * state.swing;
        leftArm.xRot += Mth.cos(pos) * 0.5F * speed - 1.6F * state.swing;
        for (int k = 0; k < 2; k++) hanging[k].xRot = -0.5F * speed * Mth.cos(pos + k) + Mth.sin(age * 0.1F + k) * 0.1F;
        if (pet > 0) { // kneels, the head lowered into the hand, the chains still
            rightLeg.xRot = Mth.lerp(pet, rightLeg.xRot, -1.4F);
            leftLeg.xRot = Mth.lerp(pet, leftLeg.xRot, 0.2F);
            body.y += 4F * pet;
            body.xRot += 0.3F * pet;
            head.xRot += 0.4F * pet + Mth.sin(state.petTime * 0.2F) * 0.08F * pet;
            leftArm.xRot = Mth.lerp(pet, leftArm.xRot, -0.2F);
            rightArm.xRot = Mth.lerp(pet, rightArm.xRot, -0.2F);
        }
        if (state.move >= 0) move(state.move, state.moveTime);
    }

    /** The moves (ground slam, chain hook, earthbreaker, wrecking flail). */
    private void move(int move, float t) {
        switch (move) {
            case 0 -> { // slam: both fists raised high, brought down
                float raise = t < 12 ? t / 12F : 0, down = t >= 12 && t < 18 ? 1 : 0;
                rightArm.xRot -= 2.6F * raise - 0.4F * down;
                leftArm.xRot -= 2.6F * raise - 0.4F * down;
                body.xRot += 0.4F * down;
            }
            case 1 -> { // hook: the right arm wound back, then thrown forward
                float wind = t < 10 ? t / 10F : 0, throwArm = t >= 10 && t < 16 ? 1 : 0;
                rightArm.xRot += 0.8F * wind - 1.8F * throwArm;
                rightArm.zRot -= 0.5F * wind;
                body.yRot = -0.4F * wind + 0.3F * throwArm;
            }
            case 2 -> { // earthbreaker: crouched, arms up in the air, crashing down
                float crouch = t < 10 ? t / 10F : 0, air = t >= 10 && t < 24 ? 1 : 0, land = t >= 24 && t < 32 ? 1 : 0; // leaps at 10, lands about 24
                body.y += 3F * crouch;
                rightArm.xRot -= 2.8F * air;
                leftArm.xRot -= 2.8F * air;
                body.xRot += 0.6F * land;
                rightArm.xRot -= -0.6F * land;
                leftArm.xRot -= -0.6F * land;
            }
            case 3 -> { // flail: arms out wide, the whole body spinning
                float out = Mth.clamp(Math.min(t / 6F, (70 - t) / 6F), 0, 1);
                rightArm.zRot += 1.3F * out;
                leftArm.zRot -= 1.3F * out;
                body.yRot = t * 0.6F * out;
            }
            default -> {}
        }
    }
}
