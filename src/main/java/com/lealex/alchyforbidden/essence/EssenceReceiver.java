package com.lealex.alchyforbidden.essence;

import net.minecraft.core.BlockPos;

/**
 * What essence can be sent to along a link: an altar (into its reserve) or a Weeping Fountain (into its own store,
 * which it drinks from before any altar). essence/Relays routes end at one.
 */
public interface EssenceReceiver {
    /** Takes what it has room for; returns how much it took. */
    int addEssence(Essence essence, int amount);

    /** How much more of the essence it can take now. */
    int room(Essence essence);

    /** The block a guided stream lands in (an altar's eye, a fountain's spout). */
    BlockPos intakePos();

    /** Its core's place (where links to it point). */
    BlockPos receiverPos();
}
