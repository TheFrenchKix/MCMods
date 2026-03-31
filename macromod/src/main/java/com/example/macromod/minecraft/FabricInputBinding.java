package com.example.macromod.minecraft;

import net.minecraft.client.option.KeyBinding;

final class FabricInputBinding implements InputBinding {

    private final int keyCode;
    private final KeyBinding binding;

    FabricInputBinding(int keyCode, KeyBinding binding) {
        this.keyCode = keyCode;
        this.binding = binding;
    }

    @Override
    public int getKeyCode() {
        return keyCode;
    }

    @Override
    public boolean isDown() {
        return binding.isPressed();
    }
}
