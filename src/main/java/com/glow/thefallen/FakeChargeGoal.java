package com.glow.thefallen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.EnumSet;

/**
 * OBSERVING jumpscare.
 *
 * Sequence:
 *  1. canUse() - random 1/600 chance or force-flag. Player must be ≤32 blocks away.
 *  2. start()  - become visible, scream, set isChargingJumpscare + isForceVisible.
 *               Rotate the player's camera to face the entity.
 *  3. tick()   - sprint toward the player.
 *  4. vanish() - called ONLY when within 2 blocks of the player.
 *               Stays visible the whole approach; goes invisible after contact.
 *  5. stop()   - if the goal is cancelled (e.g. 200-tick timeout) without contact,
 *               vanish gracefully.
 */
public class FakeChargeGoal extends Goal {
    private final TheFallenEntity mob;
    private Player target;
    private int chargeTicks;
    private boolean charging;
    private boolean hasVanished; // guard so vanish() is only called once

    public FakeChargeGoal(TheFallenEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    // ---- Goal lifecycle ----

    @Override
    public boolean canUse() {
        if (mob.forceJumpscare) {
            mob.forceJumpscare = false;
            this.target = mob.forcedTarget;
            return target != null;
        }

        if (!mob.isObserving()) return false;
        // ~1 in 600 ticks = roughly once every 30 s when nearby
        if (mob.getRandom().nextInt(600) != 0) return false;

        Player nearest = mob.level().getNearestPlayer(mob, 32.0D);
        if (nearest == null || !mob.hasLineOfSight(nearest)) return false;

        this.target = nearest;
        return true;
    }

    @Override
    public void start() {
        chargeTicks = 0;
        charging = true;
        hasVanished = false;
        mob.isChargingJumpscare = true;
        mob.isForceVisible = true;   // keep isForceVisible TRUE so flicker can't override

        // Become visible
        mob.setInvisible(false);

        // Scream
        mob.level().playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                ModSounds.SCREAM.get(), SoundSource.HOSTILE, 2.0F, 0.5F);

        // Rotate the player's camera to face the entity (server-side teleport trick with same pos)
        // Rotate the player's camera to face the entity
        if (target instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, mob.getEyePosition());
        }
    }

    @Override
    public boolean canContinueToUse() {
        // Extend limit to 200 ticks but let vanish() be the real terminator
        return charging && !hasVanished && target != null && target.isAlive()
                && mob.isObserving() && chargeTicks < 200;
    }

    @Override
    public void tick() {
        chargeTicks++;
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        mob.getNavigation().moveTo(target, 1.5D);

        // Contact check: within 2 blocks → vanish NOW
        if (mob.distanceToSqr(target) < 4.0D) { // 2^2 = 4
            vanish();
        }
    }

    @Override
    public void stop() {
        // Called if the goal was interrupted or the 200-tick limit was hit
        vanish();
    }

    // ---- Internal helpers ----

    private void vanish() {
        if (hasVanished) return; // idempotent
        hasVanished = true;
        charging = false;

        // Poof sound centered on the player so it's loud
        if (target != null) {
            mob.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    ModSounds.GLITCH_EXPLOSION.get(), SoundSource.HOSTILE, 3.0F, 2.0F);
        }

        // Damage + status effects
        if (target != null && target.isAlive()) {
            float damage = mob.getRandom().nextBoolean() ? 1.0F : 2.0F;
            target.hurt(mob.damageSources().mobAttack(mob), damage);

            if (target instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
                sp.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0));
            }
        }

        // Teleport away 30 blocks behind the player
        if (target != null) {
            Vec3 look = target.getLookAngle();
            double tx = target.getX() - look.x * 30;
            double tz = target.getZ() - look.z * 30;
            BlockPos pos = BlockPos.containing(tx, target.getY(), tz);
            int surfaceY = mob.level()
                    .getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos)
                    .getY();
            if (mob.level().canSeeSky(target.blockPosition())) {
                mob.teleportTo(tx, Math.max(surfaceY + 1.0, target.getY()), tz);
            } else {
                mob.teleportTo(tx, target.getY(), tz);
            }
        }

        // Go invisible and release flags
        mob.setInvisible(true);
        mob.isChargingJumpscare = false;
        mob.isForceVisible = false;
    }
}
