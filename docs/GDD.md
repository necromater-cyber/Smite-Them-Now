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

### 4.1 Leaders

Each side has one leader per level, drawn larger than the followers, with a visible ring showing their reach.

- **The Prophet** (Believers): stays with his flock. Converts the nearest Non-Believer follower inside his ring (130 units) in 5 seconds. Heathens who run to the flock as human shields are walking into his sermon. Losing him costs 50 Faith and terrifies the whole flock.
- **The Heretic** (Non-Believers): walks to the nearest flock and leads one Believer astray every 5 seconds inside his ring (110 units), at 10 Faith each. He does not scare and cannot be converted. A ward absorbs the first Bolt; blasts and fire ignore it. Zealots, shielded Believers and fresh converts are immune to him. Killing him pays 10 Prayer and panics every heathen.
- A bar over a follower's head shows how far they have been talked round. It drains when the preacher loses reach.

### 4.3 Inquisitors

Heathens who attack. Chapters IV and V.

- An Inquisitor walks at a steady pace to the Prophet if he lives, otherwise to the nearest Believer.
- In reach, he winds up for 1.3 seconds (a bar fills, the sword lifts) and kills. A Shield over the victim stops the blow.
- A Believer killed this way costs 15 Faith ("Martyred") instead of 25. The Prophet still costs 50.
- Inquisitors do not scare, shelter, or convert. One Bolt kills them.
- Chapter V sends three more in from the page edges at 0:40 and 1:25.

### 4.4 Sheltering

Chapters IV and V.

- A spooked heathen follower with an ordinary (not holy) building within 340 units runs to its door and goes inside, instead of running to the flock.
- Inside, they cannot be seen, hit, burned, blown about, preached to or counted as witnesses. The doorway shows eyes and a head count.
- They come out after 5 seconds. A Meteor glowing nearby keeps them in.
- A collapse kills everyone inside in the same instant, so a full house is the longest chain in the game. The intended play: herd them indoors with a charging Meteor, then drop it on the roof.

### 4.2 Witness conversion

- A Non-Believer follower within 170 units of a smiting has a 10% chance to convert on the spot while the Prophet lives, 5% without him. The rest are spooked as before.
- A Believer who sees another Believer die has a 25% chance to walk away while the Heretic lives, 12% without him. The rest are terrified as before.
- New converts cannot be harmed for 2 seconds, so the tail of a barrel chain does not bless and burn the same person.
- Reactions are worked out once per tick, after every death in that tick.

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

### 5.6 Divine Fatigue

Chapters IV and V. Each Bolt adds one step of fatigue, and each step adds 4 Prayer to the next Bolt, up to 5 steps (12 rising to 32). Fatigue recovers at 0.6 steps per second. A Bolt every two seconds is always full price; tapping as fast as you can is not. The other powers are unaffected.

### 5.5 Destructible buildings

- A building is sound, cracked, or rubble. A blast near it does one step of damage. A Meteor landing on it does two.
- Fire against a wall for 2.5 seconds does one step.
- A collapse crushes anyone within 34 units of the walls. Crushing at least one heathen pays 5 Prayer ("Poetic Justice").
- Flattening a holy building costs 15 Faith and terrifies nearby Believers ("Sacrilege").
- Rubble no longer blocks movement.

## 6. Opponent types

- **Rival AI God:** a tug-of-war race to smite each other's flocks. Built as the Holy War mode, section 6.1.
- **Defiant Civilization (reverse tower defense):** Non-Believers build Lightning Rods, Anti-Magic Ward Totems and Inquisitors.
- **Apocalypse / Escort:** protect migrating Believers through hostile terrain and raiders.

**In the proof of concept:** a first slice of the Defiant Civilization. Chapter III has pre-built Lightning Rods:

- A rod swallows any Bolt cast inside its ring (230 units). The Bolt is still paid for.
- Three swallowed Bolts overload the rod. It explodes and takes nearby heathens with it.
- A Meteor or barrel blast fells a rod at once.
- Rods do nothing about Meteors, wind or fire.
- Six seconds after a rod falls, the nearest idle heathen picks up a mallet and walks over. Six seconds of work raises it again. Smiting or scaring the builder stops the work, and another is sent. Each rod can be rebuilt twice.

Not built yet: heathens constructing new rods, Ward Totems, Inquisitors, the Rival God and Escort modes.

### 6.1 Holy War (the Populous-flavoured mode)

A second mode beside the arcade chapters: about five minutes, a living map, and an opponent who does to you what you do to them. Direct smiting stays. Land sculpting is deliberately left out for now.

**The rival god**
- Earns 0.2 Prayer per second per living heathen, shown as a red bar under the clock. Fewer heathens means a weaker god.
- Bolt (20): marks one Believer follower with a red crosshair for 1.6 seconds, then strikes. Never aimed at the Prophet.
- Meteor (45): when four or more Believers stand close together, marks the spot with a red ring for 2.6 seconds. Kills Believers in 110 units, wrecks your buildings, spares its own people, leaves no fire.
- Counters: a Shield stops both. Gust pushes people out of a Meteor ring. Keeping the flock spread out denies the Meteor.
- A Believer killed by the rival costs 10 Faith; the Prophet 30.

**Growing settlements**
- Buildings belong to a side. A finished house sends out one follower every 16 seconds, up to 22 followers per side.
- Each side founds a new house on its own half of the page (Believers every 22 seconds, heathens every 15), up to five. Building takes 7 seconds, and foundations fall to a single blast.
- Finished houses take three steps of damage. A direct Meteor does two, so it needs a follow-up: a barrel, fire, or a second blast.
- Destroying your own house costs 10 Faith.

**Rally**
- A fifth power, only in this mode: tap to plant a banner for 12 seconds (10 Prayer).
- The flock gathers at the banner. Followers within 170 units fight any heathen in reach: 2.2 seconds per blow, and one blow in four kills the Believer instead (5 Faith).
- Heathen followers who are attacked run for a house, which sets up the collapse play.
- Fighting Believers do not pray, and a gathered flock is exactly what the rival's Meteor wants.

**The rival answers your defences**
- Gust (12): if you Shield a Believer it has marked, it blows them out from under the Shield and delays the Bolt to land afterwards. It also blows a flock gathered at a banner back towards home. Shown as a red arrow for 1.1 seconds. At most once every 11 seconds.
- Ward (15): if you charge a Meteor over one of its finished houses, it may frame the house in red for 5 seconds, during which your damage does nothing. At most once every 12 seconds. The answer is to release early (free) or retarget.
- Taunts when it kills someone, at most one every 7 seconds.
- The red bar has notches at 20 and 45 so the player can read what it can afford next.

Design intent: every defence the player buys can be made to cost twice, and every attack can be wasted. Playtesting found the fun in this mode is the budget, so the rival is built to pressure the budget.

**Doctrines** (the meta-progression from section 7, first version)

| Doctrine | Effect |
|---|---|
| Benevolent Shepherd | Shield 9, Prophet converts in 3 s, Meteor 45 |
| Wrathful | Meteor 25, Bolt 10, all Faith losses x1.5 |
| Trickster | Gust 4 and 40% stronger, chain bonus doubled with a cap of 10 links |

**Maps**
- Two Hills: open ground.
- The Ford: an impassable river with one crossing and barrels on it.
- Walled Town: destructible stone walls around the heathen quarter, one gate.

**Armageddon**
- When the five minutes run out, sheltering, building, spawning, conversion and prayer all stop. Both flocks walk at the nearest enemy and trade blows every 1.4 seconds: 60% to kill, 80% for a leader, 85% for a Zealot.
- Deaths in the battle cost no Faith. Shields still work, and so does every other power, on whatever Prayer is left.
- The rival stops earning Prayer too.
- Last flock standing wins. After 60 seconds the larger flock wins.

**Winning and stars**
- Win by leaving no heathens and no heathen houses, or by winning Armageddon.
- Stars: win; Prophet alive; won before Armageddon.

### 6.2 Replayability

Two questions, kept separate.

**Is one match engaging?** Sound and vibration were missing entirely and are now in (placeholder, synthesised). Beyond that this can only be answered by watching people who do not know the rules. The chapter list shows rounds played and "Again" taps on the device as a crude measure.

**Is there a reason to play a tenth match?**

*Rival gods.* Four opponents who spend Prayer differently, so each asks for a different budget.

| Rival | Changes |
|---|---|
| Vorn, the Stormlord | Bolt 16 with a 1.4 s warning |
| Mother Brick | Bolt 26; founds a house every 9 s, up to six; houses take four hits and send a follower every 11 s |
| The General | Bolt 28; spends 26 Prayer to send an Inquisitor from a house, up to three at once |
| Slyboots | Bolt 24; Gust every 5 s and ward every 6 s, ward costs 8; half the time lures a Believer away (16 Prayer, 2.2 s warning, 5 Faith) instead of striking |

*The power deck.* Five miracles per match, chosen from eight. Three are unlocked by Holy War wins:

| Miracle | Cost | Unlock | Effect |
|---|---|---|---|
| Divine Anvil | 22, tap | 1 win | Kills everyone within 46 units, two steps of damage to a building it lands on, fells rods. No warning, no fire. |
| Earthquake | 30, hold | 2 wins | One step of damage to every building within 220 units, yours included. Kills nobody. Throws people about and spooks heathens. |
| Conversion Beam | 18, tap | 3 wins | Converts the nearest heathen follower within 60 units. No charge if nobody is in reach. |

Not built yet, in the order planned: a ladder through the rival gods with rising handicaps, prophecies (optional goals that appear mid-match), and a daily challenge.

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

**Scope:** five chapters chosen from a chapter list, four powers, a leader on each side, conversion, destructible buildings, a briefing card per chapter, win and lose cards with a star rating, best stars saved per chapter.

**Stars:** one for winning, one for losing no Believers (dead or led astray), one for reaching the chapter's chain goal. A Bolt-only win earns two at most.

| Chapter | Believers | Non-Believers | Barrels | Rods | Time | Chain goal |
|---|---|---|---|---|---|---|
| I. A Gentle Smiting | 6 + Prophet | 9 | 2 | 0 | 2:00 | 2 |
| II. Market Day | 10 + Prophet | 19 + Heretic | 7 | 0 | 2:30 | 3 |
| III. The Defiant | 9 + Prophet | 17 + Heretic | 4 | 3 | 2:45 | 4 |
| IV. The Inquisition | 7 + Prophet | 15 + 3 Inquisitors | 3 | 0 | 2:45 | 4 |
| V. Day of Reckoning | 10 + Prophet | 17 + Heretic + 2 Inquisitors, 3 more arriving | 4 | 2 | 3:00 | 5 |

**Tuning values** (all at the top of `sim/World.kt`):

| Value | Setting |
|---|---|
| Starting Prayer / maximum | 22 / 100 |
| Prayer per calm Believer | 0.35 per second (Zealot: double) |
| Bolt / Meteor / Gust / Shield cost | 12 / 35 / 8 / 15 |
| Shield radius / duration | 115 units / 6 s |
| Rod ring / Bolts to overload | 230 units / 3 |
| Meteor charge time | 0.9 s; Non-Believers react once it is 30% charged |
| Faith lost per Believer killed | 25 of 100 |
| Zealot duration / Terror duration | 6 s / 5 s |

Scripted bots, Bolts only, perfect aim:

- Chapters I to III: a bot that goes for the Heretic first wins in 12 to 45 seconds with two stars.
- Chapters IV and V: a bot that goes for Inquisitors first wins in 30 to 65 seconds, usually with two stars. A bot that ignores them lost to a Crisis of Faith in all six runs. Doing nothing loses chapter IV in under a minute.
- Dropping a Meteor on a house after herding heathens into it killed 2 to 4 at once in tests. Up to 15 were hiding at one time in chapter V.

Holy War with rival gods (Two Hills, Shepherd): against Mother Brick a Bolts-only bot loses both runs, where it wins on numbers against the others. Against the General, doing nothing or only shielding loses in 45 to 55 seconds. Against Slyboots and Vorn the results are close to the earlier ones. So the rivals do ask for different play, at least from bots.

Holy War after the second round of changes, on all three maps: doing nothing still loses in about a minute. A bot that only shields every warning reaches Armageddon at 22 against 22 and won all nine of its runs, with between 3 and 18 Believers left, in 12 to 28 seconds of battle. A bot that also Meteors the houses wins before Armageddon in 75 to 125 seconds with any doctrine. The earlier figures:

Holy War, same kind of bots: doing nothing loses to a Crisis of Faith in about a minute, with heathens at 22. Bolts alone hold the line and win on numbers at the bell for two stars, but never clear the houses. A bot that shields every omen and Meteors the houses wins outright in about two minutes. A bot that also marches the flock across the page with Rally lost one run in three, mostly to its own Meteors.

Chapters I to III have been played on an emulator and judged too easy, which is what IV and V are for. IV and V have not been played by a person yet.

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

| 2026-11-15 | The page is 1000 x 1650 units | Fills a tall phone screen. Older, squarer phones get side margins. |
| 2026-11-15 | Leaders convert one follower at a time | A whole flock turning at once would end a round in seconds. One at a time gives the player a clock to race. |
| 2026-11-15 | The third star needs a chain | Stops Bolt-only play from earning full marks, without making it a way to lose. |
| 2026-11-15 | Build moved to Gradle 9.6.0, Android Gradle plugin 9.5.1, Kotlin 2.2.10 | Upgraded by Android Studio on the designer's machine. Supersedes the row above. |

| 2026-11-15 | Difficulty comes from opposition, not tighter numbers | Chapters I to III were too easy because nothing on the page could beat the player and Bolt answered everything. Inquisitors attack, sheltering defeats Bolt, fatigue prices Bolt spam. |
| 2026-11-15 | The new rules apply only from chapter IV | Keeps I to III as a gentle introduction. |

| 2026-11-15 | Borrow from Populous without becoming it | Taken: a rival god, growing settlements, followers who fight. Left out: land sculpting and the slow pace. Kept as a separate five-minute mode so the arcade chapters stay short. |

| 2026-11-15 | Holy War is the core of the game; the chapters are its tutorial | The designer found Holy War far more engaging, specifically the tension of splitting Prayer between attack and defence. |
| 2026-11-15 | Matches end in Armageddon, not a head count | A battle decided by saved Prayer rewards the budgeting that the mode is about. |

| 2026-11-16 | Sounds are generated by a script, not sourced | No licences to track, and nothing to fetch. They are placeholders for a sound designer to replace. |
| 2026-11-16 | Miracles unlock by Holy War wins, one per win | The simplest progression that gives a reason for a second, third and fourth match. |

## 11. Ideas backlog

- Land sculpting (raise, lower, flood), if Holy War proves the mode is worth deepening.
- The rival god using Gust and conversion, and reacting to where the player is looking.

- Ward Totems: zones where no power works, to be knocked out from outside.
- Reinforcement waves of ordinary heathens.
- Prayer only near a standing holy building.


- Heathens who build and repair rods mid-level, so the Defiant chapter fights back.

- Pre-Smite Preview: press and hold to see the consequence before release.
- Poetic Justice bonuses for ironic kills.
- Dust and smoke that block the AI's line of sight.
- Engine choice once the art style is set.
