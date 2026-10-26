package com.lealex.alchyforbidden.essence;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The gems set in a spell ring's sockets (item/SpellRingItem). Each one of a kind adds its step again:
 * SWIFT shortens the cooldown, FURY strikes harder, LASTING holds longer, THRIFT costs less, REACH goes further.
 * The cooldown and the cost never fall under 40%.
 */
public enum SpellGem {
    SWIFT("swift", 0x7AE0FF),
    FURY("fury", 0xFF4A4A),
    LASTING("lasting", 0x6AA0FF),
    THRIFT("thrift", 0x6AE07A),
    REACH("reach", 0xE0C060);

    private final String id;
    private final int color;

    SpellGem(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public int color() {
        return color;
    }

    public Component displayName() {
        return Component.translatable("item.alchyforbidden.gem_" + id);
    }

    public Component effect() {
        return Component.translatable("gem.alchyforbidden." + id);
    }

    public static @Nullable SpellGem byId(String id) {
        for (SpellGem gem : values()) if (gem.id.equals(id)) return gem;
        return null;
    }
}
