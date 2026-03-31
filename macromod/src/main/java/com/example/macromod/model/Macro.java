package com.example.macromod.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a complete macro — a named sequence of steps with execution configuration.
 */
@Environment(EnvType.CLIENT)
public class Macro {

    /** Unique identifier (UUID). */
    private String id;

    /** Display name. */
    private String name;

    /** Optional description. */
    private String description;

    /** Creation timestamp (epoch millis). */
    private long createdAt;

    /** Last modification timestamp (epoch millis). */
    private long updatedAt;

    /** Ordered list of steps to execute. */
    private List<MacroStep> steps;

    /** Execution configuration for this macro. */
    private MacroConfig config;

    public Macro() {
        this.id = UUID.randomUUID().toString();
        this.steps = new ArrayList<>();
        this.config = new MacroConfig();
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.description = "";
    }

    public Macro(String name) {
        this();
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<MacroStep> getSteps() {
        return steps;
    }

    public void setSteps(List<MacroStep> steps) {
        this.steps = steps;
    }

    public MacroConfig getConfig() {
        return config;
    }

    public void setConfig(MacroConfig config) {
        this.config = config;
    }

    /**
     * Marks the macro as modified (updates the timestamp).
     */
    public void markModified() {
        this.updatedAt = System.currentTimeMillis();
    }

    /**
     * Adds a step to the end of the macro.
     */
    public void addStep(MacroStep step) {
        steps.add(step);
        markModified();
    }

    /**
     * Removes a step by index.
     */
    public void removeStep(int index) {
        if (index >= 0 && index < steps.size()) {
            steps.remove(index);
            markModified();
        }
    }

    /**
     * Resets all steps for re-execution.
     */
    public void reset() {
        for (MacroStep step : steps) {
            step.reset();
        }
    }

    /**
     * Returns the total number of block targets across all steps.
     */
    public int getTotalBlockCount() {
        int count = 0;
        for (MacroStep step : steps) {
            count += step.getTargets().size();
        }
        return count;
    }

    /**
     * Creates a deep copy of this macro with a new ID.
     */
    public Macro duplicate() {
        Macro copy = new Macro(name + " (copy)");
        copy.description = description;
        copy.config = config.copy();
        for (MacroStep step : steps) {
            copy.steps.add(step.copy());
        }
        return copy;
    }
}
