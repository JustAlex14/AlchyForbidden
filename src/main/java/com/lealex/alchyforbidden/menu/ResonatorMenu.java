package com.lealex.alchyforbidden.menu;

import com.lealex.alchyforbidden.Config;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The Echo Resonator's screen: its ender pearls and eyes of ender (a slot each over the resonator's counts), an
 * offering slot for the next
 * tier's cost, the player's inventory. The screen reads the rest (the round, the Corruption waiting, the altar it
 * feeds) from the resonator's block entity, which the server keeps in step.
 */
public class ResonatorMenu extends AbstractContainerMenu implements CoreMenu {
    public static final int WIDTH = 176, HEIGHT = 212, INVENTORY_Y = 128;
    public static final int PEARLS = 0, EYES = 1, OFFER = 2, FIRST_INVENTORY = 3;
    public static final int PEARL_X = 17, PEARL_Y = 34, EYE_X = 17, EYE_Y = 58, OFFER_X = 8, OFFER_Y = 86;

    private final @Nullable ResonatorBlockEntity resonator;
    private final @Nullable BlockPos pos;
    private final boolean server;
    private final SimpleContainer offering = new SimpleContainer(1);

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
        addSlot(stockSlot(false, PEARL_X, PEARL_Y));
        addSlot(stockSlot(true, EYE_X, EYE_Y));
        addSlot(new Slot(offering, 0, OFFER_X, OFFER_Y) {
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

    private Slot stockSlot(boolean eye, int x, int y) {
        Item item = eye ? Items.ENDER_EYE : Items.ENDER_PEARL;
        return new Slot(new Stock(eye), 0, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(item);
            }

            @Override
            public int getMaxStackSize() {
                return Config.resonatorMaxPearls();
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return Math.min(stack.getMaxStackSize(), Config.resonatorMaxPearls());
            }
        };
    }

    public @Nullable ResonatorBlockEntity resonator() {
        return resonator;
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

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < FIRST_INVENTORY) {
            if (!moveItemStackTo(stack, FIRST_INVENTORY, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.is(Items.ENDER_PEARL)) {
            if (!moveItemStackTo(stack, PEARLS, PEARLS + 1, false)) return ItemStack.EMPTY;
        } else if (stack.is(Items.ENDER_EYE)) {
            if (!moveItemStackTo(stack, EYES, EYES + 1, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, OFFER, OFFER + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return ItemStack.matches(before, stack) ? ItemStack.EMPTY : before;
    }

    /**
     * The resonator's pearls (or its eyes) as one slot (only the server changes the count; the client sees it through
     * the sync).
     */
    private class Stock implements Container {
        private final boolean eye;
        private final Item item;

        Stock(boolean eye) {
            this.eye = eye;
            this.item = eye ? Items.ENDER_EYE : Items.ENDER_PEARL;
        }

        private int count() {
            return resonator == null ? 0 : eye ? resonator.eyes() : resonator.pearls();
        }

        private void set(int count) {
            if (eye) resonator.setEyes(count);
            else resonator.setPearls(count);
        }

        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return count() <= 0;
        }

        @Override
        public ItemStack getItem(int slot) {
            return count() <= 0 ? ItemStack.EMPTY : new ItemStack(item, count());
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            if (!server || resonator == null || count() <= 0) return ItemStack.EMPTY;
            int taken = Math.min(amount, count());
            set(count() - taken);
            return new ItemStack(item, taken);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, Integer.MAX_VALUE);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (!server || resonator == null) return;
            set(stack.is(item) ? stack.getCount() : 0);
        }

        @Override
        public int getMaxStackSize() {
            return Config.resonatorMaxPearls();
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
