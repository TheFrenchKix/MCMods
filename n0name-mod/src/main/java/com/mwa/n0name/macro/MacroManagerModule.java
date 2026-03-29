package com.mwa.n0name.macro;

import com.mwa.n0name.DebugLogger;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Vec3d;

import java.awt.Desktop;
import java.io.IOException;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * Top-level module that owns MacroManager, MacroRecorder, MacroExecutor, MacroRenderer.
 * Provides the public API used by n0nameScreen and n0nameMod.
 */
public class MacroManagerModule {

    private final MacroManager manager;
    private final MacroRecorder recorder;
    private final MacroExecutor executor;
    private final MacroRenderer renderer;

    // Currently selected macro in GUI (for visualization)
    private String selectedMacroName = null;
    private Macro previewMacro = null;
    // Per-macro render visibility (only renders 3D preview if toggled on)
    private final Set<String> renderVisible = new HashSet<>();

    public MacroManagerModule() {
        manager = new MacroManager();
        recorder = new MacroRecorder();
        executor = new MacroExecutor();
        renderer = new MacroRenderer();
    }

    // --- Accessors ---
    public MacroManager getManager() { return manager; }
    public MacroRecorder getRecorder() { return recorder; }
    public MacroExecutor getExecutor() { return executor; }

    public List<String> getMacroNames() { return manager.getMacroNames(); }
    public List<Macro> getAllMacros() { return manager.getAllMacros(); }
    public Macro getMacro(String name) { return manager.getMacro(name); }

    public String getSelectedMacroName() { return selectedMacroName; }
    public void setSelectedMacroName(String name) {
        selectedMacroName = name;
        previewMacro = name != null ? manager.getMacro(name) : null;
    }

    // --- Recording ---
    public boolean isRecording() { return recorder.isRecording(); }

    public void startRecording(String name, MacroType type) {
        if (executor.isRunning()) executor.stop();
        recorder.setRecordingType(type);
        recorder.startRecording(name);
    }

    public void stopRecording() {
        Macro result = recorder.stopRecording();
        if (result != null && result.stepCount() > 0) {
            manager.addMacro(result);
            setSelectedMacroName(result.getName());
        }
    }

    public void onLeftClick() { recorder.onLeftClick(); }
    public void onRightClick() { recorder.onRightClick(); }

    // --- Execution ---
    public boolean isExecuting() { return executor.isRunning(); }
    public boolean isPaused() { return executor.isPaused(); }

    public void playMacro(String name) {
        Macro macro = manager.getMacro(name);
        if (macro == null) {
            DebugLogger.log("MacroModule", "Macro not found: " + name);
            return;
        }
        if (recorder.isRecording()) recorder.stopRecording();
        executor.start(macro);
        setSelectedMacroName(name);
    }

    public void stopExecution() { executor.stop(); }
    public void pauseExecution() { executor.pause(); }
    public void resumeExecution() { executor.resume(); }

    // --- CRUD ---
    public void createMacro(String name, MacroType type) {
        Macro macro = new Macro(name, type);
        manager.addMacro(macro);
        setSelectedMacroName(name);
    }

    public void deleteMacro(String name) {
        manager.deleteMacro(name);
        renderVisible.remove(name);
        if (name.equals(selectedMacroName)) {
            selectedMacroName = null;
            previewMacro = null;
        }
    }

    public void toggleRepeat(String name) {
        Macro macro = manager.getMacro(name);
        if (macro != null) {
            macro.setRepeat(!macro.isRepeat());
            manager.updateMacro(macro);
        }
    }

    public void cycleMacroType(String name) {
        Macro macro = manager.getMacro(name);
        if (macro != null) {
            MacroType[] types = MacroType.values();
            macro.setType(types[(macro.getType().ordinal() + 1) % types.length]);
            manager.updateMacro(macro);
        }
    }

    // --- Folder ---
    public void openMacrosFolder() {
        try {
            Desktop.getDesktop().open(manager.getMacrosFolder().toFile());
        } catch (IOException e) {
            DebugLogger.log("MacroModule", "Failed to open macros folder: " + e.getMessage());
        }
    }

    // --- Tick/Render/HUD ---
    public void tick() {
        recorder.tick();
        executor.tick();
    }

    public void frameUpdate() {
        executor.frameUpdate();
    }

    public void render(WorldRenderContext context) {
        // Render recording preview
        if (recorder.isRecording() && recorder.getCurrentMacro() != null) {
            renderer.renderRecording(context, recorder.getCurrentMacro());
            return;
        }

        // Render execution
        if (executor.isRunning() && executor.getCurrentMacro() != null) {
            renderer.render(context, executor.getCurrentMacro(), executor.getCurrentStepIndex());
            return;
        }

        // Render selected macro preview
        if (previewMacro != null && isRenderVisible(selectedMacroName)) {
            renderer.render(context, previewMacro, -1);
        }
    }

    public void renderHud(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        Vec3d playerPos = new Vec3d(client.player.getX(), client.player.getY(), client.player.getZ());

        // Recording indicator
        if (recorder.isRecording()) {
            Macro currentMacro = recorder.getCurrentMacro();
            String text = "\u25CF REC: " + (currentMacro != null ?
                currentMacro.stepCount() + " steps" : "...");
            drawContext.drawTextWithShadow(client.textRenderer, text, 4, 4, 0xFFFF4444);
            renderer.renderHud(drawContext, client.textRenderer, currentMacro, playerPos, -1);
            return;
        }

        // Execution HUD
        if (executor.isRunning() || executor.isPaused()) {
            String text = executor.getStatusText();
            if (executor.getLoopCount() > 0) text += " [Loop " + executor.getLoopCount() + "]";
            int color = executor.isPaused() ? 0xFFFFAA44 : 0xFF44FF44;
            drawContext.drawTextWithShadow(client.textRenderer, text, 4, 4, color);
            renderer.renderHud(drawContext, client.textRenderer, executor.getCurrentMacro(), playerPos, executor.getCurrentStepIndex());
            return;
        }

        // Preview: selected macro minimap (no status text)
        if (previewMacro != null) {
            renderer.renderHud(drawContext, client.textRenderer, previewMacro, playerPos, -1);
        }
    }

    // --- Render visibility ---
    public boolean isRenderVisible(String name) {
        return name != null && renderVisible.contains(name);
    }

    public void toggleRenderVisible(String name) {
        if (name == null) return;
        if (!renderVisible.remove(name)) renderVisible.add(name);
    }
}
