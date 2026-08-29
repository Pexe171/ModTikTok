package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.TikTokChaosRuntime;
import br.com.modtiktok.tiktokchaos.analytics.SessionStats;
import br.com.modtiktok.tiktokchaos.config.TikTokChaosConfig;
import br.com.modtiktok.tiktokchaos.live.LiveEvent;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import br.com.modtiktok.tiktokchaos.preset.PresetApplyMode;
import br.com.modtiktok.tiktokchaos.preset.PresetCompatibility;
import br.com.modtiktok.tiktokchaos.preset.PresetDocument;
import br.com.modtiktok.tiktokchaos.preset.PresetPreview;
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
    private int presetPage;
    private String selectedPresetId = "survival-chaos";
    private boolean destructiveConfirmationPending;

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
            addRenderableWidget(Button.builder(ClientText.component(value.key), button -> setTab(value))
                    .bounds(left + index * tabWidth, top + 32, tabWidth - 2, 20).build());
        }

        switch (tab) {
            case CONNECTION -> initConnection(left, top);
            case RULES -> initRules(left, top);
            case HISTORY -> initHistory(left, top);
            case SAFETY -> initSafety(left, top);
            case SIMULATOR -> initSimulator(left, top);
            case PRESETS -> initPresets(left, top);
            case SESSION -> initSession(left, top);
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
        usernameField.setHint(ClientText.component("gui.tiktokchaos.username_hint"));
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

        addRenderableWidget(Button.builder(runStateLabel(), button -> {
            if (runtime.areActionsPaused()) runtime.resumeActions(); else runtime.pauseActions();
            rebuildScreen();
        }).bounds(left + 18, top + 142, 194, 22).build());
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
            addRenderableWidget(Button.builder(ClientText.component("gui.tiktokchaos.edit"), button -> {
                if (minecraft != null) minecraft.setScreen(new RuleEditorScreen(this, rule, false));
            }).bounds(left + PANEL_WIDTH - 104, y, 86, 19).build());
        }
        addRenderableWidget(Button.builder(ClientText.component("gui.tiktokchaos.new_rule"), button -> {
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
        addRenderableWidget(Button.builder(ClientText.component("gui.tiktokchaos.clear_queue"), button -> TikTokChaosMod.runtime().clearQueue())
                .bounds(left + 18, top + 212, 96, 20).build());
        addRenderableWidget(Button.builder(toggleLabel(TikTokChaosMod.runtime().config().hud.showChat,
                "gui.tiktokchaos.chat_hud"), button -> {
            TikTokChaosMod.runtime().config().hud.showChat = !TikTokChaosMod.runtime().config().hud.showChat;
            TikTokChaosMod.runtime().saveConfig();
            rebuildScreen();
        }).bounds(left + 122, top + 212, 126, 20).build());
    }

    private void initSafety(int left, int top) {
        TikTokChaosConfig.Safety safety = TikTokChaosMod.runtime().config().safety;
        addCounter(left, top + 74, () -> safety.maxActionsPerSecond,
                value -> safety.maxActionsPerSecond = clamp(value, 1, 20));
        addCounter(left, top + 110, () -> safety.maxTrackedMobs,
                value -> safety.maxTrackedMobs = clamp(value, 1, 100));
        addCounter(left, top + 146, () -> safety.mobLifetimeSeconds,
                value -> safety.mobLifetimeSeconds = clamp(value, 10, 600));
        addRenderableWidget(Button.builder(hudLabel(), button -> {
            TikTokChaosMod.runtime().config().hud.enabled = !TikTokChaosMod.runtime().config().hud.enabled;
            TikTokChaosMod.runtime().saveConfig();
            rebuildScreen();
        }).bounds(left + 18, top + 184, 180, 21).build());
        addRenderableWidget(Button.builder(adaptivePerformanceLabel(), button -> {
            safety.adaptivePerformance = !safety.adaptivePerformance;
            TikTokChaosMod.runtime().saveConfig();
            rebuildScreen();
        }).bounds(left + 206, top + 184, 206, 21).build());
        addRenderableWidget(Button.builder(destructiveActionsLabel(), button -> {
            if (safety.destructiveActionsEnabled) {
                safety.destructiveActionsEnabled = false;
                safety.destructiveActionsConfirmed = false;
                destructiveConfirmationPending = false;
                TikTokChaosMod.runtime().saveConfig();
            } else if (destructiveConfirmationPending) {
                safety.destructiveActionsEnabled = true;
                safety.destructiveActionsConfirmed = true;
                destructiveConfirmationPending = false;
                TikTokChaosMod.runtime().saveConfig();
            } else {
                destructiveConfirmationPending = true;
            }
            rebuildScreen();
        }).bounds(left + 18, top + 212, 300, 20).build());
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
            Button button = Button.builder(ClientText.component(simulationKey(type)),
                    pressed -> TikTokChaosMod.runtime().simulate(type))
                    .bounds(left + 18 + column * 199, top + 76 + row * 32, 190, 24).build();
            button.active = TikTokChaosMod.runtime().isWorldActive();
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(ClientText.component("gui.tiktokchaos.open_detailed_simulator"), button -> {
            if (minecraft != null) minecraft.setScreen(new SimulatorScreen(this));
        }).bounds(left + 18, top + 204, 190, 20).build());
    }

    private void initPresets(int left, int top) {
        TikTokChaosRuntime runtime = TikTokChaosMod.runtime();
        List<PresetDocument> presets = runtime.presetCatalog();
        int pageCount = Math.max(1, (presets.size() + 3) / 4);
        presetPage = Math.max(0, Math.min(presetPage, pageCount - 1));
        int first = presetPage * 4;
        int last = Math.min(presets.size(), first + 4);
        for (int index = first; index < last; index++) {
            PresetDocument preset = presets.get(index);
            PresetCompatibility compatibility = runtime.presetCompatibility(preset.id);
            String prefix = preset.id.equals(selectedPresetId) ? "◆ " : "◇ ";
            prefix += compatibility.available() ? "✓ " : "✕ ";
            String name = ClientText.configuredName("preset", preset.id, preset.name);
            addRenderableWidget(Button.builder(Component.literal(prefix + trim(name, 46)), button -> {
                selectedPresetId = preset.id;
                rebuildScreen();
            }).bounds(left + 18, top + 70 + (index - first) * 22, PANEL_WIDTH - 36, 20).build());
        }
        if (pageCount > 1) {
            Button previous = Button.builder(Component.literal("‹"), button -> {
                presetPage--;
                rebuildScreen();
            }).bounds(left + 18, top + 160, 28, 20).build();
            previous.active = presetPage > 0;
            addRenderableWidget(previous);
            Button next = Button.builder(Component.literal("›"), button -> {
                presetPage++;
                rebuildScreen();
            }).bounds(left + 50, top + 160, 28, 20).build();
            next.active = presetPage + 1 < pageCount;
            addRenderableWidget(next);
        }
        PresetCompatibility selectedCompatibility = runtime.presetCompatibility(selectedPresetId);
        Button replace = Button.builder(ClientText.component("gui.tiktokchaos.replace"), button -> {
            runtime.applyPreset(selectedPresetId, PresetApplyMode.REPLACE);
            rebuildScreen();
        }).bounds(left + 92, top + 160, 126, 20).build();
        replace.active = selectedCompatibility.available();
        addRenderableWidget(replace);
        Button merge = Button.builder(ClientText.component("gui.tiktokchaos.merge"), button -> {
            runtime.applyPreset(selectedPresetId, PresetApplyMode.MERGE);
            rebuildScreen();
        }).bounds(left + 224, top + 160, 92, 20).build();
        merge.active = selectedCompatibility.available();
        addRenderableWidget(merge);
        addRenderableWidget(Button.builder(ClientText.component("gui.tiktokchaos.export_current"), button -> {
            runtime.exportPreset();
            rebuildScreen();
        }).bounds(left + 18, top + 188, 132, 20).build());
    }

    private void initSession(int left, int top) {
        TikTokChaosRuntime runtime = TikTokChaosMod.runtime();
        addRenderableWidget(Button.builder(toggleLabel(runtime.config().overlay.enabled,
                "gui.tiktokchaos.local_obs_overlay"), button -> {
            runtime.config().overlay.enabled = !runtime.config().overlay.enabled;
            runtime.saveConfig();
            rebuildScreen();
        }).bounds(left + 250, top + 148, 162, 20).build());
        addRenderableWidget(Button.builder(toggleLabel(runtime.config().hud.showGoals,
                "gui.tiktokchaos.goals_hud"), button -> {
            runtime.config().hud.showGoals = !runtime.config().hud.showGoals;
            runtime.saveConfig();
            rebuildScreen();
        }).bounds(left + 18, top + 178, 120, 20).build());
        addRenderableWidget(Button.builder(toggleLabel(runtime.config().hud.showRanking,
                "gui.tiktokchaos.ranking_hud"), button -> {
            runtime.config().hud.showRanking = !runtime.config().hud.showRanking;
            runtime.saveConfig();
            rebuildScreen();
        }).bounds(left + 146, top + 178, 126, 20).build());
        addRenderableWidget(Button.builder(toggleLabel(runtime.config().hud.hideViewerNames,
                "gui.tiktokchaos.hide_names"), button -> {
            runtime.config().hud.hideViewerNames = !runtime.config().hud.hideViewerNames;
            runtime.saveConfig();
            rebuildScreen();
        }).bounds(left + 280, top + 178, 132, 20).build());
        addRenderableWidget(Button.builder(ClientText.component("gui.tiktokchaos.reset_session"), button -> {
            runtime.resetSessionStats();
            rebuildScreen();
        }).bounds(left + 18, top + 204, 110, 20).build());
        addRenderableWidget(Button.builder(toggleLabel(runtime.config().avatars.enabled,
                "gui.tiktokchaos.temporary_avatars"), button -> {
            runtime.config().avatars.enabled = !runtime.config().avatars.enabled;
            runtime.saveConfig();
            rebuildScreen();
        }).bounds(left + 136, top + 204, 204, 20).build());
    }

    private void addCounter(int left, int y, IntGetter getter, IntSetter setter) {
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
        graphics.drawString(font, ClientText.text("gui.tiktokchaos.platform"), left + PANEL_WIDTH - 102, top + 4,
                0xFFC6BCCE, true);

        switch (tab) {
            case CONNECTION -> renderConnection(graphics, left, top);
            case RULES -> renderRules(graphics, left, top);
            case HISTORY -> renderHistory(graphics, left, top);
            case SAFETY -> renderSafety(graphics, left, top);
            case SIMULATOR -> renderSimulator(graphics, left, top);
            case PRESETS -> renderPresets(graphics, left, top);
            case SESSION -> renderSession(graphics, left, top);
        }
    }

    private void renderConnection(GuiGraphics graphics, int left, int top) {
        TikTokChaosRuntime runtime = TikTokChaosMod.runtime();
        graphics.drawString(font, ClientText.text("gui.tiktokchaos.streaming_account"), left + 18, top + 64,
                0xFFCFC4D6, true);
        int color = runtime.status() == br.com.modtiktok.tiktokchaos.live.ConnectionStatus.CONNECTED
                ? 0xFF66F0C8 : 0xFFFFC857;
        graphics.drawString(font, ClientText.text("gui.tiktokchaos.f9_cleanup"), left + 224, top + 149,
                0xFFFF6B81, true);
        graphics.drawString(font, ClientText.text("gui.tiktokchaos.connection_state", ClientText.status(runtime.status()),
                        ClientText.runState(runtime.runState())),
                left + 18, top + 176, color, true);
        graphics.drawString(font, trim(ClientText.runtimeMessage(runtime.statusDetail()), 60), left + 18, top + 192,
                0xFFCFC4D6, true);
        String error = runtime.configManager().getLastError();
        if (!error.isBlank()) graphics.drawString(font, trim(ClientText.runtimeMessage(error), 60), left + 18,
                top + 208, 0xFFFF6B81, true);
    }

    private void renderRules(GuiGraphics graphics, int left, int top) {
        int enabled = (int) TikTokChaosMod.runtime().config().rules.stream().filter(rule -> rule.enabled).count();
        graphics.drawString(font, ClientText.text("gui.tiktokchaos.active_rules", enabled),
                left + 18, top + 56, 0xFFBDB0C7, true);
    }

    private void renderHistory(GuiGraphics graphics, int left, int top) {
        List<LiveEvent> events = TikTokChaosMod.runtime().historySnapshot();
        graphics.drawString(font, ClientText.text("gui.tiktokchaos.recent_events"), left + 18, top + 60,
                0xFFBDB0C7, true);
        int count = Math.min(9, events.size());
        for (int index = 0; index < count; index++) {
            LiveEvent event = events.get(index);
            int color = event.type() == LiveEventType.GIFT ? 0xFFFFD166 : 0xFFD9CEDF;
            graphics.drawString(font, trim(ClientText.eventSummary(event), 60), left + 18, top + 78 + index * 14,
                    color, true);
        }
        if (events.isEmpty()) {
            graphics.drawString(font, ClientText.text("gui.tiktokchaos.no_events"), left + 18, top + 82,
                    0xFFB8AFC0, true);
        }
        graphics.drawString(font, trim(ClientText.runtimeMessage(TikTokChaosMod.runtime().lastAction()), 60),
                left + 18, top + 198,
                0xFF66F0C8, true);
    }

    private void renderSafety(GuiGraphics graphics, int left, int top) {
        TikTokChaosConfig.Safety safety = TikTokChaosMod.runtime().config().safety;
        graphics.drawString(font, ClientText.text("gui.tiktokchaos.safety_intro"), left + 18, top + 58,
                0xFFBDB0C7, true);
        drawCounter(graphics, left, top + 74, ClientText.text("gui.tiktokchaos.actions_per_second"),
                safety.maxActionsPerSecond);
        drawCounter(graphics, left, top + 110, ClientText.text("gui.tiktokchaos.mob_limit"), safety.maxTrackedMobs);
        drawCounter(graphics, left, top + 146, ClientText.text("gui.tiktokchaos.mob_lifetime"),
                safety.mobLifetimeSeconds);
    }

    private void renderSimulator(GuiGraphics graphics, int left, int top) {
        graphics.drawString(font, ClientText.text("gui.tiktokchaos.simulator_intro"), left + 18, top + 58,
                0xFFBDB0C7, true);
        graphics.drawString(font, ClientText.text("gui.tiktokchaos.simulator_limits"), left + 216, top + 210,
                0xFFB8AFC0, true);
    }

    private void renderPresets(GuiGraphics graphics, int left, int top) {
        TikTokChaosRuntime runtime = TikTokChaosMod.runtime();
        List<PresetDocument> catalog = runtime.presetCatalog();
        int first = Math.min(presetPage * 4, Math.max(0, catalog.size() - 1));
        boolean popularMods = !catalog.isEmpty() && "popular-mods".equals(catalog.get(first).category);
        graphics.drawString(font, ClientText.text(popularMods ? "gui.tiktokchaos.preset_popular_mods"
                        : "gui.tiktokchaos.preset_intro"), left + 18, top + 56,
                0xFFBDB0C7, true);
        try {
            PresetPreview replace = runtime.previewPreset(selectedPresetId, PresetApplyMode.REPLACE);
            String preview = replace.available()
                    ? ClientText.text("gui.tiktokchaos.preset_preview", replace.resultingRules(),
                    replace.disabledRules())
                    : ClientText.text("gui.tiktokchaos.preset_missing_mods",
                    trim(String.join(", ", replace.missingRequirements()), 44));
            graphics.drawString(font, preview, left + 158, top + 194,
                    replace.available() ? 0xFF66F0C8 : 0xFFFF6B81, true);
        } catch (RuntimeException error) {
            graphics.drawString(font, trim(error.getMessage(), 48), left + 158, top + 194, 0xFFFF6B81, true);
        }
        graphics.drawString(font, ClientText.text("gui.tiktokchaos.preset_folder"), left + 18, top + 212,
                0xFFB8AFC0, true);
    }

    private void renderSession(GuiGraphics graphics, int left, int top) {
        SessionStats.Snapshot stats = TikTokChaosMod.runtime().sessionStats();
        graphics.drawString(font, ClientText.text("gui.tiktokchaos.session_only"), left + 18, top + 56,
                0xFF66F0C8, true);
        graphics.drawString(font, ClientText.text("gui.tiktokchaos.session_totals", stats.coins(), stats.gifts(),
                stats.likes()), left + 18, top + 76, 0xFFE3D9EA, true);
        graphics.drawString(font, ClientText.text("gui.tiktokchaos.action_totals", stats.executedActions(),
                stats.failedActions(), stats.droppedActions()), left + 18, top + 92,
                0xFFE3D9EA, true);
        int y = top + 112;
        for (int index = 0; index < Math.min(2, stats.goals().size()); index++) {
            SessionStats.GoalProgress goal = stats.goals().get(index);
            String goalName = ClientText.configuredName("goal", goal.id(), goal.name());
            graphics.drawString(font, ClientText.text("gui.tiktokchaos.goal_progress", goalName, goal.current(),
                            goal.target()),
                    left + 18, y, goal.complete() ? 0xFF66F0C8 : 0xFFFFD166, true);
            y += 14;
        }
        for (int index = 0; index < Math.min(2, stats.ranking().size()); index++) {
            SessionStats.ViewerRank viewer = stats.ranking().get(index);
            graphics.drawString(font, ClientText.text("gui.tiktokchaos.viewer_rank", index + 1,
                            trim(ClientText.viewerName(viewer.name()), 24), viewer.coins(), viewer.mobsDefeated()),
                    left + 220, top + 112 + index * 14,
                    0xFFBDB0C7, true);
        }
        String overlay = TikTokChaosMod.runtime().overlayUrl();
        graphics.drawString(font, overlay.isBlank() ? ClientText.text("gui.tiktokchaos.obs_disabled")
                        : ClientText.text("gui.tiktokchaos.obs_url", trim(overlay, 34)),
                left + 18, top + 154, overlay.isBlank() ? 0xFFB8AFC0 : 0xFF66F0C8, true);
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
        return toggleLabel(enabled, "gui.tiktokchaos.auto_reconnect")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private Component hudLabel() {
        boolean enabled = TikTokChaosMod.runtime().config().hud.enabled;
        return toggleLabel(enabled, "gui.tiktokchaos.compact_hud")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private Component runStateLabel() {
        boolean paused = TikTokChaosMod.runtime().areActionsPaused();
        return ClientText.component(paused ? "gui.tiktokchaos.resume_actions" : "gui.tiktokchaos.pause_actions")
                .withStyle(paused ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
    }

    private Component adaptivePerformanceLabel() {
        boolean enabled = TikTokChaosMod.runtime().config().safety.adaptivePerformance;
        return toggleLabel(enabled, "gui.tiktokchaos.adaptive_fps")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private Component destructiveActionsLabel() {
        TikTokChaosConfig.Safety safety = TikTokChaosMod.runtime().config().safety;
        if (safety.destructiveActionsEnabled && safety.destructiveActionsConfirmed) {
            return ClientText.component("gui.tiktokchaos.reversible_world_active")
                    .withStyle(ChatFormatting.RED);
        }
        if (destructiveConfirmationPending) {
            return ClientText.component("gui.tiktokchaos.confirm_block_changes", safety.maxChangedBlocks)
                    .withStyle(ChatFormatting.RED);
        }
        return ClientText.component("gui.tiktokchaos.block_actions_disabled").withStyle(ChatFormatting.GRAY);
    }

    private Component ruleLabel(Rule rule) {
        String state = ClientText.text(rule.enabled ? "gui.tiktokchaos.rule_active_prefix"
                : "gui.tiktokchaos.rule_paused_prefix");
        String name = ClientText.configuredName("rule", rule.id, rule.name);
        return Component.literal(state + name)
                .withStyle(rule.enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private String simulationKey(LiveEventType type) {
        return switch (type) {
            case LIKE -> "gui.tiktokchaos.simulate_likes";
            case GIFT -> "gui.tiktokchaos.simulate_gift";
            case COMMENT -> "gui.tiktokchaos.simulate_comment";
            case FOLLOW -> "gui.tiktokchaos.simulate_follow";
            case SHARE -> "gui.tiktokchaos.simulate_share";
            case SUBSCRIBE -> "gui.tiktokchaos.simulate_subscribe";
            case JOIN -> "gui.tiktokchaos.simulate_join";
            case ROOM_STATS -> "gui.tiktokchaos.simulate_viewers";
            default -> "event.tiktokchaos." + type.name().toLowerCase(java.util.Locale.ROOT);
        };
    }

    private net.minecraft.network.chat.MutableComponent toggleLabel(boolean enabled, String key) {
        return Component.literal(enabled ? "✓ " : "✕ ").append(ClientText.component(key));
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
        CONNECTION("gui.tiktokchaos.tab.connection"),
        RULES("gui.tiktokchaos.tab.rules"),
        HISTORY("gui.tiktokchaos.tab.history"),
        SAFETY("gui.tiktokchaos.tab.safety"),
        SIMULATOR("gui.tiktokchaos.tab.simulator"),
        PRESETS("gui.tiktokchaos.tab.presets"),
        SESSION("gui.tiktokchaos.tab.session");

        private final String key;

        Tab(String key) {
            this.key = key;
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
