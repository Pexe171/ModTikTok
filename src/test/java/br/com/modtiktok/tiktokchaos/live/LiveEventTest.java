package br.com.modtiktok.tiktokchaos.live;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LiveEventTest {
    @Test
    void normalizesTextAndNonNegativeNumbers() {
        LiveEvent event = new LiveEvent("", LiveEventType.COMMENT, 0, null, "  Ana\u0000  ", -5, -9,
                1, null, -10, "  !ZuMbI  ");

        assertFalse(event.id().isBlank());
        assertEquals("Ana", event.userName());
        assertEquals(0, event.amount());
        assertEquals(0, event.giftValue());
        assertEquals("!zumbi", event.normalizedComment());
    }
}
