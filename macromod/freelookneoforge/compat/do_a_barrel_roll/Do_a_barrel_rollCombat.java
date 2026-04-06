package quatum.freelookneoforge.compat.do_a_barrel_roll;

import net.neoforged.fml.ModList;
import quatum.freelookneoforge.FreeLookneoForge;

public class Do_a_barrel_rollCombat {
    static boolean isloaded;

    public static void setUp(){
        isloaded=ModList.get().isLoaded("do_a_barrel_roll");
        if(isloaded)
            FreeLookneoForge.LOGGER.warn("When both Barrel Roll and Free Look are enabled, barrel rolls bugs while using Free Look. \n" +
                    " Otherwise, it will work normally. This issue occurs only when trying to perform a barrel roll with Free Look active.");
    }

}
