package com.glow.thefallen.client;

import com.glow.thefallen.TheFallenEntity;
import com.glow.thefallen.TheFallenMod;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model for TheFallenEntity.
 * Geometry/animations come from Blockbench (the_fallen.bbmodel).
 * Texture swaps with state; both currently point at temp textures
 * generated from the Blockbench UV layout — replace with AI-made
 * observer/hunter textures using the same layout.
 */
public class TheFallenModel extends GeoModel<TheFallenEntity> {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(TheFallenMod.MODID, "geo/the_fallen.geo.json");
    private static final ResourceLocation ANIMATIONS =
            ResourceLocation.fromNamespaceAndPath(TheFallenMod.MODID, "animations/the_fallen.animation.json");
    private static final ResourceLocation TEXTURE_OBSERVER =
            ResourceLocation.fromNamespaceAndPath(TheFallenMod.MODID, "textures/entity/the_fallen_observer.png");
    private static final ResourceLocation TEXTURE_HUNTER =
            ResourceLocation.fromNamespaceAndPath(TheFallenMod.MODID, "textures/entity/the_fallen_hunter.png");

    @Override
    public ResourceLocation getModelResource(TheFallenEntity entity) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(TheFallenEntity entity) {
        return entity.isHunting() ? TEXTURE_HUNTER : TEXTURE_OBSERVER;
    }

    @Override
    public ResourceLocation getAnimationResource(TheFallenEntity entity) {
        return ANIMATIONS;
    }
}
