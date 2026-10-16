package com.lealex.alchyforbidden.essence;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * The three essences of the mage's craft. WRATH (anger, from killing) and SORROW (grief, from the dark) can be
 * carried in vessels; SCULK CORRUPTION is the altar's own: it gathers in the altar from the sculk around it and is
 * never carried. Rites spend them.
 */
public enum Essence implements StringRepresentable {
    WRATH("wrath", 0xD0304A, true),
    SORROW("sorrow", 0x3FC6D0, true),
    CORRUPTION("corruption", 0x0E7A70, false);

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
}
