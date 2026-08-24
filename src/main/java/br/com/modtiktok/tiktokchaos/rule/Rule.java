package br.com.modtiktok.tiktokchaos.rule;

import br.com.modtiktok.tiktokchaos.live.LiveEventType;

import java.util.ArrayList;
import java.util.List;

public final class Rule {
    public String id = "rule";
    public String name = "Regra";
    public boolean enabled = true;
    public LiveEventType event = LiveEventType.COMMENT;
    public RuleCondition condition = new RuleCondition();
    public long cooldownMillis = 0;
    public long perUserCooldownMillis = 0;
    public ExecutionSpec execution = new ExecutionSpec();
    public List<ActionSpec> actions = new ArrayList<>();
    public List<SequenceStep> sequence = new ArrayList<>();

    public Rule() {
    }

    public Rule(String id, String name, LiveEventType event, RuleCondition condition, long cooldownMillis,
                long perUserCooldownMillis, List<ActionSpec> actions) {
        this.id = id;
        this.name = name;
        this.event = event;
        this.condition = condition;
        this.cooldownMillis = cooldownMillis;
        this.perUserCooldownMillis = perUserCooldownMillis;
        this.actions = new ArrayList<>(actions);
    }
}
