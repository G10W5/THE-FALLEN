# **Project: THE FALLEN**

**Target Version: Minecraft 1.21.1 (NeoForge)**

## **1\. Project Overview**

Create a Minecraft Horror Mod featuring a single entity that cycles through two distinct behaviors based on the day/night cycle.

* **Daytime:** "The Observer." A black player-model stalker that stays in shadows and corrupts reality if looked at.  
* **Nighttime:** "The Fallen." A white player-model hunter that ignores gravity, glides through the air, and breaks blocks to reach the player.

## **2\. Phase 1: The Observer (Daytime Logic)**

**Objective:** Psychological horror and environmental manipulation.

* **Appearance:** Use a **Player Model** with a solid **Pure Black** texture (RGB 0,0,0).  
* **Behavior (The "Stare" Punishment):**  
  * **Logic:** Every tick, check if player.canSee(this) AND this.getViewVector().dot(player.getLookAngle()) \< \-0.9 (Direct eye contact).  
  * **The Glitch Event:** 1\. Play minecraft:entity.generic.explode sound (no damage).  
    2\. **Block Corruption:** Scan 5-block radius. Replace 70% of blocks with modid:glitched\_block.  
    3\. **Teleport:** Immediately tp entity 60 blocks away to an obscured location.  
* **The Subterranean Creep (Caves):**  
  * **Ceiling Stalk:** If Y \< 60 or light level \< 7, teleport to the ceiling directly above the player's path. Use setNoGravity(true).  
  * **Echo Footsteps:** Play stone.step sounds 5 blocks behind the player with a 0.5s delay.  
  * **Light Snuffing:** Scan 10-block radius for TorchBlock. Replace with Air and spawn minecraft:torch as an ItemEntity at that position.  
* **Gravity Play:** Scan 5-block radius for ItemEntity. Apply setDeltaMovement(0, 0.1, 0\) to make dropped items float and swirl before despawning.

## **3\. Phase 2: The Transition (Sunset Event)**

* **Trigger:** level.getDayTime() \== 13000\.  
* **Transformation:** 1\. Switch texture to **Pure White** (RGB 255, 255, 255).  
  2\. Broadcast Action Bar messages in §k (Obfuscated): §kRUN §rTHE SKY IS FALLING.  
  3\. Transition Data Component state from OBSERVING to HUNTING.

## **4\. Phase 3: The Fallen (Nighttime Logic)**

**Objective:** Relentless pursuit with non-standard movement.

### **A. Movement & Gravity Control**

* **Surface Flight:** Must stay 1.5 \- 2.0 blocks above the ground. Use a custom MoveControl to hover and glide directly toward the player, ignoring floor collision and pathing over air.  
* **Cave Crawling:** Use GravityChangerAPI (or manual orientation) to stick feet to walls/ceilings. The entity moves toward the player regardless of verticality.  
* **Player Lifting:** On the surface, trigger a "Gravity Well" every 30s. Send GravityChangePayload to set player gravity to INVERSE for 3 seconds.

### **B. The "Breach" (Block Breaking)**

* **Goal:** Custom BreachGoal.  
* **Logic:** When blocked by geometry, scan BlockState at eyeHeight and footHeight.  
* **The Rip:** On break, spawn a FallingBlockEntity with setDeltaMovement(0, 0.5, 0\) (no item drop).

## **5\. Phase 4: Custom "Glitched" Block**

* **Properties:**  
  * MapColor.COLOR\_BLACK.  
  * Strength(-1.0F, 3600000.0F) (Unbreakable/Bedrock-tier).  
  * **Texture:** Static/Glitch effect.

## **6\. Technical Execution for the Agent**

**Technical Guardrails for 1.21.1 Implementation:**

1. **Registry:** Use DeferredRegister.create(Registries.ENTITY\_TYPE, MODID).  
2. **Models:** Use PlayerModel\<TheFallenEntity\> in the EntityRenderer. Swapping textures is more efficient than swapping entities.  
3. **Data Components:** Do not use NBT. Use DataComponents.CUSTOM\_DATA for state tracking.  
4. **Networking:** Use CustomPacketPayload for syncing gravity changes.  
5. **AI Goal Priority:**  
   * Priority 0: FloatGoal.  
   * Priority 1: BreachGoal (HUNTING only).  
   * Priority 2: StalkGoal (OBSERVING only).

## **7\. Victory Condition**

* At level.isDay() \== true, reset to "The Observer" (Black texture), reset gravity to normal, and resume stalking.

### **8\. For future improvements**

- Implement an event system to add different types of events.