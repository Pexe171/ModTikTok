package br.com.modtiktok.tiktokchaos.gameplay;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Random;

/** Immutable, lazily built registry pools used by high-frequency LIVE actions. */
final class RandomActionTargets {
    private RandomActionTargets() {
    }

    static EntityType<?> entity(Random random) {
        return pick(Pools.ENTITIES, random, "Nenhum mob disponível");
    }

    static Item item(Random random) {
        return pick(Pools.ITEMS, random, "Nenhum item disponível");
    }

    static Holder<MobEffect> effect(Random random) {
        return pick(Pools.EFFECTS, random, "Nenhum efeito disponível");
    }

    private static <T> T pick(List<T> values, Random random, String emptyMessage) {
        if (values.isEmpty()) throw new IllegalStateException(emptyMessage);
        return values.get(random.nextInt(values.size()));
    }

    /** Loaded only when the first random action executes, after registries are complete. */
    private static final class Pools {
        private static final List<EntityType<?>> ENTITIES = BuiltInRegistries.ENTITY_TYPE.stream()
                .filter(ActionTargets::isAllowedEntity)
                .toList();
        private static final List<Item> ITEMS = BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .toList();
        private static final List<Holder<MobEffect>> EFFECTS = BuiltInRegistries.MOB_EFFECT.stream()
                .map(BuiltInRegistries.MOB_EFFECT::wrapAsHolder)
                .toList();
    }
}
