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
import com.necromater.smitethemnow.sim.Faction
import com.necromater.smitethemnow.sim.Fx
import com.necromater.smitethemnow.sim.FxKind
import com.necromater.smitethemnow.sim.Mood
import com.necromater.smitethemnow.sim.Npc
import com.necromater.smitethemnow.sim.Outcome
import com.necromater.smitethemnow.sim.Power
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

    private enum class Screen { MENU, BRIEFING, PLAY, RESULT }

    private val prefs = context.getSharedPreferences("smite", Context.MODE_PRIVATE)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val serif: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
    private val serifBold: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    private val powers = Power.values()
    private val numerals = arrayOf("I", "II", "III", "IV", "V")

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
    private val blotX = FloatArray(70)
    private val blotY = FloatArray(70)
    private val blotR = FloatArray(70)

    private var screen = Screen.MENU
    private var level = 0
    private var world = World(0)
    private var selected = Power.LIGHTNING

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
            blotR[i] = 14f + r.nextFloat() * 46f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        hudH = h * 0.085f
        barH = h * 0.11f
        val areaH = h - hudH - barH
        scale = min(w / World.W, areaH / World.H)
        originX = (w - World.W * scale) / 2f
        originY = hudH + (areaH - World.H * scale) / 2f
    }

    // ---------------------------------------------------------------- flow

    private fun startLevel(index: Int) {
        level = index.coerceIn(0, World.LEVELS.size - 1)
        world = World(level, System.nanoTime())
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
                Screen.BRIEFING -> screen = Screen.PLAY
                Screen.RESULT -> {
                    if (overTime > 0.8f && buttons.isNotEmpty() && sy >= buttonTop && sy <= buttonBottom) {
                        val index = (sx / (width / buttons.size.toFloat())).toInt().coerceIn(0, buttons.size - 1)
                        when (buttons[index]) {
                            "Again" -> startLevel(level)
                            "Next" -> startLevel(level + 1)
                            else -> screen = Screen.MENU
                        }
                    }
                }
                Screen.PLAY -> {
                    if (sy >= height - barH) {
                        val index = (sx / (width / powers.size.toFloat())).toInt()
                        selected = powers[index.coerceIn(0, powers.size - 1)]
                        return true
                    }
                    val wx = toWorldX(sx)
                    val wy = toWorldY(sy)
                    when (selected) {
                        Power.LIGHTNING -> world.castLightning(wx, wy)
                        Power.SHIELD -> world.castShield(wx, wy)
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
        fill(Color.argb(26, 120, 84, 40))
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
        for (b in w.buildings) drawBuilding(c, b)

        for (b in w.barrels) {
            if (!b.alive) continue
            val lit = b.fuse >= 0f
            fill(if (lit) vermilion else Color.rgb(150, 100, 52))
            c.drawCircle(b.x, b.y, 14f, paint)
            stroke(ink, 3f)
            c.drawCircle(b.x, b.y, 14f, paint)
            c.drawCircle(b.x, b.y, 7f, paint)
        }

        for (r in w.rods) drawRod(c, r.x, r.y, r.alive, r.charges)
        for (f in w.fires) drawFire(c, f.x, f.y, f.radius, min(1f, f.ttl / 0.6f))
        for (n in w.npcs) if (n.alive) drawNpc(c, n)

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

    private fun drawBuilding(c: Canvas, b: Building) {
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
        // Flat, front-on and proud of it, like a building in a margin.
        fill(Color.rgb(240, 228, 196))
        c.drawRect(b.x, b.y, b.x + b.w, b.y + b.h, paint)
        val roofH = b.h * 0.42f
        fill(if (b.holy) ultramarine else vermilion)
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
    }

    private fun drawNpc(c: Canvas, n: Npc) {
        val x = n.x
        val y = n.y
        val believer = n.faction == Faction.BELIEVER
        val zealot = believer && n.mood == Mood.ZEALOT
        val terror = believer && n.mood == Mood.TERROR
        val headY = y - 11f

        if (zealot) {
            // Rays of unreasonable enthusiasm.
            stroke(gold, 3f)
            var k = 0
            while (k < 8) {
                val a = k * 0.7854f + clock * 2f
                c.drawLine(x + cos(a) * 15f, headY + sin(a) * 15f, x + cos(a) * 23f, headY + sin(a) * 23f, paint)
                k++
            }
            stroke(ink, 3f)
            c.drawLine(x - 7f, y - 3f, x - 15f, y - 16f, paint)
            c.drawLine(x + 7f, y - 3f, x + 15f, y - 16f, paint)
        }

        // Robe.
        path.reset()
        path.moveTo(x - 5f, y - 6f)
        path.lineTo(x + 5f, y - 6f)
        path.lineTo(x + 11f, y + 14f)
        path.lineTo(x - 11f, y + 14f)
        path.close()
        fill(if (!believer) murrey else if (terror) Color.rgb(150, 160, 184) else ultramarine)
        c.drawPath(path, paint)
        stroke(ink, 2.5f)
        c.drawPath(path, paint)

        if (believer && !terror) {
            fill(if (zealot) goldLight else gold)
            c.drawCircle(x, headY, 11f, paint)
            stroke(ink, 1.5f)
            c.drawCircle(x, headY, 11f, paint)
        }

        fill(skin)
        c.drawCircle(x, headY, 6.5f, paint)
        stroke(ink, 2f)
        c.drawCircle(x, headY, 6.5f, paint)

        if (!believer) {
            // A pointed hood, so nobody mistakes them for the saved.
            path.reset()
            path.moveTo(x - 8f, headY - 2f)
            path.lineTo(x + 2f, headY - 21f)
            path.lineTo(x + 8f, headY - 2f)
            path.close()
            fill(ink)
            c.drawPath(path, paint)
            if (n.mood == Mood.SPOOKED) {
                label(c, "!", x + 15f, headY - 8f, 30f, vermilion, Paint.Align.CENTER)
            }
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

    private fun drawRod(c: Canvas, x: Float, y: Float, alive: Boolean, charges: Int) {
        if (!alive) {
            stroke(ink, 6f)
            c.drawLine(x - 6f, y + 4f, x + 44f, y + 16f, paint)
            fill(ink)
            c.drawCircle(x - 6f, y + 4f, 9f, paint)
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
        val barW = w * 0.34f
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
        label(c, clockText, w / 2f, hudH * 0.5f, hudH * 0.4f, if (secs <= 20) Color.rgb(236, 110, 90) else vellum, Paint.Align.CENTER)
        plain(c, world.count(Faction.NON_BELIEVER).toString() + " heathens", w / 2f, hudH * 0.84f, hudH * 0.24f, vellumDark, Paint.Align.CENTER)
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
        val slot = width / powers.size.toFloat()
        val gap = slot * 0.06f
        for (i in powers.indices) {
            val p = powers[i]
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
            fitted(c, p.label, cx, t + (b - t) * 0.47f, barH * 0.27f, r - l - 16f, inkNow, true)
            fitted(c, p.cost.toInt().toString() + " · " + p.gesture, cx, t + (b - t) * 0.8f, barH * 0.17f, r - l - 16f, if (affordable) vermilion else inkNow, false)
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
        val startX = (w - capW - restW) / 2f
        val baseY = h * 0.17f
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
        fitted(c, "being a Book of Petty Miracles", w / 2f, h * 0.225f, w * 0.045f, w * 0.7f, vermilion, false)

        val cardL = m + w * 0.06f
        val cardR = w - m - w * 0.06f
        val cardH = h * 0.155f
        val first = h * 0.28f
        for (i in World.LEVELS.indices) {
            val info = World.LEVELS[i]
            val t = first + i * (cardH + h * 0.025f)
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
            fill(if (i == 2) vermilion else ultramarine)
            c.drawCircle(rx, ry, roundel, paint)
            stroke(gold, 4f)
            c.drawCircle(rx, ry, roundel - 5f, paint)
            stroke(ink, 2.5f)
            c.drawCircle(rx, ry, roundel, paint)
            fitted(c, numerals[i], rx, ry + roundel * 0.36f, roundel * 1.05f, roundel * 1.4f, goldLight, true)

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
        fitted(c, "Tap a chapter to begin", w / 2f, h * 0.9f, w * 0.05f, w * 0.7f, vermilion, true)
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
        fitted(c, "Chapter " + numerals[level], w / 2f, t + (b - t) * 0.12f, w * 0.045f, inner, vermilion, false)
        fitted(c, info.title, w / 2f, t + (b - t) * 0.25f, w * 0.085f, inner, ink, true)
        fitted(c, info.blurb, w / 2f, t + (b - t) * 0.35f, w * 0.04f, inner, ink, false)
        stroke(gold, 3f)
        c.drawLine(l + 60f, t + (b - t) * 0.41f, r - 60f, t + (b - t) * 0.41f, paint)
        for (i in info.tips.indices) {
            fitted(c, info.tips[i], w / 2f, t + (b - t) * (0.52f + 0.085f * i), w * 0.046f, inner, ink, false)
        }
        val blink = 150 + (105 * (0.5f + 0.5f * sin(clock * 4f))).toInt()
        fitted(c, "Tap to begin", w / 2f, t + (b - t) * 0.93f, w * 0.055f, inner, Color.argb(blink, 196, 52, 36), true)
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
        fitted(c, if (won) "All Smitten" else "Divine Failure", w / 2f, t + ph * 0.17f, w * 0.1f, inner, if (won) ink else vermilion, true)
        if (won) {
            fitted(c, stars(world.stars()), w / 2f, t + ph * 0.34f, w * 0.13f, inner, gold, true)
        } else {
            fitted(c, world.defeatReason, w / 2f, t + ph * 0.32f, w * 0.05f, inner, ink, false)
        }
        stroke(gold, 3f)
        c.drawLine(l + 60f, t + ph * 0.41f, r - 60f, t + ph * 0.41f, paint)
        fitted(c, "Smitten: " + world.smitten, w / 2f, t + ph * 0.52f, w * 0.05f, inner, ink, false)
        fitted(c, "Believers lost: " + world.believersLost, w / 2f, t + ph * 0.61f, w * 0.05f, inner, ink, false)
        fitted(c, "Best chain: x" + world.bestChain, w / 2f, t + ph * 0.70f, w * 0.05f, inner, ink, false)

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
