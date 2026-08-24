package br.com.modtiktok.tiktokchaos.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;

import java.lang.reflect.Method;

/** Small bridge for the MobEffect texture API changed between Minecraft 1.20 and 1.21. */
final class MinecraftVersionCompat {
    private MinecraftVersionCompat() {
    }

    static TextureAtlasSprite effectSprite(Minecraft minecraft, Object effectReference) {
        Object textures = minecraft.getMobEffectTextures();
        MobEffect effect = unwrap(effectReference);
        for (Method method : textures.getClass().getMethods()) {
            if (!method.getName().equals("get") || method.getParameterCount() != 1) continue;
            Class<?> parameter = method.getParameterTypes()[0];
            Object argument = parameter.isInstance(effectReference) ? effectReference
                    : parameter.isInstance(effect) ? effect : null;
            if (argument == null) continue;
            try {
                return (TextureAtlasSprite) method.invoke(textures, argument);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Não foi possível obter o ícone do efeito", exception);
            }
        }
        throw new IllegalStateException("API de textura de efeitos não reconhecida");
    }

    @SuppressWarnings("unchecked")
    private static MobEffect unwrap(Object effectReference) {
        if (effectReference instanceof Holder<?> holder) {
            return ((Holder<MobEffect>) holder).value();
        }
        return (MobEffect) effectReference;
    }
}
