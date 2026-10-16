package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.block.entity.ResonatorBlockEntity;
import com.lealex.alchyforbidden.essence.Essence;
import com.lealex.alchyforbidden.menu.ResonatorMenu;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.lealex.alchyx.client.Lines;
import com.lealex.alchyx.client.WindowTheme;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Echo Resonator's screen (menu/ResonatorMenu), on the altar's sheets. Left: its pearls and eyes of ender (the
 * warden once it has it). Middle: the round seen from above, the four shriekers at the corners and the rift in the middle, the
 * pearl flying it as it really does, each shrieker flashing when it is passed. Right: the Corruption waiting. Under
 * the round: the next tier's cost (an offering slot and given / needed), and the altar it feeds.
 */
public class ResonatorScreen extends AbstractContainerScreen<ResonatorMenu> {
    private static final WindowTheme THEME = new WindowTheme(0xFF0B0A10, 0xFF26232E, 0xFF18161E, 0xFFE6DDF2, 0xFF9C93AE,
            0xFFE88A6A, 0xFF3FC6D0,
            Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/gui/panel_altar.png"),
            Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/gui/band_altar.png"));
    private static final int HEADER = 18;
    private static final int MAP_X = 52, MAP_Y = 22, MAP = 60;          // the round's square
    private static final int BAR_X = 150, BAR_TOP = 22, BAR_BOTTOM = 80; // the Corruption waiting
    private static final int COSTS_X = 30, COSTS_Y = 86, STATUS_Y = 106;

    public ResonatorScreen(ResonatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, ResonatorMenu.WIDTH, ResonatorMenu.HEIGHT);
        inventoryLabelY = ResonatorMenu.INVENTORY_Y - 10;
        titleLabelX = 24;
    }

    private static boolean over(double mouseX, double mouseY, int x, int y, int wide, int high) {
        return mouseX >= x && mouseX < x + wide && mouseY >= y && mouseY < y + high;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        ResonatorBlockEntity resonator = menu.resonator();
        THEME.back(graphics, x, y, imageWidth, imageHeight, HEADER, new ItemStack(ModRegistries.RESONATOR_ITEM.get()));
        for (int i = ResonatorMenu.FIRST_INVENTORY; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            THEME.well(graphics, x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17);
        }
        // pearls and eyes of ender (or the warden: no pearls any more, it blasts the shriekers itself)
        int px = x + ResonatorMenu.PEARL_X, py = y + ResonatorMenu.PEARL_Y;
        int ex = x + ResonatorMenu.EYE_X, ey = y + ResonatorMenu.EYE_Y;
        THEME.well(graphics, px - 3, py - 3, px + 19, py + 19);
        THEME.well(graphics, ex - 3, ey - 3, ex + 19, ey + 19);
        if (resonator != null && resonator.wardenWorks() && !menu.slots.get(ResonatorMenu.PEARLS).hasItem()) {
            graphics.fakeItem(new ItemStack(Items.WARDEN_SPAWN_EGG), px, py);
        } else {
            stock(graphics, px, py, ResonatorMenu.PEARLS, Items.ENDER_PEARL, "gui.alchyforbidden.pearl_slot", mouseX, mouseY);
        }
        stock(graphics, ex, ey, ResonatorMenu.EYES, Items.ENDER_EYE, "gui.alchyforbidden.eye_of_ender_slot", mouseX, mouseY);
        int ox = x + ResonatorMenu.OFFER_X, oy = y + ResonatorMenu.OFFER_Y;
        THEME.well(graphics, ox - 1, oy - 1, ox + 17, oy + 17);
        if (over(mouseX, mouseY, ox, oy, 16, 16) && !menu.slots.get(ResonatorMenu.OFFER).hasItem()) {
            graphics.setTooltipForNextFrame(font, List.of(Component.translatable("gui.alchyforbidden.offer_slot")), Optional.empty(), mouseX, mouseY);
        }
        if (resonator == null) return;
        round(graphics, x, y, resonator);
        waiting(graphics, x, y, resonator, mouseX, mouseY);
        costs(graphics, x, y, resonator, mouseX, mouseY);
    }

    /** An empty stock slot: its item faint, a tooltip saying what goes there. */
    private void stock(GuiGraphicsExtractor graphics, int px, int py, int slot, net.minecraft.world.item.Item item, String tip, int mouseX, int mouseY) {
        if (menu.slots.get(slot).hasItem()) return;
        graphics.fakeItem(new ItemStack(item), px, py);
        graphics.fill(px, py, px + 16, py + 16, 0xB026232E);
        if (over(mouseX, mouseY, px, py, 16, 16)) {
            graphics.setTooltipForNextFrame(font, List.of(Component.translatable(tip)), Optional.empty(), mouseX, mouseY);
        }
    }

    /** The round from above: the square, the shriekers, the rift, the pearl where it is. */
    private void round(GuiGraphicsExtractor graphics, int x, int y, ResonatorBlockEntity resonator) {
        int left = x + MAP_X, top = y + MAP_Y;
        THEME.well(graphics, left - 2, top - 2, left + MAP + 2, top + MAP + 2);
        int[][] corners = {{left + 4, top + 4}, {left + MAP - 4, top + 4}, {left + MAP - 4, top + MAP - 4}, {left + 4, top + MAP - 4}};
        int cx = left + MAP / 2, cy = top + MAP / 2;
        for (int i = 0; i < 4; i++) { // the way between them, dotted
            int[] a = corners[i], b = corners[(i + 1) % 4];
            for (int k = 0; k <= 10; k++) {
                int dx = a[0] + (b[0] - a[0]) * k / 10, dy = a[1] + (b[1] - a[1]) * k / 10;
                graphics.fill(dx, dy, dx + 1, dy + 1, 0xFF2A4A50);
            }
        }
        float time = Minecraft.getInstance().level == null ? 0
                : Minecraft.getInstance().level.getGameTime() + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float t = resonator.isFlying() ? Math.max(0, time - resonator.launchedAt()) / resonator.legTicks() : -1;
        float sinceBlast = time - resonator.blastStart() - ResonatorBlockEntity.STREAM_TICKS; // tier 3: the stream arrives
        for (int i = 0; i < 4; i++) {
            boolean flash = (t >= 1 && ((int) t - 1) % 4 == i && t - (int) t < 0.4F) // just after the pearl passes it
                    || (resonator.wardenWorks() && i == resonator.blastTarget() && sinceBlast >= 0 && sinceBlast < 8);
            int[] c = corners[i];
            graphics.fakeItem(new ItemStack(Items.SCULK_SHRIEKER), c[0] - 8, c[1] - 8);
            if (flash) graphics.fill(c[0] - 9, c[1] - 9, c[0] + 9, c[1] + 9, 0x803FC6D0);
        }
        // the rift in the middle, breathing
        float breath = 0.5F + 0.5F * Mth.sin(time * 0.15F);
        int r = 3 + Math.round(breath);
        graphics.fill(cx - r, cy - r - 1, cx + r, cy + r + 1, 0xFF160A28);
        graphics.fill(cx - 1, cy - r, cx + 1, cy + r, 0xFFB98CFF);
        if (resonator.wardenWorks()) { // the warden in the middle, its core's energy flowing to the shrieker drawing on it
            graphics.fakeItem(new ItemStack(Items.WARDEN_SPAWN_EGG), cx - 8, cy - 8);
            float flow = (time - resonator.blastStart()) / ResonatorBlockEntity.STREAM_TICKS;
            if (flow >= 0 && flow <= 1) {
                int[] to = corners[resonator.blastTarget()];
                for (int k = 0; k < 3; k++) {
                    float f = Mth.clamp(flow - k * 0.08F, 0, 1);
                    int bx = Math.round(cx + (to[0] - cx) * f), by = Math.round(cy + (to[1] - cy) * f);
                    graphics.fill(bx - 1, by - 1, bx + 2, by + 2, k == 0 ? 0xFF3FC6D0 : 0x803FC6D0);
                }
            }
        } else if (t >= 0) { // the pearl: out of the rift, then round the shriekers
            int leg = (int) t;
            float along = Mth.clamp(t - leg, 0, 1);
            int[] from = leg == 0 ? new int[] {cx, cy} : corners[(leg - 1) % 4];
            int[] to = corners[leg % 4];
            int pxl = Math.round(from[0] + (to[0] - from[0]) * along), pyl = Math.round(from[1] + (to[1] - from[1]) * along);
            graphics.fakeItem(new ItemStack(resonator.flyingItem()), pxl - 8, pyl - 8);
        }
    }

    /** The Corruption kept here (none goes to waste: it waits for an altar with room). */
    private void waiting(GuiGraphicsExtractor graphics, int x, int y, ResonatorBlockEntity resonator, int mouseX, int mouseY) {
        int bx = x + BAR_X, top = y + BAR_TOP, bottom = y + BAR_BOTTOM;
        graphics.fill(bx - 1, top - 1, bx + 7, bottom + 1, 0xFF0B0A10);
        graphics.fill(bx, top, bx + 6, bottom, 0xFF1A1820);
        int cap = Math.max(1, Config.resonatorBuffer());
        int filled = Math.round((bottom - top) * Math.min(1F, resonator.buffer() / (float) cap));
        graphics.fill(bx, bottom - filled, bx + 6, bottom, 0xFF000000 | Essence.CORRUPTION.color());
        if (over(mouseX, mouseY, bx - 1, top - 1, 8, bottom - top + 2)) {
            graphics.setTooltipForNextFrame(font, List.of(Essence.CORRUPTION.displayName(),
                    Component.translatable("gui.alchyforbidden.resonator_waiting", resonator.buffer(), cap)), Optional.empty(), mouseX, mouseY);
        }
        // the altar it feeds
        BlockPos altar = resonator.linkedAltar();
        int ax = x + 126, ay = y + 22; // left of the bar, beside the round
        graphics.fakeItem(new ItemStack(ModRegistries.ALTAR_ITEM.get()), ax, ay);
        if (altar == null) graphics.fill(ax, ay, ax + 16, ay + 16, 0xA0401010);
        if (over(mouseX, mouseY, ax, ay, 16, 16)) {
            graphics.setTooltipForNextFrame(font, List.of(altar == null
                    ? Component.translatable("gui.alchyforbidden.resonator_no_altar", Config.resonatorReach())
                    : Component.translatable("gui.alchyforbidden.resonator_feeds", altar.getX(), altar.getY(), altar.getZ())), Optional.empty(), mouseX, mouseY);
        }
    }

    /** The next tier's cost (given / needed), and a line on how it works. */
    private void costs(GuiGraphicsExtractor graphics, int x, int y, ResonatorBlockEntity resonator, int mouseX, int mouseY) {
        int cx = x + COSTS_X, cy = y + COSTS_Y;
        var costs = resonator.nextCosts();
        for (int i = 0; i < costs.size() && i < 3; i++) {
            Config.Cost cost = costs.get(i);
            int ix = cx + i * 38;
            ItemStack shown = new ItemStack(cost.item());
            graphics.item(shown, ix, cy);
            int given = resonator.givenOf(cost.item());
            graphics.text(font, given + "/" + cost.count(), ix + 17, cy + 5, given >= cost.count() ? 0xFF3FC6D0 : THEME.text(), false);
            if (over(mouseX, mouseY, ix, cy, 16, 16)) graphics.setTooltipForNextFrame(font, shown, mouseX, mouseY);
        }
        if (costs.isEmpty()) graphics.text(font, Component.translatable("gui.alchyforbidden.resonator_whole"), cx, cy + 5, THEME.faint(), false);
        BlockPos altar = resonator.linkedAltar();
        boolean needsWarden = resonator.tier() == 2 && !resonator.wardenSlain();
        String line = needsWarden ? Component.translatable("gui.alchyforbidden.resonator_needs_warden").getString() : altar == null ? Component.translatable("gui.alchyforbidden.resonator_no_altar", Config.resonatorReach()).getString()
                : Component.translatable("gui.alchyforbidden.resonator_feeds", altar.getX(), altar.getY(), altar.getZ()).getString();
        graphics.text(font, Lines.clip(font, line, imageWidth - 16), x + 8, y + STATUS_Y, altar == null || needsWarden ? THEME.warn() : THEME.faint(), false);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, 7, THEME.accent(), false);
        ResonatorBlockEntity resonator = menu.resonator();
        if (resonator != null) {
            String tier = "Tier " + resonator.tier();
            graphics.text(font, tier, imageWidth - 10 - font.width(tier), 7, THEME.faint(), false);
        }
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, THEME.faint(), false);
    }
}
