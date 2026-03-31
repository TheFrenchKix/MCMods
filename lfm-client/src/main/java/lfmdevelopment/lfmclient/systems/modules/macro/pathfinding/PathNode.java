package lfmdevelopment.lfmclient.systems.modules.macro.pathfinding;

import net.minecraft.util.math.BlockPos;


public class PathNode implements Comparable<PathNode> {
    public final BlockPos pos;
    public double gCost;
    public double hCost;
    public PathNode parent;

    public PathNode(BlockPos pos) {
        this.pos = pos;
        this.gCost = Double.MAX_VALUE;
        this.hCost = 0;
    }

    public double fCost() {
        return gCost + hCost;
    }

    @Override
    public int compareTo(PathNode other) {
        return Double.compare(this.fCost(), other.fCost());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PathNode n)) return false;
        return pos.equals(n.pos);
    }

    @Override
    public int hashCode() {
        return pos.hashCode();
    }
}

