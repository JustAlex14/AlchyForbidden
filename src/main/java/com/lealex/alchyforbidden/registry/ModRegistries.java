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
            () -> net.neoforged.neoforge.attachment.AttachmentType.builder(() -> false).serialize(com.mojang.serialization.Codec.BOOL.fieldOf("knotted")).build());
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

    /** What a vessel holds: one essence and how much (essence/VesselContents). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.lealex.alchyforbidden.essence.VesselContents>> VESSEL_CONTENTS =
            DATA_COMPONENTS.registerComponentType("vessel", builder -> builder
                    .persistent(com.lealex.alchyforbidden.essence.VesselContents.CODEC)
                    .networkSynchronized(com.lealex.alchyforbidden.essence.VesselContents.STREAM_CODEC));

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
    /** The Lament Well's core: at the bottom of its shaft (made by an altar rite). */
    public static final DeferredBlock<com.lealex.alchyforbidden.block.LamentWellBlock> LAMENT_WELL = BLOCKS.registerBlock("lament_well",
            com.lealex.alchyforbidden.block.LamentWellBlock::new,
            p -> p.mapColor(MapColor.DEEPSLATE).strength(4.0f, 9.0f).requiresCorrectToolForDrops()
                    .sound(SoundType.DEEPSLATE_TILES));
    public static final DeferredItem<BlockItem> LAMENT_WELL_ITEM = ITEMS.registerSimpleBlockItem("lament_well", LAMENT_WELL);

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
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.lealex.alchyforbidden.block.entity.LamentWellBlockEntity>> LAMENT_WELL_BE =
            BLOCK_ENTITY_TYPES.register("lament_well", () -> new BlockEntityType<>(com.lealex.alchyforbidden.block.entity.LamentWellBlockEntity::new, false, LAMENT_WELL.get()));

    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<com.lealex.alchyforbidden.menu.ResonatorMenu>> RESONATOR_MENU =
            MENU_TYPES.register("resonator", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.lealex.alchyforbidden.menu.ResonatorMenu::new));
    /** The Killing Pit's and the Lament Well's screen. */
    public static final DeferredHolder<net.minecraft.world.inventory.MenuType<?>, net.minecraft.world.inventory.MenuType<com.lealex.alchyforbidden.menu.SourceMenu>> SOURCE_MENU =
            MENU_TYPES.register("source", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(com.lealex.alchyforbidden.menu.SourceMenu::new));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AltarBlockEntity>> ALTAR_BE =
            BLOCK_ENTITY_TYPES.register("altar", () -> new BlockEntityType<>(AltarBlockEntity::new, false, ALTAR.get()));

    // ---- Particles (client/RiftMote draws them, sprites from tools/textures.py) ----

    /** A violet mote of the rift: drawn into the altar while it gathers itself, and along the eye's gaze. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RIFT_MOTE =
            PARTICLE_TYPES.register("rift_mote", () -> new SimpleParticleType(false));
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
    }
}
