package com.necromater.smitethemnow

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.necromater.smitethemnow.sim.Faction
import com.necromater.smitethemnow.sim.Fx
import com.necromater.smitethemnow.sim.FxKind
import com.necromater.smitethemnow.sim.Mood
import com.necromater.smitethemnow.sim.Outcome
import com.necromater.smitethemnow.sim.Power
import com.necromater.smitethemnow.sim.World
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws the world with placeholder shapes and turns touches into powers.
 *
 * Layout, top to bottom: HUD strip, the diorama, the power bar.
 * The loop is the simplest one that works: update and draw in onDraw,
 * then ask for the next frame.
 */
class GameView(context: Context) : View(context) {

    private var world = World()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val powers = Power.values()

    private var lastNanos = 0L
    private var clock = 0f
    private var started = false
    private var overTime = 0f
    private var selected = Power.LIGHTNING

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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        hudH = h * 0.085f
        barH = h * 0.12f
        val areaH = h - hudH - barH
        scale = min(w / World.W, areaH / World.H)
        originX = (w - World.W * scale) / 2f
        originY = hudH + (areaH - World.H * scale) / 2f
    }

    // ---------------------------------------------------------------- input

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val sx = event.x
        val sy = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!started) {
                    started = true
                    return true
                }
                if (world.outcome != Outcome.PLAYING) {
                    if (overTime > 0.8f) restart()
                    return true
                }
                if (sy >= height - barH) {
                    val index = (sx / (width / powers.size.toFloat())).toInt()
                    selected = powers[index.coerceIn(0, powers.size - 1)]
                    return true
                }
                val wx = toWorldX(sx)
                val wy = toWorldY(sy)
                when (selected) {
                    Power.LIGHTNING -> world.castLightning(wx, wy)
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

    private fun restart() {
        world = World(System.nanoTime())
        overTime = 0f
        dragging = false
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

        if (started) {
            world.update(dt)
            if (world.outcome != Outcome.PLAYING) overTime += dt
        }

        canvas.drawColor(Color.rgb(24, 22, 30))

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
        if (!started) drawIntro(canvas)
        else if (world.outcome != Outcome.PLAYING) drawResult(canvas)

        postInvalidateOnAnimation()
    }

    // ---------------------------------------------------------------- world

    private fun drawWorld(c: Canvas) {
        val w = world
        fill(Color.rgb(196, 182, 140))
        c.drawRect(0f, 0f, World.W, World.H, paint)

        // Scorch marks where someone used to be.
        for (n in w.npcs) {
            if (n.alive) continue
            fill(Color.argb(70, 40, 30, 20))
            c.drawCircle(n.x, n.y, 13f, paint)
        }

        for (b in w.buildings) {
            if (b.standing) {
                fill(Color.rgb(122, 92, 66))
                c.drawRect(b.x, b.y, b.x + b.w, b.y + b.h, paint)
                fill(Color.rgb(150, 70, 52))
                c.drawRect(b.x + 10f, b.y + 10f, b.x + b.w - 10f, b.y + b.h - 10f, paint)
                stroke(Color.rgb(98, 44, 34), 4f)
                c.drawLine(b.x + 10f, b.y + b.h / 2f, b.x + b.w - 10f, b.y + b.h / 2f, paint)
            } else {
                fill(Color.argb(150, 80, 70, 60))
                c.drawRect(b.x, b.y, b.x + b.w, b.y + b.h, paint)
                fill(Color.rgb(104, 94, 84))
                var k = 0
                while (k < 9) {
                    val rx = b.x + b.w * (0.12f + 0.38f * (k % 3))
                    val ry = b.y + b.h * (0.18f + 0.32f * (k / 3))
                    c.drawCircle(rx, ry, 11f + (k % 2) * 5f, paint)
                    k++
                }
            }
        }

        for (b in w.barrels) {
            if (!b.alive) continue
            val lit = b.fuse >= 0f
            fill(if (lit) Color.rgb(255, 120, 40) else Color.rgb(94, 60, 30))
            c.drawCircle(b.x, b.y, 14f, paint)
            stroke(Color.rgb(40, 26, 14), 3f)
            c.drawCircle(b.x, b.y, 14f, paint)
            c.drawCircle(b.x, b.y, 7f, paint)
        }

        for (f in w.fires) {
            val fade = min(1f, f.ttl / 0.6f)
            val flicker = 0.85f + 0.15f * sin(clock * 18f + f.x * 0.05f)
            fill(Color.argb((95 * fade).toInt(), 255, 110, 20))
            c.drawCircle(f.x, f.y, f.radius * flicker, paint)
            fill(Color.argb((150 * fade).toInt(), 255, 200, 60))
            c.drawCircle(f.x, f.y, f.radius * 0.55f * flicker, paint)
        }

        for (n in w.npcs) {
            if (!n.alive) continue
            val r = World.NPC_RADIUS
            if (n.faction == Faction.BELIEVER) {
                when (n.mood) {
                    Mood.ZEALOT -> {
                        val pulse = 4f + 3f * sin(clock * 12f + n.x)
                        stroke(Color.argb(220, 255, 255, 255), 3f)
                        c.drawCircle(n.x, n.y, r + pulse, paint)
                        fill(Color.rgb(255, 222, 80))
                    }
                    Mood.TERROR -> fill(Color.rgb(150, 170, 200))
                    else -> fill(Color.rgb(240, 196, 60))
                }
                c.drawCircle(n.x, n.y, r, paint)
                stroke(Color.rgb(110, 84, 20), 2.5f)
                c.drawCircle(n.x, n.y, r, paint)
            } else {
                fill(Color.rgb(140, 40, 70))
                c.drawCircle(n.x, n.y, r, paint)
                stroke(Color.rgb(60, 14, 30), 2.5f)
                c.drawCircle(n.x, n.y, r, paint)
                if (n.mood == Mood.SPOOKED) {
                    text(Color.rgb(60, 14, 30), 30f, Paint.Align.CENTER, true)
                    c.drawText("!", n.x, n.y - r - 6f, paint)
                }
            }
        }

        if (w.charging) {
            val full = w.charge >= 1f
            fill(Color.argb((40 + 90 * w.charge).toInt(), 255, 90, 30))
            c.drawCircle(w.chargeX, w.chargeY, World.METEOR_RADIUS * w.charge, paint)
            stroke(if (full) Color.WHITE else Color.rgb(255, 150, 60), if (full) 7f else 4f)
            c.drawCircle(w.chargeX, w.chargeY, World.METEOR_RADIUS, paint)
        }

        if (dragging) {
            stroke(Color.argb(170, 255, 255, 255), 8f)
            c.drawLine(dragX0, dragY0, dragX1, dragY1, paint)
            fill(Color.argb(170, 255, 255, 255))
            c.drawCircle(dragX1, dragY1, 14f, paint)
        }

        for (f in w.fx) drawFx(c, f)
    }

    private fun drawFx(c: Canvas, f: Fx) {
        val t = (f.age / f.life).coerceIn(0f, 1f)
        val alpha = (255 * (1f - t)).toInt()
        when (f.kind) {
            FxKind.BOLT -> {
                stroke(Color.argb(alpha, 255, 255, 190), 7f)
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
                fill(Color.argb(alpha, 255, 255, 255))
                c.drawCircle(f.x, f.y, 26f * (1f - t) + 6f, paint)
            }
            FxKind.BLAST -> {
                fill(Color.argb((alpha * 0.55f).toInt(), 255, 170, 60))
                c.drawCircle(f.x, f.y, f.size * (0.4f + 0.6f * t), paint)
                stroke(Color.argb(alpha, 255, 240, 200), 8f)
                c.drawCircle(f.x, f.y, f.size * t, paint)
            }
            FxKind.GUST -> {
                val dx = f.x2 - f.x
                val dy = f.y2 - f.y
                val len = max(1f, hypot(dx, dy))
                val nx = -dy / len
                val ny = dx / len
                stroke(Color.argb((alpha * 0.8f).toInt(), 255, 255, 255), 5f)
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
                val color = if (f.good) Color.argb(alpha, 255, 255, 255) else Color.argb(alpha, 255, 80, 70)
                text(color, 34f * f.size, Paint.Align.CENTER, true)
                c.drawText(f.text, f.x.coerceIn(120f, World.W - 120f), f.y - 24f - 60f * t, paint)
            }
            FxKind.ORB -> {
                fill(Color.argb(alpha, 120, 230, 255))
                c.drawCircle(f.x, f.y - 14f - 50f * t, 6f, paint)
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

        text(Color.rgb(150, 225, 250), labelSize, Paint.Align.LEFT, false)
        c.drawText("PRAYER " + world.prayer.toInt(), pad, labelY, paint)
        meter(c, pad, barTop, pad + barW, barBottom, world.prayer / World.MAX_PRAYER, Color.rgb(90, 200, 240))

        text(Color.rgb(250, 215, 110), labelSize, Paint.Align.RIGHT, false)
        c.drawText("FAITH " + world.faith.toInt(), w - pad, labelY, paint)
        meter(c, w - pad - barW, barTop, w - pad, barBottom, world.faith / 100f, Color.rgb(240, 196, 60))

        val secs = world.timeLeft.toInt()
        val urgent = secs <= 20
        text(if (urgent) Color.rgb(255, 90, 80) else Color.WHITE, hudH * 0.4f, Paint.Align.CENTER, true)
        c.drawText((secs / 60).toString() + ":" + (secs % 60).toString().padStart(2, '0'), w / 2f, hudH * 0.5f, paint)
        text(Color.rgb(220, 150, 170), hudH * 0.24f, Paint.Align.CENTER, false)
        c.drawText(world.count(Faction.NON_BELIEVER).toString() + " heathens", w / 2f, hudH * 0.84f, paint)
    }

    private fun meter(c: Canvas, l: Float, t: Float, r: Float, b: Float, frac: Float, color: Int) {
        val radius = (b - t) / 2f
        fill(Color.rgb(50, 46, 60))
        c.drawRoundRect(l, t, r, b, radius, radius, paint)
        val f = frac.coerceIn(0f, 1f)
        if (f > 0.01f) {
            fill(color)
            c.drawRoundRect(l, t, l + max((r - l) * f, radius * 2f), b, radius, radius, paint)
        }
    }

    private fun drawPowerBar(c: Canvas) {
        val top = height - barH
        val slot = width / powers.size.toFloat()
        val gap = slot * 0.05f
        for (i in powers.indices) {
            val p = powers[i]
            val l = i * slot + gap
            val r = (i + 1) * slot - gap
            val t = top + gap
            val b = height - gap
            val affordable = world.canAfford(p)
            val isSelected = p == selected
            fill(if (isSelected) Color.rgb(84, 70, 120) else Color.rgb(44, 40, 56))
            c.drawRoundRect(l, t, r, b, 18f, 18f, paint)
            if (isSelected) {
                stroke(Color.rgb(250, 215, 110), 5f)
                c.drawRoundRect(l, t, r, b, 18f, 18f, paint)
            }
            val cx = (l + r) / 2f
            val ink = if (affordable) Color.WHITE else Color.rgb(120, 116, 130)
            text(ink, barH * 0.26f, Paint.Align.CENTER, true)
            c.drawText(p.label, cx, t + (b - t) * 0.45f, paint)
            text(if (affordable) Color.rgb(150, 225, 250) else Color.rgb(120, 116, 130), barH * 0.17f, Paint.Align.CENTER, false)
            c.drawText(p.cost.toInt().toString() + "  ·  " + p.gesture, cx, t + (b - t) * 0.78f, paint)
        }
    }

    // ---------------------------------------------------------------- overlays

    private fun drawIntro(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        fill(Color.argb(215, 16, 14, 22))
        c.drawRect(0f, 0f, w, h, paint)
        text(Color.rgb(250, 215, 110), w * 0.105f, Paint.Align.CENTER, true)
        c.drawText("SMITE THEM NOW", w / 2f, h * 0.24f, paint)

        val lines = arrayOf(
            "Gold are your Believers. They pray.",
            "Crimson are heathens. They must go.",
            "",
            "Bolt: tap a heathen.",
            "Meteor: hold until the ring turns white.",
            "Gust: swipe to shove crowds and fan fires.",
            "",
            "Barrels explode. Fire spreads.",
            "Hurt your own flock and Faith drops.",
            "Heathens hide among Believers."
        )
        text(Color.WHITE, w * 0.043f, Paint.Align.CENTER, false)
        for (i in lines.indices) c.drawText(lines[i], w / 2f, h * 0.34f + i * w * 0.07f, paint)

        val blink = 150 + (105 * (0.5f + 0.5f * sin(clock * 4f))).toInt()
        text(Color.argb(blink, 150, 225, 250), w * 0.06f, Paint.Align.CENTER, true)
        c.drawText("Tap to begin", w / 2f, h * 0.86f, paint)
    }

    private fun drawResult(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val won = world.outcome == Outcome.VICTORY
        fill(Color.argb(min(200, (overTime * 400f).toInt()), 16, 14, 22))
        c.drawRect(0f, 0f, w, h, paint)
        if (overTime < 0.4f) return

        text(if (won) Color.rgb(250, 215, 110) else Color.rgb(255, 90, 80), w * 0.115f, Paint.Align.CENTER, true)
        c.drawText(if (won) "ALL SMITTEN" else "DIVINE FAILURE", w / 2f, h * 0.32f, paint)

        text(Color.WHITE, w * 0.05f, Paint.Align.CENTER, false)
        if (won) {
            val stars = world.stars()
            text(Color.rgb(250, 215, 110), w * 0.13f, Paint.Align.CENTER, true)
            c.drawText("★".repeat(stars) + "☆".repeat(3 - stars), w / 2f, h * 0.43f, paint)
            text(Color.WHITE, w * 0.05f, Paint.Align.CENTER, false)
        } else {
            c.drawText(world.defeatReason, w / 2f, h * 0.41f, paint)
        }
        c.drawText("Smitten: " + world.smitten, w / 2f, h * 0.52f, paint)
        c.drawText("Believers lost: " + world.believersLost, w / 2f, h * 0.57f, paint)
        c.drawText("Best chain: x" + world.bestChain, w / 2f, h * 0.62f, paint)

        if (overTime > 0.8f) {
            text(Color.rgb(150, 225, 250), w * 0.06f, Paint.Align.CENTER, true)
            c.drawText("Tap to smite again", w / 2f, h * 0.76f, paint)
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

    private fun text(color: Int, size: Float, align: Paint.Align, bold: Boolean) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        paint.isFakeBoldText = bold
    }
}
