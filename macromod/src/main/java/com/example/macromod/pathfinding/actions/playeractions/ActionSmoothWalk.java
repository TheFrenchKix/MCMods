package com.example.macromod.pathfinding.actions.playeractions;

import com.example.macromod.data.blockpos.BaseBlockPos;
import com.example.macromod.math.vectors.vec3.Vector3d;
import com.example.macromod.misc.ProcState;
import com.example.macromod.pathfinding.actions.ActionObserver;
import com.example.macromod.pathfinding.nodes.Node;
import com.example.macromod.player.PlayerCamera;
import com.example.macromod.player.PlayerInput;
import com.example.macromod.player.PlayerUtils;

/**
 * Walk action for smoothed multi-block segments.
 * Replaces multiple consecutive ActionWalk nodes that share the same Y level and
 * have clear line-of-sight between endpoints.
 * <p>
 * Uses angle-decomposed movement (forward + strafe) so the player can navigate
 * diagonals and long segments without oscillating or micro-stopping.
 */
public class ActionSmoothWalk extends Action {


    private static final float SPRINT_YAW_THRESHOLD = 35f;
    private static final double ARRIVE_RADIUS_SQ = 0.35 * 0.35;
    private static final double ARRIVE_RADIUS_DIAG_SQ = 0.50 * 0.50;
    private static final double ARRIVE_DY = 0.5;
    private static final double DECOMPOSITION_THRESHOLD = 0.1;


    public ActionSmoothWalk(Node from, Node to, double cost) {
        super(from, to, cost);
    }


    @Override
    public String getActionName() {
        return "smooth-walk";
    }


    @Override
    public String getActionNameExp() {
        return "smooth-walk-sprint";
    }


    @Override
    public ProcState tick(boolean firstTick) {
        ActionObserver.tickAction(this.getActionNameExp());

        if (hasReached()) {
            PlayerUtils.getInput().setSneak();
            return ProcState.DONE;
        }

        BaseBlockPos target = getTo().getPos();
        Vector3d playerPos = PlayerUtils.getPlayerPosition();
        PlayerCamera camera = PlayerUtils.getCamera();
        PlayerInput input = PlayerUtils.getInput();

        // Point camera at target (keepPitch = true so we don't look at the ground)
        camera.setLookAt(target, true);

        // Compute desired yaw toward target block center
        double dx = (target.getCenterX()) - playerPos.x;
        double dz = (target.getCenterZ()) - playerPos.z;
        float wantYaw = (float) (-Math.atan2(dx, dz) * (180.0 / Math.PI));

        // Get current yaw from look direction
        Vector3d lookDir = camera.getLookDir();
        float currentYaw = (float) (-Math.atan2(lookDir.x, lookDir.z) * (180.0 / Math.PI));

        float dYaw = wrapAngle(wantYaw - currentYaw);

        // Decompose movement: forward = cos(dYaw), strafe = sin(dYaw)
        double rad = Math.toRadians(dYaw);
        double fwd = Math.cos(rad);
        double strafe = Math.sin(rad);

        input.setMoveForward(fwd > DECOMPOSITION_THRESHOLD);
        input.setMoveBackward(fwd < -DECOMPOSITION_THRESHOLD);
        input.setMoveRight(strafe > DECOMPOSITION_THRESHOLD);
        input.setMoveLeft(strafe < -DECOMPOSITION_THRESHOLD);

        // Sprint only when roughly facing the target
        input.setSprint(Math.abs(dYaw) < SPRINT_YAW_THRESHOLD);

        return ProcState.EXECUTING;
    }


    private boolean hasReached() {
        BaseBlockPos target = getTo().getPos();
        Vector3d pos = PlayerUtils.getPlayerPosition();

        double dx = pos.x - target.getCenterX();
        double dz = pos.z - target.getCenterZ();
        double dy = Math.abs(pos.y - target.getY());

        boolean diagonal = Math.abs(dx) > 0.3 && Math.abs(dz) > 0.3;
        double radiusSq = diagonal ? ARRIVE_RADIUS_DIAG_SQ : ARRIVE_RADIUS_SQ;

        return (dx * dx + dz * dz) <= radiusSq && dy <= ARRIVE_DY;
    }


    private static float wrapAngle(float a) {
        a %= 360f;
        if (a > 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }


    @Override
    public boolean isOnPath(BaseBlockPos position) {
        BaseBlockPos from = getFrom().getPos();
        BaseBlockPos to = getTo().getPos();

        // Must be at the same Y level
        if (position.getY() != from.getY()) {
            return false;
        }

        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        int px = position.getX() - from.getX();
        int pz = position.getZ() - from.getZ();

        int lenSq = dx * dx + dz * dz;
        if (lenSq == 0) {
            return position.equals(from);
        }

        // Project position onto the from->to segment
        double t = (double) (px * dx + pz * dz) / lenSq;
        if (t < -0.1 || t > 1.1) {
            return false;
        }

        // Perpendicular distance from position to closest point on line
        double closestX = from.getX() + t * dx;
        double closestZ = from.getZ() + t * dz;
        double distSq = (position.getX() - closestX) * (position.getX() - closestX)
                       + (position.getZ() - closestZ) * (position.getZ() - closestZ);

        // Within 1.5 blocks of the line
        return distSq <= 2.25;
    }


}
