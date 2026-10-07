package com.glow.thefallen;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * HUNTING-state goal: Hybrid pathfinding AI.
 *
 * 1. PRIMARY: Minecraft's A* navigation.
 * 2. DEAD PATH: if the path ended (or none exists) and we're still not in melee range -> direct mode
 *    after ~8 ticks (no more "thinking" at the foot of a pillar).
 * 3. STUCK: no movement for ~12 ticks -> direct mode.
 * 4. DIRECT MODE: walk straight at target, jump on walls (BreakBlockGoal runs in parallel and digs).
 * 5. TOWERING: player is above and close -> raise ourselves one block every TOWER_INTERVAL ticks.
 *    (4 blocks/s at 5 ticks; a player pillar-jumping is ~2.5 blocks/s, so the mob always catches up.)
 * 6. BRIDGING: place blocks over liquid / air gaps. 7. DESCENDING: dig down when player is far below.
 */
public class DirectChaseGoal extends Goal {
    private static final int STUCK_LIMIT = 12;        // ticks without moving before direct mode (was 30)
    private static final int DEAD_PATH_LIMIT = 8;     // ticks with a finished/missing path before direct mode
    private static final int TOWER_INTERVAL = 5;      // ticks per tower block. Raise this to nerf towering.
    private static final double TOWER_RANGE_SQ = 64.0D; // horizontal range (8 blocks) where towering is allowed
    private static final double TOWER_NEAR_SQ = 9.0D;   // within 3 blocks horizontally -> tower in place

    private final TheFallenEntity mob;
    private int attackTicks = 0;

    private double lastX, lastY, lastZ;
    private int stuckTicks = 0;
    private int deadPathTicks = 0;
    private boolean directMode = false;
    private int directModeTicks = 0;
    private int pathRefreshCooldown = 0;
    private int towerCooldown = 0;
    private int towerHold = 0;

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
        return canUse();
    }

    @Override
    public void start() {
        this.attackTicks = 0;
        this.stuckTicks = 0;
        this.deadPathTicks = 0;
        this.directMode = false;
        this.directModeTicks = 0;
        this.pathRefreshCooldown = 0;
        this.towerCooldown = 0;
        this.towerHold = 0;
        this.lastX = mob.getX();
        this.lastY = mob.getY();
        this.lastZ = mob.getZ();
    }

    private void enterDirectMode() {
        directMode = true;
        directModeTicks = 0;
        deadPathTicks = 0;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // --- Geometry ---
        double horizontalDistSq = mob.distanceToSqr(target.getX(), mob.getY(), target.getZ());
        double yDiff = target.getY() - mob.getY();
        double distSq = mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        double reach = (double) (mob.getBbWidth() * 2.0F * mob.getBbWidth() * 2.0F + target.getBbWidth());
        boolean inMelee = distSq <= reach;

        // --- Stuck detection ---
        double dx = mob.getX() - lastX;
        double dy = mob.getY() - lastY;
        double dz = mob.getZ() - lastZ;
        double movementSq = dx * dx + dy * dy + dz * dz;
        if (movementSq < 0.01) {
            stuckTicks++;
        } else {
            stuckTicks = Math.max(0, stuckTicks - 2);
        }
        lastX = mob.getX();
        lastY = mob.getY();
        lastZ = mob.getZ();

        if (stuckTicks > STUCK_LIMIT && !directMode) {
            enterDirectMode();
        }

        // Towering only happens in direct mode (which now kicks in fast)
        boolean needsTowering = directMode && yDiff >= 1.2 && horizontalDistSq < TOWER_RANGE_SQ;
        if (needsTowering && (mob.horizontalCollision || horizontalDistSq < TOWER_NEAR_SQ)) {
            towerHold = 10; // hysteresis: collision flag drops once we stand still
        }
        boolean towering = needsTowering && towerHold > 0;
        if (towerHold > 0) towerHold--;

        // Direct mode expiry
        if (directMode) {
            directModeTicks++;
            if (directModeTicks > 60) {
                if (needsTowering) {
                    directModeTicks = 30;
                } else {
                    directMode = false;
                    stuckTicks = 0;
                    deadPathTicks = 0;
                    pathRefreshCooldown = 0;
                }
            }
        }

        // --- Movement ---
        if (directMode) {
            // A leftover A* path would overwrite our wanted position every tick (nav ticks after goals)
            mob.getNavigation().stop();

            if (towering) {
                // Hold still on the pillar column and raise ourselves
                mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 1.0D);
                mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
                towerStep();
            } else {
                mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), 1.4D);
                if (mob.horizontalCollision && mob.onGround()) {
                    mob.getJumpControl().jump();
                }
            }
        } else {
            pathRefreshCooldown--;
            if (pathRefreshCooldown <= 0) {
                mob.getNavigation().moveTo(target, 1.2D);
                pathRefreshCooldown = 10;
            }

            // Path finished (or doesn't exist) but we're not on the target -> stop thinking, go direct.
            if (mob.getNavigation().isDone() && !inMelee) {
                if (++deadPathTicks > DEAD_PATH_LIMIT) {
                    enterDirectMode();
                }
            } else {
                deadPathTicks = 0;
            }

            if (mob.horizontalCollision && mob.onGround()) {
                mob.getJumpControl().jump();
            }
        }

        // --- Melee Attack ---
        this.attackTicks = Math.max(this.attackTicks - 1, 0);
        if (inMelee && this.attackTicks <= 0) {
            this.attackTicks = 20;
            mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(target);
        }

        // --- DESCENDING: player far below, dig down ---
        if (yDiff < -2.0 && horizontalDistSq < 64.0) {
            BlockPos below = mob.blockPosition().below();
            BlockState belowState = mob.level().getBlockState(below);
            if (!belowState.isAir() && belowState.getDestroySpeed(mob.level(), below) >= 0) {
                mob.level().destroyBlock(below, true);
            }
        }

        // --- BRIDGING (not while towering) ---
        if (!towering) {
            Vec3 moveDir = target.position().subtract(mob.position()).normalize();
            if (mob.onGround() || mob.isInWater()) {
                BlockPos pos1 = BlockPos.containing(mob.getX() + moveDir.x * 1.0, mob.getY() - 1.0, mob.getZ() + moveDir.z * 1.0);
                BlockPos pos2 = BlockPos.containing(mob.getX() + moveDir.x * 2.0, mob.getY() - 1.0, mob.getZ() + moveDir.z * 2.0);

                for (BlockPos aheadFeet : new BlockPos[]{pos1, pos2}) {
                    BlockState aheadState = mob.level().getBlockState(aheadFeet);

                    if (aheadState.liquid()) {
                        placeEerie(aheadFeet);
                    } else if ((directMode || mob.horizontalCollision) && aheadState.isAir() && Math.abs(yDiff) < 2.0) {
                        placeEerie(aheadFeet);
                    }
                }
            }
        }
    }

    /**
     * Pillar-rise: every TOWER_INTERVAL ticks, place a block in our own foot cell and lift ourselves
     * on top of it. No jump arc, so it's faster than a player pillar-jumping.
     */
    private void towerStep() {
        if (towerCooldown > 0) {
            towerCooldown--;
            return;
        }
        if (!mob.onGround()) return;

        Level level = mob.level();
        BlockPos feet = mob.blockPosition();
        BlockState feetState = level.getBlockState(feet);
        if (!feetState.isAir() && !feetState.canBeReplaced()) return;

        // Clear the cells our head will move into (across the full body width)
        int h = Math.max(1, Mth.ceil(mob.getBbHeight()));
        AABB box = mob.getBoundingBox();
        BlockPos lo = BlockPos.containing(box.minX + 0.01, feet.getY() + h, box.minZ + 0.01);
        BlockPos hi = BlockPos.containing(box.maxX - 0.01, feet.getY() + h, box.maxZ - 0.01);
        for (BlockPos p : BlockPos.betweenClosed(lo, hi)) {
            if (!BreakBlockGoal.isSolid(level, p)) continue;
            if (level.getBlockState(p).getDestroySpeed(level, p) < 0) return; // unbreakable ceiling, can't rise
            level.destroyBlock(p, true);
        }

        placeEerie(feet);
        mob.setPos(feet.getX() + 0.5, feet.getY() + 1.0, feet.getZ() + 0.5);
        mob.setDeltaMovement(0, 0, 0);
        mob.fallDistance = 0.0F;
        towerCooldown = TOWER_INTERVAL;
    }

    private void placeEerie(BlockPos pos) {
        mob.level().setBlockAndUpdate(pos, ModBlocks.EERIE_COBBLESTONE.get().defaultBlockState());
        mob.level().playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.HOSTILE, 0.6F, 1.0F);
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }
}
