package com.lealex.alchyforbidden.client.familiar;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.client.familiar.mesh.VeilWispMesh;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The Veil Wisp: a deep hood with two cold eyes in its dark, a shroud under it falling into four tatters, two thin arms
 * reaching forward round a soul that turns between its hands. It drifts a little over the ground, bobbing. Mesh and 128 x 128
 * texture from tools/models.py. Petted, it turns its hood up into the hand, the tatters ripple and its arms open.
 */
public class VeilWispModel extends EntityModel<RiftBeastRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "veil_wisp"), "main");
    private final ModelPart body, hood, rightArm, leftArm, orb;
    private final ModelPart[] tatters = new ModelPart[4];

    public VeilWispModel(ModelPart root) {
        super(root);
        body = root.getChild("body");
        hood = body.getChild("hood");
        rightArm = body.getChild("right_arm");
        leftArm = body.getChild("left_arm");
        orb = body.getChild("orb");
        for (int i = 0; i < 4; i++) tatters[i] = body.getChild("tatter" + i);
    }

    public static LayerDefinition createBodyLayer() {
        return VeilWispMesh.create();
    }

    @Override
    public void setupAnim(RiftBeastRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks, speed = Math.min(1F, state.walkAnimationSpeed), pet = state.pet;
        body.y += -2.5F + Mth.sin(age * 0.1F) * 1.2F; // drifting over the ground
        body.xRot = 0.15F * speed; // leaning into its way
        hood.yRot = state.yRot * Mth.DEG_TO_RAD;
        hood.xRot = state.xRot * Mth.DEG_TO_RAD;
        for (int i = 0; i < 4; i++) {
            tatters[i].xRot = 0.25F * speed + Mth.sin(age * 0.2F + i * 1.3F) * 0.12F + pet * Mth.sin(age * 0.9F + i) * 0.3F;
            tatters[i].zRot = Mth.cos(age * 0.17F + i) * 0.1F;
        }
        float cast = state.swing;
        rightArm.xRot -= 0.5F * cast;
        leftArm.xRot -= 0.5F * cast;
        orb.yRot = age * 0.15F;
        orb.xRot = age * 0.1F;
        orb.z -= 1.5F * cast;
        if (pet > 0) { // the hood turned up into the hand, the arms open
            hood.xRot = Mth.lerp(pet, hood.xRot, -0.5F);
            hood.zRot = Mth.sin(state.petTime * 0.3F) * 0.2F * pet;
            rightArm.zRot += 0.6F * pet;
            leftArm.zRot -= 0.6F * pet;
            body.zRot = Mth.sin(state.petTime * 0.25F) * 0.08F * pet;
        }
        if (state.move >= 0) move(state.move, state.moveTime);
    }

    /** The moves (rift barrage, blink away, soul snare, spectral rain). */
    private void move(int move, float t) {
        switch (move) {
            case 0 -> { // barrage: the soul drawn back, then both arms thrust out
                float draw = t < 12 ? t / 12F : Mth.clamp(1 - (t - 12) / 8F, 0, 1);
                rightArm.xRot -= 0.6F * draw;
                leftArm.xRot -= 0.6F * draw;
                orb.z += t < 12 ? 2F * draw : -2F * draw;
            }
            case 1 -> { // blink: folding in on itself
                float fold = Mth.clamp(1 - Math.abs(t - 2) / 4F, 0, 1);
                body.y += 3F * fold;
                for (ModelPart tatter : tatters) tatter.xRot -= 0.8F * fold;
            }
            case 2 -> { // snare: arms raised high, the soul held up
                float raise = t < 10 ? t / 10F : Mth.clamp(1 - (t - 10) / 6F, 0, 1);
                rightArm.xRot -= 1.4F * raise;
                leftArm.xRot -= 1.4F * raise;
                orb.y -= 5F * raise;
                hood.xRot -= 0.4F * raise;
            }
            case 3 -> { // rain: both arms up the whole time, the soul high, the shroud whipping
                float up = Mth.clamp(Math.min(t / 8F, (50 - t) / 6F), 0, 1);
                rightArm.xRot -= 2.0F * up;
                leftArm.xRot -= 2.0F * up;
                orb.y -= 7F * up;
                for (ModelPart tatter : tatters) tatter.xRot += Mth.sin(t * 1.3F) * 0.4F * up;
            }
            default -> {}
        }
    }
}
