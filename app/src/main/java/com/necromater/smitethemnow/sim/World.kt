package com.necromater.smitethemnow.sim

import kotlin.math.abs
import kotlin.math.ceil
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
class World(
    val level: Int = 1,
    seed: Long = 7L,
    val doctrine: Doctrine = Doctrine.NONE,
    /** Which Holy War map: 0 Two Hills, 1 The Ford, 2 Walled Town. */
    val map: Int = 0,
    val rival: RivalGod = RivalGod.VORN,
    /** The miracles brought to a Holy War. Null means the standard five. */
    deck: List<Power>? = null
) {

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

        /** Inquisitors: walk to a Believer, wind up, strike. A Shield stops the blow. */
        const val STRIKE_TIME = 1.3f
        const val MARTYR_PENALTY = 15f

        /** Sheltering: a spooked heathen runs for a building this close and hides inside. */
        const val REFUGE_RADIUS = 340f
        const val SHELTER_TIME = 5f

        /** Divine Fatigue: each Bolt in quick succession costs this much more, up to a cap. */
        const val FATIGUE_STEP = 4f
        const val FATIGUE_CAP = 5
        const val FATIGUE_RECOVERY = 0.6f

        /** Holy War: the rival god earns Prayer from living heathens and spends it on your flock. */
        const val RIVAL_RATE = 0.2f
        const val RIVAL_BOLT_COST = 20f
        const val RIVAL_BOLT_WARNING = 1.6f
        const val RIVAL_METEOR_COST = 45f
        const val RIVAL_METEOR_WARNING = 2.6f
        const val RIVAL_METEOR_RADIUS = 110f
        const val RIVAL_KILL_PENALTY = 10f
        /** It answers your defences: a Gust to empty a Shield or a Rally, a ward to save a house. */
        const val RIVAL_GUST_COST = 12f
        const val RIVAL_GUST_WARNING = 1.1f
        const val RIVAL_WARD_COST = 15f
        const val RIVAL_WARD_TIME = 5f
        val TAUNTS = listOf("TOO SLOW", "MINE NOW", "YOUR GOD NAPS", "WAS THAT YOURS?", "PRAY HARDER", "HOW CARELESS")

        /** Holy War: settlements grow. */
        const val BUILD_INTERVAL = 22f
        const val HEATHEN_BUILD_INTERVAL = 15f
        /** Settlement houses are sturdier than chapter buildings: a direct Meteor only cracks them. */
        const val HOUSE_HP = 3
        const val BUILD_TIME = 7f
        const val SPAWN_INTERVAL = 16f
        const val MAX_HOUSES = 5
        const val POPULATION_CAP = 22

        /** Rally: the flock gathers at the banner and fights. Fighting is not safe. */
        const val RALLY_TIME = 12f
        const val RALLY_REACH = 170f
        const val RALLY_STRIKE_TIME = 2.2f
        const val RALLY_LOSS_CHANCE = 0.25f
        const val BATTLE_PENALTY = 5f

        // Who or what killed someone.
        private const val BY_PLAYER = 0
        private const val BY_BLADE = 1
        private const val BY_RIVAL = 2
        private const val BY_BATTLE = 3
        private const val BY_ARMAGEDDON = 4

        const val HOLY_WAR = 5

        const val QUAKE_RADIUS = 220f
        const val ANVIL_RADIUS = 46f
        const val BEAM_REACH = 60f
        const val WHISPER_COST = 16f
        const val WHISPER_WARNING = 2.2f
        const val INQUISITOR_COST = 26f

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
            ),
            LevelInfo(
                "The Inquisition",
                "They have sharpened things. Guard your flock.",
                listOf(
                    "Inquisitors cut Believers down. A",
                    "Shield stops the blade. So does a Bolt.",
                    "Frightened heathens hide indoors:",
                    "bring the building down on them.",
                    "Bolts in a hurry cost more. Pace them."
                ),
                4
            ),
            LevelInfo(
                "Day of Reckoning",
                "Everything at once. Do try to keep up.",
                listOf(
                    "A Heretic, rods, and Inquisitors,",
                    "with more Inquisitors on the way.",
                    "Herd them indoors with a Meteor's glow,",
                    "then drop the roof for a long chain.",
                    "Your Prophet is worth a Shield."
                ),
                5
            ),
            LevelInfo(
                "Holy War",
                "A rival god. Two growing flocks. Five minutes.",
                listOf(
                    "Their god smites back. Red marks warn",
                    "you: Shield the spot or Gust them clear.",
                    "Houses breed followers. Raze theirs.",
                    "Rally plants a banner: your flock fights.",
                    "When time is up: Armageddon."
                ),
                0
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

    /** Holy War only. */
    var holyWar = false
        private set

    /** When the five minutes are up, everyone marches out and settles it by hand. */
    var armageddon = false
        private set
    private var armageddonClock = 0f
    // How this rival god plays. Set from [rival] when a Holy War starts.
    var rivalBoltCost = RIVAL_BOLT_COST
        private set
    var rivalMeteorCost = RIVAL_METEOR_COST
        private set
    private var rivalBoltWarning = RIVAL_BOLT_WARNING
    private var rivalWardCost = RIVAL_WARD_COST
    private var rivalWardEvery = 12f
    private var rivalGustEvery = 11f
    private var heathenBuildInterval = HEATHEN_BUILD_INTERVAL
    private var heathenSpawnInterval = SPAWN_INTERVAL
    private var heathenHouseHp = HOUSE_HP
    private var heathenMaxHouses = MAX_HOUSES

    /** Sounds waiting to be played. The app empties this every frame. */
    val cues = ArrayList<Cue>()

    private fun cue(c: Cue) {
        if (cues.size < 24) cues += c
    }

    /** Which power the current press-and-hold is charging. */
    var chargePower = Power.METEOR
        private set
    val chargeRadius: Float get() = if (chargePower == Power.QUAKE) QUAKE_RADIUS else METEOR_RADIUS

    private var wardCooldown = 6f
    private var rivalGustCooldown = 8f
    private var tauntClock = 0f
    var rivalPrayer = 0f
        private set
    val omens = ArrayList<Omen>()
    var banner: Banner? = null
        private set
    private var rivalThink = 4f
    private var buildClockFlock = 14f
    private var buildClockHeathen = 9f

    /** The powers on offer. Rally only exists in Holy War. */
    val powers: List<Power>
        get() = if (holyWar) warPowers else chapterPowers
    private val chapterPowers = listOf(Power.LIGHTNING, Power.METEOR, Power.GUST, Power.SHIELD)
    private val warPowers: List<Power> =
        if (deck != null && deck.isNotEmpty()) deck
        else listOf(Power.LIGHTNING, Power.METEOR, Power.GUST, Power.SHIELD, Power.RALLY)

    /** Divine Fatigue. Whole steps raise the price of the next Bolt. */
    var fatigue = 0f
        private set

    /** Chapters IV and V switch these on. I to III stay as the gentle introduction. */
    var sheltering = false
        private set
    var fatigueOn = false
        private set

    /** Inquisitors still to come: seconds into the round, x, y. */
    private val arrivals = ArrayList<FloatArray>()

    /** Deaths waiting to be reacted to: x, y, and 1 for a heathen or 0 for a Believer. */
    private val witnessed = ArrayList<FloatArray>()

    val chainGoal: Int get() = LEVELS[level.coerceIn(0, LEVELS.size - 1)].chainGoal

    init {
        when (level) {
            0 -> buildHamlet()
            2 -> buildDefiant()
            3 -> buildInquisition()
            4 -> buildReckoning()
            HOLY_WAR -> buildHolyWar()
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

    private fun inquisitor(x: Float, yy: Float) {
        place(Npc(x, y(yy), Faction.NON_BELIEVER, Role.INQUISITOR))
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

    /** Chapter IV: Inquisitors hunt the flock, and frightened heathens hide indoors. */
    private fun buildInquisition() {
        timeLimit = 165f
        sheltering = true
        fatigueOn = true
        building(400f, 1120f, 200f, 130f, holy = true)
        building(120f, 330f, 210f, 130f)
        building(660f, 420f, 210f, 130f)
        building(140f, 760f, 170f, 120f)

        group(Faction.BELIEVER, 7, 500f, 1290f, 75f)
        leader(Faction.BELIEVER, 560f, 1300f)

        group(Faction.NON_BELIEVER, 6, 240f, 520f, 80f)
        group(Faction.NON_BELIEVER, 6, 760f, 620f, 80f)
        group(Faction.NON_BELIEVER, 3, 300f, 940f, 60f)
        inquisitor(480f, 160f)
        inquisitor(900f, 240f)
        inquisitor(80f, 620f)

        // Barrels against the west house: one spark cracks it. Two blasts bring it down.
        barrel(350f, 400f)
        barrel(360f, 470f)
        barrel(660f, 600f)
    }

    /** Chapter V: a Heretic, two rods, Inquisitors now and Inquisitors later. */
    private fun buildReckoning() {
        timeLimit = 180f
        sheltering = true
        fatigueOn = true
        building(70f, 1150f, 200f, 130f, holy = true)
        building(380f, 420f, 220f, 130f)
        building(720f, 760f, 200f, 130f)
        building(100f, 240f, 180f, 120f)
        building(700f, 150f, 170f, 110f)

        group(Faction.BELIEVER, 6, 230f, 1310f, 70f)
        group(Faction.BELIEVER, 4, 800f, 1240f, 55f)
        leader(Faction.BELIEVER, 300f, 1290f)

        rod(480f, 640f)
        rod(820f, 480f)

        group(Faction.NON_BELIEVER, 6, 480f, 620f, 90f)
        group(Faction.NON_BELIEVER, 5, 800f, 560f, 80f)
        group(Faction.NON_BELIEVER, 4, 220f, 460f, 70f)
        group(Faction.NON_BELIEVER, 2, 560f, 960f, 40f)
        leader(Faction.NON_BELIEVER, 600f, 330f)
        inquisitor(900f, 120f)
        inquisitor(60f, 330f)
        arrivals += floatArrayOf(40f, 500f, y(30f))
        arrivals += floatArrayOf(85f, 960f, y(700f))
        arrivals += floatArrayOf(85f, 40f, y(420f))

        // A line that starts outside both rings and ends against the middle house.
        barrel(180f, 700f)
        barrel(260f, 660f)
        barrel(340f, 620f)
        barrel(700f, 940f)
    }

    /** Holy War: two settlements, top and bottom, and a rival god watching the top one. */
    private fun buildHolyWar() {
        timeLimit = 300f
        holyWar = true
        sheltering = true
        fatigueOn = true
        when (rival) {
            RivalGod.VORN -> {
                rivalBoltCost = 16f
                rivalBoltWarning = 1.4f
            }
            RivalGod.BRICK -> {
                rivalBoltCost = 26f
                heathenBuildInterval = 9f
                heathenSpawnInterval = 11f
                heathenHouseHp = 4
                heathenMaxHouses = 6
            }
            RivalGod.GENERAL -> rivalBoltCost = 28f
            RivalGod.SLYBOOTS -> {
                rivalBoltCost = 24f
                rivalGustEvery = 5f
                rivalWardEvery = 6f
                rivalWardCost = 8f
            }
        }

        // Your side is the same on every map.
        buildings += Building(400f, y(1150f), 200f, 130f, true, Faction.BELIEVER)
        buildings += Building(120f, y(1060f), 170f, 115f, false, Faction.BELIEVER)
        group(Faction.BELIEVER, 7, 480f, 1300f, 80f)

        when (map) {
            1 -> {
                // The Ford: a river across the middle with one crossing.
                buildings += Building(0f, y(690f), 380f, 70f, false, null, Building.WATER)
                buildings += Building(620f, y(690f), 380f, 70f, false, null, Building.WATER)
                buildings += Building(380f, y(130f), 210f, 130f, false, Faction.NON_BELIEVER)
                buildings += Building(720f, y(250f), 170f, 115f, false, Faction.NON_BELIEVER)
                buildings += Building(90f, y(300f), 170f, 115f, false, Faction.NON_BELIEVER)
                group(Faction.NON_BELIEVER, 5, 480f, 330f, 80f)
                group(Faction.NON_BELIEVER, 4, 780f, 430f, 70f)
                group(Faction.NON_BELIEVER, 2, 180f, 480f, 50f)
                // Barrels on the crossing: whoever is on it when they go up does not finish crossing.
                barrel(450f, 705f)
                barrel(550f, 735f)
                barrel(300f, 360f)
            }
            2 -> {
                // Walled Town: the heathens sit behind stone with one gate. Stone can be broken.
                buildings += Building(140f, y(80f), 36f, y(480f), false, null, Building.WALL)
                buildings += Building(824f, y(80f), 36f, y(480f), false, null, Building.WALL)
                buildings += Building(140f, y(560f), 270f, 36f, false, null, Building.WALL)
                buildings += Building(590f, y(560f), 270f, 36f, false, null, Building.WALL)
                buildings += Building(400f, y(120f), 200f, 125f, false, Faction.NON_BELIEVER)
                buildings += Building(600f, y(340f), 170f, 115f, false, Faction.NON_BELIEVER)
                buildings += Building(230f, y(330f), 170f, 115f, false, Faction.NON_BELIEVER)
                group(Faction.NON_BELIEVER, 5, 500f, 300f, 70f)
                group(Faction.NON_BELIEVER, 4, 680f, 500f, 50f)
                group(Faction.NON_BELIEVER, 2, 300f, 490f, 40f)
                barrel(500f, 640f)
                barrel(900f, 400f)
                barrel(100f, 300f)
            }
            else -> {
                // Two Hills: open ground and a barrel line down the middle.
                buildings += Building(380f, y(130f), 210f, 130f, false, Faction.NON_BELIEVER)
                buildings += Building(720f, y(250f), 170f, 115f, false, Faction.NON_BELIEVER)
                buildings += Building(90f, y(300f), 170f, 115f, false, Faction.NON_BELIEVER)
                group(Faction.NON_BELIEVER, 5, 480f, 330f, 80f)
                group(Faction.NON_BELIEVER, 4, 780f, 430f, 70f)
                group(Faction.NON_BELIEVER, 2, 180f, 480f, 50f)
                barrel(420f, 640f)
                barrel(500f, 680f)
                barrel(580f, 720f)
                barrel(200f, 520f)
            }
        }
        leader(Faction.BELIEVER, 540f, 1310f)
        for (b in buildings) {
            if (b.owner == null) continue
            b.hp = if (b.owner == Faction.NON_BELIEVER) heathenHouseHp else HOUSE_HP
            b.spawnClock = spawnEvery(b.owner) * (0.5f + rng.nextFloat() * 0.5f)
        }
    }

    // ---------------------------------------------------------------- queries

    fun count(faction: Faction): Int {
        var c = 0
        for (n in npcs) if (n.alive && n.faction == faction) c++
        return c
    }

    /** What a power costs right now: the doctrine sets the base, fatigue adds to the Bolt. */
    fun costOf(power: Power): Float {
        var base = power.cost
        when (doctrine) {
            Doctrine.SHEPHERD -> {
                if (power == Power.SHIELD) base = 9f
                if (power == Power.METEOR) base = 45f
            }
            Doctrine.WRATHFUL -> {
                if (power == Power.METEOR) base = 25f
                if (power == Power.LIGHTNING) base = 10f
            }
            Doctrine.TRICKSTER -> if (power == Power.GUST) base = 4f
            Doctrine.NONE -> {}
        }
        if (power != Power.LIGHTNING || !fatigueOn) return base
        return base + FATIGUE_STEP * min(FATIGUE_CAP, ceil(fatigue).toInt())
    }

    fun canAfford(power: Power): Boolean = prayer >= costOf(power)

    /** How many heathens are hiding in this building. */
    fun occupants(b: Building): Int {
        var c = 0
        for (n in npcs) if (n.alive && n.inside === b) c++
        return c
    }

    private fun doorX(b: Building): Float = b.x + b.w / 2f
    private fun doorY(b: Building): Float = b.y + b.h + NPC_RADIUS + 6f

    private fun refugeFor(n: Npc): Building? {
        var best: Building? = null
        var bestD = REFUGE_RADIUS * REFUGE_RADIUS
        for (b in buildings) {
            if (!b.standing || b.holy || b.kind != Building.HOUSE || b.owner == Faction.BELIEVER || b.built < 1f) continue
            val d = dist2(n.x, n.y, doorX(b), doorY(b))
            if (d < bestD) {
                bestD = d
                best = b
            }
        }
        return best
    }

    val flockKept: Boolean get() = believersLost == 0 && defected == 0
    val chainMade: Boolean get() = bestChain >= chainGoal

    // The second and third stars, and how the result card describes them.
    val goal2Met: Boolean get() = if (holyWar) leaderOf(Faction.BELIEVER) != null else flockKept
    val goal3Met: Boolean get() = if (holyWar) !armageddon else chainMade
    val goal2Text: String get() = if (holyWar) "Your Prophet lives" else "Not one Believer lost"
    val goal3Text: String get() = if (holyWar) "Won before Armageddon" else "A chain of $chainGoal or more"

    /** 1 to 3, only meaningful after a victory. */
    fun stars(): Int {
        var s = 1
        if (goal2Met) s++
        if (goal3Met) s++
        return s
    }

    fun houses(faction: Faction): Int {
        var c = 0
        for (b in buildings) if (b.standing && b.owner == faction) c++
        return c
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
            if (!n.alive || n.faction != faction || n === skip || n.inside != null) continue
            if (followersOnly && !n.isFollower) continue
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
        if (fatigueOn) fatigue = min(FATIGUE_CAP.toFloat(), fatigue + 1f)
        cue(Cue.ZAP)
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
            if (!n.alive || n.inside != null || isShielded(n)) continue
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
    fun beginCharge(x: Float, y: Float, power: Power = Power.METEOR): Boolean {
        if (outcome != Outcome.PLAYING) return false
        if (!canAfford(power)) {
            fx += Fx(FxKind.TEXT, x, y, x, y, 1f, 0.9f, "MORE PRAYER", false)
            cue(Cue.DENY)
            return false
        }
        chargePower = power
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
        if (!spend(chargePower, chargeX, chargeY)) return
        if (chargePower == Power.QUAKE) {
            quake(chargeX, chargeY)
        } else {
            explode(chargeX, chargeY, METEOR_RADIUS, 85f, 4.5f, true)
            shake = max(shake, 0.5f)
        }
    }

    /** Earthquake: kills nobody, cracks every building in reach, yours included, and throws people about. */
    private fun quake(x: Float, y: Float) {
        fx += Fx(FxKind.BLAST, x, y, x, y, QUAKE_RADIUS, 0.7f)
        shake = max(shake, 0.9f)
        cue(Cue.CRUMBLE)
        for (b in buildings) {
            if (b.standing && distToRect(x, y, b) < QUAKE_RADIUS) damage(b, 1)
        }
        for (r in rods) {
            if (r.alive && dist2(x, y, r.x, r.y) < QUAKE_RADIUS * QUAKE_RADIUS) fellRod(r, "ROD FELLED")
        }
        for (n in npcs) {
            if (!n.alive || n.inside != null || dist2(x, y, n.x, n.y) > QUAKE_RADIUS * QUAKE_RADIUS) continue
            n.pushX += (rng.nextFloat() - 0.5f) * 700f
            n.pushY += (rng.nextFloat() - 0.5f) * 700f
            if (n.faction == Faction.NON_BELIEVER && n.isFollower) {
                n.mood = Mood.SPOOKED
                n.moodTime = 3f
            }
        }
    }

    /** Divine Anvil. Tap: small, instant, heavy. Flattens whoever is under it and ignores rods and wards on people. */
    fun castAnvil(x: Float, y: Float): Boolean {
        if (outcome != Outcome.PLAYING) return false
        if (!spend(Power.ANVIL, x, y)) return false
        fx += Fx(FxKind.BLAST, x, y, x, y, ANVIL_RADIUS * 1.3f, 0.35f)
        shake = max(shake, 0.4f)
        cue(Cue.BOOM)
        for (n in npcs) {
            if (n.alive && n.inside == null && dist2(x, y, n.x, n.y) < ANVIL_RADIUS * ANVIL_RADIUS) kill(n)
        }
        for (b in buildings) {
            if (b.standing && distToRect(x, y, b) < 12f) damage(b, 2)
        }
        for (b in barrels) {
            if (b.alive && dist2(x, y, b.x, b.y) < ANVIL_RADIUS * ANVIL_RADIUS) lightFuse(b, 0.05f)
        }
        for (r in rods) {
            if (r.alive && dist2(x, y, r.x, r.y) < ANVIL_RADIUS * ANVIL_RADIUS) fellRod(r, "ROD FELLED")
        }
        return true
    }

    /** Conversion Beam. Tap a heathen follower: they are yours now. Costs nothing if nobody is there. */
    fun castBeam(x: Float, y: Float): Boolean {
        if (outcome != Outcome.PLAYING) return false
        var best: Npc? = null
        var bestD = BEAM_REACH * BEAM_REACH
        for (n in npcs) {
            if (!n.alive || n.inside != null || n.faction != Faction.NON_BELIEVER || !n.isFollower) continue
            val d = dist2(x, y, n.x, n.y)
            if (d < bestD) {
                bestD = d
                best = n
            }
        }
        if (best == null) {
            fx += Fx(FxKind.TEXT, x, y, x, y, 1f, 0.9f, "NO ONE TO SAVE", false)
            cue(Cue.DENY)
            return false
        }
        if (!spend(Power.BEAM, x, y)) return false
        fx += Fx(FxKind.BOLT, best.x, best.y, best.x, best.y, 1f, 0.35f)
        convert(best, "CONVERTED")
        return true
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
        cue(Cue.WHOOSH)

        for (n in npcs) {
            if (!n.alive || n.inside != null) continue
            if (distToSegment(n.x, n.y, x1, y1, x2, y2) < GUST_WIDTH) {
                val force = if (doctrine == Doctrine.TRICKSTER) GUST_FORCE * 1.4f else GUST_FORCE
                n.pushX += ux * force
                n.pushY += uy * force
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
        cue(Cue.SHIELD)
        return true
    }

    /** Tap. Plants a banner. The flock gathers there and fights heathens who come near. */
    fun castRally(x: Float, y: Float): Boolean {
        if (outcome != Outcome.PLAYING || !holyWar) return false
        if (!spend(Power.RALLY, x, y)) return false
        banner = Banner(x, y, RALLY_TIME)
        cue(Cue.HORN)
        fx += Fx(FxKind.TEXT, x, y, x, y, 1.1f, 1.1f, "TO THE BANNER", true)
        return true
    }

    private fun spend(power: Power, x: Float, y: Float): Boolean {
        val cost = costOf(power)
        if (prayer < cost) {
            cue(Cue.DENY)
            fx += Fx(FxKind.TEXT, x, y, x, y, 1f, 0.9f, "MORE PRAYER", false)
            return false
        }
        prayer -= cost
        return true
    }

    // ---------------------------------------------------------------- consequences

    /** [heavy] is a Meteor: a direct hit levels a sound building in one go. */
    private fun explode(x: Float, y: Float, radius: Float, fireRadius: Float, fireTtl: Float, heavy: Boolean) {
        fx += Fx(FxKind.BLAST, x, y, x, y, radius, 0.45f)
        cue(Cue.BOOM)
        shake = max(shake, 0.3f)
        val r2 = radius * radius
        for (n in npcs) {
            // Anyone indoors is safe from the blast itself. The roof is another matter.
            if (n.alive && n.inside == null && dist2(x, y, n.x, n.y) < r2) kill(n)
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

    private fun damage(b: Building, amount: Int, rival: Boolean = false) {
        if (!b.standing || b.kind == Building.WATER) return
        if (b.ward > 0f && !rival) return
        b.hp -= amount
        b.scorch = 0f
        if (b.hp > 0) return

        // Down it comes, on whoever was leaning against it.
        b.standing = false
        cue(Cue.CRUMBLE)
        val cx = b.x + b.w / 2f
        val cy = b.y + b.h / 2f
        fx += Fx(FxKind.BLAST, cx, cy, cx, cy, max(b.w, b.h) * 0.75f, 0.6f)
        shake = max(shake, 0.4f)
        var crushed = 0
        for (n in npcs) {
            if (!n.alive) continue
            if (n.inside != null && n.inside !== b) continue
            if (n.inside == null && distToRect(n.x, n.y, b) > CRUSH_MARGIN) continue
            n.inside = null
            val wasHeathen = n.faction == Faction.NON_BELIEVER
            if (rival && wasHeathen) continue
            kill(n, if (rival) BY_RIVAL else BY_PLAYER)
            if (wasHeathen && !n.alive) crushed++
        }
        if (b.owner == Faction.BELIEVER && !b.holy) {
            if (!rival) loseFaith(10f)
            fx += Fx(FxKind.TEXT, cx, cy, cx, cy, 1.2f, 1.5f, if (rival) "HOUSE LOST" else "YOUR OWN HOUSE", false)
        } else if (b.holy) {
            loseFaith((if (rival) 10f else CHAPEL_PENALTY))
            fx += Fx(FxKind.TEXT, cx, cy, cx, cy, 1.3f, 1.5f, if (rival) "CHAPEL LOST" else "SACRILEGE", false)
            for (n in npcs) {
                if (n.alive && n.faction == Faction.BELIEVER && dist2(cx, cy, n.x, n.y) < 300f * 300f) terrify(n, cx, cy)
            }
        } else if (crushed > 0) {
            prayer = min(MAX_PRAYER, prayer + 5f)
            fx += Fx(FxKind.TEXT, cx, cy - 30f, cx, cy - 30f, 1.2f, 1.5f, "POETIC JUSTICE", true)
        }
    }

    /** Every Faith loss goes through here so a doctrine can scale it. */
    private fun loseFaith(amount: Float) {
        faith = max(0f, faith - amount * (if (doctrine == Doctrine.WRATHFUL) 1.5f else 1f))
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

    /** [cause] decides what a Believer's death costs: your own doing costs most. */
    private fun kill(n: Npc, cause: Int = BY_PLAYER) {
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
        n.inside = null
        if (n.faction == Faction.NON_BELIEVER) {
            chain = if (chainClock > 0f) chain + 1 else 1
            chainClock = CHAIN_WINDOW
            bestChain = max(bestChain, chain)
            smitten++
            cue(Cue.SMITE)
            val chainPay = if (doctrine == Doctrine.TRICKSTER) 2f * min(chain - 1, 10) else CHAIN_BONUS * min(chain - 1, CHAIN_BONUS_CAP)
            prayer = min(MAX_PRAYER, prayer + KILL_REWARD + chainPay)
            if (n.isLeader) {
                prayer = min(MAX_PRAYER, prayer + 10f)
                fx += Fx(FxKind.TEXT, n.x, n.y, n.x, n.y, 1.4f, 1.5f, "HERETIC SMITTEN", true)
                for (o in npcs) {
                    if (o.alive && o.faction == Faction.NON_BELIEVER && o.isFollower) {
                        o.mood = Mood.SPOOKED
                        o.moodTime = 4f
                    }
                }
            } else {
                val label = if (chain > 1) "x$chain CHAIN" else if (n.isInquisitor) "INQUISITOR DOWN" else "SMITTEN"
                fx += Fx(FxKind.TEXT, n.x, n.y, n.x, n.y, 1f + 0.12f * min(chain, 6), 1.1f, label, true)
            }
            witnessed += floatArrayOf(n.x, n.y, 1f)
        } else {
            believersLost++
            cue(Cue.LOSS)
            chain = 0
            chainClock = 0f
            if (cause == BY_ARMAGEDDON) {
                // The last battle costs lives, not Faith.
                fx += Fx(FxKind.TEXT, n.x, n.y, n.x, n.y, 1f, 1.1f, "FELL", false)
            } else if (n.isLeader) {
                loseFaith((if (cause == BY_RIVAL) 30f else PROPHET_PENALTY))
                fx += Fx(FxKind.TEXT, n.x, n.y, n.x, n.y, 1.4f, 1.6f, "YOUR PROPHET!", false)
                for (o in npcs) {
                    if (o.alive && o.faction == Faction.BELIEVER) terrify(o, n.x, n.y)
                }
            } else if (cause == BY_BLADE) {
                loseFaith(MARTYR_PENALTY)
                fx += Fx(FxKind.TEXT, n.x, n.y, n.x, n.y, 1.3f, 1.4f, "MARTYRED", false)
            } else if (cause == BY_RIVAL) {
                loseFaith(RIVAL_KILL_PENALTY)
                fx += Fx(FxKind.TEXT, n.x, n.y, n.x, n.y, 1.3f, 1.4f, "STRUCK DOWN", false)
            } else if (cause == BY_BATTLE) {
                loseFaith(BATTLE_PENALTY)
                fx += Fx(FxKind.TEXT, n.x, n.y, n.x, n.y, 1.1f, 1.3f, "FELL IN BATTLE", false)
            } else {
                loseFaith(BELIEVER_PENALTY)
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
        if (armageddon) witnessed.clear()
        if (witnessed.isEmpty()) return
        val seeTheLight = if (leaderOf(Faction.BELIEVER) != null) WITNESS_CHANCE_WITH_PROPHET else WITNESS_CHANCE
        val walkAway = if (leaderOf(Faction.NON_BELIEVER) != null) DEFECT_CHANCE_WITH_HERETIC else DEFECT_CHANCE
        for (e in witnessed) {
            val ex = e[0]
            val ey = e[1]
            val heathenDied = e[2] > 0.5f
            for (o in npcs) {
                if (!o.alive || o.inside != null) continue
                val d = dist2(ex, ey, o.x, o.y)
                if (heathenDied) {
                    if (o.faction == Faction.BELIEVER) {
                        // A smiting right next to a Believer who came out unharmed: zealotry.
                        if (o.mood != Mood.TERROR && d < ZEAL_RADIUS * ZEAL_RADIUS) {
                            o.mood = Mood.ZEALOT
                            o.moodTime = ZEAL_TIME
                        }
                    } else if (o.isFollower) {
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
        cue(Cue.CONVERT)
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
        cue(Cue.LOSS)
        n.faction = Faction.NON_BELIEVER
        n.mood = Mood.CALM
        n.moodTime = 0f
        n.sway = 0f
        n.retarget = 0f
        defected++
        loseFaith(faithCost)
        fx += Fx(FxKind.TEXT, n.x, n.y, n.x, n.y, 1.05f, 1.3f, label, false)
    }

    // ---------------------------------------------------------------- tick

    fun update(dtIn: Float) {
        val dt = min(max(dtIn, 0f), 0.05f)
        updateFx(dt)
        if (shake > 0f) shake = max(0f, shake - dt)
        if (outcome != Outcome.PLAYING) return

        timeLeft = max(0f, timeLeft - dt)
        if (holyWar && timeLeft <= 0f && !armageddon) startArmageddon()
        if (armageddon) armageddonClock += dt
        if (chainClock > 0f) {
            chainClock -= dt
            if (chainClock <= 0f) chain = 0
        }
        if (charging) charge = min(1f, charge + dt / METEOR_CHARGE_TIME)
        if (fatigue > 0f) fatigue = max(0f, fatigue - FATIGUE_RECOVERY * dt)

        var ai = arrivals.size - 1
        while (ai >= 0) {
            val a = arrivals[ai]
            if (timeLimit - timeLeft >= a[0]) {
                arrivals.removeAt(ai)
                val n = Npc(a[1], a[2], Faction.NON_BELIEVER, Role.INQUISITOR)
                keepInBounds(n)
                npcs += n
                val tx = n.x.coerceIn(260f, W - 260f)
                val ty = n.y.coerceIn(80f, H - 80f)
                fx += Fx(FxKind.TEXT, tx, ty, tx, ty, 1.2f, 2f, "MORE INQUISITORS", false)
            }
            ai--
        }

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
        if (holyWar) {
            updateSettlements(dt)
            updateRival(dt)
            val ban = banner
            if (ban != null) {
                ban.ttl -= dt
                if (ban.ttl <= 0f) banner = null
            }
        }
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

    /** Time is up. Everyone comes out, the houses stop mattering, and both flocks charge. */
    private fun startArmageddon() {
        armageddon = true
        cue(Cue.HORN)
        sheltering = false
        banner = null
        for (n in npcs) {
            if (!n.alive) continue
            val den = n.inside
            if (den != null) {
                n.inside = null
                n.x = doorX(den) + (rng.nextFloat() - 0.5f) * 40f
                n.y = doorY(den)
            }
            n.mood = Mood.CALM
            n.moodTime = 0f
            n.task = null
            n.strike = 0f
        }
        fx += Fx(FxKind.TEXT, W / 2f, H * 0.45f, W / 2f, H * 0.45f, 2.2f, 3.5f, "ARMAGEDDON", true)
        shake = max(shake, 0.6f)
    }

    /**
     * Armageddon: head for the nearest enemy and trade blows. Zealots and leaders hit harder.
     * Nobody prays, so the Prayer each god saved is all there is left to spend.
     */
    private fun brawl(n: Npc, dt: Float): Boolean {
        val enemy = if (n.faction == Faction.BELIEVER) Faction.NON_BELIEVER else Faction.BELIEVER
        val foe = nearest(n.x, n.y, enemy, false) ?: return false
        n.targetX = foe.x
        n.targetY = foe.y
        if (dist2(n.x, n.y, foe.x, foe.y) < 34f * 34f) {
            n.strike += dt / 1.4f
            if (n.strike >= 1f) {
                n.strike = 0f
                val odds = if (n.mood == Mood.ZEALOT) 0.85f else if (n.isLeader) 0.8f else 0.6f
                if (rng.nextFloat() < odds) {
                    if (foe.hp > 1) foe.hp-- else kill(foe, BY_ARMAGEDDON)
                }
            }
        } else {
            n.strike = max(0f, n.strike - dt)
        }
        return true
    }

    /** Holy War: finished houses send out followers, and each side keeps building more. */
    private fun updateSettlements(dt: Float) {
        for (b in buildings) if (b.ward > 0f) b.ward -= dt
        if (armageddon) return
        for (i in buildings.indices) {
            val b = buildings[i]
            val owner = b.owner
            if (!b.standing || owner == null) continue
            if (b.built < 1f) {
                b.built += dt / BUILD_TIME
                if (b.built >= 1f) {
                    b.built = 1f
                    b.hp = if (owner == Faction.NON_BELIEVER) heathenHouseHp else HOUSE_HP
                    b.spawnClock = spawnEvery(owner)
                }
                continue
            }
            b.spawnClock -= dt
            if (b.spawnClock <= 0f) {
                b.spawnClock = spawnEvery(owner)
                if (count(owner) < POPULATION_CAP) {
                    val n = Npc(doorX(b), doorY(b), owner)
                    n.retarget = 0f
                    if (owner == Faction.BELIEVER) n.grace = 1f
                    npcs += n
                }
            }
        }
        buildClockFlock -= dt
        if (buildClockFlock <= 0f) {
            buildClockFlock = BUILD_INTERVAL
            foundHouse(Faction.BELIEVER)
        }
        buildClockHeathen -= dt
        if (buildClockHeathen <= 0f) {
            buildClockHeathen = heathenBuildInterval
            foundHouse(Faction.NON_BELIEVER)
        }
    }

    private fun spawnEvery(owner: Faction?): Float =
        if (owner == Faction.NON_BELIEVER) heathenSpawnInterval else SPAWN_INTERVAL

    /** Starts a new house near one the side already has, if there is room for it. */
    private fun foundHouse(owner: Faction) {
        if (houses(owner) >= (if (owner == Faction.NON_BELIEVER) heathenMaxHouses else MAX_HOUSES) || count(owner) < 3) return
        val own = ArrayList<Building>()
        for (b in buildings) if (b.standing && b.owner == owner) own += b
        if (own.isEmpty()) return
        val w = 160f
        val h = 110f
        for (attempt in 0 until 14) {
            val from = own[rng.nextInt(own.size)]
            val x = from.x + (rng.nextFloat() - 0.5f) * 620f
            val yy = from.y + (rng.nextFloat() - 0.5f) * 520f
            if (x < 50f || x + w > W - 50f || yy < 60f || yy + h > H - 80f) continue
            // Each side keeps to its own half of the page.
            if (owner == Faction.BELIEVER && yy < H * 0.55f) continue
            if (owner == Faction.NON_BELIEVER && yy + h > H * 0.45f) continue
            var clear = true
            for (o in buildings) {
                if (!o.standing) continue
                if (x < o.x + o.w + 70f && x + w > o.x - 70f && yy < o.y + o.h + 80f && yy + h > o.y - 70f) clear = false
            }
            if (!clear) continue
            val b = Building(x, yy, w, h, false, owner)
            b.built = 0f
            b.hp = 1
            buildings += b
            // Nobody gets walled in by the new foundations.
            for (n in npcs) if (n.alive && n.inside == null) keepInBounds(n)
            return
        }
    }

    /** Holy War: the other god. Earns Prayer from living heathens, warns, then strikes. */
    private fun updateRival(dt: Float) {
        var souls = 0
        for (n in npcs) if (n.alive && n.faction == Faction.NON_BELIEVER) souls++
        if (!armageddon) rivalPrayer = min(MAX_PRAYER, rivalPrayer + RIVAL_RATE * souls * dt)
        if (tauntClock > 0f) tauntClock -= dt
        wardCooldown -= dt
        rivalGustCooldown -= dt

        // You are charging a Meteor over one of its houses: it may throw a ward over the roof.
        if (charging && charge > NOTICE_AT && wardCooldown <= 0f && rivalPrayer >= rivalWardCost) {
            for (b in buildings) {
                if (!b.standing || b.owner != Faction.NON_BELIEVER || b.built < 1f || b.ward > 0f) continue
                if (distToRect(chargeX, chargeY, b) < METEOR_RADIUS * 0.6f) {
                    b.ward = RIVAL_WARD_TIME
                    cue(Cue.SHIELD)
                    rivalPrayer -= rivalWardCost
                    wardCooldown = rivalWardEvery
                    val cx = b.x + b.w / 2f
                    fx += Fx(FxKind.TEXT, cx, b.y, cx, b.y, 1.1f, 1.2f, "WARDED", false)
                    break
                }
            }
        }

        var i = omens.size - 1
        while (i >= 0) {
            val o = omens[i]
            val t = o.target
            if (t != null) {
                if (!t.alive || t.faction != Faction.BELIEVER) {
                    omens.removeAt(i)
                    i--
                    continue
                }
                o.x = t.x
                o.y = t.y
            }
            o.ttl -= dt
            if (o.ttl <= 0f) {
                omens.removeAt(i)
                val lostBefore = believersLost
                if (o.meteor) {
                    rivalBlast(o.x, o.y)
                } else if (o.gust) {
                    // Blows your people out of wherever you put them.
                    cue(Cue.WHOOSH)
                    fx += Fx(FxKind.GUST, o.x - o.dx * 110f, o.y - o.dy * 110f, o.x + o.dx * 110f, o.y + o.dy * 110f, GUST_WIDTH, 0.55f)
                    for (n in npcs) {
                        if (!n.alive || n.inside != null || n.faction != Faction.BELIEVER) continue
                        if (dist2(o.x, o.y, n.x, n.y) < 130f * 130f) {
                            n.pushX += o.dx * 560f
                            n.pushY += o.dy * 560f
                        }
                    }
                } else if (o.whisper) {
                    if (t != null) {
                        if (isShielded(t)) {
                            fx += Fx(FxKind.TEXT, t.x, t.y, t.x, t.y, 0.9f, 0.9f, "UNMOVED", true)
                        } else {
                            defect(t, "LURED AWAY", 5f)
                        }
                    }
                } else if (t != null) {
                    fx += Fx(FxKind.BOLT, t.x, t.y, t.x, t.y, 1f, 0.28f, "", false)
                    shake = max(shake, 0.12f)
                    cue(Cue.ZAP)
                    kill(t, BY_RIVAL)
                }
                if (believersLost > lostBefore && tauntClock <= 0f) {
                    tauntClock = 7f
                    val line = TAUNTS[rng.nextInt(TAUNTS.size)]
                    fx += Fx(FxKind.TEXT, W / 2f, 150f, W / 2f, 150f, 1.25f, 2.2f, line, false)
                }
            }
            i--
        }

        rivalThink -= dt
        if (rivalThink > 0f) return
        rivalThink = 0.8f + rng.nextFloat() * 0.9f
        if (omens.size >= 3 || souls == 0) return

        if (rivalGustCooldown <= 0f && rivalPrayer >= RIVAL_GUST_COST) {
            // A marked Believer you have shielded: blow them out from under it, then strike.
            for (o in omens) {
                val t = o.target ?: continue
                if (o.kind != Omen.BOLT || !isShielded(t)) continue
                var sx = t.x
                var sy = t.y + 1f
                for (sh in shields) if (dist2(t.x, t.y, sh.x, sh.y) < sh.radius * sh.radius) {
                    sx = sh.x
                    sy = sh.y
                }
                var dx = t.x - sx
                var dy = t.y - sy
                val d = hypot(dx, dy)
                if (d < 1f) {
                    dx = 1f
                    dy = 0f
                } else {
                    dx /= d
                    dy /= d
                }
                rivalPrayer -= RIVAL_GUST_COST
                rivalGustCooldown = rivalGustEvery
                o.ttl += RIVAL_GUST_WARNING + 0.5f
                cue(Cue.WARN)
                omens += Omen(Omen.GUST, t.x, t.y, null, RIVAL_GUST_WARNING, RIVAL_GUST_WARNING, dx, dy)
                return
            }
            // A flock gathered at a banner: send it home.
            val ban = banner
            if (ban != null) {
                var gathered = 0
                for (n in npcs) {
                    if (n.alive && n.faction == Faction.BELIEVER && dist2(ban.x, ban.y, n.x, n.y) < 130f * 130f) gathered++
                }
                if (gathered >= 3) {
                    rivalPrayer -= RIVAL_GUST_COST
                    rivalGustCooldown = rivalGustEvery
                    cue(Cue.WARN)
                    omens += Omen(Omen.GUST, ban.x, ban.y, null, RIVAL_GUST_WARNING, RIVAL_GUST_WARNING, 0f, 1f)
                    return
                }
            }
        }

        if (omens.size >= 2) return
        if (rivalPrayer >= rivalMeteorCost) {
            // Look for a knot of at least four Believers.
            var bestN: Npc? = null
            var bestCount = 3
            for (n in npcs) {
                if (!n.alive || n.faction != Faction.BELIEVER) continue
                var c = 0
                for (o in npcs) {
                    if (o.alive && o.faction == Faction.BELIEVER && dist2(n.x, n.y, o.x, o.y) < 95f * 95f) c++
                }
                if (c > bestCount) {
                    bestCount = c
                    bestN = n
                }
            }
            if (bestN != null) {
                rivalPrayer -= rivalMeteorCost
                cue(Cue.WARN)
                omens += Omen(Omen.METEOR, bestN.x, bestN.y, null, RIVAL_METEOR_WARNING, RIVAL_METEOR_WARNING)
                return
            }
        }
        if (rival == RivalGod.GENERAL && rivalPrayer >= INQUISITOR_COST) {
            // The General's answer to everything: send a man with a sword.
            var out = 0
            for (n in npcs) if (n.alive && n.isInquisitor) out++
            var from: Building? = null
            for (b in buildings) if (b.standing && b.owner == Faction.NON_BELIEVER && b.built >= 1f) from = b
            if (out < 3 && from != null) {
                rivalPrayer -= INQUISITOR_COST
                val n = Npc(doorX(from), doorY(from), Faction.NON_BELIEVER, Role.INQUISITOR)
                npcs += n
                cue(Cue.WARN)
                fx += Fx(FxKind.TEXT, n.x.coerceIn(220f, W - 220f), n.y, n.x, n.y, 1.1f, 1.6f, "INQUISITOR SENT", false)
                return
            }
        }
        if (rival == RivalGod.SLYBOOTS && rivalPrayer >= WHISPER_COST && rng.nextFloat() < 0.5f) {
            // Slyboots would sooner steal a follower than kill one.
            val pool = ArrayList<Npc>()
            for (n in npcs) {
                if (!n.alive || n.faction != Faction.BELIEVER || n.isLeader || n.mood == Mood.ZEALOT || isShielded(n)) continue
                var marked = false
                for (o in omens) if (o.target === n) marked = true
                if (!marked) pool += n
            }
            if (pool.isNotEmpty()) {
                val victim = pool[rng.nextInt(pool.size)]
                rivalPrayer -= WHISPER_COST
                cue(Cue.WARN)
                omens += Omen(Omen.WHISPER, victim.x, victim.y, victim, WHISPER_WARNING, WHISPER_WARNING)
                return
            }
        }
        if (rivalPrayer >= rivalBoltCost && rng.nextFloat() > 0.25f) {
            val pool = ArrayList<Npc>()
            for (n in npcs) {
                if (!n.alive || n.faction != Faction.BELIEVER || n.isLeader || isShielded(n)) continue
                var marked = false
                for (o in omens) if (o.target === n) marked = true
                if (!marked) pool += n
            }
            if (pool.isNotEmpty()) {
                val victim = pool[rng.nextInt(pool.size)]
                rivalPrayer -= rivalBoltCost
                cue(Cue.WARN)
                omens += Omen(Omen.BOLT, victim.x, victim.y, victim, rivalBoltWarning, rivalBoltWarning)
            }
        }
    }

    /** The rival's Meteor: spares its own people, leaves no fire, wrecks your buildings. */
    private fun rivalBlast(x: Float, y: Float) {
        val radius = RIVAL_METEOR_RADIUS
        fx += Fx(FxKind.BLAST, x, y, x, y, radius, 0.5f, "", false)
        cue(Cue.BOOM)
        shake = max(shake, 0.5f)
        for (n in npcs) {
            if (n.alive && n.inside == null && n.faction == Faction.BELIEVER && dist2(x, y, n.x, n.y) < radius * radius) {
                kill(n, BY_RIVAL)
            }
        }
        for (b in buildings) {
            if (!b.standing) continue
            val d = distToRect(x, y, b)
            if (d < radius * 0.6f) damage(b, if (d < 25f) 2 else 1, true)
        }
        for (b in barrels) {
            if (b.alive && dist2(x, y, b.x, b.y) < radius * radius) lightFuse(b, 0.18f)
        }
    }

    /** Each leader works on the nearest follower of the other side within reach. */
    private fun updatePreaching(dt: Float) {
        for (n in npcs) n.preached = false

        val prophet = leaderOf(Faction.BELIEVER)
        if (prophet != null && prophet.mood != Mood.TERROR) {
            val t = nearest(prophet.x, prophet.y, Faction.NON_BELIEVER, true)
            if (t != null && dist2(prophet.x, prophet.y, t.x, t.y) < PROPHET_AURA * PROPHET_AURA) {
                t.preached = true
                t.sway += dt / (if (doctrine == Doctrine.SHEPHERD) 3f else PROPHET_TIME)
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
                    if (!n.alive || n.faction != Faction.NON_BELIEVER || !n.isFollower || n.task != null || n.inside != null) continue
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
            if (b.mood != Mood.SPOOKED && b.inside == null && dist2(r.x, r.y, b.x, b.y) < 34f * 34f) {
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

            val den = n.inside
            if (den != null) {
                // Hiding indoors. A glowing sky nearby keeps them in a little longer.
                if (charging && charge > NOTICE_AT &&
                    dist2(den.x + den.w / 2f, den.y + den.h / 2f, chargeX, chargeY) < THREAT_RADIUS * THREAT_RADIUS
                ) {
                    n.shelterClock = max(n.shelterClock, 2f)
                }
                n.shelterClock -= dt
                if (n.shelterClock <= 0f) {
                    n.inside = null
                    n.x = doorX(den) + (rng.nextFloat() - 0.5f) * 30f
                    n.y = doorY(den)
                    n.mood = Mood.CALM
                    n.moodTime = 0f
                    n.retarget = 0f
                }
                continue
            }

            val speed: Float
            var stopAt = 5f
            if (armageddon && brawl(n, dt)) {
                stopAt = 24f
                speed = 95f
            } else if (n.faction == Faction.BELIEVER) {
                if (n.mood == Mood.TERROR) {
                    // Run away from whatever just happened. No praying while panicking.
                    val dx = n.x - n.fearX
                    val dy = n.y - n.fearY
                    val d = max(1f, hypot(dx, dy))
                    n.targetX = n.x + dx / d * 120f
                    n.targetY = n.y + dy / d * 120f
                    speed = if (n.isLeader) 110f else 150f
                } else if (banner != null && crusade(n, dt)) {
                    // Fighting at the banner. No time to pray.
                    stopAt = 24f
                    speed = 85f
                } else {
                    val ban = banner
                    val anchorX = ban?.x ?: n.homeX
                    val anchorY = ban?.y ?: n.homeY
                    n.retarget -= dt
                    if (n.retarget <= 0f) {
                        val roam = if (n.isLeader) 50f else 90f
                        n.retarget = 1.5f + rng.nextFloat() * 2.5f
                        n.targetX = anchorX + (rng.nextFloat() - 0.5f) * roam
                        n.targetY = anchorY + (rng.nextFloat() - 0.5f) * roam
                    } else if (ban != null && dist2(n.targetX, n.targetY, ban.x, ban.y) > 80f * 80f) {
                        n.retarget = 0f
                    }
                    // Hurry when far from where they belong, then settle.
                    speed = if (dist2(n.x, n.y, anchorX, anchorY) > 150f * 150f) 90f else 32f
                    val rate = PRAYER_RATE * (if (n.mood == Mood.ZEALOT) 2f else 1f)
                    prayer += rate * dt
                    n.orbClock += rate * dt
                    if (n.orbClock >= 1.2f) {
                        n.orbClock = 0f
                        fx += Fx(FxKind.ORB, n.x, n.y, n.x, n.y, 1f, 0.9f)
                    }
                }
            } else if (n.isInquisitor) {
                // Straight for the Prophet if there is one, else whoever is nearest. No fear, no doubt.
                val prey = leaderOf(Faction.BELIEVER) ?: nearest(n.x, n.y, Faction.BELIEVER, false)
                if (prey != null) {
                    n.targetX = prey.x
                    n.targetY = prey.y
                    if (dist2(n.x, n.y, prey.x, prey.y) < 34f * 34f) {
                        n.strike += dt / STRIKE_TIME
                        if (n.strike >= 1f) {
                            n.strike = 0f
                            kill(prey, BY_BLADE)
                        }
                    } else {
                        n.strike = max(0f, n.strike - dt)
                    }
                }
                stopAt = 24f
                speed = 58f
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
                val refuge = if (sheltering && n.mood == Mood.SPOOKED) refugeFor(n) else null
                if (refuge != null) {
                    // Somewhere to hide: through the door and out of sight.
                    n.targetX = doorX(refuge)
                    n.targetY = doorY(refuge)
                    stopAt = 3f
                    speed = 170f
                    if (dist2(n.x, n.y, n.targetX, n.targetY) < 16f * 16f) {
                        n.inside = refuge
                        n.shelterClock = SHELTER_TIME
                        n.x = refuge.x + refuge.w / 2f
                        n.y = refuge.y + refuge.h / 2f
                        n.pushX = 0f
                        n.pushY = 0f
                        n.heat = 0f
                        continue
                    }
                } else if (n.mood == Mood.SPOOKED) {
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
                if (!o.alive || o.inside != null) continue
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

    /**
     * A Believer follower near the banner picks a fight with the nearest heathen in reach.
     * Returns true if this one is fighting. Most blows land. Some go the other way.
     */
    private fun crusade(n: Npc, dt: Float): Boolean {
        val ban = banner ?: return false
        if (n.isLeader || dist2(n.x, n.y, ban.x, ban.y) > RALLY_REACH * RALLY_REACH) {
            n.strike = 0f
            return false
        }
        var foe: Npc? = null
        var bestD = 150f * 150f
        for (o in npcs) {
            if (!o.alive || o.faction != Faction.NON_BELIEVER || o.inside != null) continue
            if (dist2(o.x, o.y, ban.x, ban.y) > RALLY_REACH * RALLY_REACH) continue
            val d = dist2(n.x, n.y, o.x, o.y)
            if (d < bestD) {
                bestD = d
                foe = o
            }
        }
        if (foe == null) {
            n.strike = 0f
            return false
        }
        n.targetX = foe.x
        n.targetY = foe.y
        // Heathen followers do not stand and wait for it.
        if (foe.isFollower && foe.mood != Mood.SPOOKED) {
            foe.mood = Mood.SPOOKED
            foe.moodTime = 1.5f
        }
        if (bestD < 34f * 34f) {
            n.strike += dt / RALLY_STRIKE_TIME
            if (n.strike >= 1f) {
                n.strike = 0f
                if (rng.nextFloat() < RALLY_LOSS_CHANCE) {
                    kill(n, BY_BATTLE)
                } else if (foe.hp > 1) {
                    foe.hp--
                } else {
                    kill(foe)
                }
            }
        } else {
            n.strike = max(0f, n.strike - dt)
        }
        return true
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
            flock == 0 -> lose(if (armageddon) "Lost at Armageddon" else "Nobody left to worship you")
            // Once Armageddon starts the houses no longer count: last flock standing wins.
            heathens == 0 && (armageddon || houses(Faction.NON_BELIEVER) == 0) -> win()
            // A battle that will not end goes to whoever has more left.
            armageddon && armageddonClock > 60f -> if (flock > heathens) win() else lose("Outnumbered at the end")
            timeLeft <= 0f && !holyWar -> lose("They stopped paying attention")
        }
    }

    private fun win() {
        outcome = Outcome.VICTORY
        charging = false
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
