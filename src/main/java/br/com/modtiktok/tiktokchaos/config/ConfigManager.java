package br.com.modtiktok.tiktokchaos.config;

import br.com.modtiktok.tiktokchaos.analytics.GoalMetric;
import br.com.modtiktok.tiktokchaos.analytics.GoalSpec;
import br.com.modtiktok.tiktokchaos.rule.ActionSpec;
import br.com.modtiktok.tiktokchaos.rule.ExecutionSpec;
import br.com.modtiktok.tiktokchaos.rule.ExecutionTier;
import br.com.modtiktok.tiktokchaos.rule.Rule;
import br.com.modtiktok.tiktokchaos.rule.ScalingSpec;
import br.com.modtiktok.tiktokchaos.rule.SequenceStep;
import br.com.modtiktok.tiktokchaos.rule.WeightedChoice;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ConfigManager {
    private static final int MAX_BACKUPS = 10;
    private static final Logger LOGGER = Logger.getLogger("TikTokChaosConfig");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path path;
    private TikTokChaosConfig config;
    private String lastError = "";

    public ConfigManager(Path path) {
        this.path = path;
        this.config = TikTokChaosConfig.defaults();
    }

    public synchronized TikTokChaosConfig load() {
        lastError = "";
        if (!Files.exists(path)) {
            config = TikTokChaosConfig.defaults();
            save();
            return config;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject document = new JsonParser().parse(json).getAsJsonObject();
            int originalSchema = document.has("schemaVersion") ? document.get("schemaVersion").getAsInt() : 1;
            TikTokChaosConfig loaded = migrate(document, originalSchema);
            config = validate(loaded);
            if (originalSchema != TikTokChaosConfig.CURRENT_SCHEMA_VERSION) {
                createBackup();
                writeConfig();
            }
        } catch (Exception exception) {
            lastError = "Configuração inválida: " + exception.getMessage();
            LOGGER.log(Level.SEVERE, "Não foi possível carregar " + path + ". O arquivo foi preservado.", exception);
            config = TikTokChaosConfig.defaults();
        }
        return config;
    }

    public synchronized boolean save() {
        try {
            config = validate(config);
            String serialized = GSON.toJson(config);
            if (Files.exists(path)) {
                String current = Files.readString(path, StandardCharsets.UTF_8);
                if (current.equals(serialized)) {
                    lastError = "";
                    return true;
                }
                createBackup();
            }
            writeConfig(serialized);
            lastError = "";
            return true;
        } catch (Exception exception) {
            lastError = "Não foi possível salvar: " + exception.getMessage();
            LOGGER.log(Level.SEVERE, "Não foi possível salvar " + path, exception);
            return false;
        }
    }

    public synchronized TikTokChaosConfig get() {
        return config;
    }

    public synchronized void set(TikTokChaosConfig config) {
        this.config = validate(config);
    }

    public synchronized String getLastError() {
        return lastError;
    }

    public Path getPath() {
        return path;
    }

    public Path getBackupDirectory() {
        return path.resolveSibling(path.getFileName() + ".backups");
    }

    static TikTokChaosConfig validate(TikTokChaosConfig value) {
        if (value == null) {
            throw new IllegalArgumentException("objeto vazio");
        }
        if (value.schemaVersion != TikTokChaosConfig.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schemaVersion não suportado: " + value.schemaVersion);
        }
        if (value.connection == null) value.connection = new TikTokChaosConfig.Connection();
        if (value.hud == null) value.hud = new TikTokChaosConfig.Hud();
        if (value.safety == null) value.safety = new TikTokChaosConfig.Safety();
        if (value.avatars == null) value.avatars = new TikTokChaosConfig.Avatars();
        if (value.overlay == null) value.overlay = new TikTokChaosConfig.Overlay();
        if (value.goals == null) value.goals = new ArrayList<>();
        if (value.rules == null) value.rules = new ArrayList<>();

        value.connection.username = sanitizeUsername(value.connection.username);
        value.connection.reconnectInitialSeconds = clamp(value.connection.reconnectInitialSeconds, 2, 60);
        value.connection.reconnectMaximumSeconds = clamp(value.connection.reconnectMaximumSeconds,
                value.connection.reconnectInitialSeconds, 300);
        value.hud.historySize = clamp(value.hud.historySize, 10, 500);
        value.hud.offsetX = clamp(value.hud.offsetX, 0, 2_000);
        value.hud.offsetY = clamp(value.hud.offsetY, 0, 2_000);
        value.safety.maxActionsPerSecond = clamp(value.safety.maxActionsPerSecond, 1, 20);
        value.safety.maxTrackedMobs = clamp(value.safety.maxTrackedMobs, 1, 100);
        value.safety.minSpawnRadius = clamp(value.safety.minSpawnRadius, 3, 32);
        value.safety.maxSpawnRadius = clamp(value.safety.maxSpawnRadius, value.safety.minSpawnRadius, 48);
        value.safety.mobLifetimeSeconds = clamp(value.safety.mobLifetimeSeconds, 10, 600);
        value.safety.maxQueueSize = clamp(value.safety.maxQueueSize, 10, 2_000);
        value.safety.maxTriggersPerEvent = clamp(value.safety.maxTriggersPerEvent, 1, 20);
        value.safety.maxViewerBosses = clamp(value.safety.maxViewerBosses, 1, 10);
        value.safety.maxChangedBlocks = clamp(value.safety.maxChangedBlocks, 1, 512);
        value.safety.blockRestoreSeconds = clamp(value.safety.blockRestoreSeconds, 5, 300);
        if (!value.safety.destructiveActionsEnabled) value.safety.destructiveActionsConfirmed = false;
        value.avatars.maxBytes = clamp(value.avatars.maxBytes, 16_384, 1_048_576);
        value.avatars.maxDimension = clamp(value.avatars.maxDimension, 32, 1_024);
        if (value.avatars.allowedHosts == null) value.avatars.allowedHosts = new ArrayList<>();
        if (value.avatars.allowedHosts.size() > 20) {
            throw new IllegalArgumentException("máximo de 20 hosts de avatar");
        }
        value.avatars.allowedHosts.removeIf(host -> host == null || !host.toLowerCase()
                .matches("^(\\*\\.)?[a-z0-9.-]+$"));
        value.overlay.port = clamp(value.overlay.port, 0, 65_535);

        if (value.goals.size() > 10) throw new IllegalArgumentException("máximo de 10 metas");
        Set<String> goalIds = new HashSet<>();
        for (GoalSpec goal : value.goals) {
            if (goal == null || goal.id == null || goal.id.isBlank() || !goalIds.add(goal.id)) {
                throw new IllegalArgumentException("meta sem ID único");
            }
            if (goal.name == null || goal.name.isBlank()) goal.name = goal.id;
            if (goal.metric == null) goal.metric = GoalMetric.COINS;
            goal.target = Math.max(1, Math.min(100_000_000L, goal.target));
        }

        Set<String> ids = new HashSet<>();
        for (Rule rule : value.rules) {
            if (rule == null || rule.id == null || rule.id.isBlank() || !ids.add(rule.id)) {
                throw new IllegalArgumentException("regra sem ID único");
            }
            if (rule.name == null || rule.name.isBlank()) rule.name = rule.id;
            if (rule.event == null || rule.condition == null) {
                throw new IllegalArgumentException("regra incompleta: " + rule.id);
            }
            if (rule.actions == null) rule.actions = new ArrayList<>();
            if (rule.sequence == null) rule.sequence = new ArrayList<>();
            if (rule.execution == null) rule.execution = new ExecutionSpec();
            if (rule.execution.mode == null) {
                rule.execution.mode = br.com.modtiktok.tiktokchaos.rule.ExecutionMode.PER_UNIT;
            }
            if (rule.execution.tiers == null) rule.execution.tiers = new ArrayList<>();
            if (rule.execution.scaling == null) rule.execution.scaling = new ScalingSpec();
            if (rule.execution.roulette == null) rule.execution.roulette = new ArrayList<>();
            rule.cooldownMillis = Math.max(0, rule.cooldownMillis);
            rule.perUserCooldownMillis = Math.max(0, rule.perUserCooldownMillis);
            rule.condition.threshold = clamp(rule.condition.threshold, 1, 1_000_000);
            rule.condition.minGiftValue = Math.max(0, rule.condition.minGiftValue);
            rule.condition.maxGiftValue = Math.max(rule.condition.minGiftValue, rule.condition.maxGiftValue);
            validateActions(rule.actions, rule.id);
            if (rule.sequence.size() > 20) {
                throw new IllegalArgumentException("sequência excede 20 passos em " + rule.id);
            }
            for (SequenceStep step : rule.sequence) {
                if (step == null || step.action == null) {
                    throw new IllegalArgumentException("passo inválido em " + rule.id);
                }
                step.delayTicks = clamp(step.delayTicks, 0, 20 * 120);
                validateActions(List.of(step.action), rule.id);
            }
            if (rule.execution.tiers.size() > 20 || rule.execution.roulette.size() > 20) {
                throw new IllegalArgumentException("execução excede 20 opções em " + rule.id);
            }
            for (ExecutionTier tier : rule.execution.tiers) {
                if (tier == null) throw new IllegalArgumentException("faixa inválida em " + rule.id);
                tier.minAmount = clamp(tier.minAmount, 1, 1_000_000);
                tier.minCoins = clamp(tier.minCoins, 0, 100_000_000);
                tier.repeats = clamp(tier.repeats, 1, value.safety.maxTriggersPerEvent);
                if (tier.actions == null) tier.actions = new ArrayList<>();
                validateActions(tier.actions, rule.id);
            }
            for (WeightedChoice choice : rule.execution.roulette) {
                if (choice == null) throw new IllegalArgumentException("opção de roleta inválida em " + rule.id);
                if (choice.name == null) choice.name = "Opção";
                choice.weight = clamp(choice.weight, 1, 100_000);
                if (choice.actions == null) choice.actions = new ArrayList<>();
                validateActions(choice.actions, rule.id);
            }
            validateScaling(rule.execution.scaling);
        }
        return value;
    }

    private TikTokChaosConfig migrate(JsonObject document, int schemaVersion) {
        if (schemaVersion < 1 || schemaVersion > TikTokChaosConfig.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("schemaVersion não suportado: " + schemaVersion);
        }
        JsonObject migrated = new JsonParser().parse(GSON.toJson(document)).getAsJsonObject();
        if (schemaVersion == 1) {
            migrated.addProperty("schemaVersion", 2);
            schemaVersion = 2;
        }
        if (schemaVersion == 2) {
            migrated.addProperty("schemaVersion", 3);
            schemaVersion = 3;
        }
        if (schemaVersion != TikTokChaosConfig.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("migração incompleta para schemaVersion " + schemaVersion);
        }
        return GSON.fromJson(migrated, TikTokChaosConfig.class);
    }

    private void createBackup() throws IOException {
        if (!Files.exists(path)) return;
        Path directory = getBackupDirectory();
        Files.createDirectories(directory);
        String baseName = path.getFileName().toString();
        long timestamp = System.currentTimeMillis();
        Path backup = directory.resolve(baseName + "." + timestamp + ".bak");
        int suffix = 1;
        while (Files.exists(backup)) {
            backup = directory.resolve(baseName + "." + timestamp + "-" + suffix++ + ".bak");
        }
        Files.copy(path, backup, StandardCopyOption.COPY_ATTRIBUTES);

        try (var stream = Files.list(directory)) {
            List<Path> backups = stream
                    .filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().startsWith(baseName + "."))
                    .sorted(Comparator.comparingLong(this::lastModified).reversed())
                    .toList();
            for (int index = MAX_BACKUPS; index < backups.size(); index++) {
                Files.deleteIfExists(backups.get(index));
            }
        }
    }

    private long lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException ignored) {
            return Long.MIN_VALUE;
        }
    }

    private void writeConfig() throws IOException {
        writeConfig(GSON.toJson(config));
    }

    private void writeConfig(String serialized) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, serialized, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void validateActions(List<ActionSpec> actions, String ruleId) {
        if (actions.size() > 20) throw new IllegalArgumentException("regra excede 20 ações: " + ruleId);
        for (ActionSpec action : actions) {
            if (action == null || action.type == null) {
                throw new IllegalArgumentException("ação inválida em " + ruleId);
            }
            action.amount = clamp(action.amount, 1, 20);
            action.durationTicks = clamp(action.durationTicks, 0, 20 * 600);
            action.amplifier = clamp(action.amplifier, 0, 10);
            action.radius = clamp(action.radius, 3, 48);
            if (action.target == null) action.target = "";
            if (action.message == null) action.message = "";
        }
    }

    private static void validateScaling(ScalingSpec scaling) {
        if (scaling.metric == null) scaling.metric = br.com.modtiktok.tiktokchaos.rule.ScaleMetric.AMOUNT;
        if (scaling.mode == null) scaling.mode = br.com.modtiktok.tiktokchaos.rule.ScaleMode.STEPS;
        if (scaling.target == null) scaling.target = br.com.modtiktok.tiktokchaos.rule.ScaleTarget.ACTION_AMOUNT;
        scaling.startAt = clamp(scaling.startAt, 0, 100_000_000);
        scaling.stepSize = clamp(scaling.stepSize, 1, 100_000_000);
        if (!Double.isFinite(scaling.baseValue)) scaling.baseValue = 1.0;
        if (!Double.isFinite(scaling.increment)) scaling.increment = 1.0;
        scaling.baseValue = Math.max(-100_000, Math.min(100_000, scaling.baseValue));
        scaling.increment = Math.max(-100_000, Math.min(100_000, scaling.increment));
        scaling.minimum = clamp(scaling.minimum, 0, 20 * 600);
        scaling.maximum = clamp(scaling.maximum, scaling.minimum, 20 * 600);
    }

    private static String sanitizeUsername(String username) {
        if (username == null) return "";
        String clean = username.strip();
        if (clean.startsWith("@")) clean = clean.substring(1);
        clean = clean.replaceAll("[^A-Za-z0-9._]", "");
        return clean.length() > 64 ? clean.substring(0, 64) : clean;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
