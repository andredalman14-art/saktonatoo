package com.example.saktonato

import android.content.Context
import android.content.SharedPreferences

data class SavedProfile(
    val heightCm: Float = 0f,
    val weightKg: Float = 0f,
    val chestCm: Float = 0f,     // chest or bust
    val waistCm: Float = 0f,
    val shoulderCm: Float = 0f,  // optional, 0 = not given
    val usualSize: String = "M"
) {
    val isComplete: Boolean get() = heightCm in 100f..220f

    /** Used by the camera screen. Falls back to the height-based shoulder estimate when shoulderCm is 0. */
    fun toBuyerProfile() = BuyerProfile(
        heightCm = if (heightCm > 0f) heightCm else 165f,
        shoulderCm = shoulderCm.takeIf { it > 0f }
    )
}

object ProfileStore {
    private lateinit var prefs: SharedPreferences
    var current: SavedProfile = SavedProfile()
        private set
    var favoritePitCm: Float = 0f
        private set

    fun saveFavorite(v: Float) {
        favoritePitCm = prefs.getFloat("fav_pit", 0f)
        prefs.edit().putFloat("fav_pit", v).apply()
    }

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("sakto_profile", Context.MODE_PRIVATE)
        current = SavedProfile(
            heightCm = prefs.getFloat("height", 0f),
            weightKg = prefs.getFloat("weight", 0f),
            chestCm = prefs.getFloat("chest", 0f),
            waistCm = prefs.getFloat("waist", 0f),
            shoulderCm = prefs.getFloat("shoulder", 0f),
            usualSize = prefs.getString("size", "M") ?: "M"
        )
    }

    fun save(p: SavedProfile) {
        current = p
        prefs.edit()
            .putFloat("height", p.heightCm)
            .putFloat("weight", p.weightKg)
            .putFloat("chest", p.chestCm)
            .putFloat("waist", p.waistCm)
            .putFloat("shoulder", p.shoulderCm)
            .putString("size", p.usualSize)
            .apply()
    }
}