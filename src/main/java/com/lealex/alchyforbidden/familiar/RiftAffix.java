package com.lealex.alchyforbidden.familiar;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * What the rift leaves in an altered creature: one affix per grade (Rifted 1, Deep-Rifted 2, Void-Rifted 3), rolled as
 * it comes through, kept in its ring forever (a corrupting ritual adds one). They work the same on a familiar and on an
 * arena foe (familiar/RiftBeast). Lang affix.alchyforbidden.<id> and .desc.
 */
public enum RiftAffix {
    VAMPIRIC("vampiric", 0xE0485A),
    BLINKING("blinking", 0xB98CFF),
    THORNED("thorned", 0x8AC040),
    FRENZIED("frenzied", 0xFF7A3A),
    SPLITTING("splitting", 0x9FE6E0),
    HUNGERING("hungering", 0xB01020),
    WARDED("warded", 0x7AB4FF),
    VOIDTOUCHED("voidtouched", 0x5A2AA0),
    GRAVE_BOUND("grave_bound", 0xC8D8E8),
    CHILLING("chilling", 0xA0D8FF),
    BURNING("burning", 0xFF9030),
    WITHERING("withering", 0x4A4A5A);

    private final String id;
    private final int color;

    RiftAffix(String id, int color) {
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
        return Component.translatable("affix.alchyforbidden." + id).withStyle(s -> s.withColor(color));
    }

    public Component description() {
        return Component.translatable("affix.alchyforbidden." + id + ".desc");
    }

    public static @Nullable RiftAffix byId(String id) {
        for (RiftAffix affix : values()) if (affix.id.equals(id)) return affix;
        return null;
    }

    /** Every affix allowed (a filter's mask: bit = ordinal). */
    public static final int ALL = (1 << 12) - 1;

    public boolean in(int mask) {
        return (mask >> ordinal() & 1) != 0;
    }

    /** How many affixes a filter leaves out. */
    public static int excluded(int mask) {
        return values().length - Integer.bitCount(mask & ALL);
    }

    /** One more affix the creature does not have yet, among the allowed (null when none is left). */
    public static @Nullable RiftAffix rollNew(RandomSource random, List<String> have, int mask) {
        List<RiftAffix> left = new ArrayList<>();
        for (RiftAffix affix : values()) if (affix.in(mask) && !have.contains(affix.id)) left.add(affix);
        return left.isEmpty() ? null : left.get(random.nextInt(left.size()));
    }

    public static @Nullable RiftAffix rollNew(RandomSource random, List<String> have) {
        return rollNew(random, have, ALL);
    }

    /** count distinct affixes among the allowed, as ids (fewer if the filter leaves fewer). */
    public static List<String> roll(RandomSource random, int count, int mask) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RiftAffix affix = rollNew(random, out, mask);
            if (affix != null) out.add(affix.id);
        }
        return List.copyOf(out);
    }

    public static List<String> roll(RandomSource random, int count) {
        return roll(random, count, ALL);
    }
}
