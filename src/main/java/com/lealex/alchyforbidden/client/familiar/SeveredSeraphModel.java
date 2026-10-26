package com.lealex.alchyforbidden.client.familiar;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.client.familiar.mesh.SeveredSeraphMesh;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The Severed Seraph (mesh and texture from tools/models.py: a hood round a void, a cracked mask with violet eyes and
 * gold tears, a broken halo tilted over it, a pale robe with a rune panel and a hole of light where the heart was, a gold
 * collar and a chain bound round it, long sleeves with gold manacles and broken chains, a robe trailing to a point, two
 * wings of two segments with blade feathers), drawn 1.5 times larger.
 * Phase I it floats, wings beating slow. Phase II its wings burn (violet fire along every feather) and its halo's six
 * shards circle it as blades; it moves sharper. Phase III the mask breaks: half of it gone and its pieces floating off,
 * one eye of the rift under it, the halo shattered into bits round its head; it twitches, its wings beat hard.
 * Resting as a familiar, only its broken halo at its owner's shoulder. Petted, it bows and folds its wings round itself.
 */
public class SeveredSeraphModel extends EntityModel<RiftBeastRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "severed_seraph"), "main");
    private final ModelPart head, mask, maskBroken, face, halo, haloBits, body, skirt, skirtB, skirtC, rightArm, leftArm,
            rightWing, leftWing, rightWingB, leftWingB, rightFire, leftFire, rightFireB, leftFireB;
    private final ModelPart[] shards = new ModelPart[6];

    public SeveredSeraphModel(ModelPart root) {
        super(root);
        head = root.getChild("head");
        mask = head.getChild("mask");
        maskBroken = head.getChild("mask_broken");
        face = head.getChild("face");
        halo = head.getChild("halo");
        haloBits = head.getChild("halo_bits");
        body = root.getChild("body");
        skirt = body.getChild("skirt");
        skirtB = skirt.getChild("skirt_b");
        skirtC = skirtB.getChild("skirt_c");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightWing = body.getChild("right_wing");
        leftWing = body.getChild("left_wing");
        rightWingB = rightWing.getChild("right_wing_b");
        leftWingB = leftWing.getChild("left_wing_b");
        rightFire = rightWing.getChild("right_wing_fire");
        leftFire = leftWing.getChild("left_wing_fire");
        rightFireB = rightWingB.getChild("right_wing_b_fire");
        leftFireB = leftWingB.getChild("left_wing_b_fire");
        for (int i = 0; i < 6; i++) shards[i] = root.getChild("shard" + i);
    }

    public static LayerDefinition createBodyLayer() {
        return SeveredSeraphMesh.create();
    }

    @Override
    public void setupAnim(RiftBeastRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks, speed = Math.min(1F, state.walkAnimationSpeed), pet = state.pet;
        int phase = state.familiar ? 1 : state.phase;
        boolean rest = state.dormant > 0.5F; // resting as a familiar: the whole of it, small
        mask.visible = phase < 3 || rest;
        maskBroken.visible = face.visible = phase >= 3 && !rest;
        halo.visible = phase < 3 || rest;
        haloBits.visible = phase >= 3 && !rest;
        boolean burning = phase >= 2;
        rightFire.visible = leftFire.visible = rightFireB.visible = leftFireB.visible = burning;
        for (int i = 0; i < 6; i++) shards[i].visible = phase >= 2 && !rest; // they still cut in phase III: still seen
        float bob = Mth.sin(age * 0.08F) * 1.2F - 2F;
        for (ModelPart p : new ModelPart[] {head, body, rightArm, leftArm}) p.y += bob;
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        halo.yRot = age * (rest ? 0.08F : 0.04F);
        float rate = phase == 3 ? 0.42F : phase == 2 ? 0.3F : 0.18F;
        float beat = Mth.sin(age * (rate + 0.12F * speed)), amp = phase == 3 ? 0.4F : 0.25F;
        rightWing.zRot += -amp * beat;
        leftWing.zRot += amp * beat;
        rightWing.yRot += 0.15F * speed;
        leftWing.yRot -= 0.15F * speed;
        rightWingB.zRot += -(amp + 0.1F) * beat;
        leftWingB.zRot += (amp + 0.1F) * beat;
        if (burning) { // the flames licking up off the feathers
            float f = 1 + 0.25F * Mth.sin(age * 0.8F);
            rightFire.yScale = leftFire.yScale = f;
            rightFireB.yScale = leftFireB.yScale = 2 - f;
        }
        body.xRot = 0.15F * speed;
        skirt.xRot += 0.3F * speed + Mth.sin(age * 0.1F) * 0.05F;
        skirtB.xRot += 0.2F * speed + Mth.sin(age * 0.1F - 0.6F) * 0.08F;
        skirtC.xRot += 0.2F * speed + Mth.sin(age * 0.1F - 1.2F) * 0.1F;
        rightArm.xRot = -0.1F + Mth.sin(age * 0.07F) * 0.05F;
        leftArm.xRot = -0.1F - Mth.sin(age * 0.07F) * 0.05F;
        rightArm.zRot = 0.15F;
        leftArm.zRot = -0.15F;
        if (phase >= 2) { // the halo's shards circling as blades
            for (int i = 0; i < 6; i++) {
                float a = age * 0.25F + i * Mth.TWO_PI / 6;
                shards[i].x = Mth.cos(a) * 20;
                shards[i].z = Mth.sin(a) * 20;
                shards[i].y = 6 + bob + Mth.sin(age * 0.2F + i) * 2;
                shards[i].yRot = -a;
                shards[i].zRot = Mth.HALF_PI;
                shards[i].xRot = age * 0.4F;
            }
        }
        if (phase >= 3) { // unmasked: twitching, the halo's bits drifting, the floating pieces of the mask turning away
            if (Mth.sin(age * 1.7F) > 0.93F) head.zRot += 0.25F * Mth.sin(age * 9F);
            haloBits.yRot = age * 0.03F;
            haloBits.y += Mth.sin(age * 0.11F) * 0.8F;
            maskBroken.yRot = Mth.sin(age * 0.05F) * 0.06F;
            rightArm.zRot += Mth.sin(age * 0.6F) * 0.05F;
        }
        float sw = state.swing; // the blow: a sweep of the right hand, fingers like knives
        rightArm.xRot -= 1.6F * sw;
        rightArm.yRot = -0.6F * sw;
        body.yRot = -0.2F * sw;
        if (pet > 0) { // bows, wings folded round it, the halo low
            head.xRot = Mth.lerp(pet, head.xRot, 0.6F);
            body.xRot = Mth.lerp(pet, body.xRot, 0.3F);
            rightWing.yRot = Mth.lerp(pet, rightWing.yRot, 1.2F);
            leftWing.yRot = Mth.lerp(pet, leftWing.yRot, -1.2F);
            rightWingB.yRot = Mth.lerp(pet, rightWingB.yRot, 0.8F);
            leftWingB.yRot = Mth.lerp(pet, leftWingB.yRot, -0.8F);
            halo.y += 2.5F * pet;
            rightArm.xRot = Mth.lerp(pet, rightArm.xRot, -0.8F);
            leftArm.xRot = Mth.lerp(pet, leftArm.xRot, -0.8F);
            rightArm.yRot = Mth.lerp(pet, rightArm.yRot, -0.5F);
            leftArm.yRot = Mth.lerp(pet, leftArm.yRot, 0.5F);
        }
        if (state.move >= 0) move(state.move, state.moveTime, (state.visual & 1) != 0);
        if (state.special >= 0) special(state.special, state.specialTime);
        if (state.intro >= 0) intro(state.intro);
    }

    /** The moves (heavenly dive, blade fan, feather storm, blink slash); landed: its dive has struck the floor. */
    private void move(int move, float t, boolean landed) {
        switch (move) {
            case 0 -> { // dive: wings up and back as it climbs, folded tight as it falls, flung open as it lands
                if (landed) {
                    rightWing.zRot += 0.7F;
                    leftWing.zRot -= 0.7F;
                    head.xRot += 0.4F;
                    rightArm.xRot = -0.3F;
                    leftArm.xRot = -0.3F;
                    rightArm.zRot = 0.6F;
                    leftArm.zRot = -0.6F;
                } else if (t < 26) {
                    float up = t / 26F;
                    rightWing.zRot += 0.6F * up;
                    leftWing.zRot -= 0.6F * up;
                    head.xRot -= 0.3F * up;
                } else {
                    rightWing.yRot += 1.1F;
                    leftWing.yRot -= 1.1F;
                    body.xRot += 0.9F;
                    head.xRot += 0.5F;
                    rightArm.xRot = -2.8F;
                    leftArm.xRot = -2.8F;
                }
            }
            case 1 -> { // blade fan: wings drawn back, then flung forward with both hands
                float draw = t < 10 ? t / 10F : 0, fling = t >= 10 ? Mth.clamp(1 - (t - 10) / 10F, 0, 1) : 0;
                rightWing.yRot += -0.8F * draw + 0.9F * fling;
                leftWing.yRot += 0.8F * draw - 0.9F * fling;
                rightArm.xRot = -1.4F * fling;
                leftArm.xRot = -1.4F * fling;
                rightArm.yRot = 0.5F * fling;
                leftArm.yRot = -0.5F * fling;
            }
            case 2 -> { // feather storm: spinning with its wings spread, throwing feathers off them
                float spin = Mth.clamp((t - 10) / 30F, 0, 1);
                body.yRot += spin * 18F;
                rightWing.zRot += 0.5F;
                leftWing.zRot -= 0.5F;
                rightArm.zRot = 1.2F;
                leftArm.zRot = -1.2F;
            }
            case 3 -> { // blink slash: a cut each time it comes out of a rift
                float k = t < 6 ? 0 : ((t - 6) % 10) / 10F;
                float cut = k < 0.3F ? k / 0.3F : 1 - (k - 0.3F) / 0.7F;
                rightArm.xRot = -2.2F * cut;
                rightArm.yRot = 0.8F - 1.6F * k;
                body.yRot = 0.4F - 0.8F * k;
            }
            default -> {}
        }
    }

    /** The arena specials (halo ring, judgement, chasing judgement). */
    private void special(int special, float t) {
        switch (special) {
            case 0 -> { // halo ring: the halo sinks down round it and is cast to the floor
                float drop = Mth.clamp(t / 10F, 0, 1);
                halo.y += 14 * drop;
                halo.xRot = 0;
                haloBits.y += 14 * drop;
                rightArm.xRot = -0.8F * drop;
                leftArm.xRot = -0.8F * drop;
                rightArm.zRot = 0.8F * drop;
                leftArm.zRot = -0.8F * drop;
            }
            case 1, 2 -> { // judgement: arms raised, wings spread wide, the halo blazing high
                float raise = Mth.clamp(t / 14F, 0, 1);
                rightArm.xRot = -2.9F * raise;
                leftArm.xRot = (special == 2 ? -1.6F : -2.9F) * raise;
                rightArm.zRot = 0.4F * raise;
                leftArm.zRot = -0.4F * raise;
                rightWing.zRot += 0.5F * raise;
                leftWing.zRot -= 0.5F * raise;
                halo.y -= 3F * raise;
                halo.yRot += t * 0.3F;
                head.xRot -= 0.5F * raise;
            }
            default -> {}
        }
    }

    /** Its entrance: folded in its wings, head bowed, coming down the light; then the wings fling open and the halo lights. */
    private void intro(float t) {
        float open = Mth.clamp((t - 62) / 7F, 0, 1), over = open * (1 + 0.35F * Mth.clamp(1 - (t - 69) / 8F, 0, 1));
        float fold = 1 - open;
        rightWing.yRot = Mth.lerp(fold, rightWing.yRot, 1.3F);
        leftWing.yRot = Mth.lerp(fold, leftWing.yRot, -1.3F);
        rightWingB.yRot = Mth.lerp(fold, rightWingB.yRot, 0.9F);
        leftWingB.yRot = Mth.lerp(fold, leftWingB.yRot, -0.9F);
        rightWing.zRot += -0.7F * over;
        leftWing.zRot += 0.7F * over;
        head.xRot = Mth.lerp(fold, head.xRot, 0.6F) - 0.3F * open;
        rightArm.xRot = Mth.lerp(fold, rightArm.xRot, -0.8F);
        leftArm.xRot = Mth.lerp(fold, leftArm.xRot, -0.8F);
        rightArm.zRot = 0.15F + 0.9F * over;
        leftArm.zRot = -0.15F - 0.9F * over;
        halo.xScale = halo.yScale = halo.zScale = over;
        halo.visible = open > 0;
    }
}
