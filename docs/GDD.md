# Smite Them Now: Game Design Document

Working title. Living document: sections marked **Open** are undecided.

## 1. Core concept and tone

- **Platform:** Android, designed around touch: tap, drag, pinch, press-and-hold.
- **Mode:** Single-player PvE.
- **View:** 2D top-down.
- **Tone:** Punchy, urgent, arcade destruction with a humorous, slightly petty deity.
- **Session length:** Micro-scenarios of 2–3 minutes. Not a slow-burn civilization builder.

## 2. Core loop

1. A compact, destructible diorama holds Believers and Non-Believers.
2. Believers pray, which generates **Prayer** (mana).
3. The player spends Prayer on god powers to destroy every Non-Believer.
4. Harming Believers drains the **Faith** meter and cripples Prayer generation ("Crisis of Faith").
5. The level ends when the last Non-Believer falls, Faith hits zero, the flock is gone, or time runs out.

## 3. God powers (the Pantheon Deck)

| Role | Power | Gesture | In the proof of concept |
|---|---|---|---|
| Precision | Lightning Bolt | Tap | Yes |
| Precision | Divine Anvil (tiny radius, heavy physics) | Tap | No |
| Area | Meteor Shower (fire, wide radius) | Press and hold | Yes, as a single Meteor |
| Area | Earthquake (collapses buildings, tosses NPCs) | Open | No |
| Control | Shepherd's Gust (directional wind) | Swipe | Yes |
| Control | Miracle Shield (protects Believers) | Tap | Yes |
| Control | Conversion Beam | Open | No |
| Utility | Rain cloud (see combo 2) | Pinch to spread | No |

**Miracle Shield, as built:** a circle that lasts 6 seconds. Believers inside cannot be harmed by Bolts, blasts or fire. Non-Believers inside get no protection, so a Shield followed by a Meteor is the answer to human shields, and it pays out in Zealots.

## 4. Crowd AI

- **Human shields:** Non-Believers who see a spell charging run into crowds of Believers. In the proof of concept they also do this after witnessing a smiting.
- **Zealotry:** Smiting an enemy right next to a Believer without hurting them puts that Believer in a Frenzied Zealot state with double prayer speed.
- **Doubt and Terror:** Hurting a Believer makes those nearby stop praying, flee, or (later) defect.
- **Social spread:** Isolated NPCs can be converted by a surrounding crowd. Not built yet.

## 5. Domino combos

The map should reward setting things up over spamming the biggest spell.

### 5.1 Grease Fire Cascade (Meteor + Gust + oil barrels)
Fire from a Meteor is a contained area. Swipe a Gust through it and the fire travels along the swipe, leaving burning ground behind and detonating any barrel it crosses. Each barrel throws fire onto the next.
- The swipe is the path the fire takes, so aiming feels like steering.
- Wind does not care whose roof it hits. Believers downwind are at risk.
- Counterplay for a Defiant Civilization: firebreaks, bucket brigades, Inquisitors who put fires out.

**In the proof of concept**, in a simple form: barrels chain, and Gust carries fire.

### 5.2 Conductive Congregation (Lightning Rods + rain)
Enemy Lightning Rods redirect bolts. Rain makes puddles that join into a conductive network, so a rod that catches a bolt passes the charge to every Non-Believer standing in water.
- The enemy's counter becomes the player's weapon.
- Believers wade too. The skill is herding the flock to dry ground first.
- Smart Non-Believers climb to high ground when storm clouds form, so it becomes a race.

### 5.3 Domino Tower Topple (Divine Anvil + Earthquake + leaning buildings)
Earthquakes add a visible lean to tall buildings. An Anvil dropped on the edge of a weakened tower tips it away from the impact, and it can knock down the next one.
- Press and hold shows a ghosted fall path before release.
- Dust clouds blind the AI, so Non-Believers cannot see the next spell charging.
- "Poetic Justice" bonus for dropping the heathens' temple on their own tax office.

### 5.4 Chain Multiplier
Kills in quick succession form a chain. Each extra link pays more Prayer. This favours the Trickster/Puppeteer playstyle.

**In the proof of concept:** links must land within 0.8 seconds of each other, each link adds 1 Prayer on top of the base 2, capped at 6 extra. The short window is deliberate: it rewards barrel and fire chains, and stops rapid Bolt taps from paying for themselves.

## 6. Opponent types

- **Rival AI God:** a tug-of-war race to smite each other's flocks.
- **Defiant Civilization (reverse tower defense):** Non-Believers build Lightning Rods, Anti-Magic Ward Totems and Inquisitors.
- **Apocalypse / Escort:** protect migrating Believers through hostile terrain and raiders.

**In the proof of concept:** a first slice of the Defiant Civilization. Chapter III has pre-built Lightning Rods:

- A rod swallows any Bolt cast inside its ring (230 units). The Bolt is still paid for.
- Three swallowed Bolts overload the rod. It explodes and takes nearby heathens with it.
- A Meteor or barrel blast fells a rod at once.
- Rods do nothing about Meteors, wind or fire.

Not built yet: heathens constructing rods during play, Ward Totems, Inquisitors, the Rival God and Escort modes.

## 7. Meta-progression

Between levels the player shapes their religion's playstyle: Benevolent Shepherd, Wrathful Old-Testament, or Trickster/Puppeteer. **Open:** what each one changes mechanically.

## 8. Art direction

**Decided: illuminated manuscript.** Each level is a page from a medieval book of petty miracles.

- **Page:** stained vellum with a ruled border in ink, vermilion and gold, and roundels at the corners.
- **Palette:** ink brown, vellum, vermilion, ultramarine, gold leaf, murrey. Nothing outside it.
- **Figures:** flat marginalia. Believers wear ultramarine robes and a gold halo. Heathens wear murrey and a pointed black hood. Terrified Believers lose the halo and go grey. Zealots raise their arms and radiate.
- **Buildings:** drawn flat and front-on, as manuscripts do, even though the map is top-down. Holy buildings have a blue roof and a gold cross, others a vermilion roof.
- **Spells:** gold leaf with an ink underlay. Lightning is a gold zigzag, a Shield is a gold ring, fire is vermilion tongues with gold hearts.
- **Type:** serif throughout, with a drop capital on the title.
- **Humour:** mock-solemnity. Holy art recording very small-minded acts.

Everything is drawn in code for now. Hand-painted assets come later.

**Open:** whether the god is visible (a hand, a face in the clouds) or an unseen force.

## 9. Proof of concept

**Purpose:** find out whether tap, hold and swipe feel good on a phone, and whether human shields, Zealotry and barrel chains produce interesting decisions in a 2–3 minute round.

**Scope:** three chapters chosen from a chapter list, four powers, a briefing card per chapter, win and lose cards with a star rating, best stars saved per chapter.

| Chapter | Believers | Non-Believers | Barrels | Rods | Time |
|---|---|---|---|---|---|
| I. A Gentle Smiting | 6 | 9 | 2 | 0 | 2:00 |
| II. Market Day | 10 | 20 | 7 | 0 | 2:30 |
| III. The Defiant | 9 | 18 | 4 | 3 | 2:45 |

**Tuning values** (all at the top of `sim/World.kt`):

| Value | Setting |
|---|---|
| Starting Prayer / maximum | 30 / 100 |
| Prayer per calm Believer | 0.35 per second (Zealot: double) |
| Bolt / Meteor / Gust / Shield cost | 12 / 35 / 8 / 15 |
| Shield radius / duration | 115 units / 6 s |
| Rod ring / Bolts to overload | 230 units / 3 |
| Meteor charge time | 0.9 s; Non-Believers react once it is 35% charged |
| Faith lost per Believer killed | 25 of 100 |
| Zealot duration / Terror duration | 6 s / 5 s |

A scripted bot with perfect aim, using only Bolts, clears chapter I in about 30 seconds, II in 35–50 and III in 40–65. Chapter II has been played on an emulator and felt good. Chapters I and III, the Shield and the rods have not been played by a person yet.

## 10. Decisions log

| Date | Decision | Reason |
|---|---|---|
| 2026-11-15 | 2D top-down view | Chosen by the designer. |
| 2026-11-15 | Portrait orientation | One-handed, arcade-style play. Easy to revisit. |
| 2026-11-15 | Proof of concept in Kotlin on a plain Android Canvas, no engine, no libraries | Opens and runs in Android Studio with nothing extra to install. The rules live in a separate `sim` package with no Android code, so they can move to libGDX or another engine later. |
| 2026-11-15 | Powers are chosen on a bottom bar, then cast with their gesture | Keeps each gesture unambiguous. Worth testing against a no-bar scheme where the gesture alone picks the power. |
| 2026-11-15 | Prayer is collected automatically | Keeps attention on smiting. Tapping orbs to collect them is an option to test later. |

| 2026-11-15 | Illuminated manuscript art style | Flat figures read well from above, gold-leaf spells suit the tone, and it is cheap to produce in 2D. |
| 2026-11-15 | Shields are fixed circles that protect Believers only | Makes Shield the direct counter to human shields without making the flock invulnerable on the move. |
| 2026-11-15 | Rods can be brute-forced with three Bolts | A player who has not found the clever route is slowed, not stuck. |
| 2026-11-15 | Build on Gradle 8.14.3, Android Gradle plugin 8.13.2, Gradle JDK 21 | What the designer's Android Studio accepted. |

## 11. Ideas backlog

- Heathens who build and repair rods mid-level, so the Defiant chapter fights back.

- Pre-Smite Preview: press and hold to see the consequence before release.
- Poetic Justice bonuses for ironic kills.
- Dust and smoke that block the AI's line of sight.
- Destructible buildings that matter (the proof of concept only flattens them).
- Engine choice once the art style is set.
