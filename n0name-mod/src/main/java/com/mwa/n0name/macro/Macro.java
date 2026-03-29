package com.mwa.n0name.macro;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A named macro containing a sequence of steps.
 */
public class Macro {

    private String name;
    private MacroType type;
    private boolean repeat;
    private final List<MacroStep> steps;

    public Macro(String name, MacroType type) {
        this.name = name;
        this.type = type;
        this.repeat = false;
        this.steps = new ArrayList<>();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public MacroType getType() { return type; }
    public void setType(MacroType type) { this.type = type; }

    public boolean isRepeat() { return repeat; }
    public void setRepeat(boolean repeat) { this.repeat = repeat; }

    public List<MacroStep> getSteps() { return steps; }

    public void addStep(MacroStep step) { steps.add(step); }
    public void removeStep(int index) { if (index >= 0 && index < steps.size()) steps.remove(index); }
    public void clearSteps() { steps.clear(); }
    public int stepCount() { return steps.size(); }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("name", name);
        obj.addProperty("type", type.name());
        obj.addProperty("repeat", repeat);
        JsonArray arr = new JsonArray();
        for (MacroStep step : steps) {
            arr.add(step.toJson());
        }
        obj.add("steps", arr);
        return obj;
    }

    public static Macro fromJson(JsonObject obj) {
        String name = obj.has("name") ? obj.get("name").getAsString() : "unnamed";
        MacroType type;
        try {
            type = MacroType.valueOf(obj.get("type").getAsString());
        } catch (Exception e) {
            type = MacroType.CUSTOM;
        }
        Macro macro = new Macro(name, type);
        macro.repeat = obj.has("repeat") && obj.get("repeat").getAsBoolean();
        if (obj.has("steps")) {
            for (var el : obj.getAsJsonArray("steps")) {
                macro.steps.add(MacroStep.fromJson(el.getAsJsonObject()));
            }
        }
        return macro;
    }
}
