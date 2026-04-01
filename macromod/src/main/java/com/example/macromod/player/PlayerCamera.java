package com.example.macromod.player;

import com.example.macromod.data.blockpos.BaseBlockPos;
import com.example.macromod.math.vectors.vec2.Vector2d;
import com.example.macromod.math.vectors.vec2.Vector2f;
import com.example.macromod.math.vectors.vec3.Vector3d;
import com.example.macromod.minecraft.MinecraftAdapter;
import com.example.macromod.misc.Direction;

public class PlayerCamera {

    public enum CameraState {
        LOCKED,
        FREELOOK,
        DEFAULT
    }

    private CameraState state = CameraState.DEFAULT;
    private CameraState preForcedState = state;
    private final Vector2f lastFreeView = new Vector2f();
    private final MinecraftAdapter minecraftAdapter;

    public PlayerCamera(MinecraftAdapter minecraftAdapter) {
        this.minecraftAdapter = minecraftAdapter;
        this.minecraftAdapter.setMouseChangeInterceptor(() -> getState() != CameraState.LOCKED);
    }


    /**
     * Sets the state of the player-camera and syncs the camera rotation to the player rotation if switching to free-look mode.
     *
     * @param state the new state of the camera
     */
    public void setState(CameraState state) {
        this.state = state;
    }


    /**
     * @return the {@link CameraState} of the player-camera
     */
    public CameraState getState() {
        return state;
    }


    /**
     * Checks if the player is looking at the given {@link BaseBlockPos}
     *
     * @param pos           the position
     * @param ignorePitch   set to true to ignore the pitch / up-down-axis
     * @param rangeAngleDeg the threshold of the angle in degrees
     * @return whether the player is looking at the given position
     */
    public boolean isLookingAt(BaseBlockPos pos, boolean ignorePitch, double rangeAngleDeg) {
        return isLookingAt(pos.getX(), pos.getY(), pos.getZ(), ignorePitch, rangeAngleDeg);
    }


    /**
     * Checks if the player is looking at the block at the given position
     *
     * @param x             the x position of the block
     * @param y             the y position of the block
     * @param z             the z position of the block
     * @param ignorePitch   set to true to ignore the pitch / up-down-axis
     * @param rangeAngleDeg the threshold of the angle in degrees
     * @return whether the player is looking at the given position
     */
    public boolean isLookingAt(int x, int y, int z, boolean ignorePitch, double rangeAngleDeg) {
        if (minecraftAdapter.hasPlayer()) {
            if (ignorePitch) {
                final Vector2d posHead = minecraftAdapter.getPlayerHeadPositionXZ();
                final Vector2d posBlock = new Vector2d(x + 0.5, z + 0.5);
                final Vector2d dirBlock = posBlock.copy().sub(posHead).normalize();
                final Vector2d lookDir = new Vector2d(getLookDir().x, getLookDir().z).normalize();
                final double angle = lookDir.angleDeg(dirBlock);
                return Math.abs(angle) <= rangeAngleDeg;
            } else {
                final Vector3d posHead = minecraftAdapter.getPlayerHeadPosition();
                final Vector3d posBlock = new Vector3d(x + 0.5, y + 0.5, z + 0.5);
                final Vector3d dirBlock = posBlock.copy().sub(posHead).normalize();
                final Vector3d lookDir = getLookDir().normalize();
                final double angle = lookDir.angleDeg(dirBlock);
                return Math.abs(angle) <= rangeAngleDeg;
            }
        } else {
            return false;
        }
    }


    /**
     * @return the direction the player is looking as a {@link Vector3d}
     */
    public Vector3d getLookDir() {
        return minecraftAdapter.getLookDir();
    }


    /**
     * Sets the view-direction of the player to look at the given {@link BaseBlockPos}.
     *
     * @param pos the position to look at
     */
    public void setLookAt(BaseBlockPos pos) {
        setLookAt(pos, false);
    }


    /**
     * Sets the view-direction of the player to look at the given {@link BaseBlockPos}.
     *
     * @param pos       the {@link BaseBlockPos} to look at
     * @param keepPitch set to true to keep the pitch of the current view-direction
     */
    public void setLookAt(BaseBlockPos pos, boolean keepPitch) {
        setLookAt(pos.getX(), pos.getY(), pos.getZ(), keepPitch);
    }


    /**
     * Sets the view-direction of the player to look at a block at the given position.
     *
     * @param x         the x position of the block to look at
     * @param y         the y position of the block to look at
     * @param z         the z position of the block to look at
     * @param keepPitch set to true to keep the pitch of the current view-direction
     */
    public void setLookAt(int x, int y, int z, boolean keepPitch) {
        if (minecraftAdapter.hasPlayer()) {
            final Vector3d posBlock = new Vector3d(x + 0.5, y + 0.5, z + 0.5);
            final Vector3d posHead = minecraftAdapter.getPlayerHeadPosition();
            final Vector3d dir = posBlock.copy().sub(posHead).normalize().scale(-1);
            if (keepPitch) {
                dir.y = 0;
            }
            setLook(dir);
        }
    }


    /**
     * Sets the view-direction of the player to look at the given point
     *
     * @param point the position of the point
     */
    public void setLookAtPoint(Vector3d point) {
        if (minecraftAdapter.hasPlayer()) {
            final Vector3d posHead = minecraftAdapter.getPlayerHeadPosition();
            final Vector3d dir = point.copy().sub(posHead).normalize().scale(-1);
            setLook(dir);
        }
    }


    /**
     * Makes the player look at the given side of the block at the given {@link BaseBlockPos}.
     *
     * @param pos       the position
     * @param direction the direction of the side
     */
    public void setLookAtBlockSide(BaseBlockPos pos, Direction direction) {
        final PlayerCamera camera = PlayerUtils.getCamera();
        final Vector3d posLookAt = new Vector3d(pos.getCenterX(), pos.getCenterY(), pos.getCenterZ())
                .add(direction.dx * 0.5, direction.dy * 0.5, direction.dz * 0.5);
        camera.setLookAtPoint(posLookAt);
    }


    /**
     * Sets the view-direction of the player.
     *
     * @param dir the new view-direction
     */
    public void setLook(Vector3d dir) {
        double pitch = Math.asin(dir.y);
        double yaw = Math.atan2(dir.z, dir.x);
        pitch = pitch * 180.0 / Math.PI;
        yaw = yaw * 180.0 / Math.PI;
        yaw += 90f;
        setLook(pitch, yaw);
    }


    /**
     * Sets the view-direction of the player.
     *
     * @param pitch the new pitch
     * @param yaw   the new yaw
     */
    public void setLook(double pitch, double yaw) {
        minecraftAdapter.setPlayerRotation((float) yaw, (float) pitch);
    }


    /**
     * Forces the camera to look in specific directions, event when freelook is enabled.
     * Does NOT lock camera if freelook is currently enabled (to allow user camera control).
     */
    public void enableForceCamera() {
        preForcedState = getState();
        // Don't lock camera if user has freelook enabled - they should keep control
        if (getState() != CameraState.FREELOOK) {
            setState(CameraState.LOCKED);
        }
    }


    /**
     * Stops forcing the camera to look in specific directions, event when freelook is enabled.
     *
     * @param restoreFreelookView restores the view direction to the state before forceCamera was enabled
     */
    public void disableForceCamera(boolean restoreFreelookView) {
        setState(preForcedState);
        if (restoreFreelookView) {
            minecraftAdapter.setCameraRotation(lastFreeView.x, lastFreeView.y);
        }
    }


    /**
     * @return whether the camera is forced to look in a specific direction.
     */
    public boolean isForceEnabled() {
        return getState() == CameraState.LOCKED;
    }

    /**
     * Check if the camera should be allowed to move.
     * Used by lock-camera feature to completely disable camera movement during macro.
     */
    public boolean canRotateCamera() {
        // Allow rotation when freelook is enabled or when not in locked state
        return getState() == CameraState.FREELOOK || getState() != CameraState.LOCKED;
    }

}
