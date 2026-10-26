package com.lealex.alchyforbidden.item;

import com.lealex.alchyforbidden.essence.RingContents;
import com.lealex.alchyforbidden.essence.Spell;
import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/**
 * A spell ring of tier 1, 2 or 3 (made by altar rites). The Binding Circle imbues one spell of its tier or lower into
 * it; worn in the circle's Rings tab, in the order its spells are picked. Gem sockets: 1, 2, or 4 for the third tier.
 */
public class SpellRingItem extends Item {
    private final int tier;

    public SpellRingItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    public static int sockets(int tier) {
        return tier >= 3 ? 4 : tier;
    }

    public static RingContents contents(ItemStack stack) {
        return stack.getOrDefault(ModRegistries.RING_CONTENTS.get(), RingContents.EMPTY);
    }

    public static void setContents(ItemStack stack, RingContents contents) {
        if (contents.spell().isEmpty() && contents.gems().isEmpty()) stack.remove(ModRegistries.RING_CONTENTS.get());
        else stack.set(ModRegistries.RING_CONTENTS.get(), contents);
    }

    public static @Nullable Spell spell(ItemStack stack) {
        return stack.getItem() instanceof SpellRingItem ? contents(stack).spellOrNull() : null;
    }

    public static int tierOf(ItemStack stack) {
        return stack.getItem() instanceof SpellRingItem ring ? ring.tier : 0;
    }

    @Override
    public Component getName(ItemStack stack) {
        Spell spell = spell(stack);
        return spell == null ? super.getName(stack) : Component.translatable("item.alchyforbidden.spell_ring.imbued", super.getName(stack), spell.displayName());
    }

    /** A gem clicked onto the ring in an inventory goes into its first free socket (like filling a bundle). */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack ring, ItemStack other, net.minecraft.world.inventory.Slot slot,
                                            net.minecraft.world.inventory.ClickAction action, net.minecraft.world.entity.player.Player player,
                                            net.minecraft.world.entity.SlotAccess access) {
        if (action != net.minecraft.world.inventory.ClickAction.PRIMARY || !(other.getItem() instanceof SpellGemItem gem) || ring.getCount() != 1) return false;
        RingContents contents = contents(ring);
        for (int i = 0; i < sockets(tier); i++) {
            if (contents.gem(i) != null) continue;
            setContents(ring, contents.withGem(i, gem.gem()));
            other.shrink(1);
            player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.2F);
            return true;
        }
        return false; // every socket taken: the click does what it normally does
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return spell(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        RingContents contents = contents(stack);
        Spell spell = contents.spellOrNull();
        if (spell == null) {
            tooltip.accept(Component.translatable("item.alchyforbidden.spell_ring.blank", tier).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.accept(spell.description().copy().withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.translatable("gui.alchyforbidden.spell_cast", com.lealex.alchyforbidden.essence.Spells.costLine(com.lealex.alchyforbidden.essence.Spells.costs(spell, contents)),
                    String.format(java.util.Locale.ROOT, "%.1f", com.lealex.alchyforbidden.essence.Spells.cooldown(spell, contents) / 20F)).withStyle(s -> s.withColor(0xC8C0D8)));
        }
        // the sockets and what the gems change are drawn under the text (RingTooltip, client/RingTooltipClient)
    }

    @Override
    public java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(ItemStack stack) {
        return java.util.Optional.of(new RingTooltip(tier, contents(stack)));
    }
}
