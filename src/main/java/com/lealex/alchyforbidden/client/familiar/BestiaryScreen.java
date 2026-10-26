package com.lealex.alchyforbidden.client.familiar;

import com.lealex.alchyforbidden.familiar.FamiliarData;
import com.lealex.alchyforbidden.familiar.Familiars;
import com.lealex.alchyforbidden.familiar.RiftBeast;
import com.lealex.alchyforbidden.familiar.RiftCreature;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.lealex.alchyx.client.Lines;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Rift Bestiary's pages. Left: every creature of the rift, then the familiars the reader wears ("Yours"). Right: the
 * picked one turning in a frame, its name, tier, lure and what calling it costs, its strength (base, or a worn
 * familiar's own with its grade and level), and its four traits and four moves with the level each comes at (a worn
 * familiar's learned ones lit). Hover a trait or move for what it does.
 */
public class BestiaryScreen extends Screen {
    private static final int W = 300, H = 190, LIST_W = 92, ROW = 12, PAGE_W = W - LIST_W - 12;
    private static final int TEXT = 0xFFE6DDF2, FAINT = 0xFF9C93AE, ACCENT = 0xFF2FC6B0, GOLD = 0xFFE8B84A, DARK = 0xFF55505E;
    private final Map<RiftCreature, RiftBeast> previews = new EnumMap<>(RiftCreature.class);
    private int picked = 0; // creatures first, then the worn rings

    public BestiaryScreen() {
        super(Component.translatable("item.alchyforbidden.rift_bestiary"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new BestiaryScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<FamiliarData.@Nullable Worn> worn() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? List.of() : Familiars.worn(player);
    }

    private @Nullable RiftBeast preview(RiftCreature creature, int corruption) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        RiftBeast beast = previews.computeIfAbsent(creature, c -> c.type().create(level, EntitySpawnReason.LOAD));
        if (beast != null) beast.previewGrade(corruption);
        return beast;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
        super.extractRenderState(graphics, mouseX, mouseY, partial);
        int x = (width - W) / 2, y = (height - H) / 2;
        graphics.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xFF0B0A10);
        graphics.fill(x, y, x + W, y + H, 0xF018161E);
        graphics.fill(x + LIST_W, y + 4, x + LIST_W + 1, y + H - 4, 0xFF2A2436);
        graphics.text(font, title, x + 6, y + 6, 0xFFB98CFF, false);
        // the list: creatures, then the reader's familiars
        RiftCreature[] all = RiftCreature.values();
        List<FamiliarData.@Nullable Worn> worn = worn();
        int ly = y + 20;
        for (int i = 0; i < all.length; i++, ly += ROW) row(graphics, x, ly, i, all[i], all[i].displayName(), all[i].color(), mouseX, mouseY);
        ly += 4;
        graphics.text(font, Component.translatable("gui.alchyforbidden.bestiary_yours"), x + 6, ly, ACCENT, false);
        ly += ROW;
        boolean any = false;
        for (int k = 0; k < worn.size(); k++) {
            var ring = worn.get(k);
            if (ring == null || ring.data().creatureOrNull() == null) continue;
            any = true;
            row(graphics, x, ly, all.length + k, ring.data().creatureOrNull(), ring.data().title(),
                    RiftCreature.gradeColor(ring.data().corruption()), mouseX, mouseY);
            ly += ROW;
        }
        if (!any) graphics.text(font, Component.translatable("gui.alchyforbidden.bestiary_none"), x + 8, ly, DARK, false);
        // the page
        RiftCreature creature;
        FamiliarData mine = null;
        if (picked < all.length) {
            creature = all[picked];
        } else {
            var ring = picked - all.length < worn.size() ? worn.get(picked - all.length) : null;
            if (ring == null || ring.data().creatureOrNull() == null) {
                picked = 0;
                creature = all[0];
            } else {
                creature = ring.data().creatureOrNull();
                mine = ring.data();
            }
        }
        page(graphics, x + LIST_W + 6, y + 6, creature, mine, mouseX, mouseY);
    }

    private void row(GuiGraphicsExtractor graphics, int x, int ly, int index, RiftCreature creature, Component name, int color, int mouseX, int mouseY) {
        boolean on = picked == index, hover = mouseX >= x + 4 && mouseX < x + LIST_W - 2 && mouseY >= ly - 2 && mouseY < ly + ROW - 2;
        if (on || hover) graphics.fill(x + 4, ly - 2, x + LIST_W - 2, ly + ROW - 2, on ? 0xFF33263A : 0xFF241C2C);
        // its portrait (the HUD's, drawn 10 px to fit the row), then its name
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, portrait(creature), x + 6, ly - 1, 0F, 0F, 10, 10, 16, 16, 16, 16);
        graphics.text(font, Lines.clip(font, name.getString(), LIST_W - 24), x + 19, ly, 0xFF000000 | color, false);
    }

    /** A creature's HUD portrait (tools/portraits.py). */
    public static net.minecraft.resources.Identifier portrait(RiftCreature creature) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath("alchyforbidden", "textures/familiar/portrait/" + creature.id() + ".png");
    }

    private static final int TILE = 20, TILE_STEP = 23;

    /** How many blocks the creature takes up drawn (its tallest or widest, its renderer's scale included). */
    private static float previewExtent(RiftCreature creature) {
        return switch (creature) {
            case SCULK_CRAWLER, SHARD_HOUND -> 1.4F;
            case VEIL_WISP -> 1.8F;
            case HOLLOW_STALKER -> 2.4F;
            case SHACKLED_BRUTE, THE_CHOIR -> 2.8F;
            case ABYSSAL_KNIGHT -> 5.0F;
            case SEVERED_SERAPH -> 5.5F;
            case WEEPING_CHANDELIER -> 6.0F;
            case VOID_GOLEM -> 7.0F;
            case BONE_DRAGON -> 12.0F;
        };
    }

    private void page(GuiGraphicsExtractor graphics, int px, int py, RiftCreature creature, @Nullable FamiliarData mine, int mouseX, int mouseY) {
        int corruption = mine == null ? 0 : mine.corruption();
        // the creature turning in its frame
        graphics.fill(px, py, px + 64, py + 74, 0xFF0F0D14);
        RiftBeast beast = preview(creature, corruption);
        if (beast != null) { // sized so the whole creature fits its frame (the great ones are many blocks across)
            int size = Math.max(4, Math.min(26, Math.round(56 / previewExtent(creature))));
            InventoryScreen.extractEntityInInventoryFollowsMouse(graphics, px + 2, py + 2, px + 62, py + 72, size, 0.0625F, mouseX, mouseY, beast);
        }
        // name, tier, lure, cost, level
        int tx = px + 70, ty = py + 2, textW = PAGE_W - 70;
        Component name = mine == null ? creature.displayName() : mine.title();
        graphics.text(font, Lines.clip(font, name.getString(), textW), tx, ty, 0xFF000000 | (mine == null ? creature.color() : RiftCreature.gradeColor(corruption)), false);
        if (over(mouseX, mouseY, tx, ty - 1, textW, 10)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.alchyforbidden.bestiary_grades")
                    .withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        }
        ty += 11;
        Component tier = Component.translatable(creature.boss() ? "gui.alchyforbidden.bestiary_tier_boss" : "gui.alchyforbidden.bestiary_tier", creature.tier());
        if (mine != null && !mine.name().isEmpty()) tier = creature.fullName(corruption).copy().append(Component.literal(", ")).append(tier); // a named one: what it is
        graphics.text(font, Lines.clip(font, tier.getString(), textW), tx, ty, creature.boss() ? 0xFFE0485A : FAINT, false);
        ty += 11;
        ItemStack lure = new ItemStack(ModRegistries.LURES.get(creature).get());
        graphics.item(lure, tx, ty);
        if (over(mouseX, mouseY, tx, ty, 16, 16)) graphics.setTooltipForNextFrame(font, lure, mouseX, mouseY);
        graphics.text(font, Lines.clip(font, lure.getHoverName().getString(), textW - 22), tx + 20, ty + 4, TEXT, false); // its lure
        ty += 20;
        if (mine != null) {
            graphics.text(font, Component.translatable("gui.alchyforbidden.bestiary_level", mine.level(), FamiliarData.MAX_LEVEL), tx, ty, ACCENT, false);
            ty += 10;
            int barW = textW - 4, filled = mine.level() >= FamiliarData.MAX_LEVEL ? barW : Math.round(barW * mine.xp() / (float) FamiliarData.toNext(mine.level()));
            graphics.fill(tx, ty, tx + barW, ty + 3, 0xFF0B0A10);
            graphics.fill(tx, ty, tx + filled, ty + 3, ACCENT);
            if (over(mouseX, mouseY, tx, ty - 2, barW, 7)) {
                graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.alchyforbidden.bestiary_xp",
                        mine.level() >= FamiliarData.MAX_LEVEL ? "-" : mine.xp() + " / " + FamiliarData.toNext(mine.level()))), mouseX, mouseY);
            }
        }
        // strength: short labels in two rows under the frame
        double hp = 0, dmg = 0, speed = 0, armor = 0, toughness = 0;
        if (beast != null) {
            hp = beast.getAttributeBaseValue(Attributes.MAX_HEALTH);
            dmg = beast.getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
            speed = beast.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
            armor = beast.getAttributeBaseValue(Attributes.ARMOR);
        }
        if (mine != null) {
            hp *= mine.healthFactor();
            dmg *= mine.damageFactor();
            speed *= mine.speedFactor();
            armor += mine.armorBonus();
            toughness = mine.toughnessBonus();
        }
        int sy = py + 80;
        stat(graphics, px, sy, "bestiary_hp", fmt(hp), 0xFFE0485A, mouseX, mouseY);
        stat(graphics, px + 66, sy, "bestiary_atk", fmt(dmg), 0xFFE88A6A, mouseX, mouseY);
        stat(graphics, px + 132, sy, "bestiary_spd", String.format(java.util.Locale.ROOT, "%.2f", speed), 0xFFD8F0F4, mouseX, mouseY);
        stat(graphics, px, sy + 11, "bestiary_arm", fmt(armor), 0xFF9CB4D8, mouseX, mouseY);
        stat(graphics, px + 66, sy + 11, "bestiary_tgh", fmt(toughness), 0xFFB98CFF, mouseX, mouseY);
        // what the rift left in it (a worn altered familiar): its affixes, what each does under the mouse
        if (mine != null && !mine.affixes().isEmpty()) {
            int ax = px;
            Component label = Component.translatable("gui.alchyforbidden.bestiary_affixes");
            graphics.text(font, label, ax, py + 104, FAINT, false);
            ax += font.width(label) + 4;
            for (String id : mine.affixes()) {
                var affix = com.lealex.alchyforbidden.familiar.RiftAffix.byId(id);
                if (affix == null) continue;
                Component affixName = affix.displayName();
                int w = font.width(affixName);
                if (ax + w > px + PAGE_W) break;
                graphics.text(font, affixName, ax, py + 104, 0xFFFFFFFF, false);
                if (over(mouseX, mouseY, ax, py + 103, w, 10)) {
                    List<Component> tip = new ArrayList<>();
                    tip.add(affixName);
                    tip.addAll(wrapped(affix.description()));
                    graphics.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
                }
                ax += w + 6;
            }
        }
        // traits and moves: a row of signs each, everything else under the mouse
        int ay = py + 118;
        graphics.text(font, Component.translatable("gui.alchyforbidden.bestiary_traits"), px, ay, ACCENT, false);
        graphics.text(font, Component.translatable("gui.alchyforbidden.bestiary_moves"), px + 100, ay, GOLD, false);
        ay += 11;
        List<RiftCreature.Trait> traits = creature.traits();
        List<RiftCreature.Move> moves = creature.moves();
        for (int i = 0; i < 4; i++) {
            if (i < traits.size()) {
                var trait = traits.get(i);
                boolean lit = mine == null || mine.level() >= trait.level();
                List<Component> tip = new ArrayList<>();
                tip.add(creature.traitName(trait).copy().withStyle(s -> s.withColor(ACCENT)));
                tip.add(Component.translatable("gui.alchyforbidden.bestiary_trait_at", trait.level()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                tip.addAll(wrapped(creature.traitDescription(trait)));
                if (!lit) tip.add(Component.translatable("gui.alchyforbidden.bestiary_not_yet", trait.level()).withStyle(net.minecraft.ChatFormatting.RED));
                tile(graphics, px + i * TILE_STEP, ay, icon(creature, trait.id()), trait.level(), lit, ACCENT, tip, mouseX, mouseY);
            }
            if (i < moves.size()) {
                var move = moves.get(i);
                boolean lit = mine == null || mine.level() >= move.level();
                List<Component> tip = new ArrayList<>();
                tip.add(creature.moveName(move).copy().withStyle(s -> s.withColor(GOLD)));
                tip.add(Component.translatable("gui.alchyforbidden.bestiary_move_at", move.level(), fmt(move.cooldown() / 20.0), fmt(move.minRange()), fmt(move.maxRange()))
                        .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                tip.addAll(wrapped(creature.moveDescription(move)));
                if (!lit) tip.add(Component.translatable("gui.alchyforbidden.bestiary_not_yet", move.level()).withStyle(net.minecraft.ChatFormatting.RED));
                tile(graphics, px + 100 + i * TILE_STEP, ay, icon(creature, move.id()), move.level(), lit, GOLD, tip, mouseX, mouseY);
            }
        }
        graphics.text(font, Component.translatable("gui.alchyforbidden.bestiary_hover"), px, ay + TILE + 6, DARK, false);
    }

    private static boolean over(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private List<Component> wrapped(Component text) {
        List<Component> out = new ArrayList<>();
        for (String line : Lines.wrap(font, text.getString(), 180)) out.add(Component.literal(line).withStyle(net.minecraft.ChatFormatting.GRAY));
        return out;
    }

    /** A short label and its value, the full name under the mouse. */
    private void stat(GuiGraphicsExtractor graphics, int x, int y, String key, String value, int color, int mouseX, int mouseY) {
        Component label = Component.translatable("gui.alchyforbidden." + key);
        graphics.text(font, label, x, y, color, false);
        graphics.text(font, value, x + font.width(label) + 3, y, TEXT, false);
        if (over(mouseX, mouseY, x, y - 1, 62, 10)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.alchyforbidden." + key + "_full")), mouseX, mouseY);
        }
    }

    private static net.minecraft.resources.Identifier icon(RiftCreature creature, String id) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath(com.lealex.alchyforbidden.AlchyForbidden.MODID,
                "textures/familiar/" + creature.id() + "_" + id + ".png");
    }

    /** A trait or move as its sign in a frame: lit when known (or in the plain book), dark with its level otherwise. */
    private void tile(GuiGraphicsExtractor graphics, int x, int y, net.minecraft.resources.Identifier texture, int level, boolean lit, int color,
                      List<Component> tip, int mouseX, int mouseY) {
        boolean hover = over(mouseX, mouseY, x, y, TILE, TILE);
        graphics.fill(x, y, x + TILE, y + TILE, hover ? 0xFFFFFFFF : lit ? color : 0xFF2A2436);
        graphics.fill(x + 1, y + 1, x + TILE - 1, y + TILE - 1, 0xFF0F0D14);
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, x + 2, y + 2, 0F, 0F, 16, 16, 16, 16);
        if (!lit) graphics.fill(x + 1, y + 1, x + TILE - 1, y + TILE - 1, 0xB8100E16);
        String lv = String.valueOf(level);
        graphics.text(font, lv, x + TILE - font.width(lv), y + TILE - 7, lit ? 0xFFFFFFFF : FAINT, true);
        if (hover) graphics.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int x = (width - W) / 2, y = (height - H) / 2;
        RiftCreature[] all = RiftCreature.values();
        int ly = y + 20;
        for (int i = 0; i < all.length; i++, ly += ROW) {
            if (hit(event, x, ly)) {
                picked = i;
                return true;
            }
        }
        ly += 4 + ROW;
        var worn = worn();
        for (int k = 0; k < worn.size(); k++) {
            var ring = worn.get(k);
            if (ring == null || ring.data().creatureOrNull() == null) continue;
            if (hit(event, x, ly)) {
                picked = all.length + k;
                return true;
            }
            ly += ROW;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static boolean hit(MouseButtonEvent event, int x, int ly) {
        return event.x() >= x + 4 && event.x() < x + LIST_W - 2 && event.y() >= ly - 2 && event.y() < ly + ROW - 2;
    }
}
