package br.com.modtiktok.tiktokchaos.gameplay;

import net.minecraft.entity.EntityType;
import net.minecraft.item.SpawnEggItem;

public final class ActionTargets {
    public static final String RANDOM_TARGET = "tiktokchaos:random";

    private ActionTargets() {
    }

    public static boolean isAllowedEntity(EntityType<?> type) {
        return type.canSummon() && SpawnEggItem.byId(type) != null;
    }

    public static boolean isRandom(String target) {
        return RANDOM_TARGET.equals(target);
    }
}
