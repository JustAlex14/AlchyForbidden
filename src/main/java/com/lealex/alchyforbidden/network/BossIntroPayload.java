package com.lealex.alchyforbidden.network;

import com.lealex.alchyforbidden.AlchyForbidden;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A great one has come through: client/BossIntro shows it (the creature's entity id, how long). */
public record BossIntroPayload(int entity, int ticks) implements CustomPacketPayload {
    public static final Type<BossIntroPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "boss_intro"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BossIntroPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BossIntroPayload::entity,
            ByteBufCodecs.VAR_INT, BossIntroPayload::ticks,
            BossIntroPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @EventBusSubscriber(modid = AlchyForbidden.MODID)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> com.lealex.alchyforbidden.client.BossIntro.start(payload));
        }
    }
}
