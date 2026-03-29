package lfmdevelopment.lfmclient.systems.modules.macro.data;

import com.google.gson.*;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Type;

public class MacroAction {
    public ActionType type;
    public MoveMode mode;
    public BlockPos position;
    public boolean usePathfinder = true;
    public double tolerance = 0.3;
    public int retries = 3;
    public int delay = 0;
    public String block;

    public MacroAction() {}

    public static MacroAction move(BlockPos pos, MoveMode mode, int delay) {
        MacroAction a = new MacroAction();
        a.type = ActionType.MOVE;
        a.mode = mode;
        a.position = pos;
        a.usePathfinder = true;
        a.tolerance = 0.3;
        a.retries = 3;
        a.delay = delay;
        return a;
    }

    public static MacroAction mine(BlockPos pos, String blockId, int delay) {
        MacroAction a = new MacroAction();
        a.type = ActionType.MINE;
        a.position = pos;
        a.block = blockId;
        a.delay = delay;
        return a;
    }

    public static MacroAction interact(BlockPos pos, String blockId, int delay) {
        MacroAction a = new MacroAction();
        a.type = ActionType.INTERACT;
        a.position = pos;
        a.block = blockId;
        a.delay = delay;
        return a;
    }

    public static class Serializer implements JsonSerializer<MacroAction>, JsonDeserializer<MacroAction> {
        @Override
        public JsonElement serialize(MacroAction src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", src.type.name());

            if (src.mode != null) obj.addProperty("mode", src.mode.name());
            if (src.position != null) {
                JsonObject pos = new JsonObject();
                pos.addProperty("x", src.position.getX());
                pos.addProperty("y", src.position.getY());
                pos.addProperty("z", src.position.getZ());
                obj.add("position", pos);
            }
            if (src.type == ActionType.MOVE) {
                obj.addProperty("usePathfinder", src.usePathfinder);
                obj.addProperty("tolerance", src.tolerance);
                obj.addProperty("retries", src.retries);
            }
            if (src.block != null) obj.addProperty("block", src.block);
            obj.addProperty("delay", src.delay);
            return obj;
        }

        @Override
        public MacroAction deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            MacroAction a = new MacroAction();

            a.type = ActionType.valueOf(obj.get("type").getAsString());

            if (obj.has("mode")) a.mode = MoveMode.valueOf(obj.get("mode").getAsString());

            if (obj.has("position")) {
                JsonObject pos = obj.getAsJsonObject("position");
                a.position = new BlockPos(pos.get("x").getAsInt(), pos.get("y").getAsInt(), pos.get("z").getAsInt());
            }

            if (obj.has("usePathfinder")) a.usePathfinder = obj.get("usePathfinder").getAsBoolean();
            if (obj.has("tolerance")) a.tolerance = obj.get("tolerance").getAsDouble();
            if (obj.has("retries")) a.retries = obj.get("retries").getAsInt();
            if (obj.has("block")) a.block = obj.get("block").getAsString();
            if (obj.has("delay")) a.delay = obj.get("delay").getAsInt();

            return a;
        }
    }
}
