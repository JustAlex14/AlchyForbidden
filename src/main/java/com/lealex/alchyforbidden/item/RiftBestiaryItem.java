package com.lealex.alchyforbidden.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * The Rift Bestiary (an altar rite): every creature of the rift written down (its look, lure, cost, strength, traits and
 * moves) and the familiars the reader wears. Opens its screen (client/BestiaryScreen) on use.
 */
public class RiftBestiaryItem extends Item {
    /** Set by the client: opens the screen. */
    public static Runnable open = () -> {};

    public RiftBestiaryItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) open.run();
        return InteractionResult.SUCCESS;
    }
}
