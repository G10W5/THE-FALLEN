package com.glow.thefallen.client;

import com.glow.thefallen.ModSounds;
import com.glow.thefallen.TheFallenEntity;
import com.glow.thefallen.TheFallenMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.List;

/**
 * Client-side look-triggered jumpscare.
 *
 * How it works: every few client ticks, checks whether the local player is
 * looking directly at a VISIBLE TheFallenEntity within range (mirrors the
 * server-side eye-contact test in GlitchEventGoal). On trigger, shows a
 * fullscreen face overlay with jitter + red flash for ~2 seconds and plays
 * the scream locally. No packets needed — purely presentational.
 */
@EventBusSubscriber(modid = TheFallenMod.MODID, value = Dist.CLIENT)
public class JumpscareOverlay {

    private static final ResourceLocation FACE =
            ResourceLocation.fromNamespaceAndPath(TheFallenMod.MODID, "textures/gui/jumpscare.png");

    private static final double RANGE = 24.0D;
    private static final int DURATION_TICKS = 40;
    private static final int COOLDOWN_TICKS = 3600; // 3 min between scares

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

        AABB search = mc.player.getBoundingBox().inflate(RANGE);
        List<TheFallenEntity> entities = mc.level.getEntitiesOfClass(TheFallenEntity.class, search);
        if (entities.isEmpty()) return;

        // Extended crosshair test: only fires when the crosshair ray from the
        // player's eyes actually strikes the entity (no wall in between).
        // Vanilla crosshair only reaches ~3 blocks, so this replicates it at
        // mod range instead of using a loose look-direction cone.
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(RANGE));
        AABB rayBox = mc.player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                mc.player, eye, end, rayBox,
                e -> e instanceof TheFallenEntity f && f.isAlive() && !f.isInvisible(), 0.0F);
        if (entityHit == null || !(entityHit.getEntity() instanceof TheFallenEntity)) return;

        BlockHitResult blockHit = mc.level.clip(
                new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        if (blockHit.getType() != HitResult.Type.MISS
                && blockHit.getLocation().distanceToSqr(eye)
                        < entityHit.getLocation().distanceToSqr(eye)) {
            return;
        }
        trigger();
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
