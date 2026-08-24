package br.com.modtiktok.tiktokchaos;

import br.com.modtiktok.tiktokchaos.analytics.SessionStats;
import br.com.modtiktok.tiktokchaos.avatar.TemporaryAvatarCache;
import br.com.modtiktok.tiktokchaos.config.ConfigManager;
import br.com.modtiktok.tiktokchaos.config.TikTokChaosConfig;
import br.com.modtiktok.tiktokchaos.gameplay.ActionExecutor;
import br.com.modtiktok.tiktokchaos.live.ConnectionStatus;
import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import br.com.modtiktok.tiktokchaos.live.TikTokLiveService;
import br.com.modtiktok.tiktokchaos.preset.PresetApplyMode;
import br.com.modtiktok.tiktokchaos.preset.PresetDocument;
import br.com.modtiktok.tiktokchaos.preset.PresetManager;
import br.com.modtiktok.tiktokchaos.preset.PresetPreview;
import br.com.modtiktok.tiktokchaos.overlay.LocalOverlayServer;
import br.com.modtiktok.tiktokchaos.rule.ActionQueue;
import br.com.modtiktok.tiktokchaos.rule.ActionRequest;
import br.com.modtiktok.tiktokchaos.rule.RuleEngine;
import br.com.modtiktok.tiktokchaos.simulator.SimulationRequest;
import br.com.modtiktok.tiktokchaos.simulator.SimulationResult;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Optional;

public final class TikTokChaosRuntime implements AutoCloseable {
    private final ConfigManager configManager;
    private final PresetManager presetManager;
    private final RuleEngine ruleEngine = new RuleEngine();
    private final ActionExecutor actionExecutor = new ActionExecutor();
    private final AdaptivePerformanceGuard performanceGuard = new AdaptivePerformanceGuard();
    private final SessionStats sessionStats = new SessionStats();
    private final TemporaryAvatarCache avatarCache = new TemporaryAvatarCache();
    private final LocalOverlayServer overlayServer;
    private final ActionQueue actionQueue;
    private final TikTokLiveService liveService;
    private final Deque<LiveEvent> history = new ArrayDeque<>();
    private final Deque<String> seenEventOrder = new ArrayDeque<>();
    private final Set<String> seenEventIds = new HashSet<>();
    private final AtomicReference<LiveEvent> lastEvent = new AtomicReference<>();

    private volatile ConnectionStatus status = ConnectionStatus.DISCONNECTED;
    private volatile ActionRunState runState = ActionRunState.ACTIVE;
    private volatile String statusDetail = "Abra um mundo para conectar";
    private volatile String lastAction = "Nenhuma ação executada";
    private volatile boolean worldActive;
    private volatile boolean liveSessionOpen;
    private volatile MinecraftServer lastServer;
    private volatile long nextActionAt;
    private volatile long nextCleanupAt;

    public TikTokChaosRuntime(Path configDirectory) {
        configManager = new ConfigManager(configDirectory.resolve("tiktok-chaos.json"));
        TikTokChaosConfig config = configManager.load();
        presetManager = new PresetManager(configManager.getPath());
        overlayServer = new LocalOverlayServer(this::overlayJson);
        actionQueue = new ActionQueue(config.safety.maxQueueSize);
        liveService = new TikTokLiveService(this::receiveEvent, this::updateStatus);
        syncOverlay();
    }

    public void onClientTick(Minecraft minecraft) {
        performanceGuard.recordTick(System.nanoTime());
        MinecraftServer server = minecraft.getSingleplayerServer();
        boolean activeNow = minecraft.level != null && minecraft.player != null && server != null;
        if (activeNow) lastServer = server;
        if (activeNow && !worldActive) {
            worldActive = true;
            statusDetail = "Mundo pronto; pressione F8 para conectar";
            if (config().connection.autoConnectWhenWorldOpens && !config().connection.username.isBlank()) {
                connect();
            }
        } else if (!activeNow && worldActive) {
            worldActive = false;
            liveSessionOpen = false;
            avatarCache.clear();
            liveService.disconnect();
            actionQueue.clear();
            ruleEngine.resetSession();
            scheduleSessionCleanup(lastServer);
            statusDetail = "Abra um mundo para conectar";
        }

        if (!activeNow) return;
        long now = System.currentTimeMillis();
        if (now >= nextCleanupAt) {
            nextCleanupAt = now + 1_000L;
            server.execute(() -> {
                actionExecutor.cleanup(server, config().safety);
                actionExecutor.drainDefeatedViewers().forEach(sessionStats::recordViewerMobDefeated);
            });
        }
        if (runState == ActionRunState.PAUSED || now < nextActionAt) return;

        ActionRequest request = actionQueue.pollReady(now);
        if (request == null) return;
        nextActionAt = now + performanceGuard.actionDelayMillis(config().safety.maxActionsPerSecond,
                config().safety.adaptivePerformance);
        UUID playerId = minecraft.player.getUUID();
        server.execute(() -> {
            ActionExecutor.ExecutionResult result = actionExecutor.execute(server, playerId, request, config().safety);
            if (!request.simulated()) sessionStats.recordExecuted(result.success());
            lastAction = (result.success() ? "✓ " : "⚠ ") + request.ruleName() + ": " + result.message();
            if (!result.success()) {
                TikTokChaosMod.LOGGER.warn("Ação {} falhou: {}", request.ruleId(), result.message());
            }
        });
    }

    public synchronized void receiveEvent(LiveEvent event) {
        receiveEvent(event, true);
    }

    private void receiveEvent(LiveEvent event, boolean countStats) {
        if (!seenEventIds.add(event.id())) return;
        seenEventOrder.addLast(event.id());
        while (seenEventOrder.size() > 500) {
            seenEventIds.remove(seenEventOrder.removeFirst());
        }
        lastEvent.set(event);
        history.addFirst(event);
        while (history.size() > config().hud.historySize) history.removeLast();
        if (config().avatars.enabled && !event.avatarUrl().isBlank()) {
            avatarCache.request(viewerKey(event), event.avatarUrl(), config().avatars);
        }

        if (event.type() == LiveEventType.LIVE_STARTED) {
            if (!liveSessionOpen) sessionStats.reset();
            liveSessionOpen = true;
            return;
        }

        if (event.type() == LiveEventType.LIVE_ENDED) {
            liveSessionOpen = false;
            avatarCache.clear();
            actionQueue.clear();
            ruleEngine.resetSession();
            scheduleSessionCleanup(lastServer);
            lastAction = "LIVE encerrada; ações temporárias removidas";
            return;
        }

        if (countStats && event.type() != LiveEventType.ROOM_STATS) sessionStats.recordEvent(event);

        if (!worldActive || event.type() == LiveEventType.ROOM_STATS
                || runState == ActionRunState.PAUSED) {
            return;
        }
        List<RuleEngine.MatchedAction> matches = ruleEngine.evaluate(config(), event, System.currentTimeMillis());
        for (RuleEngine.MatchedAction match : matches) {
            if (!actionQueue.offer(match.ruleId(), match.ruleName(), match.event(), match.action(), !countStats,
                    match.delayTicks())) {
                if (countStats) sessionStats.recordDropped();
                lastAction = "⚠ Fila cheia; evento de baixa prioridade descartado";
            } else if (countStats) {
                sessionStats.recordQueued(event);
            }
        }
    }

    public synchronized List<LiveEvent> historySnapshot() {
        return List.copyOf(new ArrayList<>(history));
    }

    public synchronized String latestChatLine() {
        for (LiveEvent event : history) {
            if (event.type() != LiveEventType.COMMENT) continue;
            String name = config().hud.hideViewerNames ? "Espectador" : event.userName();
            return name + ": " + event.comment();
        }
        return "";
    }

    public void connect() {
        if (!worldActive) {
            updateStatus(ConnectionStatus.ERROR, "Abra um mundo singleplayer antes de conectar");
            return;
        }
        if (config().connection.username.isBlank()) {
            updateStatus(ConnectionStatus.ERROR, "Informe o @usuário da LIVE");
            return;
        }
        ruleEngine.resetSession();
        liveService.connect(config().connection.username, config().connection);
    }

    public void disconnect() {
        liveService.disconnect();
        liveSessionOpen = false;
        avatarCache.clear();
        actionQueue.clear();
        ruleEngine.resetSession();
        scheduleSessionCleanup(lastServer);
        lastAction = "Desconectado; ações temporárias removidas";
    }

    public synchronized void pauseActions() {
        runState = ActionRunState.PAUSED;
        actionQueue.clear();
        ruleEngine.resetSession();
        lastAction = "Ações pausadas; a LIVE continua conectada";
    }

    public synchronized void resumeActions() {
        runState = ActionRunState.ACTIVE;
        nextActionAt = 0;
        lastAction = "Ações retomadas pelo painel";
    }

    public synchronized void emergencyStop() {
        runState = ActionRunState.PAUSED;
        actionQueue.clear();
        ruleEngine.resetSession();
        scheduleSessionCleanup(lastServer);
        lastAction = "EMERGÊNCIA F9: fila, sequências, mobs, efeitos, clima e blocos restaurados";
    }

    public void clearQueue() {
        actionQueue.clear();
        lastAction = "Fila limpa manualmente";
    }

    public void simulate(LiveEventType type) {
        simulate(SimulationRequest.defaults(type), true);
    }

    public synchronized SimulationResult simulate(SimulationRequest request, boolean execute) {
        LiveEvent event = request.toEvent();
        List<RuleEngine.MatchedAction> matches = ruleEngine.preview(config(), event, System.currentTimeMillis());
        List<ActionRequest> accepted = actionQueue.preview(matches);
        List<String> actions = accepted.stream()
                .map(action -> action.ruleName() + ": " + action.action().type
                        + (action.action().target.isBlank() ? "" : " " + action.action().target)
                        + " x" + action.action().amount
                        + (action.scheduledAtMillis() > System.currentTimeMillis() + 50
                        ? " (atrasada)" : ""))
                .toList();
        List<String> warnings = new ArrayList<>();
        if (matches.size() > accepted.size()) {
            warnings.add((matches.size() - accepted.size()) + " ações seriam descartadas pelos limites da fila");
        }
        if (areActionsPaused()) warnings.add("As ações estão pausadas; use o painel para retomar");
        if (!worldActive) warnings.add("Abra um mundo singleplayer para executar o teste");

        boolean executed = execute && worldActive && !areActionsPaused();
        if (executed) receiveEvent(event, false);
        return new SimulationResult(event, executed, matches.size(), accepted.size(),
                actions, warnings);
    }

    public synchronized boolean saveConfig() {
        actionQueue.setCapacity(config().safety.maxQueueSize);
        if (!config().avatars.enabled) avatarCache.clear();
        boolean saved = configManager.save();
        if (saved) syncOverlay();
        return saved;
    }

    public List<PresetDocument> presetCatalog() {
        return presetManager.catalog();
    }

    public PresetPreview previewPreset(String presetId, PresetApplyMode mode) {
        PresetDocument preset = presetManager.find(presetId);
        if (preset == null) throw new IllegalArgumentException("Preset não encontrado: " + presetId);
        return presetManager.preview(config(), preset, mode, actionExecutor::isTargetAvailable);
    }

    public synchronized boolean applyPreset(String presetId, PresetApplyMode mode) {
        PresetDocument preset = presetManager.find(presetId);
        if (preset == null) {
            lastAction = "Preset não encontrado: " + presetId;
            return false;
        }
        TikTokChaosConfig applied = presetManager.apply(config(), preset, mode, actionExecutor::isTargetAvailable);
        configManager.set(applied);
        actionQueue.setCapacity(applied.safety.maxQueueSize);
        boolean saved = configManager.save();
        lastAction = saved ? "Preset aplicado: " + preset.name + " (" + mode + ")"
                : configManager.getLastError();
        return saved;
    }

    public synchronized Path exportPreset() {
        try {
            Path exported = presetManager.exportCurrent(config());
            lastAction = "Preset exportado: " + exported.getFileName();
            return exported;
        } catch (Exception exception) {
            lastAction = "Falha ao exportar preset: " + exception.getMessage();
            return null;
        }
    }

    public Path presetDirectory() {
        return presetManager.directory();
    }

    public Optional<Path> avatarPath(LiveEvent event) {
        return event == null ? Optional.empty() : avatarCache.find(viewerKey(event));
    }

    public String overlayUrl() {
        return overlayServer.url();
    }

    public TikTokChaosConfig config() {
        return configManager.get();
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public ConnectionStatus status() {
        return status;
    }

    public ActionRunState runState() {
        return runState;
    }

    public boolean areActionsPaused() {
        return runState == ActionRunState.PAUSED;
    }

    public String statusDetail() {
        return statusDetail;
    }

    public LiveEvent lastEvent() {
        return lastEvent.get();
    }

    public String lastAction() {
        return lastAction;
    }

    public int queueSize() {
        return actionQueue.size();
    }

    public int trackedMobCount() {
        return actionExecutor.trackedCount();
    }

    public boolean isPerformanceThrottled() {
        return performanceGuard.isThrottled(config().safety.adaptivePerformance);
    }

    public int estimatedFps() {
        return performanceGuard.estimatedFps();
    }

    public SessionStats.Snapshot sessionStats() {
        return sessionStats.snapshot(config().goals, config().hud.hideViewerNames);
    }

    public void resetSessionStats() {
        sessionStats.reset();
        lastAction = "Estatísticas da sessão zeradas";
    }

    public boolean isWorldActive() {
        return worldActive;
    }

    public boolean isConnectionRunning() {
        return liveService.isRunning();
    }

    private void updateStatus(ConnectionStatus status, String detail) {
        this.status = status;
        this.statusDetail = detail == null ? "" : detail;
    }

    private void scheduleSessionCleanup(MinecraftServer server) {
        if (server == null) {
            actionExecutor.clearTracking();
            return;
        }
        server.execute(() -> actionExecutor.restoreAndRemove(server));
    }

    private String viewerKey(LiveEvent event) {
        return event.userId().isBlank() ? event.userName() : event.userId();
    }

    private synchronized void syncOverlay() {
        if (!config().overlay.enabled) {
            overlayServer.close();
            return;
        }
        if (overlayServer.isRunning()) return;
        try {
            overlayServer.start(config().overlay.port);
        } catch (Exception error) {
            config().overlay.enabled = false;
            lastAction = "Overlay local não iniciou: " + error.getMessage();
        }
    }

    private String overlayJson() {
        SessionStats.Snapshot stats = sessionStats();
        return "{\"status\":\"" + jsonEscape(status.label()) + "\",\"runState\":\""
                + jsonEscape(runState.label()) + "\",\"detail\":\"" + jsonEscape(statusDetail)
                + "\",\"lastAction\":\"" + jsonEscape(lastAction) + "\",\"queue\":" + queueSize()
                + ",\"mobs\":" + trackedMobCount() + ",\"coins\":" + stats.coins()
                + ",\"likes\":" + stats.likes() + ",\"gifts\":" + stats.gifts() + "}";
    }

    private String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    @Override
    public void close() {
        liveService.close();
        actionQueue.clear();
        avatarCache.close();
        overlayServer.close();
        scheduleSessionCleanup(lastServer);
    }
}
