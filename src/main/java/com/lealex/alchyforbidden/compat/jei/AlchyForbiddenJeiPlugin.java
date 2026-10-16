package com.lealex.alchyforbidden.compat.jei;

import com.lealex.alchyforbidden.AlchyForbidden;
import com.lealex.alchyforbidden.essence.Essence;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.lealex.alchyforbidden.rite.AltarRite;
import com.lealex.alchyforbidden.rite.AltarRites;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * JEI page for the altar's rites: the stands laid out as round the altar (the inner four, then the outer four of the
 * third tier), the result on the right, and under them the tier it needs, what it costs of each essence (in the
 * essence's colour) and how long it takes. The altar is the catalyst. Rites come from the server's list (sent on
 * join and /reload), or straight from the integrated server in single player.
 */
@JeiPlugin
public class AlchyForbiddenJeiPlugin implements IModPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "jei");
    /** One page (tab) per altar tier, the altar the catalyst of each: U on the altar shows the three. */
    public static final List<IRecipeType<AltarRite>> RITES = List.of(
            IRecipeType.create(AlchyForbidden.MODID, "altar_rite_1", AltarRite.class),
            IRecipeType.create(AlchyForbidden.MODID, "altar_rite_2", AltarRite.class),
            IRecipeType.create(AlchyForbidden.MODID, "altar_rite_3", AltarRite.class));
    private static final int TEXT = 0xFF404040;

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        for (int tier = 1; tier <= RITES.size(); tier++) {
            registration.addRecipeCategories(new RiteCategory(registration.getJeiHelpers().getGuiHelper(), tier));
        }
    }

    // What the pages hold now, per tier (to swap them when the server sends its list again), and JEI while it runs
    private static final java.util.Map<Integer, List<AltarRite>> SHOWN = new java.util.HashMap<>();
    private static mezz.jei.api.runtime.@org.jspecify.annotations.Nullable IJeiRuntime runtime;

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // The server's list may not be here yet (it comes after JEI starts): the integrated server's in single player,
        // else the rites the mod ships with, read from its own files; refresh() swaps in the server's list on arrival
        List<AltarRite> rites = AltarRites.clientList();
        if (rites.isEmpty()) rites = AltarRites.all(); // single player: the integrated server's, datapacks included
        if (rites.isEmpty() && Minecraft.getInstance().level != null) {
            rites = AltarRites.builtIn(Minecraft.getInstance().level.registryAccess()); // the mod's own files
        }
        SHOWN.clear();
        for (int tier = 1; tier <= RITES.size(); tier++) {
            List<AltarRite> ofTier = ofTier(rites, tier);
            registration.addRecipes(RITES.get(tier - 1), ofTier);
            SHOWN.put(tier, ofTier);
        }
    }

    private static List<AltarRite> ofTier(List<AltarRite> rites, int tier) {
        return rites.stream().filter(rite -> rite.tier() == tier).toList();
    }

    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        AltarRites.clientListener = AlchyForbiddenJeiPlugin::refresh;
        if (!AltarRites.clientList().isEmpty()) refresh();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        AltarRites.clientListener = () -> {};
    }

    /** The server sent its rites (join, /reload): the pages show those, in place of what they showed. */
    private static void refresh() {
        if (runtime == null) return;
        List<AltarRite> rites = AltarRites.clientList();
        for (int tier = 1; tier <= RITES.size(); tier++) {
            IRecipeType<AltarRite> type = RITES.get(tier - 1);
            List<AltarRite> old = SHOWN.getOrDefault(tier, List.of());
            if (!old.isEmpty()) runtime.getRecipeManager().hideRecipes(type, old);
            List<AltarRite> ofTier = ofTier(rites, tier);
            if (!ofTier.isEmpty()) runtime.getRecipeManager().addRecipes(type, ofTier);
            SHOWN.put(tier, ofTier);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (IRecipeType<AltarRite> type : RITES) registration.addCraftingStation(type, ModRegistries.ALTAR_ITEM.get());
    }

    private static class RiteCategory extends AbstractRecipeCategory<AltarRite> {
        // the stands' places (top-left corners of the slots), as in the altar's screen: inner NW, NE, SE, SW, then outer
        private static final int[][] STANDS = {{25, 19}, {61, 19}, {61, 55}, {25, 55}, {7, 1}, {79, 1}, {79, 73}, {7, 73}};
        private static final int CENTER_X = 44, CENTER_Y = 38, RESULT_X = 128, RESULT_Y = 37;

        RiteCategory(IGuiHelper gui, int tier) {
            super(RITES.get(tier - 1), Component.translatable("jei.alchyforbidden.altar_rite", tier),
                    gui.createDrawableItemLike(ModRegistries.ALTAR_ITEM.get()), 160, 118);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, AltarRite rite, IFocusGroup focuses) {
            List<Ingredient> ingredients = rite.ingredients();
            for (int i = 0; i < ingredients.size() && i < STANDS.length; i++) {
                List<ItemStack> stacks = ingredients.get(i).items().map(ItemStack::new).toList();
                builder.addInputSlot(STANDS[i][0], STANDS[i][1]).setStandardSlotBackground().addItemStacks(stacks);
            }
            builder.addOutputSlot(RESULT_X, RESULT_Y).setOutputSlotBackground().add(rite.result());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, AltarRite rite, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(101, 38);
        }

        @Override
        public void draw(AltarRite rite, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            // the eye in the middle, where the stands' threads meet
            graphics.fakeItem(new ItemStack(ModRegistries.RIFT_EYE.get()), CENTER_X, CENTER_Y);
            int y = 95;
            graphics.text(font, Component.translatable("jei.alchyforbidden.tier", rite.tier()), 2, y, TEXT, false);
            String time = Math.round(rite.ticks() / 20f) + " s";
            graphics.text(font, time, 158 - font.width(time), y, TEXT, false);
            int x = 2;
            y += 11;
            for (Essence essence : Essence.values()) {
                int need = rite.costs()[essence.ordinal()];
                if (need <= 0) continue;
                Component line = Component.literal(need + " ").append(essence.displayName());
                graphics.text(font, line, x, y, 0xFF000000 | darker(essence.color()), false);
                x += font.width(line) + 8;
            }
            if (rite.totalCost() == 0) graphics.text(font, Component.translatable("jei.alchyforbidden.no_cost"), 2, y, TEXT, false);
        }

        /** The essence's colour, darkened to read on JEI's light page. */
        private static int darker(int rgb) {
            int r = (rgb >> 16 & 255) * 3 / 4, g = (rgb >> 8 & 255) * 3 / 4, b = (rgb & 255) * 3 / 4;
            return r << 16 | g << 8 | b;
        }
    }
}
