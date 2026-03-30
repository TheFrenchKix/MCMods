package lfmdevelopment.lfmclient.systems.modules.macro.pathfinding;

import net.minecraft.util.math.BlockPos;

public class StuckDetector {
    private BlockPos lastPos;
    private int stuckTicks;
    private final int threshold;

    public StuckDetector(int tickThreshold) {
        this.threshold = tickThreshold;
        reset();
    }

    public void reset() {
        this.lastPos = null;
        this.stuckTicks = 0;
    }

    public boolean update(BlockPos currentPos) {
        if (lastPos != null && lastPos.equals(currentPos)) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
            lastPos = currentPos;
        }
        return stuckTicks >= threshold;
    }

    public int getStuckTicks() {
        return stuckTicks;
    }
}
