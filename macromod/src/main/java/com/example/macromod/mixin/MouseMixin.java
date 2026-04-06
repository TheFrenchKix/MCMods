package com.example.macromod.mixin;

import com.example.macromod.manager.FreelookManager;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Intercepts the look-rotation update that lives inside
 * {@link Mouse#updateMouse(double)}.
 *
 * <p>Minecraft's normal flow is:
 * <pre>
 *   Mouse.updateMouse(tickDelta)
 *     → compute sensitivity-adjusted (dx, dy)
 *     → player.changeLookDirection(dx, dy)   ← we intercept here
 * </pre>
 *
 * When Freelook is active, the delta values are redirected into
 * {@link FreelookManager#updateCamera(double, double)} instead of being
 * applied to the player entity's yaw/pitch.  This means:
 * <ul>
 *   <li>The player's actual rotation (and therefore movement direction) is
 *       frozen while looking around freely.</li>
 *   <li>No modified rotation is ever sent to the server.</li>
 * </ul>
 */
@Mixin(Mouse.class)
public class MouseMixin {

    /**
     * Redirects the {@code ClientPlayerEntity.changeLookDirection(DD)} call
     * inside {@code Mouse.updateMouse}.
     *
     * <p>The (dx, dy) values arriving here are already multiplied by mouse
     * sensitivity and any smoothing Minecraft applies — no further scaling
     * is necessary.
     */
    @Redirect(
            method = "updateMouse",
            at = @At(
                    value  = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"
            )
    )
    private void freelook$redirectChangeLookDirection(
            ClientPlayerEntity player,
            double cursorDeltaX,
            double cursorDeltaY
    ) {
        FreelookManager flm = FreelookManager.getInstance();
        if (flm.isEnabled()) {
            // Feed the delta into the freelook camera; leave player rotation unchanged.
            flm.updateCamera(cursorDeltaX, cursorDeltaY);
        } else {
            // Freelook off → normal behaviour.
            player.changeLookDirection(cursorDeltaX, cursorDeltaY);
        }
    }
}
