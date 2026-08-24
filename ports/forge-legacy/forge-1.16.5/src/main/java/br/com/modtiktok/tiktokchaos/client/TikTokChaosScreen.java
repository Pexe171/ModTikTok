package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.TikTokChaosRuntime;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.ITextComponent;

public final class TikTokChaosScreen extends Screen {
    private static final int PANEL_WIDTH = 430;
    private final Screen parent;
    private TextFieldWidget usernameField;

    public TikTokChaosScreen(Screen parent) {
        super(new StringTextComponent("TikTok Chaos"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(18, (height - 255) / 2);
        TikTokChaosRuntime runtime = TikTokChaosMod.runtime();
        usernameField = new TextFieldWidget(font, left + 18, top + 58, PANEL_WIDTH - 36, 22,
                new StringTextComponent("Usuario do TikTok"));
        usernameField.setMaxLength(64);
        usernameField.setValue(runtime.config().connection.username);
        usernameField.setSuggestion("@usuario");
        addButton(usernameField);

        Button connection = new Button(left + 18, top + 92, 126, 22,
                new StringTextComponent(runtime.isConnectionRunning() ? "Desconectar" : "Conectar"), button -> {
                    saveUsername();
                    if (runtime.isConnectionRunning()) runtime.disconnect(); else runtime.connect();
                    rebuildScreen();
                });
        connection.active = runtime.isWorldActive();
        addButton(connection);
        addButton(new Button(left + 152, top + 92, 92, 22, new StringTextComponent("Salvar"), button -> {
            saveUsername();
            runtime.saveConfig();
        }));
        addButton(new Button(left + 252, top + 92, 160, 22, reconnectLabel(), button -> {
            runtime.config().connection.autoReconnect = !runtime.config().connection.autoReconnect;
            runtime.saveConfig();
            rebuildScreen();
        }));
        addSimulation(left + 18, top + 154, "100 curtidas", LiveEventType.LIKE);
        addSimulation(left + 217, top + 154, "Presente (120)", LiveEventType.GIFT);
        addSimulation(left + 18, top + 184, "Comentario !zumbi", LiveEventType.COMMENT);
        addSimulation(left + 217, top + 184, "Novo follow", LiveEventType.FOLLOW);
        addButton(new Button(left + PANEL_WIDTH - 82, top + 222, 82, 20,
                new StringTextComponent("Fechar"), button -> onClose()));
    }

    private void addSimulation(int x, int y, String label, LiveEventType type) {
        Button button = new Button(x, y, 190, 24, new StringTextComponent(label),
                pressed -> TikTokChaosMod.runtime().simulate(type));
        button.active = TikTokChaosMod.runtime().isWorldActive();
        addButton(button);
    }

    @Override
    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTick) {
        fill(matrixStack, 0, 0, width, height, 0xB0000000);
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(18, (height - 255) / 2);
        fillGradient(matrixStack, left - 8, top - 8, left + PANEL_WIDTH + 8, top + 250,
                0xF21A1024, 0xF20E1420);
        fill(matrixStack, left - 8, top - 8, left - 4, top + 250, 0xFFE83E8C);
        super.render(matrixStack, mouseX, mouseY, partialTick);
        font.drawShadow(matrixStack, "TIKTOK", left, top + 4, 0xFFE83E8C);
        font.drawShadow(matrixStack, "CHAOS", left + 44, top + 4, 0xFF66F0C8);
        font.drawShadow(matrixStack, "Forge 1.16.5", left + PANEL_WIDTH - 82, top + 4, 0xFFC6BCCE);
        font.drawShadow(matrixStack, "Conta que esta transmitindo", left + 18, top + 44, 0xFFCFC4D6);
        TikTokChaosRuntime runtime = TikTokChaosMod.runtime();
        int color = runtime.status() == br.com.modtiktok.tiktokchaos.live.ConnectionStatus.CONNECTED
                ? 0xFF66F0C8 : 0xFFFFC857;
        font.drawShadow(matrixStack, runtime.status().label(), left + 18, top + 124, color);
        font.drawShadow(matrixStack, trim(runtime.statusDetail(), 58), left + 18, top + 138, 0xFFCFC4D6);
    }

    private ITextComponent reconnectLabel() {
        boolean enabled = TikTokChaosMod.runtime().config().connection.autoReconnect;
        return new StringTextComponent((enabled ? "[x] " : "[ ] ") + "Reconexao automatica")
                .withStyle(enabled ? TextFormatting.GREEN : TextFormatting.GRAY);
    }

    private void saveUsername() {
        if (usernameField != null) {
            TikTokChaosMod.runtime().config().connection.username = usernameField.getValue();
            TikTokChaosMod.runtime().saveConfig();
        }
    }

    private void rebuildScreen() {
        if (minecraft != null) minecraft.setScreen(new TikTokChaosScreen(parent));
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
