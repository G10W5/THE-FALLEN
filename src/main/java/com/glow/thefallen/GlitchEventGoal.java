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
 * Detection: player.canSee(entity) AND dot(entity.viewVector, player.lookAngle) < -0.9
 *
 * On trigger:
 * 1. Play explosion sound (no damage).
 * 2. Corrupt 70% of blocks in a 5-block radius → GlitchedBlock.
 * 3. Teleport entity 60 blocks away to an obscured location.
 */
public class GlitchEventGoal extends Goal {
    private final TheFallenEntity mob;
    private Player target;
    private int cooldown = 0;

    // Blocks that should never be corrupted (safety guard from the original guide)
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
        if (cooldown > 0) {
            cooldown--;
            return false;
        }
        this.target = mob.level().getNearestPlayer(mob, 64.0D);
        if (target == null) return false;

        // Must be standing in the distance (at least 15 blocks away)
        if (mob.distanceToSqr(target) < 225.0D) {
            return false;
        }

        return isDirectEyeContact();
    }

    @Override
    public void start() {
        if (target == null) return;
        Level level = mob.level();

        // 1. Explosion sound (no actual explosion/damage)
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.5F, 0.5F);

        // 2. Block Corruption: 30% of blocks in 5-block radius → GlitchedBlock
        corruptBlocks(level);

        // 3. Teleport entity away to an obscured location
        teleportAway();

        // 10-second cooldown (200 ticks)
        cooldown = 200;
    }

    /**
     * Check if the player is directly looking at the entity.
     * We compute the direction from the player's eye position to the entity center,
     * then verify the player's look angle aligns with that direction (dot > 0.97 = ~14 degree cone).
     */
    private boolean isDirectEyeContact() {
        if (!target.hasLineOfSight(mob)) return false;

        // Direction from player eyes to entity center
        Vec3 toEntity = mob.getEyePosition().subtract(target.getEyePosition()).normalize();
        Vec3 playerLook = target.getLookAngle().normalize();

        // dot > 0.97 means player is looking within ~14 degrees of the entity center
        double dot = toEntity.dot(playerLook);
        return dot > 0.97;
    }

    /**
     * Corrupt 70% of non-air, non-blacklisted blocks in a 5-block radius.
     */
    private void corruptBlocks(Level level) {
        BlockPos center = mob.blockPosition();
        int radius = 5;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {

            BlockState state = level.getBlockState(pos);

            // Skip air, liquids, and blacklisted blocks
            if (state.isAir() || state.liquid()) continue;
            if (isBlacklisted(state)) continue;
            if (state.is(ModBlocks.GLITCHED_BLOCK.get())) continue; // Already corrupted

            // 30% chance to corrupt
            if (mob.getRandom().nextFloat() < 0.30F) {
                level.setBlockAndUpdate(pos, ModBlocks.GLITCHED_BLOCK.get().defaultBlockState());
            }
        }
    }

    /**
     * Check if a block is on the safety blacklist.
     */
    private boolean isBlacklisted(BlockState state) {
        String blockName = state.getBlock().builtInRegistryHolder().key().location().toString();
        for (String blacklisted : BLACKLIST) {
            if (blockName.equals(blacklisted)) return true;
        }
        return false;
    }

    /**
     * Teleport 60 blocks away from the player to an obscured location.
     */
    private void teleportAway() {
        Level level = mob.level();

        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = mob.getRandom().nextFloat() * Math.PI * 2;
            double dist = 50 + mob.getRandom().nextFloat() * 20; // 50-70 blocks
            double tx = mob.getX() + Math.cos(angle) * dist;
            double tz = mob.getZ() + Math.sin(angle) * dist;

            // Find a valid Y
            BlockPos testPos = BlockPos.containing(tx, 0, tz);
            BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, testPos);

            if (ground.getY() > level.getMinBuildHeight()) {
                mob.teleportTo(tx, ground.getY() + 1.0, tz);
                return;
            }
        }

        // Fallback: just teleport far away
        double angle = mob.getRandom().nextFloat() * Math.PI * 2;
        double tx = mob.getX() + Math.cos(angle) * 60;
        double tz = mob.getZ() + Math.sin(angle) * 60;
        BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(tx, 0, tz));
        mob.teleportTo(tx, ground.getY() + 1.0, tz);
    }
}
