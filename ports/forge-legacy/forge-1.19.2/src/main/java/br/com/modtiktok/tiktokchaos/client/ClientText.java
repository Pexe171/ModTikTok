package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.ActionRunState;
import br.com.modtiktok.tiktokchaos.live.ConnectionStatus;
import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import net.minecraft.client.resources.language.I18n;

import java.util.Locale;

final class ClientText {
    private ClientText() {
    }

    static String text(String key, Object... arguments) {
        return I18n.get(key, arguments);
    }

    static String status(ConnectionStatus status) {
        return text("status.tiktokchaos.connection." + status.name().toLowerCase(Locale.ROOT));
    }

    static String runState(ActionRunState state) {
        return text("status.tiktokchaos.actions." + state.name().toLowerCase(Locale.ROOT));
    }

    static String configuredName(String category, String id, String fallback) {
        if (id == null || id.isBlank()) return fallback;
        String key = category + ".tiktokchaos." + id.toLowerCase(Locale.ROOT).replace('_', '-');
        return I18n.exists(key) ? I18n.get(key) : fallback;
    }

    static String eventSummary(LiveEvent event) {
        String user = event.userName();
        return switch (event.type()) {
            case LIKE -> text("history.tiktokchaos.like", user, event.amount());
            case GIFT -> text("history.tiktokchaos.gift", user, event.giftName(), event.amount());
            case COMMENT -> text("history.tiktokchaos.comment", user, event.comment());
            case FOLLOW -> text("history.tiktokchaos.follow", user);
            case SHARE -> text("history.tiktokchaos.share", user);
            case SUBSCRIBE -> text("history.tiktokchaos.subscribe", user);
            case JOIN -> text("history.tiktokchaos.join", user);
            case ROOM_STATS -> text("history.tiktokchaos.room_stats", event.total());
            case LIVE_STARTED -> text("history.tiktokchaos.live_started");
            case LIVE_ENDED -> text("history.tiktokchaos.live_ended");
        };
    }
}
