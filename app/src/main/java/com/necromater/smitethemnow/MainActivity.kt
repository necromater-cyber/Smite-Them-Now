package com.necromater.smitethemnow

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager

class MainActivity : Activity() {

    private lateinit var game: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        game = GameView(this)
        setContentView(game)
    }

    override fun onDestroy() {
        game.release()
        super.onDestroy()
    }

    /** Back leaves a level for the chapter list. On the chapter list it closes the app. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!game.handleBack()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
