/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.SectionedEntityCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SectionedEntityCache.class)
public interface SectionedEntityCacheAccessor {
    @Accessor("trackedPositions")
    LongSortedSet lfm$getTrackedPositions();

    @Accessor("trackingSections")
    <T extends EntityLike> Long2ObjectMap<EntityTrackingSection<T>> lfm$getTrackingSections();
}
