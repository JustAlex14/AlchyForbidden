package com.lealex.alchyforbidden;

import com.lealex.alchyforbidden.client.AltarVision;
import com.lealex.alchyforbidden.client.EyeGaze;
import com.lealex.alchyforbidden.client.RiftMote;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only registrations (never loaded on a dedicated server). */
@Mod(value = AlchyForbidden.MODID, dist = Dist.CLIENT)
public class AlchyForbiddenClient {
    public AlchyForbiddenClient(IEventBus modEventBus, ModContainer container) {
        // The grimoire's paper (tools/book_textures.py): old vellum, then soot, the sculk's veins, the rift's violet, and
        // in the last chapter only the violet frost; one sheet a chapter, a mark on four pages in ten
        java.util.List<net.minecraft.resources.Identifier> paper = new java.util.ArrayList<>();
        for (int sheet = 0; sheet < 10; sheet++) {
            paper.add(net.minecraft.resources.Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/gui/grimoire" + (sheet == 0 ? "" : "_" + sheet) + ".png"));
        }
        com.lealex.alchyx.client.BookPapers.register(net.minecraft.resources.Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "guide"),
                java.util.List.of("fall", "altar", "griefs", "wells", "weeping", "carvings", "rings", "maw", "trials", "door"), paper, null, null,
                new com.lealex.alchyx.client.BookPapers.Marks(java.util.stream.IntStream.range(0, 6).mapToObj(mark ->
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(AlchyForbidden.MODID, "textures/gui/grimoire_marks_" + mark + ".png")).toList(), 0.6F));
        // The rift's motes and the sculk's flecks
        modEventBus.addListener(RiftMote::registerProviders);
        // The eye over the altar: a figure the animation files name ("figure": "alchyforbidden:eye")
        modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) ->
                event.enqueueWork(() -> {
                    com.lealex.alchyforbidden.client.EyeFigure.register();
                    com.lealex.alchyforbidden.client.EchoPearlFigure.register(); // the resonator's pearl on its round
                    com.lealex.alchyforbidden.client.DeadWardenFigure.register(); // the dead warden in its rift
                }));
        // The altar's screen
        modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) ->
                {
                    event.register(com.lealex.alchyforbidden.registry.ModRegistries.ALTAR_MENU.get(), com.lealex.alchyforbidden.client.AltarScreen::new);
                    event.register(com.lealex.alchyforbidden.registry.ModRegistries.RESONATOR_MENU.get(), com.lealex.alchyforbidden.client.ResonatorScreen::new);
                    event.register(com.lealex.alchyforbidden.registry.ModRegistries.SOURCE_MENU.get(), com.lealex.alchyforbidden.client.SourceScreen::new);
                    event.register(com.lealex.alchyforbidden.registry.ModRegistries.INSCRIBING_MENU.get(), com.lealex.alchyforbidden.client.InscribingScreen::new);
                });
        // The held eye looks where it wants the player to go
        NeoForge.EVENT_BUS.addListener(EyeGaze::onClientTick);
        // ...and shows what the altar near it will grow next
        NeoForge.EVENT_BUS.addListener(AltarVision::onClientTick);
        NeoForge.EVENT_BUS.addListener(AltarVision::onExtract);
        NeoForge.EVENT_BUS.addListener(AltarVision::onSubmit);
        // The rifts the altar's parts come out of
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.AltarRifts::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.AltarRifts::onExtract);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.AltarRifts::onSubmit);
        // The rift in the deepest block of each Lament Well
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.WellRifts::onExtract);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.ArenaWalls::onExtract);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.ArenaWalls::onSubmit);
        // The attacks of the rift's creatures drawn as shapes
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.AttackFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.AttackFx::onExtract);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.AttackFx::onSubmit);
        // The void's pull seen from outside
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.VoidPullCamera::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.VoidPullCamera::onRenderFrame);
        com.lealex.alchyx.client.scene.BodyPoses.register(com.lealex.alchyforbidden.client.VoidPullCamera::pose);
        // A great one's entrance
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.BossIntro::onClientTick);
        // The carvings that move the body, and the essence reserve's bars
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.InscriptionClient::onPlayerTick);
        // The Inscribing Bier: the walk is AlchyX's scene (SceneWalk), the lying down drawn through AlchyX BodyPoses
        com.lealex.alchyx.client.scene.SceneWalk.onArrive(com.lealex.alchyforbidden.essence.BierRest.SCENE, com.lealex.alchyforbidden.client.BierRestClient::arrived);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.BierRestClient::onPlayerTick);
        com.lealex.alchyx.client.scene.BodyPoses.register(com.lealex.alchyforbidden.client.BierLying::pose);
        com.lealex.alchyx.client.scene.BodyPoses.register(com.lealex.alchyforbidden.client.ChantPose::pose);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.ChantPose::onPlayerTick);
        // The parts the rift grows fly out of it with rift motes and land in sculk flecks (AlchyX GrowthParts)
        com.lealex.alchyx.client.growth.GrowthParts.registerStyle(com.lealex.alchyforbidden.block.entity.GrowingCoreBlockEntity.RIFT_STYLE,
                new com.lealex.alchyx.client.growth.GrowthParts.Style(() -> com.lealex.alchyforbidden.registry.ModRegistries.RIFT_MOTE.get(), (level, pos) -> {
                    net.minecraft.util.RandomSource random = level.getRandom();
                    for (int i = 0; i < 6; i++) {
                        level.addParticle(com.lealex.alchyforbidden.registry.ModRegistries.SCULK_FLECK.get(), pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8,
                                pos.getY() + 0.9, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8, 0, 0.04, 0);
                    }
                }));
        // What the carvings leave on the body (AlchyX BodyOverlays)
        com.lealex.alchyx.client.scene.BodyOverlays.register(com.lealex.alchyforbidden.client.BodyMarks::collect);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.InscriptionVisuals::onClientTick);
        modEventBus.addListener(com.lealex.alchyforbidden.client.InscriptionClient::onRegisterLayers);
        // spells: the keys (Controls, AlchyForbidden), the slots by the hotbar
        modEventBus.addListener(com.lealex.alchyforbidden.client.SpellKeys::onRegisterKeys);
        // the creatures of the rift
        com.lealex.alchyforbidden.item.RiftBestiaryItem.open = com.lealex.alchyforbidden.client.familiar.BestiaryScreen::open;
        modEventBus.addListener(com.lealex.alchyforbidden.client.familiar.FamiliarRenderers::onLayers);
        modEventBus.addListener(com.lealex.alchyforbidden.client.familiar.FamiliarRenderers::onRenderers);
        modEventBus.addListener(com.lealex.alchyforbidden.client.ItemTints::onRegister);
        modEventBus.addListener(com.lealex.alchyforbidden.client.RingTooltipClient::onRegister);
        modEventBus.addListener(com.lealex.alchyforbidden.client.SpellHud::onRegisterLayers);
        // The eye's speaking box
        modEventBus.addListener(com.lealex.alchyforbidden.client.EyeSpeech::onRegisterLayers);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.EyeSpeech::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.SpellKeys::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.SpellFx::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.SpellFx::onExtract);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.SpellFx::onSubmit);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.ImbueRituals::onExtract);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.ImbueRituals::onSubmit);
        // The carved wings, a model with animated textures on the player's back
        modEventBus.addListener(com.lealex.alchyforbidden.client.WingLayer::onAddLayers);
        modEventBus.addListener(com.lealex.alchyforbidden.client.BierKnifeLayer::onAddLayers);
        modEventBus.addListener(com.lealex.alchyforbidden.client.WingLayer::onRegisterModifiers);

        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.FountainWater::onExtract);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.KnotVisuals::onExtract);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.KnotVisuals::onSubmit);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.WellRifts::onSubmit);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.FountainWater::onSubmit);
        // The circle a rite draws on the altar's floor
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.RiteCircles::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.RelayLinks::onClientTick);
        // a core broken after it grew says the tier it keeps
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event) -> {
            net.minecraft.world.item.component.CustomData state = event.getItemStack().get(com.lealex.alchyx.registry.AlchyXRegistries.KEPT_CORE.get());
            int kept = state == null ? 0 : state.copyTag().getIntOr("tier", 0);
            if (kept > 1) {
                event.getToolTip().add(1, net.minecraft.network.chat.Component.translatable("item.alchyforbidden.core_tier", kept)
                        .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
                event.getToolTip().add(2, net.minecraft.network.chat.Component.translatable("item.alchyforbidden.core_tier.hint")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        });
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.RiteCircles::onExtract);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.RiteCircles::onSubmit);
        // A warden killed beside a resonator, taken up into the rift
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.WardenTaken::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.WardenTaken::onExtract);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.WardenTaken::onSubmit);
    }
}
