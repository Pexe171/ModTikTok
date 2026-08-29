package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.gameplay.ActionTargets;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import br.com.modtiktok.tiktokchaos.rule.ActionSpec;
import br.com.modtiktok.tiktokchaos.rule.ActionType;
import br.com.modtiktok.tiktokchaos.rule.ExecutionMode;
import br.com.modtiktok.tiktokchaos.rule.ExecutionSpec;
import br.com.modtiktok.tiktokchaos.rule.ExecutionTier;
import br.com.modtiktok.tiktokchaos.rule.Rule;
import br.com.modtiktok.tiktokchaos.rule.RuleCondition;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class RuleEditorScreen extends Screen {
    private static final int PANEL_WIDTH = 470;
    private static final LiveEventType[] EDITABLE_EVENTS = {
            LiveEventType.LIKE, LiveEventType.GIFT, LiveEventType.COMMENT, LiveEventType.FOLLOW,
            LiveEventType.SHARE, LiveEventType.SUBSCRIBE, LiveEventType.JOIN
    };

    private final Screen parent;
    private final Rule rule;
    private final boolean newRule;
    private EditBox nameField;
    private EditBox commandField;
    private EditBox giftIdField;
    private EditBox minValueField;
    private EditBox maxValueField;
    private EditBox thresholdField;
    private EditBox cooldownField;
    private EditBox userCooldownField;
    private EditBox actionTargetField;
    private int actionIndex;
    private String validationMessage = "";

    public RuleEditorScreen(Screen parent, Rule rule, boolean newRule) {
        super(ClientText.component("gui.tiktokchaos.rule_editor.title"));
        this.parent = parent;
        this.rule = rule;
        this.newRule = newRule;
        if (rule.condition == null) rule.condition = new RuleCondition();
        if (rule.execution == null) rule.execution = new ExecutionSpec();
        if (rule.actions == null) rule.actions = new ArrayList<>();
        if (rule.actions.isEmpty()) rule.actions.add(ActionSpec.spawn("minecraft:zombie", 1));
    }

    public static Rule newRule() {
        RuleCondition condition = new RuleCondition();
        condition.commentCommand = "!novo";
        return new Rule("custom_" + System.currentTimeMillis(), ClientText.text("gui.tiktokchaos.rule_editor.default_name"),
                LiveEventType.COMMENT, condition,
                3_000, 20_000, List.of(ActionSpec.spawn("minecraft:zombie", 1)));
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(10, (height - 310) / 2);
        int fieldX = left + 154;
        int fieldWidth = 292;

        nameField = field(fieldX, top + 42, fieldWidth,
                ClientText.configuredName("rule", rule.id, rule.name), 80);
        commandField = field(fieldX, top + 94, 128, rule.condition.commentCommand, 64);
        giftIdField = field(fieldX + 138, top + 94, 72, Integer.toString(rule.condition.giftId), 10);
        thresholdField = field(fieldX + 220, top + 94, 72, Integer.toString(rule.condition.threshold), 10);
        minValueField = field(fieldX, top + 122, 92, Integer.toString(rule.condition.minGiftValue), 10);
        maxValueField = field(fieldX + 102, top + 122, 108, Integer.toString(rule.condition.maxGiftValue), 10);
        cooldownField = field(fieldX, top + 150, 92, Long.toString(rule.cooldownMillis / 1_000), 7);
        userCooldownField = field(fieldX + 102, top + 150, 108,
                Long.toString(rule.perUserCooldownMillis / 1_000), 7);

        addRenderableWidget(Button.builder(eventLabel(), button -> {
            persistFields(false);
            int index = indexOf(EDITABLE_EVENTS, rule.event);
            rule.event = EDITABLE_EVENTS[(index + 1) % EDITABLE_EVENTS.length];
            rebuildScreen();
        }).bounds(fieldX, top + 68, 210, 20).build());

        addRenderableWidget(Button.builder(enabledLabel(), button -> {
            rule.enabled = !rule.enabled;
            rebuildScreen();
        }).bounds(fieldX + 220, top + 68, 72, 20).build());

        ActionSpec action = currentAction();
        addRenderableWidget(Button.builder(actionTypeLabel(action), button -> {
            persistFields(false);
            ActionSpec current = currentAction();
            ActionType[] types = ActionType.values();
            current.type = types[(current.type.ordinal() + 1) % types.length];
            applyTargetDefault(current);
            rebuildScreen();
        }).bounds(fieldX, top + 180, 210, 20).build());

        if (hasPrimaryCounter(action.type)) {
            addRenderableWidget(Button.builder(Component.literal("−"), button -> {
                persistFields(false);
                adjustPrimary(currentAction(), -1);
                rebuildScreen();
            }).bounds(fieldX + 220, top + 180, 32, 20).build());
            addRenderableWidget(Button.builder(Component.literal("+"), button -> {
                persistFields(false);
                adjustPrimary(currentAction(), 1);
                rebuildScreen();
            }).bounds(fieldX + 260, top + 180, 32, 20).build());
        }

        VisualTargetCatalog.Kind pickerKind = pickerKind(action.type);
        if (pickerKind != null) {
            actionTargetField = field(fieldX, top + 206, 128, action.target, 128);
            actionTargetField.setHint(Component.literal("minecraft:id"));
            addRenderableWidget(Button.builder(ClientText.component("gui.tiktokchaos.choose"), button -> openTargetPicker())
                    .bounds(fieldX + 136, top + 206, 74, 20).build());
        } else if (action.type == ActionType.MESSAGE || action.type == ActionType.CENTER_MESSAGE) {
            actionTargetField = field(fieldX, top + 206, fieldWidth, action.message, 200);
            actionTargetField.setHint(ClientText.component("gui.tiktokchaos.rule_editor.message_hint"));
        } else if (action.type == ActionType.PLAY_SOUND) {
            actionTargetField = field(fieldX, top + 206, fieldWidth, action.target, 128);
            actionTargetField.setHint(Component.literal("minecraft:entity.experience_orb.pickup"));
        } else if (action.type == ActionType.MOD_INTEGRATION) {
            actionTargetField = field(fieldX, top + 206, fieldWidth, action.target, 128);
            actionTargetField.setHint(Component.literal("pixelmon:random_shiny"));
        } else {
            actionTargetField = null;
        }

        if (action.type == ActionType.APPLY_EFFECT) {
            addRenderableWidget(Button.builder(Component.literal("−"), button -> {
                persistFields(false);
                currentAction().durationTicks = Math.max(20, currentAction().durationTicks - 100);
                rebuildScreen();
            }).bounds(fieldX + 220, top + 206, 32, 20).build());
            addRenderableWidget(Button.builder(Component.literal("+"), button -> {
                persistFields(false);
                currentAction().durationTicks = Math.min(12_000, currentAction().durationTicks + 100);
                rebuildScreen();
            }).bounds(fieldX + 260, top + 206, 32, 20).build());
        }

        Button previous = Button.builder(Component.literal("‹"), button -> {
            persistFields(false);
            actionIndex--;
            rebuildScreen();
        }).bounds(left + 18, top + 234, 28, 20).build();
        previous.active = actionIndex > 0;
        addRenderableWidget(previous);
        Button next = Button.builder(Component.literal("›"), button -> {
            persistFields(false);
            actionIndex++;
            rebuildScreen();
        }).bounds(left + 52, top + 234, 28, 20).build();
        next.active = actionIndex + 1 < rule.actions.size();
        addRenderableWidget(next);
        addRenderableWidget(Button.builder(ClientText.component("gui.tiktokchaos.rule_editor.add_action"), button -> {
            persistFields(false);
            rule.actions.add(ActionSpec.spawn("minecraft:zombie", 1));
            actionIndex = rule.actions.size() - 1;
            rebuildScreen();
        }).bounds(left + 90, top + 234, 70, 20).build());
        Button removeAction = Button.builder(ClientText.component("gui.tiktokchaos.rule_editor.remove_action"), button -> {
            persistFields(false);
            if (rule.actions.size() > 1) {
                rule.actions.remove(actionIndex);
                actionIndex = Math.max(0, actionIndex - 1);
            }
            rebuildScreen();
        }).bounds(left + 168, top + 234, 70, 20).build();
        removeAction.active = rule.actions.size() > 1;
        addRenderableWidget(removeAction);
        addRenderableWidget(Button.builder(executionLabel(), button -> {
            persistFields(false);
            ExecutionMode[] modes = ExecutionMode.values();
            rule.execution.mode = modes[(rule.execution.mode.ordinal() + 1) % modes.length];
            if (rule.execution.mode == ExecutionMode.TIERED && rule.execution.tiers.isEmpty()) {
                rule.execution.tiers.add(new ExecutionTier(1, 0, 1, copyActions(rule.actions)));
                rule.execution.tiers.add(new ExecutionTier(3, 0, 1, copyActions(rule.actions)));
            }
            rebuildScreen();
        }).bounds(left + 250, top + 234, 96, 20).build());
        addRenderableWidget(Button.builder(ClientText.component("gui.tiktokchaos.rule_editor.sequence",
                rule.sequence.size()), button -> {
            persistFields(false);
            if (minecraft != null) minecraft.setScreen(new SequenceEditorScreen(this, rule));
        }).bounds(left + 350, top + 234, 96, 20).build());

        addRenderableWidget(Button.builder(ClientText.component("gui.tiktokchaos.save"), button -> save())
                .bounds(left + 18, top + 272, 94, 22).build());
        if (!newRule) {
            addRenderableWidget(Button.builder(ClientText.component("gui.tiktokchaos.delete")
                    .withStyle(ChatFormatting.RED), button -> {
                TikTokChaosMod.runtime().config().rules.remove(rule);
                TikTokChaosMod.runtime().saveConfig();
                returnToParent();
            }).bounds(left + 120, top + 272, 94, 22).build());
        }
        addRenderableWidget(Button.builder(ClientText.component("gui.tiktokchaos.cancel"), button -> returnToParent())
                .bounds(left + PANEL_WIDTH - 112, top + 272, 94, 22).build());
    }

    private EditBox field(int x, int y, int width, String value, int maxLength) {
        EditBox edit = new EditBox(font, x, y, width, 20, Component.empty());
        edit.setMaxLength(maxLength);
        edit.setValue(value == null ? "" : value);
        addRenderableWidget(edit);
        return edit;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep this screen readable when another UI mod enables menu background blur.
        graphics.fill(0, 0, width, height, 0xB0000000);
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(10, (height - 310) / 2);
        graphics.fillGradient(left - 8, top - 8, left + PANEL_WIDTH + 8, top + 304,
                0xF21A1024, 0xF20E1420);
        graphics.fill(left - 8, top - 8, left - 4, top + 304, 0xFF66F0C8);

        // Keep custom labels above blur injected during vanilla widget rendering.
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, ClientText.text(newRule ? "gui.tiktokchaos.rule_editor.new_heading"
                : "gui.tiktokchaos.rule_editor.edit_heading"), left, top + 4, 0xFF66F0C8, true);
        label(graphics, left, top + 48, ClientText.text("gui.tiktokchaos.rule_editor.name"));
        label(graphics, left, top + 74, ClientText.text("gui.tiktokchaos.rule_editor.event_state"));
        label(graphics, left, top + 100, ClientText.text("gui.tiktokchaos.rule_editor.command_id_goal"));
        label(graphics, left, top + 128, ClientText.text("gui.tiktokchaos.rule_editor.min_max"));
        label(graphics, left, top + 156, ClientText.text("gui.tiktokchaos.rule_editor.cooldowns"));
        ActionSpec action = currentAction();
        label(graphics, left, top + 186, primaryLabel(action));
        label(graphics, left, top + 212, targetLabel(action));
        graphics.drawString(font, ClientText.text("gui.tiktokchaos.rule_editor.action_summary", actionIndex + 1,
                        rule.actions.size(), actionSummary(action)),
                left + 250, top + 258, 0xFFBDB0C7, true);
        if (!validationMessage.isBlank()) {
            graphics.drawString(font, validationMessage, left + 222, top + 279, 0xFFFF6B81, true);
        }
    }

    private void label(GuiGraphics graphics, int left, int y, String value) {
        graphics.drawString(font, value, left + 18, y, 0xFFD9CEDF, true);
    }

    private void save() {
        if (!persistFields(true)) return;
        if (newRule && !TikTokChaosMod.runtime().config().rules.contains(rule)) {
            TikTokChaosMod.runtime().config().rules.add(rule);
        }
        if (TikTokChaosMod.runtime().saveConfig()) returnToParent();
        else validationMessage = TikTokChaosMod.runtime().configManager().getLastError();
    }

    private boolean persistFields(boolean validate) {
        try {
            rule.name = nameField.getValue().strip();
            rule.condition.commentCommand = commandField.getValue().strip();
            rule.condition.giftId = integer(giftIdField, -1);
            rule.condition.threshold = Math.max(1, integer(thresholdField, 1));
            rule.condition.minGiftValue = Math.max(0, integer(minValueField, 0));
            rule.condition.maxGiftValue = Math.max(rule.condition.minGiftValue,
                    integer(maxValueField, Integer.MAX_VALUE));
            rule.cooldownMillis = Math.max(0, longValue(cooldownField, 0)) * 1_000L;
            rule.perUserCooldownMillis = Math.max(0, longValue(userCooldownField, 0)) * 1_000L;
            ActionSpec action = currentAction();
            if (actionTargetField != null) {
                if (action.type == ActionType.MESSAGE || action.type == ActionType.CENTER_MESSAGE) {
                    action.message = actionTargetField.getValue().strip();
                }
                else action.target = actionTargetField.getValue().strip();
            }
            if (validate && rule.name.isBlank()) {
                throw new IllegalArgumentException(ClientText.text("gui.tiktokchaos.rule_editor.name_required"));
            }
            validationMessage = "";
            return true;
        } catch (IllegalArgumentException exception) {
            validationMessage = exception.getMessage();
            return false;
        }
    }

    private ActionSpec currentAction() {
        actionIndex = Math.max(0, Math.min(actionIndex, rule.actions.size() - 1));
        return rule.actions.get(actionIndex);
    }

    private void applyTargetDefault(ActionSpec action) {
        action.target = switch (action.type) {
            case SPAWN_ENTITY, SPAWN_VIEWER_BOSS -> "minecraft:zombie";
            case GIVE_ITEM -> "minecraft:bread";
            case APPLY_EFFECT -> "minecraft:slowness";
            case PLAY_SOUND -> "minecraft:entity.experience_orb.pickup";
            case VISUAL_ITEM_RAIN, GIFT_CANNON -> "minecraft:diamond";
            case MOD_INTEGRATION -> "pixelmon:random_shiny";
            default -> "";
        };
        if (action.type == ActionType.APPLY_EFFECT) {
            action.durationTicks = 200;
            action.amplifier = 0;
        } else if (action.type == ActionType.SHORT_TELEPORT) {
            action.radius = 10;
        } else if (action.type == ActionType.SET_WEATHER) {
            action.durationTicks = 600;
        } else if (action.type == ActionType.MESSAGE || action.type == ActionType.CENTER_MESSAGE) {
            action.message = "";
        } else if (action.type == ActionType.FREEZE_PLAYER) {
            action.durationTicks = 100;
        }
    }

    private void openTargetPicker() {
        if (!persistFields(false) || minecraft == null) return;
        ActionSpec action = currentAction();
        VisualTargetCatalog.Kind kind = pickerKind(action.type);
        if (kind == null) return;
        minecraft.setScreen(new TargetPickerScreen(this, kind, action.target, selected -> action.target = selected));
    }

    private VisualTargetCatalog.Kind pickerKind(ActionType type) {
        return switch (type) {
            case SPAWN_ENTITY, SPAWN_VIEWER_BOSS -> VisualTargetCatalog.Kind.ENTITY;
            case GIVE_ITEM -> VisualTargetCatalog.Kind.ITEM;
            case APPLY_EFFECT -> VisualTargetCatalog.Kind.EFFECT;
            case VISUAL_ITEM_RAIN, GIFT_CANNON -> VisualTargetCatalog.Kind.ITEM;
            default -> null;
        };
    }

    private boolean hasPrimaryCounter(ActionType type) {
        return switch (type) {
            case SPAWN_ENTITY, GIVE_ITEM, APPLY_EFFECT, SHORT_TELEPORT, SET_WEATHER, LAUNCH_PLAYER,
                    FREEZE_PLAYER, PARTICLE_BURST, VISUAL_ITEM_RAIN, GIFT_CANNON, LIKE_FOUNTAIN,
                    MOD_INTEGRATION -> true;
            default -> false;
        };
    }

    private void adjustPrimary(ActionSpec action, int direction) {
        switch (action.type) {
            case SPAWN_ENTITY, GIVE_ITEM, MOD_INTEGRATION ->
                    action.amount = clamp(action.amount + direction, 1, 20);
            case APPLY_EFFECT -> action.amplifier = clamp(action.amplifier + direction, 0, 9);
            case SHORT_TELEPORT -> action.radius = clamp(action.radius + direction, 3, 64);
            case SET_WEATHER -> action.durationTicks = clamp(action.durationTicks + direction * 100, 100, 12_000);
            case LAUNCH_PLAYER, PARTICLE_BURST, VISUAL_ITEM_RAIN, GIFT_CANNON, LIKE_FOUNTAIN ->
                    action.amount = clamp(action.amount + direction, 1, 20);
            case FREEZE_PLAYER -> action.durationTicks = clamp(action.durationTicks + direction * 20, 20, 2_400);
            default -> {
            }
        }
    }

    private String primaryLabel(ActionSpec action) {
        return switch (action.type) {
            case SPAWN_ENTITY, GIVE_ITEM, MOD_INTEGRATION ->
                    ClientText.text("gui.tiktokchaos.rule_editor.type_amount");
            case APPLY_EFFECT -> ClientText.text("gui.tiktokchaos.rule_editor.type_effect_level");
            case SHORT_TELEPORT -> ClientText.text("gui.tiktokchaos.rule_editor.type_radius");
            case SET_WEATHER, FREEZE_PLAYER -> ClientText.text("gui.tiktokchaos.rule_editor.type_duration");
            case LAUNCH_PLAYER -> ClientText.text("gui.tiktokchaos.rule_editor.type_strength");
            case PARTICLE_BURST, VISUAL_ITEM_RAIN, GIFT_CANNON, LIKE_FOUNTAIN ->
                    ClientText.text("gui.tiktokchaos.rule_editor.type_quantity");
            case SPAWN_VIEWER_BOSS -> ClientText.text("gui.tiktokchaos.rule_editor.type_unique_boss");
            case REVERSIBLE_BLOCK_BOX -> ClientText.text("gui.tiktokchaos.rule_editor.type_safety_limit");
            default -> ClientText.text("gui.tiktokchaos.rule_editor.action_type");
        };
    }

    private String targetLabel(ActionSpec action) {
        return switch (action.type) {
            case SPAWN_ENTITY -> ClientText.text("gui.tiktokchaos.rule_editor.mob_catalog");
            case SPAWN_VIEWER_BOSS -> ClientText.text("gui.tiktokchaos.rule_editor.boss_catalog");
            case GIVE_ITEM -> ClientText.text("gui.tiktokchaos.rule_editor.item_catalog");
            case APPLY_EFFECT -> ClientText.text("gui.tiktokchaos.rule_editor.effect_duration");
            case MESSAGE -> ClientText.text("gui.tiktokchaos.rule_editor.displayed_message");
            case CENTER_MESSAGE -> ClientText.text("gui.tiktokchaos.rule_editor.center_message");
            case PLAY_SOUND -> ClientText.text("gui.tiktokchaos.rule_editor.sound_id");
            case VISUAL_ITEM_RAIN, GIFT_CANNON -> ClientText.text("gui.tiktokchaos.rule_editor.visual_item");
            case MOD_INTEGRATION -> ClientText.text("gui.tiktokchaos.rule_editor.mod_integration_target");
            default -> ClientText.text("gui.tiktokchaos.rule_editor.automatic_config");
        };
    }

    private String actionSummary(ActionSpec action) {
        String random = ActionTargets.isRandom(action.target)
                ? ClientText.text("gui.tiktokchaos.rule_editor.random_suffix") : "";
        return switch (action.type) {
            case SPAWN_ENTITY, GIVE_ITEM -> ClientText.text("gui.tiktokchaos.rule_editor.summary_amount",
                    action.amount, random);
            case APPLY_EFFECT -> ClientText.text("gui.tiktokchaos.rule_editor.summary_effect",
                    action.amplifier + 1, Math.max(1, action.durationTicks / 20), random);
            case SHORT_TELEPORT -> ClientText.text("gui.tiktokchaos.rule_editor.summary_radius", action.radius);
            case SET_WEATHER, FREEZE_PLAYER -> ClientText.text("gui.tiktokchaos.seconds",
                    Math.max(1, action.durationTicks / 20));
            case MESSAGE -> action.message.isBlank() ? ClientText.text("gui.tiktokchaos.rule_editor.default_message")
                    : trim(action.message, 24);
            case COSMETIC_LIGHTNING -> ClientText.text("gui.tiktokchaos.rule_editor.visual_lightning");
            case RANDOM_SAFE_ITEM -> ClientText.text("gui.tiktokchaos.rule_editor.random_safe_item");
            case RANDOM_POSITIVE_EFFECT -> ClientText.text("gui.tiktokchaos.rule_editor.random_positive_effect");
            case RANDOM_NEGATIVE_EFFECT -> ClientText.text("gui.tiktokchaos.rule_editor.random_negative_effect");
            case PLAY_SOUND -> action.target.isBlank() ? ClientText.text("gui.tiktokchaos.rule_editor.default_sound")
                    : trim(action.target, 24);
            case LAUNCH_PLAYER -> ClientText.text("gui.tiktokchaos.rule_editor.summary_strength", action.amount);
            case PARTICLE_BURST -> ClientText.text("gui.tiktokchaos.rule_editor.summary_particles",
                    action.amount * 10);
            case CENTER_MESSAGE -> action.message.isBlank()
                    ? ClientText.text("gui.tiktokchaos.rule_editor.default_center_message") : trim(action.message, 24);
            case VISUAL_ITEM_RAIN -> ClientText.text("gui.tiktokchaos.rule_editor.summary_visual_items", action.amount);
            case GIFT_CANNON -> ClientText.text("gui.tiktokchaos.rule_editor.summary_cannon_items", action.amount);
            case LIKE_FOUNTAIN -> ClientText.text("gui.tiktokchaos.rule_editor.summary_hearts", action.amount * 10);
            case SPAWN_VIEWER_BOSS -> ClientText.text("gui.tiktokchaos.rule_editor.viewer_boss_summary");
            case REVERSIBLE_BLOCK_BOX -> ClientText.text("gui.tiktokchaos.rule_editor.rollback_box_summary");
            case MOD_INTEGRATION -> ClientText.text("gui.tiktokchaos.rule_editor.mod_integration_summary",
                    action.amount, trim(action.target, 24));
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private Component eventLabel() {
        return ClientText.component("gui.tiktokchaos.rule_editor.event", ClientText.event(rule.event));
    }

    private Component enabledLabel() {
        return ClientText.component(rule.enabled ? "gui.tiktokchaos.active" : "gui.tiktokchaos.paused")
                .withStyle(rule.enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private Component actionTypeLabel(ActionSpec action) {
        return ClientText.component("gui.tiktokchaos.rule_editor.action", ClientText.action(action.type));
    }

    private Component executionLabel() {
        return ClientText.component("gui.tiktokchaos.rule_editor.combo", ClientText.execution(rule.execution.mode));
    }

    private List<ActionSpec> copyActions(List<ActionSpec> actions) {
        return actions.stream().map(ActionSpec::copy).toList();
    }

    private int integer(EditBox field, int fallback) {
        String value = field.getValue().strip();
        if (value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(ClientText.text("gui.tiktokchaos.invalid_number", value));
        }
    }

    private long longValue(EditBox field, long fallback) {
        String value = field.getValue().strip();
        if (value.isBlank()) return fallback;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(ClientText.text("gui.tiktokchaos.invalid_number", value));
        }
    }

    private int indexOf(LiveEventType[] values, LiveEventType value) {
        for (int index = 0; index < values.length; index++) if (values[index] == value) return index;
        return 0;
    }

    private void rebuildScreen() {
        clearWidgets();
        init();
    }

    private void returnToParent() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        returnToParent();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
