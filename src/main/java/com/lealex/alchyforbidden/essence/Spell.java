package com.lealex.alchyforbidden.essence;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * The spells a ring can be imbued with at the Binding Circle (essence/Spells): each opened by a tier of the circle,
 * cast for some of each essence (paid from the carved reserve, then vessels), with a cooldown; imbuing it costs items
 * and essences. Few and cheap at the first tier; the great ones at the third, dear and slow to come back.
 * The numbers below are the defaults: the config (spells.<id>) has the last word, read through the getters.
 */
public enum Spell {
    // id, circle tier, cast Wrath / Sorrow / Corruption, cooldown (ticks), colour, imbue Wrath / Sorrow / Corruption, items
    RIFT_BOLT("rift_bolt", 1, 6, 0, 2, 20, 0xE0485A, 30, 0, 10, "minecraft:blaze_powder*4", "alchyforbidden:wrath_ember*4"),
    MIST_STEP("mist_step", 1, 0, 8, 4, 60, 0x9FE6E0, 0, 30, 10, "minecraft:ender_pearl*4", "alchyforbidden:sorrow_tear*4"),
    WISP_LIGHT("wisp_light", 1, 0, 3, 1, 20, 0xFFF0B0, 0, 15, 5, "minecraft:glowstone_dust*16", "alchyforbidden:sorrow_tear*2"),
    BONE_SPEAR("bone_spear", 1, 8, 2, 0, 40, 0xE8E0C8, 30, 10, 0, "minecraft:bone_block*4", "alchyforbidden:wrath_ember*4"),
    SOUL_SHIELD("soul_shield", 2, 5, 20, 0, 400, 0x3FC6D0, 20, 60, 0, "minecraft:shield*1", "minecraft:soul_sand*8", "alchyforbidden:sorrow_tear*4"),
    ECHO_PULSE("echo_pulse", 2, 0, 4, 8, 200, 0x3FD8C8, 0, 20, 30, "minecraft:sculk_sensor*1", "minecraft:echo_shard*2", "alchyforbidden:corruption_spore*4"),
    GREAT_LEAP("great_leap", 2, 10, 0, 4, 100, 0xE89A5A, 30, 0, 20, "minecraft:rabbit_foot*2", "minecraft:slime_ball*4", "alchyforbidden:wrath_ember*4"),
    DRIFTING_SOUL("drifting_soul", 2, 0, 8, 2, 200, 0xD8F0F4, 0, 30, 10, "minecraft:feather*8", "minecraft:phantom_membrane*1", "alchyforbidden:sorrow_tear*4"),
    BILE_SPRAY("bile_spray", 2, 4, 0, 10, 100, 0x8AC040, 20, 0, 40, "minecraft:rotten_flesh*16", "minecraft:slime_ball*4", "alchyforbidden:corruption_spore*4"),
    SCULK_SNARE("sculk_snare", 2, 0, 6, 12, 200, 0x14A090, 0, 20, 40, "minecraft:sculk*16", "minecraft:cobweb*4", "alchyforbidden:corruption_spore*4"),
    SCULK_FANGS("sculk_fangs", 2, 20, 0, 10, 160, 0xB0304A, 80, 0, 30, "minecraft:sculk*16", "minecraft:bone*8", "alchyforbidden:wrath_ember*4"),
    WAIL("wail", 2, 6, 25, 0, 300, 0xC8D8E8, 20, 80, 0, "minecraft:goat_horn*1", "minecraft:ghast_tear*2", "alchyforbidden:mourning_pearl*1"),
    SOUL_DRAIN("soul_drain", 2, 10, 20, 0, 200, 0x6FE0F0, 30, 70, 10, "minecraft:soul_lantern*2", "minecraft:ghast_tear*1", "alchyforbidden:mourning_pearl*1"),
    SHADOW_VEIL("shadow_veil", 2, 0, 25, 10, 900, 0x4A4A6A, 0, 80, 30, "minecraft:fermented_spider_eye*4", "minecraft:ink_sac*8", "alchyforbidden:mourning_pearl*2"),
    RIFT_POCKET("rift_pocket", 2, 0, 5, 12, 200, 0x7A4AC8, 0, 20, 60, "minecraft:ender_chest*1", "alchyforbidden:rift_alloy*1", "alchyforbidden:corruption_spore*4"),
    VERDANT_PULSE("verdant_pulse", 2, 5, 15, 0, 400, 0x7AE07A, 10, 60, 0, "minecraft:bone_meal*32", "minecraft:moss_block*8", "alchyforbidden:sorrow_tear*4"),
    RIFT_BORE("rift_bore", 2, 8, 0, 16, 100, 0x9A6AE0, 30, 0, 80, "minecraft:diamond_pickaxe*1", "minecraft:tnt*4", "alchyforbidden:rift_alloy*2"),
    RIFT_CHAINS("rift_chains", 3, 10, 10, 30, 500, 0x9C7AE8, 40, 40, 100, "minecraft:iron_chain*8", "alchyforbidden:rift_alloy*2", "alchyforbidden:rift_silk*2"),
    WRATH_NOVA("wrath_nova", 3, 40, 0, 10, 400, 0xFF7A3A, 120, 0, 40, "minecraft:blaze_rod*4", "minecraft:fire_charge*8", "alchyforbidden:wrath_ember*8"),
    GORE_BURST("gore_burst", 3, 35, 0, 15, 500, 0xB01020, 120, 20, 40, "minecraft:tnt*2", "minecraft:redstone_block*4", "alchyforbidden:wrath_ember*8"),
    RIFT_METEOR("rift_meteor", 3, 50, 0, 25, 800, 0xFF7030, 150, 0, 80, "minecraft:magma_block*8", "alchyforbidden:rift_alloy*4", "alchyforbidden:wrath_ember*8"),
    STASIS_FIELD("stasis_field", 3, 0, 45, 25, 1200, 0xA0D8FF, 0, 150, 80, "minecraft:clock*2", "minecraft:packed_ice*8", "alchyforbidden:mourning_pearl*4"),
    SHADOW_STRIKE("shadow_strike", 3, 30, 20, 30, 900, 0x3A2A5A, 120, 80, 120, "minecraft:ender_eye*4", "minecraft:ink_sac*16", "alchyforbidden:rift_silk*4"),
    RIFT_GATE("rift_gate", 3, 0, 20, 40, 600, 0xC08CFF, 0, 80, 200, "minecraft:ender_eye*8", "minecraft:crying_obsidian*4", "alchyforbidden:rift_silk*2"),
    BLOOD_RITE("blood_rite", 3, 30, 0, 10, 1800, 0xB01830, 200, 0, 80, "minecraft:wither_skeleton_skull*1", "minecraft:golden_apple*2", "alchyforbidden:wrath_ember*16"),
    SONIC_BOOM("sonic_boom", 3, 30, 0, 70, 900, 0x2FC6B0, 80, 0, 250, "minecraft:sculk_shrieker*1", "minecraft:echo_shard*8", "alchyforbidden:abyssal_core*1"),
    BLACK_HOLE("black_hole", 3, 20, 20, 80, 1800, 0x5A3A8A, 60, 80, 250, "minecraft:crying_obsidian*8", "alchyforbidden:rift_silk*4", "alchyforbidden:abyssal_core*1"),
    RIFT_ANCHOR("rift_anchor", 3, 0, 30, 50, 2400, 0xE0B0FF, 0, 60, 150, "minecraft:lodestone*1", "alchyforbidden:rift_silk*4", "alchyforbidden:mourning_pearl*4"),
    SCULK_PLAGUE("sculk_plague", 3, 10, 20, 70, 1800, 0x0A6A60, 40, 60, 250, "minecraft:sculk_catalyst*2", "alchyforbidden:corruption_spore*16", "alchyforbidden:abyssal_core*1"),
    SOUL_STORM("soul_storm", 3, 20, 70, 20, 2400, 0x6FE8FF, 60, 250, 80, "minecraft:soul_lantern*8", "alchyforbidden:mourning_pearl*8", "alchyforbidden:abyssal_core*1"),
    ABYSSAL_FORM("abyssal_form", 3, 60, 60, 90, 6000, 0x5A2AA0, 150, 150, 300, "minecraft:nether_star*1", "alchyforbidden:rift_silk*4", "alchyforbidden:abyssal_core*2");

    public record Cost(Item item, int count) {}

    private final String id;
    private final int tier, cooldown, color, wrath, sorrow, corruption;
    private final int[] cast;
    private final String[] items;
    private @Nullable List<Cost> costs;
    private @Nullable List<? extends String> costsFrom;

    Spell(String id, int tier, int castWrath, int castSorrow, int castCorruption, int cooldown, int color, int wrath, int sorrow, int corruption, String... items) {
        this.id = id;
        this.tier = tier;
        this.cast = new int[] {castWrath, castSorrow, castCorruption};
        this.cooldown = cooldown;
        this.color = color;
        this.wrath = wrath;
        this.sorrow = sorrow;
        this.corruption = corruption;
        this.items = items;
    }

    public String id() {
        return id;
    }

    // ---- the defaults (the config's own defaults) ----

    public int baseTier() {
        return tier;
    }

    public int[] baseCast() {
        return cast.clone();
    }

    public int baseCooldown() {
        return cooldown;
    }

    public int[] baseImbue() {
        return new int[] {wrath, sorrow, corruption};
    }

    public String[] baseItems() {
        return items.clone();
    }

    // ---- what the game uses (config) ----

    /** The Binding Circle's tier that opens it (and the ring tier it needs). */
    public int tier() {
        return com.lealex.alchyforbidden.Config.spellTier(this);
    }

    /** What casting it costs in this essence (before a ring's gems). */
    public int castCost(Essence essence) {
        return castCosts()[essence.ordinal()];
    }

    /** Its cast costs, Wrath / Sorrow / Corruption (a fresh array). */
    public int[] castCosts() {
        return com.lealex.alchyforbidden.Config.spellCast(this);
    }

    /** The essence it costs most of (its colour on the HUD, its main cost). */
    public Essence essence() {
        int[] now = castCosts();
        Essence best = Essence.WRATH;
        for (Essence e : Essence.values()) if (now[e.ordinal()] > now[best.ordinal()]) best = e;
        return best;
    }

    /** Ticks before it can be cast again. */
    public int cooldown() {
        return com.lealex.alchyforbidden.Config.spellCooldown(this);
    }

    /** What its damage is multiplied by (config powerPercent x damagePercent). */
    public double power() {
        return com.lealex.alchyforbidden.Config.spellPower(this);
    }

    public int color() {
        return color;
    }

    /** What binding it costs in each essence. */
    public int bindEssence(Essence essence) {
        return com.lealex.alchyforbidden.Config.spellImbue(this)[essence.ordinal()];
    }

    /** What binding it costs in items. */
    public List<Cost> bindItems() {
        List<? extends String> from = com.lealex.alchyforbidden.Config.spellItems(this);
        if (costs == null || costsFrom != from) { // parsed again only when the config changed
            List<Cost> out = new ArrayList<>();
            for (String entry : from) {
                try {
                    String[] parts = entry.trim().split("\\*");
                    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(parts[0]));
                    int count = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
                    if (item != net.minecraft.world.item.Items.AIR && count > 0) out.add(new Cost(item, count));
                } catch (RuntimeException badEntry) {
                    // a mistyped entry is skipped
                }
            }
            costs = List.copyOf(out);
            costsFrom = from;
        }
        return costs;
    }

    public Component displayName() {
        return Component.translatable("spell.alchyforbidden." + id);
    }

    public Component description() {
        return Component.translatable("spell.alchyforbidden." + id + ".effect");
    }

    /** The HUD / screen icon. */
    public Identifier icon() {
        return Identifier.fromNamespaceAndPath("alchyforbidden", "textures/spell/" + id + ".png");
    }

    public static @Nullable Spell byId(String id) {
        for (Spell spell : values()) {
            if (spell.id.equals(id)) return spell;
        }
        return null;
    }
}
