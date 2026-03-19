package com.glow.thefallen;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * OBSERVING-state goal: Triggers the "Glitch Event" when the player makes direct eye contact.
 *
 * Sequence:
 * 1. WAITING phase: Observer stands in distance, fully visible (bait). Up to 10 seconds.
 * 2. On eye contact: INSTANTLY plays explosion sound, corrupts blocks, shakes camera, and vanishes.
 * 3. If player ignores him: Observer quietly vanishes after 10 seconds (no event).
 */
public class GlitchEventGoal extends Goal {
    private final TheFallenEntity mob;
    private Player target;
    private int cooldown    = 0;
    private int glitchTicks = 0;

    private enum Phase { NONE, WAITING }
    private Phase phase = Phase.NONE;

    private static final String[] BLACKLIST = {
        "minecraft:bedrock", "minecraft:end_portal_frame", "minecraft:barrier",
        "minecraft:command_block", "minecraft:structure_block", "minecraft:spawner"
    };

    public GlitchEventGoal(TheFallenEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.forceGlitch) {
            mob.forceGlitch = false;
            this.target = mob.forcedTarget;
            return true;
        }
        if (!mob.isObserving()) return false;
        if (cooldown > 0) { cooldown--; return false; }

        this.target = mob.level().getNearestPlayer(mob, 64.0D);
        if (target == null) return false;
        if (mob.distanceToSqr(target) < 225.0D) return false; // must be > 15 blocks away

        return mob.getRandom().nextInt(200) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        // Only keep the goal alive during WAITING; once GLITCHING resolves we stop immediately
        return phase == Phase.WAITING && glitchTicks < 200; // up to 10 seconds of waiting
    }

    @Override
    public void start() {
        if (target == null) return;
        phase = Phase.WAITING;
        glitchTicks = 0;
        mob.getNavigation().stop();
        mob.isForceVisible = true;
        mob.setInvisible(false);
    }

    @Override
    public void tick() {
        glitchTicks++;
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (phase == Phase.WAITING && isDirectEyeContact()) {
            triggerGlitch();
            // Mark as done — canContinueToUse() will return false next tick,
            // which causes the GoalSelector to call stop() for us.
            glitchTicks = 9999;
        }
    }

    @Override
    public void stop() {
        mob.isForceVisible = false;
        mob.setInvisible(true);

        if (phase == Phase.WAITING && glitchTicks >= 200) {
            // Player ignored him — just quietly vanish
            cooldown = 100;
        }
        phase = Phase.NONE;
    }

    private void triggerGlitch() {
        Level level = mob.level();

        // 1. Explosion sound at PLAYER position for maximum impact
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                ModSounds.GLITCH_EXPLOSION.get(), SoundSource.HOSTILE, 3.0F, 0.5F);

        // 2. Camera shake: a harmless zero-power explosion at the player's feet
        //    This shakes the player's screen without damaging terrain or the player.
        level.explode(null,
            target.getX(), target.getY(), target.getZ(),
            0.0F,  // no blast radius → no block damage
            Level.ExplosionInteraction.NONE);

        // 3. Block corruption around the Observer
        corruptBlocks(level);

        // 4. Cooldown before this can fire again
        cooldown = 200;
    }

    private boolean isDirectEyeContact() {
        if (!target.hasLineOfSight(mob)) return false;
        Vec3 toEntity  = mob.getEyePosition().subtract(target.getEyePosition()).normalize();
        Vec3 playerLook = target.getLookAngle().normalize();
        return toEntity.dot(playerLook) > 0.97;
    }

    private void corruptBlocks(Level level) {
        BlockPos center = mob.blockPosition();
        int radius = 5;
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.liquid()) continue;
            if (isBlacklisted(state)) continue;
            if (state.is(ModBlocks.GLITCHED_BLOCK.get())) continue;
            if (mob.getRandom().nextFloat() < 0.30F) {
                level.setBlockAndUpdate(pos, ModBlocks.GLITCHED_BLOCK.get().defaultBlockState());
            }
        }
    }

    private boolean isBlacklisted(BlockState state) {
        String name = state.getBlock().builtInRegistryHolder().key().location().toString();
        for (String b : BLACKLIST) if (name.equals(b)) return true;
        return false;
    }
}
