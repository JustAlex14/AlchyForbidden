package com.lealex.alchyforbidden;

import com.lealex.alchyforbidden.block.entity.AltarBlockEntity;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.lealex.alchyx.block.ChamberShellBlock;
import com.lealex.alchyx.multiblock.CoreTracker;
import com.lealex.alchyx.multiblock.ShellParts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/** AlchyForbidden: the mage left for dead in an ancient city, the eye in the rift, and the altar it grows. */
@Mod(AlchyForbidden.MODID)
public class AlchyForbidden {
    public static final String MODID = "alchyforbidden";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AlchyForbidden(IEventBus modEventBus, ModContainer modContainer) {
        ModRegistries.register(modEventBus);
        // Hoppers and pipes on the cores themselves (the shells already give the core's, through AlchyX)
        modEventBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, ModRegistries.ALTAR_BE.get(),
                    (altar, side) -> altar.getItemHandler());
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, ModRegistries.RESONATOR_BE.get(),
                    (resonator, side) -> resonator.getItemHandler());
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, ModRegistries.KILLING_PIT_BE.get(),
                    (pit, side) -> pit.getItemHandler());
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, ModRegistries.WEEPING_FOUNTAIN_BE.get(),
                    (fountain, side) -> fountain.getItemHandler());
            event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, ModRegistries.ESSENCE_INFUSER_BE.get(),
                    (infuser, side) -> infuser.getItemHandler());
        });
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        com.lealex.alchyx.config.CommonConfigSync.share(modContainer); // players on a server see its costs, tiers and cooldowns
        // Before AlchyX's own handler (which opens the machine, or lets a block be placed): a click on a stand lays
        // the held item on it or takes it back, whatever the item is
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.familiar.ShowcaseBossCommand::register); // trailer entrances
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, AlchyForbidden::useStand);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, AlchyForbidden::kindle);
        // Where Wrath, Sorrow and Sculk Corruption come from
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.essence.EssenceEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.essence.EssenceEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.essence.Inscriptions::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.essence.Inscriptions::onFall);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.essence.Inscriptions::onGameEvent);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.essence.Inscriptions::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.essence.Inscriptions::onEffectApplicable);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOW, com.lealex.alchyforbidden.essence.Inscriptions::onDeath);
        // Condensed mobs: what a knotted spawner makes folds into one mob of its kind, which counts as all of them
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.essence.MobStacks::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.essence.MobStacks::onServerTick);
        // Echo Obelisks: the tuner's clicks come before any block's own; the relayed streams fly hop by hop
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, com.lealex.alchyforbidden.item.AttuningForkItem::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.essence.Relays::onServerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, com.lealex.alchyforbidden.essence.MobStacks::onDrops);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.essence.MobStacks::onExperience);
        // Deaths in a Killing Pit, near a Lament Well or beside an Echo Resonator: AlchyX DeathWatch tells each machine
        // (deathReach / onDeathNearby / takeDropsNearby on their block entities)
    }

    /** A component fed to the multiblock it kindles (its core or any of its parts): it works harder for a while. */
    private static void kindle(PlayerInteractEvent.RightClickBlock event) {
        var stack = event.getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof com.lealex.alchyforbidden.item.ComponentItem)) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        var core = level.getBlockEntity(pos) instanceof com.lealex.alchyforbidden.block.entity.GrowingCoreBlockEntity own ? own
                : CoreTracker.formedCoreAt(level, pos) instanceof com.lealex.alchyforbidden.block.entity.GrowingCoreBlockEntity part ? part : null;
        if (core == null || !core.tryKindle(event.getEntity(), stack)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void useStand(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!ShellParts.isShell(state)) return;
        BlockState look = ChamberShellBlock.disguiseAt(level, pos);
        if (look == null || !look.is(ModRegistries.OFFERING_STAND.get())) return;
        // only an altar's stands hold things (the Lament Well's are for show: a click opens the well as usual)
        if (!(CoreTracker.formedCoreAt(level, pos) instanceof AltarBlockEntity altar)) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide()) return;
        altar.useStand(event.getEntity(), event.getHand(), altar.standAt(pos));
    }
}
