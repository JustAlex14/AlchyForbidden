package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.Config;
import com.lealex.alchyforbidden.block.entity.InscribingBierBlockEntity;
import com.lealex.alchyforbidden.essence.Essence;
import com.lealex.alchyforbidden.essence.Inscription;
import com.lealex.alchyforbidden.essence.Inscriptions;
import com.lealex.alchyforbidden.menu.InscribingMenu;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.lealex.alchyx.client.Lines;
import com.lealex.alchyx.client.WindowTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Inscribing Bier's screen (menu/InscribingMenu). BODY: the parts of the body down the left, the player in the
 * middle (a thread from the chosen part to where it is on the body, a glyph on every part carved, the corruption under
 * it), and at the right what can be carved on the chosen part: pick one to see what it does and costs, then Inscribe
 * (or Burn away what is carved there). GROWTH: the next tier's cost and an offering slot, as the other multiblocks.
 */
public class InscribingScreen extends AbstractContainerScreen<InscribingMenu> {
    private static final WindowTheme THEME = new WindowTheme(0xFF0B0A10, 0xFF26232E, 0xFF18161E, 0xFFE6DDF2, 0xFF9C93AE,
            0xFFE88A6A, 0xFFB98CFF,
            Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/gui/panel_altar.png"),
            Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/gui/band_altar.png"));
    private static final int HEADER = 18, TABS_X = 118, TAB_W = 36;
    /** The tabs in the header, in order (menu tab numbers). */
    private static final int[] TAB_ORDER = {InscribingMenu.BODY, InscribingMenu.SLOTS, InscribingMenu.GROWTH};
    private static final int EXPAND_X = 150, EXPAND_Y = 134, EXPAND_W = 70, EXPAND_H = 14;
    private static final int PART_X = 8, PART_Y = 22, PART_W = 54, PART_H = 13, PART_STEP = 15;
    private static final int BODY_X0 = 66, BODY_Y0 = 22, BODY_X1 = 120, BODY_Y1 = 116, BODY_SIZE = 40;
    private static final int COL_X = 126, COL_W = 102, OPTION_Y = 34, OPTION_H = 12, VISIBLE = 5; // the carvings' list (scrolls)
    private static final int COST_Y = 100, ESSENCE_Y = 121, BUTTON_Y = 136, BUTTON_H = 12, INFO_W = 7;

    private Inscription.Part part = Inscription.Part.HEAD;
    private Inscription chosen = Inscription.on(Inscription.Part.HEAD).getFirst();
    private int scroll = 0;

    public InscribingScreen(InscribingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, InscribingMenu.WIDTH, InscribingMenu.HEIGHT);
        inventoryLabelX = InscribingMenu.INVENTORY_X;
        inventoryLabelY = InscribingMenu.INVENTORY_Y - 10;
        titleLabelX = 24;
    }

    private static boolean over(double mouseX, double mouseY, int x, int y, int wide, int high) {
        return mouseX >= x && mouseX < x + wide && mouseY >= y && mouseY < y + high;
    }

    private void press(int button) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    /** How far down the body box a part is drawn (0 = top of the head). */
    private static float partAt(Inscription.Part part) {
        return switch (part) {
            case HEAD -> 0.16F;
            case CHEST, BACK -> 0.36F;
            case ARMS -> 0.42F;
            case LEGS -> 0.7F;
            case FEET -> 0.9F;
            case SKIN -> 0.55F;
        };
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = leftPos, y = topPos;
        THEME.back(graphics, x, y, imageWidth, imageHeight, HEADER, new ItemStack(ModRegistries.INSCRIBING_BIER_ITEM.get()));
        String[] names = {"Body", "Growth", "Slots"};
        for (int i = 0; i < TAB_ORDER.length; i++) {
            int t = TAB_ORDER[i], tx = x + TABS_X + i * (TAB_W + 2);
            boolean on = menu.tab() == t;
            graphics.fill(tx, y + 6, tx + TAB_W, y + HEADER - 1, on ? 0xFF2E2440 : 0xFF15131B);
            graphics.fill(tx, y + HEADER - 2, tx + TAB_W, y + HEADER - 1, on ? THEME.accent() : 0xFF2A2436);
            graphics.centeredText(font, names[t], tx + TAB_W / 2, y + 8, on ? THEME.accent() : THEME.faint());
        }
        for (int i = InscribingMenu.FIRST_INVENTORY; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            THEME.well(graphics, x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17);
        }
        InscribingBierBlockEntity bier = menu.bier();
        LocalPlayer player = Minecraft.getInstance().player;
        if (bier == null || player == null) return;
        if (menu.tab() == InscribingMenu.BODY) body(graphics, x, y, bier, player, mouseX, mouseY);
        else if (menu.tab() == InscribingMenu.SLOTS) slots(graphics, x, y, bier, player, mouseX, mouseY);
        else growth(graphics, x, y, bier, mouseX, mouseY);
    }

    // ---- Body ----

    private void body(GuiGraphicsExtractor graphics, int x, int y, InscribingBierBlockEntity bier, LocalPlayer player, int mouseX, int mouseY) {
        // the parts, down the left
        Inscription.Part[] parts = Inscription.Part.values();
        for (int i = 0; i < parts.length; i++) {
            Inscription.Part p = parts[i];
            int bx = x + PART_X, by = y + PART_Y + i * PART_STEP;
            boolean on = p == part;
            List<Inscription> carvedHere = Inscriptions.on(player, p);
            graphics.fill(bx, by, bx + PART_W, by + PART_H, on ? 0xFF2E2440 : 0xFF15131B);
            graphics.fill(bx, by + PART_H - 1, bx + PART_W, by + PART_H, on ? THEME.accent() : 0xFF2A2436);
            graphics.text(font, Component.translatable("inscription.alchyforbidden.part." + p.id()), bx + 4, by + 3, on ? THEME.text() : THEME.faint(), false);
            for (int k = 0; k < carvedHere.size() && k < 4; k++) { // a dot per carving on it
                int dx = bx + PART_W - 6 - k * 5;
                graphics.fill(dx, by + 4, dx + 3, by + 7, 0xFF000000 | carvedHere.get(k).color());
            }
        }
        // the body itself
        int x0 = x + BODY_X0, y0 = y + BODY_Y0, x1 = x + BODY_X1, y1 = y + BODY_Y1;
        THEME.well(graphics, x0 - 1, y0 - 1, x1 + 1, y1 + 1);
        InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, x0, y0, x1, y1, BODY_SIZE, 0.0625F, mouseX, mouseY, player);
        float time = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime() + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int cx = (x0 + x1) / 2;
        java.util.Map<Inscription.Part, Integer> seen = new java.util.EnumMap<>(Inscription.Part.class);
        for (Inscription carved : Inscriptions.of(player)) { // a glyph glowing on every carving, side by side on a part
            int nth = seen.merge(carved.part(), 1, Integer::sum) - 1;
            int gy = y0 + Math.round((y1 - y0) * partAt(carved.part())) + (nth / 2) * 5;
            int pulse = 160 + Math.round(60 * Mth.sin(time * 0.15F + carved.ordinal()));
            int gx = (carved.part() == Inscription.Part.ARMS ? cx + 9 : carved.part() == Inscription.Part.BACK ? cx - 7 : cx) + (nth % 2 == 0 ? 0 : 5);
            graphics.fill(gx - 1, gy - 2, gx + 2, gy + 3, pulse << 24 | carved.color());
            graphics.fill(gx - 2, gy - 1, gx + 3, gy + 2, (pulse / 2) << 24 | carved.color());
        }
        { // a dotted thread from the chosen part to where it is on the body
            int fy = y + PART_Y + part.ordinal() * PART_STEP + PART_H / 2, ty = y0 + Math.round((y1 - y0) * partAt(part));
            int fx = x + PART_X + PART_W, tx = part == Inscription.Part.ARMS ? cx + 9 : part == Inscription.Part.BACK ? cx - 7 : cx - 3;
            for (int k = 0; k <= 16; k += 2) {
                int px = fx + (tx - fx) * k / 16, py = fy + (ty - fy) * k / 16;
                graphics.fill(px, py, px + 1, py + 1, THEME.accent());
            }
        }
        // what the bier holds of each essence (its links): three meters side by side under the body, each with its icon
        int third = (x1 - x0) / 3;
        for (Essence essence : Essence.values()) {
            int cell = x0 + essence.ordinal() * third;
            graphics.text(font, essence.icon(), cell, y1 + 6, 0xFFFFFFFF, false);
            int mx0 = cell + 10, mx1 = cell + third - 3, my = y1 + 8;
            graphics.fill(mx0 - 1, my - 1, mx1 + 1, my + 4, 0xFF0B0A10);
            graphics.fill(mx0, my, mx1, my + 3, 0xFF1A1820);
            int cap = Math.max(1, Config.bierStore());
            int filled = Math.round((mx1 - mx0) * Math.min(1F, bier.held(essence) / (float) cap));
            graphics.fill(mx0, my, mx0 + filled, my + 3, 0xFF000000 | lifted(essence.color()));
            if (over(mouseX, mouseY, cell, my - 3, third, 10)) {
                graphics.setTooltipForNextFrame(font, List.of(essence.displayName().copy().withStyle(st -> st.withColor(lifted(essence.color()))),
                        Component.translatable("inscription.alchyforbidden.bier_meter", bier.held(essence), cap)), Optional.empty(), mouseX, mouseY);
            }
        }
        int corruption = Inscriptions.corruption(player);
        String line = "Corruption " + corruption + "/" + Config.maxInscriptions() + "  x" + String.format(java.util.Locale.ROOT, "%.1f", Inscriptions.power(player));
        int lx = x + PART_X, ly = y + 140; // (under the parts and the meters, left of the button)
        graphics.text(font, line, lx, ly, corruption > 1 ? THEME.accent() : THEME.faint(), false);
        if (over(mouseX, mouseY, lx, ly - 1, font.width(line), 10)) {
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("inscription.alchyforbidden.power", Config.powerPerCarving()));
            graphics.setTooltipForNextFrame(font, tip, Optional.empty(), mouseX, mouseY);
        }
        // what can be carved on the chosen part
        int col = x + COL_X;
        graphics.text(font, Component.translatable("inscription.alchyforbidden.part." + part.id()), col, y + 22, THEME.accent(), false);
        List<Inscription> options = Inscription.on(part);
        List<Inscription> carvedHere = Inscriptions.on(player, part);
        graphics.text(font, carvedHere.size() + "/" + Config.carvingsPerPart(bier.standing()), col + COL_W - font.width(carvedHere.size() + "/" + Config.carvingsPerPart(bier.standing())), y + 22, THEME.faint(), false);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, options.size() - VISIBLE)));
        int listTop = y + OPTION_Y, rowW = COL_W - (options.size() > VISIBLE ? 5 : 0);
        THEME.well(graphics, col - 3, listTop - 2, col + COL_W + 1, listTop + VISIBLE * OPTION_H + 1);
        for (int i = 0; i < VISIBLE && scroll + i < options.size(); i++) {
            Inscription option = options.get(scroll + i);
            int oy = listTop + i * OPTION_H;
            boolean open = option.tier() <= bier.standing();
            boolean on = option == chosen;
            boolean hover = over(mouseX, mouseY, col - 2, oy, rowW + 2, OPTION_H);
            if (on || hover) graphics.fill(col - 2, oy, col + rowW, oy + OPTION_H - 1, on ? 0xFF2E2440 : 0xFF1E1A26);
            boolean isCarved = carvedHere.contains(option);
            String mark = isCarved ? "*" : open ? "" : "T" + option.tier();
            int markW = mark.isEmpty() ? 0 : font.width(mark) + 3;
            // the info icon right after the name: what it does, on hover
            String name = Lines.clip(font, Inscriptions.name(option).getString(), rowW - 4 - markW - INFO_W);
            graphics.text(font, name, col, oy + 2, open ? 0xFF000000 | option.color() : 0xFF55505E, false);
            int ix = col + font.width(name) + 3, iy = oy + 1;
            boolean info = over(mouseX, mouseY, ix - 1, iy - 1, INFO_W + 2, OPTION_H);
            graphics.fill(ix, iy + 1, ix + INFO_W - 1, iy + INFO_W + 1, info ? THEME.accent() : 0xFF3A3448);
            graphics.text(font, "i", ix + 2, iy + 1, info ? 0xFF0B0A10 : THEME.faint(), false);
            if (info) {
                List<Component> tip = new ArrayList<>();
                tip.add(Inscriptions.name(option));
                for (String l : Lines.wrap(font, Component.translatable("inscription.alchyforbidden." + option.id() + ".effect").getString(), 160)) {
                    tip.add(Component.literal(l).withStyle(net.minecraft.ChatFormatting.GRAY));
                }
                Config.Upkeep upkeep = Config.upkeep(option);
                if (upkeep != null) {
                    boolean each = option == Inscription.SECOND_LEAP || option == Inscription.SECOND_BREATH;
                    tip.add(Component.translatable(each ? "inscription.alchyforbidden.upkeep_each" : "inscription.alchyforbidden.upkeep",
                            upkeep.amount(), upkeep.essence().icon()).withStyle(s -> s.withColor(upkeep.essence().color())));
                }
                tip.add(Component.translatable("inscription.alchyforbidden.scales").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
                graphics.setTooltipForNextFrame(font, tip, Optional.empty(), mouseX, mouseY);
            }
            if (!mark.isEmpty()) graphics.text(font, mark, col + rowW - markW + 1, oy + 2, isCarved ? THEME.accent() : 0xFF55505E, false);
        }
        if (options.size() > VISIBLE) { // the scroll bar
            int bx = col + COL_W - 3, top = listTop, height = VISIBLE * OPTION_H - 1;
            graphics.fill(bx, top, bx + 3, top + height, 0xFF15131B);
            int thumb = Math.max(6, height * VISIBLE / options.size());
            int ty = top + (height - thumb) * scroll / Math.max(1, options.size() - VISIBLE);
            graphics.fill(bx, ty, bx + 3, ty + thumb, THEME.accent());
        }
        if (chosen.part() != part) return;
        // its price: items from the inventory...
        List<Inscription.Cost> costs = chosen.costs();
        for (int i = 0; i < costs.size() && i < 5; i++) {
            Inscription.Cost cost = costs.get(i);
            int ix = col + i * 20, iy = y + COST_Y;
            ItemStack shown = cost.stack();
            graphics.item(shown, ix, iy);
            boolean enough = player.getAbilities().instabuild || InscribingBierBlockEntity.count(player, cost) >= cost.count();
            String n = String.valueOf(cost.count());
            graphics.text(font, n, ix + 17 - font.width(n), iy + 9, enough ? 0xFFFFFFFF : 0xFFFF5555, true);
            if (over(mouseX, mouseY, ix, iy, 16, 16)) graphics.setTooltipForNextFrame(font, shown, mouseX, mouseY);
        }
        // ...and essences from the altars, short (the names on hover)
        int ex = col;
        List<Component> essenceTip = new ArrayList<>();
        int cell = COL_W / 3;
        for (Essence essence : Essence.values()) {
            int need = chosen.essence(essence);
            if (need <= 0) continue;
            boolean held = bier.available(player, essence) >= need;
            String s2 = Lines.clip(font, String.valueOf(need), cell - 11);
            graphics.text(font, essence.icon(), ex, y + ESSENCE_Y, 0xFFFFFFFF, false);
            graphics.text(font, s2, ex + 9, y + ESSENCE_Y, held ? THEME.text() : 0xFFFF5555, false);
            ex += cell;
            essenceTip.add(Component.literal(need + " ").append(essence.icon()).append(" ").append(essence.displayName()).withStyle(st -> st.withColor(held ? lifted(essence.color()) : 0xFF5555)));
        }
        if (!essenceTip.isEmpty() && over(mouseX, mouseY, col, y + ESSENCE_Y - 1, ex - col, 10)) {
            essenceTip.add(Component.translatable("inscription.alchyforbidden.from_where", Config.bierReach()));
            essenceTip.add(Component.translatable("inscription.alchyforbidden.bier_holds", bier.held(Essence.WRATH), bier.held(Essence.SORROW), bier.held(Essence.CORRUPTION)));
            graphics.setTooltipForNextFrame(font, essenceTip, Optional.empty(), mouseX, mouseY);
        }
        // the button
        int bx = col - 2, by = y + BUTTON_Y, bw = COL_W + 2;
        boolean burn = carvedHere.contains(chosen);
        Component refusal = burn ? null : bier.refusal(player, chosen);
        boolean ready = burn || refusal == null;
        graphics.fill(bx, by, bx + bw, by + BUTTON_H, ready ? (burn ? 0xFF4A1E1E : 0xFF3A2A55) : 0xFF1A1820);
        graphics.fill(bx, by + BUTTON_H - 1, bx + bw, by + BUTTON_H, ready ? (burn ? 0xFFE88A6A : THEME.accent()) : 0xFF2A2436);
        graphics.centeredText(font, Component.translatable(burn ? "inscription.alchyforbidden.burn" : "inscription.alchyforbidden.inscribe"),
                bx + bw / 2, by + 2, ready ? THEME.text() : 0xFF6A6474);
        if (over(mouseX, mouseY, bx, by, bw, BUTTON_H)) {
            graphics.setTooltipForNextFrame(font, List.of(burn ? Component.translatable("inscription.alchyforbidden.burn_tip")
                    : refusal != null ? refusal : Component.translatable("inscription.alchyforbidden.inscribe_tip")), Optional.empty(), mouseX, mouseY);
        }
    }

    private static int lifted(int color) {
        int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
        return (r + (255 - r) / 3) << 16 | (g + (255 - g) / 3) << 8 | (b + (255 - b) / 3);
    }

    // ---- Slots: how many carvings the body can carry ----

    private void slots(GuiGraphicsExtractor graphics, int x, int y, InscribingBierBlockEntity bier, LocalPlayer player, int mouseX, int mouseY) {
        int level = Inscriptions.carvingLevel(player), capacity = Inscriptions.capacity(player), carried = Inscriptions.corruption(player);
        graphics.text(font, Component.translatable("gui.alchyforbidden.slots_title", carried, capacity), x + 10, y + 24, THEME.accent(), false);
        // five levels, three slots each: carved ones lit in violet, open ones hollow, sealed ones dark
        for (int lv = 0; lv <= Inscriptions.MAX_LEVEL; lv++) {
            int cx = x + 12 + lv * 42, cy = y + 40;
            boolean open = lv <= level;
            graphics.text(font, new String[] {"I", "II", "III", "IV", "V"}[lv], cx, cy, open ? THEME.accent() : THEME.faint(), false);
            for (int k = 0; k < 3; k++) {
                int slot = lv * 3 + k, sx = cx + k * 12, sy = cy + 12;
                int frame = open ? 0xFF6A4A9A : 0xFF2A2436;
                graphics.fill(sx, sy, sx + 10, sy + 10, frame);
                int inner = slot < carried ? 0xFFB98CFF : open ? 0xFF15131B : 0xFF0B0A10;
                graphics.fill(sx + 1, sy + 1, sx + 9, sy + 9, inner);
            }
        }
        InscribingBierBlockEntity.Expansion next = InscribingBierBlockEntity.expansion(level);
        if (next == null) {
            graphics.text(font, Component.translatable("gui.alchyforbidden.slots_all_open"), x + 10, y + 74, THEME.faint(), false);
            return;
        }
        graphics.text(font, Component.translatable("gui.alchyforbidden.slots_next", capacity + 3, next.bierTier()), x + 10, y + 70,
                bier.standing() >= next.bierTier() ? THEME.text() : 0xFFFF5555, false);
        for (int i = 0; i < next.items().size(); i++) { // its items
            Inscription.Cost cost = next.items().get(i);
            int ix = x + 10 + i * 30, iy = y + 84;
            ItemStack shown = cost.stack();
            graphics.item(shown, ix, iy);
            boolean enough = player.getAbilities().instabuild || InscribingBierBlockEntity.count(player, cost) >= cost.count();
            graphics.text(font, String.valueOf(cost.count()), ix + 17, iy + 8, enough ? THEME.text() : 0xFFFF5555, true);
            if (over(mouseX, mouseY, ix, iy, 16, 16)) graphics.setTooltipForNextFrame(font, shown, mouseX, mouseY);
        }
        int ex = x + 10; // its essences
        for (Essence essence : Essence.values()) {
            int need = next.essences()[essence.ordinal()];
            if (need <= 0) continue;
            boolean held = player.getAbilities().instabuild || bier.available(player, essence) >= need;
            graphics.text(font, essence.icon(), ex, y + 108, 0xFFFFFFFF, false);
            graphics.text(font, String.valueOf(need), ex + 10, y + 108, held ? THEME.text() : 0xFFFF5555, false);
            ex += 40;
        }
        // Open
        Component refusal = bier.expandRefusal(player);
        boolean usable = refusal == null, hover = over(mouseX, mouseY, x + EXPAND_X, y + EXPAND_Y, EXPAND_W, EXPAND_H);
        graphics.fill(x + EXPAND_X - 1, y + EXPAND_Y - 1, x + EXPAND_X + EXPAND_W + 1, y + EXPAND_Y + EXPAND_H + 1, 0xFF0B0A10);
        graphics.fill(x + EXPAND_X, y + EXPAND_Y, x + EXPAND_X + EXPAND_W, y + EXPAND_Y + EXPAND_H, !usable ? 0xFF1A1820 : hover ? 0xFF4A2A70 : 0xFF33204E);
        graphics.centeredText(font, Component.translatable("gui.alchyforbidden.slots_open"), x + EXPAND_X + EXPAND_W / 2, y + EXPAND_Y + 3, usable ? 0xFFE6DDF2 : 0xFF55505E);
        if (hover) {
            graphics.setTooltipForNextFrame(font, font.split((refusal != null ? refusal : Component.translatable("gui.alchyforbidden.slots_open_tip")), 170), mouseX, mouseY);
        }
    }

    // ---- Growth ----

    private void growth(GuiGraphicsExtractor graphics, int x, int y, InscribingBierBlockEntity bier, int mouseX, int mouseY) {
        var costs = bier.nextCosts();
        for (int i = 0; i < costs.size() && i < 4; i++) { // four rows, 17 apart, above the offering slot (y 96)
            Config.Cost cost = costs.get(i);
            int ix = x + 10, iy = y + 22 + i * 17;
            ItemStack shown = new ItemStack(cost.item());
            graphics.item(shown, ix, iy);
            int given = bier.givenOf(cost.item());
            graphics.text(font, given + "/" + cost.count(), ix + 18, iy + 4, given >= cost.count() ? THEME.accent() : THEME.text(), false);
            if (over(mouseX, mouseY, ix, iy, 16, 16)) graphics.setTooltipForNextFrame(font, shown, mouseX, mouseY);
        }
        int ox = x + InscribingMenu.OFFER_X, oy = y + InscribingMenu.OFFER_Y;
        if (menu.slots.get(InscribingMenu.OFFER).isActive()) THEME.well(graphics, ox - 1, oy - 1, ox + 17, oy + 17); // none at the last tier
        if (menu.slots.get(InscribingMenu.OFFER).isActive() && over(mouseX, mouseY, ox, oy, 16, 16) && !menu.slots.get(InscribingMenu.OFFER).hasItem()) {
            graphics.setTooltipForNextFrame(font, List.of(Component.translatable("gui.alchyforbidden.offer_slot")), Optional.empty(), mouseX, mouseY);
        }
        int cx = x + 80, ly = y + 24;
        int max = bier.maxTier();
        graphics.text(font, "Tier " + bier.tier() + (bier.tier() < max ? " of " + max : " (whole)"), cx, ly, THEME.accent(), false);
        ly += 12;
        String text = bier.tier() >= max ? Component.translatable("inscribing_bier.alchyforbidden.whole").getString() : bier.wantsLine().getString();
        for (String line : Lines.wrap(font, text, 140)) {
            if (ly > y + 130) break;
            graphics.text(font, line, cx, ly, THEME.faint(), false);
            ly += 10;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.tab() == InscribingMenu.BODY && over(mouseX, mouseY, leftPos + COL_X - 3, topPos + OPTION_Y - 2, COL_W + 4, VISIBLE * OPTION_H + 3)) {
            int max = Math.max(0, Inscription.on(part).size() - VISIBLE);
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, Component.translatable("block.alchyforbidden.inscribing_bier"), titleLabelX, 7, THEME.accent(), false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, THEME.faint(), false);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        for (int i = 0; i < TAB_ORDER.length; i++) {
            if (over(mx, my, leftPos + TABS_X + i * (TAB_W + 2), topPos + 5, TAB_W, HEADER - 5)) {
                menu.setTab(TAB_ORDER[i]);
                press(InscribingMenu.TAB + TAB_ORDER[i]);
                return true;
            }
        }
        LocalPlayer player = Minecraft.getInstance().player;
        InscribingBierBlockEntity bier = menu.bier();
        if (menu.tab() == InscribingMenu.SLOTS && player != null && bier != null
                && over(mx, my, leftPos + EXPAND_X, topPos + EXPAND_Y, EXPAND_W, EXPAND_H) && bier.expandRefusal(player) == null) {
            press(InscribingMenu.EXPAND);
            return true;
        }
        if (menu.tab() == InscribingMenu.BODY && player != null && bier != null) {
            Inscription.Part[] parts = Inscription.Part.values();
            for (int i = 0; i < parts.length; i++) {
                if (over(mx, my, leftPos + PART_X, topPos + PART_Y + i * PART_STEP, PART_W, PART_H)) {
                    part = parts[i];
                    scroll = 0;
                    chosen = Inscription.on(part).getFirst();
                    return true;
                }
            }
            List<Inscription> options = Inscription.on(part);
            for (int i = 0; i < VISIBLE && scroll + i < options.size(); i++) {
                if (over(mx, my, leftPos + COL_X - 2, topPos + OPTION_Y + i * OPTION_H, COL_W - 3, OPTION_H)) {
                    chosen = options.get(scroll + i);
                    return true;
                }
            }
            if (over(mx, my, leftPos + COL_X - 2, topPos + BUTTON_Y, COL_W + 2, BUTTON_H) && chosen.part() == part) {
                if (Inscriptions.has(player, chosen)) press(InscribingMenu.BURN + chosen.ordinal());
                else if (bier.refusal(player, chosen) == null) press(InscribingMenu.INSCRIBE + chosen.ordinal());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
