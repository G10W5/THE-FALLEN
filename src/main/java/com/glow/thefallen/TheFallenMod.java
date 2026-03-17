package com.glow.thefallen;

import com.mojang.logging.LogUtils;
 import com.glow.thefallen.worldgen.ModFeatures;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.slf4j.Logger;

@Mod(TheFallenMod.MODID)
public class TheFallenMod {
    public static final String MODID = "thefallen";
    private static final Logger LOGGER = LogUtils.getLogger();

    public TheFallenMod(IEventBus modEventBus) {
        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::registerAttributes);

        // Register all registries
        ModEntities.ENTITIES.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.ITEMS.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModFeatures.FEATURES.register(modEventBus);

        // Register game event handlers
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(TransitionHandler.class);
        NeoForge.EVENT_BUS.register(SpawnHandler.class);
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("THE FALLEN mod setup complete.");
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.THE_FALLEN.get(), TheFallenEntity.createAttributes().build());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("THE FALLEN - server starting. The Observer watches...");
    }

    @SubscribeEvent
    public void onCommandsRegister(RegisterCommandsEvent event) {
        TheFallenCommands.register(event.getDispatcher());
    }
}
