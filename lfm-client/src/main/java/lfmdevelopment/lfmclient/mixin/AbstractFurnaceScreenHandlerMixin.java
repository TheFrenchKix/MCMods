/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixin;

import lfmdevelopment.lfmclient.mixininterface.IAbstractFurnaceScreenHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(AbstractFurnaceScreenHandler.class)
public abstract class AbstractFurnaceScreenHandlerMixin implements IAbstractFurnaceScreenHandler {
    @Shadow
    protected abstract boolean isSmeltable(ItemStack itemStack);

    @Override
    public boolean lfm$isItemSmeltable(ItemStack itemStack) {
        return isSmeltable(itemStack);
    }
}
