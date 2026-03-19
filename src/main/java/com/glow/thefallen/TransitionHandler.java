package com.glow.thefallen;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Random;

/**
 * Handles global day/night transitions for The Fallen mod.
 */
public class TransitionHandler {
    private static final Random RANDOM = new Random();
    private static final String[] SUNSET_MESSAGES = {
            "§kTHE NIGHT BEGINS", "§kRUN", "§kHE IS COMING", "§kDON'T LOOK BACK"
    };

    private static boolean wasDay = true;

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            // Only process day/night cycle for the Overworld to prevent dimension race conditions
            if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
                return;
            }

            boolean isDay = level.isDay();
            
            if (wasDay && !isDay && level.getDayTime() % 24000 > 12000) {
                triggerSunset(level);
                wasDay = false;
            } else if (!wasDay && isDay) {
                triggerSunrise(level);
                wasDay = true;
            }
        }
    }

    private static void triggerSunset(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            for (int i = 0; i < 3 + RANDOM.nextInt(2); i++) {
                String msg = SUNSET_MESSAGES[RANDOM.nextInt(SUNSET_MESSAGES.length)];
                player.displayClientMessage(Component.literal(msg), true);
            }
        }
        
        for (Entity e : level.getAllEntities()) {
            if (e instanceof TheFallenEntity entity) {
                entity.setState(TheFallenEntity.STATE_HUNTING);
            }
        }
    }

    private static void triggerSunrise(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            player.removeEffect(MobEffects.LEVITATION);
        }
        
        for (Entity e : level.getAllEntities()) {
            if (e instanceof TheFallenEntity entity) {
                level.sendParticles(
                        ParticleTypes.LARGE_SMOKE,
                        entity.getX(), entity.getY(), entity.getZ(),
                        30, 0.5, 0.5, 0.5, 0.05);

                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                        ModSounds.SUNRISE_VANISH.get(), SoundSource.HOSTILE,
                        2.0F, 2.0F);

                entity.setState(TheFallenEntity.STATE_OBSERVING);
            }
        }
    }
}
