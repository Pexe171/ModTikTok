package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.TikTokChaosRuntime;
import br.com.modtiktok.tiktokchaos.analytics.SessionStats;
import br.com.modtiktok.tiktokchaos.live.ConnectionStatus;
import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;

public final class TikTokChaosHud {
    private TikTokChaosHud() {
    }

    public static void render(MatrixStack matrixStack) {
        Minecraft minecraft = Minecraft.getInstance();
        TikTokChaosRuntime runtime = TikTokChaosMod.runtime();
        if (!runtime.config().hud.enabled || minecraft.options.hideGui || minecraft.screen != null) return;
        int x = runtime.config().hud.offsetX;
        int y = runtime.config().hud.offsetY;
        SessionStats.Snapshot stats = runtime.sessionStats();
        int height = 54 + (runtime.config().hud.showGoals && !stats.goals().isEmpty() ? 14 : 0)
                + (runtime.config().hud.showRanking && !stats.ranking().isEmpty() ? 14 : 0)
                + (runtime.config().hud.showChat && !runtime.latestChatLine().isBlank() ? 14 : 0);
        AbstractGui.fill(matrixStack, x, y, x + 260, y + height, 0xB5120D1D);
        AbstractGui.fill(matrixStack, x, y, x + 3, y + height,
                runtime.areActionsPaused() ? 0xFFFF5D73 : statusColor(runtime.status()));
        minecraft.font.drawShadow(matrixStack,
                "TikTok Chaos - " + runtime.status().label() + " - " + runtime.runState().label(), x + 9, y + 7,
                0xFFF4E9FF);
        LiveEvent event = runtime.lastEvent();
        String eventText = event == null ? "Aguardando eventos" : truncate(event.summary(), 34);
        minecraft.font.drawShadow(matrixStack, eventText, x + 9, y + 21, 0xFFE3D9EA);
        minecraft.font.drawShadow(matrixStack,
                "Fila " + runtime.queueSize() + " - Mobs " + runtime.trackedMobCount() + "/"
                        + runtime.config().safety.maxTrackedMobs
                        + (runtime.isPerformanceThrottled() ? " - PROTECAO FPS" : ""),
                x + 9, y + 35, 0xFFB8AFC2);
        int nextY = y + 49;
        if (runtime.config().hud.showGoals && !stats.goals().isEmpty()) {
            SessionStats.GoalProgress goal = stats.goals().get(0);
            minecraft.font.drawShadow(matrixStack,
                    "Meta: " + goal.name() + " " + goal.current() + "/" + goal.target(), x + 9, nextY,
                    goal.complete() ? 0xFF66F0C8 : 0xFFFFD166);
            nextY += 14;
        }
        if (runtime.config().hud.showRanking && !stats.ranking().isEmpty()) {
            SessionStats.ViewerRank leader = stats.ranking().get(0);
            minecraft.font.drawShadow(matrixStack, "Top: " + truncate(leader.name(), 20) + " - "
                    + leader.coins() + " moedas", x + 9, nextY, 0xFFE3D9EA);
            nextY += 14;
        }
        if (runtime.config().hud.showChat && !runtime.latestChatLine().isBlank()) {
            minecraft.font.drawShadow(matrixStack, "Chat: " + truncate(runtime.latestChatLine(), 34), x + 9,
                    nextY, 0xFFCFC4D6);
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
        return value.length() <= length ? value : value.substring(0, length - 3) + "...";
    }
}
