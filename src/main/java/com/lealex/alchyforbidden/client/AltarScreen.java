package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.block.entity.AltarBlockEntity;
import com.lealex.alchyforbidden.essence.Essence;
import com.lealex.alchyforbidden.menu.AltarMenu;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.lealex.alchyforbidden.rite.AltarRite;
import com.lealex.alchyforbidden.rite.AltarRites;
import com.lealex.alchyx.client.Lines;
import com.lealex.alchyx.client.WindowTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The altar's screen (menu/AltarMenu), laid with AlchyX's WindowTheme on the altar's own sheets (tools/textures.py:
 * deepslate tiles with sculk in the cracks, a band of the builders' reinforced stone).
 * RITES: the stands round the middle as they lie round the altar (a stand the altar has not grown yet shows
 * sealed), what they make in the middle (faint until the rite runs, then a ring of the rift fills round it), the
 * result, Begin. GROWTH: the eye's place, the offering slot, what the next tier still wants, the tier's state.
 * On the right, every rite the altar knows: those above its tier are dim and locked, those the next tier opens are
 * framed. Hover one for what it takes.
 */
public class AltarScreen extends AbstractContainerScreen<AltarMenu> {
    private static final WindowTheme THEME = new WindowTheme(0xFF0B0A10, 0xFF26232E, 0xFF18161E, 0xFFE6DDF2, 0xFF9C93AE,
            0xFFE88A6A, 0xFFB98CFF,
            Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/gui/panel_altar.png"),
            Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/gui/band_altar.png"));
    private static final int SCULK = 0xFF2BC3CF, LOCKED = 0xB0101016, RING_OFF = 0xFF2A2436, RING_ON = 0xFFB98CFF;
    private static final int HEADER = 18;
    private static final int TABS_X = 104, TAB_W = 34;
    private static final int BEGIN_X = 58, BEGIN_Y = 102, BEGIN_W = 60, BEGIN_H = 14;
    private static final int LIST_X = 180, LIST_Y = 34, LIST_COLS = 3, LIST_ROWS = 5;
    private int scroll = 0;

    public AltarScreen(AltarMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, AltarMenu.WIDTH, AltarMenu.HEIGHT);
        inventoryLabelX = 38;
        inventoryLabelY = AltarMenu.INVENTORY_Y - 10;
        titleLabelX = 24;
    }

    private void press(int button) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private static boolean over(double mouseX, double mouseY, int x, int y, int wide, int high) {
        return mouseX >= x && mouseX < x + wide && mouseY >= y && mouseY < y + high;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        AltarBlockEntity altar = menu.altar();
        THEME.back(graphics, x, y, imageWidth, imageHeight, HEADER, new ItemStack(ModRegistries.ALTAR_ITEM.get()));
        // the two tabs, in the header
        String[] names = {"Rites", "Growth"};
        for (int t = 0; t < 2; t++) {
            int tx = x + TABS_X + t * (TAB_W + 2);
            boolean on = menu.tab() == t;
            graphics.fill(tx, y + 6, tx + TAB_W, y + HEADER - 1, on ? 0xFF3A2E52 : 0xFF15131B);
            graphics.fill(tx, y + HEADER - 2, tx + TAB_W, y + HEADER - 1, on ? THEME.accent() : 0xFF2A2436);
            graphics.centeredText(font, names[t], tx + TAB_W / 2, y + 8, on ? THEME.accent() : THEME.faint());
        }
        // the player's inventory
        for (int i = AltarMenu.FIRST_INVENTORY; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            THEME.well(graphics, x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17);
        }
        if (menu.tab() == AltarMenu.RITES) rites(graphics, x, y, altar, mouseX, mouseY);
        else growth(graphics, x, y, altar, mouseX, mouseY);
        riteList(graphics, x, y, altar, mouseX, mouseY);
    }

    // ---- RITES ----

    private void rites(GuiGraphicsExtractor graphics, int x, int y, AltarBlockEntity altar, int mouseX, int mouseY) {
        essenceBars(graphics, x, y, altar, mouseX, mouseY);
        int stands = altar == null ? 4 : altar.standCount();
        int cx = x + AltarMenu.CENTER_X + 8, cy = y + AltarMenu.CENTER_Y + 8;
        // threads of sculk from each stand that holds something to the middle
        for (int i = 0; i < AltarBlockEntity.MAX_STANDS; i++) {
            if (i >= stands || !menu.slots.get(i).hasItem()) continue;
            int sx = x + AltarMenu.STAND_XY[i][0] + 8, sy = y + AltarMenu.STAND_XY[i][1] + 8;
            for (int k = 1; k < 10; k++) {
                int px = sx + (cx - sx) * k / 10, py = sy + (cy - sy) * k / 10;
                graphics.fill(px, py, px + 1, py + 1, (k % 2 == 0 ? SCULK : 0xFF0E5F68));
            }
        }
        for (int i = 0; i < AltarBlockEntity.MAX_STANDS; i++) {
            int sx = x + AltarMenu.STAND_XY[i][0], sy = y + AltarMenu.STAND_XY[i][1];
            THEME.well(graphics, sx - 1, sy - 1, sx + 17, sy + 17);
            if (i >= stands) { // not grown yet: sealed
                graphics.fill(sx, sy, sx + 16, sy + 16, LOCKED);
                graphics.fill(sx + 4, sy + 7, sx + 12, sy + 9, 0xFF3A3346);
                if (over(mouseX, mouseY, sx, sy, 16, 16)) {
                    graphics.setTooltipForNextFrame(font, List.of(Component.translatable("gui.alchyforbidden.stand_sealed")), Optional.empty(), mouseX, mouseY);
                }
            }
        }
        // the middle: what the stands make, and the rite's ring
        THEME.well(graphics, cx - 13, cy - 13, cx + 13, cy + 13);
        float progress = altar == null ? 0 : altar.riteProgress();
        for (int k = 0; k < 24; k++) {
            double angle = Math.PI * 2 * k / 24 - Math.PI / 2;
            int px = cx + (int) Math.round(Math.cos(angle) * 16), py = cy + (int) Math.round(Math.sin(angle) * 16);
            graphics.fill(px - 1, py - 1, px + 1, py + 1, k < progress * 24 ? RING_ON : RING_OFF);
        }
        ItemStack making = altar == null ? ItemStack.EMPTY : altar.isRiting() ? altar.getAnimationDisplay("result") : altar.preview();
        if (!making.isEmpty()) {
            graphics.fakeItem(making, cx - 8, cy - 8);
            if (!altar.isRiting()) graphics.fill(cx - 8, cy - 8, cx + 8, cy + 8, 0x7026232E); // faint until it runs
            if (over(mouseX, mouseY, cx - 8, cy - 8, 16, 16)) {
                AltarRite rite = altar.isRiting() ? null : AltarRites.clientMatch(altar.standItems());
                List<Component> lines = new ArrayList<>();
                lines.add(making.getHoverName().copy().withStyle(ChatFormatting.LIGHT_PURPLE));
                if (rite != null) {
                    if (rite.tier() > altar.standing()) lines.add(Component.translatable("gui.alchyforbidden.rite_tier", rite.tier()).withStyle(ChatFormatting.RED));
                    costLines(lines, rite, altar);
                }
                graphics.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
            }
        }
        int rx = x + AltarMenu.RESULT_X, ry = y + AltarMenu.RESULT_Y;
        THEME.well(graphics, rx - 3, ry - 3, rx + 19, ry + 19);
        // Begin
        boolean can = altar != null && !altar.isRiting() && !making.isEmpty();
        int bx = x + BEGIN_X, by = y + BEGIN_Y;
        boolean hover = over(mouseX, mouseY, bx, by, BEGIN_W, BEGIN_H);
        graphics.fill(bx, by, bx + BEGIN_W, by + BEGIN_H, can ? (hover ? 0xFF4A3A6A : 0xFF33284A) : 0xFF1A1820);
        graphics.fill(bx, by + BEGIN_H - 1, bx + BEGIN_W, by + BEGIN_H, can ? THEME.accent() : 0xFF2A2436);
        String label = altar != null && altar.isRiting() ? "..." : "Begin";
        graphics.centeredText(font, label, bx + BEGIN_W / 2, by + 3, can ? THEME.text() : THEME.faint());
    }

    // ---- The essences ----

    private static final int BAR_TOP = 22, BAR_BOTTOM = 96, BAR_W = 4;

    /** Three bars at the left (Wrath, Sorrow, Sculk Corruption), what the stands' rite would spend marked on each. */
    private void essenceBars(GuiGraphicsExtractor graphics, int x, int y, AltarBlockEntity altar, int mouseX, int mouseY) {
        int px = x + AltarMenu.POUR_X, py = y + AltarMenu.POUR_Y;
        THEME.well(graphics, px - 1, py - 1, px + 17, py + 17);
        if (over(mouseX, mouseY, px, py, 16, 16) && !menu.slots.get(AltarMenu.POUR).hasItem()) {
            graphics.setTooltipForNextFrame(font, List.of(Component.translatable("gui.alchyforbidden.pour_slot")), Optional.empty(), mouseX, mouseY);
        }
        if (altar == null) return;
        int cap = Math.max(1, altar.reserveCap());
        AltarRite rite = altar.isRiting() ? null : AltarRites.clientMatch(altar.standItems());
        Essence[] essences = Essence.values();
        for (int i = 0; i < essences.length; i++) {
            Essence essence = essences[i];
            int bx = x + 8 + i * (BAR_W + 2), top = y + BAR_TOP, bottom = y + BAR_BOTTOM;
            graphics.fill(bx - 1, top - 1, bx + BAR_W + 1, bottom + 1, 0xFF0B0A10);
            graphics.fill(bx, top, bx + BAR_W, bottom, 0xFF1A1820);
            int have = altar.essence(essence);
            int filled = Math.round((bottom - top) * Math.min(1F, have / (float) cap));
            graphics.fill(bx, bottom - filled, bx + BAR_W, bottom, 0xFF000000 | essence.color());
            if (rite != null) { // what the rite would take: the top of the bar it would spend, or red past it
                int need = rite.costs()[i];
                if (need > 0) {
                    int needH = Math.round((bottom - top) * Math.min(1F, need / (float) cap));
                    int from = bottom - filled, to = Math.min(bottom, from + needH);
                    boolean enough = have >= need;
                    graphics.fill(bx, from, bx + BAR_W, to, enough ? 0x90FFFFFF : 0xB0FF3030);
                    if (!enough) graphics.fill(bx, bottom - Math.min(bottom - top, needH), bx + BAR_W, bottom - Math.min(bottom - top, needH) + 1, 0xFFFF3030);
                }
            }
            if (over(mouseX, mouseY, bx - 1, top - 1, BAR_W + 2, bottom - top + 2)) {
                List<Component> lines = new ArrayList<>();
                lines.add(essence.displayName().copy().withColor(essence.color()));
                lines.add(Component.literal(have + " / " + cap).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("gui.alchyforbidden.essence_from." + essence.getSerializedName()).withStyle(ChatFormatting.DARK_GRAY));
                graphics.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
            }
        }
    }

    /** A rite's costs, one line per essence it spends (red when the altar holds too little). */
    private static void costLines(List<Component> lines, AltarRite rite, AltarBlockEntity altar) {
        for (Essence essence : Essence.values()) {
            int need = rite.costs()[essence.ordinal()];
            if (need <= 0) continue;
            boolean enough = altar == null || altar.essence(essence) >= need;
            lines.add(Component.literal(need + " ").append(essence.displayName())
                    .withColor(enough ? essence.color() : 0xFF5050));
        }
    }

    // ---- GROWTH ----

    private void growth(GuiGraphicsExtractor graphics, int x, int y, AltarBlockEntity altar, int mouseX, int mouseY) {
        int ex = x + AltarMenu.EYE_X, ey = y + AltarMenu.EYE_Y;
        THEME.well(graphics, ex - 3, ey - 3, ex + 19, ey + 19);
        if (altar != null && !altar.hasEye()) {
            graphics.fakeItem(new ItemStack(ModRegistries.RIFT_EYE.get()), ex, ey);
            graphics.fill(ex, ey, ex + 16, ey + 16, 0xB026232E);
        }
        int ox = x + AltarMenu.OFFER_X, oy = y + AltarMenu.OFFER_Y;
        THEME.well(graphics, ox - 1, oy - 1, ox + 17, oy + 17);
        // no labels beside the two slots (the state is written there): what they are shows under the mouse
        if (over(mouseX, mouseY, ex, ey, 16, 16) && !menu.slots.get(AltarMenu.EYE).hasItem()) {
            graphics.setTooltipForNextFrame(font, List.of(Component.translatable("gui.alchyforbidden.eye_slot")), Optional.empty(), mouseX, mouseY);
        } else if (over(mouseX, mouseY, ox, oy, 16, 16) && !menu.slots.get(AltarMenu.OFFER).hasItem()) {
            graphics.setTooltipForNextFrame(font, List.of(Component.translatable("gui.alchyforbidden.offer_slot")), Optional.empty(), mouseX, mouseY);
        }
        if (altar == null) return;
        // what the next tier still wants
        List<Config.Cost> costs = altar.nextCosts();
        int row = 0;
        for (Config.Cost cost : costs) {
            int ix = x + 10, iy = y + 24 + row * 19;
            ItemStack shown = new ItemStack(cost.item());
            graphics.item(shown, ix, iy);
            int given = altar.givenOf(cost.item());
            boolean done = given >= cost.count();
            graphics.text(font, given + "/" + cost.count(), ix + 18, iy + 4, done ? SCULK : THEME.text(), false);
            if (over(mouseX, mouseY, ix, iy, 16, 16)) graphics.setTooltipForNextFrame(font, shown, mouseX, mouseY);
            row++;
        }
        // the state, at the right
        List<String> lines = new ArrayList<>();
        lines.add("Tier " + altar.tier() + (altar.tier() < AltarBlockEntity.MAX_TIER ? " of " + AltarBlockEntity.MAX_TIER : " (whole)"));
        lines.addAll(Lines.wrap(font, altar.statusLine().getString(), 64));
        // the state, in its own column right of the slots, down to above the hint
        int ly = y + 24;
        for (String line : lines) {
            if (ly > y + 96) break;
            graphics.text(font, line, x + 104, ly, ly == y + 24 ? THEME.accent() : THEME.faint(), false);
            ly += 10;
        }
        if (altar.nextTier() > 0) graphics.text(font, Lines.clip(font, "Hold the eye near the altar to see it grow", 164), x + 8, y + 112, 0xFF7A7090, false);
    }

    // ---- The rites it knows ----

    private void riteList(GuiGraphicsExtractor graphics, int x, int y, AltarBlockEntity altar, int mouseX, int mouseY) {
        int lx = x + LIST_X;
        THEME.well(graphics, lx - 3, y + LIST_Y - 13, lx + LIST_COLS * 18 + 1, y + LIST_Y + LIST_ROWS * 18 + 2);
        graphics.text(font, "Rites", lx, y + LIST_Y - 10, THEME.accent(), false);
        List<AltarRite> rites = AltarRites.clientList();
        int standing = altar == null ? 1 : altar.standing();
        int next = altar == null ? 0 : altar.nextTier();
        int first = scroll * LIST_COLS;
        for (int i = first; i < rites.size() && i < first + LIST_COLS * LIST_ROWS; i++) {
            AltarRite rite = rites.get(i);
            int ix = lx + ((i - first) % LIST_COLS) * 18, iy = y + LIST_Y + ((i - first) / LIST_COLS) * 18;
            boolean locked = rite.tier() > standing;
            if (menu.tab() == AltarMenu.GROWTH && next > 0 && rite.tier() == next) { // what the next tier opens
                graphics.fill(ix - 1, iy - 1, ix + 17, iy + 17, 0xFF6A4FA0);
            }
            graphics.fakeItem(rite.result(), ix, iy);
            if (locked) {
                graphics.fill(ix, iy, ix + 16, iy + 16, LOCKED);
                graphics.fill(ix + 5, iy + 8, ix + 11, iy + 13, 0xFF6A6078); // a small lock
                graphics.fill(ix + 6, iy + 5, ix + 10, iy + 6, 0xFF6A6078);
                graphics.fill(ix + 6, iy + 5, ix + 7, iy + 8, 0xFF6A6078);
                graphics.fill(ix + 9, iy + 5, ix + 10, iy + 8, 0xFF6A6078);
            }
            if (over(mouseX, mouseY, ix, iy, 16, 16)) graphics.setTooltipForNextFrame(font, riteTooltip(rite, locked), Optional.empty(), mouseX, mouseY);
        }
    }

    private static List<Component> riteTooltip(AltarRite rite, boolean locked) {
        List<Component> lines = new ArrayList<>();
        lines.add(rite.result().getHoverName().copy().withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.translatable("gui.alchyforbidden.rite_tier", rite.tier()).withStyle(locked ? ChatFormatting.RED : ChatFormatting.GRAY));
        for (Ingredient ingredient : rite.ingredients()) {
            var items = ingredient.items().toList();
            Component name = items.isEmpty() ? Component.literal("?") : new ItemStack(items.getFirst()).getHoverName();
            lines.add(Component.literal("- ").append(name).withStyle(ChatFormatting.GRAY));
        }
        costLines(lines, rite, null);
        lines.add(Component.translatable("gui.alchyforbidden.rite_time", Math.round(rite.ticks() / 20f)).withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, 7, THEME.accent(), false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, THEME.faint(), false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (int t = 0; t < 2; t++) {
            if (over(event.x(), event.y(), leftPos + TABS_X + t * (TAB_W + 2), topPos + 5, TAB_W, HEADER - 5)) {
                menu.setTab(t);
                press(AltarMenu.TAB + t);
                return true;
            }
        }
        if (menu.tab() == AltarMenu.RITES && over(event.x(), event.y(), leftPos + BEGIN_X, topPos + BEGIN_Y, BEGIN_W, BEGIN_H)) {
            press(AltarMenu.BEGIN);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (over(mouseX, mouseY, leftPos + LIST_X - 3, topPos + LIST_Y, LIST_COLS * 18 + 4, LIST_ROWS * 18)) {
            int rows = (AltarRites.clientList().size() + LIST_COLS - 1) / LIST_COLS;
            scroll = Math.max(0, Math.min(Math.max(0, rows - LIST_ROWS), scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
