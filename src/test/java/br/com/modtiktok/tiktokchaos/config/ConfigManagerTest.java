package br.com.modtiktok.tiktokchaos.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {
    @TempDir
    Path tempDirectory;

    @Test
    void savesAndLoadsValidatedConfiguration() {
        Path file = tempDirectory.resolve("config.json");
        ConfigManager manager = new ConfigManager(file);
        TikTokChaosConfig config = manager.load();
        config.connection.username = "@minha.live!";
        config.safety.maxTrackedMobs = 999;

        assertTrue(manager.save());
        TikTokChaosConfig loaded = new ConfigManager(file).load();

        assertEquals("minha.live", loaded.connection.username);
        assertEquals(100, loaded.safety.maxTrackedMobs);
        assertFalse(loaded.rules.isEmpty());
    }

    @Test
    void preservesInvalidFileAndFallsBackToDefaults() throws Exception {
        Path file = tempDirectory.resolve("config.json");
        Files.writeString(file, "{ conteúdo inválido");
        ConfigManager manager = new ConfigManager(file);

        TikTokChaosConfig loaded = manager.load();

        assertFalse(manager.getLastError().isBlank());
        assertEquals("{ conteúdo inválido", Files.readString(file));
        assertEquals(12, loaded.rules.size());
    }

    @Test
    void migratesSchemaOneAndPreservesAnAutomaticBackup() throws Exception {
        Path file = tempDirectory.resolve("config.json");
        ConfigManager initial = new ConfigManager(file);
        assertTrue(initial.save());
        String schemaOne = Files.readString(file).replaceFirst("\"schemaVersion\": "
                + TikTokChaosConfig.CURRENT_SCHEMA_VERSION, "\"schemaVersion\": 1");
        Files.writeString(file, schemaOne);

        ConfigManager migrated = new ConfigManager(file);
        TikTokChaosConfig config = migrated.load();

        assertEquals(TikTokChaosConfig.CURRENT_SCHEMA_VERSION, config.schemaVersion);
        assertTrue(Files.readString(file).contains("\"schemaVersion\": "
                + TikTokChaosConfig.CURRENT_SCHEMA_VERSION));
        try (var backups = Files.list(migrated.getBackupDirectory())) {
            Path backup = backups.findFirst().orElseThrow();
            assertTrue(Files.readString(backup).contains("\"schemaVersion\": 1"));
        }
    }

    @Test
    void keepsOnlyTheTenMostRecentBackups() throws Exception {
        Path file = tempDirectory.resolve("config.json");
        ConfigManager manager = new ConfigManager(file);
        TikTokChaosConfig config = manager.load();
        for (int index = 0; index < 15; index++) {
            config.hud.offsetX = index;
            assertTrue(manager.save());
        }

        try (var backups = Files.list(manager.getBackupDirectory())) {
            assertEquals(10, backups.count());
        }
    }
}
