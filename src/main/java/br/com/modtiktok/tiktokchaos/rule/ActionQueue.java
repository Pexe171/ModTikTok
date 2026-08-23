package br.com.modtiktok.tiktokchaos.rule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

public final class ActionQueue {
    private static final Comparator<ActionRequest> ORDER = Comparator.comparingInt(ActionRequest::priority)
            .thenComparingLong(ActionRequest::sequence);

    private final AtomicLong sequence = new AtomicLong();
    private final PriorityQueue<ActionRequest> queue = new PriorityQueue<>(ORDER);
    private int capacity;

    public ActionQueue(int capacity) {
        this.capacity = Math.max(10, capacity);
    }

    public synchronized boolean offer(String ruleId, String ruleName,
                                      br.com.modtiktok.tiktokchaos.live.LiveEvent event, ActionSpec action) {
        ActionRequest request = new ActionRequest(sequence.incrementAndGet(), ruleId, ruleName, event, action);
        if (queue.size() < capacity) {
            return queue.offer(request);
        }

        ActionRequest worst = queue.stream().max(ORDER).orElse(null);
        if (worst != null && ORDER.compare(request, worst) < 0) {
            queue.remove(worst);
            return queue.offer(request);
        }
        return false;
    }

    public synchronized ActionRequest poll() {
        return queue.poll();
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
            queue.poll();
        }
    }

    public synchronized List<ActionRequest> snapshot() {
        ArrayList<ActionRequest> copy = new ArrayList<>(queue);
        copy.sort(ORDER);
        return List.copyOf(copy);
    }
}
