package com.lealex.alchyforbidden.client.familiar;

import com.lealex.alchyforbidden.client.familiar.mesh.SculkCrawlerMesh;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

/**
 * The Sculk Crawler: a long low body of sculk plates with a collar and two shoulder plates, three ridged plates and two
 * pairs of sculk-sensor tendrils down its spine, a wedge of a head under a brow ridge with glowing eyes on its sides and
 * two tendrils swept back, a jaw with teeth that drops open as it bites, four legs bent at the knee and splayed out
 * ending in claws, a tail of three segments with a fin at its tip. Mesh and 128 x 128 texture from tools/models.py.
 * Petted (state.pet), it sinks low, presses its head up into the hand rubbing side to side, tendrils quivering, the
 * tail lashing.
 */
public class SculkCrawlerModel extends EntityModel<RiftBeastRenderState> {
    public static final net.minecraft.client.model.geom.ModelLayerLocation LAYER = new net.minecraft.client.model.geom.ModelLayerLocation(
            net.minecraft.resources.Identifier.fromNamespaceAndPath(com.lealex.alchyforbidden.AlchyForbidden.MODID, "sculk_crawler"), "main");
    private final ModelPart body, collar, head, jaw, tail1, tail2, tail3;
    private final ModelPart[] upper = new ModelPart[4], lower = new ModelPart[4], tendrils = new ModelPart[6], shoulders = new ModelPart[2];
    private static final String[] LEGS = {"right_front", "left_front", "right_hind", "left_hind"};

    public SculkCrawlerModel(ModelPart root) {
        super(root);
        body = root.getChild("body");
        collar = body.getChild("collar");
        head = root.getChild("head");
        jaw = head.getChild("jaw");
        tail1 = root.getChild("tail1");
        tail2 = tail1.getChild("tail2");
        tail3 = tail2.getChild("tail3");
        for (int i = 0; i < 4; i++) {
            upper[i] = root.getChild(LEGS[i] + "_leg");
            lower[i] = upper[i].getChild(LEGS[i] + "_shin");
        }
        tendrils[0] = head.getChild("tendril_r");
        tendrils[1] = head.getChild("tendril_l");
        for (int k = 0; k < 4; k++) tendrils[2 + k] = body.getChild("back_tendril" + k);
        shoulders[0] = body.getChild("shoulder_r");
        shoulders[1] = body.getChild("shoulder_l");
    }

    public static LayerDefinition createBodyLayer() {
        return SculkCrawlerMesh.create();
    }

    @Override
    public void setupAnim(RiftBeastRenderState state) {
        super.setupAnim(state);
        float pos = state.walkAnimationPos * 0.6662F, speed = Math.min(1F, state.walkAnimationSpeed), age = state.ageInTicks, pet = state.pet;
        // diagonal pairs step together, like a lizard: the leg swings forward lifted, back planted
        for (int i = 0; i < 4; i++) {
            float phase = (i == 0 || i == 3) ? 0 : Mth.PI;
            upper[i].xRot += Mth.cos(pos + phase) * 0.6F * speed;
            lower[i].xRot += Math.max(0, Mth.sin(pos + phase)) * 0.45F * speed;
            float side = i % 2 == 0 ? -1 : 1;
            upper[i].zRot += side * 0.35F * pet; // petted: legs splay, the body sinks
            lower[i].zRot -= side * 0.25F * pet;
        }
        shoulders[0].zRot -= Mth.cos(pos) * 0.12F * speed;
        shoulders[1].zRot += Mth.cos(pos + Mth.PI) * 0.12F * speed;
        // the whole body breathes and sways a little as it goes (body, head and tail together, legs planted)
        float lift = Mth.sin(age * 0.1F) * 0.25F + Mth.cos(pos * 2) * 0.3F * speed + 2.2F * pet;
        body.y += lift;
        body.zRot = Mth.cos(pos) * 0.05F * speed;
        tail1.y += lift;
        head.y += lift;
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD - state.swing * 0.35F;
        jaw.xRot = state.swing * 0.9F + (Mth.sin(age * 0.09F) + 1) * 0.04F;
        collar.xRot += state.swing * 0.2F;
        // tendrils: a slow sway, flicking up on a bite
        for (int k = 0; k < tendrils.length; k++) {
            float wave = Mth.sin(age * 0.18F + k * 1.3F) * 0.15F;
            tendrils[k].xRot += wave + state.swing * 0.4F;
            tendrils[k].zRot += Mth.cos(age * 0.13F + k) * 0.08F;
        }
        float sway = Mth.sin(age * 0.15F + pos * 0.5F) * (0.25F + speed * 0.2F);
        if (pet > 0) { // the petting: head pressed up into the hand and rubbing, tendrils quivering, tail lashing
            float rub = Mth.sin(state.petTime * 0.45F);
            head.xRot = Mth.lerp(pet, head.xRot, -0.55F);
            head.yRot = Mth.lerp(pet, head.yRot, rub * 0.45F);
            head.zRot = rub * 0.25F * pet;
            jaw.xRot = Mth.lerp(pet, jaw.xRot, 0.2F + 0.1F * Mth.sin(state.petTime * 0.3F));
            for (int k = 0; k < tendrils.length; k++) tendrils[k].xRot += pet * (0.5F + Mth.sin(state.petTime * 1.7F + k) * 0.18F);
            sway = Mth.lerp(pet, sway, Mth.sin(state.petTime * 0.9F) * 0.7F);
        }
        tail1.yRot = sway;
        tail2.yRot = sway * 1.3F;
        tail3.yRot = sway * 1.6F;
        if (state.move >= 0) move(state.move, state.moveTime);
    }

    /** The moves (index in RiftCreature.SCULK_CRAWLER.moves(): lunge, sculk spit, burrow, shriek). */
    private void move(int move, float t) {
        switch (move) {
            case 0 -> { // lunge: crouched and coiled, then stretched out in the air
                float coil = Mth.clamp(t / 8F, 0, 1) * (t < 8 ? 1 : 0), fly = t >= 8 ? Mth.clamp(1 - (t - 8) / 14F, 0, 1) : 0;
                body.y += 2.5F * coil;
                head.y += 2.5F * coil;
                tail1.y += 2.5F * coil;
                head.xRot += -0.3F * fly;
                jaw.xRot += 0.8F * fly;
                for (int i = 0; i < 4; i++) {
                    float side = i % 2 == 0 ? -1 : 1;
                    upper[i].zRot += side * 0.4F * coil;
                    upper[i].xRot += (i < 2 ? -1.0F : 1.0F) * fly; // front legs reach forward, hind legs kick back
                }
            }
            case 1 -> { // sculk spit: rears back, jaw wide at the spit, collar flared
                float rear = t < 10 ? t / 10F : Mth.clamp(1 - (t - 10) / 8F, 0, 1);
                head.xRot += -0.7F * rear;
                jaw.xRot += t >= 9 && t < 14 ? 1.0F : 0.2F * rear;
                collar.xRot -= 0.4F * rear;
                body.xRot -= 0.15F * rear;
            }
            case 2 -> { // burrow: shakes and sinks into the ground, then bursts up
                float sink = t < 14 ? t / 14F : t < 30 ? 1 : Mth.clamp(1 - (t - 30) / 6F, 0, 1);
                float shake = t < 14 ? Mth.sin(t * 2.5F) * 0.15F : 0;
                float down = 10F * sink;
                body.y += down;
                head.y += down;
                tail1.y += down;
                body.zRot += shake;
                for (ModelPart leg : upper) leg.y += down;
                if (t >= 30) head.xRot -= 0.6F * (1 - sink);
            }
            case 3 -> { // shriek: rears up, jaw as wide as it goes, every tendril up, trembling
                float rise = t < 20 ? t / 20F : Mth.clamp(1 - (t - 20) / 14F, 0, 1), tremble = Mth.sin(t * 3.1F) * 0.06F * rise;
                head.xRot += -0.9F * rise + tremble;
                jaw.xRot += 1.1F * rise;
                body.xRot -= 0.25F * rise;
                body.y -= 1.5F * rise;
                head.y -= 2.5F * rise;
                for (ModelPart tendril : tendrils) tendril.xRot += 0.9F * rise + tremble * 4;
            }
            default -> {}
        }
    }
}
