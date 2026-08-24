package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.TikTokChaosRuntime;
import br.com.modtiktok.tiktokchaos.analytics.SessionStats;
import br.com.modtiktok.tiktokchaos.live.ConnectionStatus;
import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;

public final class TikTokChaosHud {
    private TikTokChaosHud() {
    }

    public static void render(PoseStack poseStack) {
        Minecraft minecraft = Minecraft.getInstance();
        TikTokChaosRuntime runtime = TikTokChaosMod.runtime();
        if (!runtime.config().hud.enabled || minecraft.options.hideGui || minecraft.screen != null) return;

        int x = runtime.config().hud.offsetX;
        int y = runtime.config().hud.offsetY;
        SessionStats.Snapshot stats = runtime.sessionStats();
        int height = 54 + (runtime.config().hud.showGoals && !stats.goals().isEmpty() ? 14 : 0)
                + (runtime.config().hud.showRanking && !stats.ranking().isEmpty() ? 14 : 0)
                + (runtime.config().hud.showChat && !runtime.latestChatLine().isBlank() ? 14 : 0);
        GuiComponent.fill(poseStack, x, y, x + 260, y + height, 0xB5120D1D);
        GuiComponent.fill(poseStack, x, y, x + 3, y + height,
                runtime.areActionsPaused() ? 0xFFFF5D73 : statusColor(runtime.status()));
        minecraft.font.drawShadow(poseStack,
                ClientText.text("hud.tiktokchaos.header_legacy", ClientText.status(runtime.status()),
                        ClientText.runState(runtime.runState())), x + 9, y + 7,
                0xFFF4E9FF);

        LiveEvent event = runtime.lastEvent();
        String eventText = event == null ? ClientText.text("hud.tiktokchaos.waiting_events")
                : truncate(ClientText.eventSummary(event), 34);
        minecraft.font.drawShadow(poseStack, eventText, x + 9, y + 21, 0xFFE3D9EA);
        minecraft.font.drawShadow(poseStack,
                ClientText.text("hud.tiktokchaos.queue_mobs_legacy", runtime.queueSize(), runtime.trackedMobCount(),
                        runtime.config().safety.maxTrackedMobs)
                        + (runtime.isPerformanceThrottled()
                        ? ClientText.text("hud.tiktokchaos.fps_protection_legacy") : ""),
                x + 9, y + 35, 0xFFB8AFC2);
        int nextY = y + 49;
        if (runtime.config().hud.showGoals && !stats.goals().isEmpty()) {
            SessionStats.GoalProgress goal = stats.goals().get(0);
            minecraft.font.drawShadow(poseStack,
                    ClientText.text("hud.tiktokchaos.goal", goal.name(), goal.current(), goal.target()), x + 9, nextY,
                    goal.complete() ? 0xFF66F0C8 : 0xFFFFD166);
            nextY += 14;
        }
        if (runtime.config().hud.showRanking && !stats.ranking().isEmpty()) {
            SessionStats.ViewerRank leader = stats.ranking().get(0);
            minecraft.font.drawShadow(poseStack, ClientText.text("hud.tiktokchaos.top_legacy",
                    truncate(leader.name(), 20), leader.coins()), x + 9, nextY, 0xFFE3D9EA);
            nextY += 14;
        }
        if (runtime.config().hud.showChat && !runtime.latestChatLine().isBlank()) {
            minecraft.font.drawShadow(poseStack, ClientText.text("hud.tiktokchaos.chat",
                    truncate(runtime.latestChatLine(), 34)), x + 9,
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
        return value.length() <= length ? value : value.substring(0, length - 1) + "...";
    }
}
