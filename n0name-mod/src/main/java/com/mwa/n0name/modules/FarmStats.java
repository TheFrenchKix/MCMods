package com.mwa.n0name.modules;

import com.mwa.n0name.ModConfig;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared runtime farming statistics used by AutoFarm and UI helpers.
 */
public final class FarmStats {

    private static final AtomicLong cropBreakCount = new AtomicLong(0L);
    private static final AtomicLong wheatBreakCount = new AtomicLong(0L);
    private static final AtomicLong carrotBreakCount = new AtomicLong(0L);
    private static final AtomicLong potatoBreakCount = new AtomicLong(0L);

    private FarmStats() {
    }

    public static void addCropBreaks(long amount) {
        if (amount > 0) {
            cropBreakCount.addAndGet(amount);
        }
    }

    public static long getCropBreakCount() {
        return cropBreakCount.get();
    }

    public static void addWheatBreaks(long amount) {
        if (amount > 0) {
            wheatBreakCount.addAndGet(amount);
            cropBreakCount.addAndGet(amount);
        }
    }

    public static void addCarrotBreaks(long amount) {
        if (amount > 0) {
            carrotBreakCount.addAndGet(amount);
            cropBreakCount.addAndGet(amount);
        }
    }

    public static void addPotatoBreaks(long amount) {
        if (amount > 0) {
            potatoBreakCount.addAndGet(amount);
            cropBreakCount.addAndGet(amount);
        }
    }

    public static long getWheatBreakCount() { return wheatBreakCount.get(); }
    public static long getCarrotBreakCount() { return carrotBreakCount.get(); }
    public static long getPotatoBreakCount() { return potatoBreakCount.get(); }

    public static double estimateCoinsPerHour(long farmActiveMs) {
        if (farmActiveMs <= 0) return 0.0;
        ModConfig cfg = ModConfig.getInstance();
        double totalCoins = getWheatBreakCount() * cfg.getWheatPrice()
            + getCarrotBreakCount() * cfg.getCarrotPrice()
            + getPotatoBreakCount() * cfg.getPotatoPrice();
        return totalCoins * (3600000.0 / farmActiveMs);
    }
}
