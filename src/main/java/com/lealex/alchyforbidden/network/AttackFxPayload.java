package com.lealex.alchyforbidden.network;

import com.lealex.alchyforbidden.AlchyForbidden;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * What a creature of the rift's attacks look like (client/AttackFx draws them as shapes, not dust):
 * BOLT: a projectile with a model by style (id, pos, velocity, life, size, colour; entity = what it homes on, owner =
 * whom it comes back to when it is a boomerang (a = 1)); BOLT_END stops it. BEAM: a ray from pos along d, extra long,
 * size wide, turning about Y over its life (a = amplitude, b = offset, style high bits: 1 a sine sweep, 2 a full turn).
 * RING: a ring going out along the floor (size = speed a tick, extra = how far). ZONE: a patch of floor (size = radius,
 * style: wax, fire, shadow). COLUMN: a column of light (size radius, extra height), a warning for delay ticks first.
 * LINE: spikes rising along a line from pos along d (extra long, size = blocks a tick, style: obsidian, bone).
 * NOVA: a blast going out (size = how far). EDGE: a burning ring closing in (extra = from, size = blocks a tick, a = to).
 */
public record AttackFxPayload(int kind, int id, double x, double y, double z, float dx, float dy, float dz, int life, int delay,
                              float size, float extra, float a, float b, int color, int style, int entity, int owner) implements CustomPacketPayload {
    public static final int BOLT = 0, BOLT_END = 1, BEAM = 2, RING = 3, ZONE = 4, COLUMN = 5, LINE = 6, NOVA = 7, EDGE = 8;
    // bolt styles
    public static final int ORB = 0, WAX = 1, BONE = 2, BLADE = 3, BOULDER = 4, FEATHER = 5, SHARD = 6;
    // beam styles (low bits), sweep modes (style >> 4)
    public static final int SONIC = 0, CORE = 1, SWEEP_SINE = 1 << 4, SWEEP_TURN = 2 << 4;
    // zone styles
    public static final int Z_WAX = 0, Z_FIRE = 1, Z_SHADOW = 2;
    // line styles
    public static final int L_OBSIDIAN = 0, L_BONE = 1;

    public static final Type<AttackFxPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "attack_fx"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AttackFxPayload> STREAM_CODEC = StreamCodec.of(AttackFxPayload::write, AttackFxPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, AttackFxPayload p) {
        buf.writeVarInt(p.kind);
        buf.writeVarInt(p.id);
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeFloat(p.dx);
        buf.writeFloat(p.dy);
        buf.writeFloat(p.dz);
        buf.writeVarInt(p.life);
        buf.writeVarInt(p.delay);
        buf.writeFloat(p.size);
        buf.writeFloat(p.extra);
        buf.writeFloat(p.a);
        buf.writeFloat(p.b);
        buf.writeInt(p.color);
        buf.writeVarInt(p.style);
        buf.writeVarInt(p.entity);
        buf.writeVarInt(p.owner);
    }

    private static AttackFxPayload read(RegistryFriendlyByteBuf buf) {
        return new AttackFxPayload(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** To everyone who sees that creature. */
    public void send(Entity from) {
        PacketDistributor.sendToPlayersTrackingEntity(from, this);
    }

    @EventBusSubscriber(modid = AlchyForbidden.MODID)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC, (payload, context) -> com.lealex.alchyforbidden.client.AttackFx.receive(payload));
        }
    }
}
