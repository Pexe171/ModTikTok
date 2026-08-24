package br.com.modtiktok.tiktokchaos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptivePerformanceGuardTest {
    @Test
    void throttlesAfterSustainedSlowTicksAndCanBeDisabled() {
        AdaptivePerformanceGuard guard = new AdaptivePerformanceGuard();
        long now = 1_000_000_000L;
        guard.recordTick(now);
        for (int index = 0; index < 20; index++) {
            now += 150_000_000L;
            guard.recordTick(now);
        }

        assertTrue(guard.isThrottled(true));
        assertTrue(guard.actionDelayMillis(20, true) >= 500);
        assertFalse(guard.isThrottled(false));
        assertEquals(50, guard.actionDelayMillis(20, false));
    }
}
