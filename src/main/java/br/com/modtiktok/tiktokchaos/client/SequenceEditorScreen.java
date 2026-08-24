package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.rule.ActionSpec;
import br.com.modtiktok.tiktokchaos.rule.ActionType;
import br.com.modtiktok.tiktokchaos.rule.Rule;
import br.com.modtiktok.tiktokchaos.rule.SequenceStep;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SequenceEditorScreen extends Screen {
    private final Screen parent;
    private final Rule rule;
    private int index;
    private EditBox delayField;
    private EditBox valueField;
    private String validation = "";

    public SequenceEditorScreen(Screen parent, Rule rule) {
        super(ClientText.component("gui.tiktokchaos.sequence.title"));
        this.parent = parent;
        this.rule = rule;
    }

    @Override
    protected void init() {
        int left = (width - 500) / 2;
        int top = Math.max(12, (height - 280) / 2);
        if (rule.sequence.isEmpty()) {
            addRenderableWidget(Button.builder(ClientText.component("gui.tiktokchaos.sequence.first_step"), button -> {
                rule.sequence.add(new SequenceStep(0, ActionSpec.simple(ActionType.PARTICLE_BURST)));
                rebuildScreen();
            }).bounds(left + 170, top + 112, 160, 24).build());
        } else {
            index = Math.max(0, Math.min(index, rule.sequence.size() - 1));
            SequenceStep step = rule.sequence.get(index);
            delayField = field(left + 162, top + 66, 100,
                    decimalSeconds(step.delayTicks), "gui.tiktokchaos.sequence.seconds_hint");
            addRenderableWidget(Button.builder(ClientText.component("gui.tiktokchaos.rule_editor.action",
                    ClientText.action(step.action.type)), button -> {
                persist();
                ActionType[] values = ActionType.values();
                step.action.type = values[(step.action.type.ordinal() + 1) % values.length];
                applyDefault(step.action);
                rebuildScreen();
            }).bounds(left + 162, top + 98, 250, 22).build());
            valueField = field(left + 162, top + 130, 250, value(step.action),
                    "gui.tiktokchaos.sequence.target_hint");
            addRenderableWidget(Button.builder(Component.literal("−"), button -> {
                persist();
                step.action.amount = Math.max(1, step.action.amount - 1);
                rebuildScreen();
            }).bounds(left + 420, top + 98, 30, 22).build());
            addRenderableWidget(Button.builder(Component.literal("+"), button -> {
                persist();
                step.action.amount = Math.min(20, step.action.amount + 1);
                rebuildScreen();
            }).bounds(left + 454, top + 98, 30, 22).build());

            Button previous = Button.builder(Component.literal("‹"), button -> {
                persist();
                index--;
                rebuildScreen();
            }).bounds(left + 18, top + 178, 34, 22).build();
            previous.active = index > 0;
            addRenderableWidget(previous);
            Button next = Button.builder(Component.literal("›"), button -> {
                persist();
                index++;
                rebuildScreen();
            }).bounds(left + 58, top + 178, 34, 22).build();
            next.active = index + 1 < rule.sequence.size();
            addRenderableWidget(next);
            Button add = Button.builder(ClientText.component("gui.tiktokchaos.sequence.add_step"), button -> {
                persist();
                if (rule.sequence.size() >= 20) {
                    validation = ClientText.text("gui.tiktokchaos.sequence.step_limit");
                    return;
                }
                int delay = step.delayTicks + 20;
                rule.sequence.add(new SequenceStep(delay, ActionSpec.simple(ActionType.PARTICLE_BURST)));
                index = rule.sequence.size() - 1;
                rebuildScreen();
            }).bounds(left + 104, top + 178, 84, 22).build();
            add.active = rule.sequence.size() < 20;
            addRenderableWidget(add);
            addRenderableWidget(Button.builder(ClientText.component("gui.tiktokchaos.sequence.remove_step"), button -> {
                rule.sequence.remove(index);
                index = Math.max(0, index - 1);
                rebuildScreen();
            }).bounds(left + 196, top + 178, 84, 22).build());
        }
        addRenderableWidget(Button.builder(ClientText.component("gui.tiktokchaos.done"), button -> {
            if (persist() && minecraft != null) minecraft.setScreen(parent);
        }).bounds(left + 388, top + 238, 94, 22).build());
    }

    private EditBox field(int x, int y, int width, String value, String hintKey) {
        EditBox field = new EditBox(font, x, y, width, 22, ClientText.component(hintKey));
        field.setMaxLength(200);
        field.setValue(value);
        field.setHint(ClientText.component(hintKey));
        addRenderableWidget(field);
        return field;
    }

    private boolean persist() {
        if (rule.sequence.isEmpty() || delayField == null) return true;
        try {
            SequenceStep step = rule.sequence.get(index);
            double seconds = Double.parseDouble(delayField.getValue().replace(',', '.'));
            if (seconds < 0 || seconds > 120) {
                throw new IllegalArgumentException(ClientText.text("gui.tiktokchaos.sequence.invalid_delay"));
            }
            step.delayTicks = (int) Math.round(seconds * 20.0);
            String value = valueField.getValue().strip();
            if (step.action.type == ActionType.MESSAGE || step.action.type == ActionType.CENTER_MESSAGE) {
                step.action.message = value;
            } else {
                step.action.target = value;
            }
            validation = "";
            return true;
        } catch (RuntimeException error) {
            validation = error.getMessage();
            return false;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xB0000000);
        int left = (width - 500) / 2;
        int top = Math.max(12, (height - 280) / 2);
        graphics.fillGradient(left, top, left + 500, top + 280, 0xF21A1024, 0xF20E1420);
        graphics.fill(left, top, left + 4, top + 280, 0xFF66F0C8);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, ClientText.text("gui.tiktokchaos.sequence.heading"), left + 18, top + 18,
                0xFF66F0C8, true);
        if (!rule.sequence.isEmpty()) {
            SequenceStep step = rule.sequence.get(Math.max(0, Math.min(index, rule.sequence.size() - 1)));
            graphics.drawString(font, ClientText.text("gui.tiktokchaos.sequence.step", index + 1,
                            rule.sequence.size()), left + 18, top + 48,
                    0xFFE3D9EA, true);
            graphics.drawString(font, ClientText.text("gui.tiktokchaos.sequence.delay"), left + 18, top + 72,
                    0xFFCFC4D6, true);
            graphics.drawString(font, ClientText.text("gui.tiktokchaos.sequence.amount_strength", step.action.amount),
                    left + 286, top + 72,
                    0xFFCFC4D6, true);
            graphics.drawString(font, ClientText.text("gui.tiktokchaos.sequence.target_or_message"), left + 18,
                    top + 136, 0xFFCFC4D6, true);
            graphics.drawString(font, ClientText.text("gui.tiktokchaos.sequence.f9_note"), left + 18,
                    top + 214, 0xFFFFD166, true);
        }
        if (!validation.isBlank()) graphics.drawString(font, validation, left + 18, top + 244, 0xFFFF6B81, true);
    }

    private static String value(ActionSpec action) {
        return action.type == ActionType.MESSAGE || action.type == ActionType.CENTER_MESSAGE
                ? action.message : action.target;
    }

    private static String decimalSeconds(int ticks) {
        return ticks % 20 == 0 ? Integer.toString(ticks / 20) : String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0);
    }

    private static void applyDefault(ActionSpec action) {
        action.target = switch (action.type) {
            case SPAWN_ENTITY, SPAWN_VIEWER_BOSS -> "minecraft:zombie";
            case GIVE_ITEM, VISUAL_ITEM_RAIN, GIFT_CANNON -> "minecraft:diamond";
            case APPLY_EFFECT -> "minecraft:slowness";
            case PLAY_SOUND -> "minecraft:entity.experience_orb.pickup";
            default -> "";
        };
        if (action.type == ActionType.MESSAGE || action.type == ActionType.CENTER_MESSAGE) action.message = "TikTok Chaos!";
        if (action.type == ActionType.FREEZE_PLAYER) action.durationTicks = 100;
    }

    private void rebuildScreen() {
        clearWidgets();
        init();
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
