package br.com.modtiktok.tiktokchaos.live;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.config.TikTokChaosConfig;
import io.github.jwdeveloper.tiktok.TikTokLive;
import io.github.jwdeveloper.tiktok.data.events.common.TikTokHeaderEvent;
import io.github.jwdeveloper.tiktok.data.models.users.User;
import io.github.jwdeveloper.tiktok.live.LiveClient;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class TikTokLiveService implements AutoCloseable {
    private final ScheduledExecutorService executor;
    private final Consumer<LiveEvent> eventSink;
    private final BiConsumer<ConnectionStatus, String> statusSink;
    private final AtomicInteger generation = new AtomicInteger();

    private volatile LiveClient client;
    private volatile ScheduledFuture<?> retryTask;
    private volatile boolean manualStop = true;
    private volatile int retrySeconds = 2;
    private volatile String username = "";
    private volatile TikTokChaosConfig.Connection settings;

    public TikTokLiveService(Consumer<LiveEvent> eventSink,
                             BiConsumer<ConnectionStatus, String> statusSink) {
        this.eventSink = eventSink;
        this.statusSink = statusSink;
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "TikTok-Chaos-Live");
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newSingleThreadScheduledExecutor(factory);
    }

    public synchronized void connect(String username, TikTokChaosConfig.Connection settings) {
        disconnectInternal(false);
        this.username = normalizeUsername(username);
        if (this.username.isBlank()) {
            statusSink.accept(ConnectionStatus.ERROR, "Informe o @usuário da LIVE");
            return;
        }
        this.settings = copy(settings);
        this.retrySeconds = this.settings.reconnectInitialSeconds;
        this.manualStop = false;
        int currentGeneration = generation.incrementAndGet();
        statusSink.accept(ConnectionStatus.CONNECTING, "Conectando em @" + this.username);
        executor.execute(() -> attemptConnect(currentGeneration));
    }

    public synchronized void disconnect() {
        manualStop = true;
        generation.incrementAndGet();
        disconnectInternal(true);
    }

    public boolean isRunning() {
        return !manualStop;
    }

    private void attemptConnect(int expectedGeneration) {
        if (manualStop || generation.get() != expectedGeneration) return;
        try {
            LiveClient newClient = TikTokLive.newClient(username)
                    .configure(value -> {
                        value.setClientLanguage("pt");
                        value.getHttpSettings().setTimeout(Duration.ofSeconds(15));
                        value.setRetryOnConnectionFailure(false);
                        value.setPrintToConsole(false);
                        value.setFetchGifts(true);
                    })
                    .onConnected((liveClient, event) -> {
                        retrySeconds = settings.reconnectInitialSeconds;
                        statusSink.accept(ConnectionStatus.CONNECTED, "Conectado em @" + username);
                        eventSink.accept(controlEvent(LiveEventType.LIVE_STARTED));
                    })
                    .onDisconnected((liveClient, event) -> {
                        if (!manualStop && generation.get() == expectedGeneration) {
                            statusSink.accept(ConnectionStatus.RECONNECTING, "Conexão encerrada: " + event.getReason());
                            scheduleRetry(expectedGeneration);
                        }
                    })
                    .onError((liveClient, event) -> {
                        Throwable error = event.getException();
                        String message = error == null ? "Falha desconhecida" : safeMessage(error);
                        statusSink.accept(ConnectionStatus.ERROR, message);
                    })
                    .onLike((liveClient, event) -> eventSink.accept(new LiveEvent(
                            headerId(event), LiveEventType.LIKE, eventTime(event), userId(event.getUser()),
                            userName(event.getUser()), event.getLikes(), event.getTotalLikes(), -1, "", 0, "")))
                    .onGift((liveClient, event) -> eventSink.accept(new LiveEvent(
                            headerId(event), LiveEventType.GIFT, eventTime(event), userId(event.getUser()),
                            userName(event.getUser()), Math.max(1, event.getCombo()), 0, event.getGift().getId(),
                            event.getGift().getName(), event.getGift().getDiamondCost() * Math.max(1, event.getCombo()), "")))
                    .onComment((liveClient, event) -> eventSink.accept(new LiveEvent(
                            headerId(event), LiveEventType.COMMENT, eventTime(event), userId(event.getUser()),
                            userName(event.getUser()), 1, 0, -1, "", 0, event.getText())))
                    .onFollow((liveClient, event) -> eventSink.accept(socialEvent(
                            LiveEventType.FOLLOW, event, event.getUser(), 1, event.getTotalFollowers())))
                    .onShare((liveClient, event) -> eventSink.accept(socialEvent(
                            LiveEventType.SHARE, event, event.getUser(), 1, event.getTotalShares())))
                    .onSubscribe((liveClient, event) -> eventSink.accept(socialEvent(
                            LiveEventType.SUBSCRIBE, event, event.getUser(), 1, 0)))
                    .onJoin((liveClient, event) -> eventSink.accept(socialEvent(
                            LiveEventType.JOIN, event, event.getUser(), 1, event.getTotalUsers())))
                    .onRoomInfo((liveClient, event) -> eventSink.accept(new LiveEvent(
                            UUID.randomUUID().toString(), LiveEventType.ROOM_STATS, Instant.now().toEpochMilli(),
                            "", "TikTok", 0, event.getRoomInfo().getViewersCount(), -1, "", 0, "")))
                    .onLiveEnded((liveClient, event) -> eventSink.accept(controlEvent(LiveEventType.LIVE_ENDED)))
                    .build();

            if (manualStop || generation.get() != expectedGeneration) {
                newClient.disconnect();
                return;
            }
            client = newClient;
            newClient.connect();
        } catch (Throwable error) {
            if (manualStop || generation.get() != expectedGeneration) return;
            String message = safeMessage(error);
            boolean offline = error.getClass().getSimpleName().contains("Offline");
            statusSink.accept(offline ? ConnectionStatus.WAITING_FOR_LIVE : ConnectionStatus.ERROR, message);
            TikTokChaosMod.LOGGER.warn("Falha ao conectar em @{}: {}", username, message);
            scheduleRetry(expectedGeneration);
        }
    }

    private synchronized void scheduleRetry(int expectedGeneration) {
        if (manualStop || generation.get() != expectedGeneration || settings == null || !settings.autoReconnect) {
            return;
        }
        if (retryTask != null && !retryTask.isDone()) return;
        int delay = retrySeconds;
        retrySeconds = Math.min(settings.reconnectMaximumSeconds, Math.max(delay + 1, delay * 2));
        statusSink.accept(ConnectionStatus.RECONNECTING, "Nova tentativa em " + delay + "s");
        retryTask = executor.schedule(() -> {
            retryTask = null;
            if (!manualStop && generation.get() == expectedGeneration) {
                statusSink.accept(ConnectionStatus.CONNECTING, "Reconectando em @" + username);
                attemptConnect(expectedGeneration);
            }
        }, delay, TimeUnit.SECONDS);
    }

    private synchronized void disconnectInternal(boolean publishStatus) {
        ScheduledFuture<?> scheduled = retryTask;
        retryTask = null;
        if (scheduled != null) scheduled.cancel(false);
        LiveClient current = client;
        client = null;
        if (current != null) {
            try {
                current.disconnect();
            } catch (Throwable error) {
                TikTokChaosMod.LOGGER.debug("Erro ignorado ao desconectar TikTok", error);
            }
        }
        if (publishStatus) statusSink.accept(ConnectionStatus.DISCONNECTED, "Desconectado manualmente");
    }

    private LiveEvent socialEvent(LiveEventType type, TikTokHeaderEvent header, User user, int amount, int total) {
        return new LiveEvent(headerId(header), type, eventTime(header), userId(user), userName(user), amount, total,
                -1, "", 0, "");
    }

    private LiveEvent controlEvent(LiveEventType type) {
        return new LiveEvent(UUID.randomUUID().toString(), type, Instant.now().toEpochMilli(), "", "TikTok", 0, 0,
                -1, "", 0, "");
    }

    private static String headerId(TikTokHeaderEvent event) {
        return event.getMessageId() == 0 ? UUID.randomUUID().toString() : Long.toUnsignedString(event.getMessageId());
    }

    private static long eventTime(TikTokHeaderEvent event) {
        long value = event.getTimeStamp();
        if (value <= 0) return Instant.now().toEpochMilli();
        return value < 10_000_000_000L ? value * 1_000L : value;
    }

    private static String userId(User user) {
        return user == null || user.getId() == null ? "" : Long.toUnsignedString(user.getId());
    }

    private static String userName(User user) {
        if (user == null) return "Anônimo";
        if (user.getProfileName() != null && !user.getProfileName().isBlank()) return user.getProfileName();
        return user.getName();
    }

    private static String normalizeUsername(String value) {
        if (value == null) return "";
        String result = value.strip();
        return result.startsWith("@") ? result.substring(1) : result;
    }

    private static String safeMessage(Throwable error) {
        String value = error.getMessage();
        if (value == null || value.isBlank()) value = error.getClass().getSimpleName();
        return value.length() > 180 ? value.substring(0, 180) : value;
    }

    private static TikTokChaosConfig.Connection copy(TikTokChaosConfig.Connection source) {
        TikTokChaosConfig.Connection copy = new TikTokChaosConfig.Connection();
        copy.username = source.username;
        copy.autoReconnect = source.autoReconnect;
        copy.autoConnectWhenWorldOpens = source.autoConnectWhenWorldOpens;
        copy.reconnectInitialSeconds = source.reconnectInitialSeconds;
        copy.reconnectMaximumSeconds = source.reconnectMaximumSeconds;
        return copy;
    }

    @Override
    public void close() {
        disconnect();
        executor.shutdownNow();
    }
}
