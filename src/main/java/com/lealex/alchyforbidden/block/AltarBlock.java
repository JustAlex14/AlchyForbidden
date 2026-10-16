package com.lealex.alchyforbidden.block;

import com.lealex.alchyforbidden.block.entity.AltarBlockEntity;
import com.lealex.alchyx.block.MultiblockCoreBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The altar's core: the block the mage carved by hand. The player builds the first tier around it (a deepslate floor
 * and four candles); every later tier is grown by the rift itself once the altar is awake and has been given what it
 * asks for. Right-click it with the eye to set the eye over its center (that wakes it), with what it wants to give
 * it, with an empty hand to hear what it wants; sneak + right-click with an empty hand takes the eye back.
 */
public class AltarBlock extends MultiblockCoreBlock {
    /** Rising with the platform: drawn by nothing here (client/AltarRifts slides it up into its new place). */
    public static final BooleanProperty RISING = BooleanProperty.create("rising");

    public AltarBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RISING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RISING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AltarBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof AltarBlockEntity altar) {
            if (altar.takesEye(stack)) {
                if (!level.isClientSide()) altar.setEye(player, stack);
                return InteractionResult.SUCCESS;
            }
            if (altar.wants(stack)) {
                if (!level.isClientSide()) altar.offer(player, stack);
                return InteractionResult.SUCCESS;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown() && level.getBlockEntity(pos) instanceof AltarBlockEntity altar && altar.hasEye()) {
            if (!level.isClientSide()) altar.takeEye(player);
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }
}
