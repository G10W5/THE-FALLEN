package com.glow.thefallen;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.InteractionHand;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * HUNTING-state goal: Hybrid pathfinding AI.
 *
 * Strategy:
 * 1. PRIMARY: Use Minecraft's built-in A* navigation (handles terrain, stairs, etc.)
 * 2. STUCK DETECTION: Track position over time; if no progress for 20 ticks, switch to direct mode
 * 3. DIRECT MODE: Walk straight at target + jump when hitting walls + break blocks in path
 * 4. TOWERING: Jump + place blocks ONLY when player is above AND entity is close horizontally
 * 5. BRIDGING: Place blocks ONLY over water/lava
 * 6. DESCENDING: Break blocks below when player is below
 */
public class DirectChaseGoal extends Goal {
    private final TheFallenEntity mob;
    private int attackTicks = 0;

    // Stuck detection
    private double lastX, lastY, lastZ;
    private int stuckTicks = 0;
    private boolean directMode = false;
    private int directModeTicks = 0;
    private int pathRefreshCooldown = 0;

    public DirectChaseGoal(TheFallenEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!mob.isHunting()) return false;
        if (mob.isRecovering) return false;
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        if (!mob.isHunting()) return false;
        if (mob.isRecovering) return false;
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        this.attackTicks = 0;
        this.stuckTicks = 0;
        this.directMode = false;
        this.directModeTicks = 0;
        this.pathRefreshCooldown = 0;
        this.lastX = mob.getX();
        this.lastY = mob.getY();
        this.lastZ = mob.getZ();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        // Always face the target
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // --- Stuck Detection ---
        double dx = mob.getX() - lastX;
        double dy = mob.getY() - lastY;
        double dz = mob.getZ() - lastZ;
        double movementSq = dx * dx + dy * dy + dz * dz;

        if (movementSq < 0.01) { // Barely moved
            stuckTicks++;
        } else {
            stuckTicks = Math.max(0, stuckTicks - 2); // Recovering from stuck
        }

        lastX = mob.getX();
        lastY = mob.getY();
        lastZ = mob.getZ();

        // Enter direct mode if stuck for 1.5 seconds (30 ticks)
        if (stuckTicks > 30 && !directMode) {
            directMode = true;
            directModeTicks = 0;
        }

        // Core distance calculations
        double horizontalDistSq = mob.distanceToSqr(target.getX(), mob.getY(), target.getZ());
        double yDiff = target.getY() - mob.getY();
        boolean needsTowering = yDiff >= 1.2 && horizontalDistSq < 64.0 && (directMode || mob.horizontalCollision);

        // Renew direct mode if we still clearly need to tower near the player
        if (directMode) {
            directModeTicks++;
            if (directModeTicks > 60) {
                if (needsTowering) {
                    directModeTicks = 30; // give it more time to keep building
                } else {
                    directMode = false;
                    stuckTicks = 0;
                }
            }
        }

        // --- Movement ---
        if (directMode) {
            if (needsTowering && (mob.horizontalCollision || horizontalDistSq < 4.0)) {
                // Lock onto current X/Z to build a perfect vertical pillar without sliding around corners
                mob.getMoveControl().setWantedPosition(mob.getX(), target.getY(), mob.getZ(), 1.0D);
            } else {
                // DIRECT MODE: Walk straight at target, ignoring navmesh
                mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.4D);
            }

            // Jump when hitting walls
            if (mob.horizontalCollision && mob.onGround()) {
                mob.getJumpControl().jump();
            }
        } else {
            // PATHFINDING MODE: Use Minecraft's A* navigation
            pathRefreshCooldown--;
            if (pathRefreshCooldown <= 0) {
                mob.getNavigation().moveTo(target, 1.2D);
                pathRefreshCooldown = 10; // Re-path every 0.5 seconds
            }

            // Still jump if colliding to help pathfinding over small bumps
            if (mob.horizontalCollision && mob.onGround()) {
                mob.getJumpControl().jump();
            }
        }

        // --- Melee Attack ---
        this.attackTicks = Math.max(this.attackTicks - 1, 0);
        double distSq = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        double reach = (double)(mob.getBbWidth() * 2.0F * mob.getBbWidth() * 2.0F + target.getBbWidth());

        if (distSq <= reach && this.attackTicks <= 0) {
            this.attackTicks = 20;
            mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(target);
        }

        // --- Smart Block Placement (ONLY when needed) ---
        // TOWERING: Player is above (more than a normal jump) AND we're stuck OR hitting a wall
        if (needsTowering) {
            if (mob.onGround()) {
                // Just jump — we'll place the block when we start falling from the peak
                mob.getJumpControl().jump();
            } else if (mob.getDeltaMovement().y <= 0.0) {
                // Airborne and at peak/falling — safely place block below current block space
                BlockPos placePos = mob.blockPosition().below();
                BlockState state = mob.level().getBlockState(placePos);
                
                if (state.isAir() || state.canBeReplaced()) {
                    mob.level().setBlockAndUpdate(placePos, ModBlocks.EERIE_COBBLESTONE.get().defaultBlockState());
                    mob.level().playSound(null, placePos, SoundEvents.STONE_PLACE, SoundSource.HOSTILE, 0.6F, 1.0F);
                    // Center the mob horizontally on the placed block to prevent falling off while towering
                    mob.setPos(placePos.getX() + 0.5, mob.getY(), placePos.getZ() + 0.5);
                    mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0); // Nullify horizontal drift
                }
            }
            // Break ceilings if they block our towering
            BlockPos headPos1 = mob.blockPosition().above(1);
            BlockPos headPos2 = mob.blockPosition().above(2);
            for (BlockPos hp : new BlockPos[]{headPos1, headPos2}) {
                BlockState hs = mob.level().getBlockState(hp);
                if (!hs.isAir() && hs.getDestroySpeed(mob.level(), hp) >= 0) {
                    mob.level().destroyBlock(hp, false);
                }
            }
        }

        // DESCENDING: Player is below, break blocks under our feet to drop down
        if (yDiff < -2.0 && horizontalDistSq < 64.0) {
            BlockPos below = mob.blockPosition().below();
            BlockState belowState = mob.level().getBlockState(below);
            if (!belowState.isAir() && belowState.getDestroySpeed(mob.level(), below) >= 0) {
                mob.level().destroyBlock(below, false);
            }
        }

        // BRIDGING over water/lava: ALWAYS active
        // BRIDGING over air gaps: ONLY when stuck AND player is at roughly same Y level (not climbing terrain)
        Vec3 moveDir = target.position().subtract(mob.position()).normalize();
        if (mob.onGround() || mob.isInWater()) {
            BlockPos aheadFeet = BlockPos.containing(
                mob.getX() + moveDir.x * 2.0, mob.getY() - 1.0, mob.getZ() + moveDir.z * 2.0);
            BlockState aheadState = mob.level().getBlockState(aheadFeet);

            if (aheadState.liquid()) {
                // Always bridge over water/lava
                mob.level().setBlockAndUpdate(aheadFeet, ModBlocks.EERIE_COBBLESTONE.get().defaultBlockState());
                mob.level().playSound(null, aheadFeet, SoundEvents.STONE_PLACE, SoundSource.HOSTILE, 0.6F, 1.0F);
            } else if ((directMode || mob.horizontalCollision) && aheadState.isAir() && Math.abs(yDiff) < 2.0) {
                // Bridge over air gaps when stuck OR bumping into terrain at same Y level
                mob.level().setBlockAndUpdate(aheadFeet, ModBlocks.EERIE_COBBLESTONE.get().defaultBlockState());
                mob.level().playSound(null, aheadFeet, SoundEvents.STONE_PLACE, SoundSource.HOSTILE, 0.6F, 1.0F);
            }
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }
}
