package com.mwa.n0name.macro;

public enum MacroType {
    FARM("Farm",     0xFF55AA55),
    FIGHT("Fight",   0xFFFF5555),
    CUSTOM("Custom", 0xFF5588FF);

    public final String label;
    public final int color;

    MacroType(String label, int color) {
        this.label = label;
        this.color = color;
    }
}
