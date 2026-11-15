# Smite Them Now

A single-player Android game about being a petty god. Each level is a 2–3 minute diorama: destroy every Non-Believer, spare your Believers, and try to look dignified doing it.

This repo holds the design docs and a playable proof of concept.

- **Design:** [docs/GDD.md](docs/GDD.md)
- **Proof of concept:** the Android app in `app/`

## Run the proof of concept

1. Open this folder in Android Studio (**File > Open**).
2. Let Gradle sync, then press **Run** with an emulator or a phone attached. A real phone is better: the point of this build is to feel the gestures.

Build setup: Gradle 9.6.0, Android Gradle plugin 9.5.1, Kotlin 2.2.10, Gradle JDK 21, Android SDK platform 35. The app runs on Android 7.0 and up.

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
- **Lightning Rods** swallow any Bolt cast inside their ring. Three Bolts overload a rod and it explodes. A Meteor or barrel blast fells it at once. A heathen with a mallet will come to rebuild it, up to twice per rod.
- **Your Prophet** (white robe, mitre) converts the nearest heathen inside his ring in 5 seconds. Lose him and Faith drops by 50.
- **The Heretic** (black robe, red hood) walks to your flock and leads Believers astray one at a time, 5 seconds each, 10 Faith each. His ward eats the first Bolt. Zealots and shielded Believers ignore him.
- **Witnesses:** heathens who see a smiting up close may convert on the spot. Believers who see one of their own die may walk away.
- **Inquisitors** (red robe, sword; chapters IV and V) walk to your Prophet, then the rest of the flock, and cut them down one by one. Each Believer they kill costs 15 Faith. A Shield stops the blade, and one Bolt stops the Inquisitor.
- **Sheltering** (chapters IV and V): frightened heathens run into the nearest ordinary building and cannot be hit while inside. They come out after 5 seconds. Bring the building down and everyone in it goes at once, as one chain.
- **Divine Fatigue** (chapters IV and V): each Bolt in quick succession costs 4 more Prayer, up to 32. It recovers one step every 1.7 seconds or so.
- **Buildings** crack on the first blast and collapse on the second, crushing anyone against the walls. A direct Meteor levels one outright. Fire against a wall does the same, slowly. Flattening your own chapel costs 15 Faith.

Stars: one for winning, one for losing no Believers (dead or led astray), one for a chain of the length the chapter asks for. Your best result per chapter is saved.

## Chapters

| | Title | What it teaches |
|---|---|---|
| I | A Gentle Smiting | Bolt, Zealotry, the Prophet, a first barrel pair |
| II | Market Day | The Heretic, Meteor, Gust, Shield, human shields, a barrel line |
| III | The Defiant | Lightning Rods and the builders who put them back |
| IV | The Inquisition | Inquisitors, sheltering, Divine Fatigue |
| V | Day of Reckoning | All of it, with more Inquisitors arriving mid-round |

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
