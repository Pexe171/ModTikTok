package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.TikTokChaosRuntime;
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
        GuiComponent.fill(poseStack, x, y, x + 226, y + 54, 0xB5120D1D);
        GuiComponent.fill(poseStack, x, y, x + 3, y + 54, statusColor(runtime.status()));
        minecraft.font.drawShadow(poseStack, "TikTok Chaos - " + runtime.status().label(), x + 9, y + 7,
                0xFFF4E9FF);

        LiveEvent event = runtime.lastEvent();
        String eventText = event == null ? "Aguardando eventos" : truncate(event.summary(), 34);
        minecraft.font.drawShadow(poseStack, eventText, x + 9, y + 21, 0xFFE3D9EA);
        minecraft.font.drawShadow(poseStack,
                "Fila " + runtime.queueSize() + " - Mobs " + runtime.trackedMobCount() + "/"
                        + runtime.config().safety.maxTrackedMobs,
                x + 9, y + 35, 0xFFB8AFC2);
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
