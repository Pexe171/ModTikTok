package br.com.modtiktok.tiktokchaos;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.nio.file.Path;

public final class TikTokChaosMod {
    public static final String MOD_ID = "tiktokchaos";
    public static final Logger LOGGER = LogUtils.getLogger();
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
        if (runtime == null) throw new IllegalStateException("TikTok Chaos ainda não inicializado");
        return runtime;
    }
}
