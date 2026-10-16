package com.lealex.alchyforbidden.rite;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * One rite of the altar (data/alchyforbidden/altar_rite/*.json): what lies on its stands (one item each, in any
 * order), what it makes, the altar tier it needs and how long it takes.
 *
 * <pre>
 * {"ingredients": ["minecraft:sculk", "#minecraft:candles", ...], "result": {"id": "minecraft:echo_shard"},
 *  "tier": 1, "ticks": 100, "wrath": 0, "sorrow": 20, "corruption": 10}
 * </pre>
 * The essences are spent from the altar's reserve when the rite begins (given back if it breaks); the rite's
 * animation takes its colours from them and grows with their total.
 * The altar has four stands at its first two tiers and eight at the third, so a rite takes one to eight
 * ingredients. Higher tiers are how the altar learns more rites: a rite above the altar's tier shows locked.
 */
public record AltarRite(List<Ingredient> ingredients, ItemStackTemplate output, int tier, int ticks, int wrath, int sorrow, int corruption) {
    public static final Codec<AltarRite> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Ingredient.CODEC.listOf(1, 8).fieldOf("ingredients").forGetter(AltarRite::ingredients),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(AltarRite::output),
            Codec.intRange(1, 3).optionalFieldOf("tier", 1).forGetter(AltarRite::tier),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("ticks", 100).forGetter(AltarRite::ticks),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("wrath", 0).forGetter(AltarRite::wrath),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("sorrow", 0).forGetter(AltarRite::sorrow),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("corruption", 0).forGetter(AltarRite::corruption)
    ).apply(instance, AltarRite::new));

    /**
     * What it makes, a fresh stack each time. (Read as a template: a whole stack cannot be decoded while the data
     * loads, the items' components are not bound yet, and every rite failed to load.)
     */
    public ItemStack result() {
        return output.create();
    }

    /** What it costs of each essence, in Essence order (wrath, sorrow, corruption). */
    public int[] costs() {
        return new int[] {wrath, sorrow, corruption};
    }

    public int totalCost() {
        return wrath + sorrow + corruption;
    }

    /** True if these items (the stands' non-empty items, one each) are exactly this rite's ingredients, in any order. */
    public boolean matches(List<ItemStack> items) {
        if (items.size() != ingredients.size()) return false;
        return assign(new ArrayList<>(items), 0);
    }

    // Small backtracking: ingredient i takes one of the items left (tags can overlap, so greedy is not enough)
    private boolean assign(List<ItemStack> left, int i) {
        if (i == ingredients.size()) return true;
        Ingredient ingredient = ingredients.get(i);
        for (int k = 0; k < left.size(); k++) {
            ItemStack item = left.get(k);
            if (!ingredient.test(item)) continue;
            left.remove(k);
            if (assign(left, i + 1)) return true;
            left.add(k, item);
        }
        return false;
    }
}
