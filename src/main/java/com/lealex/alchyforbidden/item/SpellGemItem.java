package com.lealex.alchyforbidden.item;

import com.lealex.alchyforbidden.essence.SpellGem;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A gem for a spell ring's socket (set at the Binding Circle's Rings tab). */
public class SpellGemItem extends Item {
    private final SpellGem gem;

    public SpellGemItem(SpellGem gem, Properties properties) {
        super(properties);
        this.gem = gem;
    }

    public SpellGem gem() {
        return gem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(gem.effect().copy().withStyle(s -> s.withColor(gem.color())));
        tooltip.accept(Component.translatable("item.alchyforbidden.gem.hint").withStyle(ChatFormatting.GRAY));
    }
}
