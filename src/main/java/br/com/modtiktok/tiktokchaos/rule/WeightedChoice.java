package br.com.modtiktok.tiktokchaos.rule;

import java.util.ArrayList;
import java.util.List;

public final class WeightedChoice {
    public String name = "Opção";
    public int weight = 1;
    public List<ActionSpec> actions = new ArrayList<>();

    public WeightedChoice() {
    }

    public WeightedChoice(String name, int weight, List<ActionSpec> actions) {
        this.name = name;
        this.weight = weight;
        this.actions = new ArrayList<>(actions);
    }
}
