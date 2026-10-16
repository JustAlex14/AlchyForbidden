package com.lealex.alchyforbidden.block;

import com.lealex.alchyforbidden.block.entity.LamentWellBlockEntity;
import com.lealex.alchyx.block.MultiblockCoreBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Lament Well's core, at the bottom of its shaft (made by an altar rite). Right-click it with what its next
 * tier asks for to give it, with an offering of loss (from the second tier) to give it that, with an empty hand to
 * hear how it does.
 */
public class LamentWellBlock extends MultiblockCoreBlock {
    public LamentWellBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LamentWellBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof LamentWellBlockEntity core) {
            if (core.offering(stack) > 0) {
                if (!level.isClientSide()) core.offerLoss(player, stack);
                return InteractionResult.SUCCESS;
            }
            if (core.wants(stack)) {
                if (!level.isClientSide()) core.offer(player, stack);
                return InteractionResult.SUCCESS;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
