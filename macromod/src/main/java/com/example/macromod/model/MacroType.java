package com.example.macromod.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Macro execution mode types.
 */
@Environment(EnvType.CLIENT)
public enum MacroType {
    /** Standard step-based macro: navigate to waypoints and mine blocks. */
    NORMAL,
    
    /** Line farm mode: hold left click and move in a snake pattern until hitting a block. */
    LINE_FARM
}
