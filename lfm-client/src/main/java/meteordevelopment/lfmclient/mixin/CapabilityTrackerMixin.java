/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixin;

import com.mojang.blaze3d.opengl.GlStateManager;
import lfmdevelopment.lfmclient.mixininterface.ICapabilityTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GlStateManager.CapabilityTracker.class)
public abstract class CapabilityTrackerMixin implements ICapabilityTracker {
    @Shadow
    private boolean state;

    @Shadow
    public abstract void setState(boolean state);

    @Override
    public boolean lfm$get() {
        return state;
    }

    @Override
    public void lfm$set(boolean state) {
        setState(state);
    }
}
