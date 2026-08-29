package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.TikTokChaosMod;
import br.com.modtiktok.tiktokchaos.TikTokChaosRuntime;
import br.com.modtiktok.tiktokchaos.preset.PresetApplyMode;
import br.com.modtiktok.tiktokchaos.preset.PresetCompatibility;
import br.com.modtiktok.tiktokchaos.preset.PresetDocument;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class PopularModPresetsScreen extends Screen {
    private static final int PANEL_WIDTH = 430;
    private final Screen parent;
    private int page;
    private String selectedId = "cursed-walking";

    public PopularModPresetsScreen(Screen parent) {
        super(Component.translatable("gui.tiktokchaos.preset_popular_mods"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(18, (height - 255) / 2);
        TikTokChaosRuntime runtime = TikTokChaosMod.runtime();
        List<PresetDocument> presets = presets(runtime);
        int pages = Math.max(1, (presets.size() + 4) / 5);
        page = Math.max(0, Math.min(page, pages - 1));
        int first = page * 5;
        for (int index = first; index < Math.min(presets.size(), first + 5); index++) {
            PresetDocument preset = presets.get(index);
            PresetCompatibility compatibility = runtime.presetCompatibility(preset.id);
            String marker = compatibility.available() ? "[x] " : "[ ] ";
            if (preset.id.equals(selectedId)) marker = "> " + marker;
            addRenderableWidget(new Button(left + 18, top + 54 + (index - first) * 25, 394, 22,
                    Component.literal(marker + ClientText.configuredName("preset", preset.id, preset.name)),
                    button -> {
                        selectedId = preset.id;
                        rebuild();
                    }));
        }
        Button previous = new Button(left + 18, top + 188, 32, 20, Component.literal("<"), button -> {
            page--;
            rebuild();
        });
        previous.active = page > 0;
        addRenderableWidget(previous);
        Button next = new Button(left + 54, top + 188, 32, 20, Component.literal(">"), button -> {
            page++;
            rebuild();
        });
        next.active = page + 1 < pages;
        addRenderableWidget(next);
        PresetCompatibility selected = runtime.presetCompatibility(selectedId);
        Button replace = new Button(left + 98, top + 188, 116, 20,
                Component.translatable("gui.tiktokchaos.replace"),
                button -> apply(runtime, PresetApplyMode.REPLACE));
        replace.active = selected.available();
        addRenderableWidget(replace);
        Button merge = new Button(left + 220, top + 188, 92, 20,
                Component.translatable("gui.tiktokchaos.merge"),
                button -> apply(runtime, PresetApplyMode.MERGE));
        merge.active = selected.available();
        addRenderableWidget(merge);
        addRenderableWidget(new Button(left + 320, top + 188, 92, 20,
                Component.translatable("gui.tiktokchaos.back"), button -> onClose()));
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        fill(poseStack, 0, 0, width, height, 0xB0000000);
        int left = (width - PANEL_WIDTH) / 2;
        int top = Math.max(18, (height - 255) / 2);
        fillGradient(poseStack, left - 8, top - 8, left + PANEL_WIDTH + 8, top + 230,
                0xF21A1024, 0xF20E1420);
        super.render(poseStack, mouseX, mouseY, partialTick);
        font.drawShadow(poseStack, ClientText.text("gui.tiktokchaos.preset_popular_mods"), left + 18, top + 18,
                0xFF66F0C8);
        PresetCompatibility compatibility = TikTokChaosMod.runtime().presetCompatibility(selectedId);
        String status = compatibility.available()
                ? ClientText.text("gui.tiktokchaos.preset_compatible")
                : ClientText.text("gui.tiktokchaos.preset_missing_mods",
                trim(String.join(", ", compatibility.missingRequirements()), 46));
        font.drawShadow(poseStack, status, left + 18, top + 218,
                compatibility.available() ? ChatFormatting.GREEN.getColor() : ChatFormatting.RED.getColor());
    }

    private void apply(TikTokChaosRuntime runtime, PresetApplyMode mode) {
        runtime.applyPreset(selectedId, mode);
        rebuild();
    }

    private List<PresetDocument> presets(TikTokChaosRuntime runtime) {
        return runtime.presetCatalog().stream().filter(preset -> "popular-mods".equals(preset.category)).toList();
    }

    private void rebuild() {
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

    private static String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
