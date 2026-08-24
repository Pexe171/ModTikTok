package br.com.modtiktok.tiktokchaos.rule;

import java.util.ArrayList;
import java.util.List;

public final class ExecutionTier {
    public int minAmount = 1;
    public int minCoins = 0;
    public int repeats = 1;
    public List<ActionSpec> actions = new ArrayList<>();

    public ExecutionTier() {
    }

    public ExecutionTier(int minAmount, int minCoins, int repeats, List<ActionSpec> actions) {
        this.minAmount = minAmount;
        this.minCoins = minCoins;
        this.repeats = repeats;
        this.actions = new ArrayList<>(actions);
    }

    public boolean matches(br.com.modtiktok.tiktokchaos.live.LiveEvent event) {
        return event.amount() >= minAmount && event.giftValue() >= minCoins;
    }
}
