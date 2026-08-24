package br.com.modtiktok.tiktokchaos.analytics;

import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStatsTest {
    @Test
    void aggregatesSessionGoalsAndRanksViewersWithoutPersistence() {
        SessionStats stats = new SessionStats();
        LiveEvent ana = LiveEvent.gift("Ana", 5655, "Rosa", 1, 3);
        LiveEvent bia = LiveEvent.gift("Bia", 5655, "Rosa", 10, 1);
        stats.recordEvent(ana);
        stats.recordQueued(ana);
        stats.recordEvent(bia);
        stats.recordQueued(bia);
        stats.recordExecuted(true);
        GoalSpec goal = new GoalSpec("coins", "10 moedas", GoalMetric.COINS, 10);

        SessionStats.Snapshot snapshot = stats.snapshot(List.of(goal), false);

        assertEquals(13, snapshot.coins());
        assertEquals(4, snapshot.gifts());
        assertTrue(snapshot.goals().get(0).complete());
        assertEquals("Bia", snapshot.ranking().get(0).name());
        assertEquals(2, snapshot.queuedActions());
        assertEquals(1, snapshot.executedActions());
    }

    @Test
    void canHideViewerNamesAndResetTheSession() {
        SessionStats stats = new SessionStats();
        stats.recordEvent(LiveEvent.gift("Fernanda", 1, "Rosa", 1, 1));

        assertEquals("Fe***", stats.snapshot(List.of(), true).ranking().get(0).name());
        stats.reset();
        assertTrue(stats.snapshot(List.of(), false).ranking().isEmpty());
        assertEquals(0, stats.snapshot(List.of(), false).coins());
    }
}
