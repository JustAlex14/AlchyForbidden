package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.network.BossIntroPayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import com.lealex.alchyx.client.scene.CameraShots;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A great one's entrance: the view leaves the player and looks at the creature from the front, starting far off and
 * closing in on it while its name comes up (the server's title), then goes back to the player as the fight begins.
 */
public final class BossIntro {
    private BossIntro() {}

    private static int entity = -1, ticks, length;
    private static boolean watching;
    private static final Identifier SHOT = Identifier.fromNamespaceAndPath("alchyforbidden", "boss_intro");

    public static void start(BossIntroPayload payload) {
        entity = payload.entity();
        length = payload.ticks();
        ticks = 0;
        watching = false;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (entity < 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            stop(minecraft);
            return;
        }
        Entity boss = minecraft.level.getEntity(entity);
        if (!watching) {
            if (boss == null) {
                if (++ticks > 20) stop(minecraft); // it never arrived here
                return;
            }
            CameraShots.take(SHOT, CameraType.THIRD_PERSON_FRONT, BossIntro::distance); // AlchyX gives the camera back after
            CameraShots.watch(SHOT, boss);
            watching = true;
            ticks = 0;
        }
        if (boss == null || !boss.isAlive() || ++ticks > length) stop(minecraft);
    }

    private static void stop(Minecraft minecraft) {
        if (watching) CameraShots.release(SHOT);
        watching = false;
        entity = -1;
    }

    /** Far off, then closing in on it (eased), held for the name. */
    private static float distance(float usual) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity boss = minecraft.level == null ? null : minecraft.level.getEntity(entity);
        if (boss == null) return usual;
        float near = Math.max(5F, boss.getBbHeight() * 1.6F + boss.getBbWidth() * 1.2F), far = near * 2.6F;
        float k = Mth.clamp(ticks / (length * 0.6F), 0, 1);
        k = k * k * (3 - 2 * k);
        return Mth.lerp(k, far, near);
    }
}
