package com.lealex.alchyforbidden.client.familiar;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.client.familiar.mesh.TheChoirMesh;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The Choir: a heart of light in a ring of five sculk heads turned outward, each with its jaw and two tendrils hanging
 * under it, joined by links of a crown; the ring turns slowly, each head bobbing on its own and singing (its jaw opening).
 * Mesh and 128 x 128 texture from tools/models.py, drawn 1.4 times larger. Petted, the ring tips toward the hand and every head
 * turns to it, humming.
 */
public class TheChoirModel extends EntityModel<RiftBeastRenderState> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "the_choir"), "main");
    private final ModelPart crown, core;
    private final ModelPart[] heads = new ModelPart[5], jaws = new ModelPart[5];

    public TheChoirModel(ModelPart root) {
        super(root);
        crown = root.getChild("crown");
        core = crown.getChild("core");
        for (int i = 0; i < 5; i++) {
            heads[i] = crown.getChild("head" + i);
            jaws[i] = heads[i].getChild("jaw");
        }
    }

    public static LayerDefinition createBodyLayer() {
        return TheChoirMesh.create();
    }

    @Override
    public void setupAnim(RiftBeastRenderState state) {
        super.setupAnim(state);
        float age = state.ageInTicks, pet = state.pet;
        crown.yRot = age * 0.02F;
        crown.y += Mth.sin(age * 0.08F) * 1.2F;
        core.yRot = -age * 0.08F;
        core.xRot = age * 0.05F;
        for (int i = 0; i < 5; i++) {
            heads[i].y = Mth.sin(age * 0.12F + i * 1.3F) * 0.8F;
            heads[i].xRot = Mth.sin(age * 0.05F + i) * 0.1F;
            jaws[i].xRot = (Mth.sin(age * 0.2F + i * 0.9F) + 1) * 0.15F + state.swing * 0.8F;
        }
        if (pet > 0) { // tipped toward the hand, every head turned in, humming
            crown.xRot = -0.35F * pet;
            for (int i = 0; i < 5; i++) {
                heads[i].yRot = Mth.lerp(pet, heads[i].yRot, heads[i].yRot) + Mth.sin(state.petTime * 0.2F + i) * 0.2F * pet;
                jaws[i].xRot = Mth.lerp(pet, jaws[i].xRot, 0.1F + (Mth.sin(state.petTime * 0.6F + i) + 1) * 0.05F);
            }
        }
        if (state.move >= 0) move(state.move, state.moveTime);
        if (state.intro >= 0) intro(state.intro);
    }

    /** The moves (sonic boom, wail of sorrow, canticle, crescendo). */
    private void move(int move, float t) {
        switch (move) {
            case 0 -> { // boom: the ring stills, every jaw opening wide together
                float open = t < 20 ? t / 20F : Mth.clamp(1 - (t - 20) / 6F, 0, 1);
                for (ModelPart jaw : jaws) jaw.xRot += 0.9F * open;
                core.xRot = 0;
            }
            case 1 -> { // wail: the ring tipped forward, wailing
                float tip = Mth.clamp(Math.min(t / 6F, (24 - t) / 6F), 0, 1);
                crown.xRot += 0.5F * tip;
                for (ModelPart jaw : jaws) jaw.xRot += 0.7F * tip;
            }
            case 2 -> { // canticle: each head in turn snapping its jaw open
                int head = Mth.clamp((int) ((t - 6) / 4), 0, 4);
                if (t >= 6 && t < 26) jaws[head].xRot += 1.0F;
                crown.yRot += t * 0.05F;
            }
            case 3 -> { // crescendo: the ring rising and spinning faster and faster, all jaws open, then the burst
                float rise = Mth.clamp(t / 40F, 0, 1);
                crown.y -= 4F * rise;
                crown.yRot += t * t * 0.002F;
                for (ModelPart jaw : jaws) jaw.xRot += 1.0F * rise;
            }
            default -> {}
        }
    }

    /** Its entrance: the heads closed small round the light as it comes down, turning; they open and sing. */
    private void intro(float t) {
        float open = Mth.clamp((t - 50) / 12F, 0, 1);
        crown.yRot += (1 - open) * t * 0.08F;
        for (int i = 0; i < 5; i++) {
            float s = 0.25F + 0.75F * open;
            heads[i].xScale = heads[i].yScale = heads[i].zScale = s;
            if (t > 62) jaws[i].xRot += 0.9F + Mth.sin(t * 0.6F + i) * 0.15F;
        }
    }
}
