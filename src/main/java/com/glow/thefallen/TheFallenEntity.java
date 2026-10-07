package com.glow.thefallen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import com.glow.thefallen.worldgen.feature.InvertedObeliskFeature;
import com.glow.thefallen.worldgen.feature.FracturedChunkFeature;

import java.util.Optional;

/**
 * A single entity that cycles between two states:
 * - OBSERVING (Daytime): Invisible, stalks player, triggers glitch events, places eerie blocks.
 * - HUNTING  (Nighttime): Visible, ground AI, breaks walls to reach player.
 *
 * New mechanics:
 * - Shadow Phase: On being hit, teleports 5 blocks behind attacker with smoke.
 * - Fake Death: At ≤50 health (25 hearts), collapses visibly for 30 seconds
 *   (invulnerable, passive) then resurges.
 * - Ambient Mimicry: Plays distorted vanilla sounds near the player.
 * - Cave-Only Light Sabotage: Only destroys torches/lanterns in darkness (sky light = 0).
 */
public class TheFallenEntity extends Monster implements GeoEntity {

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<String> STATE =
            SynchedEntityData.defineId(TheFallenEntity.class, EntityDataSerializers.STRING);
    /** Synced so the client-side GeckoLib controller sees collapse/charge states. */
    private static final EntityDataAccessor<Boolean> DATA_RECOVERING =
            SynchedEntityData.defineId(TheFallenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_CHARGING =
            SynchedEntityData.defineId(TheFallenEntity.class, EntityDataSerializers.BOOLEAN);

    private boolean lastSyncedRecovering = false;
    private boolean lastSyncedCharging = false;

    public static final String STATE_OBSERVING = "observing";
    public static final String STATE_HUNTING   = "hunting";

    // --- Flags ---
    private int flickerCooldown      = 0;
    private int flickerDuration      = 0;
    private boolean isFlickering     = false;
    private int awayFromTargetTicks  = 0;

    public boolean forceJumpscare    = false;
    public boolean forceGlitch       = false;
    public boolean forceRoofCollapse = false;
    public Player  forcedTarget      = null;
    public boolean isChargingJumpscare = false;
    public boolean isForceVisible    = false;

    // --- Fake-Death State ---
    /** True while the Hunter is in fake-death (recovering). No AI, invisible, invulnerable. */
    public boolean isRecovering = false;
    /** Counts down from 600 (30 seconds) while recovering. */
    private int recoveryTicks = 0;
    /** Blocks Shadow Phase from triggering more than once immediately after a hit. */
    private int shadowPhaseCooldown = 0;

    // --- Timers ---
    private int structureSpawnCooldown = 12000;
    private int effectStartupDelay     = 3600;
    /** Cooldown between ambient mimicry/footstep plays (randomised per event). */
    private int ambientSoundCooldown   = 200;

    public TheFallenEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.42D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.FOLLOW_RANGE, 2048.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, STATE_OBSERVING);
        builder.define(DATA_RECOVERING, false);
        builder.define(DATA_CHARGING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // OBSERVING goals
        this.goalSelector.addGoal(1, new GlitchEventGoal(this));
        this.goalSelector.addGoal(2, new FakeChargeGoal(this));
        this.goalSelector.addGoal(3, new StalkPlayerGoal(this));
        // HUNTING goals
        this.goalSelector.addGoal(1, new BreakBlockGoal(this));
        this.goalSelector.addGoal(2, new DirectChaseGoal(this));
        // Shared
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 64.0F));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, this::animPredicate));
    }

    private PlayState animPredicate(AnimationState<TheFallenEntity> state) {
        // Server logic writes plain booleans; the client copy only sees them
        // via synced entity data — read the right source per side.
        boolean recovering = this.level().isClientSide
                ? this.entityData.get(DATA_RECOVERING) : this.isRecovering;
        boolean charging = this.level().isClientSide
                ? this.entityData.get(DATA_CHARGING) : this.isChargingJumpscare;
        if (recovering) {
            return state.setAndContinue(RawAnimation.begin()
                    .then("animation.the_fallen.fake_death", Animation.LoopType.HOLD_ON_LAST_FRAME));
        }
        if (charging) {
            return state.setAndContinue(RawAnimation.begin()
                    .then("animation.the_fallen.jumpscare", Animation.LoopType.PLAY_ONCE));
        }
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive() && this.distanceToSqr(target) < 9.0D) {
            return state.setAndContinue(RawAnimation.begin()
                    .then("animation.the_fallen.attack", Animation.LoopType.PLAY_ONCE));
        }
        if (state.isMoving()) {
            if (isHunting()) {
                return state.setAndContinue(RawAnimation.begin()
                        .then("animation.the_fallen.run", Animation.LoopType.LOOP));
            }
            return state.setAndContinue(RawAnimation.begin()
                    .then("animation.the_fallen.walk", Animation.LoopType.LOOP));
        }
        return state.setAndContinue(RawAnimation.begin()
                .then("animation.the_fallen.idle", Animation.LoopType.LOOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // =========================================================
    // State Management
    // =========================================================

    public String getState() { return this.entityData.get(STATE); }

    public void setState(String state) {
        this.entityData.set(STATE, state);
        applyStateEffects();
    }

    public boolean isObserving() { return STATE_OBSERVING.equals(getState()); }
    public boolean isHunting()   { return STATE_HUNTING.equals(getState()); }

    private void applyStateEffects() {
        if (isObserving()) {
            this.setInvisible(true);
            this.setInvulnerable(true);
            this.setNoGravity(false);
        } else {
            // HUNTING: becomes vulnerable to trigger Shadow Phase / Fake Death
            this.setInvisible(false);
            this.setInvulnerable(false);
            this.setNoGravity(false);
        }
    }

    // =========================================================
    // Shadow Phase & Fake Death  — hurt() override
    // =========================================================

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Ignore damage while observing or recovering
        if (isObserving() || isRecovering) return false;

        // Ignore environmental damage (fire, fall, etc.)
        if (source.getDirectEntity() == null) return false;

        Player attacker = (source.getEntity() instanceof Player p) ? p : null;

        // --- Fake Death at ≤ 50 HP (25 hearts) ---
        float currentHp = this.getHealth();
        if (currentHp - amount <= 50.0F && !isRecovering) {
            triggerFakeDeath();
            return false; // no real damage
        }

        // --- Shadow Phase: teleport 5 blocks behind attacker ---
        if (shadowPhaseCooldown <= 0 && attacker != null) {
            shadowPhaseCooldown = 40; // 2-second cooldown to prevent spam
            Vec3 look = attacker.getLookAngle();
            double tx = attacker.getX() - look.x * 5;
            double ty = attacker.getY();
            double tz = attacker.getZ() - look.z * 5;

            // Smoke burst at old position
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                    getX(), getY() + 1.0, getZ(), 20, 0.4, 0.5, 0.4, 0.02);
            }
            this.teleportTo(tx, ty, tz);
            // Smoke burst at new position
            if (this.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                    tx, ty + 1.0, tz, 20, 0.4, 0.5, 0.4, 0.02);
            }

            // Absorb the hit (shadow phase negates this blow)
            return false;
        }

        return super.hurt(source, amount);
    }

    private void triggerFakeDeath() {
        if (!(this.level() instanceof ServerLevel sl)) return;

        isRecovering = true;
        recoveryTicks = 600; // 30 seconds

        // Visual / Audio: scream + collapse smoke
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE,       getX(), getY() + 1.0, getZ(), 30, 0.5, 0.5, 0.5, 0.05);
        this.level().playSound(null, getX(), getY(), getZ(),
                ModSounds.FAKE_DEATH.get(), SoundSource.HOSTILE, 2.0F, 1.0F);

        // Visible collapse: the body falls (fake_death anim) and lies on the
        // ground for 30 s. Stays invulnerable + passive while "dead".
        this.setInvisible(false);
        this.setInvulnerable(true);
        this.getNavigation().stop();
        this.setHealth(100.0F); // Restore health silently
    }

    // =========================================================
    // Main Tick
    // =========================================================

    @Override
    public void tick() {
        super.tick();

        // Advance shadow-phase cooldown
        if (shadowPhaseCooldown > 0) shadowPhaseCooldown--;

        // Mirror collapse/charge flags to the client for the anim controller
        if (!this.level().isClientSide) {
            if (lastSyncedRecovering != isRecovering) {
                this.entityData.set(DATA_RECOVERING, isRecovering);
                lastSyncedRecovering = isRecovering;
            }
            if (lastSyncedCharging != isChargingJumpscare) {
                this.entityData.set(DATA_CHARGING, isChargingJumpscare);
                lastSyncedCharging = isChargingJumpscare;
            }
        }

        // --- Fake-Death Recovery ---
        if (isRecovering) {
            recoveryTicks--;
            this.setInvisible(false); // corpse stays visible while "dead"
            this.setInvulnerable(true);
            this.getNavigation().stop();
            this.setTarget(null);
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0);
            if (recoveryTicks <= 0) {
                isRecovering = false;
                this.setInvisible(false);
                this.setInvulnerable(false);
                // Reappear with dramatic effect
                if (this.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.0, getZ(), 40, 0.5, 1.0, 0.5, 0.05);
                }
                this.level().playSound(null, getX(), getY(), getZ(),
                        ModSounds.RESURRECTION.get(), SoundSource.HOSTILE, 2.0F, 0.7F);
            }
            return; // Skip all other logic while recovering
        }

        if (isObserving() && !this.level().isClientSide) {
            // --- Visibility Flicker ---
            if (isFlickering) {
                flickerDuration--;
                this.setInvisible(false);
                if (flickerDuration <= 0) {
                    isFlickering = false;
                    if (!isForceVisible && !isChargingJumpscare) this.setInvisible(true);
                    flickerCooldown = 60 + this.random.nextInt(120);
                }
            } else {
                flickerCooldown--;
                if (flickerCooldown <= 0) {
                    if (!isForceVisible && !isChargingJumpscare) {
                        isFlickering = true;
                        flickerDuration = 10 + this.random.nextInt(10);
                        this.level().playSound(null, getX(), getY(), getZ(),
                                ModSounds.WHISPER.get(), SoundSource.AMBIENT, 0.8F, 0.8F);
                    } else {
                        flickerCooldown = 20;
                    }
                }
                if (!isChargingJumpscare && !isForceVisible) this.setInvisible(true);
            }

            if (this.random.nextFloat() < 0.005F) {
                this.level().playSound(null, getX(), getY(), getZ(),
                        ModSounds.WHISPER.get(), SoundSource.AMBIENT, 0.5F, 1.0F);
            }

            if (!isChargingJumpscare) {
                this.setInvulnerable(true);
                this.setNoGravity(false);
            }

            // --- Structure Spawner ---
            structureSpawnCooldown--;
            if (structureSpawnCooldown <= 0) {
                structureSpawnCooldown = 12000 + this.random.nextInt(2400);
                Player nearest = this.level().getNearestPlayer(this, -1.0D);
                if (nearest != null) {
                    double angle = this.random.nextFloat() * Math.PI * 2;
                    double dist  = 64 + this.random.nextInt(64);
                    double tx    = nearest.getX() + Math.cos(angle) * dist;
                    double tz    = nearest.getZ() + Math.sin(angle) * dist;
                    BlockPos spawnPos = BlockPos.containing(tx, nearest.getY(), tz);
                    if (this.level() instanceof ServerLevel sl) {
                        net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<NoneFeatureConfiguration> ctx =
                            new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<>(
                                Optional.empty(), sl, sl.getChunkSource().getGenerator(),
                                this.random, spawnPos, NoneFeatureConfiguration.INSTANCE);
                        if (this.random.nextBoolean()) new InvertedObeliskFeature(NoneFeatureConfiguration.CODEC).place(ctx);
                        else                           new FracturedChunkFeature(NoneFeatureConfiguration.CODEC).place(ctx);
                    }
                }
            }

            // --- Eerie Events: Eerie Torch or Sign ---
            if (this.random.nextInt(6000) == 0) {
                Player p = this.level().getNearestPlayer(this, 32.0D);
                if (p != null) {
                    double angle    = this.random.nextFloat() * Math.PI * 2;
                    double dist     = 5 + this.random.nextInt(5);
                    BlockPos ePos   = BlockPos.containing(
                        p.getX() + Math.cos(angle) * dist, p.getY(),
                        p.getZ() + Math.sin(angle) * dist);
                    while (this.level().getBlockState(ePos).isAir() && ePos.getY() > this.level().getMinBuildHeight())
                        ePos = ePos.below();
                    ePos = ePos.above();
                    if (this.level().getBlockState(ePos).isAir()) {
                        if (this.random.nextBoolean()) {
                            this.level().setBlockAndUpdate(ePos, ModBlocks.EERIE_TORCH.get().defaultBlockState());
                        } else {
                            this.level().setBlockAndUpdate(ePos, Blocks.OAK_SIGN.defaultBlockState());
                            if (this.level().getBlockEntity(ePos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
                                String[] msgs = {"I SEE YOU", "RUN", "DON'T LOOK BACK", "HELP ME"};
                                sign.getFrontText().setMessage(0, net.minecraft.network.chat.Component.literal(
                                    msgs[this.random.nextInt(msgs.length)]));
                                sign.setChanged();
                                // Push the text to clients — setChanged() alone only marks
                                // the chunk dirty for saving, it does NOT sync the BE.
                                BlockState placed = this.level().getBlockState(ePos);
                                this.level().sendBlockUpdated(ePos, placed, placed, 3);
                            }
                        }
                    }
                }
            }

            // --- Ambient Mimicry (OBSERVING) ---
            ambientSoundCooldown--;
            if (ambientSoundCooldown <= 0) {
                ambientSoundCooldown = 300 + this.random.nextInt(600); // 15-45 seconds
                Player p = this.level().getNearestPlayer(this, 24.0D);
                if (p != null) {
                    // Place the sound 5-10 blocks behind or beside the player
                    Vec3 look = p.getLookAngle();
                    double ox = -look.x * (5 + this.random.nextInt(5)) + (this.random.nextDouble() - 0.5) * 4;
                    double oz = -look.z * (5 + this.random.nextInt(5)) + (this.random.nextDouble() - 0.5) * 4;
                    double sx = p.getX() + ox;
                    double sy = p.getY();
                    double sz = p.getZ() + oz;

                    int pick = this.random.nextInt(4);
                    if (pick == 0) this.level().playSound(null, sx, sy, sz,
                            ModSounds.PHANTOM_STEP.get(), SoundSource.HOSTILE, 0.5F, 1.0F);
                    else if (pick == 1) this.level().playSound(null, sx, sy, sz,
                            ModSounds.MIMICRY_EAT.get(), SoundSource.HOSTILE, 0.6F, 1.0F);
                    else if (pick == 2) this.level().playSound(null, sx, sy, sz,
                            ModSounds.MIMICRY_CHEST.get(), SoundSource.HOSTILE, 0.6F, 1.0F);
                    else this.level().playSound(null, sx, sy, sz,
                            ModSounds.MIMICRY_HURT.get(), SoundSource.HOSTILE, 0.6F, 1.0F);
                }
            }

            // --- Random Blindness ---
            if (effectStartupDelay > 0) {
                effectStartupDelay--;
            } else if (!isChargingJumpscare && this.random.nextInt(6000) == 0) {
                Player nearest2 = this.level().getNearestPlayer(this, 32.0D);
                if (nearest2 instanceof ServerPlayer sp) {
                    sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
                    sp.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
                }
            }

        } else if (isHunting()) {
            this.setInvisible(false);
            this.setInvulnerable(false); // Allow Shadow Phase damage handling
            this.setNoGravity(false);

            if (this.tickCount % 40 == 0) {
                this.level().playSound(null, getX(), getY(), getZ(),
                        ModSounds.HUMMING.get(), SoundSource.HOSTILE, 1.0F, 0.5F);
            }

            if (!this.level().isClientSide) {
                for (int i = 0; i < 2; i++) {
                    double offX = (this.random.nextDouble() - 0.5) * 0.5;
                    double offZ = (this.random.nextDouble() - 0.5) * 0.5;
                    ((ServerLevel) this.level()).sendParticles(
                            ParticleTypes.ASH, getX() + offX, getY() + 1.0, getZ() + offZ, 1, 0, 0.1, 0, 0.02);
                }

                // --- Cave-Only Light Sabotage ---
                // Only destroy light sources where there is NO sky exposure (i.e. inside caves).
                if (this.tickCount % 5 == 0) {
                    BlockPos center = blockPosition();
                    for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(2, 2, 2))) {
                        // Sky light level 0 means no sunlight reaches this block → it's underground/cave
                        if (this.level().getBrightness(LightLayer.SKY, pos) == 0) {
                            BlockState bs = this.level().getBlockState(pos);
                            if (bs.is(BlockTags.CANDLES) ||
                                bs.is(BlockTags.CAMPFIRES) ||
                                bs.getBlock() instanceof TorchBlock ||
                                bs.getBlock() instanceof LanternBlock) {
                                this.level().destroyBlock(pos, false);
                                this.level().playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.HOSTILE, 1.0F, 0.5F);
                            }
                        }
                    }
                }

                // --- Ambient Mimicry (HUNTING) ---
                ambientSoundCooldown--;
                if (ambientSoundCooldown <= 0) {
                    ambientSoundCooldown = 200 + this.random.nextInt(400);
                    Player p = this.level().getNearestPlayer(this, 24.0D);
                    if (p != null) {
                        Vec3 look = p.getLookAngle();
                        double ox = -look.x * (5 + this.random.nextInt(5)) + (this.random.nextDouble() - 0.5) * 4;
                        double oz = -look.z * (5 + this.random.nextInt(5)) + (this.random.nextDouble() - 0.5) * 4;
                        int pick = this.random.nextInt(2);
                        if (pick == 0) this.level().playSound(null, p.getX() + ox, p.getY(), p.getZ() + oz,
                                ModSounds.PHANTOM_STEP.get(), SoundSource.HOSTILE, 0.5F, 1.0F);
                        else           this.level().playSound(null, p.getX() + ox, p.getY(), p.getZ() + oz,
                                ModSounds.MIMICRY_HURT.get(), SoundSource.HOSTILE, 0.5F, 1.0F);
                    }
                }
            }

            // --- Weeping Angel Speed ---
            Player nearestHunt = this.level().getNearestPlayer(this, 48.0D);
            if (nearestHunt != null) {
                Vec3 toMe = this.getEyePosition().subtract(nearestHunt.getEyePosition()).normalize();
                double dot = toMe.dot(nearestHunt.getLookAngle().normalize());
                boolean playerLookingAtMe = dot > 0.85 && nearestHunt.hasLineOfSight(this);
                double speed = playerLookingAtMe ? 0.30D : 0.42D;
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
            } else {
                this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.42D);
            }
        }

        // --- Proximity Leash ---
        if (!this.level().isClientSide) {
            Player nearestLeash = this.level().getNearestPlayer(this, -1.0D);
            if (nearestLeash != null) {
                double distSq = this.distanceToSqr(nearestLeash);
                if (distSq > 40 * 40) {
                    // Instantly teleport behind the player when too far (e.g. after respawn)
                    Vec3 look = nearestLeash.getLookAngle();
                    double tx = nearestLeash.getX() - look.x * 30;
                    double tz = nearestLeash.getZ() - look.z * 30;
                    BlockPos pos = BlockPos.containing(tx, nearestLeash.getY(), tz);
                    if (this.level().canSeeSky(nearestLeash.blockPosition())) {
                        int sy = this.level().getHeightmapPos(
                                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos).getY();
                        this.teleportTo(tx, Math.max(sy + 1.0, nearestLeash.getY()), tz);
                    } else {
                        this.teleportTo(tx, nearestLeash.getY(), tz);
                    }
                }
            }
        }
    }

    // =========================================================
    // Spawn / Persistence
    // =========================================================

    /**
     * Intercept actual death: trigger fake-death instead of removing the entity,
     * but only during the HUNTING state.
     */
    @Override
    public void die(DamageSource source) {
        if (isHunting() && !isRecovering) {
            triggerFakeDeath();
            // do NOT call super.die() — keep the entity alive
        } else {
            super.die(source);
        }
    }

    public void forceJumpscare(Player player)    { this.forcedTarget = player; this.forceJumpscare = true; }
    public void forceGlitchEvent(Player player)  { this.forcedTarget = player; this.forceGlitch = true; }
    public void forceRoofCollapse(Player player) { this.forcedTarget = player; this.forceRoofCollapse = true; }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean isPushable()                { return false; }
    @Override public boolean canBeCollidedWith()         { return !isObserving(); }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putString("FallenState", getState());
        compound.putBoolean("Recovering", isRecovering);
        compound.putInt("RecoveryTicks", recoveryTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        setState(compound.contains("FallenState") ? compound.getString("FallenState") : STATE_OBSERVING);
        isRecovering  = compound.getBoolean("Recovering");
        recoveryTicks = compound.getInt("RecoveryTicks");
    }
}
