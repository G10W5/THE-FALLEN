package com.glow.thefallen;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public class GlitchedBlock extends Block {

    /**
     * Custom SoundType: metallic / glitchy sounds on hit and break.
     * Uses anvil sounds for a harsh metallic feel.
     */
    public static final SoundType GLITCH_SOUND = new SoundType(
            1.0F, 0.5F, // volume, pitch (low pitch = ominous)
            SoundEvents.ANVIL_LAND,       // break
            SoundEvents.ANVIL_HIT,        // step
            SoundEvents.ANVIL_PLACE,      // place
            SoundEvents.ANVIL_HIT,        // hit
            SoundEvents.ANVIL_FALL        // fall
    );

    public GlitchedBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(-1.0F, 3600000.0F) // Unbreakable like bedrock
                .sound(GLITCH_SOUND)
                .noLootTable()
                .isValidSpawn((state, level, pos, type) -> false)
                .lightLevel(state -> 0)
        );
    }

    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.entity.player.Player player) {
        return false; // Cannot be harvested even with tools
    }
}
