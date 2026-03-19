package com.glow.thefallen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, TheFallenMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> WHISPER        = registerSound("whisper");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCREAM         = registerSound("scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLITCH_PLACE   = registerSound("glitch_place");
    public static final DeferredHolder<SoundEvent, SoundEvent> HUMMING        = registerSound("humming");
    public static final DeferredHolder<SoundEvent, SoundEvent> WORLD_ENTER    = registerSound("world_enter");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROOF_CRACK     = registerSound("roof_crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> SUNRISE_VANISH = registerSound("sunrise_vanish");
    public static final DeferredHolder<SoundEvent, SoundEvent> ECHO_STEP      = registerSound("echo_step");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLITCH_EXPLOSION = registerSound("glitch_explosion");

    // New sounds for ambient horror
    /** Played 5-10 blocks behind the player — sounds like footsteps */
    public static final DeferredHolder<SoundEvent, SoundEvent> PHANTOM_STEP   = registerSound("phantom_step");
    /** Mimicry: distorted version of a player eating */
    public static final DeferredHolder<SoundEvent, SoundEvent> MIMICRY_EAT    = registerSound("mimicry_eat");
    /** Mimicry: distorted version of a chest opening */
    public static final DeferredHolder<SoundEvent, SoundEvent> MIMICRY_CHEST  = registerSound("mimicry_chest");
    /** Mimicry: distorted player hurt sound */
    public static final DeferredHolder<SoundEvent, SoundEvent> MIMICRY_HURT   = registerSound("mimicry_hurt");
    /** Played when the Hunter fake-dies and collapses */
    public static final DeferredHolder<SoundEvent, SoundEvent> FAKE_DEATH     = registerSound("fake_death");
    /** Played when the Hunter reappears after fake-death */
    public static final DeferredHolder<SoundEvent, SoundEvent> RESURRECTION   = registerSound("resurrection");

    private static DeferredHolder<SoundEvent, SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(TheFallenMod.MODID, name)));
    }
}
