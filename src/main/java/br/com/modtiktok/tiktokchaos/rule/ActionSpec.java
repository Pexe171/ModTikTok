package br.com.modtiktok.tiktokchaos.rule;

public final class ActionSpec {
    public ActionType type = ActionType.MESSAGE;
    public String target = "";
    public int amount = 1;
    public int durationTicks = 0;
    public int amplifier = 0;
    public int radius = 10;
    public String message = "";

    public ActionSpec() {
    }

    public ActionSpec(ActionType type, String target, int amount, int durationTicks, int amplifier, int radius,
                      String message) {
        this.type = type;
        this.target = target == null ? "" : target;
        this.amount = amount;
        this.durationTicks = durationTicks;
        this.amplifier = amplifier;
        this.radius = radius;
        this.message = message == null ? "" : message;
    }

    public static ActionSpec spawn(String entity, int amount) {
        return new ActionSpec(ActionType.SPAWN_ENTITY, entity, amount, 0, 0, 10, "");
    }

    public static ActionSpec give(String item, int amount) {
        return new ActionSpec(ActionType.GIVE_ITEM, item, amount, 0, 0, 0, "");
    }

    public static ActionSpec effect(String effect, int seconds, int amplifier) {
        return new ActionSpec(ActionType.APPLY_EFFECT, effect, 1, seconds * 20, amplifier, 0, "");
    }

    public static ActionSpec simple(ActionType type) {
        return new ActionSpec(type, "", 1, 0, 0, 10, "");
    }

    public ActionSpec copy() {
        return new ActionSpec(type, target, amount, durationTicks, amplifier, radius, message);
    }
}
