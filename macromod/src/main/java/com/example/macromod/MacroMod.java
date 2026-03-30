package com.example.macromod;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for Macro Mod.
 * This mod is client-only; this class exists only for the mod ID constant and logger.
 */
@Environment(EnvType.CLIENT)
public class MacroMod {

    public static final String MOD_ID = "macromod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
}
