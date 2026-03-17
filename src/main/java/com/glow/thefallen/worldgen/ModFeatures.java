package com.glow.thefallen.worldgen;

import com.glow.thefallen.TheFallenMod;
import com.glow.thefallen.worldgen.feature.FracturedChunkFeature;
import com.glow.thefallen.worldgen.feature.InvertedObeliskFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, TheFallenMod.MODID);

    /** Floating upside-down deepslate obelisk */
    public static final DeferredHolder<Feature<?>, InvertedObeliskFeature> INVERTED_OBELISK =
            FEATURES.register("inverted_obelisk", () -> new InvertedObeliskFeature(NoneFeatureConfiguration.CODEC));

    /** Floating fractured chunk of dirt/stone */
    public static final DeferredHolder<Feature<?>, FracturedChunkFeature> FRACTURED_CHUNK =
            FEATURES.register("fractured_chunk", () -> new FracturedChunkFeature(NoneFeatureConfiguration.CODEC));
}
