package br.com.modtiktok.tiktokchaos.rule;

import br.com.modtiktok.tiktokchaos.config.TikTokChaosConfig;
import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class RuleEngine {
    private final Map<String, Long> globalCooldowns = new HashMap<>();
    private final Map<String, Long> userCooldowns = new HashMap<>();
    private final Map<String, Integer> counters = new HashMap<>();

    public synchronized List<MatchedAction> evaluate(TikTokChaosConfig config, LiveEvent event, long now) {
        return evaluate(config, event, now, globalCooldowns, userCooldowns, counters);
    }

    public synchronized List<MatchedAction> preview(TikTokChaosConfig config, LiveEvent event, long now) {
        return evaluate(config, event, now, new HashMap<>(globalCooldowns), new HashMap<>(userCooldowns),
                new HashMap<>(counters));
    }

    private List<MatchedAction> evaluate(TikTokChaosConfig config, LiveEvent event, long now,
                                         Map<String, Long> globalState, Map<String, Long> userState,
                                         Map<String, Integer> counterState) {
        List<Rule> candidates = config.rules.stream()
                .filter(rule -> rule.enabled && rule.event == event.type() && rule.condition != null)
                .toList();

        if (event.type() == LiveEventType.GIFT) {
            List<Rule> exact = candidates.stream()
                    .filter(rule -> rule.condition.isSpecificGift() && rule.condition.matches(event))
                    .toList();
            if (!exact.isEmpty()) {
                candidates = exact;
            } else {
                candidates = candidates.stream().filter(rule -> !rule.condition.isSpecificGift()).toList();
            }
        }

        List<MatchedAction> result = new ArrayList<>();
        for (Rule rule : candidates) {
            if (!rule.condition.matches(event)) continue;

            ExecutionPlan plan = resolveExecution(rule, event);
            if (plan.actions().isEmpty()) continue;
            int triggers = calculateTriggers(rule, event, config.safety.maxTriggersPerEvent, counterState,
                    plan.repeats());
            if (triggers == 0 || !cooldownAllows(rule, event, now, globalState, userState)) {
                continue;
            }
            for (int trigger = 0; trigger < triggers; trigger++) {
                for (PlannedAction action : plan.actions()) {
                    result.add(new MatchedAction(rule.id, rule.name, event, action.action(), action.delayTicks()));
                }
            }
            markCooldown(rule, event, now, globalState, userState);
        }
        return List.copyOf(result);
    }

    public synchronized void resetSession() {
        globalCooldowns.clear();
        userCooldowns.clear();
        counters.clear();
    }

    private int calculateTriggers(Rule rule, LiveEvent event, int maxTriggers, Map<String, Integer> counterState,
                                  int plannedRepeats) {
        int triggerLimit = Math.max(1, maxTriggers);
        if (event.type() == LiveEventType.GIFT) {
            ExecutionMode mode = rule.execution == null || rule.execution.mode == null
                    ? ExecutionMode.PER_UNIT : rule.execution.mode;
            return switch (mode) {
                case PER_UNIT -> Math.min(Math.max(1, event.amount()), triggerLimit);
                case ONCE, SCALED -> 1;
                case TIERED -> Math.min(Math.max(1, plannedRepeats), triggerLimit);
            };
        }

        int threshold = Math.max(1, rule.condition.threshold);
        if (event.type() != LiveEventType.LIKE || threshold == 1) {
            return 1;
        }

        int total = counterState.getOrDefault(rule.id, 0) + Math.max(0, event.amount());
        int available = total / threshold;
        int triggers = Math.min(available, triggerLimit);
        counterState.put(rule.id, total - triggers * threshold);
        return triggers;
    }

    private ExecutionPlan resolveExecution(Rule rule, LiveEvent event) {
        ExecutionSpec execution = rule.execution == null ? new ExecutionSpec() : rule.execution;
        List<ActionSpec> actions = rule.actions == null ? List.of() : rule.actions;
        int repeats = 1;

        if (event.type() == LiveEventType.GIFT && execution.mode == ExecutionMode.TIERED) {
            ExecutionTier tier = highestTier(execution.tiers, event);
            if (tier == null) return new ExecutionPlan(List.of(), 0);
            repeats = Math.max(1, tier.repeats);
            if (tier.actions != null && !tier.actions.isEmpty()) actions = tier.actions;
        } else if (execution.roulette != null && !execution.roulette.isEmpty()) {
            WeightedChoice selected = weightedChoice(execution.roulette, rule, event);
            if (selected != null && selected.actions != null && !selected.actions.isEmpty()) {
                actions = selected.actions;
            }
        }

        boolean scaledExecution = event.type() == LiveEventType.GIFT && execution.mode == ExecutionMode.SCALED;
        if (scaledExecution) {
            ScalingSpec scaling = execution.scaling == null ? new ScalingSpec() : execution.scaling;
            actions = actions.stream().map(action -> scaling.apply(action, event)).toList();
        }
        List<PlannedAction> planned = new ArrayList<>();
        actions.forEach(action -> planned.add(new PlannedAction(action, 0)));
        if (rule.sequence != null) {
            ScalingSpec scaling = execution.scaling == null ? new ScalingSpec() : execution.scaling;
            for (SequenceStep step : rule.sequence) {
                if (step == null || step.action == null) continue;
                ActionSpec action = scaledExecution ? scaling.apply(step.action, event) : step.action;
                planned.add(new PlannedAction(action, Math.max(0, Math.min(20 * 120, step.delayTicks))));
            }
        }
        return new ExecutionPlan(List.copyOf(planned), repeats);
    }

    private ExecutionTier highestTier(List<ExecutionTier> tiers, LiveEvent event) {
        if (tiers == null) return null;
        ExecutionTier selected = null;
        for (ExecutionTier tier : tiers) {
            if (tier == null || !tier.matches(event)) continue;
            if (selected == null || tier.minCoins > selected.minCoins
                    || tier.minCoins == selected.minCoins && tier.minAmount > selected.minAmount) {
                selected = tier;
            }
        }
        return selected;
    }

    private WeightedChoice weightedChoice(List<WeightedChoice> choices, Rule rule, LiveEvent event) {
        long total = 0;
        for (WeightedChoice choice : choices) {
            if (choice != null && choice.actions != null && !choice.actions.isEmpty()) {
                total += Math.max(1, choice.weight);
            }
        }
        if (total <= 0) return null;
        Random deterministic = new Random(31L * event.id().hashCode() + rule.id.hashCode());
        long selected = Math.floorMod(deterministic.nextLong(), total);
        for (WeightedChoice choice : choices) {
            if (choice == null || choice.actions == null || choice.actions.isEmpty()) continue;
            selected -= Math.max(1, choice.weight);
            if (selected < 0) return choice;
        }
        return null;
    }

    private boolean cooldownAllows(Rule rule, LiveEvent event, long now, Map<String, Long> globalState,
                                   Map<String, Long> userState) {
        long globalUntil = globalState.getOrDefault(rule.id, 0L);
        if (globalUntil > now) {
            return false;
        }
        String userKey = rule.id + ':' + event.userId() + ':' + event.userName();
        return userState.getOrDefault(userKey, 0L) <= now;
    }

    private void markCooldown(Rule rule, LiveEvent event, long now, Map<String, Long> globalState,
                              Map<String, Long> userState) {
        if (rule.cooldownMillis > 0) {
            globalState.put(rule.id, now + rule.cooldownMillis);
        }
        if (rule.perUserCooldownMillis > 0) {
            String userKey = rule.id + ':' + event.userId() + ':' + event.userName();
            userState.put(userKey, now + rule.perUserCooldownMillis);
        }
    }

    public record MatchedAction(String ruleId, String ruleName, LiveEvent event, ActionSpec action, int delayTicks) {
        public MatchedAction(String ruleId, String ruleName, LiveEvent event, ActionSpec action) {
            this(ruleId, ruleName, event, action, 0);
        }
    }

    private record PlannedAction(ActionSpec action, int delayTicks) {
    }

    private record ExecutionPlan(List<PlannedAction> actions, int repeats) {
    }
}
