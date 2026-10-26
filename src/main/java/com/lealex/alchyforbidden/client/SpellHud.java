package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.essence.Spell;
import com.lealex.alchyforbidden.essence.Spells;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jspecify.annotations.Nullable;

/**
 * The soul's spell slots beside the hotbar (right of it): each slot's icon, the one picked framed in its spell's
 * colour, a dark sweep over a spell still cooling down, a dim one when the body cannot pay it now; each slot's cost in its corner; for three
 * seconds after the pick changes, its name and cost centred over the hotbar. Nothing until a spell is bound.
 */
public final class SpellHud {
    private SpellHud() {}

    private static final int SIZE = 18;

    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "spell_slots"), SpellHud::draw);
    }

    /** A spell's icon (16 x 16) at x, y. */
    public static void icon(GuiGraphicsExtractor graphics, Spell spell, int x, int y) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, spell.icon(), x, y, 0F, 0F, 16, 16, 16, 16);
    }

    private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()) return;
        List<Spells.@Nullable Worn> rings = Spells.worn(player);
        List<@Nullable Spell> slots = Spells.slots(player);
        if (slots.isEmpty()) {
            familiar(graphics, minecraft, player, graphics.guiWidth() / 2 + 91 + 8, graphics.guiHeight() - SIZE - 3, delta);
            return;
        }
        long now = player.level().getGameTime();
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int width = slots.size() * (SIZE + 2) - 2;
        int x0 = graphics.guiWidth() / 2 + 91 + 8, y = graphics.guiHeight() - SIZE - 3;
        if (x0 + width > graphics.guiWidth() - 4) x0 = graphics.guiWidth() / 2 - 91 - 32 - width; // no room right: left of the hotbar (past the offhand)
        int picked = SpellKeys.selected();
        int fx = x0 + width + 6; // the familiar picked, right of the spells (left of them when the spells moved left)
        if (fx + SIZE > graphics.guiWidth() - 4) fx = x0 - SIZE - 6;
        familiar(graphics, minecraft, player, fx, y, delta);
        for (int i = 0; i < slots.size(); i++) {
            int x = x0 + i * (SIZE + 2);
            Spell spell = slots.get(i);
            boolean on = i == picked;
            graphics.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, on && spell != null ? 0xFF000000 | spell.color() : on ? 0xFFB98CFF : 0xC00B0A10);
            graphics.fill(x, y, x + SIZE, y + SIZE, 0xC018161E);
            if (spell == null) continue;
            icon(graphics, spell, x + 1, y + 1);
            var contents = rings.get(i).contents();
            int[] costs = Spells.costs(spell, contents);
            int castCost = costs[spell.essence().ordinal()], cooldown = Spells.cooldown(spell, contents);
            long ready = Spells.readyAt(player, spell);
            boolean payable = Spells.canPay(player, costs);
            if (ready > now) { // cooling down: a dark sweep falling as it comes back, the seconds left
                float left = Math.min(1F, (ready - now - partial) / Math.max(1F, cooldown));
                int h = Math.round(SIZE * left);
                graphics.fill(x, y + SIZE - h, x + SIZE, y + SIZE, 0xB0000000);
                String secs = String.valueOf((int) Math.ceil((ready - now) / 20.0));
                graphics.centeredText(minecraft.font, secs, x + SIZE / 2, y + 5, 0xFFFFFFFF);
            } else if (!payable) {
                graphics.fill(x, y, x + SIZE, y + SIZE, 0x90200810); // nothing to pay it with
            }
            // its cost in the corner, in its essence's colour (red when the body cannot pay it)
            String cost = String.valueOf(castCost);
            int color = payable ? 0xFF000000 | lifted(spell.essence().color()) : 0xFFFF5060;
            graphics.text(minecraft.font, cost, x + SIZE + 1 - minecraft.font.width(cost), y + SIZE - 7, color, true);
        }
        // the spell picked: its name and cost over the middle of the hotbar for 3 s after a switch, fading
        Spell shown = picked < slots.size() ? slots.get(picked) : null;
        Component shownCost = shown == null ? Component.empty() : Spells.costLine(Spells.costs(shown, rings.get(picked).contents()));
        long since = now - SpellKeys.changedAt();
        if (shown != null && since < 60) {
            int alpha = since < 45 ? 255 : Math.max(16, (int) (255 * (60 - since) / 15F));
            Component line = shown.displayName().copy().withStyle(s -> s.withColor(shown.color()))
                    .append(Component.literal("  ")).append(shownCost.copy().withStyle(s -> s.withColor(0xE6DDF2)));
            int cy = graphics.guiHeight() - 72;
            int w = minecraft.font.width(line);
            int cx = graphics.guiWidth() / 2;
            graphics.fill(cx - w / 2 - 4, cy - 3, cx + w / 2 + 4, cy + 11, (alpha * 0x90 / 255) << 24 | 0x0B0A10);
            graphics.centeredText(minecraft.font, line, cx, cy, alpha << 24 | 0xFFFFFF);
        }
    }

    /** The familiar ring picked (SpellKeys.familiar): its ring in a frame of its creature's colour, lit while it is out, a crack timer when cracked. */
    private static void familiar(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer player, int x, int y, DeltaTracker delta) {
        var rings = com.lealex.alchyforbidden.familiar.Familiars.worn(player);
        int slot = SpellKeys.familiar();
        if (slot >= rings.size() || rings.get(slot) == null) return;
        var ring = rings.get(slot);
        var data = ring.data();
        boolean out = com.lealex.alchyforbidden.familiar.Familiars.outSlots(player).contains(slot);
        long now = player.level().getGameTime();
        var creature = data.creatureOrNull();
        int color = creature == null ? 0xB98CFF : creature.color();
        graphics.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, out ? 0xFF000000 | color : 0xC00B0A10);
        graphics.fill(x, y, x + SIZE, y + SIZE, 0xC018161E);
        if (creature != null) { // the summon's own face (textures/familiar/portrait, tools/portraits.py)
            graphics.blit(RenderPipelines.GUI_TEXTURED, net.minecraft.resources.Identifier.fromNamespaceAndPath("alchyforbidden",
                    "textures/familiar/portrait/" + creature.id() + ".png"), x + 1, y + 1, 0F, 0F, 16, 16, 16, 16);
        } else {
            graphics.item(com.lealex.alchyforbidden.familiar.Familiars.toStack(ring), x + 1, y + 1);
        }
        if (data.cracked(now)) {
            graphics.fill(x, y, x + SIZE, y + SIZE, 0xA0200810);
            graphics.centeredText(minecraft.font, String.valueOf((data.crackedUntil() - now) / 20 + 1), x + SIZE / 2, y + 5, 0xFFFF7070);
        } else if (!out) {
            graphics.fill(x, y, x + SIZE, y + SIZE, 0x50000000); // resting in its ring
        }
        // its name over the hotbar for 3 s after the pick changes
        long since = now - SpellKeys.familiarChangedAt();
        if (since < 60) {
            int alpha = since < 45 ? 255 : Math.max(16, (int) (255 * (60 - since) / 15F));
            Component line = data.title();
            int cy = graphics.guiHeight() - 84, w = minecraft.font.width(line), cx = graphics.guiWidth() / 2;
            graphics.fill(cx - w / 2 - 4, cy - 3, cx + w / 2 + 4, cy + 11, (alpha * 0x90 / 255) << 24 | 0x0B0A10);
            graphics.centeredText(minecraft.font, line, cx, cy, alpha << 24 | 0xFFFFFF);
        }
    }

    /** An essence colour lifted toward white (Corruption's deep teal reads badly on the dark HUD). */
    private static int lifted(int color) {
        int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
        return (r + (255 - r) * 2 / 5) << 16 | (g + (255 - g) * 2 / 5) << 8 | (b + (255 - b) * 2 / 5);
    }
}
