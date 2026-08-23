package br.com.modtiktok.tiktokchaos.gameplay;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;

/** Shared selection/execution policy for visual action targets. */
public final class ActionTargets {
    public static final String RANDOM_TARGET = "tiktokchaos:random";

    private ActionTargets() {
    }

    /**
     * Spawn eggs give the catalog a real icon and exclude players, projectiles,
     * technical entities and vanilla bosses that are unsafe for LIVE automation.
     */
    public static boolean isAllowedEntity(EntityType<?> type) {
        return type.canSummon() && SpawnEggItem.byId(type) != null;
    }

    public static boolean isRandom(String target) {
        return RANDOM_TARGET.equals(target);
    }
}
