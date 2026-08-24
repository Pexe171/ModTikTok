package br.com.modtiktok.tiktokchaos;

/** Controls whether incoming LIVE events are allowed to create gameplay actions. */
public enum ActionRunState {
    ACTIVE("ATIVO"),
    PAUSED("PAUSADO");

    private final String label;

    ActionRunState(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
