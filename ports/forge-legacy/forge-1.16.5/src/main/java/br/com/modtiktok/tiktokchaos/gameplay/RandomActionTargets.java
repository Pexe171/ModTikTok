package br.com.modtiktok.tiktokchaos.gameplay;

import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.potion.Effect;
import net.minecraft.util.registry.Registry;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

final class RandomActionTargets {
    private RandomActionTargets() {
    }

    static EntityType<?> entity(Random random) {
        return pick(Pools.ENTITIES, random, "Nenhum mob disponivel");
    }

    static Item item(Random random) {
        return pick(Pools.ITEMS, random, "Nenhum item disponivel");
    }

    static Effect effect(Random random) {
        return pick(Pools.EFFECTS, random, "Nenhum efeito disponivel");
    }

    private static <T> T pick(List<T> values, Random random, String emptyMessage) {
        if (values.isEmpty()) throw new IllegalStateException(emptyMessage);
        return values.get(random.nextInt(values.size()));
    }

    private static final class Pools {
        private static final List<EntityType<?>> ENTITIES = Registry.ENTITY_TYPE.stream()
                .filter(ActionTargets::isAllowedEntity)
                .collect(Collectors.toList());
        private static final List<Item> ITEMS = Registry.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .collect(Collectors.toList());
        private static final List<Effect> EFFECTS = Registry.MOB_EFFECT.stream().collect(Collectors.toList());
    }
}
