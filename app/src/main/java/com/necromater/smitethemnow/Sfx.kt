package com.necromater.smitethemnow

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Sound effects and vibration.
 *
 * The sounds are short generated WAV files in res/raw (see tools/make_sounds.py).
 * Slots 0 to 10 line up with the sim's Cue enum, then WIN and LOSE.
 */
class Sfx(context: Context) {

    companion object {
        const val WIN = 11
        const val LOSE = 12
    }

    var enabled = true

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val vibrator: Vibrator? = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    private val ids = intArrayOf(
        pool.load(context, R.raw.sfx_zap, 1),
        pool.load(context, R.raw.sfx_boom, 1),
        pool.load(context, R.raw.sfx_whoosh, 1),
        pool.load(context, R.raw.sfx_shield, 1),
        pool.load(context, R.raw.sfx_smite, 1),
        pool.load(context, R.raw.sfx_loss, 1),
        pool.load(context, R.raw.sfx_convert, 1),
        pool.load(context, R.raw.sfx_crumble, 1),
        pool.load(context, R.raw.sfx_warn, 1),
        pool.load(context, R.raw.sfx_horn, 1),
        pool.load(context, R.raw.sfx_deny, 1),
        pool.load(context, R.raw.sfx_win, 1),
        pool.load(context, R.raw.sfx_lose, 1)
    )

    fun play(slot: Int) {
        if (!enabled || slot < 0 || slot >= ids.size) return
        val id = ids[slot]
        if (id != 0) pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    /** A short buzz. Does nothing on devices without a vibrator. */
    @Suppress("DEPRECATION")
    fun buzz(millis: Long) {
        if (!enabled) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v.vibrate(millis)
            }
        } catch (e: Exception) {
            // Vibration is a nicety. Never let it take the game down.
        }
    }

    fun release() {
        pool.release()
    }
}
