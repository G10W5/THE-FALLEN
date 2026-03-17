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
 * Generates an inverted obelisk high in the sky:
 * - A wide flat base at the top
 * - Narrowing downward like an upside-down pyramid
 * - Built from Deepslate, Cobbled Deepslate, and GlitchedBlocks
 */
public class InvertedObeliskFeature extends Feature<NoneFeatureConfiguration> {

    public InvertedObeliskFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        // Anchor high in the sky (between Y 160 and Y 200)
        int skyY = 160 + random.nextInt(40);
        BlockPos base = new BlockPos(origin.getX(), skyY, origin.getZ());

        // Obelisk tip at the top, layers grow wider as Y decreases -> inverted pyramid
        // We build from top (widest) down to bottom (point)
        // Layer 0 (top, widest): 9x9
        // Layer 1: 7x7
        // Layer 2: 5x5
        // Layer 3: 3x3
        // Layer 4 (bottom point): 1x1 (the "spike" pointing down)
        int[] layerSizes = {9, 7, 5, 3, 1};

        for (int layerIndex = 0; layerIndex < layerSizes.length; layerIndex++) {
            int halfSize = layerSizes[layerIndex] / 2;
            // Top layer at skyY, subsequent layers are lower
            int currentY = skyY - layerIndex;

            for (int dx = -halfSize; dx <= halfSize; dx++) {
                for (int dz = -halfSize; dz <= halfSize; dz++) {
                    BlockPos pos = base.offset(dx, currentY - skyY, dz);
                    if (level.isOutsideBuildHeight(pos.getY())) continue;
                    BlockState state = pickBlock(random, layerIndex, layerSizes.length);
                    level.setBlock(pos, state, 2);
                }
            }
        }

        // Add a 2-block vertical pillar below the tip as the "spike"
        for (int dy = 1; dy <= 3; dy++) {
            BlockPos spikePos = base.offset(0, -(layerSizes.length - 1) - dy, 0);
            if (level.isOutsideBuildHeight(spikePos.getY())) break;
            level.setBlock(spikePos, ModBlocks.GLITCHED_BLOCK.get().defaultBlockState(), 2);
        }

        return true;
    }

    private BlockState pickBlock(RandomSource random, int layerIndex, int totalLayers) {
        // More glitched blocks toward the bottom (the narrowing point)
        float glitchChance = (float) layerIndex / totalLayers;
        float r = random.nextFloat();

        if (r < glitchChance * 0.5f) {
            return ModBlocks.GLITCHED_BLOCK.get().defaultBlockState();
        } else if (r < 0.5f) {
            return Blocks.DEEPSLATE.defaultBlockState();
        } else if (r < 0.8f) {
            return Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        } else {
            return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
        }
    }
}
