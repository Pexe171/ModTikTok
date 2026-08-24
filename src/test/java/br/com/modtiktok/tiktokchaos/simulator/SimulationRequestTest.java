package br.com.modtiktok.tiktokchaos.simulator;

import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulationRequestTest {
    @Test
    void threeRosesCarryAmountAndTotalCoinValueSeparately() {
        SimulationRequest request = new SimulationRequest("Ana", LiveEventType.GIFT, 5655, "Rosa", 1,
                3, 0, "");

        LiveEvent event = request.toEvent();

        assertEquals(3, event.amount());
        assertEquals(3, event.giftValue());
    }
}
