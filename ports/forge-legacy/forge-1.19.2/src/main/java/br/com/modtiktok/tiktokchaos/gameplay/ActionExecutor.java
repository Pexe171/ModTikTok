package br.com.modtiktok.tiktokchaos.gameplay;

import br.com.modtiktok.tiktokchaos.config.TikTokChaosConfig;
import br.com.modtiktok.tiktokchaos.rule.ActionRequest;
import br.com.modtiktok.tiktokchaos.rule.ActionSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Random random = new Random();

    public ExecutionResult execute(MinecraftServer server, UUID playerId, ActionRequest request,
                                   TikTokChaosConfig.Safety safety) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return ExecutionResult.failure("Jogador nao encontrado");
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
            if (entity == null || entity.isRemoved()) {
                trackedEntities.remove(entry.getKey(), tracked);
            } else if (now >= tracked.removeAt()) {
                entity.discard();
                trackedEntities.remove(entry.getKey(), tracked);
            }
        }
    }

    public int trackedCount() {
        return trackedEntities.size();
    }

    public void clearTracking() {
        trackedEntities.clear();
    }

    private String spawn(ServerLevel level, ServerPlayer player, ActionRequest request, ActionSpec action,
                         TikTokChaosConfig.Safety safety) {
        ResourceLocation id = ResourceLocation.tryParse(action.target);
        EntityType<?> type = ActionTargets.isRandom(action.target)
                ? RandomActionTargets.entity(random)
                : id == null || !Registry.ENTITY_TYPE.containsKey(id) ? null : Registry.ENTITY_TYPE.get(id);
        if (type == null) throw new IllegalArgumentException("Entidade desconhecida");
        if (!ActionTargets.isAllowedEntity(type)) {
            throw new IllegalArgumentException("Entidade nao permitida: " + action.target);
        }

        int room = Math.max(0, safety.maxTrackedMobs - trackedEntities.size());
        int count = Math.min(Math.max(1, action.amount), room);
        if (count == 0) return "limite de mobs atingido";

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
                        System.currentTimeMillis() + safety.mobLifetimeSeconds * 1_000L));
                spawned++;
            }
        }
        return spawned + (spawned == 1 ? " mob invocado: " : " mobs invocados: ")
                + type.getDescription().getString();
    }

    private String give(ServerPlayer player, ActionSpec action) {
        ResourceLocation id = ResourceLocation.tryParse(action.target);
        Item item = ActionTargets.isRandom(action.target)
                ? RandomActionTargets.item(random)
                : id == null ? Items.AIR : Registry.ITEM.get(id);
        if (item == Items.AIR) throw new IllegalArgumentException("Item invalido: " + action.target);
        ItemStack stack = new ItemStack(item, Math.max(1, action.amount));
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        return stack.getCount() + "x " + stack.getHoverName().getString();
    }

    private String applyEffect(ServerPlayer player, ActionSpec action) {
        MobEffect effect = ActionTargets.isRandom(action.target)
                ? RandomActionTargets.effect(random)
                : effectById(action.target);
        if (effect == null) throw new IllegalArgumentException("Efeito invalido: " + action.target);
        player.addEffect(new MobEffectInstance(effect, Math.max(20, action.durationTicks), action.amplifier));
        ResourceLocation effectId = Registry.MOB_EFFECT.getKey(effect);
        return "efeito " + (effectId == null ? effect.getDisplayName().getString() : effectId.getPath());
    }

    private String teleport(ServerLevel level, ServerPlayer player, ActionSpec action) {
        BlockPos position = findSafePosition(level, player.blockPosition(), 3, Math.max(3, action.radius));
        player.teleportTo(level, position.getX() + 0.5, position.getY(), position.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        return "teleporte curto";
    }

    private String lightning(ServerLevel level, ServerPlayer player, TikTokChaosConfig.Safety safety) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return "raio indisponivel";
        BlockPos position = findSafePosition(level, player.blockPosition(), safety.minSpawnRadius,
                safety.maxSpawnRadius);
        bolt.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
        return "raio cosmetico";
    }

    private String weather(ServerLevel level, ActionSpec action) {
        int duration = action.durationTicks > 0 ? action.durationTicks : 20 * 30;
        level.setWeatherParameters(0, duration, true, false);
        return "chuva temporaria";
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
        player.addEffect(new MobEffectInstance(effect, ticks, 0));
        return "efeito surpresa";
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
                                 long removeAt) {
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
