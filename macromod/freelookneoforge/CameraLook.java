package quatum.freelookneoforge;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import quatum.freelookneoforge.KeyBinding.FreeLookKey;

public class CameraLook {
    public static final CameraLook instance = new CameraLook();

    float xRot, yRot;
    boolean firstLook = true;
    boolean LookActive = false;
    boolean SwapActive = false;
    boolean SyncUse=true;
    boolean SyncPickItem=true;
    boolean SyncAttack=true;
    boolean last=false;
    boolean SwapBack=false;
    boolean firstSwap = true;
    CameraType lastCameraType= CameraType.FIRST_PERSON;
    CameraType lastCameraSwap= CameraType.FIRST_PERSON;
    public void setSwapActive(boolean value){this.SwapActive=value;}
    public boolean getSwapActive(){return this.SwapActive;}

    public void setSyncUse(boolean value){this.SyncUse = value;}
    public void setSyncPickItem(boolean value){this.SyncPickItem = value;}
    public void setSyncAttack(boolean value){this.SyncAttack = value;}
    public boolean shouldSync(CameraType cameraType){
        if(((this.SyncUse && Config.RotateKeyUseValue)||(this.SyncPickItem&&Config.RotatePickItemValue)||(this.SyncAttack&&Config.RotateAttackValue)) && this.LookActive){
            if (cameraType == CameraType.FIRST_PERSON)
                return Config.RotateFirstPersonValue;
            if (cameraType == CameraType.THIRD_PERSON_FRONT)
                return Config.RotateThirdPersonFrontValue;
            if (cameraType ==CameraType.THIRD_PERSON_BACK)
                return Config.RotateThirdPersonBackValue;
        }
        return false;
    }
    public void doSyndIfWanted(){
        CameraType type =Minecraft.getInstance().options.getCameraType();
        if(this.shouldSync(type)&&this.isCamaraMode(type)){
            LocalPlayer LP = Minecraft.getInstance().player;
            if(Minecraft.getInstance().options.getCameraType() == CameraType.THIRD_PERSON_FRONT){
                LP.setXRot(this.convertToAngle(this.getXRot())*-1);
                LP.setYRot(this.getYRot()-180);
            }else {
                LP.setXRot(this.convertToAngle(this.getXRot()));
                LP.setYRot(this.getYRot());
            }
        }
    }
    public void updateKeys(){
        this.setSwapActive(FreeLookKey.PERSPECTIVESWAP_KAY.isDown());
        this.setLookActive(FreeLookKey.FREELOOK_KAY.isDown());
        this.setSyncAttack(Minecraft.getInstance().options.keyAttack.isDown());
        this.setSyncPickItem(Minecraft.getInstance().options.keyPickItem.isDown());
        this.setSyncUse(Minecraft.getInstance().options.keyUse.isDown());

    }
    public boolean isCamaraMode(CameraType cameraType ){
        if(cameraType == CameraType.FIRST_PERSON){
            if(Config.ModeFirstPersonValue==0){
                return true;
            }
        }
        if(cameraType == CameraType.THIRD_PERSON_FRONT){
            if(Config.ModeThirdPersonFrontValue==0){
                return true;
            }
        }
        if(cameraType == CameraType.THIRD_PERSON_BACK){
            if(Config.ModeThirdPersonBackValue==0){
                return true;
            }
        }
        return false;
    }
    public void setLookActive(boolean vale){this.LookActive = vale;}
    public boolean isLookActive(){return this.LookActive;}
    public float getYRot(){return this.yRot;}
    public float getXRot(){return this.xRot;}

    public void onPlayerTurn(LocalPlayer player, double XRot, double YRot){
        CameraType cameraType = Minecraft.getInstance().options.getCameraType();
        if(this.LookActive && this.isCamaraMode(cameraType)) {
            if(this.firstLook){
                cameraType = this.SwitchPerspective(cameraType);
                if(cameraType == CameraType.THIRD_PERSON_FRONT) {
                    this.yRot = player.getYRot() - 180;
                    this.xRot = player.getXRot()*-1;
                }
                else{
                    this.yRot= player.getYRot();
                    this.xRot = player.getXRot();
                }
                this.firstLook = false;
                this.last = true;
            }
            this.yRot += (float) XRot * 0.15F;
            this.xRot += (float) YRot * 0.15F;
            if(!Config.UpSideDownValue)
            this.xRot = Mth.clamp(this.xRot, -90, 90);
        }else{
            this.firstLook =true;
            player.turn(XRot,YRot);
        }

    }
    public CameraType SwitchPerspective(CameraType cameraType){
        this.lastCameraType =cameraType;
        if(Config.AutoSwitchPerspectiveValue != SwitchPerspective.OFF){
            switch (Config.AutoSwitchPerspectiveValue){
                case FirstPerson:
                    Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON);
                    cameraType=CameraType.FIRST_PERSON;
                    break;
                case ThirdPersonBack:
                    Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    cameraType=CameraType.THIRD_PERSON_BACK;
                    break;
                case ThirdPersonFront:
                    Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    cameraType=CameraType.THIRD_PERSON_FRONT;
                    break;
            }
        }
        return cameraType;
    }
    public void SwitchPerspectiveBack(){
        if(Config.AutoSwitchPerspectiveValue != SwitchPerspective.OFF && Config.AutoSwitchPerspeciveBackValue && !this.LookActive && this.last){
            this.last=false;
            Minecraft.getInstance().options.setCameraType(CameraType.valueOf(String.valueOf(this.lastCameraType)));
        }
    }
    public void SwapPerspective(){
        if(!this.SwapActive){this.firstSwap=true;return;}
        CameraType cameraType = CameraType.FIRST_PERSON;
        if(Config.AutoSwapPerspectiveValue != SwitchPerspective.OFF){
            switch (Config.AutoSwapPerspectiveValue){
                case FirstPerson:
                    cameraType =CameraType.FIRST_PERSON;
                    break;
                case ThirdPersonBack:
                    cameraType=CameraType.THIRD_PERSON_BACK;
                    break;
                case ThirdPersonFront:
                    cameraType=CameraType.THIRD_PERSON_FRONT;
                    break;
            }
        }
        if(this.firstSwap) {
            this.firstSwap = false;
            this.lastCameraSwap = Minecraft.getInstance().options.getCameraType();
            Minecraft.getInstance().options.setCameraType(cameraType);
        }
        this.SwapBack = true;
    }
    public void SwapPerspectiveBack(){
        if(Config.AutoSwapPerspectiveValue != SwitchPerspective.OFF && Config.AutoSwapPerspeciveBackValue && this.SwapBack && !this.SwapActive){

            this.SwapBack=false;
            Minecraft.getInstance().options.setCameraType(CameraType.valueOf(String.valueOf(this.lastCameraSwap)));
        }
    }
    public float convertToAngle(float zahl) {
        // Umwandeln der Zahl in den Bereich von -180° bis 180°
        float result = zahl % 360;
        if (result > 180) {
            result -= 360;
        }
        // Umwandeln der Zahl in den Bereich von -90° bis 90°
        if (result > 90) {
            result -= 180;
        } else if (result < -90) {
            result += 180;
        }
        // Kontrolle, dass der Winkel im Bereich von -90° bis 90° liegt
        if (result > 90) {
            result = 90;
        } else if (result < -90) {
            result = -90;
        }
        return result;
    }

}
