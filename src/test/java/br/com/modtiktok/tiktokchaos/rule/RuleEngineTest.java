package br.com.modtiktok.tiktokchaos.rule;

import br.com.modtiktok.tiktokchaos.config.TikTokChaosConfig;
import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineTest {
    @Test
    void accumulatesLikesAndCarriesTheRemainder() {
        RuleEngine engine = new RuleEngine();
        TikTokChaosConfig config = TikTokChaosConfig.defaults();

        assertTrue(engine.evaluate(config, LiveEvent.like("Ana", 60), 1_000).isEmpty());
        assertEquals(1, engine.evaluate(config, LiveEvent.like("Bia", 40), 1_100).size());
        assertEquals(2, engine.evaluate(config, LiveEvent.like("Caio", 250), 1_200).size());
        assertTrue(engine.evaluate(config, LiveEvent.like("Duda", 49), 1_300).isEmpty());
        assertEquals(1, engine.evaluate(config, LiveEvent.like("Eva", 1), 1_400).size());
    }

    @Test
    void exactGiftRuleOverridesValueTier() {
        RuleEngine engine = new RuleEngine();
        TikTokChaosConfig config = TikTokChaosConfig.defaults();
        RuleCondition exact = new RuleCondition();
        exact.giftId = 777;
        config.rules.add(new Rule("gift_777", "Especial", LiveEventType.GIFT, exact, 0, 0,
                List.of(ActionSpec.give("minecraft:diamond", 1))));

        List<RuleEngine.MatchedAction> actions = engine.evaluate(config,
                LiveEvent.gift("Ana", 777, "Especial", 120, 1), 2_000);

        assertEquals(1, actions.size());
        assertEquals("gift_777", actions.getFirst().ruleId());
    }

    @Test
    void appliesGlobalAndPerUserCommentCooldowns() {
        RuleEngine engine = new RuleEngine();
        TikTokChaosConfig config = TikTokChaosConfig.defaults();

        assertEquals(1, engine.evaluate(config, LiveEvent.comment("Ana", "!zumbi"), 1_000).size());
        assertTrue(engine.evaluate(config, LiveEvent.comment("Ana", "!zumbi"), 5_000).isEmpty());
        assertEquals(1, engine.evaluate(config, LiveEvent.comment("Bia", "!zumbi"), 5_000).size());
    }
}
