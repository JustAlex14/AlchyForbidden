package com.lealex.alchyforbidden.client.familiar;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.client.familiar.mesh.WeepingChandelierMesh;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The Weeping Chandelier (mesh and texture from tools/models.py: an iron hub with four small skulls under a spiked gold
 * crown, a gem drop, two rings (the outer one hung with crystal drops), six curling arms each ending in a cup, a candle
 * and a soul flame, a chain climbing into the dark, three drowned guests circling), drawn 2.0 times larger.
 * Phase I it hangs on its chain, swaying. Phase II the chain has snapped (a broken end left), the hub is split with
 * light, bent iron hangs off its ring, two candles are out: it lies tilted and drags itself on its arms like legs.
 * Phase III it hangs upside down, spinning, every candle roaring into a tall flame. Resting as a familiar, a small
 * cluster of candles turning at its owner's shoulder. Petted, its candles flare and its arms fold in.
 */
public class WeepingChandelierModel extends EntityModel<RiftBeastRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "weeping_chandelier"), "main");
    private final ModelPart frame, cracks, ring, ring2, chain, chainBroken, debris;
    private final ModelPart[] arms = new ModelPart[6], tips = new ModelPart[6], flames = new ModelPart[6], blazes = new ModelPart[6], ghosts = new ModelPart[3];

    public WeepingChandelierModel(ModelPart root) {
        super(root);
        frame = root.getChild("frame");
        cracks = frame.getChild("cracks");
        ring = frame.getChild("ring");
        ring2 = frame.getChild("ring2");
        chain = frame.getChild("chain");
        chainBroken = frame.getChild("chain_broken");
        debris = ring2.getChild("debris");
        for (int i = 0; i < 6; i++) {
            arms[i] = frame.getChild("arm" + i);
            tips[i] = arms[i].getChild("arm" + i + "_tip");
            flames[i] = tips[i].getChild("flame" + i);
            blazes[i] = tips[i].getChild("blaze" + i);
        }
        for (int i = 0; i < 3; i++) ghosts[i] = frame.getChild("ghost" + i);
    }

    public static LayerDefinition createBodyLayer() {
        return WeepingChandelierMesh.create();
    }

    @Override
    public void setupAnim(RiftBeastRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks, pet = state.pet;
        int phase = state.familiar ? 1 : state.phase;
        float shift = Mth.clamp(state.phaseTime / 40F, 0, 1), ease = shift * shift * (3 - 2 * shift);
        boolean rest = state.dormant > 0.5F;
        chain.visible = phase == 1 && !rest;
        chainBroken.visible = phase == 2 && !rest;
        cracks.visible = phase >= 2 && !rest;
        debris.visible = phase == 2;
        ring.visible = ring2.visible = !rest;
        for (int i = 0; i < 6; i++) {
            flames[i].visible = phase < 3 && !(phase == 2 && (i == 1 || i == 4)); // two snuffed by the fall
            blazes[i].visible = phase == 3 && !rest;
            float flick = 1 + 0.15F * Mth.sin(age * 0.6F + i * 1.7F);
            flames[i].yScale = flick * (1 + 0.6F * pet);
            blazes[i].yScale = 1 + 0.3F * Mth.sin(age * 0.45F + i);
            blazes[i].xScale = blazes[i].zScale = 1 + 0.12F * Mth.sin(age * 0.7F + i * 2);
        }
        frame.yRot = age * 0.01F + state.yRot * Mth.DEG_TO_RAD * 0.3F;
        if (phase == 1) { // hanging: a slow sway on its chain
            frame.xRot = Mth.sin(age * 0.05F) * 0.06F;
            frame.zRot = Mth.cos(age * 0.043F) * 0.05F;
            for (int i = 0; i < 6; i++) {
                arms[i].zRot = Mth.sin(age * 0.07F + i) * 0.04F;
                tips[i].xRot += Mth.sin(age * 0.1F + i * 1.1F) * 0.08F;
            }
            ring2.zRot = Mth.sin(age * 0.06F + 1) * 0.04F;
        } else if (phase == 2) { // fallen: a spider of iron, its six arms as legs, the hub carried low between them
            boolean moving = (state.visual & 2) != 0;
            float gait = age * (moving ? 0.7F : 0.15F), amp = moving ? 1F : 0.25F;
            frame.yRot = 0; // facing where it scuttles (the body's own turn)
            frame.y += 7F * ease + Math.abs(Mth.sin(gait)) * 0.8F * amp; // low, bobbing with each step
            frame.xRot = 0.05F * ease;
            frame.zRot = Mth.sin(gait) * 0.05F * amp;
            for (int i = 0; i < 6; i++) { // two tripods in turn (0, 2, 4 and 1, 3, 5): a leg lifts and reaches forward, the other three push
                float ph = gait + (i % 2 == 0 ? 0 : Mth.PI);
                float lift = Math.max(0, Mth.sin(ph)) * amp;
                arms[i].xRot = (1.15F - 0.55F * lift) * ease;             // planted down and out, lifted while it swings
                arms[i].zRot = Mth.cos(ph) * 0.18F * amp;                  // the stride
                tips[i].xRot = (-1.15F + 0.35F * lift) * ease;             // the candle kept upright on its foot
            }
            ring2.zRot = 0.12F * ease + Mth.sin(gait * 0.5F) * 0.03F;      // the outer ring knocked askew, rattling
            ring2.xRot = -0.08F * ease;
        } else { // the last dance: upside down, spinning, every candle roaring
            frame.zRot = Mth.PI * ease;
            frame.yRot = age * 0.08F;
            frame.y -= 2F * ease;
            for (int i = 0; i < 6; i++) {
                arms[i].xRot = Mth.sin(age * 0.15F + i * 1.2F) * 0.25F;
                tips[i].xRot += Mth.sin(age * 0.3F + i) * 0.15F;
            }
        }
        for (int i = 0; i < 3; i++) { // the guests circling, bobbing (waltzing wider in the last dance)
            ghosts[i].visible = !rest;
            ghosts[i].yRot = age * (phase == 3 ? 0.12F : 0.05F) + i * Mth.TWO_PI / 3;
            ghosts[i].y = Mth.sin(age * 0.1F + i * 2) * 1.5F;
            ghosts[i].xScale = ghosts[i].zScale = phase == 3 ? 1.25F : 1F;
        }
        if ((state.visual & 1) != 0 && !rest) { // guttering: flames shrunk to embers, arms sagging, the whole of it slumped
            for (int i = 0; i < 6; i++) {
                flames[i].yScale *= 0.35F;
                blazes[i].yScale *= 0.35F;
                arms[i].xRot += 0.45F;
                tips[i].xRot -= 0.3F;
            }
            frame.xRot += 0.12F;
            frame.zRot += Mth.sin(age * 0.05F) * 0.05F;
        }
        float sw = state.swing; // a ranged cast: the candles lean toward the prey
        for (ModelPart tip : tips) tip.xRot += 0.3F * sw;
        if (rest) { // resting: a turning cluster of candles round the hub
            frame.yRot = age * 0.08F;
            frame.zRot = frame.xRot = 0;
            for (int i = 0; i < 6; i++) {
                arms[i].xRot = -0.9F;
                tips[i].xRot = 0.9F;
            }
        }
        if (pet > 0) {
            for (int i = 0; i < 6; i++) arms[i].xRot = Mth.lerp(pet, arms[i].xRot, -0.4F);
            frame.y += 1.5F * pet;
        }
        if (state.move >= 0) move(state.move, state.moveTime);
        if (state.special >= 0) special(state.special, state.specialTime, phase);
        if (state.intro >= 0) intro(state.intro);
    }

    /** The moves (wax rain, soul flames, chain lash, free the guests). */
    private void move(int move, float t) {
        switch (move) {
            case 0 -> { // wax rain: the candles tipped over, pouring
                float tip = Mth.clamp(t / 10F, 0, 1);
                for (int i = 0; i < 6; i++) tips[i].xRot += 1.1F * tip + Mth.sin(t * 0.5F + i) * 0.1F;
            }
            case 1 -> { // soul flames: the arms flick up one after another as each flame leaves
                for (int i = 0; i < 6; i++) {
                    float k = Mth.clamp((t - 10 - i * 3) / 3F, 0, 1), back = Mth.clamp((t - 13 - i * 3) / 6F, 0, 1);
                    arms[i].xRot -= 0.8F * (k - back);
                    flames[i].yScale *= 1 + 1.5F * (k - back);
                }
            }
            case 2 -> { // chain drop: arms drawn up as it climbs, trembling, then flung out as it falls and lands
                float up = Mth.clamp(t / 10F, 0, 1), fall = Mth.clamp((t - 14) / 3F, 0, 1), after = Mth.clamp((t - 17) / 7F, 0, 1);
                for (int i = 0; i < 6; i++) {
                    arms[i].xRot += -0.6F * up * (1 - fall) + 0.9F * fall * (1 - after) + (t > 10 && t < 14 ? Mth.sin(t * 3F + i) * 0.06F : 0);
                }
                ring2.zRot += 0.3F * fall * Mth.sin(t * 2F) * (1 - after);
                frame.zRot += fall * (1 - after) * Mth.sin(t * 2.6F) * 0.12F;
            }
            case 3 -> { // free the guests: the ghosts thrown out from it
                float out = Mth.clamp((t - 6) / 8F, 0, 1);
                for (ModelPart g : ghosts) {
                    g.xScale = g.zScale = 1 + out * 0.6F;
                    g.y -= 4 * out;
                }
                frame.y -= 2 * Mth.sin(out * Mth.PI);
            }
            default -> {}
        }
    }

    /** The arena specials (pendulum, candle ring, blackout, toll). */
    private void special(int special, float t, int phase) {
        switch (special) {
            case 0 -> { // the pendulum: drawn back, then swung through its prey, tilting with the arc, and swinging to a stop
                float back = Mth.clamp(t / 20F, 0, 1), swing = Mth.clamp((t - 20) / 30F, 0, 1), stop = Mth.clamp((t - 50) / 10F, 0, 1);
                float tilt = t < 20 ? -0.7F * back : -0.7F + 1.4F * (1 - Mth.cos(swing * Mth.PI)) / 2;
                frame.xRot += tilt * (1 - stop) + Mth.sin(t * 0.6F) * 0.15F * stop * (1 - stop) * 4;
                chain.xRot = -frame.xRot * 0.3F;
                for (int i = 0; i < 6; i++) tips[i].xRot += -tilt * 0.4F; // the candles trailing
            }
            case 1 -> { // the candle ring: arms raised, trembling, flames swelling as each candle lights
                float rise = Mth.clamp(t / 20F, 0, 1);
                for (int i = 0; i < 6; i++) {
                    arms[i].xRot = Mth.lerp(rise, arms[i].xRot, -0.7F) + Mth.sin(t * 1.3F + i) * 0.05F;
                    flames[i].yScale *= 1 + t / 60F;
                }
            }
            case 2 -> { // blackout: every flame gone, the arms clenched
                for (int i = 0; i < 6; i++) {
                    flames[i].visible = false;
                    arms[i].xRot = Mth.lerp(Mth.clamp(t / 6F, 0, 1), arms[i].xRot, 0.6F);
                }
            }
            case 3 -> { // the toll: up on its arms (pushing down), then slammed and ringing like a bell
                float rear = Mth.clamp(t / 14F, 0, 1), slam = Mth.clamp((t - 18) / 3F, 0, 1), k = Math.max(0, 1 - Math.max(0, t - 21) / 11F);
                for (int i = 0; i < 6; i++) arms[i].xRot += (0.6F * rear) * (1 - slam) - 0.5F * slam * k;
                frame.xRot -= 0.25F * rear * (1 - slam);
                frame.zRot += Mth.sin(t * 0.9F) * 0.35F * k * slam;
                ring2.xRot += Mth.sin(t * 1.7F) * 0.2F * k * slam;
                ring.zRot += Mth.sin(t * 2.1F) * 0.15F * k * slam;
            }
            default -> {}
        }
    }

    /** Its entrance: dark as it comes down turning, the candles catching one by one, then its guests. */
    private void intro(float t) {
        chain.visible = true;
        frame.yRot += (1 - Mth.clamp(t / 56F, 0, 1)) * 2.5F;
        for (int i = 0; i < 6; i++) {
            float lit = Mth.clamp((t - 58 - i * 4) / 3F, 0, 1), after = Mth.clamp((t - 61 - i * 4) / 8F, 0, 1);
            flames[i].visible = lit > 0;
            float pop = lit * (1 + 0.7F * (1 - after));
            flames[i].xScale = flames[i].zScale = pop;
            flames[i].yScale = pop * 1.3F;
            blazes[i].visible = false;
        }
        float g = Mth.clamp((t - 84) / 8F, 0, 1);
        for (ModelPart gh : ghosts) {
            gh.visible = g > 0;
            gh.xScale = gh.yScale = gh.zScale = g;
        }
    }
}
