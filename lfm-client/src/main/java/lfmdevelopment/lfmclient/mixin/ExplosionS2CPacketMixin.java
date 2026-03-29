/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixin;

import lfmdevelopment.lfmclient.mixininterface.IExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Optional;

@Mixin(ExplosionS2CPacket.class)
public abstract class ExplosionS2CPacketMixin implements IExplosionS2CPacket {
    @Shadow
    @Final
    @Mutable
    private Optional<Vec3d> playerKnockback;

    @Override
    public void lfm$setVelocityX(float velocity) {
        if (playerKnockback.isPresent()) {
            Vec3d kb = playerKnockback.get();
            playerKnockback = Optional.of(new Vec3d(velocity, kb.y, kb.z));
        } else {
            playerKnockback = Optional.of(new Vec3d(velocity, 0, 0));
        }
    }

    @Override
    public void lfm$setVelocityY(float velocity) {
        if (playerKnockback.isPresent()) {
            Vec3d kb = playerKnockback.get();
            playerKnockback = Optional.of(new Vec3d(kb.x, velocity, kb.z));
        } else {
            playerKnockback = Optional.of(new Vec3d(0, velocity, 0));
        }
    }

    @Override
    public void lfm$setVelocityZ(float velocity) {
        if (playerKnockback.isPresent()) {
            Vec3d kb = playerKnockback.get();
            playerKnockback = Optional.of(new Vec3d(kb.x, kb.y, velocity));
        } else {
            playerKnockback = Optional.of(new Vec3d(0, 0, velocity));
        }
    }
}
