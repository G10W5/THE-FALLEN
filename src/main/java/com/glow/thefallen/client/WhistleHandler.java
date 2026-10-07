package com.glow.thefallen.client;

import com.glow.thefallen.ModSounds;
import com.glow.thefallen.TheFallenEntity;
import com.glow.thefallen.TheFallenMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side whistle loop manager.
 *
 * The server only raises a synced flag while the glitch bait runs; this
 * handler plays one looping whistle per flagged entity, positioned at the
 * entity (so it stays directional), and stops it the instant the flag
 * drops, the entity vanishes/dies/unloads, or the player leaves range.
 * No more ghost whistling after he disappears.
 */
@EventBusSubscriber(modid = TheFallenMod.MODID, value = Dist.CLIENT)
public class WhistleHandler {

    private static final double RANGE = 64.0D;
    private static final Map<Integer, WhistleSound> ACTIVE = new HashMap<>();
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) return;

        tickCounter++;
        if (tickCounter % 5 != 0) return;

        AABB search = mc.player.getBoundingBox().inflate(RANGE);
        List<TheFallenEntity> entities = mc.level.getEntitiesOfClass(TheFallenEntity.class, search);

        for (TheFallenEntity entity : entities) {
            boolean want = entity.isAlive() && !entity.isRemoved()
                    && !entity.isInvisible() && entity.isWhistling();
            WhistleSound sound = ACTIVE.get(entity.getId());
            if (want && (sound == null || sound.isStopped())) {
                sound = new WhistleSound(entity);
                mc.getSoundManager().play(sound);
                ACTIVE.put(entity.getId(), sound);
            } else if (!want && sound != null) {
                mc.getSoundManager().stop(sound);
                ACTIVE.remove(entity.getId());
            }
        }

        // Drop entries for unloaded entities or finished sounds
        ACTIVE.entrySet().removeIf(entry ->
                mc.level.getEntity(entry.getKey()) == null || entry.getValue().isStopped());
    }

    /** Looping whistle pinned to the entity. Killed via the sound manager. */
    public static class WhistleSound extends AbstractTickableSoundInstance {
        private final TheFallenEntity entity;

        public WhistleSound(TheFallenEntity entity) {
            super(ModSounds.WHISTLE.get(), SoundSource.HOSTILE, entity.getRandom());
            this.entity = entity;
            this.looping = true;
            this.volume = 2.5F;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            updatePos();
        }

        private void updatePos() {
            this.x = entity.getX();
            this.y = entity.getY() + 1.5D;
            this.z = entity.getZ();
        }

        @Override
        public void tick() {
            if (!entity.isAlive() || entity.isRemoved()
                    || entity.isInvisible() || !entity.isWhistling()) {
                Minecraft.getInstance().getSoundManager().stop(this);
                return;
            }
            updatePos();
        }
    }
}
