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
}
