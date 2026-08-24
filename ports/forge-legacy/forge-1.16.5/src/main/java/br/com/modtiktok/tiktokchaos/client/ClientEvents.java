package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;

public final class ClientEvents {
    private static final KeyBinding OPEN_MENU = new KeyBinding(
            "key.tiktokchaos.open_menu", 299, "key.categories.tiktokchaos"
    );

    private ClientEvents() {
    }

    public static KeyBinding openMenuMapping() {
        return OPEN_MENU;
    }

    public static void clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        TikTokChaosMod.runtime().onClientTick(minecraft);
        while (OPEN_MENU.consumeClick()) {
            minecraft.setScreen(new TikTokChaosScreen(minecraft.screen));
        }
    }

    public static void renderHud(MatrixStack matrixStack) {
        TikTokChaosHud.render(matrixStack);
    }
}
