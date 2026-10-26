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
    private static final WindowTheme FOUNTAIN = new WindowTheme(0xFF0B0A10, 0xFF26232E, 0xFF18161E, 0xFFE6DDF2, 0xFF9C93AE,
            0xFFE88A6A, 0xFFB98CFF, PANEL, BAND);
    private static final WindowTheme INFUSER = new WindowTheme(0xFF0B0A10, 0xFF26232E, 0xFF18161E, 0xFFE6DDF2, 0xFF9C93AE,
            0xFFE88A6A, 0xFFD8B4FF, PANEL, BAND);
    private static final WindowTheme MAW = new WindowTheme(0xFF0B0A10, 0xFF26232E, 0xFF18161E, 0xFFE6DDF2, 0xFF9C93AE,
            0xFFE88A6A, 0xFF2FC6B0, PANEL, BAND);
    private static final WindowTheme CIRCLE = new WindowTheme(0xFF0B0A10, 0xFF26232E, 0xFF18161E, 0xFFE6DDF2, 0xFF9C93AE,
            0xFFE88A6A, 0xFFC86CFF, PANEL, BAND);
    // the circle: its spells in a grid of four, the picked one's costs under them, the button at the bottom right
    // the spells, by the tier that opens them: a row label (I, II, III) at the left, 7 to a row, 2 rows seen, scrolled
    private static final int SPELL_X = 22, SPELL_Y = 22, SPELL_STEP = 18, SPELL_COLUMNS = 7, SPELL_ROWS = 2, SPELL_SCROLL_X = 150;
    private int spellScroll = 0;
    private static final int SPELL_BTN_X = 96, SPELL_BTN_Y = 104, SPELL_BTN_W = 46;
    private int pickedSpell = 0;
    private static final Identifier GEM_STATS = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/gui/gem_stats.png");
    private static final int HEADER = 18, TABS_X = 96, TAB_W = 36;
    // the infuser: its three essences to pick, the vessel under them, the bind button (tier 3)
    private static final int ESS_X = 8, ESS_Y = 24, ESS_W = 26, ESS_H = 14, ESS_STEP = 28;
    private static final int BIND_X = 8, BIND_Y = 84, BIND_W = 80;
    // the fountain: its products in a grid of four, the bar of this one's weeping under them
    private static final int PICK_X = 8, PICK_Y = 22, PICK_STEP = 20, PICK_COLUMNS = 4, PICK_ROWS = 3;
    /** The first row of the fountain's products shown (the wheel scrolls them). */
    private int pickScroll;
    private static final int BAR_X = 98, BAR_TOP = 32, BAR_BOTTOM = 82;   // the essence waiting (its icon over it)
    private static final int STATUS_Y = 100, STATUS_END = 118;
    private static final int METER_X = 89, METER_STEP = 8, METER_W = 5, METER_TOP = 32, METER_BOTTOM = 76; // the fountain's three (icons over them)
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

    private boolean fountain() {
        return menu.core() instanceof com.lealex.alchyforbidden.block.entity.WeepingFountainBlockEntity;
    }

    private boolean infuser() {
        return menu.core() instanceof com.lealex.alchyforbidden.block.entity.EssenceInfuserBlockEntity;
    }

    private boolean circle() {
        return menu.core() instanceof com.lealex.alchyforbidden.block.entity.SoulCircleBlockEntity;
    }

    private boolean maw() {
        return menu.core() instanceof com.lealex.alchyforbidden.block.entity.RiftMawBlockEntity;
    }

    /** The tabs shown, in order: the circle and the maw also have their Rings. */
    private int[] tabs() {
        return circle() || maw() ? new int[]{SourceMenu.MAIN, SourceMenu.RINGS, SourceMenu.GROWTH} : new int[]{SourceMenu.MAIN, SourceMenu.GROWTH};
    }

    private int tabsX() {
        return circle() || maw() ? 58 : TABS_X;
    }

    private WindowTheme theme() {
        return maw() ? MAW : circle() ? CIRCLE : infuser() ? INFUSER : fountain() ? FOUNTAIN : pit() ? PIT : WELL;
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
                new ItemStack(maw() ? ModRegistries.RIFT_MAW_ITEM.get() : circle() ? ModRegistries.SOUL_CIRCLE_ITEM.get() : infuser() ? ModRegistries.ESSENCE_INFUSER_ITEM.get() : fountain() ? ModRegistries.WEEPING_FOUNTAIN_ITEM.get()
                        : pit() ? ModRegistries.KILLING_PIT_ITEM.get() : ModRegistries.LAMENT_WELL_ITEM.get()));
        int[] tabs = tabs();
        String[] names = {maw() ? Component.translatable("gui.alchyforbidden.maw_tab").getString() : circle() ? Component.translatable("gui.alchyforbidden.circle_tab").getString() : infuser() ? Component.translatable("gui.alchyforbidden.infuser_tab").getString() : fountain() ? "Weep" : pit() ? "Pit" : "Well", "Growth"};
        for (int i = 0; i < tabs.length; i++) {
            int t = tabs[i], tx = x + tabsX() + i * (TAB_W + 2);
            boolean on = menu.tab() == t;
            String name = t == SourceMenu.RINGS ? Component.translatable("gui.alchyforbidden.rings_tab").getString() : names[t];
            graphics.fill(tx, y + 6, tx + TAB_W, y + HEADER - 1, on ? 0xFF33263A : 0xFF15131B);
            graphics.fill(tx, y + HEADER - 2, tx + TAB_W, y + HEADER - 1, on ? theme.accent() : 0xFF2A2436);
            graphics.centeredText(font, name, tx + TAB_W / 2, y + 8, on ? theme.accent() : theme.faint());
        }
        for (int i = menu.firstInventory(); i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            theme.well(graphics, x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17);
        }
        GrowingCoreBlockEntity core = menu.core();
        if (core == null) return;
        if (affixSearch != null && menu.tab() != SourceMenu.MAIN) affixSearch.setVisible(false);
        if (menu.tab() == SourceMenu.MAIN) {
            if (core instanceof KillingPitBlockEntity pit) pitTab(graphics, x, y, pit, mouseX, mouseY);
            else if (core instanceof LamentWellBlockEntity well) wellTab(graphics, x, y, well, mouseX, mouseY);
            else if (core instanceof com.lealex.alchyforbidden.block.entity.WeepingFountainBlockEntity fountain) fountainTab(graphics, x, y, fountain, mouseX, mouseY);
            else if (core instanceof com.lealex.alchyforbidden.block.entity.EssenceInfuserBlockEntity infuser) infuserTab(graphics, x, y, infuser, mouseX, mouseY);
            else if (core instanceof com.lealex.alchyforbidden.block.entity.SoulCircleBlockEntity circle) circleTab(graphics, x, y, circle, mouseX, mouseY);
            else if (core instanceof com.lealex.alchyforbidden.block.entity.RiftMawBlockEntity maw) mawTab(graphics, x, y, maw, mouseX, mouseY);
        } else if (menu.tab() == SourceMenu.RINGS) {
            if (core instanceof com.lealex.alchyforbidden.block.entity.RiftMawBlockEntity maw) handTab(graphics, x, y, maw, mouseX, mouseY);
            else ringsTab(graphics, x, y, mouseX, mouseY);
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
        if (hover) graphics.setTooltipForNextFrame(font, font.split(tip, 160), mouseX, mouseY);
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
    }

    // ---- The fountain ----

    private void fountainTab(GuiGraphicsExtractor graphics, int x, int y, com.lealex.alchyforbidden.block.entity.WeepingFountainBlockEntity fountain,
                             int mouseX, int mouseY) {
        WindowTheme theme = FOUNTAIN;
        List<Config.Product> products = com.lealex.alchyforbidden.block.entity.WeepingFountainBlockEntity.products();
        Config.Product chosen = fountain.chosen();
        int rows = (products.size() + PICK_COLUMNS - 1) / PICK_COLUMNS;
        pickScroll = Math.max(0, Math.min(pickScroll, rows - PICK_ROWS));
        if (rows > PICK_ROWS) { // a scroll bar beside the grid when there are more rows than it shows
            int top = y + PICK_Y - 1, height = PICK_ROWS * PICK_STEP - 2, bx = x + PICK_X + PICK_COLUMNS * PICK_STEP - 2;
            graphics.fill(bx, top, bx + 2, top + height, 0xFF0B0A10);
            int knob = Math.max(6, height * PICK_ROWS / rows), at = top + (height - knob) * pickScroll / (rows - PICK_ROWS);
            graphics.fill(bx, at, bx + 2, at + knob, theme.accent());
        }
        for (int cell = 0; cell < PICK_ROWS * PICK_COLUMNS; cell++) {
            int i = pickScroll * PICK_COLUMNS + cell;
            if (i >= products.size()) break;
            Config.Product product = products.get(i);
            int px = x + PICK_X + (cell % PICK_COLUMNS) * PICK_STEP, py = y + PICK_Y + (cell / PICK_COLUMNS) * PICK_STEP;
            boolean open = product.tier() <= fountain.standing();
            boolean picked = chosen != null && chosen.item() == product.item();
            graphics.fill(px - 2, py - 2, px + 18, py + 18, picked ? theme.accent() : 0xFF0B0A10);
            graphics.fill(px - 1, py - 1, px + 17, py + 17, picked ? 0xFF33263A : 0xFF1A1820);
            graphics.item(new ItemStack(product.item()), px, py);
            if (!open) graphics.fill(px, py, px + 16, py + 16, 0xC015131B);
            if (over(mouseX, mouseY, px, py, 16, 16)) {
                List<Component> tip = new java.util.ArrayList<>();
                tip.add(product.item().getName(new ItemStack(product.item())));
                tip.add(Component.translatable("gui.alchyforbidden.fountain_cost", product.cost(), product.essence().icon())
                        .withStyle(s -> s.withColor(product.essence().color())));
                tip.add(Component.translatable("gui.alchyforbidden.fountain_time", String.format(java.util.Locale.ROOT, "%.1f", fountain.ticksFor(product) / 20F)));
                tip.add(Component.translatable(!open ? "gui.alchyforbidden.fountain_locked" : picked ? "gui.alchyforbidden.fountain_stop" : "gui.alchyforbidden.fountain_pick", product.tier()));
                graphics.setTooltipForNextFrame(font, tip, Optional.empty(), mouseX, mouseY);
            }
        }
        // what it holds of each essence (its links, the sources round it): three meters between the grid and the store
        for (Essence essence : Essence.values()) {
            int mx = x + METER_X + essence.ordinal() * METER_STEP, top = y + METER_TOP, bottom = y + METER_BOTTOM;
            int cap = Math.max(1, Config.fountainStore()), amount = fountain.held(essence);
            meterIcon(graphics, essence, mx + METER_W / 2, top);
            graphics.fill(mx - 1, top - 1, mx + METER_W + 1, bottom + 1, 0xFF0B0A10);
            graphics.fill(mx, top, mx + METER_W, bottom, 0xFF1A1820);
            int filled = Math.round((bottom - top) * Math.min(1F, amount / (float) cap));
            graphics.fill(mx, bottom - filled, mx + METER_W, bottom, 0xFF000000 | brighter(essence.color()));
            if (over(mouseX, mouseY, mx - 1, top - 1, METER_W + 2, bottom - top + 2)) {
                graphics.setTooltipForNextFrame(font, List.of(essence.displayName(),
                        Component.translatable("gui.alchyforbidden.fountain_meter", amount, cap)), Optional.empty(), mouseX, mouseY);
            }
        }
        // the one it weeps now: its bar, and why it waits
        int bx = x + 8, by = y + 86, bw = 88;
        long now = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        graphics.fill(bx - 1, by - 1, bx + bw + 1, by + 6, 0xFF0B0A10);
        graphics.fill(bx, by, bx + bw, by + 5, 0xFF1A1820);
        if (chosen != null) {
            graphics.fill(bx, by, bx + Math.round(bw * fountain.progress(now)), by + 5, 0xFF000000 | chosen.essence().color());
        }
        String state = switch (fountain.state()) {
            case com.lealex.alchyforbidden.block.entity.WeepingFountainBlockEntity.WORKING -> ""; // (nothing is said while it weeps)
            case com.lealex.alchyforbidden.block.entity.WeepingFountainBlockEntity.THIRSTY -> "gui.alchyforbidden.fountain_thirsty";
            case com.lealex.alchyforbidden.block.entity.WeepingFountainBlockEntity.FULL -> "gui.alchyforbidden.fountain_full";
            case com.lealex.alchyforbidden.block.entity.WeepingFountainBlockEntity.LOCKED -> "gui.alchyforbidden.fountain_locked_now";
            default -> "gui.alchyforbidden.fountain_nothing";
        };
        boolean bad = fountain.state() != com.lealex.alchyforbidden.block.entity.WeepingFountainBlockEntity.WORKING;
        Component line = fountain.state() == com.lealex.alchyforbidden.block.entity.WeepingFountainBlockEntity.THIRSTY && chosen != null
                ? Component.translatable(state, chosen.cost(), chosen.essence().icon(), Config.fountainReach())
                : Component.translatable(state);
        int ly = y + STATUS_Y;
        for (String part : bad ? Lines.wrap(font, line.getString(), imageWidth - 16) : List.<String>of()) { // nothing said while it weeps
            if (ly > y + STATUS_END - 8) break;
            graphics.text(font, part, x + 8, ly, theme.warn(), false);
            ly += 10;
        }
        // right: what it wept
        for (int i = 0; i < 9; i++) {
            int sx = x + SourceMenu.DROPS_X + (i % 3) * 18, sy = y + SourceMenu.DROPS_Y + (i / 3) * 18;
            theme.well(graphics, sx - 1, sy - 1, sx + 17, sy + 17);
        }
    }

    // ---- The infuser ----

    private void infuserTab(GuiGraphicsExtractor graphics, int x, int y, com.lealex.alchyforbidden.block.entity.EssenceInfuserBlockEntity infuser,
                            int mouseX, int mouseY) {
        WindowTheme theme = INFUSER;
        // the three essences: the one it pours framed
        for (Essence essence : Essence.values()) {
            int ex = x + ESS_X + essence.ordinal() * ESS_STEP, ey = y + ESS_Y;
            boolean picked = infuser.chosen() == essence, hover = over(mouseX, mouseY, ex, ey, ESS_W, ESS_H);
            graphics.fill(ex - 1, ey - 1, ex + ESS_W + 1, ey + ESS_H + 1, picked ? 0xFF000000 | brighter(essence.color()) : 0xFF0B0A10);
            graphics.fill(ex, ey, ex + ESS_W, ey + ESS_H, picked ? 0xFF33263A : hover ? 0xFF2A2234 : 0xFF1A1820);
            graphics.text(font, essence.icon(), ex + 9, ey + 3, 0xFFFFFFFF, false); // its sign, centred in the button
            if (hover) graphics.setTooltipForNextFrame(font, List.of(Component.translatable("gui.alchyforbidden.infuser_pick", essence.displayName())),
                    Optional.empty(), mouseX, mouseY);
        }
        // the vessel it fills, how fast
        int vx = x + SourceMenu.VESSEL_X, vy = y + SourceMenu.VESSEL_Y;
        theme.well(graphics, vx - 3, vy - 3, vx + 19, vy + 19);
        if (!menu.slots.get(0).hasItem()) {
            graphics.fakeItem(new ItemStack(ModRegistries.CRACKED_VESSEL.get()), vx, vy);
            graphics.fill(vx, vy, vx + 16, vy + 16, 0xB026232E);
            if (over(mouseX, mouseY, vx, vy, 16, 16)) graphics.setTooltipForNextFrame(font, List.of(Component.translatable("gui.alchyforbidden.infuser_vessel")),
                    Optional.empty(), mouseX, mouseY);
        }
        // how fast it pours; when the store has nothing of that essence, why (the whole sentence on hover)
        boolean thirsty = menu.slots.get(0).hasItem() && infuser.held(infuser.chosen()) <= 0 && !infuser.pouring();
        Component rate = thirsty ? Component.translatable("gui.alchyforbidden.infuser_empty")
                : Component.translatable("gui.alchyforbidden.infuser_rate", Config.infuserRate(infuser.standing()));
        graphics.text(font, rate, x + 62, vy + 4, thirsty ? theme.warn() : 0xFF000000 | brighter(infuser.chosen().color()), false);
        if (thirsty && over(mouseX, mouseY, x + 62, vy, 26, 16)) {
            graphics.setTooltipForNextFrame(font, font.split(Component.translatable("gui.alchyforbidden.infuser_thirsty", Config.infuserReach()), 160), mouseX, mouseY);
        }
        row(graphics, Component.translatable("gui.alchyforbidden.source_tier", infuser.standing()), x, y + 72, theme.faint());
        // the body it feeds (tier 3)
        boolean open = infuser.standing() >= 3, boundNow = infuser.bound() != null;
        button(graphics, x + BIND_X, y + BIND_Y, BIND_W, boundNow ? Component.translatable("gui.alchyforbidden.infuser_unbind") : Component.translatable("gui.alchyforbidden.infuser_bind"),
                open, boundNow, mouseX, mouseY, open ? Component.translatable("gui.alchyforbidden.infuser_bind_tip", Config.infuserBodyRate())
                        : Component.translatable("gui.alchyforbidden.infuser_bind_locked"));
        if (boundNow) {
            graphics.text(font, Lines.clip(font, Component.translatable("gui.alchyforbidden.infuser_bound", infuser.boundName()).getString(), 80),
                    x + 8, y + BIND_Y + 16, theme.accent(), false);
        }
        // what it holds of each essence: three meters
        for (Essence essence : Essence.values()) {
            int mx = x + 148 + essence.ordinal() * METER_STEP, top = y + METER_TOP, bottom = y + METER_BOTTOM + 30; // right edge, clear of the rate line
            int cap = Math.max(1, Config.infuserStore()), amount = infuser.held(essence);
            meterIcon(graphics, essence, mx + METER_W / 2, top);
            graphics.fill(mx - 1, top - 1, mx + METER_W + 1, bottom + 1, 0xFF0B0A10);
            graphics.fill(mx, top, mx + METER_W, bottom, 0xFF1A1820);
            int filled = Math.round((bottom - top) * Math.min(1F, amount / (float) cap));
            graphics.fill(mx, bottom - filled, mx + METER_W, bottom, 0xFF000000 | brighter(essence.color()));
            if (over(mouseX, mouseY, mx - 1, top - 1, METER_W + 2, bottom - top + 2)) {
                graphics.setTooltipForNextFrame(font, List.of(essence.displayName(),
                        Component.translatable("gui.alchyforbidden.fountain_meter", amount, cap)), Optional.empty(), mouseX, mouseY);
            }
        }
    }

    // ---- The circle ----

    private void circleTab(GuiGraphicsExtractor graphics, int x, int y, com.lealex.alchyforbidden.block.entity.SoulCircleBlockEntity circle,
                           int mouseX, int mouseY) {
        WindowTheme theme = CIRCLE;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        com.lealex.alchyforbidden.essence.Spell[] spells = com.lealex.alchyforbidden.essence.Spell.values();
        pickedSpell = Math.floorMod(pickedSpell, spells.length);
        // every spell, by tier: worn ones framed in their colour, the ones its tier does not open dimmed
        List<List<com.lealex.alchyforbidden.essence.Spell>> rows = spellRows();
        spellScroll = Math.max(0, Math.min(spellScroll, rows.size() - SPELL_ROWS));
        for (int r = 0; r < SPELL_ROWS && spellScroll + r < rows.size(); r++) {
            List<com.lealex.alchyforbidden.essence.Spell> row = rows.get(spellScroll + r);
            int ry = y + SPELL_Y + r * SPELL_STEP;
            boolean first = spellScroll + r == 0 || rows.get(spellScroll + r - 1).getFirst().tier() != row.getFirst().tier();
            if (first) graphics.text(font, "I".repeat(row.getFirst().tier()), x + 8, ry + 4, row.getFirst().tier() <= circle.standing() ? theme.accent() : theme.faint(), false);
            for (int c = 0; c < row.size(); c++) {
                com.lealex.alchyforbidden.essence.Spell spell = row.get(c);
                int i = spell.ordinal();
                int sx = x + SPELL_X + c * SPELL_STEP, sy = ry;
                boolean known = com.lealex.alchyforbidden.essence.Spells.knows(player, spell), open = spell.tier() <= circle.standing();
                graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, i == pickedSpell ? theme.accent() : known ? 0xFF000000 | spell.color() : 0xFF0B0A10);
                SpellHud.icon(graphics, spell, sx, sy);
                if (!open) graphics.fill(sx, sy, sx + 16, sy + 16, 0xC015131B);
                if (over(mouseX, mouseY, sx, sy, 16, 16)) {
                    List<Component> tip = new java.util.ArrayList<>(SpellSlotsScreen.tooltip(font, spell));
                    if (known) tip.add(Component.translatable("gui.alchyforbidden.circle_known").withStyle(s -> s.withColor(theme.accent())));
                    if (spell.tier() > 1) tip.add(Component.translatable("gui.alchyforbidden.circle_ring_tier", spell.tier()).withStyle(s -> s.withColor(theme.faint())));
                    if (!open) tip.add(Component.translatable("gui.alchyforbidden.circle_locked", spell.tier()).withStyle(s -> s.withColor(theme.warn())));
                    graphics.setTooltipForNextFrame(font, tip, Optional.empty(), mouseX, mouseY);
                }
            }
        }
        // the scroll bar, when there are more rows than shown
        if (rows.size() > SPELL_ROWS) {
            int top = y + SPELL_Y - 1, height = SPELL_ROWS * SPELL_STEP - 2;
            graphics.fill(x + SPELL_SCROLL_X, top, x + SPELL_SCROLL_X + 4, top + height, 0xFF0B0A10);
            int knob = Math.max(6, height * SPELL_ROWS / rows.size());
            int at = top + (height - knob) * spellScroll / Math.max(1, rows.size() - SPELL_ROWS);
            graphics.fill(x + SPELL_SCROLL_X, at, x + SPELL_SCROLL_X + 4, at + knob, theme.accent());
        }
        // right of the costs: the ring to imbue
        int ringX = x + SourceMenu.RING_X, ringY = y + SourceMenu.RING_Y;
        ItemStack ring = menu.imbueRing();
        theme.well(graphics, ringX - 1, ringY - 1, ringX + 17, ringY + 17);
        if (ring.isEmpty()) {
            graphics.fakeItem(new ItemStack(ModRegistries.SPELL_RING_1.get()), ringX, ringY);
            graphics.fill(ringX, ringY, ringX + 16, ringY + 16, 0xB026232E);
            if (over(mouseX, mouseY, ringX, ringY, 16, 16)) {
                graphics.setTooltipForNextFrame(font, font.split(Component.translatable("gui.alchyforbidden.ring_slot"), 160), mouseX, mouseY);
            }
        }
        // the picked one: its name, cast cost, what binding it takes
        com.lealex.alchyforbidden.essence.Spell spell = spells[pickedSpell];
        boolean known = com.lealex.alchyforbidden.essence.Spells.knows(player, spell);
        graphics.text(font, Lines.clip(font, spell.displayName().getString(), 136), x + 8, y + 64, 0xFF000000 | spell.color(), false);
        graphics.text(font, Lines.clip(font, Component.translatable("gui.alchyforbidden.circle_cast_all", com.lealex.alchyforbidden.essence.Spells.costLine(spell.castCosts()),
                String.format(java.util.Locale.ROOT, "%.1f", spell.cooldown() / 20F)).getString(), 136), x + 8, y + 75, theme.faint(), false);
        List<com.lealex.alchyforbidden.essence.Spell.Cost> items = spell.bindItems();
        for (int k = 0; k < items.size() && k < 3; k++) {
            var cost = items.get(k);
            int ix = x + 8 + k * 30, iy = y + 85;
            ItemStack shown = new ItemStack(cost.item());
            graphics.item(shown, ix, iy);
            boolean enough = player.getAbilities().instabuild || com.lealex.alchyforbidden.block.entity.SoulCircleBlockEntity.count(player, cost.item()) >= cost.count();
            graphics.text(font, String.valueOf(cost.count()), ix + 17, iy + 8, known ? theme.faint() : enough ? theme.text() : theme.warn(), false);
            if (over(mouseX, mouseY, ix, iy, 16, 16)) graphics.setTooltipForNextFrame(font, shown, mouseX, mouseY);
        }
        // its essences: a chip and the number each (red when the circle, the body and the altars round it lack it)
        int cell = 0;
        for (Essence essence : Essence.values()) {
            int need = spell.bindEssence(essence);
            if (need <= 0) continue;
            int ex = x + 8 + cell * 29, ey = y + SPELL_BTN_Y + 2;
            boolean enough = player.getAbilities().instabuild || circle.available(player, essence) >= need;
            graphics.text(font, essence.icon(), ex, ey, 0xFFFFFFFF, false);
            graphics.text(font, String.valueOf(need), ex + 9, ey, known ? theme.faint() : enough ? 0xFF000000 | brighter(essence.color()) : theme.warn(), false);
            if (over(mouseX, mouseY, ex, ey - 1, 27, 10)) {
                graphics.setTooltipForNextFrame(font, List.of(essence.displayName(),
                        Component.translatable("gui.alchyforbidden.circle_from", Config.circleReach())), Optional.empty(), mouseX, mouseY);
            }
            cell++;
        }
        // imbue the ring with it, or (a ring that holds a spell) the ritual that tears it out
        com.lealex.alchyforbidden.essence.Spell inRing = com.lealex.alchyforbidden.item.SpellRingItem.spell(ring);
        boolean tearing = inRing != null;
        Component refusal = tearing ? null : circle.refusal(player, spell, ring);
        Component tip = tearing ? Component.translatable("gui.alchyforbidden.circle_unbind_tip", inRing.displayName(),
                        com.lealex.alchyforbidden.block.entity.SoulCircleBlockEntity.UNBIND_SORROW, Essence.SORROW.icon())
                : refusal != null ? refusal : Component.translatable("gui.alchyforbidden.circle_bind_tip");
        button(graphics, x + SPELL_BTN_X, y + SPELL_BTN_Y, SPELL_BTN_W, Component.translatable(tearing ? "gui.alchyforbidden.circle_unbind" : "gui.alchyforbidden.circle_bind"),
                tearing ? circle.isFormed() : refusal == null, tearing, mouseX, mouseY, tip);
        // what it holds of each essence: three meters on the right edge
        for (Essence essence : Essence.values()) {
            int mx = x + 150 + essence.ordinal() * METER_STEP, top = y + 70, bottom = y + METER_BOTTOM + 40; // under the grid (icons over them)
            int cap = Math.max(1, Config.circleStore()), amount = circle.held(essence);
            meterIcon(graphics, essence, mx + METER_W / 2, top);
            graphics.fill(mx - 1, top - 1, mx + METER_W + 1, bottom + 1, 0xFF0B0A10);
            graphics.fill(mx, top, mx + METER_W, bottom, 0xFF1A1820);
            int filled = Math.round((bottom - top) * Math.min(1F, amount / (float) cap));
            graphics.fill(mx, bottom - filled, mx + METER_W, bottom, 0xFF000000 | brighter(essence.color()));
            if (over(mouseX, mouseY, mx - 1, top - 1, METER_W + 2, bottom - top + 2)) {
                graphics.setTooltipForNextFrame(font, List.of(essence.displayName(),
                        Component.translatable("gui.alchyforbidden.fountain_meter", amount, cap)), Optional.empty(), mouseX, mouseY);
            }
        }
    }

    /** The spells in rows: by the tier that opens them, each tier starting a new row, 7 to a row. */
    private static List<List<com.lealex.alchyforbidden.essence.Spell>> spellRows() {
        List<List<com.lealex.alchyforbidden.essence.Spell>> rows = new java.util.ArrayList<>();
        for (int tier = 1; tier <= 3; tier++) {
            List<com.lealex.alchyforbidden.essence.Spell> row = new java.util.ArrayList<>();
            for (com.lealex.alchyforbidden.essence.Spell spell : com.lealex.alchyforbidden.essence.Spell.values()) {
                if (spell.tier() != tier) continue;
                row.add(spell);
                if (row.size() == SPELL_COLUMNS) {
                    rows.add(row);
                    row = new java.util.ArrayList<>();
                }
            }
            if (!row.isEmpty()) rows.add(row);
        }
        return rows;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (menu.tab() == SourceMenu.MAIN && maw() && filtering
                && over(mouseX, mouseY, leftPos + MAW_COL, topPos + FILTER_LIST_Y - 2, FILTER_W, FILTER_ROWS * FILTER_ROW + 2)) {
            affixScroll = Math.max(0, Math.min(shownAffixes().size() - FILTER_ROWS, affixScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (menu.tab() == SourceMenu.MAIN && fountain() && over(mouseX, mouseY, leftPos + PICK_X - 2, topPos + PICK_Y - 2, PICK_COLUMNS * PICK_STEP + 2, PICK_ROWS * PICK_STEP + 2)) {
            int rows = (com.lealex.alchyforbidden.block.entity.WeepingFountainBlockEntity.products().size() + PICK_COLUMNS - 1) / PICK_COLUMNS;
            pickScroll = Math.max(0, Math.min(rows - PICK_ROWS, pickScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (menu.tab() == SourceMenu.MAIN && circle() && over(mouseX, mouseY, leftPos + 6, topPos + SPELL_Y - 2, SPELL_SCROLL_X - 2, SPELL_ROWS * SPELL_STEP + 2)) {
            spellScroll = Math.max(0, Math.min(spellRows().size() - SPELL_ROWS, spellScroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ---- The maw ----

    private static final int MAW_COL = 40, MAW_BTN_X = 40, MAW_BTN_Y = 104, MAW_BTN_W = 56, FILTER_BTN_X = 100, FILTER_BTN_W = 46;
    /** The Call tab shows the affix filter instead of the creature (client). */
    private boolean filtering;
    /** A Release pressed once, waiting for the second press: its slot and when. */
    private int releaseArmed = -1;
    private long releaseArmedAt;

    /** A faint picture of what goes in an empty slot, and its tooltip. */
    private void ghostSlot(GuiGraphicsExtractor graphics, WindowTheme theme, int sx, int sy, ItemStack held, ItemStack ghost, Component tip, int mouseX, int mouseY) {
        theme.well(graphics, sx - 1, sy - 1, sx + 17, sy + 17);
        if (!held.isEmpty()) return;
        graphics.fakeItem(ghost, sx, sy);
        graphics.fill(sx, sy, sx + 16, sy + 16, 0xB026232E);
        if (over(mouseX, mouseY, sx, sy, 16, 16)) {
            graphics.setTooltipForNextFrame(font, font.split(tip, 160), mouseX, mouseY);
        }
    }

    /** Summon: the lure and the blank ring at the left, what the lure calls and what it costs, the odds, Begin. */
    private void mawTab(GuiGraphicsExtractor graphics, int x, int y, com.lealex.alchyforbidden.block.entity.RiftMawBlockEntity maw, int mouseX, int mouseY) {
        WindowTheme theme = MAW;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack lure = menu.mawLure(), ring = menu.mawRing();
        ghostSlot(graphics, theme, x + SourceMenu.LURE_X, y + SourceMenu.LURE_Y, lure,
                new ItemStack(ModRegistries.LURES.get(com.lealex.alchyforbidden.familiar.RiftCreature.SCULK_CRAWLER).get()),
                Component.translatable("gui.alchyforbidden.maw_lure_slot"), mouseX, mouseY);
        ghostSlot(graphics, theme, x + SourceMenu.MAW_RING_X, y + SourceMenu.MAW_RING_Y, ring, new ItemStack(ModRegistries.FAMILIAR_RING_1.get()),
                Component.translatable("gui.alchyforbidden.maw_ring_slot"), mouseX, mouseY);
        ghostSlot(graphics, theme, x + SourceMenu.CATALYST_X, y + SourceMenu.CATALYST_Y, menu.mawCatalyst(), new ItemStack(ModRegistries.RIFT_RESIDUE.get()),
                Component.translatable("gui.alchyforbidden.maw_catalyst_slot", Config.residueOdds(), Config.residueMax()), mouseX, mouseY);
        ghostSlot(graphics, theme, x + SourceMenu.PRISM_X, y + SourceMenu.PRISM_Y, menu.mawPrism(), new ItemStack(ModRegistries.VOID_PRISM.get()),
                Component.translatable("gui.alchyforbidden.maw_prism_slot"), mouseX, mouseY);
        int cx = x + MAW_COL;
        if (affixSearch != null) affixSearch.setVisible(filtering);
        if (filtering) {
            filterPanel(graphics, x, y, mouseX, mouseY);
        } else if (maw.phase() != com.lealex.alchyforbidden.block.entity.RiftMawBlockEntity.NONE) { // a ritual under way
            var called = maw.ritualCreature();
            graphics.text(font, Component.translatable("gui.alchyforbidden.maw_running"), cx, y + 24, theme.accent(), false);
            if (called != null) graphics.text(font, Lines.clip(font, called.fullName(maw.ritualCorruption()).getString(), 100), cx, y + 36,
                    0xFF000000 | com.lealex.alchyforbidden.familiar.RiftCreature.gradeColor(maw.ritualCorruption()), false);
            int ly = y + 50;
            for (String line : Lines.wrap(font, Component.translatable("gui.alchyforbidden.maw_running_tip").getString(), 100)) {
                graphics.text(font, line, cx, ly, theme.faint(), false);
                ly += 10;
            }
        } else if (lure.getItem() instanceof com.lealex.alchyforbidden.item.RiftLureItem lureItem) {
            var called = lureItem.creature();
            graphics.text(font, Lines.clip(font, called.displayName().getString(), 100), cx, y + 24, 0xFF000000 | called.color(), false);
            graphics.text(font, Lines.clip(font, Component.translatable("gui.alchyforbidden.maw_tier", called.tier()).getString(), 104), cx, y + 35,
                    called.tier() <= maw.standing() ? theme.faint() : theme.warn(), false);
            int cell = 0;
            int[] costs = called.summonCosts();
            for (Essence essence : Essence.values()) {
                int need = costs[essence.ordinal()];
                if (need <= 0) continue;
                int ex = cx + cell * 32, ey = y + 47;
                boolean enough = player.getAbilities().instabuild || maw.available(player, essence) >= need;
                graphics.text(font, essence.icon(), ex, ey, 0xFFFFFFFF, false);
                graphics.text(font, String.valueOf(need), ex + 9, ey, enough ? 0xFF000000 | brighter(essence.color()) : theme.warn(), false);
                if (over(mouseX, mouseY, ex, ey - 1, 30, 10)) {
                    graphics.setTooltipForNextFrame(font, List.of(essence.displayName(),
                            Component.translatable("gui.alchyforbidden.maw_from", Config.mawReach())), Optional.empty(), mouseX, mouseY);
                }
                cell++;
            }
            // the odds it comes altered: its tier, the residue offered, the hour and the place
            ItemStack catalyst = menu.mawCatalyst();
            int[] odds = maw.odds(catalyst.isEmpty() ? 0 : catalyst.getCount());
            int total = Math.max(1, odds[0] + odds[1] + odds[2] + odds[3]);
            graphics.text(font, Lines.clip(font, Component.translatable("gui.alchyforbidden.maw_altered").getString(), 104), cx, y + 60, theme.faint(), false);
            for (int g = 1; g <= 3; g++) {
                int ox = cx + (g - 1) * 32, oy = y + 71;
                graphics.text(font, Math.round(odds[g] * 100F / total) + "%", ox, oy, 0xFF000000 | com.lealex.alchyforbidden.familiar.RiftCreature.gradeColor(g), false);
                if (over(mouseX, mouseY, ox, oy - 1, 30, 10)) {
                    int color = com.lealex.alchyforbidden.familiar.RiftCreature.gradeColor(g);
                    graphics.setTooltipForNextFrame(font, List.of(Component.literal(com.lealex.alchyforbidden.familiar.RiftCreature.grade(g).trim())
                            .withStyle(s -> s.withColor(color))), Optional.empty(), mouseX, mouseY);
                }
            }
            graphics.text(font, Lines.clip(font, Component.translatable("gui.alchyforbidden.maw_arena", maw.arenaRadius()).getString(), 104), cx, y + 83, theme.faint(), false);
            int stir = maw.conditionBonus();
            graphics.text(font, Lines.clip(font, Component.translatable(stir > 0 ? "gui.alchyforbidden.maw_stirs" : "gui.alchyforbidden.maw_calm", stir).getString(), 104),
                    cx, y + 93, stir > 0 ? 0xFF000000 | com.lealex.alchyforbidden.familiar.RiftCreature.gradeColor(2) : theme.faint(), false);
            if (over(mouseX, mouseY, cx, y + 92, 104, 10)) {
                boolean[] c = maw.conditions();
                List<Component> tip = new java.util.ArrayList<>();
                tip.add(Component.translatable("gui.alchyforbidden.maw_conditions"));
                tip.add(Component.translatable("gui.alchyforbidden.maw_cond_night", Config.nightOdds()).withStyle(c[0] && !c[1] ? net.minecraft.ChatFormatting.LIGHT_PURPLE : net.minecraft.ChatFormatting.DARK_GRAY));
                tip.add(Component.translatable("gui.alchyforbidden.maw_cond_moon", Config.newMoonOdds()).withStyle(c[1] ? net.minecraft.ChatFormatting.LIGHT_PURPLE : net.minecraft.ChatFormatting.DARK_GRAY));
                tip.add(Component.translatable("gui.alchyforbidden.maw_cond_deep", Config.deepDarkOdds()).withStyle(c[2] ? net.minecraft.ChatFormatting.LIGHT_PURPLE : net.minecraft.ChatFormatting.DARK_GRAY));
                graphics.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
            }
        } else {
            int ly = y + 24;
            for (String line : Lines.wrap(font, Component.translatable("gui.alchyforbidden.maw_idle").getString(), 100)) {
                graphics.text(font, line, cx, ly, theme.faint(), false);
                ly += 10;
            }
        }
        Component refusal = maw.refusal(player, lure, ring, menu.filterMask(), menu.mawPrism());
        button(graphics, x + MAW_BTN_X, y + MAW_BTN_Y, MAW_BTN_W, Component.translatable("gui.alchyforbidden.maw_begin"), refusal == null, false, mouseX, mouseY,
                refusal != null ? refusal : Component.translatable("gui.alchyforbidden.maw_begin_tip"));
        int out = com.lealex.alchyforbidden.familiar.RiftAffix.excluded(menu.filterMask());
        button(graphics, x + FILTER_BTN_X, y + MAW_BTN_Y, FILTER_BTN_W, Component.translatable(filtering ? "gui.alchyforbidden.maw_filter_done"
                        : out > 0 ? "gui.alchyforbidden.maw_filter_on" : "gui.alchyforbidden.maw_filter"), true, out > 0, mouseX, mouseY,
                Component.translatable("gui.alchyforbidden.maw_filter_tip"));
        // what it holds of each essence: three meters on the right edge
        for (Essence essence : Essence.values()) {
            int mx = x + 150 + essence.ordinal() * METER_STEP, top = y + 32, bottom = y + 116;
            int cap = Math.max(1, Config.mawStore()), amount = maw.held(essence);
            meterIcon(graphics, essence, mx + METER_W / 2, top);
            graphics.fill(mx - 1, top - 1, mx + METER_W + 1, bottom + 1, 0xFF0B0A10);
            graphics.fill(mx, top, mx + METER_W, bottom, 0xFF1A1820);
            int filled = Math.round((bottom - top) * Math.min(1F, amount / (float) cap));
            graphics.fill(mx, bottom - filled, mx + METER_W, bottom, 0xFF000000 | brighter(essence.color()));
            if (over(mouseX, mouseY, mx - 1, top - 1, METER_W + 2, bottom - top + 2)) {
                graphics.setTooltipForNextFrame(font, List.of(essence.displayName(),
                        Component.translatable("gui.alchyforbidden.fountain_meter", amount, cap)), Optional.empty(), mouseX, mouseY);
            }
        }
    }

    // ---- The affix filter: a search box over a list, one affix a row ----

    private static final int FILTER_ROWS = 5, FILTER_ROW = 10, FILTER_LIST_Y = 36, FILTER_W = 104;
    private net.minecraft.client.gui.components.@org.jspecify.annotations.Nullable EditBox affixSearch;
    private String affixQuery = "";
    private int affixScroll;

    @Override
    protected void init() {
        super.init();
        affixSearch = null;
        if (!maw()) return;
        affixSearch = new net.minecraft.client.gui.components.EditBox(font, leftPos + MAW_COL, topPos + 22, FILTER_W, 11, Component.translatable("gui.alchyforbidden.maw_filter_search"));
        affixSearch.setMaxLength(24);
        affixSearch.setHint(Component.translatable("gui.alchyforbidden.maw_filter_search").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        affixSearch.setValue(affixQuery);
        affixSearch.setResponder(text -> {
            affixQuery = text;
            affixScroll = 0;
        });
        affixSearch.setVisible(false);
        addRenderableWidget(affixSearch);
    }

    /** The affixes the search lets through (name or what it does), in order. */
    private List<com.lealex.alchyforbidden.familiar.RiftAffix> shownAffixes() {
        String q = affixQuery.trim().toLowerCase(java.util.Locale.ROOT);
        List<com.lealex.alchyforbidden.familiar.RiftAffix> out = new java.util.ArrayList<>();
        for (var affix : com.lealex.alchyforbidden.familiar.RiftAffix.values()) {
            if (q.isEmpty() || affix.displayName().getString().toLowerCase(java.util.Locale.ROOT).contains(q)
                    || affix.description().getString().toLowerCase(java.util.Locale.ROOT).contains(q)) out.add(affix);
        }
        return out;
    }

    /** The filter: the search box, the affixes that match one a row (a tick box, the name in its colour), the cost under. */
    private void filterPanel(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        WindowTheme theme = MAW;
        int cx = x + MAW_COL, mask = menu.filterMask();
        List<com.lealex.alchyforbidden.familiar.RiftAffix> shown = shownAffixes();
        affixScroll = Math.max(0, Math.min(affixScroll, shown.size() - FILTER_ROWS));
        theme.well(graphics, cx - 1, y + FILTER_LIST_Y - 2, cx + FILTER_W + 1, y + FILTER_LIST_Y + FILTER_ROWS * FILTER_ROW);
        for (int r = 0; r < FILTER_ROWS && affixScroll + r < shown.size(); r++) {
            var affix = shown.get(affixScroll + r);
            int ry = y + FILTER_LIST_Y + r * FILTER_ROW;
            boolean on = affix.in(mask), hover = over(mouseX, mouseY, cx, ry - 1, FILTER_W - 4, FILTER_ROW);
            if (hover) graphics.fill(cx, ry - 1, cx + FILTER_W - 4, ry + FILTER_ROW - 1, 0x40FFFFFF);
            graphics.fill(cx + 2, ry + 1, cx + 9, ry + 8, 0xFF0B0A10); // the tick box
            if (on) graphics.fill(cx + 3, ry + 2, cx + 8, ry + 7, 0xFF000000 | affix.color());
            graphics.text(font, Lines.clip(font, affix.displayName().getString(), FILTER_W - 18), cx + 12, ry + 1,
                    on ? 0xFF000000 | affix.color() : 0xFF55505E, false);
            if (hover) {
                List<Component> tip = new java.util.ArrayList<>();
                tip.add(affix.displayName());
                for (String line : Lines.wrap(font, affix.description().getString(), 170)) tip.add(Component.literal(line).withStyle(net.minecraft.ChatFormatting.GRAY));
                tip.add(Component.translatable(on ? "gui.alchyforbidden.maw_filter_click_off" : "gui.alchyforbidden.maw_filter_click_on").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                graphics.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
            }
        }
        if (shown.isEmpty()) graphics.text(font, Component.translatable("gui.alchyforbidden.maw_filter_nothing"), cx + 4, y + FILTER_LIST_Y + 1, theme.faint(), false);
        if (shown.size() > FILTER_ROWS) { // the scroll bar
            int top = y + FILTER_LIST_Y - 1, height = FILTER_ROWS * FILTER_ROW - 1, knob = Math.max(5, height * FILTER_ROWS / shown.size());
            int at = top + (height - knob) * affixScroll / Math.max(1, shown.size() - FILTER_ROWS);
            graphics.fill(cx + FILTER_W - 3, top, cx + FILTER_W - 1, top + height, 0xFF0B0A10);
            graphics.fill(cx + FILTER_W - 3, at, cx + FILTER_W - 1, at + knob, theme.accent());
        }
        int[] cost = com.lealex.alchyforbidden.block.entity.RiftMawBlockEntity.filterCost(mask);
        int left = Integer.bitCount(mask & com.lealex.alchyforbidden.familiar.RiftAffix.ALL);
        Component line = cost[0] == 0 ? Component.translatable("gui.alchyforbidden.maw_filter_free")
                : Component.translatable("gui.alchyforbidden.maw_filter_cost", left, cost[0], cost[1]);
        graphics.text(font, Lines.clip(font, line.getString(), FILTER_W), cx, y + 93, cost[0] == 0 ? theme.faint() : theme.warn(), false);
        if (over(mouseX, mouseY, cx, y + 92, FILTER_W, 10)) {
            graphics.setTooltipForNextFrame(font, font.split(Component.translatable("gui.alchyforbidden.maw_filter_cost_tip", cost[0], cost[1]), 180), mouseX, mouseY);
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (affixSearch != null && affixSearch.isVisible() && affixSearch.isFocused() && event.key() != 256) { // its keys while typing (E would close)
            affixSearch.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    /** Rings: the left hand's familiar rings, each with its state under it, Mend under a cracked one. */
    private void handTab(GuiGraphicsExtractor graphics, int x, int y, com.lealex.alchyforbidden.block.entity.RiftMawBlockEntity maw, int mouseX, int mouseY) {
        WindowTheme theme = MAW;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        graphics.text(font, Component.translatable("gui.alchyforbidden.hand_worn"), x + 8, y + 20, theme.faint(), false);
        int slots = com.lealex.alchyforbidden.familiar.Familiars.slots(player);
        var worn = com.lealex.alchyforbidden.familiar.Familiars.worn(player);
        var out = com.lealex.alchyforbidden.familiar.Familiars.outSlots(player);
        long now = player.level().getGameTime();
        for (int i = 0; i < SourceMenu.FAM_MAX; i++) {
            int sx = x + SourceMenu.FAM_X + i * SourceMenu.FAM_STEP, sy = y + SourceMenu.FAM_Y;
            if (i >= slots) {
                graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF0B0A10);
                graphics.fill(sx, sy, sx + 16, sy + 16, 0xFF15131B);
                if (over(mouseX, mouseY, sx, sy, 16, 16)) {
                    graphics.setTooltipForNextFrame(font, List.of(Component.translatable("gui.alchyforbidden.hand_locked")), Optional.empty(), mouseX, mouseY);
                }
                continue;
            }
            theme.well(graphics, sx - 1, sy - 1, sx + 17, sy + 17);
            var ring = i < worn.size() ? worn.get(i) : null;
            if (ring == null) {
                if (!menu.slots.get(2 + i).hasItem() && over(mouseX, mouseY, sx, sy, 16, 16)) {
                    graphics.setTooltipForNextFrame(font, font.split(Component.translatable("gui.alchyforbidden.hand_slot"), 160), mouseX, mouseY);
                }
                continue;
            }
            var data = ring.data();
            graphics.text(font, "Lv " + data.level(), sx - 2, sy + 20, theme.text(), false);
            boolean cracked = data.cracked(now);
            String state = cracked ? ((data.crackedUntil() - now) / 20 + 1) + "s" : out.contains(i)
                    ? Component.translatable("gui.alchyforbidden.hand_out").getString() : Component.translatable("gui.alchyforbidden.hand_ready").getString();
            graphics.text(font, Lines.clip(font, state, 46), sx - 2, sy + 30, cracked ? theme.warn() : out.contains(i) ? theme.accent() : theme.faint(), false);
            if (cracked) {
                int cost = Config.familiarMendSorrow() * ring.tier();
                boolean enough = player.getAbilities().instabuild || maw.available(player, Essence.SORROW) >= cost;
                button(graphics, sx - 4, sy + 42, 44, Component.translatable("gui.alchyforbidden.hand_mend"), enough && maw.isFormed(), false, mouseX, mouseY,
                        Component.translatable("gui.alchyforbidden.hand_mend_tip", cost, Essence.SORROW.icon()));
            } else {
                int[] cost = com.lealex.alchyforbidden.block.entity.RiftMawBlockEntity.corruptCost(data);
                Component refusal = maw.corruptRefusal(player, i, menu.filterMask(), menu.mawPrism());
                Component tip = cost == null ? Component.translatable("rift_maw.alchyforbidden.corrupt_max")
                        : Component.translatable("gui.alchyforbidden.hand_corrupt_tip", cost[0], Essence.CORRUPTION.icon(), cost[1], Config.corruptFailChance(),
                        refusal == null ? Component.empty() : refusal.copy().withStyle(net.minecraft.ChatFormatting.RED));
                button(graphics, sx - 4, sy + 42, 44, Component.translatable("gui.alchyforbidden.hand_corrupt"), refusal == null, false, mouseX, mouseY, tip);
            }
            boolean armed = releaseArmed == i && player.level().getGameTime() - releaseArmedAt < 60;
            button(graphics, sx - 4, sy + 56, 44, Component.translatable(armed ? "gui.alchyforbidden.hand_release_sure" : "gui.alchyforbidden.hand_release"),
                    maw.isFormed(), armed, mouseX, mouseY, Component.translatable("gui.alchyforbidden.hand_release_tip",
                            com.lealex.alchyforbidden.block.entity.RiftMawBlockEntity.releaseResidue(data)));
        }
        graphics.text(font, Lines.clip(font, Component.translatable("gui.alchyforbidden.hand_hint").getString(), 160), x + 8, y + 104, theme.faint(), false);
    }

    // ---- The circle's rings ----

    private void ringsTab(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        WindowTheme theme = CIRCLE;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        int slots = com.lealex.alchyforbidden.essence.Spells.soulSlots(player);
        graphics.text(font, Component.translatable("gui.alchyforbidden.rings_worn"), x + 8, y + 20, theme.faint(), false);
        for (int i = 0; i < SourceMenu.WORN_MAX; i++) {
            int sx = x + SourceMenu.WORN_X + i * SourceMenu.WORN_STEP, sy = y + SourceMenu.WORN_Y;
            if (i < slots) {
                theme.well(graphics, sx - 1, sy - 1, sx + 17, sy + 17);
                graphics.text(font, String.valueOf(i + 1), sx + 6, sy + 19, theme.faint(), false);
                if (!menu.slots.get(1 + i).hasItem() && over(mouseX, mouseY, sx, sy, 16, 16)) {
                    graphics.setTooltipForNextFrame(font, font.split(Component.translatable("gui.alchyforbidden.rings_worn_tip"), 160), mouseX, mouseY);
                }
            } else {
                graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF0B0A10);
                graphics.fill(sx, sy, sx + 16, sy + 16, 0xFF15131B);
                if (over(mouseX, mouseY, sx, sy, 16, 16)) {
                    graphics.setTooltipForNextFrame(font, List.of(Component.translatable("gui.alchyforbidden.rings_locked", 2 + Math.min(3, i - 1))),
                            Optional.empty(), mouseX, mouseY);
                }
            }
        }
        graphics.text(font, Component.translatable("gui.alchyforbidden.rings_bench"), x + 8, y + 62, theme.faint(), false);
        int bx = x + SourceMenu.BENCH_X, by = y + SourceMenu.BENCH_Y;
        theme.well(graphics, bx - 1, by - 1, bx + 17, by + 17);
        ItemStack ring = menu.benchRing();
        if (ring.isEmpty()) {
            graphics.fakeItem(new ItemStack(ModRegistries.SPELL_RING_3.get()), bx, by);
            graphics.fill(bx, by, bx + 16, by + 16, 0xB026232E);
        }
        int sockets = menu.sockets();
        for (int i = 0; i < SourceMenu.GEMS; i++) {
            int gx = x + SourceMenu.GEM_X + i * SourceMenu.GEM_STEP;
            if (i < sockets) {
                theme.well(graphics, gx - 1, by - 1, gx + 17, by + 17);
            } else {
                graphics.fill(gx - 1, by - 1, gx + 17, by + 17, 0xFF0B0A10);
                graphics.fill(gx, by, gx + 16, by + 16, 0xFF15131B);
            }
            if (over(mouseX, mouseY, gx, by, 16, 16) && !(i < sockets && menu.slots.get(2 + SourceMenu.WORN_MAX + i).hasItem())) {
                graphics.setTooltipForNextFrame(font, font.split(Component.translatable(ring.isEmpty() ? "gui.alchyforbidden.gem_no_ring"
                                : i < sockets ? "gui.alchyforbidden.gem_socket" : "gui.alchyforbidden.gem_locked"), 160), mouseX, mouseY);
            }
        }
        // what the bench ring's gems do: a sign and its value each, the name under the mouse
        if (!ring.isEmpty()) {
            var contents = com.lealex.alchyforbidden.item.SpellRingItem.contents(ring);
            double[] factors = {contents.costFactor(), contents.cooldownFactor(), contents.damageFactor(), contents.durationFactor(), contents.rangeFactor()};
            String[] names = {"cost", "cooldown", "damage", "duration", "range"};
            for (int k = 0; k < factors.length; k++) { // five cells of 32: the sign, then the percentage without its % (it fits)
                int sx = x + 8 + k * 32, sy = y + 100;
                graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, GEM_STATS, sx, sy, k * 8F, 0F, 8, 8, 40, 8);
                boolean changed = Math.abs(factors[k] - 1) > 1e-6;
                graphics.text(font, String.valueOf(Math.round(factors[k] * 100)), sx + 10, sy, changed ? theme.accent() : theme.faint(), false);
                if (over(mouseX, mouseY, sx, sy - 1, 30, 10)) {
                    graphics.setTooltipForNextFrame(font, List.of(Component.translatable("gui.alchyforbidden.gem_stat." + names[k], percent(factors[k]))),
                            Optional.empty(), mouseX, mouseY);
                }
            }
        }
    }

    private static String percent(double factor) {
        return Math.round(factor * 100) + "%";
    }

    // ---- Shared ----

    /** One short line left of the bar (cut to fit, never under it). */
    private void row(GuiGraphicsExtractor graphics, Component text, int x, int y, int color) {
        graphics.text(font, Lines.clip(font, text.getString(), BAR_X - 12), x + 8, y, color, false);
    }

    /** An essence's icon centred over a meter's top (cx = the meter's middle). */
    private void meterIcon(GuiGraphicsExtractor graphics, Essence essence, int cx, int top) {
        graphics.text(font, essence.icon(), cx - 4, top - 10, 0xFFFFFFFF, false);
    }

    /** The essence waiting for an altar with room: a bar, its tooltip. */
    private void bar(GuiGraphicsExtractor graphics, int x, int y, Essence essence, int amount, int cap, int mouseX, int mouseY) {
        int bx = x + BAR_X, top = y + BAR_TOP, bottom = y + BAR_BOTTOM;
        meterIcon(graphics, essence, bx + 3, top);
        graphics.fill(bx - 1, top - 1, bx + 7, bottom + 1, 0xFF0B0A10);
        graphics.fill(bx, top, bx + 6, bottom, 0xFF1A1820);
        int filled = Math.round((bottom - top) * Math.min(1F, amount / (float) Math.max(1, cap)));
        graphics.fill(bx, bottom - filled, bx + 6, bottom, 0xFF000000 | essence.color());
        if (over(mouseX, mouseY, bx - 1, top - 1, 8, bottom - top + 2)) {
            graphics.setTooltipForNextFrame(font, List.of(essence.displayName(),
                    Component.translatable("gui.alchyforbidden.resonator_waiting", amount, cap)), Optional.empty(), mouseX, mouseY);
        }
    }

    /** An essence's colour lifted for the dark panel (Corruption's deep teal is hard to read as it is). */
    private static int brighter(int color) {
        int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
        r = r + (255 - r) / 3;
        g = g + (255 - g) / 3;
        b = b + (255 - b) / 3;
        return r << 16 | g << 8 | b;
    }

    /** The next tier: its cost (given / needed) with the offering slot under, what it still wants at the right. */
    private void growth(GuiGraphicsExtractor graphics, int x, int y, GrowingCoreBlockEntity core, int mouseX, int mouseY) {
        WindowTheme theme = theme();
        var costs = core.nextCosts();
        for (int i = 0; i < costs.size() && i < 4; i++) { // four rows, 17 apart, above the offering slot (y 96)
            Config.Cost cost = costs.get(i);
            int ix = x + 10, iy = y + 22 + i * 17;
            ItemStack shown = new ItemStack(cost.item());
            graphics.item(shown, ix, iy);
            int given = core.givenOf(cost.item());
            graphics.text(font, given + "/" + cost.count(), ix + 18, iy + 4, given >= cost.count() ? theme.accent() : theme.text(), false);
            if (over(mouseX, mouseY, ix, iy, 16, 16)) graphics.setTooltipForNextFrame(font, shown, mouseX, mouseY);
        }
        int ox = x + SourceMenu.OFFER_X, oy = y + SourceMenu.OFFER_Y;
        boolean offerOpen = menu.slots.get(menu.offerSlot()).isActive(); // none at the last tier
        if (offerOpen) theme.well(graphics, ox - 1, oy - 1, ox + 17, oy + 17);
        if (offerOpen && over(mouseX, mouseY, ox, oy, 16, 16) && !menu.slots.get(menu.offerSlot()).hasItem()) {
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
        graphics.text(font, Component.translatable(maw() ? "gui.alchyforbidden.maw_short" : circle() ? "gui.alchyforbidden.circle_short" : infuser() ? "gui.alchyforbidden.infuser_short" : fountain() ? "gui.alchyforbidden.fountain_short"
                        : pit() ? "gui.alchyforbidden.pit_short" : "gui.alchyforbidden.well_short"),
                titleLabelX, 7, theme().accent(), false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, theme().faint(), false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int[] tabs = tabs();
        for (int i = 0; i < tabs.length; i++) {
            if (over(event.x(), event.y(), leftPos + tabsX() + i * (TAB_W + 2), topPos + 5, TAB_W, HEADER - 5)) {
                menu.setTab(tabs[i]);
                press(SourceMenu.TAB + tabs[i]);
                return true;
            }
        }
        if (menu.tab() == SourceMenu.MAIN && menu.core() instanceof com.lealex.alchyforbidden.block.entity.WeepingFountainBlockEntity) {
            int count = com.lealex.alchyforbidden.block.entity.WeepingFountainBlockEntity.products().size();
            for (int cell = 0; cell < PICK_ROWS * PICK_COLUMNS; cell++) {
                int i = pickScroll * PICK_COLUMNS + cell;
                if (i >= count) break;
                int px = leftPos + PICK_X + (cell % PICK_COLUMNS) * PICK_STEP, py = topPos + PICK_Y + (cell / PICK_COLUMNS) * PICK_STEP;
                if (over(event.x(), event.y(), px - 1, py - 1, 18, 18)) {
                    press(SourceMenu.PICK + i);
                    return true;
                }
            }
        }
        if (menu.tab() == SourceMenu.MAIN && menu.core() instanceof com.lealex.alchyforbidden.block.entity.SoulCircleBlockEntity circle) {
            var spells = com.lealex.alchyforbidden.essence.Spell.values();
            List<List<com.lealex.alchyforbidden.essence.Spell>> rows = spellRows();
            for (int r = 0; r < SPELL_ROWS && spellScroll + r < rows.size(); r++) {
                List<com.lealex.alchyforbidden.essence.Spell> row = rows.get(spellScroll + r);
                for (int c = 0; c < row.size(); c++) {
                    if (over(event.x(), event.y(), leftPos + SPELL_X + c * SPELL_STEP, topPos + SPELL_Y + r * SPELL_STEP, 16, 16)) {
                        pickedSpell = row.get(c).ordinal();
                        return true;
                    }
                }
            }
            var player = Minecraft.getInstance().player;
            if (player != null && over(event.x(), event.y(), leftPos + SPELL_BTN_X, topPos + SPELL_BTN_Y, SPELL_BTN_W, BTN_H)) {
                var spell = spells[Math.floorMod(pickedSpell, spells.length)];
                ItemStack ring = menu.imbueRing();
                if (com.lealex.alchyforbidden.item.SpellRingItem.spell(ring) != null) {
                    if (circle.isFormed()) press(SourceMenu.UNBIND_SPELL);
                    return true;
                }
                if (circle.refusal(player, spell, ring) == null) {
                    press(SourceMenu.BIND_SPELL + spell.ordinal());
                    return true;
                }
            }
        }
        if (menu.core() instanceof com.lealex.alchyforbidden.block.entity.RiftMawBlockEntity maw) {
            var player = Minecraft.getInstance().player;
            if (player != null && menu.tab() == SourceMenu.MAIN && over(event.x(), event.y(), leftPos + MAW_BTN_X, topPos + MAW_BTN_Y, MAW_BTN_W, BTN_H)
                    && maw.refusal(player, menu.mawLure(), menu.mawRing(), menu.filterMask(), menu.mawPrism()) == null) {
                press(SourceMenu.BEGIN);
                return true;
            }
            if (menu.tab() == SourceMenu.MAIN && over(event.x(), event.y(), leftPos + FILTER_BTN_X, topPos + MAW_BTN_Y, FILTER_BTN_W, BTN_H)) {
                filtering = !filtering;
                return true;
            }
            if (menu.tab() == SourceMenu.MAIN && filtering) {
                List<com.lealex.alchyforbidden.familiar.RiftAffix> shown = shownAffixes();
                for (int r = 0; r < FILTER_ROWS && affixScroll + r < shown.size(); r++) {
                    int ry = topPos + FILTER_LIST_Y + r * FILTER_ROW;
                    if (over(event.x(), event.y(), leftPos + MAW_COL, ry - 1, FILTER_W - 4, FILTER_ROW)) {
                        int i = shown.get(affixScroll + r).ordinal();
                        menu.toggleFilter(i);
                        press(SourceMenu.FILTER + i);
                        return true;
                    }
                }
            }
            if (player != null && menu.tab() == SourceMenu.RINGS) {
                var wornRings = com.lealex.alchyforbidden.familiar.Familiars.worn(player);
                for (int i = 0; i < wornRings.size() && i < SourceMenu.FAM_MAX; i++) {
                    if (wornRings.get(i) == null) continue;
                    int sx = leftPos + SourceMenu.FAM_X + i * SourceMenu.FAM_STEP, sy = topPos + SourceMenu.FAM_Y;
                    if (!over(event.x(), event.y(), sx - 4, sy + 56, 44, BTN_H) || !maw.isFormed()) continue;
                    long now = player.level().getGameTime();
                    if (releaseArmed == i && now - releaseArmedAt < 60) { // the second press: it goes
                        releaseArmed = -1;
                        press(SourceMenu.RELEASE + i);
                    } else {
                        releaseArmed = i;
                        releaseArmedAt = now;
                    }
                    return true;
                }
            }
            if (player != null && menu.tab() == SourceMenu.RINGS) {
                var worn = com.lealex.alchyforbidden.familiar.Familiars.worn(player);
                for (int i = 0; i < worn.size() && i < SourceMenu.FAM_MAX; i++) {
                    var ring = worn.get(i);
                    if (ring == null) continue;
                    int sx = leftPos + SourceMenu.FAM_X + i * SourceMenu.FAM_STEP, sy = topPos + SourceMenu.FAM_Y;
                    if (!over(event.x(), event.y(), sx - 4, sy + 42, 44, BTN_H)) continue;
                    if (ring.data().cracked(player.level().getGameTime())) {
                        press(SourceMenu.MEND + i);
                        return true;
                    }
                    if (maw.corruptRefusal(player, i, menu.filterMask(), menu.mawPrism()) == null) {
                        press(SourceMenu.CORRUPT + i);
                        return true;
                    }
                }
            }
        }
        if (menu.tab() == SourceMenu.MAIN && menu.core() instanceof com.lealex.alchyforbidden.block.entity.EssenceInfuserBlockEntity infuser) {
            for (Essence essence : Essence.values()) {
                if (over(event.x(), event.y(), leftPos + ESS_X + essence.ordinal() * ESS_STEP, topPos + ESS_Y, ESS_W, ESS_H)) {
                    press(SourceMenu.ESSENCE + essence.ordinal());
                    return true;
                }
            }
            if (infuser.standing() >= 3 && over(event.x(), event.y(), leftPos + BIND_X, topPos + BIND_Y, BIND_W, BTN_H)) {
                press(infuser.bound() != null ? SourceMenu.UNBIND : SourceMenu.BIND);
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
