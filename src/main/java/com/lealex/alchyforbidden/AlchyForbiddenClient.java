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
        // A warden killed beside a resonator, taken up into the rift
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.WardenTaken::onClientTick);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.WardenTaken::onExtract);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.client.WardenTaken::onSubmit);
    }
}
