package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.TikTokChaosRuntime;
import br.com.modtiktok.tiktokchaos.config.TikTokChaosConfig;
import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import br.com.modtiktok.tiktokchaos.rule.Rule;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class TikTokChaosScreen extends Screen {
    private static final int PANEL_WIDTH = 430;
    private static final int RULES_PER_PAGE = 7;

    private final Screen parent;
    private Tab tab = Tab.CONNECTION;
    private EditBox usernameField;
    private int rulePage;

    public TikTokChaosScreen(Screen parent) {
        super(Component.translatable("gui.tiktokchaos.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(18, (height - 255) / 2);
        int tabWidth = PANEL_WIDTH / Tab.values().length;

        for (int index = 0; index < Tab.values().length; index++) {
            Tab value = Tab.values()[index];
            addRenderableWidget(Button.builder(Component.literal(value.label), button -> setTab(value))
                    .bounds(left + index * tabWidth, top + 32, tabWidth - 2, 20).build());
        }

        switch (tab) {
            case CONNECTION -> initConnection(left, top);
            case RULES -> initRules(left, top);
            case HISTORY -> initHistory(left, top);
            case SAFETY -> initSafety(left, top);
            case SIMULATOR -> initSimulator(left, top);
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.tiktokchaos.close"), button -> onClose())
                .bounds(left + PANEL_WIDTH - 82, top + 222, 82, 20).build());
    }

    private void initConnection(int left, int top) {
        TikTokChaosRuntime runtime = TikTokChaosMod.runtime();
        usernameField = new EditBox(font, left + 18, top + 78, PANEL_WIDTH - 36, 22,
                Component.translatable("gui.tiktokchaos.username"));
        usernameField.setMaxLength(64);
        usernameField.setValue(runtime.config().connection.username);
        usernameField.setHint(Component.literal("@usuario"));
        addRenderableWidget(usernameField);

        Component connectionLabel = runtime.isConnectionRunning()
                ? Component.translatable("gui.tiktokchaos.disconnect")
                : Component.translatable("gui.tiktokchaos.connect");
        Button connection = Button.builder(connectionLabel, button -> {
            saveUsername();
            if (runtime.isConnectionRunning()) runtime.disconnect(); else runtime.connect();
            rebuildScreen();
        }).bounds(left + 18, top + 112, 126, 22).build();
        connection.active = runtime.isWorldActive();
        addRenderableWidget(connection);

        addRenderableWidget(Button.builder(Component.translatable("gui.tiktokchaos.save"), button -> {
            saveUsername();
            runtime.saveConfig();
        }).bounds(left + 152, top + 112, 92, 22).build());

        addRenderableWidget(Button.builder(autoReconnectLabel(), button -> {
            runtime.config().connection.autoReconnect = !runtime.config().connection.autoReconnect;
            rebuildScreen();
        }).bounds(left + 252, top + 112, 160, 22).build());
    }

    private void initRules(int left, int top) {
        List<Rule> rules = TikTokChaosMod.runtime().config().rules;
        int pageCount = Math.max(1, (rules.size() + RULES_PER_PAGE - 1) / RULES_PER_PAGE);
        rulePage = Math.max(0, Math.min(rulePage, pageCount - 1));
        int first = rulePage * RULES_PER_PAGE;
        int last = Math.min(rules.size(), first + RULES_PER_PAGE);
        for (int index = first; index < last; index++) {
            Rule rule = rules.get(index);
            int y = top + 66 + (index - first) * 21;
            addRenderableWidget(Button.builder(ruleLabel(rule), button -> {
                rule.enabled = !rule.enabled;
                TikTokChaosMod.runtime().saveConfig();
                rebuildScreen();
            }).bounds(left + 18, y, PANEL_WIDTH - 128, 19).build());
            addRenderableWidget(Button.builder(Component.literal("Editar"), button -> {
                if (minecraft != null) minecraft.setScreen(new RuleEditorScreen(this, rule, false));
            }).bounds(left + PANEL_WIDTH - 104, y, 86, 19).build());
        }
        addRenderableWidget(Button.builder(Component.literal("+ Nova regra"), button -> {
            Rule rule = RuleEditorScreen.newRule();
            if (minecraft != null) minecraft.setScreen(new RuleEditorScreen(this, rule, true));
        }).bounds(left + 92, top + 218, 112, 20).build());
        if (pageCount > 1) {
            Button previous = Button.builder(Component.literal("‹"), button -> {
                rulePage--;
                rebuildScreen();
            }).bounds(left + 18, top + 218, 28, 20).build();
            previous.active = rulePage > 0;
            addRenderableWidget(previous);
            Button next = Button.builder(Component.literal("›"), button -> {
                rulePage++;
                rebuildScreen();
            }).bounds(left + 52, top + 218, 28, 20).build();
            next.active = rulePage + 1 < pageCount;
            addRenderableWidget(next);
        }
    }

    private void initHistory(int left, int top) {
        addRenderableWidget(Button.builder(Component.literal("Limpar fila"), button -> TikTokChaosMod.runtime().clearQueue())
                .bounds(left + 18, top + 212, 96, 20).build());
    }

    private void initSafety(int left, int top) {
        TikTokChaosConfig.Safety safety = TikTokChaosMod.runtime().config().safety;
        addCounter(left, top + 74, "Ações por segundo", () -> safety.maxActionsPerSecond,
                value -> safety.maxActionsPerSecond = clamp(value, 1, 20));
        addCounter(left, top + 110, "Limite de mobs", () -> safety.maxTrackedMobs,
                value -> safety.maxTrackedMobs = clamp(value, 1, 100));
        addCounter(left, top + 146, "Tempo dos mobs (s)", () -> safety.mobLifetimeSeconds,
                value -> safety.mobLifetimeSeconds = clamp(value, 10, 600));
        addRenderableWidget(Button.builder(hudLabel(), button -> {
            TikTokChaosMod.runtime().config().hud.enabled = !TikTokChaosMod.runtime().config().hud.enabled;
            TikTokChaosMod.runtime().saveConfig();
            rebuildScreen();
        }).bounds(left + 18, top + 184, 180, 21).build());
    }

    private void initSimulator(int left, int top) {
        LiveEventType[] types = {
                LiveEventType.LIKE, LiveEventType.GIFT, LiveEventType.COMMENT, LiveEventType.FOLLOW,
                LiveEventType.SHARE, LiveEventType.SUBSCRIBE, LiveEventType.JOIN, LiveEventType.ROOM_STATS
        };
        for (int index = 0; index < types.length; index++) {
            LiveEventType type = types[index];
            int column = index % 2;
            int row = index / 2;
            Button button = Button.builder(Component.literal(simulationLabel(type)),
                    pressed -> TikTokChaosMod.runtime().simulate(type))
                    .bounds(left + 18 + column * 199, top + 76 + row * 32, 190, 24).build();
            button.active = TikTokChaosMod.runtime().isWorldActive();
            addRenderableWidget(button);
        }
    }

    private void addCounter(int left, int y, String label, IntGetter getter, IntSetter setter) {
        addRenderableWidget(Button.builder(Component.literal("−"), button -> {
            setter.set(getter.get() - 1);
            TikTokChaosMod.runtime().saveConfig();
            rebuildScreen();
        }).bounds(left + 286, y, 32, 22).build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> {
            setter.set(getter.get() + 1);
            TikTokChaosMod.runtime().saveConfig();
            rebuildScreen();
        }).bounds(left + 380, y, 32, 22).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Avoid Screen#renderBackground here: shader-based menu blur from UI mods can
        // also blur everything we draw before the vanilla widgets.
        graphics.fill(0, 0, width, height, 0xB0000000);
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(18, (height - 255) / 2);

        graphics.fillGradient(left - 8, top - 8, left + PANEL_WIDTH + 8, top + 250,
                0xF21A1024, 0xF20E1420);
        graphics.fill(left - 8, top - 8, left - 4, top + 250, 0xFFE83E8C);

        // FancyMenu applies its blur while the vanilla screen renders. Draw our
        // labels afterwards so they remain on the sharp foreground layer.
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, "TIKTOK", left, top + 4, 0xFFE83E8C, true);
        graphics.drawString(font, "CHAOS", left + 44, top + 4, 0xFF66F0C8, true);
        graphics.drawString(font, "NeoForge 1.21.1", left + PANEL_WIDTH - 102, top + 4, 0xFFC6BCCE, true);

        switch (tab) {
            case CONNECTION -> renderConnection(graphics, left, top);
            case RULES -> renderRules(graphics, left, top);
            case HISTORY -> renderHistory(graphics, left, top);
            case SAFETY -> renderSafety(graphics, left, top);
            case SIMULATOR -> renderSimulator(graphics, left, top);
        }
    }

    private void renderConnection(GuiGraphics graphics, int left, int top) {
        TikTokChaosRuntime runtime = TikTokChaosMod.runtime();
        graphics.drawString(font, "Conta que está transmitindo", left + 18, top + 64, 0xFFCFC4D6, true);
        int color = runtime.status() == br.com.modtiktok.tiktokchaos.live.ConnectionStatus.CONNECTED
                ? 0xFF66F0C8 : 0xFFFFC857;
        graphics.drawString(font, "● " + runtime.status().label(), left + 18, top + 152, color, true);
        graphics.drawString(font, trim(runtime.statusDetail(), 60), left + 18, top + 168, 0xFFCFC4D6, true);
        String error = runtime.configManager().getLastError();
        if (!error.isBlank()) graphics.drawString(font, trim(error, 60), left + 18, top + 188, 0xFFFF6B81, true);
        graphics.drawString(font, "A integração é não oficial e pode exigir atualização futura.",
                left + 18, top + 207, 0xFFB8AFC0, true);
    }

    private void renderRules(GuiGraphics graphics, int left, int top) {
        int enabled = (int) TikTokChaosMod.runtime().config().rules.stream().filter(rule -> rule.enabled).count();
        graphics.drawString(font, enabled + " regras ativas • clique para ligar/desligar",
                left + 18, top + 56, 0xFFBDB0C7, true);
    }

    private void renderHistory(GuiGraphics graphics, int left, int top) {
        List<LiveEvent> events = TikTokChaosMod.runtime().historySnapshot();
        graphics.drawString(font, "Últimos eventos desta sessão", left + 18, top + 60, 0xFFBDB0C7, true);
        int count = Math.min(9, events.size());
        for (int index = 0; index < count; index++) {
            LiveEvent event = events.get(index);
            int color = event.type() == LiveEventType.GIFT ? 0xFFFFD166 : 0xFFD9CEDF;
            graphics.drawString(font, trim(event.summary(), 60), left + 18, top + 78 + index * 14, color, true);
        }
        if (events.isEmpty()) {
            graphics.drawString(font, "Nenhum evento recebido ainda.", left + 18, top + 82, 0xFFB8AFC0, true);
        }
        graphics.drawString(font, trim(TikTokChaosMod.runtime().lastAction(), 60), left + 18, top + 198,
                0xFF66F0C8, true);
    }

    private void renderSafety(GuiGraphics graphics, int left, int top) {
        TikTokChaosConfig.Safety safety = TikTokChaosMod.runtime().config().safety;
        graphics.drawString(font, "Limites que protegem FPS e mundo", left + 18, top + 58, 0xFFBDB0C7, true);
        drawCounter(graphics, left, top + 74, "Ações por segundo", safety.maxActionsPerSecond);
        drawCounter(graphics, left, top + 110, "Limite de mobs", safety.maxTrackedMobs);
        drawCounter(graphics, left, top + 146, "Tempo dos mobs (s)", safety.mobLifetimeSeconds);
    }

    private void renderSimulator(GuiGraphics graphics, int left, int top) {
        graphics.drawString(font, "Teste regras sem abrir uma LIVE", left + 18, top + 58, 0xFFBDB0C7, true);
        graphics.drawString(font, "Os testes usam o mesmo pipeline e os mesmos limites dos eventos reais.",
                left + 18, top + 210, 0xFFB8AFC0, true);
    }

    private void drawCounter(GuiGraphics graphics, int left, int y, String label, int value) {
        graphics.drawString(font, label, left + 18, y + 7, 0xFFD9CEDF, true);
        graphics.drawCenteredString(font, Integer.toString(value), left + 349, y + 7, 0xFFFFFFFF);
    }

    private void saveUsername() {
        if (usernameField != null) {
            TikTokChaosMod.runtime().config().connection.username = usernameField.getValue();
            TikTokChaosMod.runtime().saveConfig();
        }
    }

    private void setTab(Tab value) {
        saveUsername();
        tab = value;
        rebuildScreen();
    }

    private void rebuildScreen() {
        clearWidgets();
        init();
    }

    private Component autoReconnectLabel() {
        boolean enabled = TikTokChaosMod.runtime().config().connection.autoReconnect;
        return Component.literal((enabled ? "✓ " : "✕ ") + "Reconexão automática")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private Component hudLabel() {
        boolean enabled = TikTokChaosMod.runtime().config().hud.enabled;
        return Component.literal((enabled ? "✓ " : "✕ ") + "HUD compacto")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private Component ruleLabel(Rule rule) {
        String state = rule.enabled ? "✓ ATIVA  " : "✕ PAUSADA  ";
        return Component.literal(state + rule.name)
                .withStyle(rule.enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private String simulationLabel(LiveEventType type) {
        return switch (type) {
            case LIKE -> "100 curtidas";
            case GIFT -> "Presente (120)";
            case COMMENT -> "Comentário !zumbi";
            case FOLLOW -> "Novo follow";
            case SHARE -> "Compartilhamento";
            case SUBSCRIBE -> "Inscrição";
            case JOIN -> "Entrada";
            case ROOM_STATS -> "42 espectadores";
            default -> type.name();
        };
    }

    @Override
    public void onClose() {
        saveUsername();
        TikTokChaosMod.runtime().saveConfig();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum Tab {
        CONNECTION("Conexão"),
        RULES("Regras"),
        HISTORY("Histórico"),
        SAFETY("Segurança"),
        SIMULATOR("Simulador");

        private final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    @FunctionalInterface
    private interface IntGetter {
        int get();
    }

    @FunctionalInterface
    private interface IntSetter {
        void set(int value);
    }
}
