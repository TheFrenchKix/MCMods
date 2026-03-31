package lfmdevelopment.lfmclient.utils.render;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import lfmdevelopment.lfmclient.lfmClient;
import lfmdevelopment.lfmclient.renderer.Texture;
import lfmdevelopment.lfmclient.utils.network.Http;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static lfmdevelopment.lfmclient.lfmClient.mc;

public class PlayerHeadTexture extends Texture {
    private static final int HEAD_SIZE = 8;

    private boolean needsRotate;

    public PlayerHeadTexture(String url) {
        super(HEAD_SIZE, HEAD_SIZE, TextureFormat.RGBA8, FilterMode.NEAREST, FilterMode.NEAREST);

        BufferedImage skin;
        try {
            skin = ImageIO.read(Http.get(url).sendInputStream());
        } catch (IOException e) {
            lfmClient.LOG.warn("Failed to load player skin from URL: {}", url, e);
            uploadFallback();
            return;
        }

        if (skin == null) {
            lfmClient.LOG.warn("Received empty player skin image from URL: {}", url);
            uploadFallback();
            return;
        }

        byte[] head = new byte[HEAD_SIZE * HEAD_SIZE * 4];
        int[] pixel = new int[4];

        int i = 0;
        for (int x = 8; x < 16; x++) {
            for (int y = 8; y < 16; y++) {
                skin.getData().getPixel(x, y, pixel);

                for (int j = 0; j < 4; j++) {
                    head[i] = (byte) pixel[j];
                    i++;
                }
            }
        }

        i = 0;
        for (int x = 40; x < 48; x++) {
            for (int y = 8; y < 16; y++) {
                skin.getData().getPixel(x, y, pixel);

                if (pixel[3] != 0) {
                    for (int j = 0; j < 4; j++) {
                        head[i] = (byte) pixel[j];
                        i++;
                    }
                }
                else i += 4;
            }
        }

        ByteBuffer headBuffer = BufferUtils.createByteBuffer(head.length);
        headBuffer.put(head);
        headBuffer.rewind();
        upload(headBuffer);

        needsRotate = true;
    }

    public PlayerHeadTexture() {
        super(HEAD_SIZE, HEAD_SIZE, TextureFormat.RGBA8, FilterMode.NEAREST, FilterMode.NEAREST);

        var steve = mc.getResourceManager().getResource(lfmClient.identifier("textures/steve.png"));
        if (steve.isEmpty()) {
            lfmClient.LOG.warn("Missing player head fallback texture: {}", lfmClient.identifier("textures/steve.png"));
            uploadFallback();
            return;
        }

        try (InputStream inputStream = steve.get().getInputStream()) {
            ByteBuffer data = TextureUtil.readResource(inputStream);
            data.rewind();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                IntBuffer comp = stack.mallocInt(1);

                ByteBuffer image = STBImage.stbi_load_from_memory(data, width, height, comp, 4);
                if (image == null) {
                    lfmClient.LOG.warn("Failed to decode player head fallback texture.");
                    uploadFallback();
                    return;
                }

                upload(image);
                STBImage.stbi_image_free(image);
            }
            MemoryUtil.memFree(data);
        }
        catch (IOException e) {
            lfmClient.LOG.warn("Failed to load player head fallback texture.", e);
            uploadFallback();
        }
    }

    private void uploadFallback() {
        byte[] rgba = new byte[HEAD_SIZE * HEAD_SIZE * 4];

        for (int y = 0; y < HEAD_SIZE; y++) {
            for (int x = 0; x < HEAD_SIZE; x++) {
                int i = (y * HEAD_SIZE + x) * 4;
                boolean dark = ((x + y) & 1) == 0;

                rgba[i] = (byte) (dark ? 70 : 140);
                rgba[i + 1] = (byte) (dark ? 70 : 140);
                rgba[i + 2] = (byte) (dark ? 70 : 140);
                rgba[i + 3] = (byte) 255;
            }
        }

        ByteBuffer buffer = BufferUtils.createByteBuffer(rgba.length);
        buffer.put(rgba);
        buffer.rewind();
        upload(buffer);
    }

    public boolean needsRotate() {
        return needsRotate;
    }
}
