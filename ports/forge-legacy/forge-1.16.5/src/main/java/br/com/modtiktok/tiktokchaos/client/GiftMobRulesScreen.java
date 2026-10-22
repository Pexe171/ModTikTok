package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.gameplay.ActionTargets;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import br.com.modtiktok.tiktokchaos.rule.ActionSpec;
import br.com.modtiktok.tiktokchaos.rule.ActionType;
import br.com.modtiktok.tiktokchaos.rule.Rule;
import br.com.modtiktok.tiktokchaos.simulator.SimulationRequest;
import com.google.gson.Gson;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.UUID;

/** Compact gift-to-mob editor for Minecraft versions without the full rule editor. */
public final class GiftMobRulesScreen extends Screen {
    private final Screen parent;
    private Rule selected;
    private TextFieldWidget giftNameField;
    private TextFieldWidget giftIdField;
    private TextFieldWidget mobField;
    private TextFieldWidget amountField;
    private String message = "";

    public GiftMobRulesScreen(Screen parent) {
        super(new TranslationTextComponent("gui.tiktokchaos.gift_mobs.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (width - 430) / 2;
        int top = Math.max(10, (height - 255) / 2);
        addButton(new Button(left + 18, top + 54, 32, 20, new StringTextComponent("<"), b -> select(-1)));
        addButton(new Button(left + 380, top + 54, 32, 20, new StringTextComponent(">"), b -> select(1)));
        giftNameField = field(left + 18, top + 100, 284,
                selected == null ? "" : selected.condition.giftName, "gui.tiktokchaos.rule_editor.gift_name_hint");
        giftIdField = field(left + 312, top + 100, 100,
                selected == null || selected.condition.giftId < 0 ? "" : Integer.toString(selected.condition.giftId),
                "gui.tiktokchaos.rule_editor.gift_id_hint");
        mobField = field(left + 18, top + 146, 284,
                selected == null ? "minecraft:zombie" : selected.actions.get(0).target,
                "gui.tiktokchaos.gift_mobs.mob_hint");
        amountField = field(left + 312, top + 146, 100,
                selected == null ? "1" : Integer.toString(selected.actions.get(0).amount),
                "gui.tiktokchaos.simulator.amount_hint");
        addButton(new Button(left + 18, top + 190, 88, 22,
                new TranslationTextComponent("gui.tiktokchaos.save"), b -> save()));
        Button delete = new Button(left + 114, top + 190, 88, 22,
                new TranslationTextComponent("gui.tiktokchaos.delete"), b -> delete());
        delete.active = selected != null;
        addButton(delete);
        addButton(new Button(left + 210, top + 190, 106, 22,
                new TranslationTextComponent("gui.tiktokchaos.preview"), b -> preview()));
        addButton(new Button(left + 324, top + 190, 88, 22,
                new TranslationTextComponent("gui.tiktokchaos.back"), b -> onClose()));
    }

    private TextFieldWidget field(int x, int y, int width, String value, String hint) {
        TextFieldWidget field = new TextFieldWidget(font, x, y, width, 22, new TranslationTextComponent(hint));
        field.setMaxLength(128);
        field.setValue(value == null ? "" : value);
        addButton(field);
        return field;
    }

    private List<Rule> rules() {
        return TikTokChaosMod.runtime().config().rules.stream()
                .filter(r -> r.event == LiveEventType.GIFT && r.condition != null && r.condition.isSpecificGift()
                        && r.actions.size() == 1 && r.actions.get(0).type == ActionType.SPAWN_ENTITY).toList();
    }

    private void select(int direction) {
        List<Rule> rules = rules();
        int index = selected == null ? rules.size() : rules.indexOf(selected);
        index = Math.floorMod(index + direction, rules.size() + 1);
        selected = index == rules.size() ? null : rules.get(index);
        message = "";
        rebuild();
    }

    private void save() {
        try {
            String giftName = giftNameField.getValue().strip();
            int giftId = giftIdField.getValue().isBlank() ? -1 : Integer.parseInt(giftIdField.getValue().strip());
            int amount = Integer.parseInt(amountField.getValue().strip());
            if (giftId < -1 || amount < 1 || amount > 20) throw new IllegalArgumentException();
            if (giftName.isBlank() && giftId < 0) {
                message = ClientText.text("gui.tiktokchaos.gift_mobs.gift_required");
                return;
            }
            String mob = mobField.getValue().strip();
            ResourceLocation id = ResourceLocation.tryParse(mob);
            if (!ActionTargets.isRandom(mob) && (id == null || !Registry.ENTITY_TYPE.containsKey(id)
                    || !ActionTargets.isAllowedEntity(Registry.ENTITY_TYPE.get(id)))) {
                message = ClientText.text("gui.tiktokchaos.gift_mobs.invalid_mob");
                return;
            }
            Gson gson = new Gson();
            Rule updated = selected == null ? new Rule() : gson.fromJson(gson.toJson(selected), Rule.class);
            if (selected == null) {
                updated.id = "gift_mob_" + UUID.randomUUID();
                updated.name = (giftName.isBlank() ? Integer.toString(giftId) : giftName) + " -> " + mob;
            }
            updated.event = LiveEventType.GIFT;
            updated.condition.commentCommand = "";
            updated.condition.giftName = giftName;
            updated.condition.giftId = giftId;
            updated.actions = List.of(ActionSpec.spawn(mob, amount));
            List<Rule> configured = TikTokChaosMod.runtime().config().rules;
            int index = selected == null ? configured.size() : configured.indexOf(selected);
            if (selected == null) configured.add(updated); else configured.set(index, updated);
            if (!TikTokChaosMod.runtime().saveConfig()) {
                if (selected == null) configured.remove(updated); else configured.set(index, selected);
                message = TikTokChaosMod.runtime().configManager().getLastError();
                return;
            }
            selected = updated;
            message = ClientText.text("gui.tiktokchaos.gift_mobs.saved");
            rebuild();
        } catch (IllegalArgumentException exception) {
            message = ClientText.text("gui.tiktokchaos.gift_mobs.invalid_numbers");
        }
    }

    private void delete() {
        if (selected == null) return;
        List<Rule> configured = TikTokChaosMod.runtime().config().rules;
        int index = configured.indexOf(selected);
        configured.remove(index);
        if (!TikTokChaosMod.runtime().saveConfig()) {
            configured.add(index, selected);
            message = TikTokChaosMod.runtime().configManager().getLastError();
            return;
        }
        selected = null;
        message = "";
        rebuild();
    }

    private void preview() {
        try {
            int id = giftIdField.getValue().isBlank() ? -1 : Integer.parseInt(giftIdField.getValue().strip());
            var result = TikTokChaosMod.runtime().simulate(new SimulationRequest("Viewer", LiveEventType.GIFT,
                    id, giftNameField.getValue(), 1, 3, 0, ""), false);
            message = ClientText.text("gui.tiktokchaos.gift_mobs.preview_result", result.matchedActions());
        } catch (IllegalArgumentException exception) {
            message = ClientText.text("gui.tiktokchaos.gift_mobs.invalid_numbers");
        }
    }

    private void rebuild() {
        buttons.clear();
        children.clear();
        init();
    }

    @Override
    public void render(MatrixStack poseStack, int mouseX, int mouseY, float partialTick) {
        int left = (width - 430) / 2;
        int top = Math.max(10, (height - 255) / 2);
        fill(poseStack, 0, 0, width, height, 0xB0000000);
        fillGradient(poseStack, left - 8, top - 8, left + 438, top + 245, 0xF21A1024, 0xF20E1420);
        super.render(poseStack, mouseX, mouseY, partialTick);
        font.drawShadow(poseStack, ClientText.text("gui.tiktokchaos.gift_mobs.title"), left + 18, top + 8, 0xFF66F0C8);
        font.drawShadow(poseStack, ClientText.text("gui.tiktokchaos.rule_editor.gift_help"), left + 18, top + 28, 0xFFBDB0C7);
        String name = selected == null ? ClientText.text("gui.tiktokchaos.rule_editor.new_heading") : selected.name;
        font.drawShadow(poseStack, font.plainSubstrByWidth(name, 310), left + 58, top + 60, 0xFFFFFFFF);
        font.drawShadow(poseStack, ClientText.text("gui.tiktokchaos.gift_mobs.gift_fields"), left + 18, top + 86, 0xFFD9CEDF);
        font.drawShadow(poseStack, ClientText.text("gui.tiktokchaos.rule_editor.gift_id_hint"), left + 312, top + 86, 0xFFD9CEDF);
        font.drawShadow(poseStack, ClientText.text("gui.tiktokchaos.gift_mobs.mob_fields"), left + 18, top + 132, 0xFFD9CEDF);
        font.drawShadow(poseStack, ClientText.text("gui.tiktokchaos.simulator.amount_hint"), left + 312, top + 132, 0xFFD9CEDF);
        font.drawShadow(poseStack, font.plainSubstrByWidth(message, 394), left + 18, top + 224, 0xFFFFD166);
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
