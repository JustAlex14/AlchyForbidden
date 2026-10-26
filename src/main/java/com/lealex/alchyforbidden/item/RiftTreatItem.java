package com.lealex.alchyforbidden.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** A treat for a familiar (altar rites): fed to it by its owner (right-click), it gives it experience. */
public class RiftTreatItem extends Item {
    private final int xp;

    public RiftTreatItem(int xp, Properties properties) {
        super(properties);
        this.xp = xp;
    }

    public int xp() {
        return xp;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.alchyforbidden.rift_treat.xp", xp).withStyle(s -> s.withColor(0x2FC6B0)));
        tooltip.accept(Component.translatable("item.alchyforbidden.rift_treat.use").withStyle(ChatFormatting.GRAY));
    }
}
