package quatum.freelookneoforge.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import quatum.freelookneoforge.CameraLook;

@Mixin(value = MouseHandler.class)
public class MouseHandlerMixin {
    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void onLocalPlayerTurn(LocalPlayer player, double yRot, double xRot) {
        CameraLook.instance.onPlayerTurn(player, yRot, xRot);
    }
}
