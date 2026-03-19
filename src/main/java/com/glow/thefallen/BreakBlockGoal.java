package com.glow.thefallen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/**
 * HUNTING-state goal: The Fallen breaks blocks in its path to reach the player.
 *
 * From the original guide:
 * - Triggers when mob.horizontalCollision is true and target is within 10 blocks.
 * - Uses level.destroyBlockProgress for cracking animation over 60-100 ticks.
 * - "The Rip": On break, spawns FallingBlockEntity with upward velocity.
 *
 * Safety: Blacklists bedrock, end_portal_frame, barrier, and GlitchedBlock.
 */
public class BreakBlockGoal extends Goal {
    private final TheFallenEntity mob;
    private int breakTime;
    private int lastBreakProgress = -1;
    private int breakDuration; // Randomized per block (60-100 ticks)

    private static final String[] BLACKLIST = {
            "minecraft:bedrock", "minecraft:end_portal_frame", "minecraft:barrier"
    };

    public BreakBlockGoal(TheFallenEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!mob.isHunting()) return false;
        if (mob.isRecovering) return false;
        if (!mob.horizontalCollision) return false;
        if (mob.getTarget() == null) return false;
        return mob.distanceToSqr(mob.getTarget()) < 100.0D; // Within 10 blocks
    }

    @Override
    public boolean canContinueToUse() {
        return mob.isHunting() && !mob.isRecovering && breakTime < breakDuration;
    }

    @Override
    public void start() {
        this.breakTime = 0;
        this.lastBreakProgress = -1;
        // Instant aggression: break in ~6 ticks (0.3 seconds)
        this.breakDuration = 6;
    }

    @Override
    public void tick() {
        breakTime++;

        BlockPos pos = mob.blockPosition().relative(mob.getDirection());
        // Scan at foot height and eye height
        BlockPos[] targets = {pos, pos.above()};

        for (BlockPos targetPos : targets) {
            BlockState state = mob.level().getBlockState(targetPos);

            if (state.isAir()) continue;
            if (isBlacklisted(state)) continue;
            if (state.is(ModBlocks.GLITCHED_BLOCK.get())) continue;
            if (state.getDestroySpeed(mob.level(), targetPos) < 0) continue; // Skip unbreakable

            // Show cracking progress
            int progress = (int) ((float) breakTime / breakDuration * 10.0F);
            if (progress != lastBreakProgress) {
                mob.level().destroyBlockProgress(mob.getId(), targetPos, progress);
                lastBreakProgress = progress;
            }

            // Break the block
            if (breakTime >= breakDuration) {
                mob.level().destroyBlock(targetPos, false); // Don't drop items normally

                // "The Rip": Spawn FallingBlockEntity with upward velocity
                Level level = mob.level();
                if (!level.isClientSide) {
                    FallingBlockEntity fallingBlock = FallingBlockEntity.fall(level, targetPos, state);
                    if (fallingBlock != null) {
                        java.util.Random rng = new java.util.Random();
                        fallingBlock.setDeltaMovement(
                            (rng.nextDouble() - 0.5) * 0.4,
                            0.4 + rng.nextDouble() * 0.2,
                            (rng.nextDouble() - 0.5) * 0.4);
                        fallingBlock.setHurtsEntities(0.0F, 0);
                        fallingBlock.dropItem = false;    // Never drop as item
                        fallingBlock.time = 600;          // Despawn age threshold — block vanishes instantly
                        level.addFreshEntity(fallingBlock);
                    }
                }

                breakTime = 0;
                lastBreakProgress = -1;
            }
            break; // Only work on one block at a time
        }
    }

    @Override
    public void stop() {
        // Clean up crack animation
        BlockPos pos = mob.blockPosition().relative(mob.getDirection());
        mob.level().destroyBlockProgress(mob.getId(), pos, -1);
        mob.level().destroyBlockProgress(mob.getId(), pos.above(), -1);
    }

    private boolean isBlacklisted(BlockState state) {
        String name = state.getBlock().builtInRegistryHolder().key().location().toString();
        for (String b : BLACKLIST) {
            if (name.equals(b)) return true;
        }
        return false;
    }
}
