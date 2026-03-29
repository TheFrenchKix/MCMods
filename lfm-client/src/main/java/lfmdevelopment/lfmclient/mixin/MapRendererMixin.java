/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import lfmdevelopment.lfmclient.systems.modules.Modules;
import lfmdevelopment.lfmclient.systems.modules.render.NoRender;
import net.minecraft.client.render.MapRenderState;
import net.minecraft.client.render.MapRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.map.MapDecoration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(MapRenderer.class)
public abstract class MapRendererMixin {
    @ModifyExpressionValue(method = "draw", at = @At(value = "FIELD", target = "Lnet/minecraft/client/render/MapRenderState;decorations:Ljava/util/List;"))
    private List<MapDecoration> getIconsProxy(List<MapDecoration> original) {
        return (Modules.get().get(NoRender.class).noMapMarkers()) ? List.of() : original;
    }

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void onDraw(MapRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, boolean skipRenderingDecorations, int light, CallbackInfo ci) {
        if (Modules.get().get(NoRender.class).noMapContents()) ci.cancel();
    }
}
