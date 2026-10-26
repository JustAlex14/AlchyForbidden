package com.lealex.alchyforbidden.client.familiar;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.client.familiar.mesh.HollowStalkerMesh;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The Hollow Stalker: long thin legs, a pelvis, a bare spine running up to a chest cage with nothing in it, a tall blank
 * mask with one slit, very long arms in two parts ending in three claws each. It walks stooped, arms hanging. Mesh and
 * 128 x 128 texture from tools/models.py. Petted, it bends low, tilts its mask right over and folds its claws.
 */
public class HollowStalkerModel extends EntityModel<RiftBeastRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "hollow_stalker"), "main");
    private final ModelPart body, chest, head, rightArm, leftArm, rightFore, leftFore, rightLeg, leftLeg;

    public HollowStalkerModel(ModelPart root) {
        super(root);
        body = root.getChild("body");
        chest = body.getChild("chest");
        head = chest.getChild("head");
        rightArm = chest.getChild("right_arm");
        leftArm = chest.getChild("left_arm");
        rightFore = rightArm.getChild("right_fore");
        leftFore = leftArm.getChild("left_fore");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createBodyLayer() {
        return HollowStalkerMesh.create();
    }

    @Override
    public void setupAnim(RiftBeastRenderState state) {
        super.setupAnim(state);
        float pos = state.walkAnimationPos * 0.6662F * 0.8F, speed = Math.min(1F, state.walkAnimationSpeed), age = state.ageInTicks, pet = state.pet;
        rightLeg.xRot = Mth.cos(pos) * 0.8F * speed;
        leftLeg.xRot = Mth.cos(pos + Mth.PI) * 0.8F * speed;
        rightArm.xRot += Mth.cos(pos + Mth.PI) * 0.35F * speed - 1.8F * state.swing;
        leftArm.xRot += Mth.cos(pos) * 0.35F * speed - 1.2F * state.swing;
        rightFore.xRot += -0.6F * state.swing;
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        head.zRot = Mth.sin(age * 0.03F) * 0.1F; // a slow, wrong tilt
        chest.xRot += Mth.sin(age * 0.05F) * 0.03F;
        if (pet > 0) { // bent low, the mask tipped right over, claws folded
            body.xRot += 0.5F * pet;
            head.zRot = Mth.lerp(pet, head.zRot, 1.2F + Mth.sin(state.petTime * 0.2F) * 0.1F);
            rightFore.xRot -= 0.9F * pet;
            leftFore.xRot -= 0.9F * pet;
        }
        if (state.move >= 0) move(state.move, state.moveTime);
    }

    /** The moves (shadow step, fade, rending flurry, hollow grasp). */
    private void move(int move, float t) {
        switch (move) {
            case 0 -> { // shadow step: claws drawn back, then a lunge
                float draw = t < 6 ? t / 6F : 0, lunge = t >= 6 && t < 12 ? 1 : 0;
                rightArm.xRot += 0.8F * draw - 1.9F * lunge;
                body.xRot += 0.4F * lunge;
            }
            case 1 -> { // fade: folding into itself
                float fold = Mth.clamp(t / 6F, 0, 1);
                rightArm.zRot += 0.6F * fold;
                leftArm.zRot -= 0.6F * fold;
                body.xRot += 0.3F * fold;
            }
            case 2 -> { // flurry: the two arms slashing in turn
                float a = Mth.sin(t * 1.25F);
                rightArm.xRot -= 1.4F + a * 0.9F;
                leftArm.xRot -= 1.4F - a * 0.9F;
                rightArm.zRot += a * 0.4F;
                leftArm.zRot += a * 0.4F;
            }
            case 3 -> { // grasp: the right arm raised straight up high, holding, then hurled down
                float hold = t >= 8 && t < 36 ? 1 : 0, slam = t >= 36 && t < 44 ? 1 : 0;
                rightArm.xRot = Mth.lerp(hold, rightArm.xRot, -3.0F) + 1.5F * slam;
                rightFore.xRot = Mth.lerp(hold, rightFore.xRot, 0F);
                body.xRot += 0.5F * slam;
                head.xRot -= 0.5F * hold;
            }
            default -> {}
        }
    }
}
