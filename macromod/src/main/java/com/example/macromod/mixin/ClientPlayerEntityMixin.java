package com.example.macromod.mixin;

import com.example.macromod.MacroModClient;
import com.example.macromod.manager.MacroExecutor;
import com.example.macromod.model.MacroState;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin into ClientPlayerEntity to integrate with the macro movement system.
 * Allows the MovementHelper to apply movement vectors during macro execution.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    /**
     * Injected at the head of tickMovement() to allow the macro system
     * to override movement behavior when a macro is executing.
     * The actual movement simulation happens via KeyBinding.setPressed() in MovementHelper,
     * so this mixin primarily ensures the macro system's tick is synchronized with player movement.
     */
    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void macromod$onTickMovement(CallbackInfo ci) {
        MacroExecutor executor = MacroModClient.getExecutor();
        if (executor != null && executor.getState() == MacroState.MOVING) {
            // Movement is handled by MovementHelper via KeyBinding manipulation.
            // This injection point ensures we're in sync with the player's movement tick.
            // Additional movement logic can be added here if needed.
        }
    }
    
}
