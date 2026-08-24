package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import br.com.modtiktok.tiktokchaos.simulator.SimulationRequest;
import br.com.modtiktok.tiktokchaos.simulator.SimulationResult;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SimulatorScreen extends Screen {
    private static final LiveEventType[] TYPES = {
            LiveEventType.GIFT, LiveEventType.LIKE, LiveEventType.COMMENT, LiveEventType.FOLLOW,
            LiveEventType.SHARE, LiveEventType.SUBSCRIBE, LiveEventType.JOIN, LiveEventType.ROOM_STATS
    };

    private final Screen parent;
    private LiveEventType type = LiveEventType.GIFT;
    private String username = "Teste";
    private String giftId = "5655";
    private String giftName = "Rosa";
    private String unitCoins = "1";
    private String amount = "3";
    private String likes = "100";
    private String comment = "!zumbi";
    private EditBox usernameField;
    private EditBox giftIdField;
    private EditBox giftNameField;
    private EditBox unitCoinsField;
    private EditBox amountField;
    private EditBox likesField;
    private EditBox commentField;
    private SimulationResult result;

    public SimulatorScreen(Screen parent) {
        super(Component.literal("Simulador detalhado"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (width - 560) / 2;
        int top = Math.max(12, (height - 330) / 2);
        usernameField = field(left + 20, top + 54, 180, username, "Espectador");
        addRenderableWidget(Button.builder(Component.literal("Tipo: " + type.name()), button -> {
            saveDraft();
            int index = (indexOf(type) + 1) % TYPES.length;
            type = TYPES[index];
            rebuildScreen();
        }).bounds(left + 212, top + 54, 170, 22).build());

        giftIdField = field(left + 20, top + 100, 88, giftId, "ID");
        giftNameField = field(left + 116, top + 100, 176, giftName, "Nome do presente");
        unitCoinsField = field(left + 300, top + 100, 104, unitCoins, "Moedas/un.");
        amountField = field(left + 412, top + 100, 128, amount, "Quantidade");
        likesField = field(left + 20, top + 146, 88, likes, "Curtidas");
        commentField = field(left + 116, top + 146, 424, comment, "Comentário");

        addRenderableWidget(Button.builder(Component.literal("Prévia"), button -> run(false))
                .bounds(left + 20, top + 188, 120, 22).build());
        Button execute = Button.builder(Component.literal("Executar no mundo"), button -> run(true))
                .bounds(left + 148, top + 188, 164, 22).build();
        execute.active = TikTokChaosMod.runtime().isWorldActive() && !TikTokChaosMod.runtime().areActionsPaused();
        addRenderableWidget(execute);
        addRenderableWidget(Button.builder(Component.literal("Fechar"), button -> onClose())
                .bounds(left + 460, top + 292, 80, 22).build());
    }

    private EditBox field(int x, int y, int width, String value, String hint) {
        EditBox field = new EditBox(font, x, y, width, 22, Component.literal(hint));
        field.setMaxLength(160);
        field.setValue(value);
        field.setHint(Component.literal(hint));
        addRenderableWidget(field);
        return field;
    }

    private void run(boolean execute) {
        saveDraft();
        SimulationRequest request = new SimulationRequest(username, type, number(giftId, -1), giftName,
                number(unitCoins, 0), number(amount, 1), number(likes, 0), comment);
        result = TikTokChaosMod.runtime().simulate(request, execute);
    }

    private void saveDraft() {
        if (usernameField == null) return;
        username = usernameField.getValue();
        giftId = giftIdField.getValue();
        giftName = giftNameField.getValue();
        unitCoins = unitCoinsField.getValue();
        amount = amountField.getValue();
        likes = likesField.getValue();
        comment = commentField.getValue();
    }

    private void rebuildScreen() {
        clearWidgets();
        init();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xB0000000);
        int left = (width - 560) / 2;
        int top = Math.max(12, (height - 330) / 2);
        graphics.fillGradient(left, top, left + 560, top + 330, 0xF21A1024, 0xF20E1420);
        graphics.fill(left, top, left + 4, top + 330, 0xFFE83E8C);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, "SIMULADOR DETALHADO", left + 20, top + 18, 0xFF66F0C8, true);
        graphics.drawString(font, "Usuário", left + 20, top + 42, 0xFFCFC4D6, true);
        graphics.drawString(font, "Presente: ID, nome, moedas por unidade e quantidade", left + 20, top + 88,
                0xFFCFC4D6, true);
        graphics.drawString(font, "Curtidas", left + 20, top + 134, 0xFFCFC4D6, true);
        graphics.drawString(font, "Comentário", left + 116, top + 134, 0xFFCFC4D6, true);
        renderResult(graphics, left, top);
    }

    private void renderResult(GuiGraphics graphics, int left, int top) {
        if (result == null) {
            graphics.drawString(font, "A prévia não altera cooldowns, metas ou a fila.", left + 20, top + 226,
                    0xFFB8AFC0, true);
            return;
        }
        int color = result.executed() ? 0xFF66F0C8 : 0xFFFFD166;
        String headline = (result.executed() ? "EXECUTADO" : "PRÉVIA") + " • " + result.matchedActions()
                + " ações correspondentes • " + result.queuedActions() + " enfileiradas";
        graphics.drawString(font, headline, left + 20, top + 226, color, true);
        int line = 0;
        for (String action : result.actions()) {
            if (line >= 3) break;
            graphics.drawString(font, trim(action, 78), left + 20, top + 244 + line++ * 13, 0xFFE3D9EA, true);
        }
        for (String warning : result.warnings()) {
            if (line >= 4) break;
            graphics.drawString(font, trim(warning, 78), left + 20, top + 244 + line++ * 13, 0xFFFF6B81, true);
        }
    }

    private static int number(String value, int fallback) {
        try {
            return Integer.parseInt(value.strip());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int indexOf(LiveEventType type) {
        for (int index = 0; index < TYPES.length; index++) if (TYPES[index] == type) return index;
        return 0;
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
