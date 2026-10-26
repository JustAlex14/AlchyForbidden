package com.lealex.alchyforbidden.menu;

import com.lealex.alchyforbidden.block.entity.InscribingBierBlockEntity;
import com.lealex.alchyforbidden.essence.Inscription;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.lealex.alchyx.menu.CoreMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Inscribing Bier's screen, two tabs. BODY: the player's body and its parts, what can be carved into each (drawn
 * by the screen; no slots: the items come from the inventory). GROWTH: the next tier's cost and an offering slot.
 * Buttons: TAB + tab, INSCRIBE + inscription, BURN + inscription.
 */
public class InscribingMenu extends AbstractContainerMenu implements CoreMenu {
    public static final int WIDTH = 236, HEIGHT = 250, INVENTORY_X = 38, INVENTORY_Y = 168;
    public static final int BODY = 0, GROWTH = 1, SLOTS = 2, TAB = 0, EXPAND = 5, INSCRIBE = 10, BURN = 110;
    public static final int OFFER = 0, FIRST_INVENTORY = 1;
    public static final int OFFER_X = 10, OFFER_Y = 96;

    private final @Nullable InscribingBierBlockEntity bier;
    private final @Nullable BlockPos pos;
    private final boolean server;
    private final SimpleContainer offering = new SimpleContainer(1);
    private int tab = BODY;

    /** Client side. */
    public InscribingMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos());
    }

    private InscribingMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, inventory.player.level().getBlockEntity(pos) instanceof InscribingBierBlockEntity found ? found : null, pos, false);
    }

    /** Server side. */
    public InscribingMenu(int containerId, Inventory inventory, InscribingBierBlockEntity bier) {
        this(containerId, inventory, bier, bier.getBlockPos(), true);
    }

    private InscribingMenu(int containerId, Inventory inventory, @Nullable InscribingBierBlockEntity bier, @Nullable BlockPos pos, boolean server) {
        super(ModRegistries.INSCRIBING_MENU.get(), containerId);
        this.bier = bier;
        this.pos = pos;
        this.server = server;
        Player player = inventory.player;
        addSlot(new TabSlot(offering, 0, OFFER_X, OFFER_Y, GROWTH) {
            @Override
            public boolean isActive() { // gone once the multiblock is at its last tier
                return super.isActive() && InscribingMenu.this.bier != null && InscribingMenu.this.bier.tier() < InscribingMenu.this.bier.maxTier();
            }

            @Override
            public void setChanged() {
                super.setChanged();
                ItemStack stack = getItem();
                if (InscribingMenu.this.server && InscribingMenu.this.bier != null && !stack.isEmpty() && InscribingMenu.this.bier.wants(stack)) {
                    InscribingMenu.this.bier.offer(player, stack); // takes what it still needs; the rest stays here
                }
            }
        });
        addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
    }

    public @Nullable InscribingBierBlockEntity bier() {
        return bier;
    }

    public int tab() {
        return tab;
    }

    public void setTab(int tab) {
        this.tab = tab;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == TAB + BODY || buttonId == TAB + GROWTH || buttonId == TAB + SLOTS) {
            tab = buttonId - TAB;
            return true;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || bier == null) return false;
        if (buttonId == EXPAND) {
            bier.expand(serverPlayer);
            return true;
        }
        Inscription[] all = Inscription.values();
        if (buttonId >= INSCRIBE && buttonId < INSCRIBE + all.length) {
            bier.inscribe(serverPlayer, all[buttonId - INSCRIBE]);
            return true;
        }
        if (buttonId >= BURN && buttonId < BURN + all.length) {
            bier.burnAway(serverPlayer, all[buttonId - BURN]);
            return true;
        }
        return false;
    }

    @Override
    public @Nullable BlockPos getCorePos() {
        return pos;
    }

    @Override
    public boolean stillValid(Player player) {
        return !server || (bier != null && !bier.isRemoved() && bier.withinReach(player));
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, offering);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < FIRST_INVENTORY) {
            if (!moveItemStackTo(stack, FIRST_INVENTORY, slots.size(), true)) return ItemStack.EMPTY;
        } else if (tab != GROWTH || !moveItemStackTo(stack, OFFER, OFFER + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return ItemStack.matches(before, stack) ? ItemStack.EMPTY : before;
    }

    /** A slot that is only there on its tab. */
    private class TabSlot extends Slot {
        private final int onTab;

        TabSlot(Container container, int slot, int x, int y, int onTab) {
            super(container, slot, x, y);
            this.onTab = onTab;
        }

        @Override
        public boolean isActive() {
            return tab == onTab;
        }
    }
}
