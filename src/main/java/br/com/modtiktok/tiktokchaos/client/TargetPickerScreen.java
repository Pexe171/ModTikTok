package br.com.modtiktok.tiktokchaos.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.Consumer;

/** Searchable, scrollable and virtualized registry picker. */
public final class TargetPickerScreen extends Screen {
    private static final int CELL_HEIGHT = 50;

    private final Screen parent;
    private final VisualTargetCatalog.Kind kind;
    private final String selectedId;
    private final Consumer<String> onSelected;
    private EditBox searchField;
    private List<VisualTargetCatalog.Entry> allEntries = List.of();
    private List<VisualTargetCatalog.Entry> filteredEntries = List.of();
    private String query = "";
    private int scrollRow;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int gridLeft;
    private int gridTop;
    private int gridRight;
    private int gridBottom;
    private int columns;
    private int cellWidth;
    private int visibleRows;

    public TargetPickerScreen(Screen parent, VisualTargetCatalog.Kind kind, String selectedId,
                              Consumer<String> onSelected) {
        super(Component.literal(kind.title()));
        this.parent = parent;
        this.kind = kind;
        this.selectedId = selectedId == null ? "" : selectedId;
        this.onSelected = onSelected;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(640, Math.max(300, width - 24));
        panelHeight = Math.min(380, Math.max(220, height - 20));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        gridLeft = panelLeft + 14;
        gridTop = panelTop + 54;
        gridRight = panelLeft + panelWidth - 22;
        int availableGridHeight = panelHeight - 88;
        visibleRows = Math.max(1, availableGridHeight / CELL_HEIGHT);
        gridBottom = gridTop + visibleRows * CELL_HEIGHT;
        columns = Math.max(2, (gridRight - gridLeft) / 92);
        cellWidth = (gridRight - gridLeft) / columns;

        searchField = new EditBox(font, panelLeft + 14, panelTop + 27, panelWidth - 120, 20,
                Component.literal("Buscar"));
        searchField.setMaxLength(100);
        searchField.setHint(Component.literal("Buscar por nome ou ID..."));
        searchField.setValue(query);
        searchField.setResponder(value -> {
            query = value;
            applyFilter(false);
        });
        addRenderableWidget(searchField);
        addRenderableWidget(Button.builder(Component.literal("Voltar"), button -> returnToParent())
                .bounds(panelLeft + panelWidth - 96, panelTop + 27, 82, 20).build());

        allEntries = VisualTargetCatalog.entries(kind, minecraft);
        applyFilter(true);
        setInitialFocus(searchField);
    }

    private void applyFilter(boolean centerSelection) {
        String normalized = VisualTargetCatalog.normalize(query);
        filteredEntries = normalized.isBlank()
                ? allEntries
                : allEntries.stream().filter(entry -> entry.searchText().contains(normalized)).toList();
        scrollRow = Mth.clamp(scrollRow, 0, maxScrollRow());
        if (centerSelection && !selectedId.isBlank()) {
            for (int index = 0; index < filteredEntries.size(); index++) {
                if (filteredEntries.get(index).id().equals(selectedId)) {
                    scrollRow = Mth.clamp(index / columns - visibleRows / 2, 0, maxScrollRow());
                    break;
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xB8000000);
        graphics.fillGradient(panelLeft - 6, panelTop - 6, panelLeft + panelWidth + 6, panelTop + panelHeight + 6,
                0xFA1A1024, 0xFA0E1420);
        graphics.fill(panelLeft - 6, panelTop - 6, panelLeft - 2, panelTop + panelHeight + 6, 0xFF66F0C8);
        graphics.fill(gridLeft - 2, gridTop - 2, gridRight + 2, gridBottom + 2, 0xD9070A10);

        // As in the other screens, draw custom content after FancyMenu's blur hook.
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawString(font, kind.title().toUpperCase(), panelLeft + 14, panelTop + 8, 0xFF66F0C8, true);
        graphics.drawString(font, filteredEntries.size() + " " + kind.plural(), panelLeft + panelWidth - 92,
                panelTop + 9, 0xFFC6BCCE, true);

        VisualTargetCatalog.Entry hovered = renderVisibleEntries(graphics, mouseX, mouseY);
        renderScrollbar(graphics);
        graphics.drawString(font, "Role para ver mais • clique para selecionar", gridLeft,
                panelTop + panelHeight - 20, 0xFFB8AFC0, true);
        if (filteredEntries.isEmpty()) {
            graphics.drawCenteredString(font, "Nenhum resultado encontrado", (gridLeft + gridRight) / 2,
                    gridTop + 20, 0xFFFFC857);
        }
        if (hovered != null) {
            graphics.renderTooltip(font, List.of(
                    Component.literal(hovered.name()),
                    Component.literal(hovered.id()).withStyle(ChatFormatting.GRAY)
            ), java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private VisualTargetCatalog.Entry renderVisibleEntries(GuiGraphics graphics, int mouseX, int mouseY) {
        VisualTargetCatalog.Entry hovered = null;
        int firstIndex = scrollRow * columns;
        int lastIndex = Math.min(filteredEntries.size(), firstIndex + visibleRows * columns);
        graphics.enableScissor(gridLeft, gridTop, gridRight, gridBottom);
        for (int index = firstIndex; index < lastIndex; index++) {
            int local = index - firstIndex;
            int column = local % columns;
            int row = local / columns;
            int x = gridLeft + column * cellWidth;
            int y = gridTop + row * CELL_HEIGHT;
            VisualTargetCatalog.Entry entry = filteredEntries.get(index);
            boolean isHovered = mouseX >= x && mouseX < x + cellWidth - 2
                    && mouseY >= y && mouseY < y + CELL_HEIGHT - 2;
            boolean selected = entry.id().equals(selectedId);
            int border = selected ? 0xFF66F0C8 : isHovered ? 0xFFE83E8C : 0xFF33283E;
            int background = selected ? 0xE0233B3C : isHovered ? 0xE0321B36 : 0xD916121D;
            graphics.fill(x, y, x + cellWidth - 2, y + CELL_HEIGHT - 2, border);
            graphics.fill(x + 1, y + 1, x + cellWidth - 3, y + CELL_HEIGHT - 3, background);

            int iconX = x + (cellWidth - 16) / 2;
            int iconY = y + 4;
            if (!entry.itemIcon().isEmpty()) {
                graphics.renderItem(entry.itemIcon(), iconX, iconY, index);
            } else if (entry.effectIcon() != null) {
                TextureAtlasSprite sprite = MinecraftVersionCompat.effectSprite(minecraft, entry.effectIcon());
                graphics.blit(iconX - 1, iconY - 1, 0, 18, 18, sprite);
            }

            String name = font.plainSubstrByWidth(entry.name(), cellWidth - 8);
            String id = font.plainSubstrByWidth(entry.id(), cellWidth - 8);
            graphics.drawCenteredString(font, name, x + cellWidth / 2, y + 23, 0xFFFFFFFF);
            graphics.drawCenteredString(font, id, x + cellWidth / 2, y + 35, 0xFF9E93A8);
            if (isHovered) hovered = entry;
        }
        graphics.disableScissor();
        return hovered;
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int totalRows = totalRows();
        if (totalRows <= visibleRows) return;
        int trackX = gridRight + 6;
        int trackHeight = gridBottom - gridTop;
        int thumbHeight = Math.max(14, trackHeight * visibleRows / totalRows);
        int travel = trackHeight - thumbHeight;
        int thumbY = gridTop + (maxScrollRow() == 0 ? 0 : travel * scrollRow / maxScrollRow());
        graphics.fill(trackX, gridTop, trackX + 4, gridBottom, 0xFF241B2B);
        graphics.fill(trackX, thumbY, trackX + 4, thumbY + thumbHeight, 0xFF66F0C8);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return handleMouseScrolled(mouseX, mouseY, scrollY);
    }

    /** Minecraft 1.20.1 and older expose the three-argument scroll callback. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        return handleMouseScrolled(mouseX, mouseY, scrollY);
    }

    private boolean handleMouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!insideGrid(mouseX, mouseY) || maxScrollRow() == 0) return false;
        int direction = scrollY > 0 ? -1 : 1;
        scrollRow = Mth.clamp(scrollRow + direction, 0, maxScrollRow());
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        if (insideGrid(mouseX, mouseY)) {
            int column = (int) (mouseX - gridLeft) / cellWidth;
            int row = (int) (mouseY - gridTop) / CELL_HEIGHT;
            int index = (scrollRow + row) * columns + column;
            if (column < columns && row < visibleRows && index >= 0 && index < filteredEntries.size()) {
                onSelected.accept(filteredEntries.get(index).id());
                returnToParent();
                return true;
            }
        }
        int trackX = gridRight + 4;
        if (mouseX >= trackX && mouseX <= trackX + 8 && mouseY >= gridTop && mouseY < gridBottom
                && maxScrollRow() > 0) {
            double ratio = (mouseY - gridTop) / (double) (gridBottom - gridTop);
            scrollRow = Mth.clamp((int) Math.round(ratio * maxScrollRow()), 0, maxScrollRow());
            return true;
        }
        return false;
    }

    private boolean insideGrid(double mouseX, double mouseY) {
        return mouseX >= gridLeft && mouseX < gridRight && mouseY >= gridTop && mouseY < gridBottom;
    }

    private int totalRows() {
        return (filteredEntries.size() + columns - 1) / columns;
    }

    private int maxScrollRow() {
        return Math.max(0, totalRows() - visibleRows);
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
