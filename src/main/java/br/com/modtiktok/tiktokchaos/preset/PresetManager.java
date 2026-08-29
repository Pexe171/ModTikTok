package br.com.modtiktok.tiktokchaos.preset;

import br.com.modtiktok.tiktokchaos.config.TikTokChaosConfig;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import br.com.modtiktok.tiktokchaos.rule.ActionSpec;
import br.com.modtiktok.tiktokchaos.rule.ActionType;
import br.com.modtiktok.tiktokchaos.rule.ExecutionSpec;
import br.com.modtiktok.tiktokchaos.rule.ExecutionTier;
import br.com.modtiktok.tiktokchaos.rule.Rule;
import br.com.modtiktok.tiktokchaos.rule.RuleCondition;
import br.com.modtiktok.tiktokchaos.rule.SequenceStep;
import br.com.modtiktok.tiktokchaos.rule.WeightedChoice;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class PresetManager {
    private static final long MAX_PRESET_BYTES = 1_048_576L;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter EXPORT_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Path directory;
    private final Map<String, PresetDocument> builtIns;

    public PresetManager(Path configFile) {
        this.directory = configFile.getParent().resolve("tiktok-chaos").resolve("presets");
        this.builtIns = createBuiltIns();
    }

    public Path directory() {
        return directory;
    }

    public List<PresetDocument> catalog() {
        List<PresetDocument> result = new ArrayList<>();
        builtIns.values().forEach(document -> result.add(copy(document)));
        result.addAll(loadLocal());
        return List.copyOf(result);
    }

    public PresetDocument find(String id) {
        PresetDocument builtIn = builtIns.get(id);
        if (builtIn != null) return copy(builtIn);
        return loadLocal().stream().filter(document -> document.id.equals(id)).findFirst()
                .map(PresetManager::copy).orElse(null);
    }

    public List<PresetDocument> loadLocal() {
        if (!Files.isDirectory(directory)) return List.of();
        List<PresetDocument> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .sorted()
                    .forEach(path -> {
                        try {
                            result.add(read(path));
                        } catch (RuntimeException | IOException ignored) {
                            // Invalid files remain untouched and are simply excluded from the catalog.
                        }
                    });
        } catch (IOException ignored) {
            return List.of();
        }
        return List.copyOf(result);
    }

    public PresetDocument read(Path file) throws IOException {
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        Path normalizedFile = file.toAbsolutePath().normalize();
        if (!normalizedFile.getParent().equals(normalizedDirectory)
                || !normalizedFile.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new IllegalArgumentException("O preset precisa estar na pasta local de presets");
        }
        if (Files.isSymbolicLink(normalizedFile) || Files.size(normalizedFile) > MAX_PRESET_BYTES) {
            throw new IllegalArgumentException("Arquivo de preset não permitido");
        }
        JsonObject root = new JsonParser().parse(Files.readString(normalizedFile, StandardCharsets.UTF_8))
                .getAsJsonObject();
        rejectPersonalData(root);
        PresetDocument document = GSON.fromJson(root, PresetDocument.class);
        return validate(document);
    }

    public Path exportCurrent(TikTokChaosConfig config) throws IOException {
        Files.createDirectories(directory);
        TikTokChaosConfig.Safety safety = GSON.fromJson(GSON.toJson(config.safety), TikTokChaosConfig.Safety.class);
        PresetDocument document = new PresetDocument("exported", "Preset exportado",
                "Regras exportadas localmente pelo TikTok Chaos", safety, config.rules);
        document = validate(document);
        String base = "tiktok-chaos-" + EXPORT_TIME.format(LocalDateTime.now());
        Path target = directory.resolve(base + ".json");
        int suffix = 2;
        while (Files.exists(target)) target = directory.resolve(base + "-" + suffix++ + ".json");
        Files.writeString(target, GSON.toJson(document), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return target;
    }

    public PresetPreview preview(TikTokChaosConfig current, PresetDocument preset, PresetApplyMode mode,
                                 Predicate<ActionSpec> targetValidator) {
        return preview(current, preset, mode, targetValidator, ignored -> true);
    }

    public PresetPreview preview(TikTokChaosConfig current, PresetDocument preset, PresetApplyMode mode,
                                 Predicate<ActionSpec> targetValidator, Predicate<String> modValidator) {
        ApplyResult result = merge(current, validate(copy(preset)), mode, targetValidator, modValidator, false);
        return result.preview();
    }

    public TikTokChaosConfig apply(TikTokChaosConfig current, PresetDocument preset, PresetApplyMode mode,
                                   Predicate<ActionSpec> targetValidator) {
        return apply(current, preset, mode, targetValidator, ignored -> true);
    }

    public TikTokChaosConfig apply(TikTokChaosConfig current, PresetDocument preset, PresetApplyMode mode,
                                   Predicate<ActionSpec> targetValidator, Predicate<String> modValidator) {
        return merge(current, validate(copy(preset)), mode, targetValidator, modValidator, true).config();
    }

    public PresetCompatibility compatibility(PresetDocument preset, Predicate<String> modValidator) {
        PresetDocument checked = validate(copy(preset));
        List<String> missing = new ArrayList<>();
        for (PresetRequirement requirement : checked.requirements) {
            boolean found = requirement.anyOfModIds.stream().anyMatch(modValidator);
            if (!found) {
                missing.add(requirement.name + " (" + String.join(" or ", requirement.anyOfModIds) + ")");
            }
        }
        return new PresetCompatibility(missing.isEmpty(), List.copyOf(missing));
    }

    private ApplyResult merge(TikTokChaosConfig current, PresetDocument preset, PresetApplyMode mode,
                              Predicate<ActionSpec> targetValidator, Predicate<String> modValidator,
                              boolean requireCompatible) {
        PresetCompatibility compatibility = compatibility(preset, modValidator);
        if (requireCompatible && !compatibility.available()) {
            throw new IllegalStateException("Required mods missing: "
                    + String.join(", ", compatibility.missingRequirements()));
        }
        TikTokChaosConfig result = GSON.fromJson(GSON.toJson(current), TikTokChaosConfig.class);
        List<String> warnings = new ArrayList<>();
        int added = 0;
        int replaced = 0;
        int renamed = 0;
        int disabled = 0;
        if (!compatibility.available()) {
            warnings.add("Required mods missing: " + String.join(", ", compatibility.missingRequirements()));
        }

        if (mode == PresetApplyMode.REPLACE) {
            replaced = result.rules.size();
            result.rules = new ArrayList<>();
            if (preset.recommendedSafety != null) {
                result.safety = GSON.fromJson(GSON.toJson(preset.recommendedSafety), TikTokChaosConfig.Safety.class);
            }
        }

        Set<String> usedIds = new HashSet<>();
        result.rules.forEach(rule -> usedIds.add(rule.id));
        for (Rule source : preset.rules) {
            Rule rule = GSON.fromJson(GSON.toJson(source), Rule.class);
            String originalId = rule.id;
            rule.id = uniqueId(originalId, usedIds);
            if (!rule.id.equals(originalId)) {
                renamed++;
                warnings.add("ID " + originalId + " importado como " + rule.id);
            }
            usedIds.add(rule.id);
            boolean missingTarget = allActions(rule).stream().anyMatch(action -> !targetValidator.test(action));
            if (missingTarget) {
                rule.enabled = false;
                disabled++;
                warnings.add("Regra " + rule.name + " desativada: alvo ausente ou inválido");
            }
            result.rules.add(rule);
            added++;
        }

        PresetPreview preview = new PresetPreview(preset.id, preset.name, mode, result.rules.size(), added,
                replaced, renamed, disabled, compatibility.available(), compatibility.missingRequirements(),
                List.copyOf(warnings));
        return new ApplyResult(result, preview);
    }

    private PresetDocument readBuiltIn(String id) {
        PresetDocument document = builtIns.get(id);
        return document == null ? null : copy(document);
    }

    private static PresetDocument validate(PresetDocument value) {
        if (value == null || value.schemaVersion != PresetDocument.CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Versão de preset não suportada");
        }
        value.id = cleanId(value.id);
        if (value.name == null || value.name.isBlank()) value.name = value.id;
        if (value.description == null) value.description = "";
        if (value.category == null || value.category.isBlank()) value.category = "general";
        value.category = cleanId(value.category);
        if (value.requirements == null) value.requirements = new ArrayList<>();
        if (value.requirements.size() > 20) throw new IllegalArgumentException("Preset exceeds 20 requirements");
        for (PresetRequirement requirement : value.requirements) {
            if (requirement == null) throw new IllegalArgumentException("Preset contains an empty requirement");
            if (requirement.name == null || requirement.name.isBlank()) requirement.name = "Mod";
            if (requirement.anyOfModIds == null || requirement.anyOfModIds.isEmpty()) {
                throw new IllegalArgumentException("Preset contains a requirement without mod ids");
            }
            requirement.anyOfModIds = requirement.anyOfModIds.stream()
                    .map(id -> id == null ? "" : id.strip().toLowerCase(Locale.ROOT))
                    .filter(id -> id.matches("[a-z0-9_-]{1,80}"))
                    .distinct().toList();
            if (requirement.anyOfModIds.isEmpty()) {
                throw new IllegalArgumentException("Preset contains an invalid mod requirement");
            }
        }
        if (value.rules == null) value.rules = new ArrayList<>();
        if (value.rules.size() > 500) throw new IllegalArgumentException("Preset excede 500 regras");
        Set<String> ids = new HashSet<>();
        for (Rule rule : value.rules) {
            if (rule == null) throw new IllegalArgumentException("Preset contém regra vazia");
            rule.id = uniqueId(cleanId(rule.id), ids);
            ids.add(rule.id);
            if (rule.name == null || rule.name.isBlank()) rule.name = rule.id;
            if (rule.event == null || rule.condition == null) throw new IllegalArgumentException("Regra incompleta");
            if (rule.actions == null) rule.actions = new ArrayList<>();
            if (rule.actions.size() > 20) throw new IllegalArgumentException("Regra excede 20 ações");
            validateActions(rule.actions);
            if (rule.execution == null) rule.execution = new ExecutionSpec();
            if (rule.execution.tiers == null) rule.execution.tiers = new ArrayList<>();
            if (rule.execution.roulette == null) rule.execution.roulette = new ArrayList<>();
            for (ExecutionTier tier : rule.execution.tiers) {
                if (tier == null) throw new IllegalArgumentException("Preset contém faixa vazia");
                if (tier.actions == null) tier.actions = new ArrayList<>();
                validateActions(tier.actions);
            }
            for (WeightedChoice choice : rule.execution.roulette) {
                if (choice == null) throw new IllegalArgumentException("Preset contém opção vazia");
                if (choice.actions == null) choice.actions = new ArrayList<>();
                validateActions(choice.actions);
            }
            if (rule.sequence == null) rule.sequence = new ArrayList<>();
            if (rule.sequence.size() > 20) throw new IllegalArgumentException("Sequência excede 20 passos");
            for (SequenceStep step : rule.sequence) {
                if (step == null || step.action == null) throw new IllegalArgumentException("Passo vazio");
                validateActions(List.of(step.action));
            }
        }
        return value;
    }

    private static void validateActions(List<ActionSpec> actions) {
        if (actions.size() > 20) throw new IllegalArgumentException("Lista excede 20 ações");
        for (ActionSpec action : actions) {
            if (action == null || action.type == null) throw new IllegalArgumentException("Ação vazia");
            if (action.target == null) action.target = "";
            if (action.message == null) action.message = "";
        }
    }

    private static List<ActionSpec> allActions(Rule rule) {
        List<ActionSpec> result = new ArrayList<>(rule.actions);
        for (ExecutionTier tier : rule.execution.tiers) result.addAll(tier.actions);
        for (WeightedChoice choice : rule.execution.roulette) result.addAll(choice.actions);
        for (SequenceStep step : rule.sequence) result.add(step.action);
        return result;
    }

    private static void rejectPersonalData(JsonElement element) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(PresetManager::rejectPersonalData);
            return;
        }
        if (!element.isJsonObject()) return;
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (key.equals("connection") || key.equals("username") || key.equals("userid")
                    || key.equals("avatarurl") || key.equals("commenthistory")) {
                throw new IllegalArgumentException("Preset contém dado pessoal ou de conexão: " + entry.getKey());
            }
            rejectPersonalData(entry.getValue());
        }
    }

    private static String uniqueId(String preferred, Set<String> usedIds) {
        if (!usedIds.contains(preferred)) return preferred;
        int suffix = 2;
        while (usedIds.contains(preferred + "-" + suffix)) suffix++;
        return preferred + "-" + suffix;
    }

    private static String cleanId(String value) {
        String clean = value == null ? "preset" : value.strip().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "-").replaceAll("-+", "-");
        clean = clean.replaceAll("^-|-$", "");
        if (clean.isBlank()) clean = "preset";
        return clean.length() > 80 ? clean.substring(0, 80) : clean;
    }

    private static PresetDocument copy(PresetDocument document) {
        return GSON.fromJson(GSON.toJson(document), PresetDocument.class);
    }

    private static Map<String, PresetDocument> createBuiltIns() {
        Map<String, PresetDocument> presets = new LinkedHashMap<>();
        TikTokChaosConfig defaults = TikTokChaosConfig.defaults();
        presets.put("survival-chaos", new PresetDocument("survival-chaos", "Survival Chaos",
                "Equilíbrio entre recompensas, sustos e combate.", safety(2, 20, 60), defaults.rules));

        List<Rule> zombies = List.of(
                gift("zombie-gift", "Presente: horda zumbi", 1, Integer.MAX_VALUE,
                        ActionSpec.spawn("minecraft:zombie", 1)),
                comment("zombie-command", "!zumbi", ActionSpec.spawn("minecraft:zombie", 1)),
                likes("zombie-likes", 100, ActionSpec.spawn("minecraft:zombie", 1))
        );
        presets.put("zombie-apocalypse", new PresetDocument("zombie-apocalypse", "Zombie Apocalypse",
                "Toda interação alimenta uma invasão zumbi controlada.", safety(2, 30, 75), zombies));

        List<Rule> rewards = List.of(
                gift("reward-gift", "Presente: recompensa", 1, Integer.MAX_VALUE,
                        ActionSpec.simple(ActionType.RANDOM_SAFE_ITEM)),
                likes("reward-likes", 100, ActionSpec.simple(ActionType.RANDOM_POSITIVE_EFFECT)),
                simple("reward-follow", "Follow: comida", LiveEventType.FOLLOW,
                        ActionSpec.give("minecraft:bread", 4))
        );
        presets.put("safe-rewards", new PresetDocument("safe-rewards", "Safe Rewards",
                "Somente recompensas e efeitos positivos.", safety(4, 10, 30), rewards));

        List<Rule> hardcore = List.of(
                gift("hardcore-small", "Presente pequeno", 1, 9, ActionSpec.spawn("minecraft:skeleton", 1)),
                gift("hardcore-large", "Presente grande", 10, Integer.MAX_VALUE,
                        ActionSpec.spawn("minecraft:ravager", 1)),
                likes("hardcore-likes", 50, ActionSpec.simple(ActionType.RANDOM_NEGATIVE_EFFECT)),
                simple("hardcore-share", "Share: tempestade", LiveEventType.SHARE,
                        ActionSpec.simple(ActionType.SET_WEATHER))
        );
        presets.put("hardcore-night", new PresetDocument("hardcore-night", "Hardcore Night",
                "Ritmo agressivo, ainda limitado e reversível.", safety(2, 25, 60), hardcore));

        Rule boxRule = gift("bedrock-box-gift", "Presente: caixa reversível", 10, Integer.MAX_VALUE,
                ActionSpec.simple(ActionType.REVERSIBLE_BLOCK_BOX));
        boxRule.enabled = false;
        presets.put("bedrock-box", new PresetDocument("bedrock-box", "Bedrock Box",
                "Caixa temporária. A regra começa desligada e exige confirmação em Segurança.",
                safety(1, 10, 30), List.of(boxRule)));

        Rule arenaGift = gift("arena-gift", "Presente: onda da arena", 1, Integer.MAX_VALUE,
                ActionSpec.spawn("tiktokchaos:random", 1));
        arenaGift.sequence.add(new br.com.modtiktok.tiktokchaos.rule.SequenceStep(40,
                ActionSpec.spawn("tiktokchaos:random", 1)));
        arenaGift.sequence.add(new br.com.modtiktok.tiktokchaos.rule.SequenceStep(80,
                ActionSpec.simple(ActionType.LIKE_FOUNTAIN)));
        presets.put("mob-arena", new PresetDocument("mob-arena", "Mob Arena",
                "Ondas pequenas e temporizadas de mobs seguros.", safety(2, 25, 60), List.of(arenaGift)));

        Rule bossGift = gift("boss-rush-gift", "Presente: boss do espectador", 100, Integer.MAX_VALUE,
                ActionSpec.spawn("minecraft:ravager", 1));
        bossGift.actions.get(0).type = ActionType.SPAWN_VIEWER_BOSS;
        bossGift.execution.mode = br.com.modtiktok.tiktokchaos.rule.ExecutionMode.ONCE;
        presets.put("boss-rush", new PresetDocument("boss-rush", "Boss Rush",
                "Presentes grandes invocam bosses limitados e identificados.", safety(1, 20, 90),
                List.of(bossGift)));

        Rule diamondGift = gift("diamond-hunt-gift", "Presente: pista de diamante", 1, Integer.MAX_VALUE,
                ActionSpec.simple(ActionType.GIFT_CANNON));
        diamondGift.actions.get(0).target = "minecraft:diamond";
        diamondGift.actions.get(0).amount = 5;
        diamondGift.sequence.add(new br.com.modtiktok.tiktokchaos.rule.SequenceStep(20,
                new ActionSpec(ActionType.CENTER_MESSAGE, "", 1, 0, 0, 10,
                        "Encontre o diamante visual!")));
        presets.put("diamond-hunt", new PresetDocument("diamond-hunt", "Diamond Hunt",
                "Desafio visual sem criar itens coletáveis nem alterar blocos.", safety(3, 15, 30),
                List.of(diamondGift)));
        List<Rule> cursedWalking = new ArrayList<>();
        cursedWalking.add(simple("cursed-follow-ammo", "Follow: munição 9mm", LiveEventType.FOLLOW,
                integration("tacz:ammo_9mm", 24)));
        cursedWalking.add(gift("cursed-small-ammo", "Presente pequeno: cartuchos 12g", 1, 9,
                integration("tacz:ammo_12g", 8)));
        cursedWalking.add(once(gift("cursed-medium-ammo", "Presente médio: munição 5.56", 10, 99,
                integration("tacz:ammo_556x45", 24))));
        cursedWalking.add(once(gift("cursed-rare-ammo", "Presente grande: munição rara .50 BMG", 100,
                Integer.MAX_VALUE, integration("tacz:ammo_50bmg", 8))));
        cursedWalking.add(likes("cursed-zombie-wave", 100, ActionSpec.spawn("minecraft:zombie", 1)));
        presets.put("cursed-walking", modPreset("cursed-walking", "Cursed Walking",
                "Seguidores entregam munição e presentes sobem até a rara .50 BMG.",
                safety(4, 35, 75), cursedWalking,
                PresetRequirement.mod("Timeless and Classics Zero", "tacz"),
                PresetRequirement.mod("Cursed Walking zombies", "zombies_plus", "feralzombie")));

        List<Rule> pixelmon = List.of(
                gift("pixelmon-shiny-gift", "Presente: Pokémon shiny aleatório", 1, Integer.MAX_VALUE,
                        integration("pixelmon:random_shiny", 1)),
                simple("pixelmon-follow-candy", "Follow: Rare Candy", LiveEventType.FOLLOW,
                        ActionSpec.give("pixelmon:rare_candy", 2)),
                simple("pixelmon-sub-master-ball", "Inscrição: Master Ball", LiveEventType.SUBSCRIBE,
                        ActionSpec.give("pixelmon:master_ball", 1))
        );
        presets.put("pixelmon", modPreset("pixelmon", "Pixelmon",
                "Cada unidade do presente invoca um Pokémon shiny aleatório.", safety(4, 20, 90), pixelmon,
                PresetRequirement.mod("Pixelmon", "pixelmon")));

        List<Rule> cobblemon = List.of(
                gift("cobblemon-random-gift", "Presente: Pokémon aleatório da área", 1, Integer.MAX_VALUE,
                        integration("cobblemon:random", 1)),
                simple("cobblemon-follow-candy", "Follow: Rare Candy", LiveEventType.FOLLOW,
                        ActionSpec.give("cobblemon:rare_candy", 2)),
                simple("cobblemon-sub-master-ball", "Inscrição: Master Ball", LiveEventType.SUBSCRIBE,
                        ActionSpec.give("cobblemon:master_ball", 1))
        );
        presets.put("cobblemon", modPreset("cobblemon", "Cobblemon",
                "Presentes invocam Pokémon da tabela natural da área.", safety(4, 20, 90), cobblemon,
                PresetRequirement.mod("Cobblemon", "cobblemon")));

        List<Rule> allTheMods = List.of(
                simple("atm-follow-redstone", "Follow: redstone", LiveEventType.FOLLOW,
                        ActionSpec.give("minecraft:redstone", 16)),
                gift("atm-small-allthemodium", "Presente pequeno: allthemodium", 1, 9,
                        ActionSpec.give("allthemodium:allthemodium_ingot", 1)),
                once(gift("atm-medium-vibranium", "Presente médio: vibranium", 10, 99,
                        ActionSpec.give("allthemodium:vibranium_ingot", 1))),
                once(gift("atm-large-unobtainium", "Presente grande: unobtainium", 100, Integer.MAX_VALUE,
                        ActionSpec.give("allthemodium:unobtainium_ingot", 1)))
        );
        presets.put("all-the-mods", modPreset("all-the-mods", "All the Mods",
                "Progressão de redstone a Allthemodium, Vibranium e Unobtainium.",
                safety(4, 20, 60), allTheMods,
                PresetRequirement.mod("Allthemodium (ATM 6/7/8/9/10)", "allthemodium")));

        List<Rule> betterMc = List.of(
                simple("bettermc-follow-ambrosium", "Follow: Ambrosium", LiveEventType.FOLLOW,
                        ActionSpec.give("aether:ambrosium_shard", 4)),
                gift("bettermc-gift-warpstone", "Presente: Warp Stone", 1, Integer.MAX_VALUE,
                        ActionSpec.give("waystones:warp_stone", 1)),
                likes("bettermc-likes-aerbunny", 250, ActionSpec.spawn("aether:aerbunny", 1))
        );
        presets.put("better-mc", modPreset("better-mc", "Better MC (Forge)",
                "Recompensas do Aether e Waystones, componentes centrais do pack.",
                safety(3, 20, 60), betterMc,
                PresetRequirement.mod("The Aether", "aether"),
                PresetRequirement.mod("Waystones", "waystones")));

        List<Rule> dawnCraft = List.of(
                simple("dawncraft-follow-gapple", "Follow: maçã dourada", LiveEventType.FOLLOW,
                        ActionSpec.give("minecraft:golden_apple", 1)),
                gift("dawncraft-gift-nine-tails", "Presente: Nine Tails", 10, Integer.MAX_VALUE,
                        ActionSpec.spawn("simple_mobs:nine_tails", 1)),
                likes("dawncraft-likes-strength", 100, ActionSpec.effect("minecraft:strength", 20, 0))
        );
        presets.put("dawncraft", modPreset("dawncraft", "DawnCraft",
                "Combate e recompensas inspirados na progressão Souls-like do pack.",
                safety(2, 20, 75), dawnCraft,
                PresetRequirement.mod("DawnCraft Mobs", "simple_mobs")));

        List<Rule> vaultHunters = List.of(
                simple("vault-follow-diamond", "Follow: Vault Diamond", LiveEventType.FOLLOW,
                        ActionSpec.give("the_vault:vault_diamond", 1)),
                once(gift("vault-gift-crystal", "Presente: Vault Crystal", 10, Integer.MAX_VALUE,
                        ActionSpec.give("the_vault:vault_crystal", 1))),
                likes("vault-likes-luck", 100, ActionSpec.effect("minecraft:luck", 30, 0))
        );
        presets.put("vault-hunters", modPreset("vault-hunters", "Vault Hunters",
                "Vault Diamonds e cristais ligados à progressão do modpack.",
                safety(3, 20, 60), vaultHunters,
                PresetRequirement.mod("The Vault", "the_vault")));

        List<Rule> create = List.of(
                simple("create-follow-alloy", "Follow: Andesite Alloy", LiveEventType.FOLLOW,
                        ActionSpec.give("create:andesite_alloy", 4)),
                gift("create-gift-sheet", "Presente: Golden Sheet", 1, 9,
                        ActionSpec.give("create:golden_sheet", 2)),
                once(gift("create-gift-precision", "Presente maior: Precision Mechanism", 10,
                        Integer.MAX_VALUE, ActionSpec.give("create:precision_mechanism", 1)))
        );
        presets.put("create", modPreset("create", "Create",
                "Materiais de automação e mecanismos de precisão.", safety(4, 15, 45), create,
                PresetRequirement.mod("Create", "create")));

        List<Rule> mekanism = List.of(
                simple("mekanism-follow-osmium", "Follow: Osmium", LiveEventType.FOLLOW,
                        ActionSpec.give("mekanism:ingot_osmium", 4)),
                gift("mekanism-small-alloy", "Presente pequeno: Infused Alloy", 1, 9,
                        ActionSpec.give("mekanism:alloy_infused", 2)),
                once(gift("mekanism-medium-alloy", "Presente médio: Reinforced Alloy", 10, 99,
                        ActionSpec.give("mekanism:alloy_reinforced", 2))),
                once(gift("mekanism-large-alloy", "Presente grande: Atomic Alloy", 100,
                        Integer.MAX_VALUE, ActionSpec.give("mekanism:alloy_atomic", 2)))
        );
        presets.put("mekanism", modPreset("mekanism", "Mekanism",
                "Escada de materiais do Osmium ao Atomic Alloy.", safety(4, 15, 45), mekanism,
                PresetRequirement.mod("Mekanism", "mekanism")));

        List<Rule> ironSpells = List.of(
                simple("irons-follow-common-ink", "Follow: Common Ink", LiveEventType.FOLLOW,
                        ActionSpec.give("irons_spellbooks:common_ink", 2)),
                gift("irons-small-rare-ink", "Presente pequeno: Rare Ink", 1, 9,
                        ActionSpec.give("irons_spellbooks:rare_ink", 1)),
                once(gift("irons-medium-epic-ink", "Presente médio: Epic Ink", 10, 99,
                        ActionSpec.give("irons_spellbooks:epic_ink", 1))),
                once(gift("irons-large-legendary-ink", "Presente grande: Legendary Ink", 100,
                        Integer.MAX_VALUE, ActionSpec.give("irons_spellbooks:legendary_ink", 1)))
        );
        presets.put("irons-spells", modPreset("irons-spells", "Iron's Spells 'n Spellbooks",
                "Tintas mágicas comuns, raras, épicas e lendárias.", safety(4, 15, 45), ironSpells,
                PresetRequirement.mod("Iron's Spells 'n Spellbooks", "irons_spellbooks")));

        return java.util.Collections.unmodifiableMap(presets);
    }

    private static TikTokChaosConfig.Safety safety(int actionsPerSecond, int mobs, int lifetime) {
        TikTokChaosConfig.Safety safety = new TikTokChaosConfig.Safety();
        safety.maxActionsPerSecond = actionsPerSecond;
        safety.maxTrackedMobs = mobs;
        safety.mobLifetimeSeconds = lifetime;
        return safety;
    }

    private static Rule gift(String id, String name, int min, int max, ActionSpec action) {
        RuleCondition condition = new RuleCondition();
        condition.minGiftValue = min;
        condition.maxGiftValue = max;
        return new Rule(id, name, LiveEventType.GIFT, condition, 0, 0, List.of(action));
    }

    private static Rule likes(String id, int threshold, ActionSpec action) {
        RuleCondition condition = new RuleCondition();
        condition.threshold = threshold;
        return new Rule(id, threshold + " curtidas", LiveEventType.LIKE, condition, 0, 0, List.of(action));
    }

    private static Rule comment(String id, String command, ActionSpec action) {
        RuleCondition condition = new RuleCondition();
        condition.commentCommand = command;
        return new Rule(id, "Comando " + command, LiveEventType.COMMENT, condition, 3_000, 15_000,
                List.of(action));
    }

    private static Rule simple(String id, String name, LiveEventType type, ActionSpec action) {
        return new Rule(id, name, type, new RuleCondition(), 0, 0, List.of(action));
    }

    private static PresetDocument modPreset(String id, String name, String description,
                                            TikTokChaosConfig.Safety safety, List<Rule> rules,
                                            PresetRequirement... requirements) {
        return new PresetDocument(id, name, description, "popular-mods", List.of(requirements), safety, rules);
    }

    private static ActionSpec integration(String target, int amount) {
        return new ActionSpec(ActionType.MOD_INTEGRATION, target, amount, 0, 0, 0, "");
    }

    private static Rule once(Rule rule) {
        rule.execution.mode = br.com.modtiktok.tiktokchaos.rule.ExecutionMode.ONCE;
        return rule;
    }

    private record ApplyResult(TikTokChaosConfig config, PresetPreview preview) {
    }
}
