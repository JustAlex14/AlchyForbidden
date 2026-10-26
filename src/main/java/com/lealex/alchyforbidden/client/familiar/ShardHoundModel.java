package com.lealex.alchyforbidden.client.familiar;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.client.familiar.mesh.ShardHoundMesh;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The Shard Hound: a lean hound, long legs, a long snout with a jaw, pricked ears, three amethyst crystals leaning back
 * along its spine and one on each shoulder, a tail held up ending in a crystal. Mesh and 128 x 128 texture from tools/models.py.
 * Petted, it sits back, tilts its head into the hand, lifts a paw and wags.
 */
public class ShardHoundModel extends EntityModel<RiftBeastRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "shard_hound"), "main");
    private final ModelPart body, head, jaw, tail;
    private final ModelPart[] legs = new ModelPart[4], crystals = new ModelPart[5];

    public ShardHoundModel(ModelPart root) {
        super(root);
        body = root.getChild("body");
        head = root.getChild("head");
        jaw = head.getChild("jaw");
        tail = body.getChild("tail");
        String[] names = {"right_front_leg", "left_front_leg", "right_hind_leg", "left_hind_leg"};
        for (int i = 0; i < 4; i++) legs[i] = root.getChild(names[i]);
        for (int i = 0; i < 5; i++) crystals[i] = body.getChild("crystal" + i);
    }

    public static LayerDefinition createBodyLayer() {
        return ShardHoundMesh.create();
    }

    @Override
    public void setupAnim(RiftBeastRenderState state) {
        super.setupAnim(state);
        float pos = state.walkAnimationPos * 0.6662F, speed = Math.min(1F, state.walkAnimationSpeed), age = state.ageInTicks, pet = state.pet;
        for (int i = 0; i < 4; i++) legs[i].xRot = Mth.cos(pos + ((i == 0 || i == 3) ? 0 : Mth.PI)) * 1.2F * speed;
        body.y += Mth.sin(age * 0.12F) * 0.2F;
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        jaw.xRot = 0.6F * state.swing + (speed > 0.5F ? 0.15F : 0);
        tail.yRot = Mth.sin(age * 0.2F) * 0.15F;
        tail.xRot = 0.7F - speed * 0.3F;
        for (int i = 0; i < 5; i++) crystals[i].zRot += Mth.sin(age * 0.05F + i) * 0.02F;
        if (pet > 0) { // sits back, head tilted into the hand, a paw up, wagging
            body.xRot = -0.55F * pet;
            body.y += 2.5F * pet;
            body.z += 1.5F * pet;
            legs[2].xRot = Mth.lerp(pet, legs[2].xRot, -1.4F);
            legs[3].xRot = Mth.lerp(pet, legs[3].xRot, -1.4F);
            legs[2].y += 3F * pet;
            legs[3].y += 3F * pet;
            legs[0].xRot = Mth.lerp(pet, legs[0].xRot, -0.9F + Mth.sin(state.petTime * 0.3F) * 0.2F);
            head.y -= 1.0F * pet;
            head.zRot = Mth.sin(state.petTime * 0.2F) * 0.35F * pet;
            head.xRot = Mth.lerp(pet, head.xRot, -0.3F);
            tail.yRot = Mth.sin(state.petTime * 1.4F) * 0.7F * pet;
        }
        if (state.move >= 0) move(state.move, state.moveTime);
    }

    /** The moves (crystal charge, shard volley, howl, crystallize). */
    private void move(int move, float t) {
        switch (move) {
            case 0 -> { // charge: crouched low, then stretched flat out
                float crouch = t < 10 ? t / 10F : 0, run = t >= 10 && t < 20 ? 1 : 0;
                body.y += 2.5F * crouch;
                head.y += 2.5F * crouch + 1.5F * run;
                head.xRot += 0.4F * run;
                legs[0].xRot = legs[1].xRot = run > 0 ? -1.2F : legs[0].xRot;
                legs[2].xRot = legs[3].xRot = run > 0 ? 1.2F : legs[2].xRot;
            }
            case 1 -> { // volley: a shake from nose to tail, the crystals standing up
                float shake = t < 12 ? Mth.sin(t * 2.4F) * (t / 12F) : 0;
                body.zRot += shake * 0.3F;
                head.zRot -= shake * 0.4F;
                for (ModelPart crystal : crystals) crystal.xRot += 0.5F * Mth.clamp(t / 12F, 0, 1) * (t < 14 ? 1 : 0);
            }
            case 2 -> { // howl: sat back, the head raised high, the jaw open
                float up = t < 10 ? t / 10F : Mth.clamp(1 - (t - 24) / 6F, 0, 1);
                body.xRot -= 0.5F * up;
                body.y += 2F * up;
                head.xRot = Mth.lerp(up, head.xRot, -1.2F);
                head.y -= 2F * up;
                jaw.xRot = Mth.lerp(up, jaw.xRot, 0.7F);
            }
            case 3 -> { // crystallize: low and still, staring, the crystals trembling
                float hold = Mth.clamp(Math.min(t / 6F, (44 - t) / 6F), 0, 1);
                body.y += 1.5F * hold;
                head.xRot += 0.3F * hold;
                for (ModelPart crystal : crystals) crystal.zRot += Mth.sin(t * 2F) * 0.08F * hold;
            }
            default -> {}
        }
    }
}
