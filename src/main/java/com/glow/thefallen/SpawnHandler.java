package com.glow.thefallen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the natural spawning of TheFallenEntity:
 * - 30 seconds after a player joins, spawn the entity near them (out of sight).
 * - Send a corrupted "§k████████ §rhas joined the world" chat message.
 * - Grant the "Stalked" advancement.
 */
public class SpawnHandler {

    private static final Map<UUID, Integer> playerJoinTicks = new HashMap<>();
    private static final int SPAWN_DELAY_TICKS = 600; // 30 seconds

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        for (ServerPlayer player : serverLevel.players()) {
            UUID uuid = player.getUUID();

            if (!playerJoinTicks.containsKey(uuid)) {
                playerJoinTicks.put(uuid, 0);
            }

            int ticks = playerJoinTicks.get(uuid);
            playerJoinTicks.put(uuid, ticks + 1);

            if (ticks == 20) { // 1 second after join, play eerie music
                serverLevel.playSound(null, player.blockPosition(), 
                        ModSounds.WORLD_ENTER.get(), 
                        net.minecraft.sounds.SoundSource.RECORDS, 3.0F, 1.0F);
            }

            if (ticks == SPAWN_DELAY_TICKS) {
                // Check if any TheFallenEntity exists in the entire level
                boolean alreadyExists = false;
                for (net.minecraft.world.entity.Entity e : serverLevel.getEntities().getAll()) {
                    if (e instanceof TheFallenEntity) {
                        alreadyExists = true;
                        break;
                    }
                }
                if (alreadyExists) continue;

                spawnEntity(serverLevel, player);
            }
        }
    }

    private static void spawnEntity(ServerLevel level, ServerPlayer player) {
        Vec3 lookAngle = player.getLookAngle();
        double angle = Math.atan2(lookAngle.z, lookAngle.x) + Math.PI;
        angle += (level.random.nextFloat() - 0.5) * Math.PI * 0.5;
        double dist = 40 + level.random.nextInt(21);

        double spawnX = player.getX() + Math.cos(angle) * dist;
        double spawnZ = player.getZ() + Math.sin(angle) * dist;

        BlockPos groundPos = level.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(spawnX, 0, spawnZ));

        TheFallenEntity entity = ModEntities.THE_FALLEN.get().create(level);
        if (entity != null) {
            // Spawn 1 block above the heightmap position to ensure we don't clip into the ground
            double spawnY = groundPos.getY() + 1.0;
            entity.moveTo(groundPos.getX() + 0.5, spawnY, groundPos.getZ() + 0.5, 0, 0);
            entity.setState(TheFallenEntity.STATE_OBSERVING);
            level.addFreshEntity(entity);

            for (ServerPlayer p : level.players()) {
                p.sendSystemMessage(
                        Component.literal("§k████████ §r§7has joined the world"));
                p.displayClientMessage(
                        Component.literal("§kHE IS WATCHING"), true);
                
                var advancement = level.getServer().getAdvancements().get(
                        ResourceLocation.fromNamespaceAndPath(TheFallenMod.MODID, "stalked"));
                if (advancement != null) {
                    p.getAdvancements().award(advancement, "stalked_by_mod");
                }
            }
        }
    }

    public static void onPlayerLeave(UUID uuid) {
        playerJoinTicks.remove(uuid);
    }
}
