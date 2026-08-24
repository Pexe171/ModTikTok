package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    private static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.tiktokchaos.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.tiktokchaos"
    );
    private static final KeyMapping EMERGENCY_STOP = new KeyMapping(
            "key.tiktokchaos.emergency_stop",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            "key.categories.tiktokchaos"
    );

    private ClientEvents() {
    }

    public static KeyMapping openMenuMapping() {
        return OPEN_MENU;
    }

    public static KeyMapping emergencyStopMapping() {
        return EMERGENCY_STOP;
    }

    public static void clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        TikTokChaosMod.runtime().onClientTick(minecraft);
        while (OPEN_MENU.consumeClick()) {
            minecraft.setScreen(new TikTokChaosScreen(minecraft.screen));
        }
        while (EMERGENCY_STOP.consumeClick()) {
            TikTokChaosMod.runtime().emergencyStop();
        }
    }

    public static void renderHud(PoseStack poseStack) {
        TikTokChaosHud.render(poseStack);
    }
}
