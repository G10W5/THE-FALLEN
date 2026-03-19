package com.glow.thefallen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * A dark, ethereal torch placed by the Observer.
 * Emits SOUL_FIRE_FLAME particles instead of normal fire.
 * Provides only level 4 light (barely visible, just above total darkness).
 */
public class EerieTorchBlock extends TorchBlock {

    public EerieTorchBlock() {
        super(
            ParticleTypes.SOUL_FIRE_FLAME,
            BlockBehaviour.Properties.of()
                .noCollission()
                .instabreak()
                .lightLevel(state -> 4)   // Very dim — barely lights the area
                .sound(SoundType.WOOD)
                .noOcclusion()
        );
    }

    @Override
    public void animateTick(net.minecraft.world.level.block.state.BlockState state,
                            Level level,
                            BlockPos pos,
                            RandomSource random) {
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.7D;
        double z = pos.getZ() + 0.5D;

        // Dark soul-fire flame particles
        level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0.0D, 0.0D, 0.0D);

        // Occasional large smoke puff for extra eeriness
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, x, y + 0.1D, z, 0.0D, 0.02D, 0.0D);
        }
    }
}
