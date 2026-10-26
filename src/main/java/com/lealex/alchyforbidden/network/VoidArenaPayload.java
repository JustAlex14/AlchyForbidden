package com.lealex.alchyforbidden.network;

import com.lealex.alchyforbidden.AlchyForbidden;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The void arena a challenger stands in (client/ArenaWalls draws its wall): its middle (the floor block), its radius,
 * when it opened (game time), and whether it stands at all (false: the trial is over, the wall goes).
 */
public record VoidArenaPayload(BlockPos center, int radius, long openedAt, boolean on) implements CustomPacketPayload {
    public static final Type<VoidArenaPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "void_arena"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VoidArenaPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, VoidArenaPayload::center,
            ByteBufCodecs.VAR_INT, VoidArenaPayload::radius,
            ByteBufCodecs.VAR_LONG, VoidArenaPayload::openedAt,
            ByteBufCodecs.BOOL, VoidArenaPayload::on,
            VoidArenaPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @EventBusSubscriber(modid = AlchyForbidden.MODID)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC,
                    (payload, context) -> com.lealex.alchyforbidden.client.ArenaWalls.voidArena(payload));
        }
    }
}
