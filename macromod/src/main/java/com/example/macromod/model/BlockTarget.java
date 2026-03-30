package com.example.macromod.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * Represents a single block target within a macro step.
 * Holds the position, expected block type, optional NBT data, and mining status.
 */
@Environment(EnvType.CLIENT)
public class BlockTarget {

    /** World position of the block to mine. */
    private BlockPos pos;

    /** Registry ID of the expected block, e.g. "minecraft:coal_ore". */
    private String blockId;

    /** Optional NBT data for complex blocks (containers, signs, etc.). */
    private NbtCompound blockNbt;

    /** Whether this block has been successfully mined. */
    private boolean mined;

    /** Whether this block was skipped due to mismatch. */
    private boolean skipped;

    public BlockTarget() {
    }

    public BlockTarget(BlockPos pos, String blockId) {
        this.pos = pos;
        this.blockId = blockId;
        this.mined = false;
        this.skipped = false;
    }

    public BlockTarget(BlockPos pos, String blockId, NbtCompound blockNbt) {
        this(pos, blockId);
        this.blockNbt = blockNbt;
    }

    public BlockPos getPos() {
        return pos;
    }

    public void setPos(BlockPos pos) {
        this.pos = pos;
    }

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
    }

    public NbtCompound getBlockNbt() {
        return blockNbt;
    }

    public void setBlockNbt(NbtCompound blockNbt) {
        this.blockNbt = blockNbt;
    }

    public boolean isMined() {
        return mined;
    }

    public void setMined(boolean mined) {
        this.mined = mined;
    }

    public boolean isSkipped() {
        return skipped;
    }

    public void setSkipped(boolean skipped) {
        this.skipped = skipped;
    }

    /**
     * Returns true if this target has been processed (mined or skipped).
     */
    public boolean isProcessed() {
        return mined || skipped;
    }

    /**
     * Resets the mining/skipped status for re-execution.
     */
    public void reset() {
        this.mined = false;
        this.skipped = false;
    }

    /**
     * Creates a deep copy of this block target.
     */
    public BlockTarget copy() {
        BlockTarget copy = new BlockTarget(pos, blockId);
        copy.blockNbt = blockNbt != null ? blockNbt.copy() : null;
        copy.mined = false;
        copy.skipped = false;
        return copy;
    }
}
