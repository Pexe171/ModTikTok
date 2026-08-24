package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.TikTokChaosRuntime;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TikTokChaosScreen extends Screen {
    private static final int PANEL_WIDTH = 430;
    private final Screen parent;
    private EditBox usernameField;

    public TikTokChaosScreen(Screen parent) {
        super(Component.literal("TikTok Chaos"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(18, (height - 255) / 2);
        TikTokChaosRuntime runtime = TikTokChaosMod.runtime();

        usernameField = new EditBox(font, left + 18, top + 58, PANEL_WIDTH - 36, 22,
                Component.literal("Usuario do TikTok"));
        usernameField.setMaxLength(64);
        usernameField.setValue(runtime.config().connection.username);
        usernameField.setSuggestion("@usuario");
        addRenderableWidget(usernameField);

        Button connection = new Button(left + 18, top + 92, 126, 22,
                runtime.isConnectionRunning() ? Component.literal("Desconectar") : Component.literal("Conectar"),
                button -> {
                    saveUsername();
                    if (runtime.isConnectionRunning()) runtime.disconnect(); else runtime.connect();
                    rebuildScreen();
                });
        connection.active = runtime.isWorldActive();
        addRenderableWidget(connection);
        addRenderableWidget(new Button(left + 152, top + 92, 92, 22, Component.literal("Salvar"), button -> {
            saveUsername();
            runtime.saveConfig();
        }));
        addRenderableWidget(new Button(left + 252, top + 92, 160, 22, reconnectLabel(), button -> {
            runtime.config().connection.autoReconnect = !runtime.config().connection.autoReconnect;
            runtime.saveConfig();
            rebuildScreen();
        }));
        addRenderableWidget(new Button(left + 18, top + 122, 194, 22, runStateLabel(), button -> {
            if (runtime.areActionsPaused()) runtime.resumeActions(); else runtime.pauseActions();
            rebuildScreen();
        }));

        addSimulation(left + 18, top + 168, "100 curtidas", LiveEventType.LIKE);
        addSimulation(left + 217, top + 168, "Presente (120)", LiveEventType.GIFT);
        addSimulation(left + 18, top + 198, "Comentario !zumbi", LiveEventType.COMMENT);
        addSimulation(left + 217, top + 198, "Novo follow", LiveEventType.FOLLOW);
        addRenderableWidget(new Button(left + PANEL_WIDTH - 82, top + 222, 82, 20,
                Component.literal("Fechar"), button -> onClose()));
    }

    private void addSimulation(int x, int y, String label, LiveEventType type) {
        Button button = new Button(x, y, 190, 24, Component.literal(label),
                pressed -> TikTokChaosMod.runtime().simulate(type));
        button.active = TikTokChaosMod.runtime().isWorldActive();
        addRenderableWidget(button);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        fill(poseStack, 0, 0, width, height, 0xB0000000);
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(18, (height - 255) / 2);
        fillGradient(poseStack, left - 8, top - 8, left + PANEL_WIDTH + 8, top + 250,
                0xF21A1024, 0xF20E1420);
        fill(poseStack, left - 8, top - 8, left - 4, top + 250, 0xFFE83E8C);
        super.render(poseStack, mouseX, mouseY, partialTick);
        font.drawShadow(poseStack, "TIKTOK", left, top + 4, 0xFFE83E8C);
        font.drawShadow(poseStack, "CHAOS", left + 44, top + 4, 0xFF66F0C8);
        font.drawShadow(poseStack, "Forge 1.19.2", left + PANEL_WIDTH - 82, top + 4, 0xFFC6BCCE);
        font.drawShadow(poseStack, "Conta que esta transmitindo", left + 18, top + 44, 0xFFCFC4D6);
        TikTokChaosRuntime runtime = TikTokChaosMod.runtime();
        int color = runtime.status() == br.com.modtiktok.tiktokchaos.live.ConnectionStatus.CONNECTED
                ? 0xFF66F0C8 : 0xFFFFC857;
        font.drawShadow(poseStack, runtime.status().label() + " - ACOES " + runtime.runState().label(),
                left + 224, top + 126, color);
        font.drawShadow(poseStack, "F9 = emergencia e limpeza", left + 18, top + 150, 0xFFFF6B81);
    }

    private Component reconnectLabel() {
        boolean enabled = TikTokChaosMod.runtime().config().connection.autoReconnect;
        return Component.literal((enabled ? "[x] " : "[ ] ") + "Reconexao automatica")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private Component runStateLabel() {
        boolean paused = TikTokChaosMod.runtime().areActionsPaused();
        return Component.literal(paused ? "> Retomar acoes" : "|| Pausar acoes")
                .withStyle(paused ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
    }

    private void saveUsername() {
        if (usernameField != null) {
            TikTokChaosMod.runtime().config().connection.username = usernameField.getValue();
            TikTokChaosMod.runtime().saveConfig();
        }
    }

    private void rebuildScreen() {
        clearWidgets();
        init();
    }

    @Override
    public void onClose() {
        saveUsername();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
