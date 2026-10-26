package com.lealex.alchyforbidden.network;

import com.lealex.alchyforbidden.AlchyForbidden;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The client asks to glide on its Chained Wings (jump pressed in the air): essence/Inscriptions.startGliding. */
public record GlidePayload() implements CustomPacketPayload {
    public static final Type<GlidePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "glide"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GlidePayload> STREAM_CODEC = StreamCodec.unit(new GlidePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @EventBusSubscriber(modid = AlchyForbidden.MODID)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToServer(TYPE, STREAM_CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayer player) com.lealex.alchyforbidden.essence.Inscriptions.startGliding(player);
            });
        }
    }
}
