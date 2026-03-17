package com.glow.thefallen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

/**
 * Custom MoveControl for TheFallenEntity:
 * - HUNTING on surface: Hovers 1.5-2.0 blocks above the ground and glides toward the target.
 * - HUNTING in caves: Simulated wall/ceiling crawl via setNoGravity + surface-seeking.
 * - OBSERVING: Falls through to default pathfinding (mob walks normally while invisible).
 */
public class HoverMoveControl extends MoveControl {
    private final TheFallenEntity entity;

    private static final double HOVER_HEIGHT = 2.0D;
    private static final double HOVER_SPEED = 0.08D;
    private static final double GLIDE_SPEED = 0.15D;

    public HoverMoveControl(TheFallenEntity entity) {
        super(entity);
        this.entity = entity;
    }

    @Override
    public void tick() {
        if (!entity.isHunting()) {
            // OBSERVING: use default ground-based movement
            super.tick();
            return;
        }

        // --- HUNTING MODE ---
        Vec3 currentPos = entity.position();
        BlockPos blockBelow = entity.blockPosition();

        // Find the ground level below
        int groundY = findGroundY(blockBelow);
        double targetHoverY = groundY + HOVER_HEIGHT;
        
        // If the target is higher up (on a pillar or swimming on water surface), elevate to reach them
        if (entity.getTarget() != null) {
            targetHoverY = Math.max(targetHoverY, entity.getTarget().getY());
        }

        // --- Vertical: hover at target height ---
        double verticalDiff = targetHoverY - currentPos.y;
        double vy = 0;
        if (Math.abs(verticalDiff) > 0.2) {
            vy = Math.signum(verticalDiff) * HOVER_SPEED;
        }

        // --- Horizontal: glide toward target ---
        double vx = 0, vz = 0;
        if (entity.getTarget() != null) {
            Vec3 targetPos = entity.getTarget().position();
            Vec3 direction = targetPos.subtract(currentPos).normalize();
            vx = direction.x * GLIDE_SPEED;
            vz = direction.z * GLIDE_SPEED;
        } else if (this.hasWanted()) {
            // Move toward the wanted position set by goals
            double dx = this.getWantedX() - currentPos.x;
            double dz = this.getWantedZ() - currentPos.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > 0.5) {
                vx = (dx / dist) * GLIDE_SPEED;
                vz = (dz / dist) * GLIDE_SPEED;
            }
        }

        entity.setDeltaMovement(vx, vy, vz);
        entity.hasImpulse = true;

        // Face the movement direction
        if (vx != 0 || vz != 0) {
            float yaw = (float) (Math.atan2(vz, vx) * (180.0 / Math.PI)) - 90.0F;
            entity.setYRot(yaw);
            entity.yBodyRot = yaw;
        }
    }

    /**
     * Scan downward from the entity's position to find the top of the first solid block.
     */
    private int findGroundY(BlockPos pos) {
        BlockPos.MutableBlockPos mutable = pos.mutable();
        for (int y = pos.getY(); y > entity.level().getMinBuildHeight(); y--) {
            mutable.setY(y);
            if (!entity.level().getBlockState(mutable).isAir()) {
                return y + 1; // Top of the solid block
            }
        }
        return pos.getY(); // Fallback
    }
}
