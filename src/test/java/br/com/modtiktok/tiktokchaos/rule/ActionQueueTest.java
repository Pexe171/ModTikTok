package br.com.modtiktok.tiktokchaos.rule;

import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionQueueTest {
    @Test
    void prioritizesGiftsOverLikes() {
        ActionQueue queue = new ActionQueue(10);
        ActionSpec action = ActionSpec.spawn("minecraft:zombie", 1);
        queue.offer("like", "Like", LiveEvent.like("Ana", 100), action);
        queue.offer("gift", "Gift", LiveEvent.gift("Bia", 1, "Rosa", 1, 1), action);

        assertEquals(LiveEventType.GIFT, queue.poll().event().type());
        assertEquals(LiveEventType.LIKE, queue.poll().event().type());
    }

    @Test
    void replacesWorstEventWhenFull() {
        ActionQueue queue = new ActionQueue(10);
        ActionSpec action = ActionSpec.spawn("minecraft:zombie", 1);
        for (int index = 0; index < 10; index++) {
            assertTrue(queue.offer("join", "Entrada", LiveEvent.simple(LiveEventType.JOIN, "U" + index), action));
        }
        assertTrue(queue.offer("gift", "Presente", LiveEvent.gift("VIP", 9, "Gift", 100, 1), action));
        assertFalse(queue.offer("join", "Entrada", LiveEvent.simple(LiveEventType.JOIN, "Tarde"), action));
        assertEquals(LiveEventType.GIFT, queue.poll().event().type());
        assertEquals(10, queue.size() + 1);
    }

    @Test
    void shrinkingCapacityKeepsTheHighestPriorityEvents() {
        ActionQueue queue = new ActionQueue(20);
        ActionSpec action = ActionSpec.spawn("minecraft:zombie", 1);
        for (int index = 0; index < 10; index++) {
            queue.offer("like", "Like", LiveEvent.like("L" + index, 1), action);
            queue.offer("gift", "Gift", LiveEvent.gift("G" + index, index, "Rosa", 1, 1), action);
        }

        queue.setCapacity(10);

        assertEquals(10, queue.size());
        assertTrue(queue.snapshot().stream().allMatch(request -> request.event().type() == LiveEventType.GIFT));
    }

    @Test
    void previewAppliesCapacityWithoutMutatingTheRealQueue() {
        ActionQueue queue = new ActionQueue(10);
        ActionSpec action = ActionSpec.spawn("minecraft:zombie", 1);
        for (int index = 0; index < 10; index++) {
            queue.offer("like", "Like", LiveEvent.like("L" + index, 1), action);
        }
        RuleEngine.MatchedAction gift = new RuleEngine.MatchedAction("gift", "Gift",
                LiveEvent.gift("Ana", 1, "Rosa", 1, 1), action);

        assertEquals(1, queue.preview(java.util.List.of(gift)).size());
        assertEquals(10, queue.size());
        assertTrue(queue.snapshot().stream().allMatch(request -> request.event().type() == LiveEventType.LIKE));
    }

    @Test
    void delayedSequenceStepsDoNotBlockReadyLowerPriorityActions() {
        ActionQueue queue = new ActionQueue(10);
        ActionSpec action = ActionSpec.simple(ActionType.PARTICLE_BURST);
        long now = System.currentTimeMillis();
        queue.offer("gift", "Gift futuro", LiveEvent.gift("Ana", 1, "Rosa", 1, 1), action, false, 40);
        queue.offer("like", "Like agora", LiveEvent.like("Bia", 100), action, false, 0);

        assertEquals(LiveEventType.LIKE, queue.pollReady(now + 100).event().type());
        assertEquals(null, queue.pollReady(now + 1_000));
        assertEquals(LiveEventType.GIFT, queue.pollReady(now + 3_000).event().type());
    }
}
