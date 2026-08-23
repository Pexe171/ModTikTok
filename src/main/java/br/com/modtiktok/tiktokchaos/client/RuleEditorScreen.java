package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.gameplay.ActionTargets;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import br.com.modtiktok.tiktokchaos.rule.ActionSpec;
import br.com.modtiktok.tiktokchaos.rule.ActionType;
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
        super(Component.literal("Editor de regra"));
        this.parent = parent;
        this.rule = rule;
        this.newRule = newRule;
        if (rule.condition == null) rule.condition = new RuleCondition();
        if (rule.actions == null) rule.actions = new ArrayList<>();
        if (rule.actions.isEmpty()) rule.actions.add(ActionSpec.spawn("minecraft:zombie", 1));
    }

    public static Rule newRule() {
        RuleCondition condition = new RuleCondition();
        condition.commentCommand = "!novo";
        return new Rule("custom_" + System.currentTimeMillis(), "Minha regra", LiveEventType.COMMENT, condition,
                3_000, 20_000, List.of(ActionSpec.spawn("minecraft:zombie", 1)));
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(10, (height - 310) / 2);
        int fieldX = left + 154;
        int fieldWidth = 292;

        nameField = field(fieldX, top + 42, fieldWidth, rule.name, 80);
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
            addRenderableWidget(Button.builder(Component.literal("Escolher"), button -> openTargetPicker())
                    .bounds(fieldX + 136, top + 206, 74, 20).build());
        } else if (action.type == ActionType.MESSAGE) {
            actionTargetField = field(fieldX, top + 206, fieldWidth, action.message, 200);
            actionTargetField.setHint(Component.literal("Mensagem mostrada no jogo"));
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
        addRenderableWidget(Button.builder(Component.literal("+ Ação"), button -> {
            persistFields(false);
            rule.actions.add(ActionSpec.spawn("minecraft:zombie", 1));
            actionIndex = rule.actions.size() - 1;
            rebuildScreen();
        }).bounds(left + 90, top + 234, 70, 20).build());
        Button removeAction = Button.builder(Component.literal("− Ação"), button -> {
            persistFields(false);
            if (rule.actions.size() > 1) {
                rule.actions.remove(actionIndex);
                actionIndex = Math.max(0, actionIndex - 1);
            }
            rebuildScreen();
        }).bounds(left + 168, top + 234, 70, 20).build();
        removeAction.active = rule.actions.size() > 1;
        addRenderableWidget(removeAction);

        addRenderableWidget(Button.builder(Component.literal("Salvar"), button -> save())
                .bounds(left + 18, top + 272, 94, 22).build());
        if (!newRule) {
            addRenderableWidget(Button.builder(Component.literal("Excluir").withStyle(ChatFormatting.RED), button -> {
                TikTokChaosMod.runtime().config().rules.remove(rule);
                TikTokChaosMod.runtime().saveConfig();
                returnToParent();
            }).bounds(left + 120, top + 272, 94, 22).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Cancelar"), button -> returnToParent())
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
        graphics.drawString(font, newRule ? "NOVA REGRA" : "EDITAR REGRA", left, top + 4, 0xFF66F0C8, true);
        label(graphics, left, top + 48, "Nome");
        label(graphics, left, top + 74, "Evento / estado");
        label(graphics, left, top + 100, "Comando / ID / meta");
        label(graphics, left, top + 128, "Valor mín. / máx.");
        label(graphics, left, top + 156, "Cooldown global/usuário (s)");
        ActionSpec action = currentAction();
        label(graphics, left, top + 186, primaryLabel(action));
        label(graphics, left, top + 212, targetLabel(action));
        graphics.drawString(font, "Ação " + (actionIndex + 1) + "/" + rule.actions.size()
                        + " • " + actionSummary(action),
                left + 250, top + 240, 0xFFBDB0C7, true);
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
                if (action.type == ActionType.MESSAGE) action.message = actionTargetField.getValue().strip();
                else action.target = actionTargetField.getValue().strip();
            }
            if (validate && rule.name.isBlank()) throw new IllegalArgumentException("Informe um nome");
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
            case SPAWN_ENTITY -> "minecraft:zombie";
            case GIVE_ITEM -> "minecraft:bread";
            case APPLY_EFFECT -> "minecraft:slowness";
            default -> "";
        };
        if (action.type == ActionType.APPLY_EFFECT) {
            action.durationTicks = 200;
            action.amplifier = 0;
        } else if (action.type == ActionType.SHORT_TELEPORT) {
            action.radius = 10;
        } else if (action.type == ActionType.SET_WEATHER) {
            action.durationTicks = 600;
        } else if (action.type == ActionType.MESSAGE) {
            action.message = "";
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
            case SPAWN_ENTITY -> VisualTargetCatalog.Kind.ENTITY;
            case GIVE_ITEM -> VisualTargetCatalog.Kind.ITEM;
            case APPLY_EFFECT -> VisualTargetCatalog.Kind.EFFECT;
            default -> null;
        };
    }

    private boolean hasPrimaryCounter(ActionType type) {
        return switch (type) {
            case SPAWN_ENTITY, GIVE_ITEM, APPLY_EFFECT, SHORT_TELEPORT, SET_WEATHER -> true;
            default -> false;
        };
    }

    private void adjustPrimary(ActionSpec action, int direction) {
        switch (action.type) {
            case SPAWN_ENTITY, GIVE_ITEM -> action.amount = clamp(action.amount + direction, 1, 20);
            case APPLY_EFFECT -> action.amplifier = clamp(action.amplifier + direction, 0, 9);
            case SHORT_TELEPORT -> action.radius = clamp(action.radius + direction, 3, 64);
            case SET_WEATHER -> action.durationTicks = clamp(action.durationTicks + direction * 100, 100, 12_000);
            default -> {
            }
        }
    }

    private String primaryLabel(ActionSpec action) {
        return switch (action.type) {
            case SPAWN_ENTITY, GIVE_ITEM -> "Tipo da ação / quantidade";
            case APPLY_EFFECT -> "Tipo / nível do efeito";
            case SHORT_TELEPORT -> "Tipo / raio em blocos";
            case SET_WEATHER -> "Tipo / duração";
            default -> "Tipo da ação";
        };
    }

    private String targetLabel(ActionSpec action) {
        return switch (action.type) {
            case SPAWN_ENTITY -> "Mob / catálogo visual";
            case GIVE_ITEM -> "Item / catálogo visual";
            case APPLY_EFFECT -> "Efeito / duração";
            case MESSAGE -> "Mensagem exibida";
            default -> "Configuração automática";
        };
    }

    private String actionSummary(ActionSpec action) {
        String random = ActionTargets.isRandom(action.target) ? " • aleatório" : "";
        return switch (action.type) {
            case SPAWN_ENTITY, GIVE_ITEM -> "quantidade " + action.amount + random;
            case APPLY_EFFECT -> "nível " + (action.amplifier + 1) + " • "
                    + Math.max(1, action.durationTicks / 20) + "s" + random;
            case SHORT_TELEPORT -> "raio " + action.radius + " blocos";
            case SET_WEATHER -> Math.max(1, action.durationTicks / 20) + " segundos";
            case MESSAGE -> action.message.isBlank() ? "mensagem padrão" : trim(action.message, 24);
            case COSMETIC_LIGHTNING -> "raio apenas visual";
            case RANDOM_SAFE_ITEM -> "item seguro aleatório";
            case RANDOM_POSITIVE_EFFECT -> "efeito positivo aleatório";
            case RANDOM_NEGATIVE_EFFECT -> "efeito negativo aleatório";
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }

    private Component eventLabel() {
        return Component.literal("Evento: " + eventName(rule.event));
    }

    private Component enabledLabel() {
        return Component.literal(rule.enabled ? "Ativa" : "Pausada")
                .withStyle(rule.enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    private Component actionTypeLabel(ActionSpec action) {
        return Component.literal("Ação: " + actionName(action.type));
    }

    private String eventName(LiveEventType type) {
        return switch (type) {
            case LIKE -> "Curtidas";
            case GIFT -> "Presente";
            case COMMENT -> "Comentário";
            case FOLLOW -> "Follow";
            case SHARE -> "Compartilhamento";
            case SUBSCRIBE -> "Inscrição";
            case JOIN -> "Entrada";
            default -> type.name();
        };
    }

    private String actionName(ActionType type) {
        return switch (type) {
            case SPAWN_ENTITY -> "Invocar mob";
            case GIVE_ITEM -> "Dar item";
            case APPLY_EFFECT -> "Aplicar efeito";
            case SHORT_TELEPORT -> "Teleporte curto";
            case COSMETIC_LIGHTNING -> "Raio visual";
            case SET_WEATHER -> "Chuva temporária";
            case MESSAGE -> "Mensagem";
            case RANDOM_SAFE_ITEM -> "Item aleatório";
            case RANDOM_POSITIVE_EFFECT -> "Efeito positivo";
            case RANDOM_NEGATIVE_EFFECT -> "Efeito negativo";
        };
    }

    private int integer(EditBox field, int fallback) {
        String value = field.getValue().strip();
        if (value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Número inválido: " + value);
        }
    }

    private long longValue(EditBox field, long fallback) {
        String value = field.getValue().strip();
        if (value.isBlank()) return fallback;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Número inválido: " + value);
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
