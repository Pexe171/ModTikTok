package br.com.modtiktok.tiktokchaos.rule;

public final class SequenceStep {
    public int delayTicks = 0;
    public ActionSpec action = new ActionSpec();

    public SequenceStep() {
    }

    public SequenceStep(int delayTicks, ActionSpec action) {
        this.delayTicks = delayTicks;
        this.action = action;
    }
}
