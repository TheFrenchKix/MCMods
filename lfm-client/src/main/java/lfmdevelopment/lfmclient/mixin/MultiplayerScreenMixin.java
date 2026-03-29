/*
 * This file is part of the lfm Client distribution (https://github.com/lfmDevelopment/lfm-client).
 * Copyright (c) lfm Development.
 */

package lfmdevelopment.lfmclient.mixin;

import lfmdevelopment.lfmclient.gui.GuiThemes;
import lfmdevelopment.lfmclient.systems.modules.Modules;
import lfmdevelopment.lfmclient.systems.modules.player.NameProtect;
import lfmdevelopment.lfmclient.systems.proxies.Proxies;
import lfmdevelopment.lfmclient.systems.proxies.Proxy;
import lfmdevelopment.lfmclient.utils.render.color.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static lfmdevelopment.lfmclient.lfmClient.mc;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {
    @Unique
    private int textColor1;
    @Unique
    private int textColor2;

    @Unique
    private String loggedInAs;
    @Unique
    private int loggedInAsLength;

    @Unique
    private ButtonWidget accounts;

    @Unique
    private ButtonWidget proxies;

    public MultiplayerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "refreshWidgetPositions", at = @At("TAIL"))
    private void onInit(CallbackInfo info) {
        textColor1 = Color.fromRGBA(255, 255, 255, 255);
        textColor2 = Color.fromRGBA(175, 175, 175, 255);

        loggedInAs = "Logged in as ";
        loggedInAsLength = textRenderer.getWidth(loggedInAs);

        if (accounts == null) {
            accounts = addDrawableChild(
                new ButtonWidget.Builder(Text.literal("Accounts"), button -> client.setScreen(GuiThemes.get().accountsScreen()))
                    .size(75, 20)
                    .build()
            );
        }
        accounts.setPosition(this.width - 75 - 3, 3);

        if (proxies == null) {
            proxies = addDrawableChild(
                    new ButtonWidget.Builder(Text.literal("Proxies"), button -> client.setScreen(GuiThemes.get().proxiesScreen()))
                        .size(75, 20)
                        .build()
                );
        }
        proxies.setPosition(this.width - 75 - 3 - 75 - 2, 3);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);

        int x = 3;
        int y = 3;

        // Logged in as
        context.drawTextWithShadow(mc.textRenderer, loggedInAs, x, y, textColor1);
        context.drawTextWithShadow(mc.textRenderer, Modules.get().get(NameProtect.class).getName(client.getSession().getUsername()), x + loggedInAsLength, y, textColor2);

        y += textRenderer.fontHeight + 2;

        // Proxy
        Proxy proxy = Proxies.get().getEnabled();

        String left = proxy != null ? "Using proxy " : "Not using a proxy";
        String right = proxy != null ? (proxy.name.get() != null && !proxy.name.get().isEmpty() ? "(" + proxy.name.get() + ") " : "") + proxy.address.get() + ":" + proxy.port.get() : null;

        context.drawTextWithShadow(mc.textRenderer, left, x, y, textColor1);
        if (right != null)
            context.drawTextWithShadow(mc.textRenderer, right, x + textRenderer.getWidth(left), y, textColor2);
    }
}
