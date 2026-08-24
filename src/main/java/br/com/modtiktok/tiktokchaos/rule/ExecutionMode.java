package br.com.modtiktok.tiktokchaos.rule;

public enum ExecutionMode {
    /** Repeat the complete action set for every gift in the combo. */
    PER_UNIT,
    /** Execute the complete action set once, regardless of combo size. */
    ONCE,
    /** Use only the highest matching combo/coin tier. */
    TIERED,
    /** Execute once and scale declared action fields. */
    SCALED
}
