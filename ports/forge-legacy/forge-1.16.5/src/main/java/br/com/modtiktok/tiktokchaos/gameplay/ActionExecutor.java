package br.com.modtiktok.tiktokchaos.gameplay;

import br.com.modtiktok.tiktokchaos.config.TikTokChaosConfig;
import br.com.modtiktok.tiktokchaos.rule.ActionRequest;
import br.com.modtiktok.tiktokchaos.rule.ActionSpec;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.LightningBoltEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Effect;
import net.minecraft.potion.EffectInstance;
import net.minecraft.potion.Effects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerWorld;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ActionExecutor {
    private static final List<Item> SAFE_RANDOM_ITEMS = List.of(
            Items.BREAD, Items.COOKED_BEEF, Items.BAKED_POTATO, Items.TORCH, Items.ARROW, Items.IRON_INGOT
    );
    private static final List<Effect> POSITIVE_EFFECTS = List.of(
            Effects.REGENERATION, Effects.MOVEMENT_SPEED, Effects.DAMAGE_RESISTANCE, Effects.ABSORPTION
    );
    private static final List<Effect> NEGATIVE_EFFECTS = List.of(
            Effects.MOVEMENT_SLOWDOWN, Effects.WEAKNESS, Effects.BLINDNESS, Effects.HUNGER
    );

    private final Map<UUID, TrackedEntity> trackedEntities = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public ExecutionResult execute(MinecraftServer server, UUID playerId, ActionRequest request,
                                   TikTokChaosConfig.Safety safety) {
        ServerPlayerEntity player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return ExecutionResult.failure("Jogador nao encontrado");
        ServerWorld level = player.getLevel();
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
            player.sendMessage(new StringTextComponent("[TikTok] ").withStyle(TextFormatting.LIGHT_PURPLE)
                    .append(new StringTextComponent(request.event().userName() + " -> " + result)
                            .withStyle(TextFormatting.WHITE)), Util.NIL_UUID);
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
            ServerWorld level = server.getLevel(tracked.dimension());
            Entity entity = level == null ? null : level.getEntity(entry.getKey());
            if (entity == null || entity.removed) {
                trackedEntities.remove(entry.getKey(), tracked);
            } else if (now >= tracked.removeAt()) {
                entity.remove();
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

    private String spawn(ServerWorld level, ServerPlayerEntity player, ActionRequest request, ActionSpec action,
                         TikTokChaosConfig.Safety safety) {
        ResourceLocation id = ResourceLocation.tryParse(action.target);
        EntityType<?> type = ActionTargets.isRandom(action.target)
                ? RandomActionTargets.entity(random)
                : id == null || !Registry.ENTITY_TYPE.containsKey(id) ? null : Registry.ENTITY_TYPE.get(id);
        if (type == null) throw new IllegalArgumentException("Entidade desconhecida");
        if (!ActionTargets.isAllowedEntity(type)) throw new IllegalArgumentException("Entidade nao permitida");

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
            entity.setCustomName(new StringTextComponent(request.event().userName()));
            if (level.addFreshEntity(entity)) {
                trackedEntities.put(entity.getUUID(), new TrackedEntity(level.dimension(),
                        System.currentTimeMillis() + safety.mobLifetimeSeconds * 1_000L));
                spawned++;
            }
        }
        return spawned + (spawned == 1 ? " mob invocado: " : " mobs invocados: ")
                + type.getDescription().getString();
    }

    private String give(ServerPlayerEntity player, ActionSpec action) {
        ResourceLocation id = ResourceLocation.tryParse(action.target);
        Item item = ActionTargets.isRandom(action.target) ? RandomActionTargets.item(random)
                : id == null ? Items.AIR : Registry.ITEM.get(id);
        if (item == Items.AIR) throw new IllegalArgumentException("Item invalido: " + action.target);
        ItemStack stack = new ItemStack(item, Math.max(1, action.amount));
        if (!player.inventory.add(stack)) player.drop(stack, false);
        return stack.getCount() + "x " + stack.getHoverName().getString();
    }

    private String applyEffect(ServerPlayerEntity player, ActionSpec action) {
        Effect effect = ActionTargets.isRandom(action.target) ? RandomActionTargets.effect(random)
                : effectById(action.target);
        if (effect == null) throw new IllegalArgumentException("Efeito invalido: " + action.target);
        player.addEffect(new EffectInstance(effect, Math.max(20, action.durationTicks), action.amplifier));
        ResourceLocation effectId = Registry.MOB_EFFECT.getKey(effect);
        return "efeito " + (effectId == null ? effect.getDisplayName().getString() : effectId.getPath());
    }

    private String teleport(ServerWorld level, ServerPlayerEntity player, ActionSpec action) {
        BlockPos position = findSafePosition(level, player.blockPosition(), 3, Math.max(3, action.radius));
        player.teleportTo(level, position.getX() + 0.5, position.getY(), position.getZ() + 0.5,
                player.yRot, player.xRot);
        return "teleporte curto";
    }

    private String lightning(ServerWorld level, ServerPlayerEntity player, TikTokChaosConfig.Safety safety) {
        LightningBoltEntity bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return "raio indisponivel";
        BlockPos position = findSafePosition(level, player.blockPosition(), safety.minSpawnRadius,
                safety.maxSpawnRadius);
        bolt.moveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
        return "raio cosmetico";
    }

    private String weather(ServerWorld level, ActionSpec action) {
        int duration = action.durationTicks > 0 ? action.durationTicks : 20 * 30;
        level.setWeatherParameters(0, duration, true, false);
        return "chuva temporaria";
    }

    private String message(ServerPlayerEntity player, ActionRequest request, ActionSpec action) {
        String value = action.message.isBlank() ? request.ruleName() : action.message;
        player.displayClientMessage(new StringTextComponent(value), false);
        return value;
    }

    private String randomItem(ServerPlayerEntity player) {
        Item item = SAFE_RANDOM_ITEMS.get(random.nextInt(SAFE_RANDOM_ITEMS.size()));
        int amount = item == Items.TORCH || item == Items.ARROW ? 8 : 2;
        ItemStack stack = new ItemStack(item, amount);
        if (!player.inventory.add(stack)) player.drop(stack, false);
        return amount + "x " + stack.getHoverName().getString();
    }

    private String randomEffect(ServerPlayerEntity player, List<Effect> effects, int ticks) {
        Effect effect = effects.get(random.nextInt(effects.size()));
        player.addEffect(new EffectInstance(effect, ticks, 0));
        return "efeito surpresa";
    }

    private BlockPos findSafePosition(ServerWorld level, BlockPos origin, int minimumRadius, int maximumRadius) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int radius = minimumRadius + random.nextInt(Math.max(1, maximumRadius - minimumRadius + 1));
            int x = origin.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = origin.getZ() + (int) Math.round(Math.sin(angle) * radius);
            BlockPos column = new BlockPos(x, origin.getY(), z);
            if (!level.hasChunkAt(column)) continue;
            BlockPos top = level.getHeightmapPos(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, column);
            if (level.getWorldBorder().isWithinBounds(top) && level.getBlockState(top).isAir()
                    && level.getBlockState(top.above()).isAir()) return top;
        }
        return origin.offset(minimumRadius, 1, 0);
    }

    private Effect effectById(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !Registry.MOB_EFFECT.containsKey(key)) return null;
        return Registry.MOB_EFFECT.get(key);
    }

    private record TrackedEntity(net.minecraft.util.RegistryKey<World> dimension, long removeAt) {
    }

    public record ExecutionResult(boolean success, String message) {
        public static ExecutionResult success(String message) { return new ExecutionResult(true, message); }
        public static ExecutionResult failure(String message) { return new ExecutionResult(false, message); }
    }
}
