package com.lealex.alchyforbidden.essence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What a vessel holds (the item's data component): one essence, and how much of it. An empty vessel has none. */
public record VesselContents(Essence essence, int amount) {
    public static final Codec<VesselContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Essence.CODEC.fieldOf("essence").forGetter(VesselContents::essence),
            Codec.INT.fieldOf("amount").forGetter(VesselContents::amount)
    ).apply(instance, VesselContents::new));

    public static final StreamCodec<ByteBuf, VesselContents> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Essence.values()[i], Essence::ordinal), VesselContents::essence,
            ByteBufCodecs.VAR_INT, VesselContents::amount,
            VesselContents::new);
}
