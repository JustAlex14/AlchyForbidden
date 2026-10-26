package com.lealex.alchyforbidden.item;

import com.lealex.alchyforbidden.familiar.FamiliarData;
import com.lealex.alchyforbidden.familiar.RiftCreature;
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
 * A familiar ring of tier 1, 2 or 3 (altar rites), worn on the left hand. Blank, it goes into a Rift Maw's ritual; the
 * creature slain there is bound in it (a creature of its tier or lower). No gem sockets: the creature's grade and level
 * are what make it stronger.
 */
public class FamiliarRingItem extends Item {
    private final int tier;

    public FamiliarRingItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    public static @Nullable FamiliarData data(ItemStack stack) {
        return stack.get(ModRegistries.FAMILIAR.get());
    }

    public static void setData(ItemStack stack, @Nullable FamiliarData data) {
        if (data == null) stack.remove(ModRegistries.FAMILIAR.get());
        else stack.set(ModRegistries.FAMILIAR.get(), data);
    }

    public static int tierOf(ItemStack stack) {
        return stack.getItem() instanceof FamiliarRingItem ring ? ring.tier : 0;
    }

    @Override
    public Component getName(ItemStack stack) {
        FamiliarData data = data(stack);
        RiftCreature creature = data == null ? null : data.creatureOrNull();
        if (creature == null) return super.getName(stack);
        if (!data.name().isEmpty()) return Component.translatable("item.alchyforbidden.familiar_ring.named", data.title(), creature.fullName(data.corruption()));
        return Component.translatable("item.alchyforbidden.familiar_ring.bound", super.getName(stack), creature.fullName(data.corruption()));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return data(stack) != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        FamiliarData data = data(stack);
        if (data == null || data.creatureOrNull() == null) {
            tooltip.accept(Component.translatable("item.alchyforbidden.familiar_ring.blank", tier).withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.accept(Component.translatable("item.alchyforbidden.familiar_ring.level", data.level(), FamiliarData.MAX_LEVEL,
                data.level() >= FamiliarData.MAX_LEVEL ? "-" : data.xp() + "/" + FamiliarData.toNext(data.level())).withStyle(s -> s.withColor(0xC8C0D8)));
        tooltip.accept(Component.translatable("item.alchyforbidden.familiar_ring.stats", Math.round(data.healthFactor() * 100), Math.round(data.damageFactor() * 100))
                .withStyle(ChatFormatting.GRAY));
        if (data.level() > 1) tooltip.accept(Component.translatable("item.alchyforbidden.familiar_ring.armor", String.format(java.util.Locale.ROOT, "%.1f", data.armorBonus()),
                String.format(java.util.Locale.ROOT, "%.2f", data.toughnessBonus()), Math.round((data.speedFactor() - 1) * 100)).withStyle(ChatFormatting.GRAY));
        for (String id : data.affixes()) { // what the rift left in it
            var affix = com.lealex.alchyforbidden.familiar.RiftAffix.byId(id);
            if (affix == null) continue;
            tooltip.accept(Component.translatable("item.alchyforbidden.familiar_ring.affix", affix.displayName()));
            if (flag.hasShiftDown()) tooltip.accept(Component.literal("  ").append(affix.description()).withStyle(ChatFormatting.GRAY));
        }
        for (RiftCreature.Trait trait : data.creatureOrNull().traits()) {
            boolean known = data.level() >= trait.level();
            tooltip.accept(Component.translatable("item.alchyforbidden.familiar_ring.trait", trait.level(), data.creatureOrNull().traitName(trait))
                    .withStyle(known ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
            if (known && flag.hasShiftDown()) tooltip.accept(Component.literal("  ").append(data.creatureOrNull().traitDescription(trait)).withStyle(ChatFormatting.GRAY));
        }
        for (RiftCreature.Move move : data.creatureOrNull().moves()) {
            boolean known = data.level() >= move.level();
            tooltip.accept(Component.translatable("item.alchyforbidden.familiar_ring.move", move.level(), data.creatureOrNull().moveName(move))
                    .withStyle(known ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
            if (known && flag.hasShiftDown()) tooltip.accept(Component.literal("  ").append(data.creatureOrNull().moveDescription(move)).withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.translatable("item.alchyforbidden.familiar_ring.wear").withStyle(ChatFormatting.DARK_GRAY));
    }
}
