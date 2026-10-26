package com.lealex.alchyforbidden.familiar;

import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

/**
 * The creatures of the rift a Rift Maw can call (each by its own lure) and a familiar ring can hold once it is slain in
 * the arena. Tier = the maw tier that can call it and the ring tier that can hold it; bosses fight with a bar.
 * Summoning costs Wrath / Sorrow / Corruption, lost with the lure if the ritual fails.
 */
public enum RiftCreature {
    // id, tier, boss, summon Wrath / Sorrow / Corruption, colour, entity type
    SCULK_CRAWLER("sculk_crawler", 1, false, 40, 20, 40, 0x2FC6B0, () -> com.lealex.alchyforbidden.registry.ModRegistries.SCULK_CRAWLER.get()),
    VEIL_WISP("veil_wisp", 1, false, 20, 40, 30, 0xB8A0FF, () -> com.lealex.alchyforbidden.registry.ModRegistries.VEIL_WISP.get()),
    SHARD_HOUND("shard_hound", 1, false, 50, 10, 30, 0xC48CFF, () -> com.lealex.alchyforbidden.registry.ModRegistries.SHARD_HOUND.get()),
    SHACKLED_BRUTE("shackled_brute", 2, false, 120, 40, 80, 0x6A5A7A, () -> com.lealex.alchyforbidden.registry.ModRegistries.SHACKLED_BRUTE.get()),
    HOLLOW_STALKER("hollow_stalker", 2, false, 60, 80, 100, 0x8A8AA0, () -> com.lealex.alchyforbidden.registry.ModRegistries.HOLLOW_STALKER.get()),
    ABYSSAL_KNIGHT("abyssal_knight", 3, true, 300, 100, 250, 0x8A4AE8, () -> com.lealex.alchyforbidden.registry.ModRegistries.ABYSSAL_KNIGHT.get()),
    THE_CHOIR("the_choir", 3, true, 120, 300, 250, 0x3FD8C8, () -> com.lealex.alchyforbidden.registry.ModRegistries.THE_CHOIR.get()),
    // the void's great ones (tier 4): fought in the void trials (familiar/VoidTrials)
    WEEPING_CHANDELIER("weeping_chandelier", 4, true, 300, 500, 400, 0x6CE0E8, () -> com.lealex.alchyforbidden.registry.ModRegistries.WEEPING_CHANDELIER.get()),
    VOID_GOLEM("void_golem", 4, true, 500, 300, 400, 0x6A3AA8, () -> com.lealex.alchyforbidden.registry.ModRegistries.VOID_GOLEM.get()),
    BONE_DRAGON("bone_dragon", 4, true, 400, 400, 500, 0xD8D0C0, () -> com.lealex.alchyforbidden.registry.ModRegistries.BONE_DRAGON.get()),
    SEVERED_SERAPH("severed_seraph", 4, true, 400, 500, 250, 0xE8E0FF, () -> com.lealex.alchyforbidden.registry.ModRegistries.SEVERED_SERAPH.get());

    private final String id;
    private final int tier, color;
    private final boolean boss;
    private final int[] summon;
    private final Supplier<? extends EntityType<? extends RiftBeast>> type;

    RiftCreature(String id, int tier, boolean boss, int wrath, int sorrow, int corruption, int color, Supplier<? extends EntityType<? extends RiftBeast>> type) {
        this.id = id;
        this.tier = tier;
        this.boss = boss;
        this.summon = new int[] {wrath, sorrow, corruption};
        this.color = color;
        this.type = type;
    }

    public String id() {
        return id;
    }

    public int tier() {
        return tier;
    }

    public boolean boss() {
        return boss;
    }

    /** What calling it costs in each essence (Wrath, Sorrow, Corruption; a copy). */
    public int[] summonCosts() {
        return summon.clone();
    }

    public int color() {
        return color;
    }

    public EntityType<? extends RiftBeast> type() {
        return type.get();
    }

    public Component displayName() {
        return Component.translatable("entity.alchyforbidden." + id);
    }

    /** A trait a familiar learns at a level (lang trait.alchyforbidden.<creature>.<id> and .desc). */
    public record Trait(int level, String id) {}

    /** The levels at which every creature learns something. */
    public static final int[] MILESTONES = {5, 10, 15, 20};

    /** Its four traits, at levels 5, 10, 15 and 20. */
    public java.util.List<Trait> traits() {
        return switch (this) {
            case SCULK_CRAWLER -> java.util.List.of(new Trait(5, "clinging_bite"), new Trait(10, "sculk_burst"), new Trait(15, "pounce"), new Trait(20, "echo_strike"));
            case VEIL_WISP -> java.util.List.of(new Trait(5, "soul_siphon"), new Trait(10, "veil"), new Trait(15, "piercing_bolts"), new Trait(20, "twin_flame"));
            case SHARD_HOUND -> java.util.List.of(new Trait(5, "keen_fangs"), new Trait(10, "pack_bond"), new Trait(15, "shatter"), new Trait(20, "prismatic_hide"));
            case SHACKLED_BRUTE -> java.util.List.of(new Trait(5, "crushing_blows"), new Trait(10, "taunt"), new Trait(15, "iron_will"), new Trait(20, "chained_blows"));
            case HOLLOW_STALKER -> java.util.List.of(new Trait(5, "backstab"), new Trait(10, "lurker"), new Trait(15, "bleed"), new Trait(20, "death_mark"));
            case ABYSSAL_KNIGHT -> java.util.List.of(new Trait(5, "abyssal_plate"), new Trait(10, "cleave"), new Trait(15, "unyielding"), new Trait(20, "echo_knight"));
            case THE_CHOIR -> java.util.List.of(new Trait(5, "harmony"), new Trait(10, "discord"), new Trait(15, "choir_of_many"), new Trait(20, "requiem"));
            case WEEPING_CHANDELIER -> java.util.List.of(new Trait(5, "soul_candles"), new Trait(10, "drowned_guests"), new Trait(15, "molten_wax"), new Trait(20, "eternal_flame"));
            case VOID_GOLEM -> java.util.List.of(new Trait(5, "obsidian_shell"), new Trait(10, "taunt"), new Trait(15, "crushing_weight"), new Trait(20, "void_heart"));
            case BONE_DRAGON -> java.util.List.of(new Trait(5, "hollow_bones"), new Trait(10, "sculk_marrow"), new Trait(15, "bone_armor"), new Trait(20, "death_rattle"));
            case SEVERED_SERAPH -> java.util.List.of(new Trait(5, "feathered_blades"), new Trait(10, "grace"), new Trait(15, "severed_halo"), new Trait(20, "ascension"));
        };
    }

    /**
     * A move a familiar learns at a level and uses in a fight, in turn with the others it knows: strike = the tick its
     * effect lands, length = how long it plays, cooldown in ticks, the distance to its target it is used from.
     * Lang move.alchyforbidden.<creature>.<id> and .desc.
     */
    public record Move(int level, String id, int strike, int length, int cooldown, double minRange, double maxRange) {}

    /** Its four moves, at levels 5, 10, 15 and 20. */
    public java.util.List<Move> moves() {
        return switch (this) {
            case SCULK_CRAWLER -> java.util.List.of(new Move(5, "lunge", 8, 24, 120, 3.5, 10), new Move(10, "sculk_spit", 10, 18, 160, 3, 14),
                    new Move(15, "burrow", 30, 40, 300, 5, 16), new Move(20, "shriek", 20, 34, 400, 0, 7));
            case VEIL_WISP -> java.util.List.of(new Move(5, "rift_barrage", 12, 20, 140, 4, 16), new Move(10, "blink_away", 2, 10, 120, 0, 3.5),
                    new Move(15, "soul_snare", 10, 16, 240, 3, 14), new Move(20, "spectral_rain", 10, 50, 400, 3, 16));
            case SHARD_HOUND -> java.util.List.of(new Move(5, "crystal_charge", 10, 22, 140, 3, 12), new Move(10, "shard_volley", 12, 20, 160, 0, 8),
                    new Move(15, "howl", 10, 30, 400, 0, 10), new Move(20, "crystallize", 12, 44, 300, 2, 10));
            case SHACKLED_BRUTE -> java.util.List.of(new Move(5, "ground_slam", 12, 22, 160, 0, 4), new Move(10, "chain_hook", 10, 18, 200, 4, 12),
                    new Move(15, "earthbreaker", 24, 34, 300, 4, 14), new Move(20, "wrecking_flail", 10, 70, 400, 0, 4));
            case HOLLOW_STALKER -> java.util.List.of(new Move(5, "shadow_step", 6, 14, 120, 3, 14), new Move(10, "fade", 4, 8, 300, 4, 20),
                    new Move(15, "rending_flurry", 4, 26, 200, 0, 3), new Move(20, "hollow_grasp", 8, 44, 360, 0, 3.4));
            case ABYSSAL_KNIGHT -> java.util.List.of(new Move(5, "rift_slash", 10, 18, 140, 0, 8), new Move(10, "void_charge", 10, 24, 200, 4, 14),
                    new Move(15, "abyssal_smite", 26, 36, 300, 3, 16), new Move(20, "rift_collapse", 40, 50, 500, 0, 8));
            case THE_CHOIR -> java.util.List.of(new Move(5, "sonic_boom", 20, 26, 160, 3, 18), new Move(10, "wail_of_sorrow", 10, 24, 240, 0, 10),
                    new Move(15, "canticle", 6, 28, 200, 4, 18), new Move(20, "crescendo", 40, 48, 500, 0, 8));
            case WEEPING_CHANDELIER -> java.util.List.of(new Move(5, "wax_rain", 10, 40, 200, 0, 18), new Move(10, "soul_flames", 10, 28, 180, 3, 20),
                    new Move(15, "chain_lash", 14, 24, 220, 0, 14), new Move(20, "free_ghosts", 10, 20, 500, 0, 20));
            case VOID_GOLEM -> java.util.List.of(new Move(5, "slam", 18, 34, 160, 0, 10), new Move(10, "slab_throw", 16, 28, 200, 4, 22),
                    new Move(15, "stone_guard", 4, 80, 320, 0, 16), new Move(20, "void_pull", 30, 44, 400, 0, 14));
            case BONE_DRAGON -> java.util.List.of(new Move(5, "bone_rain", 10, 40, 200, 0, 22), new Move(10, "sonic_breath", 20, 44, 240, 3, 22),
                    new Move(15, "wyrm_dive", 24, 40, 260, 4, 22), new Move(20, "sonic_roar", 14, 24, 300, 0, 10));
            case SEVERED_SERAPH -> java.util.List.of(new Move(5, "heavenly_dive", 26, 40, 200, 4, 18), new Move(10, "blade_fan", 10, 24, 140, 3, 16),
                    new Move(15, "feather_storm", 10, 40, 240, 0, 12), new Move(20, "blink_slash", 6, 36, 300, 0, 16));
        };
    }

    public @Nullable Move moveAt(int level) {
        for (Move move : moves()) if (move.level() == level) return move;
        return null;
    }

    public Component moveName(Move move) {
        return Component.translatable("move.alchyforbidden." + id + "." + move.id());
    }

    public Component moveDescription(Move move) {
        return Component.translatable("move.alchyforbidden." + id + "." + move.id() + ".desc");
    }

    public @Nullable Trait traitAt(int level) {
        for (Trait trait : traits()) if (trait.level() == level) return trait;
        return null;
    }

    public Component traitName(Trait trait) {
        return Component.translatable("trait.alchyforbidden." + id + "." + trait.id());
    }

    public Component traitDescription(Trait trait) {
        return Component.translatable("trait.alchyforbidden." + id + "." + trait.id() + ".desc");
    }

    public static @Nullable RiftCreature byId(String id) {
        for (RiftCreature creature : values()) if (creature.id.equals(id)) return creature;
        return null;
    }

    // ---- How altered it came (the AlchyFarm crops' grades: Rifted, Deep-Rifted, Void-Rifted) ----

    /** The word before an altered creature's name ("" when not altered). */
    public static String grade(int corruption) {
        return switch (corruption) {
            case 1 -> "Rifted ";
            case 2 -> "Deep-Rifted ";
            case 3 -> "Void-Rifted ";
            default -> "";
        };
    }

    /** The colour of an altered creature's name: light violet, dark violet at the top. */
    public static int gradeColor(int corruption) {
        return switch (corruption) {
            case 1 -> 0xD8B4FF;
            case 2 -> 0xB98CFF;
            case 3 -> 0x8A4AE8;
            default -> 0xE6DDF2;
        };
    }

    /** Its full name: the name in its own colour (bold for the great ones), then how altered it came, in the grade's colour. */
    public Component fullName(int corruption) {
        net.minecraft.network.chat.MutableComponent name = Component.empty().append(displayName().copy().withStyle(s -> s.withColor(color()).withBold(boss())));
        if (corruption > 0) name.append(Component.literal(" (" + grade(corruption).trim() + ")").withStyle(s -> s.withColor(gradeColor(corruption)).withBold(false)));
        return name;
    }
}
