package com.lealex.alchyforbidden.network;

import com.lealex.alchyforbidden.AlchyForbidden;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The eye says something to one player: client/EyeSpeech shows it in its speaking box, typed out. */
public record EyeSpeechPayload(Component line) implements CustomPacketPayload {
    public static final Type<EyeSpeechPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "eye_speech"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EyeSpeechPayload> STREAM_CODEC =
            ComponentSerialization.TRUSTED_STREAM_CODEC.map(EyeSpeechPayload::new, EyeSpeechPayload::line);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @EventBusSubscriber(modid = AlchyForbidden.MODID)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> com.lealex.alchyforbidden.client.EyeSpeech.say(payload.line()));
        }
    }
}
