package br.com.modtiktok.tiktokchaos.platform.forge;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.client.ClientEvents;
import br.com.modtiktok.tiktokchaos.client.TikTokChaosScreen;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.client.ConfigGuiHandler;
import net.minecraftforge.client.gui.OverlayRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(TikTokChaosMod.MOD_ID)
public final class ForgeEntrypoint {
    public ForgeEntrypoint() {
        TikTokChaosMod.initialize(FMLPaths.CONFIGDIR.get());
        ClientRegistry.registerKeyBinding(ClientEvents.openMenuMapping());
        OverlayRegistry.registerOverlayTop("tiktok_chaos_hud",
                (gui, poseStack, partialTick, width, height) -> ClientEvents.renderHud(poseStack));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) ClientEvents.clientTick();
        });
        ModLoadingContext.get().registerExtensionPoint(
                ConfigGuiHandler.ConfigGuiFactory.class,
                () -> new ConfigGuiHandler.ConfigGuiFactory((minecraft, parent) ->
                        new TikTokChaosScreen(parent)));
        TikTokChaosMod.LOGGER.info("TikTok Chaos carregado para Forge 1.18.2");
    }
}
