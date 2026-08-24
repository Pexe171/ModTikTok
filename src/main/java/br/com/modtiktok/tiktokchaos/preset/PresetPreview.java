package br.com.modtiktok.tiktokchaos.preset;

import java.util.List;

public record PresetPreview(
        String presetId,
        String presetName,
        PresetApplyMode mode,
        int resultingRules,
        int addedRules,
        int replacedRules,
        int renamedRules,
        int disabledRules,
        List<String> warnings
) {
}
