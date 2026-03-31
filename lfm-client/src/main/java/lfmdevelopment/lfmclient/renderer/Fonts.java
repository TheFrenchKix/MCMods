/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.renderer;

import lfmdevelopment.lfmclient.lfmClient;
import lfmdevelopment.lfmclient.events.lfm.CustomFontChangedEvent;
import lfmdevelopment.lfmclient.gui.WidgetScreen;
import lfmdevelopment.lfmclient.renderer.text.CustomTextRenderer;
import lfmdevelopment.lfmclient.renderer.text.FontFace;
import lfmdevelopment.lfmclient.renderer.text.FontFamily;
import lfmdevelopment.lfmclient.renderer.text.FontInfo;
import lfmdevelopment.lfmclient.systems.config.Config;
import lfmdevelopment.lfmclient.utils.PreInit;
import lfmdevelopment.lfmclient.utils.render.FontUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static lfmdevelopment.lfmclient.lfmClient.mc;

public class Fonts {
    public static final String[] BUILTIN_FONTS = { "JetBrains Mono", "Comfortaa", "Tw Cen MT", "Pixelation" };

    public static String DEFAULT_FONT_FAMILY;
    public static FontFace DEFAULT_FONT;

    public static final List<FontFamily> FONT_FAMILIES = new ArrayList<>();
    public static CustomTextRenderer RENDERER;

    private Fonts() {
    }

    @PreInit
    public static void refresh() {
        FONT_FAMILIES.clear();

        for (String builtinFont : BUILTIN_FONTS) {
            FontUtils.loadBuiltin(FONT_FAMILIES, builtinFont);
        }

        for (String fontPath : FontUtils.getSearchPaths()) {
            FontUtils.loadSystem(FONT_FAMILIES, new File(fontPath));
        }

        FONT_FAMILIES.sort(Comparator.comparing(FontFamily::getName));

        lfmClient.LOG.info("Found {} font families.", FONT_FAMILIES.size());

        DEFAULT_FONT = findDefaultFont();
        DEFAULT_FONT_FAMILY = DEFAULT_FONT.info.family();

        Config config = Config.get();
        load(config != null ? config.font.get() : DEFAULT_FONT);
    }

    private static FontFace findDefaultFont() {
        FontInfo preferredInfo = FontUtils.getBuiltinFontInfo(BUILTIN_FONTS[1]);
        if (preferredInfo != null) {
            FontFamily preferredFamily = getFamily(preferredInfo.family());
            FontFace preferredFont = preferredFamily != null ? preferredFamily.get(FontInfo.Type.Regular) : null;
            if (preferredFont != null) return preferredFont;
        }

        for (FontFamily family : FONT_FAMILIES) {
            FontFace regular = family.get(FontInfo.Type.Regular);
            if (regular != null) return regular;

            FontFace bold = family.get(FontInfo.Type.Bold);
            if (bold != null) return bold;

            FontFace italic = family.get(FontInfo.Type.Italic);
            if (italic != null) return italic;

            FontFace boldItalic = family.get(FontInfo.Type.BoldItalic);
            if (boldItalic != null) return boldItalic;
        }

        throw new IllegalStateException("No fonts could be loaded.");
    }

    public static void load(FontFace fontFace) {
        if (RENDERER != null) {
            if (RENDERER.fontFace.equals(fontFace)) return;
            else RENDERER.destroy();
        }

        try {
            RENDERER = new CustomTextRenderer(fontFace);
            lfmClient.EVENT_BUS.post(CustomFontChangedEvent.get());
        }
        catch (Exception e) {
            if (fontFace.equals(DEFAULT_FONT)) {
                throw new RuntimeException("Failed to load default font: " + fontFace, e);
            }

            lfmClient.LOG.error("Failed to load font: {}", fontFace, e);
            load(Fonts.DEFAULT_FONT);
        }

        if (mc.currentScreen instanceof WidgetScreen && Config.get().customFont.get()) {
            ((WidgetScreen) mc.currentScreen).invalidate();
        }
    }

    public static FontFamily getFamily(String name) {
        for (FontFamily fontFamily : Fonts.FONT_FAMILIES) {
            if (fontFamily.getName().equalsIgnoreCase(name)) {
                return fontFamily;
            }
        }

        return null;
    }
}
