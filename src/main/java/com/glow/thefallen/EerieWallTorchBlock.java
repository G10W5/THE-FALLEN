package com.glow.thefallen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wall-mounted variant of the Eerie Torch.
 */
public class EerieWallTorchBlock extends WallTorchBlock {

    public EerieWallTorchBlock() {
        super(
            ParticleTypes.SOUL_FIRE_FLAME,
            BlockBehaviour.Properties.of()
                .noCollission()
                .instabreak()
                .lightLevel(state -> 4)
                .sound(SoundType.WOOD)
                .noOcclusion()
        );
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5D + facing.getOpposite().getStepX() * 0.27D;
        double y = pos.getY() + 0.7D;
        double z = pos.getZ() + 0.5D + facing.getOpposite().getStepZ() * 0.27D;

        level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0.0D, 0.0D, 0.0D);
        if (random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, x, y + 0.1D, z, 0.0D, 0.02D, 0.0D);
        }
    }
}
