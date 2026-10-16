package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.registry.ModRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The held eye looks where it wants the player to go: every few ticks a short trail of rift motes leaves the player
 * toward the spot it last named (the GAZE component), and the motes swirl around the spot once the player is there.
 * Only for the local player, only while an eye with a gaze in this dimension is in a hand.
 */
public final class EyeGaze {
    private EyeGaze() {}

    private static final int EVERY = 6;   // ticks between two trails
    private static final int MOTES = 5;

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || minecraft.isPaused() || level.getGameTime() % EVERY != 0) return;
        GlobalPos gaze = gazeIn(player.getItemInHand(InteractionHand.MAIN_HAND));
        if (gaze == null) gaze = gazeIn(player.getItemInHand(InteractionHand.OFF_HAND));
        if (gaze == null || gaze.dimension() != level.dimension()) return;

        Vec3 target = Vec3.atCenterOf(gaze.pos());
        Vec3 from = player.getEyePosition().add(0, -0.35, 0);
        Vec3 way = target.subtract(from);
        double distance = way.length();
        if (distance < 2.5) { // there: a ring of motes around the spot
            for (int i = 0; i < MOTES; i++) {
                double angle = level.getRandom().nextDouble() * Math.PI * 2;
                level.addParticle(ModRegistries.RIFT_MOTE.get(), target.x + Math.cos(angle) * 0.8, target.y + 0.6,
                        target.z + Math.sin(angle) * 0.8, -Math.sin(angle) * 0.05, 0.01, Math.cos(angle) * 0.05);
            }
            return;
        }
        Vec3 step = way.normalize();
        for (int i = 0; i < MOTES; i++) {
            double along = 1.0 + i * 0.55;
            Vec3 at = from.add(step.scale(along));
            level.addParticle(ModRegistries.RIFT_MOTE.get(), at.x, at.y, at.z, step.x * 0.06, step.y * 0.06, step.z * 0.06);
        }
    }

    private static GlobalPos gazeIn(ItemStack stack) {
        return stack.is(ModRegistries.RIFT_EYE.get()) ? stack.get(ModRegistries.GAZE.get()) : null;
    }
}
