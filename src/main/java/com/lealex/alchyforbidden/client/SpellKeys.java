package com.lealex.alchyforbidden.client;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.essence.Spell;
import com.lealex.alchyforbidden.essence.Spells;
import com.lealex.alchyforbidden.network.SpellPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The spells' keys, all in the game's Controls under "AlchyForbidden" (players set them as they like): cast the spell
 * picked (R), pick the next / the previous slot (V / none), open the soul's slots (K, client/SpellSlotsScreen). The
 * slot picked is the player's own (this side); a cast sends it to the server (network/SpellPayloads.Cast).
 */
public final class SpellKeys {
    private SpellKeys() {}

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "spells"));
    public static final KeyMapping CAST = new KeyMapping("key.alchyforbidden.cast_spell", InputConstants.KEY_R, CATEGORY);
    public static final KeyMapping NEXT = new KeyMapping("key.alchyforbidden.next_spell", InputConstants.KEY_V, CATEGORY);
    public static final KeyMapping PREVIOUS = new KeyMapping("key.alchyforbidden.previous_spell", InputConstants.UNKNOWN.getValue(), CATEGORY);
    public static final KeyMapping SLOTS = new KeyMapping("key.alchyforbidden.spell_slots", InputConstants.KEY_K, CATEGORY);
    /** Calls the left hand's familiars out, or sends them back. */
    public static final KeyMapping FAMILIARS = new KeyMapping("key.alchyforbidden.call_familiars", InputConstants.KEY_G, CATEGORY);
    /** Picks the next familiar ring worn (the one the familiar key calls). */
    public static final KeyMapping NEXT_FAMILIAR = new KeyMapping("key.alchyforbidden.next_familiar", InputConstants.KEY_H, CATEGORY);
    /** Picks the previous familiar ring worn (unbound by default). */
    public static final KeyMapping PREVIOUS_FAMILIAR = new KeyMapping("key.alchyforbidden.previous_familiar", InputConstants.UNKNOWN.getValue(), CATEGORY);

    /** The familiar slot picked (this side), and when it changed. */
    private static int familiar = 0;
    private static long familiarChangedAt = -1000L;

    public static int familiar() {
        return familiar;
    }

    public static long familiarChangedAt() {
        return familiarChangedAt;
    }

    /** The slot picked, and when it was last changed (the HUD shows its name for a moment). */
    private static int selected = 0;
    private static long changedAt = -1000L;

    public static int selected() {
        return selected;
    }

    public static long changedAt() {
        return changedAt;
    }

    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(CAST);
        event.register(NEXT);
        event.register(PREVIOUS);
        event.register(SLOTS);
        event.register(FAMILIARS);
        event.register(NEXT_FAMILIAR);
        event.register(PREVIOUS_FAMILIAR);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        List<@Nullable Spell> slots = Spells.slots(player);
        if (slots.isEmpty()) {
            while (CAST.consumeClick() || NEXT.consumeClick() || PREVIOUS.consumeClick()) {} // nothing bound yet
        } else {
            if (selected >= slots.size()) selected = 0;
            while (NEXT.consumeClick()) pick(player, (selected + 1) % slots.size());
            while (PREVIOUS.consumeClick()) pick(player, (selected + slots.size() - 1) % slots.size());
            while (CAST.consumeClick()) {
                if (slots.get(selected) != null) ClientPacketDistributor.sendToServer(new SpellPayloads.Cast(selected));
            }
        }
        var rings = com.lealex.alchyforbidden.familiar.Familiars.worn(player);
        if (familiar >= rings.size() || (familiar < rings.size() && rings.get(familiar) == null)) { // the picked ring was taken off: the first one worn
            for (int i = 0; i < rings.size(); i++) if (rings.get(i) != null) { familiar = i; break; }
        }
        while (NEXT_FAMILIAR.consumeClick()) cycleFamiliar(player, rings, 1);
        while (PREVIOUS_FAMILIAR.consumeClick()) cycleFamiliar(player, rings, -1);
        while (FAMILIARS.consumeClick()) ClientPacketDistributor.sendToServer(new SpellPayloads.CallFamiliars(familiar));
        while (SLOTS.consumeClick()) {
            if (minecraft.screen == null) minecraft.setScreen(new SpellSlotsScreen());
        }
    }

    /** Steps the picked familiar ring to the next (or previous) worn one; its name shows over the hotbar either way. */
    private static void cycleFamiliar(LocalPlayer player, List<com.lealex.alchyforbidden.familiar.FamiliarData.@Nullable Worn> rings, int step) {
        int n = rings.size();
        if (n == 0) return;
        for (int k = 1; k <= n; k++) {
            int i = Math.floorMod(familiar + k * step, n);
            if (rings.get(i) != null) {
                familiar = i;
                break;
            }
        }
        familiarChangedAt = player.level().getGameTime();
    }

    private static void pick(LocalPlayer player, int slot) {
        selected = slot;
        changedAt = player.level().getGameTime();
    }
}
