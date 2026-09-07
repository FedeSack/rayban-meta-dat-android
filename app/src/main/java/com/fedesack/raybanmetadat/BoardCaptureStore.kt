package com.fedesack.raybanmetadat

import android.content.Context

class BoardCaptureStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<BoardCapture> = BoardCaptureMath.parseAll(prefs.getString(KEY, "").orEmpty())

    fun save(items: List<BoardCapture>) {
        prefs.edit().putString(KEY, BoardCaptureMath.serializeAll(items)).apply()
    }

    companion object {
        const val PREFS_NAME = "rayban_dat_captures"
        private const val KEY = "items"
    }
}
