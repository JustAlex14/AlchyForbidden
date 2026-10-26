package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.essence.BierRest;
import com.lealex.alchyx.client.scene.SceneWalk;
import com.lealex.alchyx.client.scene.ScreenFade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The lying on the Inscribing Bier, on the player's own side. The walk there is AlchyX's (SceneWalk: path, camera,
 * stride, the blink when there is no way); this is what follows once there: the model lies down (client/BierLying,
 * timed from {@link #lyingTime}), the camera looks across the slab at them, a moment still, the screen fades dark
 * (ScreenFade) and the server is told to open the screen (network/BierReadyPayload). A carving or a burning closes the
 * screen to be watched, then it comes back. When the screen closes for good the scene lets go (SceneWalk.release:
 * keys and camera back) and the dark lifts.
 */
public final class BierRestClient {
    private BierRestClient() {}

    /** After lying down: the animation, a moment still, the fade; then the server is told to open the screen. */
    private static final int HOLD = 8, FADE_IN = 12, READY = BierLying.ANIM + HOLD + FADE_IN, WAIT_LIMIT = READY + 80;

    private static @Nullable BlockPos bier;
    private static Direction facing = Direction.NORTH, side = Direction.NORTH;
    private static boolean screenSeen, readySent;
    private static int lain, closed;

    /** SceneWalk's arrival at the bier (registered in AlchyForbiddenClient): lie down. */
    public static void arrived() {
        List<Integer> extra = SceneWalk.extra();
        if (extra.size() < 2) return;
        bier = SceneWalk.anchor();
        facing = Direction.from3DDataValue(Math.floorMod(extra.get(0), 6));
        side = Direction.from3DDataValue(Math.floorMod(extra.get(1), 6));
        screenSeen = readySent = false;
        lain = closed = 0;
    }

    /** The local player's head direction while they lie down (before the server's mark arrives), or null. */
    public static @Nullable Direction lyingFacing() {
        return lying() ? facing : null;
    }

    /** The bier the local player is lying down on, or null. */
    public static @Nullable BlockPos lyingBier() {
        return lying() ? bier : null;
    }

    /** The long side of the slab the local player stands on while lying down, or null. */
    public static @Nullable Direction lyingSide() {
        return lying() ? side : null;
    }

    /** Ticks since the local player lay down (their own count: the animation starts the moment they do). */
    public static float lyingTime(float partial) {
        return lain + partial;
    }

    private static boolean lying() {
        return bier != null && SceneWalk.active(BierRest.SCENE) && SceneWalk.arrived();
    }

    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) return;
        if (!lying()) {
            bier = null;
            return;
        }
        SceneWalk.aim(side.getOpposite().toYRot(), 35F); // the camera behind them: beside the slab, looking across it
        lain++;
        if (lain >= READY && !readySent) { // lain down, held, faded: the screen may open
            readySent = true;
            ClientPacketDistributor.sendToServer(new com.lealex.alchyforbidden.network.BierReadyPayload());
        }
        if (BierRest.act(player) != BierRest.Act.NONE) { // a carving or a burning: watch it, the screen comes back after
            screenSeen = false;
            closed = 0;
            lain = Math.min(lain, BierLying.ANIM + HOLD); // no dark while it plays
        } else if (Minecraft.getInstance().screen instanceof InscribingScreen) {
            screenSeen = true;
            closed = 0;
        } else if ((screenSeen && ++closed > 6) || lain > WAIT_LIMIT) {
            bier = null; // closed for good (a few ticks' grace: an act's mark may come after the close)
            SceneWalk.release();
            return;
        }
        ScreenFade.show(Mth.clamp((lain - (BierLying.ANIM + HOLD)) / (float) FADE_IN, 0F, 1F)); // the fade before the screen
    }
}
