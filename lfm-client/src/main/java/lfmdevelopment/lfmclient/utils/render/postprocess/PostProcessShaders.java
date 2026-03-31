package lfmdevelopment.lfmclient.utils.render.postprocess;

import lfmdevelopment.lfmclient.lfmClient;
import lfmdevelopment.lfmclient.events.render.Render2DEvent;
import lfmdevelopment.lfmclient.utils.PreInit;
import meteordevelopment.orbit.EventHandler;

import static lfmdevelopment.lfmclient.lfmClient.mc;

public class PostProcessShaders {
    public static EntityShader ENTITY_OUTLINE;

    private PostProcessShaders() {}

    @PreInit
    public static void init() {
        ENTITY_OUTLINE = new EntityOutlineShader();

        lfmClient.EVENT_BUS.subscribe(PostProcessShaders.class);
    }

    public static void beginRender() {
        ENTITY_OUTLINE.clearTexture();
    }

    public static void submitEntityVertices() {
        ENTITY_OUTLINE.submitVertices();
    }

    @EventHandler
    private static void onRender(Render2DEvent event) {
        ENTITY_OUTLINE.render();
    }

    public static void onResized(int width, int height) {
        if (mc == null) return;

        ENTITY_OUTLINE.onResized(width, height);
    }
}

