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
| Control | Miracle Shield (protects Believers) | Open | No |
| Control | Conversion Beam | Open | No |
| Utility | Rain cloud (see combo 2) | Pinch to spread | No |

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

None of these are in the proof of concept. It has passive Non-Believers only.

## 7. Meta-progression

Between levels the player shapes their religion's playstyle: Benevolent Shepherd, Wrathful Old-Testament, or Trickster/Puppeteer. **Open:** what each one changes mechanically.

## 8. Art direction

**Open.** The view is 2D top-down. Three candidate styles, each needing a top-down adaptation:

- **Toybox Diorama:** chunky, tilt-shift, peg-like NPCs. Gleeful, consequence-free destruction.
- **Illuminated Manuscript:** medieval painted look, gold-leaf spells. Humour from mock-solemnity.
- **Saturday-Morning Cartoon:** bold outlines, big readable expressions. Slapstick first.

Also open: whether the god is visible (a hand, a face in the clouds) or an unseen force.

The proof of concept uses flat placeholder shapes on a parchment-coloured ground. That is not a style decision.

## 9. Proof of concept

**Purpose:** find out whether tap, hold and swipe feel good on a phone, and whether human shields, Zealotry and barrel chains produce interesting decisions in a 2–3 minute round.

**Scope:** one level, 10 Believers in two flocks, 20 Non-Believers, 7 barrels, 5 buildings, three powers, a 2:30 timer, win and lose screens with a star rating.

**Tuning values** (all at the top of `sim/World.kt`):

| Value | Setting |
|---|---|
| Starting Prayer / maximum | 30 / 100 |
| Prayer per calm Believer | 0.35 per second (Zealot: double) |
| Bolt / Meteor / Gust cost | 12 / 35 / 8 |
| Meteor charge time | 0.9 s; Non-Believers react once it is 35% charged |
| Faith lost per Believer killed | 25 of 100 |
| Zealot duration / Terror duration | 6 s / 5 s |
| Time limit | 150 s |

These numbers have only been checked with a scripted bot, which clears the level in 30–50 seconds with perfect aim. They have not been played by a person yet and will need adjusting.

## 10. Decisions log

| Date | Decision | Reason |
|---|---|---|
| 2026-11-15 | 2D top-down view | Chosen by the designer. |
| 2026-11-15 | Portrait orientation | One-handed, arcade-style play. Easy to revisit. |
| 2026-11-15 | Proof of concept in Kotlin on a plain Android Canvas, no engine, no libraries | Opens and runs in Android Studio with nothing extra to install. The rules live in a separate `sim` package with no Android code, so they can move to libGDX or another engine later. |
| 2026-11-15 | Powers are chosen on a bottom bar, then cast with their gesture | Keeps each gesture unambiguous. Worth testing against a no-bar scheme where the gesture alone picks the power. |
| 2026-11-15 | Prayer is collected automatically | Keeps attention on smiting. Tapping orbs to collect them is an option to test later. |

## 11. Ideas backlog

- Pre-Smite Preview: press and hold to see the consequence before release.
- Poetic Justice bonuses for ironic kills.
- Dust and smoke that block the AI's line of sight.
- Destructible buildings that matter (the proof of concept only flattens them).
- Engine choice once the art style is set.
