package com.lealex.alchyforbidden.block;

import com.lealex.alchyforbidden.block.entity.EssenceInfuserBlockEntity;
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
 * The Essence Infuser's core, in the middle of its floor (made by an altar rite). Right-click it with what its next
 * tier asks for to give it; otherwise it opens its screen (pick the essence, lay a vessel in, bind it to you).
 */
public class EssenceInfuserBlock extends MultiblockCoreBlock {
    public EssenceInfuserBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EssenceInfuserBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof EssenceInfuserBlockEntity core && core.wants(stack)) {
            if (!level.isClientSide()) core.offer(player, stack);
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
