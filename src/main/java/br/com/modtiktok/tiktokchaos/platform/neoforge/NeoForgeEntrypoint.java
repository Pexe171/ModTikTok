package br.com.modtiktok.tiktokchaos.platform.neoforge;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.client.ClientEvents;
import br.com.modtiktok.tiktokchaos.client.TikTokChaosScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = TikTokChaosMod.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeEntrypoint {
    public NeoForgeEntrypoint(IEventBus modBus, ModContainer container) {
        TikTokChaosMod.initialize(FMLPaths.CONFIGDIR.get());
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(ClientEvents.openMenuMapping()));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ClientEvents.clientTick());
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post event) ->
                ClientEvents.renderHud(event.getGuiGraphics()));
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> new TikTokChaosScreen(parent));
        TikTokChaosMod.LOGGER.info("TikTok Chaos carregado para NeoForge 1.21.1");
    }
}
