package br.com.modtiktok.tiktokchaos.platform.forge;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.client.ClientEvents;
import br.com.modtiktok.tiktokchaos.client.TikTokChaosScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(TikTokChaosMod.MOD_ID)
public final class ForgeEntrypoint {
    public ForgeEntrypoint() {
        TikTokChaosMod.initialize(FMLPaths.CONFIGDIR.get());
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(ClientEvents.openMenuMapping()));
        modBus.addListener((RegisterGuiOverlaysEvent event) ->
                event.registerAboveAll("hud", (gui, poseStack, partialTick, width, height) ->
                        ClientEvents.renderHud(poseStack)));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) ClientEvents.clientTick();
        });
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) ->
                        new TikTokChaosScreen(parent)));
        TikTokChaosMod.LOGGER.info("TikTok Chaos carregado para Forge 1.19.2");
    }
}
