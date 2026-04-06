package com.example.macromod.mixin;

import com.example.macromod.manager.FreelookManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides the FOV when freelook is active so that freelook uses its own
 * dedicated FOV without touching Minecraft's global FOV setting.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void freelook$overrideFov(Camera camera, float tickDelta, boolean changingFov,
                                      CallbackInfoReturnable<Float> cir) {
        FreelookManager flm = FreelookManager.getInstance();
        if (flm.isEnabled()) {
            cir.setReturnValue((float) flm.getFreelookFov());
        }
    }
}
