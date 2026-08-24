package br.com.modtiktok.tiktokchaos.platform.forge;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.client.ClientEvents;
import br.com.modtiktok.tiktokchaos.client.TikTokChaosScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

@Mod(TikTokChaosMod.MOD_ID)
public final class ForgeEntrypoint {
    public ForgeEntrypoint(FMLJavaModLoadingContext context) {
        TikTokChaosMod.initialize(FMLPaths.CONFIGDIR.get());
        context.getModEventBus().addListener((RegisterKeyMappingsEvent event) ->
                event.register(ClientEvents.openMenuMapping()));
        context.getModEventBus().addListener((AddGuiOverlayLayersEvent event) ->
                event.getLayeredDraw().add(
                        ResourceLocation.fromNamespaceAndPath(TikTokChaosMod.MOD_ID, "hud"),
                        (graphics, deltaTracker) -> ClientEvents.renderHud(graphics)));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post event) ->
                ClientEvents.clientTick());
        context.getContainer().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) ->
                        new TikTokChaosScreen(parent)));
        TikTokChaosMod.LOGGER.info("TikTok Chaos carregado para Forge 1.21.1");
    }
}
