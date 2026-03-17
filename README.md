# The Fallen

A Minecraft horror mod built with NeoForge 1.21.1.

## Overview

**The Fallen** introduces a mysterious and terrifying entity known as **The Observer**. This entity stalks the player during the day and turns into a lethal hunter at night. The mod features dynamic events, custom structures, and a unique 24-hour cycle of psychological and physical horror.

## Key Features

- **The Observer (Observing State):**
  - Stalks the player from a distance during the day.
  - Invisible but prone to "flickering" into visibility.
  - Triggers "Glitch Events" on direct eye contact, corrupting nearby blocks.
  - Occasionally performs "Fake Charges" (jumpscares) that apply Blindness and Nausea.
  - Spawns eerie structures (Inverted Obelisks, Fractured Chunks) near the player.

- **The Hunter (Hunting State):**
  - Triggered at nightfall (time 13000).
  - High speed, aggressive melee attacks, and block-breaking capabilities.
  - Emits a haunting low-frequency hum.

- **Dynamic Events:**
  - **Gravity Well:** Randomized gravity failure during the night.
  - **Roof Collapse:** Physical blocks falling from above when in caves or under structures.
  - **Ambient Disturbance:** Footstep echoes, torch snuffing, and item levitation.

## Commands (OP required)

- `/thefallen force jumpscare` - Forces a fake charge jumpscare.
- `/thefallen force glitch` - Forces a block corruption glitch event.
- `/thefallen force state [observing|hunting]` - Manually toggles the entity's state.

## Technical Details

- **Version:** Minecraft 1.21.1
- **Loader:** NeoForge
- **Package:** `com.glow.thefallen`
- **Author:** Glow

## Build Instructions

To build the mod from source, use the included Gradle wrapper:

```bash
./gradlew build
```

The resulting JAR will be in `build/libs/`.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
