package com.glow.thefallen.client;

import com.glow.thefallen.TheFallenEntity;
import com.glow.thefallen.TheFallenMod;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renderer for TheFallenEntity using a Player (Humanoid) model.
 * Swaps texture based on entity state:
 * - OBSERVING: Pure black (observer.png)
 * - HUNTING:   Pure white (fallen.png)
 */
public class TheFallenRenderer extends HumanoidMobRenderer<TheFallenEntity, HumanoidModel<TheFallenEntity>> {

    private static final ResourceLocation TEXTURE_OBSERVER =
            ResourceLocation.fromNamespaceAndPath(TheFallenMod.MODID, "textures/entity/observer.png");
    private static final ResourceLocation TEXTURE_FALLEN =
            ResourceLocation.fromNamespaceAndPath(TheFallenMod.MODID, "textures/entity/fallen.png");

    public TheFallenRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(TheFallenEntity entity) {
        if (entity.isHunting()) {
            return TEXTURE_FALLEN;
        }
        return TEXTURE_OBSERVER;
    }
}
