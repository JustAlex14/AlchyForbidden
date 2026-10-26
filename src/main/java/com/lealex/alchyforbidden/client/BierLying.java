package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.essence.BierRest;
import com.lealex.alchyforbidden.essence.Inscription;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import com.lealex.alchyx.client.scene.BodyPoses;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A player lying down on the Inscribing Bier (essence/BierRest), drawn: the player stays on the floor beside the slab
 * and only the model moves, through keyframes (KEYS): it leans over the slab with both hands on it, pushes up and
 * turns to sit on its edge (hands behind on the stone, legs over the side), then swings its legs up onto the slab
 * while it lies back along it, centred, on its back.
 * <p>
 * While a carving is traced into it or burnt out of it (essence/BierRest.act) the lying body goes through it: traced, it
 * arches and twitches, the carved part most (arms lift, legs draw up, the head jerks back); burnt, it writhes, rolls
 * from side to side and clutches at itself, strongest at the start.
 * <p>
 * Drawn through AlchyX's BodyPoses (registered as a source in AlchyForbiddenClient): each frame {@link #pose} gives
 * the hips' place, the facing, the tilt and roll, and the limbs; AlchyX moves the model and poses the limbs (and worn
 * armour). The knife (client/BierKnifeLayer) reads its own render data set here.
 */
public final class BierLying {
    private BierLying() {}

    private static final float[] NONE = new float[0];
    private static final float HIP = BodyPoses.HIP;

    /** Where the hips are at a key. */
    private enum Spot { STAND, LEAN, SEAT, LIE }

    /** Yaw at a key: toward the slab, away from it (sitting on its edge), toward the feet end (lying). */
    private enum Face { SLAB, OUT, FEET }

    /** A keyframe: time, hips, facing, tilt, arms x / z, legs x / spread, head x, hop (a lift into the next key). */
    private record Key(float t, Spot spot, Face face, float tilt, float armX, float armZ, float legX, float spread, float headX, float hop) {}

    private static final Key[] KEYS = {
            new Key(0, Spot.STAND, Face.SLAB, 0F, 0F, 0.05F, 0F, 0F, 0F, 0F),
            new Key(7, Spot.LEAN, Face.SLAB, -0.35F, -1.05F, 0.15F, 0F, 0F, 0.35F, 0F), // bends over, hands on the stone
            new Key(11, Spot.LEAN, Face.SLAB, -0.4F, -1.0F, 0.15F, 0F, 0F, 0.3F, 0.25F), // a breath, then pushes up
            new Key(21, Spot.SEAT, Face.OUT, 0.15F, 0.55F, 0.2F, -1.45F, 0.12F, 0F, 0F), // sits on the edge, hands behind
            new Key(26, Spot.SEAT, Face.OUT, 0.2F, 0.6F, 0.2F, -1.45F, 0.12F, -0.1F, 0F),
            new Key(40, Spot.LIE, Face.FEET, Mth.HALF_PI, 0F, 0.08F, 0F, 0.03F, 0F, 0F)}; // legs up, lies back
    /** How long the lying down takes. */
    public static final int ANIM = 40;


    /** AlchyX BodyPoses source: the body of a player lying down on a bier, or null. */
    public static BodyPoses.@Nullable BodyPose pose(Player player, AvatarRenderState state, float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean self = player == minecraft.player;
        BlockPos pos = BierRest.lyingOn(player);
        Direction head = BierRest.facing(player);
        Direction side = BierRest.side(player);
        if (self && BierRestClient.lyingFacing() != null) { // the local player's own walk comes before the server's mark
            pos = BierRestClient.lyingBier();
            head = BierRestClient.lyingFacing();
            side = BierRestClient.lyingSide();
        }
        // not inside the bier's own screen, where the body is drawn standing
        if (pos == null || head == null || side == null || (self && minecraft.screen instanceof InscribingScreen)) {
            state.setRenderData(BierKnifeLayer.KNIFE, NONE);
            return null;
        }
        // from when they lay down (once: not again after the screen, nor when they come back into view)
        float t = Math.min(ANIM, self && BierRestClient.lyingFacing() != null ? BierRestClient.lyingTime(partial) : BierRest.lieTime(player, partial));
        int k = 0;
        while (k < KEYS.length - 2 && t >= KEYS[k + 1].t()) k++;
        Key a = KEYS[k], b = KEYS[k + 1];
        float raw = Mth.clamp((t - a.t()) / (b.t() - a.t()), 0F, 1F), e = raw * raw * (3F - 2F * raw);

        double[] pa = spot(a.spot(), pos, head, side, state), pb = spot(b.spot(), pos, head, side, state);
        float yawA = yaw(a.face(), head, side), yawB = yaw(b.face(), head, side);
        float yaw = Mth.lerp(e, yawA, yawB); // unwrapped: always the same way round
        float lift = a.hop() * Mth.sin(raw * Mth.PI);
        float armX = Mth.lerp(e, a.armX(), b.armX()), armZ = Mth.lerp(e, a.armZ(), b.armZ()), legX = Mth.lerp(e, a.legX(), b.legX());
        float[] limbs = {armX, armZ, armX, armZ, legX, legX, Mth.lerp(e, a.spread(), b.spread()), Mth.lerp(e, a.headX(), b.headX()), 0F};
        // body: the hips (world), tilt, (unused), roll
        double[] body = {Mth.lerp(e, pa[0], pb[0]), Mth.lerp(e, pa[1], pb[1]) + lift, Mth.lerp(e, pa[2], pb[2]), Mth.lerp(e, a.tilt(), b.tilt()), 0, 0, 0};
        BierRest.Act act = BierRest.act(player);
        float actT = BierRest.actTime(player, partial);
        // which way it lies: the body's right side toward the camera (where they stand) or away. Acts are made for
        // the right side toward it and mirrored otherwise, so the carved arm or leg, or the bared back, face the camera
        boolean mirror = (head.getStepZ() * side.getStepX() - head.getStepX() * side.getStepZ()) < 0; // right = up x head
        if (act != BierRest.Act.NONE) {
            acting(act, BierRest.actPart(player), actT, body, limbs);
            if (mirror) {
                swap(limbs, 0, 2);
                swap(limbs, 1, 3);
                swap(limbs, 4, 5);
                limbs[8] = -limbs[8];
                body[6] = -body[6];
            }
        }
        state.setRenderData(BierKnifeLayer.KNIFE, act == BierRest.Act.CARVE
                ? new float[] {BierRest.actPart(player).ordinal(), actT, player.getId(), mirror ? 1F : 0F} : NONE); // the knife at work
        return new BodyPoses.BodyPose(body[0], body[1], body[2], yaw, (float) body[3], (float) body[6], limbs);
    }

    private static void swap(float[] a, int i, int j) {
        float x = a[i];
        a[i] = a[j];
        a[j] = x;
    }

    /** Rises over the first `in` of 0..1, holds, falls over the last `out`. */
    private static float plateau(float p, float in, float out) {
        return smooth(p / in) * smooth((1F - p) / out);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    /**
     * The lying body going through a carving (traced, the knife at work: client/BierKnifeLayer) or a burning, each
     * body part its own way; on top of the lying pose. limbs: rArmX, rArmZ, lArmX, lArmZ, rLegX, lLegX, spread,
     * headX, headY. body[1] lifts the hips, body[6] rolls about the long axis.
     */
    private static void acting(BierRest.Act act, Inscription.Part part, float t, double[] body, float[] limbs) {
        float twitch = Mth.sin(t * 1.7F) * Mth.sin(t * 0.63F); // uneven, never quite repeating
        if (act == BierRest.Act.EXPAND) { // rising off the stone, opening wide, the head thrown back as the body takes more
            float env = plateau(Mth.clamp(t / BierRest.EXPAND_ACT, 0F, 1F), 0.3F, 0.2F);
            float breath = Mth.sin(t * 0.25F);
            body[1] += env * (0.45F + 0.04F * breath);
            body[3] += env * 0.15F; // arched
            limbs[0] -= env * 0.3F;
            limbs[2] -= env * 0.3F;
            limbs[1] += env * (1.3F + 0.1F * breath);
            limbs[3] += env * (1.3F + 0.1F * breath);
            limbs[6] += env * 0.25F;
            limbs[7] -= env * 0.6F;
            if (t > BierRest.EXPAND_ACT * 0.75F) body[6] += env * 0.08F * Mth.sin(t * 2.1F); // the last shudder
            return;
        }
        if (act == BierRest.Act.CARVE) {
            float env = plateau(Mth.clamp(t / BierRest.CARVE_ACT, 0F, 1F), 0.15F, 0.2F);
            float flinch = env * Math.max(0F, Mth.sin(t * 0.785F)) * 0.6F; // with each stroke of the knife (8 ticks)
            switch (part) {
                case HEAD -> { // chin up for the blade, hands gripping the stone's edges
                    limbs[7] -= env * 0.35F + flinch * 0.2F;
                    limbs[8] = env * 0.12F * twitch;
                    limbs[1] += env * 0.2F;
                    limbs[3] += env * 0.2F;
                    body[6] = env * 0.03F * twitch;
                }
                case CHEST -> { // arched, arms flung wide
                    body[1] += env * (0.1F + 0.04F * flinch);
                    limbs[1] += env * 1.15F;
                    limbs[3] += env * 1.15F;
                    limbs[0] -= env * 0.25F;
                    limbs[2] -= env * 0.25F;
                    limbs[7] -= env * 0.45F + flinch * 0.2F;
                }
                case BACK -> { // rolled onto the side, the back bared, curled
                    body[6] = env * (1.25F + 0.06F * flinch);
                    limbs[0] -= env * 0.9F;
                    limbs[2] -= env * 0.9F;
                    limbs[4] -= env * 0.8F;
                    limbs[5] -= env * 0.95F;
                    limbs[7] += env * 0.35F;
                }
                case ARMS -> { // the right arm stretched out on the stone, the left gripping, the head turned to watch
                    limbs[1] += env * 1.4F;
                    limbs[0] -= env * 0.15F;
                    limbs[1] += flinch * 0.15F;
                    limbs[3] += env * 0.15F;
                    limbs[8] = -env * 0.6F;
                    body[6] = env * 0.04F * twitch;
                }
                case LEGS -> { // the right knee drawn up, the thigh offered
                    limbs[4] -= env * (0.85F + flinch * 0.2F);
                    limbs[6] += env * 0.1F;
                    limbs[7] += env * 0.25F; // looking down at it
                    limbs[1] += env * 0.15F;
                    limbs[3] += env * 0.15F;
                }
                case FEET -> { // legs a little raised, the feet curling with each cut
                    limbs[4] -= env * 0.25F + flinch * 0.3F;
                    limbs[5] -= env * 0.25F + flinch * 0.15F;
                    limbs[7] += env * 0.3F;
                }
                case SKIN -> { // the whole body: arched, arms half out, shuddering as the blade runs its length
                    body[1] += env * 0.06F;
                    body[6] = env * 0.08F * Mth.sin(t * 1.3F);
                    limbs[1] += env * 0.6F;
                    limbs[3] += env * 0.6F;
                    limbs[7] -= env * 0.3F;
                    limbs[6] += env * 0.12F * twitch;
                }
            }
            return;
        }
        // burnt out: strongest at once, dying down
        float p = Mth.clamp(t / BierRest.BURN_ACT, 0F, 1F);
        float env = (1F - p) * Math.min(1F, t / 3F);
        float writhe = Mth.sin(t * 0.75F);
        switch (part) {
            case HEAD -> { // the head thrashing, the hands up over it
                limbs[8] = env * 0.8F * Mth.sin(t * 0.9F);
                limbs[7] -= env * 0.3F;
                limbs[0] -= env * 2.5F;
                limbs[2] -= env * 2.5F;
                limbs[1] += env * 0.3F;
                limbs[3] += env * 0.3F;
                body[6] = env * 0.15F * writhe;
            }
            case CHEST -> { // clutching the chest, arching, rolling
                limbs[0] -= env * 1.4F;
                limbs[2] -= env * 1.4F;
                limbs[1] -= env * 0.6F;
                limbs[3] -= env * 0.6F;
                body[1] += env * 0.1F * Math.abs(writhe);
                body[6] = env * 0.25F * writhe;
                limbs[7] -= env * 0.4F;
            }
            case BACK -> { // rolling from side to side to put it out
                body[6] = env * 0.7F * Mth.sin(t * 0.6F);
                body[1] += env * 0.06F;
                limbs[4] -= env * 0.5F * Math.abs(writhe);
                limbs[5] -= env * 0.5F * Math.abs(Mth.cos(t * 0.75F));
                limbs[7] -= env * 0.3F;
            }
            case ARMS -> { // the right arm flailing, the left grabbing at it
                limbs[1] += env * (1.1F + 0.6F * Mth.sin(t * 1.5F));
                limbs[0] += env * 0.8F * Mth.sin(t * 1.1F);
                limbs[2] -= env * 1.2F;
                limbs[3] -= env * 0.8F;
                limbs[8] = -env * 0.5F;
                body[6] = env * 0.15F * writhe;
            }
            case LEGS -> { // kicking, one leg then the other
                limbs[4] -= env * 0.95F * Math.abs(Mth.sin(t * 0.6F));
                limbs[5] -= env * 0.95F * Math.abs(Mth.cos(t * 0.6F));
                limbs[6] += env * 0.15F * writhe;
                limbs[7] += env * 0.3F;
            }
            case FEET -> { // the feet pedalling fast
                limbs[4] -= env * 0.45F * Math.abs(Mth.sin(t * 1.3F));
                limbs[5] -= env * 0.45F * Math.abs(Mth.cos(t * 1.3F));
                limbs[7] += env * 0.25F;
            }
            case SKIN -> { // all of it: rolling, clawing, kicking
                body[6] = env * 0.4F * writhe;
                body[1] += env * 0.06F * Math.abs(Mth.sin(t * 1.4F));
                limbs[0] -= env * 1.1F;
                limbs[2] -= env * 0.7F;
                limbs[1] += env * 0.6F * Mth.sin(t * 1.9F);
                limbs[3] -= env * 0.4F;
                limbs[4] -= env * 0.5F * Math.abs(Mth.sin(t * 0.55F));
                limbs[5] -= env * 0.5F * Math.abs(Mth.cos(t * 0.55F));
                limbs[7] -= env * 0.35F;
            }
        }
    }

    /** The hips' place at a key (world). */
    private static double[] spot(Spot spot, BlockPos pos, Direction head, Direction side, AvatarRenderState state) {
        double cx = pos.getX() + 0.5, cz = pos.getZ() + 0.5, floor = pos.getY(), top = pos.getY() + 1;
        return switch (spot) {
            case STAND -> new double[] {state.x, state.y + HIP, state.z};
            case LEAN -> new double[] {cx + side.getStepX() * 0.95, floor + HIP, cz + side.getStepZ() * 0.95};
            case SEAT -> new double[] {cx + side.getStepX() * 0.25, top + 0.12, cz + side.getStepZ() * 0.25};
            // lying: feet half a body (1.875) short of the centre, the hips 0.703 up from them; the back on the stone
            case LIE -> new double[] {cx - head.getStepX() * (0.94 - HIP), top + 0.12, cz - head.getStepZ() * (0.94 - HIP)};
        };
    }

    /**
     * The yaw at a key, unwrapped so the body always turns the same way round: from facing the slab, through facing
     * the head end, to facing out (sitting), then on to facing the feet end (lying: tipping back from there lays the
     * head toward `head`). Turning half round by the shortest way was a coin toss (180 either way), and the model
     * sometimes spun the other way.
     */
    private static float yaw(Face face, Direction head, Direction side) {
        float slab = side.getOpposite().toYRot();
        float turn = Mth.wrapDegrees(head.toYRot() - slab) > 0 ? 1F : -1F; // the head end's way from facing the slab
        return switch (face) {
            case SLAB -> slab;
            case OUT -> slab + 180F * turn;
            case FEET -> slab + 270F * turn;
        };
    }
}
