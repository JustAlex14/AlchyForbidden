package com.lealex.alchyforbidden.menu;

import com.lealex.alchyforbidden.block.entity.AltarBlockEntity;
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
 * The altar's screen, two tabs.
 * RITES: the eight stands (as many as the altar has: four, then eight at the third tier), what they make, the
 * result waiting to be taken, and Begin. GROWTH: the eye's place over the center, an offering slot that gives the
 * altar what the next tier asks for, and the list of what is still wanted.
 * Slots: 0-7 stands, 8 result, 9 offering, 10 eye, then the player's inventory. A slot shows only on its tab.
 * Buttons: TAB + tab, BEGIN.
 */
public class AltarMenu extends AbstractContainerMenu implements CoreMenu {
    public static final int TAB = 0, BEGIN = 10;
    /** FILL + a rite's place in the list: lay that rite's ingredients on the stands from the player's inventory. */
    public static final int FILL = 100;
    public static final int RITES = 0, GROWTH = 1;
    public static final int WIDTH = 236, HEIGHT = 216, INVENTORY_Y = 134;
    public static final int RESULT = 8, OFFER = 9, EYE = 10, POUR = 11, FIRST_INVENTORY = 12;
    /** The pour slot (Rites tab, bottom left, under the essence bars): vessels and sculk go into the reserve. */
    public static final int POUR_X = 8, POUR_Y = 102;
    /** The stands' places in the window, as they lie round the altar: the inner four, then the outer four. */
    public static final int[][] STAND_XY = {{58, 32}, {102, 32}, {102, 72}, {58, 72}, {26, 22}, {134, 22}, {134, 82}, {26, 82}};
    public static final int CENTER_X = 80, CENTER_Y = 52, RESULT_X = 150, RESULT_Y = 52;
    public static final int EYE_X = 80, EYE_Y = 30, OFFER_X = 80, OFFER_Y = 72;

    private final @Nullable AltarBlockEntity altar;
    private final @Nullable BlockPos pos;
    private final Player player;
    private final boolean server;
    private final SimpleContainer offering = new SimpleContainer(1);
    private final SimpleContainer pouring = new SimpleContainer(1);
    private int tab = RITES;

    /** Client side. */
    public AltarMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos());
    }

    private AltarMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, inventory.player.level().getBlockEntity(pos) instanceof AltarBlockEntity found ? found : null,
                pos, new SimpleContainer(AltarBlockEntity.MAX_STANDS), new SimpleContainer(1), false);
    }

    /** Server side. */
    public AltarMenu(int containerId, Inventory inventory, AltarBlockEntity altar) {
        this(containerId, inventory, altar, altar.getBlockPos(), altar.stands(), altar.output(), true);
    }

    private AltarMenu(int containerId, Inventory inventory, @Nullable AltarBlockEntity altar, @Nullable BlockPos pos,
                      Container stands, Container output, boolean server) {
        super(ModRegistries.ALTAR_MENU.get(), containerId);
        this.altar = altar;
        this.pos = pos;
        this.player = inventory.player;
        this.server = server;
        for (int i = 0; i < AltarBlockEntity.MAX_STANDS; i++) {
            int index = i;
            addSlot(new TabSlot(stands, i, STAND_XY[i][0], STAND_XY[i][1], RITES) {
                @Override
                public boolean isActive() {
                    return super.isActive() && index < standCount();
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    return isActive() && !riting();
                }
            });
        }
        addSlot(new TabSlot(output, 0, RESULT_X, RESULT_Y, RITES) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addSlot(new TabSlot(offering, 0, OFFER_X, OFFER_Y, GROWTH) {
            @Override
            public boolean isActive() { // gone once the multiblock is at its last tier
                return super.isActive() && AltarMenu.this.altar != null && AltarMenu.this.altar.tier() < AltarMenu.this.altar.maxTier();
            }

            @Override
            public void setChanged() {
                super.setChanged();
                ItemStack stack = getItem();
                if (AltarMenu.this.server && AltarMenu.this.altar != null && !stack.isEmpty() && AltarMenu.this.altar.wants(stack)) {
                    AltarMenu.this.altar.offer(player, stack); // takes what it still needs; the rest stays here
                }
            }
        });
        addSlot(new TabSlot(new EyePlace(), 0, EYE_X, EYE_Y, GROWTH) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return AltarMenu.this.altar != null && AltarMenu.this.altar.takesEye(stack);
            }

            @Override
            public boolean mayPickup(Player player) {
                return AltarMenu.this.altar != null && !AltarMenu.this.altar.isGrowing();
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addSlot(new TabSlot(pouring, 0, POUR_X, POUR_Y, RITES) {
            @Override
            public void setChanged() {
                super.setChanged();
                ItemStack stack = getItem();
                if (AltarMenu.this.server && AltarMenu.this.altar != null && !stack.isEmpty()) {
                    AltarMenu.this.altar.pour(stack); // a vessel empties (and stays here to be taken back), sculk is taken
                }
            }
        });
        addStandardInventorySlots(inventory, 38, INVENTORY_Y);
    }

    public @Nullable AltarBlockEntity altar() {
        return altar;
    }

    @Override
    public @Nullable BlockPos getCorePos() {
        return pos;
    }

    public int tab() {
        return tab;
    }

    /** The screen switches tabs at once on its side, and tells the server. */
    public void setTab(int tab) {
        this.tab = tab;
    }

    private int standCount() {
        return altar == null ? 4 : altar.standCount();
    }

    private boolean riting() {
        return altar != null && altar.isRiting();
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == TAB + RITES || buttonId == TAB + GROWTH) {
            tab = buttonId - TAB;
            return true;
        }
        if (buttonId == BEGIN && server && altar != null) {
            altar.beginRite(player);
            return true;
        }
        if (buttonId >= FILL && server && altar != null) {
            java.util.List<com.lealex.alchyforbidden.rite.AltarRite> rites = com.lealex.alchyforbidden.rite.AltarRites.all();
            int index = buttonId - FILL;
            if (index < rites.size()) altar.fillStands(player, rites.get(index));
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return !server || (altar != null && !altar.isRemoved() && altar.withinReach(player));
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, offering); // what the altar did not take goes back
        clearContainer(player, pouring);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < FIRST_INVENTORY) {
            if (!moveItemStackTo(stack, FIRST_INVENTORY, slots.size(), true)) return ItemStack.EMPTY;
        } else if (tab == GROWTH) {
            Slot eye = slots.get(EYE);
            if (eye.mayPlace(stack) && !eye.hasItem()) {
                eye.setByPlayer(stack.split(1));
            } else if (!moveItemStackTo(stack, OFFER, OFFER + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof com.lealex.alchyforbidden.item.VesselItem
                && com.lealex.alchyforbidden.item.VesselItem.contents(stack) != null) { // (sculk may be an ingredient: it goes to the stands)
            if (!moveItemStackTo(stack, POUR, POUR + 1, false)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int i = 0; i < AltarBlockEntity.MAX_STANDS && !stack.isEmpty(); i++) { // one on each free stand
                Slot stand = slots.get(i);
                if (stand.isActive() && !stand.hasItem() && stand.mayPlace(stack)) {
                    stand.setByPlayer(stack.split(1));
                    moved = true;
                }
            }
            if (!moved) return ItemStack.EMPTY;
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

    /** The eye's place over the center, read from and written to the altar (only the server changes it). */
    private class EyePlace implements Container {
        @Override
        public int getContainerSize() {
            return 1;
        }

        @Override
        public boolean isEmpty() {
            return getItem(0).isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return altar == null ? ItemStack.EMPTY : altar.getAnimationDisplay(AltarBlockEntity.EYE);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            return server && altar != null ? altar.removeEye() : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (!server || altar == null) return; // the client sees the eye through the altar's own sync
            if (stack.isEmpty()) altar.removeEye();
            else altar.placeEye(stack);
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
