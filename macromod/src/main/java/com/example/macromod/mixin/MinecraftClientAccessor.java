package com.example.macromod.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

    @Invoker("doAttack")
    boolean macromod$doAttack();

    @Invoker("doItemUse")
    void macromod$doItemUse();
}