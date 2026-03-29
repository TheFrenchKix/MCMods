/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import lfmdevelopment.lfmclient.mixininterface.IEntityRenderState;
import lfmdevelopment.lfmclient.systems.modules.Modules;
import lfmdevelopment.lfmclient.systems.modules.render.Freecam;
import lfmdevelopment.lfmclient.systems.modules.render.NoRender;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.scoreboard.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static lfmdevelopment.lfmclient.lfmClient.mc;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> {
    // Freecam

    @ModifyExpressionValue(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;getCameraEntity()Lnet/minecraft/entity/Entity;"))
    private Entity hasLabelGetCameraEntityProxy(Entity cameraEntity) {
        return Modules.get().isActive(Freecam.class) ? null : cameraEntity;
    }

    // Player model rendering in main menu

    @ModifyExpressionValue(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getScoreboardTeam()Lnet/minecraft/scoreboard/Team;"))
    private Team hasLabelClientPlayerEntityGetScoreboardTeamProxy(Team team) {
        return (mc.player == null) ? null : team;
    }

    // NoRender dead entities

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void render$Head(S state, net.minecraft.client.util.math.MatrixStack matrixStack, net.minecraft.client.render.command.OrderedRenderCommandQueue orderedRenderCommandQueue, net.minecraft.client.render.state.CameraRenderState arg, CallbackInfo ci) {
        Entity entity = ((IEntityRenderState) state).lfm$getEntity();
        if (!(entity instanceof LivingEntity livingEntity)) return;

        if (Modules.get().get(NoRender.class).noDeadEntities() && livingEntity.isDead()) ci.cancel();
    }
}
