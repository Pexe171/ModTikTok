package br.com.modtiktok.tiktokchaos;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public final class TikTokChaosMod {
    public static final String MOD_ID = "tiktokchaos";
    public static final Logger LOGGER = LogManager.getLogger("TikTok Chaos");
    private static TikTokChaosRuntime runtime;

    private TikTokChaosMod() {
    }

    public static synchronized TikTokChaosRuntime initialize(Path configDirectory) {
        if (runtime != null) return runtime;
        runtime = new TikTokChaosRuntime(configDirectory);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (runtime != null) runtime.close();
        }, "TikTok-Chaos-Shutdown"));
        return runtime;
    }

    public static TikTokChaosRuntime runtime() {
        if (runtime == null) throw new IllegalStateException("TikTok Chaos ainda nao inicializado");
        return runtime;
    }
}
