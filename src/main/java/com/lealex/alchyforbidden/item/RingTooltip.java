package com.lealex.alchyforbidden.item;

import com.lealex.alchyforbidden.essence.RingContents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** The picture part of a spell ring's tooltip: its sockets and what its gems change (client/RingTooltipClient draws it). */
public record RingTooltip(int tier, RingContents contents) implements TooltipComponent {}
