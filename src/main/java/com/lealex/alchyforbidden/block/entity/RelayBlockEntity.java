package com.lealex.alchyforbidden.block.entity;

import com.lealex.alchyforbidden.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * An Echo Obelisk's foot: where it sends what reaches it, set with the Attuning Fork. It may branch: up to
 * Config.obeliskBranches places (other obelisks, altars, fountains); what reaches it goes down a branch that leads to
 * something with room, the branches taken in turn (essence/Relays). Synced.
 */
public class RelayBlockEntity extends BlockEntity {
    private final java.util.List<BlockPos> targets = new java.util.ArrayList<>();
    /** Which branch to try first next time (not saved): the branches share what comes through. */
    private int turn = 0;

    public RelayBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.RELAY_BE.get(), pos, state);
    }

    public java.util.List<BlockPos> targets() {
        return java.util.Collections.unmodifiableList(targets);
    }

    public boolean hasTarget(BlockPos pos) {
        return targets.contains(pos);
    }

    /** Adds a branch (false when it already has as many as it may). */
    public boolean addTarget(BlockPos pos) {
        if (targets.contains(pos)) return true;
        if (targets.size() >= com.lealex.alchyforbidden.Config.obeliskBranches()) return false;
        targets.add(pos.immutable());
        changed();
        return true;
    }

    public void removeTarget(BlockPos pos) {
        if (targets.remove(pos)) changed();
    }

    public void clearTargets() {
        if (targets.isEmpty()) return;
        targets.clear();
        changed();
    }

    public int turn() {
        return turn;
    }

    public void setTurn(int turn) {
        this.turn = turn;
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Broken (not unloaded): links pointing at it are cut. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof net.minecraft.server.level.ServerLevel server) com.lealex.alchyforbidden.essence.Relays.unlinkTo(server, pos, false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!targets.isEmpty()) output.store("targets", BlockPos.CODEC.listOf(), java.util.List.copyOf(targets));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        targets.clear();
        input.read("targets", BlockPos.CODEC.listOf()).ifPresent(targets::addAll);
        input.read("target", BlockPos.CODEC).ifPresent(old -> { if (!targets.contains(old)) targets.add(old); }); // obelisks from before branching
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
