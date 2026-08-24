package br.com.modtiktok.tiktokchaos.rule;

import br.com.modtiktok.tiktokchaos.live.LiveEvent;

public final class ScalingSpec {
    public ScaleMetric metric = ScaleMetric.AMOUNT;
    public ScaleMode mode = ScaleMode.STEPS;
    public ScaleTarget target = ScaleTarget.ACTION_AMOUNT;
    public int startAt = 1;
    public int stepSize = 1;
    public double baseValue = 1.0;
    public double increment = 1.0;
    public int minimum = 1;
    public int maximum = 20;

    public ActionSpec apply(ActionSpec source, LiveEvent event) {
        ActionSpec scaled = source.copy();
        int input = metric == ScaleMetric.COINS ? event.giftValue() : event.amount();
        int distance = Math.max(0, input - Math.max(0, startAt));
        double steps = mode == ScaleMode.LINEAR
                ? distance / (double) Math.max(1, stepSize)
                : Math.floor(distance / (double) Math.max(1, stepSize));
        int value = (int) Math.round(baseValue + steps * increment);
        value = Math.max(minimum, Math.min(maximum, value));
        switch (target) {
            case ACTION_AMOUNT -> scaled.amount = Math.max(1, Math.min(20, value));
            case DURATION_TICKS -> scaled.durationTicks = Math.max(0, Math.min(20 * 600, value));
            case AMPLIFIER -> scaled.amplifier = Math.max(0, Math.min(10, value));
            case RADIUS -> scaled.radius = Math.max(3, Math.min(48, value));
        }
        return scaled;
    }
}
