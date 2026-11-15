# Smite Them Now

A single-player Android game about being a petty god. Each level is a 2–3 minute diorama: destroy every Non-Believer, spare your Believers, and try to look dignified doing it.

This repo holds the design docs and a playable proof of concept.

- **Design:** [docs/GDD.md](docs/GDD.md)
- **Proof of concept:** the Android app in `app/`

## Run the proof of concept

1. Open this folder in Android Studio (**File > Open**).
2. Let Gradle sync. The first sync downloads Gradle 8.14.3, the Android Gradle plugin 8.9.1 and Kotlin 2.0.21. If Android Studio offers to upgrade the plugin, either choice is fine.
3. Press **Run** with an emulator or a phone attached. A real phone is better: the point of this build is to feel the gestures.

Requirements: Android Studio Meerkat (2024.3) or newer, Android SDK platform 35. The app runs on Android 7.0 and up.

## How to play

| Power | Gesture | Prayer | What it does |
|---|---|---|---|
| Bolt | Tap | 12 | Kills the one person nearest your tap. No warning. |
| Meteor | Hold until the ring turns white, then release | 35 | Big blast, leaves fire. Heathens see it charging and run for cover among your Believers. Release early and it fizzles for free. |
| Gust | Swipe | 8 | Shoves everyone along the swipe and carries fire with it. |

Pick a power on the bottom bar, then use its gesture on the map.

- **Prayer** is your mana. Living, calm Believers generate it.
- **Faith** drops by 25 each time a Believer dies. At zero you lose.
- Smite a heathen right next to an unharmed Believer and that Believer turns **Zealot**: double Prayer for 6 seconds.
- Kill a Believer and the ones nearby panic and stop praying for 5 seconds.
- **Barrels** explode when struck or burned, and set off their neighbours. Kills in quick succession build a chain, and each link pays more Prayer.

You have 2:30. Three stars for winning with no Believers lost and a minute to spare.

## What is in the code

```
app/src/main/java/com/necromater/smitethemnow/
  sim/Entities.kt   data: NPCs, buildings, barrels, fire, effects
  sim/World.kt      all the rules, and every tuning number at the top
  GameView.kt       drawing and touch input
  MainActivity.kt   launches the view
```

The `sim` package has no Android imports. The rules can be tested on a desktop JVM and would carry over to a game engine if the project outgrows a plain Canvas.

Everything on screen is a placeholder shape. Art direction is still open, see the GDD.
