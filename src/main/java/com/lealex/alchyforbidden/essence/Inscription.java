package com.lealex.alchyforbidden.essence;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What the Inscribing Bier can carve into a body (essence/Inscriptions): each belongs to one part, one carving per
 * part; the Bier's tier opens it; it costs items (taken from the inventory) and essences (drawn from the nearest awake
 * altar). Every carving carried is a step of corruption.
 */
public enum Inscription {
    NIGHT_EYES("night_eyes", Part.HEAD, 1, 0x5A4AE0, 0, 40, 10, "minecraft:golden_carrot*4", "minecraft:phantom_membrane*2", "alchyforbidden:bone_ink*2"),
    ECHO_SIGHT("echo_sight", Part.HEAD, 1, 0x3CDCE6, 0, 30, 30, "minecraft:echo_shard*2", "minecraft:glow_ink_sac*4", "alchyforbidden:bone_ink*2"),
    CLEAR_MIND("clear_mind", Part.HEAD, 2, 0xE6E0FF, 0, 80, 25, "minecraft:ghast_tear*2", "minecraft:golden_carrot*4", "minecraft:milk_bucket*1", "alchyforbidden:echo_ink*2", "alchyforbidden:sorrow_tear*3"),
    PALE_MIND("pale_mind", Part.HEAD, 2, 0xB8E0A0, 0, 60, 20, "minecraft:milk_bucket*2", "minecraft:spider_eye*8", "minecraft:golden_carrot*4", "alchyforbidden:echo_ink*2", "alchyforbidden:sorrow_tear*3"),

    HOLLOW_LUNGS("hollow_lungs", Part.CHEST, 1, 0x2FA89A, 0, 40, 10, "minecraft:pufferfish*2", "minecraft:prismarine_crystals*8", "minecraft:kelp*16", "alchyforbidden:bone_ink*2"),
    STONE_SHELL("stone_shell", Part.CHEST, 2, 0x8C8696, 80, 0, 25, "minecraft:obsidian*8", "minecraft:turtle_scute*2", "alchyforbidden:rift_alloy*2", "alchyforbidden:echo_ink*2", "alchyforbidden:wrath_ember*3"),
    ESSENCE_RESERVE("essence_reserve", Part.CHEST, 2, 0xB98CFF, 0, 60, 60, "minecraft:amethyst_block*4", "minecraft:echo_shard*4", "minecraft:glass_bottle*3", "alchyforbidden:echo_ink*2", "alchyforbidden:sorrow_tear*3"),
    IRON_GUT("iron_gut", Part.CHEST, 1, 0xA07050, 30, 0, 10, "minecraft:rotten_flesh*16", "minecraft:cooked_beef*8", "alchyforbidden:bone_ink*2"),
    SECOND_BREATH("second_breath", Part.CHEST, 3, 0xF0D070, 150, 150, 150, "minecraft:totem_of_undying*1", "minecraft:golden_apple*2", "alchyforbidden:void_ink*2", "alchyforbidden:abyssal_core*1"),
    TOUGH_METABOLISM("tough_metabolism", Part.CHEST, 3, 0x7AE0C0, 120, 150, 150, "minecraft:golden_apple*4", "minecraft:milk_bucket*4", "alchyforbidden:mourning_pearl*4", "alchyforbidden:void_ink*2", "alchyforbidden:abyssal_core*1"),
    DEEP_HEART("deep_heart", Part.CHEST, 3, 0x7A3ED6, 150, 150, 200, "minecraft:golden_apple*2", "minecraft:sculk_catalyst*2", "alchyforbidden:void_ink*2", "alchyforbidden:abyssal_core*1"),

    LONG_REACH("long_reach", Part.ARMS, 1, 0x9C7AE8, 40, 0, 10, "minecraft:string*8", "minecraft:ender_pearl*2", "minecraft:amethyst_shard*8", "alchyforbidden:bone_ink*2"),
    QUICK_HANDS("quick_hands", Part.ARMS, 2, 0xF2D25A, 80, 0, 25, "minecraft:sugar*16", "minecraft:feather*8", "minecraft:gold_ingot*4", "alchyforbidden:echo_ink*2", "alchyforbidden:wrath_ember*3"),
    WRATHFUL_ARMS("wrathful_arms", Part.ARMS, 2, 0xD0304A, 100, 0, 30, "minecraft:blaze_powder*4", "minecraft:fermented_spider_eye*2", "minecraft:nether_wart*8", "alchyforbidden:echo_ink*2", "alchyforbidden:wrath_ember*3"),
    HEAVY_HAND("heavy_hand", Part.ARMS, 1, 0x8A6A4A, 40, 0, 10, "minecraft:iron_ingot*8", "minecraft:piston*2", "alchyforbidden:bone_ink*2"),
    SOUL_SIPHON("soul_siphon", Part.ARMS, 3, 0xC02858, 150, 100, 150, "minecraft:wither_rose*2", "minecraft:ghast_tear*4", "alchyforbidden:wrath_ember*8", "alchyforbidden:void_ink*2"),
    RIFT_GRIP("rift_grip", Part.ARMS, 2, 0x6E5AA0, 0, 25, 80, "minecraft:diamond*2", "minecraft:redstone_block*2", "minecraft:amethyst_shard*8", "alchyforbidden:echo_ink*2", "alchyforbidden:corruption_spore*3"),

    CHAINED_WINGS("chained_wings", Part.BACK, 2, 0xA8A4B8, 100, 100, 0, "minecraft:phantom_membrane*8", "minecraft:iron_chain*16", "minecraft:feather*16", "alchyforbidden:echo_ink*2", "alchyforbidden:wrath_ember*3"),
    DREAD_MANTLE("dread_mantle", Part.BACK, 2, 0x4A3060, 80, 80, 40, "minecraft:wither_skeleton_skull*1", "minecraft:soul_sand*16", "minecraft:phantom_membrane*4", "alchyforbidden:echo_ink*2", "alchyforbidden:wrath_ember*3"),
    ECHO_WINGS("echo_wings", Part.BACK, 3, 0x3FC6D0, 200, 200, 300, "minecraft:phantom_membrane*16", "minecraft:echo_shard*8", "alchyforbidden:rift_silk*4", "alchyforbidden:void_ink*2", "alchyforbidden:abyssal_core*1"),

    SWIFT_STRIDE("swift_stride", Part.LEGS, 1, 0x7AE8B0, 40, 0, 10, "minecraft:sugar*16", "minecraft:rabbit_foot*1", "alchyforbidden:bone_ink*2"),
    HIGH_LEAP("high_leap", Part.LEGS, 1, 0x9FE6A0, 40, 0, 10, "minecraft:rabbit_hide*4", "minecraft:slime_ball*8", "minecraft:feather*4", "alchyforbidden:bone_ink*2"),
    SECOND_LEAP("second_leap", Part.LEGS, 2, 0xA0F0E0, 60, 60, 0, "minecraft:rabbit_foot*2", "minecraft:phantom_membrane*4", "minecraft:wind_charge*8", "alchyforbidden:echo_ink*2", "alchyforbidden:wrath_ember*3"),
    SILENT_STEP("silent_step", Part.LEGS, 2, 0x0E7A70, 0, 40, 60, "minecraft:sculk_sensor*2", "minecraft:white_wool*8", "minecraft:phantom_membrane*2", "alchyforbidden:echo_ink*2", "alchyforbidden:corruption_spore*3"),

    WATER_WALKER("water_walker", Part.FEET, 1, 0x3A70E0, 0, 40, 10, "minecraft:lily_pad*8", "minecraft:prismarine_shard*8", "alchyforbidden:bone_ink*2"),
    LAVA_WALKER("lava_walker", Part.FEET, 2, 0xF07A2A, 100, 0, 30, "minecraft:magma_cream*8", "minecraft:obsidian*8", "minecraft:blaze_powder*4", "alchyforbidden:echo_ink*2", "alchyforbidden:wrath_ember*3"),
    WALL_CLIMBER("wall_climber", Part.FEET, 3, 0x6A5A3A, 80, 80, 120, "minecraft:spider_eye*16", "minecraft:string*32", "minecraft:slime_ball*8", "alchyforbidden:rift_silk*2", "alchyforbidden:void_ink*2"),
    SKY_WALKER("sky_walker", Part.FEET, 3, 0xD8F0FF, 0, 200, 200, "minecraft:phantom_membrane*8", "minecraft:feather*32", "alchyforbidden:rift_silk*4", "alchyforbidden:void_ink*2", "alchyforbidden:abyssal_core*1"),

    BARK_SKIN("bark_skin", Part.SKIN, 1, 0x6A8A3A, 40, 0, 10, "minecraft:oak_log*32", "minecraft:vine*8", "minecraft:iron_ingot*4", "alchyforbidden:bone_ink*2"),
    FIRE_SKIN("fire_skin", Part.SKIN, 2, 0xFF8A3A, 80, 0, 25, "minecraft:magma_cream*4", "minecraft:blaze_rod*4", "minecraft:crying_obsidian*4", "alchyforbidden:echo_ink*2", "alchyforbidden:wrath_ember*3"),
    THORN_SKIN("thorn_skin", Part.SKIN, 2, 0x5A9A3A, 80, 0, 25, "minecraft:cactus*16", "minecraft:pointed_dripstone*8", "alchyforbidden:rift_alloy*2", "alchyforbidden:echo_ink*2", "alchyforbidden:wrath_ember*3"),
    SHADOW_SKIN("shadow_skin", Part.SKIN, 3, 0x2A2238, 0, 150, 100, "minecraft:ink_sac*16", "minecraft:phantom_membrane*4", "minecraft:ender_eye*2", "alchyforbidden:mourning_pearl*4", "alchyforbidden:void_ink*2"),

    // ---- the second wave (essence/WildCarvings) ----
    HOARDERS_EYE("hoarders_eye", Part.HEAD, 1, 0xE0C060, 0, 30, 10, "minecraft:gold_ingot*4", "minecraft:ender_pearl*2", "minecraft:hopper*1", "alchyforbidden:bone_ink*2"),
    HUNTERS_MARK("hunters_mark", Part.HEAD, 2, 0xE05050, 80, 0, 20, "minecraft:spectral_arrow*16", "minecraft:spider_eye*4", "minecraft:glow_ink_sac*4", "alchyforbidden:echo_ink*2", "alchyforbidden:wrath_ember*3"),
    CARRION_FEAST("carrion_feast", Part.CHEST, 1, 0x6A3A2A, 40, 0, 10, "minecraft:rotten_flesh*32", "minecraft:spider_eye*8", "minecraft:bone*8", "alchyforbidden:bone_ink*2"),
    VAMPIRIC_HEART("vampiric_heart", Part.CHEST, 2, 0xA01030, 120, 40, 0, "minecraft:ghast_tear*2", "minecraft:redstone_block*4", "minecraft:fermented_spider_eye*4", "alchyforbidden:echo_ink*2", "alchyforbidden:wrath_ember*3"),
    RIFT_FIST("rift_fist", Part.ARMS, 2, 0x8A5AE0, 80, 0, 60, "minecraft:echo_shard*2", "minecraft:piston*4", "minecraft:amethyst_block*2", "alchyforbidden:echo_ink*2", "alchyforbidden:wrath_ember*3"),
    VEIN_RIP("vein_rip", Part.ARMS, 2, 0x5ACDE0, 40, 0, 80, "minecraft:diamond_pickaxe*1", "minecraft:amethyst_shard*16", "minecraft:redstone*32", "alchyforbidden:echo_ink*2", "alchyforbidden:corruption_spore*3"),
    EXECUTIONER("executioner", Part.ARMS, 3, 0x7A0A1A, 200, 0, 150, "minecraft:netherite_scrap*2", "minecraft:wither_skeleton_skull*1", "alchyforbidden:rift_alloy*4", "alchyforbidden:void_ink*2", "alchyforbidden:abyssal_core*1"),
    LEECH_MANTLE("leech_mantle", Part.BACK, 2, 0x9A2030, 80, 60, 0, "minecraft:rotten_flesh*16", "minecraft:ghast_tear*1", "minecraft:slime_ball*8", "alchyforbidden:echo_ink*2", "alchyforbidden:wrath_ember*3"),
    GRASPING_TENDRILS("grasping_tendrils", Part.BACK, 3, 0x1A9A8A, 60, 60, 200, "minecraft:sculk_vein*32", "minecraft:sculk_catalyst*1", "minecraft:lead*4", "alchyforbidden:corruption_spore*8", "alchyforbidden:void_ink*2"),
    MOON_STEP("moon_step", Part.LEGS, 1, 0xE8F0FF, 0, 40, 10, "minecraft:feather*16", "minecraft:phantom_membrane*2", "alchyforbidden:bone_ink*2"),
    METEOR_STOMP("meteor_stomp", Part.LEGS, 2, 0xC05A20, 120, 0, 40, "minecraft:magma_block*8", "minecraft:heavy_core*1", "minecraft:obsidian*8", "alchyforbidden:echo_ink*2", "alchyforbidden:wrath_ember*3"),
    EMBER_TRAIL("ember_trail", Part.FEET, 2, 0xFF6A2A, 100, 0, 30, "minecraft:blaze_powder*8", "minecraft:magma_cream*4", "minecraft:fire_charge*8", "alchyforbidden:echo_ink*2", "alchyforbidden:wrath_ember*3"),
    TOXIC_OOZE("toxic_ooze", Part.SKIN, 2, 0x7AC040, 40, 40, 40, "minecraft:slime_block*2", "minecraft:spider_eye*8", "minecraft:poisonous_potato*8", "alchyforbidden:echo_ink*2", "alchyforbidden:wrath_ember*3"),
    MIRROR_SKIN("mirror_skin", Part.SKIN, 3, 0xD0E8FF, 0, 150, 150, "minecraft:glass*32", "minecraft:shield*1", "minecraft:amethyst_block*4", "alchyforbidden:mourning_pearl*2", "alchyforbidden:void_ink*2");

    /** The parts of the body a carving goes on (one each). */
    public enum Part {
        HEAD, CHEST, ARMS, BACK, LEGS, FEET, SKIN;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record Cost(Item item, int count) {
        public ItemStack stack() {
            return new ItemStack(item, count);
        }
    }

    private final String id;
    private final Part part;
    private final int tier, color, wrath, sorrow, corruption;
    private final String[] items;
    private @Nullable List<Cost> costs;

    Inscription(String id, Part part, int tier, int color, int wrath, int sorrow, int corruption, String... items) {
        this.id = id;
        this.part = part;
        this.tier = tier;
        this.color = color;
        this.wrath = wrath;
        this.sorrow = sorrow;
        this.corruption = corruption;
        this.items = items;
    }

    public String id() {
        return id;
    }

    public Part part() {
        return part;
    }

    /** The Bier's tier that opens it. */
    public int tier() {
        return tier;
    }

    public int color() {
        return color;
    }

    public int essence(Essence essence) {
        return switch (essence) {
            case WRATH -> wrath;
            case SORROW -> sorrow;
            case CORRUPTION -> corruption;
        };
    }

    /** The items it takes (read once the items exist). */
    public List<Cost> costs() {
        if (costs == null) {
            List<Cost> out = new ArrayList<>();
            for (String entry : items) {
                String[] parts = entry.split("\\*");
                Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(parts[0]));
                out.add(new Cost(item, parts.length > 1 ? Integer.parseInt(parts[1]) : 1));
            }
            costs = List.copyOf(out);
        }
        return costs;
    }

    public static List<Inscription> on(Part part) {
        List<Inscription> out = new ArrayList<>();
        for (Inscription inscription : values()) if (inscription.part == part) out.add(inscription);
        return out;
    }

    private static final java.util.Map<String, Inscription> BY_ID = new java.util.HashMap<>();

    static {
        for (Inscription inscription : values()) BY_ID.put(inscription.id, inscription);
    }

    public static @Nullable Inscription byId(String id) {
        return BY_ID.get(id);
    }
}
