package lfmdevelopment.lfmclient.systems.modules.macro.pathfinding;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Path {
    private final List<BlockPos> nodes;
    private int currentIndex = 0;

    public Path(List<BlockPos> nodes) {
        this.nodes = new ArrayList<>(nodes);
    }

    public BlockPos current() {
        if (currentIndex >= nodes.size()) return null;
        return nodes.get(currentIndex);
    }

    public BlockPos next() {
        if (currentIndex + 1 >= nodes.size()) return null;
        return nodes.get(currentIndex + 1);
    }

    public boolean advance() {
        if (currentIndex < nodes.size() - 1) {
            currentIndex++;
            return true;
        }
        return false;
    }

    public boolean isComplete() {
        return currentIndex >= nodes.size() - 1;
    }

    public int currentIndex() {
        return currentIndex;
    }

    public int size() {
        return nodes.size();
    }

    public List<BlockPos> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    public BlockPos getStart() {
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    public BlockPos getEnd() {
        return nodes.isEmpty() ? null : nodes.get(nodes.size() - 1);
    }
}
