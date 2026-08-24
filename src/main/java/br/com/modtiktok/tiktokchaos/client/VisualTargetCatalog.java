package br.com.modtiktok.tiktokchaos.client;

import br.com.modtiktok.tiktokchaos.gameplay.ActionTargets;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/** Builds each localized registry catalog once and reuses it between picker screens. */
final class VisualTargetCatalog {
    private static final EnumMap<Kind, List<Entry>> CACHE = new EnumMap<>(Kind.class);
    private static String cachedLanguage = "";

    private VisualTargetCatalog() {
    }

    static synchronized List<Entry> entries(Kind kind, Minecraft minecraft) {
        String language = minecraft.getLanguageManager().getSelected();
        if (!language.equals(cachedLanguage)) {
            CACHE.clear();
            cachedLanguage = language;
        }
        return CACHE.computeIfAbsent(kind, VisualTargetCatalog::build);
    }

    private static List<Entry> build(Kind kind) {
        List<Entry> entries = switch (kind) {
            case ENTITY -> buildEntities();
            case ITEM -> buildItems();
            case EFFECT -> buildEffects();
        };
        entries.sort(Comparator
                .comparing((Entry entry) -> !entry.id().startsWith("minecraft:"))
                .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::id));
        entries.add(0, randomEntry(kind));
        return List.copyOf(entries);
    }

    private static Entry randomEntry(Kind kind) {
        String name = ClientText.text("gui.tiktokchaos.picker.random_all_mods");
        return switch (kind) {
            case ENTITY -> Entry.item(ActionTargets.RANDOM_TARGET, name,
                    new ItemStack(Items.SPAWNER));
            case ITEM -> Entry.item(ActionTargets.RANDOM_TARGET, name,
                    new ItemStack(Items.CHEST));
            case EFFECT -> Entry.item(ActionTargets.RANDOM_TARGET, name,
                    new ItemStack(Items.POTION));
        };
    }

    private static List<Entry> buildEntities() {
        List<Entry> entries = new ArrayList<>();
        for (var registered : BuiltInRegistries.ENTITY_TYPE.entrySet()) {
            EntityType<?> type = registered.getValue();
            if (!ActionTargets.isAllowedEntity(type)) continue;
            SpawnEggItem egg = SpawnEggItem.byId(type);
            if (egg == null) continue;
            String id = registered.getKey().location().toString();
            String name = type.getDescription().getString();
            entries.add(Entry.item(id, name, new ItemStack(egg)));
        }
        return entries;
    }

    private static List<Entry> buildItems() {
        List<Entry> entries = new ArrayList<>();
        for (var registered : BuiltInRegistries.ITEM.entrySet()) {
            Item item = registered.getValue();
            if (item == Items.AIR) continue;
            String id = registered.getKey().location().toString();
            ItemStack stack = new ItemStack(item);
            entries.add(Entry.item(id, stack.getHoverName().getString(), stack));
        }
        return entries;
    }

    private static List<Entry> buildEffects() {
        List<Entry> entries = new ArrayList<>();
        for (var registered : BuiltInRegistries.MOB_EFFECT.entrySet()) {
            MobEffect effect = registered.getValue();
            String id = registered.getKey().location().toString();
            Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
            entries.add(Entry.effect(id, effect.getDisplayName().getString(), holder));
        }
        return entries;
    }

    static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .strip();
    }

    enum Kind {
        ENTITY("gui.tiktokchaos.picker.entity_title", "gui.tiktokchaos.picker.entity_count"),
        ITEM("gui.tiktokchaos.picker.item_title", "gui.tiktokchaos.picker.item_count"),
        EFFECT("gui.tiktokchaos.picker.effect_title", "gui.tiktokchaos.picker.effect_count");

        private final String titleKey;
        private final String countKey;

        Kind(String titleKey, String countKey) {
            this.titleKey = titleKey;
            this.countKey = countKey;
        }

        String titleKey() {
            return titleKey;
        }

        String countKey() {
            return countKey;
        }
    }

    record Entry(String id, String name, String searchText, ItemStack itemIcon, Object effectIcon) {
        static Entry item(String id, String name, ItemStack icon) {
            return new Entry(id, name, normalize(name + " " + id), icon, null);
        }

        static Entry effect(String id, String name, Holder<MobEffect> icon) {
            return new Entry(id, name, normalize(name + " " + id), ItemStack.EMPTY, icon);
        }
    }
}
