package com.lealex.alchyforbidden.item;

import com.lealex.alchyforbidden.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

/**
 * The Rift Knot: right-click a spawner (vanilla's, or another mod's that keeps the game's spawner block entity, as
 * Apotheosis's do) to bind it, again to set it free. A bound spawner's mobs fold into one condensed mob of their kind
 * (essence/MobStacks). The knot is not used up.
 */
public class RiftKnotItem extends Item {
    public RiftKnotItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity spawner = level.getBlockEntity(pos);
        if (spawner == null || !(spawner instanceof SpawnerBlockEntity || level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.SPAWNER))) {
            return InteractionResult.PASS; // (a spawner block of the game, whatever block entity a mod gives it)
        }
        if (level instanceof ServerLevel server) {
            boolean bind = !spawner.getData(ModRegistries.KNOTTED);
            spawner.setData(ModRegistries.KNOTTED, bind);
            spawner.setChanged();
            server.sendParticles(bind ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    30, 0.4, 0.4, 0.4, 0.1);
            server.playSound(null, pos, bind ? SoundEvents.AMETHYST_BLOCK_RESONATE : SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 1.0f, bind ? 0.6f : 1.2f);
            if (context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player) {
                player.sendSystemMessage(Component.translatable(bind ? "item.alchyforbidden.rift_knot.bound" : "item.alchyforbidden.rift_knot.freed"), true);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
