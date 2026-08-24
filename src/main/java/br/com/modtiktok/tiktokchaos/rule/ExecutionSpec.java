package br.com.modtiktok.tiktokchaos.rule;

import java.util.ArrayList;
import java.util.List;

public final class ExecutionSpec {
    public ExecutionMode mode = ExecutionMode.PER_UNIT;
    public List<ExecutionTier> tiers = new ArrayList<>();
    public ScalingSpec scaling = new ScalingSpec();
    public List<WeightedChoice> roulette = new ArrayList<>();
}
