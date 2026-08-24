package br.com.modtiktok.tiktokchaos.gameplay;

import net.minecraft.core.Registry;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/** Registry pools for the pre-1.19.3 registry API. */
final class RandomActionTargets {
    private RandomActionTargets() {
    }

    static EntityType<?> entity(Random random) {
        return pick(Pools.ENTITIES, random, "Nenhum mob disponivel");
    }

    static Item item(Random random) {
        return pick(Pools.ITEMS, random, "Nenhum item disponivel");
    }

    static MobEffect effect(Random random) {
        return pick(Pools.EFFECTS, random, "Nenhum efeito disponivel");
    }

    private static <T> T pick(List<T> values, Random random, String emptyMessage) {
        if (values.isEmpty()) throw new IllegalStateException(emptyMessage);
        return values.get(random.nextInt(values.size()));
    }

    private static final class Pools {
        private static final List<EntityType<?>> ENTITIES = Registry.ENTITY_TYPE.stream()
                .filter(ActionTargets::isAllowedEntity)
                .collect(Collectors.toUnmodifiableList());
        private static final List<Item> ITEMS = Registry.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .collect(Collectors.toUnmodifiableList());
        private static final List<MobEffect> EFFECTS = Registry.MOB_EFFECT.stream()
                .collect(Collectors.toUnmodifiableList());
    }
}
