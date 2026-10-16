package com.lealex.alchyforbidden.menu;

import com.lealex.alchyforbidden.block.entity.GrowingCoreBlockEntity;
import com.lealex.alchyforbidden.block.entity.KillingPitBlockEntity;
import com.lealex.alchyforbidden.block.entity.LamentWellBlockEntity;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.lealex.alchyx.menu.CoreMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The screen of the Killing Pit and the Lament Well (the essence sources), two tabs like the resonator's. MAIN: the
 * well's offering slot (an offering of loss put in is taken at once); the pit has none (it keeps nothing).
 * GROWTH: an offering slot for the next tier's cost. Then the player's inventory. Buttons: TAB + tab.
 */
public class SourceMenu extends AbstractContainerMenu implements CoreMenu {
    public static final int WIDTH = 176, HEIGHT = 212, INVENTORY_Y = 128;
    public static final int MAIN = 0, GROWTH = 1, TAB = 0;
    /** The pit's healing button (tier 3): off, or on again. */
    public static final int HEAL = 5, VOID = 6, TAKE_XP = 7;
    public static final int DROPS_X = 112, DROPS_Y = 24, LOSS_X = 130, LOSS_Y = 46, OFFER_X = 10, OFFER_Y = 96;

    private final @Nullable GrowingCoreBlockEntity core;
    private final @Nullable BlockPos pos;
    private final boolean server;
    private final SimpleContainer offering = new SimpleContainer(1);
    private final SimpleContainer loss = new SimpleContainer(1);
    private final int offerSlot, firstInventory;
    private int tab = MAIN;

    /** Client side. */
    public SourceMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos());
    }

    private SourceMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, inventory.player.level().getBlockEntity(pos) instanceof GrowingCoreBlockEntity found ? found : null, pos, false);
    }

    /** Server side. */
    public SourceMenu(int containerId, Inventory inventory, GrowingCoreBlockEntity core) {
        this(containerId, inventory, core, core.getBlockPos(), true);
    }

    private SourceMenu(int containerId, Inventory inventory, @Nullable GrowingCoreBlockEntity core, @Nullable BlockPos pos, boolean server) {
        super(ModRegistries.SOURCE_MENU.get(), containerId);
        this.core = core;
        this.pos = pos;
        this.server = server;
        Player player = inventory.player;
        if (core instanceof KillingPitBlockEntity pit) { // the pit's store, three by three, take only
            for (int i = 0; i < 9; i++) {
                addSlot(new TabSlot(pit.drops(), i, DROPS_X + (i % 3) * 18, DROPS_Y + (i / 3) * 18, MAIN) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            }
        } else { // the well's offering of loss
            addSlot(new TabSlot(loss, 0, LOSS_X, LOSS_Y, MAIN) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return SourceMenu.this.core instanceof LamentWellBlockEntity well && well.offering(stack) > 0;
                }

                @Override
                public void setChanged() {
                    super.setChanged();
                    ItemStack stack = getItem();
                    if (SourceMenu.this.server && SourceMenu.this.core instanceof LamentWellBlockEntity well && !stack.isEmpty()) {
                        well.offerLoss(player, stack); // taken, all of it
                        if (stack.isEmpty()) set(ItemStack.EMPTY);
                    }
                }
            });
        }
        offerSlot = slots.size();
        addSlot(new TabSlot(offering, 0, OFFER_X, OFFER_Y, GROWTH) {
            @Override
            public void setChanged() {
                super.setChanged();
                ItemStack stack = getItem();
                if (SourceMenu.this.server && SourceMenu.this.core != null && !stack.isEmpty() && SourceMenu.this.core.wants(stack)) {
                    SourceMenu.this.core.offer(player, stack); // takes what it still needs; the rest stays here
                }
            }
        });
        firstInventory = slots.size();
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
    }

    public @Nullable GrowingCoreBlockEntity core() {
        return core;
    }

    public int firstInventory() {
        return firstInventory;
    }

    public int offerSlot() {
        return offerSlot;
    }

    public int tab() {
        return tab;
    }

    public void setTab(int tab) {
        this.tab = tab;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == TAB + MAIN || buttonId == TAB + GROWTH) {
            tab = buttonId - TAB;
            return true;
        }
        if (buttonId == HEAL && server && core instanceof KillingPitBlockEntity pit) {
            pit.toggleHealing();
            return true;
        }
        if (buttonId == VOID && server && core instanceof KillingPitBlockEntity pit) {
            pit.toggleVoid();
            return true;
        }
        if (buttonId == TAKE_XP && server && core instanceof KillingPitBlockEntity pit) {
            pit.giveExperience(player);
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
        return !server || (core != null && !core.isRemoved() && core.withinReach(player));
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, offering);
        clearContainer(player, loss);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < firstInventory) {
            if (!moveItemStackTo(stack, firstInventory, slots.size(), true)) return ItemStack.EMPTY;
        } else if (tab == GROWTH) {
            if (!moveItemStackTo(stack, offerSlot, offerSlot + 1, false)) return ItemStack.EMPTY;
        } else if (core instanceof LamentWellBlockEntity well && well.offering(stack) > 0) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else {
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
