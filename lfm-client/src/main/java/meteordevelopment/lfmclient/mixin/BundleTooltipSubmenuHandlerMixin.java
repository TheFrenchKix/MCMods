/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import lfmdevelopment.lfmclient.systems.modules.Modules;
import lfmdevelopment.lfmclient.systems.modules.misc.InventoryTweaks;
import net.minecraft.client.gui.tooltip.BundleTooltipSubmenuHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BundleContentsComponent;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BundleTooltipSubmenuHandler.class)
public class BundleTooltipSubmenuHandlerMixin {
    @ModifyExpressionValue(method = "sendPacket", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/BundleItem;getNumberOfStacksShown(Lnet/minecraft/item/ItemStack;)I"))
    private int uncapBundleScrolling1(int original, ItemStack item, int slotId, int selectedItemIndex) {
        if (Modules.get().get(InventoryTweaks.class).uncapBundleScrolling()) return item.getOrDefault(DataComponentTypes.BUNDLE_CONTENTS, BundleContentsComponent.DEFAULT).size();
        return original;
    }

    @ModifyExpressionValue(method = "onScroll", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/BundleItem;getNumberOfStacksShown(Lnet/minecraft/item/ItemStack;)I"))
    private int uncapBundleScrolling2(int original, double horizontal, double vertical, int slotId, ItemStack item) {
        if (Modules.get().get(InventoryTweaks.class).uncapBundleScrolling()) return item.getOrDefault(DataComponentTypes.BUNDLE_CONTENTS, BundleContentsComponent.DEFAULT).size();
        return original;
    }
}
