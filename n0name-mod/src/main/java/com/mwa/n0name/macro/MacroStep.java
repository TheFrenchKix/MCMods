package com.mwa.n0name.macro;

import com.google.gson.JsonObject;

/**
 * A single step in a macro sequence.
 */
public class MacroStep {

    private StepType type;
    private double x, y, z;
    private float yaw, pitch;
    private String param;   // item name, entity type, command, etc.
    private int delayTicks;  // wait time for WAIT steps, or inter-step delay

    public MacroStep(StepType type, double x, double y, double z, float yaw, float pitch, String param, int delayTicks) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.param = param != null ? param : "";
        this.delayTicks = delayTicks;
    }

    public MacroStep(StepType type, double x, double y, double z, float yaw, float pitch) {
        this(type, x, y, z, yaw, pitch, "", 0);
    }

    public StepType getType() { return type; }
    public void setType(StepType type) { this.type = type; }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public void setPosition(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }

    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public void setRotation(float yaw, float pitch) { this.yaw = yaw; this.pitch = pitch; }

    public String getParam() { return param; }
    public void setParam(String param) { this.param = param != null ? param : ""; }

    public int getDelayTicks() { return delayTicks; }
    public void setDelayTicks(int delayTicks) { this.delayTicks = delayTicks; }

    public int getBlockX() { return (int) Math.floor(x); }
    public int getBlockY() { return (int) Math.floor(y); }
    public int getBlockZ() { return (int) Math.floor(z); }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type.name());
        obj.addProperty("x", x);
        obj.addProperty("y", y);
        obj.addProperty("z", z);
        obj.addProperty("yaw", yaw);
        obj.addProperty("pitch", pitch);
        if (!param.isEmpty()) obj.addProperty("param", param);
        if (delayTicks > 0) obj.addProperty("delay", delayTicks);
        return obj;
    }

    public static MacroStep fromJson(JsonObject obj) {
        StepType type;
        try {
            type = StepType.valueOf(obj.get("type").getAsString());
        } catch (IllegalArgumentException e) {
            type = StepType.WALK;
        }
        double x = obj.has("x") ? obj.get("x").getAsDouble() : 0;
        double y = obj.has("y") ? obj.get("y").getAsDouble() : 0;
        double z = obj.has("z") ? obj.get("z").getAsDouble() : 0;
        float yaw = obj.has("yaw") ? obj.get("yaw").getAsFloat() : 0;
        float pitch = obj.has("pitch") ? obj.get("pitch").getAsFloat() : 0;
        String param = obj.has("param") ? obj.get("param").getAsString() : "";
        int delay = obj.has("delay") ? obj.get("delay").getAsInt() : 0;
        return new MacroStep(type, x, y, z, yaw, pitch, param, delay);
    }
}
