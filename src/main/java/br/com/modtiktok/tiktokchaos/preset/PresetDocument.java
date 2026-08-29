package br.com.modtiktok.tiktokchaos.preset;

import br.com.modtiktok.tiktokchaos.config.TikTokChaosConfig;
import br.com.modtiktok.tiktokchaos.rule.Rule;

import java.util.ArrayList;
import java.util.List;

/** Portable preset data. Connection details and viewer data are intentionally absent. */
public final class PresetDocument {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public int schemaVersion = CURRENT_SCHEMA_VERSION;
    public String id = "preset";
    public String name = "Preset";
    public String description = "";
    public String category = "general";
    public List<PresetRequirement> requirements = new ArrayList<>();
    public TikTokChaosConfig.Safety recommendedSafety;
    public List<Rule> rules = new ArrayList<>();

    public PresetDocument() {
    }

    public PresetDocument(String id, String name, String description,
                          TikTokChaosConfig.Safety recommendedSafety, List<Rule> rules) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.recommendedSafety = recommendedSafety;
        this.rules = new ArrayList<>(rules);
    }

    public PresetDocument(String id, String name, String description, String category,
                          List<PresetRequirement> requirements, TikTokChaosConfig.Safety recommendedSafety,
                          List<Rule> rules) {
        this(id, name, description, recommendedSafety, rules);
        this.category = category;
        this.requirements = new ArrayList<>(requirements);
    }
}
