/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixin;

import lfmdevelopment.lfmclient.utils.Utils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static lfmdevelopment.lfmclient.lfmClient.mc;

@Mixin(TridentItem.class)
public abstract class TridentItemMixin {
    @Inject(method = "onStoppedUsing", at = @At("HEAD"))
    private void onStoppedUsingHead(ItemStack stack, World world, LivingEntity user, int remainingUseTicks, CallbackInfoReturnable<Boolean> info) {
        if (user == mc.player) Utils.isReleasingTrident = true;
    }

    @Inject(method = "onStoppedUsing", at = @At("TAIL"))
    private void onStoppedUsingTail(ItemStack stack, World world, LivingEntity user, int remainingUseTicks, CallbackInfoReturnable<Boolean> info) {
        if (user == mc.player) Utils.isReleasingTrident = false;
    }
}
