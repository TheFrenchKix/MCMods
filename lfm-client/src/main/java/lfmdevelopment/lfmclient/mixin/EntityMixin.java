/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import lfmdevelopment.lfmclient.lfmClient;
import lfmdevelopment.lfmclient.events.entity.EntityMoveEvent;
import lfmdevelopment.lfmclient.events.entity.player.JumpVelocityMultiplierEvent;
import lfmdevelopment.lfmclient.events.entity.player.PlayerMoveEvent;
import lfmdevelopment.lfmclient.systems.modules.Modules;
import lfmdevelopment.lfmclient.systems.modules.movement.*;
import lfmdevelopment.lfmclient.systems.modules.render.ESP;
import lfmdevelopment.lfmclient.systems.modules.render.FreeLook;
import lfmdevelopment.lfmclient.systems.modules.render.Freecam;
import lfmdevelopment.lfmclient.systems.modules.render.NoRender;
import lfmdevelopment.lfmclient.utils.Utils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import static lfmdevelopment.lfmclient.lfmClient.mc;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "isTouchingWater", at = @At(value = "HEAD"), cancellable = true)
    private void isTouchingWater(CallbackInfoReturnable<Boolean> info) {
        if ((Object) this != mc.player) return;

        if (Modules.get().get(NoSlow.class).fluidDrag()) info.setReturnValue(false);
    }

    @Inject(method = "isInLava", at = @At(value = "HEAD"), cancellable = true)
    private void isInLava(CallbackInfoReturnable<Boolean> info) {
        if ((Object) this != mc.player) return;

        if (Modules.get().get(NoSlow.class).fluidDrag()) info.setReturnValue(false);
    }

    @ModifyExpressionValue(method = "updateSwimming", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;isSubmergedInWater()Z"))
    private boolean isSubmergedInWater(boolean submerged) {
        if ((Object) this != mc.player) return submerged;

        if (Modules.get().get(NoSlow.class).fluidDrag()) return false;
        return submerged;
    }

    @ModifyArgs(method = "pushAwayFrom(Lnet/minecraft/entity/Entity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;addVelocity(DDD)V"))
    private void onPushAwayFrom(Args args, Entity entity) {
    }

    @ModifyReturnValue(method = "getJumpVelocityMultiplier", at = @At("RETURN"))
    private float onGetJumpVelocityMultiplier(float original) {
        if ((Object) this == mc.player) {
            JumpVelocityMultiplierEvent event = lfmClient.EVENT_BUS.post(JumpVelocityMultiplierEvent.get());
            return (original * event.multiplier);
        }

        return original;
    }

    @Inject(method = "move", at = @At("HEAD"))
    private void onMove(MovementType type, Vec3d movement, CallbackInfo info) {
        if ((Object) this == mc.player) {
            lfmClient.EVENT_BUS.post(PlayerMoveEvent.get(type, movement));
        }
        else {
            lfmClient.EVENT_BUS.post(EntityMoveEvent.get((Entity) (Object) this, movement));
        }
    }

    @ModifyExpressionValue(method = "getVelocityMultiplier", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;getBlock()Lnet/minecraft/block/Block;"))
    private Block modifyVelocityMultiplierBlock(Block original) {
        if ((Object) this != mc.player) return original;

        if (original == Blocks.SOUL_SAND && Modules.get().get(NoSlow.class).soulSand()) return Blocks.STONE;
        if (original == Blocks.HONEY_BLOCK && Modules.get().get(NoSlow.class).honeyBlock()) return Blocks.STONE;
        return original;
    }

    @ModifyReturnValue(method = "isInvisibleTo(Lnet/minecraft/entity/player/PlayerEntity;)Z", at = @At("RETURN"))
    private boolean isInvisibleToCanceller(boolean original) {
        if (!Utils.canUpdate()) return original;
        ESP esp = Modules.get().get(ESP.class);
        if (Modules.get().get(NoRender.class).noInvisibility() || esp.isActive() && !esp.shouldSkip((Entity) (Object) this)) return false;
        return original;
    }

    @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
    private void isGlowing(CallbackInfoReturnable<Boolean> info) {
        if (Modules.get().get(NoRender.class).noGlowing()) info.setReturnValue(false);
    }

    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true)
    private void onIsInvisibleTo(PlayerEntity player, CallbackInfoReturnable<Boolean> info) {
        if (player == null) info.setReturnValue(false);
    }

    @ModifyReturnValue(method = "getPose", at = @At("RETURN"))
    private EntityPose modifyGetPose(EntityPose original) {
        if ((Object) this != mc.player) return original;

        if (original == EntityPose.CROUCHING && !mc.player.isSneaking() && ((PlayerEntityAccessor) mc.player).lfm$canChangeIntoPose(EntityPose.STANDING)) return EntityPose.STANDING;
        return original;
    }

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void updateChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this != mc.player) return;

        Freecam freecam = Modules.get().get(Freecam.class);
        FreeLook freeLook = Modules.get().get(FreeLook.class);

        if (freecam.isActive()) {
            freecam.changeLookDirection(cursorDeltaX * 0.15, cursorDeltaY * 0.15);
            ci.cancel();
        }
        else if (freeLook.cameraMode()) {
            freeLook.cameraYaw += (float) (cursorDeltaX / freeLook.sensitivity.get().floatValue());
            freeLook.cameraPitch += (float) (cursorDeltaY / freeLook.sensitivity.get().floatValue());

            if (Math.abs(freeLook.cameraPitch) > 90.0F) freeLook.cameraPitch = freeLook.cameraPitch > 0.0F ? 90.0F : -90.0F;
            ci.cancel();
        }
    }
}
