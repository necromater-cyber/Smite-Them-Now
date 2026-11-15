package com.necromater.smitethemnow.sim

/*
 * Plain data for the simulation. Nothing in the sim package touches Android,
 * so the rules can be tested on a desktop JVM or moved to another engine.
 */

enum class Faction { BELIEVER, NON_BELIEVER }

/**
 * A LEADER preaches: the Prophet for the Believers, the Heretic for the heathens.
 * An INQUISITOR is a heathen who walks up to Believers and kills them.
 */
enum class Role { FOLLOWER, LEADER, INQUISITOR }

/** CALM is the default. ZEALOT and TERROR apply to Believers, SPOOKED to Non-Believers. */
enum class Mood { CALM, ZEALOT, TERROR, SPOOKED }

enum class Power(val label: String, val cost: Float, val gesture: String) {
    LIGHTNING("Bolt", 12f, "tap"),
    METEOR("Meteor", 35f, "hold"),
    GUST("Gust", 8f, "swipe"),
    SHIELD("Shield", 15f, "tap")
}

enum class Outcome { PLAYING, VICTORY, DEFEAT }

enum class FxKind { BOLT, BLAST, GUST, TEXT, ORB }

class Npc(var x: Float, var y: Float, var faction: Faction, val role: Role = Role.FOLLOWER) {
    var alive = true
    var mood = Mood.CALM
    var moodTime = 0f
    var homeX = x
    var homeY = y
    var targetX = x
    var targetY = y
    var retarget = 0f
    /** Velocity added by wind. Decays quickly. */
    var pushX = 0f
    var pushY = 0f
    /** Where the thing that terrified this Believer happened. */
    var fearX = x
    var fearY = y
    /** Seconds spent standing in fire. */
    var heat = 0f
    var orbClock = 0f
    /** Throttles the "SPARED" pop-up while a shielded Believer stands in harm's way. */
    var sparedClock = 0f
    /** Bolts needed to kill. The Heretic's ward soaks up the first one. */
    var hp = 1
    /** 0..1: how far the other side's leader has talked this follower round. */
    var sway = 0f
    var preached = false
    /** A new convert cannot be harmed for a moment, so one chain does not bless and burn them. */
    var grace = 0f
    /** The fallen Lightning Rod this heathen has been sent to rebuild. */
    var task: Rod? = null

    /** The building this heathen is hiding in. Hidden people cannot be seen or hit. */
    var inside: Building? = null
    var shelterClock = 0f
    /** 0..1: an Inquisitor's wind-up before the blow lands. */
    var strike = 0f

    val isLeader: Boolean get() = role == Role.LEADER
    val isFollower: Boolean get() = role == Role.FOLLOWER
    val isInquisitor: Boolean get() = role == Role.INQUISITOR
}

class Building(val x: Float, val y: Float, val w: Float, val h: Float, val holy: Boolean = false) {
    var standing = true
    /** 2 is sound, 1 is cracked, 0 is rubble. */
    var hp = 2
    /** Seconds of fire damage building up towards the next crack. */
    var scorch = 0f
}

/** A Miracle Shield: Believers standing inside cannot be harmed while it lasts. */
class Shield(val x: Float, val y: Float, val radius: Float, var ttl: Float)

/** A Lightning Rod: swallows any Bolt cast inside its ring until it overloads. */
class Rod(val x: Float, val y: Float) {
    var alive = true
    var charges = 0
    /** Seconds until the heathens send someone to put it back up. */
    var rebuildDelay = 0f
    /** 0..1 while a builder is working on it. */
    var progress = 0f
    var rebuildsLeft = 2
    var builder: Npc? = null
}

class Barrel(val x: Float, val y: Float) {
    var alive = true
    /** Negative means not lit. Otherwise seconds until it explodes. */
    var fuse = -1f
}

class Fire(var x: Float, var y: Float, val radius: Float, var ttl: Float) {
    var vx = 0f
    var vy = 0f
    var driftTime = 0f
    var trail = 0f
}

/** What the chapter list and the briefing card show, and the chain needed for the third star. */
class LevelInfo(val title: String, val blurb: String, val tips: List<String>, val chainGoal: Int)

/** A short-lived visual event. The renderer decides how each kind looks. */
class Fx(
    val kind: FxKind,
    val x: Float,
    val y: Float,
    val x2: Float,
    val y2: Float,
    val size: Float,
    val life: Float,
    val text: String = "",
    val good: Boolean = true
) {
    var age = 0f
}
