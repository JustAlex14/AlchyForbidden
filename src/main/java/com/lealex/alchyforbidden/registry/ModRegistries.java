package com.lealex.alchyforbidden.registry;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.block.AltarBlock;
import com.lealex.alchyforbidden.block.FacingLookBlock;
import com.lealex.alchyforbidden.block.entity.AltarBlockEntity;
import com.lealex.alchyforbidden.item.RiftEyeItem;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Everything AlchyForbidden registers: blocks, items, block entity types, particles, sounds and the creative tab. */
public final class ModRegistries {
    private ModRegistries() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(AlchyForbidden.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AlchyForbidden.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, AlchyForbidden.MODID);
    public static final DeferredRegister<net.neoforged.neoforge.attachment.AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.ATTACHMENT_TYPES, AlchyForbidden.MODID);
    /** A spawner bound with a Rift Knot (on its block entity, any mod's: essence/MobStacks). */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Boolean>> KNOTTED = ATTACHMENT_TYPES.register("knotted",
            () -> net.neoforged.neoforge.attachment.AttachmentType.builder(() -> false).serialize(com.mojang.serialization.Codec.BOOL.fieldOf("knotted"))
                    .sync(new net.neoforged.neoforge.attachment.AttachmentSyncHandler<Boolean>() {
                        @Override
                        public void write(net.minecraft.network.RegistryFriendlyByteBuf buf, Boolean value, boolean initialSync) {
                            buf.writeBoolean(value);
                        }

                        @Override
                        public Boolean read(net.neoforged.neoforge.attachment.IAttachmentHolder holder, net.minecraft.network.RegistryFriendlyByteBuf buf, Boolean previous) {
                            boolean on = buf.readBoolean();
                            if (holder instanceof net.minecraft.world.level.block.entity.BlockEntity spawner) {
                                com.lealex.alchyforbidden.essence.MobStacks.knotSeen(spawner, on);
                            }
                            return on;
                        }
                    }).build());
    /** Where a knotted spawner's mobs gather (its own position: round the spawner). Synced: client/KnotVisuals marks it. */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<net.minecraft.core.BlockPos>> KNOT_TARGET = ATTACHMENT_TYPES.register("knot_target",
            () -> net.neoforged.neoforge.attachment.AttachmentType.builder(() -> net.minecraft.core.BlockPos.ZERO)
                    .serialize(net.minecraft.core.BlockPos.CODEC.fieldOf("target"))
                    .sync(net.minecraft.core.BlockPos.STREAM_CODEC).build());
    /** How many mobs a condensed mob is (essence/MobStacks). */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Integer>> MOB_STACK = ATTACHMENT_TYPES.register("mob_stack",
            () -> net.neoforged.neoforge.attachment.AttachmentType.builder(() -> 1).serialize(com.mojang.serialization.Codec.INT.fieldOf("count")).build());

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AlchyForbidden.MODID);
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, AlchyForbidden.MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, AlchyForbidden.MODID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, AlchyForbidden.MODID);

    /** Where the eye last said it wants the player to go (it looks that way while held). Sent to the client. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> GAZE =
            DATA_COMPONENTS.registerComponentType("gaze", builder -> builder
                    .persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC));

    /** The gathering point marked on a Rift Knot (item/RiftKnotItem). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> KNOT_MARK =
            DATA_COMPONENTS.registerComponentType("knot_mark", builder -> builder
                    .persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC));

    /** Where an Attuning Fork started its link: the sender clicked first (item/AttuningForkItem). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> FORK_FROM =
            DATA_COMPONENTS.registerComponentType("fork_from", builder -> builder
                    .persistent(GlobalPos.CODEC)
                    .networkSynchronized(GlobalPos.STREAM_CODEC));

    /** What a vessel holds: one essence and how much (essence/VesselContents). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.lealex.alchyforbidden.essence.VesselContents>> VESSEL_CONTENTS =
            DATA_COMPONENTS.registerComponentType("vessel", builder -> builder
                    .persistent(com.lealex.alchyforbidden.essence.VesselContents.CODEC)
                    .networkSynchronized(com.lealex.alchyforbidden.essence.VesselContents.STREAM_CODEC));

    /** What the Inscribing Bier has carved into a player's body (essence/Inscriptions): kept through death, synced to them. */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<java.util.List<String>>> INSCRIPTIONS = ATTACHMENT_TYPES.register("inscriptions",
            () -> net.neoforged.neoforge.attachment.AttachmentType.<java.util.List<String>>builder(() -> java.util.List.of())
                    .serialize(com.mojang.serialization.Codec.STRING.listOf().fieldOf("inscriptions"))
                    .copyOnDeath()
                    .sync(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.apply(net.minecraft.network.codec.ByteBufCodecs.list())).build());
    /** The spell rings a player wears, in order (essence/Spells: "tier|spell|gem,gem...", "" for an empty slot): kept through death, synced. */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<java.util.List<String>>> WORN_RINGS = ATTACHMENT_TYPES.register("worn_rings",
            () -> net.neoforged.neoforge.attachment.AttachmentType.<java.util.List<String>>builder(() -> java.util.List.of())
                    .serialize(com.mojang.serialization.Codec.STRING.listOf().fieldOf("rings")).copyOnDeath().sync(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.apply(net.minecraft.network.codec.ByteBufCodecs.list())).build());
    /** How many rings a player can wear (2 + the highest Binding Circle tier they used; 0 before any). */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Integer>> SOUL_SLOTS = ATTACHMENT_TYPES.register("soul_slots",
            () -> net.neoforged.neoforge.attachment.AttachmentType.builder(() -> 0)
                    .serialize(com.mojang.serialization.Codec.INT.fieldOf("count")).copyOnDeath().sync(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());
    /** When each spell can be cast again: "id=game time". */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<java.util.List<String>>> SPELL_COOLDOWNS = ATTACHMENT_TYPES.register("spell_cooldowns",
            () -> net.neoforged.neoforge.attachment.AttachmentType.<java.util.List<String>>builder(() -> java.util.List.of()).sync(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.apply(net.minecraft.network.codec.ByteBufCodecs.list())).build());

    /** What a spell ring holds: its spell and the gems in its sockets (essence/RingContents). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.lealex.alchyforbidden.essence.RingContents>> RING_CONTENTS =
            DATA_COMPONENTS.registerComponentType("ring", builder -> builder
                    .persistent(com.lealex.alchyforbidden.essence.RingContents.CODEC)
                    .networkSynchronized(com.lealex.alchyforbidden.essence.RingContents.STREAM_CODEC));

    /** The Wrath, Sorrow and Corruption a body carved with an Essence Reserve holds (essence/Inscriptions). */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<java.util.List<Integer>>> ESSENCE_RESERVE = ATTACHMENT_TYPES.register("essence_reserve",
            () -> net.neoforged.neoforge.attachment.AttachmentType.<java.util.List<Integer>>builder(() -> java.util.List.of(0, 0, 0))
                    .serialize(com.mojang.serialization.Codec.INT.listOf().fieldOf("essences"))
                    .copyOnDeath()
                    .sync(net.minecraft.network.codec.ByteBufCodecs.VAR_INT.apply(net.minecraft.network.codec.ByteBufCodecs.list())).build());

    /** The Inscribing Bier a player lies on, [x, y, z, head facing], empty when not (essence/BierRest): synced, not saved. */
    /** How far a body has been opened for carvings (0..4: 3, 6, 9, 12, 15 carvings; essence/Inscriptions.capacity). */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Integer>> CARVING_LEVEL = ATTACHMENT_TYPES.register("carving_level",
            () -> net.neoforged.neoforge.attachment.AttachmentType.builder(() -> 0)
                    .serialize(com.mojang.serialization.Codec.INT.fieldOf("level")).copyOnDeath().sync(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());
    /** A player chanting at a multiblock (essence/Chants): anchor x, y, z, start, ticks. Synced, not saved. */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<java.util.List<Integer>>> CHANTING = ATTACHMENT_TYPES.register("chanting",
            () -> net.neoforged.neoforge.attachment.AttachmentType.<java.util.List<Integer>>builder(() -> java.util.List.of())
                    .sync(net.minecraft.network.codec.ByteBufCodecs.VAR_INT.apply(net.minecraft.network.codec.ByteBufCodecs.list())).build());
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<java.util.List<Integer>>> RESTING = ATTACHMENT_TYPES.register("resting",
            () -> net.neoforged.neoforge.attachment.AttachmentType.<java.util.List<Integer>>builder(() -> java.util.List.of())
                    .sync(net.minecraft.network.codec.ByteBufCodecs.VAR_INT.apply(net.minecraft.network.codec.ByteBufCodecs.list())).build());

    // ---- The vessels for Wrath and Sorrow (each made by a rite of its tier) ----

    public static final DeferredItem<com.lealex.alchyforbidden.item.VesselItem> CRACKED_VESSEL = ITEMS.registerItem("cracked_vessel",
            p -> new com.lealex.alchyforbidden.item.VesselItem(com.lealex.alchyforbidden.Config::crackedVessel, p), p -> p.stacksTo(1));
    public static final DeferredItem<com.lealex.alchyforbidden.item.VesselItem> BOUND_VESSEL = ITEMS.registerItem("bound_vessel",
            p -> new com.lealex.alchyforbidden.item.VesselItem(com.lealex.alchyforbidden.Config::boundVessel, p), p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.lealex.alchyforbidden.item.VesselItem> RIFT_VESSEL = ITEMS.registerItem("rift_vessel",
            p -> new com.lealex.alchyforbidden.item.VesselItem(com.lealex.alchyforbidden.Config::riftVessel, p), p -> p.stacksTo(1).rarity(Rarity.RARE));


    // ---- The altar ----

    /** The altar's core: a block of deepslate the mage carved by hand, with a socket on top for the eye. */
    public static final DeferredBlock<AltarBlock> ALTAR = BLOCKS.registerBlock("altar", AltarBlock::new,
            p -> p.mapColor(MapColor.DEEPSLATE)
                    .strength(4.0f, 9.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE_TILES)
                    .noOcclusion() // its model is a carved shape, not a full cube
                    .lightLevel(state -> 4));
    public static final DeferredItem<BlockItem> ALTAR_ITEM = ITEMS.registerSimpleBlockItem("altar", ALTAR);

    /** The Echo Resonator's core: the carved block with the rift in it that throws the pearls (made by an altar rite). */
    public static final DeferredBlock<com.lealex.alchyforbidden.block.ResonatorBlock> RESONATOR = BLOCKS.registerBlock("resonator",
            com.lealex.alchyforbidden.block.ResonatorBlock::new,
            p -> p.mapColor(MapColor.DEEPSLATE).strength(4.0f, 9.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE_TILES).lightLevel(state -> 5));
    public static final DeferredItem<BlockItem> RESONATOR_ITEM = ITEMS.registerSimpleBlockItem("resonator", RESONATOR);

    /** The Killing Pit's core: under the middle of its spiked floor (made by an altar rite). */
    public static final DeferredBlock<com.lealex.alchyforbidden.block.KillingPitBlock> KILLING_PIT = BLOCKS.registerBlock("killing_pit",
            com.lealex.alchyforbidden.block.KillingPitBlock::new,
            p -> p.mapColor(MapColor.DEEPSLATE).strength(4.0f, 9.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE_TILES).lightLevel(state -> 3));
    /** Binds a spawner: its mobs fold into one condensed mob (essence/MobStacks). */
    public static final DeferredItem<com.lealex.alchyforbidden.item.RiftKnotItem> RIFT_KNOT = ITEMS.registerItem("rift_knot",
            com.lealex.alchyforbidden.item.RiftKnotItem::new, p -> p.stacksTo(1).rarity(net.minecraft.world.item.Rarity.RARE));
    public static final DeferredItem<BlockItem> KILLING_PIT_ITEM = ITEMS.registerSimpleBlockItem("killing_pit", KILLING_PIT);
    /** The Echo Obelisk: a pillar two blocks high carrying essences one link further (essence/Relays). */
    public static final DeferredBlock<com.lealex.alchyforbidden.block.RelayBlock> ECHO_OBELISK = BLOCKS.registerBlock("echo_obelisk",
            com.lealex.alchyforbidden.block.RelayBlock::new,
            p -> p.mapColor(MapColor.DEEPSLATE).strength(3.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_BRICKS).noOcclusion()
                    .lightLevel(state -> state.getValue(com.lealex.alchyforbidden.block.RelayBlock.HALF)
                            == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER ? 9 : 2));
    public static final DeferredItem<BlockItem> ECHO_OBELISK_ITEM = ITEMS.registerSimpleBlockItem("echo_obelisk", ECHO_OBELISK);
    /** Links senders to relays and altars (item/AttuningForkItem). */
    public static final DeferredItem<com.lealex.alchyforbidden.item.AttuningForkItem> ATTUNING_FORK = ITEMS.registerItem("attuning_fork",
            com.lealex.alchyforbidden.item.AttuningForkItem::new, p -> p.stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON));

    // ---- Spell rings and their gems (altar rites) ----
    public static final DeferredItem<com.lealex.alchyforbidden.item.SpellRingItem> SPELL_RING_1 = ITEMS.registerItem("spell_ring_1",
            p -> new com.lealex.alchyforbidden.item.SpellRingItem(1, p), p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.lealex.alchyforbidden.item.SpellRingItem> SPELL_RING_2 = ITEMS.registerItem("spell_ring_2",
            p -> new com.lealex.alchyforbidden.item.SpellRingItem(2, p), p -> p.stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<com.lealex.alchyforbidden.item.SpellRingItem> SPELL_RING_3 = ITEMS.registerItem("spell_ring_3",
            p -> new com.lealex.alchyforbidden.item.SpellRingItem(3, p), p -> p.stacksTo(1).rarity(Rarity.EPIC));
    public static final java.util.Map<com.lealex.alchyforbidden.essence.SpellGem, DeferredItem<com.lealex.alchyforbidden.item.SpellGemItem>> GEMS = gems();

    private static java.util.Map<com.lealex.alchyforbidden.essence.SpellGem, DeferredItem<com.lealex.alchyforbidden.item.SpellGemItem>> gems() {
        java.util.Map<com.lealex.alchyforbidden.essence.SpellGem, DeferredItem<com.lealex.alchyforbidden.item.SpellGemItem>> out =
                new java.util.EnumMap<>(com.lealex.alchyforbidden.essence.SpellGem.class);
        for (com.lealex.alchyforbidden.essence.SpellGem gem : com.lealex.alchyforbidden.essence.SpellGem.values()) {
            out.put(gem, ITEMS.registerItem("gem_" + gem.id(), p -> new com.lealex.alchyforbidden.item.SpellGemItem(gem, p), p -> p.stacksTo(16).rarity(Rarity.UNCOMMON)));
        }
        return out;
    }

    // ---- Familiars: the Rift Maw, its rings and lures, the creatures of the rift ----

    public static final DeferredRegister<net.minecraft.world.entity.EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AlchyForbidden.MODID);
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.lealex.alchyforbidden.familiar.SculkCrawler>> SCULK_CRAWLER =
            ENTITY_TYPES.register("sculk_crawler", id -> net.minecraft.world.entity.EntityType.Builder
                    .of(com.lealex.alchyforbidden.familiar.SculkCrawler::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.95F, 0.7F).eyeHeight(0.5F).clientTrackingRange(10)
                    .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.lealex.alchyforbidden.familiar.VeilWisp>> VEIL_WISP =
            ENTITY_TYPES.register("veil_wisp", id -> net.minecraft.world.entity.EntityType.Builder
                    .of(com.lealex.alchyforbidden.familiar.VeilWisp::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.6F, 1.5F).eyeHeight(1.2F).clientTrackingRange(10)
                    .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.lealex.alchyforbidden.familiar.ShardHound>> SHARD_HOUND =
            ENTITY_TYPES.register("shard_hound", id -> net.minecraft.world.entity.EntityType.Builder
                    .of(com.lealex.alchyforbidden.familiar.ShardHound::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.8F, 0.95F).eyeHeight(0.8F).clientTrackingRange(10)
                    .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.lealex.alchyforbidden.familiar.ShackledBrute>> SHACKLED_BRUTE =
            ENTITY_TYPES.register("shackled_brute", id -> net.minecraft.world.entity.EntityType.Builder
                    .of(com.lealex.alchyforbidden.familiar.ShackledBrute::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.95F, 2.2F).eyeHeight(1.7F).clientTrackingRange(10)
                    .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.lealex.alchyforbidden.familiar.HollowStalker>> HOLLOW_STALKER =
            ENTITY_TYPES.register("hollow_stalker", id -> net.minecraft.world.entity.EntityType.Builder
                    .of(com.lealex.alchyforbidden.familiar.HollowStalker::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.7F, 2.1F).eyeHeight(1.9F).clientTrackingRange(10)
                    .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.lealex.alchyforbidden.familiar.AbyssalKnight>> ABYSSAL_KNIGHT =
            ENTITY_TYPES.register("abyssal_knight", id -> net.minecraft.world.entity.EntityType.Builder
                    .of(com.lealex.alchyforbidden.familiar.AbyssalKnight::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.95F, 3.0F).eyeHeight(2.6F).clientTrackingRange(12)
                    .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.lealex.alchyforbidden.familiar.TheChoir>> THE_CHOIR =
            ENTITY_TYPES.register("the_choir", id -> net.minecraft.world.entity.EntityType.Builder
                    .of(com.lealex.alchyforbidden.familiar.TheChoir::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(0.95F, 1.4F).eyeHeight(0.8F).clientTrackingRange(12)
                    .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.lealex.alchyforbidden.familiar.WeepingChandelier>> WEEPING_CHANDELIER =
            ENTITY_TYPES.register("weeping_chandelier", id -> net.minecraft.world.entity.EntityType.Builder
                    .of(com.lealex.alchyforbidden.familiar.WeepingChandelier::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(2.6F, 2.4F).eyeHeight(1.2F).clientTrackingRange(16).updateInterval(1).fireImmune()
                    .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.lealex.alchyforbidden.familiar.VoidGolem>> VOID_GOLEM =
            ENTITY_TYPES.register("void_golem", id -> net.minecraft.world.entity.EntityType.Builder
                    .of(com.lealex.alchyforbidden.familiar.VoidGolem::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(1.5F, 4.2F).eyeHeight(3.6F).clientTrackingRange(16).updateInterval(1).fireImmune()
                    .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.lealex.alchyforbidden.familiar.BoneDragon>> BONE_DRAGON =
            ENTITY_TYPES.register("bone_dragon", id -> net.minecraft.world.entity.EntityType.Builder
                    .of(com.lealex.alchyforbidden.familiar.BoneDragon::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(3.0F, 2.4F).eyeHeight(1.8F).clientTrackingRange(16).updateInterval(1).fireImmune()
                    .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, id)));
    public static final DeferredHolder<net.minecraft.world.entity.EntityType<?>, net.minecraft.world.entity.EntityType<com.lealex.alchyforbidden.familiar.SeveredSeraph>> SEVERED_SERAPH =
            ENTITY_TYPES.register("severed_seraph", id -> net.minecraft.world.entity.EntityType.Builder
                    .of(com.lealex.alchyforbidden.familiar.SeveredSeraph::new, net.minecraft.world.entity.MobCategory.MISC)
                    .sized(1.2F, 2.6F).eyeHeight(2.2F).clientTrackingRange(16).updateInterval(1)
                    .build(net.minecraft.resources.ResourceKey.create(Registries.ENTITY_TYPE, id)));

    /** The Rift Maw's core, in the middle of its pit (made by an altar rite). */
    public static final DeferredBlock<com.lealex.alchyforbidden.block.RiftMawBlock> RIFT_MAW = BLOCKS.registerBlock("rift_maw",
            com.lealex.alchyforbidden.block.RiftMawBlock::new,
            p -> p.mapColor(MapColor.DEEPSLATE).strength(4.0f, 9.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_TILES).lightLevel(state -> 7));
    public static final DeferredItem<BlockItem> RIFT_MAW_ITEM = ITEMS.registerSimpleBlockItem("rift_maw", RIFT_MAW);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.lealex.alchyforbidden.block.entity.RiftMawBlockEntity>> RIFT_MAW_BE =
            BLOCK_ENTITY_TYPES.register("rift_maw", () -> new BlockEntityType<>(com.lealex.alchyforbidden.block.entity.RiftMawBlockEntity::new, false, RIFT_MAW.get()));

    public static final DeferredItem<com.lealex.alchyforbidden.item.FamiliarRingItem> FAMILIAR_RING_1 = ITEMS.registerItem("familiar_ring_1",
            p -> new com.lealex.alchyforbidden.item.FamiliarRingItem(1, p), p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.lealex.alchyforbidden.item.FamiliarRingItem> FAMILIAR_RING_2 = ITEMS.registerItem("familiar_ring_2",
            p -> new com.lealex.alchyforbidden.item.FamiliarRingItem(2, p), p -> p.stacksTo(1).rarity(Rarity.RARE));
    public static final DeferredItem<com.lealex.alchyforbidden.item.FamiliarRingItem> FAMILIAR_RING_3 = ITEMS.registerItem("familiar_ring_3",
            p -> new com.lealex.alchyforbidden.item.FamiliarRingItem(3, p), p -> p.stacksTo(1).rarity(Rarity.EPIC));
    public static final DeferredItem<com.lealex.alchyforbidden.item.FamiliarRingItem> FAMILIAR_RING_4 = ITEMS.registerItem("familiar_ring_4",
            p -> new com.lealex.alchyforbidden.item.FamiliarRingItem(4, p), p -> p.stacksTo(1).rarity(Rarity.EPIC));
    /** One lure per creature (item id = <creature>_lure). */
    public static final java.util.Map<com.lealex.alchyforbidden.familiar.RiftCreature, DeferredItem<com.lealex.alchyforbidden.item.RiftLureItem>> LURES = lures();

    private static java.util.Map<com.lealex.alchyforbidden.familiar.RiftCreature, DeferredItem<com.lealex.alchyforbidden.item.RiftLureItem>> lures() {
        java.util.Map<com.lealex.alchyforbidden.familiar.RiftCreature, DeferredItem<com.lealex.alchyforbidden.item.RiftLureItem>> out =
                new java.util.EnumMap<>(com.lealex.alchyforbidden.familiar.RiftCreature.class);
        for (com.lealex.alchyforbidden.familiar.RiftCreature creature : com.lealex.alchyforbidden.familiar.RiftCreature.values()) {
            out.put(creature, ITEMS.registerItem(creature.id() + "_lure", p -> new com.lealex.alchyforbidden.item.RiftLureItem(creature, p),
                    p -> p.stacksTo(16).rarity(creature.boss() ? Rarity.EPIC : Rarity.UNCOMMON)));
        }
        return out;
    }

    /** Every creature of the rift written down, and the familiars its reader wears (item/RiftBestiaryItem). */
    public static final DeferredItem<com.lealex.alchyforbidden.item.RiftBestiaryItem> RIFT_BESTIARY = ITEMS.registerItem("rift_bestiary",
            com.lealex.alchyforbidden.item.RiftBestiaryItem::new, p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));

    /** What an altered creature slain in the arena leaves: for the corrupting ritual and the finest treats. */
    public static final DeferredItem<Item> RIFT_RESIDUE = ITEMS.registerItem("rift_residue", Item::new, p -> p.stacksTo(64).rarity(Rarity.UNCOMMON));
    /** What pays for an affix filter at the maw (a tier 3 rite, dear on purpose). */
    public static final DeferredItem<Item> VOID_PRISM = ITEMS.registerItem("void_prism", Item::new, p -> p.stacksTo(64).rarity(Rarity.EPIC));

    /** Ritual components (item/ComponentItem): the Weeping Fountain weeps some, the altar makes the others. */
    public static final DeferredItem<com.lealex.alchyforbidden.item.ComponentItem> WRATH_EMBER = ITEMS.registerItem("wrath_ember",
            p -> new com.lealex.alchyforbidden.item.ComponentItem(com.lealex.alchyforbidden.item.ComponentItem.Source.FOUNTAIN, 1, p), p -> p.stacksTo(64).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.lealex.alchyforbidden.item.ComponentItem> SORROW_TEAR = ITEMS.registerItem("sorrow_tear",
            p -> new com.lealex.alchyforbidden.item.ComponentItem(com.lealex.alchyforbidden.item.ComponentItem.Source.FOUNTAIN, 1, p), p -> p.stacksTo(64).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.lealex.alchyforbidden.item.ComponentItem> CORRUPTION_SPORE = ITEMS.registerItem("corruption_spore",
            p -> new com.lealex.alchyforbidden.item.ComponentItem(com.lealex.alchyforbidden.item.ComponentItem.Source.FOUNTAIN, 1, p), p -> p.stacksTo(64).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.lealex.alchyforbidden.item.ComponentItem> BONE_INK = ITEMS.registerItem("bone_ink",
            p -> new com.lealex.alchyforbidden.item.ComponentItem(com.lealex.alchyforbidden.item.ComponentItem.Source.ALTAR, 1, p), p -> p.stacksTo(64).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.lealex.alchyforbidden.item.ComponentItem> RIFT_ALLOY = ITEMS.registerItem("rift_alloy",
            p -> new com.lealex.alchyforbidden.item.ComponentItem(com.lealex.alchyforbidden.item.ComponentItem.Source.ALTAR, 2, p), p -> p.stacksTo(64).rarity(Rarity.RARE));
    public static final DeferredItem<com.lealex.alchyforbidden.item.ComponentItem> ECHO_INK = ITEMS.registerItem("echo_ink",
            p -> new com.lealex.alchyforbidden.item.ComponentItem(com.lealex.alchyforbidden.item.ComponentItem.Source.ALTAR, 2, p), p -> p.stacksTo(64).rarity(Rarity.RARE));
    public static final DeferredItem<com.lealex.alchyforbidden.item.ComponentItem> MOURNING_PEARL = ITEMS.registerItem("mourning_pearl",
            p -> new com.lealex.alchyforbidden.item.ComponentItem(com.lealex.alchyforbidden.item.ComponentItem.Source.FOUNTAIN, 2, p), p -> p.stacksTo(64).rarity(Rarity.RARE));
    public static final DeferredItem<com.lealex.alchyforbidden.item.ComponentItem> RIFT_SILK = ITEMS.registerItem("rift_silk",
            p -> new com.lealex.alchyforbidden.item.ComponentItem(com.lealex.alchyforbidden.item.ComponentItem.Source.FOUNTAIN, 3, p), p -> p.stacksTo(64).rarity(Rarity.EPIC));
    public static final DeferredItem<com.lealex.alchyforbidden.item.ComponentItem> VOID_INK = ITEMS.registerItem("void_ink",
            p -> new com.lealex.alchyforbidden.item.ComponentItem(com.lealex.alchyforbidden.item.ComponentItem.Source.ALTAR, 3, p), p -> p.stacksTo(64).rarity(Rarity.EPIC));
    public static final DeferredItem<com.lealex.alchyforbidden.item.ComponentItem> ABYSSAL_CORE = ITEMS.registerItem("abyssal_core",
            p -> new com.lealex.alchyforbidden.item.ComponentItem(com.lealex.alchyforbidden.item.ComponentItem.Source.ALTAR, 3, p), p -> p.stacksTo(64).rarity(Rarity.EPIC));

    /** Treats for familiars (altar rites): experience when fed. */
    public static final DeferredItem<com.lealex.alchyforbidden.item.RiftTreatItem> SCULK_MORSEL = ITEMS.registerItem("sculk_morsel",
            p -> new com.lealex.alchyforbidden.item.RiftTreatItem(60, p), p -> p.stacksTo(64));
    public static final DeferredItem<com.lealex.alchyforbidden.item.RiftTreatItem> ECHO_HEART = ITEMS.registerItem("echo_heart",
            p -> new com.lealex.alchyforbidden.item.RiftTreatItem(300, p), p -> p.stacksTo(64).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<com.lealex.alchyforbidden.item.RiftTreatItem> VOID_DELICACY = ITEMS.registerItem("void_delicacy",
            p -> new com.lealex.alchyforbidden.item.RiftTreatItem(1500, p), p -> p.stacksTo(16).rarity(Rarity.RARE));

    public static net.minecraft.world.item.Item familiarRingOfTier(int tier) {
        return (tier >= 4 ? FAMILIAR_RING_4 : tier == 3 ? FAMILIAR_RING_3 : tier == 2 ? FAMILIAR_RING_2 : FAMILIAR_RING_1).get();
    }

    /** What a familiar ring holds: the creature bound in it, its grade, level, experience, crack (familiar/FamiliarData). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.lealex.alchyforbidden.familiar.FamiliarData>> FAMILIAR =
            DATA_COMPONENTS.registerComponentType("familiar", builder -> builder
                    .persistent(com.lealex.alchyforbidden.familiar.FamiliarData.CODEC)
                    .networkSynchronized(com.lealex.alchyforbidden.familiar.FamiliarData.STREAM_CODEC));
    /** The familiar rings worn on the left hand, in order ("tier|creature|grade|level|xp|cracked until", "" empty): kept through death, synced. */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<java.util.List<String>>> WORN_FAMILIARS = ATTACHMENT_TYPES.register("worn_familiars",
            () -> net.neoforged.neoforge.attachment.AttachmentType.<java.util.List<String>>builder(() -> java.util.List.of())
                    .serialize(com.mojang.serialization.Codec.STRING.listOf().fieldOf("rings")).copyOnDeath().sync(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8.apply(net.minecraft.network.codec.ByteBufCodecs.list())).build());
    /** How many familiar rings the left hand wears (1 to start). */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<Integer>> FAMILIAR_SLOTS = ATTACHMENT_TYPES.register("familiar_slots",
            () -> net.neoforged.neoforge.attachment.AttachmentType.builder(() -> 1)
                    .serialize(com.mojang.serialization.Codec.INT.fieldOf("count")).copyOnDeath().sync(net.minecraft.network.codec.ByteBufCodecs.VAR_INT).build());
    /** The ring slots whose familiar is out now (synced, not saved). */
    public static final java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<java.util.List<Integer>>> FAMILIARS_OUT = ATTACHMENT_TYPES.register("familiars_out",
            () -> net.neoforged.neoforge.attachment.AttachmentType.<java.util.List<Integer>>builder(() -> java.util.List.of())
                    .sync(net.minecraft.network.codec.ByteBufCodecs.VAR_INT.apply(net.minecraft.network.codec.ByteBufCodecs.list())).build());

    public static net.minecraft.world.item.Item ringOfTier(int tier) {
        return (tier >= 3 ? SPELL_RING_3 : tier == 2 ? SPELL_RING_2 : SPELL_RING_1).get();
    }
    /** The Lament Well's core: at the bottom of its shaft (made by an altar rite). */
    public static final DeferredBlock<com.lealex.alchyforbidden.block.LamentWellBlock> LAMENT_WELL = BLOCKS.registerBlock("lament_well",
            com.lealex.alchyforbidden.block.LamentWellBlock::new,
            p -> p.mapColor(MapColor.DEEPSLATE).strength(4.0f, 9.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE_TILES));
    public static final DeferredItem<BlockItem> LAMENT_WELL_ITEM = ITEMS.registerSimpleBlockItem("lament_well", LAMENT_WELL);
    /** The Weeping Fountain's core: in the middle of its basin (made by an altar rite). */
    public static final DeferredBlock<com.lealex.alchyforbidden.block.WeepingFountainBlock> WEEPING_FOUNTAIN = BLOCKS.registerBlock("weeping_fountain",
            com.lealex.alchyforbidden.block.WeepingFountainBlock::new,
            p -> p.mapColor(MapColor.DEEPSLATE).strength(4.0f, 9.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_TILES).lightLevel(state -> 5));
    public static final DeferredItem<BlockItem> WEEPING_FOUNTAIN_ITEM = ITEMS.registerSimpleBlockItem("weeping_fountain", WEEPING_FOUNTAIN);
    /** The Binding Circle's core: in the middle of its circle (made by an altar rite). */
    public static final DeferredBlock<com.lealex.alchyforbidden.block.SoulCircleBlock> SOUL_CIRCLE = BLOCKS.registerBlock("binding_circle",
            com.lealex.alchyforbidden.block.SoulCircleBlock::new,
            p -> p.mapColor(MapColor.DEEPSLATE).strength(4.0f, 9.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_TILES).lightLevel(state -> 6));
    public static final DeferredItem<BlockItem> SOUL_CIRCLE_ITEM = ITEMS.registerSimpleBlockItem("binding_circle", SOUL_CIRCLE);
    /** The Essence Infuser's core: in the middle of its floor (made by an altar rite). */
    public static final DeferredBlock<com.lealex.alchyforbidden.block.EssenceInfuserBlock> ESSENCE_INFUSER = BLOCKS.registerBlock("essence_infuser",
            com.lealex.alchyforbidden.block.EssenceInfuserBlock::new,
            p -> p.mapColor(MapColor.DEEPSLATE).strength(4.0f, 9.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_TILES).lightLevel(state -> 6));
    public static final DeferredItem<BlockItem> ESSENCE_INFUSER_ITEM = ITEMS.registerSimpleBlockItem("essence_infuser", ESSENCE_INFUSER);
    /** The Inscribing Bier's core: the middle of its slab (made by an altar rite). */
    public static final DeferredBlock<com.lealex.alchyforbidden.block.InscribingBierBlock> INSCRIBING_BIER = BLOCKS.registerBlock("inscribing_bier",
            com.lealex.alchyforbidden.block.InscribingBierBlock::new,
            p -> p.mapColor(MapColor.DEEPSLATE).strength(4.0f, 9.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_TILES).lightLevel(state -> 4));
    public static final DeferredItem<BlockItem> INSCRIBING_BIER_ITEM = ITEMS.registerSimpleBlockItem("inscribing_bier", INSCRIBING_BIER);

    // The looks the altar's parts take (disguises of AlchyX shells). No items: the player never places these, the
    // first tier wears the flagstone and the rift grows the rest.

    /** The first tier's floor: the player's deepslate, carved over with the mage's lines. */
    public static final DeferredBlock<Block> ALTAR_FLAGSTONE = look("altar_flagstone", 0);
    /** The second tier's ring: ancient city stone with sculk running through it. */
    public static final DeferredBlock<Block> VEINED_DEEPSLATE = look("veined_deepslate", 0);
    /** The second tier's pillars: the ancient builders' column, cracked open by sculk. */
    public static final DeferredBlock<Block> WARDEN_PILLAR = look("warden_pillar", 0);
    /** The altar's middle as it rises, one block higher at each grown tier: a carved column with the socket on top. */
    public static final DeferredBlock<Block> ALTAR_PEDESTAL = look("altar_pedestal", 3);
    /** The Killing Pit's floor: deepslate with rift-black spikes standing out of it. */
    public static final DeferredBlock<Block> SPIKED_FLOOR = look("spiked_floor", 0);
    /** The Killing Pit's windows: glass gone dark violet in the rift, cracked, see-through. */
    public static final DeferredBlock<net.minecraft.world.level.block.TransparentBlock> RIFT_GLASS = BLOCKS.registerBlock("rift_glass",
            net.minecraft.world.level.block.TransparentBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(1.0f, 6.0f).sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false).isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false));
    /** The steps that lead up to the risen middle (the flagstone's carved stone, as stairs). */
    public static final DeferredBlock<StairBlock> ALTAR_STAIRS = BLOCKS.registerBlock("altar_stairs",
            p -> new StairBlock(ALTAR_FLAGSTONE.get().defaultBlockState(), p),
            p -> p.mapColor(MapColor.DEEPSLATE).strength(3.5f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE_TILES));
    /**
     * The offering stands round the altar's middle (the candles of the first tier become them as it forms; the third
     * tier grows four more on the outer ring's corners): a short carved stand with a candle stub, items laid on them
     * float over the plate. Its own model: not a full block.
     */
    public static final DeferredBlock<Block> OFFERING_STAND = BLOCKS.registerBlock("offering_stand", Block::new,
            p -> p.mapColor(MapColor.DEEPSLATE).strength(3.5f, 6.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE_BRICKS).noOcclusion().lightLevel(state -> 5));
    /** The carved circle across the first tier's floor: its middle, and a side and a corner turned four ways. */
    public static final DeferredBlock<Block> CIRCLE_CENTER = look("circle_center", 4);
    public static final DeferredBlock<FacingLookBlock> CIRCLE_SIDE = facingLook("circle_side", 2);
    public static final DeferredBlock<FacingLookBlock> CIRCLE_CORNER = facingLook("circle_corner", 2);
    /** The pillars' foot and crown (the shaft between is the warden pillar). */
    public static final DeferredBlock<Block> PILLAR_BASE = look("pillar_base", 0);
    public static final DeferredBlock<Block> PILLAR_CAPITAL = look("pillar_capital", 3);

    private static DeferredBlock<FacingLookBlock> facingLook(String name, int light) {
        return BLOCKS.registerBlock(name, FacingLookBlock::new, p -> p.mapColor(MapColor.DEEPSLATE)
                .strength(3.5f, 6.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE_TILES)
                .lightLevel(state -> light));
    }

    /** The resonator's sacrificial table, where the dead warden lies: dark slabs with a channel cut along them. */
    public static final DeferredBlock<Block> SACRIFICIAL_SLAB = look("sacrificial_slab", 2);
    /** The third tier's runestones: hand-carved by the mage, the rune glowing violet. */
    public static final DeferredBlock<Block> MAGE_RUNE = look("mage_rune", 6);
    /** The third tier's lanterns on the pillars: a deepslate cage around a shard of the rift. */
    public static final DeferredBlock<Block> RIFT_LANTERN = look("rift_lantern", 12);

    private static DeferredBlock<Block> look(String name, int light) {
        return BLOCKS.registerBlock(name, Block::new, p -> p.mapColor(MapColor.DEEPSLATE)
                .strength(3.5f, 6.0f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE_BRICKS)
                .lightLevel(state -> light));
    }

    // ---- The eye ----

    /** The eye: crafted, it looks where it wants the player to go and says what it wants. */
    public static final DeferredItem<RiftEyeItem> RIFT_EYE = ITEMS.registerItem("rift_eye", RiftEyeItem::new,
            p -> p.stacksTo(1).rarity(Rarity.RARE));

    public static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, AlchyForbidden.MODID);
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<com.lealex.alchyforbidden.menu.AltarMenu>> ALTAR_MENU =
            MENU_TYPES.register("altar", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.lealex.alchyforbidden.menu.AltarMenu::new));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.lealex.alchyforbidden.block.entity.ResonatorBlockEntity>> RESONATOR_BE =
            BLOCK_ENTITY_TYPES.register("resonator", () -> new BlockEntityType<>(com.lealex.alchyforbidden.block.entity.ResonatorBlockEntity::new, false, RESONATOR.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.lealex.alchyforbidden.block.entity.KillingPitBlockEntity>> KILLING_PIT_BE =
            BLOCK_ENTITY_TYPES.register("killing_pit", () -> new BlockEntityType<>(com.lealex.alchyforbidden.block.entity.KillingPitBlockEntity::new, false, KILLING_PIT.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.lealex.alchyforbidden.block.entity.RelayBlockEntity>> RELAY_BE =
            BLOCK_ENTITY_TYPES.register("echo_obelisk", () -> new BlockEntityType<>(com.lealex.alchyforbidden.block.entity.RelayBlockEntity::new, false, ECHO_OBELISK.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.lealex.alchyforbidden.block.entity.SoulCircleBlockEntity>> SOUL_CIRCLE_BE =
            BLOCK_ENTITY_TYPES.register("binding_circle", () -> new BlockEntityType<>(com.lealex.alchyforbidden.block.entity.SoulCircleBlockEntity::new, false, SOUL_CIRCLE.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.lealex.alchyforbidden.block.entity.EssenceInfuserBlockEntity>> ESSENCE_INFUSER_BE =
            BLOCK_ENTITY_TYPES.register("essence_infuser", () -> new BlockEntityType<>(com.lealex.alchyforbidden.block.entity.EssenceInfuserBlockEntity::new, false, ESSENCE_INFUSER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.lealex.alchyforbidden.block.entity.WeepingFountainBlockEntity>> WEEPING_FOUNTAIN_BE =
            BLOCK_ENTITY_TYPES.register("weeping_fountain", () -> new BlockEntityType<>(com.lealex.alchyforbidden.block.entity.WeepingFountainBlockEntity::new, false, WEEPING_FOUNTAIN.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.lealex.alchyforbidden.block.entity.InscribingBierBlockEntity>> INSCRIBING_BIER_BE =
            BLOCK_ENTITY_TYPES.register("inscribing_bier", () -> new BlockEntityType<>(com.lealex.alchyforbidden.block.entity.InscribingBierBlockEntity::new, false, INSCRIBING_BIER.get()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.lealex.alchyforbidden.block.entity.LamentWellBlockEntity>> LAMENT_WELL_BE =
            BLOCK_ENTITY_TYPES.register("lament_well", () -> new BlockEntityType<>(com.lealex.alchyforbidden.block.entity.LamentWellBlockEntity::new, false, LAMENT_WELL.get()));

    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<com.lealex.alchyforbidden.menu.ResonatorMenu>> RESONATOR_MENU =
            MENU_TYPES.register("resonator", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.lealex.alchyforbidden.menu.ResonatorMenu::new));
    /** The Killing Pit's and the Lament Well's screen. */
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<com.lealex.alchyforbidden.menu.SourceMenu>> SOURCE_MENU =
            MENU_TYPES.register("source", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.lealex.alchyforbidden.menu.SourceMenu::new));
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<com.lealex.alchyforbidden.menu.InscribingMenu>> INSCRIBING_MENU =
            MENU_TYPES.register("inscribing", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.lealex.alchyforbidden.menu.InscribingMenu::new));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AltarBlockEntity>> ALTAR_BE =
            BLOCK_ENTITY_TYPES.register("altar", () -> new BlockEntityType<>(AltarBlockEntity::new, false, ALTAR.get()));

    // ---- Particles (client/RiftMote draws them, sprites from tools/textures.py) ----

    /** A violet mote of the rift: drawn into the altar while it gathers itself, and along the eye's gaze. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RIFT_MOTE =
            PARTICLE_TYPES.register("rift_mote", () -> new SimpleParticleType(false));
    /** Wrath on its way to an altar: a crimson ember, flickering, drifting up. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WRATH_MOTE =
            PARTICLE_TYPES.register("wrath_mote", () -> new SimpleParticleType(false));
    /** Sorrow on its way: a pale teal tear-shaped wisp, hanging in the air. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SORROW_MOTE =
            PARTICLE_TYPES.register("sorrow_mote", () -> new SimpleParticleType(false));
    /** Sculk Corruption on its way: a dark sculk spore with a cyan heart, sinking a little. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CORRUPTION_MOTE =
            PARTICLE_TYPES.register("corruption_mote", () -> new SimpleParticleType(false));
    /** A teal fleck of sculk, thrown up where the altar grows a block. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SCULK_FLECK =
            PARTICLE_TYPES.register("sculk_fleck", () -> new SimpleParticleType(false));

    // ---- Sounds (assets/alchyforbidden/sounds.json: the game's own recordings, pitched and mixed for the altar) ----

    public static final DeferredHolder<SoundEvent, SoundEvent> ALTAR_OFFER = sound("altar.offer");
    public static final DeferredHolder<SoundEvent, SoundEvent> ALTAR_GATHER = sound("altar.gather");
    public static final DeferredHolder<SoundEvent, SoundEvent> ALTAR_GROW = sound("altar.grow");
    public static final DeferredHolder<SoundEvent, SoundEvent> ALTAR_AWAKEN = sound("altar.awaken");
    public static final DeferredHolder<SoundEvent, SoundEvent> EYE_WHISPER = sound("eye.whisper");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, name)));
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.alchyforbidden"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> RIFT_EYE.get().getDefaultInstance())
                    .displayItems((parameters, output) -> ITEMS.getEntries().forEach(item -> output.accept(item.get())))
                    .build());

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITY_TYPES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);
        MENU_TYPES.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) ->
                {
                    event.put(SCULK_CRAWLER.get(), com.lealex.alchyforbidden.familiar.SculkCrawler.createAttributes().build());
                    event.put(VEIL_WISP.get(), com.lealex.alchyforbidden.familiar.VeilWisp.createAttributes().build());
                    event.put(SHARD_HOUND.get(), com.lealex.alchyforbidden.familiar.ShardHound.createAttributes().build());
                    event.put(SHACKLED_BRUTE.get(), com.lealex.alchyforbidden.familiar.ShackledBrute.createAttributes().build());
                    event.put(HOLLOW_STALKER.get(), com.lealex.alchyforbidden.familiar.HollowStalker.createAttributes().build());
                    event.put(ABYSSAL_KNIGHT.get(), com.lealex.alchyforbidden.familiar.AbyssalKnight.createAttributes().build());
                    event.put(THE_CHOIR.get(), com.lealex.alchyforbidden.familiar.TheChoir.createAttributes().build());
                    event.put(WEEPING_CHANDELIER.get(), com.lealex.alchyforbidden.familiar.WeepingChandelier.createAttributes().build());
                    event.put(VOID_GOLEM.get(), com.lealex.alchyforbidden.familiar.VoidGolem.createAttributes().build());
                    event.put(BONE_DRAGON.get(), com.lealex.alchyforbidden.familiar.BoneDragon.createAttributes().build());
                    event.put(SEVERED_SERAPH.get(), com.lealex.alchyforbidden.familiar.SeveredSeraph.createAttributes().build());
                });
    }
}
