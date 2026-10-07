package com.glow.thefallen.client;

import com.glow.thefallen.ModSounds;
import com.glow.thefallen.TheFallenEntity;
import com.glow.thefallen.TheFallenMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;

/**
 * Client-side jumpscare face-flash.
 *
 * Fully event-driven: the server raises a synced flag on the entity exactly
 * when the glitch eye-contact resolves or a charge connects, and this overlay
 * shows the fullscreen face for ~2 seconds. It never self-triggers on look
 * direction, so no random pop-ins.
 */
@EventBusSubscriber(modid = TheFallenMod.MODID, value = Dist.CLIENT)
public class JumpscareOverlay {

    private static final ResourceLocation FACE =
            ResourceLocation.fromNamespaceAndPath(TheFallenMod.MODID, "textures/gui/jumpscare.png");

    private static final double RANGE = 64.0D;
    private static final int DURATION_TICKS = 40;
    private static final int COOLDOWN_TICKS = 1200; // 1 min between flashes

    private static int activeTicks = 0;
    private static int cooldownTicks = 0;
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (activeTicks > 0) {
            activeTicks--;
            return;
        }
        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.isPaused()) return;
        if (mc.screen != null) return; // don't fire while a GUI is open

        tickCounter++;
        if (tickCounter % 5 != 0) return;

        // Event-driven: the server raises the flag exactly when the glitch
        // eye-contact resolves or a charge connects. No independent
        // look-detection here, so no random pop-ins.
        AABB search = mc.player.getBoundingBox().inflate(RANGE);
        List<TheFallenEntity> entities = mc.level.getEntitiesOfClass(TheFallenEntity.class, search);
        for (TheFallenEntity entity : entities) {
            if (entity.isShowingScare()) {
                trigger();
                break;
            }
        }
    }

    private static void trigger() {
        activeTicks = DURATION_TICKS;
        cooldownTicks = COOLDOWN_TICKS;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(ModSounds.SCREAM.get(), 2.0F, 0.5F);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (activeTicks <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // Intensity ramps up then cuts out at the end
        float progress = 1.0F - (activeTicks / (float) DURATION_TICKS);
        float shake = progress < 0.85F ? 1.0F : (1.0F - progress) * 6.0F;

        int dx = (int) ((Math.random() - 0.5) * 24.0 * shake);
        int dy = (int) ((Math.random() - 0.5) * 18.0 * shake);

        // Darken first so the face pops
        graphics.fill(0, 0, width, height, 0xCC000000);
        // Fullscreen face, stretched from the 256x256 texture, jittered for shake
        graphics.blit(FACE, dx, dy, 0, 0, width, height, 256, 256);
        // Red flash overlay, strongest at the start
        int alpha = (int) (90.0F * (1.0F - progress) + 20.0F);
        graphics.fill(0, 0, width, height, (alpha << 24) | 0x00FF0000);
    }
}
