package br.com.modtiktok.tiktokchaos.rule;

import br.com.modtiktok.tiktokchaos.live.LiveEvent;

public record ActionRequest(long sequence, String ruleId, String ruleName, LiveEvent event, ActionSpec action,
                            long scheduledAtMillis, boolean simulated) {
    public int priority() {
        return event.priority();
    }
}
