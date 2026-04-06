package com.example.macromod.config;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.List;

/**
 * General mod configuration data class.
 */
@Environment(EnvType.CLIENT)
public class ModConfig {

    private String hudPosition = "TOP_LEFT";
    private float hudScale = 1.0f;
    private boolean hudVisible = true;
    private int defaultMiningDelay = 50;
    private int defaultMoveTimeout = 200;
    private float defaultArrivalRadius = 1.5f;
    private boolean defaultAttackDanger = true;
    private boolean defaultSkipMismatch = true;
    private int maxPathNodes = 2000;
    private boolean recordingAutoAddBlocks = false;
    private boolean keybindHudVisible = true;

    // ── Debug ────────────────────────────────────────────────────────
    private boolean debugLogging = false;

    // ── Freelook ─────────────────────────────────────────────────────
    private int freelookFov = 90;

    // ── ESP / Visuals ────────────────────────────────────────────────
    private boolean targetEspEnabled = true;
    private boolean entitiesEspEnabled = false;
    private boolean blockEspEnabled = false;
    private int blockEspRadius = 16;
    private List<String> entityWhitelist = new ArrayList<>();
    private List<String> blockWhitelist = new ArrayList<>();

    public String getHudPosition() {
        return hudPosition;
    }

    public void setHudPosition(String hudPosition) {
        this.hudPosition = hudPosition;
    }

    public float getHudScale() {
        return hudScale;
    }

    public void setHudScale(float hudScale) {
        this.hudScale = hudScale;
    }

    public boolean isHudVisible() {
        return hudVisible;
    }

    public void setHudVisible(boolean hudVisible) {
        this.hudVisible = hudVisible;
    }

    public int getDefaultMiningDelay() {
        return defaultMiningDelay;
    }

    public void setDefaultMiningDelay(int defaultMiningDelay) {
        this.defaultMiningDelay = defaultMiningDelay;
    }

    public int getDefaultMoveTimeout() {
        return defaultMoveTimeout;
    }

    public void setDefaultMoveTimeout(int defaultMoveTimeout) {
        this.defaultMoveTimeout = defaultMoveTimeout;
    }

    public float getDefaultArrivalRadius() {
        return defaultArrivalRadius;
    }

    public void setDefaultArrivalRadius(float defaultArrivalRadius) {
        this.defaultArrivalRadius = defaultArrivalRadius;
    }

    public boolean isDefaultAttackDanger() {
        return defaultAttackDanger;
    }

    public void setDefaultAttackDanger(boolean defaultAttackDanger) {
        this.defaultAttackDanger = defaultAttackDanger;
    }

    public boolean isDefaultSkipMismatch() {
        return defaultSkipMismatch;
    }

    public void setDefaultSkipMismatch(boolean defaultSkipMismatch) {
        this.defaultSkipMismatch = defaultSkipMismatch;
    }

    public int getMaxPathNodes() {
        return maxPathNodes;
    }

    public void setMaxPathNodes(int maxPathNodes) {
        this.maxPathNodes = maxPathNodes;
    }

    public boolean isRecordingAutoAddBlocks() {
        return recordingAutoAddBlocks;
    }

    public void setRecordingAutoAddBlocks(boolean recordingAutoAddBlocks) {
        this.recordingAutoAddBlocks = recordingAutoAddBlocks;
    }

    public boolean isKeybindHudVisible() {
        return keybindHudVisible;
    }

    public void setKeybindHudVisible(boolean keybindHudVisible) {
        this.keybindHudVisible = keybindHudVisible;
    }

    // ── Debug getters/setters ────────────────────────────────────────

    public boolean isDebugLogging() { return debugLogging; }
    public void setDebugLogging(boolean debugLogging) { this.debugLogging = debugLogging; }

    // ── Freelook getters/setters ─────────────────────────────────────

    public int getFreelookFov() { return freelookFov; }
    public void setFreelookFov(int fov) { this.freelookFov = Math.max(30, Math.min(110, fov)); }

    // ── ESP getters/setters ──────────────────────────────────────────

    public boolean isTargetEspEnabled() { return targetEspEnabled; }
    public void setTargetEspEnabled(boolean v) { this.targetEspEnabled = v; }

    public boolean isEntitiesEspEnabled() { return entitiesEspEnabled; }
    public void setEntitiesEspEnabled(boolean v) { this.entitiesEspEnabled = v; }

    public boolean isBlockEspEnabled() { return blockEspEnabled; }
    public void setBlockEspEnabled(boolean v) { this.blockEspEnabled = v; }

    public int getBlockEspRadius() { return blockEspRadius; }
    public void setBlockEspRadius(int r) { this.blockEspRadius = Math.max(4, Math.min(32, r)); }

    public List<String> getEntityWhitelist() {
        if (entityWhitelist == null) {
            entityWhitelist = new ArrayList<>();
        }
        return entityWhitelist;
    }
    public void setEntityWhitelist(List<String> list) { this.entityWhitelist = list; }

    public List<String> getBlockWhitelist() {
        if (blockWhitelist == null) {
            blockWhitelist = new ArrayList<>();
        }
        return blockWhitelist;
    }
    public void setBlockWhitelist(List<String> list) { this.blockWhitelist = list; }
}
