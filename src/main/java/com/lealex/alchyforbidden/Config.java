package com.lealex.alchyforbidden;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Game settings, saved in config/alchyforbidden-common.toml. Read through the getters: they fall back to the defaults
 * while the file isn't loaded yet.
 */
public final class Config {
    private Config() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("The altar: what each tier costs and how it grows.").push("altar");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TIER_2_COST = BUILDER
            .comment("What the altar asks for before it grows its second tier. One entry: \"item id*count\".")
            .defineListAllowEmpty("tier2Cost", List.of(
                    "minecraft:sculk*16",
                    "minecraft:amethyst_shard*8",
                    "minecraft:echo_shard*2"), () -> "", value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TIER_3_COST = BUILDER
            .comment("What the altar asks for before it grows its third tier. One entry: \"item id*count\".")
            .defineListAllowEmpty("tier3Cost", List.of(
                    "minecraft:sculk_catalyst*1",
                    "minecraft:echo_shard*4",
                    "minecraft:crying_obsidian*4",
                    "minecraft:amethyst_shard*16"), () -> "", value -> value instanceof String);

    public static final ModConfigSpec.IntValue GATHER_TICKS = BUILDER
            .comment("How long the altar gathers itself before it grows, in ticks (20 = 1 second).")
            .defineInRange("gatherTicks", 60, 0, 1200);

    public static final ModConfigSpec.IntValue TICKS_PER_STEP = BUILDER
            .comment("Ticks between two steps of the growth.")
            .defineInRange("ticksPerStep", 2, 1, 40);

    public static final ModConfigSpec.IntValue BLOCKS_PER_STEP = BUILDER
            .comment("Blocks the altar grows at each step.")
            .defineInRange("blocksPerStep", 1, 1, 16);

    static {
        BUILDER.pop();
        BUILDER.comment("The noise of a growing altar.").push("noise");
    }

    public static final ModConfigSpec.BooleanValue LOUD_GROWTH = BUILDER
            .comment("True if a growing altar is loud: near sculk shriekers it sets one off (a warning toward the warden,",
                    "as if the player had been heard), anywhere else it draws hostile mobs. Wool around the altar muffles it.")
            .define("loudGrowth", true);

    public static final ModConfigSpec.IntValue WOOL_RADIUS = BUILDER
            .comment("How far from the altar wool and carpets count, in blocks (a cube around it).")
            .defineInRange("woolRadius", 5, 1, 8);

    public static final ModConfigSpec.IntValue WOOL_TO_MUFFLE = BUILDER
            .comment("How many wool blocks or carpets within that reach muffle the growth.")
            .defineInRange("woolToMuffle", 12, 0, 256);

    public static final ModConfigSpec.IntValue SHRIEKER_REACH = BUILDER
            .comment("How far a sculk shrieker hears a growing altar, in blocks.")
            .defineInRange("shriekerReach", 32, 0, 64);

    public static final ModConfigSpec.IntValue MOBS_DRAWN = BUILDER
            .comment("How many hostile mobs a loud growth draws when no shrieker hears it (none on peaceful).")
            .defineInRange("mobsDrawn", 3, 0, 16);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> DRAWN_MOBS = BUILDER
            .comment("The kinds a loud growth draws, picked at random (entity ids).")
            .defineListAllowEmpty("drawnMobs", List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider"),
                    () -> "", value -> value instanceof String);

    static {
        BUILDER.pop();
        BUILDER.comment("The eye.").push("eye");
    }

    public static final ModConfigSpec.IntValue EYE_ALTAR_RANGE = BUILDER
            .comment("How far the eye feels an altar, in blocks.")
            .defineInRange("altarRange", 48, 8, 128);

    public static final ModConfigSpec.IntValue EYE_SEARCH_CHUNKS = BUILDER
            .comment("How far the eye looks for an ancient city, in chunks (an eye of ender looks 100 chunks).")
            .defineInRange("citySearchChunks", 100, 0, 200);

    static {
        BUILDER.pop();
        BUILDER.comment("The essences: Wrath, Sorrow and Sculk Corruption.").push("essences");
    }

    public static final ModConfigSpec.IntValue CRACKED_VESSEL = BUILDER
            .comment("How much a Cracked Vessel holds.").defineInRange("crackedVessel", 100, 1, 1000000);
    public static final ModConfigSpec.IntValue BOUND_VESSEL = BUILDER
            .comment("How much a Bound Vessel holds.").defineInRange("boundVessel", 400, 1, 1000000);
    public static final ModConfigSpec.IntValue RIFT_VESSEL = BUILDER
            .comment("How much a Rift Vessel holds.").defineInRange("riftVessel", 1600, 1, 1000000);

    public static final ModConfigSpec.IntValue WRATH_PER_KILL = BUILDER
            .comment("Wrath a vessel takes when its carrier kills a hostile mob, for every 10 health the mob had (at least 1).")
            .defineInRange("wrathPer10Health", 5, 0, 10000);
    public static final ModConfigSpec.IntValue SORROW_IN_DARK = BUILDER
            .comment("Sorrow a vessel takes every second its carrier stands in the dark.")
            .defineInRange("sorrowPerSecond", 1, 0, 10000);
    public static final ModConfigSpec.IntValue SORROW_IN_DEEP_DARK = BUILDER
            .comment("The same in the deep dark (the biome of the ancient cities).")
            .defineInRange("sorrowPerSecondDeepDark", 3, 0, 10000);
    public static final ModConfigSpec.IntValue SORROW_LIGHT = BUILDER
            .comment("The most light where the carrier stands that still counts as dark (0 to 15).")
            .defineInRange("sorrowMaxLight", 3, 0, 15);

    public static final ModConfigSpec.IntValue CORRUPTION_PER_DEATH = BUILDER
            .comment("Sculk Corruption an altar gains when a mob dies near a sculk catalyst (within catalystReach of the death)",
                    "that is itself within altarReach of the altar.")
            .defineInRange("corruptionPerDeath", 4, 0, 10000);
    public static final ModConfigSpec.IntValue CORRUPTION_PER_XP = BUILDER
            .comment("And this much more for every point of experience the mob would have given (the altar takes it).")
            .defineInRange("corruptionPerExperience", 2, 0, 1000);
    public static final ModConfigSpec.IntValue CATALYST_REACH = BUILDER
            .defineInRange("catalystReach", 8, 1, 16);
    public static final ModConfigSpec.IntValue ALTAR_REACH = BUILDER
            .defineInRange("altarReach", 16, 1, 48);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> RESERVE = BUILDER
            .comment("How much of each essence the altar holds at tiers 1, 2 and 3.")
            .defineList("altarReserve", List.of("400", "1600", "6400"), () -> "", value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SCULK_OFFERINGS = BUILDER
            .comment("What the altar's pour slot turns into Sculk Corruption: \"item id=corruption per item\".")
            .defineListAllowEmpty("sculkOfferings", List.of("minecraft:sculk=2", "minecraft:sculk_vein=1",
                    "minecraft:sculk_sensor=12", "minecraft:sculk_shrieker=20", "minecraft:sculk_catalyst=40"),
                    () -> "", value -> value instanceof String);

    static {
        BUILDER.pop();
        BUILDER.comment("The Echo Resonator: ender pearls flying through sculk shriekers make Sculk Corruption.").push("resonator");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> RESONATOR_TIER_2 = BUILDER
            .comment("What the resonator asks for before it grows its second tier. One entry: \"item id*count\".")
            .defineListAllowEmpty("tier2Cost", List.of("minecraft:sculk_catalyst*2", "minecraft:echo_shard*4", "minecraft:amethyst_block*4"),
                    () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RESONATOR_TIER_3 = BUILDER
            .comment("And its third, given only once a warden has been killed beside it (the dead warden blasts the shriekers: no more pearls).")
            .defineListAllowEmpty("tier3Cost", List.of("minecraft:echo_shard*8", "minecraft:sculk_shrieker*4", "minecraft:sculk_catalyst*2"),
                    () -> "", value -> value instanceof String);
    public static final ModConfigSpec.IntValue RESONATOR_BREAK = BUILDER
            .comment("The chance, in a hundred, that a pearl breaks each time it passes a shrieker.")
            .defineInRange("breakChance", 45, 0, 100);
    public static final ModConfigSpec.IntValue RESONATOR_EYE_BREAK = BUILDER
            .comment("The same for an eye of ender (the resonator throws its eyes first).")
            .defineInRange("eyeBreakChance", 15, 0, 100);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RESONATOR_PER_BOOM = BUILDER
            .comment("Sculk Corruption each sonic boom makes, at tiers 1, 2 and 3.")
            .defineList("corruptionPerBoom", List.of("6", "10", "40"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.IntValue RESONATOR_BLAST = BUILDER
            .comment("Tier 3: ticks between two of the warden's blasts (its charge is 34 of them).")
            .defineInRange("wardenBlastTicks", 60, 40, 1200);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RESONATOR_LEG = BUILDER
            .comment("Ticks the pearl takes from one shrieker to the next, at tiers 1, 2 and 3.")
            .defineList("legTicks", List.of("16", "12", "8"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.IntValue RESONATOR_PEARLS = BUILDER
            .comment("How many ender pearls and eyes of ender the resonator holds (both together).").defineInRange("maxPearls", 64, 1, 4096);
    public static final ModConfigSpec.IntValue RESONATOR_BUFFER = BUILDER
            .comment("How much Corruption it keeps when no altar has room (it stops at that).").defineInRange("buffer", 400, 0, 100000);
    public static final ModConfigSpec.IntValue RESONATOR_REACH = BUILDER
            .comment("How far it reaches an awake altar, in blocks.").defineInRange("altarReach", 24, 1, 64);

    static {
        BUILDER.pop();
        BUILDER.comment("The Killing Pit: hostile mobs that die in it give Wrath (and their drops).").push("killing_pit");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PIT_TIER_2 = BUILDER
            .comment("What the pit asks for before it grows its second tier. One entry: \"item id*count\".")
            .defineListAllowEmpty("tier2Cost", List.of("minecraft:bone_block*4", "minecraft:iron_bars*8", "minecraft:echo_shard*2"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PIT_TIER_3 = BUILDER
            .comment("And its third.")
            .defineListAllowEmpty("tier3Cost", List.of("minecraft:blaze_rod*8", "minecraft:crying_obsidian*4", "minecraft:echo_shard*4"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PIT_DAMAGE = BUILDER
            .comment("Damage its spikes deal each second to a hostile mob inside, at tiers 1, 2 and 3.")
            .defineList("damage", List.of("3", "5", "8"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PIT_WRATH = BUILDER
            .comment("Wrath of a death in the pit, in percent of what the same kill gives a vessel, at tiers 1, 2 and 3.")
            .defineList("wrathPercent", List.of("100", "150", "200"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PIT_RATE = BUILDER
            .comment("Wrath a hostile mob of 20 health in the pit gives each second it suffers, at tiers 1, 2 and 3 (bigger mobs give more, in step with their max health; it grows the longer it suffers).")
            .defineList("wrathPerSecond", List.of("1", "2", "3"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.IntValue PIT_TORTURE = BUILDER
            .comment("Every this many seconds a mob has suffered in the pit, its Wrath each second grows by the base again (one that heals and lives on gives more and more).")
            .defineInRange("tortureSeconds", 10, 1, 3600);
    public static final ModConfigSpec.DoubleValue PIT_CAP = BUILDER
            .comment("The most Wrath one mob can give each second, per point of its max health (a zombie, 20 health, tops out at 20 times this).")
            .defineInRange("wrathCapPerHealth", 1.0, 0.01, 1000.0);
    public static final ModConfigSpec.IntValue PIT_LURE_RANGE = BUILDER
            .comment("Tier 2: how far the rift calls hostile mobs from, at night.").defineInRange("lureRange", 24, 4, 64);
    public static final ModConfigSpec.IntValue PIT_LURE_COUNT = BUILDER
            .comment("Tier 2: how many it calls each time.").defineInRange("lureCount", 2, 0, 16);
    public static final ModConfigSpec.IntValue PIT_LURE_TICKS = BUILDER
            .comment("Tier 2: ticks between two calls.").defineInRange("lureTicks", 100, 20, 12000);
    public static final ModConfigSpec.IntValue PIT_MAX_MOBS = BUILDER
            .comment("It calls or makes no more once this many hostile mobs are in it.").defineInRange("maxMobs", 8, 1, 64);
    public static final ModConfigSpec.IntValue PIT_HEAL_BELOW = BUILDER
            .comment("Tier 3: the rift heals a mob in the pit back to full once its health falls under this percent (so it suffers on).")
            .defineInRange("healBelow", 30, 1, 99);
    public static final ModConfigSpec.IntValue PIT_HEAL_COST = BUILDER
            .comment("Tier 3: Sculk Corruption each healing costs, from the nearest altar (none there: no healing).")
            .defineInRange("healCost", 5, 0, 10000);
    public static final ModConfigSpec.IntValue PIT_BUFFER = BUILDER
            .comment("How much Wrath it keeps when no altar has room.").defineInRange("buffer", 2000, 0, 1000000);
    public static final ModConfigSpec.IntValue PIT_XP = BUILDER
            .comment("The most experience the pit stores from what dies in it (its screen gives it to you).").defineInRange("maxExperience", 10000, 0, 10000000);
    public static final ModConfigSpec.IntValue PIT_REACH = BUILDER
            .comment("How far it reaches an awake altar, in blocks.").defineInRange("altarReach", 24, 1, 64);

    static {
        BUILDER.pop();
        BUILDER.comment("The Lament Well: the ghosts of what dies near it weep round it, and their grief is Sorrow.").push("lament_well");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> WELL_TIER_2 = BUILDER
            .comment("What the well asks for before it grows its second tier. One entry: \"item id*count\".")
            .defineListAllowEmpty("tier2Cost", List.of("minecraft:soul_sand*16", "minecraft:ghast_tear*2", "minecraft:echo_shard*2"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WELL_TIER_3 = BUILDER
            .comment("And its third.")
            .defineListAllowEmpty("tier3Cost", List.of("minecraft:sculk_catalyst*1", "minecraft:ghast_tear*4", "minecraft:echo_shard*4"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.IntValue WELL_NAMED = BUILDER
            .comment("Tier 2: Sorrow for a named item thrown in.").defineInRange("offeringNamed", 20, 0, 10000);
    public static final ModConfigSpec.IntValue WELL_BROKEN = BUILDER
            .comment("Tier 2: for a tool or armour nearly broken.").defineInRange("offeringBroken", 15, 0, 10000);
    public static final ModConfigSpec.IntValue WELL_DISC = BUILDER
            .comment("Tier 2: for a music disc.").defineInRange("offeringDisc", 40, 0, 10000);
    public static final ModConfigSpec.IntValue WELL_TOTEM = BUILDER
            .comment("Tier 2: for a totem of undying.").defineInRange("offeringTotem", 200, 0, 10000);
    public static final ModConfigSpec.IntValue WELL_GHOST_TICKS = BUILDER
            .comment("How long the ghost of a mob that died near the well weeps round it, in ticks.").defineInRange("ghostTicks", 600, 20, 72000);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WELL_GHOSTS = BUILDER
            .comment("Most ghosts weeping round it at once, at tiers 1, 2 and 3.")
            .defineList("maxGhosts", List.of("4", "8", "16"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WELL_GHOST_RANGE = BUILDER
            .comment("How near a death must be to leave a ghost, at tiers 1, 2 and 3.")
            .defineList("ghostRange", List.of("8", "12", "16"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WELL_GHOST_SORROW = BUILDER
            .comment("Sorrow each ghost adds each second, at tiers 1, 2 and 3.")
            .defineList("ghostSorrow", List.of("1", "2", "3"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.IntValue WELL_BUFFER = BUILDER
            .comment("How much Sorrow it keeps when no altar has room.").defineInRange("buffer", 400, 0, 100000);
    public static final ModConfigSpec.IntValue WELL_REACH = BUILDER
            .comment("How far it reaches an awake altar, in blocks.").defineInRange("altarReach", 24, 1, 64);

    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    public static List<Cost> resonatorCostFor(int tier) {
        List<? extends String> lines = switch (tier) {
            case 2 -> readList(RESONATOR_TIER_2, List.of("minecraft:sculk_catalyst*2", "minecraft:echo_shard*4", "minecraft:amethyst_block*4"));
            case 3 -> readList(RESONATOR_TIER_3, List.of("minecraft:echo_shard*8", "minecraft:sculk_shrieker*4", "minecraft:sculk_catalyst*2"));
            default -> List.of();
        };
        return parseCosts(lines);
    }

    public static int resonatorBreakChance() { return read(RESONATOR_BREAK, 45); }
    public static int resonatorEyeBreakChance() { return read(RESONATOR_EYE_BREAK, 15); }
    public static int resonatorPerBoom(int tier) { return perTier(RESONATOR_PER_BOOM, tier, new int[] {6, 10, 40}); }
    public static int resonatorBlastTicks() { return read(RESONATOR_BLAST, 60); }
    public static int resonatorLegTicks(int tier) { return perTier(RESONATOR_LEG, tier, new int[] {16, 12, 8}); }
    public static int resonatorMaxPearls() { return read(RESONATOR_PEARLS, 64); }
    public static int resonatorBuffer() { return read(RESONATOR_BUFFER, 400); }
    public static int resonatorReach() { return read(RESONATOR_REACH, 24); }

    public static List<Cost> pitCostFor(int tier) {
        return parseCosts(switch (tier) {
            case 2 -> readList(PIT_TIER_2, List.of("minecraft:bone_block*4", "minecraft:iron_bars*8", "minecraft:echo_shard*2"));
            case 3 -> readList(PIT_TIER_3, List.of("minecraft:blaze_rod*8", "minecraft:crying_obsidian*4", "minecraft:echo_shard*4"));
            default -> List.of();
        });
    }

    public static int pitDamage(int tier) { return perTier(PIT_DAMAGE, tier, new int[] {3, 5, 8}); }
    public static int pitWrathPercent(int tier) { return perTier(PIT_WRATH, tier, new int[] {100, 150, 200}); }
    public static int pitWrathPerSecond(int tier) { return perTier(PIT_RATE, tier, new int[] {1, 2, 3}); }
    public static int pitTortureSeconds() { return read(PIT_TORTURE, 10); }
    public static double pitCapPerHealth() {
        try {
            return PIT_CAP.get();
        } catch (RuntimeException notLoaded) {
            return 1.0;
        }
    }
    public static int pitLureRange() { return read(PIT_LURE_RANGE, 24); }
    public static int pitLureCount() { return read(PIT_LURE_COUNT, 2); }
    public static int pitLureTicks() { return read(PIT_LURE_TICKS, 100); }
    public static int pitMaxMobs() { return read(PIT_MAX_MOBS, 8); }
    public static int pitHealBelow() { return read(PIT_HEAL_BELOW, 30); }
    public static int pitHealCost() { return read(PIT_HEAL_COST, 5); }
    public static int pitBuffer() { return read(PIT_BUFFER, 2000); }
    public static int pitReach() { return read(PIT_REACH, 24); }
    public static int pitMaxXp() { return read(PIT_XP, 10000); }

    public static List<Cost> wellCostFor(int tier) {
        return parseCosts(switch (tier) {
            case 2 -> readList(WELL_TIER_2, List.of("minecraft:soul_sand*16", "minecraft:ghast_tear*2", "minecraft:echo_shard*2"));
            case 3 -> readList(WELL_TIER_3, List.of("minecraft:sculk_catalyst*1", "minecraft:ghast_tear*4", "minecraft:echo_shard*4"));
            default -> List.of();
        });
    }

    public static int wellNamed() { return read(WELL_NAMED, 20); }
    public static int wellBroken() { return read(WELL_BROKEN, 15); }
    public static int wellDisc() { return read(WELL_DISC, 40); }
    public static int wellTotem() { return read(WELL_TOTEM, 200); }
    public static int wellGhostTicks() { return read(WELL_GHOST_TICKS, 600); }
    public static int wellMaxGhosts(int tier) { return perTier(WELL_GHOSTS, tier, new int[] {4, 8, 16}); }
    public static int wellGhostRange(int tier) { return perTier(WELL_GHOST_RANGE, tier, new int[] {8, 12, 16}); }
    public static int wellGhostSorrow(int tier) { return perTier(WELL_GHOST_SORROW, tier, new int[] {1, 2, 3}); }
    public static int wellBuffer() { return read(WELL_BUFFER, 400); }
    public static int wellReach() { return read(WELL_REACH, 24); }

    private static int perTier(ModConfigSpec.ConfigValue<List<? extends String>> value, int tier, int[] fallback) {
        int index = Math.max(0, Math.min(tier, fallback.length) - 1);
        try {
            List<? extends String> lines = value.get();
            return Integer.parseInt(lines.get(Math.min(index, lines.size() - 1)).trim());
        } catch (RuntimeException notLoadedOrWrong) {
            return fallback[index];
        }
    }

    public static int crackedVessel() { return read(CRACKED_VESSEL, 100); }
    public static int boundVessel() { return read(BOUND_VESSEL, 400); }
    public static int riftVessel() { return read(RIFT_VESSEL, 1600); }
    public static int wrathPer10Health() { return read(WRATH_PER_KILL, 5); }
    public static int sorrowPerSecond() { return read(SORROW_IN_DARK, 1); }
    public static int sorrowPerSecondDeepDark() { return read(SORROW_IN_DEEP_DARK, 3); }
    public static int sorrowMaxLight() { return read(SORROW_LIGHT, 3); }
    public static int corruptionPerDeath() { return read(CORRUPTION_PER_DEATH, 4); }
    public static int catalystReach() { return read(CATALYST_REACH, 8); }
    public static int corruptionPerExperience() { return read(CORRUPTION_PER_XP, 2); }
    public static int altarReach() { return read(ALTAR_REACH, 16); }

    /** How much of each essence the altar holds at this tier. */
    public static int reserve(int tier) {
        List<? extends String> lines = readList(RESERVE, List.of("400", "1600", "6400"));
        int index = Math.max(0, Math.min(tier, lines.size()) - 1);
        try {
            return Math.max(1, Integer.parseInt(lines.get(index).trim()));
        } catch (RuntimeException wrong) {
            return 400 << (2 * (tier - 1));
        }
    }

    /** Sculk Corruption one of this item gives in the pour slot (0 = not an offering). */
    public static int sculkOffering(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        for (String line : readList(SCULK_OFFERINGS, List.of("minecraft:sculk=2"))) {
            String[] parts = line.split("=");
            if (parts.length == 2 && id.toString().equals(parts[0].trim())) {
                try {
                    return Math.max(0, Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException wrong) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /** One thing the altar asks for. */
    public record Cost(Item item, int count) {}

    /** What growing to this tier costs (empty for a tier with no cost list). */
    public static List<Cost> costFor(int tier) {
        List<? extends String> lines = switch (tier) {
            case 2 -> readList(TIER_2_COST, List.of("minecraft:sculk*16", "minecraft:amethyst_shard*8", "minecraft:echo_shard*2"));
            case 3 -> readList(TIER_3_COST, List.of("minecraft:sculk_catalyst*1", "minecraft:echo_shard*4",
                    "minecraft:crying_obsidian*4", "minecraft:amethyst_shard*16"));
            default -> List.of();
        };
        return parseCosts(lines);
    }

    private static List<Cost> parseCosts(List<? extends String> lines) {
        List<Cost> costs = new ArrayList<>();
        for (String line : lines) {
            String[] parts = line.trim().split("\\*");
            Identifier id = Identifier.tryParse(parts[0].trim());
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                AlchyForbidden.LOGGER.warn("altar cost \"{}\": no such item", line);
                continue;
            }
            int count = 1;
            if (parts.length > 1) {
                try {
                    count = Math.max(1, Integer.parseInt(parts[1].trim()));
                } catch (NumberFormatException wrong) {
                    AlchyForbidden.LOGGER.warn("altar cost \"{}\": the count is not a number", line);
                }
            }
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item != Items.AIR) costs.add(new Cost(item, count));
        }
        return costs;
    }

    /** The kinds a loud growth may draw. */
    public static List<EntityType<?>> drawnMobs() {
        List<EntityType<?>> kinds = new ArrayList<>();
        for (String line : readList(DRAWN_MOBS, List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider"))) {
            Identifier id = Identifier.tryParse(line.trim());
            if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) kinds.add(BuiltInRegistries.ENTITY_TYPE.getValue(id));
        }
        return kinds;
    }

    public static int gatherTicks() {
        return read(GATHER_TICKS, 60);
    }

    public static int ticksPerStep() {
        return read(TICKS_PER_STEP, 2);
    }

    public static int blocksPerStep() {
        return read(BLOCKS_PER_STEP, 1);
    }

    public static boolean loudGrowth() {
        try {
            return LOUD_GROWTH.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }

    public static int woolRadius() {
        return read(WOOL_RADIUS, 5);
    }

    public static int woolToMuffle() {
        return read(WOOL_TO_MUFFLE, 12);
    }

    public static int shriekerReach() {
        return read(SHRIEKER_REACH, 32);
    }

    public static int mobsDrawn() {
        return read(MOBS_DRAWN, 3);
    }

    public static int eyeAltarRange() {
        return read(EYE_ALTAR_RANGE, 48);
    }

    public static int eyeSearchChunks() {
        return read(EYE_SEARCH_CHUNKS, 100);
    }

    private static int read(ModConfigSpec.IntValue value, int fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }

    private static List<? extends String> readList(ModConfigSpec.ConfigValue<List<? extends String>> value, List<String> fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }
}
