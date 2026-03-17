# Task: THE FALLEN Minecraft Horror Mod

## Phase 1: The Observer (Daytime Logic) [x]
- [x] Initialized NeoForge 1.21.1 Project Structure
- [x] Setup Base Mod Class and Registry (Entities, Items)
- [x] Implement "The Observer" Entity
    - [x] Set invisible/invulnerable/no-AI base state
    - [x] Implement Stalking Goal (15-20 blocks distance)
    - [x] Implement Teleportation Logic (FOV/Opaque block avoidance)
- [x] Implement Subterranean Logic
    - [x] Ceiling Stalk behavior (y < 60 / light < 7)
    - [x] Echo Footsteps (sound delay logic)
    - [x] Light Snuffing (torch removal)
- [x] Implement Psychological Triggers
    - [x] Random Cave Ambience
    - [x] Anti-Gravity Glitch (item floating)

## Phase 2: The Transition (Sunset Event) [x]
- [x] Implement Time Check Logic (Sunset trigger)
- [x] Create Action Bar Message Logic (§k formatting)
- [x] Implement Despawn/Spawn Cycle

## Phase 3: The Fallen (Nighttime Logic) [x]
- [x] Implement "The Fallen" Entity
- [x] Movement & Gravity Control
    - [ ] Wall-Walking (Cave logic - Partially simulated)
    - [x] Player Lifting (Gravity Well)
- [x] Breach Logic (Block Breaking)
    - [x] Scan for blocks at eye/foot height
    - [x] Implement `destroyBlockProgress` (60-100 ticks)
    - [x] FallingBlockEntity "pull" effect

## Phase 4: Environmental Variations [x]
- [x] Surface Hunt Tactics (Cover destruction)
- [x] Cave Hunt Tactics (Sealing exits, Darkness/Slowness)

## Phase 5: Polish & Upgrades [/]
- [ ] GeckoLib Integration (Recommended for manual setup)
- [ ] Spatial Audio Overhaul
- [x] Final Cleanup & Despawn Logic (Morning)
- [x] Install Gradle Wrapper and Build Mod
- [x] Register Entity Attributes
- [x] Fix Rendering Crash (Client Renderers)
- [x] Register Event Handlers (Automatic Cycle)
