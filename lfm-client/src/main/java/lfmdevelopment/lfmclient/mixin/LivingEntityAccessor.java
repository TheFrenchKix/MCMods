/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixin;

import net.minecraft.entity.LivingEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.registry.tag.TagKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Invoker("swimUpward")
    void lfm$swimUpwards(TagKey<Fluid> fluid);

    @Accessor("jumping")
    boolean lfm$isJumping();

    @Accessor("jumpingCooldown")
    int lfm$getJumpCooldown();

    @Accessor("jumpingCooldown")
    void lfm$setJumpCooldown(int cooldown);
}
