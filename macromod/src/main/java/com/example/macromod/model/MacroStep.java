package com.example.macromod.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single step (waypoint) in a macro.
 * Contains a destination position and a list of block targets to mine at that location.
 */
@Environment(EnvType.CLIENT)
public class MacroStep {

    /** Human-readable label for this step, e.g. "Coal vein 1". */
    private String label;

    /** Navigation destination — the player walks to this position. */
    private BlockPos destination;

    /** Blocks to mine once the player arrives at the destination. */
    private List<BlockTarget> targets;

    /** Optional search radius around the waypoint for block scanning. */
    private int radius;

    public MacroStep() {
        this.targets = new ArrayList<>();
        this.radius = 5;
    }

    public MacroStep(String label, BlockPos destination) {
        this();
        this.label = label;
        this.destination = destination;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public BlockPos getDestination() {
        return destination;
    }

    public void setDestination(BlockPos destination) {
        this.destination = destination;
    }

    public List<BlockTarget> getTargets() {
        return targets;
    }

    public void setTargets(List<BlockTarget> targets) {
        this.targets = targets;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    /**
     * Adds a block target to this step.
     */
    public void addTarget(BlockTarget target) {
        this.targets.add(target);
    }

    /**
     * Removes a block target by index.
     */
    public void removeTarget(int index) {
        if (index >= 0 && index < targets.size()) {
            targets.remove(index);
        }
    }

    /**
     * Returns the count of blocks that have been processed (mined or skipped).
     */
    public int getProcessedCount() {
        int count = 0;
        for (BlockTarget target : targets) {
            if (target.isProcessed()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns the count of blocks that have been successfully mined.
     */
    public int getMinedCount() {
        int count = 0;
        for (BlockTarget target : targets) {
            if (target.isMined()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns true if all targets in this step are processed.
     */
    public boolean isComplete() {
        if (targets.isEmpty()) {
            return true;
        }
        for (BlockTarget target : targets) {
            if (!target.isProcessed()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resets all block targets for re-execution.
     */
    public void reset() {
        for (BlockTarget target : targets) {
            target.reset();
        }
    }

    /**
     * Creates a deep copy of this step.
     */
    public MacroStep copy() {
        MacroStep copy = new MacroStep(label, destination);
        copy.radius = radius;
        for (BlockTarget target : targets) {
            copy.targets.add(target.copy());
        }
        return copy;
    }
}
