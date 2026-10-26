package com.lealex.alchyforbidden.essence;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * The three essences of the mage's craft: WRATH (anger, from killing), SORROW (grief, from the dark) and SCULK
 * CORRUPTION (from deaths by a sculk catalyst, the resonator). All three can be carried in vessels. Rites spend them.
 */
public enum Essence implements StringRepresentable {
    WRATH("wrath", 0xD0304A, true),
    SORROW("sorrow", 0x3FC6D0, true),
    CORRUPTION("corruption", 0x0E7A70, true);

    public static final Codec<Essence> CODEC = StringRepresentable.fromEnum(Essence::values);

    private final String id;
    private final int color;
    private final boolean carried;

    Essence(String id, int color, boolean carried) {
        this.id = id;
        this.color = color;
        this.carried = carried;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** Its colour (RGB): bars, vessels, a rite's particles. */
    public int color() {
        return color;
    }

    /** True if a vessel can hold it. */
    public boolean carried() {
        return carried;
    }

    public Component displayName() {
        return Component.translatable("essence.alchyforbidden." + id);
    }

    private static final net.minecraft.network.chat.FontDescription ICONS =
            new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.fromNamespaceAndPath("alchyforbidden", "icons"));

    /** Its sign as text (a glyph of the font alchyforbidden:icons, white so it keeps its own colours): for amounts, "8" + icon. */
    public Component icon() {
        return Component.literal(String.valueOf((char) (0xE000 + ordinal())))
                .withStyle(style -> style.withFont(ICONS).withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFFFFFF)));
    }
}
