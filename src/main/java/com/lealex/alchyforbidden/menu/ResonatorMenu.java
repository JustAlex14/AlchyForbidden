package com.lealex.alchyforbidden.menu;

import com.lealex.alchyforbidden.block.entity.ResonatorBlockEntity;
import com.lealex.alchyforbidden.registry.ModRegistries;
import com.lealex.alchyx.menu.CoreMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The Echo Resonator's screen, two tabs like the altar's. ROUND: its stock of ender pearls and eyes of ender (one slot
 * for both, a view over the resonator's counts), the round and the Corruption waiting (drawn by the screen). GROWTH:
 * the next tier's cost and an offering slot for it. Then the player's inventory.
 * <p>
 * The stock slot is handled here, not by the game's slot logic: it holds two kinds at once (it shows the one thrown
 * next, the eyes), so a click with pearls or eyes in hand puts them in, an empty hand takes out what it shows, a
 * shift-click on it sends that to the inventory, and nothing is ever swapped.
 * Buttons: TAB + tab.
 */
public class ResonatorMenu extends AbstractContainerMenu implements CoreMenu {
    public static final int WIDTH = 176, HEIGHT = 212, INVENTORY_Y = 128;
    public static final int ROUND = 0, GROWTH = 1, TAB = 0;
    public static final int STOCK = 0, OFFER = 1, FIRST_INVENTORY = 2;
    public static final int STOCK_X = 17, STOCK_Y = 34, OFFER_X = 10, OFFER_Y = 96;

    private final @Nullable ResonatorBlockEntity resonator;
    private final @Nullable BlockPos pos;
    private final boolean server;
    private final SimpleContainer offering = new SimpleContainer(1);
    private int tab = ROUND;

    /** Client side. */
    public ResonatorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos());
    }

    private ResonatorMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, inventory.player.level().getBlockEntity(pos) instanceof ResonatorBlockEntity found ? found : null, pos, false);
    }

    /** Server side. */
    public ResonatorMenu(int containerId, Inventory inventory, ResonatorBlockEntity resonator) {
        this(containerId, inventory, resonator, resonator.getBlockPos(), true);
    }

    private ResonatorMenu(int containerId, Inventory inventory, @Nullable ResonatorBlockEntity resonator, @Nullable BlockPos pos, boolean server) {
        super(ModRegistries.RESONATOR_MENU.get(), containerId);
        this.resonator = resonator;
        this.pos = pos;
        this.server = server;
        Player player = inventory.player;
        addSlot(new TabSlot(new Stock(), 0, STOCK_X, STOCK_Y, ROUND) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false; // (clicked() puts them in)
            }

            @Override
            public boolean mayPickup(Player player) {
                return false; // (clicked() takes them out)
            }
        });
        addSlot(new TabSlot(offering, 0, OFFER_X, OFFER_Y, GROWTH) {
            @Override
            public boolean isActive() { // gone once the multiblock is at its last tier
                return super.isActive() && ResonatorMenu.this.resonator != null && ResonatorMenu.this.resonator.tier() < ResonatorMenu.this.resonator.maxTier();
            }

            @Override
            public void setChanged() {
                super.setChanged();
                ItemStack stack = getItem();
                if (ResonatorMenu.this.server && ResonatorMenu.this.resonator != null && !stack.isEmpty()
                        && ResonatorMenu.this.resonator.wants(stack)) {
                    ResonatorMenu.this.resonator.offer(player, stack); // takes what it still needs; the rest stays here
                }
            }
        });
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
    }

    public @Nullable ResonatorBlockEntity resonator() {
        return resonator;
    }

    public int tab() {
        return tab;
    }

    /** The screen switches tabs at once on its side, and tells the server. */
    public void setTab(int tab) {
        this.tab = tab;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == TAB + ROUND || buttonId == TAB + GROWTH) {
            tab = buttonId - TAB;
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
        return !server || (resonator != null && !resonator.isRemoved() && resonator.withinReach(player));
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, offering);
    }

    private static boolean thrown(ItemStack stack) {
        return stack.is(Items.ENDER_PEARL) || stack.is(Items.ENDER_EYE);
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (slotId != STOCK || tab != ROUND) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        if (resonator == null) return;
        ItemStack carried = getCarried();
        if (clickType == ContainerInput.PICKUP) {
            if (thrown(carried)) { // in they go (left: all, right: one)
                int amount = button == 1 ? 1 : carried.getCount();
                if (server) carried.shrink(resonator.add(carried.copyWithCount(amount)));
                setCarried(carried);
            } else if (carried.isEmpty() && server) { // out: what it shows (left: a stack, right: half of one)
                ItemStack out = resonator.takeShown(button == 1 ? 32 : 64);
                setCarried(out);
            }
        } else if (clickType == ContainerInput.QUICK_MOVE && server) { // to the inventory, a stack at a time
            ItemStack out = resonator.takeShown(64);
            if (!out.isEmpty()) {
                moveItemStackTo(out, FIRST_INVENTORY, slots.size(), true);
                if (!out.isEmpty()) resonator.add(out); // what did not fit goes back in
            }
        }
        // (other clicks, the number keys and drops, do nothing on it)
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index == STOCK || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < FIRST_INVENTORY) {
            if (!moveItemStackTo(stack, FIRST_INVENTORY, slots.size(), true)) return ItemStack.EMPTY;
        } else if (thrown(stack) && resonator != null) {
            if (server) stack.shrink(resonator.add(stack.copy()));
            else return ItemStack.EMPTY;
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

    /** The resonator's stock as one slot: shows what it throws next (its eyes, then its pearls), changed by clicked(). */
    private class Stock implements Container {
        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return resonator == null || resonator.pearls() + resonator.eyes() <= 0;
        }

        @Override
        public ItemStack getItem(int slot) {
            if (resonator == null) return ItemStack.EMPTY;
            if (resonator.eyes() > 0) return new ItemStack(Items.ENDER_EYE, resonator.eyes());
            return resonator.pearls() > 0 ? new ItemStack(Items.ENDER_PEARL, resonator.pearls()) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {}

        @Override
        public int getMaxStackSize() {
            return 4096;
        }

        @Override
        public void setChanged() {}

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {}
    }
}
