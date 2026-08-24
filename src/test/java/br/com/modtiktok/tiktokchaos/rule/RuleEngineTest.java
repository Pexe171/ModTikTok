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
    void repeatsGiftRuleForEveryComboUnit() {
        RuleEngine engine = new RuleEngine();
        TikTokChaosConfig config = TikTokChaosConfig.defaults();

        List<RuleEngine.MatchedAction> actions = engine.evaluate(config,
                LiveEvent.gift("Ana", 5655, "Rosa", 1, 3), 2_000);

        assertEquals(3, actions.size());
        assertTrue(actions.stream().allMatch(action -> action.ruleId().equals("gift_small")));
        assertTrue(actions.stream().allMatch(action -> action.event().amount() == 3));
    }

    @Test
    void repeatsTheCompleteActionSetAndCapsLargeGiftCombos() {
        RuleEngine engine = new RuleEngine();
        TikTokChaosConfig config = TikTokChaosConfig.defaults();
        config.rules.clear();
        config.safety.maxTriggersPerEvent = 3;
        config.rules.add(new Rule("gift_combo", "Combo", LiveEventType.GIFT, new RuleCondition(), 0, 0,
                List.of(ActionSpec.spawn("minecraft:zombie", 1), ActionSpec.give("minecraft:bread", 1))));

        List<RuleEngine.MatchedAction> actions = engine.evaluate(config,
                LiveEvent.gift("Ana", 5655, "Rosa", 1, 10), 2_000);

        assertEquals(6, actions.size());
        assertEquals(List.of(ActionType.SPAWN_ENTITY, ActionType.GIVE_ITEM,
                        ActionType.SPAWN_ENTITY, ActionType.GIVE_ITEM,
                        ActionType.SPAWN_ENTITY, ActionType.GIVE_ITEM),
                actions.stream().map(action -> action.action().type).toList());
    }

    @Test
    void appliesGiftCooldownOncePerIncomingCombo() {
        RuleEngine engine = new RuleEngine();
        TikTokChaosConfig config = TikTokChaosConfig.defaults();
        config.rules.clear();
        config.rules.add(new Rule("gift_combo", "Combo", LiveEventType.GIFT, new RuleCondition(), 1_000, 0,
                List.of(ActionSpec.spawn("minecraft:zombie", 1))));

        assertEquals(3, engine.evaluate(config,
                LiveEvent.gift("Ana", 5655, "Rosa", 1, 3), 1_000).size());
        assertTrue(engine.evaluate(config,
                LiveEvent.gift("Bia", 5655, "Rosa", 1, 3), 1_500).isEmpty());
        assertEquals(3, engine.evaluate(config,
                LiveEvent.gift("Bia", 5655, "Rosa", 1, 3), 2_000).size());
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
