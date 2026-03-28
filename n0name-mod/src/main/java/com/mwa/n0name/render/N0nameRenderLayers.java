package com.mwa.n0name.render;

import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;

public final class N0nameRenderLayers {

    /**
     * ESP lines — renders through walls (no depth test), fogless, line width 2.
     */
    public static final RenderLayer ESP_LINES =
        RenderLayer.of("n0name:esp_lines",
            RenderSetup.builder(N0nameRenderPipelines.ESP_LINES)
                .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .build());

    /**
     * ESP filled quads — renders through walls (no depth test), translucent.
     */
    public static final RenderLayer ESP_QUADS =
        RenderLayer.of("n0name:esp_quads",
            RenderSetup.builder(N0nameRenderPipelines.ESP_QUADS)
                .translucent()
                .build());

    /**
     * Normal lines with depth test — for path rendering that respects depth.
     */
    public static final RenderLayer LINES =
        RenderLayer.of("n0name:lines",
            RenderSetup.builder(N0nameRenderPipelines.DEPTH_TEST_LINES)
                .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .build());

    private N0nameRenderLayers() {}
}
