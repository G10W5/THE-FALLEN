package com.glow.thefallen.worldgen.feature;

import com.glow.thefallen.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Generates floating fractured terrain chunks in the sky:
 * - A rough spheroid of dirt, stone, gravel and GlitchedBlocks
 * - Irregular edges to mimic a violently ripped-out piece of the ground
 * - Hovering between Y 80 and Y 140 to look "plucked" from the terrain
 */
public class FracturedChunkFeature extends Feature<NoneFeatureConfiguration> {

    public FracturedChunkFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        // Float between Y 80 and Y 140
        int floatY = 80 + random.nextInt(60);
        BlockPos center = new BlockPos(origin.getX(), floatY, origin.getZ());

        // Radius of the chunk (8-12 blocks)
        int radiusX = 6 + random.nextInt(4);
        int radiusY = 3 + random.nextInt(3);
        int radiusZ = 6 + random.nextInt(4);

        int placed = 0;

        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dy = -radiusY; dy <= radiusY; dy++) {
                for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                    // Ellipsoid equation with irregular edge noise
                    double nx = (double) dx / radiusX;
                    double ny = (double) dy / radiusY;
                    double nz = (double) dz / radiusZ;

                    double dist = nx * nx + ny * ny + nz * nz;

                    // Add some noise to the edge to make it jagged
                    double noiseMod = 0.15 * random.nextDouble();

                    if (dist > (1.0 - noiseMod)) continue;

                    BlockPos pos = center.offset(dx, dy, dz);
                    if (level.isOutsideBuildHeight(pos.getY())) continue;
                    BlockState state = pickBlock(random, dy, radiusY);
                    level.setBlock(pos, state, 2);
                    placed++;
                }
            }
        }

        // Add a few dangling "root" blocks hanging from the bottom to sell the "ripped out" look
        for (int i = 0; i < 5 + random.nextInt(8); i++) {
            int rootDx = random.nextInt(radiusX * 2 + 1) - radiusX;
            int rootDz = random.nextInt(radiusZ * 2 + 1) - radiusZ;
            int rootLength = 2 + random.nextInt(5);

            for (int dy = 1; dy <= rootLength; dy++) {
                BlockPos rootPos = center.offset(rootDx, -radiusY - dy, rootDz);
                if (level.isOutsideBuildHeight(rootPos.getY())) break;
                BlockState rootState = random.nextBoolean()
                        ? Blocks.STONE.defaultBlockState()
                        : Blocks.DIRT.defaultBlockState();
                level.setBlock(rootPos, rootState, 2);
            }
        }

        return placed > 0;
    }

    private BlockState pickBlock(RandomSource random, int dy, int radiusY) {
        // Top layer → grass/dirt, middle → stone, edges/bottom → mix with glitch
        float edgeFactor = Math.abs((float) dy / radiusY);
        float r = random.nextFloat();

        // Very high glitch chance near edges/bottom
        if (edgeFactor > 0.75f && r < 0.25f) {
            return ModBlocks.GLITCHED_BLOCK.get().defaultBlockState();
        }

        if (dy >= radiusY - 1) {
            // Top surface: grass
            return random.nextBoolean() ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        } else if (dy <= -radiusY + 1) {
            // Bottom: exposed stone/gravel
            return random.nextFloat() < 0.6f ? Blocks.STONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState();
        } else {
            // Interior: layered dirt and stone
            if (r < 0.4f) return Blocks.DIRT.defaultBlockState();
            else if (r < 0.7f) return Blocks.STONE.defaultBlockState();
            else if (r < 0.85f) return Blocks.GRAVEL.defaultBlockState();
            else return Blocks.COBBLESTONE.defaultBlockState();
        }
    }
}
