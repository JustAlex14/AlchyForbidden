package com.lealex.alchyforbidden.item;

import com.lealex.alchyforbidden.familiar.RiftCreature;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** What one creature of the rift cannot resist (altar rites): offered at a Rift Maw, it calls that creature out. */
public class RiftLureItem extends Item {
    private final RiftCreature creature;

    public RiftLureItem(RiftCreature creature, Properties properties) {
        super(properties);
        this.creature = creature;
    }

    public RiftCreature creature() {
        return creature;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.alchyforbidden.rift_lure.calls", creature.displayName()).withStyle(s -> s.withColor(creature.color())));
        tooltip.accept(Component.translatable("item.alchyforbidden.rift_lure.tier", creature.tier()).withStyle(ChatFormatting.GRAY));
        if (creature.boss()) tooltip.accept(Component.translatable("item.alchyforbidden.rift_lure.boss").withStyle(ChatFormatting.RED));
    }
}
