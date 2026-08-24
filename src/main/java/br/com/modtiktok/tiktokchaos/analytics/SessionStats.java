package br.com.modtiktok.tiktokchaos.analytics;

import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** In-memory analytics. Nothing in this class is serialized to configuration. */
public final class SessionStats {
    private final EnumMap<LiveEventType, Long> eventCounts = new EnumMap<>(LiveEventType.class);
    private final Map<String, MutableViewer> viewers = new HashMap<>();
    private long startedAt = System.currentTimeMillis();
    private long coins;
    private long likes;
    private long gifts;
    private long queuedActions;
    private long executedActions;
    private long failedActions;
    private long droppedActions;

    public synchronized void reset() {
        eventCounts.clear();
        viewers.clear();
        startedAt = System.currentTimeMillis();
        coins = 0;
        likes = 0;
        gifts = 0;
        queuedActions = 0;
        executedActions = 0;
        failedActions = 0;
        droppedActions = 0;
    }

    public synchronized void recordEvent(LiveEvent event) {
        eventCounts.merge(event.type(), 1L, Long::sum);
        MutableViewer viewer = viewer(event);
        viewer.events++;
        switch (event.type()) {
            case GIFT -> {
                gifts += Math.max(1, event.amount());
                coins += event.giftValue();
                viewer.gifts += Math.max(1, event.amount());
                viewer.coins += event.giftValue();
            }
            case LIKE -> {
                likes += event.amount();
                viewer.likes += event.amount();
            }
            default -> {
            }
        }
    }

    public synchronized void recordQueued(LiveEvent event) {
        queuedActions++;
        viewer(event).actions++;
    }

    public synchronized void recordDropped() {
        droppedActions++;
    }

    public synchronized void recordExecuted(boolean success) {
        if (success) executedActions++;
        else failedActions++;
    }

    public synchronized void recordViewerMobDefeated(String viewerName) {
        if (viewerName == null || viewerName.isBlank()) return;
        String key = viewerName.toLowerCase(Locale.ROOT);
        viewers.computeIfAbsent(key, ignored -> new MutableViewer(viewerName)).mobsDefeated++;
    }

    public synchronized Snapshot snapshot(List<GoalSpec> goals, boolean hideViewerNames) {
        List<ViewerRank> ranking = viewers.values().stream()
                .sorted(Comparator.comparingLong(MutableViewer::score).reversed()
                        .thenComparing(viewer -> viewer.name.toLowerCase(Locale.ROOT)))
                .limit(10)
                .map(viewer -> new ViewerRank(hideViewerNames ? anonymize(viewer.name) : viewer.name,
                        viewer.coins, viewer.gifts, viewer.likes, viewer.actions, viewer.mobsDefeated,
                        viewer.score()))
                .toList();
        List<GoalProgress> progress = new ArrayList<>();
        if (goals != null) {
            for (GoalSpec goal : goals) {
                if (goal == null || !goal.enabled || goal.metric == null) continue;
                long current = metric(goal.metric);
                progress.add(new GoalProgress(goal.id, goal.name, goal.metric, current, goal.target,
                        current >= goal.target));
            }
        }
        return new Snapshot(startedAt, coins, likes, gifts, eventCounts.getOrDefault(LiveEventType.FOLLOW, 0L),
                eventCounts.getOrDefault(LiveEventType.SHARE, 0L), queuedActions, executedActions, failedActions,
                droppedActions, List.copyOf(progress), ranking);
    }

    private long metric(GoalMetric metric) {
        return switch (metric) {
            case COINS -> coins;
            case LIKES -> likes;
            case GIFTS -> gifts;
            case ACTIONS -> executedActions;
            case FOLLOWS -> eventCounts.getOrDefault(LiveEventType.FOLLOW, 0L);
            case SHARES -> eventCounts.getOrDefault(LiveEventType.SHARE, 0L);
        };
    }

    private MutableViewer viewer(LiveEvent event) {
        String key = event.userId().isBlank() ? event.userName().toLowerCase(Locale.ROOT) : event.userId();
        return viewers.computeIfAbsent(key, ignored -> new MutableViewer(event.userName()));
    }

    private static String anonymize(String name) {
        if (name == null || name.isBlank()) return "Espectador";
        int visible = Math.min(2, name.length());
        return name.substring(0, visible) + "***";
    }

    private static final class MutableViewer {
        private final String name;
        private long events;
        private long coins;
        private long gifts;
        private long likes;
        private long actions;
        private long mobsDefeated;

        private MutableViewer(String name) {
            this.name = name;
        }

        private long score() {
            return coins * 100L + gifts * 10L + actions * 5L + mobsDefeated * 3L + likes + events;
        }
    }

    public record GoalProgress(String id, String name, GoalMetric metric, long current, long target,
                               boolean complete) {
    }

    public record ViewerRank(String name, long coins, long gifts, long likes, long actions, long mobsDefeated,
                             long score) {
    }

    public record Snapshot(long startedAt, long coins, long likes, long gifts, long follows, long shares,
                           long queuedActions, long executedActions, long failedActions, long droppedActions,
                           List<GoalProgress> goals, List<ViewerRank> ranking) {
    }
}
