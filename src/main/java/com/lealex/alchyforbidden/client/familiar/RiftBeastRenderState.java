package com.lealex.alchyforbidden.client.familiar;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/**
 * What every rift creature's model reads: its attack swing (0..1), its grade, whether it is a familiar, and the petting
 * (pet: 0..1, eased in and out; petTime: ticks since it began).
 */
public class RiftBeastRenderState extends LivingEntityRenderState {
    public float swing, pet, petTime;
    /** How far through its attack it is, 0..1 (0 when not attacking): for blows that sweep one way. */
    public float swingTime;
    /** The move playing (-1 none: its index in the creature's moves) and its ticks so far. */
    public int move = -1;
    public float moveTime;
    public int corruption;
    public boolean familiar, echo;
    /** A great one's fight phase (1..3) and ticks since it changed; how far it has folded into its small resting form (0..1). */
    public int phase = 1;
    public float phaseTime = 1000, dormant, dormantScale = 0.3F;
    /** Ticks into its entrance (RiftBeast.INTRO_TICKS long), -1 when none: each great one's model plays its own. */
    public float intro = -1;
    /** The arena special playing (-1 none: its index in the creature's specialIds()) and its ticks so far. */
    public int special = -1;
    public float specialTime;
    /** The creature's own number for what its model shows (see each creature). */
    public int visual;
}
