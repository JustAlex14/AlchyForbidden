package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.essence.RingContents;
import com.lealex.alchyforbidden.essence.Spell;
import com.lealex.alchyforbidden.essence.SpellGem;
import com.lealex.alchyforbidden.essence.Spells;
import com.lealex.alchyforbidden.item.SpellRingItem;
import com.lealex.alchyx.client.Lines;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The rings a player wears, opened anywhere with its key (client/SpellKeys, K): one row per ring in the order its
 * spells are picked, the spell, the gems in its sockets, what it really costs and how long it cools. Only a view: rings
 * are worn and ordered in a Binding Circle's Rings tab.
 */
public class SpellSlotsScreen extends Screen {
    private static final int W = 256, ROW = 22;

    public SpellSlotsScreen() {
        super(Component.translatable("gui.alchyforbidden.spell_slots"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        List<Spells.Worn> rings = new ArrayList<>(Spells.worn(player));
        var familiars = com.lealex.alchyforbidden.familiar.Familiars.worn(player);
        int hand = 14 + familiars.size() * ROW;
        int h = 34 + Math.max(1, rings.size()) * ROW + 14 + hand;
        int x = (width - W) / 2, y = (height - h) / 2;
        graphics.fill(x - 2, y - 2, x + W + 2, y + h + 2, 0xFF0B0A10);
        graphics.fill(x, y, x + W, y + h, 0xF018161E);
        graphics.text(font, title, x + 8, y + 6, 0xFFB98CFF, false);
        familiarRows(graphics, player, familiars, x, y + 22 + Math.max(1, rings.size()) * ROW + 4);
        if (rings.isEmpty()) {
            int ly = y + 22;
            for (String line : Lines.wrap(font, Component.translatable("gui.alchyforbidden.spells_none").getString(), W - 16)) {
                graphics.text(font, line, x + 8, ly, 0xFF6A6478, false);
                ly += 10;
            }
            return;
        }
        Spell hovered = null;
        RingContents hoveredRing = null;
        for (int i = 0; i < rings.size(); i++) {
            int ry = y + 22 + i * ROW;
            Spells.Worn ring = rings.get(i);
            graphics.text(font, String.valueOf(i + 1), x + 8, ry + 5, 0xFF55505E, false);
            graphics.fill(x + 17, ry - 1, x + 37, ry + 19, i == SpellKeys.selected() ? 0xFFB98CFF : 0xFF0B0A10);
            graphics.fill(x + 18, ry, x + 36, ry + 18, 0xFF241C2C);
            if (ring == null) {
                graphics.text(font, Component.translatable("gui.alchyforbidden.ring_empty"), x + 42, ry + 5, 0xFF55505E, false);
                continue;
            }
            graphics.item(ring.toStack(), x + 19, ry + 1);
            Spell spell = ring.spell();
            if (spell == null) continue;
            graphics.text(font, Lines.clip(font, spell.displayName().getString(), 118), x + 42, ry + 1, 0xFF000000 | spell.color(), false);
            Component stats = Spells.costLine(Spells.costs(spell, ring.contents())).copy()
                    .append(", " + seconds(Spells.cooldown(spell, ring.contents())));
            graphics.text(font, stats, x + 42, ry + 10, 0xFF9C93AE, false);
            int sockets = SpellRingItem.sockets(ring.tier());
            for (int k = 0; k < sockets; k++) { // the sockets: a chip per gem
                int gx = x + W - 6 - (sockets - k) * 21, gy = ry + 2; // right-aligned: the cost line has the room left of them
                SpellGem gem = ring.contents().gem(k);
                graphics.fill(gx, gy, gx + 18, gy + 14, 0xFF0B0A10);
                if (gem != null) graphics.fill(gx + 2, gy + 2, gx + 16, gy + 12, 0xFF000000 | gem.color());
            }
            if (mouseX >= x + 17 && mouseX < x + W - 4 && mouseY >= ry - 1 && mouseY < ry + 19) {
                hovered = spell;
                hoveredRing = ring.contents();
            }
        }
        graphics.text(font, Component.translatable("gui.alchyforbidden.rings_hint"), x + 8, y + h - 12, 0xFF6A6478, false);
        if (hovered != null) {
            List<Component> tip = new ArrayList<>(tooltip(font, hovered));
            for (int k = 0; k < hoveredRing.gems().size(); k++) {
                SpellGem gem = hoveredRing.gem(k);
                if (gem != null) tip.add(gem.displayName().copy().append(": ").append(gem.effect()).withStyle(s -> s.withColor(gem.color())));
            }
            graphics.setTooltipForNextFrame(font, tip, Optional.empty(), mouseX, mouseY);
        }
    }

    /** The left hand: each familiar ring, its creature, level and whether it is out, ready or cracked. */
    private void familiarRows(GuiGraphicsExtractor graphics, LocalPlayer player, List<com.lealex.alchyforbidden.familiar.FamiliarData.@org.jspecify.annotations.Nullable Worn> familiars,
                              int x, int y) {
        graphics.text(font, Component.translatable("gui.alchyforbidden.hand_title"), x + 8, y, 0xFF2FC6B0, false);
        var out = com.lealex.alchyforbidden.familiar.Familiars.outSlots(player);
        long now = player.level().getGameTime();
        for (int i = 0; i < familiars.size(); i++) {
            int ry = y + 12 + i * ROW;
            graphics.fill(x + 17, ry - 1, x + 37, ry + 19, 0xFF0B0A10);
            graphics.fill(x + 18, ry, x + 36, ry + 18, 0xFF1C2426);
            var ring = familiars.get(i);
            if (ring == null) {
                graphics.text(font, Component.translatable("gui.alchyforbidden.ring_empty"), x + 42, ry + 5, 0xFF55505E, false);
                continue;
            }
            graphics.item(com.lealex.alchyforbidden.familiar.Familiars.toStack(ring), x + 19, ry + 1);
            var data = ring.data();
            var creature = data.creatureOrNull();
            if (creature == null) continue;
            graphics.text(font, Lines.clip(font, data.title().getString(), 110), x + 42, ry + 1,
                    0xFF000000 | com.lealex.alchyforbidden.familiar.RiftCreature.gradeColor(data.corruption()), false);
            boolean cracked = data.cracked(now);
            String state = cracked ? Component.translatable("gui.alchyforbidden.hand_cracked", (data.crackedUntil() - now) / 20 + 1).getString()
                    : Component.translatable(out.contains(i) ? "gui.alchyforbidden.hand_out" : "gui.alchyforbidden.hand_ready").getString();
            graphics.text(font, "Lv " + data.level() + "  " + state, x + 42, ry + 10, cracked ? 0xFFE0485A : out.contains(i) ? 0xFF2FC6B0 : 0xFF9C93AE, false);
        }
    }

    /** Ticks as seconds, "12s" or "2.5s" (no ".0"). */
    static String seconds(int ticks) {
        float s = ticks / 20F;
        return (s == Math.floor(s) ? String.valueOf((int) s) : String.format(java.util.Locale.ROOT, "%.1f", s)) + "s";
    }

    /** A spell's name, what it does, its cost and cooldown (also used by the circle's screen). */
    public static List<Component> tooltip(Font font, Spell spell) {
        List<Component> tip = new ArrayList<>();
        tip.add(spell.displayName().copy().withStyle(s -> s.withColor(spell.color())));
        for (String line : Lines.wrap(font, spell.description().getString(), 180)) tip.add(Component.literal(line).withStyle(net.minecraft.ChatFormatting.GRAY));
        tip.add(Component.translatable("gui.alchyforbidden.spell_cast", Spells.costLine(spell.castCosts()),
                String.format(java.util.Locale.ROOT, "%.1f", spell.cooldown() / 20F)).withStyle(s -> s.withColor(0xC8C0D8)));
        return tip;
    }
}
