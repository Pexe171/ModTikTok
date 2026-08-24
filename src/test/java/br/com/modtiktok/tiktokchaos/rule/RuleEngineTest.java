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
        assertEquals("gift_777", actions.get(0).ruleId());
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

    @Test
    void previewDoesNotConsumeLikeCountersOrCooldowns() {
        RuleEngine engine = new RuleEngine();
        TikTokChaosConfig config = TikTokChaosConfig.defaults();

        assertTrue(engine.preview(config, LiveEvent.like("Ana", 60), 1_000).isEmpty());
        assertTrue(engine.evaluate(config, LiveEvent.like("Ana", 40), 1_100).isEmpty());
        assertEquals(1, engine.evaluate(config, LiveEvent.like("Ana", 60), 1_200).size());

        LiveEvent command = LiveEvent.comment("Ana", "!zumbi");
        assertEquals(1, engine.preview(config, command, 2_000).size());
        assertEquals(1, engine.evaluate(config, command, 2_000).size());
    }

    @Test
    void supportsOnceTieredAndScaledGiftExecution() {
        RuleEngine engine = new RuleEngine();
        TikTokChaosConfig config = TikTokChaosConfig.defaults();
        config.rules.clear();
        Rule rule = new Rule("combo", "Combo", LiveEventType.GIFT, new RuleCondition(), 0, 0,
                List.of(ActionSpec.spawn("minecraft:zombie", 1)));
        config.rules.add(rule);

        rule.execution.mode = ExecutionMode.ONCE;
        assertEquals(1, engine.evaluate(config, LiveEvent.gift("Ana", 1, "Rosa", 1, 10), 1_000).size());

        rule.execution.mode = ExecutionMode.TIERED;
        rule.execution.tiers = List.of(
                new ExecutionTier(1, 0, 1, List.of(ActionSpec.spawn("minecraft:zombie", 1))),
                new ExecutionTier(3, 3, 1, List.of(ActionSpec.spawn("minecraft:skeleton", 1)))
        );
        List<RuleEngine.MatchedAction> tiered = engine.evaluate(config,
                LiveEvent.gift("Ana", 1, "Rosa", 1, 3), 2_000);
        assertEquals("minecraft:skeleton", tiered.get(0).action().target);

        rule.execution.mode = ExecutionMode.SCALED;
        rule.execution.scaling = new ScalingSpec();
        List<RuleEngine.MatchedAction> scaled = engine.evaluate(config,
                LiveEvent.gift("Ana", 1, "Rosa", 1, 3), 3_000);
        assertEquals(3, scaled.get(0).action().amount);
    }

    @Test
    void weightedRouletteIsDeterministicForPreviewAndExecution() {
        RuleEngine engine = new RuleEngine();
        TikTokChaosConfig config = TikTokChaosConfig.defaults();
        config.rules.clear();
        Rule rule = new Rule("roulette", "Roleta", LiveEventType.GIFT, new RuleCondition(), 0, 0,
                List.of());
        rule.execution.mode = ExecutionMode.ONCE;
        rule.execution.roulette = List.of(
                new WeightedChoice("Comida", 1, List.of(ActionSpec.give("minecraft:bread", 1))),
                new WeightedChoice("Mob", 3, List.of(ActionSpec.spawn("minecraft:zombie", 1)))
        );
        config.rules.add(rule);
        LiveEvent event = LiveEvent.gift("Ana", 1, "Rosa", 1, 1);

        String previewTarget = engine.preview(config, event, 1_000).get(0).action().target;
        String executedTarget = engine.evaluate(config, event, 1_000).get(0).action().target;

        assertEquals(previewTarget, executedTarget);
    }

    @Test
    void schedulesBoundedSequenceStepsWithoutLosingImmediateActions() {
        RuleEngine engine = new RuleEngine();
        TikTokChaosConfig config = TikTokChaosConfig.defaults();
        config.rules.clear();
        Rule rule = new Rule("show", "Show", LiveEventType.FOLLOW, new RuleCondition(), 0, 0,
                List.of(ActionSpec.simple(ActionType.PARTICLE_BURST)));
        rule.sequence.add(new SequenceStep(40, ActionSpec.simple(ActionType.CENTER_MESSAGE)));
        config.rules.add(rule);

        List<RuleEngine.MatchedAction> actions = engine.evaluate(config,
                LiveEvent.simple(LiveEventType.FOLLOW, "Ana"), 1_000);

        assertEquals(2, actions.size());
        assertEquals(0, actions.get(0).delayTicks());
        assertEquals(40, actions.get(1).delayTicks());
    }
}
