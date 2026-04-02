package com.test;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 */
public class TestMod implements ClientModInitializer {

    public static final String MOD_ID = "test-mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);


    @Override
    public void onInitializeClient() {
        LOGGER.info("[TestMod] Initializing...");

        LOGGER.info("[TestMod] Ready.");
    }
}
