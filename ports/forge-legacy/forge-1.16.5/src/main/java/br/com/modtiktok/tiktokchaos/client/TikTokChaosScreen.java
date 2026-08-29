package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.TikTokChaosRuntime;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.ITextComponent;

public final class TikTokChaosScreen extends Screen {
    private static final int PANEL_WIDTH = 430;
    private final Screen parent;
    private TextFieldWidget usernameField;

    public TikTokChaosScreen(Screen parent) {
        super(new TranslationTextComponent("gui.tiktokchaos.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(18, (height - 255) / 2);
        TikTokChaosRuntime runtime = TikTokChaosMod.runtime();
        usernameField = new TextFieldWidget(font, left + 18, top + 58, PANEL_WIDTH - 36, 22,
                new TranslationTextComponent("gui.tiktokchaos.username"));
        usernameField.setMaxLength(64);
        usernameField.setValue(runtime.config().connection.username);
        usernameField.setSuggestion(ClientText.text("gui.tiktokchaos.username_hint"));
        addButton(usernameField);

        Button connection = new Button(left + 18, top + 92, 126, 22,
                new TranslationTextComponent(runtime.isConnectionRunning() ? "gui.tiktokchaos.disconnect"
                        : "gui.tiktokchaos.connect"), button -> {
                    saveUsername();
                    if (runtime.isConnectionRunning()) runtime.disconnect(); else runtime.connect();
                    rebuildScreen();
                });
        connection.active = runtime.isWorldActive();
        addButton(connection);
        addButton(new Button(left + 152, top + 92, 92, 22,
                new TranslationTextComponent("gui.tiktokchaos.save"), button -> {
            saveUsername();
            runtime.saveConfig();
        }));
        addButton(new Button(left + 252, top + 92, 160, 22, reconnectLabel(), button -> {
            runtime.config().connection.autoReconnect = !runtime.config().connection.autoReconnect;
            runtime.saveConfig();
            rebuildScreen();
        }));
        addButton(new Button(left + 18, top + 122, 194, 22, runStateLabel(), button -> {
            if (runtime.areActionsPaused()) runtime.resumeActions(); else runtime.pauseActions();
            rebuildScreen();
        }));
        addButton(new Button(left + 220, top + 122, 192, 22,
                new TranslationTextComponent("gui.tiktokchaos.popular_mod_presets"), button -> {
            saveUsername();
            if (minecraft != null) minecraft.setScreen(new PopularModPresetsScreen(this));
        }));
        addSimulation(left + 18, top + 168, "gui.tiktokchaos.simulate_likes", LiveEventType.LIKE);
        addSimulation(left + 217, top + 168, "gui.tiktokchaos.simulate_gift", LiveEventType.GIFT);
        addSimulation(left + 18, top + 198, "gui.tiktokchaos.simulate_comment", LiveEventType.COMMENT);
        addSimulation(left + 217, top + 198, "gui.tiktokchaos.simulate_follow", LiveEventType.FOLLOW);
        addButton(new Button(left + PANEL_WIDTH - 82, top + 222, 82, 20,
                new TranslationTextComponent("gui.tiktokchaos.close"), button -> onClose()));
    }

    private void addSimulation(int x, int y, String key, LiveEventType type) {
        Button button = new Button(x, y, 190, 24, new TranslationTextComponent(key),
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
        font.drawShadow(matrixStack, ClientText.text("gui.tiktokchaos.streaming_account"), left + 18, top + 44,
                0xFFCFC4D6);
        TikTokChaosRuntime runtime = TikTokChaosMod.runtime();
        int color = runtime.status() == br.com.modtiktok.tiktokchaos.live.ConnectionStatus.CONNECTED
                ? 0xFF66F0C8 : 0xFFFFC857;
        font.drawShadow(matrixStack, ClientText.text("gui.tiktokchaos.connection_state_legacy",
                        ClientText.status(runtime.status()), ClientText.runState(runtime.runState())),
                left + 224, top + 126, color);
        font.drawShadow(matrixStack, ClientText.text("gui.tiktokchaos.f9_cleanup_legacy"), left + 18, top + 150,
                0xFFFF6B81);
    }

    private ITextComponent reconnectLabel() {
        boolean enabled = TikTokChaosMod.runtime().config().connection.autoReconnect;
        return new StringTextComponent(enabled ? "[x] " : "[ ] ")
                .append(new TranslationTextComponent("gui.tiktokchaos.auto_reconnect"))
                .withStyle(enabled ? TextFormatting.GREEN : TextFormatting.GRAY);
    }

    private ITextComponent runStateLabel() {
        boolean paused = TikTokChaosMod.runtime().areActionsPaused();
        return new TranslationTextComponent(paused ? "gui.tiktokchaos.resume_actions_legacy"
                        : "gui.tiktokchaos.pause_actions_legacy")
                .withStyle(paused ? TextFormatting.GREEN : TextFormatting.YELLOW);
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
