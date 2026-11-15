package com.necromater.smitethemnow.sim

/*
 * Plain data for the simulation. Nothing in the sim package touches Android,
 * so the rules can be tested on a desktop JVM or moved to another engine.
 */

enum class Faction { BELIEVER, NON_BELIEVER }

/** CALM is the default. ZEALOT and TERROR apply to Believers, SPOOKED to Non-Believers. */
enum class Mood { CALM, ZEALOT, TERROR, SPOOKED }

enum class Power(val label: String, val cost: Float, val gesture: String) {
    LIGHTNING("Bolt", 12f, "tap"),
    METEOR("Meteor", 35f, "hold"),
    GUST("Gust", 8f, "swipe")
}

enum class Outcome { PLAYING, VICTORY, DEFEAT }

enum class FxKind { BOLT, BLAST, GUST, TEXT, ORB }

class Npc(var x: Float, var y: Float, val faction: Faction) {
    var alive = true
    var mood = Mood.CALM
    var moodTime = 0f
    val homeX = x
    val homeY = y
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
}

class Building(val x: Float, val y: Float, val w: Float, val h: Float) {
    var standing = true
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
