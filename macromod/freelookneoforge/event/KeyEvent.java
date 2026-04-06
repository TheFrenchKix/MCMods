package quatum.freelookneoforge.event;


import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import quatum.freelookneoforge.CameraLook;
import quatum.freelookneoforge.FreeLookneoForge;
import quatum.freelookneoforge.KeyBinding.FreeLookKey;


public class KeyEvent {
    @EventBusSubscriber(modid = FreeLookneoForge.MODID,value = Dist.CLIENT)
    public static class ClientModBusEvent{
        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event){
            event.register(FreeLookKey.FREELOOK_KAY);
            event.register(FreeLookKey.PERSPECTIVESWAP_KAY);
        }
    }
    @EventBusSubscriber(modid = FreeLookneoForge.MODID,value = Dist.CLIENT)
    public static class ClientForgeEvents{
        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onInput(InputEvent.Key event) {KeyEvent.onInput();}
        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void  onMouseInput(InputEvent.MouseButton.Pre event) {KeyEvent.onInput();}
    }

    public static void onInput(){
        CameraLook cameraLook = CameraLook.instance;
        cameraLook.updateKeys();
        cameraLook.SwapPerspective();
        cameraLook.doSyndIfWanted();
        cameraLook.SwapPerspectiveBack();
        cameraLook.SwitchPerspectiveBack();

    }
}
