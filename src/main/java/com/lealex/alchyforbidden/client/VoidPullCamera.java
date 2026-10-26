package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.network.VoidPullPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.lealex.alchyx.client.scene.CameraShots;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * The void's pull seen from outside: the view goes to third person, pulled well back, and the player turns to look up
 * at the rift drawing them in, so the camera behind them sees them lifted, dragged and swallowed. Through the rift, the
 * view stays outside while they grow back in the arena, then goes back to what it was. Counted in its own ticks (the
 * world changes on the way).
 */
public final class VoidPullCamera {
    private VoidPullCamera() {}

    private static Vec3 rift = Vec3.ZERO, grip = Vec3.ZERO;
    private static int ticks, pull, total = -1, grab = -1, letGo = -1;
    private static final Identifier SHOT = Identifier.fromNamespaceAndPath("alchyforbidden", "void_pull");

    public static void start(VoidPullPayload payload) {
        rift = new Vec3(payload.x(), payload.y(), payload.z());
        pull = payload.pull();
        total = payload.total();
        grip = new Vec3(payload.gx(), payload.gy(), payload.gz());
        grab = payload.grab();
        letGo = payload.letGo();
        ticks = 0;
        CameraShots.take(SHOT, CameraType.THIRD_PERSON_BACK, VoidPullCamera::distance); // held, and given back after, by AlchyX
    }

    public static boolean active() {
        return total >= 0;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (total < 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || ++ticks > total) {
            CameraShots.release(SHOT);
            total = -1;
        }
    }

    /** Every frame while pulled: the player (and so the camera behind them) turns to the rift. */
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        if (total < 0 || ticks > pull) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean clinging = grab >= 0 && ticks < letGo;
        Vec3 to = (clinging ? grip : rift).subtract(player.getEyePosition()); // clinging it looks at the pillar, then at the rift
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90F;
        float pitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        pitch = Mth.clamp(pitch, -55F, 20F); // up at it, the camera still seeing them
        float k = 0.12F;
        player.setYRot(player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot()) * k);
        player.setXRot(player.getXRot() + (pitch - player.getXRot()) * k);
        player.yRotO = player.getYRot();
        player.xRotO = player.getXRot();
    }

    /**
     * The body (AlchyX BodyPoses, the local player): clinging, arms reaching out to the pillar and trembling, the body
     * dragged back toward the rift and the legs kicking; torn away, arms still reaching for it as it spins into the rift.
     */
    public static com.lealex.alchyx.client.scene.BodyPoses.@org.jspecify.annotations.Nullable BodyPose pose(net.minecraft.world.entity.player.Player player,
            net.minecraft.client.renderer.entity.state.AvatarRenderState state, float partial) {
        if (total < 0 || ticks > pull || player != Minecraft.getInstance().player) return null;
        float t = ticks + partial;
        Vec3 at = player.getPosition(partial);
        float[] limbs = new float[com.lealex.alchyx.client.scene.BodyPoses.LIMBS];
        boolean clinging = grab >= 0 && t < letGo;
        Vec3 look = clinging ? grip : rift;
        float yaw = (float) (Mth.atan2(look.z - at.z, look.x - at.x) * Mth.RAD_TO_DEG) - 90F;
        float on = Mth.clamp(t / 8F, 0, 1), tremble = Mth.sin(t * 2.7F) * 0.08F, kick = Mth.sin(t * 0.9F);
        float tilt, roll;
        if (clinging) {
            float strain = grab >= 0 && t >= grab ? Mth.clamp((t - grab) / 6F, 0, 1) : 0;
            limbs[com.lealex.alchyx.client.scene.BodyPoses.R_ARM_X] = -1.75F * on + tremble;
            limbs[com.lealex.alchyx.client.scene.BodyPoses.L_ARM_X] = -1.65F * on - tremble;
            limbs[com.lealex.alchyx.client.scene.BodyPoses.R_ARM_Z] = -0.15F;
            limbs[com.lealex.alchyx.client.scene.BodyPoses.L_ARM_Z] = 0.15F;
            limbs[com.lealex.alchyx.client.scene.BodyPoses.R_LEG_X] = 0.5F * kick * strain;
            limbs[com.lealex.alchyx.client.scene.BodyPoses.L_LEG_X] = -0.5F * kick * strain;
            limbs[com.lealex.alchyx.client.scene.BodyPoses.HEAD_X] = -0.3F;
            tilt = (0.25F + 0.75F * strain + 0.1F * Mth.sin(t * 1.3F)) * on; // the rift dragging the body back off the pillar
            roll = 0.1F * kick * strain;
        } else { // torn away: reaching back for the pillar, spinning into the rift
            float gone = letGo >= 0 ? Mth.clamp((t - letGo) / 20F, 0, 1) : Mth.clamp(t / 30F, 0, 1);
            limbs[com.lealex.alchyx.client.scene.BodyPoses.R_ARM_X] = -2.2F + tremble;
            limbs[com.lealex.alchyx.client.scene.BodyPoses.L_ARM_X] = -2.0F - tremble;
            limbs[com.lealex.alchyx.client.scene.BodyPoses.R_ARM_Z] = 0.4F;
            limbs[com.lealex.alchyx.client.scene.BodyPoses.L_ARM_Z] = -0.4F;
            limbs[com.lealex.alchyx.client.scene.BodyPoses.R_LEG_X] = 0.7F * kick;
            limbs[com.lealex.alchyx.client.scene.BodyPoses.L_LEG_X] = -0.7F * kick;
            limbs[com.lealex.alchyx.client.scene.BodyPoses.HEAD_X] = -0.5F;
            yaw += 180F + gone * gone * 540F; // its back to the rift, then turning over and over
            tilt = -0.6F - 0.6F * gone;
            roll = gone * 0.5F * Mth.sin(t * 0.5F);
        }
        return new com.lealex.alchyx.client.scene.BodyPoses.BodyPose(at.x, at.y + com.lealex.alchyx.client.scene.BodyPoses.HIP, at.z, yaw, tilt, roll, limbs);
    }

    /** Pulled back so the rift and the player both fit. */
    private static float distance(float usual) {
        float out = Mth.clamp(ticks / 15F, 0, 1) * Mth.clamp((total - ticks) / 10F, 0, 1);
        return Mth.lerp(out, usual, 7.5F);
    }
}
