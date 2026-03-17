package com.glow.thefallen;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.List;
import java.util.Random;

/**
 * Handles the day/night lifecycle for TheFallenEntity.
 *
 * Original guide:
 * - Sunset: Despawn Observer, spawn Fallen 40 blocks away, send §k messages.
 * - Sunrise: Fallen screams, emits smoke, despawns → Observer resumes.
 *
 * New spec:
 * - Single entity flips state at dayTime == 13000.
 * - Texture swap, goal reload, §k action bar messages.
 * - Sunrise: reset to OBSERVING, normalize gravity.
 *
 * Also handles the Gravity Well (Phase 5):
 * - Every 30s, if player can see sky, apply levitation for 3s.
 */
public class TransitionHandler {
    private static boolean isNightState = false;
    private static int gravityWellCooldown = 0;

    private static final Random RANDOM = new Random();
    private static final String[] SUNSET_MESSAGES = {
            "§kRUN §rTHE SKY IS FALLING",
            "§kHE IS HERE §rUP IS DOWN",
            "§kLOOK UP §rIT IS COMING",
            "§kGRAVITY §rBREAKS"
    };

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) return;

        boolean isNight = level.isNight(); // Helper method works perfectly for 13000 to 23000

        // --- Sunset Transition ---
        if (isNight && !isNightState) {
            triggerSunset(serverLevel);
            isNightState = true;
        }

        // --- Sunrise Reset ---
        if (!isNight && isNightState) {
            triggerSunrise(serverLevel);
            isNightState = false;
        }

        // --- Gravity Well (during night, every 30s / 600 ticks) ---
        if (!level.isDay() && gravityWellCooldown <= 0) {
            applyGravityWell(serverLevel);
            gravityWellCooldown = 600;
        }
        if (gravityWellCooldown > 0) gravityWellCooldown--;
    }

    /**
     * Sunset: Switch all TheFallenEntity instances to HUNTING state.
     * Broadcast §k messages to all players.
     */
    private static void triggerSunset(ServerLevel level) {
        // Send §k messages to all players
        for (ServerPlayer player : level.players()) {
            for (int i = 0; i < 3 + RANDOM.nextInt(2); i++) {
                String msg = SUNSET_MESSAGES[RANDOM.nextInt(SUNSET_MESSAGES.length)];
                player.displayClientMessage(Component.literal(msg), true);
            }
            
            // Flip entities near this player to HUNTING safely
            List<TheFallenEntity> entities = level.getEntitiesOfClass(TheFallenEntity.class,
                    player.getBoundingBox().inflate(500.0D));
            for (TheFallenEntity entity : entities) {
                entity.setState(TheFallenEntity.STATE_HUNTING);
            }
        }
    }

    /**
     * Sunrise: Reset all TheFallenEntity instances to OBSERVING state.
     * Emit smoke particles and play scream sound (from original guide's victory condition).
     */
    private static void triggerSunrise(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            List<TheFallenEntity> entities = level.getEntitiesOfClass(TheFallenEntity.class,
                    player.getBoundingBox().inflate(500.0D));

            for (TheFallenEntity entity : entities) {
                // Scream + smoke particles (original guide victory condition)
                level.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                        entity.getX(), entity.getY(), entity.getZ(),
                        30, 0.5, 0.5, 0.5, 0.05);

                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        net.minecraft.sounds.SoundEvents.GHAST_SCREAM, net.minecraft.sounds.SoundSource.HOSTILE,
                        2.0F, 2.0F);

                // Reset to Observer
                entity.setState(TheFallenEntity.STATE_OBSERVING);
            }

            // Normalize player gravity
            player.removeEffect(MobEffects.LEVITATION);
        }
    }

    /**
     * Gravity Well: If a player can see the sky, apply inverse gravity (levitation) for 3 seconds.
     * From Phase 5 of both guides.
     */
    private static void applyGravityWell(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            List<TheFallenEntity> entities = level.getEntitiesOfClass(TheFallenEntity.class,
                    player.getBoundingBox().inflate(200.0D));

            if (entities.isEmpty()) continue;

            if (level.canSeeSky(player.blockPosition())) {
                // Levitation for 3 seconds (60 ticks), amplifier 2
                player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 60, 2));

                // Action bar warning
                player.displayClientMessage(
                        Component.literal("§kGRAVITY §rFAILURE"), true);
            }
        }
    }
}
