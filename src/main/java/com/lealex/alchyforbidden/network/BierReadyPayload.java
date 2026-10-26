package com.lealex.alchyforbidden.network;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.essence.BierRest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The client's lying down on the Inscribing Bier has played out: open the screen (essence/BierRest.ready). */
public record BierReadyPayload() implements CustomPacketPayload {
    public static final Type<BierReadyPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "bier_ready"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BierReadyPayload> STREAM_CODEC = StreamCodec.unit(new BierReadyPayload());

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
                if (context.player() instanceof ServerPlayer player) BierRest.ready(player);
            });
        }
    }
}
