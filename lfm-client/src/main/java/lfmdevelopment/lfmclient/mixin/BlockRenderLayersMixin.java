/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixin;

import lfmdevelopment.lfmclient.systems.modules.Modules;
import lfmdevelopment.lfmclient.systems.modules.world.Ambience;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.BlockRenderLayers;
import net.minecraft.fluid.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockRenderLayers.class)
public class BlockRenderLayersMixin {
    @Inject(method = "getFluidLayer", at = @At("HEAD"), cancellable = true)
    private static void onGetFluidLayer(FluidState state, CallbackInfoReturnable<BlockRenderLayer> cir) {
        if (Modules.get() == null) return;

        Ambience ambience = Modules.get().get(Ambience.class);
        int a = ambience.lavaColor.get().a;
        if (ambience.isActive() && ambience.customLavaColor.get() && a > 0 && a < 255) {
            cir.setReturnValue(BlockRenderLayer.TRANSLUCENT);
        }
    }
}
