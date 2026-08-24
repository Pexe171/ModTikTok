package br.com.modtiktok.tiktokchaos.config;

import br.com.modtiktok.tiktokchaos.analytics.GoalMetric;
import br.com.modtiktok.tiktokchaos.analytics.GoalSpec;
import br.com.modtiktok.tiktokchaos.live.LiveEventType;
import br.com.modtiktok.tiktokchaos.rule.ActionSpec;
import br.com.modtiktok.tiktokchaos.rule.ActionType;
import br.com.modtiktok.tiktokchaos.rule.Rule;
import br.com.modtiktok.tiktokchaos.rule.RuleCondition;

import java.util.ArrayList;
import java.util.List;

public final class TikTokChaosConfig {
    public static final int CURRENT_SCHEMA_VERSION = 3;

    public int schemaVersion = CURRENT_SCHEMA_VERSION;
    public Connection connection = new Connection();
    public Hud hud = new Hud();
    public Safety safety = new Safety();
    public Avatars avatars = new Avatars();
    public Overlay overlay = new Overlay();
    public List<GoalSpec> goals = new ArrayList<>();
    public List<Rule> rules = new ArrayList<>();

    public static TikTokChaosConfig defaults() {
        TikTokChaosConfig config = new TikTokChaosConfig();
        config.goals.add(new GoalSpec("likes-1000", "1.000 curtidas", GoalMetric.LIKES, 1_000));
        config.goals.add(new GoalSpec("coins-100", "100 moedas", GoalMetric.COINS, 100));
        config.rules.add(likes());
        config.rules.add(follow());
        config.rules.add(share());
        config.rules.add(subscribe());
        config.rules.add(comment("comment_zombie", "!zumbi", ActionSpec.spawn("minecraft:zombie", 1)));
        config.rules.add(comment("comment_item", "!item", ActionSpec.simple(ActionType.RANDOM_SAFE_ITEM)));
        config.rules.add(comment("comment_luck", "!sorte", ActionSpec.simple(ActionType.RANDOM_POSITIVE_EFFECT)));
        config.rules.add(comment("comment_bad_luck", "!azar", ActionSpec.simple(ActionType.RANDOM_NEGATIVE_EFFECT)));
        config.rules.add(giftTier("gift_small", "Presente pequeno", 1, 9,
                List.of(ActionSpec.spawn("minecraft:zombie", 1))));
        config.rules.add(giftTier("gift_medium", "Presente médio", 10, 99,
                List.of(ActionSpec.spawn("minecraft:skeleton", 2))));
        config.rules.add(giftTier("gift_large", "Presente grande", 100, 999,
                List.of(ActionSpec.spawn("minecraft:zombie", 4),
                        ActionSpec.effect("minecraft:slowness", 10, 0),
                        ActionSpec.simple(ActionType.COSMETIC_LIGHTNING))));
        config.rules.add(giftTier("gift_epic", "Presente épico", 1000, Integer.MAX_VALUE,
                List.of(ActionSpec.spawn("minecraft:ravager", 1),
                        ActionSpec.effect("minecraft:blindness", 5, 0),
                        ActionSpec.simple(ActionType.COSMETIC_LIGHTNING))));
        return config;
    }

    private static Rule likes() {
        RuleCondition condition = new RuleCondition();
        condition.threshold = 100;
        return new Rule("likes_100", "100 curtidas", LiveEventType.LIKE, condition, 0, 0,
                List.of(ActionSpec.spawn("minecraft:zombie", 1)));
    }

    private static Rule follow() {
        return new Rule("follow", "Novo follow", LiveEventType.FOLLOW, new RuleCondition(), 500, 0,
                List.of(ActionSpec.give("minecraft:bread", 4)));
    }

    private static Rule share() {
        return new Rule("share", "Compartilhamento", LiveEventType.SHARE, new RuleCondition(), 1_500, 0,
                List.of(ActionSpec.spawn("minecraft:skeleton", 1)));
    }

    private static Rule subscribe() {
        return new Rule("subscribe", "Nova inscrição", LiveEventType.SUBSCRIBE, new RuleCondition(), 500, 0,
                List.of(ActionSpec.give("minecraft:golden_apple", 1),
                        ActionSpec.effect("minecraft:regeneration", 10, 0)));
    }

    private static Rule comment(String id, String command, ActionSpec action) {
        RuleCondition condition = new RuleCondition();
        condition.commentCommand = command;
        return new Rule(id, "Comando " + command, LiveEventType.COMMENT, condition, 3_000, 20_000,
                List.of(action));
    }

    private static Rule giftTier(String id, String name, int min, int max, List<ActionSpec> actions) {
        RuleCondition condition = new RuleCondition();
        condition.minGiftValue = min;
        condition.maxGiftValue = max;
        return new Rule(id, name, LiveEventType.GIFT, condition, 0, 0, actions);
    }

    public static final class Connection {
        public String username = "";
        public boolean autoReconnect = true;
        public boolean autoConnectWhenWorldOpens = false;
        public int reconnectInitialSeconds = 2;
        public int reconnectMaximumSeconds = 60;
    }

    public static final class Hud {
        public boolean enabled = true;
        public int offsetX = 8;
        public int offsetY = 8;
        public int historySize = 100;
        public boolean showGoals = true;
        public boolean showRanking = false;
        public boolean showChat = false;
        public boolean hideViewerNames = false;
    }

    public static final class Safety {
        public int maxActionsPerSecond = 2;
        public int maxTrackedMobs = 20;
        public int minSpawnRadius = 8;
        public int maxSpawnRadius = 14;
        public int mobLifetimeSeconds = 60;
        public int maxQueueSize = 200;
        public int maxTriggersPerEvent = 5;
        public boolean adaptivePerformance = true;
        public int maxViewerBosses = 3;
        public boolean destructiveActionsEnabled = false;
        public boolean destructiveActionsConfirmed = false;
        public int maxChangedBlocks = 64;
        public int blockRestoreSeconds = 30;
    }

    public static final class Avatars {
        public boolean enabled = false;
        public int maxBytes = 512 * 1024;
        public int maxDimension = 512;
        public List<String> allowedHosts = new ArrayList<>(List.of("*.tiktokcdn.com", "*.tiktokcdn-us.com"));
    }

    public static final class Overlay {
        public boolean enabled = false;
        public int port = 0;
    }
}
