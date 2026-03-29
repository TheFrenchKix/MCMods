/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import lfmdevelopment.lfmclient.systems.modules.Modules;
import lfmdevelopment.lfmclient.systems.modules.render.BetterTooltips;
import net.minecraft.item.ItemGroups;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemGroups.class)
public abstract class ItemGroupsMixin {
    @ModifyReturnValue(method = "updateDisplayContext", at = @At("RETURN"))
    private static boolean modifyReturn(boolean original) {
        return original || Modules.get().get(BetterTooltips.class).updateTooltips();
    }
}
