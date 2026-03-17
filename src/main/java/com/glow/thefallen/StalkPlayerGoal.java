package com.glow.thefallen;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * OBSERVING-state goal: The Observer stalks the player.
 * Combines: ceiling stalk, echo footsteps, light snuffing, and item floating.
 * Matches both the original guide (Phase 1) and the new technical spec.
 */
public class StalkPlayerGoal extends Goal {
    private final TheFallenEntity mob;
    private Player target;
    private int tickCount = 0;

    private static final double STALK_RANGE = 64.0D;
    private static final float MIN_DIST = 30.0F;
    private static final float MAX_DIST = 40.0F;

    public StalkPlayerGoal(TheFallenEntity mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!mob.isObserving()) return false;
        this.target = mob.level().getNearestPlayer(mob, STALK_RANGE);
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return mob.isObserving() && target != null && target.isAlive();
    }

    @Override
    public void tick() {
        if (target == null) return;
        
        if (mob.forceRoofCollapse) {
            mob.forceRoofCollapse = false;
            triggerRoofCollapse();
        }

        tickCount++;

        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double distSq = mob.distanceToSqr(target);
        BlockPos targetPos = target.blockPosition();
        boolean inCave = targetPos.getY() < 60 || mob.level().getMaxLocalRawBrightness(targetPos) < 7;

        // --- Stalking Distance ---
        if (distSq > (double)(MAX_DIST * MAX_DIST)) {
            mob.getNavigation().moveTo(target, 1.0D);
        } else if (distSq < (double)(MIN_DIST * MIN_DIST)) {
            mob.getNavigation().stop();
        } else {
            mob.getNavigation().stop();
        }

        // --- Ceiling Stalk (Cave) ---
        if (inCave && tickCount % 100 == 0) {
            ceilingStalk();
        }

        // --- Echo Footsteps (Cave) ---
        if (inCave && tickCount % 10 == 0) {
            echoFootsteps();
        }

        // --- Light Snuffing ---
        if (tickCount % 200 == 0) {
            snuffTorches();
        }

        // --- Item Floating (Anti-Gravity Glitch) ---
        if (tickCount % 80 == 0) {
            floatItems();
        }

        // --- Teleport if seen at close range (FOV avoidance) ---
        if (target.hasLineOfSight(mob) && !inCave && distSq < (double)(MIN_DIST * MIN_DIST)) {
            teleportBehindPlayer();
        }
        
        // --- Dynamic Repositioning ---
        // Periodically change stalking angles to feel more alive
        if (tickCount % 350 == 0) {
            repositionStalking();
        }

        // --- Roof Collapse Event ---
        // Every ~2 minutes, drop a warning block on the player
        if (tickCount > 0 && tickCount % 2400 == 0) {
            triggerRoofCollapse();
        }
    }

    /**
     * Ceiling Stalk: Teleport to the ceiling directly above the player's path.
     * Uses setNoGravity(true) to hang.
     */
    private void ceilingStalk() {
        Level level = mob.level();
        BlockPos above = target.blockPosition().above(3);

        // Scan upward to find the ceiling
        while (level.getBlockState(above).isAir() && above.getY() < level.getMaxBuildHeight()) {
            above = above.above();
        }

        if (!level.getBlockState(above).isAir()) {
            BlockPos hangPos = above.below(); // Just below the ceiling
            mob.teleportTo(target.getX(), hangPos.getY(), target.getZ());
            mob.setNoGravity(true);
        }
    }

    /**
     * Echo Footsteps: Play stone.step sounds 5 blocks behind the player.
     */
    private void echoFootsteps() {
        Vec3 behind = target.position().subtract(target.getLookAngle().scale(5));
        mob.level().playSound(null, behind.x, behind.y, behind.z,
                SoundEvents.STONE_STEP, SoundSource.HOSTILE, 0.8F, 0.7F);
    }

    /**
     * Light Snuffing: Scan 10-block radius for torches. Replace with Air and drop as item.
     */
    private void snuffTorches() {
        BlockPos center = target.blockPosition();
        Level level = mob.level();

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-10, -3, -10), center.offset(10, 3, 10))) {
            if (level.getBlockState(pos).getBlock() instanceof TorchBlock) {
                // Drop the torch as an item
                level.destroyBlock(pos, true);
                break; // One torch per cycle for tension
            }
        }
    }

    /**
     * Item Floating: Scan 5-block radius for dropped items. Apply upward velocity.
     */
    private void floatItems() {
        List<ItemEntity> items = mob.level().getEntitiesOfClass(
                ItemEntity.class, target.getBoundingBox().inflate(5));

        for (ItemEntity item : items) {
            Vec3 mov = item.getDeltaMovement();
            // Swirl effect: slight horizontal rotation + upward float
            double angle = mob.getRandom().nextFloat() * Math.PI * 2;
            double swirl = 0.03;
            item.setDeltaMovement(
                    mov.x + Math.cos(angle) * swirl,
                    0.1,
                    mov.z + Math.sin(angle) * swirl
            );
            item.hasImpulse = true;
        }
    }

    /**
     * Teleport behind the player if they can see the entity (FOV avoidance).
     */
    private void teleportBehindPlayer() {
        Vec3 lookAngle = target.getLookAngle();
        Vec3 teleportPos = target.position().subtract(lookAngle.scale(15));
        
        BlockPos groundPos = mob.level().getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(teleportPos.x, 0, teleportPos.z));
                
        mob.teleportTo(teleportPos.x, groundPos.getY() + 1.0, teleportPos.z);
    }
    
    /**
     * Dynamic Repositioning: Randomly teleport to a new stalking angle.
     */
    private void repositionStalking() {
        if (mob.level().isClientSide || target == null) return;
        
        // Pick a random angle around the target, 35 blocks away
        double angle = mob.getRandom().nextDouble() * Math.PI * 2;
        double tx = target.getX() + Math.cos(angle) * 35.0;
        double tz = target.getZ() + Math.sin(angle) * 35.0;
        
        BlockPos pos = BlockPos.containing(tx, target.getY(), tz);
        if (mob.level().canSeeSky(target.blockPosition())) {
            int surfaceY = mob.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos).getY();
            mob.teleportTo(tx, Math.max(surfaceY + 1.0, target.getY()), tz);
        } else {
            mob.teleportTo(tx, target.getY(), tz); // Cave teleport
        }
    }
    
    /**
     * Roof Collapse: Spawns a falling block matching the ground beneath the player, 10 blocks above them.
     */
    private void triggerRoofCollapse() {
        if (mob.level().isClientSide || target == null) return;
        
        // Sample block under player
        BlockPos under = target.blockPosition().below();
        net.minecraft.world.level.block.state.BlockState state = mob.level().getBlockState(under);
        
        if (state.isAir() || state.getBlock() == net.minecraft.world.level.block.Blocks.BEDROCK) {
            // Fallback to stone if they are flying or on bedrock
            state = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        }
        
        // Drop it from 10 blocks above the player
        BlockPos dropPos = target.blockPosition().above(10);
        
        net.minecraft.world.entity.item.FallingBlockEntity fallingBlock = net.minecraft.world.entity.item.FallingBlockEntity.fall(mob.level(), dropPos, state);
        if (fallingBlock != null) {
            // Very low damage multiplier (0.1F * 10 blocks ~ 1 damage), capped at 2 damage max
            fallingBlock.setHurtsEntities(0.1F, 2); 
            fallingBlock.dropItem = false;
            mob.level().addFreshEntity(fallingBlock);
            
            // Creepy sound at the drop source
            mob.level().playSound(null, dropPos.getX(), dropPos.getY(), dropPos.getZ(), net.minecraft.sounds.SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 0.5F);
        }
    }
}
