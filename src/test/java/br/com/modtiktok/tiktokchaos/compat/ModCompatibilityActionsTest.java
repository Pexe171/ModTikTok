package br.com.modtiktok.tiktokchaos.compat;

import br.com.modtiktok.tiktokchaos.rule.ActionSpec;
import br.com.modtiktok.tiktokchaos.rule.ActionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModCompatibilityActionsTest {
    @Test
    void createsOnlyTheAllowListedTaczGiveCommand() {
        ActionSpec action = new ActionSpec(ActionType.MOD_INTEGRATION, "tacz:ammo_50bmg", 8, 0, 0, 0, "");

        assertEquals("give @s tacz:ammo{AmmoId:\"tacz:50bmg\"} 8",
                ModCompatibilityActions.command(action));
    }

    @Test
    void clampsCommandAmountsAndRejectsArbitraryTargets() {
        ActionSpec cobblemon = new ActionSpec(ActionType.MOD_INTEGRATION, "cobblemon:random", 5_000,
                0, 0, 0, "");
        ActionSpec arbitrary = new ActionSpec(ActionType.MOD_INTEGRATION, "minecraft:op", 1,
                0, 0, 0, "");

        assertEquals("spawnpokemonfrompool 64", ModCompatibilityActions.command(cobblemon));
        assertThrows(IllegalArgumentException.class, () -> ModCompatibilityActions.command(arbitrary));
        assertTrue(ModCompatibilityActions.isKnown("pixelmon:random_shiny"));
    }
}
