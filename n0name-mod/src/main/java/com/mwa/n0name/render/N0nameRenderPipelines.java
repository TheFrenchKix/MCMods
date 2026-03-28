package com.mwa.n0name.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

public final class N0nameRenderPipelines {

    /**
     * Lines snippet without fog, using POSITION_COLOR_NORMAL_LINE_WIDTH format.
     */
    public static final Snippet FOGLESS_LINES_SNIPPET = RenderPipeline
        .builder(RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET,
            RenderPipelines.GLOBALS_SNIPPET)
        .withVertexShader(Identifier.of("n0name-mod", "core/fogless_lines"))
        .withFragmentShader(Identifier.of("n0name-mod", "core/fogless_lines"))
        .withBlend(BlendFunction.TRANSLUCENT)
        .withCull(false)
        .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH,
            DrawMode.LINES)
        .buildSnippet();

    /**
     * Lines with depth test (for path rendering behind walls).
     */
    public static final RenderPipeline DEPTH_TEST_LINES =
        RenderPipelines.register(RenderPipeline.builder(FOGLESS_LINES_SNIPPET)
            .withLocation(Identifier.of("n0name-mod", "pipeline/depth_test_lines"))
            .build());

    /**
     * Lines WITHOUT depth test — renders through walls for ESP.
     */
    public static final RenderPipeline ESP_LINES =
        RenderPipelines.register(RenderPipeline.builder(FOGLESS_LINES_SNIPPET)
            .withLocation(Identifier.of("n0name-mod", "pipeline/esp_lines"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build());

    /**
     * Filled quads WITHOUT depth test — renders through walls for ESP boxes.
     */
    public static final RenderPipeline ESP_QUADS =
        RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                .withLocation(Identifier.of("n0name-mod", "pipeline/esp_quads"))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .build());

    private N0nameRenderPipelines() {}
}
