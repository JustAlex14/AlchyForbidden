package com.lealex.alchyforbidden.familiar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/**
 * What a familiar ring holds (the item's data component, and each worn ring in essence order): the creature bound in it,
 * how altered it came (0..3), its level and experience, and the game time its ring is cracked until (it fell in battle;
 * 0 = whole).
 */
public record FamiliarData(String creature, int corruption, int level, int xp, long crackedUntil, String name, java.util.List<String> affixes) {
    public static final int MAX_LEVEL = 20;

    public static final Codec<FamiliarData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("creature").forGetter(FamiliarData::creature),
            Codec.INT.optionalFieldOf("corruption", 0).forGetter(FamiliarData::corruption),
            Codec.INT.optionalFieldOf("level", 1).forGetter(FamiliarData::level),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(FamiliarData::xp),
            Codec.LONG.optionalFieldOf("cracked_until", 0L).forGetter(FamiliarData::crackedUntil),
            Codec.STRING.optionalFieldOf("name", "").forGetter(FamiliarData::name),
            Codec.STRING.listOf().optionalFieldOf("affixes", java.util.List.of()).forGetter(FamiliarData::affixes)
    ).apply(instance, FamiliarData::new));

    public static final StreamCodec<ByteBuf, FamiliarData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, FamiliarData::creature,
            ByteBufCodecs.VAR_INT, FamiliarData::corruption,
            ByteBufCodecs.VAR_INT, FamiliarData::level,
            ByteBufCodecs.VAR_INT, FamiliarData::xp,
            ByteBufCodecs.VAR_LONG, FamiliarData::crackedUntil,
            ByteBufCodecs.STRING_UTF8, FamiliarData::name,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), FamiliarData::affixes,
            FamiliarData::new);

    public static FamiliarData fresh(RiftCreature creature, int corruption, java.util.List<String> affixes) {
        return new FamiliarData(creature.id(), corruption, 1, 0, 0L, "", java.util.List.copyOf(affixes));
    }

    public boolean has(RiftAffix affix) {
        return affixes.contains(affix.id());
    }

    /** One grade higher with one more affix (the corrupting ritual). */
    public FamiliarData corrupted(String affix) {
        java.util.List<String> more = new java.util.ArrayList<>(affixes);
        if (affix != null && !more.contains(affix)) more.add(affix);
        return new FamiliarData(creature, Math.min(3, corruption + 1), level, xp, crackedUntil, name, java.util.List.copyOf(more));
    }

    public @Nullable RiftCreature creatureOrNull() {
        return RiftCreature.byId(creature);
    }

    public boolean cracked(long now) {
        return crackedUntil > now;
    }

    public FamiliarData crackedTill(long until) {
        return new FamiliarData(creature, corruption, level, xp, until, name, affixes);
    }

    /** Named by its owner (a name tag on it, or the ring renamed in an anvil); "" takes the name back off. */
    public FamiliarData named(String name) {
        return new FamiliarData(creature, corruption, level, xp, crackedUntil, name.length() > 32 ? name.substring(0, 32) : name, affixes);
    }

    /** What it is called: its own name in its grade's colour, else the graded creature's name. */
    public net.minecraft.network.chat.Component title() {
        RiftCreature kind = creatureOrNull();
        if (name.isEmpty()) return kind == null ? net.minecraft.network.chat.Component.literal(creature) : kind.fullName(corruption);
        return net.minecraft.network.chat.Component.literal(name).withStyle(s -> s.withColor(RiftCreature.gradeColor(corruption)));
    }

    /** Experience to the next level. */
    public static int toNext(int level) {
        return 20 + level * level * 6;
    }

    /** With this much more experience: levels gained as it goes (up to MAX_LEVEL). */
    public FamiliarData withXp(int gained) {
        int lv = level, x = xp + Math.max(0, gained);
        while (lv < MAX_LEVEL && x >= toNext(lv)) {
            x -= toNext(lv);
            lv++;
        }
        if (lv >= MAX_LEVEL) x = 0;
        return new FamiliarData(creature, corruption, lv, x, crackedUntil, name, affixes);
    }

    // ---- What its grade and level make of it ----

    public double healthFactor() {
        return (1 + 0.4 * corruption) * (1 + 0.08 * (level - 1));
    }

    public double damageFactor() {
        return (1 + 0.3 * corruption) * (1 + 0.06 * (level - 1));
    }

    public double speedFactor() {
        return (1 + 0.05 * corruption) * (1 + 0.01 * (level - 1));
    }

    /** Armour and toughness added by its level. */
    public double armorBonus() {
        return 0.5 * (level - 1);
    }

    public double toughnessBonus() {
        return 0.25 * (level - 1);
    }

    public String encode(int tier) {
        return tier + "|" + creature + "|" + corruption + "|" + level + "|" + xp + "|" + crackedUntil + "|" + name.replace('|', '/') + "|" + String.join(",", affixes);
    }

    public record Worn(int tier, FamiliarData data) {}

    public static @Nullable Worn decode(String text) {
        if (text.isEmpty()) return null;
        String[] p = text.split("\\|", -1);
        if (p.length < 6) return null;
        try {
            return new Worn(Integer.parseInt(p[0]), new FamiliarData(p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]), Long.parseLong(p[5]),
                    p.length > 6 ? p[6] : "", p.length > 7 && !p[7].isEmpty() ? java.util.List.of(p[7].split(",")) : java.util.List.of()));
        } catch (NumberFormatException bad) {
            return null;
        }
    }
}
