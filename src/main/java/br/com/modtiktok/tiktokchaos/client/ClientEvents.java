package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    private static final KeyMapping OPEN_MENU = new KeyMapping(
            "key.tiktokchaos.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.tiktokchaos"
    );

    private ClientEvents() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(ClientEvents::registerKeys);
        gameBus.addListener(ClientEvents::clientTick);
        gameBus.addListener(ClientEvents::renderHud);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
    }

    private static void clientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        TikTokChaosMod.runtime().onClientTick(minecraft);
        while (OPEN_MENU.consumeClick()) {
            minecraft.setScreen(new TikTokChaosScreen(minecraft.screen));
        }
    }

    private static void renderHud(RenderGuiEvent.Post event) {
        TikTokChaosHud.render(event.getGuiGraphics());
    }
}
