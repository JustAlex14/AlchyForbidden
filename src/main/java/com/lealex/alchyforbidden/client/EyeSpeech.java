package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.registry.ModRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jspecify.annotations.Nullable;

/**
 * The eye speaking, as the game's people would: a box over the hotbar with its face (the Rift Eye, staring and
 * blinking), its name, and its words typed out a few letters a tick with a soft sculk click, then left a moment
 * to be read and faded away. A new line takes the old one's place.
 */
public final class EyeSpeech {
    private static final int MAX_WIDTH = 260, PORTRAIT = 36, PAD = 6, MAX_LINES = 4;
    private static final float CHARS_PER_TICK = 1.5F;
    private static final int FADE = 10;

    private static @Nullable Component line;
    private static int length;       // the line's letters
    private static int age;          // ticks since it began
    private static int life;         // ticks it stays in all
    private static int clicked;      // letters already clicked for
    private static @Nullable ItemStack face; // (made on first use: the layer is registered before the items exist)

    private EyeSpeech() {}

    public static void say(Component said) {
        line = said;
        length = said.getString().length();
        age = 0;
        clicked = 0;
        int typing = (int) Math.ceil(length / CHARS_PER_TICK);
        life = typing + 60 + length; // typed, then about a second and a letter's worth of ticks to read it
    }

    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "eye_speech"), EyeSpeech::draw);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        if (line == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            line = null;
            return;
        }
        age++;
        int shown = shown(0);
        while (clicked + 3 <= shown) { // a click every three letters while it types
            clicked += 3;
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SCULK_CLICKING,
                    1.5F + minecraft.player.getRandom().nextFloat() * 0.4F, 0.2F));
        }
        if (age > life) line = null;
    }

    /** The letters typed so far. */
    private static int shown(float partial) {
        return Math.min(length, (int) ((age + partial) * CHARS_PER_TICK));
    }

    private static void draw(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Component said = line;
        if (said == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) return;
        Font font = minecraft.font;
        int width = Math.min(MAX_WIDTH, graphics.guiWidth() - 32);
        int textWidth = width - PORTRAIT - PAD * 3;
        List<FormattedCharSequence> rows = font.split(said, textWidth);
        if (rows.size() > MAX_LINES) rows = rows.subList(0, MAX_LINES);
        int height = Math.max(PORTRAIT + PAD * 2, PAD * 2 + 11 + rows.size() * 10);
        int x = (graphics.guiWidth() - width) / 2, y = graphics.guiHeight() - 64 - height;
        float fade = Math.min(1F, (life - age) / (float) FADE);
        int a = Math.max(0, Math.min(255, (int) (fade * 255)));
        if (a < 8) return;
        // the box: dark, a violet rim, the face in its own frame
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, (a * 0xC8 / 255) << 24 | 0x7A3ED6);
        graphics.fill(x, y, x + width, y + height, (a * 0xE6 / 255) << 24 | 0x0E0A16);
        int px = x + PAD, py = y + (height - PORTRAIT) / 2;
        graphics.fill(px, py, px + PORTRAIT, py + PORTRAIT, (a * 0xFF / 255) << 24 | 0x05030A);
        graphics.fill(px + 1, py + 1, px + PORTRAIT - 1, py + PORTRAIT - 1, (a * 0xFF / 255) << 24 | 0x1A1226);
        graphics.pose().pushMatrix();
        graphics.pose().translate(px + 2, py + 2);
        graphics.pose().scale(2F, 2F);
        if (face == null) face = new ItemStack(ModRegistries.RIFT_EYE.get());
        graphics.item(face, 0, 0);
        graphics.pose().popMatrix();
        int tx = px + PORTRAIT + PAD;
        graphics.text(font, Component.translatable("gui.alchyforbidden.eye_name"), tx, y + PAD, a << 24 | 0xB98CFF, false);
        // the words, typed so far (each row keeps its own styles, icons included)
        int left = shown(delta.getGameTimeDeltaPartialTick(false));
        int row = 0;
        for (FormattedCharSequence full : rows) {
            if (left <= 0) break;
            int[] budget = {left};
            FormattedCharSequence typed = sink -> full.accept((index, style, codePoint) -> budget[0]-- > 0 && sink.accept(index, style, codePoint));
            graphics.text(font, typed, tx, y + PAD + 12 + row * 10, a << 24 | 0xE6DDF2, false);
            left -= count(full);
            row++;
        }
    }

    private static int count(FormattedCharSequence sequence) {
        int[] n = {0};
        sequence.accept((index, style, codePoint) -> {
            n[0]++;
            return true;
        });
        return n[0];
    }
}
