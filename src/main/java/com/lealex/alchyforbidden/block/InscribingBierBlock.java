package com.lealex.alchyforbidden.block;

import com.lealex.alchyforbidden.block.entity.InscribingBierBlockEntity;
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
 * The Inscribing Bier's core: the middle of its sacrificial slab (made by an altar rite). Right-click it with what its
 * next tier asks for to give it; otherwise the player walks to the slab and lies down on it, then its screen opens
 * (the body and what can be carved into it; essence/BierRest), from the core or any block of the structure. Sneaking on
 * the core opens the screen at once.
 */
public class InscribingBierBlock extends MultiblockCoreBlock {
    public InscribingBierBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InscribingBierBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof InscribingBierBlockEntity core && core.wants(stack)) {
            if (!level.isClientSide()) core.offer(player, stack);
            return InteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
