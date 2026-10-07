package com.glow.thefallen;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/**
 * HUNTING-state goal: The Fallen breaks blocks in its path to reach the player.
 *
 * Changes vs. the old version:
 *  - Runs in PARALLEL with DirectChaseGoal (no MOVE flag). The old version had priority 1 + MOVE,
 *    so every wall/step bump kicked DirectChaseGoal out, froze the mob for 6+ ticks and reset its AI state.
 *  - Only considers blocks that actually have a collision shape. Grass, flowers, snow layers, etc. are ignored.
 *  - Never breaks a 1-block step the mob can simply jump over.
 *  - Does not dig through the player's pillar when DirectChaseGoal should be towering instead.
 */
public class BreakBlockGoal extends Goal {
    private static final int BREAK_DURATION = 6; // ticks per block (~0.3s)

    private static final String[] BLACKLIST = {
            "minecraft:bedrock", "minecraft:end_portal_frame", "minecraft:barrier"
    };

    private final TheFallenEntity mob;
    private int breakTime;
    private int lastBreakProgress = -1;
    private BlockPos crackPos = null;

    public BreakBlockGoal(TheFallenEntity mob) {
        this.mob = mob;
        // Empty flag set = this goal never blocks (or gets blocked by) movement goals.
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    /** True if the block has a real collision shape (air, grass, flowers, water... are NOT solid). */
    public static boolean isSolid(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return !s.getCollisionShape(level, pos).isEmpty();
    }

    @Override
    public boolean canUse() {
        if (!mob.isHunting()) return false;
        if (mob.isRecovering) return false;
        if (!mob.horizontalCollision) return false;

        LivingEntity target = mob.getTarget();
        if (target == null) return false;
        if (mob.distanceToSqr(target) >= 100.0D) return false; // Within 10 blocks

        // Player is above us and close: DirectChaseGoal towers up, don't dig into their pillar.
        double yDiff = target.getY() - mob.getY();
        double hSq = mob.distanceToSqr(target.getX(), mob.getY(), target.getZ());
        if (yDiff >= 1.2D && hSq < 64.0D) return false;

        if (canStepOver()) return false; // a normal jump handles it

        return findObstacle() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return mob.isHunting() && !mob.isRecovering && findObstacle() != null;
    }

    @Override
    public void start() {
        this.breakTime = 0;
        this.lastBreakProgress = -1;
        this.crackPos = null;
    }

    @Override
    public void tick() {
        BlockPos pos = findObstacle();
        if (pos == null) return;

        // Switched to a different block: restart the crack animation
        if (!pos.equals(crackPos)) {
            clearCrack();
            crackPos = pos.immutable();
            breakTime = 0;
            lastBreakProgress = -1;
        }

        breakTime++;

        int progress = (int) ((float) breakTime / BREAK_DURATION * 10.0F);
        if (progress != lastBreakProgress) {
            mob.level().destroyBlockProgress(mob.getId(), crackPos, progress);
            lastBreakProgress = progress;
        }

        if (breakTime >= BREAK_DURATION) {
            Level level = mob.level();
            BlockState state = level.getBlockState(crackPos);
            level.destroyBlock(crackPos, false); // Don't drop items normally

            // "The Rip": spawn a FallingBlockEntity with upward velocity
            if (!level.isClientSide) {
                FallingBlockEntity fallingBlock = FallingBlockEntity.fall(level, crackPos, state);
                if (fallingBlock != null) {
                    var rng = mob.getRandom();
                    fallingBlock.setDeltaMovement(
                            (rng.nextDouble() - 0.5) * 0.4,
                            0.4 + rng.nextDouble() * 0.2,
                            (rng.nextDouble() - 0.5) * 0.4);
                    fallingBlock.setHurtsEntities(0.0F, 0);
                    fallingBlock.dropItem = false;
                    fallingBlock.time = 600; // Despawn age threshold - block vanishes instantly
                    level.addFreshEntity(fallingBlock);
                }
            }

            crackPos = null;
            breakTime = 0;
            lastBreakProgress = -1;
        }
    }

    @Override
    public void stop() {
        clearCrack();
        crackPos = null;
    }

    private void clearCrack() {
        if (crackPos != null) {
            mob.level().destroyBlockProgress(mob.getId(), crackPos, -1);
        }
    }

    private int mobHeightBlocks() {
        return Math.max(1, Mth.ceil(mob.getBbHeight()));
    }

    /**
     * True when the obstacle is exactly a 1-block step (solid at foot level, free above it,
     * and free headroom to jump). In that case the mob should jump, not dig.
     */
    private boolean canStepOver() {
        Level level = mob.level();
        BlockPos feet = mob.blockPosition();
        BlockPos front = feet.relative(mob.getDirection());
        int h = mobHeightBlocks();

        if (!isSolid(level, front)) return false;
        for (int i = 1; i <= h; i++) {
            if (isSolid(level, front.above(i))) return false;
        }
        return !isSolid(level, feet.above(h)); // headroom for the jump
    }

    /** First breakable, solid block in front of the mob across its full height. */
    private BlockPos findObstacle() {
        Level level = mob.level();
        BlockPos front = mob.blockPosition().relative(mob.getDirection());
        int h = mobHeightBlocks();

        for (int i = 0; i < h; i++) {
            BlockPos p = front.above(i);
            if (!isSolid(level, p)) continue; // air, grass, flowers, water...
            BlockState state = level.getBlockState(p);
            if (isBlacklisted(state)) continue;
            if (state.is(ModBlocks.GLITCHED_BLOCK.get())) continue;
            if (state.getDestroySpeed(level, p) < 0) continue; // unbreakable
            return p;
        }
        return null;
    }

    private boolean isBlacklisted(BlockState state) {
        String name = state.getBlock().builtInRegistryHolder().key().location().toString();
        for (String b : BLACKLIST) {
            if (name.equals(b)) return true;
        }
        return false;
    }
}
