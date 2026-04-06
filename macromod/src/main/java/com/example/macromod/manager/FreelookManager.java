package com.example.macromod.manager;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;

/**
 * Manages the Freelook (free camera rotation) state.
 *
 * <p>When enabled, the camera yaw/pitch are decoupled from the player's actual
 * rotation.  Mouse input is redirected here so the player's body and movement
 * direction remain unchanged, while only the render camera rotates freely.
 *
 * <h3>Architecture</h3>
 * <ul>
 *   <li>{@code MouseMixin} intercepts {@code Mouse.updateMouse} and redirects
 *       the {@code changeLookDirection(DD)} call here instead of onto the
 *       player entity, keeping serverbound rotation packets clean.</li>
 *   <li>{@code CameraUpdateMixin} injects at the tail of {@code Camera.update}
 *       and re-applies these angles after Minecraft has set the camera from
 *       the entity rotation, overriding it for rendering only.</li>
 * </ul>
 */
public class FreelookManager {

    // ── Singleton ──────────────────────────────────────────────────
    private static final FreelookManager INSTANCE = new FreelookManager();

    public static FreelookManager getInstance() {
        return INSTANCE;
    }

    // ── State ──────────────────────────────────────────────────────
    private boolean enabled = false;

    /** Current freelook camera yaw in degrees. Wraps freely. */
    private float cameraYaw   = 0f;

    /** Current freelook camera pitch in degrees, clamped to [-90, +90]. */
    private float cameraPitch = 0f;

    /**
     * Rotation speed multiplier, matching the NeoForge FreeLook reference (0.15).
     * Applied on top of Minecraft's already sensitivity-scaled delta.
     */
    private static final float ROTATION_SPEED = 0.15f;

    /**
     * FOV to use during freelook (in integer degrees). Stored in config for persistence.
     * Applied via GameRendererMixin during rendering only — never touches MC's global FOV.
     */
    private int freelookFov = 90;

    /**
     * The perspective that was active before freelook switched to third-person.
     */
    private Perspective savedPerspective = null;

    private FreelookManager() {}

    // ── Public API ─────────────────────────────────────────────────

    /**
     * Enable freelook.  Captures the player's current look angles so the
     * transition is seamless — no sudden camera snap.
     */
    public void enable() {
        if (enabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = (client != null) ? client.player : null;
        if (player == null) return;

        cameraYaw   = player.getYaw();
        cameraPitch = player.getPitch();
        enabled     = true;

        // Switch to third-person so the player's body is visible during freelook.
        Perspective current = client.options.getPerspective();
        if (current == Perspective.FIRST_PERSON) {
            savedPerspective = current;
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
    }

    /**
     * Disable freelook.  The Camera mixin stops overriding rotation, so the
     * camera snaps back to the player entity's stored yaw/pitch on the next
     * render frame.
     */
    public void disable() {
        enabled = false;

        // Restore the perspective that was active before freelook was enabled.
        if (savedPerspective != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                client.options.setPerspective(savedPerspective);
            }
            savedPerspective = null;
        }
    }

    /** Toggles freelook on or off. */
    public void toggle() {
        if (enabled) disable();
        else         enable();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Accumulates a mouse-look delta into the freelook camera angles.
     *
     * <p>Called by {@code MouseMixin} with the already sensitivity-adjusted
     * {@code (dx, dy)} that Minecraft would normally pass straight to
     * {@code Entity.changeLookDirection}.  No additional sensitivity scaling
     * is required here.
     *
     * <p>Applies ROTATION_SPEED multiplier to slow down camera movement.
     * Inverts horizontal (yaw) rotation for intuitive left/right inversion.
     *
     * @param dx horizontal delta (positive = look right)
     * @param dy vertical delta   (positive = look down, Minecraft convention)
     */
    public void updateCamera(double dx, double dy) {
        // Match vanilla changeLookDirection sign convention:
        //   yaw += dx  (positive dx = mouse right = turn right = yaw increases)
        cameraYaw   += (float) dx * ROTATION_SPEED;
        // pitch += dy (positive dy = mouse up = pitch increases = look up)
        cameraPitch  = Math.max(-90f, Math.min(90f, cameraPitch + (float) dy * ROTATION_SPEED));
    }

    /** Returns the freelook camera yaw (degrees). */
    public float getCameraYaw()   { return cameraYaw;   }

    /** Returns the freelook camera pitch (degrees, clamped ±90). */
    public float getCameraPitch() { return cameraPitch; }

    /** Returns the freelook-specific FOV (degrees). */
    public int getFreelookFov() { return freelookFov; }

    /** Sets the freelook FOV (clamped 30–110). Does NOT touch MC's global FOV. */
    public void setFreelookFov(int fov) { this.freelookFov = Math.max(30, Math.min(110, fov)); }
}
