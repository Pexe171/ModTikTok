package br.com.modtiktok.tiktokchaos.gameplay;

import br.com.modtiktok.tiktokchaos.config.TikTokChaosConfig;
import br.com.modtiktok.tiktokchaos.rule.ActionRequest;
import br.com.modtiktok.tiktokchaos.rule.ActionSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class ActionExecutor {
    private static final List<Item> SAFE_RANDOM_ITEMS = List.of(
            Items.BREAD, Items.COOKED_BEEF, Items.BAKED_POTATO, Items.TORCH, Items.ARROW, Items.IRON_INGOT
    );
    private static final List<Object> POSITIVE_EFFECTS = List.of(
            MobEffects.REGENERATION, MobEffects.MOVEMENT_SPEED, MobEffects.DAMAGE_RESISTANCE, MobEffects.ABSORPTION
    );
    private static final List<Object> NEGATIVE_EFFECTS = List.of(
            MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS, MobEffects.BLINDNESS, MobEffects.HUNGER
    );

    private final Map<UUID, TrackedEntity> trackedEntities = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, EffectSnapshot>> trackedEffects = new ConcurrentHashMap<>();
    private final Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>, WeatherSnapshot>
            trackedWeather = new ConcurrentHashMap<>();
    private final Queue<String> defeatedViewers = new ConcurrentLinkedQueue<>();
    private final Map<BlockChangeKey, BlockChange> blockChanges = new ConcurrentHashMap<>();
    // Avoid RandomGenerator.getDefault(): some modular Minecraft runtimes do not expose
    // the jdk.random provider selected by that factory during early mod construction.
    private final Random random = new Random();

    public ExecutionResult execute(MinecraftServer server, UUID playerId, ActionRequest request,
                                   TikTokChaosConfig.Safety safety) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return ExecutionResult.failure(tr("result.tiktokchaos.player_not_found"));
        ServerLevel level = player.serverLevel();
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
            };
            player.sendSystemMessage(Component.literal("[TikTok] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(request.event().userName() + " → " + result)
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
            case SPAWN_ENTITY, SPAWN_VIEWER_BOSS -> id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                    && ActionTargets.isAllowedEntity(BuiltInRegistries.ENTITY_TYPE.get(id));
            case GIVE_ITEM -> id != null && BuiltInRegistries.ITEM.containsKey(id)
                    && BuiltInRegistries.ITEM.get(id) != Items.AIR;
            case APPLY_EFFECT -> id != null && BuiltInRegistries.MOB_EFFECT.containsKey(id);
            case PLAY_SOUND -> id != null && BuiltInRegistries.SOUND_EVENT.containsKey(id);
            case VISUAL_ITEM_RAIN, GIFT_CANNON -> action.target.isBlank()
                    || id != null && BuiltInRegistries.ITEM.containsKey(id);
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

    /** Removes every temporary object owned by the mod and restores state changed by it. */
    public void restoreAndRemove(MinecraftServer server) {
        for (Map.Entry<UUID, TrackedEntity> entry : new ArrayList<>(trackedEntities.entrySet())) {
            TrackedEntity tracked = entry.getValue();
            ServerLevel level = server.getLevel(tracked.dimension());
            Entity entity = level == null ? null : level.getEntity(entry.getKey());
            if (entity != null && !entity.isRemoved()) entity.discard();
        }
        trackedEntities.clear();

        for (Map.Entry<UUID, Map<String, EffectSnapshot>> playerEntry
                : new ArrayList<>(trackedEffects.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerEntry.getKey());
            if (player == null) continue;
            for (EffectSnapshot snapshot : playerEntry.getValue().values()) {
                removeEffect(player, snapshot.effectReference());
                if (snapshot.previouslyActive()) {
                    player.addEffect(createEffectInstance(snapshot.effectReference(), snapshot.durationTicks(),
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
            int clearTicks = snapshot.raining() ? 0 : 20 * 60;
            int rainTicks = snapshot.raining() ? 20 * 60 : 0;
            level.setWeatherParameters(clearTicks, rainTicks, snapshot.raining(), snapshot.thundering());
        }
        trackedWeather.clear();
        restoreExpiredBlocks(server, Long.MAX_VALUE, true);
    }

    private String spawn(ServerLevel level, ServerPlayer player, ActionRequest request, ActionSpec action,
                         TikTokChaosConfig.Safety safety) {
        ResourceLocation id = ResourceLocation.tryParse(action.target);
        EntityType<?> type = ActionTargets.isRandom(action.target)
                ? RandomActionTargets.entity(random)
                : id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                    ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
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
                : id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) throw new IllegalArgumentException(tr("result.tiktokchaos.invalid_item", action.target));
        ItemStack stack = new ItemStack(item, Math.max(1, action.amount));
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        return stack.getCount() + "x " + stack.getHoverName().getString();
    }

    private String applyEffect(ServerPlayer player, ActionSpec action) {
        Object effect = ActionTargets.isRandom(action.target)
                ? RandomActionTargets.effect(random)
                : effectById(action.target);
        if (effect == null) throw new IllegalArgumentException(tr("result.tiktokchaos.invalid_effect", action.target));
        applyTrackedEffect(player, effect, Math.max(20, action.durationTicks), action.amplifier);
        MobEffect effectValue = unwrapEffect(effect);
        ResourceLocation effectId = BuiltInRegistries.MOB_EFFECT.getKey(effectValue);
        return tr("result.tiktokchaos.effect", effectId == null ? effectValue.getDisplayName().getString()
                : effectId.getPath());
    }

    private String teleport(ServerLevel level, ServerPlayer player, ActionSpec action) {
        BlockPos position = findSafePosition(level, player.blockPosition(), 3, Math.max(3, action.radius));
        player.teleportTo(level, position.getX() + 0.5, position.getY(), position.getZ() + 0.5,
                Set.of(), player.getYRot(), player.getXRot());
        return tr("result.tiktokchaos.short_teleport");
    }

    private String lightning(ServerLevel level, ServerPlayer player, TikTokChaosConfig.Safety safety) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return tr("result.tiktokchaos.lightning_unavailable");
        BlockPos position = findSafePosition(level, player.blockPosition(), safety.minSpawnRadius,
                safety.maxSpawnRadius);
        bolt.moveTo(position.getCenter());
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

    private String randomEffect(ServerPlayer player, List<Object> effects, int ticks) {
        Object effect = effects.get(random.nextInt(effects.size()));
        applyTrackedEffect(player, effect, ticks, 0);
        return tr("result.tiktokchaos.surprise_effect");
    }

    private String playSound(ServerLevel level, ServerPlayer player, ActionSpec action) {
        ResourceLocation id = ResourceLocation.tryParse(action.target);
        SoundEvent sound = id == null ? null : BuiltInRegistries.SOUND_EVENT.get(id);
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
                : id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalArgumentException(tr("result.tiktokchaos.invalid_visual_item", action.target));
        }
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
        ResourceLocation id = ResourceLocation.tryParse(action.target);
        Item item = ActionTargets.isRandom(action.target) || action.target.isBlank()
                ? SAFE_RANDOM_ITEMS.get(random.nextInt(SAFE_RANDOM_ITEMS.size()))
                : id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            throw new IllegalArgumentException(tr("result.tiktokchaos.invalid_visual_item", action.target));
        }
        int count = Math.max(1, Math.min(12, action.amount));
        int spawned = 0;
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        for (int index = 0; index < count && trackedEntities.size() < safety.maxTrackedMobs; index++) {
            ItemEntity entity = new ItemEntity(level, player.getX(), player.getEyeY(), player.getZ(),
                    new ItemStack(item, 1));
            entity.setPickUpDelay(32_767);
            entity.setDeltaMovement(look.x * 0.65 + (random.nextDouble() - 0.5) * 0.15,
                    look.y * 0.65 + 0.2, look.z * 0.65 + (random.nextDouble() - 0.5) * 0.15);
            if (level.addFreshEntity(entity)) {
                trackedEntities.put(entity.getUUID(), new TrackedEntity(level.dimension(),
                        System.currentTimeMillis() + 5_000L, "", false));
                spawned++;
            }
        }
        return tr("result.tiktokchaos.gift_cannon", spawned);
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
        ResourceLocation id = ResourceLocation.tryParse(action.target);
        EntityType<?> type = ActionTargets.isRandom(action.target)
                ? RandomActionTargets.entity(random)
                : id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
        if (type == null || !ActionTargets.isAllowedEntity(type)) {
            throw new IllegalArgumentException(tr("result.tiktokchaos.unknown_boss"));
        }
        Entity entity = type.create(level);
        if (!(entity instanceof LivingEntity living)) {
            throw new IllegalArgumentException(tr("result.tiktokchaos.invalid_boss_type"));
        }
        BlockPos position = findSafePosition(level, player.blockPosition(), safety.minSpawnRadius,
                safety.maxSpawnRadius);
        living.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5,
                random.nextFloat() * 360.0F, 0.0F);
        living.setCustomName(Component.literal(request.event().userName() + " [BOSS]"));
        living.setCustomNameVisible(true);
        var maxHealth = living.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(Math.min(200.0, Math.max(40.0, maxHealth.getBaseValue() * 3.0)));
            living.setHealth(living.getMaxHealth());
        }
        if (!level.addFreshEntity(living)) return tr("result.tiktokchaos.boss_spawn_failed");
        trackedEntities.put(living.getUUID(), new TrackedEntity(level.dimension(),
                System.currentTimeMillis() + safety.mobLifetimeSeconds * 1_000L,
                request.event().userName(), true));
        return tr("result.tiktokchaos.boss_spawned", request.event().userName());
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

    private void applyTrackedEffect(ServerPlayer player, Object effectReference, int ticks, int amplifier) {
        MobEffect effect = unwrapEffect(effectReference);
        ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
        String key = id == null ? effect.getDescriptionId() : id.toString();
        trackedEffects.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(key, ignored -> snapshotEffect(player, effectReference));
        player.addEffect(createEffectInstance(effectReference, ticks, amplifier));
    }

    private EffectSnapshot snapshotEffect(ServerPlayer player, Object effectReference) {
        MobEffectInstance current = findEffect(player, effectReference);
        if (current == null) return new EffectSnapshot(effectReference, false, 0, 0);
        return new EffectSnapshot(effectReference, true, current.getDuration(), current.getAmplifier());
    }

    private MobEffectInstance findEffect(ServerPlayer player, Object effectReference) {
        MobEffect effect = unwrapEffect(effectReference);
        for (Method method : player.getClass().getMethods()) {
            if (!method.getName().equals("getEffect") || method.getParameterCount() != 1) continue;
            Object argument = compatibleEffectArgument(method.getParameterTypes()[0], effectReference, effect);
            if (argument == null) continue;
            try {
                Object value = method.invoke(player, argument);
                return value instanceof MobEffectInstance instance ? instance : null;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Não foi possível consultar o efeito", exception);
            }
        }
        return null;
    }

    private void removeEffect(ServerPlayer player, Object effectReference) {
        MobEffect effect = unwrapEffect(effectReference);
        for (Method method : player.getClass().getMethods()) {
            if (!method.getName().equals("removeEffect") || method.getParameterCount() != 1) continue;
            Object argument = compatibleEffectArgument(method.getParameterTypes()[0], effectReference, effect);
            if (argument == null) continue;
            try {
                method.invoke(player, argument);
                return;
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Não foi possível remover o efeito", exception);
            }
        }
    }

    private Object compatibleEffectArgument(Class<?> parameter, Object effectReference, MobEffect effect) {
        if (parameter.isInstance(effectReference)) return effectReference;
        return parameter.isInstance(effect) ? effect : null;
    }

    private MobEffectInstance createEffectInstance(Object effectReference, int ticks, int amplifier) {
        MobEffect effect = unwrapEffect(effectReference);
        for (Constructor<?> constructor : MobEffectInstance.class.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length < 3 || parameters[1] != int.class || parameters[2] != int.class) continue;
            Object argument = parameters[0].isInstance(effectReference) ? effectReference
                    : parameters[0].isInstance(effect) ? effect : null;
            if (argument == null) continue;
            try {
                return (MobEffectInstance) constructor.newInstance(argument, ticks, amplifier);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Não foi possível criar o efeito", exception);
            }
        }
        throw new IllegalStateException("API de efeitos não reconhecida");
    }

    @SuppressWarnings("unchecked")
    private MobEffect unwrapEffect(Object effectReference) {
        if (effectReference instanceof net.minecraft.core.Holder<?> holder) {
            return ((net.minecraft.core.Holder<MobEffect>) holder).value();
        }
        return (MobEffect) effectReference;
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

    private net.minecraft.core.Holder<MobEffect> effectById(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.MOB_EFFECT.containsKey(key)) return null;
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(key);
        return effect == null ? null : BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
    }

    private static String tr(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }

    private record TrackedEntity(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                 long removeAt, String viewerName, boolean boss) {
    }

    private record BlockChangeKey(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                  BlockPos position) {
    }

    private record BlockChange(BlockState original, BlockState replacement, long restoreAt) {
    }

    private record EffectSnapshot(Object effectReference, boolean previouslyActive, int durationTicks,
                                  int amplifier) {
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
