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

/**
 * The void's pull begins for this player (client/VoidPullCamera shows it from outside): the rift it draws them into,
 * how long they are pulled before going through, and how long the view stays outside in all; the block they cling to
 * (its centre) and from / until which tick (grab -1: nothing to cling to).
 */
public record VoidPullPayload(double x, double y, double z, int pull, int total, double gx, double gy, double gz, int grab, int letGo) implements CustomPacketPayload {
    public static final Type<VoidPullPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "void_pull"));
    public static final StreamCodec<RegistryFriendlyByteBuf, VoidPullPayload> STREAM_CODEC = StreamCodec.of(VoidPullPayload::write, VoidPullPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, VoidPullPayload p) {
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeVarInt(p.pull);
        buf.writeVarInt(p.total);
        buf.writeDouble(p.gx);
        buf.writeDouble(p.gy);
        buf.writeDouble(p.gz);
        buf.writeInt(p.grab);
        buf.writeInt(p.letGo);
    }

    private static VoidPullPayload read(RegistryFriendlyByteBuf buf) {
        return new VoidPullPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readVarInt(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt(), buf.readInt());
    }

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
                    (payload, context) -> com.lealex.alchyforbidden.client.VoidPullCamera.start(payload));
        }
    }
}
