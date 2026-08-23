package br.com.modtiktok.tiktokchaos;

import br.com.modtiktok.tiktokchaos.client.ClientEvents;
import br.com.modtiktok.tiktokchaos.client.TikTokChaosScreen;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(value = TikTokChaosMod.MOD_ID, dist = Dist.CLIENT)
public final class TikTokChaosMod {
    public static final String MOD_ID = "tiktokchaos";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static TikTokChaosRuntime runtime;

    public TikTokChaosMod(IEventBus modBus, ModContainer container) {
        runtime = new TikTokChaosRuntime();
        ClientEvents.register(modBus, NeoForge.EVENT_BUS);
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> new TikTokChaosScreen(parent));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (runtime != null) runtime.close();
        }, "TikTok-Chaos-Shutdown"));
        LOGGER.info("TikTok Chaos carregado para NeoForge 1.21.1");
    }

    public static TikTokChaosRuntime runtime() {
        if (runtime == null) throw new IllegalStateException("TikTok Chaos ainda não inicializado");
        return runtime;
    }
}
