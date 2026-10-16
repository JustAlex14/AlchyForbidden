package com.lealex.alchyforbidden.block;

import com.lealex.alchyforbidden.block.entity.ResonatorBlockEntity;
import com.lealex.alchyx.block.MultiblockCoreBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Echo Resonator's core: the mage's carved block with the rift inside it that throws the pearls. Right-click it
 * with ender pearls (or eyes of ender, which break less often) to load it, with what its next tier asks for to give it, with an empty hand to hear how it does;
 * sneak + right-click with an empty hand takes the pearls back.
 */
public class ResonatorBlock extends MultiblockCoreBlock {
    public ResonatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResonatorBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ResonatorBlockEntity resonator) {
            if (stack.is(Items.ENDER_PEARL) || stack.is(Items.ENDER_EYE)) {
                if (!level.isClientSide()) resonator.feed(player, stack);
                return InteractionResult.SUCCESS;
            }
            if (resonator.wants(stack)) {
                if (!level.isClientSide()) resonator.offer(player, stack);
                return InteractionResult.SUCCESS;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof ResonatorBlockEntity resonator && resonator.pearls() + resonator.eyes() > 0) {
            if (!level.isClientSide()) resonator.takePearls(player);
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }
}
