package com.necromater.smitethemnow.sim

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * One micro-scenario: a village diorama, a flock, some heathens and a clock.
 * [level] picks one of the layouts described in [LEVELS].
 *
 * Coordinates are logical units (W x H), top-down, y pointing down the screen.
 * The view scales this to whatever phone it runs on.
 */
class World(val level: Int = 1, seed: Long = 7L) {

    companion object {
        const val W = 1000f
        const val H = 1650f

        const val MAX_PRAYER = 100f
        const val START_PRAYER = 22f

        /** Prayer per second from one calm Believer. Zealots give double. */
        const val PRAYER_RATE = 0.35f
        /** Prayer for a smiting, plus a bonus per extra link in the chain, up to a cap. */
        const val KILL_REWARD = 2f
        const val CHAIN_BONUS = 1f
        const val CHAIN_BONUS_CAP = 6
        const val CHAIN_WINDOW = 0.8f

        const val BELIEVER_PENALTY = 25f
        const val PROPHET_PENALTY = 50f
        const val DEFECT_PENALTY = 10f
        const val CHAPEL_PENALTY = 15f

        const val BOLT_RADIUS = 42f
        const val METEOR_RADIUS = 120f
        const val METEOR_CHARGE_TIME = 0.9f
        const val BARREL_RADIUS = 115f
        const val GUST_WIDTH = 95f
        const val GUST_FORCE = 520f
        const val GUST_MIN_LENGTH = 60f

        const val THREAT_RADIUS = 300f
        const val NOTICE_AT = 0.3f
        const val SPOOK_RADIUS = 260f
        const val ZEAL_RADIUS = 110f
        const val ZEAL_TIME = 6f
        const val TERROR_RADIUS = 230f
        const val TERROR_TIME = 5f
        const val BURN_TIME = 0.3f
        const val NPC_RADIUS = 13f
        const val MAX_FIRES = 60

        const val SHIELD_RADIUS = 115f
        const val SHIELD_TIME = 6f

        /** Bolts cast within this distance of a rod are swallowed by it. */
        const val ROD_RADIUS = 230f
        /** Bolts a rod can swallow before it overloads and explodes. */
        const val ROD_CAPACITY = 3
        const val ROD_BLAST = 95f
        const val REBUILD_DELAY = 6f
        const val REBUILD_TIME = 6f

        /** The Prophet talks one nearby heathen round at a time. */
        const val PROPHET_AURA = 130f
        const val PROPHET_TIME = 5f
        /** The Heretic does the same to your flock, a little more slowly. */
        const val HERETIC_AURA = 110f
        const val HERETIC_TIME = 5f

        /** Heathens this close to a smiting may see the light on the spot. */
        const val WITNESS_RADIUS = 170f
        const val WITNESS_CHANCE = 0.05f
        const val WITNESS_CHANCE_WITH_PROPHET = 0.10f
        /** Believers who watch one of their own die may walk away. */
        const val DEFECT_CHANCE = 0.12f
        const val DEFECT_CHANCE_WITH_HERETIC = 0.25f

        /** Seconds of fire against a wall before it cracks. */
        const val SCORCH_TIME = 2.5f
        const val CRUSH_MARGIN = 34f

        val LEVELS: List<LevelInfo> = listOf(
            LevelInfo(
                "A Gentle Smiting",
                "A quiet hamlet. A few doubters. Begin here.",
                listOf(
                    "Choose Bolt, then tap a heathen.",
                    "Smite one beside a Believer and",
                    "the Believer prays twice as fast.",
                    "Your Prophet converts any heathen",
                    "who lingers near him. Keep him safe."
                ),
                2
            ),
            LevelInfo(
                "Market Day",
                "Crowds, oil barrels, and a rival preacher.",
                listOf(
                    "The Heretic leads your flock astray.",
                    "His ward eats one Bolt. Send two.",
                    "Hold Meteor until the ring is whole.",
                    "Shield the flock, then let fly.",
                    "Blasts bring buildings down on heads."
                ),
                3
            ),
            LevelInfo(
                "The Defiant",
                "They have built Lightning Rods. The nerve.",
                listOf(
                    "A rod swallows any Bolt in its ring.",
                    "Three Bolts overload it. Blasts",
                    "and Meteors fell it at once.",
                    "They will send someone to rebuild.",
                    "Smite the builder before he is done."
                ),
                4
            )
        )
    }

    private val rng = Random(seed)

    val npcs = ArrayList<Npc>()
    val buildings = ArrayList<Building>()
    val barrels = ArrayList<Barrel>()
    val fires = ArrayList<Fire>()
    val fx = ArrayList<Fx>()
    val shields = ArrayList<Shield>()
    val rods = ArrayList<Rod>()

    /** Mana. Believers generate it, powers spend it. */
    var prayer = START_PRAYER
        private set

    /** Devotion. Drops when you hurt or lose your own flock. At zero you lose. */
    var faith = 100f
        private set

    var timeLimit = 150f
        private set
    var timeLeft = 0f
        private set
    var outcome = Outcome.PLAYING
        private set
    var defeatReason = ""
        private set

    var chain = 0
        private set
    var bestChain = 0
        private set
    var smitten = 0
        private set
    var believersLost = 0
        private set
    var converted = 0
        private set
    var defected = 0
        private set
    private var chainClock = 0f

    var charging = false
        private set
    var chargeX = 0f
        private set
    var chargeY = 0f
        private set
    /** 0..1. The meteor only drops if this reached 1 before release. */
    var charge = 0f
        private set

    /** Screen shake strength for the renderer, decays on its own. */
    var shake = 0f
        private set

    /** Deaths waiting to be reacted to: x, y, and 1 for a heathen or 0 for a Believer. */
    private val witnessed = ArrayList<FloatArray>()

    val chainGoal: Int get() = LEVELS[level.coerceIn(0, LEVELS.size - 1)].chainGoal

    init {
        when (level) {
            0 -> buildHamlet()
            2 -> buildDefiant()
            else -> buildMarket()
        }
        timeLeft = timeLimit
    }

    // ---------------------------------------------------------------- levels

    /** Layouts were drawn for a page 1400 tall. This stretches them to the real height. */
    private fun y(v: Float): Float = v * H / 1400f

    private fun building(x: Float, yTop: Float, w: Float, h: Float, holy: Boolean = false) {
        buildings += Building(x, y(yTop), w, h, holy)
    }

    private fun barrel(x: Float, yy: Float) {
        barrels += Barrel(x, y(yy))
    }

    private fun rod(x: Float, yy: Float) {
        rods += Rod(x, y(yy))
    }

    private fun group(faction: Faction, count: Int, cx: Float, cy: Float, spread: Float) {
        for (i in 0 until count) {
            val n = Npc(
                cx + (rng.nextFloat() - 0.5f) * 2f * spread,
                y(cy) + (rng.nextFloat() - 0.5f) * 2f * spread,
                faction
            )
            place(n)
        }
    }

    private fun leader(faction: Faction, x: Float, yy: Float) {
        val n = Npc(x, y(yy), faction, Role.LEADER)
        if (faction == Faction.NON_BELIEVER) n.hp = 2
        place(n)
    }

    private fun place(n: Npc) {
        n.retarget = rng.nextFloat() * 2f
        n.orbClock = rng.nextFloat()
        keepInBounds(n)
        n.homeX = n.x
        n.homeY = n.y
        npcs += n
    }

    /** Chapter I: one flock and its Prophet, a handful of heathens, one easy barrel pair. */
    private fun buildHamlet() {
        timeLimit = 120f
        building(110f, 960f, 200f, 130f, holy = true)
        building(640f, 250f, 180f, 120f)
        building(160f, 420f, 150f, 110f)

        group(Faction.BELIEVER, 6, 280f, 1200f, 70f)
        leader(Faction.BELIEVER, 360f, 1130f)

        group(Faction.NON_BELIEVER, 4, 680f, 520f, 70f)
        group(Faction.NON_BELIEVER, 2, 380f, 700f, 40f)
        group(Faction.NON_BELIEVER, 1, 800f, 1000f, 20f)
        group(Faction.NON_BELIEVER, 1, 480f, 240f, 20f)
        // One loiterer beside the Prophet: the first conversion lesson.
        group(Faction.NON_BELIEVER, 1, 440f, 1110f, 10f)

        barrel(640f, 610f)
        barrel(720f, 630f)
    }

    /** Chapter II: the market village, now with a Heretic. */
    private fun buildMarket() {
        timeLimit = 150f
        building(90f, 880f, 200f, 130f, holy = true)
        building(610f, 1060f, 220f, 140f)
        building(140f, 250f, 180f, 120f)
        building(700f, 170f, 170f, 120f)
        building(430f, 760f, 150f, 110f)

        group(Faction.BELIEVER, 6, 230f, 1130f, 70f)
        group(Faction.BELIEVER, 4, 790f, 420f, 60f)
        leader(Faction.BELIEVER, 300f, 1080f)

        group(Faction.NON_BELIEVER, 7, 690f, 980f, 80f)
        group(Faction.NON_BELIEVER, 6, 400f, 540f, 90f)
        group(Faction.NON_BELIEVER, 1, 500f, 920f, 20f)
        group(Faction.NON_BELIEVER, 1, 240f, 700f, 20f)
        group(Faction.NON_BELIEVER, 1, 820f, 720f, 20f)
        group(Faction.NON_BELIEVER, 1, 520f, 300f, 20f)
        group(Faction.NON_BELIEVER, 1, 880f, 1260f, 20f)
        group(Faction.NON_BELIEVER, 1, 120f, 120f, 20f)
        leader(Faction.NON_BELIEVER, 540f, 470f)

        // A line of oil barrels through the market: light one end, watch it run.
        barrel(300f, 560f)
        barrel(385f, 585f)
        barrel(470f, 610f)
        barrel(555f, 635f)
        barrel(640f, 660f)
        // And two by the tavern, uncomfortably close to the chapel flock.
        barrel(560f, 1010f)
        barrel(520f, 1100f)
    }

    /** Chapter III: three Lightning Rods guard the heathen quarters, and they get rebuilt. */
    private fun buildDefiant() {
        timeLimit = 165f
        building(60f, 110f, 190f, 110f, holy = true)
        building(620f, 200f, 180f, 110f)
        building(840f, 1060f, 130f, 120f)
        building(60f, 980f, 170f, 120f)

        group(Faction.BELIEVER, 4, 170f, 310f, 55f)
        group(Faction.BELIEVER, 5, 500f, 1150f, 65f)
        leader(Faction.BELIEVER, 440f, 1210f)

        rod(740f, 420f)
        rod(300f, 760f)
        rod(760f, 900f)

        group(Faction.NON_BELIEVER, 6, 740f, 470f, 90f)
        group(Faction.NON_BELIEVER, 6, 330f, 770f, 75f)
        group(Faction.NON_BELIEVER, 5, 760f, 930f, 80f)
        leader(Faction.NON_BELIEVER, 800f, 500f)

        // The first barrel sits just outside every ring. The last one sits next to a rod.
        barrel(560f, 600f)
        barrel(480f, 650f)
        barrel(400f, 700f)
        barrel(330f, 740f)
    }

    // ---------------------------------------------------------------- queries

    fun count(faction: Faction): Int {
        var c = 0
        for (n in npcs) if (n.alive && n.faction == faction) c++
        return c
    }

    fun canAfford(power: Power): Boolean = prayer >= power.cost

    val flockKept: Boolean get() = believersLost == 0 && defected == 0
    val chainMade: Boolean get() = bestChain >= chainGoal

    /** 1 to 3, only meaningful after a victory. */
    fun stars(): Int {
        var s = 1
        if (flockKept) s++
        if (chainMade) s++
        return s
    }

    fun isShielded(n: Npc): Boolean {
        if (n.faction != Faction.BELIEVER) return false
        for (s in shields) {
            if (dist2(n.x, n.y, s.x, s.y) < s.radius * s.radius) return true
        }
        return false
    }

    fun leaderOf(faction: Faction): Npc? {
        for (n in npcs) if (n.alive && n.isLeader && n.faction == faction) return n
        return null
    }

    private fun rodCovering(x: Float, y: Float): Rod? {
        var best: Rod? = null
        var bestD = ROD_RADIUS * ROD_RADIUS
        for (r in rods) {
            if (!r.alive) continue
            val d = dist2(x, y, r.x, r.y)
            if (d < bestD) {
                bestD = d
                best = r
            }
        }
        return best
    }

    private fun nearest(x: Float, y: Float, faction: Faction, followersOnly: Boolean, skip: Npc? = null): Npc? {
        var best: Npc? = null
        var bestD = Float.MAX_VALUE
        for (n in npcs) {
            if (!n.alive || n.faction != faction || n === skip) continue
            if (followersOnly && n.isLeader) continue
            val d = dist2(x, y, n.x, n.y)
            if (d < bestD) {
                bestD = d
                best = n
            }
        }
        return best
    }

    // ---------------------------------------------------------------- powers

    /** Tap. Instant, no warning, hits the single NPC nearest the tap. */
    fun castLightning(x: Float, y: Float): Boolean {
        if (outcome != Outcome.PLAYING) return false
        if (!spend(Power.LIGHTNING, x, y)) return false
        shake = max(shake, 0.12f)

        val rod = rodCovering(x, y)
        if (rod != null) {
            // The rod takes the strike. Enough of them and it cooks.
            fx += Fx(FxKind.BOLT, rod.x, rod.y, rod.x, rod.y, 1f, 0.28f)
            rod.charges++
            if (rod.charges >= ROD_CAPACITY) {
                fellRod(rod, "OVERLOAD")
                explode(rod.x, rod.y, ROD_BLAST, 45f, 2.5f, false)
            } else {
                fx += Fx(FxKind.TEXT, x, y, x, y, 1f, 0.9f, "GROUNDED", false)
            }
            return true
        }
        fx += Fx(FxKind.BOLT, x, y, x, y, 1f, 0.28f)

        var best: Npc? = null
        var bestD = BOLT_RADIUS * BOLT_RADIUS
        for (n in npcs) {
            if (!n.alive || isShielded(n)) continue
            val d = dist2(x, y, n.x, n.y)
            if (d < bestD) {
                bestD = d
                best = n
            }
        }
        if (best != null) {
            if (best.hp > 1) {
                best.hp--
                fx += Fx(FxKind.TEXT, best.x, best.y, best.x, best.y, 1f, 0.9f, "WARDED", false)
            } else {
                kill(best)
            }
        }
        for (b in barrels) {
            if (b.alive && dist2(x, y, b.x, b.y) < BOLT_RADIUS * BOLT_RADIUS) lightFuse(b, 0.05f)
        }
        return true
    }

    /** Press and hold. Everyone can see it coming, which is the point. */
    fun beginCharge(x: Float, y: Float): Boolean {
        if (outcome != Outcome.PLAYING) return false
        if (!canAfford(Power.METEOR)) {
            fx += Fx(FxKind.TEXT, x, y, x, y, 1f, 0.9f, "MORE PRAYER", false)
            return false
        }
        charging = true
        charge = 0f
        chargeX = x
        chargeY = y
        return true
    }

    fun moveCharge(x: Float, y: Float) {
        if (!charging) return
        chargeX = x
        chargeY = y
    }

    /** Release. A full charge drops the meteor, an early release costs nothing. */
    fun releaseCharge() {
        if (!charging) return
        charging = false
        if (outcome != Outcome.PLAYING) return
        if (charge < 1f) {
            fx += Fx(FxKind.TEXT, chargeX, chargeY, chargeX, chargeY, 1f, 0.8f, "FIZZLE", false)
            return
        }
        if (!spend(Power.METEOR, chargeX, chargeY)) return
        explode(chargeX, chargeY, METEOR_RADIUS, 85f, 4.5f, true)
        shake = max(shake, 0.5f)
    }

    fun cancelCharge() {
        charging = false
    }

    /** Swipe. Shoves people and carries fire along the swipe direction. */
    fun castGust(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        if (outcome != Outcome.PLAYING) return false
        val dx = x2 - x1
        val dy = y2 - y1
        val len = hypot(dx, dy)
        if (len < GUST_MIN_LENGTH) return false
        if (!spend(Power.GUST, x1, y1)) return false
        val ux = dx / len
        val uy = dy / len
        fx += Fx(FxKind.GUST, x1, y1, x2, y2, GUST_WIDTH, 0.55f)

        for (n in npcs) {
            if (!n.alive) continue
            if (distToSegment(n.x, n.y, x1, y1, x2, y2) < GUST_WIDTH) {
                n.pushX += ux * GUST_FORCE
                n.pushY += uy * GUST_FORCE
            }
        }
        for (f in fires) {
            if (distToSegment(f.x, f.y, x1, y1, x2, y2) < GUST_WIDTH + f.radius * 0.5f) {
                f.vx = ux * 260f
                f.vy = uy * 260f
                f.driftTime = 0.9f
                f.ttl = max(f.ttl, 2.5f)
            }
        }
        return true
    }

    /** Tap. Believers inside the circle cannot be harmed until it fades. Heathens can. */
    fun castShield(x: Float, y: Float): Boolean {
        if (outcome != Outcome.PLAYING) return false
        if (!spend(Power.SHIELD, x, y)) return false
        shields += Shield(x, y, SHIELD_RADIUS, SHIELD_TIME)
        return true
    }

    private fun spend(power: Power, x: Float, y: Float): Boolean {
        if (prayer < power.cost) {
            fx += Fx(FxKind.TEXT, x, y, x, y, 1f, 0.9f, "MORE PRAYER", false)
            return false
        }
        prayer -= power.cost
        return true
    }

    // ---------------------------------------------------------------- consequences

    /** [heavy] is a Meteor: a direct hit levels a sound building in one go. */
    private fun explode(x: Float, y: Float, radius: Float, fireRadius: Float, fireTtl: Float, heavy: Boolean) {
        fx += Fx(FxKind.BLAST, x, y, x, y, radius, 0.45f)
        shake = max(shake, 0.3f)
        val r2 = radius * radius
        for (n in npcs) {
            if (n.alive && dist2(x, y, n.x, n.y) < r2) kill(n)
        }
        for (b in buildings) {
            if (!b.standing) continue
            val d = distToRect(x, y, b)
            if (d < radius * 0.6f) damage(b, if (heavy && d < 25f) 2 else 1)
        }
        for (b in barrels) {
            if (b.alive && dist2(x, y, b.x, b.y) < r2) lightFuse(b, 0.18f)
        }
        for (r in rods) {
            if (r.alive && dist2(x, y, r.x, r.y) < r2) fellRod(r, "ROD FELLED")
        }
        addFire(Fire(x, y, fireRadius, fireTtl))
    }

    private fun fellRod(r: Rod, label: String) {
        r.alive = false
        r.charges = 0
        r.progress = 0f
        r.rebuildDelay = REBUILD_DELAY
        fx += Fx(FxKind.TEXT, r.x, r.y, r.x, r.y, 1.15f, 1.2f, label, true)
    }

    private fun damage(b: Building, amount: Int) {
        if (!b.standing) return
        b.hp -= amount
        b.scorch = 0f
        if (b.hp > 0) return

        // Down it comes, on whoever was leaning against it.
        b.standing = false
        val cx = b.x + b.w / 2f
        val cy = b.y + b.h / 2f
        fx += Fx(FxKind.BLAST, cx, cy, cx, cy, max(b.w, b.h) * 0.75f, 0.6f)
        shake = max(shake, 0.4f)
        var crushed = 0
        for (n in npcs) {
            if (!n.alive || distToRect(n.x, n.y, b) > CRUSH_MARGIN) continue
            val wasHeathen = n.faction == Faction.NON_BELIEVER
            kill(n)
            if (wasHeathen && !n.alive) crushed++
        }
        if (b.holy) {
            faith = max(0f, faith - CHAPEL_PENALTY)
            fx += Fx(FxKind.TEXT, cx, cy, cx, cy, 1.3f, 1.5f, "SACRILEGE", false)
            for (n in npcs) {
                if (n.alive && n.faction == Faction.BELIEVER && dist2(cx, cy, n.x, n.y) < 300f * 300f) terrify(n, cx, cy)
            }
        } else if (crushed > 0) {
            prayer = min(MAX_PRAYER, prayer + 5f)
            fx += Fx(FxKind.TEXT, cx, cy - 30f, cx, cy - 30f, 1.2f, 1.5f, "POETIC JUSTICE", true)
        }
    }

    private fun lightFuse(b: Barrel, delay: Float) {
        if (b.alive && b.fuse < 0f) b.fuse = delay
    }

    private fun addFire(f: Fire) {
        if (fires.size < MAX_FIRES) fires += f
    }

    private fun terrify(n: Npc, fromX: Float, fromY: Float) {
        n.mood = Mood.TERROR
        n.moodTime = TERROR_TIME
        n.fearX = fromX
        n.fearY = fromY
    }

    private fun kill(n: Npc) {
        if (!n.alive || n.grace > 0f) return
        if (isShielded(n)) {
            if (n.sparedClock <= 0f) {
                n.sparedClock = 1.2f
                fx += Fx(FxKind.TEXT, n.x, n.y, n.x, n.y, 0.9f, 0.9f, "SPARED", true)
            }
            return
        }
        n.alive = false
        n.task = null
        if (n.faction == Faction.NON_BELIEVER) {
            chain = if (chainClock > 0f) chain + 1 else 1
            chainClock = CHAIN_WINDOW
            bestChain = max(bestChain, chain)
            smitten++
            prayer = min(MAX_PRAYER, prayer + KILL_REWARD + CHAIN_BONUS * min(chain - 1, CHAIN_BONUS_CAP))
            if (n.isLeader) {
                prayer = min(MAX_PRAYER, prayer + 10f)
                fx += Fx(FxKind.TEXT, n.x, n.y, n.x, n.y, 1.4f, 1.5f, "HERETIC SMITTEN", true)
                for (o in npcs) {
                    if (o.alive && o.faction == Faction.NON_BELIEVER) {
                        o.mood = Mood.SPOOKED
                        o.moodTime = 4f
                    }
                }
            } else {
                val label = if (chain > 1) "x$chain CHAIN" else "SMITTEN"
                fx += Fx(FxKind.TEXT, n.x, n.y, n.x, n.y, 1f + 0.12f * min(chain, 6), 1.1f, label, true)
            }
            witnessed += floatArrayOf(n.x, n.y, 1f)
        } else {
            believersLost++
            chain = 0
            chainClock = 0f
            if (n.isLeader) {
                faith = max(0f, faith - PROPHET_PENALTY)
                fx += Fx(FxKind.TEXT, n.x, n.y, n.x, n.y, 1.4f, 1.6f, "YOUR PROPHET!", false)
                for (o in npcs) {
                    if (o.alive && o.faction == Faction.BELIEVER) terrify(o, n.x, n.y)
                }
            } else {
                faith = max(0f, faith - BELIEVER_PENALTY)
                fx += Fx(FxKind.TEXT, n.x, n.y, n.x, n.y, 1.3f, 1.4f, "DOUBT!", false)
            }
            witnessed += floatArrayOf(n.x, n.y, 0f)
        }
    }

    /**
     * How the survivors take it. This runs once per tick, after every death in that tick,
     * so nobody converts in the middle of the blast that is about to hit them.
     */
    private fun reactToDeaths() {
        if (witnessed.isEmpty()) return
        val seeTheLight = if (leaderOf(Faction.BELIEVER) != null) WITNESS_CHANCE_WITH_PROPHET else WITNESS_CHANCE
        val walkAway = if (leaderOf(Faction.NON_BELIEVER) != null) DEFECT_CHANCE_WITH_HERETIC else DEFECT_CHANCE
        for (e in witnessed) {
            val ex = e[0]
            val ey = e[1]
            val heathenDied = e[2] > 0.5f
            for (o in npcs) {
                if (!o.alive) continue
                val d = dist2(ex, ey, o.x, o.y)
                if (heathenDied) {
                    if (o.faction == Faction.BELIEVER) {
                        // A smiting right next to a Believer who came out unharmed: zealotry.
                        if (o.mood != Mood.TERROR && d < ZEAL_RADIUS * ZEAL_RADIUS) {
                            o.mood = Mood.ZEALOT
                            o.moodTime = ZEAL_TIME
                        }
                    } else if (!o.isLeader) {
                        if (d < WITNESS_RADIUS * WITNESS_RADIUS && rng.nextFloat() < seeTheLight) {
                            convert(o, "CONVERTED")
                        } else if (d < SPOOK_RADIUS * SPOOK_RADIUS) {
                            // The rest run for the nearest human shield.
                            o.mood = Mood.SPOOKED
                            o.moodTime = 3f
                        }
                    }
                } else if (o.faction == Faction.BELIEVER && d < TERROR_RADIUS * TERROR_RADIUS) {
                    if (!o.isLeader && !isShielded(o) && rng.nextFloat() < walkAway) {
                        defect(o, "LOST TO DOUBT", 0f)
                    } else {
                        terrify(o, ex, ey)
                    }
                }
            }
        }
        witnessed.clear()
    }

    private fun convert(n: Npc, label: String) {
        n.faction = Faction.BELIEVER
        n.mood = Mood.CALM
        n.moodTime = 0f
        n.sway = 0f
        n.task = null
        n.grace = 2f
        n.retarget = 0f
        // Join the nearest flock.
        val friend = nearest(n.x, n.y, Faction.BELIEVER, false, n)
        n.homeX = friend?.homeX ?: n.x
        n.homeY = friend?.homeY ?: n.y
        converted++
        prayer = min(MAX_PRAYER, prayer + 3f)
        fx += Fx(FxKind.TEXT, n.x, n.y, n.x, n.y, 1.05f, 1.3f, label, true)
    }

    private fun defect(n: Npc, label: String, faithCost: Float) {
        n.faction = Faction.NON_BELIEVER
        n.mood = Mood.CALM
        n.moodTime = 0f
        n.sway = 0f
        n.retarget = 0f
        defected++
        faith = max(0f, faith - faithCost)
        fx += Fx(FxKind.TEXT, n.x, n.y, n.x, n.y, 1.05f, 1.3f, label, false)
    }

    // ---------------------------------------------------------------- tick

    fun update(dtIn: Float) {
        val dt = min(max(dtIn, 0f), 0.05f)
        updateFx(dt)
        if (shake > 0f) shake = max(0f, shake - dt)
        if (outcome != Outcome.PLAYING) return

        timeLeft = max(0f, timeLeft - dt)
        if (chainClock > 0f) {
            chainClock -= dt
            if (chainClock <= 0f) chain = 0
        }
        if (charging) charge = min(1f, charge + dt / METEOR_CHARGE_TIME)

        var si = shields.size - 1
        while (si >= 0) {
            shields[si].ttl -= dt
            if (shields[si].ttl <= 0f) shields.removeAt(si)
            si--
        }
        updateBarrels(dt)
        updateFires(dt)
        updatePreaching(dt)
        updateRods(dt)
        updateNpcs(dt)
        reactToDeaths()
        prayer = min(MAX_PRAYER, prayer)
        checkOutcome()
    }

    private fun updateFx(dt: Float) {
        var i = fx.size - 1
        while (i >= 0) {
            val f = fx[i]
            f.age += dt
            if (f.age >= f.life) fx.removeAt(i)
            i--
        }
    }

    private fun updateBarrels(dt: Float) {
        for (i in barrels.indices) {
            val b = barrels[i]
            if (!b.alive || b.fuse < 0f) continue
            b.fuse -= dt
            if (b.fuse <= 0f) {
                b.alive = false
                explode(b.x, b.y, BARREL_RADIUS, 70f, 3.5f, false)
            }
        }
    }

    private fun updateFires(dt: Float) {
        var spawned: ArrayList<Fire>? = null
        var i = fires.size - 1
        while (i >= 0) {
            val f = fires[i]
            f.ttl -= dt
            if (f.driftTime > 0f) {
                f.driftTime -= dt
                f.x = (f.x + f.vx * dt).coerceIn(0f, W)
                f.y = (f.y + f.vy * dt).coerceIn(0f, H)
                f.trail += hypot(f.vx, f.vy) * dt
                if (f.trail > 55f) {
                    // Wind-driven fire leaves burning ground behind it.
                    f.trail = 0f
                    val list = spawned ?: ArrayList<Fire>().also { spawned = it }
                    list += Fire(f.x, f.y, f.radius * 0.8f, 3f)
                }
            }
            for (b in barrels) {
                val reach = f.radius + 10f
                if (b.alive && dist2(f.x, f.y, b.x, b.y) < reach * reach) lightFuse(b, 0.25f)
            }
            for (b in buildings) {
                // Fire against a wall cracks it, then brings it down.
                if (b.standing && distToRect(f.x, f.y, b) < f.radius * 0.5f) {
                    b.scorch += dt
                    if (b.scorch >= SCORCH_TIME) damage(b, 1)
                }
            }
            if (f.ttl <= 0f) fires.removeAt(i)
            i--
        }
        val list = spawned
        if (list != null) for (f in list) addFire(f)
    }

    /** Each leader works on the nearest follower of the other side within reach. */
    private fun updatePreaching(dt: Float) {
        for (n in npcs) n.preached = false

        val prophet = leaderOf(Faction.BELIEVER)
        if (prophet != null && prophet.mood != Mood.TERROR) {
            val t = nearest(prophet.x, prophet.y, Faction.NON_BELIEVER, true)
            if (t != null && dist2(prophet.x, prophet.y, t.x, t.y) < PROPHET_AURA * PROPHET_AURA) {
                t.preached = true
                t.sway += dt / PROPHET_TIME
                if (t.sway >= 1f) convert(t, "CONVERTED")
            }
        }

        val heretic = leaderOf(Faction.NON_BELIEVER)
        if (heretic != null) {
            var t: Npc? = null
            var bestD = HERETIC_AURA * HERETIC_AURA
            for (n in npcs) {
                // Zealots and the shielded are not listening.
                if (!n.alive || n.faction != Faction.BELIEVER || n.isLeader) continue
                if (n.mood == Mood.ZEALOT || n.grace > 0f || isShielded(n)) continue
                val d = dist2(heretic.x, heretic.y, n.x, n.y)
                if (d < bestD) {
                    bestD = d
                    t = n
                }
            }
            if (t != null) {
                t.preached = true
                t.sway += dt / HERETIC_TIME
                if (t.sway >= 1f) defect(t, "LED ASTRAY", DEFECT_PENALTY)
            }
        }

        for (n in npcs) {
            if (!n.preached && n.sway > 0f) n.sway = max(0f, n.sway - dt * 0.5f)
        }
    }

    /** Fallen rods get rebuilt: after a pause, the nearest idle heathen walks over and sets to work. */
    private fun updateRods(dt: Float) {
        for (r in rods) {
            if (r.alive || r.rebuildsLeft <= 0) continue
            val b = r.builder
            if (b == null || !b.alive || b.faction != Faction.NON_BELIEVER || b.task !== r) {
                r.builder = null
                r.rebuildDelay -= dt
                if (r.rebuildDelay > 0f) continue
                var best: Npc? = null
                var bestD = 700f * 700f
                for (n in npcs) {
                    if (!n.alive || n.faction != Faction.NON_BELIEVER || n.isLeader || n.task != null) continue
                    val d = dist2(r.x, r.y, n.x, n.y)
                    if (d < bestD) {
                        bestD = d
                        best = n
                    }
                }
                if (best == null) {
                    r.rebuildDelay = 2f
                } else {
                    best.task = r
                    r.builder = best
                }
                continue
            }
            if (b.mood != Mood.SPOOKED && dist2(r.x, r.y, b.x, b.y) < 34f * 34f) {
                r.progress += dt / REBUILD_TIME
                if (r.progress >= 1f) {
                    r.alive = true
                    r.charges = 0
                    r.progress = 0f
                    r.rebuildsLeft--
                    r.builder = null
                    b.task = null
                    fx += Fx(FxKind.TEXT, r.x, r.y, r.x, r.y, 1.15f, 1.3f, "ROD RAISED", false)
                }
            }
        }
    }

    private fun updateNpcs(dt: Float) {
        val pushDecay = exp(-3.5f * dt)
        for (i in npcs.indices) {
            val n = npcs[i]
            if (!n.alive) continue

            if (n.sparedClock > 0f) n.sparedClock -= dt
            if (n.grace > 0f) n.grace -= dt
            if (n.moodTime > 0f) {
                n.moodTime -= dt
                if (n.moodTime <= 0f) n.mood = Mood.CALM
            }

            val speed: Float
            var stopAt = 5f
            if (n.faction == Faction.BELIEVER) {
                if (n.mood == Mood.TERROR) {
                    // Run away from whatever just happened. No praying while panicking.
                    val dx = n.x - n.fearX
                    val dy = n.y - n.fearY
                    val d = max(1f, hypot(dx, dy))
                    n.targetX = n.x + dx / d * 120f
                    n.targetY = n.y + dy / d * 120f
                    speed = if (n.isLeader) 110f else 150f
                } else {
                    n.retarget -= dt
                    if (n.retarget <= 0f) {
                        val roam = if (n.isLeader) 50f else 90f
                        n.retarget = 1.5f + rng.nextFloat() * 2.5f
                        n.targetX = n.homeX + (rng.nextFloat() - 0.5f) * roam
                        n.targetY = n.homeY + (rng.nextFloat() - 0.5f) * roam
                    }
                    // New converts hurry to their flock, then settle.
                    speed = if (dist2(n.x, n.y, n.homeX, n.homeY) > 150f * 150f) 90f else 32f
                    val rate = PRAYER_RATE * (if (n.mood == Mood.ZEALOT) 2f else 1f)
                    prayer += rate * dt
                    n.orbClock += rate * dt
                    if (n.orbClock >= 1.2f) {
                        n.orbClock = 0f
                        fx += Fx(FxKind.ORB, n.x, n.y, n.x, n.y, 1f, 0.9f)
                    }
                }
            } else if (n.isLeader) {
                // The Heretic walks to your flock and starts talking. He does not scare.
                val prey = nearest(n.x, n.y, Faction.BELIEVER, true)
                if (prey != null) {
                    n.targetX = prey.x
                    n.targetY = prey.y
                }
                stopAt = 70f
                speed = 38f
            } else {
                // They take a moment to notice the sky glowing, then they run.
                if (charging && charge > NOTICE_AT && dist2(n.x, n.y, chargeX, chargeY) < THREAT_RADIUS * THREAT_RADIUS) {
                    n.mood = Mood.SPOOKED
                    n.moodTime = 2.5f
                }
                val job = n.task
                if (n.mood == Mood.SPOOKED) {
                    // Human shield: hide among the faithful.
                    val shield = nearest(n.x, n.y, Faction.BELIEVER, false)
                    if (shield != null) {
                        n.targetX = shield.x
                        n.targetY = shield.y
                    }
                    stopAt = 22f
                    speed = 170f
                } else if (job != null && !job.alive) {
                    n.targetX = job.x
                    n.targetY = job.y + 18f
                    stopAt = 8f
                    speed = 75f
                } else {
                    if (job != null) n.task = null
                    n.retarget -= dt
                    if (n.retarget <= 0f) {
                        n.retarget = 2f + rng.nextFloat() * 3f
                        n.targetX = (n.x + (rng.nextFloat() - 0.5f) * 440f).coerceIn(40f, W - 40f)
                        n.targetY = (n.y + (rng.nextFloat() - 0.5f) * 440f).coerceIn(40f, H - 40f)
                    }
                    speed = 50f
                }
            }

            var vx = 0f
            var vy = 0f
            steerAround(n)
            val tdx = goalX - n.x
            val tdy = goalY - n.y
            val td = hypot(tdx, tdy)
            if (td > (if (detour) 2f else stopAt)) {
                vx = tdx / td * speed
                vy = tdy / td * speed
            }

            // Everyone tries to walk out of fire. Being blown into it is another matter.
            var inFire = false
            for (f in fires) {
                val dx = n.x - f.x
                val dy = n.y - f.y
                val d = hypot(dx, dy)
                if (d < f.radius) inFire = true
                val edge = f.radius + 45f
                if (d < edge && d > 0.01f) {
                    val k = (edge - d) / edge * 220f
                    vx += dx / d * k
                    vy += dy / d * k
                }
            }
            n.heat = if (inFire) n.heat + dt else max(0f, n.heat - dt * 2f)

            for (j in npcs.indices) {
                if (j == i) continue
                val o = npcs[j]
                if (!o.alive) continue
                val dx = n.x - o.x
                val dy = n.y - o.y
                val d = hypot(dx, dy)
                if (d < NPC_RADIUS * 2f && d > 0.01f) {
                    vx += dx / d * 60f
                    vy += dy / d * 60f
                }
            }

            n.x += (vx + n.pushX) * dt
            n.y += (vy + n.pushY) * dt
            n.pushX *= pushDecay
            n.pushY *= pushDecay
            keepInBounds(n)

            if (n.heat > BURN_TIME) kill(n)
        }
    }

    // Where this NPC should head right now: its target, or a building corner on the way there.
    private var goalX = 0f
    private var goalY = 0f
    private var detour = false

    /**
     * If a standing building is in the straight line to the target, aim for the corner
     * that gives the shortest way round. Without this, people walk into walls and stay there.
     */
    private fun steerAround(n: Npc) {
        goalX = n.targetX
        goalY = n.targetY
        detour = false
        val inner = NPC_RADIUS - 3f
        val outer = NPC_RADIUS + 10f
        for (b in buildings) {
            if (!b.standing) continue
            // A target against or inside the wall cannot be walked round. Just press on.
            if (n.targetX > b.x - NPC_RADIUS - 2f && n.targetX < b.x + b.w + NPC_RADIUS + 2f &&
                n.targetY > b.y - NPC_RADIUS - 2f && n.targetY < b.y + b.h + NPC_RADIUS + 2f
            ) continue
            if (!crosses(n.x, n.y, n.targetX, n.targetY, b, inner)) continue

            var best = Float.MAX_VALUE
            for (k in 0 until 4) {
                val cx = cornerX(b, k, outer)
                val cy = cornerY(b, k, outer)
                val reach = hypot(cx - n.x, cy - n.y)
                // Already standing on this corner, or cannot walk straight to it.
                if (reach < 6f || crosses(n.x, n.y, cx, cy, b, inner)) continue

                // From that corner: straight to the target if possible, else via one more corner.
                var tail = Float.MAX_VALUE
                if (!crosses(cx, cy, n.targetX, n.targetY, b, inner)) {
                    tail = hypot(n.targetX - cx, n.targetY - cy)
                } else {
                    for (j in 0 until 4) {
                        if (j == k) continue
                        val jx = cornerX(b, j, outer)
                        val jy = cornerY(b, j, outer)
                        if (crosses(cx, cy, jx, jy, b, inner) || crosses(jx, jy, n.targetX, n.targetY, b, inner)) continue
                        tail = min(tail, hypot(jx - cx, jy - cy) + hypot(n.targetX - jx, n.targetY - jy))
                    }
                }
                if (tail == Float.MAX_VALUE) continue
                if (reach + tail < best) {
                    best = reach + tail
                    goalX = cx
                    goalY = cy
                    detour = true
                }
            }
            return
        }
    }

    private fun cornerX(b: Building, k: Int, pad: Float): Float =
        (if (k % 2 == 0) b.x - pad else b.x + b.w + pad).coerceIn(NPC_RADIUS, W - NPC_RADIUS)

    private fun cornerY(b: Building, k: Int, pad: Float): Float =
        (if (k < 2) b.y - pad else b.y + b.h + pad).coerceIn(NPC_RADIUS, H - NPC_RADIUS)

    /** Does the segment a-b pass through the building, grown by [pad] on every side? */
    private fun crosses(ax: Float, ay: Float, bx: Float, by: Float, b: Building, pad: Float): Boolean {
        var t0 = 0f
        var t1 = 1f
        val dx = bx - ax
        val dy = by - ay
        val left = b.x - pad
        val right = b.x + b.w + pad
        val top = b.y - pad
        val bottom = b.y + b.h + pad
        if (abs(dx) < 0.0001f) {
            if (ax <= left || ax >= right) return false
        } else {
            val u0 = (left - ax) / dx
            val u1 = (right - ax) / dx
            t0 = max(t0, min(u0, u1))
            t1 = min(t1, max(u0, u1))
            if (t0 >= t1) return false
        }
        if (abs(dy) < 0.0001f) {
            if (ay <= top || ay >= bottom) return false
        } else {
            val u0 = (top - ay) / dy
            val u1 = (bottom - ay) / dy
            t0 = max(t0, min(u0, u1))
            t1 = min(t1, max(u0, u1))
            if (t0 >= t1) return false
        }
        return (t1 - t0) * hypot(dx, dy) > 3f
    }

    private fun keepInBounds(n: Npc) {
        val r = NPC_RADIUS
        n.x = n.x.coerceIn(r, W - r)
        n.y = n.y.coerceIn(r, H - r)
        for (b in buildings) {
            if (!b.standing) continue
            val left = b.x - r
            val right = b.x + b.w + r
            val top = b.y - r
            val bottom = b.y + b.h + r
            if (n.x > left && n.x < right && n.y > top && n.y < bottom) {
                val toLeft = n.x - left
                val toRight = right - n.x
                val toTop = n.y - top
                val toBottom = bottom - n.y
                val m = min(min(toLeft, toRight), min(toTop, toBottom))
                when (m) {
                    toLeft -> n.x = left
                    toRight -> n.x = right
                    toTop -> n.y = top
                    else -> n.y = bottom
                }
            }
        }
    }

    private fun checkOutcome() {
        val heathens = count(Faction.NON_BELIEVER)
        val flock = count(Faction.BELIEVER)
        when {
            faith <= 0f -> lose("Crisis of Faith")
            flock == 0 -> lose("Nobody left to worship you")
            heathens == 0 -> {
                outcome = Outcome.VICTORY
                charging = false
            }
            timeLeft <= 0f -> lose("They stopped paying attention")
        }
    }

    private fun lose(reason: String) {
        outcome = Outcome.DEFEAT
        defeatReason = reason
        charging = false
    }

    // ---------------------------------------------------------------- geometry

    private fun dist2(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return dx * dx + dy * dy
    }

    private fun distToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 <= 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    private fun distToRect(px: Float, py: Float, b: Building): Float {
        val cx = px.coerceIn(b.x, b.x + b.w)
        val cy = py.coerceIn(b.y, b.y + b.h)
        return hypot(px - cx, py - cy)
    }
}
