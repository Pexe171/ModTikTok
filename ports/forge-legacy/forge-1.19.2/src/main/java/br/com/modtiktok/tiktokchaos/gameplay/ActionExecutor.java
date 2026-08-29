package br.com.modtiktok.tiktokchaos.gameplay;

import br.com.modtiktok.tiktokchaos.compat.ModCompatibilityActions;
import br.com.modtiktok.tiktokchaos.config.TikTokChaosConfig;
import br.com.modtiktok.tiktokchaos.rule.ActionRequest;
import br.com.modtiktok.tiktokchaos.rule.ActionSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Gameplay implementation for Minecraft's pre-1.19.3 registry and teleport APIs. */
public final class ActionExecutor {
    private static final List<Item> SAFE_RANDOM_ITEMS = List.of(
            Items.BREAD, Items.COOKED_BEEF, Items.BAKED_POTATO, Items.TORCH, Items.ARROW, Items.IRON_INGOT
    );
    private static final List<MobEffect> POSITIVE_EFFECTS = List.of(
            MobEffects.REGENERATION, MobEffects.MOVEMENT_SPEED, MobEffects.DAMAGE_RESISTANCE, MobEffects.ABSORPTION
    );
    private static final List<MobEffect> NEGATIVE_EFFECTS = List.of(
            MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS, MobEffects.BLINDNESS, MobEffects.HUNGER
    );

    private final Map<UUID, TrackedEntity> trackedEntities = new ConcurrentHashMap<>();
    private final Map<UUID, Map<MobEffect, EffectSnapshot>> trackedEffects = new ConcurrentHashMap<>();
    private final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, WeatherSnapshot>
            trackedWeather = new ConcurrentHashMap<>();
    private final Queue<String> defeatedViewers = new ConcurrentLinkedQueue<>();
    private final Map<BlockChangeKey, BlockChange> blockChanges = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public ExecutionResult execute(MinecraftServer server, UUID playerId, ActionRequest request,
                                   TikTokChaosConfig.Safety safety) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return ExecutionResult.failure(tr("result.tiktokchaos.player_not_found"));
        ServerLevel level = player.getLevel();
        ActionSpec action = request.action();

        try {
            String result = switch (action.type) {
                case SPAWN_ENTITY -> spawn(level, player, request, action, safety);
                case GIVE_ITEM -> give(player, action);
                case APPLY_EFFECT -> applyEffect(player, action);
                case SHORT_TELEPORT -> teleport(level, player, action);
                case COSMETIC_LIGHTNING -> lightning(level, player, safety);
                case SET_WEATHER -> weather(level, action);
                case MESSAGE -> message(player, request, action);
                case RANDOM_SAFE_ITEM -> randomItem(player);
                case RANDOM_POSITIVE_EFFECT -> randomEffect(player, POSITIVE_EFFECTS, 200);
                case RANDOM_NEGATIVE_EFFECT -> randomEffect(player, NEGATIVE_EFFECTS, 160);
                case PLAY_SOUND -> playSound(level, player, action);
                case LAUNCH_PLAYER -> launch(player, action);
                case FREEZE_PLAYER -> freeze(player, action);
                case PARTICLE_BURST -> particles(level, player, action);
                case CENTER_MESSAGE -> centerMessage(player, request, action);
                case VISUAL_ITEM_RAIN -> itemRain(level, player, action, safety);
                case GIFT_CANNON -> giftCannon(level, player, action, safety);
                case LIKE_FOUNTAIN -> likeFountain(level, player, action);
                case SPAWN_VIEWER_BOSS -> spawnBoss(level, player, request, action, safety);
                case REVERSIBLE_BLOCK_BOX -> reversibleBox(level, player, safety);
                case MOD_INTEGRATION -> modIntegration(server, player, action);
            };
            sendActionMessage(player, Component.literal("[TikTok] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(request.event().userName() + " -> " + result)
                            .withStyle(ChatFormatting.WHITE)));
            return ExecutionResult.success(result);
        } catch (Exception error) {
            return ExecutionResult.failure(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
    }

    public void cleanup(MinecraftServer server, TikTokChaosConfig.Safety safety) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, TrackedEntity>> iterator = trackedEntities.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TrackedEntity> entry = iterator.next();
            TrackedEntity tracked = entry.getValue();
            ServerLevel level = server.getLevel(tracked.dimension());
            Entity entity = level == null ? null : level.getEntity(entry.getKey());
            if (entity != null && !tracked.viewerName().isBlank() && !entity.isAlive()) {
                defeatedViewers.offer(tracked.viewerName());
                trackedEntities.remove(entry.getKey(), tracked);
            } else if (entity == null || entity.isRemoved()) {
                trackedEntities.remove(entry.getKey(), tracked);
            } else if (now >= tracked.removeAt()) {
                entity.discard();
                trackedEntities.remove(entry.getKey(), tracked);
            }
        }
        restoreExpiredBlocks(server, now, false);
    }

    public int trackedCount() {
        return trackedEntities.size();
    }

    public List<String> drainDefeatedViewers() {
        List<String> result = new ArrayList<>();
        String viewer;
        while ((viewer = defeatedViewers.poll()) != null) result.add(viewer);
        return List.copyOf(result);
    }

    public boolean isTargetAvailable(ActionSpec action) {
        if (action == null || action.type == null) return false;
        if (ActionTargets.isRandom(action.target)) return true;
        ResourceLocation id = ResourceLocation.tryParse(action.target);
        return switch (action.type) {
            case SPAWN_ENTITY, SPAWN_VIEWER_BOSS -> id != null && Registry.ENTITY_TYPE.containsKey(id)
                    && ActionTargets.isAllowedEntity(Registry.ENTITY_TYPE.get(id));
            case GIVE_ITEM -> id != null && Registry.ITEM.containsKey(id) && Registry.ITEM.get(id) != Items.AIR;
            case APPLY_EFFECT -> id != null && Registry.MOB_EFFECT.containsKey(id);
            case PLAY_SOUND -> id != null && Registry.SOUND_EVENT.containsKey(id);
            case VISUAL_ITEM_RAIN, GIFT_CANNON -> action.target.isBlank()
                    || id != null && Registry.ITEM.containsKey(id);
            case MOD_INTEGRATION -> ModCompatibilityActions.isAvailable(action.target);
            default -> true;
        };
    }

    public void clearTracking() {
        trackedEntities.clear();
        trackedEffects.clear();
        trackedWeather.clear();
        defeatedViewers.clear();
        blockChanges.clear();
    }

    public void restoreAndRemove(MinecraftServer server) {
        for (Map.Entry<UUID, TrackedEntity> entry : new ArrayList<>(trackedEntities.entrySet())) {
            TrackedEntity tracked = entry.getValue();
            ServerLevel level = server.getLevel(tracked.dimension());
            Entity entity = level == null ? null : level.getEntity(entry.getKey());
            if (entity != null && !entity.isRemoved()) entity.discard();
        }
        trackedEntities.clear();

        for (Map.Entry<UUID, Map<MobEffect, EffectSnapshot>> playerEntry
                : new ArrayList<>(trackedEffects.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerEntry.getKey());
            if (player == null) continue;
            for (Map.Entry<MobEffect, EffectSnapshot> effectEntry : playerEntry.getValue().entrySet()) {
                player.removeEffect(effectEntry.getKey());
                EffectSnapshot snapshot = effectEntry.getValue();
                if (snapshot.previouslyActive()) {
                    player.addEffect(new MobEffectInstance(effectEntry.getKey(), snapshot.durationTicks(),
                            snapshot.amplifier()));
                }
            }
        }
        trackedEffects.clear();

        for (Map.Entry<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, WeatherSnapshot> entry
                : new ArrayList<>(trackedWeather.entrySet())) {
            ServerLevel level = server.getLevel(entry.getKey());
            if (level == null) continue;
            WeatherSnapshot snapshot = entry.getValue();
            level.setWeatherParameters(snapshot.raining() ? 0 : 20 * 60,
                    snapshot.raining() ? 20 * 60 : 0, snapshot.raining(), snapshot.thundering());
        }
        trackedWeather.clear();
        restoreExpiredBlocks(server, Long.MAX_VALUE, true);
    }

    private String spawn(ServerLevel level, ServerPlayer player, ActionRequest request, ActionSpec action,
                         TikTokChaosConfig.Safety safety) {
        ResourceLocation id = ResourceLocation.tryParse(action.target);
        EntityType<?> type = ActionTargets.isRandom(action.target)
                ? RandomActionTargets.entity(random)
                : id == null || !Registry.ENTITY_TYPE.containsKey(id) ? null : Registry.ENTITY_TYPE.get(id);
        if (type == null) throw new IllegalArgumentException(tr("result.tiktokchaos.unknown_entity"));
        if (!ActionTargets.isAllowedEntity(type)) {
            throw new IllegalArgumentException(tr("result.tiktokchaos.entity_not_allowed", action.target));
        }

        int room = Math.max(0, safety.maxTrackedMobs - trackedEntities.size());
        int count = Math.min(Math.max(1, action.amount), room);
        if (count == 0) return tr("result.tiktokchaos.mob_limit_reached");

        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos position = findSafePosition(level, player.blockPosition(), safety.minSpawnRadius,
                    safety.maxSpawnRadius);
            Entity entity = type.create(level);
            if (entity == null) continue;
            entity.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5,
                    random.nextFloat() * 360.0F, 0.0F);
            entity.setCustomName(Component.literal(request.event().userName()));
            if (level.addFreshEntity(entity)) {
                trackedEntities.put(entity.getUUID(), new TrackedEntity(level.dimension(),
                        System.currentTimeMillis() + safety.mobLifetimeSeconds * 1_000L,
                        request.event().userName(), false));
                spawned++;
            }
        }
        return tr(spawned == 1 ? "result.tiktokchaos.mob_spawned" : "result.tiktokchaos.mobs_spawned", spawned,
                type.getDescription().getString());
    }

    private String give(ServerPlayer player, ActionSpec action) {
        ResourceLocation id = ResourceLocation.tryParse(action.target);
        Item item = ActionTargets.isRandom(action.target)
                ? RandomActionTargets.item(random)
                : id == null ? Items.AIR : Registry.ITEM.get(id);
        if (item == Items.AIR) throw new IllegalArgumentException(tr("result.tiktokchaos.invalid_item", action.target));
        ItemStack stack = new ItemStack(item, Math.max(1, action.amount));
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        return stack.getCount() + "x " + stack.getHoverName().getString();
    }

    private String modIntegration(MinecraftServer server, ServerPlayer player, ActionSpec action) {
        if (!ModCompatibilityActions.isAvailable(action.target)) {
            throw new IllegalStateException(tr("result.tiktokchaos.mod_integration_unavailable", action.target));
        }
        server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                ModCompatibilityActions.command(action));
        return tr("result.tiktokchaos.mod_integration", ModCompatibilityActions.displayName(action.target));
    }

    private String applyEffect(ServerPlayer player, ActionSpec action) {
        MobEffect effect = ActionTargets.isRandom(action.target)
                ? RandomActionTargets.effect(random)
                : effectById(action.target);
        if (effect == null) throw new IllegalArgumentException(tr("result.tiktokchaos.invalid_effect", action.target));
        applyTrackedEffect(player, effect, Math.max(20, action.durationTicks), action.amplifier);
        ResourceLocation effectId = Registry.MOB_EFFECT.getKey(effect);
        return tr("result.tiktokchaos.effect", effectId == null ? effect.getDisplayName().getString()
                : effectId.getPath());
    }

    private String teleport(ServerLevel level, ServerPlayer player, ActionSpec action) {
        BlockPos position = findSafePosition(level, player.blockPosition(), 3, Math.max(3, action.radius));
        player.teleportTo(level, position.getX() + 0.5, position.getY(), position.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        return tr("result.tiktokchaos.short_teleport");
    }

    private String lightning(ServerLevel level, ServerPlayer player, TikTokChaosConfig.Safety safety) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return tr("result.tiktokchaos.lightning_unavailable");
        BlockPos position = findSafePosition(level, player.blockPosition(), safety.minSpawnRadius,
                safety.maxSpawnRadius);
        bolt.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
        return tr("result.tiktokchaos.cosmetic_lightning");
    }

    private String weather(ServerLevel level, ActionSpec action) {
        int duration = action.durationTicks > 0 ? action.durationTicks : 20 * 30;
        trackedWeather.putIfAbsent(level.dimension(), new WeatherSnapshot(level.isRaining(), level.isThundering()));
        level.setWeatherParameters(0, duration, true, false);
        return tr("result.tiktokchaos.temporary_rain");
    }

    private String message(ServerPlayer player, ActionRequest request, ActionSpec action) {
        String value = action.message.isBlank() ? request.ruleName() : action.message;
        player.displayClientMessage(Component.literal(value), false);
        return value;
    }

    private String randomItem(ServerPlayer player) {
        Item item = SAFE_RANDOM_ITEMS.get(random.nextInt(SAFE_RANDOM_ITEMS.size()));
        int amount = item == Items.TORCH || item == Items.ARROW ? 8 : 2;
        ItemStack stack = new ItemStack(item, amount);
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        return amount + "x " + stack.getHoverName().getString();
    }

    private String randomEffect(ServerPlayer player, List<MobEffect> effects, int ticks) {
        MobEffect effect = effects.get(random.nextInt(effects.size()));
        applyTrackedEffect(player, effect, ticks, 0);
        return tr("result.tiktokchaos.surprise_effect");
    }

    private String playSound(ServerLevel level, ServerPlayer player, ActionSpec action) {
        ResourceLocation id = ResourceLocation.tryParse(action.target);
        SoundEvent sound = id == null ? null : Registry.SOUND_EVENT.get(id);
        if (sound == null) throw new IllegalArgumentException(tr("result.tiktokchaos.invalid_sound", action.target));
        level.playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
        return tr("result.tiktokchaos.sound", id.getPath());
    }

    private String launch(ServerPlayer player, ActionSpec action) {
        double strength = Math.max(0.4, Math.min(2.0, action.amount * 0.2));
        player.setDeltaMovement(player.getDeltaMovement().add(0.0, strength, 0.0));
        player.hurtMarked = true;
        return tr("result.tiktokchaos.safe_launch");
    }

    private String freeze(ServerPlayer player, ActionSpec action) {
        int duration = action.durationTicks > 0 ? action.durationTicks : 100;
        applyTrackedEffect(player, MobEffects.MOVEMENT_SLOWDOWN, duration, Math.max(4, action.amplifier));
        return tr("result.tiktokchaos.frozen_for", Math.max(1, duration / 20));
    }

    private String particles(ServerLevel level, ServerPlayer player, ActionSpec action) {
        int count = Math.max(10, Math.min(100, action.amount * 10));
        level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 1.0, player.getZ(), count,
                0.8, 1.0, 0.8, 0.05);
        return tr("result.tiktokchaos.particle_burst");
    }

    private String centerMessage(ServerPlayer player, ActionRequest request, ActionSpec action) {
        String value = action.message.isBlank() ? request.ruleName() : action.message;
        player.displayClientMessage(Component.literal(value), true);
        return value;
    }

    private String itemRain(ServerLevel level, ServerPlayer player, ActionSpec action,
                            TikTokChaosConfig.Safety safety) {
        ResourceLocation id = ResourceLocation.tryParse(action.target);
        Item item = ActionTargets.isRandom(action.target) || action.target.isBlank()
                ? SAFE_RANDOM_ITEMS.get(random.nextInt(SAFE_RANDOM_ITEMS.size()))
                : id == null ? Items.AIR : Registry.ITEM.get(id);
        if (item == Items.AIR) throw new IllegalArgumentException("Item visual invalido: " + action.target);
        int count = Math.max(1, Math.min(12, action.amount));
        int spawned = 0;
        for (int index = 0; index < count && trackedEntities.size() < safety.maxTrackedMobs; index++) {
            ItemEntity entity = new ItemEntity(level, player.getX() + random.nextDouble() * 4 - 2,
                    player.getY() + 3 + random.nextDouble() * 2, player.getZ() + random.nextDouble() * 4 - 2,
                    new ItemStack(item, 1));
            entity.setPickUpDelay(32_767);
            entity.setDeltaMovement((random.nextDouble() - 0.5) * 0.12, 0.1, (random.nextDouble() - 0.5) * 0.12);
            if (level.addFreshEntity(entity)) {
                trackedEntities.put(entity.getUUID(), new TrackedEntity(level.dimension(),
                        System.currentTimeMillis() + 5_000L, "", false));
                spawned++;
            }
        }
        return tr("result.tiktokchaos.visual_item_rain", spawned);
    }

    private String giftCannon(ServerLevel level, ServerPlayer player, ActionSpec action,
                              TikTokChaosConfig.Safety safety) {
        String result = itemRain(level, player, action, safety);
        return tr("result.tiktokchaos.gift_cannon_legacy", result);
    }

    private String likeFountain(ServerLevel level, ServerPlayer player, ActionSpec action) {
        int count = Math.max(10, Math.min(100, action.amount * 10));
        level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 0.2, player.getZ(), count,
                1.2, 2.0, 1.2, 0.08);
        return tr("result.tiktokchaos.like_fountain", count);
    }

    private String spawnBoss(ServerLevel level, ServerPlayer player, ActionRequest request, ActionSpec action,
                             TikTokChaosConfig.Safety safety) {
        long bosses = trackedEntities.values().stream().filter(TrackedEntity::boss).count();
        if (bosses >= safety.maxViewerBosses) return tr("result.tiktokchaos.boss_limit_reached");
        ActionSpec single = action.copy();
        single.amount = 1;
        int before = trackedEntities.size();
        String result = spawn(level, player, request, single, safety);
        if (trackedEntities.size() > before) {
            trackedEntities.entrySet().stream()
                    .filter(entry -> entry.getValue().viewerName().equals(request.event().userName()))
                    .max(Map.Entry.comparingByKey())
                    .ifPresent(entry -> trackedEntities.put(entry.getKey(), new TrackedEntity(
                            entry.getValue().dimension(), entry.getValue().removeAt(),
                            entry.getValue().viewerName(), true)));
        }
        return tr("result.tiktokchaos.boss_legacy", result);
    }

    private String reversibleBox(ServerLevel level, ServerPlayer player, TikTokChaosConfig.Safety safety) {
        if (!safety.destructiveActionsEnabled || !safety.destructiveActionsConfirmed) {
            throw new IllegalStateException(tr("result.tiktokchaos.world_actions_confirmation"));
        }
        int radius = 3;
        int changed = 0;
        long restoreAt = System.currentTimeMillis() + safety.blockRestoreSeconds * 1_000L;
        BlockState replacement = Blocks.GLASS.defaultBlockState();
        BlockPos center = player.blockPosition();
        outer:
        for (int y = -1; y <= 3; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean shell = y == -1 || y == 3 || Math.abs(x) == radius || Math.abs(z) == radius;
                    if (!shell) continue;
                    BlockPos position = center.offset(x, y, z).immutable();
                    if (level.getBlockEntity(position) != null) continue;
                    if (!level.getEntities((Entity) null, new AABB(position), entity -> entity != player).isEmpty()) {
                        continue;
                    }
                    BlockState original = level.getBlockState(position);
                    if (original.getDestroySpeed(level, position) < 0 || original.is(replacement.getBlock())) continue;
                    BlockChangeKey key = new BlockChangeKey(level.dimension(), position);
                    blockChanges.compute(key, (ignored, existing) -> existing == null
                            ? new BlockChange(original, replacement, restoreAt)
                            : new BlockChange(existing.original(), replacement, Math.max(existing.restoreAt(), restoreAt)));
                    level.setBlock(position, replacement, 3);
                    if (++changed >= safety.maxChangedBlocks) break outer;
                }
            }
        }
        return tr("result.tiktokchaos.blocks_changed", changed);
    }

    private void restoreExpiredBlocks(MinecraftServer server, long now, boolean force) {
        for (Map.Entry<BlockChangeKey, BlockChange> entry : new ArrayList<>(blockChanges.entrySet())) {
            BlockChange change = entry.getValue();
            if (!force && now < change.restoreAt()) continue;
            ServerLevel level = server.getLevel(entry.getKey().dimension());
            if (level != null && level.getBlockState(entry.getKey().position()).equals(change.replacement())) {
                level.setBlock(entry.getKey().position(), change.original(), 3);
            }
            blockChanges.remove(entry.getKey(), change);
        }
    }

    private void applyTrackedEffect(ServerPlayer player, MobEffect effect, int ticks, int amplifier) {
        trackedEffects.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(effect, ignored -> {
                    MobEffectInstance current = player.getEffect(effect);
                    return current == null
                            ? new EffectSnapshot(false, 0, 0)
                            : new EffectSnapshot(true, current.getDuration(), current.getAmplifier());
                });
        player.addEffect(new MobEffectInstance(effect, ticks, amplifier));
    }

    private BlockPos findSafePosition(ServerLevel level, BlockPos origin, int minimumRadius, int maximumRadius) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int radius = random.nextInt(minimumRadius, maximumRadius + 1);
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * radius);
            BlockPos column = new BlockPos(x, origin.getY(), z);
            if (!level.hasChunkAt(column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (level.getWorldBorder().isWithinBounds(top) && level.getBlockState(top).isAir()
                    && level.getBlockState(top.above()).isAir()) {
                return top;
            }
        }
        return origin.offset(minimumRadius, 1, 0);
    }

    private MobEffect effectById(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !Registry.MOB_EFFECT.containsKey(key)) return null;
        return Registry.MOB_EFFECT.get(key);
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    /** Bridges the server chat rename between Minecraft 1.18 and 1.19. */
    private void sendActionMessage(ServerPlayer player, Component message) {
        for (Method method : player.getClass().getMethods()) {
            if (method.getName().equals("sendSystemMessage") && method.getParameterCount() == 1) {
                try {
                    method.invoke(player, message);
                    return;
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Falha ao enviar mensagem da acao", exception);
                }
            }
            if (method.getName().equals("sendMessage") && method.getParameterCount() == 2
                    && method.getParameterTypes()[0].isInstance(message)
                    && method.getParameterTypes()[1] == UUID.class) {
                try {
                    method.invoke(player, message, new UUID(0L, 0L));
                    return;
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Falha ao enviar mensagem da acao", exception);
                }
            }
        }
    }

    private record TrackedEntity(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                 long removeAt, String viewerName, boolean boss) {
    }

    private record BlockChangeKey(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                  BlockPos position) {
    }

    private record BlockChange(BlockState original, BlockState replacement, long restoreAt) {
    }

    private record EffectSnapshot(boolean previouslyActive, int durationTicks, int amplifier) {
    }

    private record WeatherSnapshot(boolean raining, boolean thundering) {
    }

    public record ExecutionResult(boolean success, String message) {
        public static ExecutionResult success(String message) {
            return new ExecutionResult(true, message);
        }

        public static ExecutionResult failure(String message) {
            return new ExecutionResult(false, message);
        }
    }
}
