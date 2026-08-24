package br.com.modtiktok.tiktokchaos.config;

import br.com.modtiktok.tiktokchaos.rule.ActionSpec;
import br.com.modtiktok.tiktokchaos.rule.Rule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ConfigManager {
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
            TikTokChaosConfig loaded = GSON.fromJson(json, TikTokChaosConfig.class);
            config = validate(loaded);
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
            Files.createDirectories(path.getParent());
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(config), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
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

    static TikTokChaosConfig validate(TikTokChaosConfig value) {
        if (value == null) {
            throw new IllegalArgumentException("objeto vazio");
        }
        if (value.schemaVersion != 1) {
            throw new IllegalArgumentException("schemaVersion não suportado: " + value.schemaVersion);
        }
        if (value.connection == null) value.connection = new TikTokChaosConfig.Connection();
        if (value.hud == null) value.hud = new TikTokChaosConfig.Hud();
        if (value.safety == null) value.safety = new TikTokChaosConfig.Safety();
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
            rule.cooldownMillis = Math.max(0, rule.cooldownMillis);
            rule.perUserCooldownMillis = Math.max(0, rule.perUserCooldownMillis);
            rule.condition.threshold = clamp(rule.condition.threshold, 1, 1_000_000);
            rule.condition.minGiftValue = Math.max(0, rule.condition.minGiftValue);
            rule.condition.maxGiftValue = Math.max(rule.condition.minGiftValue, rule.condition.maxGiftValue);
            for (ActionSpec action : rule.actions) {
                if (action == null || action.type == null) {
                    throw new IllegalArgumentException("ação inválida em " + rule.id);
                }
                action.amount = clamp(action.amount, 1, 20);
                action.durationTicks = clamp(action.durationTicks, 0, 20 * 600);
                action.amplifier = clamp(action.amplifier, 0, 10);
                action.radius = clamp(action.radius, 3, 48);
                if (action.target == null) action.target = "";
                if (action.message == null) action.message = "";
            }
        }
        return value;
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
