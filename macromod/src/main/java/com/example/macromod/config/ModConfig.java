package com.example.macromod.config;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

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
    private boolean defaultStopOnDanger = true;
    private boolean defaultSkipMismatch = true;
    private int maxPathNodes = 2000;
    private boolean recordingAutoAddBlocks = false;

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

    public boolean isDefaultStopOnDanger() {
        return defaultStopOnDanger;
    }

    public void setDefaultStopOnDanger(boolean defaultStopOnDanger) {
        this.defaultStopOnDanger = defaultStopOnDanger;
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
}
