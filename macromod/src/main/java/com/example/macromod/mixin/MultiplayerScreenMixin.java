package com.example.macromod.mixin;

import com.example.macromod.proxy.ProxyListScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects a "Proxy" button into the Multiplayer screen.
 */
@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {

    protected MultiplayerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void macromod$addProxyButton(CallbackInfo ci) {
        int btnW = 60;
        int btnH = 20;
        int btnX = this.width - btnW - 6;
        int btnY = 6;
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Proxy"),
            button -> {
                if (this.client != null) {
                    this.client.setScreen(new ProxyListScreen((Screen) (Object) this));
                }
            }
        ).dimensions(btnX, btnY, btnW, btnH).build());
    }
}
