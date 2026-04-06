package quatum.freelookneoforge.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quatum.freelookneoforge.CameraLook;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private float xRot;
    @Shadow
    private float yRot;
    @Shadow @Final
    private  Quaternionf rotation;
    @Shadow @Final
    private Vector3f forwards;
    @Shadow @Final
    private Vector3f up;
    @Shadow @Final
    private Vector3f left;
    @Shadow @Final
    private static Vector3f FORWARDS;
    @Shadow @Final
    private static Vector3f UP;
    @Shadow @Final
    private static Vector3f LEFT;

    @Shadow public abstract void reset();

    @Shadow private float roll;

    @Inject(method = "setup",at = @At("HEAD"))
    private void setUp(Level p_454891_, Entity p_90577_, boolean p_90578_, boolean p_90579_, float p_90580_, CallbackInfo ci){

    }

    @Inject(method = "setRotation(FFF)V",at = @At("HEAD"),cancellable = true)
    private void onSetup(float p_90573_, float p_90574_, float roll, CallbackInfo ci) {
        CameraType cameraType = Minecraft.getInstance().options.getCameraType();
        CameraLook controller = CameraLook.instance;
        if (CameraLook.instance.isLookActive() && controller.isCamaraMode(cameraType)) {
            p_90574_ = (float) controller.getXRot();
            p_90573_=(float) controller.getYRot();
            this.xRot = p_90574_;
            this.yRot = p_90573_;
            this.roll = roll;
            this.rotation.rotationYXZ((float) Math.PI - p_90573_ * (float) (Math.PI / 180.0), -p_90574_ * (float) (Math.PI / 180.0), -roll * (float) (Math.PI / 180.0));
            this.FORWARDS.rotate(this.rotation, this.forwards);
            UP.rotate(this.rotation, this.up);
            LEFT.rotate(this.rotation, this.left);
            ci.cancel();
        }
        if(CameraLook.instance.isLookActive() && !controller.isCamaraMode(cameraType)&& !CameraLook.instance.shouldSync(cameraType)){
            ci.cancel(); return;}
    }
}
