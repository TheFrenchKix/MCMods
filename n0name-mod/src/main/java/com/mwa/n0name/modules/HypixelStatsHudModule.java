package com.mwa.n0name.modules;

import com.mwa.n0name.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Hypixel-like stats HUD with configurable sections and positioning.
 */
public class HypixelStatsHudModule {

    private final TimeLoggerModule timeLoggerModule;
    private int lastCropCount = 0;
    private long lastBpsUpdateMs = System.currentTimeMillis();
    private double avgBps = 0.0;

    public HypixelStatsHudModule(TimeLoggerModule timeLoggerModule) {
        this.timeLoggerModule = timeLoggerModule;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        long dt = now - lastBpsUpdateMs;
        if (dt < 1000L) return;

        int current = (int) FarmStats.getCropBreakCount();
        int delta = Math.max(0, current - lastCropCount);
        double instantBps = dt <= 0 ? 0.0 : (delta * 1000.0 / dt);
        avgBps = avgBps * 0.75 + instantBps * 0.25;

        lastCropCount = current;
        lastBpsUpdateMs = now;
    }

    public void renderHud(DrawContext ctx) {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.isHypixelStatsHudEnabled()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        int x = cfg.getHypixelStatsHudX();
        int y = cfg.getHypixelStatsHudY();
        float scale = cfg.getHypixelStatsHudScale();

        int baseW = 250;
        int baseH = 300;
        int w = Math.round(baseW * scale);
        int h = Math.round(baseH * scale);

        // Auto top-right if x is -1
        if (x < 0) {
            x = client.getWindow().getScaledWidth() - w - 8;
        }

        // Transparent background (no borders)
        ctx.fill(x, y, x + w, y + h, 0x60101010);

        int tx = x + Math.round(8 * scale);
        int ty = y + Math.round(10 * scale);
        int line = Math.round(20 * scale);

        String route = cfg.getGardenLaneRouteName();
        if (route == null || route.isBlank()) route = "No Route";

        ctx.drawTextWithShadow(client.textRenderer,
            route + " (" + timeLoggerModule.getAutoFarmTimeText() + ")",
            tx, ty, 0xFF66FF44);
        ty += line;

        if (cfg.isHypixelStatsShowFarming()) {
            ctx.drawTextWithShadow(client.textRenderer, "Farming", tx, ty, 0xFFD8D8D8);
            ty += line;
            ctx.drawTextWithShadow(client.textRenderer,
                "Average BPS: " + String.format(java.util.Locale.ROOT, "%.2f", avgBps),
                tx, ty, 0xFFE6D36A);
            ty += line + Math.round(6 * scale);
        }

        if (cfg.isHypixelStatsShowWorth()) {
            long worth = estimateInventoryWorth(player, cfg);
            long earnedPerHour = Math.round(FarmStats.estimateCoinsPerHour(timeLoggerModule.getAutoFarmActiveMs()));
            long totalProfit = (long)(earnedPerHour * (timeLoggerModule.getAutoFarmActiveMs() / 3600000.0));

            ctx.drawTextWithShadow(client.textRenderer, "Inventory Worth (NPC)", tx, ty, 0xFF66FF44);
            ty += line;
            ctx.drawTextWithShadow(client.textRenderer,
                "Inventory Worth: " + formatCoins(worth), tx, ty, 0xFFFF8888);
            ty += line;
            ctx.drawTextWithShadow(client.textRenderer,
                "Earned Per Hour: " + formatCoins(earnedPerHour), tx, ty, 0xFFFF6666);
            ty += line;
            ctx.drawTextWithShadow(client.textRenderer,
                "Total Profit: " + formatCoins(totalProfit), tx, ty, 0xFFFF6666);
            ty += line + Math.round(6 * scale);
        }

        if (cfg.isHypixelStatsShowSkills()) {
            double progress = Math.min(99.99, Math.max(0.0, FarmStats.getCropBreakCount() / 10000.0 * 100.0));
            long xph = Math.round(avgBps * 3600.0 * 3.0);
            long etaSec = xph <= 0 ? 0 : Math.round(((100.0 - progress) * 10000.0 / 100.0) / Math.max(1.0, avgBps));

            ctx.drawTextWithShadow(client.textRenderer, "Skills", tx, ty, 0xFF66FF44);
            ty += line;
            ctx.drawTextWithShadow(client.textRenderer, "Farming Level: XXIII (23)", tx, ty, 0xFFD8D8D8);
            ty += line;
            ctx.drawTextWithShadow(client.textRenderer,
                "Progress: " + String.format(java.util.Locale.ROOT, "%.2f%%", progress), tx, ty, 0xFFE6D36A);
            ty += line;
            ctx.drawTextWithShadow(client.textRenderer, "XP Rate: " + formatCompact(xph) + "/h", tx, ty, 0xFFE6D36A);
            ty += line;
            ctx.drawTextWithShadow(client.textRenderer, "ETA: " + formatDuration(etaSec), tx, ty, 0xFFE6D36A);
            ty += line;
        }

        ctx.drawTextWithShadow(client.textRenderer,
            "Stats HUD",
            x + Math.round(8 * scale), y + h - Math.round(14 * scale), 0x80BEBEBE);
    }

    private long estimateInventoryWorth(ClientPlayerEntity player, ModConfig cfg) {
        long total = 0L;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s == null || s.isEmpty()) continue;

            String n = s.getName().getString().toLowerCase(java.util.Locale.ROOT);
            int count = s.getCount();

            double unit = 0.0;
            if (n.contains("enchanted pumpkin")) unit = 160.0 * 2.2;
            else if (n.contains("pumpkin")) unit = 2.2;
            else if (n.contains("enchanted carrot")) unit = 160.0 * cfg.getCarrotPrice();
            else if (n.contains("carrot")) unit = cfg.getCarrotPrice();
            else if (n.contains("enchanted potato")) unit = 160.0 * cfg.getPotatoPrice();
            else if (n.contains("potato")) unit = cfg.getPotatoPrice();
            else if (n.contains("enchanted wheat") || n.contains("hay bale")) unit = 160.0 * cfg.getWheatPrice();
            else if (n.contains("wheat")) unit = cfg.getWheatPrice();

            total += Math.round(unit * count);
        }
        return total;
    }

    private String formatCoins(long c) {
        if (c >= 1_000_000_000L) return String.format(java.util.Locale.ROOT, "$%.2fB", c / 1_000_000_000.0);
        if (c >= 1_000_000L) return String.format(java.util.Locale.ROOT, "$%.1fM", c / 1_000_000.0);
        if (c >= 1_000L) return String.format(java.util.Locale.ROOT, "$%.1fk", c / 1_000.0);
        return "$" + c;
    }

    private String formatCompact(long v) {
        if (v >= 1_000_000L) return String.format(java.util.Locale.ROOT, "%.1fM", v / 1_000_000.0);
        if (v >= 1_000L) return String.format(java.util.Locale.ROOT, "%.0fk", v / 1_000.0);
        return String.valueOf(v);
    }

    private String formatDuration(long sec) {
        long m = sec / 60;
        long s = sec % 60;
        long h = m / 60;
        m = m % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }
}
