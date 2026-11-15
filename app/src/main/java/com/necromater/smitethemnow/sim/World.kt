package com.necromater.smitethemnow.sim

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * One micro-scenario: a village diorama, a flock, some heathens and a clock.
 *
 * Coordinates are logical units (W x H), top-down, y pointing down the screen.
 * The view scales this to whatever phone it runs on.
 */
class World(seed: Long = 7L) {

    companion object {
        const val W = 1000f
        const val H = 1400f

        const val TIME_LIMIT = 150f
        const val MAX_PRAYER = 100f
        const val START_PRAYER = 30f

        /** Prayer per second from one calm Believer. Zealots give double. */
        const val PRAYER_RATE = 0.35f
        /** Prayer for a smiting, plus a bonus per extra link in the chain, up to a cap. */
        const val KILL_REWARD = 2f
        const val CHAIN_BONUS = 1f
        const val CHAIN_BONUS_CAP = 6
        const val CHAIN_WINDOW = 0.8f
        const val BELIEVER_PENALTY = 25f

        const val BOLT_RADIUS = 42f
        const val METEOR_RADIUS = 120f
        const val METEOR_CHARGE_TIME = 0.9f
        const val BARREL_RADIUS = 115f
        const val GUST_WIDTH = 95f
        const val GUST_FORCE = 520f
        const val GUST_MIN_LENGTH = 60f

        const val THREAT_RADIUS = 300f
        const val NOTICE_AT = 0.35f
        const val SPOOK_RADIUS = 260f
        const val ZEAL_RADIUS = 110f
        const val ZEAL_TIME = 6f
        const val TERROR_RADIUS = 230f
        const val TERROR_TIME = 5f
        const val BURN_TIME = 0.3f
        const val NPC_RADIUS = 11f
        const val MAX_FIRES = 60
    }

    private val rng = Random(seed)

    val npcs = ArrayList<Npc>()
    val buildings = ArrayList<Building>()
    val barrels = ArrayList<Barrel>()
    val fires = ArrayList<Fire>()
    val fx = ArrayList<Fx>()

    /** Mana. Believers generate it, powers spend it. */
    var prayer = START_PRAYER
        private set

    /** Devotion. Drops when you hurt your own flock. At zero you lose. */
    var faith = 100f
        private set

    var timeLeft = TIME_LIMIT
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

    init {
        buildLevel()
    }

    // ---------------------------------------------------------------- level

    private fun buildLevel() {
        buildings += Building(90f, 880f, 200f, 130f)    // chapel
        buildings += Building(610f, 1060f, 220f, 140f)  // tavern
        buildings += Building(140f, 250f, 180f, 120f)   // market hall
        buildings += Building(700f, 170f, 170f, 120f)   // shrine
        buildings += Building(430f, 760f, 150f, 110f)   // well house

        // Two flocks to protect.
        spawnGroup(Faction.BELIEVER, 6, 230f, 1130f, 70f)
        spawnGroup(Faction.BELIEVER, 4, 790f, 420f, 60f)

        // Heathens: a tavern crowd, a market crowd and a few strays.
        spawnGroup(Faction.NON_BELIEVER, 7, 690f, 980f, 80f)
        spawnGroup(Faction.NON_BELIEVER, 7, 400f, 540f, 90f)
        spawnGroup(Faction.NON_BELIEVER, 1, 500f, 920f, 20f)
        spawnGroup(Faction.NON_BELIEVER, 1, 240f, 700f, 20f)
        spawnGroup(Faction.NON_BELIEVER, 1, 820f, 720f, 20f)
        spawnGroup(Faction.NON_BELIEVER, 1, 520f, 300f, 20f)
        spawnGroup(Faction.NON_BELIEVER, 1, 880f, 1260f, 20f)
        spawnGroup(Faction.NON_BELIEVER, 1, 120f, 120f, 20f)

        // A line of oil barrels through the market: light one end, watch it run.
        barrels += Barrel(300f, 560f)
        barrels += Barrel(385f, 585f)
        barrels += Barrel(470f, 610f)
        barrels += Barrel(555f, 635f)
        barrels += Barrel(640f, 660f)
        // And two by the tavern, uncomfortably close to the chapel flock.
        barrels += Barrel(560f, 1010f)
        barrels += Barrel(520f, 1100f)
    }

    private fun spawnGroup(faction: Faction, count: Int, cx: Float, cy: Float, spread: Float) {
        for (i in 0 until count) {
            val n = Npc(
                cx + (rng.nextFloat() - 0.5f) * 2f * spread,
                cy + (rng.nextFloat() - 0.5f) * 2f * spread,
                faction
            )
            n.retarget = rng.nextFloat() * 2f
            n.orbClock = rng.nextFloat()
            keepInBounds(n)
            npcs += n
        }
    }

    // ---------------------------------------------------------------- queries

    fun count(faction: Faction): Int {
        var c = 0
        for (n in npcs) if (n.alive && n.faction == faction) c++
        return c
    }

    fun canAfford(power: Power): Boolean = prayer >= power.cost

    /** 1 to 3, only meaningful after a victory. */
    fun stars(): Int {
        var s = 1
        if (believersLost == 0) s++
        if (timeLeft >= 60f) s++
        return s
    }

    private fun nearestBeliever(x: Float, y: Float): Npc? {
        var best: Npc? = null
        var bestD = Float.MAX_VALUE
        for (n in npcs) {
            if (!n.alive || n.faction != Faction.BELIEVER) continue
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
        fx += Fx(FxKind.BOLT, x, y, x, y, 1f, 0.28f)
        shake = max(shake, 0.12f)

        var best: Npc? = null
        var bestD = BOLT_RADIUS * BOLT_RADIUS
        for (n in npcs) {
            if (!n.alive) continue
            val d = dist2(x, y, n.x, n.y)
            if (d < bestD) {
                bestD = d
                best = n
            }
        }
        if (best != null) kill(best)
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
        explode(chargeX, chargeY, METEOR_RADIUS, 85f, 4.5f)
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

    private fun spend(power: Power, x: Float, y: Float): Boolean {
        if (prayer < power.cost) {
            fx += Fx(FxKind.TEXT, x, y, x, y, 1f, 0.9f, "MORE PRAYER", false)
            return false
        }
        prayer -= power.cost
        return true
    }

    // ---------------------------------------------------------------- consequences

    private fun explode(x: Float, y: Float, radius: Float, fireRadius: Float, fireTtl: Float) {
        fx += Fx(FxKind.BLAST, x, y, x, y, radius, 0.45f)
        shake = max(shake, 0.3f)
        val r2 = radius * radius
        for (n in npcs) {
            if (n.alive && dist2(x, y, n.x, n.y) < r2) kill(n)
        }
        for (b in buildings) {
            if (b.standing && distToRect(x, y, b) < radius * 0.6f) b.standing = false
        }
        for (b in barrels) {
            if (b.alive && dist2(x, y, b.x, b.y) < r2) lightFuse(b, 0.18f)
        }
        addFire(Fire(x, y, fireRadius, fireTtl))
    }

    private fun lightFuse(b: Barrel, delay: Float) {
        if (b.alive && b.fuse < 0f) b.fuse = delay
    }

    private fun addFire(f: Fire) {
        if (fires.size < MAX_FIRES) fires += f
    }

    private fun kill(n: Npc) {
        if (!n.alive) return
        n.alive = false
        if (n.faction == Faction.NON_BELIEVER) {
            chain = if (chainClock > 0f) chain + 1 else 1
            chainClock = CHAIN_WINDOW
            bestChain = max(bestChain, chain)
            smitten++
            prayer = min(MAX_PRAYER, prayer + KILL_REWARD + CHAIN_BONUS * min(chain - 1, CHAIN_BONUS_CAP))
            val label = if (chain > 1) "x$chain CHAIN" else "SMITTEN"
            fx += Fx(FxKind.TEXT, n.x, n.y, n.x, n.y, 1f + 0.12f * min(chain, 6), 1.1f, label, true)

            for (o in npcs) {
                if (!o.alive) continue
                val d = dist2(n.x, n.y, o.x, o.y)
                if (o.faction == Faction.BELIEVER) {
                    // A smiting right next to a Believer who came out unharmed: zealotry.
                    if (o.mood != Mood.TERROR && d < ZEAL_RADIUS * ZEAL_RADIUS) {
                        o.mood = Mood.ZEALOT
                        o.moodTime = ZEAL_TIME
                    }
                } else if (d < SPOOK_RADIUS * SPOOK_RADIUS) {
                    // Witnesses run for the nearest human shield.
                    o.mood = Mood.SPOOKED
                    o.moodTime = 3f
                }
            }
        } else {
            believersLost++
            faith = max(0f, faith - BELIEVER_PENALTY)
            chain = 0
            chainClock = 0f
            fx += Fx(FxKind.TEXT, n.x, n.y, n.x, n.y, 1.3f, 1.4f, "DOUBT!", false)
            for (o in npcs) {
                if (!o.alive || o.faction != Faction.BELIEVER) continue
                if (dist2(n.x, n.y, o.x, o.y) < TERROR_RADIUS * TERROR_RADIUS) {
                    o.mood = Mood.TERROR
                    o.moodTime = TERROR_TIME
                    o.fearX = n.x
                    o.fearY = n.y
                }
            }
        }
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

        updateBarrels(dt)
        updateFires(dt)
        updateNpcs(dt)
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
                explode(b.x, b.y, BARREL_RADIUS, 70f, 3.5f)
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
            if (f.ttl <= 0f) fires.removeAt(i)
            i--
        }
        val list = spawned
        if (list != null) for (f in list) addFire(f)
    }

    private fun updateNpcs(dt: Float) {
        val pushDecay = exp(-3.5f * dt)
        for (i in npcs.indices) {
            val n = npcs[i]
            if (!n.alive) continue

            if (n.moodTime > 0f) {
                n.moodTime -= dt
                if (n.moodTime <= 0f) n.mood = Mood.CALM
            }

            val speed: Float
            if (n.faction == Faction.BELIEVER) {
                if (n.mood == Mood.TERROR) {
                    // Run away from whatever just happened. No praying while panicking.
                    val dx = n.x - n.fearX
                    val dy = n.y - n.fearY
                    val d = max(1f, hypot(dx, dy))
                    n.targetX = n.x + dx / d * 120f
                    n.targetY = n.y + dy / d * 120f
                    speed = 150f
                } else {
                    n.retarget -= dt
                    if (n.retarget <= 0f) {
                        n.retarget = 1.5f + rng.nextFloat() * 2.5f
                        n.targetX = n.homeX + (rng.nextFloat() - 0.5f) * 90f
                        n.targetY = n.homeY + (rng.nextFloat() - 0.5f) * 90f
                    }
                    speed = 32f
                    val rate = PRAYER_RATE * (if (n.mood == Mood.ZEALOT) 2f else 1f)
                    prayer += rate * dt
                    n.orbClock += rate * dt
                    if (n.orbClock >= 1.2f) {
                        n.orbClock = 0f
                        fx += Fx(FxKind.ORB, n.x, n.y, n.x, n.y, 1f, 0.9f)
                    }
                }
            } else {
                // They take a moment to notice the sky glowing, then they run.
                if (charging && charge > NOTICE_AT && dist2(n.x, n.y, chargeX, chargeY) < THREAT_RADIUS * THREAT_RADIUS) {
                    n.mood = Mood.SPOOKED
                    n.moodTime = 2.5f
                }
                if (n.mood == Mood.SPOOKED) {
                    // Human shield: hide among the faithful.
                    val shield = nearestBeliever(n.x, n.y)
                    if (shield != null) {
                        n.targetX = shield.x
                        n.targetY = shield.y
                    }
                    speed = 170f
                } else {
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
            val tdx = n.targetX - n.x
            val tdy = n.targetY - n.y
            val td = hypot(tdx, tdy)
            val stopAt = if (n.mood == Mood.SPOOKED) 20f else 5f
            if (td > stopAt) {
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
            heathens == 0 -> {
                outcome = Outcome.VICTORY
                charging = false
            }
            faith <= 0f -> lose("Crisis of Faith")
            flock == 0 -> lose("Nobody left to worship you")
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
