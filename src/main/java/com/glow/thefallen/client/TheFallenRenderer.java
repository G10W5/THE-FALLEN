package com.glow.thefallen.client;

import com.glow.thefallen.TheFallenEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * GeckoLib renderer for TheFallenEntity.
 * Model is 44 units tall (~2.75 blocks); scaled to ~2 blocks to match gameplay.
 * Skips rendering while invisible (Observer stalk / flicker gaps / fake-death).
 */
public class TheFallenRenderer extends GeoEntityRenderer<TheFallenEntity> {

    public TheFallenRenderer(EntityRendererProvider.Context context) {
        super(context, new TheFallenModel());
        this.shadowRadius = 0.5F;
        this.withScale(0.75F);
    }

    @Override
    public void render(TheFallenEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        if (entity.isInvisible()) return;
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
