package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.essence.Inscription;
import com.lealex.alchyforbidden.essence.Inscriptions;
import com.lealex.alchyx.client.scene.BodyOverlays;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * What the Inscribing Bier leaves on a body, drawn over the player's skin through AlchyX BodyOverlays (so in the
 * inventory and the bier's own body view too, and posed with the body): the corruption, dark violet veins spreading
 * out from the heart in four stages as the body carries more carvings (a share of the most it may hold), on the
 * nearest shell; and for each carving its glyph, glowing on the next shell: every part has three spots, one per
 * carving on it in the order they were carved (white sheets marks_<part>_<1..3>, tools/textures.py body_marks()),
 * each tinted with its own carving's colour at full strength and breathing slowly. Registered in AlchyForbiddenClient.
 */
public final class BodyMarks {
    private BodyMarks() {}

    private static final int SPOTS = 3;
    private static final Identifier[][] GLYPHS = new Identifier[Inscription.Part.values().length][SPOTS];
    private static final Identifier[] CORRUPTION = new Identifier[4];

    static {
        for (Inscription.Part part : Inscription.Part.values()) {
            for (int k = 0; k < SPOTS; k++) {
                GLYPHS[part.ordinal()][k] = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID,
                        "textures/entity/marks_" + part.name().toLowerCase(java.util.Locale.ROOT) + "_" + (k + 1) + ".png");
            }
        }
        for (int i = 0; i < 4; i++) CORRUPTION[i] = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/entity/corruption_" + (i + 1) + ".png");
    }

    /** The BodyOverlays source. */
    public static void collect(Player player, float partial, List<BodyOverlays.Overlay> out) {
        List<String> carried = player.getData(com.lealex.alchyforbidden.registry.ModRegistries.INSCRIPTIONS);
        if (carried.isEmpty()) return;
        float age = player.tickCount + partial;
        float share = carried.size() / (float) Math.max(1, Config.maxInscriptions());
        int stage = Mth.clamp(Mth.ceil(share * 4), 1, 4);
        float throb = 0.85F + 0.15F * Mth.sin(age * 0.06F); // the veins throb slowly
        out.add(new BodyOverlays.Overlay(CORRUPTION[stage - 1], Math.round(throb * 255) << 24 | 0xFFFFFF, false, 0));
        float breath = 0.7F + 0.3F * (0.5F + 0.5F * Mth.sin(age * 0.09F)); // the glyphs glow and breathe
        for (Inscription.Part part : Inscription.Part.values()) {
            List<Inscription> on = Inscriptions.on(player, part); // in the order they were carved
            for (int k = 0; k < on.size() && k < SPOTS; k++) {
                out.add(new BodyOverlays.Overlay(GLYPHS[part.ordinal()][k], Math.round(breath * 255) << 24 | saturate(on.get(k).color()), true, 1));
            }
        }
    }

    /**
     * A carving's colour at full strength: its brightest channel up to 255 and the others pushed away from it, so the
     * glyphs read as that colour on any skin (lifted toward white they looked white).
     */
    private static int saturate(int color) {
        float r = (color >> 16 & 0xFF) / 255F, g = (color >> 8 & 0xFF) / 255F, b = (color & 0xFF) / 255F;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max <= 0.01F) return 0x7A3ED6;
        float span = Math.max(0.01F, max - min);
        r = Mth.clamp((r - min) / span * 0.85F + 0.15F * r / max, 0F, 1F);
        g = Mth.clamp((g - min) / span * 0.85F + 0.15F * g / max, 0F, 1F);
        b = Mth.clamp((b - min) / span * 0.85F + 0.15F * b / max, 0F, 1F);
        return Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }
}
