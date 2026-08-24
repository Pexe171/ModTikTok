package br.com.modtiktok.tiktokchaos;

/** Lightweight tick-drift guard that slows action execution when the client is struggling. */
public final class AdaptivePerformanceGuard {
    private long previousTickNanos;
    private double averageTickMillis = 50.0;

    public synchronized void recordTick(long nowNanos) {
        if (previousTickNanos != 0L) {
            double elapsedMillis = (nowNanos - previousTickNanos) / 1_000_000.0;
            if (elapsedMillis > 0 && elapsedMillis < 2_000) {
                averageTickMillis = averageTickMillis * 0.9 + elapsedMillis * 0.1;
            }
        }
        previousTickNanos = nowNanos;
    }

    public synchronized long actionDelayMillis(int configuredActionsPerSecond, boolean enabled) {
        int perSecond = Math.max(1, configuredActionsPerSecond);
        long base = Math.max(50L, 1_000L / perSecond);
        if (!enabled) return base;
        if (averageTickMillis >= 120.0) return Math.max(base, 1_000L);
        if (averageTickMillis >= 80.0) return Math.max(base, 500L);
        return base;
    }

    public synchronized boolean isThrottled(boolean enabled) {
        return enabled && averageTickMillis >= 80.0;
    }

    public synchronized int estimatedFps() {
        return (int) Math.round(1_000.0 / Math.max(1.0, averageTickMillis));
    }

    public synchronized void reset() {
        previousTickNanos = 0L;
        averageTickMillis = 50.0;
    }
}
