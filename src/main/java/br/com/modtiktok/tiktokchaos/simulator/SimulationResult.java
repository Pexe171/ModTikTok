package br.com.modtiktok.tiktokchaos.simulator;

import br.com.modtiktok.tiktokchaos.live.LiveEvent;

import java.util.List;

public record SimulationResult(
        LiveEvent event,
        boolean executed,
        int matchedActions,
        int queuedActions,
        List<String> actions,
        List<String> warnings
) {
    public SimulationResult {
        actions = List.copyOf(actions);
        warnings = List.copyOf(warnings);
    }
}
