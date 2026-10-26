package com.lealex.alchyforbidden.network;

import com.lealex.alchyforbidden.AlchyForbidden;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * A spell's picture for the players watching (client/SpellFx draws it): kind, where (x, y, z), the entity it follows
 * (or -1), how long it lasts in ticks, how big.
 * CHAINS: chains out of three rifts round the place, gripping the entity. HOLE: the black hole's rift and its turning
 * disc. RIFT: a small rift that opens and closes (the bolt's mouth, the gate's ends).
 */
public record SpellFxPayload(int kind, double x, double y, double z, int entity, int ticks, float size, int other) implements CustomPacketPayload {
    public static final int CHAINS = 0, HOLE = 1, RIFT = 2;
    /** A shadow of the caster (other = its id) dashing from x y z to the entity and striking it (size = ticks of delay). */
    public static final int SHADOW = 3;

    public static final Type<SpellFxPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "spell_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpellFxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SpellFxPayload::kind,
            ByteBufCodecs.DOUBLE, SpellFxPayload::x,
            ByteBufCodecs.DOUBLE, SpellFxPayload::y,
            ByteBufCodecs.DOUBLE, SpellFxPayload::z,
            ByteBufCodecs.VAR_INT, SpellFxPayload::entity,
            ByteBufCodecs.VAR_INT, SpellFxPayload::ticks,
            ByteBufCodecs.FLOAT, SpellFxPayload::size,
            ByteBufCodecs.VAR_INT, SpellFxPayload::other,
            SpellFxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** To every player that has the place's chunk loaded. */
    public static void send(ServerLevel level, int kind, Vec3 at, int entity, int ticks, float size) {
        send(level, kind, at, entity, ticks, size, -1);
    }

    public static void send(ServerLevel level, int kind, Vec3 at, int entity, int ticks, float size, int other) {
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos((int) Math.floor(at.x) >> 4, (int) Math.floor(at.z) >> 4),
                new SpellFxPayload(kind, at.x, at.y, at.z, entity, ticks, size, other));
    }

    @EventBusSubscriber(modid = AlchyForbidden.MODID)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC,
                    (payload, context) -> com.lealex.alchyforbidden.client.SpellFx.receive(payload));
        }
    }
}
