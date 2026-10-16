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
        });
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        // Before AlchyX's own handler (which opens the machine, or lets a block be placed): a click on a stand lays
        // the held item on it or takes it back, whatever the item is
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, AlchyForbidden::useStand);
        // Where Wrath, Sorrow and Sculk Corruption come from
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.essence.EssenceEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.essence.EssenceEvents::onPlayerTick);
        // Condensed mobs: what a knotted spawner makes folds into one mob of its kind, which counts as all of them
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.essence.MobStacks::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.essence.MobStacks::onServerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, com.lealex.alchyforbidden.essence.MobStacks::onDrops);
        NeoForge.EVENT_BUS.addListener(com.lealex.alchyforbidden.essence.MobStacks::onExperience);
        // Deaths in a Killing Pit give Wrath (their drops go into it); deaths near a Lament Well leave a weeping ghost
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) -> {
            net.minecraft.world.entity.LivingEntity dead = event.getEntity();
            if (dead.level().isClientSide() || dead instanceof net.minecraft.world.entity.player.Player) return;
            for (var pit : com.lealex.alchyforbidden.block.entity.KillingPitBlockEntity.loaded(dead.level())) {
                if (pit.holds(dead)) {
                    pit.killed(dead);
                    break;
                }
            }
            for (var well : com.lealex.alchyforbidden.block.entity.LamentWellBlockEntity.loaded(dead.level())) {
                for (int i = com.lealex.alchyforbidden.essence.MobStacks.count(dead); i > 0; i--) well.mourn(dead); // a ghost for each mob it held
            }
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingDropsEvent event) -> {
            net.minecraft.world.entity.LivingEntity dead = event.getEntity();
            if (dead.level().isClientSide()) return;
            for (var pit : com.lealex.alchyforbidden.block.entity.KillingPitBlockEntity.loaded(dead.level())) {
                if (pit.holds(dead)) {
                    pit.collectDrops(event.getDrops());
                    break;
                }
            }
        });
        // A warden killed beside an Echo Resonator opens its last tier (its body goes into the rift)
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) -> {
            if (!(event.getEntity() instanceof net.minecraft.world.entity.monster.warden.Warden warden)) return;
            for (com.lealex.alchyforbidden.block.entity.ResonatorBlockEntity resonator
                    : com.lealex.alchyforbidden.block.entity.ResonatorBlockEntity.loaded(warden.level())) {
                if (resonator.getBlockPos().distSqr(warden.blockPosition()) <= 16 * 16) resonator.wardenDied(warden);
            }
        });
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
