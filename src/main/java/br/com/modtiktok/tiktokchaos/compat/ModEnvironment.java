package br.com.modtiktok.tiktokchaos.compat;

import java.lang.reflect.Method;
import java.util.Locale;

/** Loader-neutral mod detection for the shared Forge and NeoForge core. */
public final class ModEnvironment {
    private static final String[] MOD_LIST_CLASSES = {
            "net.neoforged.fml.ModList",
            "net.minecraftforge.fml.ModList"
    };

    private ModEnvironment() {
    }

    public static boolean isLoaded(String modId) {
        if (modId == null || modId.isBlank()) return false;
        String normalized = modId.strip().toLowerCase(Locale.ROOT);
        for (String className : MOD_LIST_CLASSES) {
            try {
                Class<?> type = Class.forName(className);
                Object list = type.getMethod("get").invoke(null);
                Method isLoaded = type.getMethod("isLoaded", String.class);
                return Boolean.TRUE.equals(isLoaded.invoke(list, normalized));
            } catch (ClassNotFoundException ignored) {
                // Try the other supported loader.
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return false;
            }
        }
        return false;
    }
}
