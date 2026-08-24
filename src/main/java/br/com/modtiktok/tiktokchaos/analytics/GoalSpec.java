package br.com.modtiktok.tiktokchaos.analytics;

public final class GoalSpec {
    public String id = "goal";
    public String name = "Meta";
    public boolean enabled = true;
    public GoalMetric metric = GoalMetric.COINS;
    public long target = 100;

    public GoalSpec() {
    }

    public GoalSpec(String id, String name, GoalMetric metric, long target) {
        this.id = id;
        this.name = name;
        this.metric = metric;
        this.target = target;
    }
}
