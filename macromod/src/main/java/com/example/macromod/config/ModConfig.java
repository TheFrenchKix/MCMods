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
    private boolean defaultAttackDanger = false;
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
    private boolean blockEspEnabled = false;    private boolean fairySoulsEspEnabled = false;    private boolean hotspotEspEnabled = false;    private int blockEspRadius = 16;
    private List<String> entityWhitelist = new ArrayList<>();
    private List<String> blockWhitelist = new ArrayList<>();

    // ── Smooth Aim ───────────────────────────────────────────────────────
    private float smoothAimBaseLerp  = 0.035f;
    private float smoothAimSpeedZero = 0.55f;
    private float smoothAimSpeedSlow = 1.0f;
    private float smoothAimSpeedFast = 1.25f;
    private float smoothAimSlowZone  = 15f;
    private float smoothAimFastZone  = 55f;

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

    public boolean isFairySoulsEspEnabled() { return fairySoulsEspEnabled; }
    public void setFairySoulsEspEnabled(boolean v) { this.fairySoulsEspEnabled = v; }
    public boolean isHotspotEspEnabled() { return hotspotEspEnabled; }
    public void setHotspotEspEnabled(boolean v) { this.hotspotEspEnabled = v; }

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

    // ── Smooth Aim getters/setters ───────────────────────────────────────

    public float getSmoothAimBaseLerp()  { return smoothAimBaseLerp; }
    public void  setSmoothAimBaseLerp(float v)  { smoothAimBaseLerp  = Math.max(0.005f, Math.min(0.15f, v)); }

    public float getSmoothAimSpeedZero() { return smoothAimSpeedZero; }
    public void  setSmoothAimSpeedZero(float v) { smoothAimSpeedZero = Math.max(0.05f, Math.min(3.0f, v)); }

    public float getSmoothAimSpeedSlow() { return smoothAimSpeedSlow; }
    public void  setSmoothAimSpeedSlow(float v) { smoothAimSpeedSlow = Math.max(0.1f, Math.min(4.0f, v)); }

    public float getSmoothAimSpeedFast() { return smoothAimSpeedFast; }
    public void  setSmoothAimSpeedFast(float v) { smoothAimSpeedFast = Math.max(0.1f, Math.min(5.0f, v)); }

    public float getSmoothAimSlowZone()  { return smoothAimSlowZone; }
    public void  setSmoothAimSlowZone(float v)  { smoothAimSlowZone  = Math.max(3f, Math.min(30f, v)); }

    public float getSmoothAimFastZone()  { return smoothAimFastZone; }
    public void  setSmoothAimFastZone(float v)  { smoothAimFastZone  = Math.max(10f, Math.min(90f, v)); }
}
