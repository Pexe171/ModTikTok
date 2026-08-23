package br.com.modtiktok.tiktokchaos.live;

public enum ConnectionStatus {
    DISCONNECTED("Desconectado"),
    CONNECTING("Conectando"),
    CONNECTED("Conectado"),
    WAITING_FOR_LIVE("Aguardando a LIVE"),
    RECONNECTING("Reconectando"),
    ERROR("Erro");

    private final String label;

    ConnectionStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
