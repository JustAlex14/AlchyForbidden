package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.essence.Spell;
import com.lealex.alchyforbidden.essence.VesselContents;
import com.lealex.alchyforbidden.item.SpellRingItem;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import org.jspecify.annotations.Nullable;

/**
 * Item tints used by the item model files: alchyforbidden:vessel_essence colours a vessel's liquid layer by the essence
 * it holds (dark glass when empty), alchyforbidden:ring_spell colours a ring's stones by its spell (a dull violet
 * while blank).
 */
public final class ItemTints {
    private ItemTints() {}

    private static final int EMPTY_VESSEL = 0xFF2A2236, BLANK_RING = 0xFF8A7AA8;

    public record VesselEssence() implements ItemTintSource {
        public static final MapCodec<VesselEssence> CODEC = MapCodec.unit(new VesselEssence());

        @Override
        public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
            VesselContents contents = stack.get(ModRegistries.VESSEL_CONTENTS.get());
            return contents == null || contents.amount() <= 0 ? EMPTY_VESSEL : 0xFF000000 | contents.essence().color();
        }

        @Override
        public MapCodec<VesselEssence> type() {
            return CODEC;
        }
    }

    public record RingSpell() implements ItemTintSource {
        public static final MapCodec<RingSpell> CODEC = MapCodec.unit(new RingSpell());

        @Override
        public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity) {
            Spell spell = SpellRingItem.spell(stack);
            return spell == null ? BLANK_RING : 0xFF000000 | spell.color();
        }

        @Override
        public MapCodec<RingSpell> type() {
            return CODEC;
        }
    }

    public static void onRegister(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "vessel_essence"), VesselEssence.CODEC);
        event.register(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "ring_spell"), RingSpell.CODEC);
    }
}
