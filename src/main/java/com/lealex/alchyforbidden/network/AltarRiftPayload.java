package com.lealex.alchyforbidden.network;

import com.lealex.alchyforbidden.AlchyForbidden;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The great rift over a growing altar, for the players watching it (client/AltarRifts draws it):
 * OPEN (rift = its place, size = how wide) as the altar starts gathering itself, CLOSE once the tier stands,
 * MOVE: it goes up with a rising core. The parts themselves fly through AlchyX (growth/GrowthPayload, client/growth/
 * GrowthParts); EMERGE / SINK / RISE are kept here only as numbers.
 */
public record AltarRiftPayload(int kind, BlockPos rift, BlockPos pos, BlockState look, int size) implements CustomPacketPayload {
    public static final int OPEN = 0, CLOSE = 1, EMERGE = 2, SINK = 3;
    /** Sculk Corruption absorbed: a stream from rift (the death) to pos (the altar's core). */
    public static final int ABSORB = 4;
    /** A platform part rising one block, out of the block below pos into pos (no rift). */
    public static final int RISE = 5;

    /** How long a RISE part takes to come up `blocks` blocks (size = blocks; 0 counts as 1): 60 ticks, 30 more a block. */
    public static int riseTicks(int blocks) {
        return com.lealex.alchyx.growth.GrowthPayload.riseTicks(blocks);
    }
    /** A warden killed beside a resonator taken by the rift: rift = where it opens, pos = the body, size = its yaw. */
    public static final int TAKEN = 6;
    /** Essence sent along a link: a guided stream straight from the centre of rift to the centre of pos, size = essence + 1. */
    public static final int GUIDE = 7;
    /** The rift opened at `rift` moves (with the core rising under it) to `pos`, over `size` ticks. */
    public static final int MOVE = 8;
    /** How long a guided stream flies (essence/Relays sends the next hop as one lands). */
    public static final int GUIDE_TICKS = 24;

    public static final Type<AltarRiftPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "altar_rift"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AltarRiftPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AltarRiftPayload::kind,
            BlockPos.STREAM_CODEC, AltarRiftPayload::rift,
            BlockPos.STREAM_CODEC, AltarRiftPayload::pos,
            ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), AltarRiftPayload::look,
            ByteBufCodecs.VAR_INT, AltarRiftPayload::size,
            AltarRiftPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void open(ServerLevel level, BlockPos rift, int size) {
        send(level, new AltarRiftPayload(OPEN, rift.immutable(), rift.immutable(), Blocks.AIR.defaultBlockState(), size));
    }

    public static void close(ServerLevel level, BlockPos rift) {
        send(level, new AltarRiftPayload(CLOSE, rift.immutable(), rift.immutable(), Blocks.AIR.defaultBlockState(), 0));
    }

    public static void part(ServerLevel level, int kind, BlockPos rift, BlockPos pos, BlockState look, int size) {
        send(level, new AltarRiftPayload(kind, rift.immutable(), pos.immutable(), look, size));
    }

    /** To every player that has the rift's chunk loaded (the altar's parts are within a few blocks of it). */
    private static void send(ServerLevel level, AltarRiftPayload payload) {
        BlockPos at = payload.rift();
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(at.getX() >> 4, at.getZ() >> 4), payload);
    }

    @EventBusSubscriber(modid = AlchyForbidden.MODID)
    public static final class Registration {
        private Registration() {}

        @SubscribeEvent
        public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, STREAM_CODEC,
                    (payload, context) -> com.lealex.alchyforbidden.client.AltarRifts.receive(payload));
        }
    }
}
