package com.lealex.alchyforbidden.client.familiar;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.client.familiar.mesh.VoidGolemMesh;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The Void Golem (mesh and texture from tools/models.py: an obsidian giant, crying obsidian shards out of its shoulders
 * and down its back, a band of runes at its waist, a rune chest plate over the recess where its rift core burns, a low
 * horned head with a slit of sight and a jaw of stone, arms to its knees ending in rock fists with knuckle shards, short
 * legs with knee plates), drawn 2.4 times larger.
 * Phase I: whole, slow and heavy. Phase II: the chest plate is gone and the bared core shows through ribs of stone, the
 * shell is split with light, it walks hunched and faster; the rubble shield's stones circle it (as many as are left,
 * RiftBeastRenderState.visual). Phase III: only the core, swollen, floating, the shell's six pieces orbiting it and
 * tumbling. Resting as a familiar, the core alone at its owner's shoulder. Petted, it kneels on one fist.
 * RiftBeastRenderState.visual: bits 0-2 the shield stones left, bits 3-4 its opening (1 fists stuck in the floor,
 * 2 staggered, 3 the core venting). Arena specials by their index in VoidGolem.specialIds().
 */
public class VoidGolemModel extends EntityModel<RiftBeastRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "void_golem"), "main");
    private final ModelPart body, plate, cage, cracks, head, core, rightArm, leftArm, rightArmCracks, leftArmCracks, rightLeg, leftLeg;
    private final ModelPart[] rubble = new ModelPart[4], stones = new ModelPart[6], shells = new ModelPart[6];

    public VoidGolemModel(ModelPart root) {
        super(root);
        body = root.getChild("body");
        plate = body.getChild("plate");
        cage = body.getChild("cage");
        cracks = body.getChild("cracks");
        head = body.getChild("head");
        core = root.getChild("core");
        rightArm = root.getChild("right_arm");
        leftArm = root.getChild("left_arm");
        rightArmCracks = rightArm.getChild("right_arm_cracks");
        leftArmCracks = leftArm.getChild("left_arm_cracks");
        rightLeg = root.getChild("right_leg");
        leftLeg = root.getChild("left_leg");
        for (int i = 0; i < 4; i++) rubble[i] = core.getChild("rubble" + i);
        for (int i = 0; i < 6; i++) {
            stones[i] = root.getChild("stone" + i);
            shells[i] = root.getChild("shell" + i);
        }
    }

    public static LayerDefinition createBodyLayer() {
        return VoidGolemMesh.create();
    }

    @Override
    public void setupAnim(RiftBeastRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks;
        int phase = state.familiar ? 1 : state.phase;
        boolean rest = state.dormant > 0.5F, bare = phase >= 3 && !rest; // only the core left
        int stoneCount = state.visual & 7, opening = (state.visual >> 3) & 3;
        float shift = Mth.clamp(state.phaseTime / 40F, 0, 1);
        body.visible = rightArm.visible = leftArm.visible = rightLeg.visible = leftLeg.visible = !bare;
        plate.visible = phase == 1;
        cage.visible = cracks.visible = rightArmCracks.visible = leftArmCracks.visible = phase == 2;
        for (int i = 0; i < 6; i++) {
            shells[i].visible = phase >= 3 && !rest;
            stones[i].visible = !bare && i < stoneCount;
        }
        if (rest) { // resting: the whole golem, small, floating with its arms and legs hanging and swaying
            plate.visible = true;
            cage.visible = cracks.visible = rightArmCracks.visible = leftArmCracks.visible = false;
        }
        for (ModelPart r : rubble) r.visible = bare;
        float pulse = 1 + (phase >= 2 ? 0.14F : 0.08F) * Mth.sin(age * (phase >= 2 ? 0.35F : 0.2F));
        core.xScale = core.yScale = core.zScale = (bare ? 1.8F : 1F) * pulse;
        core.yRot = age * 0.05F;
        if (bare) { // the free core floats where the chest was, its rubble close round it and the shell's pieces in a wide orbit
            core.y = -2 + Mth.sin(age * 0.08F) * 1.5F;
            core.z = 0;
            for (int i = 0; i < 4; i++) {
                float a = age * 0.12F + i * Mth.HALF_PI;
                rubble[i].x = Mth.cos(a) * 4.5F;
                rubble[i].z = Mth.sin(a) * 4.5F;
                rubble[i].y = Mth.sin(a * 2) * 1.5F;
                rubble[i].xRot = age * 0.2F + i;
            }
            for (int i = 0; i < 6; i++) {
                float a = age * 0.05F + i * Mth.TWO_PI / 6, r = 14 * shift + 4 + 2 * Mth.sin(age * 0.07F + i);
                shells[i].x = Mth.cos(a) * r;
                shells[i].z = Mth.sin(a) * r;
                shells[i].y = -2 + Mth.sin(age * 0.09F + i * 1.3F) * 5;
                shells[i].xRot = age * 0.04F + i;
                shells[i].yRot = -a;
                shells[i].zRot = age * 0.03F * (i % 2 == 0 ? 1 : -1);
            }
            if (state.move == 3) { // void pull, its only move left: the bare core swells as it pulls, then bursts at the blast (30)
                float t = state.moveTime, open = Mth.clamp(t / 10F, 0, 1), blast = Mth.clamp((t - 30) / 4F, 0, 1);
                core.xScale *= 1 + 0.5F * open - 0.4F * blast;
                core.yScale = core.zScale = core.xScale;
            }
            if (state.special >= 0) bareSpecial(state.special, state.specialTime, age);
            return;
        }
        float pace = phase == 2 ? 0.6F : 0.45F;
        float pos = state.walkAnimationPos * pace, speed = Math.min(1F, state.walkAnimationSpeed);
        head.yRot = state.yRot * Mth.DEG_TO_RAD;
        head.xRot = state.xRot * Mth.DEG_TO_RAD;
        rightLeg.xRot = Mth.cos(pos) * 0.6F * speed;
        leftLeg.xRot = Mth.cos(pos + Mth.PI) * 0.6F * speed;
        rightArm.xRot = Mth.cos(pos + Mth.PI) * 0.5F * speed;
        leftArm.xRot = Mth.cos(pos) * 0.5F * speed;
        rightArm.zRot = 0.1F;
        leftArm.zRot = -0.1F;
        float breath = Mth.sin(age * 0.04F) * 0.4F, sway = Mth.sin(pos) * 0.05F * speed;
        body.y += breath + Math.abs(Mth.sin(pos)) * 0.8F * speed;
        body.zRot = sway;
        core.y += breath;
        if (phase == 2) { // cracked: hunched, its core pushed forward and burning, shoulders twitching
            body.xRot = 0.18F * shift;
            head.y += 2 * shift;
            head.xRot += 0.2F * shift;
            core.z -= 1.2F * shift;
            core.y += 1F * shift;
            rightArm.z -= 2 * shift;
            leftArm.z -= 2 * shift;
            rightArm.zRot += Mth.sin(age * 0.9F) * 0.02F;
            leftArm.zRot -= Mth.sin(age * 0.8F + 1) * 0.02F;
            for (int i = 0; i < 6; i++) { // the rubble shield's stones circling it
                float a = age * 0.09F + i * Mth.TWO_PI / 6;
                stones[i].x = Mth.cos(a) * 16;
                stones[i].z = Mth.sin(a) * 16;
                stones[i].y = 2 + Mth.sin(age * 0.11F + i) * 3;
                stones[i].xRot = age * 0.15F + i;
                stones[i].yRot = age * 0.1F;
            }
        }
        float t = state.swingTime; // the blow: both fists raised and brought down together
        if (t > 0) {
            float up = t < 0.45F ? t / 0.45F : 0, down = t >= 0.45F ? 1 - (t - 0.45F) / 0.55F : 0;
            rightArm.xRot = -2.8F * up - 0.6F * down;
            leftArm.xRot = -2.8F * up - 0.6F * down;
            body.xRot += 0.35F * down;
        }
        if (rest) {
            float s = Mth.sin(age * 0.06F);
            rightLeg.xRot = 0.15F + s * 0.12F;
            leftLeg.xRot = 0.15F - s * 0.12F;
            rightArm.xRot = -0.1F - s * 0.15F;
            leftArm.xRot = -0.1F + s * 0.15F;
            rightArm.zRot = 0.25F;
            leftArm.zRot = -0.25F;
            head.xRot += Mth.sin(age * 0.04F) * 0.1F;
        }
        if (state.pet > 0) {
            float f = state.pet, drop = 5F * f;
            body.y += drop;
            core.y += drop;
            rightArm.y += drop;
            leftArm.y += drop;
            rightArm.xRot = Mth.lerp(f, rightArm.xRot, -0.5F);
            head.xRot = Mth.lerp(f, head.xRot, 0.6F);
            rightLeg.xRot = Mth.lerp(f, rightLeg.xRot, -1.2F);
        }
        if (state.move >= 0) move(state.move, state.moveTime);
        if (state.special >= 0) special(state.special, state.specialTime);
        if (opening > 0) opening(opening, age);
        if (state.intro >= 0) intro(state.intro);
    }

    /** The body and arms lowered together (the legs stay on the floor: a crouch). */
    private void drop(float d) {
        body.y += d;
        core.y += d;
        rightArm.y += d;
        leftArm.y += d;
    }

    /** Eases from a to b as t goes 0..1 (smoothstep). */
    private static float ease(float a, float b, float t) {
        t = Mth.clamp(t, 0, 1);
        return Mth.lerp(t * t * (3 - 2 * t), a, b);
    }

    /** Fists driven into the floor before it, hunched over them (the end of the leap, and the stuck pose). */
    private void fistsDown(float f) {
        rightArm.xRot = Mth.lerp(f, rightArm.xRot, -0.5F);
        leftArm.xRot = Mth.lerp(f, leftArm.xRot, -0.5F);
        rightArm.zRot = Mth.lerp(f, rightArm.zRot, 0.18F);
        leftArm.zRot = Mth.lerp(f, leftArm.zRot, -0.18F);
        body.xRot += 0.5F * f;
        head.xRot += 0.3F * f;
        drop(5 * f);
    }

    /** Its openings: fists stuck in the floor and tugging, staggered with its guard broken, the core venting. */
    private void opening(int kind, float age) {
        switch (kind) {
            case 1 -> { // stuck: hunched over its fists in the floor, wrenching at them in turn
                fistsDown(1);
                float tug = Mth.sin(age * 0.8F);
                rightArm.xRot += 0.08F * tug;
                leftArm.xRot -= 0.08F * tug;
                rightArm.y -= Math.max(0, tug) * 1.2F;
                leftArm.y -= Math.max(0, -tug) * 1.2F;
                body.zRot += 0.06F * tug;
                body.xRot += 0.04F * Mth.sin(age * 1.7F);
                head.yRot += 0.15F * tug;
            }
            case 2 -> { // staggered: rocking back, arms thrown out, head lolling
                float rock = Mth.sin(age * 0.45F);
                body.xRot -= 0.3F + 0.08F * rock;
                body.zRot += 0.14F * Mth.sin(age * 0.3F);
                rightArm.xRot = 0.3F + 0.2F * rock;
                leftArm.xRot = 0.3F - 0.2F * rock;
                rightArm.zRot = 0.9F + 0.15F * rock;
                leftArm.zRot = -0.9F + 0.15F * rock;
                head.xRot = -0.45F + 0.1F * Mth.sin(age * 0.6F);
                head.zRot = 0.2F * Mth.sin(age * 0.35F);
                rightLeg.xRot = -0.25F;
                leftLeg.xRot = 0.2F;
            }
            case 3 -> { // venting: chest thrown open, the core flaring and shaking, arms hanging
                float flare = 1.35F + 0.25F * Mth.sin(age * 1.3F);
                core.xScale *= flare;
                core.yScale *= flare;
                core.zScale *= flare;
                core.x += 0.3F * Mth.sin(age * 2.9F);
                body.xRot -= 0.3F;
                head.xRot = -0.5F;
                rightArm.xRot = 0.25F;
                leftArm.xRot = 0.25F;
                rightArm.zRot = 0.4F;
                leftArm.zRot = -0.4F;
                drop(1.5F);
            }
            default -> {}
        }
    }

    /** Phase III specials of the bare core: the shell's pieces held up, drawn in, or flung out. */
    private void bareSpecial(int special, float t, float age) {
        float ring = 1, scale = 1, lift = 0;
        switch (special) {
            case 6 -> lift = -10 * Mth.sin(Mth.clamp(t / 50F, 0, 1) * Mth.PI); // the shell's pieces raised high before they rain
            case 7 -> { // dash: shrinks as it gathers, the pieces drawn tight, then a swollen rush
                float gather = Mth.clamp(t / 16F, 0, 1), go = Mth.clamp((t - 16) / 3F, 0, 1) * Mth.clamp((32 - t) / 4F, 0, 1);
                scale = 1 - 0.25F * gather * (1 - go) + 0.35F * go;
                ring = 1 - 0.55F * gather * (1 - go) - 0.3F * go;
            }
            case 8 -> { // collapse: swells and draws the pieces in, trembles, bursts at 58
                float pull = Mth.clamp(t / 34F, 0, 1), burst = Mth.clamp((t - 58) / 3F, 0, 1), back = Mth.clamp((t - 62) / 10F, 0, 1);
                scale = 1 + 0.8F * pull * (1 - burst) - 0.4F * burst * (1 - back);
                ring = (1 - 0.7F * pull) * (1 - burst) + burst * Mth.lerp(back, 2.4F, 1);
                if (t > 34 && t < 58) {
                    core.x += 0.6F * Mth.sin(age * 3.1F);
                    core.y += 0.6F * Mth.sin(age * 2.3F);
                }
            }
            default -> {}
        }
        core.xScale *= scale;
        core.yScale *= scale;
        core.zScale *= scale;
        for (ModelPart shell : shells) {
            shell.x *= ring;
            shell.z *= ring;
            shell.y += lift;
        }
    }

    /** The moves (slam, slab throw, stone guard, void pull). */
    private void move(int move, float t) {
        switch (move) {
            case 0 -> { // slam: both fists high, then down into the floor
                float up = t < 18 ? Mth.clamp(t / 12F, 0, 1) : 0, down = t >= 18 ? Mth.clamp(1 - (t - 18) / 14F, 0, 1) : 0;
                rightArm.xRot = -3.0F * up - 0.9F * down;
                leftArm.xRot = -3.0F * up - 0.9F * down;
                body.xRot += -0.15F * up + 0.5F * down;
                body.y += 2 * down;
            }
            case 1 -> { // slab throw: one arm down to tear the floor, lifted overhead, then hurled forward at the strike (16)
                float tear = Mth.clamp(t / 8F, 0, 1), lift = Mth.clamp((t - 9) / 7F, 0, 1);
                float hurl = Mth.clamp((t - 16) / 4F, 0, 1), rest = Mth.clamp((t - 20) / 8F, 0, 1);
                float arm = Mth.lerp(lift, 0.4F * tear, -3.0F);
                arm = Mth.lerp(hurl, arm, -0.9F);
                rightArm.xRot = Mth.lerp(rest, arm, rightArm.xRot);
                float lean = Mth.lerp(lift, 0.5F * tear, -0.2F);
                body.xRot += Mth.lerp(rest, Mth.lerp(hurl, lean, 0.3F), 0);
                body.yRot = -0.3F * lift * (1 - rest);
            }
            case 2 -> { // guard: arms crossed before it
                float raise = Mth.clamp(t / 4F, 0, 1);
                rightArm.xRot = -1.5F * raise;
                leftArm.xRot = -1.5F * raise;
                rightArm.yRot = -0.7F * raise;
                leftArm.yRot = 0.7F * raise;
                head.y += 1.5F * raise;
            }
            case 3 -> { // void pull: chest thrust out, arms spread, the core swelling, then the blast
                float open = Mth.clamp(t / 10F, 0, 1), blast = Mth.clamp((t - 30) / 4F, 0, 1);
                rightArm.zRot = 0.9F * open + 0.5F * blast;
                leftArm.zRot = -0.9F * open - 0.5F * blast;
                body.xRot += -0.25F * open;
                core.xScale *= 1 + 0.5F * open - 0.4F * blast;
                core.yScale = core.zScale = core.xScale;
            }
            default -> {}
        }
    }

    /** The arena specials (core beam, rubble shield, leap, boulder, fissure, earthquake, rock rain). */
    private void special(int special, float t) {
        switch (special) {
            case 0 -> { // core beam: arms drawn back, chest out, the core swelling, held through the sweep
                float charge = Mth.clamp(t / 30F, 0, 1);
                rightArm.xRot = 0.8F * charge;
                leftArm.xRot = 0.8F * charge;
                rightArm.zRot = 0.5F * charge;
                leftArm.zRot = -0.5F * charge;
                body.xRot -= 0.3F * charge;
                float s = 1 + 0.6F * charge + (t > 30 ? 0.1F * Mth.sin(t * 2) : 0);
                core.xScale *= s;
                core.yScale *= s;
                core.zScale *= s;
                if (t > 30) body.yRot = Mth.sin((t - 30) / 20F * Mth.PI) * 0.6F - 0.3F;
            }
            case 1 -> { // rubble shield: fists raised to the sky as the stones rise
                float raise = Mth.sin(Mth.clamp(t / 20F, 0, 1) * Mth.PI);
                rightArm.xRot = -2.6F * raise;
                leftArm.xRot = -2.6F * raise;
                head.xRot -= 0.5F * raise;
            }
            case 2 -> leap(t);
            case 3 -> boulder(t);
            case 4 -> { // fissure: bent over, its right fist dragged through the floor, then both brought down at the burst (64)
                float bend = Mth.clamp(t / 8F, 0, 1) * Mth.clamp((70 - t) / 6F, 0, 1);
                float drag = Mth.sin(t * 0.25F);
                body.xRot += 0.4F * bend;
                body.yRot = 0.25F * bend;
                drop(4 * bend);
                rightArm.xRot = Mth.lerp(bend, rightArm.xRot, -0.35F + 0.08F * drag);
                rightArm.zRot = Mth.lerp(bend, rightArm.zRot, 0.15F + 0.1F * drag);
                leftArm.xRot = Mth.lerp(bend, leftArm.xRot, -0.6F);
                leftArm.zRot = Mth.lerp(bend, leftArm.zRot, -0.35F);
                head.xRot += 0.25F * bend;
                if (t > 56) { // both fists up, then down at the burst
                    float up = Mth.clamp((t - 56) / 6F, 0, 1), down = Mth.clamp((t - 62) / 2F, 0, 1);
                    float arm = Mth.lerp(down, Mth.lerp(up, -0.35F, -2.9F), -0.7F);
                    rightArm.xRot = arm;
                    leftArm.xRot = arm;
                }
            }
            case 5 -> { // earthquake: three stomps (16, 36, 56), a leg and both fists raised before each, brought down together
                int k = t < 26 ? 0 : t < 46 ? 1 : 2;
                float stomp = 16 + 20 * k, up = Mth.clamp((t - (stomp - 10)) / 8F, 0, 1), down = Mth.clamp((t - stomp) / 2F, 0, 1);
                float raise = up * (1 - down), settle = down * Mth.clamp(1 - (t - stomp - 2) / 6F, 0, 1);
                ModelPart leg = k == 1 ? leftLeg : rightLeg;
                leg.xRot = -0.9F * raise;
                body.zRot += (k == 1 ? -0.12F : 0.12F) * raise;
                body.xRot += -0.15F * raise + 0.35F * settle;
                rightArm.xRot = -2.7F * raise - 0.7F * settle;
                leftArm.xRot = -2.7F * raise - 0.7F * settle;
                drop(2.5F * settle);
            }
            case 6 -> { // rock rain: both fists beating at the sky
                float raise = Mth.clamp(t / 8F, 0, 1) * Mth.clamp((56 - t) / 6F, 0, 1), beat = Mth.sin(t * 0.5F);
                rightArm.xRot = (-2.6F + 0.3F * beat) * raise;
                leftArm.xRot = (-2.6F - 0.3F * beat) * raise;
                rightArm.zRot = 0.35F * raise;
                leftArm.zRot = -0.35F * raise;
                head.xRot -= 0.6F * raise;
                body.xRot -= 0.15F * raise;
            }
            default -> {}
        }
    }

    /** The leap slam: a crouch (to 14), arms flung up through the arc, fists slammed into the floor at the landing (30). */
    private void leap(float t) {
        if (t < 14) { // crouched, arms swung back
            float c = Mth.clamp(t / 8F, 0, 1);
            drop(4 * c);
            body.xRot += 0.4F * c;
            rightArm.xRot = 0.9F * c;
            leftArm.xRot = 0.9F * c;
            rightLeg.xRot = -0.5F * c;
            leftLeg.xRot = 0.3F * c;
            head.xRot += 0.2F * c;
        } else if (t < 26) { // in the air: both fists high over its head, legs tucked
            float a = Mth.clamp((t - 14) / 4F, 0, 1);
            rightArm.xRot = ease(0.9F, -3.0F, a);
            leftArm.xRot = ease(0.9F, -3.0F, a);
            body.xRot += -0.2F * a;
            rightLeg.xRot = -0.6F * a;
            leftLeg.xRot = -0.4F * a;
            head.xRot -= 0.3F * a;
        } else { // down: fists into the floor (and held there: the stuck pose follows)
            float k = Mth.clamp((t - 26) / 4F, 0, 1);
            rightArm.xRot = -3.0F;
            leftArm.xRot = -3.0F;
            fistsDown(k);
        }
    }

    /** The boulder: both arms down to tear it out (to 8), lifted overhead (to 12), hurled at 14; the boulder drawn held. */
    private void boulder(float t) {
        float tear = Mth.clamp(t / 6F, 0, 1), lift = Mth.clamp((t - 8) / 4F, 0, 1);
        float hurl = Mth.clamp((t - 13) / 3F, 0, 1), rest = Mth.clamp((t - 17) / 7F, 0, 1);
        float arm = Mth.lerp(lift, -0.5F * tear, -3.0F);
        arm = Mth.lerp(hurl, arm, -1.1F);
        rightArm.xRot = Mth.lerp(rest, arm, rightArm.xRot);
        leftArm.xRot = Mth.lerp(rest, arm, leftArm.xRot);
        float lean = Mth.lerp(lift, 0.5F * tear, -0.25F);
        body.xRot += Mth.lerp(rest, Mth.lerp(hurl, lean, 0.35F), 0);
        drop(Mth.lerp(rest, 4 * tear * (1 - lift), 0));
        if (t > 3 && t < 14) { // the boulder between its fists, from the floor to over its head
            ModelPart rock = stones[0];
            rock.visible = true;
            rock.x = 0;
            rock.y = Mth.lerp(lift, 22, -25);
            rock.z = Mth.lerp(lift, -10, -3.5F);
            rock.xScale = rock.yScale = rock.zScale = 3F * Mth.clamp((t - 3) / 3F, 0, 1);
            rock.xRot = t * 0.2F;
            rock.yRot = t * 0.13F;
        }
    }

    /** Its entrance: its pieces fly out of the rifts and slam into place, head bowed, dark; the core lights; it roars. */
    private void intro(float t) {
        // legs, body, arms and head each fly in on a curve, turning, and land with a bounce
        piece(rightLeg, t, com.lealex.alchyforbidden.familiar.VoidGolem.LEGS_LAND, -34, -36, 14);
        piece(leftLeg, t, com.lealex.alchyforbidden.familiar.VoidGolem.LEGS_LAND + 2, 34, -36, 14);
        piece(body, t, com.lealex.alchyforbidden.familiar.VoidGolem.BODY_LAND, 0, -70, 34);
        piece(rightArm, t, com.lealex.alchyforbidden.familiar.VoidGolem.RIGHT_ARM_LAND, -56, -40, -10);
        piece(leftArm, t, com.lealex.alchyforbidden.familiar.VoidGolem.LEFT_ARM_LAND, 56, -40, -10);
        piece(head, t, com.lealex.alchyforbidden.familiar.VoidGolem.HEAD_LAND, 0, -46, -30);
        float lit = Mth.clamp((t - 66) / 5F, 0, 1), flare = 1 + 0.6F * Mth.clamp(1 - (t - 71) / 8F, 0, 1);
        core.xScale *= lit * flare;
        core.yScale *= lit * flare;
        core.zScale *= lit * flare;
        if (t < 66) {
            head.xRot = 0.55F;
            rightArm.xRot = 0.2F;
            leftArm.xRot = 0.2F;
            rightArm.zRot = 0.35F;
            leftArm.zRot = -0.35F;
        }
        float roar = Mth.clamp((t - 74) / 5F, 0, 1);
        if (roar > 0) {
            rightArm.xRot = Mth.lerp(roar, rightArm.xRot, -2.7F);
            leftArm.xRot = Mth.lerp(roar, leftArm.xRot, -2.7F);
            rightArm.zRot = 0.5F * roar;
            leftArm.zRot = -0.5F * roar;
            head.xRot = -0.6F * roar;
            body.xRot = -0.2F * roar + Mth.sin(t * 2F) * 0.03F * roar;
        }
    }

    /** One piece of the entrance: unseen until it starts, then flying in from (dx, dy, dz) model pixels away, spinning. */
    private static void piece(ModelPart part, float t, int land, float dx, float dy, float dz) {
        int flight = com.lealex.alchyforbidden.familiar.VoidGolem.PIECE_FLIGHT;
        float k = Mth.clamp((t - (land - flight)) / flight, 0F, 1F);
        if (k <= 0F) {
            part.xScale = part.yScale = part.zScale = 0F;
            return;
        }
        float e = 1F - (1F - k) * (1F - k) * (1F - k), left = 1F - e;
        part.x += dx * left;
        part.y += dy * left - Mth.sin(k * Mth.PI) * 10F; // a little arc up on the way
        part.z += dz * left;
        part.xRot += left * 3.2F;
        part.zRot += left * (dx >= 0 ? -2.4F : 2.4F);
        float s = 0.35F + 0.65F * e;
        float bounce = t > land && t < land + 5 ? 1F + 0.08F * Mth.sin((t - land) / 5F * Mth.PI) : 1F;
        part.xScale *= s * bounce;
        part.yScale *= s / bounce;
        part.zScale *= s * bounce;
    }
}
