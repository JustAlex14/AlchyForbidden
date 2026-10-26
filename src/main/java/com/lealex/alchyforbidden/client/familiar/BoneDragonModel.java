package com.lealex.alchyforbidden.client.familiar;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.client.familiar.mesh.BoneDragonMesh;
import com.lealex.alchyforbidden.familiar.BoneDragon;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Ossuarch (mesh and texture from tools/models.py: a dragon's skeleton built vertebra by vertebra, spines along its
 * back, a ribcage round a rift heart with a breastbone and sculk grown along its belly, shoulder blades and a pelvis; a
 * long neck of four vertebrae; a skull with a brow, burning eyes in their sockets, a ridged snout, a frill, cheekbones,
 * fangs and rows of teeth, a toothed jaw and horns sweeping back in three pieces; wings of arm, forearm and a hand of
 * three finger bones with claws, torn sculk skin between them; a six-part tail ending in a glowing bone blade; four
 * legs of thigh, shin and clawed foot), drawn 2.6 times larger.
 * Phase I it flies, the great slow beat of its wings. Phase II its right wing is broken (its skin torn away, hanging and
 * twitching): it crawls on all fours, the left wing folded. Phase III its wings are skins of the void, beating faster.
 * Circling its arena it rolls into the turn (bank, from BoneDragon's visual bits); crashed it sprawls, resting after a
 * strafing run it crouches and pants, a claw rake raises a forefoot and sweeps it. Specials posed: tail sweep, bone walls,
 * raising the bones, wyverns, the arena breath, the strafing run (climb, dive, the pass breathing down), the bone barrage
 * (wings flung up) and the charge (rearing, then head low and legs pumping).
 * Resting as a familiar it is drawn small, gliding. Petted, it lowers its skull and its tail curls round.
 */
public class BoneDragonModel extends EntityModel<RiftBeastRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "bone_dragon"), "main");
    private static final String[] SIDES = {"right", "left"};
    private final ModelPart body, head, jaw;
    private final ModelPart[] neck = new ModelPart[4], tail = new ModelPart[6];
    private final ModelPart[] wing = new ModelPart[2], wingB = new ModelPart[2], wingC = new ModelPart[2];
    private final ModelPart[] wingSkin = new ModelPart[2], wingBSkin = new ModelPart[2], wingVoid = new ModelPart[2], wingBVoid = new ModelPart[2];
    private final ModelPart[][] finger = new ModelPart[2][3], fingerSkin = new ModelPart[2][3], fingerVoid = new ModelPart[2][3];
    private final ModelPart[] legs = new ModelPart[4], shins = new ModelPart[4], feet = new ModelPart[4];

    public BoneDragonModel(ModelPart root) {
        super(root);
        body = root.getChild("body");
        neck[0] = body.getChild("neck0");
        for (int i = 1; i < 4; i++) neck[i] = neck[i - 1].getChild("neck" + i);
        head = neck[3].getChild("head");
        jaw = head.getChild("jaw");
        for (int s = 0; s < 2; s++) {
            String w = SIDES[s] + "_wing";
            wing[s] = body.getChild(w);
            wingSkin[s] = wing[s].getChild(w + "_skin");
            wingVoid[s] = wing[s].getChild(w + "_void");
            wingB[s] = wing[s].getChild(w + "_b");
            wingBSkin[s] = wingB[s].getChild(w + "_b_skin");
            wingBVoid[s] = wingB[s].getChild(w + "_b_void");
            wingC[s] = wingB[s].getChild(w + "_c");
            for (int f = 0; f < 3; f++) {
                String fn = SIDES[s] + "_finger" + f;
                finger[s][f] = wingC[s].getChild(fn);
                fingerSkin[s][f] = finger[s][f].getChild(fn + "_skin");
                fingerVoid[s][f] = finger[s][f].getChild(fn + "_void");
            }
        }
        tail[0] = body.getChild("tail0");
        for (int i = 1; i < 6; i++) tail[i] = tail[i - 1].getChild("tail" + i);
        String[] names = {"right_front", "left_front", "right_hind", "left_hind"};
        for (int i = 0; i < 4; i++) {
            legs[i] = body.getChild(names[i] + "_leg");
            shins[i] = legs[i].getChild(names[i] + "_shin");
            feet[i] = shins[i].getChild(names[i] + "_foot");
        }
    }

    public static LayerDefinition createBodyLayer() {
        return BoneDragonMesh.create();
    }

    /** z rotation lifting a wing's outer end (right wings run to -x, left to +x). */
    private static float lift(int s, float amount) {
        return s == 0 ? amount : -amount;
    }

    /** y rotation sweeping a wing's outer end back. */
    private static float back(int s, float amount) {
        return s == 0 ? amount : -amount;
    }

    @Override
    public void setupAnim(RiftBeastRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks, speed = Math.min(1F, state.walkAnimationSpeed);
        int phase = state.familiar ? 1 : state.phase;
        boolean grounded = phase == 2, small = state.dormant > 0.5F, voidWings = phase == 3;
        // what the fight shows (BoneDragon.V_*): crashed, striding, resting, a claw rake, its bank while circling
        boolean crashed = (state.visual & BoneDragon.V_CRASHED) != 0 && !small, resting = (state.visual & BoneDragon.V_RESTING) != 0 && !small;
        boolean striding = (state.visual & BoneDragon.V_STRIDING) != 0, claw = (state.visual & BoneDragon.V_CLAW) != 0;
        float bank = small ? 0 : (((state.visual >> BoneDragon.V_BANK_SHIFT) & 15) - 8) / 6F * 0.4F;
        float shift = Mth.clamp(state.phaseTime / 40F, 0, 1);
        // what shows: torn sculk skin, the broken right wing's lost skin, the void's wings
        for (int s = 0; s < 2; s++) {
            boolean broken = grounded && s == 0;
            wingSkin[s].visible = !voidWings;
            wingBSkin[s].visible = !voidWings && !broken;
            wingVoid[s].visible = wingBVoid[s].visible = voidWings;
            for (int f = 0; f < 3; f++) {
                fingerSkin[s][f].visible = !voidWings && !broken;
                fingerVoid[s][f].visible = voidWings;
            }
        }
        float look = state.yRot * Mth.DEG_TO_RAD, pitch = state.xRot * Mth.DEG_TO_RAD;
        for (int i = 0; i < 4; i++) { // the neck: an S that breathes, turning with the look
            neck[i].yRot = look * 0.18F + Mth.sin(age * 0.05F - i * 0.6F) * 0.05F;
            neck[i].xRot += Mth.sin(age * 0.06F - i * 0.7F) * 0.05F + pitch * 0.1F;
        }
        head.yRot = look * 0.25F;
        head.xRot += pitch * 0.3F;
        jaw.xRot = (Mth.sin(age * 0.07F) + 1) * 0.06F + state.swing * 0.8F;
        for (int i = 0; i < 6; i++) { // the tail: a wave running down it
            tail[i].yRot = Mth.sin(age * 0.09F - i * 0.65F) * (0.12F + 0.03F * i);
            tail[i].xRot += Mth.sin(age * 0.07F - i * 0.5F) * 0.04F;
        }
        if (crashed) crashed(age);
        else if (grounded) crawl(state, age, speed, shift, striding);
        else if (resting) rested(age);
        else fly(age, small ? 0.12F : voidWings ? 0.26F : 0.17F, speed, bank); // resting small: a little dragon gliding beside its owner
        if (claw && grounded && !crashed) claw(state);
        if (state.pet > 0) {
            float p = state.pet;
            neck[0].xRot = Mth.lerp(p, neck[0].xRot, 0.35F);
            neck[1].xRot = Mth.lerp(p, neck[1].xRot, 0.25F);
            head.xRot = Mth.lerp(p, head.xRot, 0.5F);
            jaw.xRot = Mth.lerp(p, jaw.xRot, 0.02F);
            for (int i = 0; i < 6; i++) tail[i].yRot = Mth.lerp(p, tail[i].yRot, 0.35F);
        }
        if (state.move >= 0) move(state.move, state.moveTime, crashed);
        if (state.special >= 0 && !crashed) special(state.special, state.specialTime, age);
        if (state.intro >= 0) intro(state.intro);
    }

    /** Flying: the great beat, the hand bones opening on the downstroke, legs tucked back; rolled into its turn (bank). */
    private void fly(float age, float rate, float speed, float bank) {
        float beat = Mth.sin(age * rate), lag = Mth.sin(age * rate - 0.8F);
        for (int s = 0; s < 2; s++) {
            wing[s].zRot = lift(s, 0.15F + 0.55F * beat);
            wing[s].yRot = back(s, 0.15F);
            wingB[s].zRot = lift(s, 0.35F * lag);
            wingB[s].yRot += back(s, -0.1F * beat);
            for (int f = 0; f < 3; f++) finger[s][f].yRot += back(s, 0.12F * (1 - beat) * (f + 1) * 0.5F); // fanning shut on the upstroke
        }
        body.y += -beat * 1.5F;
        body.xRot = 0.08F * speed - beat * 0.04F;
        body.zRot = bank;
        for (int i = 0; i < 4; i++) neck[i].zRot = -bank * 0.25F; // the head stays level as the body rolls
        for (int i = 0; i < 4; i++) {
            legs[i].xRot = 0.9F + Mth.sin(age * 0.05F + i) * 0.05F;
            shins[i].xRot = 0.6F;
            feet[i].xRot = 0.5F;
        }
    }

    /** Landed after a strafing run, catching its breath: crouched on its legs, wings half folded, head low, panting. */
    private void rested(float age) {
        float pant = Mth.sin(age * 0.5F);
        body.y += 2.5F + pant * 0.3F;
        body.xRot = 0.1F;
        for (int s = 0; s < 2; s++) {
            wing[s].zRot = lift(s, -0.1F + 0.04F * pant);
            wing[s].yRot = back(s, 0.9F);
            wingB[s].yRot = back(s, -1.6F);
            wingB[s].zRot = lift(s, -0.15F);
            for (int f = 0; f < 3; f++) finger[s][f].yRot += back(s, 0.4F);
        }
        for (int i = 0; i < 4; i++) {
            legs[i].xRot = 0.15F;
            shins[i].xRot = 0.35F;
            feet[i].xRot = -0.4F;
        }
        neck[0].xRot = 0.45F;
        neck[1].xRot = 0.3F;
        neck[2].xRot = 0.05F;
        head.xRot = 0.1F;
        jaw.xRot = 0.3F + 0.15F * pant;
    }

    /** Phase II, a claw rake: the right forefoot raised high and drawn back, then swept across before it (the swing). */
    private void claw(RiftBeastRenderState state) {
        float sweep = state.swingTime; // 0 while raised, then 0..1 through the rake
        legs[0].xRot = Mth.lerp(sweep, -1.7F, -0.2F);
        legs[0].yRot = Mth.lerp(sweep, 0.5F, -0.7F);
        legs[0].zRot = Mth.lerp(sweep, 0.35F, 0F);
        shins[0].xRot = Mth.lerp(sweep, -0.6F, 0.2F);
        body.yRot += Mth.lerp(sweep, 0.2F, -0.25F);
        body.zRot -= 0.06F * (1 - sweep);
        jaw.xRot = Math.max(jaw.xRot, 0.4F);
    }

    /** Crashed on the floor after a dive or the arena breath: sprawled, wings spread flat, neck down, heaving. */
    private void crashed(float age) {
        float heave = Mth.sin(age * 0.25F);
        body.y += 3;
        body.xRot = 0.05F + heave * 0.02F;
        for (int s = 0; s < 2; s++) {
            wing[s].zRot = lift(s, -0.25F);
            wing[s].yRot = back(s, 0.35F);
            wingB[s].zRot = lift(s, -0.2F);
        }
        for (int i = 0; i < 4; i++) {
            legs[i].zRot = i % 2 == 0 ? 0.9F : -0.9F; // legs splayed out
            legs[i].xRot = 0.2F;
        }
        neck[0].xRot = 0.35F;
        neck[1].xRot = 0.25F;
        neck[2].xRot = 0.1F;
        head.xRot = -0.2F;
        jaw.xRot = 0.3F + heave * 0.08F;
        for (int i = 0; i < 6; i++) tail[i].yRot *= 0.3F;
    }

    /** Phase II: on all fours, the left wing folded on its back, the broken right one hanging and twitching. */
    private void crawl(RiftBeastRenderState state, float age, float speed, float shift, boolean striding) {
        // striding (it drives itself on the floor): the legs walk on their own clock, its own steps barely move the walk
        float walk = striding ? age * 0.42F : state.walkAnimationPos * 0.55F;
        if (striding) speed = 1F;
        for (int i = 0; i < 4; i++) { // diagonal pairs
            float ph = walk + (i == 0 || i == 3 ? 0 : Mth.PI);
            legs[i].xRot += Mth.cos(ph) * 0.55F * speed;
            shins[i].xRot += Math.max(0, Mth.sin(ph)) * 0.6F * speed;
        }
        body.y += 2 * shift + Math.abs(Mth.sin(walk)) * 0.6F * speed;
        body.zRot = Mth.sin(walk) * 0.05F * speed + 0.08F * shift; // limping toward the broken side
        // the left wing folded back along its body
        wing[1].zRot = lift(1, 0.35F);
        wing[1].yRot = back(1, 1.25F);
        wingB[1].yRot = back(1, -2.5F);
        wingB[1].zRot = lift(1, -0.2F);
        for (int f = 0; f < 3; f++) finger[1][f].yRot += back(1, 0.6F);
        // the right wing broken, dragging
        wing[0].zRot = lift(0, -1.0F) + Mth.sin(age * 0.37F) * 0.04F;
        wing[0].yRot = back(0, 0.6F);
        wingB[0].zRot = lift(0, -0.9F) + (Mth.sin(age * 0.9F) > 0.92F ? 0.15F : 0); // a twitch now and then
        wingB[0].yRot = back(0, 0.4F);
        for (int f = 0; f < 3; f++) finger[0][f].zRot = lift(0, -0.3F - 0.15F * f);
    }

    /** The moves (bone rain, sonic breath, wyrm dive, sonic roar); crashed: the dive has landed, it lies sprawled. */
    private void move(int move, float t, boolean crashed) {
        switch (move) {
            case 0 -> { // bone rain: wings thrown wide and up, neck raised
                float up = Mth.clamp(t / 8F, 0, 1);
                for (int s = 0; s < 2; s++) {
                    wing[s].zRot = Mth.lerp(up, wing[s].zRot, lift(s, 0.9F));
                    wingB[s].zRot = Mth.lerp(up, wingB[s].zRot, lift(s, 0.3F));
                }
                neck[0].xRot -= 0.3F * up;
            }
            case 1 -> { // breath: neck stretched out straight, jaw wide, the sweep followed by the head
                float open = Mth.clamp(t / 12F, 0, 1);
                for (ModelPart n : neck) n.xRot = Mth.lerp(open, n.xRot, 0.05F);
                head.xRot = Mth.lerp(open, head.xRot, 0.1F);
                jaw.xRot = 1.0F * open;
                if (t > 20) neck[1].yRot += (Mth.sin((t - 20) / 24F * Mth.PI) * 0.8F - 0.4F) * 0.5F;
            }
            case 2 -> { // dive: climbing with wings up, then folded tight and pitched down as it falls
                if (crashed) {
                    // landed: the crashed pose stands
                } else if (t < 24) {
                    for (int s = 0; s < 2; s++) wing[s].zRot = lift(s, 0.9F + 0.2F * Mth.sin(t * 0.5F));
                } else {
                    body.xRot = 0.9F;
                    for (int s = 0; s < 2; s++) {
                        wing[s].yRot = back(s, 1.2F);
                        wing[s].zRot = lift(s, 0.2F);
                        wingB[s].yRot = back(s, -1.8F);
                    }
                    for (ModelPart n : neck) n.xRot = 0.1F;
                    jaw.xRot = 0.9F;
                    for (int i = 0; i < 4; i++) legs[i].xRot = -0.6F; // claws forward
                }
            }
            case 3 -> { // roar: rears, neck thrust out, jaw wide, shaking
                float rear = Mth.clamp(t / 10F, 0, 1);
                body.xRot -= 0.4F * rear;
                legs[0].xRot -= 0.8F * rear;
                legs[1].xRot -= 0.8F * rear;
                for (ModelPart n : neck) n.xRot = Mth.lerp(rear, n.xRot, 0.0F);
                jaw.xRot = 1.1F * rear;
                head.zRot = t > 14 ? Mth.sin(t * 2.5F) * 0.08F : 0;
            }
            default -> {}
        }
    }

    /** The arena specials (in BoneDragon.specialIds() order). */
    private void special(int special, float t, float age) {
        switch (special) {
            case 0 -> { // tail sweep: the body twists back through the warning, then the tail swung round in a whip
                float wind = Mth.clamp(t / 12F, 0, 1), lash = Mth.clamp((t - 12) / 4F, 0, 1);
                body.yRot = 0.5F * wind - 1.2F * lash;
                for (int i = 0; i < 6; i++) tail[i].yRot = -0.25F * wind + 0.4F * lash;
            }
            case 1 -> { // bone walls: rears up and stamps its forefeet down
                float up = t < 12 ? Mth.clamp(t / 10F, 0, 1) : Mth.clamp(1 - (t - 14) / 4F, 0, 1);
                body.xRot -= 0.6F * up;
                legs[0].xRot -= 1.2F * up;
                legs[1].xRot -= 1.2F * up;
                jaw.xRot = 0.6F * up;
            }
            case 2 -> { // raise the bones: head low to the floor, jaw open, calling
                float low = Mth.clamp(t / 10F, 0, 1);
                neck[0].xRot += 0.4F * low;
                neck[1].xRot += 0.3F * low;
                jaw.xRot = 0.9F * low + Mth.sin(t * 1.5F) * 0.05F;
            }
            case 3 -> { // wyverns: wings flung wide, a roar to the sky
                float up = Mth.clamp(t / 8F, 0, 1);
                for (int s = 0; s < 2; s++) wing[s].zRot = Mth.lerp(up, wing[s].zRot, lift(s, 1.0F));
                neck[0].xRot -= 0.6F * up;
                head.xRot -= 0.4F * up;
                jaw.xRot = 1.0F * up;
            }
            case 4 -> { // arena breath: neck straight, the head carried round a full turn with the breath
                float open = Mth.clamp(t / 20F, 0, 1);
                for (ModelPart n : neck) n.xRot = Mth.lerp(open, n.xRot, 0.08F);
                jaw.xRot = 1.0F * open;
                if (t >= 24) body.yRot = (t - 24) / 56F * Mth.TWO_PI;
            }
            case 5 -> strafe(t);
            case 6 -> { // bone barrage: wings flung up and held, shuddering, neck raised, jaw wide
                float up = Mth.clamp(t / 10F, 0, 1), shudder = t > 10 && t < 42 ? Mth.sin(t * 2.2F) * 0.05F : 0;
                for (int s = 0; s < 2; s++) {
                    wing[s].zRot = Mth.lerp(up, wing[s].zRot, lift(s, 1.05F + shudder));
                    wingB[s].zRot = Mth.lerp(up, wingB[s].zRot, lift(s, 0.4F));
                    for (int f = 0; f < 3; f++) finger[s][f].yRot = Mth.lerp(up, finger[s][f].yRot, back(s, -0.15F * f)); // fingers fanned wide
                }
                body.xRot -= 0.25F * up;
                neck[0].xRot -= 0.5F * up;
                neck[1].xRot -= 0.2F * up;
                head.xRot -= 0.4F * up;
                jaw.xRot = Math.max(jaw.xRot, 1.0F * up);
                body.y -= shudder * 10;
            }
            case 7 -> charge(t, age);
            default -> {}
        }
    }

    /** The strafing run: climbing with hard beats, pitching into the dive, then the pass: wings swept back, neck stretched down, breathing. */
    private void strafe(float t) {
        if (t < 12) { // pulling up
            float beat = Mth.sin(t * 0.6F);
            body.xRot = -0.3F * Mth.clamp(t / 6F, 0, 1);
            for (int s = 0; s < 2; s++) {
                wing[s].zRot = lift(s, 0.2F + 0.8F * beat);
                wingB[s].zRot = lift(s, 0.3F * beat);
            }
            return;
        }
        float dive = Mth.clamp((t - 12) / 10F, 0, 1), pass = Mth.clamp((t - 28) / 6F, 0, 1);
        body.xRot = Mth.lerp(pass, 0.35F * dive, 0.1F);
        for (int s = 0; s < 2; s++) {
            wing[s].yRot = back(s, Mth.lerp(pass, 0.6F * dive, 1.0F));
            wing[s].zRot = lift(s, Mth.lerp(pass, 0.3F, 0.15F));
            wingB[s].yRot = back(s, -0.9F * pass);
            wingB[s].zRot = lift(s, 0.1F);
        }
        for (int i = 0; i < 4; i++) legs[i].xRot = Mth.lerp(dive, legs[i].xRot, 1.3F); // tucked back
        neck[0].xRot = Mth.lerp(pass, neck[0].xRot, 0.35F);
        for (int i = 1; i < 4; i++) neck[i].xRot = Mth.lerp(pass, neck[i].xRot, 0.15F);
        head.xRot = Mth.lerp(pass, head.xRot, 0.3F);
        jaw.xRot = Mth.lerp(pass, jaw.xRot, 1.0F + Mth.sin(t * 1.7F) * 0.06F); // breathing down
        for (int i = 0; i < 6; i++) tail[i].yRot *= 1 - 0.7F * pass; // streaming straight behind
    }

    /** The charge (phase II): rearing and pawing, then head low, legs pumping, tail straight out behind. */
    private void charge(float t, float age) {
        if (t < 20) {
            float up = Mth.clamp(t / 8F, 0, 1);
            body.xRot -= 0.55F * up;
            legs[0].xRot = -1.3F * up + Mth.sin(t * 0.8F) * 0.3F;
            legs[1].xRot = -1.3F * up - Mth.sin(t * 0.8F) * 0.3F;
            neck[0].xRot -= 0.3F * up;
            jaw.xRot = Math.max(jaw.xRot, 0.7F * up);
            return;
        }
        float run = Mth.clamp((t - 20) / 4F, 0, 1), gait = age * 1.1F;
        body.xRot = 0.15F * run;
        body.y += Math.abs(Mth.sin(gait)) * 0.8F * run;
        for (int i = 0; i < 4; i++) {
            float ph = gait + (i == 0 || i == 3 ? 0 : Mth.PI);
            legs[i].xRot = Mth.lerp(run, legs[i].xRot, Mth.cos(ph) * 0.9F);
            shins[i].xRot = Mth.lerp(run, shins[i].xRot, Math.max(0, Mth.sin(ph)) * 0.8F);
        }
        neck[0].xRot = Mth.lerp(run, neck[0].xRot, 0.5F);
        neck[1].xRot = Mth.lerp(run, neck[1].xRot, 0.3F);
        neck[2].xRot = Mth.lerp(run, neck[2].xRot, 0.1F);
        head.xRot = Mth.lerp(run, head.xRot, -0.3F); // snout thrust ahead, horns first
        for (int i = 0; i < 6; i++) tail[i].yRot *= 1 - 0.8F * run;
    }

    /** Its entrance: hard wingbeats out of the rift, then the roar, neck up, jaw wide, wings flung open. */
    private void intro(float t) {
        if (t < 70) {
            float beat = Mth.sin(t * 0.32F);
            for (int s = 0; s < 2; s++) wing[s].zRot = lift(s, 0.2F + 0.75F * beat);
            for (ModelPart n : neck) n.xRot = 0.05F;
            return;
        }
        float roar = Mth.clamp((t - 70) / 6F, 0, 1);
        neck[0].xRot = -0.5F * roar;
        neck[1].xRot = -0.3F * roar;
        head.xRot = -0.4F * roar;
        jaw.xRot = 1.1F * roar + Mth.sin(t * 2.4F) * 0.05F * roar;
        for (int s = 0; s < 2; s++) {
            wing[s].zRot = lift(s, 0.9F * roar);
            wingB[s].zRot = lift(s, 0.3F * roar);
        }
    }
}
