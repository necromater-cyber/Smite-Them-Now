# Smite Them Now

A single-player Android game about being a petty god. Each level is a 2–3 minute diorama: destroy every Non-Believer, spare your Believers, and try to look dignified doing it.

This repo holds the design docs and a playable proof of concept.

- **Design:** [docs/GDD.md](docs/GDD.md)
- **Proof of concept:** the Android app in `app/`

## Run the proof of concept

1. Open this folder in Android Studio (**File > Open**).
2. Set the Gradle JDK to 17 or 21 under **Settings > Build, Execution, Deployment > Build Tools > Gradle**. Gradle 8.14.3 does not run on Java 25.
3. Let Gradle sync, then press **Run** with an emulator or a phone attached. A real phone is better: the point of this build is to feel the gestures.

Build setup: Gradle 8.14.3, Android Gradle plugin 8.13.2, Kotlin 2.0.21, Android SDK platform 35. The app runs on Android 7.0 and up.

## How to play

Pick a power on the bottom bar, then use its gesture on the page.

| Power | Gesture | Prayer | What it does |
|---|---|---|---|
| Bolt | Tap | 12 | Kills the one person nearest your tap. No warning. |
| Meteor | Hold until the ring is whole, then release | 35 | Big blast, leaves fire. Heathens see it charging and run for cover among your Believers. Release early and it fizzles for free. |
| Gust | Swipe | 8 | Shoves everyone along the swipe and carries fire with it. |
| Shield | Tap | 15 | For 6 seconds, Believers inside the circle cannot be harmed. Heathens hiding in there can. |

- **Prayer** is your mana. Living, calm Believers generate it.
- **Faith** drops by 25 each time a Believer dies. At zero you lose.
- Smite a heathen right next to an unharmed Believer and that Believer turns **Zealot**: double Prayer for 6 seconds.
- Kill a Believer and the ones nearby panic and stop praying for 5 seconds.
- **Barrels** explode when struck or burned, and set off their neighbours. Kills in quick succession build a chain, and each link pays more Prayer.
- **Lightning Rods** swallow any Bolt cast inside their ring. Three Bolts overload a rod and it explodes. A Meteor or barrel blast fells it at once.

Three stars for winning with no Believers lost and a minute to spare. Your best result per chapter is saved.

## Chapters

| | Title | What it teaches |
|---|---|---|
| I | A Gentle Smiting | Bolt, Zealotry, a first barrel pair |
| II | Market Day | Meteor, Gust, Shield, human shields, a barrel line |
| III | The Defiant | Lightning Rods |

## What is in the code

```
app/src/main/java/com/necromater/smitethemnow/
  sim/Entities.kt   data: NPCs, buildings, barrels, fire, shields, rods, effects
  sim/World.kt      all the rules, the three level layouts, every tuning number at the top
  GameView.kt       drawing, screens and touch input
  MainActivity.kt   launches the view, handles Back
```

The `sim` package has no Android imports. The rules can be tested on a desktop JVM and would carry over to a game engine if the project outgrows a plain Canvas.

The look is an illuminated manuscript, drawn entirely in code as a first pass. There are no image assets yet.
