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
    public static final int MAIN = 0, GROWTH = 1, RINGS = 2, TAB = 0;
    /** The pit's healing button (tier 3): off, or on again. */
    public static final int HEAL = 5, VOID = 6, TAKE_XP = 7;
    /** The fountain: PICK + index chooses what it weeps (its place in Config.fountainProducts). */
    public static final int PICK = 20;
    /** The infuser: ESSENCE + ordinal picks what it pours; BIND / UNBIND its body (tier 3). */
    public static final int ESSENCE = 90, BIND = 8, UNBIND = 9;
    public static final int VESSEL_X = 40, VESSEL_Y = 50;
    /** The Binding Circle: BIND_SPELL + the spell's ordinal imbues it into the ring in its slot; UNBIND_SPELL empties that ring. */
    public static final int BIND_SPELL = 120, UNBIND_SPELL = 160;
    /** The circle's slots: the ring to imbue (Bind tab); the worn rings, the bench ring and its gem sockets (Rings tab). */
    public static final int RING_X = 104, RING_Y = 84, WORN_X = 8, WORN_Y = 30, WORN_STEP = 22, WORN_MAX = 5;
    public static final int BENCH_X = 8, BENCH_Y = 74, GEM_X = 36, GEM_STEP = 20, GEMS = 4;
    public static final int DROPS_X = 112, DROPS_Y = 24, LOSS_X = 130, LOSS_Y = 46, OFFER_X = 10, OFFER_Y = 96;
    /** The Rift Maw: the lure and the blank ring (Summon tab), the left hand's rings (Rings tab); BEGIN, MEND + slot. */
    public static final int LURE_X = 12, LURE_Y = 24, MAW_RING_X = 12, MAW_RING_Y = 46, FAM_X = 12, FAM_Y = 32, FAM_STEP = 50, FAM_MAX = 3;
    public static final int CATALYST_X = 12, CATALYST_Y = 68, PRISM_X = 12, PRISM_Y = 90;
    public static final int BEGIN = 11, MEND = 12, CORRUPT = 16, RELEASE = 24, FILTER = 40;

    private final @Nullable GrowingCoreBlockEntity core;
    private final @Nullable BlockPos pos;
    private final boolean server;
    private final SimpleContainer offering = new SimpleContainer(1);
    private final SimpleContainer loss = new SimpleContainer(1);
    // the circle's: the ring to imbue, the worn rings (the player's own, read from and written to them), the bench ring
    // and its sockets (the gems mirror the ring's own: putting one in or taking it out changes the ring)
    private final SimpleContainer imbueRing = new SimpleContainer(1);
    private @Nullable SimpleContainer worn;
    private final SimpleContainer bench = new SimpleContainer(1);
    /** The bench ring's sockets, read from and written straight into the ring (no copy to keep in step). */
    private final Container gems = new GemSockets();
    // the maw's: the lure, the blank ring, the left hand's worn rings (read from and written to the player)
    private final SimpleContainer mawLure = new SimpleContainer(1);
    private final SimpleContainer mawRing = new SimpleContainer(1);
    private final SimpleContainer mawCatalyst = new SimpleContainer(1);
    private final SimpleContainer mawPrism = new SimpleContainer(1);
    /** The affixes the maw's rituals may roll (bit = RiftAffix ordinal); both sides keep it, the screen's toggles are sent. */
    private int filterMask = com.lealex.alchyforbidden.familiar.RiftAffix.ALL;
    private @Nullable SimpleContainer familiars;
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
        } else if (core instanceof com.lealex.alchyforbidden.block.entity.WeepingFountainBlockEntity fountain) { // what it wept, take only
            for (int i = 0; i < 9; i++) {
                addSlot(new TabSlot(fountain.store(), i, DROPS_X + (i % 3) * 18, DROPS_Y + (i / 3) * 18, MAIN) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            }
        } else if (core instanceof com.lealex.alchyforbidden.block.entity.EssenceInfuserBlockEntity infuser) { // the vessel it fills
            addSlot(new TabSlot(infuser.vessel(), 0, VESSEL_X, VESSEL_Y, MAIN) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof com.lealex.alchyforbidden.item.VesselItem;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        } else if (core instanceof com.lealex.alchyforbidden.block.entity.SoulCircleBlockEntity) {
            addCircleSlots(player);
        } else if (core instanceof com.lealex.alchyforbidden.block.entity.RiftMawBlockEntity) {
            addMawSlots(player);
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
            public boolean isActive() { // gone once the multiblock is at its last tier
                return super.isActive() && SourceMenu.this.core != null && SourceMenu.this.core.tier() < SourceMenu.this.core.maxTier();
            }

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

    private void addCircleSlots(Player player) {
        addSlot(new TabSlot(imbueRing, 0, RING_X, RING_Y, MAIN) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof com.lealex.alchyforbidden.item.SpellRingItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        worn = new SimpleContainer(WORN_MAX) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (!server) return;
                java.util.List<com.lealex.alchyforbidden.essence.Spells.@Nullable Worn> rings = new java.util.ArrayList<>();
                for (int i = 0; i < getContainerSize(); i++) rings.add(com.lealex.alchyforbidden.essence.Spells.Worn.of(getItem(i)));
                com.lealex.alchyforbidden.essence.Spells.setWorn(player, rings);
            }
        };
        if (server) {
            var current = com.lealex.alchyforbidden.essence.Spells.worn(player);
            for (int i = 0; i < current.size() && i < WORN_MAX; i++) {
                var ring = current.get(i);
                if (ring != null) worn.getItems().set(i, ring.toStack()); // straight in: nothing to write back
            }
        }
        for (int i = 0; i < WORN_MAX; i++) {
            final int place = i; // not 'index': Slot has a field of that name (its place in the menu), which would win here
            addSlot(new TabSlot(worn, i, WORN_X + i * WORN_STEP, WORN_Y, RINGS) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return com.lealex.alchyforbidden.item.SpellRingItem.spell(stack) != null && place < com.lealex.alchyforbidden.essence.Spells.soulSlots(player);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean isActive() {
                    return super.isActive() && place < com.lealex.alchyforbidden.essence.Spells.soulSlots(player);
                }
            });
        }
        addSlot(new TabSlot(bench, 0, BENCH_X, BENCH_Y, RINGS) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof com.lealex.alchyforbidden.item.SpellRingItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int i = 0; i < GEMS; i++) {
            final int place = i; // not 'index': Slot has a field of that name (its place in the menu), which would win here
            addSlot(new TabSlot(gems, i, GEM_X + i * GEM_STEP, BENCH_Y, RINGS) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof com.lealex.alchyforbidden.item.SpellGemItem && place < sockets();
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean isActive() {
                    return super.isActive() && place < sockets();
                }
            });
        }
    }

    private void addMawSlots(Player player) {
        addSlot(new TabSlot(mawLure, 0, LURE_X, LURE_Y, MAIN) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof com.lealex.alchyforbidden.item.RiftLureItem;
            }
        });
        addSlot(new TabSlot(mawRing, 0, MAW_RING_X, MAW_RING_Y, MAIN) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof com.lealex.alchyforbidden.item.FamiliarRingItem && com.lealex.alchyforbidden.item.FamiliarRingItem.data(stack) == null;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        familiars = new SimpleContainer(FAM_MAX) {
            @Override
            public void setChanged() {
                super.setChanged();
                if (!server || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return;
                java.util.List<com.lealex.alchyforbidden.familiar.FamiliarData.@Nullable Worn> rings = new java.util.ArrayList<>();
                for (int i = 0; i < getContainerSize(); i++) rings.add(com.lealex.alchyforbidden.familiar.Familiars.ofStack(getItem(i)));
                var before = com.lealex.alchyforbidden.familiar.Familiars.worn(serverPlayer);
                boolean changed = false;
                for (int i = 0; i < before.size(); i++) {
                    var was = before.get(i);
                    var now = i < rings.size() ? rings.get(i) : null;
                    if (!java.util.Objects.equals(was, now)) changed = true;
                }
                if (!changed) return;
                com.lealex.alchyforbidden.familiar.Familiars.recallAll(serverPlayer); // a ring taken off takes its familiar with it
                com.lealex.alchyforbidden.familiar.Familiars.setWorn(serverPlayer, rings);
            }
        };
        if (server) refreshFamiliars(player);
        addFamiliarSlots(player);
        addSlot(new TabSlot(mawCatalyst, 0, CATALYST_X, CATALYST_Y, MAIN) { // index 2 + FAM_MAX: Rift Residue, the odds' catalyst
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModRegistries.RIFT_RESIDUE.get());
            }
        });
        addSlot(new TabSlot(mawPrism, 0, PRISM_X, PRISM_Y, MAIN) { // index 3 + FAM_MAX: Void Prisms for an affix filter
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModRegistries.VOID_PRISM.get());
            }
        });
    }

    private void addFamiliarSlots(Player player) {
        for (int i = 0; i < FAM_MAX; i++) {
            final int place = i; // not 'index' (Slot's own field)
            addSlot(new TabSlot(familiars, i, FAM_X + i * FAM_STEP, FAM_Y, RINGS) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return com.lealex.alchyforbidden.item.FamiliarRingItem.data(stack) != null && place < com.lealex.alchyforbidden.familiar.Familiars.slots(player);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean isActive() {
                    return super.isActive() && place < com.lealex.alchyforbidden.familiar.Familiars.slots(player);
                }
            });
        }
    }

    /** The worn familiar rings as items again (their live level, experience and crack). */
    private void refreshFamiliars(Player player) {
        if (familiars == null) return;
        var current = com.lealex.alchyforbidden.familiar.Familiars.worn(player);
        for (int i = 0; i < FAM_MAX; i++) {
            var ring = i < current.size() ? current.get(i) : null;
            familiars.getItems().set(i, ring == null ? ItemStack.EMPTY : com.lealex.alchyforbidden.familiar.Familiars.toStack(ring)); // straight in: nothing to write back
        }
    }

    public ItemStack mawLure() {
        return mawLure.getItem(0);
    }

    public ItemStack mawRing() {
        return mawRing.getItem(0);
    }

    public ItemStack mawCatalyst() {
        return mawCatalyst.getItem(0);
    }

    public ItemStack mawPrism() {
        return mawPrism.getItem(0);
    }

    public int filterMask() {
        return filterMask;
    }

    public void toggleFilter(int affix) {
        filterMask ^= 1 << affix;
        filterMask &= com.lealex.alchyforbidden.familiar.RiftAffix.ALL;
    }

    /** The ring goes with the player between tabs: from the imbuing slot to the bench and back, when the other is free. */
    private void carryRing(int from, int to) {
        if (from == MAIN && to == RINGS && bench.getItem(0).isEmpty() && !imbueRing.getItem(0).isEmpty()) {
            bench.setItem(0, imbueRing.removeItemNoUpdate(0));
            imbueRing.setChanged();
        } else if (from == RINGS && to == MAIN && imbueRing.getItem(0).isEmpty() && !bench.getItem(0).isEmpty()) {
            imbueRing.setItem(0, bench.removeItemNoUpdate(0));
            bench.setChanged();
        }
    }

    /** How many sockets the bench ring has (0 without one). */
    public int sockets() {
        int tier = com.lealex.alchyforbidden.item.SpellRingItem.tierOf(bench.getItem(0));
        return tier == 0 ? 0 : com.lealex.alchyforbidden.item.SpellRingItem.sockets(tier);
    }

    public ItemStack imbueRing() {
        return imbueRing.getItem(0);
    }

    public ItemStack benchRing() {
        return bench.getItem(0);
    }

    /** The bench ring's sockets as a container: each slot is the gem in that socket of the ring on the bench. */
    private final class GemSockets implements Container {
        private com.lealex.alchyforbidden.essence.@Nullable SpellGem gemAt(int socket) {
            ItemStack ring = bench.getItem(0);
            return ring.getItem() instanceof com.lealex.alchyforbidden.item.SpellRingItem
                    ? com.lealex.alchyforbidden.item.SpellRingItem.contents(ring).gem(socket) : null;
        }

        private void write(int socket, com.lealex.alchyforbidden.essence.@Nullable SpellGem gem) {
            ItemStack ring = bench.getItem(0);
            if (!(ring.getItem() instanceof com.lealex.alchyforbidden.item.SpellRingItem)) return;
            com.lealex.alchyforbidden.item.SpellRingItem.setContents(ring, com.lealex.alchyforbidden.item.SpellRingItem.contents(ring).withGem(socket, gem));
        }

        @Override
        public int getContainerSize() {
            return GEMS;
        }

        @Override
        public boolean isEmpty() {
            for (int i = 0; i < GEMS; i++) if (gemAt(i) != null) return false;
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            var gem = gemAt(slot);
            return gem == null ? ItemStack.EMPTY : new ItemStack(ModRegistries.GEMS.get(gem).get());
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            ItemStack taken = getItem(slot);
            if (!taken.isEmpty() && count > 0) write(slot, null);
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return removeItem(slot, 1);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            write(slot, stack.getItem() instanceof com.lealex.alchyforbidden.item.SpellGemItem gem ? gem.gem() : null);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            bench.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            // the gems belong to the ring: nothing to clear here
        }
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
        if (buttonId == TAB + MAIN || buttonId == TAB + GROWTH || buttonId == TAB + RINGS) {
            int was = tab;
            tab = buttonId - TAB;
            if (core instanceof com.lealex.alchyforbidden.block.entity.SoulCircleBlockEntity) carryRing(was, tab);
            return true;
        }
        if (buttonId >= PICK && buttonId < PICK + 64 && server && core instanceof com.lealex.alchyforbidden.block.entity.WeepingFountainBlockEntity fountain) {
            fountain.choose(buttonId - PICK, player);
            return true;
        }
        if (server && core instanceof com.lealex.alchyforbidden.block.entity.SoulCircleBlockEntity circle
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            com.lealex.alchyforbidden.essence.Spell[] spells = com.lealex.alchyforbidden.essence.Spell.values();
            if (buttonId >= BIND_SPELL && buttonId < BIND_SPELL + spells.length) {
                circle.imbue(serverPlayer, spells[buttonId - BIND_SPELL], imbueRing.getItem(0));
                imbueRing.setChanged();
                return true;
            }
            if (buttonId == UNBIND_SPELL) {
                circle.unbind(serverPlayer, imbueRing.getItem(0));
                imbueRing.setChanged();
                return true;
            }
        }
        if (server && core instanceof com.lealex.alchyforbidden.block.entity.RiftMawBlockEntity maw && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (buttonId == BEGIN) {
                maw.begin(serverPlayer, mawLure.getItem(0), mawRing.getItem(0), mawCatalyst.getItem(0), filterMask, mawPrism.getItem(0));
                mawPrism.setChanged();
                mawLure.setChanged();
                mawRing.setChanged();
                mawCatalyst.setChanged();
                return true;
            }
            if (buttonId >= FILTER && buttonId < FILTER + com.lealex.alchyforbidden.familiar.RiftAffix.values().length) {
                toggleFilter(buttonId - FILTER);
                return true;
            }
            if (buttonId >= RELEASE && buttonId < RELEASE + FAM_MAX) {
                maw.release(serverPlayer, buttonId - RELEASE);
                refreshFamiliars(player);
                broadcastChanges();
                return true;
            }
            if (buttonId >= CORRUPT && buttonId < CORRUPT + FAM_MAX) {
                maw.corrupt(serverPlayer, buttonId - CORRUPT, filterMask, mawPrism.getItem(0));
                mawPrism.setChanged();
                refreshFamiliars(player);
                broadcastChanges();
                return true;
            }
            if (buttonId >= MEND && buttonId < MEND + FAM_MAX) {
                maw.mend(serverPlayer, buttonId - MEND);
                refreshFamiliars(player);
                broadcastChanges();
                return true;
            }
        }
        if (server && core instanceof com.lealex.alchyforbidden.block.entity.EssenceInfuserBlockEntity infuser) {
            if (buttonId >= ESSENCE && buttonId < ESSENCE + com.lealex.alchyforbidden.essence.Essence.values().length) {
                infuser.choose(com.lealex.alchyforbidden.essence.Essence.values()[buttonId - ESSENCE]);
                return true;
            }
            if ((buttonId == BIND || buttonId == UNBIND) && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                infuser.bind(serverPlayer, buttonId == BIND);
                return true;
            }
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
        clearContainer(player, imbueRing);
        clearContainer(player, bench); // its gems go with it
        clearContainer(player, mawLure);
        clearContainer(player, mawRing);
        clearContainer(player, mawCatalyst);
        clearContainer(player, mawPrism);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < firstInventory) {
            if (!moveItemStackTo(stack, firstInventory, slots.size(), true)) return ItemStack.EMPTY;
        } else if (core instanceof com.lealex.alchyforbidden.block.entity.SoulCircleBlockEntity && tab != GROWTH) {
            int imbue = 0, firstWorn = 1, benchSlot = 1 + WORN_MAX, firstGem = benchSlot + 1;
            boolean moved;
            if (stack.getItem() instanceof com.lealex.alchyforbidden.item.SpellGemItem) {
                moved = tab == RINGS && moveItemStackTo(stack, firstGem, firstGem + GEMS, false);
            } else if (stack.getItem() instanceof com.lealex.alchyforbidden.item.SpellRingItem) {
                moved = tab == MAIN ? moveItemStackTo(stack, imbue, imbue + 1, false)
                        : moveItemStackTo(stack, firstWorn, firstWorn + WORN_MAX, false) || moveItemStackTo(stack, benchSlot, benchSlot + 1, false);
            } else {
                moved = false;
            }
            if (!moved) return ItemStack.EMPTY;
        } else if (core instanceof com.lealex.alchyforbidden.block.entity.RiftMawBlockEntity && tab != GROWTH) {
            boolean moved;
            if (stack.getItem() instanceof com.lealex.alchyforbidden.item.RiftLureItem) {
                moved = tab == MAIN && moveItemStackTo(stack, 0, 1, false);
            } else if (stack.is(ModRegistries.RIFT_RESIDUE.get())) {
                moved = tab == MAIN && moveItemStackTo(stack, 2 + FAM_MAX, 3 + FAM_MAX, false);
            } else if (stack.is(ModRegistries.VOID_PRISM.get())) {
                moved = tab == MAIN && moveItemStackTo(stack, 3 + FAM_MAX, 4 + FAM_MAX, false);
            } else if (stack.getItem() instanceof com.lealex.alchyforbidden.item.FamiliarRingItem) {
                moved = com.lealex.alchyforbidden.item.FamiliarRingItem.data(stack) == null
                        ? tab == MAIN && moveItemStackTo(stack, 1, 2, false)
                        : tab == RINGS && moveItemStackTo(stack, 2, 2 + FAM_MAX, false);
            } else {
                moved = false;
            }
            if (!moved) return ItemStack.EMPTY;
        } else if (tab == GROWTH) {
            if (!moveItemStackTo(stack, offerSlot, offerSlot + 1, false)) return ItemStack.EMPTY;
        } else if (core instanceof com.lealex.alchyforbidden.block.entity.EssenceInfuserBlockEntity
                && stack.getItem() instanceof com.lealex.alchyforbidden.item.VesselItem) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
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
