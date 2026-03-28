package com.mwa.n0name.modules;

import com.mwa.n0name.ModConfig;

/**
 * Tracks runtime and AutoFarm-active time for quick farming session stats.
 */
public class TimeLoggerModule {

    private long sessionStartMs = System.currentTimeMillis();
    private long autoFarmActiveMs = 0L;
    private long lastTickMs = sessionStartMs;

    public void tick() {
        long now = System.currentTimeMillis();
        long dt = Math.max(0L, now - lastTickMs);
        lastTickMs = now;

        if (ModConfig.getInstance().isAutoFarmEnabled()) {
            autoFarmActiveMs += dt;
        }
    }

    public String getSessionTimeText() {
        return formatDuration(System.currentTimeMillis() - sessionStartMs);
    }

    public String getAutoFarmTimeText() {
        return formatDuration(autoFarmActiveMs);
    }

    public String getSummaryText() {
        long cph = Math.round(FarmStats.estimateCoinsPerHour(autoFarmActiveMs));
        return "Session " + getSessionTimeText()
            + " | Farm " + getAutoFarmTimeText()
            + " | Crops " + FarmStats.getCropBreakCount()
            + " | CPH " + cph;
    }

    public long getAutoFarmActiveMs() {
        return autoFarmActiveMs;
    }

    private String formatDuration(long ms) {
        long totalSec = Math.max(0L, ms / 1000L);
        long h = totalSec / 3600L;
        long m = (totalSec % 3600L) / 60L;
        long s = totalSec % 60L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", h, m, s);
    }
}
