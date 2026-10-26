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
 * <p>
 * And a page for the Echo Resonator, one entry per tier: what it costs (built by hand at the first tier, then given
 * to it), what it throws round its shriekers and how often each breaks (the dead warden at the last tier, which a
 * warden killed beside it opens), and the Sculk Corruption each shriek makes. Numbers from the config.
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
    /** The Echo Resonator's tiers (one entry each). */
    public static final IRecipeType<ResonatorTier> RESONATOR = IRecipeType.create(AlchyForbidden.MODID, "echo_resonator", ResonatorTier.class);

    /** One tier of the resonator, as the page shows it. */
    public record ResonatorTier(int tier) {}

    /** The Killing Pit's tiers and the Lament Well's (one entry each). */
    public static final IRecipeType<PitTier> PIT = IRecipeType.create(AlchyForbidden.MODID, "killing_pit", PitTier.class);
    public static final IRecipeType<WellTier> WELL = IRecipeType.create(AlchyForbidden.MODID, "lament_well", WellTier.class);

    public record PitTier(int tier) {}

    public record WellTier(int tier) {}

    public static final IRecipeType<com.lealex.alchyforbidden.Config.Product> FOUNTAIN =
            IRecipeType.create(AlchyForbidden.MODID, "weeping_fountain", com.lealex.alchyforbidden.Config.Product.class);

    public static final IRecipeType<com.lealex.alchyforbidden.essence.Inscription> CARVINGS = IRecipeType.create(AlchyForbidden.MODID, "bier_carving", com.lealex.alchyforbidden.essence.Inscription.class);

    /** The Binding Circle's spells (one page each), the Rift Maw's creatures (one page each), the Essence Infuser's tiers. */
    public static final IRecipeType<com.lealex.alchyforbidden.essence.Spell> SPELLS = IRecipeType.create(AlchyForbidden.MODID, "binding_circle", com.lealex.alchyforbidden.essence.Spell.class);
    public static final IRecipeType<com.lealex.alchyforbidden.familiar.RiftCreature> CREATURES = IRecipeType.create(AlchyForbidden.MODID, "rift_maw", com.lealex.alchyforbidden.familiar.RiftCreature.class);
    public static final IRecipeType<InfuserTier> INFUSER = IRecipeType.create(AlchyForbidden.MODID, "essence_infuser", InfuserTier.class);

    public record InfuserTier(int tier) {}

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        for (int tier = 1; tier <= RITES.size(); tier++) {
            registration.addRecipeCategories(new RiteCategory(registration.getJeiHelpers().getGuiHelper(), tier));
        }
        registration.addRecipeCategories(new ResonatorCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new PitCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new WellCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new FountainCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new CarvingCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new SpellCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new CreatureCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new InfuserCategory(registration.getJeiHelpers().getGuiHelper()));
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
        registration.addRecipes(RESONATOR, List.of(new ResonatorTier(1), new ResonatorTier(2), new ResonatorTier(3)));
        registration.addRecipes(PIT, List.of(new PitTier(1), new PitTier(2), new PitTier(3)));
        registration.addRecipes(WELL, List.of(new WellTier(1), new WellTier(2), new WellTier(3)));
        registration.addRecipes(FOUNTAIN, com.lealex.alchyforbidden.Config.fountainProducts());
        registration.addRecipes(CARVINGS, java.util.Arrays.stream(com.lealex.alchyforbidden.essence.Inscription.values())
                .sorted(java.util.Comparator.comparingInt(com.lealex.alchyforbidden.essence.Inscription::tier).thenComparing(c -> c.part().ordinal())).toList());
        registration.addRecipes(SPELLS, java.util.Arrays.stream(com.lealex.alchyforbidden.essence.Spell.values())
                .sorted(java.util.Comparator.comparingInt(com.lealex.alchyforbidden.essence.Spell::tier)).toList());
        registration.addRecipes(CREATURES, java.util.Arrays.stream(com.lealex.alchyforbidden.familiar.RiftCreature.values())
                .sorted(java.util.Comparator.comparingInt(com.lealex.alchyforbidden.familiar.RiftCreature::tier)).toList());
        registration.addRecipes(INFUSER, List.of(new InfuserTier(1), new InfuserTier(2), new InfuserTier(3)));
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
            List<AltarRite> ofTier = ofTier(rites, tier);
            if (old.equals(ofTier)) continue; // the same rites: nothing to do (hiding and re-adding them hid them for good)
            List<AltarRite> gone = old.stream().filter(rite -> !ofTier.contains(rite)).toList();
            List<AltarRite> fresh = ofTier.stream().filter(rite -> !old.contains(rite)).toList();
            if (!gone.isEmpty()) runtime.getRecipeManager().hideRecipes(type, gone);
            if (!ofTier.isEmpty()) runtime.getRecipeManager().unhideRecipes(type, ofTier); // (hidden by an older refresh)
            if (!fresh.isEmpty()) runtime.getRecipeManager().addRecipes(type, fresh);
            SHOWN.put(tier, ofTier);
        }
    }

    /** Clicking the altar's "Rites" heading shows its rites; clicking the resonator's round shows its page. */
    @Override
    public void registerGuiHandlers(mezz.jei.api.registration.IGuiHandlerRegistration registration) {
        registration.addRecipeClickArea(com.lealex.alchyforbidden.client.AltarScreen.class, 177, 21, 58, 12,
                RITES.toArray(new IRecipeType<?>[0]));
        registration.addRecipeClickArea(com.lealex.alchyforbidden.client.ResonatorScreen.class, 52, 22, 60, 60, RESONATOR);
        registration.addRecipeClickArea(com.lealex.alchyforbidden.client.SourceScreen.class, 4, 4, 18, 12, PIT, WELL, FOUNTAIN, SPELLS, CREATURES, INFUSER);
        registration.addRecipeClickArea(com.lealex.alchyforbidden.client.InscribingScreen.class, 4, 4, 18, 12, CARVINGS);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (IRecipeType<AltarRite> type : RITES) registration.addCraftingStation(type, ModRegistries.ALTAR_ITEM.get());
        registration.addCraftingStation(RESONATOR, ModRegistries.RESONATOR_ITEM.get());
        registration.addCraftingStation(PIT, ModRegistries.KILLING_PIT_ITEM.get());
        registration.addCraftingStation(WELL, ModRegistries.LAMENT_WELL_ITEM.get());
        registration.addCraftingStation(FOUNTAIN, ModRegistries.WEEPING_FOUNTAIN_ITEM.get());
        registration.addCraftingStation(CARVINGS, ModRegistries.INSCRIBING_BIER_ITEM.get());
        registration.addCraftingStation(SPELLS, ModRegistries.SOUL_CIRCLE_ITEM.get());
        registration.addCraftingStation(CREATURES, ModRegistries.RIFT_MAW_ITEM.get());
        registration.addCraftingStation(CREATURES, ModRegistries.RIFT_BESTIARY.get());
        registration.addCraftingStation(INFUSER, ModRegistries.ESSENCE_INFUSER_ITEM.get());
    }

    private static class RiteCategory extends AbstractRecipeCategory<AltarRite> {
        // the stands' places (top-left corners of the slots), as in the altar's screen: inner NW, NE, SE, SW, then outer
        private static final int[][] STANDS = {{25, 19}, {61, 19}, {61, 55}, {25, 55}, {7, 1}, {79, 1}, {79, 73}, {7, 73}};
        private static final int CENTER_X = 44, CENTER_Y = 38, RESULT_X = 128, RESULT_Y = 37;

        RiteCategory(IGuiHelper gui, int tier) {
            super(RITES.get(tier - 1), Component.translatable("jei.alchyforbidden.altar_rite", tier),
                    gui.createDrawableItemLike(ModRegistries.ALTAR_ITEM.get()), 160, 140); // (room for all three essences, a line each at most)
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
                Component line = Component.literal(need + " ").append(essence.icon());
                if (x > 2 && x + font.width(line) > 158) { // no room left on this line: the next one
                    x = 2;
                    y += 11;
                }
                graphics.text(font, line, x, y, 0xFF000000 | darker(essence.color()), false);
                x += font.width(line) + 8;
            }
            if (rite.totalCost() == 0) graphics.text(font, Component.translatable("jei.alchyforbidden.no_cost"), 2, y, TEXT, false);
        }

        /** The essence's colour, darkened to read on JEI's light page. */
        static int darker(int rgb) {
            int r = (rgb >> 16 & 255) * 3 / 4, g = (rgb >> 8 & 255) * 3 / 4, b = (rgb & 255) * 3 / 4;
            return r << 16 | g << 8 | b;
        }
    }

    private static class ResonatorCategory extends AbstractRecipeCategory<ResonatorTier> {
        // rows: the tier, its cost, what it throws, then three short lines; every line fits the page, none wraps
        private static final int COST_Y = 11, THROWN_Y = 44, LINES_Y = 68, STEP = 20;
        private static final int GREY = 0xFF707070;

        ResonatorCategory(IGuiHelper gui) {
            super(RESONATOR, Component.translatable("jei.alchyforbidden.resonator"),
                    gui.createDrawableItemLike(ModRegistries.RESONATOR_ITEM.get()), 160, 102);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, ResonatorTier entry, IFocusGroup focuses) {
            if (entry.tier() == 1) { // built by hand round its core: a 5 x 5 floor, a shrieker on each corner
                builder.addInputSlot(1, COST_Y).setStandardSlotBackground().add(new ItemStack(ModRegistries.RESONATOR_ITEM.get()));
                List<ItemStack> floor = new java.util.ArrayList<>(); // any block of #altar_base, 25 of them
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getTagOrEmpty(net.minecraft.tags.TagKey.create(
                        net.minecraft.core.registries.Registries.BLOCK, Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "altar_base")))
                        .forEach(holder -> {
                            ItemStack stack = new ItemStack(holder.value(), 25);
                            if (!stack.isEmpty()) floor.add(stack);
                        });
                if (floor.isEmpty()) floor.add(new ItemStack(net.minecraft.world.item.Items.POLISHED_DEEPSLATE, 25));
                builder.addInputSlot(1 + STEP, COST_Y).setStandardSlotBackground().addItemStacks(floor);
                builder.addInputSlot(1 + STEP * 2, COST_Y).setStandardSlotBackground().add(new ItemStack(net.minecraft.world.item.Items.SCULK_SHRIEKER, 4));
            } else { // given to it to grow this tier
                List<com.lealex.alchyforbidden.Config.Cost> costs = com.lealex.alchyforbidden.Config.resonatorCostFor(entry.tier());
                for (int i = 0; i < costs.size() && i < 7; i++) {
                    builder.addInputSlot(1 + i * STEP, COST_Y).setStandardSlotBackground().add(new ItemStack(costs.get(i).item(), costs.get(i).count()));
                }
            }
            if (entry.tier() < 3) { // what it throws round the shriekers
                builder.addInputSlot(1, THROWN_Y).setStandardSlotBackground().add(new ItemStack(net.minecraft.world.item.Items.ENDER_EYE));
                builder.addInputSlot(56, THROWN_Y).setStandardSlotBackground().add(new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL));
            } else { // the dead warden on its table instead
                builder.addInputSlot(1, THROWN_Y).setStandardSlotBackground().add(new ItemStack(net.minecraft.world.item.Items.WARDEN_SPAWN_EGG));
            }
        }

        @Override
        public void draw(ResonatorTier entry, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            int tier = entry.tier();
            graphics.text(font, Component.translatable("jei.alchyforbidden.tier", tier), 1, 1, TEXT, false);
            right(graphics, font, Component.translatable(tier == 1 ? "jei.alchyforbidden.resonator_built" : "jei.alchyforbidden.resonator_given"), 1, GREY);
            graphics.text(font, Component.translatable(tier < 3 ? "jei.alchyforbidden.resonator_thrown" : "jei.alchyforbidden.resonator_drained"),
                    1, THROWN_Y - 10, GREY, false);
            if (tier < 3) {
                graphics.text(font, com.lealex.alchyforbidden.Config.resonatorEyeBreakChance() + "%", 22, THROWN_Y + 5, TEXT, false);
                graphics.text(font, com.lealex.alchyforbidden.Config.resonatorBreakChance() + "%", 77, THROWN_Y + 5, TEXT, false);
            } else {
                graphics.text(font, Component.translatable("jei.alchyforbidden.resonator_warden"), 22, THROWN_Y + 5, TEXT, false);
            }
            int per = com.lealex.alchyforbidden.Config.resonatorPerBoom(tier);
            graphics.text(font, Component.translatable("jei.alchyforbidden.resonator_per_shriek", per), 1, LINES_Y,
                    0xFF000000 | RiteCategory.darker(Essence.CORRUPTION.color()), false);
            float every = (tier < 3 ? com.lealex.alchyforbidden.Config.resonatorLegTicks(tier) : com.lealex.alchyforbidden.Config.resonatorBlastTicks()) / 20f;
            graphics.text(font, Component.translatable(tier < 3 ? "jei.alchyforbidden.resonator_every" : "jei.alchyforbidden.resonator_every_all", String.format(java.util.Locale.ROOT, "%.1f", every)),
                    1, LINES_Y + 11, TEXT, false);
            graphics.text(font, Component.translatable(tier == 3 ? "jei.alchyforbidden.resonator_needs_kill" : "jei.alchyforbidden.resonator_to_altar"),
                    1, LINES_Y + 22, tier == 3 ? 0xFF8A3030 : GREY, false);
        }

        private static void right(GuiGraphicsExtractor graphics, Font font, Component text, int y, int color) {
            graphics.text(font, text, 159 - font.width(text), y, color, false);
        }
    }

    // ---- The essence sources: a tier per entry, its cost (built by hand, or given), then what it does ----

    /** Cost slots of a source's tier: the hand-built first tier's blocks, or the next tier's cost from the config. */
    private static void costRow(IRecipeLayoutBuilder builder, int tier, List<ItemStack> built, List<com.lealex.alchyforbidden.Config.Cost> given) {
        if (tier == 1) {
            for (int i = 0; i < built.size(); i++) builder.addInputSlot(1 + i * 20, 11).setStandardSlotBackground().add(built.get(i));
        } else {
            for (int i = 0; i < given.size() && i < 7; i++) {
                builder.addInputSlot(1 + i * 20, 11).setStandardSlotBackground().add(new ItemStack(given.get(i).item(), given.get(i).count()));
            }
        }
    }

    /** 25 of any block of #altar_base (the hand-built floors). */
    private static List<ItemStack> baseBlocks(int count) {
        List<ItemStack> found = new java.util.ArrayList<>();
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getTagOrEmpty(net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.BLOCK, Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "altar_base")))
                .forEach(holder -> {
                    ItemStack stack = new ItemStack(holder.value(), count);
                    if (!stack.isEmpty()) found.add(stack);
                });
        if (found.isEmpty()) found.add(new ItemStack(net.minecraft.world.item.Items.POLISHED_DEEPSLATE, count));
        return found;
    }

    private static void header(GuiGraphicsExtractor graphics, Font font, int tier) {
        graphics.text(font, Component.translatable("jei.alchyforbidden.tier", tier), 1, 1, TEXT, false);
        Component how = Component.translatable(tier == 1 ? "jei.alchyforbidden.resonator_built" : "jei.alchyforbidden.resonator_given");
        graphics.text(font, how, 159 - font.width(how), 1, 0xFF707070, false);
    }

    private static class PitCategory extends AbstractRecipeCategory<PitTier> {
        PitCategory(IGuiHelper gui) {
            super(PIT, Component.translatable("jei.alchyforbidden.pit"), gui.createDrawableItemLike(ModRegistries.KILLING_PIT_ITEM.get()), 160, 112);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, PitTier entry, IFocusGroup focuses) {
            // tier 1 by hand: the core, a 5 x 5 floor and a 5 x 5 ring two high (16 x 2 + 24 = 56 blocks)
            List<ItemStack> core = List.of(new ItemStack(ModRegistries.KILLING_PIT_ITEM.get()));
            costRow(builder, entry.tier(), List.of(core.getFirst()), com.lealex.alchyforbidden.Config.pitCostFor(entry.tier()));
            if (entry.tier() == 1) builder.addInputSlot(21, 11).setStandardSlotBackground().addItemStacks(baseBlocks(56));
        }

        @Override
        public void draw(PitTier entry, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            int tier = entry.tier(), y = 34, red = 0xFF000000 | RiteCategory.darker(Essence.WRATH.color());
            header(graphics, font, tier);
            graphics.text(font, Component.translatable("jei.alchyforbidden.pit_hurts", com.lealex.alchyforbidden.Config.pitDamage(tier)), 1, y, TEXT, false);
            int base = com.lealex.alchyforbidden.Config.pitWrathPerSecond(tier);
            graphics.text(font, Component.translatable("jei.alchyforbidden.pit_rate", base), 1, y += 11, red, false);
            graphics.text(font, Component.translatable("jei.alchyforbidden.pit_grows", base, com.lealex.alchyforbidden.Config.pitTortureSeconds()), 1, y += 11, red, false);
            String cap = com.lealex.alchyforbidden.Config.pitCapPerHealth() == Math.floor(com.lealex.alchyforbidden.Config.pitCapPerHealth())
                    ? String.valueOf((int) com.lealex.alchyforbidden.Config.pitCapPerHealth()) : String.valueOf(com.lealex.alchyforbidden.Config.pitCapPerHealth());
            graphics.text(font, Component.translatable("jei.alchyforbidden.pit_cap", cap), 1, y += 11, red, false);
            graphics.text(font, Component.translatable("jei.alchyforbidden.pit_kill", com.lealex.alchyforbidden.Config.pitWrathPercent(tier)), 1, y += 11, TEXT, false);
            if (tier >= 2) graphics.text(font, Component.translatable("jei.alchyforbidden.pit_lure", com.lealex.alchyforbidden.Config.pitLureCount()), 1, y += 11, TEXT, false);
            if (tier >= 3) graphics.text(font, Component.translatable("jei.alchyforbidden.pit_breed", com.lealex.alchyforbidden.Config.pitHealCost()), 1, y += 11, TEXT, false);
        }
    }

    private static class WellCategory extends AbstractRecipeCategory<WellTier> {
        WellCategory(IGuiHelper gui) {
            super(WELL, Component.translatable("jei.alchyforbidden.well"), gui.createDrawableItemLike(ModRegistries.LAMENT_WELL_ITEM.get()), 160, 108);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, WellTier entry, IFocusGroup focuses) {
            // tier 1 by hand: the core, a 5 x 5 floor and a waist-high ring round the hole (24 + 8 = 32 blocks), 4 candles
            costRow(builder, entry.tier(), List.of(new ItemStack(ModRegistries.LAMENT_WELL_ITEM.get())), com.lealex.alchyforbidden.Config.wellCostFor(entry.tier()));
            if (entry.tier() == 1) {
                builder.addInputSlot(21, 11).setStandardSlotBackground().addItemStacks(baseBlocks(32));
                builder.addInputSlot(41, 11).setStandardSlotBackground().add(new ItemStack(net.minecraft.world.item.Items.CANDLE, 4));
            }
            if (entry.tier() == 2) { // what it takes as offerings of loss
                ItemStack named = new ItemStack(net.minecraft.world.item.Items.NAME_TAG);
                ItemStack broken = new ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
                broken.setDamageValue(broken.getMaxDamage() - 5);
                List<ItemStack> offerings = List.of(named, broken, new ItemStack(net.minecraft.world.item.Items.MUSIC_DISC_11),
                        new ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING));
                for (int i = 0; i < offerings.size(); i++) builder.addInputSlot(1 + i * 20, 89).setStandardSlotBackground().add(offerings.get(i));
            }
        }

        @Override
        public void draw(WellTier entry, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            int tier = entry.tier(), y = 34, teal = 0xFF000000 | RiteCategory.darker(Essence.SORROW.color());
            header(graphics, font, tier);
            graphics.text(font, Component.translatable("jei.alchyforbidden.well_death", com.lealex.alchyforbidden.Config.wellGhostRange(tier)), 1, y, TEXT, false);
            graphics.text(font, Component.translatable("jei.alchyforbidden.well_ghost_time", com.lealex.alchyforbidden.Config.wellGhostTicks() / 20), 1, y += 11, TEXT, false);
            graphics.text(font, Component.translatable("jei.alchyforbidden.well_ghosts", com.lealex.alchyforbidden.Config.wellGhostSorrow(tier)), 1, y += 11, teal, false);
            graphics.text(font, Component.translatable("jei.alchyforbidden.well_max", com.lealex.alchyforbidden.Config.wellMaxGhosts(tier)), 1, y += 11, TEXT, false);
            if (tier == 2) graphics.text(font, Component.translatable("jei.alchyforbidden.well_offerings"), 1, 79, 0xFF707070, false);
        }
    }

    /** One thing the Weeping Fountain weeps: the item, what it costs, how long at each tier, the tier opening it. */
    private static class FountainCategory extends AbstractRecipeCategory<com.lealex.alchyforbidden.Config.Product> {
        FountainCategory(IGuiHelper gui) {
            super(FOUNTAIN, Component.translatable("jei.alchyforbidden.fountain"), gui.createDrawableItemLike(ModRegistries.WEEPING_FOUNTAIN_ITEM.get()), 160, 48);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, com.lealex.alchyforbidden.Config.Product product, IFocusGroup focuses) {
            builder.addOutputSlot(1, 16).setOutputSlotBackground().add(new ItemStack(product.item()));
        }

        @Override
        public void draw(com.lealex.alchyforbidden.Config.Product product, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            int color = 0xFF000000 | RiteCategory.darker(product.essence().color());
            graphics.text(font, Component.translatable("jei.alchyforbidden.fountain_tier", product.tier()), 30, 2, TEXT, false);
            graphics.text(font, Component.translatable("jei.alchyforbidden.fountain_cost", product.cost(), product.essence().icon()), 30, 14, color, false);
            String times = "";
            for (int tier = product.tier(); tier <= 3; tier++) {
                double seconds = product.seconds() / com.lealex.alchyforbidden.Config.fountainSpeed(tier);
                times += (times.isEmpty() ? "" : " / ") + String.format(java.util.Locale.ROOT, "%.0fs", seconds);
            }
            graphics.text(font, Component.translatable("jei.alchyforbidden.fountain_time", times), 30, 26, TEXT, false);
            graphics.text(font, Component.translatable("jei.alchyforbidden.fountain_from"), 30, 38, 0xFF707070, false);
        }
    }

    /** A carving the Inscribing Bier writes into a body: its part and tier, what it takes, what it does, what it costs to keep. */
    private static class CarvingCategory extends AbstractRecipeCategory<com.lealex.alchyforbidden.essence.Inscription> {
        CarvingCategory(IGuiHelper gui) {
            super(CARVINGS, Component.translatable("jei.alchyforbidden.carving"), gui.createDrawableItemLike(ModRegistries.INSCRIBING_BIER_ITEM.get()), 160, 96);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, com.lealex.alchyforbidden.essence.Inscription carving, IFocusGroup focuses) {
            var costs = carving.costs();
            for (int i = 0; i < costs.size() && i < 6; i++) builder.addInputSlot(1 + i * 20, 24).setStandardSlotBackground().add(costs.get(i).stack());
        }

        @Override
        public void draw(com.lealex.alchyforbidden.essence.Inscription carving, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            int color = 0xFF000000 | RiteCategory.darker(carving.color());
            graphics.text(font, Component.translatable("inscription.alchyforbidden." + carving.id()), 1, 2, color, false);
            graphics.text(font, Component.translatable("jei.alchyforbidden.carving_where",
                    Component.translatable("inscription.alchyforbidden.part." + carving.part().id()), carving.tier()), 1, 12, TEXT, false);
            int x = 1; // its essences, one after the other in their colours
            for (Essence essence : Essence.values()) {
                int need = carving.essence(essence);
                if (need <= 0) continue;
                Component part = Component.literal(need + " ").append(essence.icon());
                graphics.text(font, part, x, 46, 0xFF000000 | RiteCategory.darker(essence.color()), false);
                x += font.width(part) + 8;
            }
            int y = 58;
            for (String line : com.lealex.alchyx.client.Lines.wrap(font, Component.translatable("inscription.alchyforbidden." + carving.id() + ".effect").getString(), 158)) {
                if (y > 76) break;
                graphics.text(font, line, 1, y, TEXT, false);
                y += 10;
            }
            com.lealex.alchyforbidden.Config.Upkeep upkeep = com.lealex.alchyforbidden.Config.upkeep(carving);
            if (upkeep != null) {
                graphics.text(font, Component.translatable("jei.alchyforbidden.carving_upkeep", upkeep.amount(), upkeep.essence().icon()), 1, 86,
                        0xFF000000 | RiteCategory.darker(upkeep.essence().color()), false);
            }
        }
    }

    /** Lines of text wrapped to the page, styles (icons) kept; at most {@code most} lines from y. Returns the next y. */
    private static int wrapped(GuiGraphicsExtractor graphics, Font font, Component text, int y, int most, int color) {
        int shown = 0;
        for (net.minecraft.util.FormattedCharSequence line : font.split(text, 158)) {
            if (shown++ >= most) break;
            graphics.text(font, line, 1, y, color, false);
            y += 10;
        }
        return y;
    }

    private static String seconds(int ticks) {
        return ticks % 20 == 0 ? String.valueOf(ticks / 20) : String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0);
    }

    /** A spell the Binding Circle imbues into a ring: the circle tier and ring it needs, what imbuing takes, what a cast costs, what it does. */
    private static class SpellCategory extends AbstractRecipeCategory<com.lealex.alchyforbidden.essence.Spell> {
        SpellCategory(IGuiHelper gui) {
            super(SPELLS, Component.translatable("jei.alchyforbidden.spell"), gui.createDrawableItemLike(ModRegistries.SOUL_CIRCLE_ITEM.get()), 160, 104);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, com.lealex.alchyforbidden.essence.Spell spell, IFocusGroup focuses) {
            builder.addInputSlot(1, 24).setStandardSlotBackground().add(new ItemStack(ModRegistries.ringOfTier(spell.tier())));
            var items = spell.bindItems();
            for (int i = 0; i < items.size() && i < 5; i++) {
                builder.addInputSlot(29 + i * 20, 24).setStandardSlotBackground().add(new ItemStack(items.get(i).item(), items.get(i).count()));
            }
        }

        @Override
        public void draw(com.lealex.alchyforbidden.essence.Spell spell, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            graphics.text(font, spell.displayName(), 1, 2, 0xFF000000 | RiteCategory.darker(spell.color()), false);
            graphics.text(font, Component.translatable("jei.alchyforbidden.spell_where", spell.tier()), 1, 12, TEXT, false);
            int[] imbue = new int[Essence.values().length];
            for (Essence essence : Essence.values()) imbue[essence.ordinal()] = spell.bindEssence(essence);
            graphics.text(font, Component.translatable("jei.alchyforbidden.spell_imbue", com.lealex.alchyforbidden.essence.Spells.costLine(imbue)), 1, 46, TEXT, false);
            graphics.text(font, Component.translatable("jei.alchyforbidden.spell_cast",
                    com.lealex.alchyforbidden.essence.Spells.costLine(spell.castCosts()), seconds(spell.cooldown())), 1, 57, TEXT, false);
            wrapped(graphics, font, spell.description(), 70, 3, 0xFF707070);
        }
    }

    /** A creature the Rift Maw calls: its lure and the ring it binds into, the maw tier, what the call costs, where it is fought. */
    private static class CreatureCategory extends AbstractRecipeCategory<com.lealex.alchyforbidden.familiar.RiftCreature> {
        CreatureCategory(IGuiHelper gui) {
            super(CREATURES, Component.translatable("jei.alchyforbidden.creature"), gui.createDrawableItemLike(ModRegistries.RIFT_MAW_ITEM.get()), 160, 100);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, com.lealex.alchyforbidden.familiar.RiftCreature creature, IFocusGroup focuses) {
            var lure = ModRegistries.LURES.get(creature);
            if (lure != null) builder.addInputSlot(1, 24).setStandardSlotBackground().add(new ItemStack(lure.get()));
            net.minecraft.world.item.Item ring = ModRegistries.familiarRingOfTier(creature.tier());
            builder.addInputSlot(21, 24).setStandardSlotBackground().add(new ItemStack(ring));
            ItemStack bound = new ItemStack(ring);
            bound.set(ModRegistries.FAMILIAR.get(), com.lealex.alchyforbidden.familiar.FamiliarData.fresh(creature, 0, List.of()));
            builder.addOutputSlot(61, 24).setOutputSlotBackground().add(bound);
        }

        @Override
        public void draw(com.lealex.alchyforbidden.familiar.RiftCreature creature, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            Component name = creature.displayName();
            graphics.text(font, name, 1, 2, 0xFF000000 | RiteCategory.darker(creature.color()), false);
            if (creature.boss()) {
                graphics.text(font, Component.translatable("jei.alchyforbidden.creature_boss"), 4 + font.width(name), 2, 0xFF8A2030, false);
            }
            graphics.text(font, Component.translatable("jei.alchyforbidden.creature_where", creature.tier()), 1, 12, TEXT, false);
            graphics.text(font, ">", 45, 29, TEXT, false);
            graphics.text(font, Component.translatable("jei.alchyforbidden.creature_cost",
                    com.lealex.alchyforbidden.essence.Spells.costLine(creature.summonCosts())), 1, 46, TEXT, false);
            Component arena = creature.tier() >= 4
                    ? Component.translatable("jei.alchyforbidden.creature_void", com.lealex.alchyforbidden.Config.voidArenaRadius())
                    : Component.translatable("jei.alchyforbidden.creature_arena", com.lealex.alchyforbidden.Config.arenaRadius(creature.tier()));
            graphics.text(font, arena, 1, 57, TEXT, false);
            wrapped(graphics, font, Component.translatable("jei.alchyforbidden.creature_bind"), 70, 1, TEXT);
            wrapped(graphics, font, Component.translatable("jei.alchyforbidden.creature_more"), 82, 1, 0xFF707070);
        }
    }

    /** One tier of the Essence Infuser: empty vessels in, filled vessels out, how fast, and the bond with a player at the last tier. */
    private static class InfuserCategory extends AbstractRecipeCategory<InfuserTier> {
        InfuserCategory(IGuiHelper gui) {
            super(INFUSER, Component.translatable("jei.alchyforbidden.infuser"), gui.createDrawableItemLike(ModRegistries.ESSENCE_INFUSER_ITEM.get()), 160, 78);
        }

        private static List<net.minecraft.world.item.Item> vessels() {
            return List.of(ModRegistries.CRACKED_VESSEL.get(), ModRegistries.BOUND_VESSEL.get(), ModRegistries.RIFT_VESSEL.get());
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, InfuserTier tier, IFocusGroup focuses) {
            List<ItemStack> empty = new java.util.ArrayList<>(), full = new java.util.ArrayList<>();
            for (Essence essence : Essence.values()) { // in step: each vessel, then each essence
                for (net.minecraft.world.item.Item vessel : vessels()) {
                    empty.add(new ItemStack(vessel));
                    ItemStack filled = new ItemStack(vessel);
                    com.lealex.alchyforbidden.item.VesselItem.fill(filled, essence, Integer.MAX_VALUE);
                    full.add(filled);
                }
            }
            builder.addInputSlot(1, 14).setStandardSlotBackground().addItemStacks(empty);
            builder.addOutputSlot(41, 14).setOutputSlotBackground().addItemStacks(full);
        }

        @Override
        public void draw(InfuserTier tier, IRecipeSlotsView slots, GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            Font font = Minecraft.getInstance().font;
            int t = tier.tier();
            graphics.text(font, Component.translatable("jei.alchyforbidden.infuser_tier", t), 1, 2, TEXT, false);
            graphics.text(font, ">", 25, 19, TEXT, false);
            graphics.text(font, Component.translatable("jei.alchyforbidden.infuser_rate", com.lealex.alchyforbidden.Config.infuserRate(t)), 64, 19, TEXT, false);
            int y = wrapped(graphics, font, Component.translatable("jei.alchyforbidden.infuser_from", com.lealex.alchyforbidden.Config.infuserReach()), 40, 2, 0xFF707070);
            if (t >= 3) {
                wrapped(graphics, font, Component.translatable("jei.alchyforbidden.infuser_bond", com.lealex.alchyforbidden.Config.infuserBodyRate()), y + 2, 2, 0xFF5A1E8C);
            }
        }
    }
}
