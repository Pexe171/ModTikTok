package br.com.modtiktok.tiktokchaos.preset;

import br.com.modtiktok.tiktokchaos.config.TikTokChaosConfig;
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
