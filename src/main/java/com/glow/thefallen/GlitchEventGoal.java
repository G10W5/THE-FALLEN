package com.glow.thefallen;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
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

        // Eerie whistling bait while he stands there waiting to be looked at
        if (glitchTicks % 60 == 0) {
            mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                    ModSounds.WHISTLE.get(), SoundSource.HOSTILE, 1.2F, 1.0F);
        }

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

        // 4. Face-flash: the player earned this look
        mob.triggerJumpscareFlag();

        // 5. Cooldown before this can fire again
        cooldown = 200;
    }

    /**
     * Precise crosshair test: returns true only if a ray cast from the player's
     * eyes along the look vector actually hits the Observer within 64 blocks,
     * with no solid block in between. Invisible stalkers never count.
     */
    private boolean isDirectEyeContact() {
        if (target == null || mob.isInvisible()) return false;
        Vec3 eye = target.getEyePosition();
        Vec3 look = target.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(64.0D));
        AABB search = target.getBoundingBox().expandTowards(look.scale(64.0D)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                target, eye, end, search, e -> e == mob, 0.0F);
        if (entityHit == null || entityHit.getEntity() != mob) return false;
        // Reject if a wall is between player and entity
        BlockHitResult blockHit = target.level().clip(
                new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, target));
        if (blockHit.getType() != HitResult.Type.MISS
                && blockHit.getLocation().distanceToSqr(eye)
                        < entityHit.getLocation().distanceToSqr(eye)) {
            return false;
        }
        return true;
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
