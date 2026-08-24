package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.TikTokChaosRuntime;
import br.com.modtiktok.tiktokchaos.analytics.SessionStats;
import br.com.modtiktok.tiktokchaos.live.ConnectionStatus;
import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class TikTokChaosHud {
    private TikTokChaosHud() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        TikTokChaosRuntime runtime = TikTokChaosMod.runtime();
        if (!runtime.config().hud.enabled || minecraft.options.hideGui || minecraft.screen != null) return;

        int x = runtime.config().hud.offsetX;
        int y = runtime.config().hud.offsetY;
        SessionStats.Snapshot stats = runtime.sessionStats();
        int width = 260;
        int height = 54 + (runtime.config().hud.showGoals && !stats.goals().isEmpty() ? 14 : 0)
                + (runtime.config().hud.showRanking && !stats.ranking().isEmpty() ? 14 : 0)
                + (runtime.config().hud.showChat && !runtime.latestChatLine().isBlank() ? 14 : 0);
        graphics.fill(x, y, x + width, y + height, 0xB5120D1D);
        graphics.fill(x, y, x + 3, y + height,
                runtime.areActionsPaused() ? 0xFFFF5D73 : statusColor(runtime.status()));
        graphics.drawString(minecraft.font,
                "TikTok Chaos • " + runtime.status().label() + " • " + runtime.runState().label(), x + 9, y + 7,
                0xFFF4E9FF, false);

        LiveEvent event = runtime.lastEvent();
        String eventText = event == null ? "Aguardando eventos" : truncate(event.summary(), 34);
        graphics.drawString(minecraft.font, eventText, x + 9, y + 21, 0xFFE3D9EA, false);
        graphics.drawString(minecraft.font,
                "Fila " + runtime.queueSize() + "  •  Mobs " + runtime.trackedMobCount() + "/"
                        + runtime.config().safety.maxTrackedMobs
                        + (runtime.isPerformanceThrottled() ? "  •  PROTEÇÃO FPS" : ""),
                x + 9, y + 35, 0xFFB8AFC2, false);
        int nextY = y + 49;
        if (runtime.config().hud.showGoals && !stats.goals().isEmpty()) {
            SessionStats.GoalProgress goal = stats.goals().get(0);
            graphics.drawString(minecraft.font, "Meta: " + goal.name() + " " + goal.current() + "/" + goal.target(),
                    x + 9, nextY, goal.complete() ? 0xFF66F0C8 : 0xFFFFD166, false);
            nextY += 14;
        }
        if (runtime.config().hud.showRanking && !stats.ranking().isEmpty()) {
            SessionStats.ViewerRank leader = stats.ranking().get(0);
            graphics.drawString(minecraft.font, "Top: " + truncate(leader.name(), 20) + " • " + leader.coins()
                    + " moedas", x + 9, nextY, 0xFFE3D9EA, false);
            nextY += 14;
        }
        if (runtime.config().hud.showChat && !runtime.latestChatLine().isBlank()) {
            graphics.drawString(minecraft.font, "Chat: " + truncate(runtime.latestChatLine(), 34), x + 9, nextY,
                    0xFFCFC4D6, false);
        }
    }

    private static int statusColor(ConnectionStatus status) {
        return switch (status) {
            case CONNECTED -> 0xFF42E8A4;
            case CONNECTING, RECONNECTING, WAITING_FOR_LIVE -> 0xFFFFC857;
            case ERROR -> 0xFFFF5D73;
            case DISCONNECTED -> 0xFF8B8196;
        };
    }

    private static String truncate(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length - 1) + "…";
    }
}
