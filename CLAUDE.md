# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

GooseDroid is an Android virtual pet / Desktop Goose port. An animated goose lives as a screen overlay with AI behaviors, touch interactions, needs system, evolution, mini-games, and procedural rendering (no sprites — all Canvas draw calls).

**Package**: `com.cfks.goosedroid` | **Java 11** | **Min SDK 24** | **Target SDK 36** | **compileSdk 36**
**AGP**: 8.12.0 (Gradle 8.13) | **Version**: 1.0.4 (versionCode 5)

## Build Commands

```bash
./gradlew assembleDebug          # Debug APK → app/build/outputs/apk/debug/
./gradlew assembleRelease        # Release APK (minified, requires keystore in local.properties)
./gradlew test                   # Unit tests
./gradlew clean                  # Clean build artifacts
```

Release signing requires `keystore.path`, `keystore.password`, `keystore.alias`, `keystore.alias_password` in `local.properties`.

## Architecture

```
GooseDroidApplication          (Application class, crash reporting, memory management)
    ↓
MainActivity                   (Settings UI, overlay toggle, pet mode controls)
    ↓ starts overlay
GooseView                      (Custom View, ~120 FPS render loop via Handler.postDelayed)
    ↓ delegates to
TheGoose                       (Central coordinator — lifecycle, events, achievements, statistics)
    ├── GooseAI                (Behavior system: modular behaviors, memory, routines, decision tree)
    ├── GooseBehaviorTree      (Priority-based behavior tree: critical needs → interaction → needs → personality → idle)
    ├── GoosePhysics           (Movement, jumping, bouncing, friction, surface types, foot IK)
    ├── GooseRig               (Skeletal system: bones, expressions, poses)
    ├── GooseRenderer          (Procedural drawing, particles, trails, glow, status indicators)
    ├── GooseTouchHandler      (Gesture recognition: tap, pet, drag, throw, boop, tickle, belly rub, etc.)
    ├── GooseTasks             (Task/state enums: Wander, NabMouse, Sleeping, Eating, BeingPetted, etc.)
    ├── GooseLLM               (On-device "AI" — template-based thought generation, personality-driven responses, memory)
    ├── GooseVisualEffects     (Particle effects: hearts, sparkles, confetti, dust, etc.)
    ├── GooseSoundEffects      (Extended honk types, footsteps, emotional sounds via SoundPool)
    ├── GooseDreams            (Sleep thought bubbles based on daily experiences)
    ├── GooseEasterEggs        (Secret modes and hidden interactions)
    ├── GooseTrolling          (Fake notifications, vibration, troll messages)
    ├── GooseSystemReactions   (Reacts to battery level, time of day, inactivity)
    ├── MiniGames              (Feeding, chasing, catching, hide & seek, honk hero, memory honk)
    ├── Sound                  (Core MediaPlayer audio pools)
    ├── Easings                (Easing functions for animations)
    └── FootMark               (Mud footprint tracking)

Pet Systems (root package):
    ├── PetNeeds               (Tamagotchi-style: hunger, energy, happiness with decay rates)
    ├── PetPersonality         (Evolving traits: playfulness, affection, bravery, mischief [-100..+100])
    ├── PetAppearance          (Colors, hats, accessories, creature types)
    ├── PetEvolution → GooseEvolution  (Stages: Egg → Hatchling → Gosling → Adult → Elder → Legendary → Cosmic)
    ├── PetWidget              (Home screen widget with feed/play/sleep actions)
    └── PetNotificationManager (Notification alerts when needs are critical)

Activities:
    ├── MainActivity           (Main settings panel + overlay toggle)
    ├── CustomizeActivity      (Appearance editor: name, colors, hats, accessories, creature type)
    └── ConfigureActivity      (Properties file I/O for config.ini)
```

### Key Design Patterns

- **TheGoose is a coordinator** that implements multiple callback interfaces (`AICallback`, `TouchCallback`, `PhysicsCallback`, `GestureListener`, `GameCallback`). All module communication flows through it.
- **GooseAI.Behavior interface**: modular behaviors with `onEnter/onUpdate/onExit/isComplete/getPriority/getName`.
- **BehaviorTree**: standard game AI pattern with Selector/Sequence/Condition/Action nodes and a Blackboard for shared state.
- **Static state**: `PetNeeds`, `PetPersonality`, `PetAppearance` use static fields — global mutable state, not instance-based.
- **SamEngine**: math library with `Vector2`, `Time` (global deltaTime), `SamMath`, and `Deck` (weighted random selection).

### Configuration

`config.ini` (INI/Properties format) in `assets/` as defaults, copied to app private dir at first run. Managed by `ConfigureActivity`. Contains appearance, behavior, pet needs, personality, and timing settings.

### Permissions

`SYSTEM_ALERT_WINDOW` (overlay), `VIBRATE`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `FOREGROUND_SERVICE`, `RECEIVE_BOOT_COMPLETED`.

## Code Conventions

- Mixed Chinese/English comments (originally developed in AIDE+ IDE on Android)
- Spanish comments in newer files (`GooseVisualEffects`, `GooseDreams`, `GooseEasterEggs`, `PetNeeds`, etc.)
- `GooseDesktop/` subpackage contains all goose engine modules; root package has activities and pet systems
- Dependencies managed via `gradle/libs.versions.toml` version catalog
- External dependency: `app/libs/core.jar` (bundled via `fileTree`)
- ProGuard enabled for release builds — keeps all project classes (broad `-keep` rules)
- Repositories include Aliyun mirrors (Chinese Maven mirrors) in `settings.gradle`
