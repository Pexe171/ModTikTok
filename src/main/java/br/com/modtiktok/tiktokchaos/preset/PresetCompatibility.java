package br.com.modtiktok.tiktokchaos.preset;

import java.util.List;

public record PresetCompatibility(boolean available, List<String> missingRequirements) {
}
