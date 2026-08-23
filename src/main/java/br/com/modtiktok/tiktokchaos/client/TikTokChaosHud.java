package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.TikTokChaosRuntime;
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
        int width = 226;
        int height = 54;
        graphics.fill(x, y, x + width, y + height, 0xB5120D1D);
        graphics.fill(x, y, x + 3, y + height, statusColor(runtime.status()));
        graphics.drawString(minecraft.font, "TikTok Chaos • " + runtime.status().label(), x + 9, y + 7,
                0xFFF4E9FF, false);

        LiveEvent event = runtime.lastEvent();
        String eventText = event == null ? "Aguardando eventos" : truncate(event.summary(), 34);
        graphics.drawString(minecraft.font, eventText, x + 9, y + 21, 0xFFE3D9EA, false);
        graphics.drawString(minecraft.font,
                "Fila " + runtime.queueSize() + "  •  Mobs " + runtime.trackedMobCount() + "/"
                        + runtime.config().safety.maxTrackedMobs,
                x + 9, y + 35, 0xFFB8AFC2, false);
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
