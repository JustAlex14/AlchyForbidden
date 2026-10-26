package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.essence.RingContents;
import com.lealex.alchyforbidden.essence.SpellGem;
import com.lealex.alchyforbidden.item.RingTooltip;
import com.lealex.alchyforbidden.item.SpellRingItem;
import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;

/**
 * A spell ring's tooltip picture: a row of its sockets (each gem drawn, an empty socket as a dark square), then, when
 * its gems change anything, the signs of the circle's Rings tab (textures/gui/gem_stats.png) with the change each.
 */
public final class RingTooltipClient implements ClientTooltipComponent {
    public static final Identifier STATS = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/gui/gem_stats.png");
    private static final int SOCKET = 18, ROW = 10;

    private record Change(int icon, String text, int color) {}

    private final int sockets;
    private final RingContents contents;
    private final List<Change> changes = new ArrayList<>();

    public RingTooltipClient(RingTooltip tooltip) {
        sockets = SpellRingItem.sockets(tooltip.tier());
        contents = tooltip.contents();
        double[] factors = {contents.costFactor(), contents.cooldownFactor(), contents.damageFactor(), contents.durationFactor(), contents.rangeFactor()};
        int[] colors = {0xFF6AE07A, 0xFF7AE0FF, 0xFFFF6A6A, 0xFF6AA0FF, 0xFFE0C060};
        for (int k = 0; k < factors.length; k++) {
            long change = Math.round((factors[k] - 1) * 100);
            if (change != 0) changes.add(new Change(k, (change > 0 ? "+" : "") + change + "%", colors[k]));
        }
    }

    public static void onRegister(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(RingTooltip.class, RingTooltipClient::new);
    }

    @Override
    public int getHeight(Font font) {
        return SOCKET + 2 + (changes.isEmpty() ? 0 : ROW + 2);
    }

    @Override
    public int getWidth(Font font) {
        int stats = 0;
        for (Change change : changes) stats += 10 + font.width(change.text()) + 6;
        return Math.max(sockets * SOCKET, stats);
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        for (int i = 0; i < sockets; i++) {
            int sx = x + i * SOCKET;
            graphics.fill(sx, y, sx + 17, y + 17, 0xFF0B0A10);
            graphics.fill(sx + 1, y + 1, sx + 16, y + 16, 0xFF241C2C);
            SpellGem gem = contents.gem(i);
            if (gem != null) graphics.item(new ItemStack(ModRegistries.GEMS.get(gem).get()), sx, y);
        }
        int sx = x, sy = y + SOCKET + 3;
        for (Change change : changes) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, STATS, sx, sy, change.icon() * 8F, 0F, 8, 8, 40, 8);
            graphics.text(font, change.text(), sx + 10, sy, change.color(), true);
            sx += 10 + font.width(change.text()) + 6;
        }
    }
}
