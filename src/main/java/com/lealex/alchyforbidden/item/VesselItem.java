package com.lealex.alchyforbidden.item;

import com.lealex.alchyforbidden.essence.Essence;
import com.lealex.alchyforbidden.essence.VesselContents;
import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jspecify.annotations.Nullable;

/**
 * A vessel for Wrath or Sorrow: it takes the essence that fills it first and holds up to its size (config). Wrath
 * fills it when its carrier kills a hostile mob, Sorrow while they stand in the dark (essence/EssenceEvents). Poured
 * into the altar (its screen's pour slot), it empties into the altar's reserve and can be filled again, with either.
 * Three sizes, each made by a rite of the altar tier that opens it.
 */
public class VesselItem extends Item {
    private final IntSupplier capacity;

    public VesselItem(IntSupplier capacity, Properties properties) {
        super(properties);
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity.getAsInt();
    }

    public static @Nullable VesselContents contents(ItemStack stack) {
        VesselContents contents = stack.get(ModRegistries.VESSEL_CONTENTS.get());
        return contents == null || contents.amount() <= 0 ? null : contents;
    }

    /** Pours up to amount of the essence in; returns what it took (none if it holds the other essence, or is full). */
    public static int fill(ItemStack stack, Essence essence, int amount) {
        if (!(stack.getItem() instanceof VesselItem vessel) || !essence.carried() || amount <= 0 || stack.getCount() != 1) return 0;
        VesselContents now = contents(stack);
        if (now != null && now.essence() != essence) return 0;
        int held = now == null ? 0 : now.amount();
        int taken = Math.min(amount, vessel.capacity() - held);
        if (taken <= 0) return 0;
        stack.set(ModRegistries.VESSEL_CONTENTS.get(), new VesselContents(essence, held + taken));
        return taken;
    }

    /** Takes up to max out; returns what it gave. An emptied vessel holds nothing (and takes any essence again). */
    public static int drain(ItemStack stack, int max) {
        VesselContents now = contents(stack);
        if (now == null || max <= 0) return 0;
        int given = Math.min(max, now.amount());
        if (given >= now.amount()) stack.remove(ModRegistries.VESSEL_CONTENTS.get());
        else stack.set(ModRegistries.VESSEL_CONTENTS.get(), new VesselContents(now.essence(), now.amount() - given));
        return given;
    }

    @Override
    public Component getName(ItemStack stack) {
        VesselContents now = contents(stack);
        Component base = super.getName(stack);
        return now == null ? base : Component.translatable("item.alchyforbidden.vessel_of", base, now.essence().displayName());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> builder, TooltipFlag flag) {
        VesselContents now = contents(stack);
        if (now == null) {
            builder.accept(Component.translatable("item.alchyforbidden.vessel.empty", capacity()).withStyle(ChatFormatting.GRAY));
        } else {
            builder.accept(Component.translatable("item.alchyforbidden.vessel.holds", now.amount(), capacity(), now.essence().displayName())
                    .withColor(now.essence().color()));
        }
        builder.accept(Component.translatable("item.alchyforbidden.vessel.fill").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return contents(stack) != null;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        VesselContents now = contents(stack);
        return now == null ? 0 : Math.round(13.0F * Math.min(1, now.amount() / (float) Math.max(1, capacity())));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        VesselContents now = contents(stack);
        return now == null ? 0xFFFFFF : now.essence().color();
    }
}
