package com.lealex.alchyforbidden.network;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.essence.Spells;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The spells' messages, client to server: a cast from the key (the ring slot picked); the familiars' call key. */
public final class SpellPayloads {
    private SpellPayloads() {}

    public record Cast(int slot) implements CustomPacketPayload {
        public static final Type<Cast> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "cast_spell"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Cast> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Cast::slot, Cast::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The familiars' key: call them all out, or send them all back. */
    /** The familiar key: calls the familiar of the slot picked (the one out goes back first), or sends it back. */
    public record CallFamiliars(int slot) implements CustomPacketPayload {
        public static final Type<CallFamiliars> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "call_familiars"));
        public static final StreamCodec<RegistryFriendlyByteBuf, CallFamiliars> STREAM_CODEC =
                net.minecraft.network.codec.ByteBufCodecs.VAR_INT.map(CallFamiliars::new, CallFamiliars::slot).cast();

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @EventBusSubscriber(modid = AlchyForbidden.MODID)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToServer(Cast.TYPE, Cast.STREAM_CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayer player) Spells.cast(player, payload.slot());
            });
            event.registrar("1").playToServer(CallFamiliars.TYPE, CallFamiliars.STREAM_CODEC, (payload, context) -> {
                if (context.player() instanceof ServerPlayer player) com.lealex.alchyforbidden.familiar.Familiars.toggle(player, payload.slot());
            });
        }
    }
}
