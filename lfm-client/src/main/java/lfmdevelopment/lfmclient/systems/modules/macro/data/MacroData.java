package lfmdevelopment.lfmclient.systems.modules.macro.data;

import java.util.ArrayList;
import java.util.List;

public class MacroData {
    public String name = "unnamed";
    public int version = 1;
    public boolean loop = false;
    public boolean attack = false;
    public List<MacroAction> actions = new ArrayList<>();
}
