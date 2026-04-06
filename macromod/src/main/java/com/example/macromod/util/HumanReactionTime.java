package com.example.macromod.util;

import java.util.Random;

/**
 * Human reaction time simulation system.
 * Provides realistic random delays to mimic human behavior across all automation.
 *
 * <h3>Reaction Time Profiles</h3>
 * <ul>
 *   <li><b>FAST</b>: 50–150 ms — seasoned player, anticipatory response</li>
 *   <li><b>NORMAL</b>: 100–300 ms — typical player reaction time</li>
 *   <li><b>SLOW</b>: 200–500 ms — conservative, safety-focused</li>
 *   <li><b>ADAPTIVE</b>: varies dynamically based on context</li>
 * </ul>
 *
 * <h3>Distribution</h3>
 * Uses a gaussian-like distribution (skewed towards lower latencies) to simulate
 * reactive vs anticipatory behavior, avoiding uniform randomness which looks "botty".
 */
public final class HumanReactionTime {

    private static final Random RNG = new Random();

    /** Reaction time profile for different automation types. */
    public enum ReactionProfile {
        /** 50–150 ms: Experienced players, anticipatory clicks. */
        FAST(50, 150),
        /** 100–300 ms: Normal players, balanced awareness. */
        NORMAL(100, 300),
        /** 200–500 ms: Cautious players, safety-focused. */
        SLOW(200, 500),
        /** Fishing: 0–250 ms with concentration bump for delayed bites. */
        FISHING(0, 250);

        public final int minMs;
        public final int maxMs;

        ReactionProfile(int minMs, int maxMs) {
            this.minMs = minMs;
            this.maxMs = maxMs;
        }
    }

    private HumanReactionTime() {}

    /**
     * Get a human-like reaction delay using gaussian distribution.
     * Results in mostly fast responses with occasional slow ones (realistic).
     */
    public static long getReactionDelay(ReactionProfile profile) {
        return getReactionDelay(profile.minMs, profile.maxMs);
    }

    /**
     * Get a reaction delay between min and max using gaussian distribution.
     * Values are weighted towards the lower end (faster responses).
     */
    public static long getReactionDelay(int minMs, int maxMs) {
        // Gaussian with mean at 1/3 of range (favor faster responses)
        int range = maxMs - minMs;
        double gaussian = Math.abs(RNG.nextGaussian() * 0.4); // σ=0.4
        int delay = (int) (minMs + Math.min(gaussian * range, range));
        return Math.max(minMs, Math.min(delay, maxMs));
    }

    /**
     * Get a random jitter in milliseconds for breaking up timing patterns.
     * Useful for adding variance to periodic actions.
     * Range: -50 to +50 ms
     */
    public static long getTimingJitter() {
        return getRandom(-50, 50);
    }

    /**
     * Get a random value between min and max (inclusive).
     */
    public static long getRandom(int minMs, int maxMs) {
        if (minMs == maxMs) return minMs;
        return minMs + RNG.nextInt(maxMs - minMs + 1);
    }

    /**
     * Get a random value between 0 and max (inclusive).
     */
    public static long getRandom(int maxMs) {
        return getRandom(0, maxMs);
    }

    /**
     * Check if an action should trigger based on elapsed time and human reaction variability.
     * Simulates "not quite ready yet" scenarios.
     *
     * @param elapsedMs   Time elapsed since action became available
     * @param targetDelayMs Expected delay for this action
     * @return true if action should trigger now
     */
    public static boolean shouldTriggerWithReactionTime(long elapsedMs, long targetDelayMs) {
        // 30% chance of being "slow" (not ready yet)
        if (RNG.nextDouble() < 0.30 && elapsedMs < targetDelayMs * 0.7) {
            return false;
        }
        return elapsedMs >= targetDelayMs;
    }

    /**
     * Get adaptive reaction time based on situation intensity.
     * - Low intensity: slower, more relaxed
     * - High intensity: faster, more reactive
     *
     * @param intensityFraction 0.0 (relaxed) to 1.0 (intense)
     * @return Reaction delay in milliseconds
     */
    public static long getAdaptiveReactionTime(double intensityFraction) {
        // Clamp
        intensityFraction = Math.max(0.0, Math.min(1.0, intensityFraction));

        // Blend profiles: SLOW at 0, NORMAL in middle, FAST at 1
        if (intensityFraction < 0.5) {
            // Slow to Normal
            int lerpMs = (int) (ReactionProfile.SLOW.maxMs * (1.0 - intensityFraction * 2)
                    + ReactionProfile.NORMAL.minMs * (intensityFraction * 2));
            return getReactionDelay(ReactionProfile.SLOW.minMs, lerpMs);
        } else {
            // Normal to Fast
            int lerpMs = (int) (ReactionProfile.NORMAL.maxMs * (2.0 - intensityFraction * 2)
                    + ReactionProfile.FAST.maxMs * ((intensityFraction - 0.5) * 2));
            return getReactionDelay(ReactionProfile.FAST.minMs, lerpMs);
        }
    }

    /**
     * Get reaction delay for fishing actions specifically.
     * Fishing has natural delays (fish taking time to bite, player's awareness cycle).
     *
     * @param isBiteDetected whether a fish bite has been detected
     * @return Delay in milliseconds before reacting to bite
     */
    public static long getFishingReactionTime(boolean isBiteDetected) {
        if (isBiteDetected) {
            // Player reacts quickly to detected bite: 50–200 ms
            return getReactionDelay(50, 200);
        } else {
            // Idle waiting time: natural pause between casts
            return getReactionDelay(300, 800);
        }
    }

    /**
     * Get mining delay with human-like variance.
     * Accounts for: dig cooldown, decision time, placement precision.
     *
     * @param baseMiningDelayMs Base mining delay from config
     * @return Actual delay to use this tick
     */
    public static long getMiningReactionTime(int baseMiningDelayMs) {
        if (baseMiningDelayMs <= 0) return 0;

        // Add reaction variance on top of base
        long reactionVariance = getReactionDelay(ReactionProfile.NORMAL);
        return baseMiningDelayMs + reactionVariance;
    }

    /**
     * Get attack interval with human-like jitter.
     * Prevents perfectly timed attacks that look bot-like.
     *
     * @param baseIntervalMs Base attack cooldown
     * @param jitterPercent How much to vary (0–100%)
     * @return Actual interval for this attack cycle
     */
    public static long getAttackIntervalWithJitter(long baseIntervalMs, int jitterPercent) {
        if (jitterPercent <= 0) return baseIntervalMs;

        int jitterAmount = (int) ((baseIntervalMs * jitterPercent) / 100);
        long jitter = getRandom(-jitterAmount, jitterAmount);
        return Math.max(baseIntervalMs / 2, baseIntervalMs + jitter);
    }

    /**
     * Creates a tick-based random delay that feels natural.
     * For state machine delays (cast→wait→reel→delay→cast).
     *
     * @param profile Reaction profile to use
     * @return Delay in milliseconds
     */
    public static long getStateChangeDelay(ReactionProfile profile) {
        return getReactionDelay(profile);
    }
}
