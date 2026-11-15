package com.necromater.smitethemnow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.necromater.smitethemnow.sim.Building
import com.necromater.smitethemnow.sim.Doctrine
import com.necromater.smitethemnow.sim.Faction
import com.necromater.smitethemnow.sim.Fx
import com.necromater.smitethemnow.sim.FxKind
import com.necromater.smitethemnow.sim.Mood
import com.necromater.smitethemnow.sim.Npc
import com.necromater.smitethemnow.sim.Outcome
import com.necromater.smitethemnow.sim.Power
import com.necromater.smitethemnow.sim.Rod
import com.necromater.smitethemnow.sim.World
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws the game as a page from an illuminated manuscript and turns touches into powers.
 *
 * Screens: chapter list, a briefing card, play, and the result card.
 * Layout during play, top to bottom: HUD strip, the page, the power bar.
 * The loop is the simplest one that works: update and draw in onDraw,
 * then ask for the next frame.
 */
class GameView(context: Context) : View(context) {

    private enum class Screen { MENU, SETUP, BRIEFING, PLAY, RESULT }

    private val prefs = context.getSharedPreferences("smite", Context.MODE_PRIVATE)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val serif: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    private val serifBold: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val numerals = arrayOf("I", "II", "III", "IV", "V", "War")

    // The manuscript palette.
    private val ink = Color.rgb(46, 32, 22)
    private val vellum = Color.rgb(232, 216, 176)
    private val vellumDark = Color.rgb(212, 192, 148)
    private val leather = Color.rgb(52, 34, 24)
    private val gold = Color.rgb(222, 174, 54)
    private val goldLight = Color.rgb(250, 226, 136)
    private val vermilion = Color.rgb(196, 52, 36)
    private val ultramarine = Color.rgb(40, 62, 140)
    private val murrey = Color.rgb(120, 36, 66)
    private val skin = Color.rgb(236, 200, 160)

    // Faint stains so the page does not look like a flat fill.
    private val blotX = FloatArray(34)
    private val blotY = FloatArray(34)
    private val blotR = FloatArray(34)

    private var screen = Screen.MENU
    private var level = 0
    private var world = World(0)
    private var selected = Power.LIGHTNING

    // Holy War choices, remembered between sessions.
    private val mapNames = arrayOf("Two Hills", "The Ford", "Walled Town")
    private val doctrines = arrayOf(Doctrine.SHEPHERD, Doctrine.WRATHFUL, Doctrine.TRICKSTER)
    private var warMap = prefs.getInt("war_map", 0).coerceIn(0, 2)
    private var warDoctrine = prefs.getInt("war_doctrine", 0).coerceIn(0, 2)
    private var setupMapTop = 0f
    private var setupMapBottom = 0f
    private val setupDocTop = FloatArray(3)
    private val setupDocBottom = FloatArray(3)
    private var setupBeginTop = 0f
    private var setupBeginBottom = 0f

    private var lastNanos = 0L
    private var clock = 0f
    private var overTime = 0f

    private var scale = 1f
    private var originX = 0f
    private var originY = 0f
    private var hudH = 0f
    private var barH = 0f

    private var dragging = false
    private var dragX0 = 0f
    private var dragY0 = 0f
    private var dragX1 = 0f
    private var dragY1 = 0f

    // Tap targets worked out while drawing.
    private val cardTop = FloatArray(World.LEVELS.size)
    private val cardBottom = FloatArray(World.LEVELS.size)
    private var buttonTop = 0f
    private var buttonBottom = 0f
    private var buttons: List<String> = emptyList()

    init {
        val r = Random(3)
        for (i in blotX.indices) {
            blotX[i] = r.nextFloat() * World.W
            blotY[i] = r.nextFloat() * World.H
            blotR[i] = 30f + r.nextFloat() * 70f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        hudH = h * 0.078f
        barH = h * 0.1f
        val areaH = h - hudH - barH
        scale = min(w / World.W, areaH / World.H)
        originX = (w - World.W * scale) / 2f
        originY = hudH + (areaH - World.H * scale) / 2f
    }

    // ---------------------------------------------------------------- flow

    /** [setup] false skips the Holy War choices, for "Again". */
    private fun startLevel(index: Int, setup: Boolean = true) {
        level = index.coerceIn(0, World.LEVELS.size - 1)
        if (level == World.HOLY_WAR && setup) {
            screen = Screen.SETUP
            return
        }
        world = if (level == World.HOLY_WAR) {
            World(level, System.nanoTime(), doctrines[warDoctrine], warMap)
        } else {
            World(level, System.nanoTime())
        }
        selected = Power.LIGHTNING
        dragging = false
        overTime = 0f
        screen = Screen.BRIEFING
    }

    /** Called for the system Back button. Returns false when the app should close. */
    fun handleBack(): Boolean {
        if (screen == Screen.MENU) return false
        world.cancelCharge()
        dragging = false
        screen = Screen.MENU
        return true
    }

    private fun bestStars(index: Int): Int = prefs.getInt("stars_$index", 0)

    private fun finishLevel() {
        screen = Screen.RESULT
        overTime = 0f
        dragging = false
        if (world.outcome == Outcome.VICTORY && world.stars() > bestStars(level)) {
            prefs.edit().putInt("stars_$level", world.stars()).apply()
        }
    }

    // ---------------------------------------------------------------- input

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val sx = event.x
        val sy = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> when (screen) {
                Screen.MENU -> {
                    for (i in cardTop.indices) {
                        if (sy >= cardTop[i] && sy <= cardBottom[i]) startLevel(i)
                    }
                }
                Screen.SETUP -> {
                    if (sy >= setupMapTop && sy <= setupMapBottom) {
                        warMap = (sx / (width / 3f)).toInt().coerceIn(0, 2)
                        prefs.edit().putInt("war_map", warMap).apply()
                    }
                    for (i in 0 until 3) {
                        if (sy >= setupDocTop[i] && sy <= setupDocBottom[i]) {
                            warDoctrine = i
                            prefs.edit().putInt("war_doctrine", warDoctrine).apply()
                        }
                    }
                    if (sy >= setupBeginTop && sy <= setupBeginBottom) startLevel(World.HOLY_WAR, false)
                }
                Screen.BRIEFING -> screen = Screen.PLAY
                Screen.RESULT -> {
                    if (overTime > 0.8f && buttons.isNotEmpty() && sy >= buttonTop && sy <= buttonBottom) {
                        val index = (sx / (width / buttons.size.toFloat())).toInt().coerceIn(0, buttons.size - 1)
                        when (buttons[index]) {
                            "Again" -> startLevel(level, false)
                            "Next" -> startLevel(level + 1)
                            else -> screen = Screen.MENU
                        }
                    }
                }
                Screen.PLAY -> {
                    if (sy >= height - barH) {
                        val bar = world.powers
                        val index = (sx / (width / bar.size.toFloat())).toInt()
                        selected = bar[index.coerceIn(0, bar.size - 1)]
                        return true
                    }
                    val wx = toWorldX(sx)
                    val wy = toWorldY(sy)
                    when (selected) {
                        Power.LIGHTNING -> world.castLightning(wx, wy)
                        Power.SHIELD -> world.castShield(wx, wy)
                        Power.RALLY -> world.castRally(wx, wy)
                        Power.METEOR -> world.beginCharge(wx, wy)
                        Power.GUST -> {
                            dragging = true
                            dragX0 = wx
                            dragY0 = wy
                            dragX1 = wx
                            dragY1 = wy
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val wx = toWorldX(sx)
                val wy = toWorldY(sy)
                if (world.charging) world.moveCharge(wx, wy)
                if (dragging) {
                    dragX1 = wx
                    dragY1 = wy
                }
            }
            MotionEvent.ACTION_UP -> {
                if (world.charging) world.releaseCharge()
                if (dragging) {
                    dragging = false
                    world.castGust(dragX0, dragY0, dragX1, dragY1)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                world.cancelCharge()
                dragging = false
            }
        }
        return true
    }

    private fun toWorldX(sx: Float) = ((sx - originX) / scale).coerceIn(0f, World.W)
    private fun toWorldY(sy: Float) = ((sy - originY) / scale).coerceIn(0f, World.H)

    // ---------------------------------------------------------------- frame

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else min((now - lastNanos) / 1_000_000_000f, 0.05f)
        lastNanos = now
        clock += dt

        if (screen == Screen.PLAY) {
            world.update(dt)
            if (world.outcome != Outcome.PLAYING) finishLevel()
        } else if (screen == Screen.RESULT) {
            world.update(dt)
            overTime += dt
        }

        canvas.drawColor(leather)
        if (screen == Screen.MENU) {
            drawMenu(canvas)
        } else if (screen == Screen.SETUP) {
            drawSetup(canvas)
        } else {
            canvas.save()
            if (world.shake > 0f) {
                val s = world.shake * 22f
                canvas.translate(sin(clock * 91f) * s, sin(clock * 77f + 1.3f) * s)
            }
            canvas.translate(originX, originY)
            canvas.scale(scale, scale)
            drawWorld(canvas)
            canvas.restore()

            drawHud(canvas)
            drawPowerBar(canvas)
            if (screen == Screen.BRIEFING) drawBriefing(canvas)
            if (screen == Screen.RESULT) drawResult(canvas)
        }

        postInvalidateOnAnimation()
    }

    // ---------------------------------------------------------------- the page

    private fun drawWorld(c: Canvas) {
        val w = world
        fill(vellum)
        c.drawRect(0f, 0f, World.W, World.H, paint)
        fill(Color.argb(12, 120, 84, 40))
        for (i in blotX.indices) c.drawCircle(blotX[i], blotY[i], blotR[i], paint)

        // What is left of the smitten: a smudge and a small ink cross.
        for (n in w.npcs) {
            if (n.alive) continue
            fill(Color.argb(60, 46, 32, 22))
            c.drawCircle(n.x, n.y + 6f, 13f, paint)
            stroke(Color.argb(150, 46, 32, 22), 2.5f)
            c.drawLine(n.x - 6f, n.y, n.x + 6f, n.y + 12f, paint)
            c.drawLine(n.x + 6f, n.y, n.x - 6f, n.y + 12f, paint)
        }

        for (r in w.rods) {
            if (!r.alive) continue
            stroke(Color.argb(95, 46, 32, 22), 3f)
            dashedRing(c, r.x, r.y, World.ROD_RADIUS, 40)
        }

        for (s in w.shields) drawShield(c, s.x, s.y, s.radius, min(1f, s.ttl))
        for (b in w.buildings) drawBuilding(c, b, w.occupants(b))

        for (b in w.barrels) {
            if (!b.alive) continue
            val lit = b.fuse >= 0f
            fill(if (lit) vermilion else Color.rgb(150, 100, 52))
            c.drawCircle(b.x, b.y, 14f, paint)
            stroke(ink, 3f)
            c.drawCircle(b.x, b.y, 14f, paint)
            c.drawCircle(b.x, b.y, 7f, paint)
        }

        val ban = w.banner
        if (ban != null) {
            // The rally banner and the ground it claims.
            val fade = min(1f, ban.ttl)
            stroke(Color.argb((120 * fade).toInt(), 40, 62, 140), 3f)
            dashedRing(c, ban.x, ban.y, World.RALLY_REACH, 30)
            stroke(ink, 6f)
            c.drawLine(ban.x, ban.y, ban.x, ban.y - 80f, paint)
            path.reset()
            path.moveTo(ban.x, ban.y - 80f)
            path.lineTo(ban.x + 46f, ban.y - 68f + 4f * sin(clock * 6f))
            path.lineTo(ban.x, ban.y - 52f)
            path.close()
            fill(ultramarine)
            c.drawPath(path, paint)
            stroke(gold, 3f)
            c.drawPath(path, paint)
        }
        for (r in w.rods) drawRod(c, r)
        for (f in w.fires) drawFire(c, f.x, f.y, f.radius, min(1f, f.ttl / 0.6f))

        // Each leader's reach, so you can see who is about to be talked round.
        for (n in w.npcs) {
            if (!n.alive || !n.isLeader) continue
            if (n.faction == Faction.BELIEVER) {
                stroke(Color.argb(150, 222, 174, 54), 3f)
                dashedRing(c, n.x, n.y, World.PROPHET_AURA, 22)
            } else {
                stroke(Color.argb(150, 196, 52, 36), 3f)
                dashedRing(c, n.x, n.y, World.HERETIC_AURA, 20)
            }
        }
        for (n in w.npcs) if (n.alive && !n.isLeader && n.inside == null) drawNpc(c, n)
        for (n in w.npcs) if (n.alive && n.isLeader) drawNpc(c, n)

        for (o in w.omens) {
            // The rival god's aim: a red mark that closes in until the blow lands.
            val left = (o.ttl / o.total).coerceIn(0f, 1f)
            if (o.gust) {
                // A red arrow: the rival's wind is about to blow this way.
                val ax = o.x - o.dx * 110f
                val ay = o.y - o.dy * 110f
                val bx = o.x + o.dx * 110f
                val by = o.y + o.dy * 110f
                stroke(ink, 9f)
                c.drawLine(ax, ay, bx, by, paint)
                stroke(vermilion, 5f)
                c.drawLine(ax, ay, bx, by, paint)
                c.drawLine(bx, by, bx - o.dx * 30f - o.dy * 20f, by - o.dy * 30f + o.dx * 20f, paint)
                c.drawLine(bx, by, bx - o.dx * 30f + o.dy * 20f, by - o.dy * 30f - o.dx * 20f, paint)
            } else if (o.meteor) {
                fill(Color.argb((50 + 90 * (1f - left)).toInt(), 120, 20, 20))
                c.drawCircle(o.x, o.y, World.RIVAL_METEOR_RADIUS * (1f - left), paint)
                stroke(ink, 8f)
                c.drawCircle(o.x, o.y, World.RIVAL_METEOR_RADIUS, paint)
                stroke(vermilion, 4f)
                dashedRing(c, o.x, o.y, World.RIVAL_METEOR_RADIUS, 18)
            } else {
                val r = 26f + 44f * left
                stroke(ink, 7f)
                c.drawCircle(o.x, o.y, r, paint)
                stroke(vermilion, 4f)
                c.drawCircle(o.x, o.y, r, paint)
                c.drawLine(o.x - r - 10f, o.y, o.x - r + 10f, o.y, paint)
                c.drawLine(o.x + r - 10f, o.y, o.x + r + 10f, o.y, paint)
                c.drawLine(o.x, o.y - r - 10f, o.x, o.y - r + 10f, paint)
                c.drawLine(o.x, o.y + r - 10f, o.x, o.y + r + 10f, paint)
            }
        }

        if (w.charging) {
            val full = w.charge >= 1f
            fill(Color.argb((40 + 80 * w.charge).toInt(), 196, 52, 36))
            c.drawCircle(w.chargeX, w.chargeY, World.METEOR_RADIUS * w.charge, paint)
            stroke(ink, if (full) 9f else 6f)
            c.drawCircle(w.chargeX, w.chargeY, World.METEOR_RADIUS, paint)
            stroke(if (full) goldLight else gold, if (full) 5f else 3f)
            c.drawCircle(w.chargeX, w.chargeY, World.METEOR_RADIUS, paint)
            if (full) {
                var k = 0
                while (k < 12) {
                    val a = k * 0.5236f + clock * 0.8f
                    c.drawLine(
                        w.chargeX + cos(a) * (World.METEOR_RADIUS + 10f), w.chargeY + sin(a) * (World.METEOR_RADIUS + 10f),
                        w.chargeX + cos(a) * (World.METEOR_RADIUS + 30f), w.chargeY + sin(a) * (World.METEOR_RADIUS + 30f), paint
                    )
                    k++
                }
            }
        }

        if (dragging) {
            stroke(Color.argb(200, 46, 32, 22), 9f)
            c.drawLine(dragX0, dragY0, dragX1, dragY1, paint)
            stroke(Color.argb(230, 250, 226, 136), 4f)
            c.drawLine(dragX0, dragY0, dragX1, dragY1, paint)
            fill(goldLight)
            c.drawCircle(dragX1, dragY1, 12f, paint)
            stroke(ink, 2.5f)
            c.drawCircle(dragX1, dragY1, 12f, paint)
        }

        for (f in w.fx) drawFx(c, f)
        drawPageBorder(c)
    }

    private fun drawPageBorder(c: Canvas) {
        stroke(ink, 4f)
        c.drawRect(2f, 2f, World.W - 2f, World.H - 2f, paint)
        stroke(vermilion, 7f)
        c.drawRect(11f, 11f, World.W - 11f, World.H - 11f, paint)
        stroke(gold, 3f)
        c.drawRect(20f, 20f, World.W - 20f, World.H - 20f, paint)
        var k = 0
        while (k < 4) {
            val cx = if (k % 2 == 0) 16f else World.W - 16f
            val cy = if (k < 2) 16f else World.H - 16f
            fill(ultramarine)
            c.drawCircle(cx, cy, 15f, paint)
            fill(gold)
            c.drawCircle(cx, cy, 7f, paint)
            stroke(ink, 2.5f)
            c.drawCircle(cx, cy, 15f, paint)
            k++
        }
    }

    private fun drawBuilding(c: Canvas, b: Building, hiding: Int) {
        if (b.kind == Building.WATER) {
            fill(Color.rgb(96, 132, 190))
            c.drawRect(b.x, b.y, b.x + b.w, b.y + b.h, paint)
            stroke(Color.argb(170, 240, 228, 196), 3f)
            var wx = b.x + 10f
            var row = 0
            while (wx < b.x + b.w - 30f) {
                val wy = b.y + b.h * (if (row % 2 == 0) 0.33f else 0.68f) + 3f * sin(clock * 2f + wx * 0.02f)
                c.drawLine(wx, wy, wx + 14f, wy - 6f, paint)
                c.drawLine(wx + 14f, wy - 6f, wx + 28f, wy, paint)
                wx += 44f
                row++
            }
            stroke(ink, 3f)
            c.drawLine(b.x, b.y, b.x + b.w, b.y, paint)
            c.drawLine(b.x, b.y + b.h, b.x + b.w, b.y + b.h, paint)
            return
        }
        if (b.kind == Building.WALL && b.standing) {
            fill(Color.rgb(176, 166, 146))
            c.drawRect(b.x, b.y, b.x + b.w, b.y + b.h, paint)
            stroke(Color.argb(140, 46, 32, 22), 2f)
            if (b.w > b.h) {
                var bx = b.x + 30f
                while (bx < b.x + b.w) {
                    c.drawLine(bx, b.y, bx, b.y + b.h, paint)
                    bx += 30f
                }
            } else {
                var by = b.y + 30f
                while (by < b.y + b.h) {
                    c.drawLine(b.x, by, b.x + b.w, by, paint)
                    by += 30f
                }
            }
            stroke(ink, if (b.hp < 2) 6f else 3.5f)
            c.drawRect(b.x, b.y, b.x + b.w, b.y + b.h, paint)
            return
        }
        if (!b.standing) {
            fill(Color.argb(70, 46, 32, 22))
            c.drawRect(b.x, b.y, b.x + b.w, b.y + b.h, paint)
            var k = 0
            while (k < 9) {
                val rx = b.x + b.w * (0.14f + 0.36f * (k % 3))
                val ry = b.y + b.h * (0.2f + 0.3f * (k / 3))
                val rr = 10f + (k % 2) * 5f
                fill(vellumDark)
                c.drawCircle(rx, ry, rr, paint)
                stroke(ink, 2f)
                c.drawCircle(rx, ry, rr, paint)
                k++
            }
            return
        }
        if (b.built < 1f) {
            // Foundations and scaffolding. One blast clears it.
            fill(Color.argb(90, 212, 192, 148))
            c.drawRect(b.x, b.y, b.x + b.w, b.y + b.h, paint)
            stroke(ink, 3f)
            dashedRing(c, b.x + b.w / 2f, b.y + b.h / 2f, min(b.w, b.h) * 0.42f, 12)
            c.drawLine(b.x, b.y + b.h, b.x + b.w, b.y + b.h, paint)
            c.drawLine(b.x, b.y + b.h, b.x, b.y + b.h * (1f - b.built), paint)
            c.drawLine(b.x + b.w, b.y + b.h, b.x + b.w, b.y + b.h * (1f - b.built), paint)
            fill(if (b.owner == Faction.BELIEVER) ultramarine else vermilion)
            c.drawRect(b.x + 8f, b.y + b.h - 14f, b.x + 8f + (b.w - 16f) * b.built, b.y + b.h - 6f, paint)
            return
        }
        // Flat, front-on and proud of it, like a building in a margin.
        fill(Color.rgb(240, 228, 196))
        c.drawRect(b.x, b.y, b.x + b.w, b.y + b.h, paint)
        val roofH = b.h * 0.42f
        fill(if (b.holy || b.owner == Faction.BELIEVER) ultramarine else vermilion)
        c.drawRect(b.x, b.y, b.x + b.w, b.y + roofH, paint)
        stroke(Color.argb(110, 250, 226, 136), 2f)
        var hx = b.x + 14f
        while (hx < b.x + b.w - 4f) {
            c.drawLine(hx, b.y + 4f, hx - 10f, b.y + roofH - 4f, paint)
            hx += 16f
        }
        stroke(ink, 3.5f)
        c.drawRect(b.x, b.y, b.x + b.w, b.y + b.h, paint)
        c.drawLine(b.x, b.y + roofH, b.x + b.w, b.y + roofH, paint)
        // Door.
        val dx = b.x + b.w / 2f
        fill(ink)
        c.drawRect(dx - 11f, b.y + b.h - 34f, dx + 11f, b.y + b.h, paint)
        c.drawCircle(dx, b.y + b.h - 34f, 11f, paint)
        if (b.holy) {
            stroke(ink, 8f)
            c.drawLine(dx, b.y + 8f, dx, b.y + roofH - 8f, paint)
            c.drawLine(dx - 13f, b.y + roofH * 0.4f, dx + 13f, b.y + roofH * 0.4f, paint)
            stroke(goldLight, 4f)
            c.drawLine(dx, b.y + 8f, dx, b.y + roofH - 8f, paint)
            c.drawLine(dx - 13f, b.y + roofH * 0.4f, dx + 13f, b.y + roofH * 0.4f, paint)
        }
        if (b.ward > 0f) {
            // The rival god's ward: nothing you throw at this will land.
            stroke(ink, 8f)
            c.drawRect(b.x - 10f, b.y - 10f, b.x + b.w + 10f, b.y + b.h + 10f, paint)
            stroke(vermilion, 4f)
            c.drawRect(b.x - 10f, b.y - 10f, b.x + b.w + 10f, b.y + b.h + 10f, paint)
        }
        if (hiding > 0) {
            // Eyes in the doorway and a head count: somebody is in there.
            fill(goldLight)
            c.drawCircle(dx - 4.5f, b.y + b.h - 22f, 3f, paint)
            c.drawCircle(dx + 4.5f, b.y + b.h - 22f, 3f, paint)
            val bx = b.x + b.w - 6f
            val by = b.y + b.h - 6f
            fill(murrey)
            c.drawCircle(bx, by, 22f, paint)
            stroke(ink, 3f)
            c.drawCircle(bx, by, 22f, paint)
            label(c, hiding.toString(), bx, by + 10f, 30f, goldLight, Paint.Align.CENTER)
        }
        if (b.scorch > 0f) {
            fill(Color.argb((110 * min(1f, b.scorch / World.SCORCH_TIME)).toInt(), 46, 32, 22))
            c.drawRect(b.x, b.y, b.x + b.w, b.y + b.h, paint)
        }
        if (b.hp == 1) {
            // Cracked: one more blast and it comes down.
            stroke(ink, 4f)
            val x0 = b.x + b.w * 0.22f
            c.drawLine(x0, b.y, x0 + 14f, b.y + b.h * 0.3f, paint)
            c.drawLine(x0 + 14f, b.y + b.h * 0.3f, x0 - 6f, b.y + b.h * 0.55f, paint)
            c.drawLine(x0 - 6f, b.y + b.h * 0.55f, x0 + 10f, b.y + b.h, paint)
            val x1 = b.x + b.w * 0.74f
            c.drawLine(x1, b.y, x1 - 12f, b.y + b.h * 0.4f, paint)
            c.drawLine(x1 - 12f, b.y + b.h * 0.4f, x1 + 8f, b.y + b.h * 0.7f, paint)
        }
    }

    private fun drawNpc(c: Canvas, n: Npc) {
        val believer = n.faction == Faction.BELIEVER
        val leader = n.isLeader
        val zealot = believer && n.mood == Mood.ZEALOT
        val terror = believer && n.mood == Mood.TERROR
        val inquisitor = n.isInquisitor
        val s = if (leader) 1.9f else if (inquisitor) 1.65f else 1.4f
        val headY = -11f

        c.save()
        c.translate(n.x, n.y)
        c.scale(s, s)

        if (zealot || n.grace > 0f) {
            // Rays of unreasonable enthusiasm.
            stroke(gold, 3f)
            var k = 0
            while (k < 8) {
                val a = k * 0.7854f + clock * 2f
                c.drawLine(cos(a) * 15f, headY + sin(a) * 15f, cos(a) * 23f, headY + sin(a) * 23f, paint)
                k++
            }
        }
        if (zealot) {
            stroke(ink, 3f)
            c.drawLine(-7f, -3f, -15f, -16f, paint)
            c.drawLine(7f, -3f, 15f, -16f, paint)
        }

        // Staff: a crozier for the Prophet, a red-tipped rod for the Heretic.
        if (leader) {
            val sx = if (believer) 14f else -14f
            stroke(ink, 2.5f)
            c.drawLine(sx, 14f, sx, -20f, paint)
            if (believer) {
                stroke(gold, 2.5f)
                c.drawCircle(sx - 4f, -21f, 4f, paint)
            } else {
                fill(vermilion)
                c.drawCircle(sx, -23f, 4.5f, paint)
                stroke(ink, 1.5f)
                c.drawCircle(sx, -23f, 4.5f, paint)
            }
        }

        if (inquisitor) {
            // A sword that lifts as the blow winds up.
            val lift = min(1f, n.strike)
            val tipX = 16f + 6f * (1f - lift)
            val tipY = -8f - 20f * lift
            stroke(ink, 5f)
            c.drawLine(9f, 4f, tipX, tipY, paint)
            stroke(goldLight, 2.5f)
            c.drawLine(9f, 4f, tipX, tipY, paint)
            stroke(ink, 3f)
            c.drawLine(5f, 1f, 13f, 7f, paint)
        }

        // Robe.
        path.reset()
        path.moveTo(-5f, -6f)
        path.lineTo(5f, -6f)
        path.lineTo(11f, 14f)
        path.lineTo(-11f, 14f)
        path.close()
        val robe = if (believer) {
            if (terror) Color.rgb(150, 160, 184) else if (leader) Color.rgb(246, 238, 214) else ultramarine
        } else {
            if (leader) ink else if (inquisitor) vermilion else murrey
        }
        fill(robe)
        c.drawPath(path, paint)
        stroke(ink, 2.5f)
        c.drawPath(path, paint)
        if (leader) {
            stroke(if (believer) gold else vermilion, 3f)
            c.drawLine(0f, -4f, 0f, 12f, paint)
        }

        if (believer && !terror) {
            fill(if (zealot) goldLight else gold)
            c.drawCircle(0f, headY, 11f, paint)
            stroke(ink, 1.5f)
            c.drawCircle(0f, headY, 11f, paint)
        }

        fill(skin)
        c.drawCircle(0f, headY, 6.5f, paint)
        stroke(ink, 2f)
        c.drawCircle(0f, headY, 6.5f, paint)

        if (believer && leader) {
            // Mitre.
            path.reset()
            path.moveTo(-6f, headY - 4f)
            path.lineTo(0f, headY - 19f)
            path.lineTo(6f, headY - 4f)
            path.close()
            fill(goldLight)
            c.drawPath(path, paint)
            stroke(ink, 1.5f)
            c.drawPath(path, paint)
        }
        if (!believer) {
            // A pointed hood, so nobody mistakes them for the saved.
            path.reset()
            path.moveTo(-8f, headY - 2f)
            path.lineTo(2f, headY - (if (leader) 27f else 21f))
            path.lineTo(8f, headY - 2f)
            path.close()
            fill(if (leader) vermilion else ink)
            c.drawPath(path, paint)
            if (leader) {
                stroke(ink, 1.5f)
                c.drawPath(path, paint)
            }
            if (n.task != null) {
                // Carrying a mallet: this one is off to rebuild a rod.
                stroke(ink, 2.5f)
                c.drawLine(9f, 4f, 17f, -8f, paint)
                stroke(ink, 5f)
                c.drawLine(14f, -11f, 20f, -6f, paint)
            }
            if (n.mood == Mood.SPOOKED) {
                label(c, "!", 15f, headY - 8f, 26f, vermilion, Paint.Align.CENTER)
            }
            if (leader && n.hp > 1) {
                // His ward: the first Bolt breaks it.
                stroke(Color.argb(200, 196, 52, 36), 2f)
                dashedRing(c, 0f, 0f, 24f, 10)
            }
        }
        c.restore()

        if (n.strike > 0.03f) {
            val top = n.y - 36f * s
            fill(ink)
            c.drawRect(n.x - 20f, top, n.x + 20f, top + 9f, paint)
            fill(if (believer) goldLight else vermilion)
            c.drawRect(n.x - 18f, top + 2f, n.x - 18f + 36f * min(1f, n.strike), top + 7f, paint)
        }
        if (n.sway > 0.03f) {
            // How far the other side's preacher has got with this one.
            val top = n.y - 36f * s
            fill(ink)
            c.drawRect(n.x - 20f, top, n.x + 20f, top + 9f, paint)
            fill(if (believer) vermilion else goldLight)
            c.drawRect(n.x - 18f, top + 2f, n.x - 18f + 36f * min(1f, n.sway), top + 7f, paint)
        }
    }

    private fun drawFire(c: Canvas, x: Float, y: Float, radius: Float, fade: Float) {
        fill(Color.argb((70 * fade).toInt(), 196, 52, 36))
        c.drawCircle(x, y, radius, paint)
        var k = 0
        while (k < 7) {
            val a = k * 0.898f + x * 0.01f
            val d = if (k == 0) 0f else radius * 0.52f
            val px = x + cos(a) * d
            val py = y + sin(a) * d
            val h = radius * 0.5f * (0.8f + 0.2f * sin(clock * 14f + k * 1.7f + x))
            flame(c, px, py, h, Color.argb((235 * fade).toInt(), 196, 52, 36))
            flame(c, px, py + h * 0.12f, h * 0.55f, Color.argb((235 * fade).toInt(), 250, 226, 136))
            k++
        }
    }

    private fun flame(c: Canvas, x: Float, y: Float, h: Float, color: Int) {
        path.reset()
        path.moveTo(x - h * 0.36f, y + h * 0.3f)
        path.lineTo(x - h * 0.12f, y - h * 0.15f)
        path.lineTo(x, y - h * 0.75f)
        path.lineTo(x + h * 0.14f, y - h * 0.1f)
        path.lineTo(x + h * 0.36f, y + h * 0.3f)
        path.close()
        fill(color)
        c.drawPath(path, paint)
    }

    private fun drawShield(c: Canvas, x: Float, y: Float, radius: Float, fade: Float) {
        fill(Color.argb((60 * fade).toInt(), 250, 226, 136))
        c.drawCircle(x, y, radius, paint)
        stroke(Color.argb((200 * fade).toInt(), 46, 32, 22), 8f)
        c.drawCircle(x, y, radius, paint)
        stroke(Color.argb((255 * fade).toInt(), 250, 226, 136), 4f)
        c.drawCircle(x, y, radius, paint)
        stroke(Color.argb((170 * fade).toInt(), 222, 174, 54), 3f)
        dashedRing(c, x, y, radius - 14f, 24)
    }

    private fun drawRod(c: Canvas, r: Rod) {
        val x = r.x
        val y = r.y
        val charges = r.charges
        if (!r.alive) {
            stroke(ink, 6f)
            c.drawLine(x - 6f, y + 4f, x + 44f, y + 16f, paint)
            fill(ink)
            c.drawCircle(x - 6f, y + 4f, 9f, paint)
            if (r.progress > 0f) {
                fill(ink)
                c.drawRect(x - 30f, y - 30f, x + 30f, y - 18f, paint)
                fill(vermilion)
                c.drawRect(x - 27f, y - 27f, x - 27f + 54f * min(1f, r.progress), y - 21f, paint)
            }
            return
        }
        fill(ink)
        c.drawCircle(x, y, 13f, paint)
        stroke(ink, 7f)
        c.drawLine(x, y, x, y - 54f, paint)
        // The orb reddens as it fills.
        fill(if (charges == 0) gold else if (charges == 1) Color.rgb(226, 130, 44) else vermilion)
        c.drawCircle(x, y - 58f, 11f, paint)
        stroke(ink, 2.5f)
        c.drawCircle(x, y - 58f, 11f, paint)
        if (charges > 0) {
            stroke(goldLight, 2.5f)
            var k = 0
            while (k < 6) {
                val a = k * 1.047f + clock * 6f
                c.drawLine(x + cos(a) * 15f, y - 58f + sin(a) * 15f, x + cos(a) * (19f + charges * 3f), y - 58f + sin(a) * (19f + charges * 3f), paint)
                k++
            }
        }
    }

    private fun dashedRing(c: Canvas, x: Float, y: Float, radius: Float, dashes: Int) {
        val step = 6.2832f / (dashes * 2)
        var k = 0
        while (k < dashes) {
            val a0 = k * 2 * step
            val a1 = a0 + step
            c.drawLine(x + cos(a0) * radius, y + sin(a0) * radius, x + cos(a1) * radius, y + sin(a1) * radius, paint)
            k++
        }
    }

    private fun drawFx(c: Canvas, f: Fx) {
        val t = (f.age / f.life).coerceIn(0f, 1f)
        val alpha = (255 * (1f - t)).toInt()
        when (f.kind) {
            FxKind.BOLT -> {
                // Drawn twice: an ink underlay, then gold leaf on top.
                var pass = 0
                while (pass < 2) {
                    if (pass == 0) stroke(Color.argb(alpha, 46, 32, 22), 12f)
                    else stroke(Color.argb(alpha, 250, 226, 136), 6f)
                    var px = f.x + 60f
                    var py = f.y - 520f
                    var i = 1
                    while (i <= 6) {
                        val k = i / 6f
                        val jitter = if (i == 6) 0f else sin(i * 12.9898f + f.x) * 26f
                        val nx = f.x + 60f * (1f - k) + jitter
                        val ny = f.y - 520f * (1f - k)
                        c.drawLine(px, py, nx, ny, paint)
                        px = nx
                        py = ny
                        i++
                    }
                    pass++
                }
                fill(Color.argb(alpha, 250, 226, 136))
                c.drawCircle(f.x, f.y, 26f * (1f - t) + 6f, paint)
            }
            FxKind.BLAST -> {
                fill(Color.argb((alpha * 0.5f).toInt(), 222, 174, 54))
                c.drawCircle(f.x, f.y, f.size * (0.4f + 0.6f * t), paint)
                stroke(Color.argb(alpha, 46, 32, 22), 10f)
                c.drawCircle(f.x, f.y, f.size * t, paint)
                stroke(Color.argb(alpha, 196, 52, 36), 5f)
                c.drawCircle(f.x, f.y, f.size * t, paint)
            }
            FxKind.GUST -> {
                val dx = f.x2 - f.x
                val dy = f.y2 - f.y
                val len = max(1f, hypot(dx, dy))
                val nx = -dy / len
                val ny = dx / len
                stroke(Color.argb((alpha * 0.85f).toInt(), 40, 62, 140), 5f)
                var i = -2
                while (i <= 2) {
                    val off = i * f.size * 0.4f
                    val head = min(1f, t * 1.6f + 0.25f)
                    val tail = max(0f, head - 0.45f)
                    c.drawLine(
                        f.x + nx * off + dx * tail, f.y + ny * off + dy * tail,
                        f.x + nx * off + dx * head, f.y + ny * off + dy * head, paint
                    )
                    i++
                }
            }
            FxKind.TEXT -> {
                val tx = f.x.coerceIn(130f, World.W - 130f)
                val ty = f.y - 30f - 60f * t
                paint.typeface = serifBold
                paint.textAlign = Paint.Align.CENTER
                paint.textSize = 36f * f.size
                paint.isFakeBoldText = false
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 6f
                paint.color = Color.argb(alpha, 46, 32, 22)
                c.drawText(f.text, tx, ty, paint)
                paint.style = Paint.Style.FILL
                paint.color = if (f.good) Color.argb(alpha, 250, 226, 136) else Color.argb(alpha, 236, 110, 90)
                c.drawText(f.text, tx, ty, paint)
            }
            FxKind.ORB -> {
                fill(Color.argb(alpha, 250, 226, 136))
                c.drawCircle(f.x, f.y - 26f - 50f * t, 6f, paint)
                stroke(Color.argb(alpha, 46, 32, 22), 1.5f)
                c.drawCircle(f.x, f.y - 26f - 50f * t, 6f, paint)
            }
        }
    }

    // ---------------------------------------------------------------- hud

    private fun drawHud(c: Canvas) {
        val w = width.toFloat()
        val pad = w * 0.03f
        val barW = w * 0.3f
        val barTop = hudH * 0.5f
        val barBottom = hudH * 0.82f
        val labelY = hudH * 0.38f
        val labelSize = hudH * 0.26f

        label(c, "Prayer " + world.prayer.toInt(), pad, labelY, labelSize, goldLight, Paint.Align.LEFT)
        meter(c, pad, barTop, pad + barW, barBottom, world.prayer / World.MAX_PRAYER, Color.rgb(88, 116, 210))

        label(c, "Faith " + world.faith.toInt(), w - pad, labelY, labelSize, goldLight, Paint.Align.RIGHT)
        meter(c, w - pad - barW, barTop, w - pad, barBottom, world.faith / 100f, gold)

        val secs = world.timeLeft.toInt()
        val clockText = (secs / 60).toString() + ":" + (secs % 60).toString().padStart(2, '0')
        if (world.armageddon) {
            fitted(c, "ARMAGEDDON", w / 2f, hudH * 0.48f, hudH * 0.3f, w * 0.3f, Color.rgb(236, 110, 90), true)
        } else {
            label(c, clockText, w / 2f, hudH * 0.5f, hudH * 0.4f, if (secs <= 20) Color.rgb(236, 110, 90) else vellum, Paint.Align.CENTER)
        }
        if (world.holyWar) {
            // Head count for both sides, and the rival god's Prayer in red underneath.
            val tally = world.count(Faction.BELIEVER).toString() + " faithful · " + world.count(Faction.NON_BELIEVER) + " heathens"
            plain(c, tally, w / 2f, hudH * 0.76f, hudH * 0.2f, vellumDark, Paint.Align.CENTER)
            val rl = w * 0.37f
            val rr = w * 0.63f
            meter(c, rl, hudH * 0.84f, rr, hudH * 0.97f, world.rivalPrayer / World.MAX_PRAYER, vermilion)
            // Notches where it can afford a Bolt and a Meteor, so you can see what is coming.
            stroke(ink, 3f)
            val boltAt = rl + (rr - rl) * World.RIVAL_BOLT_COST / World.MAX_PRAYER
            val meteorAt = rl + (rr - rl) * World.RIVAL_METEOR_COST / World.MAX_PRAYER
            c.drawLine(boltAt, hudH * 0.82f, boltAt, hudH * 0.99f, paint)
            c.drawLine(meteorAt, hudH * 0.82f, meteorAt, hudH * 0.99f, paint)
        } else {
            plain(c, world.count(Faction.NON_BELIEVER).toString() + " heathens", w / 2f, hudH * 0.84f, hudH * 0.24f, vellumDark, Paint.Align.CENTER)
        }
    }

    private fun meter(c: Canvas, l: Float, t: Float, r: Float, b: Float, frac: Float, color: Int) {
        val radius = (b - t) / 2f
        fill(vellumDark)
        c.drawRoundRect(l, t, r, b, radius, radius, paint)
        val f = frac.coerceIn(0f, 1f)
        if (f > 0.01f) {
            fill(color)
            c.drawRoundRect(l, t, l + max((r - l) * f, radius * 2f), b, radius, radius, paint)
        }
        stroke(ink, 2.5f)
        c.drawRoundRect(l, t, r, b, radius, radius, paint)
    }

    private fun drawPowerBar(c: Canvas) {
        val top = height - barH
        val bar = world.powers
        val slot = width / bar.size.toFloat()
        val gap = slot * 0.06f
        for (i in bar.indices) {
            val p = bar[i]
            val l = i * slot + gap
            val r = (i + 1) * slot - gap
            val t = top + gap
            val b = height - gap
            val affordable = world.canAfford(p)
            val isSelected = p == selected
            fill(if (isSelected) vellum else vellumDark)
            c.drawRoundRect(l, t, r, b, 14f, 14f, paint)
            stroke(if (isSelected) gold else ink, if (isSelected) 7f else 3f)
            c.drawRoundRect(l, t, r, b, 14f, 14f, paint)
            if (isSelected) {
                stroke(ink, 2f)
                c.drawRoundRect(l - 4f, t - 4f, r + 4f, b + 4f, 16f, 16f, paint)
            }
            val cx = (l + r) / 2f
            val inkNow = if (affordable) ink else Color.argb(110, 46, 32, 22)
            fitted(c, p.label, cx, t + (b - t) * 0.47f, barH * 0.27f, r - l - 26f, inkNow, true)
            fitted(c, world.costOf(p).toInt().toString() + " · " + p.gesture, cx, t + (b - t) * 0.8f, barH * 0.17f, r - l - 34f, if (affordable) vermilion else inkNow, false)
        }
    }

    // ---------------------------------------------------------------- cards

    /** A sheet of vellum with a ruled border, for menus and messages. */
    private fun sheet(c: Canvas, l: Float, t: Float, r: Float, b: Float) {
        fill(vellum)
        c.drawRoundRect(l, t, r, b, 18f, 18f, paint)
        stroke(ink, 4f)
        c.drawRoundRect(l, t, r, b, 18f, 18f, paint)
        stroke(vermilion, 3f)
        c.drawRoundRect(l + 10f, t + 10f, r - 10f, b - 10f, 12f, 12f, paint)
    }

    private fun stars(count: Int): String = "★".repeat(count) + "☆".repeat(3 - count)

    private fun drawMenu(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val m = w * 0.05f
        sheet(c, m, m, w - m, h - m)
        stroke(gold, 3f)
        c.drawRoundRect(m + 18f, m + 18f, w - m - 18f, h - m - 18f, 8f, 8f, paint)

        // Title with a drop capital.
        val big = w * 0.2f
        val rest = w * 0.088f
        paint.typeface = serifBold
        paint.isFakeBoldText = false
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = big
        val capW = paint.measureText("S")
        paint.textSize = rest
        val restW = paint.measureText("mite Them Now")
        val startX = (w - capW - restW - 14f) / 2f + 8f
        val baseY = h * 0.15f
        fill(ultramarine)
        c.drawRect(startX - 10f, baseY - big * 0.8f, startX + capW + 6f, baseY + big * 0.08f, paint)
        stroke(ink, 3f)
        c.drawRect(startX - 10f, baseY - big * 0.8f, startX + capW + 6f, baseY + big * 0.08f, paint)
        paint.typeface = serifBold
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = big
        paint.color = goldLight
        c.drawText("S", startX - 2f, baseY, paint)
        paint.textSize = rest
        paint.color = ink
        c.drawText("mite Them Now", startX + capW + 14f, baseY, paint)
        fitted(c, "being a Book of Petty Miracles", w / 2f, h * 0.198f, w * 0.045f, w * 0.7f, vermilion, false)

        val cardL = m + w * 0.06f
        val cardR = w - m - w * 0.06f
        val cardH = h * 0.098f
        val first = h * 0.25f
        for (i in World.LEVELS.indices) {
            val info = World.LEVELS[i]
            val t = first + i * (cardH + h * 0.009f)
            val b = t + cardH
            cardTop[i] = t
            cardBottom[i] = b
            fill(Color.rgb(240, 228, 196))
            c.drawRoundRect(cardL, t, cardR, b, 14f, 14f, paint)
            stroke(ink, 3f)
            c.drawRoundRect(cardL, t, cardR, b, 14f, 14f, paint)

            val roundel = cardH * 0.24f
            val rx = cardL + roundel + cardH * 0.14f
            val ry = t + cardH * 0.36f
            fill(if (i == World.HOLY_WAR) gold else if (i >= 2) vermilion else ultramarine)
            c.drawCircle(rx, ry, roundel, paint)
            stroke(gold, 4f)
            c.drawCircle(rx, ry, roundel - 5f, paint)
            stroke(ink, 2.5f)
            c.drawCircle(rx, ry, roundel, paint)
            fitted(c, numerals[i], rx, ry + roundel * 0.36f, roundel * 1.05f, roundel * 1.4f, if (i == World.HOLY_WAR) ink else goldLight, true)

            val textL = rx + roundel + cardH * 0.12f
            paint.typeface = serifBold
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.LEFT
            paint.color = ink
            paint.textSize = cardH * 0.24f
            shrinkTo(info.title, cardR - textL - 16f)
            c.drawText(info.title, textL, t + cardH * 0.36f, paint)
            paint.color = gold
            paint.textSize = cardH * 0.22f
            c.drawText(stars(bestStars(i)), textL, t + cardH * 0.62f, paint)
            fitted(c, info.blurb, (cardL + cardR) / 2f, t + cardH * 0.87f, cardH * 0.15f, cardR - cardL - 32f, ink, false)
        }
        fitted(c, "Tap a chapter, or go to war", w / 2f, h * 0.935f, w * 0.042f, w * 0.7f, vermilion, true)
    }

    /** Holy War: pick the ground and a doctrine before the match. */
    private fun drawSetup(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val m = w * 0.05f
        sheet(c, m, m, w - m, h - m)
        val inner = w - 2f * m - 80f
        fitted(c, "Holy War", w / 2f, h * 0.13f, w * 0.12f, inner, ink, true)
        fitted(c, "A rival god. Two growing flocks. Five minutes.", w / 2f, h * 0.175f, w * 0.04f, inner, vermilion, false)

        fitted(c, "The ground", w / 2f, h * 0.25f, w * 0.05f, inner, ink, true)
        setupMapTop = h * 0.27f
        setupMapBottom = h * 0.35f
        val slot = w / 3f
        for (i in 0 until 3) {
            val l = max(m + 24f, i * slot + slot * 0.06f)
            val r = min(w - m - 24f, (i + 1) * slot - slot * 0.06f)
            val on = i == warMap
            fill(if (on) ultramarine else vellumDark)
            c.drawRoundRect(l, setupMapTop, r, setupMapBottom, 14f, 14f, paint)
            stroke(if (on) gold else ink, if (on) 6f else 3f)
            c.drawRoundRect(l, setupMapTop, r, setupMapBottom, 14f, 14f, paint)
            fitted(c, mapNames[i], (l + r) / 2f, setupMapTop + (setupMapBottom - setupMapTop) * 0.62f, w * 0.042f, r - l - 24f, if (on) goldLight else ink, true)
        }

        fitted(c, "Your doctrine", w / 2f, h * 0.43f, w * 0.05f, inner, ink, true)
        val dl = m + w * 0.06f
        val dr = w - m - w * 0.06f
        for (i in 0 until 3) {
            val t = h * 0.455f + i * h * 0.105f
            val b = t + h * 0.092f
            setupDocTop[i] = t
            setupDocBottom[i] = b
            val on = i == warDoctrine
            fill(if (on) Color.rgb(246, 238, 214) else vellumDark)
            c.drawRoundRect(dl, t, dr, b, 14f, 14f, paint)
            stroke(if (on) gold else ink, if (on) 7f else 3f)
            c.drawRoundRect(dl, t, dr, b, 14f, 14f, paint)
            fitted(c, doctrines[i].label, w / 2f, t + (b - t) * 0.42f, w * 0.05f, dr - dl - 40f, ink, true)
            fitted(c, doctrines[i].blurb, w / 2f, t + (b - t) * 0.8f, w * 0.036f, dr - dl - 40f, vermilion, false)
        }

        setupBeginTop = h * 0.81f
        setupBeginBottom = h * 0.89f
        fill(ultramarine)
        c.drawRoundRect(w * 0.25f, setupBeginTop, w * 0.75f, setupBeginBottom, 16f, 16f, paint)
        stroke(ink, 3.5f)
        c.drawRoundRect(w * 0.25f, setupBeginTop, w * 0.75f, setupBeginBottom, 16f, 16f, paint)
        fitted(c, "Begin", w / 2f, setupBeginTop + (setupBeginBottom - setupBeginTop) * 0.66f, w * 0.06f, w * 0.4f, goldLight, true)
    }

    private fun drawBriefing(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val info = World.LEVELS[level]
        fill(Color.argb(150, 52, 34, 24))
        c.drawRect(0f, 0f, w, h, paint)
        val l = w * 0.07f
        val r = w * 0.93f
        val t = h * 0.22f
        val b = h * 0.74f
        sheet(c, l, t, r, b)
        val inner = r - l - 60f
        fitted(c, if (level == World.HOLY_WAR) "The long game" else "Chapter " + numerals[level], w / 2f, t + (b - t) * 0.12f, w * 0.045f, inner, vermilion, false)
        fitted(c, info.title, w / 2f, t + (b - t) * 0.25f, w * 0.085f, inner, ink, true)
        fitted(c, info.blurb, w / 2f, t + (b - t) * 0.35f, w * 0.04f, inner, ink, false)
        stroke(gold, 3f)
        c.drawLine(l + 60f, t + (b - t) * 0.41f, r - 60f, t + (b - t) * 0.41f, paint)
        for (i in info.tips.indices) {
            fitted(c, info.tips[i], w / 2f, t + (b - t) * (0.5f + 0.078f * i), w * 0.044f, inner, ink, false)
        }
        val blink = 150 + (105 * (0.5f + 0.5f * sin(clock * 4f))).toInt()
        fitted(c, "Tap to begin", w / 2f, t + (b - t) * 0.94f, w * 0.055f, inner, Color.argb(blink, 196, 52, 36), true)
    }

    private fun drawResult(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val won = world.outcome == Outcome.VICTORY
        fill(Color.argb(min(150, (overTime * 300f).toInt()), 52, 34, 24))
        c.drawRect(0f, 0f, w, h, paint)
        if (overTime < 0.5f) return

        val l = w * 0.07f
        val r = w * 0.93f
        val t = h * 0.2f
        val b = h * 0.78f
        sheet(c, l, t, r, b)
        val inner = r - l - 60f
        val ph = b - t
        fitted(c, if (won) "All Smitten" else "Divine Failure", w / 2f, t + ph * 0.15f, w * 0.1f, inner, if (won) ink else vermilion, true)
        if (won) {
            fitted(c, stars(world.stars()), w / 2f, t + ph * 0.3f, w * 0.12f, inner, gold, true)
        } else {
            fitted(c, world.defeatReason, w / 2f, t + ph * 0.28f, w * 0.05f, inner, ink, false)
        }
        stroke(gold, 3f)
        c.drawLine(l + 60f, t + ph * 0.36f, r - 60f, t + ph * 0.36f, paint)
        fitted(c, "Smitten " + world.smitten + "  ·  Converted " + world.converted, w / 2f, t + ph * 0.45f, w * 0.046f, inner, ink, false)
        fitted(c, "Believers lost " + (world.believersLost + world.defected) + "  ·  Best chain x" + world.bestChain, w / 2f, t + ph * 0.53f, w * 0.046f, inner, ink, false)
        if (won) {
            // What the second and third stars were for.
            fitted(c, (if (world.goal2Met) "★ " else "☆ ") + world.goal2Text, w / 2f, t + ph * 0.63f, w * 0.044f, inner, if (world.goal2Met) ink else vermilion, false)
            fitted(c, (if (world.goal3Met) "★ " else "☆ ") + world.goal3Text, w / 2f, t + ph * 0.7f, w * 0.044f, inner, if (world.goal3Met) ink else vermilion, false)
        }

        buttons = if (won && level < World.LEVELS.size - 1) listOf("Again", "Next", "Chapters") else listOf("Again", "Chapters")
        buttonTop = t + ph * 0.79f
        buttonBottom = t + ph * 0.93f
        if (overTime < 0.8f) return
        val slot = w / buttons.size
        for (i in buttons.indices) {
            val bl = max(l + 30f, i * slot + slot * 0.08f)
            val br = min(r - 30f, (i + 1) * slot - slot * 0.08f)
            val primary = buttons[i] == "Next" || (buttons.size == 2 && i == 0)
            fill(if (primary) ultramarine else vellumDark)
            c.drawRoundRect(bl, buttonTop, br, buttonBottom, 14f, 14f, paint)
            stroke(ink, 3f)
            c.drawRoundRect(bl, buttonTop, br, buttonBottom, 14f, 14f, paint)
            fitted(
                c, buttons[i], (bl + br) / 2f, buttonTop + (buttonBottom - buttonTop) * 0.66f,
                w * 0.05f, br - bl - 20f, if (primary) goldLight else ink, true
            )
        }
    }

    // ---------------------------------------------------------------- paint helpers

    private fun fill(color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
    }

    private fun stroke(color: Int, width: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = color
    }

    /** Bold serif text. */
    private fun label(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align) {
        paint.typeface = serifBold
        paint.isFakeBoldText = false
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        c.drawText(s, x, y, paint)
    }

    /** Regular serif text. */
    private fun plain(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, align: Paint.Align) {
        paint.typeface = serif
        paint.isFakeBoldText = false
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        c.drawText(s, x, y, paint)
    }

    /** Centred serif text that shrinks to fit [maxWidth]. */
    private fun fitted(c: Canvas, s: String, x: Float, y: Float, size: Float, maxWidth: Float, color: Int, bold: Boolean) {
        paint.typeface = if (bold) serifBold else serif
        paint.isFakeBoldText = false
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = Paint.Align.CENTER
        shrinkTo(s, maxWidth)
        c.drawText(s, x, y, paint)
    }

    private fun shrinkTo(s: String, maxWidth: Float) {
        val measured = paint.measureText(s)
        if (measured > maxWidth && measured > 0f && maxWidth > 0f) {
            paint.textSize = paint.textSize * maxWidth / measured
        }
    }
}
