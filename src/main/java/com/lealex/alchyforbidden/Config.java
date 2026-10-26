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
            .defineListAllowEmpty("tier2Cost", List.of("minecraft:sculk*16", "minecraft:amethyst_shard*8", "minecraft:echo_shard*2", "alchyforbidden:corruption_spore*8"), () -> "", value -> value instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TIER_3_COST = BUILDER
            .comment("What the altar asks for before it grows its third tier. One entry: \"item id*count\".")
            .defineListAllowEmpty("tier3Cost", List.of("minecraft:sculk_catalyst*1", "minecraft:crying_obsidian*4", "alchyforbidden:rift_alloy*4", "alchyforbidden:echo_ink*2"), () -> "", value -> value instanceof String);

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
            .defineListAllowEmpty("tier2Cost", List.of("minecraft:sculk_catalyst*2", "minecraft:echo_shard*4", "minecraft:amethyst_block*4", "alchyforbidden:corruption_spore*8"),
                    () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RESONATOR_TIER_3 = BUILDER
            .comment("And its third, given only once a warden has been killed beside it (the dead warden blasts the shriekers: no more pearls).")
            .defineListAllowEmpty("tier3Cost", List.of("minecraft:echo_shard*8", "minecraft:sculk_shrieker*4", "minecraft:sculk_catalyst*2", "alchyforbidden:rift_alloy*4"),
                    () -> "", value -> value instanceof String);
    public static final ModConfigSpec.IntValue RESONATOR_BREAK = BUILDER
            .comment("The chance, in a hundred, that a pearl breaks each time it passes a shrieker.")
            .defineInRange("breakChance", 45, 0, 100);
    public static final ModConfigSpec.IntValue RESONATOR_EYE_BREAK = BUILDER
            .comment("The same for an eye of ender (the resonator throws its eyes first).")
            .defineInRange("eyeBreakChance", 15, 0, 100);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RESONATOR_PER_BOOM = BUILDER
            .comment("Sculk Corruption each sonic boom makes, at tiers 1, 2 and 3.")
            .defineList("corruptionPerBoom", List.of("6", "10", "60"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.IntValue RESONATOR_BLAST = BUILDER
            .comment("Tier 3: ticks between two draws on the dead warden (all four shriekers at once; the stream takes 26 of them).")
            .defineInRange("wardenBlastTicks", 30, 30, 1200);
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
            .defineListAllowEmpty("tier2Cost", List.of("minecraft:bone_block*4", "minecraft:iron_bars*8", "minecraft:echo_shard*2", "alchyforbidden:wrath_ember*8"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PIT_TIER_3 = BUILDER
            .comment("And its third.")
            .defineListAllowEmpty("tier3Cost", List.of("minecraft:blaze_rod*8", "minecraft:crying_obsidian*4", "alchyforbidden:rift_alloy*4", "alchyforbidden:wrath_ember*16"), () -> "", value -> value instanceof String);
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
            .defineListAllowEmpty("tier2Cost", List.of("minecraft:soul_sand*16", "minecraft:ghast_tear*2", "minecraft:echo_shard*2", "alchyforbidden:sorrow_tear*8"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WELL_TIER_3 = BUILDER
            .comment("And its third.")
            .defineListAllowEmpty("tier3Cost", List.of("minecraft:sculk_catalyst*1", "minecraft:ghast_tear*4", "alchyforbidden:mourning_pearl*4", "alchyforbidden:sorrow_tear*16"), () -> "", value -> value instanceof String);
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
        BUILDER.comment("Echo Obelisks: pillars that carry Wrath, Sorrow and Sculk Corruption from a source to an altar, linked with the Attuning Fork.").push("obelisk");
    }

    public static final ModConfigSpec.IntValue RELAY_RANGE = BUILDER
            .comment("How far one link reaches (source to obelisk, obelisk to obelisk, obelisk to altar), in blocks.").defineInRange("linkRange", 24, 1, 64);
    public static final ModConfigSpec.IntValue OBELISK_BRANCHES = BUILDER
            .comment("How many places one obelisk may send to (its branches).").defineInRange("maxBranches", 4, 1, 16);
    public static final ModConfigSpec.IntValue RELAY_HOPS = BUILDER
            .comment("How many obelisks one chain may go through.").defineInRange("maxObelisks", 16, 1, 64);

    static {
        BUILDER.pop();
        BUILDER.comment("The Weeping Fountain: weeps what the world gives grudgingly, paid in essence from a nearby altar.").push("weeping_fountain");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FOUNTAIN_TIER_2 = BUILDER
            .comment("What the fountain asks for before it grows its second tier. One entry: \"item id*count\".")
            .defineListAllowEmpty("tier2Cost", List.of("minecraft:crying_obsidian*4", "minecraft:amethyst_block*4", "minecraft:soul_lantern*2", "alchyforbidden:sorrow_tear*8"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FOUNTAIN_TIER_3 = BUILDER
            .comment("What it asks for before its third tier.")
            .defineListAllowEmpty("tier3Cost", List.of("minecraft:sculk_catalyst*1", "minecraft:crying_obsidian*8", "alchyforbidden:mourning_pearl*4", "alchyforbidden:rift_alloy*2"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FOUNTAIN_PRODUCTS = BUILDER
            .comment("What it can weep. One entry: \"item id=essence*cost/seconds@tier\" (essence: wrath, sorrow or corruption;",
                    "the cost is paid from the nearest awake altar for each one; seconds at the first tier; the tier that opens it).")
            .defineListAllowEmpty("products", List.of(
                    "minecraft:amethyst_shard=sorrow*8/8@1",
                    "minecraft:crying_obsidian=sorrow*40/30@1",
                    "minecraft:echo_shard=corruption*100/60@1",
                    "alchyforbidden:wrath_ember=wrath*25/10@1",
                    "alchyforbidden:sorrow_tear=sorrow*25/10@1",
                    "alchyforbidden:corruption_spore=corruption*25/10@1",
                    "minecraft:ghast_tear=sorrow*60/30@2",
                    "minecraft:ender_pearl=wrath*30/15@2",
                    "alchyforbidden:mourning_pearl=sorrow*150/40@2",
                    "minecraft:sculk_catalyst=corruption*240/90@3",
                    "minecraft:wither_skeleton_skull=wrath*500/180@3",
                    "alchyforbidden:rift_silk=corruption*250/60@3"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FOUNTAIN_SPEED = BUILDER
            .comment("How much faster it weeps at each tier (first, second, third).")
            .defineListAllowEmpty("speed", List.of("1.0", "3.0", "30.0"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.IntValue FOUNTAIN_STORE = BUILDER
            .comment("How much of each essence it keeps from what its links send it.").defineInRange("essenceStore", 2000, 0, 1000000);
    public static final ModConfigSpec.IntValue FOUNTAIN_REACH = BUILDER
            .comment("How far it reaches an awake altar to drink from, in blocks.").defineInRange("altarReach", 24, 1, 64);

    static {
        BUILDER.pop();
        BUILDER.comment("What the active carvings cost while they work: paid from the body's carved reserve first, then from the",
                "vessels it carries; a carving that cannot be paid stops working until it can. Creative mode pays nothing.").push("inscription_upkeep");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> UPKEEP = BUILDER
            .comment("One entry: \"carving id=essence*amount\" (essence: wrath, sorrow or corruption). The amount is a second's worth",
                    "while it works (flying, gliding, walking on air / water / lava, climbing, invisible), or each time for",
                    "second_leap (a leap) and second_breath (a death refused). Carvings not listed cost nothing.")
            .defineListAllowEmpty("costs", List.of(
                    "echo_wings=corruption*3", "chained_wings=sorrow*1", "sky_walker=sorrow*2", "wall_climber=corruption*1",
                    "water_walker=sorrow*1", "lava_walker=wrath*2", "shadow_skin=sorrow*1", "second_leap=wrath*5",
                    "second_breath=corruption*150"), () -> "", value -> value instanceof String);

    static {
        BUILDER.pop();
        BUILDER.comment("The Binding Circle: binds spells to a soul (and unbinds them, by a ritual).").push("binding_circle");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> CIRCLE_TIER_2 = BUILDER
            .comment("What the circle asks for before it grows its second tier. One entry: \"item id*count\".")
            .defineListAllowEmpty("tier2Cost", List.of("minecraft:amethyst_block*4", "minecraft:echo_shard*4", "minecraft:book*4", "alchyforbidden:bone_ink*4"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CIRCLE_TIER_3 = BUILDER
            .comment("What it asks for before its third tier.")
            .defineListAllowEmpty("tier3Cost", List.of("minecraft:sculk_catalyst*2", "minecraft:nether_star*1", "alchyforbidden:echo_ink*4", "alchyforbidden:mourning_pearl*4"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.IntValue CIRCLE_STORE = BUILDER
            .comment("How much of each essence it keeps from what its links send it.").defineInRange("essenceStore", 2000, 0, 1000000);
    public static final ModConfigSpec.IntValue CIRCLE_REACH = BUILDER
            .comment("How far it reaches awake altars to draw a binding's essence from, in blocks.").defineInRange("altarReach", 24, 1, 64);

    static {
        BUILDER.pop();
        BUILDER.comment("Spells: what they strike.").push("spells");
    }

    /** What spells strike: hostile mobs only, every mob (golems, animals, villagers too), or every living thing. */
    public enum SpellTargets { HOSTILE, MOBS, ALL_LIVING }

    public static final ModConfigSpec.EnumValue<SpellTargets> SPELL_TARGETS = BUILDER
            .comment("What spells strike: HOSTILE (monsters only), MOBS (every mob: monsters, golems, animals, villagers) or ALL_LIVING (anything alive but armour stands). Your own tamed pets are always spared.")
            .defineEnum("targets", SpellTargets.MOBS);
    public static final ModConfigSpec.BooleanValue SPELL_PLAYERS = BUILDER
            .comment("Whether spells strike other players too.").define("hitPlayers", false);

    public static final ModConfigSpec.IntValue SPELL_DAMAGE = BUILDER
            .comment("Every spell's damage, in percent (on top of each spell's own powerPercent).").defineInRange("damagePercent", 100, 0, 1000);
    public static final ModConfigSpec.IntValue SPELL_COST = BUILDER
            .comment("Every spell's cast cost, in percent (on top of each spell's own castCost).").defineInRange("costPercent", 100, 0, 1000);
    public static final ModConfigSpec.IntValue SPELL_COOLDOWN = BUILDER
            .comment("Every spell's cooldown, in percent (on top of each spell's own cooldownSeconds).").defineInRange("cooldownPercent", 100, 1, 1000);
    public static final ModConfigSpec.IntValue SPELL_IMBUE = BUILDER
            .comment("The essences every ring imbuing costs at the Binding Circle, in percent (items are not changed).").defineInRange("imbueEssencePercent", 100, 0, 1000);

    /** One spell's own numbers (config spells.<id>). */
    public record SpellValues(ModConfigSpec.IntValue tier, ModConfigSpec.ConfigValue<List<? extends Number>> cast, ModConfigSpec.DoubleValue cooldown,
                              ModConfigSpec.IntValue power, ModConfigSpec.ConfigValue<List<? extends Number>> imbue,
                              ModConfigSpec.ConfigValue<List<? extends String>> items) {}

    private static final java.util.EnumMap<com.lealex.alchyforbidden.essence.Spell, SpellValues> SPELL_VALUES = new java.util.EnumMap<>(com.lealex.alchyforbidden.essence.Spell.class);

    static {
        for (com.lealex.alchyforbidden.essence.Spell spell : com.lealex.alchyforbidden.essence.Spell.values()) {
            BUILDER.comment("The spell " + spell.id() + ".").push(spell.id());
            int[] cast = spell.baseCast(), imbue = spell.baseImbue();
            SPELL_VALUES.put(spell, new SpellValues(
                    BUILDER.comment("The Binding Circle tier that opens it, and the ring tier it needs (1 to 3).")
                            .defineInRange("tier", spell.baseTier(), 1, 3),
                    BUILDER.comment("What a cast costs: [Wrath, Sorrow, Sculk Corruption].")
                            .defineList("castCost", List.<Number>of(cast[0], cast[1], cast[2]), () -> 0, value -> value instanceof Number n && n.intValue() >= 0),
                    BUILDER.comment("Seconds before it can be cast again.")
                            .defineInRange("cooldownSeconds", spell.baseCooldown() / 20.0, 0.5, 3600.0),
                    BUILDER.comment("Its damage, in percent.")
                            .defineInRange("powerPercent", 100, 0, 1000),
                    BUILDER.comment("What imbuing a ring with it costs in essences: [Wrath, Sorrow, Sculk Corruption].")
                            .defineList("imbueEssence", List.<Number>of(imbue[0], imbue[1], imbue[2]), () -> 0, value -> value instanceof Number n && n.intValue() >= 0),
                    BUILDER.comment("What imbuing a ring with it costs in items. One entry: \"item id*count\".")
                            .defineListAllowEmpty("imbueItems", List.of(spell.baseItems()), () -> "", value -> value instanceof String)));
            BUILDER.pop();
        }
    }

    static {
        BUILDER.pop();
        BUILDER.comment("The Rift Maw: its ritual calls a creature of the rift into an arena; slain, it is bound in a familiar ring.").push("rift_maw");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> MAW_TIER_2 = BUILDER
            .comment("What the maw asks for before it grows its second tier. One entry: \"item id*count\".")
            .defineListAllowEmpty("tier2Cost", List.of("minecraft:crying_obsidian*8", "minecraft:iron_chain*8", "minecraft:echo_shard*4", "alchyforbidden:wrath_ember*8"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MAW_TIER_3 = BUILDER
            .comment("What it asks for before its third tier.")
            .defineListAllowEmpty("tier3Cost", List.of("minecraft:sculk_catalyst*2", "minecraft:wither_skeleton_skull*2", "alchyforbidden:rift_alloy*4", "alchyforbidden:mourning_pearl*2"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MAW_TIER_4 = BUILDER
            .comment("What it asks for before its fourth tier (the void trials).")
            .defineListAllowEmpty("tier4Cost", List.of("minecraft:nether_star*2", "alchyforbidden:void_prism*4", "alchyforbidden:rift_silk*8", "alchyforbidden:void_ink*4"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.IntValue MAW_STORE = BUILDER
            .comment("How much of each essence the maw holds from its links.").defineInRange("essenceStore", 2000, 0, 1000000);
    public static final ModConfigSpec.IntValue MAW_REACH = BUILDER
            .comment("How far it reaches awake altars to draw a ritual's essence from, in blocks.").defineInRange("altarReach", 24, 1, 64);
    public static final ModConfigSpec.IntValue ARENA_1 = BUILDER
            .comment("The arena's radius round the maw at tier 1, in blocks.").defineInRange("arenaRadius1", 8, 4, 32);
    public static final ModConfigSpec.IntValue ARENA_2 = BUILDER
            .comment("At tier 2.").defineInRange("arenaRadius2", 10, 4, 32);
    public static final ModConfigSpec.IntValue ARENA_3 = BUILDER
            .comment("At tier 3.").defineInRange("arenaRadius3", 12, 4, 32);
    public static final ModConfigSpec.IntValue ARENA_4 = BUILDER
            .comment("At tier 4 (round the maw, for the rift's opening and every creature but the void bosses).").defineInRange("arenaRadius4", 14, 4, 32);
    public static final ModConfigSpec.IntValue VOID_RADIUS = BUILDER
            .comment("The radius of the arena in the void where the tier 4 bosses are fought, in blocks.").defineInRange("voidArenaRadius", 18, 8, 48);
    public static final ModConfigSpec.IntValue VOID_SECONDS = BUILDER
            .comment("How long a void trial may last before the rift casts the challenger out (a loss).").defineInRange("voidTrialSeconds", 600, 60, 7200);
    public static final ModConfigSpec.IntValue VOID_PRISMS = BUILDER
            .comment("Void Prisms given for beating a void boss, besides the bound ring and its residue.").defineInRange("voidTrialPrisms", 2, 0, 64);
    public static final ModConfigSpec.IntValue RITUAL_SECONDS = BUILDER
            .comment("How long a ritual may last before the rift takes its creature back (a loss).").defineInRange("ritualSeconds", 300, 30, 3600);
    public static final ModConfigSpec.ConfigValue<List<? extends Number>> ALTERED_1 = BUILDER
            .comment("At tier 1, the chances in percent that a called creature comes [plain, Rifted, Deep-Rifted, Void-Rifted].")
            .defineList("alteredChances1", List.<Number>of(70, 25, 5, 0), () -> 0, value -> value instanceof Number n && n.intValue() >= 0);
    public static final ModConfigSpec.ConfigValue<List<? extends Number>> ALTERED_2 = BUILDER
            .comment("At tier 2.").defineList("alteredChances2", List.<Number>of(50, 30, 15, 5), () -> 0, value -> value instanceof Number n && n.intValue() >= 0);
    public static final ModConfigSpec.ConfigValue<List<? extends Number>> ALTERED_3 = BUILDER
            .comment("At tier 3.").defineList("alteredChances3", List.<Number>of(35, 30, 20, 15), () -> 0, value -> value instanceof Number n && n.intValue() >= 0);
    public static final ModConfigSpec.ConfigValue<List<? extends Number>> ALTERED_4 = BUILDER
            .comment("At tier 4.").defineList("alteredChances4", List.<Number>of(25, 30, 25, 20), () -> 0, value -> value instanceof Number n && n.intValue() >= 0);
    public static final ModConfigSpec.IntValue MEND_SECONDS = BUILDER
            .comment("How long a familiar ring stays cracked after its familiar falls, in seconds.").defineInRange("mendSeconds", 180, 0, 86400);
    public static final ModConfigSpec.IntValue MEND_SORROW = BUILDER
            .comment("The Sorrow that mends a cracked ring at once at a maw, per ring tier.").defineInRange("mendSorrow", 30, 0, 100000);
    public static final ModConfigSpec.IntValue XP_PER_HEALTH = BUILDER
            .comment("Experience a familiar gets for a kill, in percent of the victim's max health.").defineInRange("xpPerHealth", 50, 0, 10000);
    public static final ModConfigSpec.IntValue RESIDUE_ODDS = BUILDER
            .comment("Each Rift Residue offered in a ritual's catalyst slot moves this many percent of the plain chance to the altered grades.").defineInRange("residueOdds", 5, 0, 100);
    public static final ModConfigSpec.IntValue RESIDUE_MAX = BUILDER
            .comment("How many Rift Residue a ritual takes at most as its catalyst.").defineInRange("residueMax", 8, 0, 64);
    public static final ModConfigSpec.IntValue NIGHT_ODDS = BUILDER
            .comment("Percent of the plain chance moved to the altered grades when the ritual begins at night (open sky worlds).").defineInRange("nightOdds", 5, 0, 100);
    public static final ModConfigSpec.IntValue NEW_MOON_ODDS = BUILDER
            .comment("The same on a new moon night (instead of the night's).").defineInRange("newMoonOdds", 12, 0, 100);
    public static final ModConfigSpec.IntValue DEEP_DARK_ODDS = BUILDER
            .comment("The same when the maw stands in the deep dark.").defineInRange("deepDarkOdds", 15, 0, 100);
    public static final ModConfigSpec.IntValue FILTER_RESIDUE = BUILDER
            .comment("An affix filter: Rift Residue per affix left out.").defineInRange("filterResiduePer", 2, 0, 64);
    public static final ModConfigSpec.IntValue FILTER_PRISM = BUILDER
            .comment("An affix filter: Void Prisms = (affixes left out)^2 / this, rounded up.").defineInRange("filterPrismDivisor", 6, 1, 1000);
    public static final ModConfigSpec.IntValue RELEASE_TIER = BUILDER
            .comment("Releasing a familiar gives Rift Residue: this times the creature's tier...").defineInRange("releasePerTier", 2, 0, 64);
    public static final ModConfigSpec.IntValue RELEASE_GRADE = BUILDER
            .comment("...plus this times its grade...").defineInRange("releasePerGrade", 3, 0, 64);
    public static final ModConfigSpec.IntValue RELEASE_LEVELS = BUILDER
            .comment("...plus one per this many levels.").defineInRange("releaseLevelsPerResidue", 4, 1, 100);
    public static final ModConfigSpec.IntValue RESIDUE_PER_GRADE = BUILDER
            .comment("Rift residue an altered creature leaves when slain in the arena, per grade.").defineInRange("residuePerGrade", 2, 0, 64);
    public static final ModConfigSpec.IntValue CORRUPT_CORRUPTION = BUILDER
            .comment("Sculk Corruption the corrupting ritual takes, times the grade it reaches.").defineInRange("corruptCorruption", 150, 0, 100000);
    public static final ModConfigSpec.IntValue CORRUPT_RESIDUE = BUILDER
            .comment("Rift residue the corrupting ritual takes, times the grade it reaches.").defineInRange("corruptResidue", 3, 0, 64);
    public static final ModConfigSpec.IntValue CORRUPT_FAIL = BUILDER
            .comment("The chance in percent that a familiar rejects the corrupting ritual (paid anyway, its ring cracked).").defineInRange("corruptFailChance", 25, 0, 100);
    public static final ModConfigSpec.IntValue CORRUPT_CRACK = BUILDER
            .comment("How long a ring that rejected the corrupting ritual stays cracked, in seconds.").defineInRange("corruptCrackSeconds", 1800, 0, 86400);
    public static final ModConfigSpec.IntValue SHARED_XP = BUILDER
            .comment("What each familiar out gets of a kill its owner makes, in percent of what it would get for that kill itself.").defineInRange("sharedXpPercent", 25, 0, 1000);

    static {
        BUILDER.pop();
        BUILDER.comment("The Essence Infuser: pours its essences into vessels, and at its third tier into a bound body's reserve.").push("essence_infuser");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> INFUSER_TIER_2 = BUILDER
            .comment("What the infuser asks for before it grows its second tier. One entry: \"item id*count\".")
            .defineListAllowEmpty("tier2Cost", List.of("minecraft:amethyst_block*4", "minecraft:glass_bottle*8", "alchyforbidden:sorrow_tear*4", "alchyforbidden:wrath_ember*4"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> INFUSER_TIER_3 = BUILDER
            .comment("What it asks for before its third tier.")
            .defineListAllowEmpty("tier3Cost", List.of("minecraft:echo_shard*8", "minecraft:sculk_catalyst*2", "minecraft:ender_eye*4", "alchyforbidden:rift_alloy*4"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> INFUSER_RATE = BUILDER
            .comment("How much essence a second it pours into a vessel at each tier (first, second, third).")
            .defineListAllowEmpty("vesselRate", List.of("20", "60", "200"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.IntValue INFUSER_LINK_RATE = BUILDER
            .comment("At its third tier, how much of each essence a second it pours into the bound player's carved reserve.")
            .defineInRange("bodyRate", 40, 0, 100000);
    public static final ModConfigSpec.IntValue INFUSER_STORE = BUILDER
            .comment("How much of each essence it keeps from what its links send it.").defineInRange("essenceStore", 4000, 0, 1000000);
    public static final ModConfigSpec.IntValue INFUSER_REACH = BUILDER
            .comment("How far it reaches an awake altar to draw from when its own store runs dry, in blocks.").defineInRange("altarReach", 24, 1, 64);

    static {
        BUILDER.pop();
        BUILDER.comment("Mobs condensed by a Rift Knot.").push("mob_stacks");
    }

    public static final ModConfigSpec.IntValue STACK_LOOT_ROLLS = BUILDER
            .comment("At most this many extra loot rolls when a condensed mob dies; past it the rolls made are scaled up to",
                    "the stack's size (same average loot, less work for big stacks dying often).")
            .defineInRange("maxLootRolls", 16, 1, 63);

    static {
        BUILDER.pop();
    }

    static {
        BUILDER.comment("The Inscribing Bier and the carvings it makes in a body.").push("inscribing_bier");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> BIER_TIER_2 = BUILDER
            .comment("What the bier asks for before it grows its second tier. One entry: \"item id*count\".")
            .defineListAllowEmpty("tier2Cost", List.of("minecraft:pointed_dripstone*8", "minecraft:amethyst_block*4", "minecraft:echo_shard*2", "alchyforbidden:bone_ink*8"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BIER_TIER_3 = BUILDER
            .comment("What it asks for before its third tier.")
            .defineListAllowEmpty("tier3Cost", List.of("minecraft:sculk_catalyst*2", "minecraft:crying_obsidian*8", "alchyforbidden:echo_ink*8", "alchyforbidden:mourning_pearl*2"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.IntValue BIER_REACH = BUILDER
            .comment("How far it reaches the altars and producing multiblocks it draws a carving's essences from, in blocks.").defineInRange("reach", 24, 1, 64);
    public static final ModConfigSpec.IntValue BIER_STORE = BUILDER
            .comment("How much of each essence it keeps from what its links send it.").defineInRange("essenceStore", 2000, 0, 1000000);
    public static final ModConfigSpec.IntValue MAX_INSCRIPTIONS = BUILDER
            .comment("The most carvings one body can ever carry (the bier's Slots tab opens them three at a time, 3 to 15).").defineInRange("maxCarvings", 15, 1, 64);
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SLOTS_PER_PART = BUILDER
            .comment("How many carvings one part of the body can hold, by the Bier's tier (first, second, third).")
            .defineList("carvingsPerPart", List.of("1", "2", "3"), () -> "", value -> value instanceof String);
    public static final ModConfigSpec.IntValue POWER_PER_CARVING = BUILDER
            .comment("Every carving a body carries beyond the first makes all of them this much stronger, in percent.")
            .defineInRange("powerPerCarving", 20, 0, 200);
    public static final ModConfigSpec.IntValue ECHO_SIGHT_RANGE = BUILDER
            .comment("Echo Sight: how far hostile mobs are seen glowing, in blocks.").defineInRange("echoSightRange", 16, 4, 64);
    public static final ModConfigSpec.IntValue RESERVE_CAPACITY = BUILDER
            .comment("Essence Reserve: how much of each essence the body holds.").defineInRange("reserveCapacity", 1000, 1, 1000000);

    static {
        BUILDER.pop();
    }

    public static List<Cost> bierCostFor(int tier) {
        return parseCosts(switch (tier) {
            case 2 -> readList(BIER_TIER_2, List.of("minecraft:pointed_dripstone*8", "minecraft:amethyst_block*4", "minecraft:echo_shard*2"));
            case 3 -> readList(BIER_TIER_3, List.of("minecraft:echo_shard*8", "minecraft:sculk_catalyst*2", "minecraft:crying_obsidian*8"));
            default -> List.of();
        });
    }

    public static int bierReach() { return read(BIER_REACH, 24); }
    public static int bierStore() { return read(BIER_STORE, 2000); }
    public static int maxInscriptions() { return read(MAX_INSCRIPTIONS, 21); }
    public static int carvingsPerPart(int tier) { return perTier(SLOTS_PER_PART, tier, new int[] {1, 2, 3}); }
    public static int powerPerCarving() { return read(POWER_PER_CARVING, 20); }
    public static int echoSightRange() { return read(ECHO_SIGHT_RANGE, 16); }
    public static int reserveCapacity() { return read(RESERVE_CAPACITY, 1000); }

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
    public static int resonatorPerBoom(int tier) { return perTier(RESONATOR_PER_BOOM, tier, new int[] {6, 10, 60}); }
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
    public static int relayRange() { return read(RELAY_RANGE, 24); }
    public static int relayMaxHops() { return read(RELAY_HOPS, 16); }
    public static int obeliskBranches() { return read(OBELISK_BRANCHES, 4); }
    public static int stackLootRolls() { return read(STACK_LOOT_ROLLS, 16); }
    public static int fountainReach() { return read(FOUNTAIN_REACH, 24); }
    public static int fountainStore() { return read(FOUNTAIN_STORE, 2000); }
    public static int infuserStore() { return read(INFUSER_STORE, 4000); }
    public static int circleStore() { return read(CIRCLE_STORE, 2000); }
    public static int circleReach() { return read(CIRCLE_REACH, 24); }

    public static List<Cost> circleCostFor(int tier) {
        return parseCosts(switch (tier) {
            case 2 -> readList(CIRCLE_TIER_2, List.of("minecraft:amethyst_block*4", "minecraft:echo_shard*4", "minecraft:book*4"));
            case 3 -> readList(CIRCLE_TIER_3, List.of("minecraft:echo_shard*12", "minecraft:sculk_catalyst*2", "minecraft:nether_star*1"));
            default -> List.of();
        });
    }

    /** What a carving costs while it works (or each use), null for nothing. */
    public record Upkeep(com.lealex.alchyforbidden.essence.Essence essence, int amount) {}

    public static @org.jspecify.annotations.Nullable Upkeep upkeep(com.lealex.alchyforbidden.essence.Inscription inscription) {
        for (String line : readList(UPKEEP, List.of("echo_wings=corruption*3", "chained_wings=sorrow*1", "sky_walker=sorrow*2",
                "wall_climber=corruption*1", "water_walker=sorrow*1", "lava_walker=wrath*2", "shadow_skin=sorrow*1",
                "second_leap=wrath*5", "second_breath=corruption*150"))) {
            try {
                String[] idRest = line.trim().split("=");
                if (!idRest[0].trim().equals(inscription.id())) continue;
                String[] cost = idRest[1].split("\\*");
                com.lealex.alchyforbidden.essence.Essence essence = com.lealex.alchyforbidden.essence.Essence.valueOf(cost[0].trim().toUpperCase(java.util.Locale.ROOT));
                int amount = Integer.parseInt(cost[1].trim());
                return amount > 0 ? new Upkeep(essence, amount) : null;
            } catch (RuntimeException badLine) {
                return null;
            }
        }
        return null;
    }
    public static int infuserReach() { return read(INFUSER_REACH, 24); }
    public static int infuserBodyRate() { return read(INFUSER_LINK_RATE, 40); }
    public static int infuserRate(int tier) { return perTier(INFUSER_RATE, tier, new int[] {20, 60, 200}); }

    public static List<Cost> infuserCostFor(int tier) {
        return parseCosts(switch (tier) {
            case 2 -> readList(INFUSER_TIER_2, List.of("minecraft:amethyst_block*4", "minecraft:glass_bottle*8", "minecraft:echo_shard*2"));
            case 3 -> readList(INFUSER_TIER_3, List.of("minecraft:echo_shard*8", "minecraft:sculk_catalyst*2", "minecraft:ender_eye*4"));
            default -> List.of();
        });
    }

    public static List<Cost> fountainCostFor(int tier) {
        return parseCosts(switch (tier) {
            case 2 -> readList(FOUNTAIN_TIER_2, List.of("minecraft:crying_obsidian*4", "minecraft:amethyst_block*4", "minecraft:soul_lantern*2"));
            case 3 -> readList(FOUNTAIN_TIER_3, List.of("minecraft:echo_shard*8", "minecraft:sculk_catalyst*1", "minecraft:crying_obsidian*8"));
            default -> List.of();
        });
    }

    /** What the Weeping Fountain weeps: the item, the essence and amount it costs, its time at tier 1, the tier opening it. */
    public record Product(Item item, com.lealex.alchyforbidden.essence.Essence essence, int cost, int seconds, int tier) {}

    public static List<Product> fountainProducts() {
        List<Product> products = new ArrayList<>();
        for (String line : readList(FOUNTAIN_PRODUCTS, List.of("minecraft:crying_obsidian=sorrow*40/30@1"))) {
            try { // "item=essence*cost/seconds@tier"
                String[] eq = line.trim().split("=", 2);
                String[] at = eq[1].split("@", 2);
                String[] slash = at[0].split("/", 2);
                String[] star = slash[0].split("\\*", 2);
                Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(eq[0].trim()));
                if (item == net.minecraft.world.item.Items.AIR) continue;
                com.lealex.alchyforbidden.essence.Essence essence = com.lealex.alchyforbidden.essence.Essence.valueOf(star[0].trim().toUpperCase(java.util.Locale.ROOT));
                products.add(new Product(item, essence, Math.max(0, Integer.parseInt(star[1].trim())), Math.max(1, Integer.parseInt(slash[1].trim())),
                        Math.max(1, Math.min(3, Integer.parseInt(at[1].trim())))));
            } catch (RuntimeException badLine) {
                // a line that does not read is left out
            }
        }
        return products;
    }

    public static double fountainSpeed(int tier) {
        List<? extends String> speeds = readList(FOUNTAIN_SPEED, List.of("1.0", "3.0", "30.0"));
        try {
            return Math.max(0.1, Double.parseDouble(speeds.get(Math.max(0, Math.min(tier, speeds.size()) - 1)).trim()));
        } catch (RuntimeException badLine) {
            return 1.0;
        }
    }

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

    public static SpellTargets spellTargets() {
        try {
            return SPELL_TARGETS.get();
        } catch (IllegalStateException notLoadedYet) {
            return SpellTargets.MOBS;
        }
    }

    private static int percent(ModConfigSpec.IntValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return 100;
        }
    }

    /** Three amounts (Wrath, Sorrow, Corruption) from a list, or the fallback when it is not three numbers. */
    private static int[] three(ModConfigSpec.ConfigValue<List<? extends Number>> value, int[] fallback) {
        try {
            List<? extends Number> list = value.get();
            if (list.size() != 3) return fallback;
            return new int[] {Math.max(0, list.get(0).intValue()), Math.max(0, list.get(1).intValue()), Math.max(0, list.get(2).intValue())};
        } catch (IllegalStateException | ClassCastException notLoadedYet) {
            return fallback;
        }
    }

    private static int[] scaled(int[] amounts, int percent) {
        for (int i = 0; i < amounts.length; i++) if (amounts[i] > 0) amounts[i] = Math.max(1, Math.round(amounts[i] * percent / 100F));
        return amounts;
    }

    public static int spellTier(com.lealex.alchyforbidden.essence.Spell spell) {
        try {
            return SPELL_VALUES.get(spell).tier().get();
        } catch (IllegalStateException notLoadedYet) {
            return spell.baseTier();
        }
    }

    /** A cast's cost (Wrath, Sorrow, Corruption), costPercent counted. */
    public static int[] spellCast(com.lealex.alchyforbidden.essence.Spell spell) {
        return scaled(three(SPELL_VALUES.get(spell).cast(), spell.baseCast()), percent(SPELL_COST));
    }

    /** The cooldown in ticks, cooldownPercent counted. */
    public static int spellCooldown(com.lealex.alchyforbidden.essence.Spell spell) {
        double seconds;
        try {
            seconds = SPELL_VALUES.get(spell).cooldown().get();
        } catch (IllegalStateException notLoadedYet) {
            seconds = spell.baseCooldown() / 20.0;
        }
        return (int) Math.max(10, Math.round(seconds * 20 * percent(SPELL_COOLDOWN) / 100.0));
    }

    /** What its damage is multiplied by: its powerPercent times damagePercent. */
    public static double spellPower(com.lealex.alchyforbidden.essence.Spell spell) {
        return percent(SPELL_VALUES.get(spell).power()) / 100.0 * percent(SPELL_DAMAGE) / 100.0;
    }

    /** Imbuing's essences (Wrath, Sorrow, Corruption), imbueEssencePercent counted. */
    public static int[] spellImbue(com.lealex.alchyforbidden.essence.Spell spell) {
        return scaled(three(SPELL_VALUES.get(spell).imbue(), spell.baseImbue()), percent(SPELL_IMBUE));
    }

    /** Imbuing's items, "item id*count" each. */
    public static List<? extends String> spellItems(com.lealex.alchyforbidden.essence.Spell spell) {
        try {
            return SPELL_VALUES.get(spell).items().get();
        } catch (IllegalStateException notLoadedYet) {
            return List.of(spell.baseItems());
        }
    }

    public static boolean spellHitsPlayers() {
        try {
            return SPELL_PLAYERS.get();
        } catch (IllegalStateException notLoadedYet) {
            return false;
        }
    }

    public static List<Cost> mawCostFor(int tier) {
        return parseCosts(switch (tier) {
            case 2 -> readList(MAW_TIER_2, List.of("minecraft:crying_obsidian*8", "minecraft:echo_shard*4", "minecraft:iron_chain*8"));
            case 3 -> readList(MAW_TIER_3, List.of("minecraft:echo_shard*12", "minecraft:sculk_catalyst*2", "minecraft:wither_skeleton_skull*2"));
            case 4 -> readList(MAW_TIER_4, List.of("minecraft:nether_star*2", "alchyforbidden:void_prism*4", "minecraft:echo_shard*24", "minecraft:crying_obsidian*16"));
            default -> List.of();
        });
    }

    public static int mawStore() { return read(MAW_STORE, 2000); }
    public static int mawReach() { return read(MAW_REACH, 24); }
    public static int ritualSeconds() { return read(RITUAL_SECONDS, 300); }
    public static int familiarMendSeconds() { return read(MEND_SECONDS, 180); }
    public static int familiarMendSorrow() { return read(MEND_SORROW, 30); }
    public static int familiarXpPerHealth() { return read(XP_PER_HEALTH, 50); }
    public static int familiarSharedXp() { return read(SHARED_XP, 25); }
    public static int residuePerGrade() { return read(RESIDUE_PER_GRADE, 2); }
    public static int filterResiduePer() { return read(FILTER_RESIDUE, 2); }
    public static int filterPrismDivisor() { return read(FILTER_PRISM, 6); }
    public static int releasePerTier() { return read(RELEASE_TIER, 2); }
    public static int releasePerGrade() { return read(RELEASE_GRADE, 3); }
    public static int releaseLevelsPerResidue() { return read(RELEASE_LEVELS, 4); }
    public static int residueOdds() { return read(RESIDUE_ODDS, 5); }
    public static int residueMax() { return read(RESIDUE_MAX, 8); }
    public static int nightOdds() { return read(NIGHT_ODDS, 5); }
    public static int newMoonOdds() { return read(NEW_MOON_ODDS, 12); }
    public static int deepDarkOdds() { return read(DEEP_DARK_ODDS, 15); }
    public static int corruptCorruption() { return read(CORRUPT_CORRUPTION, 150); }
    public static int corruptResidue() { return read(CORRUPT_RESIDUE, 3); }
    public static int corruptFailChance() { return read(CORRUPT_FAIL, 25); }
    public static int corruptCrackSeconds() { return read(CORRUPT_CRACK, 1800); }

    public static int arenaRadius(int tier) {
        return tier >= 4 ? read(ARENA_4, 14) : tier == 3 ? read(ARENA_3, 12) : tier == 2 ? read(ARENA_2, 10) : read(ARENA_1, 8);
    }

    public static int voidArenaRadius() { return read(VOID_RADIUS, 18); }
    public static int voidTrialSeconds() { return read(VOID_SECONDS, 600); }
    public static int voidTrialPrisms() { return read(VOID_PRISMS, 2); }

    /** The chances (plain, Rifted, Deep-Rifted, Void-Rifted) of a creature called at this tier. */
    public static int[] alteredChances(int tier) {
        int[] fallback = tier >= 4 ? new int[] {25, 30, 25, 20} : tier == 3 ? new int[] {35, 30, 20, 15} : tier == 2 ? new int[] {50, 30, 15, 5} : new int[] {70, 25, 5, 0};
        try {
            List<? extends Number> list = (tier >= 4 ? ALTERED_4 : tier == 3 ? ALTERED_3 : tier == 2 ? ALTERED_2 : ALTERED_1).get();
            if (list.size() != 4) return fallback;
            int[] out = new int[4];
            for (int i = 0; i < 4; i++) out[i] = Math.max(0, list.get(i).intValue());
            return out;
        } catch (IllegalStateException | ClassCastException notLoadedYet) {
            return fallback;
        }
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
