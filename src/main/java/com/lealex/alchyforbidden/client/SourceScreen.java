package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.block.entity.GrowingCoreBlockEntity;
import com.lealex.alchyforbidden.block.entity.KillingPitBlockEntity;
import com.lealex.alchyforbidden.block.entity.LamentWellBlockEntity;
import com.lealex.alchyforbidden.essence.Essence;
import com.lealex.alchyforbidden.menu.SourceMenu;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.lealex.alchyx.client.Lines;
import com.lealex.alchyx.client.WindowTheme;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The screen of the Killing Pit and the Lament Well (menu/SourceMenu), on the altar's sheets, two tabs. PIT: how many
 * suffer in it, the Wrath they make each second now, the Wrath waiting, the altar it feeds, its drops (take them).
 * WELL: how dark its bottom is, the Sorrow waiting, the ghosts weeping round it, the offering slot (from tier 2), the
 * altar it feeds. GROWTH: the next tier's cost and an offering slot, what it still wants written out.
 */
public class SourceScreen extends AbstractContainerScreen<SourceMenu> {
    private static final Identifier PANEL = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/gui/panel_altar.png");
    private static final Identifier BAND = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/gui/band_altar.png");
    private static final WindowTheme PIT = new WindowTheme(0xFF0B0A10, 0xFF26232E, 0xFF18161E, 0xFFE6DDF2, 0xFF9C93AE,
            0xFFE88A6A, 0xFFE0485A, PANEL, BAND);
    private static final WindowTheme WELL = new WindowTheme(0xFF0B0A10, 0xFF26232E, 0xFF18161E, 0xFFE6DDF2, 0xFF9C93AE,
            0xFFE88A6A, 0xFF3FC6D0, PANEL, BAND);
    private static final int HEADER = 18, TABS_X = 96, TAB_W = 36;
    private static final int BAR_X = 98, BAR_TOP = 24, BAR_BOTTOM = 82;   // the essence waiting
    private static final int STATUS_Y = 100, STATUS_END = 118;
    private static final int COLUMN_X = 64, COLUMN_W = 104;
    // the pit's buttons: its two switches side by side, the experience's under them
    private static final int BTN_Y = 70, BTN_H = 12, BTN_W = 43, BTN_HEAL_X = 8, BTN_VOID_X = 53;
    private static final int XP_Y = 85, BTN_XP_X = 63, BTN_XP_W = 33;

    public SourceScreen(SourceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SourceMenu.WIDTH, SourceMenu.HEIGHT);
        inventoryLabelY = SourceMenu.INVENTORY_Y - 10;
        titleLabelX = 24;
    }

    private boolean pit() {
        return menu.core() instanceof KillingPitBlockEntity;
    }

    private WindowTheme theme() {
        return pit() ? PIT : WELL;
    }

    private static boolean over(double mouseX, double mouseY, int x, int y, int wide, int high) {
        return mouseX >= x && mouseX < x + wide && mouseY >= y && mouseY < y + high;
    }

    private void press(int button) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        WindowTheme theme = theme();
        theme.back(graphics, x, y, imageWidth, imageHeight, HEADER,
                new ItemStack(pit() ? ModRegistries.KILLING_PIT_ITEM.get() : ModRegistries.LAMENT_WELL_ITEM.get()));
        String[] names = {pit() ? "Pit" : "Well", "Growth"};
        for (int t = 0; t < 2; t++) {
            int tx = x + TABS_X + t * (TAB_W + 2);
            boolean on = menu.tab() == t;
            graphics.fill(tx, y + 6, tx + TAB_W, y + HEADER - 1, on ? 0xFF33263A : 0xFF15131B);
            graphics.fill(tx, y + HEADER - 2, tx + TAB_W, y + HEADER - 1, on ? theme.accent() : 0xFF2A2436);
            graphics.centeredText(font, names[t], tx + TAB_W / 2, y + 8, on ? theme.accent() : theme.faint());
        }
        for (int i = menu.firstInventory(); i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            theme.well(graphics, x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17);
        }
        GrowingCoreBlockEntity core = menu.core();
        if (core == null) return;
        if (menu.tab() == SourceMenu.MAIN) {
            if (core instanceof KillingPitBlockEntity pit) pitTab(graphics, x, y, pit, mouseX, mouseY);
            else if (core instanceof LamentWellBlockEntity well) wellTab(graphics, x, y, well, mouseX, mouseY);
        } else {
            growth(graphics, x, y, core, mouseX, mouseY);
        }
    }

    // ---- The pit ----

    private void pitTab(GuiGraphicsExtractor graphics, int x, int y, KillingPitBlockEntity pit, int mouseX, int mouseY) {
        WindowTheme theme = PIT;
        // left of the bar: how it does, a short line each
        row(graphics, Component.translatable("gui.alchyforbidden.pit_inside", pit.hostileCount()), x, y + 24, theme.accent());
        row(graphics, Component.translatable("gui.alchyforbidden.pit_rate", pit.ratePerSecond()), x, y + 35, 0xFF000000 | Essence.WRATH.color());
        row(graphics, Component.translatable("gui.alchyforbidden.pit_hurt", Config.pitDamage(pit.standing())), x, y + 46, theme.faint());
        row(graphics, Component.translatable("gui.alchyforbidden.source_tier", pit.standing()), x, y + 57, theme.faint());
        // its two switches: healing (from tier 3) and voiding the drops
        boolean heals = pit.standing() >= 3;
        button(graphics, x + BTN_HEAL_X, y + BTN_Y, BTN_W, Component.translatable(pit.healing() ? "gui.alchyforbidden.pit_heal_on" : "gui.alchyforbidden.pit_heal_off"),
                heals, heals && pit.healing(), mouseX, mouseY,
                Component.translatable(heals ? "gui.alchyforbidden.pit_heal_tip" : "gui.alchyforbidden.pit_heal_locked", Config.pitHealBelow(), Config.pitHealCost()));
        button(graphics, x + BTN_VOID_X, y + BTN_Y, BTN_W, Component.translatable(pit.voidsDrops() ? "gui.alchyforbidden.pit_void_on" : "gui.alchyforbidden.pit_void_off"),
                true, pit.voidsDrops(), mouseX, mouseY, Component.translatable("gui.alchyforbidden.pit_void_tip"));
        // the experience it keeps, and the button that gives it
        graphics.text(font, Component.translatable("gui.alchyforbidden.pit_xp", pit.experience()), x + 8, y + XP_Y + 3, 0xFF8FD14F, false);
        button(graphics, x + BTN_XP_X, y + XP_Y, BTN_XP_W, Component.translatable("gui.alchyforbidden.pit_take"), pit.experience() > 0, false,
                mouseX, mouseY, Component.translatable("gui.alchyforbidden.pit_xp_tip", Config.pitMaxXp()));
        bar(graphics, x, y, Essence.WRATH, pit.buffer(), Config.pitBuffer(), mouseX, mouseY);
        // right of the bar: its store
        for (int i = 0; i < 9; i++) {
            int sx = x + SourceMenu.DROPS_X + (i % 3) * 18, sy = y + SourceMenu.DROPS_Y + (i / 3) * 18;
            theme.well(graphics, sx - 1, sy - 1, sx + 17, sy + 17);
        }
        if (over(mouseX, mouseY, x + SourceMenu.DROPS_X, y + SourceMenu.DROPS_Y, 54, 54) && !anyDrops()) {
            graphics.setTooltipForNextFrame(font, List.of(Component.translatable("gui.alchyforbidden.pit_drops")), Optional.empty(), mouseX, mouseY);
        }
        feeds(graphics, x, y, pit.feedsAltar(Essence.WRATH, Config.pitReach()), Config.pitReach(), theme);
    }

    private boolean anyDrops() {
        for (int i = 0; i < 9; i++) if (menu.slots.get(i).hasItem()) return true;
        return false;
    }

    /** A small button: lit when on, greyed when it cannot be used now; its tooltip under the mouse. */
    private void button(GuiGraphicsExtractor graphics, int bx, int by, int width, Component label, boolean usable, boolean lit,
                        int mouseX, int mouseY, Component tip) {
        boolean hover = over(mouseX, mouseY, bx, by, width, BTN_H);
        graphics.fill(bx - 1, by - 1, bx + width + 1, by + BTN_H + 1, 0xFF0B0A10);
        int back = !usable ? 0xFF1A1820 : lit ? (hover ? 0xFF6A2838 : 0xFF54202C) : (hover ? 0xFF33263A : 0xFF241C2C);
        graphics.fill(bx, by, bx + width, by + BTN_H, back);
        graphics.centeredText(font, Lines.clip(font, label.getString(), width - 2), bx + width / 2, by + 3,
                !usable ? 0xFF55505E : lit ? 0xFFFFB0BC : theme().faint());
        if (hover) graphics.setTooltipForNextFrame(font, Lines.wrap(font, tip.getString(), 160).stream().map(Component::literal).map(c -> (Component) c).toList(),
                Optional.empty(), mouseX, mouseY);
    }

    // ---- The well ----

    private void wellTab(GuiGraphicsExtractor graphics, int x, int y, LamentWellBlockEntity well, int mouseX, int mouseY) {
        WindowTheme theme = WELL;
        int max = Config.wellMaxGhosts(well.standing());
        row(graphics, Component.translatable("gui.alchyforbidden.well_ghosts", well.ghostCount(), max), x, y + 26, theme.accent());
        // the ghosts as a bar, of the most it holds
        int bx = x + 8, by = y + 38, bw = 78;
        graphics.fill(bx - 1, by - 1, bx + bw + 1, by + 7, 0xFF0B0A10);
        graphics.fill(bx, by, bx + bw, by + 6, 0xFF2A2A30);
        graphics.fill(bx, by, bx + Math.round(bw * Math.min(1F, well.ghostCount() / (float) Math.max(1, max))), by + 6, 0xFF000000 | Essence.SORROW.color());
        row(graphics, Component.translatable("gui.alchyforbidden.well_rate", well.ratePerSecond()), x, y + 54, 0xFF000000 | Essence.SORROW.color());
        row(graphics, Component.translatable("gui.alchyforbidden.source_tier", well.standing()), x, y + 68, theme.faint());
        bar(graphics, x, y, Essence.SORROW, well.buffer(), Config.wellBuffer(), mouseX, mouseY);
        // the offering of loss (from the second tier)
        int ox = x + SourceMenu.LOSS_X, oy = y + SourceMenu.LOSS_Y;
        theme.well(graphics, ox - 3, oy - 3, ox + 19, oy + 19);
        boolean open = well.standing() >= 2;
        if (!menu.slots.get(0).hasItem()) {
            graphics.fakeItem(new ItemStack(Items.MUSIC_DISC_11), ox, oy);
            graphics.fill(ox, oy, ox + 16, oy + 16, open ? 0xB026232E : 0xE026232E);
        }
        if (over(mouseX, mouseY, ox, oy, 16, 16) && !menu.slots.get(0).hasItem()) {
            graphics.setTooltipForNextFrame(font, List.of(Component.translatable(open ? "gui.alchyforbidden.well_loss" : "gui.alchyforbidden.well_loss_locked")),
                    Optional.empty(), mouseX, mouseY);
        }
        feeds(graphics, x, y, well.feedsAltar(Essence.SORROW, Config.wellReach()), Config.wellReach(), theme);
    }

    // ---- Shared ----

    /** One short line left of the bar (cut to fit, never under it). */
    private void row(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        graphics.text(font, Lines.clip(font, text.getString(), BAR_X - 12), x + 8, y, color, false);
    }

    /** The essence waiting for an altar with room: a bar, its tooltip. */
    private void bar(GuiGraphicsExtractor graphics, int x, int y, Essence essence, int amount, int cap, int mouseX, int mouseY) {
        int bx = x + BAR_X, top = y + BAR_TOP, bottom = y + BAR_BOTTOM;
        graphics.fill(bx - 1, top - 1, bx + 7, bottom + 1, 0xFF0B0A10);
        graphics.fill(bx, top, bx + 6, bottom, 0xFF1A1820);
        int filled = Math.round((bottom - top) * Math.min(1F, amount / (float) Math.max(1, cap)));
        graphics.fill(bx, bottom - filled, bx + 6, bottom, 0xFF000000 | essence.color());
        if (over(mouseX, mouseY, bx - 1, top - 1, 8, bottom - top + 2)) {
            graphics.setTooltipForNextFrame(font, List.of(essence.displayName(),
                    Component.translatable("gui.alchyforbidden.resonator_waiting", amount, cap)), Optional.empty(), mouseX, mouseY);
        }
    }

    /** The altar it feeds (or why it keeps its essence), under the rest, wrapped. */
    private void feeds(GuiGraphicsExtractor graphics, int x, int y, BlockPos altar, int reach, WindowTheme theme) {
        String line = altar == null ? Component.translatable("gui.alchyforbidden.source_no_altar", reach).getString()
                : Component.translatable("gui.alchyforbidden.resonator_feeds", altar.getX(), altar.getY(), altar.getZ()).getString();
        int ly = y + STATUS_Y;
        for (String part : Lines.wrap(font, line, imageWidth - 16)) {
            if (ly > y + STATUS_END - 8) break;
            graphics.text(font, part, x + 8, ly, altar == null ? theme.warn() : theme.faint(), false);
            ly += 10;
        }
    }

    /** The next tier: its cost (given / needed) with the offering slot under, what it still wants at the right. */
    private void growth(GuiGraphicsExtractor graphics, int x, int y, GrowingCoreBlockEntity core, int mouseX, int mouseY) {
        WindowTheme theme = theme();
        var costs = core.nextCosts();
        for (int i = 0; i < costs.size() && i < 3; i++) {
            Config.Cost cost = costs.get(i);
            int ix = x + 10, iy = y + 24 + i * 19;
            ItemStack shown = new ItemStack(cost.item());
            graphics.item(shown, ix, iy);
            int given = core.givenOf(cost.item());
            graphics.text(font, given + "/" + cost.count(), ix + 18, iy + 4, given >= cost.count() ? theme.accent() : theme.text(), false);
            if (over(mouseX, mouseY, ix, iy, 16, 16)) graphics.setTooltipForNextFrame(font, shown, mouseX, mouseY);
        }
        int ox = x + SourceMenu.OFFER_X, oy = y + SourceMenu.OFFER_Y;
        theme.well(graphics, ox - 1, oy - 1, ox + 17, oy + 17);
        if (over(mouseX, mouseY, ox, oy, 16, 16) && !menu.slots.get(menu.offerSlot()).hasItem()) {
            graphics.setTooltipForNextFrame(font, List.of(Component.translatable("gui.alchyforbidden.offer_slot")), Optional.empty(), mouseX, mouseY);
        }
        int cx = x + COLUMN_X, ly = y + 24, max = core.maxTier();
        graphics.text(font, "Tier " + core.tier() + (core.tier() < max ? " of " + max : " (whole)"), cx, ly, theme.accent(), false);
        ly += 12;
        String text = core.tier() >= max ? Component.translatable("gui.alchyforbidden.source_whole").getString() : core.wantsLine().getString();
        for (String line : Lines.wrap(font, text, COLUMN_W)) {
            if (ly > y + STATUS_END - 8) break;
            graphics.text(font, line, cx, ly, theme.faint(), false);
            ly += 10;
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, Component.translatable(pit() ? "gui.alchyforbidden.pit_short" : "gui.alchyforbidden.well_short"), titleLabelX, 7, theme().accent(), false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, theme().faint(), false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (int t = 0; t < 2; t++) {
            if (over(event.x(), event.y(), leftPos + TABS_X + t * (TAB_W + 2), topPos + 5, TAB_W, HEADER - 5)) {
                menu.setTab(t);
                press(SourceMenu.TAB + t);
                return true;
            }
        }
        if (menu.tab() == SourceMenu.MAIN && menu.core() instanceof KillingPitBlockEntity pit) {
            if (pit.standing() >= 3 && over(event.x(), event.y(), leftPos + BTN_HEAL_X, topPos + BTN_Y, BTN_W, BTN_H)) {
                press(SourceMenu.HEAL);
                return true;
            }
            if (over(event.x(), event.y(), leftPos + BTN_VOID_X, topPos + BTN_Y, BTN_W, BTN_H)) {
                press(SourceMenu.VOID);
                return true;
            }
            if (pit.experience() > 0 && over(event.x(), event.y(), leftPos + BTN_XP_X, topPos + XP_Y, BTN_XP_W, BTN_H)) {
                press(SourceMenu.TAKE_XP);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
