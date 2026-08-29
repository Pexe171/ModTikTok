package br.com.modtiktok.tiktokchaos.compat;

import br.com.modtiktok.tiktokchaos.rule.ActionSpec;

import java.util.Map;

/** Closed allow-list of commands owned by supported third-party mod integrations. */
public final class ModCompatibilityActions {
    private static final Map<String, String> REQUIRED_MODS = Map.of(
            "pixelmon:random_shiny", "pixelmon",
            "cobblemon:random", "cobblemon",
            "tacz:ammo_9mm", "tacz",
            "tacz:ammo_12g", "tacz",
            "tacz:ammo_556x45", "tacz",
            "tacz:ammo_50bmg", "tacz"
    );

    private ModCompatibilityActions() {
    }

    public static boolean isAvailable(String target) {
        String required = REQUIRED_MODS.get(target);
        return required != null && ModEnvironment.isLoaded(required);
    }

    public static boolean isKnown(String target) {
        return REQUIRED_MODS.containsKey(target);
    }

    public static String command(ActionSpec action) {
        if (action == null || !isKnown(action.target)) {
            throw new IllegalArgumentException("Unknown mod integration: " + (action == null ? "" : action.target));
        }
        int amount = Math.max(1, Math.min(64, action.amount));
        return switch (action.target) {
            case "pixelmon:random_shiny" -> "pokespawn random shiny";
            case "cobblemon:random" -> "spawnpokemonfrompool " + amount;
            case "tacz:ammo_9mm" -> ammo("9mm", amount);
            case "tacz:ammo_12g" -> ammo("12g", amount);
            case "tacz:ammo_556x45" -> ammo("556x45", amount);
            case "tacz:ammo_50bmg" -> ammo("50bmg", amount);
            default -> throw new IllegalArgumentException("Unknown mod integration: " + action.target);
        };
    }

    public static String displayName(String target) {
        return switch (target) {
            case "pixelmon:random_shiny" -> "Pixelmon: random shiny";
            case "cobblemon:random" -> "Cobblemon: random Pokemon";
            case "tacz:ammo_9mm" -> "TaCZ: 9mm ammo";
            case "tacz:ammo_12g" -> "TaCZ: 12 gauge ammo";
            case "tacz:ammo_556x45" -> "TaCZ: 5.56x45 ammo";
            case "tacz:ammo_50bmg" -> "TaCZ: .50 BMG ammo";
            default -> target;
        };
    }

    private static String ammo(String ammoId, int amount) {
        return "give @s tacz:ammo{AmmoId:\"tacz:" + ammoId + "\"} " + amount;
    }
}
