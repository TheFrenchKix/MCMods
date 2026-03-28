package com.mwa.n0name.modules;

import com.mwa.n0name.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Inventory viewer HUD (top-left by default), movable and scalable.
 */
public class InventoryHudModule {

    private static final int SLOT = 18;

    public void renderHud(DrawContext ctx) {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.isInventoryHudEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        int x = cfg.getInventoryHudX();
        int y = cfg.getInventoryHudY();
        float scale = cfg.getInventoryHudScale();

        int sx = x;
        int sy = y;

        // main inventory rows (9x3)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = 9 + row * 9 + col;
                ItemStack stack = player.getInventory().getStack(slot);
                int rx = sx + Math.round(col * SLOT * scale);
                int ry = sy + Math.round(row * SLOT * scale);
                drawItemScaled(ctx, stack, rx, ry, scale);
            }
        }

        // hotbar row
        int hotY = sy + Math.round(3 * SLOT * scale) + Math.round(6 * scale);
        for (int col = 0; col < 9; col++) {
            ItemStack stack = player.getInventory().getStack(col);
            int rx = sx + Math.round(col * SLOT * scale);
            drawItemScaled(ctx, stack, rx, hotY, scale);
        }
    }

    private void drawSlotBg(DrawContext ctx, int x, int y, float scale) {
        int s = Math.round(16 * scale);
        ctx.fill(x, y, x + s, y + s, 0x66314A74);
    }

    private void drawItemScaled(DrawContext ctx, ItemStack stack, int x, int y, float scale) {
        if (stack == null || stack.isEmpty()) return;
        // DrawContext in this mapping uses a 2D matrix stack without push/pop in this path,
        // so item icon size remains vanilla (16x16) while spacing/frame are scaled.
        ctx.drawItem(stack, x, y);
        ctx.drawStackOverlay(MinecraftClient.getInstance().textRenderer, stack, x, y);
    }
}
