package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.essence.Chants;
import com.lealex.alchyx.client.scene.BodyPoses;
import com.lealex.alchyx.client.scene.SceneWalk;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * A chanting player (essence/Chants), for everyone who sees them: facing the core, arms raised high and a little
 * apart, swaying slowly against each other, the head up, leaning back a touch; the hands tremble as the ritual ends.
 * For the local player it also keeps the camera behind them looking at the core and lets the scene go once the
 * chant is over.
 */
public final class ChantPose {
    private ChantPose() {}

    private static boolean seen;
    private static int waited;

    public static BodyPoses.@Nullable BodyPose pose(Player player, AvatarRenderState state, float partial) {
        BlockPos anchor = Chants.anchor(player);
        if (anchor == null) return null;
        float t = Chants.time(player, partial);
        int length = Math.max(1, Chants.length(player));
        float on = Mth.clamp(Math.min(t / 10F, (length + 6 - t) / 8F), 0F, 1F);
        Vec3 at = player.getPosition(partial);
        float yaw = (float) (Mth.atan2(anchor.getZ() + 0.5 - at.z, anchor.getX() + 0.5 - at.x) * Mth.RAD_TO_DEG) - 90F;
        float sway = Mth.sin(t * 0.18F) * 0.14F;
        float tremble = t > length * 0.75F ? Mth.sin(t * 2.3F) * 0.05F : 0F;
        float[] limbs = new float[BodyPoses.LIMBS];
        limbs[BodyPoses.R_ARM_X] = -(0.2F + 2.4F * on) + (sway + tremble) * on;
        limbs[BodyPoses.L_ARM_X] = -(0.2F + 2.4F * on) + (-sway + tremble) * on;
        limbs[BodyPoses.R_ARM_Z] = 0.35F * on;
        limbs[BodyPoses.L_ARM_Z] = 0.35F * on;
        limbs[BodyPoses.HEAD_X] = -0.45F * on;
        limbs[BodyPoses.HEAD_Y] = sway * 0.4F * on;
        return new BodyPoses.BodyPose(at.x, at.y + BodyPoses.HIP, at.z, yaw, 0.12F * on, sway * 0.15F * on, limbs);
    }

    private static final float SLIDE = 25F;

    /** How far through the chant the local player is, 0..1 (0 before its mark). */
    private static float progress(Player player) {
        int length = Chants.length(player);
        return length <= 0 ? 0F : Mth.clamp(Chants.time(player, 0F) / length, 0F, 1F);
    }

    private static float ease(float x) {
        return x * x * (3 - 2 * x);
    }

    /** The camera comes in a little while chanting (never farther than the usual third-person view). */
    private static final net.minecraft.resources.Identifier SHOT = net.minecraft.resources.Identifier.fromNamespaceAndPath("alchyforbidden", "chant");

    private static float distance(float usual) {
        LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null || Chants.anchor(player) == null) return usual;
        float up = ease(Math.min(1F, Chants.time(player, 0F) / 30F));
        return Math.min(usual, Mth.lerp(up, usual, 3.2F)); // a little nearer than the usual view, never farther
    }

    /** The local player's camera while chanting, and the scene let go when the chant is done. */
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;
        if (!SceneWalk.active(Chants.SCENE) || !SceneWalk.arrived()) {
            com.lealex.alchyx.client.scene.CameraShots.release(SHOT);
            seen = false;
            waited = 0;
            return;
        }
        if (!com.lealex.alchyx.client.scene.CameraShots.active(SHOT)) { // over the walk's shot, given back when the chant ends
            com.lealex.alchyx.client.scene.CameraShots.take(SHOT, net.minecraft.client.CameraType.THIRD_PERSON_BACK, ChantPose::distance);
        }
        BlockPos anchor = SceneWalk.anchor();
        if (anchor != null) { // the camera rises over them and slides slowly round from one side to the other
            float yaw = (float) (Mth.atan2(anchor.getZ() + 0.5 - player.getZ(), anchor.getX() + 0.5 - player.getX()) * Mth.RAD_TO_DEG) - 90F;
            float p = progress(player);
            float up = ease(Math.min(1F, Chants.time(player, 0F) / 30F));
            SceneWalk.aim(yaw - SLIDE + 2 * SLIDE * p, 20F + 22F * up);
        }
        if (Chants.anchor(player) != null) {
            seen = true;
        } else if (seen || ++waited > 100) { // over (or its mark never came)
            seen = false;
            waited = 0;
            com.lealex.alchyx.client.scene.CameraShots.release(SHOT);
            SceneWalk.release();
        }
    }
}
