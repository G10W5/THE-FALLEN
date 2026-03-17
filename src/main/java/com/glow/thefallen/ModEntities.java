package com.glow.thefallen;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, TheFallenMod.MODID);

    // Single entity that cycles between OBSERVING (day) and HUNTING (night)
    public static final DeferredHolder<EntityType<?>, EntityType<TheFallenEntity>> THE_FALLEN = ENTITIES.register("the_fallen",
            () -> EntityType.Builder.of(TheFallenEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)  // Player-sized
                    .clientTrackingRange(64)
                    .build("the_fallen"));
}
