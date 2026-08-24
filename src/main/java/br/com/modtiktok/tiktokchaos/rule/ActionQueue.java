package br.com.modtiktok.tiktokchaos.rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class ActionQueue {
    private static final Comparator<ActionRequest> ORDER = Comparator.comparingInt(ActionRequest::priority)
            .thenComparingLong(ActionRequest::scheduledAtMillis)
            .thenComparingLong(ActionRequest::sequence);

    private final AtomicLong sequence = new AtomicLong();
    private final PriorityQueue<ActionRequest> queue = new PriorityQueue<>(ORDER);
    private int capacity;

    public ActionQueue(int capacity) {
        this.capacity = Math.max(10, capacity);
    }

    public synchronized boolean offer(String ruleId, String ruleName,
                                      br.com.modtiktok.tiktokchaos.live.LiveEvent event, ActionSpec action) {
        return offer(ruleId, ruleName, event, action, false);
    }

    public synchronized boolean offer(String ruleId, String ruleName,
                                      br.com.modtiktok.tiktokchaos.live.LiveEvent event, ActionSpec action,
                                      boolean simulated) {
        return offer(ruleId, ruleName, event, action, simulated, 0);
    }

    public synchronized boolean offer(String ruleId, String ruleName,
                                      br.com.modtiktok.tiktokchaos.live.LiveEvent event, ActionSpec action,
                                      boolean simulated, int delayTicks) {
        long scheduledAt = System.currentTimeMillis() + Math.max(0, Math.min(20 * 120, delayTicks)) * 50L;
        ActionRequest request = new ActionRequest(sequence.incrementAndGet(), ruleId, ruleName, event, action,
                scheduledAt, simulated);
        return offer(queue, request, capacity);
    }

    public synchronized ActionRequest poll() {
        return queue.poll();
    }

    public synchronized ActionRequest pollReady(long nowMillis) {
        ActionRequest ready = queue.stream()
                .filter(request -> request.scheduledAtMillis() <= nowMillis)
                .min(ORDER)
                .orElse(null);
        if (ready != null) queue.remove(ready);
        return ready;
    }

    public synchronized int size() {
        return queue.size();
    }

    public synchronized void clear() {
        queue.clear();
    }

    public synchronized void setCapacity(int capacity) {
        this.capacity = Math.max(10, capacity);
        while (queue.size() > this.capacity) {
            ActionRequest worst = queue.stream().max(ORDER).orElse(null);
            if (worst == null) break;
            queue.remove(worst);
        }
    }

    public synchronized List<ActionRequest> snapshot() {
        ArrayList<ActionRequest> copy = new ArrayList<>(queue);
        copy.sort(ORDER);
        return List.copyOf(copy);
    }

    public synchronized List<ActionRequest> preview(List<RuleEngine.MatchedAction> matches) {
        PriorityQueue<ActionRequest> simulated = new PriorityQueue<>(ORDER);
        simulated.addAll(queue);
        long firstPreviewSequence = sequence.get() + 1;
        long nextSequence = firstPreviewSequence;
        long now = System.currentTimeMillis();
        for (RuleEngine.MatchedAction match : matches) {
            offer(simulated, new ActionRequest(nextSequence++, match.ruleId(), match.ruleName(), match.event(),
                    match.action(), now + Math.max(0, match.delayTicks()) * 50L, true), capacity);
        }
        ArrayList<ActionRequest> accepted = new ArrayList<>();
        for (ActionRequest request : simulated) {
            if (request.sequence() >= firstPreviewSequence) accepted.add(request);
        }
        accepted.sort(ORDER);
        return List.copyOf(accepted);
    }

    private static boolean offer(PriorityQueue<ActionRequest> target, ActionRequest request, int capacity) {
        if (target.size() < capacity) return target.offer(request);
        ActionRequest worst = target.stream().max(ORDER).orElse(null);
        if (worst != null && ORDER.compare(request, worst) < 0) {
            target.remove(worst);
            return target.offer(request);
        }
        return false;
    }
}
