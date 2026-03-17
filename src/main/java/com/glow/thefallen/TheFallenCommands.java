package com.glow.thefallen;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class TheFallenCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("thefallen")
                .requires(source -> source.hasPermission(2)) // Require OP
                .then(Commands.literal("force")
                        .then(Commands.literal("jumpscare").executes(context -> forceEvent(context.getSource(), "jumpscare")))
                        .then(Commands.literal("roof_collapse").executes(context -> forceEvent(context.getSource(), "roof_collapse")))
                        .then(Commands.literal("glitch").executes(context -> forceEvent(context.getSource(), "glitch")))
                        .then(Commands.literal("state")
                                .then(Commands.literal("observing").executes(context -> forceState(context.getSource(), TheFallenEntity.STATE_OBSERVING)))
                                .then(Commands.literal("hunting").executes(context -> forceState(context.getSource(), TheFallenEntity.STATE_HUNTING)))
                        )
                )
        );
    }

    private static int forceEvent(CommandSourceStack source, String eventType) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Must be executed by a player."));
            return 0;
        }

        // Find nearest TheFallenEntity
        List<TheFallenEntity> entities = player.level().getEntitiesOfClass(TheFallenEntity.class, 
                new AABB(player.blockPosition()).inflate(128.0D));

        if (entities.isEmpty()) {
            source.sendFailure(Component.literal("No TheFallenEntity found within 128 blocks."));
            return 0;
        }

        TheFallenEntity fallen = entities.get(0);

        switch (eventType) {
            case "jumpscare":
                fallen.forceJumpscare(player);
                source.sendSuccess(() -> Component.literal("Forced Jumpscare Event."), true);
                break;
            case "roof_collapse":
                fallen.forceRoofCollapse(player);
                source.sendSuccess(() -> Component.literal("Forced Roof Collapse Event."), true);
                break;
            case "glitch":
                fallen.forceGlitchEvent(player);
                source.sendSuccess(() -> Component.literal("Forced Glitch Event."), true);
                break;
            default:
                source.sendFailure(Component.literal("Unknown event type: " + eventType));
                return 0;
        }

        return 1;
    }

    private static int forceState(CommandSourceStack source, String state) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        List<TheFallenEntity> entities = player.level().getEntitiesOfClass(TheFallenEntity.class, 
                new AABB(player.blockPosition()).inflate(128.0D));

        if (entities.isEmpty()) {
            source.sendFailure(Component.literal("No TheFallenEntity found within 128 blocks."));
            return 0;
        }

        TheFallenEntity fallen = entities.get(0);
        fallen.setState(state);
        source.sendSuccess(() -> Component.literal("Forced state to: " + state), true);

        return 1;
    }
}
