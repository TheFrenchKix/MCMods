package lfmdevelopment.lfmclient.utils.render.postprocess;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import lfmdevelopment.lfmclient.mixininterface.IWorldRenderer;
import lfmdevelopment.lfmclient.utils.render.CustomOutlineVertexConsumerProvider;
import net.minecraft.entity.Entity;

import static lfmdevelopment.lfmclient.lfmClient.mc;

public abstract class EntityShader extends PostProcessShader {
    public final CustomOutlineVertexConsumerProvider vertexConsumerProvider;

    protected EntityShader(RenderPipeline pipeline) {
        super(pipeline);
        this.vertexConsumerProvider = new CustomOutlineVertexConsumerProvider();
    }

    public abstract boolean shouldDraw(Entity entity);

    @Override
    protected void preDraw() {
        ((IWorldRenderer) mc.worldRenderer).lfm$pushEntityOutlineFramebuffer(framebuffer);
    }

    @Override
    protected void postDraw() {
        ((IWorldRenderer) mc.worldRenderer).lfm$popEntityOutlineFramebuffer();
    }

    public void submitVertices() {
        submitVertices(vertexConsumerProvider::draw);
    }
}
