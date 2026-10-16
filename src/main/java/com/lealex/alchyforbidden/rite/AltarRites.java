package com.lealex.alchyforbidden.rite;

import com.lealex.alchyforbidden.AlchyForbidden;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

/**
 * Loads the altar's rites (data/alchyforbidden/altar_rite/*.json) on world load and /reload, and sends them to the
 * players (join and /reload) so the altar's screen can list them, locked or not.
 */
@EventBusSubscriber(modid = AlchyForbidden.MODID)
public final class AltarRites extends SimpleJsonResourceReloadListener<AltarRite> {
    private static volatile Map<Identifier, AltarRite> rites = Map.of();
    private static volatile Map<Identifier, AltarRite> client = Map.of();
    /** Told when the client's list changes (join, /reload): JEI's plugin sets it, nothing else needs it. */
    public static Runnable clientListener = () -> {};

    private AltarRites() {
        super(AltarRite.CODEC, FileToIdConverter.json("altar_rite"));
    }

    @Override
    protected void apply(Map<Identifier, AltarRite> loaded, ResourceManager manager, ProfilerFiller profiler) {
        rites = Map.copyOf(loaded);
        AlchyForbidden.LOGGER.info("AlchyForbidden: loaded {} altar rite(s)", loaded.size());
    }

    /** The rite these items make, whatever the tier (null for none): the caller checks the tier. */
    public static @Nullable AltarRite match(List<ItemStack> items) {
        if (items.isEmpty()) return null;
        for (AltarRite rite : sorted(rites)) {
            if (rite.matches(items)) return rite;
        }
        return null;
    }

    /**
     * The rites that ship with the mod, read straight from its own files (data/alchyforbidden/altar_rite in the mod
     * jar), for a client that has not been sent the server's list yet: JEI builds its pages before that. Datapack
     * rites come with the server's list, which replaces these as soon as it arrives.
     */
    public static List<AltarRite> builtIn(net.minecraft.core.HolderLookup.Provider registries) {
        Map<Identifier, AltarRite> found = new java.util.HashMap<>();
        try {
            var file = net.neoforged.fml.ModList.get().getModFileById(AlchyForbidden.MODID);
            if (file == null) return List.of();
            var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries);
            file.getFile().getContents().visitContent("data/" + AlchyForbidden.MODID + "/altar_rite", (path, resource) -> {
                if (!path.endsWith(".json")) return;
                String name = path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length());
                try (java.io.Reader reader = resource.bufferedReader(java.nio.charset.StandardCharsets.UTF_8)) {
                    AltarRite.CODEC.parse(ops, com.google.gson.JsonParser.parseReader(reader))
                            .resultOrPartial(error -> AlchyForbidden.LOGGER.warn("altar rite {}: {}", name, error))
                            .ifPresent(rite -> found.put(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, name), rite));
                } catch (Exception broken) {
                    AlchyForbidden.LOGGER.warn("altar rite {} could not be read", name, broken);
                }
            });
        } catch (Exception failed) {
            AlchyForbidden.LOGGER.warn("could not read the built-in altar rites", failed);
        }
        return sorted(found);
    }

    /** The rites this side loaded itself (in single player the integrated server's: JEI's fallback). */
    public static List<AltarRite> all() {
        return sorted(rites);
    }

    /** Client: the rites the server sent, by tier then name (for the screen's list). */
    public static List<AltarRite> clientList() {
        return sorted(client);
    }

    /** Client: the rite these items make, from the server's list. */
    public static @Nullable AltarRite clientMatch(List<ItemStack> items) {
        if (items.isEmpty()) return null;
        for (AltarRite rite : sorted(client)) {
            if (rite.matches(items)) return rite;
        }
        return null;
    }

    private static List<AltarRite> sorted(Map<Identifier, AltarRite> map) {
        List<Map.Entry<Identifier, AltarRite>> entries = new ArrayList<>(map.entrySet());
        entries.sort(Comparator.<Map.Entry<Identifier, AltarRite>>comparingInt(e -> e.getValue().tier())
                .thenComparing(e -> e.getKey().toString()));
        List<AltarRite> list = new ArrayList<>();
        for (Map.Entry<Identifier, AltarRite> entry : entries) list.add(entry.getValue());
        return list;
    }

    public record Payload(Map<Identifier, AltarRite> rites) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "altar_rites"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC =
                ByteBufCodecs.fromCodecWithRegistriesTrusted(com.mojang.serialization.Codec.unboundedMap(Identifier.CODEC, AltarRite.CODEC))
                        .map(Payload::new, Payload::rites);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Payload.TYPE, Payload.STREAM_CODEC, (payload, context) -> {
            client = Map.copyOf(payload.rites());
            clientListener.run(); // (JEI refreshes its pages: compat/jei)
        });
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        Payload payload = new Payload(rites);
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "altar_rites"), new AltarRites());
    }
}
