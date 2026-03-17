package com.glow.thefallen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, TheFallenMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> WHISPER = registerSound("whisper");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCREAM = registerSound("scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLITCH_PLACE = registerSound("glitch_place");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUMMING = registerSound("humming");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORLD_ENTER = registerSound("world_enter");

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(TheFallenMod.MODID, name)));
    }
}
