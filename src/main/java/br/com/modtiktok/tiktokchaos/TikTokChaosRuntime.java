package br.com.modtiktok.tiktokchaos;

import br.com.modtiktok.tiktokchaos.config.ConfigManager;
import br.com.modtiktok.tiktokchaos.config.TikTokChaosConfig;
import br.com.modtiktok.tiktokchaos.gameplay.ActionExecutor;
import br.com.modtiktok.tiktokchaos.live.ConnectionStatus;
import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import br.com.modtiktok.tiktokchaos.live.TikTokLiveService;
import br.com.modtiktok.tiktokchaos.rule.ActionQueue;
import br.com.modtiktok.tiktokchaos.rule.ActionRequest;
import br.com.modtiktok.tiktokchaos.rule.RuleEngine;
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

public final class TikTokChaosRuntime implements AutoCloseable {
    private final ConfigManager configManager;
    private final RuleEngine ruleEngine = new RuleEngine();
    private final ActionExecutor actionExecutor = new ActionExecutor();
    private final ActionQueue actionQueue;
    private final TikTokLiveService liveService;
    private final Deque<LiveEvent> history = new ArrayDeque<>();
    private final Deque<String> seenEventOrder = new ArrayDeque<>();
    private final Set<String> seenEventIds = new HashSet<>();
    private final AtomicReference<LiveEvent> lastEvent = new AtomicReference<>();

    private volatile ConnectionStatus status = ConnectionStatus.DISCONNECTED;
    private volatile String statusDetail = "Abra um mundo para conectar";
    private volatile String lastAction = "Nenhuma ação executada";
    private volatile boolean worldActive;
    private volatile long nextActionAt;
    private volatile long nextCleanupAt;

    public TikTokChaosRuntime(Path configDirectory) {
        configManager = new ConfigManager(configDirectory.resolve("tiktok-chaos.json"));
        TikTokChaosConfig config = configManager.load();
        actionQueue = new ActionQueue(config.safety.maxQueueSize);
        liveService = new TikTokLiveService(this::receiveEvent, this::updateStatus);
    }

    public void onClientTick(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        boolean activeNow = minecraft.level != null && minecraft.player != null && server != null;
        if (activeNow && !worldActive) {
            worldActive = true;
            statusDetail = "Mundo pronto; pressione F8 para conectar";
            if (config().connection.autoConnectWhenWorldOpens && !config().connection.username.isBlank()) {
                connect();
            }
        } else if (!activeNow && worldActive) {
            worldActive = false;
            liveService.disconnect();
            actionQueue.clear();
            ruleEngine.resetSession();
            actionExecutor.clearTracking();
            statusDetail = "Abra um mundo para conectar";
        }

        if (!activeNow) return;
        long now = System.currentTimeMillis();
        if (now >= nextCleanupAt) {
            nextCleanupAt = now + 1_000L;
            server.execute(() -> actionExecutor.cleanup(server, config().safety));
        }
        if (now < nextActionAt) return;

        ActionRequest request = actionQueue.poll();
        if (request == null) return;
        int perSecond = Math.max(1, config().safety.maxActionsPerSecond);
        nextActionAt = now + Math.max(50, 1_000L / perSecond);
        UUID playerId = minecraft.player.getUUID();
        server.execute(() -> {
            ActionExecutor.ExecutionResult result = actionExecutor.execute(server, playerId, request, config().safety);
            lastAction = (result.success() ? "✓ " : "⚠ ") + request.ruleName() + ": " + result.message();
            if (!result.success()) {
                TikTokChaosMod.LOGGER.warn("Ação {} falhou: {}", request.ruleId(), result.message());
            }
        });
    }

    public synchronized void receiveEvent(LiveEvent event) {
        if (!seenEventIds.add(event.id())) return;
        seenEventOrder.addLast(event.id());
        while (seenEventOrder.size() > 500) {
            seenEventIds.remove(seenEventOrder.removeFirst());
        }
        lastEvent.set(event);
        history.addFirst(event);
        while (history.size() > config().hud.historySize) history.removeLast();

        if (!worldActive || event.type() == LiveEventType.ROOM_STATS
                || event.type() == LiveEventType.LIVE_STARTED || event.type() == LiveEventType.LIVE_ENDED) {
            return;
        }
        List<RuleEngine.MatchedAction> matches = ruleEngine.evaluate(config(), event, System.currentTimeMillis());
        for (RuleEngine.MatchedAction match : matches) {
            if (!actionQueue.offer(match.ruleId(), match.ruleName(), match.event(), match.action())) {
                lastAction = "⚠ Fila cheia; evento de baixa prioridade descartado";
            }
        }
    }

    public synchronized List<LiveEvent> historySnapshot() {
        return List.copyOf(new ArrayList<>(history));
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
        actionQueue.clear();
        ruleEngine.resetSession();
    }

    public void clearQueue() {
        actionQueue.clear();
        lastAction = "Fila limpa manualmente";
    }

    public void simulate(LiveEventType type) {
        LiveEvent event = switch (type) {
            case LIKE -> LiveEvent.like("Teste", 100);
            case GIFT -> LiveEvent.gift("Teste", 5655, "Rosa de teste", 120, 1);
            case COMMENT -> LiveEvent.comment("Teste", "!zumbi");
            case FOLLOW, SHARE, SUBSCRIBE, JOIN -> LiveEvent.simple(type, "Teste");
            case ROOM_STATS -> new LiveEvent(UUID.randomUUID().toString(), type, System.currentTimeMillis(), "",
                    "TikTok", 0, 42, -1, "", 0, "");
            case LIVE_STARTED, LIVE_ENDED -> LiveEvent.simple(type, "TikTok");
        };
        receiveEvent(event);
    }

    public synchronized boolean saveConfig() {
        actionQueue.setCapacity(config().safety.maxQueueSize);
        return configManager.save();
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

    @Override
    public void close() {
        liveService.close();
        actionQueue.clear();
    }
}
