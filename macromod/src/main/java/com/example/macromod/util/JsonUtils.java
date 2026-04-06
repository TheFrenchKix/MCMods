package com.example.macromod.util;

import com.example.macromod.model.BlockTarget;
import com.example.macromod.model.Macro;
import com.example.macromod.model.MacroStep;
import com.google.gson.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Type;

/**
 * Gson type adapters for Minecraft types and custom serialization helpers.
 */
@Environment(EnvType.CLIENT)
public final class JsonUtils {

    private static final Gson GSON = createGson();

    private JsonUtils() {
    }

    /**
     * Returns the shared Gson instance configured with all custom adapters.
     */
    public static Gson getGson() {
        return GSON;
    }

    private static Gson createGson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(BlockPos.class, new BlockPosAdapter())
                .registerTypeAdapter(NbtCompound.class, new NbtCompoundAdapter())
                .registerTypeAdapter(BlockTarget.class, new BlockTargetAdapter())
                .registerTypeAdapter(MacroStep.class, new MacroStepAdapter())
                .create();
    }

    /**
     * Serializes a Macro to its JSON representation.
     */
    public static String toJson(Macro macro) {
        return GSON.toJson(macro);
    }

    /**
     * Deserializes a Macro from JSON.
     */
    public static Macro fromJson(String json) {
        return GSON.fromJson(json, Macro.class);
    }

    // ─── BlockPos Adapter ───────────────────────────────────────

    public static class BlockPosAdapter implements JsonSerializer<BlockPos>, JsonDeserializer<BlockPos> {

        @Override
        public JsonElement serialize(BlockPos pos, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("x", pos.getX());
            obj.addProperty("y", pos.getY());
            obj.addProperty("z", pos.getZ());
            return obj;
        }

        @Override
        public BlockPos deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            return new BlockPos(
                    obj.get("x").getAsInt(),
                    obj.get("y").getAsInt(),
                    obj.get("z").getAsInt()
            );
        }
    }

    // ─── NbtCompound Adapter ────────────────────────────────────

    public static class NbtCompoundAdapter implements JsonSerializer<NbtCompound>, JsonDeserializer<NbtCompound> {

        @Override
        public JsonElement serialize(NbtCompound nbt, Type typeOfSrc, JsonSerializationContext context) {
            if (nbt == null) {
                return JsonNull.INSTANCE;
            }
            return new JsonPrimitive(nbt.toString());
        }

        @Override
        public NbtCompound deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonNull()) {
                return null;
            }
            try {
                return StringNbtReader.readCompound(json.getAsString());
            } catch (Exception e) {
                throw new JsonParseException("Failed to parse NBT: " + json.getAsString(), e);
            }
        }
    }

    // ─── BlockTarget Adapter ────────────────────────────────────

    public static class BlockTargetAdapter implements JsonSerializer<BlockTarget>, JsonDeserializer<BlockTarget> {

        @Override
        public JsonElement serialize(BlockTarget target, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.add("pos", context.serialize(target.getPos(), BlockPos.class));
            obj.addProperty("blockId", target.getBlockId());
            if (target.getBlockNbt() != null) {
                obj.add("blockNbt", context.serialize(target.getBlockNbt(), NbtCompound.class));
            }
            return obj;
        }

        @Override
        public BlockTarget deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            BlockPos pos = context.deserialize(obj.get("pos"), BlockPos.class);
            String blockId = obj.get("blockId").getAsString();
            NbtCompound nbt = null;
            if (obj.has("blockNbt") && !obj.get("blockNbt").isJsonNull()) {
                nbt = context.deserialize(obj.get("blockNbt"), NbtCompound.class);
            }
            return new BlockTarget(pos, blockId, nbt);
        }
    }

    // ─── MacroStep Adapter ──────────────────────────────────────

    public static class MacroStepAdapter implements JsonSerializer<MacroStep>, JsonDeserializer<MacroStep> {

        @Override
        public JsonElement serialize(MacroStep step, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("label", step.getLabel());
            obj.add("destination", context.serialize(step.getDestination(), BlockPos.class));
            obj.addProperty("radius", step.getRadius());
            JsonArray targetsArray = new JsonArray();
            for (BlockTarget target : step.getTargets()) {
                targetsArray.add(context.serialize(target, BlockTarget.class));
            }
            obj.add("targets", targetsArray);
            return obj;
        }

        @Override
        public MacroStep deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String label = obj.has("label") ? obj.get("label").getAsString() : "Step";
            BlockPos destination = context.deserialize(obj.get("destination"), BlockPos.class);
            MacroStep step = new MacroStep(label, destination);
            if (obj.has("radius")) {
                step.setRadius(obj.get("radius").getAsInt());
            }
            if (obj.has("targets")) {
                JsonArray targets = obj.getAsJsonArray("targets");
                for (JsonElement elem : targets) {
                    step.addTarget(context.deserialize(elem, BlockTarget.class));
                }
            }
            return step;
        }
    }
}
