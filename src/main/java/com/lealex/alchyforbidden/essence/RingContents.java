package com.lealex.alchyforbidden.essence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.Nullable;

/**
 * What a spell ring holds (the item's data component): the spell imbued into it ("" for none) and the gems in its
 * sockets ("" for an empty socket).
 */
public record RingContents(String spell, List<String> gems) {
    public static final RingContents EMPTY = new RingContents("", List.of());

    public static final Codec<RingContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("spell", "").forGetter(RingContents::spell),
            Codec.STRING.listOf().optionalFieldOf("gems", List.of()).forGetter(RingContents::gems)
    ).apply(instance, RingContents::new));

    public static final StreamCodec<ByteBuf, RingContents> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RingContents::spell,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), RingContents::gems,
            RingContents::new);

    public @Nullable Spell spellOrNull() {
        return spell.isEmpty() ? null : Spell.byId(spell);
    }

    public @Nullable SpellGem gem(int socket) {
        return socket < gems.size() ? SpellGem.byId(gems.get(socket)) : null;
    }

    /** How many gems of that kind it holds. */
    public int count(SpellGem gem) {
        int n = 0;
        for (String id : gems) if (id.equals(gem.id())) n++;
        return n;
    }

    public RingContents withSpell(String spell) {
        return new RingContents(spell, gems);
    }

    public RingContents withGem(int socket, @Nullable SpellGem gem) {
        List<String> out = new ArrayList<>(gems);
        while (out.size() <= socket) out.add("");
        out.set(socket, gem == null ? "" : gem.id());
        while (!out.isEmpty() && out.getLast().isEmpty()) out.removeLast();
        return new RingContents(spell, List.copyOf(out));
    }

    // what its gems do to the spell (each gem of a kind adds its step)

    public double cooldownFactor() {
        return Math.max(0.4, 1 - 0.15 * count(SpellGem.SWIFT));
    }

    public double costFactor() {
        return Math.max(0.4, 1 - 0.15 * count(SpellGem.THRIFT));
    }

    public double damageFactor() {
        return 1 + 0.2 * count(SpellGem.FURY);
    }

    public double durationFactor() {
        return 1 + 0.25 * count(SpellGem.LASTING);
    }

    public double rangeFactor() {
        return 1 + 0.25 * count(SpellGem.REACH);
    }
}
