package br.com.modtiktok.tiktokchaos.rule;

import br.com.modtiktok.tiktokchaos.config.TikTokChaosConfig;
import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RuleEngine {
    private final Map<String, Long> globalCooldowns = new HashMap<>();
    private final Map<String, Long> userCooldowns = new HashMap<>();
    private final Map<String, Integer> counters = new HashMap<>();

    public synchronized List<MatchedAction> evaluate(TikTokChaosConfig config, LiveEvent event, long now) {
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
            if (!rule.condition.matches(event) || rule.actions == null || rule.actions.isEmpty()) {
                continue;
            }

            int triggers = calculateTriggers(rule, event, config.safety.maxTriggersPerEvent);
            if (triggers == 0 || !cooldownAllows(rule, event, now)) {
                continue;
            }
            for (int trigger = 0; trigger < triggers; trigger++) {
                for (ActionSpec action : rule.actions) {
                    result.add(new MatchedAction(rule.id, rule.name, event, action));
                }
            }
            markCooldown(rule, event, now);
        }
        return List.copyOf(result);
    }

    public synchronized void resetSession() {
        globalCooldowns.clear();
        userCooldowns.clear();
        counters.clear();
    }

    private int calculateTriggers(Rule rule, LiveEvent event, int maxTriggers) {
        int triggerLimit = Math.max(1, maxTriggers);
        if (event.type() == LiveEventType.GIFT) {
            return Math.min(Math.max(1, event.amount()), triggerLimit);
        }

        int threshold = Math.max(1, rule.condition.threshold);
        if (event.type() != LiveEventType.LIKE || threshold == 1) {
            return 1;
        }

        int total = counters.getOrDefault(rule.id, 0) + Math.max(0, event.amount());
        int available = total / threshold;
        int triggers = Math.min(available, triggerLimit);
        counters.put(rule.id, total - triggers * threshold);
        return triggers;
    }

    private boolean cooldownAllows(Rule rule, LiveEvent event, long now) {
        long globalUntil = globalCooldowns.getOrDefault(rule.id, 0L);
        if (globalUntil > now) {
            return false;
        }
        String userKey = rule.id + ':' + event.userId() + ':' + event.userName();
        return userCooldowns.getOrDefault(userKey, 0L) <= now;
    }

    private void markCooldown(Rule rule, LiveEvent event, long now) {
        if (rule.cooldownMillis > 0) {
            globalCooldowns.put(rule.id, now + rule.cooldownMillis);
        }
        if (rule.perUserCooldownMillis > 0) {
            String userKey = rule.id + ':' + event.userId() + ':' + event.userName();
            userCooldowns.put(userKey, now + rule.perUserCooldownMillis);
        }
    }

    public record MatchedAction(String ruleId, String ruleName, LiveEvent event, ActionSpec action) {
    }
}
