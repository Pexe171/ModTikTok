package br.com.modtiktok.tiktokchaos.preset;

import br.com.modtiktok.tiktokchaos.config.TikTokChaosConfig;
import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import br.com.modtiktok.tiktokchaos.rule.RuleEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetManagerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void exposesTheFourSafeBuiltInPresets() {
        PresetManager manager = manager();

        assertTrue(manager.catalog().stream().anyMatch(preset -> preset.id.equals("survival-chaos")));
        assertTrue(manager.catalog().stream().anyMatch(preset -> preset.id.equals("zombie-apocalypse")));
        assertTrue(manager.catalog().stream().anyMatch(preset -> preset.id.equals("safe-rewards")));
        assertTrue(manager.catalog().stream().anyMatch(preset -> preset.id.equals("hardcore-night")));
    }

    @Test
    void exposesTenPopularModPresetsWithExplicitRequirements() {
        PresetManager manager = manager();

        var popular = manager.catalog().stream()
                .filter(preset -> preset.category.equals("popular-mods"))
                .toList();

        assertEquals(10, popular.size());
        assertTrue(popular.stream().allMatch(preset -> !preset.requirements.isEmpty()));
        assertTrue(popular.stream().anyMatch(preset -> preset.id.equals("cursed-walking")));
        assertTrue(popular.stream().anyMatch(preset -> preset.id.equals("pixelmon")));
        assertTrue(popular.stream().anyMatch(preset -> preset.id.equals("all-the-mods")));
    }

    @Test
    void incompatiblePresetCanBePreviewedButCannotBeApplied() {
        PresetManager manager = manager();
        PresetDocument pixelmon = manager.find("pixelmon");

        PresetPreview preview = manager.preview(TikTokChaosConfig.defaults(), pixelmon,
                PresetApplyMode.REPLACE, action -> true, modId -> false);

        assertFalse(preview.available());
        assertEquals(1, preview.missingRequirements().size());
        assertThrows(IllegalStateException.class, () -> manager.apply(TikTokChaosConfig.defaults(), pixelmon,
                PresetApplyMode.REPLACE, action -> true, modId -> false));
    }

    @Test
    void compatiblePresetStillDisablesRulesWhoseRegisteredTargetIsMissing() {
        PresetManager manager = manager();
        PresetDocument create = manager.find("create");

        PresetPreview preview = manager.preview(TikTokChaosConfig.defaults(), create,
                PresetApplyMode.REPLACE, action -> !action.target.equals("create:precision_mechanism"),
                modId -> modId.equals("create"));

        assertTrue(preview.available());
        assertEquals(1, preview.disabledRules());
    }

    @Test
    void threeRosesTriggerThreePixelmonSpawns() {
        PresetManager manager = manager();
        TikTokChaosConfig config = manager.apply(TikTokChaosConfig.defaults(), manager.find("pixelmon"),
                PresetApplyMode.REPLACE, action -> true, modId -> modId.equals("pixelmon"));

        var actions = new RuleEngine().evaluate(config,
                LiveEvent.gift("Viewer", 5655, "Rose", 1, 3), 1_000);

        assertEquals(3, actions.size());
        assertTrue(actions.stream().allMatch(action -> action.action().target.equals("pixelmon:random_shiny")));
    }

    @Test
    void mergeUsesDeterministicSuffixesForDuplicateRuleIds() {
        PresetManager manager = manager();
        TikTokChaosConfig current = TikTokChaosConfig.defaults();
        PresetDocument preset = manager.find("survival-chaos");

        TikTokChaosConfig merged = manager.apply(current, preset, PresetApplyMode.MERGE, action -> true);

        assertEquals(current.rules.size() * 2, merged.rules.size());
        assertTrue(merged.rules.stream().anyMatch(rule -> rule.id.equals("likes_100-2")));
    }

    @Test
    void exportNeverIncludesConnectionOrUsername() throws Exception {
        PresetManager manager = manager();
        TikTokChaosConfig config = TikTokChaosConfig.defaults();
        config.connection.username = "private_streamer";

        Path exported = manager.exportCurrent(config);
        String json = Files.readString(exported);

        assertFalse(json.contains("private_streamer"));
        assertFalse(json.contains("\"connection\""));
    }

    @Test
    void rejectsImportedPersonalDataAndLeavesTheFileUntouched() throws Exception {
        PresetManager manager = manager();
        Files.createDirectories(manager.directory());
        Path file = manager.directory().resolve("unsafe.json");
        String unsafe = "{\"schemaVersion\":1,\"id\":\"unsafe\",\"name\":\"Unsafe\","
                + "\"username\":\"someone\",\"rules\":[]}";
        Files.writeString(file, unsafe);

        assertThrows(IllegalArgumentException.class, () -> manager.read(file));
        assertEquals(unsafe, Files.readString(file));
    }

    @Test
    void previewReportsRulesDisabledByMissingTargets() {
        PresetManager manager = manager();
        PresetPreview preview = manager.preview(TikTokChaosConfig.defaults(), manager.find("zombie-apocalypse"),
                PresetApplyMode.REPLACE, action -> false);

        assertEquals(3, preview.disabledRules());
        assertEquals(3, preview.warnings().size());
    }

    private PresetManager manager() {
        return new PresetManager(tempDirectory.resolve("tiktok-chaos.json"));
    }
}
