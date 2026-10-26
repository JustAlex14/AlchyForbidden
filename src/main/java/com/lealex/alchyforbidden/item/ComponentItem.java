package com.lealex.alchyforbidden.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A ritual component: what the rites, carvings, spells and lures are made of. Its tooltip says where it comes from
 * (a Weeping Fountain only weeps some of them; the others are made at an altar) and from which tier.
 */
public class ComponentItem extends Item {
    public enum Source { FOUNTAIN, ALTAR }

    private final Source source;
    private final int tier;

    public ComponentItem(Source source, int tier, Properties properties) {
        super(properties);
        this.source = source;
        this.tier = tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.alchyforbidden.component").withStyle(s -> s.withColor(0xB98CFF)));
        tooltip.accept(Component.translatable(source == Source.FOUNTAIN ? "item.alchyforbidden.component.fountain" : "item.alchyforbidden.component.altar", tier)
                .withStyle(ChatFormatting.GRAY));
        String kindles = getDescriptionId() + ".kindles"; // the multiblock it kindles, for the four that kindle one
        if (net.minecraft.locale.Language.getInstance().has(kindles)) tooltip.accept(Component.translatable(kindles).withStyle(s -> s.withColor(0xE0B060)));
    }
}
