package br.com.modtiktok.tiktokchaos.simulator;

import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;

import java.util.UUID;

public record SimulationRequest(
        String username,
        LiveEventType type,
        int giftId,
        String giftName,
        int unitCoins,
        int amount,
        int likes,
        String comment
) {
    public SimulationRequest {
        username = username == null || username.isBlank() ? "Teste" : username.strip();
        type = type == null ? LiveEventType.GIFT : type;
        giftName = giftName == null || giftName.isBlank() ? "Rosa de teste" : giftName.strip();
        unitCoins = Math.max(0, unitCoins);
        amount = Math.max(1, amount);
        likes = Math.max(0, likes);
        comment = comment == null ? "" : comment;
    }

    public LiveEvent toEvent() {
        return switch (type) {
            case LIKE -> LiveEvent.like(username, likes);
            case GIFT -> LiveEvent.gift(username, giftId, giftName, unitCoins, amount);
            case COMMENT -> LiveEvent.comment(username, comment);
            case ROOM_STATS -> new LiveEvent(UUID.randomUUID().toString(), type, System.currentTimeMillis(), "",
                    username, 0, amount, -1, "", 0, "");
            default -> LiveEvent.simple(type, username);
        };
    }

    public static SimulationRequest defaults(LiveEventType type) {
        return switch (type) {
            case LIKE -> new SimulationRequest("Teste", type, -1, "", 0, 1, 100, "");
            case GIFT -> new SimulationRequest("Teste", type, 5655, "Rosa de teste", 1, 3, 0, "");
            case COMMENT -> new SimulationRequest("Teste", type, -1, "", 0, 1, 0, "!zumbi");
            case ROOM_STATS -> new SimulationRequest("TikTok", type, -1, "", 0, 42, 0, "");
            default -> new SimulationRequest("Teste", type, -1, "", 0, 1, 0, "");
        };
    }
}
