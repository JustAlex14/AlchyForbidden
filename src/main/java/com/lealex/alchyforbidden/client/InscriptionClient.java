package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.essence.Essence;
import com.lealex.alchyforbidden.essence.Inscription;
import com.lealex.alchyforbidden.essence.Inscriptions;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The carvings that move the body, on the player's own side (its movement is its own): Water / Lava Walker keep it on
 * the surface, Sky Walker keeps it where it stands in the air (sneak to go down), Chained Wings start a glide when jump
 * is pressed in the air (the server keeps it up). And the essence reserve's three bars beside the hotbar.
 */
public final class InscriptionClient {
    private InscriptionClient() {}

    private static boolean jumpWasDown = false;
    /** Second Leap: leaps left before touching the ground again. */
    private static int leapsLeft = 0;

    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player.isSpectator() || player.getAbilities().flying) return;
        boolean jump = player.input.keyPresses.jump();
        boolean pressed = jump && !jumpWasDown;
        jumpWasDown = jump;
        if (player.isFallFlying() || player.isPassenger()) return;
        Vec3 motion = player.getDeltaMovement();
        if (player.onGround() || player.isInWater() || player.onClimbable()) leapsLeft = Inscriptions.leaps(player);
        // Second Leap: jump again in the air (before the Chained Wings' glide, which takes the press once the leaps are spent)
        if (pressed && !player.onGround() && !player.isInWater() && leapsLeft > 0 && Inscriptions.canPay(player, Inscription.SECOND_LEAP)) {
            leapsLeft--;
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            Vec3 push = player.input.getMoveVector().lengthSquared() > 0.01F ? new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)).scale(0.12) : Vec3.ZERO;
            player.setDeltaMovement(motion.x + push.x, 0.52, motion.z + push.z);
            player.resetFallDistance();
            ClientPacketDistributor.sendToServer(new com.lealex.alchyforbidden.network.LeapPayload());
            return;
        }
        // Wall Climber: pressed against a wall, up it as on a ladder (sneak to hold on)
        if (player.horizontalCollision && !player.onGround() || player.horizontalCollision && player.input.keyPresses.forward()) {
            if (Inscriptions.has(player, Inscription.WALL_CLIMBER) && player.input.keyPresses.forward() && Inscriptions.canPay(player, Inscription.WALL_CLIMBER)) {
                player.setDeltaMovement(motion.x, player.isShiftKeyDown() ? 0 : 0.2, motion.z);
                player.resetFallDistance();
                return;
            }
        }
        // Chained Wings: jump in the air glides, as with an elytra
        if (pressed && !player.onGround() && !player.isInWater() && motion.y < 0 && Inscriptions.has(player, Inscription.CHAINED_WINGS)
                && Inscriptions.canPay(player, Inscription.CHAINED_WINGS)) {
            player.startFallFlying();
            ClientPacketDistributor.sendToServer(new com.lealex.alchyforbidden.network.GlidePayload());
            return;
        }
        if (player.isShiftKeyDown() || motion.y > 0) return; // sneak to go down; a jump goes up as ever
        boolean water = Inscriptions.has(player, Inscription.WATER_WALKER) && Inscriptions.canPay(player, Inscription.WATER_WALKER);
        boolean lava = Inscriptions.has(player, Inscription.LAVA_WALKER) && Inscriptions.canPay(player, Inscription.LAVA_WALKER);
        if (water || lava) { // stand on the surface of water / lava
            BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.1, player.getZ());
            FluidState fluid = player.level().getFluidState(below);
            if ((water && fluid.is(FluidTags.WATER)) || (lava && fluid.is(FluidTags.LAVA))) {
                double top = below.getY() + fluid.getHeight(player.level(), below);
                if (player.getY() >= top - 0.4 && player.getY() <= top + 0.3 && player.level().getFluidState(below.above()).isEmpty()) {
                    stand(player, motion, top);
                    return;
                }
            }
        }
        if (!player.onGround() && !player.isInWater() && !player.isInLava() && Inscriptions.has(player, Inscription.SKY_WALKER)
                && Inscriptions.canPay(player, Inscription.SKY_WALKER)) {
            stand(player, motion, player.getY()); // the air holds it up
        }
    }

    private static void stand(LocalPlayer player, Vec3 motion, double y) {
        if (Math.abs(player.getY() - y) > 1.0E-4) player.setPos(player.getX(), y, player.getZ());
        player.setDeltaMovement(motion.x, 0, motion.z);
        player.resetFallDistance();
        player.setOnGround(true);
    }

    // ---- The essence reserve beside the hotbar ----

    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "essence_reserve"), InscriptionClient::reserve);
    }

    private static void reserve(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator() || !Inscriptions.has(player, Inscription.ESSENCE_RESERVE)) return;
        int cap = Math.max(1, Inscriptions.reserveCap(player));
        int x = graphics.guiWidth() / 2 - 91 - 32, bottom = graphics.guiHeight() - 3, height = 18, step = 9;
        for (Essence essence : Essence.values()) {
            int bx = x + essence.ordinal() * step;
            graphics.text(minecraft.font, essence.icon(), bx - 2, bottom - height - 10, 0xFFFFFFFF, false); // the essence's icon over its bar
            graphics.fill(bx - 1, bottom - height - 1, bx + 4, bottom + 1, 0xC00B0A10);
            int filled = Math.round(height * Math.min(1F, Inscriptions.reserve(player, essence) / (float) cap));
            int color = essence.color();
            int lifted = (((color >> 16 & 0xFF) + 255) / 2) << 16 | (((color >> 8 & 0xFF) + 255) / 2) << 8 | ((color & 0xFF) + 255) / 2;
            graphics.fill(bx, bottom - filled, bx + 3, bottom, 0xFF000000 | (essence == Essence.CORRUPTION ? lifted : color));
        }
    }
}
