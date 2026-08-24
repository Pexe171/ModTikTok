package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.ActionRunState;
import br.com.modtiktok.tiktokchaos.live.ConnectionStatus;
import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import br.com.modtiktok.tiktokchaos.rule.ActionType;
import br.com.modtiktok.tiktokchaos.rule.ExecutionMode;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/** Central client-side access to Minecraft's currently selected language. */
final class ClientText {
    private ClientText() {
    }

    static MutableComponent component(String key, Object... arguments) {
        return Component.translatable(key, arguments);
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

    static String event(LiveEventType type) {
        return text("event.tiktokchaos." + type.name().toLowerCase(Locale.ROOT));
    }

    static String action(ActionType type) {
        return text("action.tiktokchaos." + type.name().toLowerCase(Locale.ROOT));
    }

    static String execution(ExecutionMode mode) {
        return text("execution.tiktokchaos." + mode.name().toLowerCase(Locale.ROOT));
    }

    static String eventSummary(LiveEvent event) {
        String user = viewerName(event.userName());
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

    static String viewerName(String value) {
        if (value == null || value.isBlank() || value.equals("Anônimo")) return text("gui.tiktokchaos.anonymous");
        return value.equals("Espectador") ? text("gui.tiktokchaos.viewer") : value;
    }

    /** Translates shared-core status messages while preserving external exception details verbatim. */
    static String runtimeMessage(String value) {
        if (value == null || value.isBlank()) return "";
        return switch (value) {
            case "Abra um mundo para conectar" -> text("runtime.tiktokchaos.open_world");
            case "Mundo pronto; pressione F8 para conectar" -> text("runtime.tiktokchaos.world_ready");
            case "Nenhuma ação executada" -> text("runtime.tiktokchaos.no_action");
            case "LIVE encerrada; ações temporárias removidas" -> text("runtime.tiktokchaos.live_cleanup");
            case "⚠ Fila cheia; evento de baixa prioridade descartado" -> text("runtime.tiktokchaos.queue_full");
            case "Desconectado; ações temporárias removidas" -> text("runtime.tiktokchaos.disconnected_cleanup");
            case "Ações pausadas; a LIVE continua conectada" -> text("runtime.tiktokchaos.actions_paused");
            case "Ações retomadas pelo painel" -> text("runtime.tiktokchaos.actions_resumed");
            case "EMERGÊNCIA F9: fila, sequências, mobs, efeitos, clima e blocos restaurados" ->
                    text("runtime.tiktokchaos.emergency_cleanup");
            case "Fila limpa manualmente" -> text("runtime.tiktokchaos.queue_cleared");
            case "Estatísticas da sessão zeradas" -> text("runtime.tiktokchaos.session_reset");
            case "Informe o @usuário da LIVE" -> text("runtime.tiktokchaos.username_required");
            case "Desconectado manualmente" -> text("runtime.tiktokchaos.disconnected_manually");
            case "Abra um mundo singleplayer antes de conectar" -> text("runtime.tiktokchaos.open_singleplayer");
            case "As ações estão pausadas; use o painel para retomar" ->
                    text("runtime.tiktokchaos.simulation_paused");
            case "Abra um mundo singleplayer para executar o teste" ->
                    text("runtime.tiktokchaos.simulation_open_world");
            default -> runtimeMessageWithArgument(value);
        };
    }

    private static String runtimeMessageWithArgument(String value) {
        String prefix = "Conectando em @";
        if (value.startsWith(prefix)) return text("runtime.tiktokchaos.connecting_to", value.substring(prefix.length()));
        prefix = "Conectado em @";
        if (value.startsWith(prefix)) return text("runtime.tiktokchaos.connected_to", value.substring(prefix.length()));
        prefix = "Reconectando em @";
        if (value.startsWith(prefix)) return text("runtime.tiktokchaos.reconnecting_to", value.substring(prefix.length()));
        prefix = "Nova tentativa em ";
        if (value.startsWith(prefix)) return text("runtime.tiktokchaos.retry_in", value.substring(prefix.length()));
        prefix = "Conexão encerrada: ";
        if (value.startsWith(prefix)) return text("runtime.tiktokchaos.connection_closed", value.substring(prefix.length()));
        prefix = "Preset não encontrado: ";
        if (value.startsWith(prefix)) return text("runtime.tiktokchaos.preset_missing", value.substring(prefix.length()));
        prefix = "Preset exportado: ";
        if (value.startsWith(prefix)) return text("runtime.tiktokchaos.preset_exported", value.substring(prefix.length()));
        prefix = "Falha ao exportar preset: ";
        if (value.startsWith(prefix)) return text("runtime.tiktokchaos.preset_export_failed", value.substring(prefix.length()));
        prefix = "Overlay local não iniciou: ";
        if (value.startsWith(prefix)) return text("runtime.tiktokchaos.overlay_failed", value.substring(prefix.length()));
        prefix = "Configuração inválida: ";
        if (value.startsWith(prefix)) return text("runtime.tiktokchaos.config_invalid", value.substring(prefix.length()));
        prefix = "Não foi possível salvar: ";
        if (value.startsWith(prefix)) return text("runtime.tiktokchaos.save_failed", value.substring(prefix.length()));
        String suffix = " ações seriam descartadas pelos limites da fila";
        if (value.endsWith(suffix)) return text("runtime.tiktokchaos.simulation_queue_drop",
                value.substring(0, value.length() - suffix.length()));
        prefix = "Preset aplicado: ";
        if (value.startsWith(prefix)) return text("runtime.tiktokchaos.preset_applied", value.substring(prefix.length()));
        return value;
    }

    static String configuredName(String category, String id, String fallback) {
        if (id == null || id.isBlank()) return fallback;
        String key = category + ".tiktokchaos." + id.toLowerCase(Locale.ROOT).replace('_', '-');
        return I18n.exists(key) ? I18n.get(key) : fallback;
    }
}
