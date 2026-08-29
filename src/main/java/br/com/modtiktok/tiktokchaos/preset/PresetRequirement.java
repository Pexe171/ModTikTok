package br.com.modtiktok.tiktokchaos.preset;

import java.util.ArrayList;
import java.util.List;

/** One required component. Any listed mod id can satisfy this component. */
public final class PresetRequirement {
    public String name = "Mod";
    public List<String> anyOfModIds = new ArrayList<>();

    public PresetRequirement() {
    }

    public PresetRequirement(String name, List<String> anyOfModIds) {
        this.name = name;
        this.anyOfModIds = new ArrayList<>(anyOfModIds);
    }

    public static PresetRequirement mod(String name, String... modIds) {
        return new PresetRequirement(name, List.of(modIds));
    }
}
