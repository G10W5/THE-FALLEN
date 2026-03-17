package com.glow.thefallen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import com.glow.thefallen.worldgen.feature.InvertedObeliskFeature;
import com.glow.thefallen.worldgen.feature.FracturedChunkFeature;

/**
 * A single entity that cycles between two states:
 * - OBSERVING (Daytime): Invisible, stalks the player, triggers glitch events on eye contact.
 * - HUNTING  (Nighttime): Visible, hovers above ground, breaks blocks to reach the player.
 */
public class TheFallenEntity extends Monster {

    // Synced data: "observing" or "hunting"
    private static final EntityDataAccessor<String> STATE =
            SynchedEntityData.defineId(TheFallenEntity.class, EntityDataSerializers.STRING);

    public static final String STATE_OBSERVING = "observing";
    public static final String STATE_HUNTING = "hunting";

    // Visibility flicker: during OBSERVING, the entity occasionally becomes briefly visible
    private int flickerCooldown = 0;
    private int flickerDuration = 0;
    private boolean isFlickering = false;
    private int awayFromTargetTicks = 0;
    
    // Command Force Flags
    public boolean forceJumpscare = false;
    public boolean forceGlitch = false;
    public boolean forceRoofCollapse = false;
    public Player forcedTarget = null;
    
    /** Set to true while FakeChargeGoal is running so the flicker tick doesn't override visibility */
    public boolean isChargingJumpscare = false;
    
    /**
     * General "force visible" flag. When true the flicker logic will NOT force the entity invisible.
     * Set this to true whenever an event needs the entity to stay visible (glitch, jumpscare charge).
     */
    public boolean isForceVisible = false;
    
    /** Cooldown for proximity structure spawning - starts at 12000 (10 min) */
    private int structureSpawnCooldown = 12000;
    
    /**
     * Startup delay before random blindness/nausea can trigger.
     * 3600 ticks = 3 minutes. Entity must exist for 3 min before the effect fires.
     */
    private int effectStartupDelay = 3600;

    public TheFallenEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        // Default to observing, will be set properly via NBT or TransitionHandler
        this.moveControl = new HoverMoveControl(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.45D) // Increased speed for scary chase
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, STATE_OBSERVING);
    }

    @Override
    protected void registerGoals() {
        // Priority 0: Don't drown
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // OBSERVING goals (daytime)
        this.goalSelector.addGoal(1, new GlitchEventGoal(this));
        this.goalSelector.addGoal(2, new FakeChargeGoal(this));
        this.goalSelector.addGoal(3, new StalkPlayerGoal(this));

        // HUNTING goals (nighttime)
        this.goalSelector.addGoal(1, new BreakBlockGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, false) {
            @Override
            public boolean canUse() {
                return TheFallenEntity.this.isHunting() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return TheFallenEntity.this.isHunting() && super.canContinueToUse();
            }
        });

        // Shared
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 64.0F));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---- State Management ----

    public String getState() {
        return this.entityData.get(STATE);
    }

    public void setState(String state) {
        this.entityData.set(STATE, state);
        applyStateEffects();
    }

    public boolean isObserving() {
        return STATE_OBSERVING.equals(getState());
    }

    public boolean isHunting() {
        return STATE_HUNTING.equals(getState());
    }

    /**
     * Apply visual and behavioral changes based on the current state.
     */
    private void applyStateEffects() {
        if (isObserving()) {
            this.setInvisible(true);
            this.setInvulnerable(true);
            this.setNoGravity(false);
        } else {
            // HUNTING: visible, vulnerable, hover movement
            this.setInvisible(false);
            this.setInvulnerable(false);
            this.setNoGravity(true); // Hover mode
        }
    }

    @Override
    public void tick() {
        super.tick();

        // --- Visibility Flicker (OBSERVING only) ---
        if (isObserving() && !level().isClientSide) {
            if (isFlickering) {
                // Currently flickering visible
                flickerDuration--;
                this.setInvisible(false); // Briefly visible!
                if (flickerDuration <= 0) {
                    isFlickering = false;
                    // Only go invisible if nothing else is forcing us to be visible
                    if (!isForceVisible && !isChargingJumpscare) {
                        this.setInvisible(true);
                    }
                    flickerCooldown = 60 + getRandom().nextInt(120); // 3-9 seconds until next flicker
                }
            } else {
                flickerCooldown--;
                if (flickerCooldown <= 0) {
                    // Trigger flicker only if no event is forcing visibility
                    if (!isForceVisible && !isChargingJumpscare) {
                        isFlickering = true;
                        flickerDuration = 10 + getRandom().nextInt(10); // Visible for 0.5-1.0s
                        // Play a creepy ambient sound when flickering
                        level().playSound(null, getX(), getY(), getZ(),
                                ModSounds.WHISPER.get(), SoundSource.AMBIENT, 0.8F, 0.8F);
                    } else {
                        // Reset cooldown silently — will try again after event is over
                        flickerCooldown = 20;
                    }
                }
                // Stay invisible — but only if no event is forcing visibility
                if (!isChargingJumpscare && !isForceVisible) {
                    this.setInvisible(true);
                }
            }
            
            // Random whisper chance even when NOT flickering
            if (getRandom().nextFloat() < 0.005F) {
                 level().playSound(null, getX(), getY(), getZ(),
                         ModSounds.WHISPER.get(), SoundSource.AMBIENT, 0.5F, 1.0F);
            }
            
            // Only enforce invulnerability when NOT in a jumpscare
            if (!isChargingJumpscare) {
                this.setInvulnerable(true);
                this.setNoGravity(false);
            }

            // Structure proximity spawner: every ~10 min, place an eerie structure near the player
            structureSpawnCooldown--;
            if (structureSpawnCooldown <= 0) {
                structureSpawnCooldown = 12000 + getRandom().nextInt(2400); // 10-12 min randomness
                Player nearest = level().getNearestPlayer(this, -1.0D);
                if (nearest != null && !level().isClientSide) {
                    // Pick a random position 64-128 blocks away from the player
                    double angle = getRandom().nextFloat() * Math.PI * 2;
                    double dist = 64 + getRandom().nextInt(64);
                    double tx = nearest.getX() + Math.cos(angle) * dist;
                    double tz = nearest.getZ() + Math.sin(angle) * dist;
                    BlockPos spawnPos = BlockPos.containing(tx, nearest.getY(), tz);

                    // Use the worldgen feature logic directly (server-side only)
                    if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        boolean spawnObelisk = getRandom().nextBoolean();
                        net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<NoneFeatureConfiguration> ctx =
                            new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<>(
                                java.util.Optional.empty(),
                                serverLevel,
                                serverLevel.getChunkSource().getGenerator(),
                                getRandom(),
                                spawnPos,
                                NoneFeatureConfiguration.INSTANCE
                            );
                        if (spawnObelisk) {
                            new InvertedObeliskFeature(NoneFeatureConfiguration.CODEC).place(ctx);
                        } else {
                            new FracturedChunkFeature(NoneFeatureConfiguration.CODEC).place(ctx);
                        }
                    }
                }
            }

            // Random Blindness + Nausea: ~once every 5 minutes (6000 ticks)
            // Has a 3-minute startup delay before it can fire for the first time.
            if (effectStartupDelay > 0) {
                effectStartupDelay--;
            } else if (!isChargingJumpscare && getRandom().nextInt(6000) == 0) {
                Player nearest2 = level().getNearestPlayer(this, 32.0D);
                if (nearest2 instanceof net.minecraft.server.level.ServerPlayer sp) {
                    sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
                    sp.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
                }
            }
        } else if (isHunting()) {
            this.setInvisible(false);
            this.setInvulnerable(false);
            this.setNoGravity(true);
            
            // Periodic humming sound during the hunt
            if (tickCount % 40 == 0) {
                 level().playSound(null, getX(), getY(), getZ(),
                         ModSounds.HUMMING.get(), SoundSource.HOSTILE, 1.0F, 0.5F);
            }
        }
        
        // --- Proximity Leash ---
        if (!level().isClientSide) {
            Player nearest = level().getNearestPlayer(this, -1.0D);
            if (nearest != null) {
                if (this.distanceToSqr(nearest) > 64 * 64) {
                    awayFromTargetTicks++;
                    if (awayFromTargetTicks > 100) {
                        // Force teleport near the player (approx 15 blocks behind their look angle)
                        net.minecraft.world.phys.Vec3 look = nearest.getLookAngle();
                        double tx = nearest.getX() - look.x * 15;
                        double tz = nearest.getZ() - look.z * 15;
                        
                        BlockPos pos = BlockPos.containing(tx, nearest.getY(), tz);
                        if (level().canSeeSky(nearest.blockPosition())) {
                            int surfaceY = level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos).getY();
                            this.teleportTo(tx, Math.max(surfaceY + 1.0, nearest.getY()), tz);
                        } else {
                            this.teleportTo(tx, nearest.getY(), tz); // Cave teleport
                        }
                        awayFromTargetTicks = 0;
                    }
                } else {
                    awayFromTargetTicks = 0;
                }
            }
        }
    }

    // ---- Command Force Methods ----
    public void forceJumpscare(Player player) {
        this.forcedTarget = player;
        this.forceJumpscare = true;
    }

    public void forceGlitchEvent(Player player) {
        this.forcedTarget = player;
        this.forceGlitch = true;
    }

    public void forceRoofCollapse(Player player) {
        this.forcedTarget = player;
        this.forceRoofCollapse = true;
    }

    // ---- Collision & Persistence ----
    
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false; // Prevent despawning when player dies or runs away
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return !isObserving(); // Only collidable when hunting
    }

    // ---- NBT Persistence ----

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putString("FallenState", getState());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("FallenState")) {
            setState(compound.getString("FallenState"));
        } else {
            setState(STATE_OBSERVING);
        }
    }
}
