package com.example.macromod.mixin;

import com.example.macromod.manager.FreelookManager;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overrides camera rotation AND position at the end of {@link Camera#update}.
 *
 * <h3>Why we override position</h3>
 * <p>Vanilla {@code Camera.update} calls:
 * <ol>
 *   <li>{@code setRotation(entityYaw, entityPitch)} — aligns camera with entity look</li>
 *   <li>{@code setPos(lerpedEyePos)} — places camera at entity eye level</li>
 *   <li>{@code moveBy(-distance, 0, 0)} — pulls camera backward in entity-look direction</li>
 * </ol>
 * <p>When freelook is active the camera may face a completely different direction than
 * the entity, so vanilla's step 3 positions the camera behind the entity's actual look
 * direction — not behind the freelook direction.  We override all three steps at TAIL
 * so the camera orbits the player based on freelook angles.
 *
 * <h3>Why moveBy uses the right direction</h3>
 * <p>{@code Camera.moveBy(d, e, f)} internally builds a {@code Vector3f(f, e, -d)},
 * rotates it by the camera's current rotation quaternion, then adds it to the
 * camera position.  Because we call {@code setRotation} with our freelook angles
 * first (which updates the quaternion), the subsequent {@code moveBy(-dist, 0, 0)}
 * naturally pulls the camera 2.5 blocks backward in the freelook look direction.
 */
@Mixin(Camera.class)
public class CameraUpdateMixin {

    /** How many blocks to pull the camera back from the entity's eye position. */
    private static final float FREELOOK_BACK_DISTANCE = 2.5f;

    /**
     * Upward offset (in blocks) applied to the entity eye position before pulling
     * the camera back.  Raises the orbit point slightly above the head so the
     * crosshair sits near shoulder/head level rather than the neck.
     */
    private static final float FREELOOK_UP_OFFSET = 0.5f;

    @Shadow protected void setRotation(float yaw, float pitch) {}
    @Shadow protected void setPos(double x, double y, double z) {}
    @Shadow protected void moveBy(float d, float e, float f) {}

    @Inject(method = "update", at = @At("TAIL"))
    private void freelook$overrideCamera(
            World world,
            Entity entity,
            boolean thirdPerson,
            boolean frontView,
            float tickDelta,
            CallbackInfo ci
    ) {
        FreelookManager flm = FreelookManager.getInstance();
        if (!flm.isEnabled()) return;

        // Step 1: Set camera rotation to freelook angles.
        // This also updates the internal quaternion used by moveBy.
        setRotation(flm.getCameraYaw(), flm.getCameraPitch());

        // Step 2: Reset camera position to the entity eye + slight upward offset,
        // discarding vanilla's already-moved-back position.
        // getCameraPosVec interpolates the entity's camera (eye) position smoothly.
        Vec3d eye = entity.getCameraPosVec(tickDelta);
        setPos(eye.x, eye.y + FREELOOK_UP_OFFSET, eye.z);

        // Step 3: Pull the camera backward by FREELOOK_BACK_DISTANCE blocks.
        // Because we called setRotation first, moveBy uses the freelook quaternion,
        // so the camera orbits in the freelook direction (not the entity direction).
        moveBy(-FREELOOK_BACK_DISTANCE, 0, 0);
    }
}